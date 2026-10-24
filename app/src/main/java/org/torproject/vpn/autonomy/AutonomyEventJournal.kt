package org.torproject.vpn.autonomy

import java.util.ArrayDeque

/**
 * Bounded in-memory diagnostic journal. It deliberately does not persist events or accept free-form
 * payloads. Persistence requires a reviewed encrypted-storage design and an explicit retention policy.
 */
class AutonomyEventJournal(capacity: Int = 128) {
    private val events = ArrayDeque<AutonomyEvent>()
    private var nextSequence = 1L

    val capacity: Int = capacity

    init {
        require(capacity in 1..4096) { "Journal capacity must be between 1 and 4096 events" }
    }

    @Synchronized
    fun record(event: AutonomyEvent): AutonomyEvent {
        val sequenced = event.copy(sequence = nextSequence++)
        if (events.size >= capacity) events.removeFirst()
        events.addLast(sequenced)
        return sequenced
    }

    @Synchronized
    fun snapshot(): List<AutonomyEvent> = events.toList()

    @Synchronized
    fun clear() {
        events.clear()
    }

    @Synchronized
    fun size(): Int = events.size
}
