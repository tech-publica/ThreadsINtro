package org.generation.italy.lesson06_visibility;

/** Publishes a stop request with volatile and joins the worker normally. */
public class VolatileFlagDemo {

    public static void main(String[] args) throws InterruptedException {
        VolatileStopTask task = new VolatileStopTask();
        Thread worker = Thread.ofPlatform().name("volatile-flag-worker").daemon(false).start(task);

        try {
            // This delay affects demonstration length, not correctness.
            Thread.sleep(200);
        } finally {
            // Also request shutdown if main is interrupted during its delay.
            task.requestStop();
            System.out.println("main: volatile stop flag set");
            worker.join();
        }
        System.out.println("main: worker terminated after observing the volatile flag");
    }
}
