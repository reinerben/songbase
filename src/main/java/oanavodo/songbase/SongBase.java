package oanavodo.songbase;


import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

public class SongBase {

    // --dryrun             only report what would be done
    // --check              only check if all songs exists
    // --nocheck            don't check if a song exists
    // --nointerpret        skip check for interpret folder
    // --rmsource           delete a source song if already exists in destination
    // --base <dir>         base folder for playlist factory
    // --map <a>=<b>    folder transfer a=b
    // --type <type>        type of stdin/stdout playlist (default: m3u)
    // --add <list>         write content of both to stdout
    // --remove <list>      write content differences to stdout
    // --intersect <list>   write content equals to stdout
    // --select <string>    write content filtered for string to stdout

    public static enum Operation { NONE, CHECKONLY, MAP, ADD, REMOVE, INTERSECT, SELECT };

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
                        root = Paths.get(args[i++]);
                        if (!Files.isDirectory(root)) throw new RuntimeException("Base folder not found: " + root.toAbsolutePath().toString());
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
                    case "--add":
                    case "--remove":
                    case "--intersect":
                        if ((i >= args.length) || args[i].startsWith("--") || args[i].isBlank()) throw new RuntimeException("Please supply compare playlist");
                        into = Paths.get(args[i++]);
                        command = Operation.valueOf(option.substring(2).toUpperCase());
                        break;
                    default:
                        throw new RuntimeException("Invalid option: " + option);
                }
            }

            switch(command) {
            case CHECKONLY:
                if (root == null) root = Paths.get("").toAbsolutePath();
                new PlaylistList(root, true);
                break;
            case SELECT: {
                if (i >= args.length) throw new RuntimeException("Please supply input playlist or specify - for stdin");
                Path inpath = !args[i].equals("-") ? Paths.get(args[i++]).toAbsolutePath() : null;
                if (root == null) {
                    root = (inpath != null) ? inpath.getParent() : Paths.get("").toAbsolutePath();
                }
                Playlist thisone = (inpath != null) ? Playlist.of(inpath, false) : Playlist.of(System.in, null, root, type, false);
                Playlist result = Playlist.empty(System.out, root, type);
                System.err.format("SONGBASE: Filter for '%s', %s\n", search, thisone.getName());
                thisone.select(search, result);
                result.write();
                break;
            }
            case ADD:
            case REMOVE:
            case INTERSECT: {
                if (i >= args.length) throw new RuntimeException("Please supply input playlist or specify - for stdin");
                Path inpath = !args[i].equals("-") ? Paths.get(args[i++]).toAbsolutePath() : null;
                if (root == null) {
                    root = (inpath != null) ? inpath.getParent() : Paths.get("").toAbsolutePath();
                }
                Playlist thatone = Playlist.of(into, false);
                Playlist thisone = (inpath != null) ? Playlist.of(inpath, false) : Playlist.of(System.in, null, root, type, false);
                Playlist result = Playlist.empty(System.out, root, type);
                switch(command) {
                case ADD:
                    System.err.format("SONGBASE: Combine %s, %s\n", thisone.getName(), thatone.getName());
                    thisone.union(thatone, result);
                    break;
                case REMOVE:
                    System.err.format("SONGBASE: Complement %s, %s\n", thisone.getName(), thatone.getName());
                    thisone.complement(thatone, result);
                    break;
                case INTERSECT:
                    System.err.format("SONGBASE: Intersect %s, %s\n", thisone.getName(), thatone.getName());
                    thisone.intersect(thatone, result);
                break;
                }
                result.write();
                break;
            }
            case MAP: {
                if (i >= args.length) throw new RuntimeException("Please supply input playlist or specify - for stdin");
                Path inpath = !args[i].equals("-") ? Paths.get(args[i++]).toAbsolutePath() : null;
                if (root == null) {
                    root = (inpath != null) ? inpath.getParent() : Paths.get("").toAbsolutePath();
                }
                PlaylistList factory = new PlaylistList(root, false);
                Playlist thisone = factory.getPlaylist(inpath);
                if (thisone == null) {
                    thisone = (inpath != null) ? Playlist.of(inpath, false) : Playlist.of(System.in, null, root, type, false);
                }

                Path base = thisone.getBase();
                if (from == null) from = "Neu";
                if (into != null) nointerpret = true;
                if (into == null) into = Paths.get("Rock");
                Path to = base.resolve(into);
                if (!Files.isDirectory(to)) throw new RuntimeException("To folder not found: " + to.toString());

                System.err.format("SONGBASE: Mapping '%s' -> '%s', %s\n", from.replaceAll("\\\\", "/"), into.toString().replaceAll("\\\\", "/"), thisone.getName());
                for (Playlist.Entry song : thisone.getEntries()) {
                    Path path = song.getFolder();
                    String interpret = song.getInterpret();
                    if (path.toString().equals(from)) {
                        Path newpath = base.resolve(interpret);
                        if (nointerpret || !Files.isDirectory(newpath)) {
                            newpath = to;
                        }
                        if (Files.isSameFile(path, newpath)) continue;
                        Song dup = song.move(newpath);
                        if (dup != null) {
                            factory.move(song, dup);
                        }
                    }
                }
                factory.update();
                if (thisone.isChanged()) thisone.write();
                break;
            }}
        }
        catch (Exception ex) {
            ex.printStackTrace(System.err);
        }
    }
}
