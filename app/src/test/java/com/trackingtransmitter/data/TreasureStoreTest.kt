package com.trackingtransmitter.data

import com.trackingtransmitter.domain.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class TreasureStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun addAndReload() { val f = tmp.newFile(); f.delete()
        TreasureStore(f).add("Schatz 1", LatLon(48.1, 11.5))
        val reloaded = TreasureStore(f).treasures.value
        assertEquals(listOf("Schatz 1"), reloaded.map { it.name })
        assertEquals(LatLon(48.1, 11.5), reloaded[0].position) }
    @Test fun delete_removes() { val s = TreasureStore(tmp.newFile().apply { delete() })
        val t = s.add("A", LatLon(0.0, 0.0)); s.delete(t.id); assertTrue(s.treasures.value.isEmpty()) }
    @Test fun missingFile_isEmpty() = assertTrue(TreasureStore(File(tmp.root, "nope.json")).treasures.value.isEmpty())
    @Test fun corruptFile_isEmpty() { val f = tmp.newFile().apply { writeText("{kaputt") }
        assertTrue(TreasureStore(f).treasures.value.isEmpty()) }
    @Test fun corruptFile_isBackedUpBeforeNextAdd() { val f = File(tmp.root, "treasures.json").apply { writeText("{kaputt") }
        TreasureStore(f) { 42L }.add("A", LatLon(0.0, 0.0))
        assertEquals("{kaputt", File(tmp.root, "treasures.json.corrupt-42").readText()) }
    @Test fun defaultNames_fillGaps() { val s = TreasureStore(tmp.newFile().apply { delete() })
        assertEquals("Schatz 1", s.nextDefaultName()); s.add("Schatz 1", LatLon(0.0, 0.0))
        val two = s.add(s.nextDefaultName(), LatLon(0.0, 0.0)); assertEquals("Schatz 2", two.name)
        s.delete(s.treasures.value.first { it.name == "Schatz 1" }.id); assertEquals("Schatz 1", s.nextDefaultName()) }
    @Test fun newestFirst() { var now = 1L; val s = TreasureStore(tmp.newFile().apply { delete() }) { now++ }
        s.add("alt", LatLon(0.0, 0.0)); s.add("neu", LatLon(0.0, 0.0))
        assertEquals(listOf("neu", "alt"), s.treasures.value.map { it.name }) }
    @Test fun waypoints_reloadInOrder() { val f = tmp.newFile(); f.delete()
        val wps = listOf(LatLon(48.0, 11.0), LatLon(48.2, 11.2))
        TreasureStore(f).add("A", LatLon(48.1, 11.5), wps)
        val t = TreasureStore(f).treasures.value.single()
        assertEquals(wps, t.waypoints)
        assertEquals(wps + LatLon(48.1, 11.5), t.route) }
    @Test fun oldFile_withoutWaypoints_loads() { val f = tmp.newFile()
        f.writeText("""[{"id":"x","name":"Alt","lat":1.0,"lon":2.0,"createdAt":3}]""")
        val t = TreasureStore(f).treasures.value.single()
        assertEquals("Alt", t.name); assertTrue(t.waypoints.isEmpty()) }
}
