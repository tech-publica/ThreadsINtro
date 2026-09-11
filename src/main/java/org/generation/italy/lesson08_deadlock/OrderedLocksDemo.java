package org.generation.italy.lesson08_deadlock;

/** Both workers acquire A before B, so they cannot form the demonstrated cycle. */
public class OrderedLocksDemo {

    public static void main(String[] args) throws InterruptedException {
        Object lockA = new Object();
        Object lockB = new Object();

        Thread firstWorker = Thread.ofPlatform().name("worker-A")
                .start(() -> useBothLocks(lockA, lockB));
        Thread secondWorker = Thread.ofPlatform().name("worker-B")
                .start(() -> useBothLocks(lockA, lockB));

        firstWorker.join();
        secondWorker.join();
        System.out.println("main: both workers finished using the order A then B");
    }

    private static void useBothLocks(Object lockA, Object lockB) {
        String name = Thread.currentThread().getName();
        try {
            synchronized (lockA) {
                System.out.println(name + ": holds A");
                // Deliberately keep A during this pause to make contention visible.
                Thread.sleep(500);
                System.out.println(name + ": needs B while still holding A");
                synchronized (lockB) {
                    System.out.println(name + ": acquired both locks");
                }
            }
            System.out.println(name + ": released both locks");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
