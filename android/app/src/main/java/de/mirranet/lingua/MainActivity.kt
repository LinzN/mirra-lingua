package de.mirranet.lingua

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import de.mirranet.lingua.ui.AppViewModel
import de.mirranet.lingua.ui.LinguaRoot
import de.mirranet.lingua.ui.LinguaTheme

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
            LinguaTheme {
                LinguaRoot(vm)
            }
        }
    }
}
