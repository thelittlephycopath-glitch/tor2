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
        val executed = Collections.synchronizedList(mutableListOf<Int>())
        val finished = CountDownLatch(1)

        try {
            repeat(40) { id ->
                debouncer.enqueueTask(taskId = id, type = "refresh") {
                    executed.add(id)
                    finished.countDown()
                }
            }

            assertTrue("Latest task should execute", finished.await(5, TimeUnit.SECONDS))
            Thread.sleep(100)
            assertEquals(listOf(39), executed.toList())
        } finally {
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
        val failure = AtomicReference<Throwable?>(null)

        try {
            debouncer.enqueueTask(taskId = -1, type = "blocker") {
                blockerStarted.countDown()
                check(releaseBlocker.await(5, TimeUnit.SECONDS)) { "Timed out waiting to release blocker" }
            }
            assertTrue("Blocking task should start", blockerStarted.await(5, TimeUnit.SECONDS))

            repeat(4) { producer ->
                Thread {
                    try {
                        repeat(20) { index ->
                            val id = producer * 20 + index
                            debouncer.enqueueTask(taskId = id, type = "same-type") {
                                executedIds.add(id)
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
        } finally {
            debouncer.cancel()
        }
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
