package com.cosmos.unreddit.data.remote.api.reddit.graphql

import com.cosmos.unreddit.data.remote.api.reddit.graphql.mapper.GraphQlTimestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GraphQlTimestampTest {
    @Test
    fun `parses a UTC offset timestamp with microseconds`() {
        val inputTimestamp: String = "2026-10-07T15:47:48.255000+0000"
        val actualEpochSeconds: Long = GraphQlTimestamp.toEpochSeconds(inputTimestamp)
        assertEquals(EXPECTED_EPOCH_SECONDS, actualEpochSeconds)
    }

    @Test
    fun `parses a zulu timestamp`() {
        val inputTimestamp: String = "2026-10-07T15:47:48Z"
        val actualEpochSeconds: Long = GraphQlTimestamp.toEpochSeconds(inputTimestamp)
        assertEquals(EXPECTED_EPOCH_SECONDS, actualEpochSeconds)
    }

    @Test
    fun `parses a colon offset timestamp`() {
        val inputTimestamp: String = "2026-10-07T17:47:48.255000+02:00"
        val actualEpochSeconds: Long = GraphQlTimestamp.toEpochSeconds(inputTimestamp)
        assertEquals(EXPECTED_EPOCH_SECONDS, actualEpochSeconds)
    }

    @Test
    fun `parses a negative offset timestamp`() {
        val inputTimestamp: String = "2026-10-07T10:47:48.255000-05:00"
        val actualEpochSeconds: Long = GraphQlTimestamp.toEpochSeconds(inputTimestamp)
        assertEquals(EXPECTED_EPOCH_SECONDS, actualEpochSeconds)
    }

    @Test
    fun `parses a compact positive offset timestamp`() {
        val inputTimestamp: String = "2026-10-07T15:47:48+0530"
        val actualEpochSeconds: Long = GraphQlTimestamp.toEpochSeconds(inputTimestamp)
        assertEquals(COMPACT_OFFSET_EPOCH_SECONDS, actualEpochSeconds)
    }

    @Test
    fun `returns zero for trailing garbage`() {
        val inputTimestamp: String = "2026-10-07T15:47:48.255000+0000garbage"
        val actualEpochSeconds: Long = GraphQlTimestamp.toEpochSeconds(inputTimestamp)
        assertEquals(INVALID_EPOCH_SECONDS, actualEpochSeconds)
    }

    @Test
    fun `returns zero for a fraction after the offset`() {
        val inputTimestamp: String = "2026-10-07T15:47:48+0000.123"
        val actualEpochSeconds: Long = GraphQlTimestamp.toEpochSeconds(inputTimestamp)
        assertEquals(INVALID_EPOCH_SECONDS, actualEpochSeconds)
    }

    @Test
    fun `returns zero for a fraction after zulu`() {
        val inputTimestamp: String = "2026-10-07T15:47:48Z.123"
        val actualEpochSeconds: Long = GraphQlTimestamp.toEpochSeconds(inputTimestamp)
        assertEquals(INVALID_EPOCH_SECONDS, actualEpochSeconds)
    }

    @Test
    fun `returns zero for a fraction before the time`() {
        val inputTimestamp: String = "2026-10-07.123T15:47:48+0000"
        val actualEpochSeconds: Long = GraphQlTimestamp.toEpochSeconds(inputTimestamp)
        assertEquals(INVALID_EPOCH_SECONDS, actualEpochSeconds)
    }

    @Test
    fun `returns zero for variable width fields`() {
        val inputTimestamp: String = "2026-1-7T5:7:8+0000"
        val actualEpochSeconds: Long = GraphQlTimestamp.toEpochSeconds(inputTimestamp)
        assertEquals(INVALID_EPOCH_SECONDS, actualEpochSeconds)
    }

    @Test
    fun `returns zero for whitespace before the offset`() {
        val inputTimestamp: String = "2026-10-07T15:47:48\t+0000"
        val actualEpochSeconds: Long = GraphQlTimestamp.toEpochSeconds(inputTimestamp)
        assertEquals(INVALID_EPOCH_SECONDS, actualEpochSeconds)
    }

    @Test
    fun `returns zero for an out of range date`() {
        val inputTimestamp: String = "2026-13-45T99:99:99+0000"
        val actualEpochSeconds: Long = GraphQlTimestamp.toEpochSeconds(inputTimestamp)
        assertEquals(INVALID_EPOCH_SECONDS, actualEpochSeconds)
    }

    @Test
    fun `returns zero for blank input`() {
        val actualEpochSeconds: Long = GraphQlTimestamp.toEpochSeconds("   ")
        assertEquals(INVALID_EPOCH_SECONDS, actualEpochSeconds)
    }

    @Test
    fun `returns null for invalid input`() {
        val actualEpochSeconds: Long? = GraphQlTimestamp.parseOrNull("not-a-timestamp")
        assertNull(actualEpochSeconds)
    }

    @Test
    fun `returns epoch zero for the epoch timestamp`() {
        val actualEpochSeconds: Long? = GraphQlTimestamp.parseOrNull("1970-01-01T00:00:00+0000")
        assertEquals(0L, actualEpochSeconds)
    }

    private companion object {
        private const val EXPECTED_EPOCH_SECONDS: Long = 1791388068L
        private const val COMPACT_OFFSET_EPOCH_SECONDS: Long = 1791368268L
        private const val INVALID_EPOCH_SECONDS: Long = 0L
    }
}
