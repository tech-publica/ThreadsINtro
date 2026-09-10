package org.generation.italy.lesson02_thread_lifecycle;

/** Observes lifecycle states without using those observations to coordinate work. */
public class SleepAndStateDemo {

    public static void main(String[] args) throws InterruptedException {
        Thread worker = new Thread(() -> {
            try {
                for (int step = 1; step <= 6; step++) {
                    // sleep() pauses this worker because the worker calls it.
                    Thread.sleep(250);
                    System.out.println(Thread.currentThread().getName() + ": step " + step);
                }
            } catch (InterruptedException exception) {
                // Preserve the interruption signal and finish this task early.
                Thread.currentThread().interrupt();
                System.out.println(Thread.currentThread().getName() + ": interrupted, stopping");
            }
        }, "paced-worker");

        System.out.println("main: before start = " + worker.getState());
        worker.start();

        // A fixed number of observations: no polling until a desired state appears.
        for (int sample = 1; sample <= 5; sample++) {
            System.out.println("main: sample " + sample + " = " + worker.getState());
            // Here sleep() pauses main, giving time between observations.
            Thread.sleep(100);
        }

        // Only join(), not the samples or elapsed time, establishes completion.
       // worker.interrupt();
        worker.join();
        System.out.println("main: after join = " + worker.getState());
    }
}
