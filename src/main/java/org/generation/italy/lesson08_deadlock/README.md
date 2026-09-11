# Lesson 8: Deadlock and consistent lock ordering

## Goal

See how two threads can become permanently stuck while each holds a lock the
other needs. Then prevent that cycle by acquiring the locks in a consistent order.

The example deliberately contains no account balances or other business logic.
There are just **two workers and two locks**, so the locking problem is easy
to see. The broken and corrected versions run as separate programs.

## Before you begin

Complete lessons [2](../lesson02_thread_lifecycle/README.md),
[3](../lesson03_interruption/README.md), and
[5](../lesson05_invariants/README.md). You should understand thread builders,
monitor ownership, synchronized blocks, `sleep()`, `join()`, and interruption.

By the end of this lesson, you should be able to:

- Explain a circular wait between two threads and two monitors.
- Distinguish ordinary lock contention from deadlock.
- Prevent this deadlock through consistent lock ordering.
- Explain why sleeping and interruption do not release acquired monitors.
- Use a small countdown gate to arrange a reproducible demonstration.
- Recognize JVM evidence of a monitor deadlock rather than infer it from delay.

## Files and reading order

| File | Role |
| --- | --- |
| [DeadlockDemo.java](DeadlockDemo.java) | Intentionally acquires A then B in one worker, and B then A in the other. |
| [DeadlockObserver.java](DeadlockObserver.java) | Supporting diagnostic helper that asks the JVM to identify monitor-deadlock cycles. |
| [OrderedLocksDemo.java](OrderedLocksDemo.java) | Corrected version: both workers acquire A before B and finish normally. |

Read the worker lambdas in `DeadlockDemo` first. The observation helper is
separate so its diagnostic APIs do not hide the central locking example.

## Example 1: Two workers that cannot proceed

Main creates two shared objects, `lockA` and `lockB`. Each object's monitor
acts as one lock. Both workers receive references to these same objects.

The workers acquire them in opposite orders:

```java
// Worker A:
synchronized (lockA) {
    // Arrange for worker B to hold B before continuing.
    synchronized (lockB) {
        // Work requiring both locks.
    }
}

// Worker B:
synchronized (lockB) {
    // Arrange for worker A to hold A before continuing.
    synchronized (lockA) {
        // Work requiring both locks.
    }
}
```

Attempting the inner synchronized block does not release the outer monitor.
If the inner monitor belongs to another thread, this thread waits to acquire it
while continuing to hold the outer monitor.

Once both reach their second acquisition, the situation is:

| Worker | Already holds | Needs next | Why it cannot acquire it |
| --- | --- | --- | --- |
| worker-A | A | B | worker-B holds B. |
| worker-B | B | A | worker-A holds A. |

Each waits for the other to release its first lock, but neither can reach the
end of its outer block until it acquires the second. This **circular wait** is
the deadlock. Neither worker reaches its `acquired both locks` message.

The four familiar ingredients of a resource deadlock are visible here:

- **Mutual exclusion:** one thread at a time can own each monitor.
- **Hold and wait:** each worker holds one monitor while requesting another.
- **No forced removal:** another thread cannot simply take away its monitor.
- **Circular wait:** worker-A waits for worker-B, and worker-B waits for worker-A.

### Make the demonstration reproducible

Opposite lock orders make a deadlock possible, but without controlled coordination
one worker might acquire and release both locks before the other starts.

The full code uses `new CountDownLatch(2)` as a small gate. A **countdown latch**
starts with a count; calls to `countDown()` reduce it, and `await()` waits until
it reaches zero. Once open, the gate stays open; it is not reset for another round.

Each worker, while holding its first lock, calls:

```java
firstLocksHeld.countDown();
firstLocksHeld.await();
```

Neither proceeds past the gate until both have announced acquiring their first
locks. `await()` does not release the outer `synchronized` monitor: its internal
waiting mechanism is separate from A and B. This intentionally sets up the bad
situation; it is not a pattern for ordinary code to copy while holding resources.

The subsequent 500-millisecond sleep makes the first acquisitions and second
requests easier to follow. It does not establish their ordering. Removing that
sleep still leaves the latch arranging the deadlock. A longer sleep alone would
not be a reliable substitute for the gate.

## Observing an actual deadlock

`DeadlockObserver.report()` obtains the JVM's `ThreadMXBean` through
`ManagementFactory.getThreadMXBean()`. This management interface exposes
information about platform threads; no external library or remote server is
involved.

`findMonitorDeadlockedThreads()` looks for cycles involving monitor acquisition.
It returns an array of thread IDs for detected cycles, or `null` if none were
found. For detected threads, `getThreadInfo()` provides their names, states,
and the names of the threads owning the locks they need.

The helper tries at most thirty times, with a 100-millisecond pause between
unsuccessful checks. This bounds its polling; scheduler delays and diagnostic
call costs mean it is not a strict wall-clock deadline. It never tries to join
the deadlocked workers indefinitely.

If no cycle is detected within that observation window, the helper reports only
that fact. It does not call a timeout proof of deadlock. Likewise, seeing
`BLOCKED` by itself would not establish a cycle: ordinary contention can cause
the same state.

### Possible output

```text
worker-A: holds A
worker-B: holds B
worker-A: needs B while still holding A
worker-B: needs A while still holding B
main: JVM detected a monitor deadlock:
  worker-A waits for worker-B; state=BLOCKED
  worker-B waits for worker-A; state=BLOCKED
main: observation finished; daemon workers cannot keep this standalone JVM alive
```

The order of worker messages and of the diagnostic entries may vary. A worker
temporarily waiting at the countdown gate is not yet part of the demonstrated
monitor cycle. The detector reports the cycle once both second-lock acquisitions
are blocked. In a normally scheduled, uninterrupted run, the gate makes that
state reproducible.

### Why this broken demo can exit

Both faulty workers are created with `.daemon()`. When main finishes and no
non-daemon thread remains, they cannot keep the standalone JVM running.

This **contains** the intentionally broken demonstration. It does not recover
the workers or resolve the deadlock; they remain blocked until process shutdown.
There is no promise of their cleanup executing during that shutdown.

Run this demo in its own Java process or its own IDE run. Do not call its `main()`
inside a shared, long-lived JVM: the daemon workers and their locked objects
would remain there. The observer also reports JVM-wide monitor cycles, so an
isolated process keeps its results specific to this example.

## Example 2: Use a consistent order

`OrderedLocksDemo` gives both workers the same locking rule:

```java
synchronized (lockA) {
    synchronized (lockB) {
        // Work requiring both locks.
    }
}
```

Now a worker cannot hold B while waiting for A, because acquiring B always
requires first acquiring A. One worker can temporarily block on A, but the
worker holding A can obtain B, finish, and release its locks. The circular wait
shown above cannot form.

The countdown gate is deliberately absent from the corrected version. It was
only there to arrange the faulty interleaving. Keeping a gate that demands both
workers hold their first lock would itself prevent progress when both first
locks are the same object: only one worker could reach the gate.

These workers are normal non-daemon threads when started from main. Main joins
both and reports completion. A possible run is:

```text
worker-A: holds A
worker-A: needs B while still holding A
worker-A: acquired both locks
worker-A: released both locks
worker-B: holds A
worker-B: needs B while still holding A
worker-B: acquired both locks
worker-B: released both locks
main: both workers finished using the order A then B
```

Either worker can go first. The `released both locks` message is printed after
leaving the synchronized blocks, so the other worker may acquire A and print
before that message appears. Lock ordering provides safety from this cycle,
not fair turns or a fixed console order.

The rule must apply to **every path that acquires these locks**. In a larger
program, introducing another path that takes B then A can reintroduce the cycle.
For operations involving several account objects, a future example could use a
stable ordering of account identifiers to choose the lock order consistently.

## Sleep, interruption, and monitor ownership

The demos deliberately sleep while holding the first monitor so students can
see it being retained. This is a presentation choice, not advice to hold locks
through long operations. `Thread.sleep()` does not release owned monitors.

Interrupting a thread blocked **entering a synchronized block** does not make
that acquisition throw `InterruptedException`, release an already-owned monitor,
or terminate the thread. Therefore interruption cannot repair this monitor
deadlock. Changing priority, calling `notifyAll()`, or waiting longer cannot
remove the circular ownership dependency either.

This differs from lesson 7's `Object.wait()`: there the worker explicitly waited
on a monitor, releasing it and later reacquiring it. Here each worker is trying
to acquire a second monitor while retaining its first.

The catch blocks in these demos handle interruptions of the countdown `await()`
or the pacing `sleep()`. If that happens before the deadlock forms, exiting the
synchronized blocks releases their monitors and the thread restores its status.
Once a worker is blocked on the second monitor, those catches are not a way out.

Main declares `throws InterruptedException` for the observer's sleep or the
corrected demo's joins. If interrupted, main may not print its normal final
message. The corrected workers still perform finite work independently.

## Classes, constructors, and methods

| Class or interface | Purpose |
| --- | --- |
| `DeadlockDemo` | Contains the two intentionally opposing lock acquisitions. |
| `OrderedLocksDemo` | Applies one common acquisition order to both workers. |
| `DeadlockObserver` | Package-private utility used only for bounded diagnostic polling. |
| `CountDownLatch` | One-shot countdown gate from `java.util.concurrent`. |
| `ManagementFactory` | Entry point for local JVM management interfaces. |
| `ThreadMXBean` | JVM interface for platform-thread diagnostics, including monitor deadlocks. |
| `ThreadInfo` | A diagnostic snapshot for one platform thread. |

The management types belong to `java.lang.management`, in the JDK's
`java.management` module. The existing classpath-based project needs no extra
dependencies or module configuration to use them.

| Method or constructor | Explanation |
| --- | --- |
| `main(String[] args)` | Each demo's entry point; arguments are unused. May propagate `InterruptedException`. |
| `OrderedLocksDemo.useBothLocks(Object lockA, Object lockB)` | Private helper that acquires A then B, logs the steps, and handles interrupted pacing. |
| `DeadlockObserver()` | Private constructor preventing instantiation of the diagnostic utility. |
| `DeadlockObserver.report()` | Static helper that performs bounded polling and prints a detected cycle or an observation-window message. |
| `Object()` | Constructs each shared lock object. |
| `CountDownLatch(int count)` | Creates the gate with two announcements required before it opens. |
| `CountDownLatch.countDown()` | Decrements the count; reaching zero releases threads waiting on this latch. Does not release unrelated object monitors. |
| `CountDownLatch.await()` | Waits until the latch reaches zero, unless interrupted. Does not release the outer A or B monitor. |
| `ManagementFactory.getThreadMXBean()` | Returns the local JVM's platform-thread management interface. |
| `ThreadMXBean.findMonitorDeadlockedThreads()` | Returns IDs of platform threads in monitor-deadlock cycles, or null if none are found. |
| `ThreadMXBean.getThreadInfo(long[] ids)` | Returns diagnostic snapshots for those IDs. An entry can be null if its thread no longer exists, so the helper checks it. |
| `ThreadInfo.getThreadName()` | Returns the observed thread's name. |
| `ThreadInfo.getLockOwnerName()` | Returns the name of the thread owning the lock it is waiting for, when available. |
| `ThreadInfo.getThreadState()` | Returns the observed `Thread.State`, which is `BLOCKED` for these monitor acquisitions. |
| `Thread.ofPlatform()` | Returns the builder for platform threads. |
| `Thread.Builder.OfPlatform.name(String name)` | Sets the worker name and returns the builder. |
| `Thread.Builder.OfPlatform.daemon()` | Configures daemon status for the intentionally broken workers. |
| `Thread.Builder.start(Runnable task)` | Creates and starts a configured thread from its lambda task. |
| `Runnable.run()` | The no-argument, no-result task contract supplied by each worker lambda. |
| `Thread.currentThread()` | Returns the executing thread for naming or interruption handling. |
| `Thread.getName()` | Returns the current worker's name in the corrected helper. |
| `Thread.sleep(long millis)` | Pauses the caller for pacing or observation, retaining monitors it owns. Can throw `InterruptedException`. |
| `Thread.interrupt()` | Restores the current thread's flag after an interruption exception in the worker catches. Does not forcibly release a monitor. |
| `Thread.join()` | Waits for a worker to terminate; used only for the corrected workers here. |
| `System.out.println(String text)` | Prints the worker trace and observer results. |

`synchronized` and `try`/`catch` are language constructs rather than methods.
The trace calls inside synchronized blocks are for classroom visibility and
affect scheduling; these demos do not measure lock performance.

## Run the examples

The project targets JDK 26. From its root, compile with Maven:

```sh
mvn compile
```

Run each command as its own process:

```sh
java -cp target/classes org.generation.italy.lesson08_deadlock.DeadlockDemo
java -cp target/classes org.generation.italy.lesson08_deadlock.OrderedLocksDemo
```

Without Maven, compile this lesson with the JDK and use the same run commands:

```sh
javac -d target/classes src/main/java/org/generation/italy/lesson08_deadlock/*.java
```

In IntelliJ IDEA, use JDK 26 and run either demo's `main()` separately.
`DeadlockObserver` is a helper, not a standalone entry point.

## Check your understanding

1. Why does worker-A keep A while it waits to enter `synchronized (lockB)`?
2. What does the countdown gate guarantee that a sleep does not?
3. Does a `BLOCKED` state alone prove deadlock?
4. Why does A-then-B ordering prevent this particular cycle?
5. Why is there no countdown gate in the corrected example?
6. Does daemon status solve the deadlock?
7. Can `interrupt()` break a thread out of blocked monitor acquisition?

Suggested answers:

1. It has not exited the outer synchronized block; attempting another acquisition
   does not release its existing monitor.
2. Both first monitors have been acquired before either worker attempts its second.
3. No. A thread can be temporarily blocked behind another worker that can finish.
4. No worker can own B while waiting for A, so the opposing dependency cannot form.
5. Requiring both workers to hold the same first lock simultaneously would prevent progress.
6. No. It merely lets the standalone process exit despite its blocked daemon workers.
7. No. Monitor entry is not an interruptible wait like `sleep()` or `Object.wait()`.

## Small exercises

1. Draw the two-worker ownership cycle using the output from the broken demo.
2. Remove only the broken demo's worker sleeps. Explain why the gate still
   arranges the cycle, even if the messages appear more quickly.
3. Change both corrected workers to acquire B before A. A consistent B-then-A
   order works too; what matters is that all participants follow the same order.
4. Add a third worker to the corrected version using the same helper, and join
   all three. Explain why additional contention does not introduce this cycle.

The diagnostic contracts are documented in the official
[ThreadMXBean API](https://docs.oracle.com/en/java/javase/26/docs/api/java.management/java/lang/management/ThreadMXBean.html)
and [ThreadInfo API](https://docs.oracle.com/en/java/javase/26/docs/api/java.management/java/lang/management/ThreadInfo.html).
See [CountDownLatch](https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/util/concurrent/CountDownLatch.html)
for the gate's behavior. A later lesson will explore locks that support timed
or interruptible acquisition when that control is needed.
