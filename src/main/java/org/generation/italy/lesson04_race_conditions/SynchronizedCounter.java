package org.generation.italy.lesson04_race_conditions;

/** Protects every access to count using this counter object's monitor. */
public class SynchronizedCounter {

    private int count;

    public synchronized void increment() {
        count++;
    }

    public synchronized int getCount() {
        return count;
    }
}
