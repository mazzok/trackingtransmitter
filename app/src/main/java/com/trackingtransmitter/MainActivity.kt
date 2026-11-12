package com.trackingtransmitter

import android.media.AudioManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.trackingtransmitter.ui.NewTreasureScreen
import com.trackingtransmitter.ui.SearchScreen
import com.trackingtransmitter.ui.SearchViewModel
import com.trackingtransmitter.ui.TreasureListScreen

class MainActivity : ComponentActivity() {
    private val app get() = application as SchatzApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // the volume keys control the beeps, which play as media
        volumeControlStream = AudioManager.STREAM_MUSIC
        setContent { MaterialTheme { App() } }
    }

    @Composable
    private fun App() {
        val nav = rememberNavController()
        NavHost(nav, startDestination = LIST) {
            composable(LIST) {
                TreasureListScreen(app.treasures, onNew = { nav.navigate(NEW) }, onSearch = { nav.navigate("search/$it") })
            }
            composable(NEW) {
                NewTreasureScreen(app.treasures, onDone = { nav.popBackStack(LIST, inclusive = false) })
            }
            composable("search/{treasureId}") { entry ->
                val id = entry.arguments?.getString("treasureId").orEmpty()
                val vm = viewModel { SearchViewModel(app, id) }
                SearchScreen(vm, onExit = { nav.popBackStack(LIST, inclusive = false) })
            }
        }
    }

    private companion object {
        const val LIST = "list"
        const val NEW = "new"
    }
}
