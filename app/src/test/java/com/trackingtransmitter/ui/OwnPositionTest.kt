package com.trackingtransmitter.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnPositionTest {
    @Test fun accurateFix_isAccepted() = assertTrue(acceptsOwnPosition(5f))
    @Test fun atLimit_isAccepted() = assertTrue(acceptsOwnPosition(10f))
    @Test fun inaccurateFix_isRejected() = assertFalse(acceptsOwnPosition(10.5f))
    @Test fun unknownAccuracy_isRejected() = assertFalse(acceptsOwnPosition(null))

    @Test fun waitText_noFix() = assertEquals("Warte auf GPS…", ownPositionWaitText(null))
    @Test fun waitText_showsRoundedAccuracy() = assertEquals("Warte auf genaues GPS… (±24 m)", ownPositionWaitText(23.6f))
}
