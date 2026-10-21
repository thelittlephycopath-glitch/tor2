package org.torproject.vpn.utils
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class TaskDebouncerTest {
    private val testDispatcher = StandardTestDispatcher()

    class TestTaskDebouncer(
        dispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1),
        pollTimeoutMs: Long = 500
    ) : TaskDebouncer(dispatcher, pollTimeoutMs) {
        val enqueued = AtomicInteger(0)
        val processed = ConcurrentLinkedQueue<QueuedTask>()

        override fun enqueueTask(taskId: Int, type: String, priority: Priority, block: () -> Unit) {
            Log.d(TAG, "enqueue task $taskId")
            enqueued.incrementAndGet()
            super.enqueueTask(taskId, type, priority, block)
        }

        override fun process(item: QueuedTask) {
            Log.d(TAG, "process task $item")
            super.process(item)
            processed.add(item)
        }
    }

    private var debouncer: TestTaskDebouncer = TestTaskDebouncer(testDispatcher)

    @After
    fun tearDown() {
        debouncer.cancel()
        debouncer = TestTaskDebouncer(testDispatcher)
    }

    @Test
    fun basic_enqueue_processes_single_item() = runTest(testDispatcher) {
        val completed = CompletableDeferred<Unit>()
        debouncer.enqueueTask(1, "A") {
            completed.complete(Unit)
        }
        completed.await()
        assertTrue(completed.isCompleted)
        assertEquals(1, debouncer.enqueued.get())
        assertEquals(1, debouncer.processed.size)
    }

    @Test
    fun newest_per_type_dedupe() = runTest(testDispatcher) {
        val completed = CompletableDeferred<Unit>()

        debouncer.enqueueTask(1, "A") {
            // This task should be replaced by task 2.
        }

        debouncer.enqueueTask(2, "A") {
            completed.complete(Unit)
        }

        // Run the debouncer and wait without blocking the test dispatcher.
        completed.await()

        assertEquals(
            "expected 2 enqueued tasks",
            2,
            debouncer.enqueued.get()
        )

        assertEquals(
            "expected 1 processed tasks",
            1,
            debouncer.processed.size
        )

        assertEquals(
            "expected processed task has id 2",
            2,
            debouncer.processed.first().id
        )

        assertEquals(
            "expected processed task has sequence number 2L",
            2L,
            debouncer.processed.first().seq
        )
    }

    @Test
    fun priority_ordering_across_types() = runTest(testDispatcher) {
        val completed = CompletableDeferred<Unit>()

        debouncer.enqueueTask(1, "low1", Priority.LOW) { }
        debouncer.enqueueTask(2, "med1", Priority.MEDIUM) { }
        debouncer.enqueueTask(3, "high1", Priority.HIGH) { }
        debouncer.enqueueTask(4, "quit", Priority.LOW) {
            completed.complete(Unit)
        }
        debouncer.enqueueTask(5, "notExecuted", Priority.LOW) { }

        completed.await()

        assertEquals(
            "expecting 5 tasks enqueued",
            5,
            debouncer.enqueued.get()
        )

        assertEquals(
            "expecting 4 tasks processed",
            4,
            debouncer.processed.size
        )

        assertEquals(
            "task with priority HIGH expected to be processed first",
            "high1",
            debouncer.processed.poll()?.type
        )

        assertEquals(
            "task with priority MEDIUM expected to be processed second",
            "med1",
            debouncer.processed.poll()?.type
        )

        assertEquals(
            "task with priority LOW expected to be processed third",
            "low1",
            debouncer.processed.poll()?.type
        )

        assertEquals(
            "task with priority LOW expected to be processed fourth",
            "quit",
            debouncer.processed.poll()?.type
        )

        assertEquals(
            "task after quit should not be executed",
            null,
            debouncer.processed.poll()
        )
    }

    @Test
    fun restart_on_enqueue_after_idle() = runTest(testDispatcher) {
        val completed = CompletableDeferred<Unit>()

        debouncer.enqueueTask(1, "A") { }

        advanceUntilIdle()

        debouncer.enqueueTask(2, "B") {
            completed.complete(Unit)
        }

        completed.await()

        assertEquals(
            "expected 2 tasks to be enqueued",
            2,
            debouncer.enqueued.get()
        )

        assertEquals(
            "expected 2 tasks to be processed",
            2,
            debouncer.processed.size
        )
    }

    @Test
    fun cancel_cancels_quickly_during_long_processing() = runTest(testDispatcher) {
        val started = CompletableDeferred<Unit>()

        debouncer.enqueueTask(1, "A") {
            Log.d("TEST", "running task A")
            started.complete(Unit)
            Thread.sleep(1_000)
        }

        debouncer.enqueueTask(2, "B") {
            Log.d("TEST", "running task B")
        }

        // Confirm that A has started before cancelling.
        started.await()

        // Cancel while A is running a thread blocking action.
        debouncer.cancel()

        advanceUntilIdle()

        assertEquals(
            "expected 2 tasks to be queued",
            2,
            debouncer.enqueued.get()
        )

        assertTrue(
            "expected task A to have started",
            started.isCompleted
        )

        assertEquals(
            "expected task A to be executed",
            1,
            debouncer.processed
                .count { task -> task.type == "A" }
        )

        assertEquals(
            "expected task B to be dropped",
            0,
            debouncer.processed
                .count { task -> task.type == "B" }
        )
    }

    @Test
    fun concurrent_enqueues_processing_each_type_dropping_dupes() = runTest(testDispatcher) {
        val types = listOf("A", "B", "C", "D")
        val total = 199
        val id = AtomicInteger(0)
        val completed = CompletableDeferred<Unit>()

        // Launch multiple coroutines to simulate concurrent enqueues.
        repeat(total) {
            launch {
                val type = types[it % types.size]
                val taskId = id.incrementAndGet()

                debouncer.enqueueTask(taskId, type) {
                    Log.d("TEST", "running $taskId")
                }
            }
        }

        launch {
            debouncer.enqueueTask(400, "quit") {
                Log.d("TEST", "quit called!")
                completed.complete(Unit)
            }
        }

        // Run the debouncer until all scheduled work is complete.
        completed.await()

        assertEquals(
            "expected that 200 tasks have been enqueued",
            200,
            debouncer.enqueued.get()
        )

        Log.d("TEST", "processed: ${debouncer.processed}")

        assertEquals(
            "expected that 5 tasks were processed",
            5,
            debouncer.processed.size
        )

        assertTrue(
            "expected that A was processed",
            debouncer.processed.any { task -> task.type == "A" }
        )

        assertTrue(
            "expected that B was processed",
            debouncer.processed.any { task -> task.type == "B" }
        )

        assertTrue(
            "expected that C was processed",
            debouncer.processed.any { task -> task.type == "C" }
        )

        assertTrue(
            "expected that D was processed",
            debouncer.processed.any { task -> task.type == "D" }
        )

        assertTrue(
            "expected that quit was processed",
            debouncer.processed.any { task -> task.type == "quit" }
        )
    }
}
