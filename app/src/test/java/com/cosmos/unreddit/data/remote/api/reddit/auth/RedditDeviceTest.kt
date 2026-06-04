package com.cosmos.unreddit.data.remote.api.reddit.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RedditDeviceTest {
    @Test
    fun `uuid is valid UUID format`() {
        val device = RedditDevice()
        val uuidRegex = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
        assertTrue("UUID '${device.uuid}' doesn't match format", uuidRegex.matches(device.uuid))
    }

    @Test
    fun `appVersion is from RedditAppVersions list`() {
        val device = RedditDevice()
        assertTrue(
            "appVersion '${device.appVersion}' not in list",
            device.appVersion in RedditAppVersions.LIST,
        )
    }

    @Test
    fun `userAgent contains Reddit prefix and Android version`() {
        val device = RedditDevice()
        assertTrue("UA should start with Reddit/", device.userAgent.startsWith("Reddit/"))
        assertTrue("UA should contain /Android ", "/Android " in device.userAgent)
    }

    @Test
    fun `androidVersion is between 9 and 14`() {
        val device = RedditDevice()
        assertTrue("Android version out of range", device.androidVersion in 9..14)
    }

    @Test
    fun `qos is a positive decimal string`() {
        val device = RedditDevice()
        val qosValue = device.qos.toDoubleOrNull()
        assertNotNull("qos should be a valid decimal", qosValue)
        assertTrue("qos should be positive", qosValue!! > 0)
    }

    @Test
    fun `codecs starts with available-codecs prefix`() {
        val device = RedditDevice()
        assertTrue(
            "codecs should start with 'available-codecs='",
            device.codecs.startsWith("available-codecs="),
        )
        assertTrue("codecs should contain video/avc", "video/avc" in device.codecs)
    }

    @Test
    fun `each instance generates unique values`() {
        val devices = (1..20).map { RedditDevice() }
        val uniqueUuids = devices.map { it.uuid }.toSet()
        assertEquals("Expected 20 unique UUIDs", 20, uniqueUuids.size)
    }
}
