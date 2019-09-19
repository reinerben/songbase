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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public abstract class Playlist {
    public static boolean dryrun = false;

    protected static class Data {
        protected Path path;
        protected Path parent;
        protected String name;
        protected String type;
        protected InputStream input;
        protected OutputStream output;

        public Data(Path path, Path parent, String name, String type, InputStream input, OutputStream output) {
            this.path = path;
            this.parent = parent;
            this.name = name;
            this.type = type;
            this.input = input;
            this.output = output;
        }
    }

    protected Data data;
    protected final List<Entry> songs = new ArrayList<>();
    private boolean changed = false;
    private boolean sorted = false;

    protected Playlist(Data data, boolean onlycheck) throws IOException {
        this.data = data;
        if ((data.path != null) || (data.input != null)) fill(onlycheck);
        sort();
    }

    public static Playlist of(Path path, boolean onlycheck) {
        if (!Files.isRegularFile(path)) throw new RuntimeException("Playlist not found: " + path.toAbsolutePath().toString());
        String name = path.getFileName().toString();
        int end = name.lastIndexOf(".");
        if (end == -1) end = name.length() - 1;
        String type = name.substring(end + 1).toLowerCase();
        return create(new Data(path, path.getParent(), name, type, null, null), onlycheck);
    }

    public static Playlist of(InputStream input, OutputStream output, Path parent, String type, boolean onlycheck) {
        return create(new Data(null, parent, "<stdin>", type, input, output), onlycheck);
    }

    public static Playlist empty(OutputStream output, Path parent, String type) {
        return create(new Data(null, parent, "<new>", type, null, output), false);
    }

    private static Playlist create(Data data, boolean onlycheck) {
        try {
            String descr = (data.path != null) ? data.path.toString() : data.name;
            switch(data.type) {
            case "m3u":
                System.err.format("PLAYLIST: reading %s\n", descr);
                return new M3u(data, onlycheck);
            case "m3u8":
                System.err.format("PLAYLIST: reading %s\n", descr);
                return new M3u8(data, onlycheck);
            default:
                throw new RuntimeException("Playlist type not supported: " + data.type);
            }
        }
        catch (IOException ex) {
            throw new RuntimeException(ex.getMessage(), ex.getCause());
        }
    }

    protected abstract void fill(boolean onlycheck) throws IOException;

    protected abstract void save() throws IOException;

    public Path getBase() {
        return data.parent;
    }

    public List<Entry> getEntries() {
        return songs;
    }

    public boolean isChanged() {
        return changed;
    }

    public void add(Stream<? extends Song> adds) {
        sort();
        AtomicInteger lowest = new AtomicInteger(Integer.MAX_VALUE);
        adds.forEach(song -> {
            int index = Collections.binarySearch(songs, song);
            if (index >= 0) return;
            index = -index - 1;
            Entry entry = new Entry(data.parent.relativize(song.getPath()), index, Song.dryrun);
            songs.add(index, entry);
            if (index < lowest.get()) lowest.set(index);
            System.err.format("%s: +%s\n", data.name, entry.getEntry());
        });
        if (lowest.get() < Integer.MAX_VALUE) {
            for (int i = lowest.get() + 1; i < songs.size(); i++) {
                songs.get(i).setIndex(i);
            }
            changed = true;
        }
    }

    public void add(Song song) {
        add(Stream.of(song));
    }

    public void remove(Stream<? extends Song> rems) {
        sort();
        AtomicInteger lowest = new AtomicInteger(Integer.MAX_VALUE);
        rems.forEach(song -> {
            int index = Collections.binarySearch(songs, song);
            if (index < 0) return;
            Entry entry = songs.remove(index);
            if (index < lowest.get()) lowest.set(index);
            System.err.format("%s: -%s\n", data.name, entry.getEntry());
        });
        if (lowest.get() < Integer.MAX_VALUE) {
            for (int i = lowest.get(); i < songs.size(); i++) {
                songs.get(i).setIndex(i);
            }
            changed = true;
        }
    }

    public void remove(Song song) {
        remove(Stream.of(song));
    }

    public void move(Song prev, Song now) {
        songs.stream().filter(song -> song.equals(prev)).forEach((song) -> {
            setSong(song.getIndex(), now.getPath());
        });
    }

    public void union(Playlist that, Playlist result) {
        result.add(Stream.concat(songs.stream(), that.songs.stream()));
        result.sorted = false;
        result.changed = true;
    }

    public void intersect(Playlist that, Playlist result) {
        sort();
        result.add(that.songs.stream().filter(song -> (Collections.binarySearch(songs, song) >= 0)));
        result.sorted = false;
        result.changed = true;
    }

    public void complement(Playlist that, Playlist result) {
        sort();
        result.add(that.songs.stream().filter(song -> (Collections.binarySearch(songs, song) < 0)));
        result.sorted = false;
        result.changed = true;
    }

    public void write() {
        if ((data.path == null) && (data.output == null)) return;
        System.err.format("PLAYLIST: writing %s\n", data.path.toString());
        if (dryrun) return;
        try {
            sort();
            save();
            changed = false;
        }
        catch (RuntimeException ex) {
            throw ex;
        }
        catch (Exception ex) {
            throw new RuntimeException(ex.getMessage(), ex.getCause());
        }
    }

    private Entry setSong(int index, Path path) {
        Entry entry = new Entry(data.parent.relativize(path), index, Song.dryrun);
        songs.set(index, entry);
        System.err.format("%s: =%s\n", data.name, entry.getEntry());
        changed = true;
        sorted = false;
        return entry;
    }

    private void sort() {
        if (sorted) return;
        songs.sort(Comparator.naturalOrder());
        for (int i = 0; i < songs.size(); i++) {
            songs.get(i).setIndex(i);
        }
        sorted = true;
    }

    public class Entry extends Song {

        private Path relpath;
        private int index;

        private Entry(Path relfile, int index, boolean notcheck) {
            super(data.parent.resolve(relfile), notcheck);
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
        public Song move(Path newpath) {
            Path newfile = moveIntern(newpath);
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

    private static class M3u extends Playlist {

        protected M3u(Data data, boolean onlycheck) throws IOException {
            super(data, onlycheck);
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
                    try {
                        int c = count.getAndIncrement();
                        songs.add(new Entry(Paths.get(line), c, false));
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

    private static class M3u8 extends M3u {

        protected M3u8(Data data, boolean onlycheck) throws IOException {
            super(data, onlycheck);
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
