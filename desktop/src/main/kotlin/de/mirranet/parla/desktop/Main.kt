package de.mirranet.parla.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import de.mirranet.parla.ui.AppViewModel
import de.mirranet.parla.ui.KeyDispatcher
import de.mirranet.parla.ui.LocalKeys
import de.mirranet.parla.ui.ParlaRoot
import de.mirranet.parla.ui.ParlaTheme
import kotlinx.coroutines.delay
import java.awt.Dimension

/** Sync interval while the window is open; also synced whenever the window gets focus. */
private const val SYNC_INTERVAL_MS = 5 * 60 * 1000L

fun main() {
    val repo = DesktopPlatform.createRepository()
    val vm = AppViewModel(repo, Audio())
    repo.syncInBackground()

    application {
        val keys = remember { KeyDispatcher() }
        val state = rememberWindowState(size = DpSize(1100.dp, 760.dp), position = WindowPosition.PlatformDefault)
        Window(
            onCloseRequest = { vm.close(); exitApplication() },
            state = state,
            title = "Parla",
            icon = painterResource("parla.png"),
            onPreviewKeyEvent = { keys.dispatch(it) },
        ) {
            LaunchedEffect(Unit) { window.minimumSize = Dimension(820, 600) }

            // Pick up progress from the phone when the user comes back to the window.
            LaunchedEffect(window) {
                val listener = object : java.awt.event.WindowAdapter() {
                    override fun windowGainedFocus(e: java.awt.event.WindowEvent?) = repo.syncInBackground()
                }
                window.addWindowFocusListener(listener)
            }
            LaunchedEffect(Unit) {
                while (true) {
                    delay(SYNC_INTERVAL_MS)
                    repo.syncInBackground()
                }
            }

            val st by vm.state.collectAsState()
            CompositionLocalProvider(LocalKeys provides keys) {
                ParlaTheme(largeText = st.largeText) { ParlaRoot(vm) }
            }
        }
    }
}
