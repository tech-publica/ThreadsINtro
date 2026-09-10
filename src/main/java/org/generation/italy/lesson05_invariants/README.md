# Lesson 5: Protecting invariants with synchronized blocks

## Goal

Learn to choose the complete operation that needs protection, then protect it
with one stable, private lock. Our example is an account with one rule:
**its balance must never become negative**.

Lesson 4 protected individual counter operations. Here we explore why individually
synchronized methods are not enough when a business operation spans multiple
calls. As this is a lesson about a concurrency problem, we first show the faulty
approach and then its solution.

## Before you begin

Complete [lesson 4](../lesson04_race_conditions/README.md). You should understand
shared state, monitors, synchronized instance methods, thread builders, and
`join()`. This lesson introduces synchronized blocks, a private lock object,
and a method that reports success or failure with a `boolean` result.

By the end of this lesson, you should be able to:

- State an object's invariant and identify operations that might violate it.
- Recognize a check-then-act race across individually synchronized calls.
- Keep a check and its dependent update inside one critical section.
- Explain the lock used by synchronized methods versus synchronized blocks.
- Use a private, final lock consistently for an object's mutable state.
- Distinguish invalid input from a valid request that cannot currently succeed.

## Files and suggested order

| Class | Role |
| --- | --- |
| [UnsafeBankAccount](UnsafeBankAccount.java) | Intentionally unsafe API with a synchronized balance getter and a synchronized deduction method that does not check available funds. |
| [CheckThenActDemo](CheckThenActDemo.java) | Two customers each check the balance, then separately deduct money if their check passed. |
| [BankAccount](BankAccount.java) | Correct account with one private lock protecting the complete withdrawal and all subsequent balance reads. |
| [AtomicWithdrawalDemo](AtomicWithdrawalDemo.java) | Two customers each request an atomic withdrawal through `tryWithdraw()`. |

Run `CheckThenActDemo`, study the interleaving below, then run
`AtomicWithdrawalDemo`. The account classes support the demos and have no `main()`.

## The account invariant

An **invariant** is a condition that the object must maintain. Here it is:

```text
balanceCents >= 0
```

The account starts with 10,000 cents. Each of two customers tries to withdraw
8,000 cents. Only one withdrawal can succeed; after that, 2,000 cents remain.
If both succeed, the balance becomes -6,000 cents and the invariant is broken.

We represent money with integer cents to avoid floating-point rounding in this
example. Values are deliberately small and fit within `int`. This is a teaching
model of an account, not a complete banking system: there are no deposits,
transfers, persistence, or currency-conversion rules.

Both account constructors reject a negative starting balance. A withdrawal
amount must be positive; zero and negative amounts are invalid inputs. In the
correct account, a positive request exceeding the available funds returns
`false` and leaves the balance unchanged.

## Example 1: Separate synchronized calls

`UnsafeBankAccount` has two individually synchronized methods:

```java
public synchronized int getBalanceCents() {
    return balanceCents;
}

public synchronized void withdrawUnchecked(int amountCents) {
    // The full method also rejects zero and negative amounts.
    balanceCents -= amountCents;
}
```

Each method acquires the receiving account's monitor and releases it on exit.
Access to the balance field is protected inside those methods. However,
`withdrawUnchecked()` deliberately does not enforce sufficient funds: its caller
tries to enforce that rule using a separate getter call.

The faulty operation in `CheckThenActDemo` is:

```java
if (account.getBalanceCents() >= withdrawalCents) {
    account.withdrawUnchecked(withdrawalCents);
}
```

The getter releases the monitor before returning to the caller. The caller's
comparison and decision therefore do not hold that monitor, and the deduction
acquires it again later. Another customer can act during the gap.

### A check-then-act race

The following sequence is possible even though no two synchronized methods
execute simultaneously on the account:

| Step | Customer 1 | Customer 2 | Balance in cents |
| --- | --- | --- | --- |
| 1 | Getter returns `10000`; releases monitor | | `10000` |
| 2 | | Getter returns `10000`; releases monitor | `10000` |
| 3 | Check passes: `10000 >= 8000` | | `10000` |
| 4 | | Check passes: `10000 >= 8000` | `10000` |
| 5 | Deducts `8000` under monitor | | `2000` |
| 6 | | Deducts `8000` under monitor | `-6000` |

Each customer bases its action on a balance that may no longer be current by
the time it deducts the money. This is a **check-then-act race**.

Unlike lesson 4's unsafe counter, this example does not lose an update and does
not have an unsynchronized field access between workers. Both deductions are
applied correctly as arithmetic. The error is that the combined operation does
not enforce the account's rule. A race condition can exist even when the
individual field accesses are synchronized.

### Reading the unsafe output

One possible run is:

```text
Intentionally unsafe check-then-act withdrawal
Starting balance (cents): 10000
customer-1: withdrawal accepted
customer-2: withdrawal accepted
Final balance (cents): -6000
Invariant broken: the account is overdrawn.
```

You may instead see one accepted withdrawal, one declined withdrawal, a final
balance of `2000`, and a message explaining that the race still exists. That
outcome is likely if one customer completes its operation before the other
checks the balance. It may happen on every run you try.

The demo does not force an overdraft, and repeated runs do not prove safety.
The table makes the faulty ordering explicit without adding synchronization
tools just to manufacture it. The unsafe deduction could also overdraw the
account if used incorrectly by a single caller; its name and documentation
mark it as an intentionally incomplete account API.

## Example 2: One atomic withdrawal operation

The corrected account owns the entire decision. Customers call:

```java
boolean withdrawn = account.tryWithdraw(withdrawalCents);
```

Inside `tryWithdraw()`, the funds check and deduction share one critical section:

```java
synchronized (lock) {
    if (balanceCents < amountCents) {
        return false;
    }
    balanceCents -= amountCents;
    return true;
}
```

The first customer to acquire the lock sees 10,000 cents and may deduct 8,000.
The other customer cannot enter that block until the first releases the lock.
It then sees 2,000 cents, returns `false`, and performs no deduction.

This is an **atomic withdrawal** with respect to other operations using that
lock. The keyword does not turn the entire program into a single operation;
it prevents competing protected operations from interleaving inside this block.
The same monitor also supplies visibility between consecutive holders.

Both the successful and unsuccessful `return` statements release the monitor.
Java also releases it if an exception exits the block. We do not need to write
a manual unlock operation.

### Choosing a stable, private lock

`BankAccount` declares:

```java
private final Object lock = new Object();
```

- `new Object()` creates a dedicated object whose monitor acts as the lock.
- `private` keeps the reference inside the account. Callers cannot obtain this
  lock through its public API and hold it around unrelated work.
- `final` prevents replacing that reference after initialization. Every method
  therefore continues to use the same lock for this account.

`final` does not perform synchronization or make `balanceCents` immutable. The
`synchronized (lock)` blocks provide mutual exclusion and visibility.

Each account owns its own lock. Two customers using the same account share its
lock; customers using separate accounts use separate locks. A new lock created
inside each method call would fail to coordinate calls, because each caller
would acquire a different monitor.

The getter follows the same rule:

```java
public int getBalanceCents() {
    synchronized (lock) {
        return balanceCents;
    }
}
```

All balance reads and writes after construction use this private lock. The
constructor initializes the field before the account is shared with workers;
thread startup safely publishes that initialized state to them. The constructor
does not expose a partially initialized account to another thread.

### Synchronized methods versus blocks

| Form | Monitor acquired | Code protected |
| --- | --- | --- |
| `public synchronized void method()` | The receiver, `this` | The whole instance-method body |
| `synchronized (this) { ... }` | The receiver, `this` | The enclosed block |
| `synchronized (lock) { ... }` | The object referenced by `lock` | The enclosed block |

A synchronized instance method and a block synchronized on `this` use the same
monitor for the same object. A private lock is a different monitor. Adding
`synchronized` to a getter while leaving updates under `synchronized (lock)`
would not make those operations share one lock.

Either synchronized methods or consistently used synchronized blocks can protect
this account correctly. The essential fix is putting the funds check and
deduction under one acquisition of the same monitor. A private lock is an
encapsulation choice that also lets us choose the protected section explicitly.

For the same reason, a caller's `synchronized (account)` block would not acquire
the private lock used internally by `BankAccount`. Callers should use the atomic
operations the account provides rather than attempt to control its internals.

### Choose the critical section around the rule

Validating that `amountCents` is positive happens before locking. It depends only
on the method argument, which belongs to that invocation. The balance check
must happen inside the block because the balance is shared mutable state.

The workers print their result only after `tryWithdraw()` returns, keeping
console output outside the account's critical section. Another withdrawal can
occur before a worker prints its message; printing order is not a record of
lock-acquisition order.

Keep the whole invariant-sensitive operation protected, and put unrelated work
outside it. Making a block smaller is not an improvement if it splits the check
from the update it is meant to protect.

### Expected output

One possible normal run is:

```text
Withdrawal protected by one private lock
Starting balance (cents): 10000
customer-1: withdrawal accepted
customer-2: withdrawal declined
Final balance (cents): 2000
```

Either customer can succeed, and the customer messages may print in either
order. The guarantees are one accepted withdrawal, one declined withdrawal,
and a final balance of 2,000 cents after both joins. There is no guaranteed turn
order or fairness.

Both demos start both workers before joining either. Joining waits for completion;
the account's locking establishes withdrawal correctness. If main is interrupted
while joining, its `InterruptedException` propagates and normal final output is
not guaranteed. Each worker performs only one finite attempt and finishes
independently.

## Classes, constructors, and methods

| API or member | Explanation |
| --- | --- |
| `main(String[] args)` | The entry point in each demo. Arguments are unused; an interrupted join may propagate via `throws InterruptedException`. |
| `UnsafeBankAccount(int initialBalanceCents)` | Creates the intentionally incomplete account with a nonnegative starting balance. |
| `UnsafeBankAccount.getBalanceCents()` | Returns a balance snapshot while holding the account object's monitor. The snapshot can become outdated after the method returns. |
| `UnsafeBankAccount.withdrawUnchecked(int amountCents)` | Deducts a positive amount under that monitor, but deliberately does not check available funds. Returns no value. |
| `BankAccount(int initialBalanceCents)` | Creates the correct account and its private lock, rejecting a negative starting balance. |
| `BankAccount.tryWithdraw(int amountCents)` | Rejects nonpositive input. Otherwise atomically checks funds and deducts them, returning `true` on success or `false` for insufficient funds. |
| `BankAccount.getBalanceCents()` | Returns the balance while holding the same private lock used by withdrawals. |
| `Object()` | Constructs the dedicated lock object. We use its monitor without calling any methods on it. |
| `IllegalArgumentException(String message)` | Constructs an unchecked exception describing invalid input. `throw` raises it; callers need not declare it, but should supply valid arguments. |
| `Runnable.run()` | The contract supplied by each task lambda. Both workers execute the same task independently and have their own local `withdrawn` variable. |
| `Thread.ofPlatform()` | Returns the builder for a platform thread. |
| `Thread.Builder.OfPlatform.name(String name)` | Configures the worker name and returns the builder. |
| `Thread.Builder.start(Runnable task)` | Creates and starts the named worker, returning its `Thread` object. |
| `Thread.currentThread()` | Returns the thread executing the call, used in the worker's result message. |
| `Thread.getName()` | Returns that worker's name. |
| `Thread.join()` | Makes main wait for a worker to terminate, unless main is interrupted. |
| `System.out.println(String text)` | Prints a message via standard output's `PrintStream`; result messages are outside account locks. |

`Thread`, `Runnable`, `Object`, and `IllegalArgumentException` belong to the
automatically imported `java.lang` package. `synchronized`, `private`, `final`,
`return`, and `throw` are language keywords, not methods.

The expression `withdrawn ? "accepted" : "declined"` is the conditional operator:
it chooses the first string when the boolean is true and the second when false.
The compound assignment `balanceCents -= amountCents` subtracts the amount and
stores the new balance; its lock protection makes the surrounding operation safe.

Invalid input and insufficient funds are deliberately different outcomes.
Trying to withdraw a negative amount is a programming error, reported by an
exception. Requesting a positive amount greater than the balance is an ordinary
business outcome, reported by `false` without changing the account.

## Run the examples

The project targets JDK 26. From the project root, compile with Maven:

```sh
mvn compile
```

Then run each demo:

```sh
java -cp target/classes org.generation.italy.lesson05_invariants.CheckThenActDemo
java -cp target/classes org.generation.italy.lesson05_invariants.AtomicWithdrawalDemo
```

Without Maven, compile this lesson using the JDK and then use the same run commands:

```sh
javac -d target/classes src/main/java/org/generation/italy/lesson05_invariants/*.java
```

In IntelliJ IDEA, select JDK 26 and run either demo's `main()` using its gutter
button. No additional dependencies are needed.

## Check your understanding

1. What invariant must the account preserve?
2. How can an overdraft occur even though both unsafe methods are synchronized?
3. Which actions must `tryWithdraw()` protect together?
4. What is the difference between locking `this` and locking the private `lock`?
5. Does making the lock field `final` itself make a withdrawal thread-safe?
6. Why does a getter still need the same private lock as the withdrawal method?
7. Is the first customer to print necessarily the first to acquire the lock?
8. Does returning `false` inside the synchronized block leave the monitor locked?

Suggested answers:

1. The balance must remain nonnegative.
2. Both customers can read sufficient funds before either deducts them; the lock
   is released between the check and the deduction.
3. Reading the balance, deciding whether funds suffice, and deducting on success.
4. They acquire different monitors in `BankAccount`; only operations using the
   same monitor exclude one another.
5. No. It stabilizes the reference; the synchronized blocks provide protection.
6. It must coordinate with the writer for protected access and visibility.
7. No. Printing occurs after leaving the account's critical section.
8. No. Exiting the block through a return releases the monitor automatically.

## Small exercises

1. Change the correct demo's withdrawal amount to 5,000 cents. Both withdrawals
   should succeed, leaving zero. With 6,000 cents, only one should succeed,
   leaving 4,000.
2. Add a third customer to the correct demo using the same account and task.
   Start all three before joining them. With the original values, exactly one
   should succeed and the final balance should still be 2,000 cents.
3. Construct an account with a negative starting balance, or call `tryWithdraw()`
   with zero in a small sequential experiment. Explain the resulting exception.
4. Rewrite the correct account using synchronized instance methods instead of
   the private lock. Preserve the complete withdrawal operation and use the same
   monitor in every method accessing the balance.
5. Explain why adding `synchronized (account)` around only the balance check
   would not fix the unsafe demo's compound operation.

This lesson uses one account and one lock. A future lesson will examine operations
involving multiple accounts and how acquiring multiple locks can lead to deadlock.
