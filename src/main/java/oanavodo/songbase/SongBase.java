package oanavodo.songbase;


import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

public class SongBase {
    public static boolean nointerpret = false;

    // --dryrun      only report what would be done
    // --check       only check if all songs exists
    // --nocheck     don't check if a song exists
    // --nointerpret skip check for interpret folder
    // --rmsource    delete a source song if already exists in destination
    // --base        base folder for playlist factory
    // --mapping     folder transfer a=b

    public static void main(String[] args) {

        try {
            boolean onlycheck = false;
            Path root = null;
            String from = null;
            Path into = null;
            int i = 0;
            while (i < args.length) {
                if (!args[i].startsWith("--")) break;
                String option = args[i++];
                switch(option) {
                    case "--check":
                        onlycheck = true;
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
                        if ((i >= args.length) || args[i].startsWith("--")) throw new RuntimeException("Please supply base folder");
                        root = Paths.get(args[i++]);
                        if (!Files.isDirectory(root)) throw new RuntimeException("Base folder not found: " + root.toAbsolutePath().toString());
                        break;
                    case "--mapping":
                        if ((i >= args.length) || args[i].startsWith("--")) throw new RuntimeException("Please supply mapping");
                        String[] parts = args[i++].split("=");
                        if ((parts.length > 2) || (parts[0].isEmpty())) throw new RuntimeException("Invalid mapping: " + Arrays.toString(parts));
                        from = parts[0];
                        if (parts.length > 1) into = Paths.get(parts[1]);
                        break;
                    default:
                        throw new RuntimeException("Invalid option: " + option);
                }
            }

            if (onlycheck) {
                if (root == null) root = Paths.get("").toAbsolutePath();
                new PlaylistList(root, true);
                System.exit(0);
            }

            if (i >= args.length) throw new RuntimeException("Please supply playlist");
            Path inpath = Paths.get(args[i++]);
            if (!inpath.toString().endsWith(".m3u")) {
                throw new RuntimeException("Specified file is no m3u playlist: " + inpath.toAbsolutePath().toString());
            }
            inpath = inpath.toAbsolutePath();
            if (!Files.isRegularFile(inpath)) {
                throw new RuntimeException("Playlist not found: " + inpath.toString());
            }

            if (root == null) root = inpath.getParent();
            PlaylistList factory = new PlaylistList(root, false);
            Playlist list = factory.getPlaylist(inpath);
            if (list == null) list = new Playlist(inpath, false);

            Path base = list.getBase();
            if (from == null) from = "Neu";
            if (into == null) into = Paths.get("Rock");
            Path to = base.resolve(into);
            if (!Files.isDirectory(to)) throw new RuntimeException("To folder not found: " + to.toString());

            System.err.format("SONGBASE: Mapping from '%s' to '%s'\n", from.replaceAll("\\\\", "/"), into.toString().replaceAll("\\\\", "/"));
            for (Playlist.Entry song : list.getEntries()) {
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
            if (list.isChanged()) list.write();
        }
        catch (Exception ex) {
            ex.printStackTrace(System.err);
        }
    }
}
