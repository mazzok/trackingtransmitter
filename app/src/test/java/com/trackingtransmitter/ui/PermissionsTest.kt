package com.trackingtransmitter.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionsTest {
    @Test fun fine_isGranted() = assertEquals(LocationAccess.GRANTED, locationAccess(fine = true, coarse = true))
    @Test fun coarseOnly_isApproximate() = assertEquals(LocationAccess.APPROXIMATE_ONLY, locationAccess(fine = false, coarse = true))
    @Test fun neither_isDenied() = assertEquals(LocationAccess.DENIED, locationAccess(fine = false, coarse = false))

    @Test fun refusedWithoutRationale_opensSettings() = assertTrue(opensSettings(refused = true, showRationale = false))
    @Test fun refusedWithRationale_asksAgain() = assertFalse(opensSettings(refused = true, showRationale = true))
    @Test fun neverAsked_asks() = assertFalse(opensSettings(refused = false, showRationale = false))
}
