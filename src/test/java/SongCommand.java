
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import oanavodo.songbase.Options;
import oanavodo.songbase.Options.Check;
import oanavodo.songbase.Playlist;
import oanavodo.songbase.Song;
import oanavodo.songbase.SongBase;
import org.opentest4j.AssertionFailedError;

public class SongCommand {

    private static Pattern cmdPattern = Pattern.compile("([^\\s%]*)%([^%]+)%|(\\S+)");

    protected static class FileCheck {
        private String name;
        private Path left;
        private Path right;

        public FileCheck(String name, Path left, Path right) {
            this.name = name;
            this.left = left;
            this.right = right;
        }
    }

    private String name;
    private final List<FileCheck> checks = new ArrayList<>();

    private Path rundir;
    private Path cmpdir;
    private StdIo usedIO = new StdIo();
    private String[] args;

    public SongCommand(String name, String command) {
        this.name = name;
        try {
            rundir = Files.createDirectories(getRundir(name));
            cmpdir = Files.createDirectories(getCmpdir(name));
            cleanDirectory(rundir);
            cleanDirectory(cmpdir);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        Matcher m = cmdPattern.matcher(command);
        StringBuilder log = new StringBuilder("songbase");
        args = m.results()
            .map(result -> (result.group(2) != null) ? provideArgument(name, result.group(1), result.group(2)) : result.group(3))
            .peek(arg -> log.append(" ").append(arg))
            .filter(arg -> !(arg.startsWith("<") || arg.startsWith(">") || arg.startsWith("2>")))
            .toArray(String[]::new);
        System.err.format("TEST(%s): %s\n", name, log.toString());
    }

    public final Path getRundir(String name) {
        return SongBaseTest.testdir.resolve(name).resolve("run");
    }

    public final Path getCmpdir(String name) {
        return SongBaseTest.testdir.resolve(name).resolve("cmp");
    }

    protected String provideArgument(String name, String prefix, String arg) {
        String ref = null;
        String[] parts = arg.split(":", 2);
        if (parts.length > 1) {
            ref = parts[0];
            arg = parts[1];
        }
        parts = arg.split("=", 2);
        String left = parts[0];
        String right = (parts.length > 1) ? parts[1] : null;
        // no file names
        if (left.isEmpty() && ((right == null) || right.isEmpty())) throw new IllegalArgumentException("Missing file name");
        if (right != null) {
            if (left.isEmpty()) left = right;
        }
        else if ("run".equals(left)) {
            return prefix + rundir.toString();
        }
        Path res = null;
        Path inres = null;
        Path outres = null;
        try {
            if (">".equals(prefix)) {
                res = Paths.get(left);
                inres = rundir.resolve(Paths.get("out_" + res.getFileName().toString()));
            }
            else {
                if (ref == null) {
                    res = SongBaseTest.resourcePath("/" + left);
                }
                else {
                    res = getRundir(ref).resolve("out_" + left);
                    if (!Files.exists(res)) res = getRundir(ref).resolve("in_" + left);
                }
                inres = rundir.resolve("in_" + res.getFileName().toString());
                Files.copy(res, inres);
                createSongs(inres);
            }
            if (right != null) {
                if (!right.isEmpty()) res = SongBaseTest.resourcePath("/" + right);
                outres = cmpdir.resolve("cmp_" + res.getFileName().toString());
                Files.copy(res, outres);
            }
            if (outres != null) checks.add(new FileCheck(left, inres, outres));
            switch(prefix) {
            case "<":
                usedIO.setIn(new FileInputStream(inres.toFile()));
                break;
            case ">":
                usedIO.setOut(new PrintStream(inres.toFile()));
                break;
            case "2>":
                usedIO.setErr(new PrintStream(inres.toFile()));
                break;
            }
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        return prefix + SongBaseTest.basedir.relativize(inres).toString();
    }

    public void call() {
        usedIO.set();
        try {
            SongBase.main(args);
        }
        finally {
            usedIO.reset();
        }
        checkResults(name, checks);
    }

    protected void checkResults(String name, List<FileCheck> checks) {
        StringBuilder log = new StringBuilder("");
        checks.forEach(check -> {
            try {
                if (!Files.exists(check.left)) {
                    log.append(" FAIL");
                    throw new AssertionFailedError(String.format("File %s has not been created", check.name));
                }
                if (Files.mismatch(check.left, check.right) != -1L) {
                    log.append(" FAIL");
                    throw new AssertionFailedError(String.format("Content of file %s is not correct", check.name));
                }
                log.append(" OK");
            }
            catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            finally {
                System.err.format("RESULT(%s):%s\n", name, log.toString());
            }
        });
    }

    private void createSongs(Path path) {
        Options options = new Options();
        options.setCheck(Check.NO);
        Song.setOptions(options);
        Playlist list = Playlist.of(path);
        list.entries().forEach(song -> {
            Path spath = rundir.resolve(song.getFolder()).resolve(song.getName());
            try {
                Files.createDirectories(spath.getParent());
                Files.createFile(spath);
            }
            catch (FileAlreadyExistsException e) {}
            catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    private void cleanDirectory(Path dir) throws IOException {
        try (Stream<Path> s = Files.walk(dir)) {
            s.filter(path -> !path.equals(dir)).forEach(path -> path.toFile().delete());
        }
    }
}
