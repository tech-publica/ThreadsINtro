package org.generation.italy.lesson07_wait_notify;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/** Delivers a shuffled supply with an exact quota for every color. */
public class RollProducer implements Runnable {

    private final Bathroom bathroom;
    private final int rollsPerColor;
    private int producedCount;

    public RollProducer(Bathroom bathroom, int rollsPerColor) {
        this.bathroom = Objects.requireNonNull(bathroom, "Bathroom is required");
        if (rollsPerColor <= 0) {
            throw new IllegalArgumentException("Rolls per color must be positive");
        }
        this.rollsPerColor = rollsPerColor;
    }

    @Override
    public void run() {
        List<PaperColor> productionPlan = createProductionPlan();
        try {
            for (PaperColor color : productionPlan) {
                // Pace deliveries without holding the bathroom's monitor.
                Thread.sleep(ThreadLocalRandom.current().nextInt(250, 651));
                if (!bathroom.putRoll(color)) {
                    return;
                }
                producedCount++;
            }
            System.out.println("producer: all " + producedCount + " rolls delivered!");
        } catch (InterruptedException exception) {
            bathroom.cancel();
            // wait()/sleep() cleared the flag; retain the interruption request.
            Thread.currentThread().interrupt();
        }
    }

    private List<PaperColor> createProductionPlan() {
        List<PaperColor> colors = new ArrayList<>();
        for (PaperColor color : PaperColor.values()) {
            for (int roll = 0; roll < rollsPerColor; roll++) {
                colors.add(color);
            }
        }
        Collections.shuffle(colors);
        return colors;
    }

    /** Read after joining the producer thread. */
    public int getProducedCount() {
        return producedCount;
    }
}
