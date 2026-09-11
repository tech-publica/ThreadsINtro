package org.generation.italy.lesson02_thread_lifecycle;

/** An optional scheduling experiment; priority guarantees neither CPU share nor finish order. */
public class ThreadPriorityDemo {

    private static final int WORKLOAD_SIZE = 100_000_000;

    public static void main(String[] args) throws InterruptedException {
        Runnable calculation = () -> runCalculation();

        Thread lowPriority = Thread.ofPlatform()
                .name("low-priority")
                .priority(Thread.MIN_PRIORITY)
                .unstarted(calculation);
        Thread highPriority = Thread.ofPlatform()
                .name("high-priority")
                .priority(Thread.MAX_PRIORITY)
                .unstarted(calculation);

        System.out.println("Scheduling experiment: priority does not guarantee a finishing order.");
        System.out.println("Requested priorities: low=" + Thread.MIN_PRIORITY + ", high=" + Thread.MAX_PRIORITY);
        System.out.println(lowPriority.getName() + ": configured priority=" + lowPriority.getPriority());
        System.out.println(highPriority.getName() + ": configured priority=" + highPriority.getPriority());

        // Both may run concurrently, but these calls do not create a simultaneous start.
        lowPriority.start();
        highPriority.start();
        lowPriority.join();
        highPriority.join();

        System.out.println("main: both workers finished; compare observations, not an expected winner.");
    }

    private static void runCalculation() {
        Thread current = Thread.currentThread();
        long startedAt = System.nanoTime();
        double checksum = 0;
        for (int number = 1; number <= WORKLOAD_SIZE; number++) {
            checksum += Math.sqrt(number);
        }
        long elapsedNanos = System.nanoTime() - startedAt;

        // Print once, after measuring the calculation; local variables are not shared.
        System.out.println(current.getName() + ": calculation finished; elapsed ms="
                + elapsedNanos / 1_000_000 + "; checksum=" + checksum);
    }
}
