package org.generation.italy.lesson02_thread_lifecycle;

/** Prints a greeting until main requests a stop through a visible boolean flag. */
public class BuongiornoTask implements Runnable {

    private volatile boolean running = true;

    @Override
    public void run() {
        try {
            while (running) {
                System.out.println("Buongiorno from " + Thread.currentThread().getName());
                Thread.sleep(3_000);
            }
        } catch (InterruptedException exception) {
            // Required handling for sleep(); flag-based stopping does not use this path.
            Thread.currentThread().interrupt();
        }
    }

    public void requestStop() {
        running = false;
    }
}
