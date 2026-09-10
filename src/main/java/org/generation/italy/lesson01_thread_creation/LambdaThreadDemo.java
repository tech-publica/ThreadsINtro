package org.generation.italy.lesson01_thread_creation;

/** Supplies each thread's Runnable task with a lambda expression. */
public class LambdaThreadDemo {

    public static void main(String[] args) throws InterruptedException {
        System.out.println(Thread.currentThread().getName() + ": starting workers");

        Thread firstWorker = new Thread(() -> {
            for (int number = 1; number <= 5; number++) {
                System.out.println(Thread.currentThread().getName() + ": " + number);
            }
        }, "worker-1");

        Thread secondWorker = new Thread(() -> {
            for (int number = 1; number <= 5; number++) {
                System.out.println(Thread.currentThread().getName() + ": " + number);
            }
        }, "worker-2");

        firstWorker.start();
        secondWorker.start();

        firstWorker.join();
        secondWorker.join();

        System.out.println(Thread.currentThread().getName() + ": both workers finished");
    }
}
