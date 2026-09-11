package org.generation.italy.lesson07_wait_notify;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/** A customer who will only use rolls of one preferred color. */
public class ColorConsumer implements Runnable {

    private final Bathroom bathroom;
    private final PaperColor preferredColor;
    private final int rollsToConsume;
    private int consumedCount;

    public ColorConsumer(Bathroom bathroom, PaperColor preferredColor, int rollsToConsume) {
        this.bathroom = Objects.requireNonNull(bathroom, "Bathroom is required");
        this.preferredColor = Objects.requireNonNull(preferredColor, "A preferred color is required");
        if (rollsToConsume <= 0) {
            throw new IllegalArgumentException("Rolls to consume must be positive");
        }
        this.rollsToConsume = rollsToConsume;
    }

    @Override
    public void run() {
        try {
            while (consumedCount < rollsToConsume) {
                if (!bathroom.takeRoll(preferredColor)) {
                    return;
                }
                // Simulate using the roll after removing it; the holder is available.
                Thread.sleep(ThreadLocalRandom.current().nextInt(500, 1101));
                consumedCount++;
                System.out.println(Thread.currentThread().getName() + ": USED "
                        + preferredColor + " roll " + consumedCount + "/" + rollsToConsume);
            }
            System.out.println(Thread.currentThread().getName() + ": all done. Excellent taste in paper!");
        } catch (InterruptedException exception) {
            bathroom.cancel();
            Thread.currentThread().interrupt();
        }
    }

    public PaperColor getPreferredColor() {
        return preferredColor;
    }

    /** Read after joining this consumer's thread. */
    public int getConsumedCount() {
        return consumedCount;
    }
}
