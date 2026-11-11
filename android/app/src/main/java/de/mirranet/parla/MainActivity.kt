package de.mirranet.parla

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import de.mirranet.parla.ui.AppViewModel
import de.mirranet.parla.ui.ParlaRoot
import de.mirranet.parla.ui.ParlaTheme

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Check for new content and upload progress whenever the app comes to the foreground.
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) = vm.sync()
        })
        setContent {
            val st by vm.state.collectAsState()
            ParlaTheme(largeText = st.largeText) {
                ParlaRoot(vm)
            }
        }
    }
}
