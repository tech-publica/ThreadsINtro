package org.generation.italy.lesson07_wait_notify;

import java.util.Objects;

/** A single shared roll holder, with all state and conditions guarded by one lock. */
public class Bathroom {

    private final Object lock = new Object();
    private PaperColor availableRoll;
    private boolean cancelled;

    /** Waits for an empty holder; returns false if the simulation was cancelled. */
    public boolean putRoll(PaperColor color) throws InterruptedException {
        Objects.requireNonNull(color, "A roll must have a color");
        synchronized (lock) {
            while (!cancelled && availableRoll != null) {
                log("holder=" + availableRoll + "; WAIT for an empty holder");
                lock.wait();
                log("AWAKE; rechecking whether the holder is empty");
            }
            if (cancelled) {
                return false;
            }
            availableRoll = color;
            log("PLACED " + color + "; notifyAll");
            lock.notifyAll();
            return true;
        }
    }

    /** Waits for the preferred color; returns false if the simulation was cancelled. */
    public boolean takeRoll(PaperColor preferredColor) throws InterruptedException {
        Objects.requireNonNull(preferredColor, "A consumer must choose a color");
        synchronized (lock) {
            while (!cancelled && availableRoll != preferredColor) {
                log("wants " + preferredColor + ", holder="
                        + (availableRoll == null ? "EMPTY" : availableRoll) + "; WAIT");
                lock.wait();
                log("AWAKE; rechecking for " + preferredColor);
            }
            if (cancelled) {
                return false;
            }
            availableRoll = null;
            log("TOOK " + preferredColor + "; holder=EMPTY; notifyAll");
            lock.notifyAll();
            return true;
        }
    }

    /** Releases all condition waiters if a worker or main is interrupted. */
    public void cancel() {
        synchronized (lock) {
            if (!cancelled) {
                cancelled = true;
                log("CANCELLED; notifyAll so nobody remains waiting for missing rolls");
                lock.notifyAll();
            }
        }
    }

    public boolean isCancelled() {
        synchronized (lock) {
            return cancelled;
        }
    }

    private static void log(String message) {
        System.out.println(Thread.currentThread().getName() + ": " + message);
    }
}
