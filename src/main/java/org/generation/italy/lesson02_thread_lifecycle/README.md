# Lesson 2: Thread lifecycle — waiting, sleeping, and observing state

## Goal

Understand what happens between creating a thread and its termination. We will
use `join()` to collect a completed calculation, `sleep()` to pause the calling
thread, and `getState()` to observe a thread's lifecycle.

The examples are complete, correct programs. State observations help explain
execution; they do not decide when a result is safe to read or when work is done.

## Before you begin

Complete [lesson 1](../lesson01_thread_creation/README.md) first. You should
recognize `Thread`, `Runnable`, lambda expressions, `start()`, and `join()`.
This lesson also uses instance fields, a getter, and basic `try`/`catch` syntax.

By the end of this lesson, you should be able to:

- Start a calculation and do independent work before waiting for its result.
- Explain why joining a worker permits reading the updates it completed.
- Identify which thread a call to `sleep()` pauses.
- Name the six thread states and explain their general meaning.
- Separate guaranteed states from snapshots that depend on timing.
- Recognize the basic interruption handling required by `sleep()` and `join()`.

## Example 1: Wait for a result

Read [SumTask.java](SumTask.java), then run
[JoinResultDemo.java](JoinResultDemo.java).

`SumTask` implements `Runnable`. Its constructor receives an inclusive upper
bound. The demo chooses `1_000_000`: underscores in a Java numeric literal improve
readability and do not change its value. The task adds the integers from 1 through
that bound. It uses `long` for the total because this sum is too large for `int`.

The `upperBound` field is `final`, so the constructor assigns it once. `run()`
accumulates the sum in a local variable and then stores the total in the task's
`result` field. `Runnable.run()` cannot return a value, so our own `getResult()`
method gives the main thread access to that field after the worker finishes.

The important sequence in `JoinResultDemo` is:

```java
SumTask task = new SumTask(1_000_000);
Thread worker = new Thread(task, "sum-worker");
worker.start();

// Main performs activity that does not need the result.

worker.join();
System.out.println("main: sum from 1 to 1000000 = " + task.getResult());
```

After `start()`, the main thread prints three progress messages representing
independent activity. The calculation may finish before, during, or after those
messages. This example does not guarantee simultaneous execution or demonstrate
a speed improvement.

### What join() guarantees

`worker.join()` makes the **calling thread**, here main, wait until `worker`
terminates. If the worker has already terminated, the call returns immediately.
The worker keeps executing while main waits for it.

Joining also provides a **visibility guarantee**. Actions performed by the worker
happen-before the main thread continues after a successful join. In this example,
that means the main thread can see the value the worker stored in `result`.
“Happens-before” describes a Java ordering and visibility guarantee, not simply
two events appearing to occur in chronological order.

The ordinary, non-`volatile` `result` field is appropriate here because:

1. Only one worker executes this task and writes the result.
2. Main reads the result only after successfully joining that worker.
3. No thread modifies the result after that join.

`getResult()` is an ordinary getter; it does not wait or synchronize by itself.
Its documented precondition is that the worker has been joined. The task is a
small teaching example for one execution, not a general shared result container.
More flexible ways to return task results will come in later lessons.

There is a matching guarantee at startup: actions in main before `start()`, such
as constructing and configuring this task, are visible to the started worker.

### Expected output

```text
main: starting the calculation
main: independent activity 1
main: independent activity 2
main: independent activity 3
main: sum from 1 to 1000000 = 500000500000
```

All these lines are printed by main, so their order is stable. The worker performs
the calculation without printing. Deterministic output does not mean the program
is sequential: there are still two threads that can make progress independently.

## Example 2: Pause a worker and observe its state

Read and run [SleepAndStateDemo.java](SleepAndStateDemo.java).

The worker performs three steps, pausing for 250 milliseconds before each one.
Main prints the worker's state before starting it, takes five state samples with
100-millisecond pauses between them, then joins it and prints its final state.

### Thread.sleep(long millis)

`Thread.sleep(250)` requests a pause of 250 milliseconds for the **thread that
executes the call**. A millisecond is one thousandth of a second. `sleep()` is a
static method on `Thread`; it does not select a worker by referring to an object.

In this demo:

- Inside the lambda, `Thread.sleep(250)` pauses `paced-worker`.
- Inside `main()`, `Thread.sleep(100)` pauses main.

The requested duration is not a precise scheduling appointment. Timer precision
and scheduling affect when the thread resumes, and interruption can end the wait
early. Waking up does not mean the thread will immediately receive CPU time.

Sleeping is useful here to pace the steps and observations. It does **not** prove
another thread has started, reached a particular line, or finished its work. It
also does not provide the visibility guarantee we obtained from `join()`.

### Thread.getState() and Thread.State

`worker.getState()` returns a `Thread.State` enum value: a snapshot of the worker's
state at that instant. An enum represents a fixed set of named values. Printing
the returned value displays a name such as `NEW` or `TIMED_WAITING`.

| State | Meaning |
| --- | --- |
| `NEW` | The thread has been constructed but has not been started. |
| `RUNNABLE` | The thread is executing in the JVM or eligible to execute; it may be waiting for CPU time or other operating-system resources. |
| `BLOCKED` | The thread is waiting to acquire a monitor lock, such as when entering a `synchronized` block whose monitor another thread holds. |
| `WAITING` | The thread is waiting without a timeout for another action, for example in `join()` on a running thread. |
| `TIMED_WAITING` | The thread is waiting with a time limit, for example during `sleep()`. |
| `TERMINATED` | The thread's execution has ended. It cannot be restarted. |

`RUNNABLE` does not prove that a thread is currently running on a CPU.
`BLOCKED` is specifically about monitor acquisition; it is not the name for every
kind of waiting. Later lessons will demonstrate monitor locks and other waiting
mechanisms when those APIs have been introduced.

For our worker, the usual progression is `NEW`, then `RUNNABLE`, then alternating
between `TIMED_WAITING` and `RUNNABLE` as it sleeps and works, and finally
`TERMINATED`. The samples will not necessarily capture every transition.

A thread can change state between a call to `getState()` and the printing of its
result. Use these snapshots for observation, not as a condition for reading data
or coordinating threads. Main takes a fixed number of samples and uses `join()`
afterward; it does not keep polling until a desired state appears.

### Expected and variable output

One possible run is:

```text
main: before start = NEW
main: sample 1 = RUNNABLE
main: sample 2 = TIMED_WAITING
main: sample 3 = TIMED_WAITING
paced-worker: step 1
main: sample 4 = TIMED_WAITING
main: sample 5 = TIMED_WAITING
paced-worker: step 2
paced-worker: step 3
main: after join = TERMINATED
```

In a normal, uninterrupted run:

- The first line is guaranteed to show `NEW`, because main has not called `start()`.
- The worker's three step messages remain in order.
- Main's five sample messages remain in order.
- The last line is guaranteed to show `TERMINATED`, because `join()` has returned.

The sample states and the relative positions of worker messages can vary.
`TIMED_WAITING` is likely because the worker spends most of its time sleeping, but
no particular intermediate state is required to appear. A sample can even show
`TERMINATED` if the worker finishes before main gets to that sample. Access to
console output may also involve monitor contention and a brief `BLOCKED` state.
There is no guaranteed total running time or exact schedule to match.

## A first look at interruption

Both `sleep()` and `join()` can throw `InterruptedException`. Interruption is a
cooperative signal to a thread; it does not forcibly terminate that thread.

In `main()`, both demos declare `throws InterruptedException`, as in lesson 1.
An interrupted wait therefore propagates out of main instead of being ignored.
If main is interrupted, the normal final output is no longer guaranteed, and
its worker is not automatically cancelled.

The lambda implements `Runnable.run()`, whose signature does not allow it to
declare `InterruptedException`. It must therefore handle that checked exception
inside its body:

```java
catch (InterruptedException exception) {
    Thread.currentThread().interrupt();
    System.out.println(Thread.currentThread().getName() + ": interrupted, stopping");
}
```

When `sleep()` throws `InterruptedException`, it clears the thread's interruption
status. `Thread.currentThread().interrupt()` restores that status. The catch block
is outside the loop, so the worker prints the message and reaches the end of its
task instead of starting another step.

The demos do not send an external interruption signal; this handler makes the
sleeping task respond sensibly if it receives one. The next lesson will examine
how to request cancellation and how working and waiting threads respond.

## Method and constructor reference

This table covers every method called or defined in these demos, including the
ones revisited from lesson 1. Constructors create objects and are listed too.

| Method or constructor | Purpose and behavior |
| --- | --- |
| `main(String[] args)` | The program entry point, executed by the main thread. The demos do not use command-line arguments. `throws InterruptedException` allows an interrupted wait to propagate to the caller. |
| `SumTask(int upperBound)` | Our constructor: stores the inclusive bound for the sum. The demo passes the positive value `1_000_000`. |
| `Runnable.run()` / `SumTask.run()` | Defines a task with no parameters and no returned value. `SumTask` computes and stores a sum; the state demo supplies the task body with a lambda. A direct call does not start a new thread. |
| `SumTask.getResult()` | Our getter: returns the stored `long` total. Call only after joining the worker that executed the task. |
| `Thread(Runnable task, String name)` | Creates a named platform-thread object with a task. Construction does not start it. |
| `Thread.start()` | Starts the thread so it can execute its task independently. Each thread object can be started only once. |
| `Thread.join()` | Waits for the target thread to terminate, unless the calling thread is interrupted. Successful completion provides visibility of the worker's completed actions. This lesson uses the overload without a timeout. |
| `Thread.sleep(long millis)` | Static method that pauses the calling thread for a requested duration, subject to timer precision and scheduling. It can throw `InterruptedException`. |
| `Thread.getState()` | Returns a `Thread.State` snapshot for observation. It does not wait for a transition or coordinate access to task results. |
| `Thread.currentThread()` | Static method returning the thread currently executing the call. In the lambda it returns `paced-worker`. |
| `Thread.getName()` | Returns a thread's name, used to identify worker messages. |
| `Thread.interrupt()` | Sends an interruption signal to the thread object on which it is called. Here the worker calls it on itself to restore its status after catching `InterruptedException`; it does not forcibly stop execution. |
| `System.out.println(String text)` | Prints a line to standard output. `System.out` is a `PrintStream`; `println()` is its method. The `+` expressions build each message before it is printed. |

## Run the examples

The project targets JDK 26. From the project root, compile with Maven:

```sh
mvn compile
```

Run the two entry points separately:

```sh
java -cp target/classes org.generation.italy.lesson02_thread_lifecycle.JoinResultDemo
java -cp target/classes org.generation.italy.lesson02_thread_lifecycle.SleepAndStateDemo
```

Without Maven, compile this lesson with the JDK, then use the same `java` commands:

```sh
javac -d target/classes src/main/java/org/generation/italy/lesson02_thread_lifecycle/*.java
```

In IntelliJ IDEA, use JDK 26 and run `main()` in either demo using the gutter run
button. `SumTask` is a supporting class and has no standalone entry point.

## Check your understanding

1. Why does main read `getResult()` after `join()` rather than after a long sleep?
2. Which thread pauses at each of the two `sleep()` calls in the state demo?
3. Can the sum worker finish before main calls `join()`? What happens then?
4. Which two state observations in the demo are guaranteed?
5. Does observing `RUNNABLE` prove the worker is executing at that exact moment?
6. Which thread may enter `WAITING` when main calls `worker.join()`?
7. Why does the lambda catch `InterruptedException` while main can declare it?

Suggested answers:

1. Joining guarantees completion and visibility of the worker's writes; sleeping
   guarantees neither.
2. The lambda's call pauses the worker; main's call pauses main.
3. Yes. Joining an already terminated thread returns immediately.
4. `NEW` before starting, and `TERMINATED` after a successful join.
5. No. It may be waiting for CPU time, and the snapshot may already be outdated.
6. Main, because it is the thread waiting for the worker's termination. If the
   worker already terminated, main need not wait.
7. `Runnable.run()` does not declare that checked exception; our `main()` methods do.

## Small exercises

1. Change the sum bound to 100 and update the output label. Verify that the result
   is 5050 while keeping the getter after `join()`.
2. Change the worker's sleep duration to 500 milliseconds. Observe the state
   samples, remembering that a particular intermediate state is never guaranteed.
3. Increase the number of state samples. Can you observe `TERMINATED` during
   sampling? Whether you do or not, keep the final `join()` in place.
4. Explain why replacing `join()` with a sleep would lose a correctness guarantee,
   even if one particular run appeared to produce the expected output.
