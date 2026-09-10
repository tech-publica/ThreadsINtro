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

Start with the lesson README, then read and run its examples in the suggested order.
