package oanavodo.songbase.playlist;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import oanavodo.songbase.Options;
import oanavodo.songbase.Options.Check;
import oanavodo.songbase.ShuffleList;
import oanavodo.songbase.Song;

/**
 * Represents a playlist.
 * A playlist can be read/written from/to a file or from/to an input/output stream.
 * @author Reiner
 */
public class Playlist {

    protected static Options options = new Options();

    public static void setOptions(Options options) {
        Playlist.options = options;
    }

    /**
     * Instantiates a playlist object from a file.
     * Changes will be written back to this file.
     * @param in path to file
     * @return
     */
    public static Playlist of(Path in) {
        PlaylistIO inio = PlaylistIO.of(in);
        return create(inio, PlaylistIO.of(in), inio.getPath().getParent());
    }

    /**
     * Instantiates a playlist object from a file.
     * Also a fake playlist for one song is allowed.
     * Changes will be written back to this file (if no fake).
     * @param in path to file
     * @return
     */
    public static Playlist ofPlaylistOrSong(Path in) {
        PlaylistIO inio = PlaylistIO.ofPlaylistOrSong(in);
        return create(inio, PlaylistIO.ofPlaylistOrSong(in), inio.getPath().getParent());
    }

    /**
     * Instantiates a playlist object from a file.
     * Changes will be written to an output stream.
     * @param in path to file
     * @param out where playlist is written after change
     * @param type playlist type. If null use type of input file
     * @return
     */
    public static Playlist of(Path in, OutputStream out, String type) {
        PlaylistIO inio = PlaylistIO.of(in);
        if (type == null) type = inio.getType();
        return create(inio, PlaylistIO.of(out, type), inio.getPath().getParent());
    }

    /**
     * Instantiates a playlist object from a file.
     * Changes will be written to an output file.
     * @param in path to file
     * @param out where playlist is written after change
     * @return
     */
    public static Playlist of(Path in, Path out) {
        PlaylistIO inio = PlaylistIO.of(in);
        return create(inio, PlaylistIO.of(out), inio.getPath().getParent());
    }

    /**
     * Instantiates a playlist object from input stream.
     * Changes are written to an output file.
     * @param in where the playlist is read in
     * @param out where playlist is written after change
     * @param parent base folder of the playlist
     * @param type playlist type
     * @return
     */
    public static Playlist of(InputStream in, Path out, Path parent, String type) {
        return create(PlaylistIO.of(in, type), PlaylistIO.of(out), parent);
    }

    /**
     * Instantiates a playlist object from input stream.
     * Changes are written to an output stream.
     * @param in where the playlist is read in
     * @param out where playlist is written after change
     * @param parent base folder of the playlist
     * @param type playlist type
     * @return
     */
    public static Playlist of(InputStream in, OutputStream out, Path parent, String type) {
        return create(PlaylistIO.of(in, type), PlaylistIO.of(out, type), parent);
    }

    /**
     * Instantiates an empty playlist object.
     * @param out where playlist is written after change
     * @param parent base folder of the playlist
     * @param type playlist type
     * @return
     */
    public static Playlist empty(OutputStream out, Path parent, String type) {
        return create(PlaylistIO.of(type), PlaylistIO.of(out, type), parent);
    }

    /**
     * Instantiates an empty playlist object.
     * @param out where playlist is written after change
     * @param parent base folder of the playlist
     * @return
     */
    public static Playlist empty(Path out, Path parent) {
        PlaylistIO outio = PlaylistIO.of(out);
        return create(PlaylistIO.of(outio.getType()), outio, parent);
    }

    private static Playlist create(PlaylistIO in, PlaylistIO out, Path parent) {
        parent = parent.normalize().toAbsolutePath();
        Playlist list = new Playlist(in, out, parent);
        if ((in.getPath() != null) && !Files.isRegularFile(in.getPath())) {
            throw new RuntimeException("Playlist not found: " + in.getPath().toString());
        }
        if (in.hasInput()) {
            try {
                if (!in.isOneSong()) System.err.format("PLAYLIST: reading %s\n", in.getName());
                in.fill(list.getInterface(null), (options.getCheck() == Check.ONLY));
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

    private Playlist(PlaylistIO in, PlaylistIO out, Path parent) {
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

    public int size() {
        return songs.size();
    }

    public void add(Stream<? extends Song> adds) {
        Stream<? extends Song> realadds = adds.filter(song -> songs.parallelStream().noneMatch(entry -> entry.equals(song)));
        realadds.forEachOrdered(song -> {
            try {
                Entry entry = new Entry(parent.relativize(song.getPath()), songs.size());
                songs.add(entry);
                changed = true;
                System.err.format("%s: + %s, %s\n", input.getName(), entry.getFolderString(), entry.getNameString());
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
            System.err.format("%s: - %s, %s\n", input.getName(), entry.getFolderString(), entry.getNameString());
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
                song.getFolderString().contains(search) ||
                song.getInterpret().contains(search) ||
                song.getTitle().contains(search)
            ));
    }

    public Stream<? extends Song> intersect(Playlist that) {
        return that.songs.stream().filter(ethat -> songs.parallelStream().anyMatch(ethis -> ethis.equals(ethat)));
    }

    public Stream<? extends Song> complement(Playlist that) {
        return that.songs.stream().filter(ethat -> songs.parallelStream().noneMatch(ethis -> ethis.equals(ethat)));
    }

    public void update(boolean sorted) {
        if (isChanged()) write(sorted);
    }

    private PlaylistInterface getInterface(Iterator<Entry> iterator) {
        return new PlaylistInterface() {
            @Override
            public EntryInterface createEntry(Path path) {
                return entryOf(path, size());
            }

            @Override
            public void addEntry(EntryInterface entry) {
                add((Entry)entry);
            }

            @Override
            public Iterator<? extends EntryInterface> getEntryIterator() {
                return (iterator != null) ? iterator : songs.iterator();
            }
        };
    }

    private class RebaseIterator implements Iterator<Entry> {
        private Path newbase;
        private Iterator<Entry> iter = songs.iterator();

        private RebaseIterator(Path newbase) {
            this.newbase = newbase;
        }

        @Override
        public boolean hasNext() {
            return iter.hasNext();
        }

        @Override
        public Entry next() {
            Entry rebased = new Entry(iter.next());
            rebased.rebase(newbase);
            return rebased;
        }
    }

    public void write(boolean sorted) {
        if (!output.hasOutput()) return;
        if (!output.isOneSong()) System.err.format("PLAYLIST: writing %s\n", output.getName());
        if (sorted) sort();
        try {
            if (!options.isDryrun() || (output.getOutput() != null)) {
                // output path may be based on another folder
                boolean rebase = ((output.getPath() != null) && !getBase().equals(output.getPath().getParent()));
                output.save(getInterface(rebase ? new RebaseIterator(output.getPath().getParent()) : null));
            }
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
        System.err.format("%s: = %s, %s\n", input.getName(), entry.getFolderString(), entry.getNameString());
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
    public class Entry extends Song implements EntryInterface {

        private Path relpath;
        private int index;

        private Entry(Path relfile, int index) {
            super(parent.resolve(relfile).normalize());
            this.relpath = relfile.getParent();
            this.index = index;
        }

        public Entry(Entry other) {
            super(other);
            this.relpath = other.relpath;
            this.index = other.index;
        }
        protected void rebase(Path newbase) {
            this.relpath = newbase.relativize(getPath()).getParent();
        }

        public Path getFolder() {
            return (relpath != null) ? relpath: Paths.get("");
        }

        @Override
        public String getFolderString() {
            return (relpath != null) ? relpath.toString().replace("\\", "/") : "";
        }

        @Override
        public String getNameString() {
            return getName().toString();
        }

        @Override
        public String getEntryString() {
            String entry = getFolderString();
            if (!entry.isEmpty()) entry += "/";
            entry += getNameString();
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
