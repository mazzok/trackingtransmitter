package com.trackingtransmitter.data

import com.trackingtransmitter.domain.LatLon
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Persists treasures as a JSON array in [file]; a missing or corrupt file yields an empty list (a corrupt one is kept as a backup). */
class TreasureStore(private val file: File, private val clock: () -> Long = System::currentTimeMillis) {
    private val _treasures = MutableStateFlow(load())
    val treasures: StateFlow<List<Treasure>> = _treasures

    @Synchronized
    fun add(name: String, stops: List<Stop>): Treasure {
        val t = Treasure(UUID.randomUUID().toString(), name, clock(), stops)
        update { listOf(t) + it }
        return t
    }

    @Synchronized
    fun delete(id: String) = update { list -> list.filterNot { it.id == id } }

    fun get(id: String): Treasure? = _treasures.value.firstOrNull { it.id == id }

    /** "Schatz N" with the smallest N >= 1 not yet used as a name. */
    fun nextDefaultName(): String {
        val names = _treasures.value.map { it.name }.toSet()
        var n = 1
        while ("Schatz $n" in names) n++
        return "Schatz $n"
    }

    private fun update(change: (List<Treasure>) -> List<Treasure>) {
        val next = change(_treasures.value)
        save(next)
        _treasures.value = next
    }

    private fun load(): List<Treasure> = try {
        if (!file.exists()) emptyList() else {
            val arr = JSONArray(file.readText())
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                Treasure(o.getString("id"), o.getString("name"), o.getLong("createdAt"), loadStops(o))
            }
        }
    } catch (e: Exception) {
        // keep the unreadable file, the next save would overwrite it
        file.renameTo(File(file.parentFile, "${file.name}.corrupt-${clock()}"))
        emptyList()
    }

    private fun latLon(o: JSONObject) = LatLon(o.getDouble("lat"), o.getDouble("lon"))

    private fun loadStops(o: JSONObject): List<Stop> {
        val stops = o.optJSONArray("stops")
        if (stops != null) return List(stops.length()) { j ->
            val s = stops.getJSONObject(j)
            Stop(latLon(s), StopKind.valueOf(s.getString("kind")), s.optString("message"))
        }
        // older files: optional "waypoints" plus the treasure at the top level
        val wps = o.optJSONArray("waypoints") ?: JSONArray()
        return List(wps.length()) { j -> Stop(latLon(wps.getJSONObject(j)), StopKind.WAYPOINT) } +
            Stop(latLon(o), StopKind.TREASURE)
    }

    private fun save(list: List<Treasure>) {
        val arr = JSONArray()
        list.forEach {
            val stops = JSONArray()
            it.stops.forEach { s ->
                stops.put(JSONObject().put("lat", s.position.lat).put("lon", s.position.lon).put("kind", s.kind.name).put("message", s.message))
            }
            arr.put(JSONObject().put("id", it.id).put("name", it.name).put("createdAt", it.createdAt).put("stops", stops))
        }
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(arr.toString())
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }
}
