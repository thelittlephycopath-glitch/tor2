package org.torproject.vpn.utils

import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskDebouncerTest {
    @Test
    fun newestPendingTaskOfSameTypeWins() {
        val debouncer = TaskDebouncer(pollTimeoutMs = 20L)
        val blockerStarted = CountDownLatch(1)
        val releaseBlocker = CountDownLatch(1)
        val executed = Collections.synchronizedList(mutableListOf<Int>())
        val finished = CountDownLatch(1)

        try {
            // Hold the single consumer so every refresh remains pending until the final enqueue.
            debouncer.enqueueTask(taskId = -1, type = "blocker") {
                blockerStarted.countDown()
                check(releaseBlocker.await(5, TimeUnit.SECONDS)) {
                    "Timed out waiting to release blocker"
                }
            }
            assertTrue("Blocking task should start", blockerStarted.await(5, TimeUnit.SECONDS))

            repeat(40) { id ->
                debouncer.enqueueTask(taskId = id, type = "refresh") {
                    executed.add(id)
                    finished.countDown()
                }
            }
            releaseBlocker.countDown()

            assertTrue("Latest task should execute", finished.await(5, TimeUnit.SECONDS))
            assertEquals("Only the newest pending item should run", listOf(39), executed.toList())
        } finally {
            releaseBlocker.countDown()
            debouncer.cancel()
        }
    }

    @Test
    fun concurrentProducersKeepOnlyNewestPendingTaskPerType() {
        val debouncer = TaskDebouncer(pollTimeoutMs = 20L)
        val blockerStarted = CountDownLatch(1)
        val releaseBlocker = CountDownLatch(1)
        val producersDone = CountDownLatch(4)
        val executedIds = Collections.synchronizedList(mutableListOf<Int>())
        val enqueueLock = Any()
        val lastEnqueuedId = AtomicInteger(Int.MIN_VALUE)
        val failure = AtomicReference<Throwable?>(null)

        try {
            debouncer.enqueueTask(taskId = -1, type = "blocker") {
                blockerStarted.countDown()
                check(
                    releaseBlocker.await(5, TimeUnit.SECONDS)
                ) {
                    "Timed out waiting to release blocker"
                }
            }
            assertTrue("Blocking task should start", blockerStarted.await(5, TimeUnit.SECONDS))

            repeat(4) { producer ->
                Thread {
                    try {
                        repeat(20) { index ->
                            val id = producer * 20 + index
                            // Keep the test's producer order deterministic so the expected
                            // winner is exactly the final accepted enqueue while the consumer is blocked.
                            synchronized(enqueueLock) {
                                debouncer.enqueueTask(taskId = id, type = "same-type") {
                                    executedIds.add(id)
                                }
                                lastEnqueuedId.set(id)
                            }
                        }
                    } catch (t: Throwable) {
                        failure.compareAndSet(null, t)
                    } finally {
                        producersDone.countDown()
                    }
                }.start()
            }

            assertTrue("Producers should finish", producersDone.await(5, TimeUnit.SECONDS))
            assertEquals(null, failure.get())
        } finally {
            releaseBlocker.countDown()
        }

        try {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (executedIds.isEmpty() && System.nanoTime() < deadline) {
                Thread.sleep(10)
            }
            assertEquals("Only the newest pending task should execute", 1, executedIds.size)
            assertEquals(
                "The final accepted enqueue must win while all tasks are pending",
                lastEnqueuedId.get(),
                executedIds.single()
            )
        } finally {
            debouncer.cancel()
        }
    }

    @Test
    fun cancelWhileConsumerIsBlockedDropsPendingWork() {
        val debouncer = TaskDebouncer(pollTimeoutMs = 20L)
        val blockerStarted = CountDownLatch(1)
        val releaseBlocker = CountDownLatch(1)
        val pendingExecutions = AtomicInteger(0)

        try {
            debouncer.enqueueTask(taskId = -1, type = "blocker") {
                blockerStarted.countDown()
                releaseBlocker.await(5, TimeUnit.SECONDS)
            }
            assertTrue("Blocking task should start", blockerStarted.await(5, TimeUnit.SECONDS))

            repeat(20) { id ->
                debouncer.enqueueTask(taskId = id, type = "pending-$id") {
                    pendingExecutions.incrementAndGet()
                }
            }

            debouncer.cancel()
        } finally {
            releaseBlocker.countDown()
            debouncer.cancel()
        }

        Thread.sleep(400)
        assertEquals("Pending work must be dropped on cancellation", 0, pendingExecutions.get())
    }

    @Test
    fun cancelDropsQueuedTasksAndRejectsLaterEnqueues() {
        val debouncer = TaskDebouncer(pollTimeoutMs = 20L)
        val executed = AtomicInteger(0)

        debouncer.enqueueTask(taskId = 1, type = "queued") {
            executed.incrementAndGet()
        }
        debouncer.cancel()
        debouncer.enqueueTask(taskId = 2, type = "after-cancel") {
            executed.incrementAndGet()
        }

        Thread.sleep(400)
        assertEquals(0, executed.get())
    }
}
