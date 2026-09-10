package org.generation.italy.lesson02_thread_lifecycle;

/** Computes a sum in one worker; read its result only after joining that worker. */
public class SumTask implements Runnable {

    private final int upperBound;
    private long result;

    public SumTask(int upperBound) {
        this.upperBound = upperBound;
    }

    @Override
    public void run() {
        long sum = 0;
        for (long number = 1; number <= upperBound; number++) {
            sum += number;
        }
        result = sum;
    }

    /** Call after the thread executing this task has been successfully joined. */
    public long getResult() {
        return result;
    }
}
