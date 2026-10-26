package org.torproject.vpn.autonomy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AutonomyEventJournalTest {
    @Test
    fun journalRemainsBoundedAndKeepsSequenceMonotonicAfterEviction() {
        val journal = AutonomyEventJournal(capacity = 2)

        journal.record(AutonomyEvent(AutonomyEventType.SERVICE_CREATED, AutonomyEventSource.VPN_SERVICE, 1))
        journal.record(AutonomyEvent(AutonomyEventType.TUNNEL_START_REQUESTED, AutonomyEventSource.VPN_SERVICE, 2))
        journal.record(AutonomyEvent(AutonomyEventType.TUNNEL_BOOTSTRAPPING, AutonomyEventSource.VPN_SERVICE, 3))

        val events = journal.snapshot()
        assertEquals(2, events.size)
        assertEquals(
            listOf(AutonomyEventType.TUNNEL_START_REQUESTED, AutonomyEventType.TUNNEL_BOOTSTRAPPING),
            events.map { it.type }
        )
        assertEquals(listOf(2L, 3L), events.map { it.sequence })
    }

    @Test
    fun clearRemovesEventsWithoutResettingSequence() {
        val journal = AutonomyEventJournal(capacity = 2)
        journal.record(AutonomyEvent(AutonomyEventType.SERVICE_CREATED, AutonomyEventSource.VPN_SERVICE, 1))
        journal.clear()

        assertEquals(0, journal.size())
        val next = journal.record(
            AutonomyEvent(AutonomyEventType.SERVICE_STOPPED, AutonomyEventSource.VPN_SERVICE, 2)
        )
        assertEquals(2L, next.sequence)
    }

    @Test
    fun journalRejectsUnboundedOrZeroCapacity() {
        assertThrows(IllegalArgumentException::class.java) { AutonomyEventJournal(0) }
        assertThrows(IllegalArgumentException::class.java) { AutonomyEventJournal(4097) }
    }
}
