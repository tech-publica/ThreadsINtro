package org.generation.italy.lesson01_thread_creation;

/** Creates workers by extending Thread. */
public class ThreadSubclassDemo {

    public static void main(String[] args) throws InterruptedException {
        System.out.println(Thread.currentThread().getName() + ": starting workers");

        CountingThread firstWorker = new CountingThread("worker-1");
        CountingThread secondWorker = new CountingThread("worker-2");

        // Start both workers before waiting for either one.
        firstWorker.start();
        secondWorker.start();

        firstWorker.join();
        secondWorker.join();

        System.out.println(Thread.currentThread().getName() + ": both workers finished");
    }
}
