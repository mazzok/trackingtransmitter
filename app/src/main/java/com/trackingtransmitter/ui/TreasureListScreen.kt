package com.trackingtransmitter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.trackingtransmitter.data.Treasure
import com.trackingtransmitter.data.TreasureStore
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

internal fun waypointCountText(n: Int): String = if (n == 1) "1 Wegpunkt" else "$n Wegpunkte"

internal fun treasureCountText(n: Int): String = if (n == 1) "1 Schatz" else "$n Schätze"

/** "2 Wegpunkte · 3 Schätze"; null for a plain single treasure. */
internal fun stopCountText(waypoints: Int, treasures: Int): String? =
    if (waypoints == 0 && treasures == 1) null
    else listOfNotNull(waypointCountText(waypoints).takeIf { waypoints > 0 }, treasureCountText(treasures)).joinToString(" · ")

private fun formatDate(ms: Long): String = DATE_FORMAT.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TreasureListScreen(store: TreasureStore, onNew: () -> Unit, onSearch: (String) -> Unit) {
    val treasures by store.treasures.collectAsState()
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = treasures.firstOrNull { it.id == selectedId }
    var toDelete by remember { mutableStateOf<Treasure?>(null) }

    toDelete?.let { t ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            text = { Text("„%s“ löschen?".format(t.name)) },
            confirmButton = { TextButton(onClick = { store.delete(t.id); toDelete = null }) { Text("Löschen") } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Abbrechen") } },
        )
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Schätze") }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (treasures.isEmpty()) {
                    Text("Noch kein Schatz versteckt.", Modifier.align(Alignment.Center).padding(24.dp))
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(treasures, key = { it.id }) { t ->
                            TreasureRow(t, t.id == selected?.id, onSelect = { selectedId = t.id }, onDelete = { toDelete = t })
                            HorizontalDivider()
                        }
                    }
                }
            }
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { selected?.let { onSearch(it.id) } }, enabled = selected != null, modifier = Modifier.fillMaxWidth()) {
                    Text("Suche starten")
                }
                OutlinedButton(onClick = onNew, modifier = Modifier.fillMaxWidth()) { Text("Neuer Schatz") }
            }
        }
    }
}

@Composable
private fun TreasureRow(t: Treasure, selected: Boolean, onSelect: () -> Unit, onDelete: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .background(if (selected) colors.primaryContainer else colors.surface)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(t.name, style = MaterialTheme.typography.titleMedium)
            val details = listOfNotNull(formatDate(t.createdAt), stopCountText(t.waypointCount, t.treasureCount)).joinToString(" · ")
            Text(details, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Löschen") }
    }
}
