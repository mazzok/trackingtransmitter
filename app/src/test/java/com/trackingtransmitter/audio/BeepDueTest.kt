package com.trackingtransmitter.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BeepDueTest {
    @Test fun notDueBeforeInterval() = assertFalse(beepDue(300, 500))
    @Test fun dueAtInterval() = assertTrue(beepDue(500, 500))
    @Test fun shorterNewIntervalFiresImmediately() = assertTrue(beepDue(300, 250))
}
