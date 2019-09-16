package oanavodo.songbase;


import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class Playlist {
    public static boolean dryrun = false;

    private Path path;
    private Path parent;
    private String name;
    private final List<Entry> songs = new ArrayList<>();
    private boolean changed = false;

    public Playlist(Path path, boolean onlycheck) {
        this.path = path;
        this.parent = path.getParent();
        this.name = path.getFileName().toString();
        try {
            if (!Files.isRegularFile(path)) throw new RuntimeException("Playlist not found: " + path.toAbsolutePath().toString());
            String name = path.toString();
            int end = name.lastIndexOf(".");
            if (end == -1) end = name.length();
            if (!name.endsWith(".m3u")) throw new RuntimeException("Playlist type not supported: " + name.substring(end));
            System.err.format("PLAYLIST: reading %s\n", path.toString());
            fill(new BufferedReader(new InputStreamReader(new FileInputStream(path.toFile()), StandardCharsets.ISO_8859_1)), onlycheck);
        }
        catch (RuntimeException ex) {
            throw ex;
        }
        catch (Exception ex) {
            throw new RuntimeException(ex.getMessage(), ex.getCause());
        }
    }

    public Playlist(InputStream input, Path parent, boolean onlycheck) {
        this.path = null;
        this.parent = parent;
        try {
            System.err.format("PLAYLIST: reading <stdin>\n");
            fill(new BufferedReader(new InputStreamReader(input, StandardCharsets.ISO_8859_1)), onlycheck);
        }
        catch (RuntimeException ex) {
            throw ex;
        }
        catch (Exception ex) {
            throw new RuntimeException(ex.getMessage(), ex.getCause());
        }
    }

    private void fill(BufferedReader reader, boolean onlycheck) throws IOException {
        AtomicInteger count = new AtomicInteger(0);
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
    public Path getBase() {
        return parent;
    }

    public List<Entry> getEntries() {
        return songs;
    }

    public void move(Song prev, Song now) {
        songs.stream().filter(song -> song.equals(prev)).forEach((song) -> {
            Path relfile = parent.relativize(now.getPath());
            setSong(song.getIndex(), new Entry(relfile, song.getIndex(), Song.dryrun));
        });
    }

    private void setSong(int index, Entry song) {
        songs.set(index, song);
        System.err.format("%s: %s\n", name, song.getEntry());
        changed = true;
    }

    public boolean isChanged() {
        return changed;
    }

    public void write() {
        if (path == null) throw new RuntimeException("Playlists read from stdin cannot be written");
        System.err.format("PLAYLIST: writing %s\n", path.toString());
        if (dryrun) return;
        try {
            songs.sort(Comparator.naturalOrder());
            for (int i = 0; i < songs.size(); i++) {
                songs.get(i).setIndex(i);
            }
            PrintWriter out = new PrintWriter(path.toFile(), StandardCharsets.ISO_8859_1);
            try (out) {
                songs.forEach(song -> out.println(song.getEntry()));
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

    public class Entry extends Song {

        private Path relpath;
        private int index;

        private Entry(Path relfile, int index, boolean notcheck) {
            super(parent.resolve(relfile), notcheck);
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
            Entry newentry = new Entry(parent.relativize(newfile), index, Song.dryrun);
            setSong(index, newentry);
            return newentry;
        }

        public int getIndex() {
            return index;
        }

        private void setIndex(int index) {
            this.index = index;
        }
    }
}
