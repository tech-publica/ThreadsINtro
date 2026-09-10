package org.generation.italy.lesson02_thread_lifecycle;

/** Waits for a worker's calculation before reading the result it produced. */
public class JoinResultDemo {

    public static void main(String[] args) throws InterruptedException {
        SumTask task = new SumTask(1_000_000);
        Thread worker = new Thread(task, "sum-worker");

        System.out.println("main: starting the calculation");
        worker.start();

        // This activity does not depend on the worker's result.
        for (int step = 1; step <= 3; step++) {
            System.out.println("main: independent activity " + step);
        }

        // join() gives us both completion and visibility of the worker's writes.
        worker.join();
        System.out.println("main: sum from 1 to 1000000 = " + task.getResult());
    }
}
