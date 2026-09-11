package org.generation.italy.lesson06_visibility;

/** Run as a standalone process: a faulty daemon worker may outlive main(). */
public class OrdinaryFlagDemo {

    public static void main(String[] args) throws InterruptedException {
        OrdinaryStopTask task = new OrdinaryStopTask();
        Thread worker = Thread.ofPlatform().name("ordinary-flag-worker").daemon().start(task);

        try {
            // Pacing only: this does not guarantee the worker reached its loop.
            Thread.sleep(200);
        } finally {
            task.requestStop();
        }
        System.out.println("main: ordinary stop flag set");

        // A timed join can return while the target thread is still alive.
        worker.join(500);
        if (worker.isAlive()) {
            System.out.println("main: worker was still alive when checked after the timed join");
        } else {
            System.out.println("main: worker terminated this time; the flag is still unsafe");
        }
        System.out.println("main: returning; the daemon worker cannot keep this standalone JVM alive");
    }
}
