# Lesson 6: Visibility and volatile stop flags

## Goal

Understand what lets one thread observe a value written by another thread.
We compare an ordinary shared stop flag with a `volatile` flag and separate
**visibility** from **atomicity**.

The first example is intentionally faulty and may leave its worker running.
It uses a timed observation and a daemon worker to let its standalone process
exit. The second example supplies the missing visibility guarantee and waits
for its normal worker to finish.

## Before you begin

Complete lessons [3](../lesson03_interruption/README.md),
[4](../lesson04_race_conditions/README.md), and
[5](../lesson05_invariants/README.md). You should recognize interruption,
shared state, critical sections, thread builders, and `join()`.

By the end of this lesson, you should be able to:

- Explain why an ordinary shared flag lacks a visibility guarantee.
- Use a volatile flag for a simple stop request.
- Explain why volatile visibility does not make compound updates atomic.
- Distinguish a timed join from confirmed thread termination.
- Explain daemon status and why it is containment, not cancellation.
- Choose between a flag and interruption based on how a task waits or works.

## Files and suggested order

| Class | Role |
| --- | --- |
| [OrdinaryStopTask](OrdinaryStopTask.java) | Deliberately faulty task that reads an ordinary boolean in a loop. |
| [OrdinaryFlagDemo](OrdinaryFlagDemo.java) | Requests a stop, waits up to 500 milliseconds, and reports the worker's observed status. |
| [VolatileStopTask](VolatileStopTask.java) | The same loop and request method, with a volatile field. |
| [VolatileFlagDemo](VolatileFlagDemo.java) | Requests a stop and joins a non-daemon worker normally. |

Run the demos as **separate Java processes**, using the commands below or the
IDE's individual run buttons. Do not call the faulty demo repeatedly inside a
long-lived test runner or a shared application: a daemon thread can continue
consuming CPU for as long as that JVM remains alive.

## The shared flag

Each demo creates one task object. Main and the worker share that object:

- Main calls `requestStop()`, which sets `stopRequested` to `true`.
- The worker executes `run()`, which reads that field in its loop condition.

The field initially contains `false`, Java's default for an instance boolean.
Main starts the worker and pauses briefly before making the request. The flag
is never reset, and each demo uses a fresh task for one execution.

The loop bodies are intentionally empty. This **active wait**, also called busy
waiting, isolates flag visibility and consumes CPU while checking. Even the
correct volatile version is not a general recommendation for waiting for work;
later lessons will introduce blocking coordination tools. There is no useful
calculation or performance comparison in this example.

We do not print, sleep, acquire locks, or inspect interruption inside these
worker loops. Additional operations could obscure the visibility question or
introduce another communication mechanism.

## Example 1: An ordinary flag

The essential code in `OrdinaryStopTask` is:

```java
private boolean stopRequested;

public void run() {
    while (!stopRequested) {
        // No synchronization between this read and main's write.
    }
}

public void requestStop() {
    stopRequested = true;
}
```

Reading or writing a boolean individually is atomic, but atomicity alone does
not ensure this worker observes main's later write. These accesses form a data
race: the two threads access the same variable, at least one writes, and there
is no suitable synchronization ordering the competing accesses.

The JVM may reuse a value already read instead of repeatedly loading the field
as a reader might expect from the source loop. The worker can therefore keep
using `false`. It may also happen to observe `true` and stop. Neither outcome
establishes a guarantee for another execution.

Do not reduce the explanation to “the value is in a CPU cache.” Java's memory
model constrains compiler and runtime optimizations as well as hardware effects.
Correctness must follow the language's rules, not assumptions about one machine.

Thread startup publishes actions that happened **before** starting the worker.
It does not automatically publish every later update to the task. Our request
is made after startup, so it needs its own communication guarantee.

### Bound the observation, not the worker's execution

The faulty demo starts its worker with:

```java
Thread worker = Thread.ofPlatform()
        .name("ordinary-flag-worker")
        .daemon()
        .start(task);
```

After requesting a stop, main calls `worker.join(500)`. This overload waits for
termination with a timeout in milliseconds. It returns no success flag: returning
does not prove the worker terminated. Main calls `worker.isAlive()` to inspect
whether this already-started thread is still alive at that moment.

The timeout limits the requested wait; scheduling can delay main's next action,
so it is not a strict wall-clock deadline for the entire program. Also,
`join(0)` means an indefinite wait, not a nonblocking check. Do not change this
faulty demo's timeout to zero.

A **daemon thread** does not keep the JVM running once all non-daemon threads
have finished. This demo's daemon designation contains a potentially nonterminating
worker when run as its own process. It does not fix visibility, stop the worker,
or promise that the worker executes cleanup when the JVM shuts down.

### Possible output

```text
main: ordinary stop flag set
main: worker was still alive when checked after the timed join
main: returning; the daemon worker cannot keep this standalone JVM alive
```

The alternative second line is:

```text
main: worker terminated this time; the flag is still unsafe
```

Both are valid observations. Being alive after the wait is not proof that the
worker will never stop, nor proof of a particular optimization; scheduling can
also affect when it finishes. The status may change between the check and the
printed message. Conversely, observing termination does not prove the code safe.

## Example 2: A volatile flag

The key change in `VolatileStopTask` is one field modifier:

```java
private volatile boolean stopRequested;
```

`volatile` is a Java keyword applied to a field, not a method call. It supplies
visibility and ordering guarantees for reads and writes of that field. In this
one-way protocol, the worker's reads cannot indefinitely reuse a pre-request
`false` as the ordinary-field version may do.

More formally, a write to a volatile variable happens-before subsequent reads
of that variable. Actions before the publishing write are also ordered before
code following a read that observes it. This is why volatile is more than a
request to “read fresh memory.” The relation is part of Java's memory model.

Here the required protocol is small: the field changes from `false` to `true`,
and a worker that continues executing its checks can observe the request and
leave its loop. Visibility does not promise scheduling fairness or a fixed
number of milliseconds before the worker executes again.

The correct demo explicitly chooses `.daemon(false)` and uses the no-argument
`join()` to wait for completion. `daemon(false)` is already the usual setting
inherited when creating a platform thread from main; making it explicit here
highlights the difference from the deliberately faulty daemon example.

Normal output is:

```text
main: volatile stop flag set
main: worker terminated after observing the volatile flag
```

Main requests the stop before waiting. The join confirms completion; it does
not itself publish the flag to a running worker or cause the worker to stop.

## Visibility is not atomicity

| Requirement | Appropriate reasoning |
| --- | --- |
| Publish a simple, independent stop flag | A volatile field fits this one-way communication. |
| Increment a shared counter | `count++` still combines read, add, and write; volatile alone cannot prevent lost updates. |
| Check an account balance and deduct funds | The invariant requires the entire decision and update to be protected together, as in lesson 5. |

Volatile accesses do not acquire a monitor or make surrounding statements
mutually exclusive. The earlier synchronized solutions protect compound
operations and also provide visibility. Volatile is suitable here because no
compound decision involving multiple shared values needs protection.

## Sleeping, interruption, and stop flags

Main's `sleep(200)` is only pacing. It guarantees neither that the worker has
entered its loop nor that a shared write becomes visible. Adding `sleep()` to
the faulty worker would not supply the missing synchronization either.

Lesson 3 used interruption for cancellation. A volatile stop flag is a separate
protocol: calling `requestStop()` does not set a thread's interruption status,
throw an exception, or wake a thread that is sleeping or waiting.

If a worker used `sleep()` between checks of a volatile flag, it would normally
finish its current sleep before checking again. In contrast, interrupting a
thread blocked in `sleep()` makes that sleep end by throwing
`InterruptedException`, subject to scheduling of the worker's response.

Our two tasks do not check interruption and make no interruptible blocking
calls. Calling `interrupt()` on them is therefore not a fallback stop mechanism.
A task must implement the cancellation protocol its caller uses.

### If main is interrupted

Both demos put `requestStop()` in `finally`, so an interruption of main's pacing
sleep still triggers the request. The ordinary task remains unsafe regardless
of that attempt; its daemon containment still applies.

The volatile demo also joins in `finally`. If main's pacing sleep was interrupted,
it requests a stop, attempts to join, and then propagates the original exception.
If main is interrupted while joining, that join can throw instead of confirming
termination. The stop request has already been published and the worker can
still finish. The normal final message is not guaranteed in these interrupted
runs. Neither demo silently swallows main's `InterruptedException`.

## Classes, constructors, and methods

All new members and revisited APIs used in the source are listed here.

| API or member | Explanation |
| --- | --- |
| `main(String[] args)` | The entry point in each demo. Arguments are unused; `throws InterruptedException` allows interruption of main's waits to propagate. |
| `OrdinaryStopTask()` | Compiler-provided constructor for the deliberately unsafe task. Its ordinary boolean initially holds `false`. |
| `OrdinaryStopTask.run()` | Reads the ordinary flag in an active loop. It has no guarantee of observing a later request. |
| `OrdinaryStopTask.requestStop()` | Writes `true` to the ordinary field without synchronization. Calling it does not establish safe communication. |
| `VolatileStopTask()` | Compiler-provided constructor for a task with an initially false volatile flag. |
| `VolatileStopTask.run()` | Checks the volatile flag until a stop request is observed. |
| `VolatileStopTask.requestStop()` | Publishes `true` through a volatile write. Repeated calls leave the flag true; the task is never reset. |
| `Runnable.run()` | The interface contract implemented by both task classes: no parameters and no returned value. The `@Override` annotation lets the compiler check that implementation. |
| `Thread.ofPlatform()` | Returns a `Thread.Builder.OfPlatform` for configuring platform threads. |
| `Thread.Builder.OfPlatform.name(String name)` | Sets the future worker's name and returns the builder. |
| `Thread.Builder.OfPlatform.daemon()` | Configures a daemon platform thread and returns the builder; equivalent to `daemon(true)`. |
| `Thread.Builder.OfPlatform.daemon(boolean on)` | Selects daemon or non-daemon status before creation; the correct demo explicitly passes `false`. |
| `Thread.Builder.start(Runnable task)` | Creates and starts the configured thread and returns its `Thread` reference. |
| `Thread.sleep(long millis)` | Pauses the calling thread for a requested duration. Here main calls it. It can throw `InterruptedException` and does not provide synchronization for the flag. |
| `Thread.join(long millis)` | Waits for termination with a timeout. Returns `void`, so normal return alone does not prove termination. Zero means indefinite waiting. |
| `Thread.isAlive()` | Reports whether the referenced thread has started and not yet terminated at the observation point. Used after the faulty worker's timed join. |
| `Thread.join()` | Waits without a timeout for worker termination, unless the caller is interrupted. Used after publishing the correct stop request. |
| `System.out.println(String text)` | Prints a message via standard output's `PrintStream`. Only main prints; the worker loops contain no output calls. |

`Thread`, `Runnable`, and `InterruptedException` are in `java.lang`, so no explicit
imports are needed. `volatile`, `while`, `try`, and `finally` are language
keywords. The `!` in `while (!stopRequested)` negates the boolean: continue while
no request is observed.

## Run the examples

The project targets JDK 26. From the project root, compile with Maven:

```sh
mvn compile
```

Run these commands separately; each launches its own JVM:

```sh
java -cp target/classes org.generation.italy.lesson06_visibility.OrdinaryFlagDemo
java -cp target/classes org.generation.italy.lesson06_visibility.VolatileFlagDemo
```

Without Maven, compile this lesson with the JDK and use the same run commands:

```sh
javac -d target/classes src/main/java/org/generation/italy/lesson06_visibility/*.java
```

In IntelliJ IDEA, select JDK 26 and run either demo's `main()` individually.
The tasks are supporting classes, not standalone entry points. Both demos
normally return within about a second, but timing is not a correctness assertion.

## Check your understanding

1. Why is an atomic boolean read not enough for safe communication here?
2. Does a timed join returning mean its target has terminated?
3. What does making the faulty worker a daemon actually accomplish?
4. If the ordinary worker stops on one run, is its flag safe?
5. Why would `volatile int count` not repair a shared `count++` operation?
6. Does setting a volatile stop flag wake a sleeping worker?
7. Why must the faulty demo run in a separate process?
8. Does `volatile` promise a response within a particular number of milliseconds?

Suggested answers:

1. Atomicity of a single access is different from visibility of another thread's
   later write; the ordinary accesses have no required synchronization.
2. No. The timeout may have elapsed; inspect whether the thread is still alive.
3. It lets the JVM shut down when its non-daemon threads finish, even if that
   worker continues looping. It does not cancel or repair the worker.
4. No. That execution happened to finish without demonstrating the missing guarantee.
5. The read–modify–write sequence remains multiple operations with no mutual exclusion.
6. No. An interruptible sleep responds to interruption, not to arbitrary flag writes.
7. A still-running daemon would otherwise continue consuming CPU in a shared JVM.
8. No. Visibility guarantees do not provide a scheduling deadline.

## Small exercises

1. Run the ordinary demo a few times, each as a separate process. Describe the
   observed outcome without concluding that a timeout proves infinite execution.
2. Set main's pacing delay to zero in the volatile demo. The worker should still
   respond to the published request; it need not already be inside its loop.
3. In a separate experiment, request a stop on a fresh ordinary task **before**
   starting its worker. Explain why the startup ordering makes that different
   from the faulty demo's write after startup.
4. Explain why replacing the private lock in lesson 5 with a volatile balance
   would not protect the account's invariant.

For the underlying contracts, see the Java language specification on
[sleep and visibility](https://docs.oracle.com/javase/specs/jls/se26/html/jls-17.html#jls-17.3)
and [happens-before ordering](https://docs.oracle.com/javase/specs/jls/se26/html/jls-17.html#jls-17.4.5),
and the [Thread API](https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/lang/Thread.html)
for daemon status, timed joins, and interruption.
