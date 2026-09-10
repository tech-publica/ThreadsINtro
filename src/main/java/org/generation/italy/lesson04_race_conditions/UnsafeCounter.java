package org.generation.italy.lesson04_race_conditions;

/** Intentionally unsafe: concurrent increments can overwrite each other's updates. */
public class UnsafeCounter {

    private int count;

    public void increment() {
        // Reading, adding, and writing are not one atomic operation.
        count++;
    }

    public int getCount() {
        return count;
    }
}
