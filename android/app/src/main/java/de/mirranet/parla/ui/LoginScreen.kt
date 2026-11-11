package de.mirranet.parla.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LoginScreen(vm: AppViewModel, serverUrl: String) {
    var register by rememberSaveable { mutableStateOf(false) }
    var username by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var password by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var confirm by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var server by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(serverUrl)) }
    var showServer by rememberSaveable { mutableStateOf(false) }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }

    fun submit() {
        val url = server.text.trim()
        localError = when {
            username.text.isBlank() || password.text.isEmpty() -> "Bitte Benutzername und Passwort eingeben."
            register && password.text.length < 8 -> "Das Passwort muss mindestens 8 Zeichen lang sein."
            register && password.text != confirm.text -> "Die beiden Passwörter sind nicht gleich."
            !url.startsWith("https://") && !url.startsWith("http://") -> "Die Serveradresse muss mit https:// beginnen."
            else -> null
        }
        if (localError == null) vm.authenticate(url, username.text, password.text, register)
    }

    Column(
        Modifier.fillMaxSize().background(Ios.GroupedBackground).systemBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        // App icon
        Box(
            Modifier.size(96.dp).shadow(8.dp, RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF5AD27A), Ios.GreenDark))),
            contentAlignment = Alignment.Center,
        ) { Text("🌳", fontSize = 52.sp) }
        Spacer(Modifier.height(18.dp))
        Text("Parla", style = IosType.Title1)
        Text("Sprachen lernen – einfach,\nkostenlos und ohne Werbung", style = IosType.Callout, color = Ios.SecondaryLabel,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(32.dp))

        Column(Modifier.widthIn(max = 440.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Segmented(listOf("Anmelden", "Neues Konto"), if (register) 1 else 0, { register = it == 1; localError = null })
            Spacer(Modifier.height(4.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White).padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                IosTextField(username, { username = it }, "Benutzername", keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next)
                IosTextField(password, { password = it }, "Passwort", password = true,
                    imeAction = if (register) ImeAction.Next else ImeAction.Done)
                if (register) IosTextField(confirm, { confirm = it }, "Passwort wiederholen", password = true, imeAction = ImeAction.Done)
                if (showServer) IosTextField(server, { server = it }, "Serveradresse", keyboardType = KeyboardType.Uri)
            }
            if (register) {
                Text("Wähle einen Namen, den du dir gut merken kannst. Das Passwort braucht mindestens 8 Zeichen.",
                    style = IosType.Footnote, color = Ios.SecondaryLabel, modifier = Modifier.padding(horizontal = 8.dp))
            }
            (localError ?: vm.error)?.let {
                Text(it, style = IosType.Callout, color = Ios.RedDark, modifier = Modifier.padding(horizontal = 8.dp))
            }
            Spacer(Modifier.height(4.dp))
            IosButton(
                if (vm.busy) "Einen Moment …" else if (register) "Konto erstellen" else "Anmelden",
                { submit() }, Modifier.fillMaxWidth(), enabled = !vm.busy,
            )
            IosButton(
                if (showServer) "Serveradresse ausblenden" else "Server: ${server.text.removePrefix("https://")}",
                { showServer = !showServer }, Modifier.fillMaxWidth(), style = ButtonStyle.Plain, height = 44.dp,
            )
        }
        Spacer(Modifier.height(32.dp))
    }
}
