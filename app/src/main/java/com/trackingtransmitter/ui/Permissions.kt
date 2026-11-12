package com.trackingtransmitter.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

enum class LocationAccess { GRANTED, APPROXIMATE_ONLY, DENIED }

/** Precise location is what the search needs; approximate alone never reaches the start accuracy. */
fun locationAccess(fine: Boolean, coarse: Boolean): LocationAccess = when {
    fine -> LocationAccess.GRANTED
    coarse -> LocationAccess.APPROXIMATE_ONLY
    else -> LocationAccess.DENIED
}

/** After a refusal without rationale Android shows no dialog any more; only the app settings can grant it then. */
fun opensSettings(refused: Boolean, showRationale: Boolean): Boolean = refused && !showRationale

private fun granted(c: Context, p: String) = c.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

fun locationAccess(c: Context): LocationAccess =
    locationAccess(granted(c, Manifest.permission.ACCESS_FINE_LOCATION), granted(c, Manifest.permission.ACCESS_COARSE_LOCATION))

fun hasLocationPermission(c: Context) = locationAccess(c) == LocationAccess.GRANTED

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

@Stable
class LocationPermission internal constructor(access: LocationAccess) {
    var access by mutableStateOf(access)
        internal set
    val granted get() = access == LocationAccess.GRANTED

    /** True after the user refused (or granted only approximate) in a request from this screen. */
    var denied by mutableStateOf(false)
        internal set

    /** The last refusal blocked further dialogs, so [request] opens the app settings. */
    internal var blocked = false
    internal var launch: () -> Unit = {}

    fun request() = launch()
}

/** Location permission for the current screen, requested once when the screen opens and re-read on every resume. */
@Composable
fun rememberLocationPermission(): LocationPermission {
    val context = LocalContext.current
    val permission = remember { LocationPermission(locationAccess(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permission.access = locationAccess(context)
        permission.denied = !permission.granted
        val rationale = context.activity()?.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) ?: true
        permission.blocked = opensSettings(permission.denied, rationale)
    }
    permission.launch = {
        if (permission.blocked) {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
            )
        } else {
            launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    // picks up a grant made in the system settings when the user comes back
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permission.access = locationAccess(context)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) { if (!permission.granted) permission.request() }
    return permission
}

/** Whether the system location switch is on; re-read on every resume, e.g. after returning from the settings. */
@Composable
fun rememberLocationEnabled(): Boolean {
    val context = LocalContext.current
    val manager = remember { context.getSystemService(LocationManager::class.java) }
    var enabled by remember { mutableStateOf(manager.isLocationEnabled) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) enabled = manager.isLocationEnabled
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return enabled
}

@Composable
fun LocationOff(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(
        modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Standort ist ausgeschaltet.", textAlign = TextAlign.Center)
        Button(onClick = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }) {
            Text("Standort einschalten")
        }
    }
}

@Composable
fun LocationDenied(permission: LocationPermission, modifier: Modifier = Modifier) {
    Column(
        modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val text = if (permission.access == LocationAccess.APPROXIMATE_ONLY) {
            "Bitte „Genauer Standort“ erlauben – sonst ist die Peilung zu ungenau."
        } else "Ohne Standortfreigabe kann die Suche nicht starten."
        Text(text, textAlign = TextAlign.Center)
        Button(onClick = permission::request) { Text("Freigeben") }
    }
}
