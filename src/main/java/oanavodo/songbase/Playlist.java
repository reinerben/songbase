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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
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

    protected Data data;
    protected final List<Entry> songs = new ArrayList<>();
    private boolean changed = false;

    protected Playlist(Data data, boolean onlycheck) throws IOException {
        this.data = data;
        if ((data.path != null) || (data.input != null)) {
            String descr = (data.path != null) ? data.path.toString() : data.name;
            System.err.format("PLAYLIST: reading %s\n", descr);
            fill(onlycheck);
        }
    }

    public static Playlist of(Path path, boolean onlycheck) {
        return Playlist.of(path, null, onlycheck);
    }

    public static Playlist of(Path path, OutputStream output, boolean onlycheck) {
        path = path.toAbsolutePath();
        if (!Files.isRegularFile(path)) throw new RuntimeException("Playlist not found: " + path.toString());
        String name = path.getFileName().toString();
        int end = name.lastIndexOf(".");
        if (end == -1) end = name.length() - 1;
        String type = name.substring(end + 1).toLowerCase();
        return create(new Data(path, path.getParent(), type, null, output), onlycheck);
    }

    public static Playlist of(InputStream input, OutputStream output, Path parent, String type, boolean onlycheck) {
        return create(new Data(null, parent, type, input, output), onlycheck);
    }

    public static Playlist empty(OutputStream output, Path parent, String type) {
        return create(new Data(null, parent, type, null, output), false);
    }

    public static boolean isSupported(Path path) {
        String name = path.getFileName().toString();
        return (name.endsWith(".m3u") || name.endsWith(".m3u8"));
    }

    private static Playlist create(Data data, boolean onlycheck) {
        try {
            switch(data.type) {
            case "m3u":
                return new M3u(data, onlycheck);
            case "m3u8":
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
                Entry entry = new Entry(data.parent.relativize(song.getPath()), songs.size(), Song.dryrun);
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
            if (!dryrun || (data.output != null)) save();
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
            songs.get(i).setIndex(i);
        }
        changed = true;
    }

    public void shuffle(int gap) {
        ShuffleList list = new ShuffleList(gap);
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
        Entry entry = new Entry(data.parent.relativize(path), index, Song.dryrun);
        songs.set(index, entry);
        System.err.format("%s: = %s, %s\n", data.name, entry.getFolder().toString().replace("\\", "/"), entry.getName());
        changed = true;
        return entry;
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

    private static class ShuffleList {

        private static class Group extends ArrayList<Entry> {
            private String name;
            private int wait = 0;

            private Group(String name) {
                super();
                this.name = name;
            }

            public String getName() {
                return name;
            }

            public boolean isBlocked() {
                return (wait > 0);
            }

            public int getBlocked() {
                return wait;
            }

            public void setBlocked(int amount) {
                wait = amount;
            }

            public void pass() {
                if (wait > 0) wait--;
            }
        }

        private Map<String, Group> base = new LinkedHashMap<>();
        private Random rand = new Random();
        private int same = 0;
        private int count = 0;
        private int gap = 0;
        private int maxgap;

        public ShuffleList(int gap) {
            this.maxgap = gap;
        }

        public boolean isEmpty() {
            return (count <= 0);
        }

        public void add(Entry entry) {
            Group group = base.get(entry.getInterpret());
            if (group == null) base.put(entry.getInterpret(), group = new Group(entry.getInterpret()));
            group.add(entry);
            count++;
            if (same < group.size()) same = group.size();
            gap = Math.min(maxgap, (count / same) - 1);
        }

        public Entry getNext() {
//            int real = base.values().stream().mapToInt(group -> group.size()).sum();
//            int blocks = base.values().stream().mapToInt(group -> group.isBlocked() ? 1 : 0).sum();
//            System.err.format("count %d, real %d, blocks %d/%d gap %d\n", count, real, base.keySet().size(), blocks, gap);
            if (count <= 0) return null;
            int index = rand.nextInt(count);
            Group found = null;
            Group alter = null;
            Group last = null;
            int pointer = 0;
            int offset = -1;
            for (Group group : base.values()) {
                int next = pointer + group.size();
                int min = ((group.size() - 1) * gap) + group.size();
                if (min >= count) {
                    found = group;
                    offset = -1;
                }
                if (group.isBlocked()) {
                    if ((alter == null) || (alter.getBlocked() > group.getBlocked())) alter = group;
                }
                else {
                    last = group;
                    if ((found == null) && (index < next)) {
                        found = group;
                        offset = index - pointer;
                    }
                }
                group.pass();
                pointer = next;
            }
            if (found == null) found = last;
            if (found == null) found = alter;
            if (found == null) return null;
            if (offset < 0) offset = rand.nextInt(found.size());
            Entry next = found.remove(offset);
            found.setBlocked(gap);
            if (found.isEmpty()) base.remove(found.getName());
            count--;
            return next;
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
