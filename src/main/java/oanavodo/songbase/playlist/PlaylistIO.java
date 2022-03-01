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
import java.util.Set;

/**
 * Helper class for reading/writting from/to a playlist.
 * Since there are several playlist formats this is a abstract base class and have to be subclassed
 * by specific playlist type classes which implements fill and save methods.
 * @author Reiner
 */
public abstract class PlaylistIO {

    @FunctionalInterface
    private interface Creator {
        PlaylistIO apply(Path path, InputStream in, OutputStream out) throws IOException;
    }

    private static final Map<String, Creator> creators = Map.of(
        "m3u", (path, in, out) -> new M3u(path, in, out),
        "m3u8", (path, in, out) -> new M3u8(path, in, out)
    );

    private static final Set<String> songtypes = Set.of("mp3");

    protected Path path;
    protected String name;
    protected InputStream input;
    protected OutputStream output;
    private String type;

    /**
     * Creates an instance for file IO
     * @param path path to playlist file
     * @return
     */
    public static PlaylistIO of(Path path) {
        return create(path, null, null, detectType(path), false);
    }

    /**
     * Creates an instance for file IO
     * Also allow intern one song fake playlist
     * @param path path to playlist or song file
     * @return
     */
    public static PlaylistIO ofPlaylistOrSong(Path path) {
        return create(path, null, null, detectType(path), true);
    }

    /**
     * Creates an instance for input stream
     * @param in playlist input string
     * @param type playlist type
     * @return
     */
    public static PlaylistIO of(InputStream in, String type) {
        return create(null, in, null, type, false);
    }

    /**
     * Creates an instance for output stream
     * @param out playlist output string
     * @param type playlist type
     * @return
     */
    public static PlaylistIO of(OutputStream out, String type) {
        return create(null, null, out, type, false);
    }

    /**
     * Creates an special instance without IO.
     * Used for empty playlists
     * @param type playlist type
     * @return
     */
    public static PlaylistIO of(String type) {
        return create(null, null, null, type, false);
    }

    private static PlaylistIO create(Path path, InputStream in, OutputStream out, String type, boolean onesong) {
        final String type1 = (type == null) ? "m3u" : type;
        try {
            Creator creator = creators.get(type);
            if ((creator == null) && onesong && songtypes.contains(type1)) {
                creator = (p, i, o) -> new OneSong(path);
            }
            if (creator == null) {
                throw new RuntimeException("Playlist type not supported: " + type1);
            }
            PlaylistIO io = creator.apply(path, in, out);
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

    /**
     * Detect type of playlist by file path ending.
     * @param path playlist path
     * @return playlist type
     */
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

    protected abstract void fill(PlaylistInterface list, boolean onlycheck) throws IOException;

    protected abstract void save(PlaylistInterface list) throws IOException;

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

    public boolean isOneSong() {
        return (this instanceof OneSong);
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
    private static class OneSong extends PlaylistIO {

        protected OneSong(Path path) throws IOException {
            super(path, null, null);
        }

        @Override
        protected void fill(PlaylistInterface list, boolean onlycheck) throws IOException {
            try {
                list.addEntry(list.createEntry(getPath().getFileName()));
            }
            catch (Exception ex) {
                if (!onlycheck) throw ex;
                System.err.println(ex.getMessage());
            }
        }
        @Override
        protected void save(PlaylistInterface list) throws IOException {
            throw new RuntimeException("Intern playlist cannot be saved: " + getPath().toString());
        }
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
        protected void fill(PlaylistInterface list, boolean onlycheck) throws IOException {
            fillwithcs(StandardCharsets.ISO_8859_1, list, onlycheck);
        }

        protected void fillwithcs(Charset cs, PlaylistInterface list, boolean onlycheck) throws IOException {
            Reader backend = (input == null) ? new FileReader(path.toFile(), cs) : new InputStreamReader(input, cs);
            BufferedReader reader = (cs == StandardCharsets.UTF_8) ? new UTF8BufferedReader(backend) : new BufferedReader(backend);
            try (reader) {
                reader.lines().forEach(line -> {
                    line = line.trim();
                    if (line.isEmpty()) return;
                    if (line.startsWith("#")) return;
                    line = line.replace("\\", "/");
                    line = line.replace("%20", " ");
                    try {
                        list.addEntry(list.createEntry(Paths.get(line)));
                    }
                    catch (Exception ex) {
                        if (!onlycheck) throw ex;
                        System.err.println(ex.getMessage());
                    }
                });
            }
        }

        @Override
        protected void save(PlaylistInterface list) throws IOException {
            savewithcs(StandardCharsets.ISO_8859_1, list) ;
        }

        protected void savewithcs(Charset cs, PlaylistInterface list) throws IOException {
            PrintWriter out = (output == null) ? new PrintWriter(path.toFile(), cs) : new PrintWriter(output, true, cs);
            try (out) {
                list.getEntryIterator().forEachRemaining(song -> printEntry(out, song));
            }
        }

        private void printEntry(PrintWriter out, EntryInterface entry) {
            out.println(entry.getEntryString());
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
        protected void fill(PlaylistInterface list, boolean onlycheck) throws IOException {
            fillwithcs(StandardCharsets.UTF_8, list, onlycheck);
        }

        @Override
        protected void save(PlaylistInterface list) throws IOException {
            savewithcs(StandardCharsets.UTF_8, list) ;
        }
    }
}
