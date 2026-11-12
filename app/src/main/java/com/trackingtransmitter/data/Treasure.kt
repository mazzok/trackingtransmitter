package com.trackingtransmitter.data

import com.trackingtransmitter.domain.LatLon

/** [waypoints] are searched in order before the treasure itself. */
data class Treasure(
    val id: String,
    val name: String,
    val position: LatLon,
    val createdAt: Long,
    val waypoints: List<LatLon> = emptyList(),
) {
    /** Every stop of the search: the waypoints, then the treasure. */
    val route: List<LatLon> get() = waypoints + position
}
