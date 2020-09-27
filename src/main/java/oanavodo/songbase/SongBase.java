package oanavodo.songbase;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import oanavodo.songbase.Options.Check;

public class SongBase {

    public static String usage() {
        return Stream.of(
            "usage: " + SongBase.class.getSimpleName() + " [<options>] [<list> ...]",
            "Manage playlists if songs are moved or have to be added or removed.",
            "<list> Path to a playlist or '-' if playlist should be read from standard input. '-' can only be specified once. Otherwise some",
            "       operations allow to specify more than one playlist. Currently m3u (ISO8859-1) and m3u8 (UTF-8) playlist types are supported.",
            "Options:",
            "--base <dir>  Base folder for searching playlists. If playlists <list> are specified it defaults to the folder of the first playlist.",
            "              If '-' is specified it defaults to the current working directory.",
            "--out <file>  If a playlist has been modified the changes are written to the specified file. If '-' is specified for <file> the",
            "              playlist is written to standard output. If option '--out' is used only one playlist argument can be specified.",
            "--dryrun      No changes are made. Only report what would be done.",
            "--nocheck     Don't check if a songs exists when reading in the playlists.",
            "--nointerpret During default map operation: don't check for interpret folders.",
            "--rmsource    During map operation: delete a song in the source folder if it already exists in destination folder.",
            "--sorted      All playlists which has to be written are sorted before writing them. This also applies to standard output writes.",
            "--type <type> Playlist type when reading from standard input and writing to standard output (defaults to m3u).",
            "--help        Display this help.",
            "Operations:",
            "If no operation is specified but the '--out' option with one playlist the playlist format can be converted.",
            "--map <a>=<b>       Move all songs from folder <a> found in playlist <list> to folder <b>. Only one playlist argument is allowed.",
            "                    All other playlists found in the base folder are updated to reflect this move.",
            "                    A special behavior in this operation (if not switched of with option '--nointerpret') is that if there is a",
            "                    folder with the name of the interpret then the song is moved to this folder instead of <b>.",
            "--check             Only check all playlists found in the base folder (defaults to working directory) if their songs exist.",
            "--sort              Sorts all playlists supplied as arguments. If solely '-' is specified standard input is sorted and written",
            "                    to standard output. If option '--out <file>' is specified the output is written to the specified file.",
            "--shuffle [<gap>]   Shuffles all playlists supplied as arguments. <gap> is an optional number of songs. If solely '-' is specified",
            "                    standard input is shuffled and written to standard output. If option '--out <file>' is specified the output is ",
            "                    written to the specified file.",
            "                    which should be between songs of same interpret (default: 5). Please note that the gap also depends",
            "                    on the variety of interprets and may be lower than requested.",
            "--select <text>     Write entries of playlist <list> which contains text <text> to standard output. Multiple playlist arguments",
            "                    are allowed. The text is searched in the folder, interpret and title part. The search is case sensitive.",
            "--add <list2>       Add content of playlist <list2> to all playlists supplied as arguments. If solely '-' is specified the union",
            "                    of standard input and <list2> are written to standard output.  If option '--out <file>' is specified the output",
            "                    is written to the specified file. If no playlist is supplied as argument the songs from <list2> are added to all",
            "                    playlists found in the base folder.",
            "--remove <list2>    Remove content of playlist <list2> from all playlists supplied as arguments. If solely '-' is specified the",
            "                    differences between standard input and <list2> are written to standard output. If option '--out <file>' is specified",
            "                    the output is written to the specified file. If no playlist is supplied as argument the songs from <list2> are",
            "                    removed from all playlists found in the base folder.",
            "--union             Write content of all playlists supplied as argument to standard output.",
            "--intersect <list2> Write common entries in playlist <list2> and playlist <list> to standard output. Only one playlist argument is allowed."
        ).collect(Collectors.joining("\n"));
    }

    public static enum Operation { NONE, CHECKONLY, MAP, ADD, REMOVE, UNION, INTERSECT, SELECT, SORT, SHUFFLE };

    public static Playlist arg2Playlist(String arg, Path root, String type, String out) {
        // check output parameter
        Path outpath = null;
        if ((out != null) && !out.equals("-")) {
            try {
                if (out.isBlank()) throw new InvalidPathException(out, "Empty output path not allowed");
                outpath = Paths.get(out);
            }
            catch (InvalidPathException ex) {
                throw new RuntimeException("Please supply a valid output playlist path", ex);
            }
        }

        // empty playlist
        if (arg == null) {
            if (root == null) root = Paths.get("").toAbsolutePath();
            if (type == null) type = "m3u";
            return (outpath == null) ? Playlist.empty(System.out, root, type) : Playlist.empty(outpath, root);
        }

        // playlist from stdin
        if (arg.equals("-")) {
            if (root == null) root = Paths.get("").toAbsolutePath();
            if (type == null) type = "m3u";
            return (outpath == null) ? Playlist.of(System.in, System.out, root, type) : Playlist.of(System.in, outpath, root, type);
        }

        // playlist from file
        Path inpath;
        try {
            if (arg.isBlank()) throw new InvalidPathException(arg, "Empty playlist path not allowed");
            inpath = Paths.get(arg);
        }
        catch (InvalidPathException ex) {
            throw new RuntimeException("Please supply a valid playlist path", ex);
        }
        return (out == null) ? Playlist.of(inpath) : (outpath == null) ? Playlist.of(inpath, System.out, type) : Playlist.of(inpath, outpath);
    }

    public static PlaylistList args2Factory(Queue<String> args, Path root, String type, String out) {
        PlaylistList factory = new PlaylistList(root, false);
        boolean stdio = false;
        boolean outio = false;
        while (!args.isEmpty()) {
            Playlist list = arg2Playlist(args.poll(), root, type, out);
            if (list.isOutio()) {
                if (outio) throw new RuntimeException("Only one playlist can be specified if '--out' option is used");
                outio = true;
            }
            if (list.isStdio()) {
                if (stdio) throw new RuntimeException("Standard input or output playlist can only be specified once");
                stdio = true;
            }
            factory.addPlaylist(list);
        }
        return factory;
    }

    public static void main(String[] args) {

        try {
            // output encoding utf-8 (call chcp 65001 for windows console)
            System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));

            Operation command = Operation.NONE;
            Options options = new Options();
            Queue<String> paras = new LinkedList<>();
            Path root = null;
            String from = null;
            Path into = null;
            String out = null;
            String type = null;
            int shufflegap = 5;
            String search = "";
            boolean nointerpret = false;
            boolean delete = false;
            boolean sorted = false;
            int i = 0;
            while (i < args.length) {
                String option = args[i++];
                if (!option.startsWith("--")) {
                    paras.add(option);
                    continue;
                }
                String value = "";
                int pos = option.indexOf("=");
                if (pos > 0) {
                    value = option.substring(pos + 1);
                    option = option.substring(0, pos + 1);
                }
                switch(option) {
                    case "--nocheck":
                        options.setCheck(Check.NO);
                        break;
                    case "--nointerpret":
                        nointerpret = true;
                        break;
                    case "--rmsource":
                        delete = true;
                        break;
                    case "--sorted":
                        sorted = true;
                        break;
                    case "--dryrun":
                        options.setDryrun(true);
                        break;
                    case "--help":
                        System.err.println(usage());
                        System.exit(0);
                        break;
                    case "--base":
                        if ((i >= args.length) || args[i].startsWith("--") || args[i].isBlank()) throw new RuntimeException("Please supply base folder");
                        value = args[i++];
                    case "--base=":
                        try {
                            if (value.isBlank()) throw new InvalidPathException(value, "Empty path not allowed");
                            root = Paths.get(value).toAbsolutePath();
                        }
                        catch (InvalidPathException ex) {
                            throw new RuntimeException("Please supply a valid " + option.substring(2) + " base path", ex);
                        }
                        if (!Files.isDirectory(root)) throw new RuntimeException("Base folder not found: " + root.toString());
                        break;
                    case "--type":
                        if ((i >= args.length) || args[i].startsWith("--") || args[i].isBlank()) throw new RuntimeException("Please supply type");
                        value = args[i++];
                    case "--type=":
                        type = value.toLowerCase();
                        break;
                    case "--out":
                        if ((i >= args.length) || args[i].startsWith("--") || args[i].isBlank()) throw new RuntimeException("Please supply output path");
                        value = args[i++];
                    case "--out=":
                        out = value;
                        break;
                    case "--check":
                        command = Operation.CHECKONLY;
                        break;
                    case "--map":
                        if ((i >= args.length) || args[i].startsWith("--") || args[i].isBlank()) throw new RuntimeException("Please supply mapping");
                        value = args[i++];
                    case "--map=":
                        String[] parts = value.split("=");
                        if ((parts.length > 2) || (parts[0].isEmpty())) throw new RuntimeException("Invalid mapping: " + Arrays.toString(parts));
                        from = parts[0];
                        if (parts.length > 1) into = Paths.get(parts[1]);
                        command = Operation.MAP;
                        break;
                    case "--sort":
                        command = Operation.SORT;
                        break;
                    case "--shuffle":
                        if ((i >= args.length) && !args[i].startsWith("--") && args[i].matches("\\d+")) {
                            value = args[i++];
                        }
                    case "--shuffle=":
                        try { shufflegap = Integer.parseInt(value, 10); }
                        catch(NumberFormatException ex) {}
                        command = Operation.SHUFFLE;
                        break;
                    case "--select":
                        if ((i >= args.length) || args[i].startsWith("--") || args[i].isBlank()) throw new RuntimeException("Please supply search string");
                        value = args[i++];
                    case "--select=":
                        search = value;
                        command = Operation.SELECT;
                        break;
                    case "--union":
                        command = Operation.UNION;
                        break;
                    case "--add":
                    case "--remove":
                    case "--intersect":
                        if ((i >= args.length) || args[i].startsWith("--") || args[i].isBlank()) throw new RuntimeException("Please supply " + option.substring(2) + " playlist");
                        value = args[i++];
                    case "--add=":
                    case "--remove=":
                    case "--intersect=":
                        try {
                            if (value.isBlank()) throw new InvalidPathException(value, "Empty path not allowed");
                            into = Paths.get(value);
                        }
                        catch (InvalidPathException ex) {
                            throw new RuntimeException("Please supply a valid " + option.substring(2) + " playlist path", ex);
                        }
                        command = Operation.valueOf(option.substring(2).replace("=", "").toUpperCase());
                        break;
                    default:
                        throw new RuntimeException("Invalid option: " + option);
                }
            }

            PlaylistList.setOptions(options);
            Playlist.setOptions(options);
            Song.setOptions(options);

            switch(command) {
            case CHECKONLY:
                options.setCheck(Check.ONLY);
                if (root == null) root = Paths.get("").toAbsolutePath();
                new PlaylistList(root, true);
                break;
            case SELECT: {
                if (paras.isEmpty()) throw new RuntimeException("Please supply input playlist[s] or specify - for stdin");
                PlaylistList factory = args2Factory(paras, root, type, null);
                if (root == null) root = factory.getBase();
                final String fsearch = search;
                Playlist result = arg2Playlist(null, root, type, out);
                result.add(
                    factory.stream()
                        .peek(list -> System.err.format("SONGBASE: Filter for '%s', %s\n", fsearch, list.getName()))
                        .flatMap(list -> list.select(fsearch))
                );
                result.write(sorted);
                break;
            }
            case SORT: {
                if (paras.isEmpty()) throw new RuntimeException("Please supply input playlist[s] or specify - for stdin");
                PlaylistList factory = args2Factory(paras, root, type, out);
                factory.stream()
                    .peek(list -> System.err.format("SONGBASE: Sort %s\n", list.getName()))
                    .forEach(list -> list.sort());
                factory.update(false);
                break;
            }
            case SHUFFLE: {
                if (paras.isEmpty()) throw new RuntimeException("Please supply input playlist[s] or specify - for stdin");
                PlaylistList factory = args2Factory(paras, root, type, out);
                final int gap = shufflegap;
                factory.stream()
                    .peek(list -> System.err.format("SONGBASE: Shuffle %s\n", list.getName()))
                    .forEach(list -> list.shuffle(gap));
                factory.update(false);
                break;
            }
            case ADD: {
                PlaylistList factory;
                if (!paras.isEmpty()) {
                    factory = args2Factory(paras, root, type, out);
                }
                else {
                    if (root == null) root = Paths.get("").toAbsolutePath();
                    factory = new PlaylistList(root, true);
                }
                Playlist that = Playlist.of(into);
                factory.removePlaylist(that);
                factory.stream()
                    .peek(list -> System.err.format("SONGBASE: Add %s to %s\n", that.getName(), list.getName()))
                    .forEach(list -> list.add(that.entries()));
                factory.update(sorted);
                break;
            }
            case REMOVE: {
                PlaylistList factory;
                if (!paras.isEmpty()) {
                    factory = args2Factory(paras, root, type, out);
                }
                else {
                    if (root == null) root = Paths.get("").toAbsolutePath();
                    factory = new PlaylistList(root, true);
                }
                Playlist that = Playlist.of(into);
                factory.removePlaylist(that);
                factory.stream()
                    .peek(list -> System.err.format("SONGBASE: Remove %s from %s\n", that.getName(), list.getName()))
                    .forEach(list -> list.remove(that.entries()));
                factory.update(sorted);
                break;
            }
            case UNION: {
                if (paras.isEmpty()) throw new RuntimeException("Please supply input playlist[s] or specify - for stdin");
                PlaylistList factory = args2Factory(paras, root, type, null);
                if (root == null) root = factory.getBase();
                Playlist result = arg2Playlist(null, root, type, out);
                result.add(
                    factory.stream()
                        .peek(list -> System.err.format("SONGBASE: Add %s\n", list.getName()))
                        .flatMap(list -> list.entries()));
                result.write(sorted);
                break;
            }
            case INTERSECT: {
                if (paras.isEmpty()) throw new RuntimeException("Please supply input playlist or specify - for stdin");
                if (paras.size() > 1) throw new RuntimeException("Only one playlist argument  can be specified for '--intersect' operation");
                Playlist thiz = arg2Playlist(paras.poll(), root, type, null);
                if (root == null) root = thiz.getBase();
                Playlist that = Playlist.of(into);
                Playlist result = arg2Playlist(null, root, type, out);
                System.err.format("SONGBASE: Common songs of %s and %s\n", thiz.getName(), that.getName());
                result.add(
                    thiz.intersect(that)
                );
                result.write(sorted);
                break;
            }
            case MAP: {
                if (paras.isEmpty()) throw new RuntimeException("Please supply input playlist or specify - for stdin");
                if (paras.size() > 1) throw new RuntimeException("Only one playlist argument can be specified for '--map' operation");
                Playlist that = arg2Playlist(paras.poll(), root, type, out);
                if (root == null) root = that.getBase();
                PlaylistList factory = new PlaylistList(root, true);
                factory.removePlaylist(that);

                Path base = that.getBase();
                if (from == null) from = "Neu";
                if (into != null) nointerpret = true;
                if (into == null) into = Paths.get("Rock");
                Path to = base.resolve(into);
                if (!Files.isDirectory(to)) throw new RuntimeException("To folder not found: " + to.toString());

                System.err.format("SONGBASE: Mapping '%s' -> '%s' based on %s\n", from.replaceAll("\\\\", "/"), into.toString().replaceAll("\\\\", "/"), that.getName());
                int countno = 0;
                Map<Path, Integer> counts = new TreeMap<>();
                for (Playlist.Entry song : that.getEntries()) {
                    String folder = song.getFolder();
                    String interpret = song.getInterpret();
                    if (!folder.equals(from)) {
                        countno++;
                        continue;
                    }
                    Path newpath = base.resolve(interpret);
                    if (nointerpret || !Files.isDirectory(newpath)) {
                        newpath = to;
                    }
                    if (Files.isSameFile(base.resolve(folder), newpath)) {
                        countno++;
                        continue;
                    }
                    Song dup = song.move(newpath, delete);
                    int count = counts.getOrDefault(newpath, 0);
                    counts.put(newpath, count + 1);
                    if (dup != null) {
                        factory.move(song, dup);
                    }
                }
                that.update(sorted);
                factory.update(sorted);
                counts.forEach((path, count) -> System.err.format("Moves to %s: %d\n", base.relativize(path).toString().replaceAll("\\\\", "/"), count));
                System.err.format("Without move: %d\n", countno);
                break;
            }
            default:
                if (paras.isEmpty()) break;
                if (out == null) throw new RuntimeException("Please specify an operation option");
                if (paras.size() > 1) throw new RuntimeException("Only one playlist can be specified if '--out' option is used");
                Playlist thiz = arg2Playlist(paras.poll(), root, type, out);
                thiz.write(sorted);
                break;
            }
        }
        catch (Exception ex) {
            ex.printStackTrace(System.err);
        }
    }
}
