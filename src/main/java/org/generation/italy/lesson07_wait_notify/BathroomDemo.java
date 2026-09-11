package org.generation.italy.lesson07_wait_notify;

import java.util.ArrayList;
import java.util.List;

/** One producer, three picky consumers, and thirty rolls that must all be used. */
public class BathroomDemo {

    private static final int ROLLS_PER_COLOR = 10;

    public static void main(String[] args) throws InterruptedException {
        Bathroom bathroom = new Bathroom();
        RollProducer producer = new RollProducer(bathroom, ROLLS_PER_COLOR);
        List<ColorConsumer> consumers = new ArrayList<>();
        List<Thread> workers = new ArrayList<>();

        System.out.println("THE VERY PICKY BATHROOM");
        System.out.println("One holder. Three color preferences. Ten rolls of each color.");

        for (PaperColor color : PaperColor.values()) {
            ColorConsumer consumer = new ColorConsumer(bathroom, color, ROLLS_PER_COLOR);
            consumers.add(consumer);
            workers.add(Thread.ofPlatform().name(color + "-fan").unstarted(consumer));
        }
        workers.add(Thread.ofPlatform().name("producer").unstarted(producer));

        for (Thread worker : workers) {
            worker.start();
        }
        try {
            for (Thread worker : workers) {
                worker.join();
            }
        } catch (InterruptedException exception) {
            bathroom.cancel();
            for (Thread worker : workers) {
                worker.interrupt();
            }
            throw exception;
        }

        if (bathroom.isCancelled()) {
            System.out.println("Simulation cancelled; quotas may be incomplete.");
            return;
        }
        System.out.println("Produced: " + producer.getProducedCount());
        for (ColorConsumer consumer : consumers) {
            System.out.println("Consumed " + consumer.getPreferredColor() + ": " + consumer.getConsumedCount());
        }
        System.out.println("All 30 rolls consumed. Bathroom closed!");
    }
}
