package org.generation.italy.lesson01_thread_creation;

/** A thread whose task is to print the numbers from 1 to 5. */
public class CountingThread extends Thread {

    public CountingThread(String name) {
        super(name);
    }

    @Override
    public void run() {
        for (int number = 1; number <= 5; number++) {
            System.out.println(Thread.currentThread().getName() + ": " + number);
        }
    }
}
