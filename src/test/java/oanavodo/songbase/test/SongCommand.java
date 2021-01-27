package oanavodo.songbase.test;


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
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import oanavodo.songbase.Options;
import oanavodo.songbase.Options.Check;
import oanavodo.songbase.Song;
import oanavodo.songbase.SongBase;
import oanavodo.songbase.playlist.Playlist;
import oanavodo.songbase.test.SongBaseTest.TestOption;
import org.opentest4j.AssertionFailedError;

public class SongCommand {

    private static final Pattern cmdPattern = Pattern.compile("([^\\s%]*)%([^%]+)%|(\\S+)");
    private static final Pattern prefixPattern = Pattern.compile("(\\d*[<>@])?(.*)");
    private static final String[] cmdPrefixes = new String[] {"<", ">", "2>", "@"};

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
    private EnumSet<TestOption> options = EnumSet.noneOf(TestOption.class);
    private final List<FileCheck> checks = new ArrayList<>();

    private Path rundir;
    private Path cmpdir;
    private StdIo usedIO = new StdIo();
    private String[] args;

    public SongCommand(String name, String command, TestOption... option) {
        this.name = name;
        Arrays.stream(option).forEach(o -> this.options.add(o));
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
            .filter(arg -> Arrays.stream(cmdPrefixes).noneMatch(prefix -> arg.startsWith(prefix)))
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
        Matcher m = prefixPattern.matcher(prefix);
        String route = m.matches() ? m.group(1) : "";
        if (route == null) route = "";
        String sub = m.matches() ? m.group(2) : "";

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
            return sub + rundir.toString();
        }
        Path res = null;
        Path inres = null;
        Path outres = null;
        try {
            if (">".equals(route) || "2>".equals(route)) {
                res = Paths.get(left);
                inres = resolve(rundir, sub, left, "out_");
            }
            else {
                if (ref == null) {
                    res = SongBaseTest.resourcePath("/" + left);
                }
                else {
                    res = resolve(getRundir(ref), null, left, "out_");
                    if (!Files.exists(res)) res = resolve(getRundir(ref), null, left, "in_");
                }
                inres = resolve(rundir, sub, left, "in_");
                Files.createDirectories(inres.getParent());
                Files.copy(res, inres);
                if (!options.contains(TestOption.NOCREATE)) createSongs(inres);
            }
            if (right != null) {
                if (!right.isEmpty()) res = SongBaseTest.resourcePath("/" + right);
                outres = resolve(cmpdir, sub, right, "cmp_");
                copyFile(res, outres);
            }
            if (outres != null) checks.add(new FileCheck(left, inres, outres));
            switch(route) {
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
        return route + SongBaseTest.basedir.relativize(inres).toString();
    }

    private Path resolve(Path base, String sub, String name, String prefix) {
        if (sub == null) sub = "";
        if (prefix == null) prefix = "";
        Path relpath = Paths.get(sub);
        Path namepath = Paths.get(name);
        return base.resolve(relpath).resolve(prefix + namepath.getFileName().toString());
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
        try {
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
            });
        }
        finally {
            System.err.format("RESULT(%s):%s\n", name, log.toString());
        }
    }

    private void createSongs(Path path) {
        Options options2 = new Options();
        options2.setCheck(Check.NO);
        Song.setOptions(options2);
        Playlist list = Playlist.of(path);
        Path relpath = rundir.relativize(list.getBase());
        list.entries().forEach(song -> {
            Path spath = rundir.resolve(relpath).resolve(song.getFolder()).resolve(song.getName());
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

    private void copyFile(Path res, Path outres) throws IOException {
        Files.createDirectories(outres.getParent());
        if (!res.toString().endsWith(".out") || !options.contains(TestOption.REPLACEOUT)) {
            Files.copy(res, outres);
            return;
        }
        String outctt = Files.readString(res);
        outctt = outctt.replace("%basedir%", SongBaseTest.basedir.toString());
        Files.writeString(outres, outctt);
    }

    private void cleanDirectory(Path dir) throws IOException {
        try (Stream<Path> s = Files.walk(dir)) {
            s.filter(path -> !path.equals(dir)).forEach(path -> path.toFile().delete());
        }
    }
}
