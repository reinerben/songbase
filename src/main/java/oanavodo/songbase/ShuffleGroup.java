package oanavodo.songbase;

import java.util.ArrayList;

/**
 * An array list decorated by a block counter.
 * The counter is decremented each time method pass is called.
 * @author Reiner
 */
public class ShuffleGroup<T> extends ArrayList<T> {

    private String name;
    private int wait = 0;

    public ShuffleGroup(String name) {
        super();
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public boolean isBlocked() {
        return wait > 0;
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
