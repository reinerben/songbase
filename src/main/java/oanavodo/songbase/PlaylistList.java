package oanavodo.songbase;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

public class PlaylistList {

    protected static Options options = new Options();

    public static void setOptions(Options options) {
        PlaylistList.options = options;
    }
    private Map<Path, Playlist> lists;
    private Path base;
    private int stdios = 0;

    public PlaylistList(Path base, boolean walk) {
        this.lists = new HashMap<>();
        this.base = (base != null) ? base.toAbsolutePath() : null;
        if (walk && (base != null)) {
            try {
                if (!Files.isDirectory(this.base)) throw new RuntimeException("Folder not found: " + this.base.toString());
                Files.walk(this.base).filter(path -> Playlist.isSupported(path)).forEach(path -> {
                    Playlist list = Playlist.of(path);
                    lists.put(list.getPath(), list);
                });
            }
            catch (RuntimeException ex) {
                throw ex;
            }
            catch (Exception ex) {
                throw new RuntimeException(ex.getMessage(), ex.getCause());
            }
        }
    }

    public boolean hasStdio() {
        return (stdios > 0);
    }

    public Path getBase() {
        return base;
    }

    public Playlist getPlaylist(Path path) {
        return lists.get(path);
    }

    public void addPlaylist(Playlist list) {
        lists.put(list.getPath(), list);
        if (list.isStdio()) stdios++;
        if (base == null) base = list.getBase();
    }

    public void removePlaylist(Playlist list) {
        Playlist that = lists.remove(list.getPath());
        if (that.isStdio() && (stdios > 0)) stdios--;
    }

    public Stream<Playlist> stream() {
        return lists.values().stream();
    }

    public void move(Song prev, Song now) {
        lists.values().forEach(list -> list.move(prev, now));
    }

    public void update(boolean sorted) {
        lists.values().stream().filter(list -> list.isChanged()).forEach(list -> list.write(sorted));
    }
}
