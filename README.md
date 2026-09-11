# Concurrent programming in Java

A collection of small, runnable lessons for classroom use and independent study.
Each lesson introduces a focused topic through correct examples and a guide to
the concepts and APIs used in the code. Lessons about concurrency problems will
also demonstrate the faulty approach and explain its solution.

## Requirements

- JDK 26, matching the current Maven configuration.
- Maven, if building with Maven. Each lesson also includes commands using
  only the JDK.
- An IDE such as IntelliJ IDEA is optional.

## Lessons

1. [Creating threads explicitly](src/main/java/org/generation/italy/lesson01_thread_creation/README.md):
   extend `Thread`, implement `Runnable`, and provide a `Runnable` with a lambda.
2. [Thread lifecycle: waiting, sleeping, and observing state](src/main/java/org/generation/italy/lesson02_thread_lifecycle/README.md):
   collect a worker's result with `join()`, pause with `sleep()`, and interpret
   thread-state snapshots.
3. [Interruption and cooperative cancellation](src/main/java/org/generation/italy/lesson03_interruption/README.md):
   cancel sleeping and computing workers, preserve interruption signals, and
   understand the difference between inspecting and clearing interruption status.
4. [Race conditions and shared mutable state](src/main/java/org/generation/italy/lesson04_race_conditions/README.md):
   observe lost counter updates, understand why they happen, and protect the
   counter with `synchronized` methods.
5. [Protecting invariants with synchronized blocks](src/main/java/org/generation/italy/lesson05_invariants/README.md):
   keep an account balance nonnegative by protecting a complete withdrawal
   operation with one private lock.
6. [Visibility and volatile stop flags](src/main/java/org/generation/italy/lesson06_visibility/README.md):
   compare ordinary and volatile flag reads, distinguish visibility from atomicity,
   and contain a deliberately faulty worker with a timed wait and daemon status.
7. [The very picky bathroom: wait and notifyAll](src/main/java/org/generation/italy/lesson07_wait_notify/README.md):
   produce ten toilet-paper rolls of each of three colors in random order while
   color-specific consumers wait, wake, and recheck the shared holder.

Start with the lesson README, then read and run its examples in the suggested order.

## Thread-creation convention

From lesson 3 onward, examples that create platform threads explicitly use the
builder API: `Thread.ofPlatform().name("worker").start(task)`. When construction
and startup need to be shown separately, use `.unstarted(task)` followed by the
returned thread's `start()` method. Each lesson explains any API it introduces.

Lessons 1 and 2 retain their introductory thread-creation examples. The
`Thread` constructors remain supported; using builders is this course's modern
style convention, not a claim that constructors are deprecated.

Later lessons will use executors and virtual-thread APIs when appropriate to
their topic. Choosing an execution model depends on the work being taught;
the builder syntax alone does not make a task faster or safer.
