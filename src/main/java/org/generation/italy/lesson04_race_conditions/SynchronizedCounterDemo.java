package org.generation.italy.lesson04_race_conditions;

/** Runs the same workload with a counter whose updates are protected. */
public class SynchronizedCounterDemo {

    public static void main(String[] args) throws InterruptedException {
        SynchronizedCounter counter = new SynchronizedCounter();
        int incrementsPerWorker = 1_000_000;

        Runnable incrementTask = () -> {
            for (int iteration = 0; iteration < incrementsPerWorker; iteration++) {
                counter.increment();
            }
        };

        Thread firstWorker = Thread.ofPlatform().name("counter-1").start(incrementTask);
        Thread secondWorker = Thread.ofPlatform().name("counter-2").start(incrementTask);

        firstWorker.join();
        secondWorker.join();

        int expected = 2 * incrementsPerWorker;
        int actual = counter.getCount();
        System.out.println("Synchronized shared counter");
        System.out.println("Expected: " + expected);
        System.out.println("Actual:   " + actual);
    }
}
