package oanavodo.songbase;


import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Song implements Comparable<Song> {
    public static boolean dryrun = false;
    public static boolean check = true;
    public static boolean delete = false;

    private Path path;
    private Path name;
    private String interpret;
    private String title;
    private boolean exists;

    public Song(Path path) {
        this(path, check);
    }

    protected Song(Path path, boolean notcheck) {
        this.exists = Files.isRegularFile(path);
        if (!notcheck && check && !exists) throw new RuntimeException("Song not found: " + path.toAbsolutePath().toString());
        this.path = path;
        name = path.getFileName();
        interpret = name.toString();
        int end = interpret.lastIndexOf(".");
        if (end == -1) throw new RuntimeException("Cannot detect interpret and title: " + path.toAbsolutePath().toString());
        int pos = interpret.indexOf("--");
        if (pos == -1) throw new RuntimeException("Cannot detect interpret and title: " + path.toAbsolutePath().toString());
        if (interpret.charAt(pos + 2) == '-') pos++;
        title = interpret.substring(pos + 2, end);
        interpret = interpret.substring(0, pos);
    }

    public Song(Song other) {
        this.path = other.path;
        this.name = other.name;
        this.interpret = other.interpret;
        this.title = other.title;
    }

    public Path getPath() {
        return path;
    }

    public Path getName() {
        return name;
    }

    public String getInterpret() {
        return interpret;
    }

    public String getTitle() {
        return title;
    }

    public Song move(Path newpath) {
        Path newfile = moveIntern(newpath);
        return (newfile != null) ? new Song(newfile, dryrun) : null;
    }

    protected Path moveIntern(Path newpath) {
        if (path.equals(newpath)) return null;
        if (!Files.isDirectory(newpath)) throw new RuntimeException("New folder not found: " + newpath.toAbsolutePath().toString());
        Path newfile = newpath.resolve(name);
        try {
            System.out.format("SONG: Moving %s to %s\n", path.toString(), newfile.toString());
            if (!dryrun) Files.move(path, newfile);
            if (dryrun && Files.exists(newfile)) throw new FileAlreadyExistsException(newfile.toString());
        }
        catch (FileAlreadyExistsException ex) {
            System.out.format("SONG: Already exist (not moved) %s\n", newfile.toString());
            if (delete) {
                System.out.format("SONG: Deleting %s\n", path.toString());
                try {
                    if (!dryrun) Files.delete(path);
                }
                catch (Exception ex2) {
                    throw new RuntimeException(ex2.getMessage(), ex2.getCause());
                }
            }
        }
        catch (Exception ex) {
            throw new RuntimeException(ex.getMessage(), ex.getCause());
        }
        return newfile;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Song)) return false;
        return path.equals(((Song)obj).path);
    }

    @Override
    public int hashCode() {
        return path.hashCode();
    }

    @Override
    public int compareTo(Song other) {
        return path.compareTo(other.path);
    }
}
