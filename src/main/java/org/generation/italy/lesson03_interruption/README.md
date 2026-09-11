# Lesson 3: Interruption and cooperative cancellation

## Goal

Learn how to request that a task stop, how the task responds, and how to wait for
its cleanup and termination. We will cancel both a sleeping worker and a worker
that continuously computes. A third, smaller demo explains interruption status.

**Interruption is a cooperative signal, not forced termination.** Calling
`interrupt()` does not jump out of another thread's code. The task must respond
by handling an interruption exception or inspecting its interruption status.
In our examples, the chosen response is to finish the task.

## Before you begin

Complete [lesson 1](../lesson01_thread_creation/README.md) and
[lesson 2](../lesson02_thread_lifecycle/README.md). You should understand tasks,
threads, lambdas, `start()`, `sleep()`, and `join()`. We will explain the
`try`/`catch`/`finally` structure used for cancellation and cleanup below.

By the end of this lesson, you should be able to:

- Create and start a named platform thread using the modern builder API.
- Request cancellation with `interrupt()` and wait for termination with `join()`.
- Respond to interruption while sleeping and while doing computation.
- Explain why an interrupted sleep clears interruption status.
- Distinguish `isInterrupted()` from `Thread.interrupted()`.
- Place cleanup where it runs on both normal completion and cancellation.
- Explain why responsiveness depends on where a computing task checks its status.

## Files and suggested order

Each class below is a standalone demo with its own `main()` method. Its worker
task is supplied as a `Runnable` lambda; there are no extra task classes.

| Class | What it demonstrates |
| --- | --- |
| [SleepingCancellationDemo](SleepingCancellationDemo.java) | Cancels a worker that sleeps between steps, handles `InterruptedException`, and performs cleanup. |
| [ComputingCancellationDemo](ComputingCancellationDemo.java) | Cancels a worker that checks its status between finite batches of calculation. |
| [InterruptionStatusDemo](InterruptionStatusDemo.java) | Shows the difference between reading interruption status and reading it while clearing it. |

## Creating workers with the modern builder API

Lesson 2's optional priority example introduced the builder API, available as a
standard API since Java 21. From this lesson onward, it is our default for
explicitly creating platform threads. This section is self-contained if you
skipped that optional example. All three demos use this form:

```java
Thread worker = Thread.ofPlatform().name("worker").start(() -> {
    // The worker's task goes here.
});
```

A **builder** is an object on which we configure properties before creating
another object. Here the calls form a chain:

1. `Thread.ofPlatform()` returns a `Thread.Builder.OfPlatform`, the builder
   interface for platform threads. This call does not create or start a worker.
2. `name("worker")` sets the future thread's name and returns the builder, so
   another method can be called on it.
3. `start(Runnable task)` creates the configured thread, starts it, and returns
   the resulting `Thread`. The lambda supplies the task's `run()` body.

`Thread.Builder.OfPlatform` specializes the `Thread.Builder` interface. The
builder configures creation; the returned `Thread` supports the familiar
`interrupt()` and `join()` operations used below. No new imports are necessary:
`Thread` belongs to `java.lang`, which Java imports automatically.

The builder's `start(task)` differs from the no-argument `worker.start()` used
in earlier lessons. The former creates and starts a thread; the latter starts
an existing, unstarted thread. Our workers are already started when the builder
call returns, so we do not call `worker.start()` again. They may even have begun
or finished their work before main receives the returned reference.

When a later example needs separate construction and startup, the builder also
supports `unstarted(task)`. It returns a new thread without starting it; calling
that thread's no-argument `start()` then schedules its execution.

The constructors shown earlier remain supported and are not deprecated.
Builders are our course convention for expressing thread type and configuration
explicitly. These examples still use platform threads and have the same
interruption behavior as their constructor-based equivalents. Virtual threads
and executors will be introduced in their own lessons.

See the official [Thread API](https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/lang/Thread.html)
and [Thread.Builder API](https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/lang/Thread.Builder.html)
for the creation methods and their contracts.

## The interruption APIs

Each thread has an **interruption status**, often called its interruption flag.
The flag represents a signal; it is not a count of cancellation requests.
Repeated calls to `interrupt()` do not queue separate requests to process.

### Thread.interrupt(): send the signal

`worker.interrupt()` signals the thread referenced by `worker`. For a running
worker doing ordinary calculation, it sets the interruption status. The worker
can continue executing until its code notices and responds to that status.

If the worker is sleeping, interruption causes `sleep()` to throw
`InterruptedException` and clear the interruption status. The same exception
behavior applies to a thread waiting in `join()`.

Calling `Thread.currentThread().interrupt()` signals the calling thread itself.
We use that form to restore the status after catching `InterruptedException`.

An interruption signal does not guarantee that arbitrary blocking operations,
including every form of I/O, will return. These demos use the known behavior of
`sleep()` and explicit checks in a computation loop.

### Thread.isInterrupted(): inspect without clearing

`worker.isInterrupted()` returns the interruption status of the referenced
thread without changing it. Inside a task, this condition checks its own status:

```java
while (!Thread.currentThread().isInterrupted()) {
    // Perform a finite unit of work.
}
```

`!` means logical negation: continue while interruption has **not** been requested.
When the flag is set, the next condition check ends the loop.

The flag is not a completion signal. Even after requesting interruption, main
still uses `join()` to establish that the worker has actually terminated.

### Thread.interrupted(): inspect the calling thread and clear

`Thread.interrupted()` is static. It returns the status of the **calling thread**
and clears that status as part of the same operation. It does not take a target
thread. Calling it from main would inspect and clear main's status.

The two similarly named methods have different effects:

| Method | Whose status? | Clears it? |
| --- | --- | --- |
| `worker.isInterrupted()` | The referenced worker | No |
| `Thread.interrupted()` | The thread executing the call | Yes |

Our cancellation loops use `isInterrupted()`, preserving the signal. The small
status demo intentionally consumes the signal to make the clearing behavior
visible; it is not a template for ignoring a cancellation request.

## Example 1: Cancel a sleeping worker

The worker in `SleepingCancellationDemo` repeats these steps:

1. Check whether it has been interrupted.
2. Sleep for 200 milliseconds.
3. Count and print a completed step.

Main starts the worker, pauses for 700 milliseconds to let the demonstration
run, then requests cancellation and joins the worker.

The worker handles the exception outside the loop:

```java
try {
    while (!Thread.currentThread().isInterrupted()) {
        Thread.sleep(200);
        // Record and print a completed step.
    }
} catch (InterruptedException exception) {
    Thread.currentThread().interrupt();
    System.out.println("sleeping-worker: sleep interrupted");
} finally {
    System.out.println("sleeping-worker: cleanup complete");
}
```

If interrupted while sleeping, the worker leaves `sleep()` by exception, restores
its interruption status, and proceeds through `finally` to the end of the task.
The catch is outside the loop, so it does not silently resume the next iteration.

If the request arrives before the loop's first check, that check can skip the
loop entirely. If it arrives between a check and the call to `sleep()`, sleeping
with the status already set throws `InterruptedException`. If it arrives after
a sleep finishes, the current step may still print before the next check exits.
All these are valid cooperative responses.

### Why restore the status?

Throwing `InterruptedException` from `sleep()` clears the status. A handler that
cannot propagate that checked exception commonly restores the signal with
`Thread.currentThread().interrupt()` and returns. Restoring allows code later
in the call stack or cleanup to observe the interruption.

`Runnable.run()` does not declare `InterruptedException`, so our lambda cannot
simply add it to its signature. It handles the exception and finishes instead.
Restoring the flag does not throw another exception or cause termination by
itself: reaching the end of the task is what terminates this worker.

### Possible output

```text
sleeping-worker: completed step 1
sleeping-worker: completed step 2
sleeping-worker: completed step 3
main: requesting cancellation
sleeping-worker: sleep interrupted
sleeping-worker: cleanup complete
main: worker terminated
```

The number of completed steps varies; it can be zero. The `sleep interrupted`
message appears only if a sleep throws the exception. Cancellation detected by
the loop condition does not enter the catch block. A step can appear after main's
request message because the worker responds at its next interruption point.

In a normal run, cleanup happens before main prints `worker terminated`, because
main successfully joins the worker first.

## Example 2: Cancel a computing worker

`ComputingCancellationDemo` deliberately repeats a small calculation: each batch
adds the integers from 1 through 100,000. It keeps the batch count and most recent
sum in local variables belonging to the worker. It does not sleep inside the task.

At each batch boundary, it checks `isInterrupted()`. Main requests cancellation
after a 500-millisecond pacing delay. The worker finishes any batch already in
progress, notices the signal at the next check, and exits through cleanup.

There is no `InterruptedException` to catch in this worker: ordinary arithmetic
does not throw that exception when another thread calls `interrupt()`.

Checking between batches trades check frequency against cancellation latency.
Larger batches do more work between opportunities to stop. Smaller batches or
checks inside a long inner loop can improve responsiveness. A check between
batches only helps if each batch itself finishes; it cannot stop an infinite or
indefinitely blocked operation inside the batch. Scheduling also affects how
quickly the worker responds, so this is not a real-time guarantee.

### Possible output

```text
main: requesting cancellation
computing-worker: cancellation observed
computing-worker: completed batches = 12000
computing-worker: last batch sum = 5000050000
computing-worker: cleanup complete
main: worker terminated
```

The batch count is illustrative and varies with the machine and scheduler. The
last sum is `5000050000` if at least one batch completed, or `0` if cancellation
was observed before any batch began. Both are valid. We use `long` because the
sum exceeds the range of `int`.

This repeated calculation makes a worker stay busy for the demonstration. Its
batch count is not a reliable performance benchmark.

## Example 3: Observe the clearing behavior

`InterruptionStatusDemo` uses a dedicated worker to keep the experiment separate
from main's interruption status. It interrupts itself and then inspects its flag.

```text
status-worker: isInterrupted() = true
status-worker: isInterrupted() again = true
status-worker: Thread.interrupted() = true
status-worker: isInterrupted() after clearing = false
status-worker: Thread.interrupted() again = false
main: worker terminated
```

The first two reads leave the flag set. The first call to `Thread.interrupted()`
returns `true` and clears it, so subsequent reads return `false`. The worker does
not call an interruptible sleep or wait between these observations. In a normal
run with no external interruption, this sequence is deterministic.

## Cleanup and the main thread

`try`, `catch`, and `finally` are Java language constructs, not methods:

- `try` contains the work that may complete normally or throw an exception.
- `catch` handles an exception of the declared type from its associated `try`.
- `finally` executes when control leaves the associated `try`/`catch`, including
  through an exception or `return`. It does not wait for other threads by itself.

The worker demos print a cleanup message in `finally` to show where resource
release belongs. These workers own no external resources, so the message is an
observable placeholder, not a claim that a file or connection was closed.
Interruption does not bypass `finally`, but `finally` is not a guarantee against
abrupt JVM termination, such as killing the process.

Main also has cleanup responsibilities. After starting its worker, it uses:

```java
try {
    Thread.sleep(500); // The sleeping-worker demo uses 700 instead.
} finally {
    System.out.println("main: requesting cancellation");
    worker.interrupt();
    worker.join();
}
```

The delay only gives the demonstration some running time. It does not prove a
worker has reached any particular step. Cancellation remains valid when this
delay is changed to zero: the signal is sent after the builder's `start(task)`
returns, and workers can
observe it as soon as they begin running.

If main's pacing sleep is interrupted, the `finally` block still requests worker
cancellation and attempts to join it. The original exception then propagates
from `main()`, which declares `throws InterruptedException`. If main is itself
interrupted while joining, that join can throw too; the cancellation request has
already been sent, but main no longer guarantees it waited for termination. The
normal final message is printed only when these operations complete successfully.

This keeps cancellation distinct from waiting: `interrupt()` requests a stop;
`join()` waits for the stop to finish. `join()` alone would wait indefinitely for
these repeating tasks in the absence of a cancellation request.

## Method reference

Every method used in these demos is listed here, including the ones revisited
from previous lessons. No additional libraries or dependencies are required.

| Method | Purpose in this lesson |
| --- | --- |
| `main(String[] args)` | Each demo's entry point. Arguments are unused. `throws InterruptedException` lets interruptions of main's sleep or join propagate. |
| `Thread.ofPlatform()` | Static method returning a `Thread.Builder.OfPlatform` for configuring platform threads. Does not start a thread. |
| `Thread.Builder.OfPlatform.name(String name)` | Configures the thread name and returns the builder for further calls. |
| `Thread.Builder.start(Runnable task)` | Creates and starts the configured thread, then returns it. Used once in each demo; the returned worker must not be started again. |
| `Thread.Builder.unstarted(Runnable task)` | Alternative described above for creating a thread whose startup is deferred. Not needed in these demos. |
| `Runnable.run()` | The task contract implemented by each lambda: no parameters, no returned value, and no declared `InterruptedException`. |
| `Thread.start()` | Starts an existing, unstarted thread. Revisited to distinguish it from the builder's `start(task)`; not called separately in these demos. |
| `Thread.interrupt()` | Signals the referenced thread. Used by main to request cancellation and by the sleeping worker to restore its own interruption status. |
| `Thread.isInterrupted()` | Reads the referenced thread's interruption status without clearing it. Used at loop boundaries and in the status demo. |
| `Thread.interrupted()` | Static method that reads and clears the calling thread's interruption status. Used only in the status demo. |
| `Thread.currentThread()` | Static method returning the thread executing the call, allowing a task to inspect or interrupt itself. |
| `Thread.sleep(long millis)` | Static method requesting a pause of the calling thread. An interruption causes `InterruptedException` and clears that thread's interruption status. Timings depend on timers and scheduling. |
| `Thread.join()` | Waits for the referenced thread to terminate, unless the calling thread is interrupted. A successful join establishes completion, including worker cleanup, and visibility of its completed writes. |
| `System.out.println(String text)` | Prints a line through the standard-output `PrintStream`. The `+` expressions turn counts and boolean status values into message text. |

`InterruptedException` is the checked exception used by our sleep and join calls.
It is caught in the sleeping worker and declared by the three `main()` methods.
No methods are called on the caught exception object.

## Run the examples

The project targets JDK 26. From the project root, compile with Maven:

```sh
mvn compile
```

Run the demos individually:

```sh
java -cp target/classes org.generation.italy.lesson03_interruption.SleepingCancellationDemo
java -cp target/classes org.generation.italy.lesson03_interruption.ComputingCancellationDemo
java -cp target/classes org.generation.italy.lesson03_interruption.InterruptionStatusDemo
```

Without Maven, compile this lesson with the JDK, then use the same run commands:

```sh
javac -d target/classes src/main/java/org/generation/italy/lesson03_interruption/*.java
```

In IntelliJ IDEA, select JDK 26 and run any demo's `main()` from the gutter button.
The cancellation demos normally finish in roughly a second or less, but no exact
duration or number of worker steps is promised.

## Check your understanding

1. Does returning from `worker.interrupt()` mean the worker has terminated?
2. Why can a completed step print after main requests cancellation?
3. Why does the sleeping worker restore its interruption status in the catch?
4. Why does the computing worker not need to catch `InterruptedException`?
5. Which thread does `Thread.interrupted()` inspect if main calls it?
6. What happens if main's pacing delay is zero?
7. Why is worker cancellation in main's `finally` block?

Suggested answers:

1. No. Main must successfully join the worker to establish its termination.
2. Interruption is cooperative; a step may already be in progress.
3. The sleep exception cleared it, and restoring it preserves the signal.
4. Its arithmetic does not throw that exception; it explicitly checks the flag.
5. Main, and it clears main's flag as well.
6. The request may arrive before any work occurs, and both workers still exit.
7. So cancellation is also requested if main is interrupted during its delay.

## Small exercises

1. Set each cancellation demo's main-thread sleep duration to zero. Confirm that
   cleanup still precedes normal completion, even if no work was completed.
2. Change the computing worker's batch size. Discuss where cancellation checks
   occur and why larger batches can delay the response; do not treat one run's
   timing as proof of a performance rule.
3. Add another `isInterrupted()` print before the clearing call in the status
   demo. Predict the result before running it.
4. Explain what would happen if a handler caught interruption and restarted the
   loop without preserving or acting on the cancellation signal.

The next lessons will turn to problems caused by multiple threads accessing
shared mutable state. Cancellation itself does not make such access safe.
