package com.trackingtransmitter.ui

import com.trackingtransmitter.data.Stop
import com.trackingtransmitter.data.StopKind
import com.trackingtransmitter.domain.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NewTreasureTextsTest {
    private val p = LatLon(0.0, 0.0)
    private val w = Stop(p, StopKind.WAYPOINT)
    private val t = Stop(p, StopKind.TREASURE)

    @Test fun nextStopName_countsPerKind() {
        assertEquals("Wegpunkt 2", nextStopName(StopKind.WAYPOINT, listOf(w, t)))
        assertEquals("Schatz 2", nextStopName(StopKind.TREASURE, listOf(w, t)))
        assertEquals("Schatz 1", nextStopName(StopKind.TREASURE, emptyList()))
    }

    @Test fun stopsProblem_lastMustBeATreasure() {
        assertNull(stopsProblem(emptyList()))
        assertNull(stopsProblem(listOf(w, t)))
        assertEquals("Der letzte Punkt muss ein Schatz sein.", stopsProblem(listOf(t, w)))
    }

    @Test fun stopCountText_mixed() {
        assertNull(stopCountText(0, 1))
        assertEquals("2 Schätze", stopCountText(0, 2))
        assertEquals("1 Wegpunkt · 1 Schatz", stopCountText(1, 1))
        assertEquals("3 Wegpunkte · 2 Schätze", stopCountText(3, 2))
    }
}
