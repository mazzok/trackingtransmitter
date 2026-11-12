package com.trackingtransmitter

import org.junit.Assert.assertEquals
import org.junit.Test

class SmokeTest {
    @Test
    fun applicationIdMatches() {
        assertEquals("com.trackingtransmitter", BuildConfig.APPLICATION_ID)
    }
}
