package oanavodo.songbase;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

/**
 * Used to shuffle a list of songs with specified minimal gap between songs of same interpret.
 * The songs are sorted into separate lists according their interpret and then mixed up to a random order
 * where songs of same interpret are tried to distribute with some distances to each other.
 * @author Reiner
 */
public class ShuffleList<T extends Song> {

    private Map<String, ShuffleGroup<T>> base = new LinkedHashMap<>();
    private Random rand = new Random();
    private int same = 0;
    private int count = 0;
    private int gap = 0;
    private int maxgap;

    public ShuffleList(int gap) {
        this.maxgap = gap;
    }

    public boolean isEmpty() {
        return count <= 0;
    }

    public void add(T entry) {
        ShuffleGroup<T> group = base.get(entry.getInterpret());
        if (group == null) base.put(entry.getInterpret(), group = new ShuffleGroup<>(entry.getInterpret()));
        group.add(entry);
        count++;
        if (same < group.size()) same = group.size();
        gap = Math.min(maxgap, (count / same) - 1);
    }

    public T getNext() {
        //            int real = base.values().stream().mapToInt(group -> group.size()).sum();
        //            int blocks = base.values().stream().mapToInt(group -> group.isBlocked() ? 1 : 0).sum();
        //            System.err.format("count %d, real %d, blocks %d/%d gap %d\n", count, real, base.keySet().size(), blocks, gap);
        if (count <= 0) return null;
        int index = rand.nextInt(count);
        ShuffleGroup<T> found = null;
        ShuffleGroup<T> alter = null;
        ShuffleGroup<T> last = null;
        int pointer = 0;
        int offset = -1;
        for (ShuffleGroup<T> group : base.values()) {
            int next = pointer + group.size();
            int min = ((group.size() - 1) * gap) + group.size();
            if (min >= count) {
                found = group;
                offset = -1;
            }
            if (group.isBlocked()) {
                if ((alter == null) || (alter.getBlocked() > group.getBlocked())) {
                    alter = group;
                }
            } else {
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
        T next = found.remove(offset);
        found.setBlocked(gap);
        if (found.isEmpty()) base.remove(found.getName());
        count--;
        return next;
    }
}
