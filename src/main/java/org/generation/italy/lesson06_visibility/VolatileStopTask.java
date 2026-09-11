package org.generation.italy.lesson06_visibility;

/** Uses a volatile flag for a stop request that is written once and never reset. */
public class VolatileStopTask implements Runnable {

    private volatile boolean stopRequested;

    @Override
    public void run() {
        while (!stopRequested) {
            // Active waiting isolates flag visibility; not a general waiting strategy.
        }
    }

    public void requestStop() {
        stopRequested = true;
    }
}
