
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
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

    private static Pattern cmdPattern = Pattern.compile("(2?[<>-]?)%([^%]+)%|(\\S+)");

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
            Path parent = Files.createDirectories(SongBaseTest.testdir);
            parent = Files.createDirectories(parent.resolve(name));
            rundir = Files.createDirectories(parent.resolve("run"));
            cmpdir = Files.createDirectories(parent.resolve("cmp"));
            cleanDirectory(rundir);
            cleanDirectory(cmpdir);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        Matcher m = cmdPattern.matcher(command);
        args = m.results()
            .map(result -> (result.group(2) != null) ? provideArgument(name, result.group(1), result.group(2)) : result.group(3))
            .filter(String::isEmpty)
            .toArray(String[]::new);
    }

    protected String provideArgument(String name, String io, String arg) {
        Path res = null;
        Path inres = null;
        Path outres = null;
        String out = "";

        String[] parts = arg.split("=", 2);
        String left = parts[0];
        String right = (parts.length > 1) ? parts[1] : null;
        try {
            if ((right == null) && "root".equals(left)) return rundir.toString();

            if (left.isEmpty()) {
                if ((right == null) || right.isEmpty()) throw new IllegalArgumentException("Missing file name");
                left = "out_" + name;
                inres = Paths.get(left);
            }
            else {
                res = SongBaseTest.resourcePath("/" + left);
                inres = rundir.resolve("in_" + res.getFileName().toString());
                Files.copy(res, inres);
                createSongs(inres);
            }
            if (right != null) {
                if (right.isEmpty()) {
                    outres = cmpdir.resolve(inres.getFileName().toString());
                    Files.copy(res, outres);
                }
                else {
                    res = SongBaseTest.resourcePath("/" + right);
                    outres = cmpdir.resolve("out_" + res.getFileName().toString());
                    Files.copy(res, outres);
                }
            }
            if (outres != null) checks.add(new FileCheck(left, inres, outres));
            switch(io) {
            case "<":
                usedIO.setIn(new FileInputStream(inres.toFile()));
                break;
            case ">":
                usedIO.setOut(new PrintStream(inres.toFile()));
                break;
            case "2>":
                usedIO.setErr(new PrintStream(inres.toFile()));
                break;
            case "-":
                out = "-" + inres.getFileName().toString();
                break;
            case "":
                out = inres.getFileName().toString();
                break;
            default:
                throw new IllegalArgumentException("Illegal io mode: " + io);
            }
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        return out;
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
        checks.forEach(check -> {
            try {
                if (!Files.exists(check.left)) {
                    throw new AssertionFailedError(String.format("File %s has not been created", check.name));
                }
                if (Files.mismatch(check.left, check.right) != -1L) {
                    throw new AssertionFailedError(String.format("Content of file %s is not correct", check.name));
                }
            }
            catch (IOException e) {
                throw new UncheckedIOException(e);
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
            catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    private void cleanDirectory(Path dir) throws IOException {
        try (Stream<Path> s = Files.walk(dir)) {
            s.forEach(path -> path.toFile().delete());
        }
    }
}
