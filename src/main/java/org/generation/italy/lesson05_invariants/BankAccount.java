package org.generation.italy.lesson05_invariants;

/** Maintains a nonnegative balance; every post-construction access uses lock. */
public class BankAccount {

    private final Object lock = new Object();
    private int balanceCents;

    public BankAccount(int initialBalanceCents) {
        if (initialBalanceCents < 0) {
            throw new IllegalArgumentException("Initial balance must not be negative");
        }
        balanceCents = initialBalanceCents;
    }

    /** Checks the funds and deducts the amount as one protected operation. */
    public boolean tryWithdraw(int amountCents) {
        if (amountCents <= 0) {
            throw new IllegalArgumentException("Withdrawal amount must be positive");
        }
        synchronized (lock) {
            if (balanceCents < amountCents) {
                return false;
            }
            balanceCents -= amountCents;
            return true;
        }
    }

    public int getBalanceCents() {
        synchronized (lock) {
            return balanceCents;
        }
    }
}
