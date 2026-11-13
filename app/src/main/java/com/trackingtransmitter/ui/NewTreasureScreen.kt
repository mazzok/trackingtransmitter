package com.trackingtransmitter.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.trackingtransmitter.data.Stop
import com.trackingtransmitter.data.StopKind
import com.trackingtransmitter.data.TreasureStore
import com.trackingtransmitter.domain.FOUND_MAX_ACCURACY_M
import com.trackingtransmitter.domain.LatLon
import com.trackingtransmitter.map.TreasureMap
import com.trackingtransmitter.sensors.LocationSource
import kotlin.math.roundToInt

/** "Meine Position verwenden" takes only a fix as accurate as the one that counts as found. */
internal fun acceptsOwnPosition(accuracyM: Float?): Boolean = accuracyM != null && accuracyM <= FOUND_MAX_ACCURACY_M

internal fun ownPositionWaitText(accuracyM: Float?): String =
    if (accuracyM == null) "Warte auf GPS…" else "Warte auf genaues GPS… (±${accuracyM.roundToInt()} m)"

/** The name the next stop of [kind] gets, e.g. "Wegpunkt 3". */
internal fun nextStopName(kind: StopKind, stops: List<Stop>): String {
    val nr = stops.count { it.kind == kind } + 1
    return if (kind == StopKind.TREASURE) "Schatz $nr" else "Wegpunkt $nr"
}

internal fun placingHint(kind: StopKind, stops: List<Stop>): String =
    "Tippe auf die Karte für ${nextStopName(kind, stops)}. Alle Punkte werden der Reihe nach gesucht."

/** Why the hunt cannot be saved yet, or null. */
internal fun stopsProblem(stops: List<Stop>): String? = when {
    stops.isEmpty() -> null
    stops.last().kind != StopKind.TREASURE -> "Der letzte Punkt muss ein Schatz sein."
    else -> null
}

private const val MESSAGE_MAX = 200

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewTreasureScreen(store: TreasureStore, onDone: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val permission = rememberLocationPermission()
    val locationEnabled = rememberLocationEnabled()
    val locations = remember { LocationSource(context) }

    val stops = remember { mutableStateListOf<Stop>() }
    var placing by remember { mutableStateOf(StopKind.TREASURE) }
    // a placed point waits here until its text is entered (or it is cancelled)
    var pending by remember { mutableStateOf<Stop?>(null) }
    fun place(p: LatLon) {
        if (pending == null) pending = Stop(p, placing)
    }
    var name by rememberSaveable { mutableStateOf(store.nextDefaultName()) }
    var user by remember { mutableStateOf<LatLon?>(null) }
    var userAccuracy by remember { mutableStateOf<Float?>(null) }
    var waitingForFix by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    // the map is centred once: on the last known position, else on the first fix
    var center by remember { mutableStateOf(if (permission.granted) locations.lastKnown() else null) }

    LaunchedEffect(permission.granted) {
        if (!permission.granted) return@LaunchedEffect
        if (center == null) center = locations.lastKnown()
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            locations.fixes().collect { fix ->
                user = fix.position
                userAccuracy = fix.accuracyM
                if (center == null) center = fix.position
                if (waitingForFix && acceptsOwnPosition(fix.accuracyM)) {
                    place(fix.position)
                    waitingForFix = false
                }
            }
        }
    }

    pending?.let { stop ->
        var message by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(nextStopName(stop.kind, stops)) },
            text = {
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it.take(MESSAGE_MAX) },
                    label = { Text("Text beim Finden (optional)") },
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = { TextButton(onClick = { stops += stop.copy(message = message.trim()); pending = null }) { Text("Setzen") } },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Abbrechen") } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Neuer Schatz") },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                StopKind.entries.forEachIndexed { i, k ->
                    SegmentedButton(
                        selected = placing == k,
                        onClick = { placing = k; waitingForFix = false },
                        shape = SegmentedButtonDefaults.itemShape(i, StopKind.entries.size),
                    ) { Text(if (k == StopKind.TREASURE) "Schatz" else "Wegpunkt") }
                }
            }
            Text(placingHint(placing, stops), Modifier.padding(horizontal = 16.dp))
            key(center) {
                TreasureMap(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    user = user,
                    headingDeg = null,
                    followUser = false,
                    initialCenter = center,
                    onTap = { place(it); waitingForFix = false },
                    treasures = stops.filter { it.kind == StopKind.TREASURE }.map { it.position },
                    waypoints = stops.filter { it.kind == StopKind.WAYPOINT }.map { it.position },
                    route = stops.map { it.position },
                )
            }
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (permission.granted && !locationEnabled) LocationOff(Modifier.fillMaxWidth())
                OutlinedButton(
                    onClick = {
                        val here = user
                        if (here != null && acceptsOwnPosition(userAccuracy)) place(here) else waitingForFix = true
                    },
                    enabled = permission.granted,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Meine Position verwenden") }
                if (waitingForFix) Text(ownPositionWaitText(userAccuracy))
                if (stops.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val waypointCount = stops.count { it.kind == StopKind.WAYPOINT }
                        Text(
                            stopCountText(waypointCount, stops.size - waypointCount) ?: treasureCountText(1),
                            Modifier.weight(1f),
                        )
                        TextButton(onClick = { stops.removeAt(stops.lastIndex) }) { Text("Letzten entfernen") }
                    }
                }
                stopsProblem(stops)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        if (!saved) {
                            saved = true
                            store.add(name.trim(), stops.toList())
                            onDone()
                        }
                    },
                    enabled = !saved && stops.lastOrNull()?.kind == StopKind.TREASURE && name.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Speichern") }
            }
        }
    }
}
