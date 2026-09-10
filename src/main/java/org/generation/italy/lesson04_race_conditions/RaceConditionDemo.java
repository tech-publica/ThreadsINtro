package org.generation.italy.lesson04_race_conditions;

/** Deliberately demonstrates a race; a matching total does not prove correctness. */
public class RaceConditionDemo {

    public static void main(String[] args) throws InterruptedException {
        UnsafeCounter counter = new UnsafeCounter();
        int incrementsPerWorker = 1_000_000;

        Runnable incrementTask = () -> {
            for (int iteration = 0; iteration < incrementsPerWorker; iteration++) {
                counter.increment();
            }
        };

        // Both workers use the SAME counter. Each execution has its own loop variable.
        Thread firstWorker = Thread.ofPlatform().name("counter-1").start(incrementTask);
        Thread secondWorker = Thread.ofPlatform().name("counter-2").start(incrementTask);

        firstWorker.join();
        secondWorker.join();

        int expected = 2 * incrementsPerWorker;
        int actual = counter.getCount();
        System.out.println("Intentionally unsafe shared counter");
        System.out.println("Expected: " + expected);
        System.out.println("Actual:   " + actual);
        if (actual == expected) {
            System.out.println("The totals match this time; the code still has a race.");
        } else {
            System.out.println("Updates were lost even though both workers finished.");
        }
    }
}
