package oanavodo.songbase;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SongBase {

    public static String usage() {
        return Stream.of(
            "usage: " + SongBase.class.getSimpleName() + " [<options>] [<list> ...]",
            "Manage playlists if songs are moved or have to be added or removed.",
            "<list> Path to a playlist or '-' if playlist has to be read of standard input. Some operations allow to specify more than one playlist.",
            "       But '-' can only be specified once. Currently m3u (ISO8859-1) and m3u8 (UTF-8) playlist types are supported.",
            "Options:",
            "--base <dir>  Base folder for searching playlists. If a playlists <list> are specified it defaults to the folder of the first playlist.",
            "              If '-' is specified the current working directory is used.",
            "--dryrun      No changes are made. Only report what would be done.",
            "--nocheck     Don't check if a songs exists when reading in the playlists.",
            "--nointerpret During default map operation: don't check for interpret folders.",
            "--rmsource    During map operation: delete a song in the source folder if it already exists in destination folder.",
            "--type <type> Playlist type when reading from standard input and writing to standard output (defaults to m3u).",
            "--help        Display this help.",
            "Operations:",
            "If playlists are written to standard output their songs are alphabetically sorted by ignoring letter case.",
            "--map <a>=<b>       Move all songs from folder <a> found in playlist <list> to folder <b>. Only one playlist argument is allowed.",
            "                    All other playlists found in the base folder are updated to reflect this move.",
            "                    This is the default operation if no other is given. Songs are moved from folder 'Neu' to 'Rock'.",
            "                    A special behavior in this default operation is that if there is a folder with the name of the interpret",
            "                    then the song is moved to this folder instead of 'Rock'.",
            "--check             Only check all playlists found in the base folder (defaults to working directory) if their songs exist.",
            "--select <string>   Write entries of playlist <list> which contains string <string> to standard output. Multiple playlist arguments are allowed.",
            "                    The string is searched in the folder, interpret and title part. The search is case sensitive.",
            "--add <list2>       Add content of playlist <list2> to all playlists supplied as arguments. If only '-' is specified",
            "                    the union of standard input and <list2> are written to standard output. If no playlist is supplied",
            "                    as argument the songs from <list2> are added to all playlists found in the base folder.",
            "--remove <list2>    Remove content of playlist <list2> from all playlists supplied as arguments. If only '-' is specified",
            "                    the differences between standard input and <list2> are written to standard output. If no playlist is supplied",
            "                    as argument the songs from <list2> are removed from all playlists found in the base folder.",
            "--union             Write content of all playlists supplied as argument to standard output.",
            "--intersect <list2> Write common entries in playlist <list2> and playlist <list> to standard output. Only one playlist argument is allowed."
        ).collect(Collectors.joining("\n"));
    }

    public static enum Operation { NONE, CHECKONLY, MAP, ADD, REMOVE, UNION, INTERSECT, SELECT };

    public static Playlist arg2Playlist(String arg, Path root, String type) {
        if (arg.equals("-")) {
            if (root == null) root = Paths.get("").toAbsolutePath();
            return Playlist.of(System.in, System.out, root, type, false);
        }
        return Playlist.of(Paths.get(arg), false);
    }

    public static PlaylistList args2Factory(String[] args, Path root, String type) {
        PlaylistList factory = new PlaylistList(root, false, false);
        int i = 0;
        while (i < args.length) {
            Playlist list = arg2Playlist(args[i], root, type);
            if (list.isStdio() && factory.hasStdio()) throw new RuntimeException("Standard input playlist can only be specified once");
            factory.addPlaylist(list);
            i++;
        }
        return factory;
    }

    public static void main(String[] args) {

        try {
            // output encoding utf-8 (call chcp 65001 for windows console)
            System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));

            Operation command = Operation.MAP;
            Path root = null;
            String from = null;
            Path into = null;
            String type = "m3u";
            String search = "";
            boolean nointerpret = false;
            int i = 0;
            while (i < args.length) {
                if (!args[i].startsWith("--")) break;
                String option = args[i++];
                switch(option) {
                    case "--check":
                        command = Operation.CHECKONLY;
                        break;
                    case "--nocheck":
                        Song.check = true;
                        break;
                    case "--nointerpret":
                        nointerpret = true;
                        break;
                    case "--rmsource":
                        Song.delete = true;
                        break;
                    case "--dryrun":
                        PlaylistList.dryrun = true;
                        Playlist.dryrun = true;
                        Song.dryrun = true;
                        break;
                    case "--base":
                        if ((i >= args.length) || args[i].startsWith("--") || args[i].isBlank()) throw new RuntimeException("Please supply base folder");
                        root = Paths.get(args[i++]).toAbsolutePath();
                        if (!Files.isDirectory(root)) throw new RuntimeException("Base folder not found: " + root.toString());
                        break;
                    case "--map":
                        if ((i >= args.length) || args[i].startsWith("--") || args[i].isBlank()) throw new RuntimeException("Please supply mapping");
                        String[] parts = args[i++].split("=");
                        if ((parts.length > 2) || (parts[0].isEmpty())) throw new RuntimeException("Invalid mapping: " + Arrays.toString(parts));
                        from = parts[0];
                        if (parts.length > 1) into = Paths.get(parts[1]);
                        command = Operation.MAP;
                        break;
                    case "--type":
                        if ((i >= args.length) || args[i].startsWith("--") || args[i].isBlank()) throw new RuntimeException("Please supply type");
                        type = args[i++].toLowerCase();
                        break;
                    case "--select":
                        if ((i >= args.length) || args[i].startsWith("--") || args[i].isBlank()) throw new RuntimeException("Please supply search string");
                        search = args[i++];
                        command = Operation.SELECT;
                        break;
                    case "--union":
                        command = Operation.UNION;
                        break;
                    case "--add":
                    case "--remove":
                    case "--intersect":
                        if ((i >= args.length) || args[i].startsWith("--") || args[i].isBlank()) throw new RuntimeException("Please supply compare playlist");
                        into = Paths.get(args[i++]);
                        command = Operation.valueOf(option.substring(2).toUpperCase());
                        break;
                    case "--help":
                        System.err.println(usage());
                        System.exit(0);
                        break;
                    default:
                        throw new RuntimeException("Invalid option: " + option);
                }
            }

            switch(command) {
            case CHECKONLY:
                if (root == null) root = Paths.get("").toAbsolutePath();
                new PlaylistList(root, true, true);
                break;
            case SELECT: {
                if (i >= args.length) throw new RuntimeException("Please supply input playlist[s] or specify - for stdin");
                PlaylistList factory = args2Factory(Arrays.copyOfRange(args, i, args.length), root, type);
                if (root == null) root = factory.getBase();
                final String fsearch = search;
                Playlist result = Playlist.empty(System.out, root, type);
                result.add(
                    factory.stream()
                        .peek(list -> System.err.format("SONGBASE: Filter for '%s', %s\n", fsearch, list.getName()))
                        .flatMap(list -> list.select(fsearch))
                );
                result.write();
                break;
            }
            case ADD: {
                PlaylistList factory;
                if (i < args.length) {
                    factory = args2Factory(Arrays.copyOfRange(args, i, args.length), root, type);
                    if (root == null) root = factory.getBase();
                }
                else {
                    if (root == null) root = Paths.get("").toAbsolutePath();
                    factory = new PlaylistList(root, true, false);
                }
                Playlist that = Playlist.of(into, false);
                factory.removePlaylist(that);
                factory.stream()
                    .peek(list -> System.err.format("SONGBASE: Add %s to %s\n", that.getName(), list.getName()))
                    .forEach(list -> list.add(that.entries())
                );
                factory.update();
                break;
            }
            case REMOVE: {
                PlaylistList factory;
                if (i < args.length) {
                    factory = args2Factory(Arrays.copyOfRange(args, i, args.length), root, type);
                    if (root == null) root = factory.getBase();
                }
                else {
                    if (root == null) root = Paths.get("").toAbsolutePath();
                    factory = new PlaylistList(root, true, false);
                }
                Playlist that = Playlist.of(into, false);
                factory.removePlaylist(that);
                factory.stream()
                    .peek(list -> System.err.format("SONGBASE: Remove %s from %s\n", that.getName(), list.getName()))
                    .forEach(list -> list.remove(that.entries())
                );
                factory.update();
                break;
            }
            case UNION: {
                if (i >= args.length) throw new RuntimeException("Please supply input playlist or specify - for stdin");
                PlaylistList factory = args2Factory(Arrays.copyOfRange(args, i, args.length), root, type);
                if (root == null) root = factory.getBase();
                Playlist result = Playlist.empty(System.out, root, type);
                result.add(
                    factory.stream()
                        .peek(list -> System.err.format("SONGBASE: Add %s\n", list.getName()))
                        .flatMap(list -> list.entries())
                );
                result.write();
                break;
            }
            case INTERSECT: {
                if (i >= args.length) throw new RuntimeException("Please supply input playlist or specify - for stdin");
                Playlist thiz = arg2Playlist(args[i], root, type);
                if (root == null) root = thiz.getBase();
                Playlist that = Playlist.of(into, false);
                Playlist result = Playlist.empty(System.out, root, type);
                System.err.format("SONGBASE: Common songs of %s and %s\n", thiz.getName(), that.getName());
                result.add(
                    thiz.intersect(that)
                );
                result.write();
                break;
            }
            case MAP: {
                if (i >= args.length) throw new RuntimeException("Please supply input playlist or specify - for stdin");
                Playlist that = arg2Playlist(args[i], root, type);
                if (root == null) root = that.getBase();
                PlaylistList factory = new PlaylistList(root, true, false);

                Path base = that.getBase();
                if (from == null) from = "Neu";
                if (into != null) nointerpret = true;
                if (into == null) into = Paths.get("Rock");
                Path to = base.resolve(into);
                if (!Files.isDirectory(to)) throw new RuntimeException("To folder not found: " + to.toString());

                System.err.format("SONGBASE: Mapping '%s' -> '%s' based on %s\n", from.replaceAll("\\\\", "/"), into.toString().replaceAll("\\\\", "/"), that.getName());
                for (Playlist.Entry song : that.getEntries()) {
                    Path path = song.getFolder();
                    String interpret = song.getInterpret();
                    if (path.toString().equals(from)) {
                        Path newpath = base.resolve(interpret);
                        if (nointerpret || !Files.isDirectory(newpath)) {
                            newpath = to;
                        }
                        if (Files.isSameFile(path, newpath)) return;
                        Song dup = song.move(newpath);
                        if (dup != null) {
                            factory.move(song, dup);
                        }
                    }
                }
                factory.update();
                //if (that.isChanged()) that.write();
                break;
            }}
        }
        catch (Exception ex) {
            ex.printStackTrace(System.err);
        }
    }
}
