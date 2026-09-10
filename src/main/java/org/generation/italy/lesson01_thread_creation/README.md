# Lesson 1: Creating threads explicitly

## Goal

Learn how to create and start Java threads using three traditional approaches:
extending `Thread`, passing a separate `Runnable` object to a thread, and passing
a lambda expression as the `Runnable`.

These examples use explicitly created **platform threads**: Java threads that are
typically backed by operating-system threads. Later lessons will introduce other
ways to arrange for tasks to execute. `Runnable` and lambdas remain useful in
modern concurrent Java code; they are not obsolete APIs.

## Before you begin

You should be comfortable with classes, constructors, inheritance, interfaces,
method overriding, `for` loops, and a Java `main()` method. No previous concurrency
knowledge is required. The lambda syntax used here is explained below.

By the end of this lesson, you should be able to:

- Create workers using each of the three approaches.
- Distinguish a task from the thread executing it.
- Explain the difference between `start()` and a direct call to `run()`.
- Start multiple workers and wait for their completion using `join()`.
- Identify what is predictable about the output and what the scheduler decides.

## The example: two workers counting

Each demo runs the same activity: two workers, named `worker-1` and `worker-2`,
each print the numbers from 1 to 5. The main thread announces the start, starts
both workers, waits for both to finish, and prints a completion message.

A **thread** is a path of execution within a running program. In these demos,
`main()` is already executing on a thread when the program begins. Starting the
two workers gives the program two additional paths of execution.

**Concurrency** means activities can make progress during overlapping periods.
They may take turns on one CPU core. **Parallelism** means activities actually
execute at the same time, which requires suitable hardware and scheduling.
Starting two threads permits concurrency; it does not guarantee simultaneous
execution or that the work will become faster.

Read the examples in this order:

| Demo | Supporting class | What changes |
| --- | --- | --- |
| [ThreadSubclassDemo.java](ThreadSubclassDemo.java) | [CountingThread.java](CountingThread.java) | The worker is a subclass of `Thread`. |
| [RunnableClassDemo.java](RunnableClassDemo.java) | [CountingTask.java](CountingTask.java) | A separate object describes the task. |
| [LambdaThreadDemo.java](LambdaThreadDemo.java) | None | A lambda describes the task inline. |

The small amount of repeated code keeps each demo independently readable and
makes the thread-creation approaches easy to compare.

## 1. Extend Thread

`CountingThread` extends `Thread` and overrides its `run()` method:

```java
public class CountingThread extends Thread {
    public CountingThread(String name) {
        super(name);
    }

    @Override
    public void run() {
        for (int number = 1; number <= 5; number++) {
            System.out.println(Thread.currentThread().getName() + ": " + number);
        }
    }
}
```

`super(name)` calls the `Thread` constructor that assigns a name. `@Override`
asks the compiler to check that the method overrides an inherited method.
The body of `run()` is the work the new thread will perform.

In `ThreadSubclassDemo`, constructing `new CountingThread("worker-1")` creates
the thread object. Calling `start()` schedules that thread to execute its `run()`
method. The main thread can continue while the worker executes.

This approach combines the task and its execution mechanism in one class.
It also uses the class's single opportunity to extend another class: Java classes
cannot extend both `Thread` and a different superclass.

## 2. Implement Runnable in a separate class

`Runnable` is an interface with one abstract method: `void run()`. It describes
an activity that takes no arguments and returns no result.

`CountingTask` implements that interface, placing the counting loop in `run()`.
The demo then supplies a task and a name to each thread:

```java
Runnable firstTask = new CountingTask();
Thread firstWorker = new Thread(firstTask, "worker-1");
firstWorker.start();
```

There are now two distinct objects with different responsibilities:

- The `CountingTask` object describes **what to do**.
- The `Thread` object provides **a thread on which to do it**.

Creating a `Runnable` does not create or start a thread. When the thread starts,
its execution invokes the supplied task's `run()` method.

This separation is usually a useful design choice: the task can extend another
class if needed, and can later be submitted to an executor without becoming a
`Thread` subclass. Executors will be introduced in a later lesson.

## 3. Supply Runnable with a lambda

A **functional interface** has one abstract method. Because `Runnable` is a
functional interface, Java lets us supply its behavior using a lambda:

```java
Thread firstWorker = new Thread(() -> {
    for (int number = 1; number <= 5; number++) {
        System.out.println(Thread.currentThread().getName() + ": " + number);
    }
}, "worker-1");
```

Read `() -> { ... }` as: “a task with no parameters that executes this block.”

- `()` matches the empty parameter list of `Runnable.run()`.
- `->` separates the parameters from the body.
- `{ ... }` supplies the body of `run()`. It returns no value.

The constructor expects a `Runnable`, so the compiler knows which interface
the lambda implements. The lambda does not start executing just because the
`Thread` constructor receives it. The demo still calls `start()` explicitly.

This has the same threading behavior as the separate `CountingTask` class.
A lambda is convenient for a short task used locally; a named class is useful
when the task deserves its own name, state, or reusable implementation.

## The APIs used in all three examples

| API | Meaning in this lesson |
| --- | --- |
| `Thread(String name)` | Constructs a thread with a name; called through `super(name)` in our subclass. |
| `Thread(Runnable task, String name)` | Constructs a named thread with a task to execute. |
| `run()` | Contains the task's work. A direct call executes on the calling thread. |
| `start()` | Schedules a new thread to execute its work; does not wait for that work to finish. |
| `join()` | Makes the calling thread wait until the target thread terminates, unless the wait is interrupted. |
| `Thread.currentThread()` | Returns the thread currently executing this line of code. |
| `getName()` | Returns a thread's name, used here to identify the source of each output line. |

### Why start() and run() are different

If the main thread directly calls `firstWorker.run()`, the body executes on the
main thread, just like any ordinary method call. No new thread starts.
Calling a task's `run()` directly has the same property.

Our demos call `start()` to request execution on a new thread. Each `Thread`
object can be started only once, including after its work has finished. Starting
it again throws `IllegalThreadStateException`. To start another worker, create
another `Thread` object.

### Why both starts come before both joins

Every demo follows this sequence:

```java
firstWorker.start();
secondWorker.start();

firstWorker.join();
secondWorker.join();
```

The two `start()` calls allow both workers to run. Then the main thread waits.
Waiting for `firstWorker` does not prevent `secondWorker` from making progress.
If a worker has already terminated, joining it returns immediately.

If we joined the first worker before starting the second, the second worker
would not start until the first had finished. That would serialize these tasks.

`join()` declares `InterruptedException`: the thread doing the waiting may be
asked to stop waiting. For this introductory example, `main()` declares
`throws InterruptedException`, allowing an interruption to propagate rather than
silently swallowing it. This keeps the lifecycle code visible. It is not a
complete cancellation policy: interrupting the main thread does not automatically
stop the workers. These workers finish their short loops independently; a later
lesson will explain cooperative interruption and cancellation.

## Run the examples

The project currently targets JDK 26. Run the following commands from the project
root, the directory containing `pom.xml`.

Compile with Maven:

```sh
mvn compile
```

Then run each demo separately:

```sh
java -cp target/classes org.generation.italy.lesson01_thread_creation.ThreadSubclassDemo
java -cp target/classes org.generation.italy.lesson01_thread_creation.RunnableClassDemo
java -cp target/classes org.generation.italy.lesson01_thread_creation.LambdaThreadDemo
```

If you do not have Maven, compile this lesson using the JDK instead, then use the
same `java` commands above:

```sh
javac -d target/classes src/main/java/org/generation/italy/lesson01_thread_creation/*.java
```

In IntelliJ IDEA, open the project as a Maven project, select JDK 26 as the project
SDK, and run the `main()` method in each demo using the gutter run button.
The supporting `CountingThread` and `CountingTask` classes do not have a `main()`
method and are not standalone entry points.

## Understand the output

One possible run is:

```text
main: starting workers
worker-1: 1
worker-2: 1
worker-2: 2
worker-1: 2
worker-1: 3
worker-2: 3
worker-2: 4
worker-1: 4
worker-1: 5
worker-2: 5
main: both workers finished
```

In a normal, uninterrupted run, these properties hold:

- The main thread's starting message comes first.
- Each worker prints 1 through 5 in order, once each.
- The main thread's completion message comes last, because both joins completed.
- There are twelve output lines: ten worker lines and two main-thread messages.

The relative order of lines from different workers is not guaranteed. Even
though `worker-1` is started first, `worker-2` may print first or finish first.
One worker may print all five lines before the other prints anything. These
tasks are deliberately short, so you may see the same ordering on many runs.
That is also valid; visible alternation is not required for concurrent code.

The scheduler decides when runnable threads execute. We do not use `sleep()`
to influence the output: a delay would not establish a reliable order between
workers. `join()` provides the completion coordination we actually need.

Each invocation of `run()` has its own local `number` variable. The workers do
not update a shared counter, so this is not a race-condition demonstration.
Console output helps us observe execution, but its timing is not a measurement
of concurrency performance.

## Check your understanding

1. Which thread executes the code before the first `start()` call?
2. Which method requests a new thread of execution: `run()` or `start()`?
3. Does the first `start()` call guarantee that worker prints first?
4. While the main thread is waiting in `firstWorker.join()`, can the second
   worker continue?
5. What is the difference between a `CountingTask` and a `Thread`?
6. Why can the lambda take the place of a `CountingTask` instance?

Suggested answers: (1) the main thread; (2) `start()`; (3) no, scheduling decides;
(4) yes; (5) the task describes the work, while the thread executes it;
(6) `Runnable` is a functional interface, and the lambda supplies its `run()` body.

## Small exercises

1. Run each demo several times. Compare the output against the guarantees above,
   even if its ordering never changes.
2. Add a third named worker to one demo. Start all three before joining them,
   and keep the completion message after all three joins.
3. Change the counting limit to 10 consistently in a demo. Explain why each
   worker still has its own loop counter.

There is no shared-state bug to repair in these examples. Later lessons will
deliberately introduce races and deadlocks, then explain their solutions.
