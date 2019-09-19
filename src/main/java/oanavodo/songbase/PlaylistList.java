package oanavodo.songbase;


import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class PlaylistList {
    public static boolean dryrun = false;

    private Map<Path, Playlist> lists;

    public PlaylistList(Path base, boolean onlycheck) {
        this.lists = new HashMap<>();
        try {
            if (!Files.isDirectory(base)) throw new RuntimeException("Folder not found: " + base.toAbsolutePath().toString());
            Files.walk(base).filter(path -> path.getFileName().toString().endsWith(".m3u")).forEach(path -> {
                lists.put(path, Playlist.of(path, onlycheck));
            });
        }
        catch (RuntimeException ex) {
            throw ex;
        }
        catch (Exception ex) {
            throw new RuntimeException(ex.getMessage(), ex.getCause());
        }
    }

    public Playlist getPlaylist(Path path) {
        return lists.get(path);
    }

    public void move(Song prev, Song now) {
        lists.values().forEach(list -> list.move(prev, now));
    }

    public void update() {
        lists.values().stream().filter(list -> list.isChanged()).forEach(list -> list.write());
    }
}
