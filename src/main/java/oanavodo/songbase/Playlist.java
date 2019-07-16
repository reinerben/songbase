package oanavodo.songbase;


import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Playlist {
    public static boolean dryrun = false;

    private Path path;
    private Path parent;
    private List<Entry> songs;
    private boolean changed;

    public Playlist(Path path, boolean onlycheck) {
        this.path = path;
        this.parent = path.getParent();
        this.changed = false;
        this.songs = new ArrayList<>();
        try {
            if (!Files.isRegularFile(path)) throw new RuntimeException("Playlist not found: " + path.toAbsolutePath().toString());
            String name = path.toString();
            int end = name.lastIndexOf(".");
            if (end == -1) end = name.length();
            if (!name.endsWith(".m3u")) throw new RuntimeException("Playlist type not supported: " + name.substring(end));
            System.out.format("PLAYLIST: reading %s\n", path.toString());
            int count = 0;
            for (String line : Files.readAllLines(path, StandardCharsets.ISO_8859_1)) {
                line = line.trim();
                if (line.isEmpty()) continue;
                if (line.startsWith("#")) continue;
                line = line.replace("\\", "/");
                try {
                    songs.add(new Entry(Paths.get(line), count++, false));
                }
                catch (Exception ex) {
                    if (!onlycheck) throw ex;
                    System.out.println(ex.getMessage());
                }
            }
        }
        catch (RuntimeException ex) {
            throw ex;
        }
        catch (Exception ex) {
            throw new RuntimeException(ex.getMessage(), ex.getCause());
        }
    }

    public Path getBase() {
        return parent;
    }

    public List<Entry> getEntries() {
        return songs;
    }

    public void move(Song prev, Song now) {
        for (Entry song : songs) {
            if (song.equals(prev)) {
                Path relfile = parent.relativize(now.getPath());
                setSong(song.getIndex(), new Entry(relfile, song.getIndex(), Song.dryrun));
            }
        }
    }

    private void setSong(int index, Entry song) {
        songs.set(index, song);
        System.out.format("%s: %s\n", path.getFileName().toString(), song.getEntry());
        changed = true;
    }

    public boolean isChanged() {
        return changed;
    }

    public void write() {
        System.out.format("PLAYLIST: writing %s\n", path.toString());
        if (dryrun) return;
        try {
            songs.sort(Comparator.naturalOrder());
            for (int i = 0; i < songs.size(); i++) {
                songs.get(i).setIndex(i);
            }
            try (PrintWriter out = new PrintWriter(path.toFile(), "ISO-8859-1")) {
                for (Entry song : songs) {
                    out.println(song.getEntry());
                }
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
