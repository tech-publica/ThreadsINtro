# Lesson 4: Race conditions and shared mutable state

## Goal

Understand how two threads can lose updates to a shared counter, then protect
the counter using `synchronized` methods. This is a lesson about a concurrency
problem, so it deliberately starts with faulty code and follows with its solution.

The earlier lessons focused on creating workers, waiting for results, and
cancelling tasks. Now both workers will modify the **same object while running**.
Waiting for them to finish is necessary, but it is not enough to make their
updates correct.

## Before you begin

Complete lessons [1](../lesson01_thread_creation/README.md),
[2](../lesson02_thread_lifecycle/README.md), and
[3](../lesson03_interruption/README.md). You should recognize `Runnable` lambdas,
the platform-thread builder, and `join()`. You will also use instance fields,
getters, loops, and the increment operator `++`.

By the end of this lesson, you should be able to:

- Identify which data is shared and which variables belong to each execution.
- Explain how two increments can produce only one increase.
- Distinguish a race condition from harmless variation in console-output order.
- Protect a shared counter with `synchronized` instance methods.
- Explain why the same monitor must protect all accesses to shared state.
- Explain why neither `join()` nor a `volatile` field makes `count++` atomic.
- Recognize that a successful run of faulty code does not prove correctness.

## Files and suggested order

| Class | Role |
| --- | --- |
| [UnsafeCounter](UnsafeCounter.java) | Intentionally faulty counter with an ordinary `increment()` and getter. |
| [RaceConditionDemo](RaceConditionDemo.java) | Runs two workers against one unsafe counter and compares expected and actual totals. |
| [SynchronizedCounter](SynchronizedCounter.java) | Correct counter that protects its field with synchronized methods. |
| [SynchronizedCounterDemo](SynchronizedCounterDemo.java) | Runs the same workload using the protected counter. |

First read the unsafe counter and run its demo. Then study the lost-update
walkthrough below, read the synchronized counter, and run the corrected demo.
Only the two classes ending in `Demo` have a `main()` entry point.

## Shared mutable state

**State** is data describing an object's current condition. Our counter's state
is its `count` field. It is **mutable** because increments change that value,
and **shared** because both workers access the same counter object.

Each demo creates exactly one counter and gives both workers the same task:

```java
Runnable incrementTask = () -> {
    for (int iteration = 0; iteration < incrementsPerWorker; iteration++) {
        counter.increment();
    }
};

Thread firstWorker = Thread.ofPlatform().name("counter-1").start(incrementTask);
Thread secondWorker = Thread.ofPlatform().name("counter-2").start(incrementTask);
```

Sharing the `Runnable` does not make the workers take turns. Each worker executes
the task independently. Their loop variables are local to their respective
executions, but both use the same captured `counter` reference.

The lambda captures `counter` and `incrementsPerWorker`. These local variables
are **effectively final**: we do not reassign them after initialization. Java
allows a lambda to capture such local variables. An unchanged reference does
not make the referenced object immutable; its `count` field still changes.

Each worker performs 1,000,000 increments, so the expected total is 2,000,000.
The values fit within `int`, so integer overflow is not the issue in this example.

## Example 1: The unsafe counter

`UnsafeCounter` starts at zero because Java initializes an instance `int` field
to `0`. Its update is short and familiar:

```java
public void increment() {
    count++;
}
```

One line of Java is not necessarily one **atomic operation**. Atomicity means an
operation is indivisible with respect to the competing accesses we care about.
Here, `count++` conceptually performs three actions:

1. Read the current value of `count`.
2. Add one to the value read.
3. Write the computed value back into `count`.

Although an individual ordinary `int` read or write is atomic, the combination
of reading, adding, and writing is not. Another thread may act between those
actions. Declaring the field `private` controls which code can access it; it does
not prevent two threads from calling its methods at the same time.

### How an update gets lost

Suppose the counter is `0` and each worker increments once. This is one possible
interleaving of their actions:

| Step | Worker 1 | Worker 2 | Shared `count` |
| --- | --- | --- | --- |
| 1 | Reads `0` | | `0` |
| 2 | | Reads `0` | `0` |
| 3 | Computes `0 + 1 = 1` | | `0` |
| 4 | | Computes `0 + 1 = 1` | `0` |
| 5 | Writes `1` | | `1` |
| 6 | | Writes `1` | `1` |

Both workers executed an increment, yet the counter increased only once. Worker
2 overwrote worker 1's update with a value based on the same earlier read.
This is a **lost update**.

The table illustrates the failure; it is not a claim that the JVM always
executes exactly this schedule or translates `++` into exactly three machine
instructions. The unsafe code also lacks visibility and ordering guarantees
between the workers. Compiler optimizations and scheduling can affect what we
observe, so the table is an explanation of one failure, not a full execution model.

A **race condition** occurs when correctness depends on how concurrent actions
are ordered or interleaved. This example also has a **data race**: conflicting
accesses to the same field, including writes, without the necessary synchronization
between the workers. These terms are related, but not interchangeable in every
program. Even individually synchronized operations can form an unsafe compound
action if the whole action is not protected.

### Reading the output

One possible run is:

```text
Intentionally unsafe shared counter
Expected: 2000000
Actual:   1378241
Updates were lost even though both workers finished.
```

The actual total is illustrative. It can change between runs, and it may even
match the expected total. The demo explicitly reports that a matching result
does not make the code safe. For example, the first worker might finish before
the second gets to execute much of its loop.

Running more iterations or repeating the program can help expose the problem,
but no chosen iteration count guarantees a visible failure. We do not insert
sleeps, yields, or coordination APIs to force the schedule from the table.

Both demos print only from main after joining both workers. Printing from every
iteration would add output overhead and synchronization that influence scheduling
and distract from the counter itself. These demos are not performance benchmarks.

### Why join() cannot repair this

Main starts both workers before joining either one. The joins establish their
termination and make their completed writes visible to main, as in lesson 2.
However, they do not make the workers' increments mutually exclusive while the
workers are running. An update already overwritten is still lost after a join.

The final getter in the unsafe demo is read after both workers finish; the
deliberate error is the competing, unsynchronized updates during their execution.
Joining fixes neither that data race nor the resulting incorrect total.

## Example 2: Protect the counter with synchronized

`SynchronizedCounter` changes the methods that access the field:

```java
public synchronized void increment() {
    count++;
}

public synchronized int getCount() {
    return count;
}
```

`synchronized` is a Java keyword, not a method or imported class. Every Java
object has an associated **monitor**, also called its intrinsic lock. Entering
a synchronized **instance method** acquires the monitor of the receiving object:
here, the counter on which the method was called, also known as `this`.

Only one thread can hold that monitor at a time. A competing worker must wait
to acquire it before entering a method synchronized on the same object. While
waiting for the monitor, it may be in the `BLOCKED` state introduced in lesson 2.

The protected method body is a **critical section**. It keeps the entire
read–modify–write operation together with respect to other accesses protected
by that monitor. The lock is released automatically when the method exits,
including if it exits by throwing an exception.

Synchronization also provides visibility: releasing a monitor happens-before
a later acquisition of that same monitor. The next worker therefore sees the
updates made under the previous holder's protection.

### Why the same object matters

Both workers call `increment()` on the **same counter**, so both must acquire
the same monitor. The getter acquires that monitor too. Java does not lock all
objects of a class when one synchronized instance method runs.

Using two unrelated locks around accesses to the same field would not establish
this mutual exclusion. Likewise, an unsynchronized method could bypass the
counter's protection. Our field is private, and both methods accessing it follow
the same locking rule.

The getter's synchronization is not strictly needed for main's one read after
both joins, because the joins already provide completion and visibility. It
keeps the counter's own contract consistent and permits safe individual reads
even while increments are occurring. Such a read is only a snapshot; it does
not promise that all increments have finished.

### Expected output

```text
Synchronized shared counter
Expected: 2000000
Actual:   2000000
```

In a normal run, both finite worker loops complete and main successfully joins
them. Each protected increment contributes one increase, giving the expected
total. Workers need not alternate or receive equal turns; mutual exclusion does
not guarantee fair scheduling.

If main is interrupted while joining, `InterruptedException` propagates from
`main()` and the normal final output is not guaranteed. These workers perform
finite tasks and finish independently. Unlike lesson 3's repeating workers,
they do not require a cancellation request to finish their work.

## Why not make count volatile?

`volatile` is a Java field modifier that provides visibility and ordering
guarantees for accesses to that field. It does not provide mutual exclusion for
a sequence of operations. Even with a volatile counter, two workers could both
read `0` and both write `1`.

For this lesson, the required property is that the **whole increment** is
protected. We obtain it with `synchronized`. Later lessons will examine visibility
in more detail and introduce atomic counter classes.

## Classes, methods, and constructors

No additional libraries are required. The custom counter classes hold the state;
`Thread` and `Runnable` come from the automatically imported `java.lang` package.

| API or member | Explanation |
| --- | --- |
| `main(String[] args)` | Each demo's entry point. Arguments are unused. `throws InterruptedException` allows an interrupted join to propagate. |
| `UnsafeCounter()` | The compiler-provided no-argument constructor creates the deliberately unsafe counter. Its `count` field initially holds `0`. |
| `UnsafeCounter.increment()` | Adds one using an unprotected `count++`; concurrent calls can lose updates. |
| `UnsafeCounter.getCount()` | Returns the field without synchronization. The demo reads it only after both joins. |
| `SynchronizedCounter()` | The compiler-provided no-argument constructor creates the protected counter, initially at `0`. |
| `SynchronizedCounter.increment()` | Acquires this counter's monitor and performs one complete increment while holding it. Returns no value. |
| `SynchronizedCounter.getCount()` | Acquires the same monitor and returns the current `int` value. |
| `Runnable.run()` | The task contract implemented by the lambda. Both workers execute its loop independently; it accepts no arguments and returns no value. |
| `Thread.ofPlatform()` | Returns a `Thread.Builder.OfPlatform` used to configure platform threads. |
| `Thread.Builder.OfPlatform.name(String name)` | Sets the name for the thread to be created and returns the builder for the next call. |
| `Thread.Builder.start(Runnable task)` | Creates and starts a thread with the given task, then returns the `Thread`. No additional `worker.start()` call is needed. |
| `Thread.join()` | Makes the calling thread wait for the referenced worker to terminate, unless the caller is interrupted. Provides visibility of completed worker actions. |
| `System.out.println(String text)` | Prints each result line through standard output's `PrintStream`. The `+` expressions combine labels and numbers. |

The builder configures creation; the returned `Thread` represents the worker.
The method bodies of both counters deliberately remain very small so their
synchronization difference is easy to inspect.

## Run the examples

The project targets JDK 26. From the project root, compile with Maven:

```sh
mvn compile
```

Run the faulty example first, then the corrected example:

```sh
java -cp target/classes org.generation.italy.lesson04_race_conditions.RaceConditionDemo
java -cp target/classes org.generation.italy.lesson04_race_conditions.SynchronizedCounterDemo
```

Without Maven, compile this lesson using the JDK, then use the same run commands:

```sh
javac -d target/classes src/main/java/org/generation/italy/lesson04_race_conditions/*.java
```

In IntelliJ IDEA, use JDK 26 and run either demo's `main()` using the gutter
button. The supporting counter classes are not standalone entry points.

## Check your understanding

1. Why does each worker have its own `iteration` variable but share `count`?
2. How can two increments from zero produce a final value of one?
3. Does obtaining 2,000,000 once prove the unsafe counter is correct?
4. Why do the joins not prevent lost updates?
5. Which object's monitor protects `SynchronizedCounter.increment()`?
6. Does `synchronized` make workers take turns in a fixed order?
7. Would `volatile int count` make `count++` atomic?
8. Would calling `getCount()` and then `increment()` be one atomic operation
   just because each method is synchronized?

Suggested answers:

1. The loop variable belongs to each execution; both executions access the
   field through the same captured counter reference.
2. Both can read zero and later write one, overwriting one another's update.
3. No. A particular schedule can hide the faulty interleaving.
4. Joins establish completion and visibility to main, not exclusive access
   between the workers during their updates.
5. The receiving counter object, `this`; both workers use that same object.
6. No. It protects exclusive access, not turn order or fairness.
7. No. Visibility of field accesses does not make the combined operation atomic.
8. No. The lock is released between the two calls, allowing another thread to
   act between them. A compound action needs protection covering the whole action.

## Small exercises

1. Run both demos several times. Record the unsafe totals, but do not require
   every unsafe run to fail. Check that the synchronized total matches each time.
2. Add a third worker to the synchronized demo using the same task and counter.
   Start all three before joining, join all three, and update the expected total.
3. Draw a valid interleaving where two unsafe increments both succeed, and
   compare it with the lost-update table.
4. Explain why changing the captured reference to `final` would not protect
   the counter's mutable field.

The solution here protects one operation on one object. Later lessons will build
on this to protect relationships between values and explore how incorrect locking
can cause problems of its own.
