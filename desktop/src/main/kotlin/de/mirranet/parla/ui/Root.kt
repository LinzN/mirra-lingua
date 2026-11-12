package de.mirranet.parla.ui

import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import kotlinx.coroutines.delay
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
    // Short notices from the repository (the phone shows these as toasts).
    var notice by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(st.message) {
        st.message?.let {
            notice = it
            vm.repo.consumeMessage()
            delay(3500)
            notice = null
        }
    }

    Box(Modifier.fillMaxSize()) {
        Screens(vm, st)
        AnimatedVisibility(
            visible = notice != null, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp),
            enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut(),
        ) {
            Text(
                notice ?: "", style = IosType.Callout, color = Color.White, textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 480.dp).shadow(6.dp, RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp))
                    .background(Color(0xE6303034)).padding(horizontal = 18.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun Screens(vm: AppViewModel, st: de.mirranet.parla.data.AppState) {
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
