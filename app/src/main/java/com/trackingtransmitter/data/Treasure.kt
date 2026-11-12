package com.trackingtransmitter.data

import com.trackingtransmitter.domain.LatLon

data class Treasure(val id: String, val name: String, val position: LatLon, val createdAt: Long)
