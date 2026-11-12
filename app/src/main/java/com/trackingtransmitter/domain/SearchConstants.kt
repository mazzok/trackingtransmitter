package com.trackingtransmitter.domain

const val FOUND_RADIUS_M = 5.0
const val NEAR_RADIUS_M = 15.0
const val FOUND_CONSECUTIVE_FIXES = 3
const val FOUND_MAX_ACCURACY_M = 10f
const val START_MAX_ACCURACY_M = 20f

const val MIN_INTERVAL_MS = 150
const val MAX_INTERVAL_MS = 1500
const val NEAR_MIN_INTERVAL_MS = 80

/** Beep pitch; it never changes, direction and distance are heard in pace and loudness. */
const val BEEP_HZ = 1000.0
const val FAR_DISTANCE_M = 300.0

/** Beep loudness at [FAR_DISTANCE_M] and beyond, in dB below full volume; it rises to 0 dB at [FOUND_RADIUS_M]. */
const val FAR_VOLUME_DB = -18.0
