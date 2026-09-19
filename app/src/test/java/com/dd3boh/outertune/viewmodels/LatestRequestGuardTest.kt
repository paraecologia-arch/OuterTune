package com.dd3boh.outertune.viewmodels

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LatestRequestGuardTest {
    @Test
    fun onlyLatestRequestCanPublish() {
        val guard = LatestRequestGuard()
        val first = guard.next()
        val second = guard.next()

        assertFalse(guard.isLatest(first))
        assertTrue(guard.isLatest(second))
    }

    @Test
    fun deselectionInvalidatesRequestInFlight() {
        val guard = LatestRequestGuard()
        val request = guard.next()

        guard.invalidate()

        assertFalse(guard.isLatest(request))
    }
}
