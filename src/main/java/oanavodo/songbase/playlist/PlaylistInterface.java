package oanavodo.songbase.playlist;

import java.nio.file.Path;
import java.util.Iterator;

public interface PlaylistInterface {
    public EntryInterface createEntry(Path path);
    public void addEntry(EntryInterface entry);
    public Iterator<? extends EntryInterface> getEntryIterator();
}
