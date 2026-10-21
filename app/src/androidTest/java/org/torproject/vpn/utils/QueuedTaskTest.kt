package org.torproject.vpn.utils

import java.util.concurrent.PriorityBlockingQueue
import kotlin.test.DefaultAsserter.assertEquals
import org.junit.After
import org.junit.Test

class QueuedTaskTest {

    var priorityQueue: PriorityBlockingQueue<QueuedTask> = PriorityBlockingQueue()

    @After
    fun tearDown() {
        priorityQueue = PriorityBlockingQueue()
    }

    @Test
    fun compareTo_higher_seqNr_same_type__invert_order() {
        val task1 = QueuedTask(0, "A", 1L, Priority.MEDIUM) {}
        val task2 = QueuedTask(1, "A", 2L, Priority.MEDIUM) {}
        val task3 = QueuedTask(2, "A", 3L, Priority.MEDIUM) {}

        priorityQueue.offer(task1)
        priorityQueue.offer(task2)
        priorityQueue.offer(task3)

        assertEquals("expected first task with sequence number 3", 3L, priorityQueue.poll()!!.seq)
        assertEquals("expected second task with sequence number 2", 2L, priorityQueue.poll()!!.seq)
        assertEquals("expected third task with sequence number 1", 1L, priorityQueue.poll()!!.seq)
    }

    @Test
    fun compareTo_higher_seqNr_different_type_keep_order() {
        val task1 = QueuedTask(0, "A", 1L, Priority.MEDIUM) {}
        val task2 = QueuedTask(1, "B", 2L, Priority.MEDIUM) {}
        val task3 = QueuedTask(2, "C", 3L, Priority.MEDIUM) {}

        priorityQueue.offer(task1)
        priorityQueue.offer(task2)
        priorityQueue.offer(task3)

        assertEquals("expected first task with sequence number 1", 1L, priorityQueue.poll()!!.seq)
        assertEquals("expected second task with sequence number 2", 2L, priorityQueue.poll()!!.seq)
        assertEquals("expected third task with sequence number 3", 3L, priorityQueue.poll()!!.seq)
    }

    @Test
    fun compareTo_higher_prio__prioritize() {
        val task1 = QueuedTask(0, "A", 1L, Priority.MEDIUM) {}
        val task2 = QueuedTask(1, "B", 2L, Priority.HIGH) {}
        val task3 = QueuedTask(2, "C", 3L, Priority.LOW) {}

        priorityQueue.offer(task1)
        priorityQueue.offer(task2)
        priorityQueue.offer(task3)

        assertEquals("expected first task with sequence number 2", 2L, priorityQueue.poll()!!.seq)
        assertEquals("expected second task with sequence number 1", 1L, priorityQueue.poll()!!.seq)
        assertEquals("expected third task with sequence number 3", 3L, priorityQueue.poll()!!.seq)
    }

    @Test
    fun compareTo_different_prios_and_types__prioritize_and_keep_order_in_same_priority_level() {
        val task1 = QueuedTask(0, "A", 1L, Priority.LOW) {}
        val task2 = QueuedTask(1, "B", 2L, Priority.HIGH) {}
        val task3 = QueuedTask(2, "C", 3L, Priority.HIGH) {}
        val task4 = QueuedTask(3, "D", 4L, Priority.LOW) {}

        priorityQueue.offer(task1)
        priorityQueue.offer(task2)
        priorityQueue.offer(task3)
        priorityQueue.offer(task4)

        assertEquals("expected first task with sequence number 2", 2L, priorityQueue.poll()!!.seq)
        assertEquals("expected second task with sequence number 3", 3L, priorityQueue.poll()!!.seq)
        assertEquals("expected third task with sequence number 1", 1L, priorityQueue.poll()!!.seq)
        assertEquals("expected third task with sequence number 4", 4L, priorityQueue.poll()!!.seq)
    }

    @Test
    fun compareTo_different_prios_same_type__prioritize_and_invert_order_in_same_priority_level() {
        val task1 = QueuedTask(0, "A", 1L, Priority.LOW) {}
        val task2 = QueuedTask(1, "A", 2L, Priority.HIGH) {}
        val task3 = QueuedTask(2, "A", 3L, Priority.HIGH) {}
        val task4 = QueuedTask(3, "B", 4L, Priority.LOW) {}

        priorityQueue.offer(task1)
        priorityQueue.offer(task2)
        priorityQueue.offer(task3)
        priorityQueue.offer(task4)

        assertEquals("expected first task with sequence number 3", 3L, priorityQueue.poll()!!.seq)
        assertEquals("expected second task with sequence number 2", 2L, priorityQueue.poll()!!.seq)
        assertEquals("expected third task with sequence number 1", 1L, priorityQueue.poll()!!.seq)
        assertEquals("expected third task with sequence number 4", 4L, priorityQueue.poll()!!.seq)
    }

    @Test
    fun compareTo_same_prio_different_types__group_by_type_inverted_order_by_seq_number() {
        val task1 = QueuedTask(0, "B", 1L, Priority.MEDIUM) {}
        val task2 = QueuedTask(1, "A", 2L, Priority.MEDIUM) {}
        val task3 = QueuedTask(2, "B", 3L, Priority.MEDIUM) {}
        val task4 = QueuedTask(3, "A", 4L, Priority.MEDIUM) {}

        priorityQueue.offer(task1)
        priorityQueue.offer(task2)
        priorityQueue.offer(task3)
        priorityQueue.offer(task4)

        assertEquals("expected first task with sequence number 3", 3L, priorityQueue.poll()!!.seq)
        assertEquals("expected second task with sequence number 1", 1L, priorityQueue.poll()!!.seq)
        assertEquals("expected third task with sequence number 4", 4L, priorityQueue.poll()!!.seq)
        assertEquals("expected third task with sequence number 2", 2L, priorityQueue.poll()!!.seq)
    }
}
