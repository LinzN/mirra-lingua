package de.mirranet.parla.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.mirranet.parla.engine.ProgressLogic
import java.time.LocalDate

/** Top-level navigation: login → content download → course choice → tree / lesson. */
@Composable
fun ParlaRoot(vm: AppViewModel) {
    val st by vm.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(st.message) {
        st.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            vm.repo.consumeMessage()
        }
    }

    when {
        !st.loggedIn -> LoginScreen(vm, st.serverUrl)
        st.courses.isEmpty() -> WaitingForContent(vm, offline = st.offline, syncing = st.syncing)
        st.course == null -> CoursePicker(vm, st, firstTime = true)
        else -> when (val sc = vm.screen) {
            Screen.Home -> HomeScreen(vm, st, st.course!!)
            Screen.CoursePicker -> CoursePicker(vm, st, firstTime = false)
            is Screen.Lesson -> LessonScreen(vm, sc.session)
            is Screen.Result -> ResultScreen(vm, sc, ProgressLogic.streak(st.progress.xp),
                st.progress.xp[LocalDate.now().toString()] ?: 0, st.settings.goal)
            is Screen.Failed -> FailScreen(vm, sc)
        }
    }
}

@Composable
private fun WaitingForContent(vm: AppViewModel, offline: Boolean, syncing: Boolean) {
    Column(
        Modifier.fillMaxSize().background(Ios.GroupedBackground).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        if (syncing) {
            CircularProgressIndicator(color = Ios.Blue)
            Spacer(Modifier.height(20.dp))
            Text("Kurse werden geladen …", style = IosType.Headline)
        } else {
            Text(if (offline) "📡" else "⚠️", fontSize = 56.sp)
            Text(if (offline) "Keine Verbindung" else "Kurse konnten nicht geladen werden", style = IosType.Title2,
                textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
            Text(if (offline) "Die Kursinhalte kommen vom Server. Verbinde dich einmal mit dem Internet, danach geht es auch offline."
                else "Der Server hat keine Kurse geliefert. Versuch es gleich noch einmal.",
                style = IosType.Callout, color = Ios.SecondaryLabel, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 12.dp))
            IosButton("Erneut versuchen", { vm.sync() }, Modifier.fillMaxWidth())
        }
    }
}
