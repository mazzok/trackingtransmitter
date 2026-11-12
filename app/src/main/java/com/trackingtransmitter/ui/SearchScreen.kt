package com.trackingtransmitter.ui

import android.media.AudioManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.trackingtransmitter.domain.SearchState
import com.trackingtransmitter.map.TreasureMap
import com.trackingtransmitter.sensors.HeadingSensor

private const val HOLD_TO_END_MS = 1_000L

/** The search: the map never shows the treasure or the waypoints, only the user and their view cone. */
@Composable
fun SearchScreen(vm: SearchViewModel, onExit: () -> Unit) {
    when {
        !vm.treasureKnown -> Message("Schatz nicht gefunden.", onExit)
        vm.headingSensor == HeadingSensor.NONE ->
            Message("Dieses Handy hat keinen Kompass – die Peilung funktioniert hier nicht.", onExit)
        else -> ActiveSearch(vm, onExit)
    }
}

@Composable
private fun Message(text: String, onBack: () -> Unit) {
    Scaffold { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Text(text, textAlign = TextAlign.Center)
            Button(onClick = onBack) { Text("Zurück") }
        }
    }
}

@Composable
private fun ActiveSearch(vm: SearchViewModel, onExit: () -> Unit) {
    val permission = rememberLocationPermission()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val view = LocalView.current
    val context = LocalContext.current
    var askExit by rememberSaveable { mutableStateOf(false) }
    val locationEnabled = rememberLocationEnabled()
    var volumeOff by remember { mutableStateOf(false) }

    // kids would hear no beeps at all; checked whenever the search (re)starts
    DisposableEffect(lifecycle) {
        val audio = context.getSystemService(AudioManager::class.java)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) volumeOff = audio.getStreamVolume(AudioManager.STREAM_MUSIC) == 0
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    DisposableEffect(lifecycle, permission.granted) {
        if (!permission.granted) return@DisposableEffect onDispose {}
        // added late, the observer still receives ON_START if the screen is already started
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> vm.resume()
                Lifecycle.Event.ON_STOP -> vm.pause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            vm.pause()
        }
    }

    BackHandler { askExit = true }
    if (askExit) {
        AlertDialog(
            onDismissRequest = { askExit = false },
            text = { Text("Suche beenden?") },
            confirmButton = { TextButton(onClick = { askExit = false; onExit() }) { Text("Beenden") } },
            dismissButton = { TextButton(onClick = { askExit = false }) { Text("Weitersuchen") } },
        )
    }

    Scaffold { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (!permission.granted) {
                if (permission.denied) LocationDenied(permission, Modifier.align(Alignment.Center))
                else Text("Warte auf Standortfreigabe…", Modifier.align(Alignment.Center).padding(24.dp))
            } else {
                val state by vm.state.collectAsState()
                val user by vm.user.collectAsState()
                val heading by vm.mapHeading.collectAsState()
                val needsCalibration by vm.needsCalibration.collectAsState()
                val leg by vm.leg.collectAsState()
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        TreasureMap(
                            modifier = Modifier.fillMaxSize(),
                            user = user,
                            headingDeg = heading,
                            treasure = null,
                            followUser = true,
                            initialCenter = null,
                            onTap = null,
                        )
                        Column(
                            Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            val route = routeText(leg, vm.waypointCount)
                            if (route != null && state != SearchState.Found) RouteInfo(route)
                            if (volumeOff) Hint("Lautstärke ist aus – bitte lauter stellen.", onDismiss = { volumeOff = false })
                            if (needsCalibration) Hint("Kompass ungenau – Handy in Form einer 8 schwenken.")
                        }
                        when {
                            !locationEnabled -> Surface(
                                Modifier.align(Alignment.Center),
                                shape = MaterialTheme.shapes.medium,
                                tonalElevation = 4.dp,
                                shadowElevation = 4.dp,
                            ) { LocationOff() }
                            state == SearchState.WaitingForSignal -> Surface(
                                Modifier.align(Alignment.Center),
                                shape = MaterialTheme.shapes.medium,
                                tonalElevation = 4.dp,
                                shadowElevation = 4.dp,
                            ) { Text("Suche Signal…", Modifier.padding(16.dp)) }
                            state == SearchState.Found -> FoundCard(
                                Modifier.align(Alignment.Center).padding(24.dp),
                                onDone = onExit,
                                onContinue = vm::continueSearching,
                            )
                        }
                    }
                    HoldToEnd(onExit, Modifier.fillMaxWidth().padding(16.dp))
                }
            }
        }
    }
}

@Composable
private fun RouteInfo(text: String) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer,
        shadowElevation = 4.dp,
    ) {
        Text(text, Modifier.padding(12.dp), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Hint(text: String, onDismiss: (() -> Unit)? = null) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
        shadowElevation = 4.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text, Modifier.weight(1f).padding(12.dp))
            if (onDismiss != null) {
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Schließen") }
            }
        }
    }
}

@Composable
private fun FoundCard(modifier: Modifier, onDone: () -> Unit, onContinue: () -> Unit) {
    Card(modifier) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Gefunden! Der Schatz ist hier irgendwo.", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onContinue) { Text("Weitersuchen") }
                Button(onClick = onDone) { Text("Fertig") }
            }
        }
    }
}

/** Ends the search only after a 1 s press, so a pocket touch cannot. */
@Composable
private fun HoldToEnd(onEnd: () -> Unit, modifier: Modifier) {
    val base = LocalViewConfiguration.current
    val slow = remember(base) {
        object : ViewConfiguration by base {
            override val longPressTimeoutMillis = HOLD_TO_END_MS
        }
    }
    CompositionLocalProvider(LocalViewConfiguration provides slow) {
        Surface(
            modifier.pointerInput(Unit) { detectTapGestures(onLongPress = { onEnd() }) },
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Text("Zum Beenden gedrückt halten", Modifier.padding(16.dp), textAlign = TextAlign.Center)
        }
    }
}
