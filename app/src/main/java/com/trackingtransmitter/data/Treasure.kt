package com.trackingtransmitter.data

import com.trackingtransmitter.domain.LatLon

enum class StopKind { WAYPOINT, TREASURE }

/** One point of a hunt; [message] (may be blank) is shown once the point is reached. */
data class Stop(val position: LatLon, val kind: StopKind, val message: String = "")

/** A hunt: its [stops] are searched in order, the last one is always a treasure. */
data class Treasure(
    val id: String,
    val name: String,
    val createdAt: Long,
    val stops: List<Stop>,
) {
    init {
        require(stops.lastOrNull()?.kind == StopKind.TREASURE) { "a hunt ends with a treasure" }
    }

    /** Every stop of the search in order. */
    val route: List<LatLon> get() = stops.map { it.position }
    val waypointCount: Int get() = stops.count { it.kind == StopKind.WAYPOINT }
    val treasureCount: Int get() = stops.count { it.kind == StopKind.TREASURE }
}
