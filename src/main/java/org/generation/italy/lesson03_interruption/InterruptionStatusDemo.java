package org.generation.italy.lesson03_interruption;

/** Demonstrates the difference between reading and clearing interruption status. */
public class InterruptionStatusDemo {

    public static void main(String[] args) throws InterruptedException {
        Thread worker = Thread.ofPlatform().name("status-worker").start(() -> {
            Thread current = Thread.currentThread();

            current.interrupt();
            System.out.println("status-worker: isInterrupted() = " + current.isInterrupted());
            System.out.println("status-worker: isInterrupted() again = " + current.isInterrupted());
            // The static method queries this executing thread and clears its flag.
            System.out.println("status-worker: Thread.interrupted() = " + Thread.interrupted());
            System.out.println("status-worker: isInterrupted() after clearing = " + current.isInterrupted());
            System.out.println("status-worker: Thread.interrupted() again = " + Thread.interrupted());
        });

        worker.join();
        System.out.println("main: worker terminated");
    }
}
