package org.generation.italy.lesson08_deadlock;

import java.util.concurrent.CountDownLatch;

/** Intentionally deadlocks two daemon workers; run only as a standalone process. */
public class DeadlockDemo {

    public static void main(String[] args) throws InterruptedException {
        Object lockA = new Object();
        Object lockB = new Object();
        CountDownLatch firstLocksHeld = new CountDownLatch(2);

        Thread.ofPlatform().name("worker-A").daemon().start(() -> {
            try {
                synchronized (lockA) {
                    System.out.println("worker-A: holds A");
                    firstLocksHeld.countDown();
                    firstLocksHeld.await();
                    // Pacing only; the latch, not this sleep, arranges the deadlock.
                    Thread.sleep(500);
                    System.out.println("worker-A: needs B while still holding A");
                    synchronized (lockB) {
                        System.out.println("worker-A: acquired both locks");
                    }
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        });

        Thread.ofPlatform().name("worker-B").daemon().start(() -> {
            try {
                synchronized (lockB) {
                    System.out.println("worker-B: holds B");
                    firstLocksHeld.countDown();
                    firstLocksHeld.await();
                    Thread.sleep(500);
                    System.out.println("worker-B: needs A while still holding B");
                    synchronized (lockA) {
                        System.out.println("worker-B: acquired both locks");
                    }
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        });

        DeadlockObserver.report();
        System.out.println("main: observation finished; daemon workers cannot keep this standalone JVM alive");
    }
}
