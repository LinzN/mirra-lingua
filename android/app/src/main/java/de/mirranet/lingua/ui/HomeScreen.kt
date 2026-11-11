package de.mirranet.lingua.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.mirranet.lingua.data.AppState
import de.mirranet.lingua.engine.Course
import de.mirranet.lingua.engine.ProgressLogic
import de.mirranet.lingua.engine.Skill
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

// ---------------------------------------------------------------------- header + tabs

@Composable
fun HomeScreen(vm: AppViewModel, st: AppState, course: Course) {
    val today = LocalDate.now().toString()
    Column(Modifier.fillMaxSize().background(Color.White)) {
        Column(Modifier.background(Palette.Green).statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier.weight(1f).clickable { vm.screen = Screen.CoursePicker },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("MirraLingua", color = Color.White, fontWeight = FontWeight.Black, fontSize = 21.sp)
                    Text("  ${course.flag} ${course.name} ▾", color = Color.White.copy(alpha = 0.85f), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                if (st.syncing) {
                    CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                } else if (st.offline) {
                    Text("offline", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { vm.sync() })
                    Spacer(Modifier.width(10.dp))
                }
                Text("🔥 ${ProgressLogic.streak(st.progress.xp)}   ⚡ ${st.progress.xp[today] ?: 0}/${st.settings.goal}",
                    color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
            }
        }
        Row(Modifier.fillMaxWidth()) {
            TabButton("Lernen", vm.tab == Tab.LEARN, Modifier.weight(1f)) { vm.tab = Tab.LEARN }
            TabButton("Fortschritt", vm.tab == Tab.STATS, Modifier.weight(1f)) { vm.tab = Tab.STATS }
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(Palette.Gray))
        Box(Modifier.weight(1f)) {
            if (vm.tab == Tab.LEARN) SkillTree(vm, st, course) else StatsTab(vm, st, course)
        }
    }
}

@Composable
private fun TabButton(text: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text.uppercase(), Modifier.padding(vertical = 13.dp), fontWeight = FontWeight.ExtraBold, fontSize = 14.sp,
            color = if (active) Palette.Blue else Palette.GrayDark, letterSpacing = 0.5.sp)
        Box(Modifier.fillMaxWidth().height(3.dp).background(if (active) Palette.Blue else Color.Transparent))
    }
}

// ---------------------------------------------------------------------- skill tree

@Composable
private fun SkillTree(vm: AppViewModel, st: AppState, course: Course) {
    var open by remember { mutableStateOf<Skill?>(null) }
    val p = st.progress
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        if (course.skills.none { p.skill(course.id, it.id).lessons > 0 }) {
            item {
                Text(
                    "Willkommen! Arbeite dich Fertigkeit für Fertigkeit durch den Baum. Jede Lektion hat 3 Herzen – " +
                            "bei jedem Fehler verlierst du eins. Tipp auf ein unterstrichenes Wort, um seine Bedeutung zu sehen.",
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Palette.BlueLight).padding(14.dp),
                    fontSize = 15.sp, lineHeight = 21.sp,
                )
            }
        }
        if (course.skills.any { ProgressLogic.isDone(p, course, it) }) {
            item {
                Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                    ChunkyButton("💪 Fertigkeiten stärken", { vm.startPractice(null) }, Modifier.widthIn(min = 260.dp),
                        color = Palette.Blue, lip = Palette.BlueDark)
                }
            }
        }
        items(course.rows) { row ->
            val unlocked = ProgressLogic.rowOpen(p, course, row)
            Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.Center) {
                course.skills.filter { it.row == row }.forEachIndexed { i, skill ->
                    if (i > 0) Spacer(Modifier.width(26.dp))
                    SkillNode(skill, unlocked, p.skill(course.id, skill.id).lessons,
                        ProgressLogic.strength(p, course, skill)) { open = skill }
                }
            }
        }
        item { Spacer(Modifier.height(40.dp)) }
    }
    open?.let { SkillSheet(vm, st, course, it) { open = null } }
}

@Composable
private fun SkillNode(skill: Skill, unlocked: Boolean, lessonsDone: Int, strength: Int, onClick: () -> Unit) {
    val done = lessonsDone >= skill.lessonCount
    val face = when {
        !unlocked -> Palette.Gray
        done && strength < 4 -> Color(0xFFFFF2C7)
        done -> Palette.Gold
        else -> Color(skill.color)
    }
    Column(Modifier.width(104.dp).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(92.dp), contentAlignment = Alignment.Center) {
            if (unlocked && !done && lessonsDone > 0) {
                Canvas(Modifier.size(92.dp)) {
                    drawArc(Palette.Gray, 0f, 360f, false, style = Stroke(7.dp.toPx()))
                    drawArc(Palette.Gold, -90f, 360f * lessonsDone / skill.lessonCount, false, style = Stroke(7.dp.toPx()))
                }
            }
            Box(Modifier.size(78.dp).padding(top = 5.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.18f)))
            Box(Modifier.size(78.dp).padding(bottom = 5.dp).clip(CircleShape).background(face), contentAlignment = Alignment.Center) {
                Text(skill.icon, fontSize = 32.sp, color = if (unlocked) Color.Unspecified else Color.Black.copy(alpha = 0.35f))
            }
        }
        Text(skill.title, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, textAlign = TextAlign.Center,
            color = if (unlocked) Palette.Text else Palette.GrayDark, lineHeight = 17.sp)
        when {
            done -> Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                (1..5).forEach { Box(Modifier.size(12.dp, 6.dp).clip(RoundedCornerShape(3.dp)).background(if (it <= strength) Palette.Gold else Palette.Gray)) }
            }
            unlocked -> Text("$lessonsDone/${skill.lessonCount}", fontSize = 12.sp, color = Palette.Muted, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SkillSheet(vm: AppViewModel, st: AppState, course: Course, skill: Skill, onClose: () -> Unit) {
    val p = st.progress
    val unlocked = ProgressLogic.rowOpen(p, course, skill.row)
    val sp = p.skill(course.id, skill.id)
    val done = sp.lessons >= skill.lessonCount
    ModalBottomSheet(onDismissRequest = onClose, containerColor = Color.White) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState())) {
            Text("${skill.icon} ${skill.title}", fontSize = 22.sp, fontWeight = FontWeight.Black)
            val info = when {
                !unlocked -> "Diese Fertigkeit ist noch gesperrt. Schließe zuerst alle Fertigkeiten darüber ab."
                done -> {
                    val s = ProgressLogic.strength(p, course, skill)
                    (if (s >= 5) "Gemeistert! Alle Lektionen abgeschlossen." else "Diese Fertigkeit verblasst langsam – Zeit zum Wiederholen.") + " Stärke: $s/5"
                }
                else -> "Lektion ${sp.lessons + 1} von ${skill.lessonCount}"
            }
            Text(info, color = Palette.Muted, modifier = Modifier.padding(vertical = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                skill.words.forEach { (t, d) ->
                    Text("$t – $d", Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xFFF7F7F7))
                        .border(2.dp, Palette.Gray, RoundedCornerShape(10.dp)).padding(horizontal = 9.dp, vertical = 4.dp), fontSize = 14.sp)
                }
            }
            Spacer(Modifier.height(18.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when {
                    !unlocked -> ChunkyButton("OK", onClose, Modifier.fillMaxWidth(), outlined = true, textColor = Palette.Blue)
                    done -> {
                        ChunkyButton("Stärken", { onClose(); vm.startPractice(skill) }, Modifier.fillMaxWidth())
                        (0 until skill.lessonCount).forEach { i ->
                            ChunkyButton("Lektion ${i + 1} wiederholen", { onClose(); vm.startLesson(skill, i) }, Modifier.fillMaxWidth(),
                                outlined = true, textColor = Palette.Blue)
                        }
                    }
                    else -> {
                        ChunkyButton("Lektion starten", { onClose(); vm.startLesson(skill, sp.lessons) }, Modifier.fillMaxWidth())
                        if (sp.lessons > 0) {
                            ChunkyButton("Vorherige Lektion wiederholen", { onClose(); vm.startLesson(skill, sp.lessons - 1) },
                                Modifier.fillMaxWidth(), outlined = true, textColor = Palette.Blue)
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------- course picker

@Composable
fun CoursePicker(vm: AppViewModel, st: AppState, firstTime: Boolean) {
    Column(
        Modifier.fillMaxSize().background(Color.White).statusBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(if (firstTime) "Was möchtest du lernen?" else "Kurs wählen", fontSize = 26.sp, fontWeight = FontWeight.Black)
        Text("Für Deutschsprachige. Dein Fortschritt bleibt pro Kurs erhalten.", color = Palette.Muted)
        st.courses.forEach { c ->
            val done = c.skills.count { ProgressLogic.isDone(st.progress, c, it) }
            val selected = c.id == st.settings.course
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(if (selected) Palette.BlueLight else Color.White)
                    .border(2.dp, if (selected) Color(0xFF84D8FF) else Palette.Gray, RoundedCornerShape(14.dp))
                    .clickable { vm.selectCourse(c.id) }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(c.flag, fontSize = 32.sp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(c.name, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    Text("$done/${c.skills.size} Fertigkeiten", color = Palette.Muted, fontSize = 14.sp)
                }
            }
        }
        if (!firstTime) {
            TextButton(onClick = { vm.screen = Screen.Home }) { Text("Zurück", color = Palette.Blue, fontWeight = FontWeight.Bold) }
        }
    }
}

// ---------------------------------------------------------------------- stats + settings

@Composable
private fun StatsTab(vm: AppViewModel, st: AppState, course: Course) {
    val xp = st.progress.xp
    val today = LocalDate.now()
    var dialog by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("🔥 ${ProgressLogic.streak(xp)}", "Tage in Folge", Modifier.weight(1f))
            StatCard("${xp.values.sum()}", "XP gesamt", Modifier.weight(1f))
            StatCard("${course.skills.count { ProgressLogic.isDone(st.progress, course, it) }}/${course.skills.size}", "Fertigkeiten ${course.flag}", Modifier.weight(1f))
        }
        Section("Heute: ${xp[today.toString()] ?: 0} von ${st.settings.goal} XP") {
            val f = ((xp[today.toString()] ?: 0).toFloat() / st.settings.goal).coerceIn(0f, 1f)
            Box(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(Palette.Gray)) {
                Box(Modifier.fillMaxWidth(f).fillMaxHeight().background(Palette.Orange))
            }
        }
        Section("Letzte 7 Tage") { WeekChart(xp, st.settings.goal, today) }
        Section("Tagesziel") {
            val goals = listOf(10 to "Locker", 20 to "Normal", 30 to "Ernsthaft", 50 to "Wahnsinnig")
            goals.chunked(2).forEach { pair ->
                Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { (v, name) ->
                        val on = st.settings.goal == v
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (on) Palette.BlueLight else Color.White)
                                .border(2.dp, if (on) Color(0xFF84D8FF) else Palette.Gray, RoundedCornerShape(12.dp))
                                .clickable { vm.repo.updateSettings { it.copy(goal = v) } }.padding(10.dp),
                        ) {
                            Text(name, fontWeight = FontWeight.ExtraBold, color = if (on) Palette.BlueDark else Palette.Text)
                            Text("$v XP pro Tag", fontSize = 13.sp, color = Palette.Muted)
                        }
                    }
                }
            }
        }
        Section("Einstellungen") {
            ToggleRow("Soundeffekte", st.settings.sound) { v -> vm.repo.updateSettings { it.copy(sound = v) } }
            ToggleRow("Hörübungen", st.settings.listen) { v -> vm.repo.updateSettings { it.copy(listen = v) } }
            if (!vm.audio.available) {
                Text("Für ${course.name} ist auf diesem Gerät keine Sprachausgabe installiert.", fontSize = 13.sp, color = Palette.Muted)
            }
            Spacer(Modifier.height(10.dp))
            ChunkyButton("${course.name} zurücksetzen", { dialog = "reset" }, Modifier.fillMaxWidth(), outlined = true, textColor = Palette.RedDark)
        }
        Section("Konto: ${st.username ?: ""}") {
            Text(
                if (st.pendingEvents > 0) "${st.pendingEvents} Ergebnis(se) warten auf die Übertragung zum Server."
                else "Alles mit ${st.serverUrl.removePrefix("https://")} synchronisiert.",
                fontSize = 14.sp, color = Palette.Muted,
            )
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ChunkyButton("Jetzt synchronisieren", { vm.sync() }, Modifier.fillMaxWidth(), outlined = true, textColor = Palette.Blue)
                ChunkyButton("Passwort ändern", { dialog = "password" }, Modifier.fillMaxWidth(), outlined = true, textColor = Palette.Blue)
                ChunkyButton("Abmelden", { dialog = "logout" }, Modifier.fillMaxWidth(), outlined = true, textColor = Palette.Blue)
                ChunkyButton("Konto löschen", { dialog = "delete" }, Modifier.fillMaxWidth(), outlined = true, textColor = Palette.RedDark)
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    when (dialog) {
        "reset" -> ConfirmDialog("${course.name} zurücksetzen?",
            "Dein Fortschritt im ${course.name}-Kurs wird gelöscht. XP und Serie bleiben erhalten.", "Zurücksetzen",
            onConfirm = { vm.repo.resetCourse(course.id); dialog = null }, onDismiss = { dialog = null })
        "logout" -> ConfirmDialog("Abmelden?",
            if (st.pendingEvents > 0) "Es gibt noch nicht übertragene Ergebnisse. Ich versuche sie vorher hochzuladen – ohne Verbindung gehen sie verloren."
            else "Dein Fortschritt ist auf dem Server gespeichert.", "Abmelden",
            onConfirm = { dialog = null; vm.logout { } }, onDismiss = { dialog = null })
        "password" -> PasswordDialog(vm) { dialog = null }
        "delete" -> DeleteDialog(vm) { dialog = null }
    }
}

@Composable
private fun StatCard(value: String, label: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).border(2.dp, Palette.Gray, RoundedCornerShape(16.dp)).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Black)
        Text(label, fontSize = 12.sp, color = Palette.Muted, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).border(2.dp, Palette.Gray, RoundedCornerShape(16.dp)).padding(16.dp)) {
        Text(title, fontWeight = FontWeight.Black, fontSize = 17.sp, modifier = Modifier.padding(bottom = 12.dp))
        content()
    }
}

@Composable
private fun ToggleRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontWeight = FontWeight.Bold)
        Switch(checked = value, onCheckedChange = onChange)
    }
}

@Composable
private fun WeekChart(xp: Map<String, Int>, goal: Int, today: LocalDate) {
    val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val values = days.map { xp[it.toString()] ?: 0 }
    val max = maxOf(goal * 1.25f, (values.maxOrNull() ?: 0).toFloat(), 10f)
    val barArea = 110.dp
    Box(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            days.forEachIndexed { i, d ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (values[i] > 0) "${values[i]}" else "", fontSize = 12.sp, color = Palette.Muted, fontWeight = FontWeight.Bold)
                    Box(Modifier.height(barArea), contentAlignment = Alignment.BottomCenter) {
                        Box(Modifier.width(30.dp).height(barArea * (values[i] / max)).clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .background(if (i == 6) Palette.Green else Palette.Gold))
                    }
                    Text(d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.GERMAN).take(2), fontSize = 12.sp, color = Palette.Muted, fontWeight = FontWeight.Bold)
                }
            }
        }
        // Goal line
        Canvas(Modifier.fillMaxWidth().height(barArea)) {
            val y = size.height * (1f - goal / max)
            val dash = 8.dp.toPx()
            var x = 0f
            while (x < size.width) {
                drawLine(Palette.Orange, Offset(x, y + 17.dp.toPx()), Offset(minOf(x + dash, size.width), y + 17.dp.toPx()), 2.dp.toPx())
                x += dash * 2
            }
        }
    }
}

@Composable
private fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Color.White,
        title = { Text(title, fontWeight = FontWeight.Black) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm, color = Palette.RedDark, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen", color = Palette.Blue) } },
    )
}

@Composable
private fun PasswordDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    var old by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Color.White,
        title = { Text("Passwort ändern", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (done) {
                    Text("Passwort geändert. Andere Geräte wurden abgemeldet.")
                } else {
                    PasswordField("Aktuelles Passwort", old) { old = it }
                    PasswordField("Neues Passwort (min. 8 Zeichen)", new) { new = it }
                    msg?.let { Text(it, color = Palette.RedDark) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (done) onDismiss()
                else if (new.length < 8) msg = "Das neue Passwort ist zu kurz."
                else vm.changePassword(old, new) { err -> if (err == null) done = true else msg = err }
            }) { Text(if (done) "OK" else "Ändern", fontWeight = FontWeight.Bold, color = Palette.Blue) }
        },
        dismissButton = { if (!done) TextButton(onClick = onDismiss) { Text("Abbrechen", color = Palette.Muted) } },
    )
}

@Composable
private fun DeleteDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    var pw by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Color.White,
        title = { Text("Konto löschen?", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Dein Konto und dein gesamter Fortschritt werden dauerhaft vom Server gelöscht.")
                PasswordField("Passwort zur Bestätigung", pw) { pw = it }
                msg?.let { Text(it, color = Palette.RedDark) }
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.deleteAccount(pw) { err -> if (err == null) onDismiss() else msg = err } }) {
                Text("Endgültig löschen", color = Palette.RedDark, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen", color = Palette.Blue) } },
    )
}

@Composable
private fun PasswordField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
    )
}
