package org.generation.italy.lesson01_thread_creation;

/** A task that can be given to a thread to execute. */
public class CountingTask implements Runnable {

    @Override
    public void run() {
        for (int number = 1; number <= 5; number++) {
            System.out.println(Thread.currentThread().getName() + ": " + number);
        }
    }
}
