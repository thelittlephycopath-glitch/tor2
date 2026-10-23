package org.torproject.vpn.utils

import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Priority levels for queued tasks.
 */
enum class Priority { LOW, MEDIUM, HIGH }

/**
 * A queued task item.
 *
 * @param id identifier for the task (for logging/traceability)
 * @param type task type used for newest-per-type deduplication and single-running-type claim
 * @param seq monotonic sequence number assigned when enqueued (higher = newer)
 * @param priority task priority; higher priority items are ordered first
 * @param block work to execute when this item is processed (synchronous/blocking callback)
 */
data class QueuedTask(
    val id: Int,
    val type: String,
    val seq: Long,
    val priority: Priority = Priority.MEDIUM,
    val block: () -> Unit
) : Comparable<QueuedTask> {
    /**
     * Implements the compare function for use in the [PriorityBlockingQueue] the following way:
     * - higher prio gets sorted to front
     * - same type, same prio: higher sequence number (newer QueuedTask) gets sorted to front
     * - different type, same prio: smaller sequence number (older QueuedTask) gets sorted to font
     */
    override fun compareTo(other: QueuedTask): Int {
        val p = other.priority.ordinal.compareTo(this.priority.ordinal)
        return if (p != 0) {
            p
        } else if (this.type == other.type) {
            other.seq.compareTo(this.seq)
        } else {
            this.seq.compareTo(other.seq)
        }
    }
}

/**
 * TaskDebouncer is a single-consumer debouncer that:
 *  - keeps only the newest item per logical type (newest-per-type deduplication),
 *  - orders items by [Priority] (HIGH first) and then by newest (highest) sequence,
 *  - allows only one running task at a time,
 *  - uses a cancellable coroutine consumer that hibernates when idle to save resources.
 *
 * Usage:
 *  - Call [enqueueTask] to submit work; the consumer will be started automatically.
 *  - Call [cancel] to cancel the consumer and clear queued state.
 *
 * @param pollTimeoutMs poll timeout in milliseconds for the underlying blocking queue; determines how long the
 *                      consumer waits for items before attempting to hibernate.
 */
open class TaskDebouncer(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1),
    private val pollTimeoutMs: Long = 1000L
) {
    companion object {
        val TAG = TaskDebouncer::class.simpleName
    }

    @Volatile private var consumerJob: Job? = null

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    // Reference of currently running task type (null when no task is running)
    private val runningType = AtomicReference<String?>(null)

    // Map of the newest enqueued items per type
    private val latestByType = ConcurrentHashMap<String, QueuedTask>()

    // thread safe priority queue holding tasks to be processed
    private val queue = PriorityBlockingQueue<QueuedTask>()
    private val counter = AtomicLong(0L)

    // Signal that a producer has enqueued work; used to avoid lost-wakeup on consumer hibernate/exit
    private val hasTask = AtomicBoolean(false)
    private val isCancelled = AtomicBoolean(false)
    private val lifecycleLock = Any()

    // Android's local JVM test stubs throw when Log methods are called. Keep logging
    // best-effort so a missing Android runtime can never break queue processing.
    private fun logDebug(message: String) {
        runCatching { Log.d(TAG, message) }
    }

    private fun logError(message: String, error: Throwable) {
        runCatching { Log.e(TAG, message, error) }
    }

    // Start consumer job.
    private fun start() {
        if (consumerJob?.isActive == true) return
        consumerJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                // debounce initial event handling of the priority queue by 250 ms
                delay(250)

                while (true) {
                    try {
                        // cooperative cancellation point
                        ensureActive()

                        // poll with timeout on a blocking queue in IO dispatcher
                        val polled = withContext(Dispatchers.IO) {
                            queue.poll(pollTimeoutMs, TimeUnit.MILLISECONDS)
                        } ?: run {
                            // no immediate item -> prepare to exit if still idle
                            hasTask.set(false)
                            // Clear the producer hasWork signal then re-check to avoid a lost-wakeup race:
                            // A producer sets hasWork = true before/while offering and calls start().
                            // If the consumer only checked queue.isEmpty() and exited, a producer could
                            // enqueue immediately after that check and the consumer would have already
                            // stopped — the new work might not be processed until someone restarts the consumer.
                            // Checking the flag (producer intent) OR the queue/map snapshots (already-enqueued items)
                            // ensures we don't exit while work either just arrived or is still present.
                            if (hasTask.get() || !queue.isEmpty() || latestByType.isNotEmpty()) {
                                continue
                            }
                            logDebug("TaskDebouncer hibernates")
                            break
                        }

                        // Validate and claim under the same lock used by enqueueTask().
                        // Otherwise a producer can replace latestByType after the stale check
                        // but before this consumer claims the slot, allowing an obsolete item
                        // to run even though a newer item was already pending.
                        val claimResult = synchronized(lifecycleLock) {
                            if (isCancelled.get()) {
                                0
                            } else {
                                val latest = latestByType[polled.type]
                                if (latest == null || latest.seq != polled.seq) {
                                    0
                                } else if (!runningType.compareAndSet(null, polled.type)) {
                                    // Preserve the newest pending item if the slot is unexpectedly busy.
                                    if (latestByType[polled.type]?.seq == polled.seq) {
                                        queue.offer(polled)
                                    }
                                    2
                                } else {
                                    // The lock makes this removal and the latest-item check one
                                    // atomic claim relative to concurrent producers.
                                    latestByType.remove(polled.type, polled)
                                    1
                                }
                            }
                        }

                        when (claimResult) {
                            0 -> {
                                logDebug("TaskDebouncer - drop stale/cancelled item ${polled.id}")
                                continue
                            }
                            2 -> {
                                // Yield if the single-running-task invariant is unexpectedly violated.
                                delay(50)
                                continue
                            }
                        }

                        ensureActive()
                        process(polled)

                        delay(50)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Keep the consumer alive after ordinary task failures, but do not swallow
                        // fatal JVM errors such as OutOfMemoryError or LinkageError.
                        logError("TaskDebouncer failed while processing queued work", e)
                    } finally {
                        runningType.set(null)
                    }
                }
            } finally {
                runningType.set(null)
            }
        }
    }

    // Cancel TaskDebouncer. Once cancelled, all newly enqueued tasks are ignored and not yet started
    // tasks are dropped.
    fun cancel() {
        synchronized(lifecycleLock) {
            logDebug("stopping TaskDebouncer")
            if (!isCancelled.compareAndSet(false, true)) return
            consumerJob?.cancel()
            consumerJob = null
            queue.clear()
            hasTask.set(false)
            runningType.set(null)
            latestByType.clear()
        }
    }

    open fun process(item: QueuedTask) {
        if (isCancelled.get()) {
            logDebug("dropping enqueued task ${item.id}")
            return
        }
        item.block()
    }

    // Enqueue newest-per-type (always keep newest).
    open fun enqueueTask(
        taskId: Int,
        type: String,
        priority: Priority = Priority.MEDIUM,
        block: () -> Unit
    ) {
        synchronized(lifecycleLock) {
            if (isCancelled.get()) {
                logDebug("immediately dropping enqueued task $taskId")
                return
            }
            val seq = counter.incrementAndGet()
            val item = QueuedTask(taskId, type, seq, priority, block)
            // Atomic per-type replacement prevents a slower producer with an older sequence
            // from overwriting a newer task after it was already enqueued.
            latestByType.compute(type) { _, current ->
                if (current == null || seq > current.seq) item else current
            }
            // If a newer producer won the race, this item is stale and need not enter the queue.
            if (latestByType[type]?.seq == seq) {
                queue.offer(item)
                hasTask.set(true)
                start()
            }
        }
    }
}
