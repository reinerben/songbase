package oanavodo.songbase;


import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

public class PlaylistList {
    public static boolean dryrun = false;

    private Map<Path, Playlist> lists;
    private Path base;

    public PlaylistList(Path base, boolean walk, boolean onlycheck) {
        this.lists = new HashMap<>();
        this.base = (base != null) ? base.toAbsolutePath() : null;
        if (walk && (base != null)) {
            try {
                if (!Files.isDirectory(this.base)) throw new RuntimeException("Folder not found: " + this.base.toString());
                Files.walk(this.base).filter(path -> Playlist.isSupported(path)).forEach(path -> {
                    Playlist list = Playlist.of(path, onlycheck);
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
        return lists.containsKey(null);
    }

    public Path getBase() {
        return base;
    }

    public Playlist getPlaylist(Path path) {
        return lists.get(path);
    }

    public void addPlaylist(Playlist list) {
        lists.put(list.getPath(), list);
        if (base == null) base = list.getBase();
    }

    public void removePlaylist(Playlist list) {
        lists.remove(list.getPath());
    }

    public Stream<Playlist> stream() {
        return lists.values().stream();
    }

    public void move(Song prev, Song now) {
        lists.values().forEach(list -> list.move(prev, now));
    }

    public void update() {
        lists.values().stream().filter(list -> list.isChanged()).forEach(list -> list.write());
    }
}
