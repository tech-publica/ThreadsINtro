package org.generation.italy.lesson01_thread_creation;

/** Separates each task from the thread that executes it. */
public class RunnableClassDemo {

    public static void main(String[] args) throws InterruptedException {
        System.out.println(Thread.currentThread().getName() + ": starting workers");

        Runnable firstTask = new CountingTask();
        Runnable secondTask = new CountingTask();

        Thread firstWorker = new Thread(firstTask, "worker-1");
        Thread secondWorker = new Thread(secondTask, "worker-2");

        // Constructing a task or a Thread does not start its execution.
        firstWorker.start();
        secondWorker.start();

        firstWorker.join();
        secondWorker.join();

        System.out.println(Thread.currentThread().getName() + ": both workers finished");
    }
}
