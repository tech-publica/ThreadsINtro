package org.generation.italy.lesson05_invariants;

/** Deliberately separates checking available funds from deducting them. */
public class CheckThenActDemo {

    public static void main(String[] args) throws InterruptedException {
        UnsafeBankAccount account = new UnsafeBankAccount(10_000);
        int withdrawalCents = 8_000;
        System.out.println("Intentionally unsafe check-then-act withdrawal");
        System.out.println("Starting balance (cents): " + account.getBalanceCents());

        Runnable withdrawalTask = () -> {
            boolean withdrawn = false;
            // The account's monitor is released between these two method calls.
            if (account.getBalanceCents() >= withdrawalCents) {
                account.withdrawUnchecked(withdrawalCents);
                withdrawn = true;
            }
            System.out.println(Thread.currentThread().getName() + ": withdrawal "
                    + (withdrawn ? "accepted" : "declined"));
        };

        Thread firstWorker = Thread.ofPlatform().name("customer-1").start(withdrawalTask);
        Thread secondWorker = Thread.ofPlatform().name("customer-2").start(withdrawalTask);
        firstWorker.join();
        secondWorker.join();

        int finalBalance = account.getBalanceCents();
        System.out.println("Final balance (cents): " + finalBalance);
        if (finalBalance < 0) {
            System.out.println("Invariant broken: the account is overdrawn.");
        } else {
            System.out.println("No overdraft this time; the check-then-act race still exists.");
        }
    }
}
