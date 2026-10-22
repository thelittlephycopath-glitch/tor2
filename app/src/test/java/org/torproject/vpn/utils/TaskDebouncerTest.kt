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
    fun concurrentProducersKeepOnePendingTaskPerType() {
        val debouncer = TaskDebouncer(pollTimeoutMs = 20L)
        val start = CountDownLatch(1)
        val producersDone = CountDownLatch(4)
        val executed = AtomicInteger(0)
        val failure = AtomicReference<Throwable?>(null)

        try {
            repeat(4) { producer ->
                Thread {
                    try {
                        start.await()
                        repeat(20) { index ->
                            debouncer.enqueueTask(
                                taskId = producer * 20 + index,
                                type = "same-type"
                            ) {
                                executed.incrementAndGet()
                            }
                        }
                    } catch (t: Throwable) {
                        failure.compareAndSet(null, t)
                    } finally {
                        producersDone.countDown()
                    }
                }.start()
            }

            start.countDown()
            assertTrue("Producers should finish", producersDone.await(5, TimeUnit.SECONDS))
            // The debouncer delays its first drain, so the concurrent submissions settle first.
            Thread.sleep(400)
            assertEquals(null, failure.get())
            assertEquals(1, executed.get())
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
