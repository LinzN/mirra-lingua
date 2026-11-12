package de.mirranet.parla.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

/**
 * Window-wide keyboard shortcuts. The window forwards every key-down event here;
 * the most recently registered handler gets it first.
 */
class KeyDispatcher {
    private val handlers = mutableListOf<(KeyEvent) -> Boolean>()

    fun register(handler: (KeyEvent) -> Boolean) { handlers += handler }
    fun unregister(handler: (KeyEvent) -> Boolean) { handlers -= handler }

    fun dispatch(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        return handlers.asReversed().toList().any { it(event) }
    }
}

val LocalKeys = staticCompositionLocalOf { KeyDispatcher() }

/** Registers [onKey] while this composable is on screen; return true to consume the key. */
@Composable
fun KeyHandler(enabled: Boolean = true, onKey: (KeyEvent) -> Boolean) {
    val keys = LocalKeys.current
    val current by rememberUpdatedState(onKey)
    DisposableEffect(keys, enabled) {
        if (!enabled) return@DisposableEffect onDispose { }
        val handler: (KeyEvent) -> Boolean = { current(it) }
        keys.register(handler)
        onDispose { keys.unregister(handler) }
    }
}

/** Desktop counterpart of Android's back handling: Escape. */
@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    val current by rememberUpdatedState(onBack)
    KeyHandler(enabled) { if (it.key == Key.Escape) { current(); true } else false }
}

/** 1-based number from the digit row or the numpad, or null. */
fun KeyEvent.digit(): Int? = when (key) {
    Key.One, Key.NumPad1 -> 1
    Key.Two, Key.NumPad2 -> 2
    Key.Three, Key.NumPad3 -> 3
    Key.Four, Key.NumPad4 -> 4
    Key.Five, Key.NumPad5 -> 5
    Key.Six, Key.NumPad6 -> 6
    Key.Seven, Key.NumPad7 -> 7
    Key.Eight, Key.NumPad8 -> 8
    Key.Nine, Key.NumPad9 -> 9
    else -> null
}

fun KeyEvent.isEnter(): Boolean = key == Key.Enter || key == Key.NumPadEnter
