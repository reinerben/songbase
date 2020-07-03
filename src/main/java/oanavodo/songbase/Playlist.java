package oanavodo.songbase;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
public abstract class Playlist {

    @FunctionalInterface
    public interface IOFunction<T, R> {
        R apply(T arg) throws IOException;
    }

    /**
     * Encapsulated playlist members.
     * Why? subclass constructors need not to be changed if arguments change
     */
    protected static class Data {
        protected Path path;
        protected Path parent;
        protected String name;
        protected String type;
        protected InputStream input;
        protected OutputStream output;

        public Data(Path path, Path parent, String type, InputStream input, OutputStream output) {
            this.path = path;
            this.parent = parent;
            this.type = type;
            this.input = input;
            this.output = output;

            if (path != null) {
                name = path.getFileName().toString();
            }
            else if (input != null) {
                name = "<stdin>";
            }
            else {
                name = "<stdout>";
            }
        }
    }

    protected static Options options = new Options();

    public static void setOptions(Options options) {
        Playlist.options = options;
    }
    
    public static Playlist of(Path path) {
        return Playlist.of(path, null);
    }

    public static Playlist of(Path path, OutputStream output) {
        path = path.toAbsolutePath();
        if (!Files.isRegularFile(path)) throw new RuntimeException("Playlist not found: " + path.toString());
        String name = path.getFileName().toString();
        int end = name.lastIndexOf(".");
        if (end == -1) end = name.length() - 1;
        String type = name.substring(end + 1).toLowerCase();
        return create(new Data(path, path.getParent(), type, null, output));
    }

    public static Playlist of(InputStream input, OutputStream output, Path parent, String type) {
        return create(new Data(null, parent, type, input, output));
    }

    public static Playlist empty(OutputStream output, Path parent, String type) {
        return create(new Data(null, parent, type, null, output));
    }

    /* used for creation and support check */
    private static IOFunction<Data, Playlist> getCreater(String type) {
        switch(type) {
        case "m3u":
            return (data) -> new M3u(data);
        case "m3u8":
            return (data) -> new M3u8(data);
        default:
            return null;
        }
    }

    private static Playlist create(Data data) {
        IOFunction<Data, Playlist> creater = getCreater(data.type);
        if (creater == null) throw new RuntimeException("Playlist type not supported: " + data.type);
        try {
            return creater.apply(data);
        }
        catch (IOException ex) {
            throw new RuntimeException(ex.getMessage(), ex.getCause());
        }
    }

    public static boolean isSupported(Path path) {
        String name = path.getFileName().toString();
        int pos = name.lastIndexOf('.');
        if (pos > 0) name = name.substring(pos + 1);
        return (getCreater(name) != null);
    }

    protected Data data;
    protected final List<Entry> songs = new ArrayList<>();
    private boolean changed = false;

    protected Playlist(Data data) throws IOException {
        this.data = data;
        if ((data.path != null) || (data.input != null)) {
            String descr = (data.path != null) ? data.path.toString() : data.name;
            System.err.format("PLAYLIST: reading %s\n", descr);
            fill(options.check == Check.ONLY);
        }
    }

    protected abstract void fill(boolean onlycheck) throws IOException;

    protected abstract void save() throws IOException;

    public boolean isStdio() {
        return (data.output != null);
    }

    public Path getBase() {
        return data.parent;
    }

    public Path getPath() {
        return data.path;
    }

    public String getName() {
        return data.name;
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
                Entry entry = new Entry(data.parent.relativize(song.getPath()), songs.size());
                songs.add(entry);
                changed = true;
                System.err.format("%s: + %s, %s\n", data.name, entry.getFolder().toString().replace("\\", "/"), entry.getName());
            }
            catch (IllegalArgumentException ex) {
                throw new RuntimeException("Song is outside of playlist base: " + song.getPath());
            }
        });
    }

    public void add(Song song) {
        add(Stream.of(song));
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
            System.err.format("%s: - %s, %s\n", data.name, entry.getFolder().toString().replace("\\", "/"), entry.getName());
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
                song.getFolder().toString().contains(search) ||
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

    public void write(boolean sorted) {
        if ((data.path == null) && (data.output == null)) return;
        String descr = (data.output != null) ? "<stdout>" : data.name;
        System.err.format("PLAYLIST: writing %s\n", descr);
        if (sorted) sort();
        try {
            if (!options.dryrun || (data.output != null)) save();
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
        Entry entry = new Entry(data.parent.relativize(path), index);
        songs.set(index, entry);
        System.err.format("%s: = %s, %s\n", data.name, entry.getFolder().toString().replace("\\", "/"), entry.getName());
        changed = true;
        return entry;
    }

    /**
     * Inner class with represents a song.
     * Holds position and the path relative to the playlist location.
     */
    public class Entry extends Song {

        private Path relpath;
        private int index;

        private Entry(Path relfile, int index) {
            super(data.parent.resolve(relfile));
            this.relpath = relfile.getParent();
            this.index = index;
        }

        public Path getFolder() {
            return relpath;
        }

        public String getEntry() {
            String entry = relpath.toString().replace("\\", "/");
            entry += "/";
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

    /**
     * Intern subclass represents m3u (iso8859-1) playlists.
     * Currently #EXT lines are not supported
     */
    private static class M3u extends Playlist {

        protected M3u(Data data) throws IOException {
            super(data);
        }

        @Override
        protected void fill(boolean onlycheck) throws IOException {
            fillwithcs(StandardCharsets.ISO_8859_1, onlycheck);
        }

        protected void fillwithcs(Charset cs, boolean onlycheck) throws IOException {
            AtomicInteger count = new AtomicInteger(0);
            Reader backend = (data.input == null) ? new FileReader(data.path.toFile(), cs) : new InputStreamReader(data.input, cs);
            BufferedReader reader = new BufferedReader(backend);
            try (reader) {
                reader.lines().forEach(line -> {
                    line = line.trim();
                    if (line.isEmpty()) return;
                    if (line.startsWith("#")) return;
                    line = line.replace("\\", "/");
                    line = line.replace("%20", " ");
                    try {
                        int c = count.getAndIncrement();
                        songs.add(new Entry(Paths.get(line), c));
                    }
                    catch (Exception ex) {
                        if (!onlycheck) throw ex;
                        System.err.println(ex.getMessage());
                    }
                });
            }
        }

        @Override
        protected void save() throws IOException {
            savewithcs(StandardCharsets.ISO_8859_1) ;
        }

        protected void savewithcs(Charset cs) throws IOException {
            PrintWriter out = (data.output == null) ? new PrintWriter(data.path.toFile(), cs) : new PrintWriter(data.output, true, cs);
            try (out) {
                songs.forEach(song -> out.println(song.getEntry()));
            }

        }
    }

    /**
     * Intern subclass represents m3u8 (utt-8) playlists.
     * Currently #EXT lines are not supported
     */
    private static class M3u8 extends M3u {

        protected M3u8(Data data) throws IOException {
            super(data);
        }

        @Override
        protected void fill(boolean onlycheck) throws IOException {
            fillwithcs(StandardCharsets.UTF_8, onlycheck);
        }

        @Override
        protected void save() throws IOException {
            savewithcs(StandardCharsets.UTF_8) ;
        }
    }
}
