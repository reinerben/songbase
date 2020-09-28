package oanavodo.songbase.playlist;

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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import oanavodo.songbase.playlist.Playlist.Entry;

public abstract class PlaylistIO {

    @FunctionalInterface
    private interface Creator {
        PlaylistIO apply(Path path, InputStream in, OutputStream out) throws IOException;
    }

    private static final Map<String, Creator> creators = Map.of(
        "m3u", (path, in, out) -> new M3u(path, in, out),
        "m3u8", (path, in, out) -> new M3u8(path, in, out)
    );

    protected Path path;
    protected String name;
    protected InputStream input;
    protected OutputStream output;
    private String type;

    public static PlaylistIO of(Path path) {
        return create(path, null, null, detectType(path));
    }

    public static PlaylistIO of(InputStream in, String type) {
        return create(null, in, null, type);
    }

    public static PlaylistIO of(OutputStream out, String type) {
        return create(null, null, out, type);
    }

    public static PlaylistIO of(String type) {
        return create(null, null, null, type);
    }

    private static PlaylistIO create(Path path, InputStream in, OutputStream out, String type) {
        final String type1 = (type == null) ? "m3u" : type;
        try {
            PlaylistIO io =  Optional.ofNullable(creators.get(type)).orElseThrow(
                () -> new RuntimeException("Playlist type not supported: " + type1)
            ).apply(path, in, out);
            io.setType(type);
            return io;
        }
        catch (IOException ex) {
            throw new RuntimeException(ex.getMessage(), ex.getCause());
        }
    }

    /**
     * Returns true if type of playlist file is supported.
     * @param path playlist file
     * @return
     */
    public static boolean isSupported(Path path) {
        return isSupported(detectType(path));
    }

    /**
     * Returns true if type of playlist file is supported.
     * @param type playlist type
     * @return
     */
    public static boolean isSupported(String type) {
        return creators.containsKey(type);
    }

    public static String detectType(Path path) {
        String name = path.getFileName().toString();
        int pos = name.lastIndexOf('.');
        if (pos > 0) name = name.substring(pos + 1);
        return name;
    }

    private PlaylistIO(Path path, InputStream in, OutputStream out) {
        this.path = (path != null) ? path.toAbsolutePath() : null;
        this.input = in;
        this.output = out;
        this.name = (path != null) ? this.path.getFileName().toString() : (in != null) ? "<stdin>" : "<stdout>";
    }

    private void setType(String type) {
        this.type = type;
    }

    protected abstract void fill(Playlist list, boolean onlycheck) throws IOException;

    protected abstract void save(Playlist list) throws IOException;

    public Path getPath() {
        return path;
    }

    public InputStream getInput() {
        return input;
    }

    public OutputStream getOutput() {
        return output;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public boolean hasInput() {
        return ((path != null) || (input != null));
    }

    public boolean hasOutput() {
        return ((path != null) || (output != null));
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 61 * hash + Objects.hashCode(this.path);
        hash = 61 * hash + Objects.hashCode(this.input);
        hash = 61 * hash + Objects.hashCode(this.output);
        hash = 61 * hash + Objects.hashCode(this.type);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        final PlaylistIO other = (PlaylistIO)obj;
        if (!Objects.equals(this.type, other.type)) return false;
        return sameIO(other);
    }

    public boolean sameIO(PlaylistIO other) {
        if (this == other) return true;
        if (other == null) return false;
        if (!Objects.equals(this.path, other.path)) return false;
        if (!Objects.equals(this.input, other.input)) return false;
        return Objects.equals(this.output, other.output);
    }

    /**
     * Intern subclass represents m3u (iso8859-1) format.
     * Currently #EXT lines are not supported
     */
    private static class M3u extends PlaylistIO {

        protected M3u(Path path, InputStream in, OutputStream out) throws IOException {
            super(path, in, out);
        }

        @Override
        protected void fill(Playlist list, boolean onlycheck) throws IOException {
            fillwithcs(StandardCharsets.ISO_8859_1, list, onlycheck);
        }

        protected void fillwithcs(Charset cs, Playlist list, boolean onlycheck) throws IOException {
            AtomicInteger count = new AtomicInteger(0);
            Reader backend = (input == null) ? new FileReader(path.toFile(), cs) : new InputStreamReader(input, cs);
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
                        list.add(list.entryOf(Paths.get(line), c));
                    }
                    catch (Exception ex) {
                        if (!onlycheck) throw ex;
                        System.err.println(ex.getMessage());
                    }
                });
            }
        }

        @Override
        protected void save(Playlist list) throws IOException {
            savewithcs(StandardCharsets.ISO_8859_1, list) ;
        }

        protected void savewithcs(Charset cs, Playlist list) throws IOException {
            PrintWriter out = (output == null) ? new PrintWriter(path.toFile(), cs) : new PrintWriter(output, true, cs);
            try (out) {
                list.entries().forEach(song -> printEntry(out, song));
            }
        }

        private void printEntry(PrintWriter out, Entry entry) {
            String record = entry.getFolder();
            if (!record.isEmpty()) record += "/";
            record += entry.getName().toString();
            out.println(record);
        }
    }

    /**
     * Intern subclass represents m3u8 (utt-8) format.
     * Currently #EXT lines are not supported
     */
    private static class M3u8 extends M3u {

        protected M3u8(Path path, InputStream in, OutputStream out) throws IOException {
            super(path, in, out);
        }

        @Override
        protected void fill(Playlist list, boolean onlycheck) throws IOException {
            fillwithcs(StandardCharsets.UTF_8, list, onlycheck);
        }

        @Override
        protected void save(Playlist list) throws IOException {
            savewithcs(StandardCharsets.UTF_8, list) ;
        }
    }
}
