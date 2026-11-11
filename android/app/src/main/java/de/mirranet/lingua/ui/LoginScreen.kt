package de.mirranet.lingua.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LoginScreen(vm: AppViewModel, serverUrl: String) {
    var register by rememberSaveable { mutableStateOf(false) }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var server by rememberSaveable { mutableStateOf(serverUrl) }
    var showServer by rememberSaveable { mutableStateOf(false) }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }

    fun submit() {
        localError = when {
            username.isBlank() || password.isEmpty() -> "Bitte Benutzername und Passwort eingeben."
            register && password.length < 8 -> "Das Passwort muss mindestens 8 Zeichen lang sein."
            register && password != confirm -> "Die Passwörter stimmen nicht überein."
            !server.startsWith("https://") && !server.startsWith("http://") -> "Die Serveradresse muss mit https:// beginnen."
            else -> null
        }
        if (localError == null) vm.authenticate(server, username, password, register)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.White)
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))
        Text("🌳", fontSize = 64.sp)
        Text("MirraLingua", fontSize = 34.sp, fontWeight = FontWeight.Black, color = Palette.Green)
        Text("Sprachen lernen – kostenlos und ohne Werbung", color = Palette.Muted, fontSize = 15.sp)
        Spacer(Modifier.height(28.dp))

        Column(Modifier.widthIn(max = 420.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ChunkyButton("Anmelden", { register = false; localError = null }, Modifier.weight(1f),
                    color = Palette.Blue, lip = Palette.BlueDark, outlined = register, textColor = Palette.Blue)
                ChunkyButton("Registrieren", { register = true; localError = null }, Modifier.weight(1f),
                    color = Palette.Blue, lip = Palette.BlueDark, outlined = !register, textColor = Palette.Blue)
            }
            OutlinedTextField(
                value = username, onValueChange = { username = it }, label = { Text("Benutzername") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next, autoCorrectEnabled = false),
            )
            OutlinedTextField(
                value = password, onValueChange = { password = it }, label = { Text("Passwort") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = if (register) ImeAction.Next else ImeAction.Done),
            )
            if (register) {
                OutlinedTextField(
                    value = confirm, onValueChange = { confirm = it }, label = { Text("Passwort wiederholen") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                )
            }
            if (showServer) {
                OutlinedTextField(
                    value = server, onValueChange = { server = it.trim() }, label = { Text("Server") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                )
            }
            (localError ?: vm.error)?.let {
                Text(it, color = Palette.RedDark, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))
            ChunkyButton(
                if (vm.busy) "Bitte warten …" else if (register) "Konto erstellen" else "Anmelden",
                { submit() }, Modifier.fillMaxWidth(), enabled = !vm.busy,
            )
            TextButton(onClick = { showServer = !showServer }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(if (showServer) "Serveradresse ausblenden" else "Server: ${server.removePrefix("https://")}", color = Palette.Muted)
            }
        }
    }
}
