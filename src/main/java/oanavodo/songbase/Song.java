package oanavodo.songbase;


import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Song implements Comparable<Song> {
    public static boolean dryrun = false;
    public static boolean check = true;

    private Path path;
    private Path name;
    private String interpret;
    private String title;
    private boolean exists;

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

    public Song move(Path newpath, boolean delete) {
        Path newfile = moveIntern(newpath, delete);
        return (newfile != null) ? new Song(newfile, dryrun) : null;
    }

    protected Path moveIntern(Path newpath, boolean delete) {
        if (path.equals(newpath)) return null;
        if (!Files.isDirectory(newpath)) throw new RuntimeException("New folder not found: " + newpath.toAbsolutePath().toString());
        Path newfile = newpath.resolve(name);

        String oldfolder = path.getParent().toString().replace("\\", "/");
        String newfolder = newpath.toString().replace("\\", "/");
        int diff = indexOfDiff(oldfolder, newfolder);
        oldfolder = oldfolder.substring(diff);
        newfolder = newfolder.substring(diff);

        try {
            System.err.format("SONG: Moving %s -> %s, %s\n", oldfolder, newfolder, getName());
            if (!dryrun) Files.move(path, newfile);
            if (dryrun && Files.exists(newfile)) throw new FileAlreadyExistsException(newfile.toString());
        }
        catch (FileAlreadyExistsException ex) {
            System.err.format("SONG: Exists %s, %s\n", newfolder, getName());
            if (delete) {
                System.err.format("SONG: Delete %s, %s\n", oldfolder, getName());
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

    private int indexOfDiff(String a, String b) {
        int i = 0;
        for (i = 0; (i < a.length()) && (i < b.length()); i++) {
            if (a.charAt(i) != b.charAt(i)) {
                break;
            }
        }
        return i;
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
