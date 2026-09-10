package org.generation.italy.lesson05_invariants;

/** Lets the account protect the entire withdrawal, including the funds check. */
public class AtomicWithdrawalDemo {

    public static void main(String[] args) throws InterruptedException {
        BankAccount account = new BankAccount(10_000);
        int withdrawalCents = 8_000;
        System.out.println("Withdrawal protected by one private lock");
        System.out.println("Starting balance (cents): " + account.getBalanceCents());

        Runnable withdrawalTask = () -> {
            boolean withdrawn = account.tryWithdraw(withdrawalCents);
            // Report the result after leaving the account's critical section.
            System.out.println(Thread.currentThread().getName() + ": withdrawal "
                    + (withdrawn ? "accepted" : "declined"));
        };

        Thread firstWorker = Thread.ofPlatform().name("customer-1").start(withdrawalTask);
        Thread secondWorker = Thread.ofPlatform().name("customer-2").start(withdrawalTask);
        firstWorker.join();
        secondWorker.join();

        System.out.println("Final balance (cents): " + account.getBalanceCents());
    }
}
