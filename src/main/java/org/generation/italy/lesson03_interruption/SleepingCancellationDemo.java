package org.generation.italy.lesson03_interruption;

import java.util.concurrent.TimeUnit;

/** Requests cancellation of a worker that pauses between steps. */
public class SleepingCancellationDemo {

    public static void main(String[] args) throws InterruptedException {
        Thread worker = Thread.ofPlatform().name("sleeping-worker").start(() -> {
            int completedSteps = 0;
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    TimeUnit.MILLISECONDS.sleep(300);
                    completedSteps++;
                    System.out.println("sleeping-worker: completed step " + completedSteps);
                }
            } catch (InterruptedException exception) {
                // sleep() clears the status when it throws; preserve the signal.
                Thread.currentThread().interrupt();
                System.out.println("sleeping-worker: sleep interrupted");
            } finally {
                // Put resource-release operations here if the task owns resources.
                System.out.println("sleeping-worker: cleanup complete");
            }
        });

        try {
            // Pace the demo; cancellation does not depend on reaching a given step.
            Thread.sleep(700);
        } finally {
            // Also request shutdown if main's sleep is interrupted.
            System.out.println("main: requesting cancellation");
            worker.interrupt();
            worker.join();
        }
        System.out.println("main: worker terminated");
    }
}
