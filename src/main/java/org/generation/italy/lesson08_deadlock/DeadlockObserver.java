package org.generation.italy.lesson08_deadlock;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;

/** Diagnostic support, kept separate from the two-lock example. */
final class DeadlockObserver {

    private DeadlockObserver() {
    }

    static void report() throws InterruptedException {
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        for (int attempt = 0; attempt < 30; attempt++) {
            long[] deadlockedIds = threads.findMonitorDeadlockedThreads();
            if (deadlockedIds != null) {
                System.out.println("main: JVM detected a monitor deadlock:");
                for (ThreadInfo info : threads.getThreadInfo(deadlockedIds)) {
                    if (info != null) {
                        System.out.println("  " + info.getThreadName() + " waits for "
                                + info.getLockOwnerName() + "; state=" + info.getThreadState());
                    }
                }
                return;
            }
            Thread.sleep(100);
        }
        System.out.println("main: no monitor deadlock detected during this observation window");
    }
}
