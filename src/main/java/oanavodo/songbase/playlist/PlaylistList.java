package oanavodo.songbase.playlist;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import oanavodo.songbase.Options;
import oanavodo.songbase.Song;

/**
 * Represents a (possibly empty) list of playlists.
 * It allows to update a change in song name or path in all affected playlists.
 * @author Reiner
 */
public class PlaylistList {

    protected static Options options = new Options();

    public static void setOptions(Options options) {
        PlaylistList.options = options;
    }
    private Map<Path, Playlist> lists;
    private Path base;

    /**
     * Instantiates a playlist factory.
     * If walk is true all playlists found beyond the base folder are added to this factory.
     * If walk is false an empty factory is created. Playlists has to be added.
     * @param base base folder of factory
     * @param walk whether playlists should be added
     */
    public PlaylistList(Path base, boolean walk) {
        this.lists = new HashMap<>();
        this.base = (base != null) ? base.toAbsolutePath() : null;
        if (walk && (base != null)) {
            try {
                if (!Files.isDirectory(this.base)) throw new RuntimeException("Folder not found: " + this.base.toString());
                Files.walk(this.base).filter(path -> PlaylistIO.isSupported(path)).forEach(path -> {
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

    public void update(boolean sorted) {
        lists.values().forEach(list -> list.update(sorted));
    }
}
