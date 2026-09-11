package org.generation.italy.lesson06_visibility;

/** Intentionally unsafe: the worker has no guarantee of observing a later stop request. */
public class OrdinaryStopTask implements Runnable {

    private boolean stopRequested;

    @Override
    public void run() {
        while (!stopRequested) {
            // Deliberately empty to isolate flag visibility; may never finish.
        }
    }

    public void requestStop() {
        stopRequested = true;
    }
}
