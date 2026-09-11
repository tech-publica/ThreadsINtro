package org.generation.italy.lesson02_thread_lifecycle;

/** Requests a cooperative stop after 100 seconds, then waits for the worker to finish. */
public class BuongiornoDemo {

    public static void main(String[] args) throws InterruptedException {
        BuongiornoTask task = new BuongiornoTask();
        Thread worker = Thread.ofPlatform().name("BuongiornoThread").start(task);

        try {
            Thread.sleep(10_000);
        } finally {
            // Publish the request even if main's own sleep is interrupted.
            task.requestStop();
            System.out.println("main: stop requested");
        }

        worker.join();
        System.out.println("main: BuongiornoThread has stopped");
    }
}
