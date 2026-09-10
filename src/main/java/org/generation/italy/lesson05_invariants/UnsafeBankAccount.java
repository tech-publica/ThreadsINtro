package org.generation.italy.lesson05_invariants;

/** Intentionally unsafe account API: separate calls cannot protect a withdrawal rule. */
public class UnsafeBankAccount {

    private int balanceCents;

    public UnsafeBankAccount(int initialBalanceCents) {
        if (initialBalanceCents < 0) {
            throw new IllegalArgumentException("Initial balance must not be negative");
        }
        balanceCents = initialBalanceCents;
    }

    public synchronized int getBalanceCents() {
        return balanceCents;
    }

    /** Deliberately omits the funds check; a previous getter call is not protection. */
    public synchronized void withdrawUnchecked(int amountCents) {
        if (amountCents <= 0) {
            throw new IllegalArgumentException("Withdrawal amount must be positive");
        }
        balanceCents -= amountCents;
    }
}
