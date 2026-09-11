# Lesson 7: The very picky bathroom — wait() and notifyAll()

## The story and the goal

This bathroom's customers have unusually strong opinions about toilet paper.
The RED fan accepts only red rolls, the GREEN fan only green rolls, and the BLUE
fan only blue rolls. A producer delivers rolls at random intervals and in random
color order. The shared holder has space for **one roll**.

Over a complete run, the producer delivers exactly ten rolls of each color.
Each customer uses ten matching rolls. The program finishes after all thirty
rolls have been used, with pauses and console messages that make the activity
visible to the class.

The concurrency lesson is how to wait for a **condition** without busy waiting,
and why waking up means “check again,” not “you may definitely proceed.”

## Before you begin

Complete lessons [3](../lesson03_interruption/README.md),
[5](../lesson05_invariants/README.md), and
[6](../lesson06_visibility/README.md). You should understand interruption,
private monitor locks, shared state, and the difference between visibility and
atomicity. This guide also introduces the enum and collection APIs used to
prepare the randomized supply.

By the end of this lesson, you should be able to:

- Wait for a condition using a monitor's `wait()` method.
- Explain how waiting releases the monitor and resuming requires reacquiring it.
- Signal state changes with `notifyAll()` while holding that same monitor.
- Explain why every wait belongs inside a condition-checking `while` loop.
- Recognize wakeups where another color, or no roll, is available.
- Explain why `notify()` can leave the appropriate consumer asleep.
- Generate a random order while preserving exact production quotas.
- Let all workers finish normally, or release condition waiters on cancellation.

## The model

| Worker | Condition for progress | Action |
| --- | --- | --- |
| Producer | Holder is empty | Place the next roll from its shuffled supply. |
| RED consumer | Holder contains RED | Remove that roll and use it. |
| GREEN consumer | Holder contains GREEN | Remove that roll and use it. |
| BLUE consumer | Holder contains BLUE | Remove that roll and use it. |

All four workers share one `Bathroom` object and its private monitor lock.
The holder is either empty (`null`) or contains one `PaperColor` value.
No roll object is necessary: color is the only property this simulation uses.

This models the shared **paper holder**, not occupancy of a single toilet cubicle.
A customer removes a roll, releases the lock, then spends time “using” it.
During that time the producer can refill the holder and another customer can
take the next roll. No thread sleeps while holding the bathroom lock.

## Files and reading order

| File | Responsibility |
| --- | --- |
| [PaperColor.java](PaperColor.java) | Defines the enum constants `RED`, `GREEN`, and `BLUE`. |
| [Bathroom.java](Bathroom.java) | Owns the shared holder, the wait conditions, notifications, and cancellation flag. Start here for the synchronization mechanism. |
| [RollProducer.java](RollProducer.java) | Builds and shuffles the balanced supply, then delivers rolls with random pauses. |
| [ColorConsumer.java](ColorConsumer.java) | Takes only its preferred color, pauses to use each roll, and stops after ten. |
| [BathroomDemo.java](BathroomDemo.java) | Creates the four named workers, starts them, joins them, and prints totals. Run this class. |

## Waiting for the right condition

The essential consumer code in `Bathroom.takeRoll()` is:

```java
synchronized (lock) {
    while (!cancelled && availableRoll != preferredColor) {
        // Print what this customer needs and what is currently in the holder.
        lock.wait();
        // Print that this customer resumed and is about to check again.
    }
    if (cancelled) {
        return false;
    }
    availableRoll = null;
    lock.notifyAll();
    return true;
}
```

Read the condition as: “while we have not cancelled, and the holder does not
contain my color, wait.” `!=` is appropriate for enum constants. A `null` holder
also differs from every color, so an empty holder makes every consumer wait.

Cancellation is an additional exit condition explained later. During a normal
run, `cancelled` stays false and progress depends entirely on the roll condition.

### Object.wait()

`wait()` is a method of `Object`, not `Thread`. Calling `lock.wait()` makes the
**calling thread** wait using the object referenced by `lock`.

The caller must own that object's monitor, which our `synchronized (lock)` block
establishes. Calling it without ownership throws `IllegalMonitorStateException`.
Waiting releases that monitor, allowing the producer and other consumers to
enter their synchronized blocks. It does not release unrelated locks.

When the thread resumes from a normal return of `wait()`, it has reacquired the
same monitor. It then reaches the loop condition again before deciding whether
to remove a roll. Another thread cannot change the holder between that check
and removal while this consumer holds the lock.

Waiting differs from sleeping: `Thread.sleep()` pauses the caller without
releasing monitors it owns. Sleeping inside this critical section would prevent
other workers from changing the very condition the customer is waiting for.

### Object.notifyAll()

After placing or removing a roll, the code calls `lock.notifyAll()` while still
holding `lock`'s monitor. This awakens the threads currently waiting on that
object. It does not choose a color or reserve the roll for anyone.

The notifying thread **still owns the lock** until it leaves the synchronized
block. Awakened threads must acquire that lock before their waits can return.
They do not all execute inside the critical section together, and there is no
guaranteed order in which they acquire it.

Think of the notification as announcing a state change. The condition itself
decides whether a particular worker can proceed.

## What students should watch for

The console reports three stages explicitly:

- `WAIT`: the condition is false; the worker is about to release the monitor and wait.
- `AWAKE`: the wait returned normally and the worker has reacquired the monitor.
- `PLACED` or `TOOK`: a worker found its condition satisfied and changed the holder.

For example, this **possible excerpt** shows the BLUE fan waking for a RED roll:

```text
BLUE-fan: wants BLUE, holder=EMPTY; WAIT
producer: PLACED RED; notifyAll
BLUE-fan: AWAKE; rechecking for BLUE
BLUE-fan: wants BLUE, holder=RED; WAIT
RED-fan: AWAKE; rechecking for RED
RED-fan: TOOK RED; holder=EMPTY; notifyAll
BLUE-fan: AWAKE; rechecking for BLUE
BLUE-fan: wants BLUE, holder=EMPTY; WAIT
```

Other workers' messages may appear between these lines. The RED fan may acquire
the lock first, so BLUE may find an empty holder without ever seeing the RED
roll. Both outcomes demonstrate why notification is not permission to consume.

With exactly one consumer per color, two consumers do **not** compete for a
matching roll. They have different predicates on shared state. This setup shows
wrong-color and empty-holder wakeups; a second consumer of one color would also
demonstrate competition for the same matching roll.

The producer has a `while` loop too: a roll already in the holder must be taken
before the next one can be placed. Its `AWAKE` message also precedes a fresh
condition check.

## Why while, not if?

A return from `wait()` does not establish the caller's desired condition:

1. A notification can concern a different condition, such as another color.
2. Another worker can change the holder before this worker reacquires the lock.
3. Java permits **spurious wakeups**: a wait may return without a corresponding
   notification. Code must still recheck the condition.

An `if` would check only once. After waking, the consumer would skip another
check and could report taking the wrong color or an absent roll. The `while`
ensures it waits again whenever the condition remains false.

The ordinary wakeups in this demo already explain the loop; we do not need to
reproduce a spurious wakeup in order to justify it.

### Why the same lock protects checking and waiting

Checking the holder and calling `wait()` occur under one monitor. A producer
cannot place a roll between a consumer's condition check and that consumer's
release of the monitor into the waiting protocol. This avoids a gap where the
consumer could miss the relevant change and wait without first checking it.

Notifications are not stored as tokens for future waiters. If nobody is waiting
when a roll is placed, the **roll remains in the holder**. A later matching
consumer sees it under the lock and takes it without waiting. Correctness comes
from checking state, not from remembering how many notifications were sent.

## Why notifyAll() rather than notify()?

`notify()` awakens one arbitrarily selected waiter on the monitor. With different
color conditions, that could be the wrong customer.

Suppose all consumers are waiting and the producer places BLUE, but a single
notification awakens RED. RED rechecks and waits again. The BLUE consumer can
remain asleep. When the producer next finds the holder full, it waits too.
There may then be no runnable worker able to make the next state change.

`notifyAll()` lets all current waiters reconsider their conditions, including
the matching consumer. After a consumer removes a roll, notifying all also lets
a waiting producer reconsider its empty-holder condition. It still provides
no fairness guarantee; progress assumes runnable workers get opportunities to run.

We use the correct notification strategy in the runnable example. Replacing it
with `notify()` can hang this program, so treat that change as a discussion
exercise unless you also arrange a bounded experiment.

## Random order with exact quotas

`RollProducer.createProductionPlan()` first builds a list containing ten copies
of each enum color, then calls `Collections.shuffle()` on it:

```java
List<PaperColor> colors = new ArrayList<>();
for (PaperColor color : PaperColor.values()) {
    for (int roll = 0; roll < rollsPerColor; roll++) {
        colors.add(color);
    }
}
Collections.shuffle(colors);
```

`PaperColor` is an **enum**, a type with a fixed set of named values.
Its generated `values()` method supplies all three values for the outer loop.
`List<PaperColor>` describes an ordered collection of those values; `ArrayList`
is the resizable list implementation. `add()` appends one entry, and `shuffle()`
reorders the existing entries without changing how many of each color exist.

Thus the delivered colors have a random order but are not independent unrestricted
draws: exactly ten of each are available. Consecutive rolls of the same color are
allowed. The plan belongs only to the producer thread and is not shared mutable
state requiring additional synchronization.

The producer cannot overwrite a roll in a full holder. It waits until the
previous roll is removed before placing the next one in its shuffled sequence.

## Pacing the simulation

`ThreadLocalRandom.current().nextInt(origin, bound)` chooses a pseudorandom integer
for the calling thread. The lower bound is included and the upper bound excluded.

- Producer: `nextInt(250, 651)` requests a sleep of 250–650 milliseconds before
  each delivery attempt.
- Consumer: `nextInt(500, 1101)` requests a sleep of 500–1,100 milliseconds after
  taking a roll, to simulate using it.

Actual intervals also include scheduling, logging, and waits for the holder.
A typical demonstration takes tens of seconds, rather than flashing past.
The sleeps affect presentation and do not make coordination correct.

The monitor-state messages are intentionally printed **inside** the critical
sections so each description matches the state at that point. This adds logging
overhead and affects scheduling. It is a classroom trace, not a production
logging or performance pattern. All long pacing sleeps remain outside the lock.

## Normal completion and cancellation

`BathroomDemo` defines the quota once as `ROLLS_PER_COLOR = 10`, gives that value
to the producer and all consumers, and creates one consumer for every enum color.
Each consumer stops after using its ten rolls. The producer finishes after
delivering all thirty. No consumer can finish its quota while additional rolls
of its color remain to be delivered, because those quotas match exactly.

Main joins all four workers, so it waits for the last roll to finish being used,
not merely for the last delivery. The counters in the producer and consumers are
written only by their owner threads and read by main after joining them.
Those joins provide visibility for the final ordinary-field reads.

The final lines of a normal run are:

```text
Produced: 30
Consumed RED: 10
Consumed GREEN: 10
Consumed BLUE: 10
All 30 rolls consumed. Bathroom closed!
```

A worker interrupted during `wait()` or `sleep()` cannot be expected to finish
its quota. Simply letting that worker exit could leave the others waiting for
rolls that will never arrive or be taken.

For that reason the worker's exception handler calls `bathroom.cancel()` and
restores its interruption flag. `cancel()` sets a flag under the same lock and
calls `notifyAll()`. Wait loops then stop waiting, and `putRoll()`/`takeRoll()`
return `false` so their callers can exit. A customer already using a roll may
finish its short pacing sleep before it next encounters cancellation.

`wait()` reacquires the monitor before throwing `InterruptedException`. Leaving
the synchronized block releases it, and the exception propagates to the task's
handler. As with sleep, throwing clears the interruption status; the handler
restores it with `Thread.currentThread().interrupt()`.

If main is interrupted while joining, it cancels the bathroom, interrupts every
worker to end their interruptible sleeps or waits, and propagates the exception.
That path requests shutdown without promising that main completed every join.
Cancelled runs do not promise the normal quotas and do not print the success
summary. The workers are normal platform threads; no daemon shutdown is used.

## Classes and API reference

| Class or enum | Purpose |
| --- | --- |
| `PaperColor` | Restricts paper colors to RED, GREEN, and BLUE. Enum constants print with their declared names. |
| `Bathroom` | Encapsulates the holder and cancellation flag behind one private `Object` monitor. |
| `RollProducer` | Implements `Runnable` to deliver the exact shuffled supply. |
| `ColorConsumer` | Implements `Runnable` for one color preference and quota. |
| `BathroomDemo` | Wires the shared bathroom and tasks together, then manages startup and completion. |
| `List<E>` / `ArrayList<E>` | Ordered collection interface and resizable implementation for the production plan, consumer tasks, and thread references. |
| `Collections` | Utility class providing the list shuffle. |
| `ThreadLocalRandom` | Supplies random pacing delays without sharing a mutable random generator between workers. |
| `Objects` | Utility class used for explicit non-null argument validation. |

The following table covers all methods and constructors introduced or used
explicitly by these source files, including private helpers.

| Method or constructor | Explanation |
| --- | --- |
| `Bathroom()` | Compiler-provided constructor; starts with an empty holder and no cancellation. |
| `Bathroom.putRoll(PaperColor color)` | Waits until empty, inserts the roll, and notifies all waiters. Returns true on insertion or false on cancellation. May throw `InterruptedException`. |
| `Bathroom.takeRoll(PaperColor preferredColor)` | Waits for the requested color, removes it, and notifies all. Returns true on removal or false on cancellation. May throw `InterruptedException`. |
| `Bathroom.cancel()` | Sets cancellation and wakes condition waiters under the shared lock. Repeated calls leave the bathroom cancelled. |
| `Bathroom.isCancelled()` | Reads cancellation under the same lock, allowing main to choose its final message. |
| `Bathroom.log(String message)` | Private helper that prefixes a monitor event with the current worker's name. |
| `RollProducer(Bathroom bathroom, int rollsPerColor)` | Stores the shared bathroom and positive per-color quota. |
| `RollProducer.createProductionPlan()` | Private helper creating the balanced, shuffled list. |
| `RollProducer.run()` | Delivers the plan with random delays and handles interruption. |
| `RollProducer.getProducedCount()` | Returns the number successfully placed; main reads it after joining the producer. |
| `ColorConsumer(Bathroom bathroom, PaperColor preferredColor, int rollsToConsume)` | Stores the bathroom, color, and positive consumption quota. |
| `ColorConsumer.run()` | Takes and uses matching rolls until its quota is met or cancellation occurs. |
| `ColorConsumer.getPreferredColor()` | Returns the immutable preference. |
| `ColorConsumer.getConsumedCount()` | Returns the completed-use count; main reads it after joining that consumer. |
| `BathroomDemo.main(String[] args)` | Entry point; arguments are unused. Propagates an interruption of main's joins after requesting cancellation. |
| `PaperColor.values()` | Compiler-generated enum method returning an array of its constants. |
| `Object()` | Constructs the private lock object. |
| `Object.wait()` | Releases the owned monitor and waits, reacquiring it before returning or throwing on interruption. Use inside a condition loop. |
| `Object.notifyAll()` | Awakens all current waiters on the owned monitor. Does not release that monitor or guarantee their conditions. |
| `Object.notify()` | Discussed alternative that awakens one arbitrary waiter; deliberately not used here. |
| `ArrayList<>()` | Creates an initially empty resizable list. The diamond `<>` lets Java infer the element type. |
| `List.add(E element)` | Appends a plan entry, consumer, or thread reference to the appropriate list. |
| `Collections.shuffle(List<?> list)` | Randomly permutes a mutable list's existing entries. |
| `ThreadLocalRandom.current()` | Returns the random generator for the executing thread. |
| `ThreadLocalRandom.nextInt(int origin, int bound)` | Chooses an integer in the interval from origin inclusive to bound exclusive. |
| `Objects.requireNonNull(value, message)` | Returns the value if non-null; otherwise throws `NullPointerException` with the supplied message. |
| `IllegalArgumentException(String message)` | Constructs the unchecked exception thrown for nonpositive quotas. |
| `Runnable.run()` | Contract implemented by producer and consumers: no arguments, no returned value, and no declared checked interruption exception. |
| `Thread.ofPlatform()` | Returns the builder for a platform thread. |
| `Thread.Builder.OfPlatform.name(String name)` | Configures the worker name and returns the builder. |
| `Thread.Builder.unstarted(Runnable task)` | Creates a configured thread without starting it. Used so main can collect all worker references first. |
| `Thread.start()` | Starts an existing unstarted worker; main starts all four before joining any. |
| `Thread.join()` | Waits for a worker to terminate and provides visibility of its completed updates. Can throw `InterruptedException`. |
| `Thread.sleep(long millis)` | Pauses the caller for pacing; can throw `InterruptedException`. |
| `Thread.currentThread()` | Returns the executing thread for logging and restoration of interruption status. |
| `Thread.getName()` | Returns the worker name shown in messages. |
| `Thread.interrupt()` | Signals a worker, or restores the current thread's status after an interruption exception. |
| `System.out.println(String text)` | Prints the trace, progress messages, and summary. |

The enhanced `for` loops visit array or list elements in order. Local variables
and the production plan belong to the executing thread. The shared holder and
cancellation flag need no `volatile` modifier because all their accesses use
the same monitor.

## Run the simulation

The project targets JDK 26. From the project root:

```sh
mvn compile
java -cp target/classes org.generation.italy.lesson07_wait_notify.BathroomDemo
```

Without Maven, compile the lesson using the JDK, then use the same run command:

```sh
javac -d target/classes src/main/java/org/generation/italy/lesson07_wait_notify/*.java
```

In IntelliJ IDEA, select JDK 26 and run `BathroomDemo.main()` from the gutter.
Allow tens of seconds for the paced run. The ordering of colors, waits, wakeups,
and customer messages changes between executions; the normal final totals do not.

## Check your understanding

1. A BLUE fan wakes after a RED delivery. Why must it wait again?
2. Why can a fan find an empty holder immediately after being notified?
3. Does `notifyAll()` release the producer's lock?
4. Which lock does `wait()` release, and when is it reacquired?
5. Could `notify()` wake a customer who cannot use the available roll?
6. Why shuffle a balanced list instead of choosing each color without a quota?
7. Why do the consumer sleeps happen after `takeRoll()` returns?
8. Why is joining just the producer insufficient for declaring all rolls used?
9. What stops the remaining waiters if a consumer is interrupted?

Suggested answers:

1. Notification does not satisfy its color condition; the while loop checks again.
2. Another consumer may have removed the roll before it reacquired the monitor.
3. No. Awakened threads must wait for the notifier to release it.
4. The monitor of the object on which wait is called; it is reacquired before
   wait returns or throws due to interruption.
5. Yes, and leaving the matching customer asleep can prevent further progress.
6. Shuffling preserves ten occurrences of each color while randomizing their order.
7. They simulate roll use without preventing other threads from accessing the holder.
8. The last delivered roll may still be in the holder or being used by a consumer.
9. Shared cancellation changes the wait condition and notifies all waiters.

## Small exercises

1. Find an `AWAKE` line followed by another `WAIT` from the same fan. Explain
   what the holder contained when that fan checked again.
2. Increase the consumers' delay range to make a busy fan's next roll remain in
   the holder longer. Look for the producer waiting for an empty holder.
3. Explain a possible stuck execution if only one consumer were notified after
   a delivery. Do this on paper before considering changes to the runnable code.
4. As an extension, add a second fan of one color and divide that color's ten
   rolls between them. Keep the total consumption quota for that color at ten.
   Observe how a matching roll can disappear before another matching fan resumes.

The underlying method contracts are documented in the
[Object API](https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/lang/Object.html).
The randomization helpers are described in
[Collections](https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/util/Collections.html)
and [ThreadLocalRandom](https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/util/concurrent/ThreadLocalRandom.html).
A later lesson will implement producer–consumer coordination with `BlockingQueue`.
