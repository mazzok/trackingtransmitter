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
    fun add(name: String, position: LatLon): Treasure {
        val t = Treasure(UUID.randomUUID().toString(), name, position, clock())
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
                Treasure(o.getString("id"), o.getString("name"), LatLon(o.getDouble("lat"), o.getDouble("lon")), o.getLong("createdAt"))
            }
        }
    } catch (e: Exception) {
        // keep the unreadable file, the next save would overwrite it
        file.renameTo(File(file.parentFile, "${file.name}.corrupt-${clock()}"))
        emptyList()
    }

    private fun save(list: List<Treasure>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("id", it.id).put("name", it.name).put("lat", it.position.lat).put("lon", it.position.lon).put("createdAt", it.createdAt))
        }
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(arr.toString())
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }
}
