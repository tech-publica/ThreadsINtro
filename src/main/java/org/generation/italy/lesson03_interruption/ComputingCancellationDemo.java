package org.generation.italy.lesson03_interruption;

/** Checks for cancellation between finite batches of computation. */
public class ComputingCancellationDemo {

    public static void main(String[] args) throws InterruptedException {
        Thread worker = Thread.ofPlatform().name("computing-worker").start(() -> {
            long completedBatches = 0;
            long lastBatchSum = 0;
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    long sum = 0;
                    for (int number = 1; number <= 100_000; number++) {
                        sum += number;
                    }
                    lastBatchSum = sum;
                    completedBatches++;
                }
                System.out.println("computing-worker: cancellation observed");
            } finally {
                System.out.println("computing-worker: completed batches = " + completedBatches);
                System.out.println("computing-worker: last batch sum = " + lastBatchSum);
                // Put resource-release operations here if the task owns resources.
                System.out.println("computing-worker: cleanup complete");
            }
        });

        try {
            // This delay controls demonstration length, not worker progress.
            Thread.sleep(500);
        } finally {
            System.out.println("main: requesting cancellation");
            worker.interrupt();
            worker.join();
        }
        System.out.println("main: worker terminated");
    }
}
