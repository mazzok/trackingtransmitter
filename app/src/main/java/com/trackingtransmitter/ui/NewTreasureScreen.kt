package com.trackingtransmitter.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewTreasureScreen(store: TreasureStore, onDone: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val permission = rememberLocationPermission()
    val locationEnabled = rememberLocationEnabled()
    val locations = remember { LocationSource(context) }

    var marker by remember { mutableStateOf<LatLon?>(null) }
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
                    marker = fix.position
                    waitingForFix = false
                }
            }
        }
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
            Text("Tippe auf die Karte, um den Schatz zu setzen.", Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            key(center) {
                TreasureMap(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    user = user,
                    headingDeg = null,
                    treasure = marker,
                    followUser = false,
                    initialCenter = center,
                    onTap = { marker = it; waitingForFix = false },
                )
            }
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (permission.granted && !locationEnabled) LocationOff(Modifier.fillMaxWidth())
                OutlinedButton(
                    onClick = {
                        val here = user
                        if (here != null && acceptsOwnPosition(userAccuracy)) marker = here else waitingForFix = true
                    },
                    enabled = permission.granted,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Meine Position verwenden") }
                if (waitingForFix) Text(ownPositionWaitText(userAccuracy))
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
                            marker?.let { store.add(name.trim(), it) }
                            onDone()
                        }
                    },
                    enabled = !saved && marker != null && name.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Speichern") }
            }
        }
    }
}
