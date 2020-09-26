package oanavodo.songbase;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import oanavodo.songbase.Options.Check;

/**
 * Represents a playlist.
 * Since there are several playlist formats this is a abstract base class and have to be subclassed
 * by specific playlist type classes which implements fill and save methods.
 * A playlist can be read/written from/to a file or to an input/output stream.
 * @author Reiner
 */
public class Playlist2 {

    protected static Options options = new Options();

    public static void setOptions(Options options) {
        Playlist2.options = options;
    }

    /**
     * Instantiates a playlist object from a file.Changes will be written back to this file.
     * @param in path to file
     * @return
     */
    public static Playlist2 of(Path in) {
        PlaylistIO inio = PlaylistIO.of(in);
        return create(inio, PlaylistIO.of(in), inio.getPath().getParent());
    }

    /**
     * Instantiates a playlist object from a file.Changes will be written to an output stream.
     * @param in path to file
     * @param out where playlist is written after change
     * @return
     */
    public static Playlist2 of(Path in, OutputStream out) {
        PlaylistIO inio = PlaylistIO.of(in);
        return create(inio, PlaylistIO.of(out, inio.getType()), inio.getPath().getParent());
    }

    /**
     * Instantiates a playlist object from a file.Changes will be written to an output file.
     * @param in path to file
     * @param out where playlist is written after change
     * @return
     */
    public static Playlist2 of(Path in, Path out) {
        PlaylistIO inio = PlaylistIO.of(in);
        return create(inio, PlaylistIO.of(out), inio.getPath().getParent());
    }

    /**
     * Instantiates a playlist object from input stream.Changes are written to an output file.
     * @param in where the playlist is read in
     * @param out where playlist is written after change
     * @param parent base folder of the playlist
     * @param type playlist type
     * @return
     */
    public static Playlist2 of(InputStream in, Path out, Path parent, String type) {
        return create(PlaylistIO.of(in, type), PlaylistIO.of(out), parent);
    }

    /**
     * Instantiates a playlist object from input stream.Changes are written to an output stream.
     * @param in where the playlist is read in
     * @param out where playlist is written after change
     * @param parent base folder of the playlist
     * @param type playlist type
     * @return
     */
    public static Playlist2 of(InputStream in, OutputStream out, Path parent, String type) {
        return create(PlaylistIO.of(in, type), PlaylistIO.of(out, type), parent);
    }

    /**
     * Instantiates an empty playlist object.
     * @param out where playlist is written after change
     * @param parent base folder of the playlist
     * @param type playlist type
     * @return
     */
    public static Playlist2 empty(OutputStream out, Path parent, String type) {
        return create(PlaylistIO.of(type), PlaylistIO.of(out, type), parent);
    }

    /**
     * Instantiates an empty playlist object.
     * @param out where playlist is written after change
     * @param parent base folder of the playlist
     * @param type playlist type
     * @return
     */
    public static Playlist2 empty(Path out, Path parent, String type) {
        return create(PlaylistIO.of(type), PlaylistIO.of(out), parent);
    }

    private static Playlist2 create(PlaylistIO in, PlaylistIO out, Path parent) {
        Playlist2 list = new Playlist2(in, out, parent);
        if ((in.getPath() != null) && !Files.isRegularFile(in.getPath())) {
            throw new RuntimeException("Playlist not found: " + in.getPath().toString());
        }
        if (in.hasInput()) {
            try {
                System.err.format("PLAYLIST: reading %s\n", in.getName());
                in.fill(list, (options.getCheck() == Check.ONLY));
            }
            catch (IOException ex) {
                throw new RuntimeException(ex.getMessage(), ex.getCause());
            }
        }
        return list;
    }

    private PlaylistIO input;
    private PlaylistIO output;
    private Path parent;
    private final List<Entry> songs = new ArrayList<>();
    private boolean changed = false;

    private Playlist2(PlaylistIO in, PlaylistIO out, Path parent) {
        this.input = in;
        this.output = out;
        this.parent = parent;
    }

    public boolean isStdio() {
        return ((input.getInput() != null) || (output.getOutput() != null));
    }

    public boolean isOutio() {
        return ((output.getPath() != null) && !input.sameIO(output));
    }

    public Path getBase() {
        return parent;
    }

    public Path getPath() {
        return input.getPath();
    }

    public String getName() {
        return input.getName();
    }

    public List<Entry> getEntries() {
        return songs;
    }

    public Stream<Entry> entries() {
        return songs.stream();
    }

    public boolean isChanged() {
        return changed;
    }

    public void add(Stream<? extends Song> adds) {
        Stream<? extends Song> realadds = adds.filter(song -> songs.parallelStream().noneMatch(entry -> entry.equals(song)));
        realadds.forEachOrdered(song -> {
            try {
                Entry entry = new Entry(parent.relativize(song.getPath()), songs.size());
                songs.add(entry);
                changed = true;
                System.err.format("%s: + %s, %s\n", input.getName(), entry.getFolder(), entry.getName());
            }
            catch (IllegalArgumentException ex) {
                throw new RuntimeException("Song is outside of playlist base: " + song.getPath());
            }
        });
    }

    public void add(Song song) {
        add(Stream.of(song));
    }

    void add(Entry entry) {
        songs.add(entry);
    }

    public void remove(Stream<? extends Song> rems) {
        Stream<Integer> realrems = rems.parallel()
            .map(song -> songs.parallelStream().filter(entry -> entry.equals(song)).map(entry -> entry.getIndex()).findAny().orElse(-1))
            .filter(index -> (index != -1))
            .sorted();
        AtomicInteger offset = new AtomicInteger(0);
        AtomicInteger lowest = new AtomicInteger(Integer.MAX_VALUE);
        realrems.forEachOrdered(index -> {
            index -= offset.get();
            Entry entry = songs.remove((int)index);
            if (index < lowest.get()) lowest.set(index);
            offset.incrementAndGet();
            changed = true;
            System.err.format("%s: - %s, %s\n", input.getName(), entry.getFolder(), entry.getName());
        });
        if (lowest.get() < Integer.MAX_VALUE) {
            for (int i = lowest.get(); i < songs.size(); i++) {
                songs.get(i).setIndex(i);
            }
        }
    }

    public void remove(Song song) {
        remove(Stream.of(song));
    }

    public void move(Song prev, Song now) {
        songs.parallelStream()
            .filter(song -> song.equals(prev))
            .forEach((song) -> {
                setSong(song.getIndex(), now.getPath());
            });
    }

    public Stream<? extends Song> select(String search) {
        return songs.stream()
            .filter(song -> (
                song.getFolder().contains(search) ||
                song.getInterpret().contains(search) ||
                song.getTitle().contains(search)
            ));
    }

    public Stream<? extends Song> intersect(Playlist2 that) {
        return that.songs.stream().filter(ethat -> songs.parallelStream().anyMatch(ethis -> ethis.equals(ethat)));
    }

    public Stream<? extends Song> complement(Playlist2 that) {
        return that.songs.stream().filter(ethat -> songs.parallelStream().noneMatch(ethis -> ethis.equals(ethat)));
    }

    public void update(boolean sorted) {
        if (isChanged()) write(sorted);
    }

    public void write(boolean sorted) {
        if (!output.hasOutput()) return;
        System.err.format("PLAYLIST: writing %s\n", output.getName());
        if (sorted) sort();
        try {
            if (!options.isDryrun() || (output.getOutput() != null)) output.save(this);
            changed = false;
        }
        catch (RuntimeException ex) {
            throw ex;
        }
        catch (Exception ex) {
            throw new RuntimeException(ex.getMessage(), ex.getCause());
        }
    }

    public void sort() {
        songs.sort(Comparator.naturalOrder());
        for (int i = 0; i < songs.size(); i++) {
            Entry song = songs.get(i);
            if (song.getIndex() != i) {
                song.setIndex(i);
                changed = true;
            }
        }
    }

    public void shuffle(int gap) {
        ShuffleList<Entry> list = new ShuffleList(gap);
        songs.forEach(song -> list.add(song));
        songs.clear();
        while (!list.isEmpty()) {
            Entry entry = list.getNext();
            entry.setIndex(songs.size());
            songs.add(entry);
        }
        changed = true;
    }

    private Entry setSong(int index, Path path) {
        Entry entry = new Entry(parent.relativize(path), index);
        songs.set(index, entry);
        System.err.format("%s: = %s, %s\n", input.getName(), entry.getFolder(), entry.getName());
        changed = true;
        return entry;
    }

    Entry entryOf(Path relfile, int index) {
        return new Entry(relfile, index);
    }

    /**
     * Inner class with represents a song.
     * Holds position and the path relative to the playlist location.
     */
    public class Entry extends Song {

        private Path relpath;
        private int index;

        private Entry(Path relfile, int index) {
            super(parent.resolve(relfile));
            this.relpath = relfile.getParent();
            this.index = index;
        }

        public String getFolder() {
            return (relpath != null) ? relpath.toString().replace("\\", "/") : "";
        }

        public String getEntry() {
            String entry = getFolder();
            if (!entry.isEmpty()) entry += "/";
            entry += getName().toString();
            return entry;
        }

        @Override
        public Song move(Path newpath, boolean delete) {
            Path newfile = moveIntern(newpath, delete);
            if (newfile == null) return null;
            return setSong(index, newfile);
        }

        public int getIndex() {
            return index;
        }

        private void setIndex(int index) {
            this.index = index;
        }

        @Override
        public int compareTo(Song other) {
            return getPath().toString().compareToIgnoreCase(((Entry)other).getPath().toString());
        }
    }
}
