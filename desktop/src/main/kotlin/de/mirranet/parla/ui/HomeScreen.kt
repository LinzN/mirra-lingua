package de.mirranet.parla.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.mirranet.parla.desktop.BuildInfo
import de.mirranet.parla.desktop.DesktopPlatform
import de.mirranet.parla.data.AppState
import de.mirranet.parla.engine.Course
import de.mirranet.parla.engine.ProgressLogic
import de.mirranet.parla.engine.Skill
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

// ---------------------------------------------------------------------- shell with tab bar

@Composable
fun HomeScreen(vm: AppViewModel, st: AppState, course: Course) {
    val tabs = listOf(
        TabItem("Lernen", Icons.Rounded.Home),
        TabItem("Fortschritt", Icons.Rounded.Star),
        TabItem("Einstellungen", Icons.Rounded.Settings),
    )
    // Ctrl+1..3 switches sections, like the tabs on the phone.
    KeyHandler { e ->
        val n = e.digit()
        if (e.isCtrlPressed && n != null && n <= tabs.size) { vm.tab = Tab.entries[n - 1]; true } else false
    }
    Row(Modifier.fillMaxSize().background(Ios.GroupedBackground)) {
        Sidebar(
            tabs, vm.tab.ordinal, { vm.tab = Tab.entries[it] },
            header = {
                Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(36.dp).clip(RoundedCornerShape(9.dp)).background(Ios.Green),
                        contentAlignment = Alignment.Center,
                    ) { Text("🌳", fontSize = 20.sp) }
                    Spacer(Modifier.width(10.dp))
                    Text("Parla", style = IosType.Title3)
                }
            },
            footer = {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { vm.screen = Screen.CoursePicker }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(course.flag, fontSize = 22.sp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(course.name, style = IosType.Callout, fontWeight = FontWeight.SemiBold)
                        Text("Kurs wechseln", style = IosType.Footnote, color = Ios.Blue)
                    }
                }
            },
        )
        Box(Modifier.weight(1f).fillMaxHeight()) {
            PageWidth {
                when (vm.tab) {
                    Tab.LEARN -> LearnTab(vm, st, course)
                    Tab.STATS -> StatsTab(vm, st, course)
                    Tab.SETTINGS -> SettingsTab(vm, st, course)
                }
            }
        }
    }
}
    }
}

// ---------------------------------------------------------------------- Lernen

@Composable
private fun LearnTab(vm: AppViewModel, st: AppState, course: Course) {
    var open by remember { mutableStateOf<Skill?>(null) }
    var lockedInfo by remember { mutableStateOf<Skill?>(null) }
    val p = st.progress
    val today = st.progress.xp[LocalDate.now().toString()] ?: 0
    // Units grouped by level, in course order.
    val groups = course.skills.fold(mutableListOf<Pair<String?, MutableList<Skill>>>()) { acc, s ->
        if (acc.isEmpty() || acc.last().first != s.level) acc.add(s.level to mutableListOf(s)) else acc.last().second.add(s)
        acc
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)) {
        item {
            LargeTitle("Lernen") {
                Row(
                    Modifier.clip(RoundedCornerShape(20.dp)).background(Color.White).clickable { vm.screen = Screen.CoursePicker }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(course.flag, fontSize = 20.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(course.name, style = IosType.Callout, fontWeight = FontWeight.SemiBold, color = Ios.Blue)
                }
            }
        }
        item { TodayCard(st, today) }
        item { ContinueCard(vm, st, course) }
        item {
            val weak = vm.weakSkills(course)
            if (course.skills.any { ProgressLogic.isDone(p, course, it) }) {
                Section {
                    ListRow(
                        title = if (weak.isNotEmpty()) "Wiederholen" else "Üben",
                        subtitle = if (weak.isNotEmpty()) "${weak.size} ${if (weak.size == 1) "Einheit verblasst" else "Einheiten verblassen"} – kurz auffrischen"
                        else "Gelerntes festigen",
                        leading = { IconTile("💪", Ios.Orange) },
                        chevron = true,
                        onClick = { vm.startPractice(null) },
                    )
                }
            }
        }
        groups.forEach { (level, skills) ->
            item {
                Section(header = levelName(level)) {
                    skills.forEachIndexed { i, skill ->
                        if (i > 0) RowDivider(inset = 66.dp)
                        UnitRow(st, course, skill) {
                            if (ProgressLogic.rowOpen(p, course, skill.row)) open = skill else lockedInfo = skill
                        }
                    }
                }
            }
        }
    }

    open?.let { UnitSheet(vm, st, course, it) { open = null } }
    lockedInfo?.let {
        IosAlert(
            title = "Noch gesperrt",
            message = "„${it.title}“ wird frei, sobald du die Einheiten davor abgeschlossen hast.",
            confirmText = "OK", dismissText = null,
            onConfirm = { lockedInfo = null }, onDismiss = { lockedInfo = null },
        )
    }
}

private fun levelName(level: String?): String = when (level?.uppercase()) {
    null -> "Einheiten"
    "A1" -> "Stufe A1 · Einstieg"
    "A2" -> "Stufe A2 · Grundlagen"
    "B1" -> "Stufe B1 · Fortgeschritten"
    "B2" -> "Stufe B2 · Selbstständig"
    else -> "Stufe $level"
}

@Composable
private fun TodayCard(st: AppState, today: Int) {
    Section {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Heute", style = IosType.Footnote, color = Ios.SecondaryLabel)
                Text("$today von ${st.settings.goal} XP", style = IosType.Headline)
                Spacer(Modifier.height(8.dp))
                ProgressBar(today.toFloat() / st.settings.goal, color = Ios.Orange)
            }
            Spacer(Modifier.width(20.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🔥", fontSize = 26.sp)
                val s = ProgressLogic.streak(st.progress.xp)
                Text("$s ${if (s == 1) "Tag" else "Tage"}", style = IosType.Subhead, fontWeight = FontWeight.SemiBold)
            }
        }
        if (st.syncing || st.offline || st.pendingEvents > 0) {
            RowDivider()
            Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (st.syncing) {
                    CircularProgressIndicator(Modifier.size(14.dp), color = Ios.SecondaryLabel, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    when {
                        st.syncing -> "Wird gespeichert …"
                        st.offline -> "Offline – dein Fortschritt wird später gespeichert."
                        else -> "Wird gleich gespeichert."
                    },
                    style = IosType.Footnote, color = Ios.SecondaryLabel,
                )
            }
        }
    }
}

@Composable
private fun ContinueCard(vm: AppViewModel, st: AppState, course: Course) {
    val next = vm.nextLesson(course)
    Section {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            if (next == null) {
                Text("Alles geschafft! 🎉", style = IosType.Title3)
                Text("Du hast alle Einheiten abgeschlossen. Mit „Üben“ hältst du alles frisch.",
                    style = IosType.Callout, color = Ios.SecondaryLabel, modifier = Modifier.padding(top = 4.dp, bottom = 14.dp))
                IosButton("Jetzt üben", { vm.startPractice(null) }, Modifier.fillMaxWidth())
            } else {
                val (skill, lesson) = next
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(skill.icon, Color(skill.color), size = 60.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (lesson == 0) "Als Nächstes" else "Weiter lernen", style = IosType.Footnote, color = Ios.SecondaryLabel)
                        Text(skill.title, style = IosType.Title3)
                        Text("Lektion ${lesson + 1} von ${skill.lessonCount} · ${ProgressLogic.difficultyLabel(vm.difficulty(course, skill))}",
                            style = IosType.Subhead, color = Ios.SecondaryLabel)
                    }
                }
                Spacer(Modifier.height(16.dp))
                IosButton("Lektion starten", { vm.startLesson(skill, lesson) }, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun UnitRow(st: AppState, course: Course, skill: Skill, onClick: () -> Unit) {
    val p = st.progress
    val open = ProgressLogic.rowOpen(p, course, skill.row)
    val sp = p.skill(course.id, skill.id)
    val done = sp.lessons >= skill.lessonCount
    val strength = ProgressLogic.strength(p, course, skill)
    ListRow(
        title = skill.title,
        titleColor = if (open) Ios.Label else Ios.SecondaryLabel,
        subtitle = when {
            !open -> "Gesperrt"
            done && strength < 4 -> "Fertig · bitte wiederholen"
            done -> "Fertig"
            sp.lessons > 0 -> "Lektion ${sp.lessons + 1} von ${skill.lessonCount}"
            else -> "${skill.lessonCount} Lektionen"
        },
        leading = { IconTile(skill.icon, Color(skill.color), dimmed = !open) },
        onClick = onClick,
        chevron = open && !done,
        trailing = {
            when {
                !open -> Icon(Icons.Rounded.Lock, contentDescription = "gesperrt", tint = Ios.TertiaryLabel, modifier = Modifier.size(20.dp))
                done -> StrengthBars(strength)
                sp.lessons > 0 -> Box(Modifier.width(56.dp)) { ProgressBar(sp.lessons.toFloat() / skill.lessonCount, color = Ios.Blue, height = 6.dp) }
            }
        },
    )
}

@Composable
private fun StrengthBars(strength: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
        (1..5).forEach {
            Box(Modifier.size(width = 6.dp, height = (6 + it * 3).dp).clip(RoundedCornerShape(2.dp))
                .background(if (it <= strength) (if (strength < 4) Ios.Orange else Ios.Green) else Ios.Fill))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UnitSheet(vm: AppViewModel, st: AppState, course: Course, skill: Skill, onClose: () -> Unit) {
    val p = st.progress
    val sp = p.skill(course.id, skill.id)
    val done = sp.lessons >= skill.lessonCount
    BackHandler { onClose() }
    Dialog(onDismissRequest = onClose) {
        Column(
            Modifier.widthIn(max = 520.dp).fillMaxWidth().heightIn(max = 680.dp)
                .clip(RoundedCornerShape(18.dp)).background(Ios.GroupedBackground)
                .verticalScroll(rememberScrollState()).padding(top = 20.dp, bottom = 16.dp),
        ) {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                IconTile(skill.icon, Color(skill.color), size = 56.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(skill.title, style = IosType.Title2)
                    val strength = ProgressLogic.strength(p, course, skill)
                    Text(
                        when {
                            done && strength >= 5 -> "Fertig – sitzt sicher"
                            done -> "Fertig – Stärke $strength von 5"
                            else -> "Lektion ${sp.lessons + 1} von ${skill.lessonCount}"
                        },
                        style = IosType.Callout, color = Ios.SecondaryLabel,
                    )
                }
            }
            Section(footer = "Die Aufgaben passen sich deinen Antworten an.") {
                skill.level?.let {
                    ListRow("Stufe", value = it)
                    RowDivider()
                }
                ListRow("Schwierigkeit", value = ProgressLogic.difficultyLabel(vm.difficulty(course, skill)))
                if (sp.answers > 0) {
                    RowDivider()
                    ListRow("Richtige Antworten", value = "${(sp.accuracy * 100).toInt()} %")
                }
            }
            Section(header = "Wörter") {
                FlowRow(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    skill.words.forEach { (t, d) ->
                        Column(Modifier.clip(RoundedCornerShape(10.dp)).background(Ios.FillLight).padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Text(t, style = IosType.Callout, fontWeight = FontWeight.SemiBold)
                            Text(d, style = IosType.Footnote, color = Ios.SecondaryLabel)
                        }
                    }
                }
            }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (done) {
                    IosButton("Üben", { onClose(); vm.startPractice(skill) }, Modifier.fillMaxWidth())
                    (0 until skill.lessonCount).forEach { i ->
                        IosButton("Lektion ${i + 1} wiederholen", { onClose(); vm.startLesson(skill, i) }, Modifier.fillMaxWidth(), style = ButtonStyle.Tinted)
                    }
                } else {
                    IosButton("Lektion ${sp.lessons + 1} starten", { onClose(); vm.startLesson(skill, sp.lessons) }, Modifier.fillMaxWidth())
                    if (sp.lessons > 0) {
                        IosButton("Lektion ${sp.lessons} wiederholen", { onClose(); vm.startLesson(skill, sp.lessons - 1) },
                            Modifier.fillMaxWidth(), style = ButtonStyle.Tinted)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------- course picker

@Composable
fun CoursePicker(vm: AppViewModel, st: AppState, firstTime: Boolean) {
    if (!firstTime) BackHandler { vm.screen = Screen.Home }
    Box(Modifier.fillMaxSize().background(Ios.GroupedBackground)) { PageWidth(600.dp) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 16.dp, bottom = 24.dp)) {
        if (!firstTime) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), contentAlignment = Alignment.CenterEnd) {
                IosButton("Fertig", { vm.screen = Screen.Home }, style = ButtonStyle.Plain, height = 44.dp)
            }
        }
        LargeTitle(if (firstTime) "Was möchtest du lernen?" else "Kurs wählen")
        Text("Alle Kurse sind für Deutschsprachige. Dein Fortschritt bleibt pro Kurs gespeichert.",
            style = IosType.Callout, color = Ios.SecondaryLabel, modifier = Modifier.padding(horizontal = 20.dp))
        Section {
            st.courses.forEachIndexed { i, c ->
                if (i > 0) RowDivider(inset = 72.dp)
                val done = c.skills.count { ProgressLogic.isDone(st.progress, c, it) }
                ListRow(
                    title = c.name,
                    subtitle = "$done von ${c.skills.size} Einheiten",
                    leading = { Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) { Text(c.flag, fontSize = 34.sp) } },
                    onClick = { vm.selectCourse(c.id) },
                    trailing = {
                        if (c.id == st.settings.course) Icon(Icons.Rounded.Check, contentDescription = "ausgewählt", tint = Ios.Blue)
                    },
                )
            }
        }
    }
    } }
}

// ---------------------------------------------------------------------- Fortschritt

@Composable
private fun StatsTab(vm: AppViewModel, st: AppState, course: Course) {
    val xp = st.progress.xp
    val today = LocalDate.now()
    val p = st.progress
    val skills = p.courses[course.id].orEmpty().values.filter { it.answers > 0 }
    val accuracy = if (skills.isEmpty()) null
    else (100 * skills.sumOf { it.accuracy * it.answers } / skills.sumOf { it.answers }).toInt()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 16.dp, bottom = 24.dp)) {
        LargeTitle("Fortschritt")
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("🔥", "${ProgressLogic.streak(xp)}", "Tage in Folge", Modifier.weight(1f))
                StatTile("⚡", "${xp.values.sum()}", "XP gesamt", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(course.flag, "${course.skills.count { ProgressLogic.isDone(p, course, it) }}/${course.skills.size}", "Einheiten fertig", Modifier.weight(1f))
                StatTile("🎯", accuracy?.let { "$it %" } ?: "–", "richtig beantwortet", Modifier.weight(1f))
            }
        }
        Section(header = "Diese Woche") {
            Box(Modifier.padding(16.dp)) { WeekChart(xp, st.settings.goal, today) }
        }
        val goals = listOf(10 to "Locker", 20 to "Normal", 30 to "Ernsthaft", 50 to "Intensiv")
        val sel = goals.indexOfFirst { it.first == st.settings.goal }.coerceAtLeast(0)
        Section(header = "Tagesziel", footer = "${goals[sel].second}: ${goals[sel].first} XP am Tag – das sind etwa ${goals[sel].first / 12 + 1} Lektionen.") {
            Box(Modifier.padding(12.dp)) {
                Segmented(goals.map { "${it.first}" }, sel, { i -> vm.repo.updateSettings { it.copy(goal = goals[i].first) } })
            }
        }
    }
}

@Composable
private fun StatTile(emoji: String, value: String, label: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(Color.White).padding(16.dp)) {
        Text(emoji, fontSize = 22.sp)
        Spacer(Modifier.height(6.dp))
        Text(value, style = IosType.Title2)
        Text(label, style = IosType.Footnote, color = Ios.SecondaryLabel)
    }
}

@Composable
private fun WeekChart(xp: Map<String, Int>, goal: Int, today: LocalDate) {
    val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val values = days.map { xp[it.toString()] ?: 0 }
    val max = maxOf(goal * 1.25f, (values.maxOrNull() ?: 0).toFloat(), 10f)
    val area = 120.dp
    Column {
        Box(Modifier.fillMaxWidth().height(area)) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                days.indices.forEach { i ->
                    Box(Modifier.weight(1f).height(area), contentAlignment = Alignment.BottomCenter) {
                        Box(Modifier.fillMaxWidth().height(area * (values[i] / max)).clip(RoundedCornerShape(6.dp))
                            .background(if (values[i] >= goal) Ios.Green else if (i == 6) Ios.Blue else Ios.Blue.copy(alpha = 0.45f)))
                    }
                }
            }
            Canvas(Modifier.fillMaxSize()) {
                val y = size.height * (1f - goal / max)
                drawLine(Ios.Orange, Offset(0f, y), Offset(size.width, y), 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            days.forEachIndexed { i, d ->
                Text(d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.GERMAN).take(2), Modifier.weight(1f),
                    style = IosType.Footnote, color = if (i == 6) Ios.Label else Ios.SecondaryLabel,
                    fontWeight = if (i == 6) FontWeight.SemiBold else FontWeight.Normal, textAlign = TextAlign.Center)
            }
        }
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 16.dp, height = 2.dp).background(Ios.Orange))
            Text("  Tagesziel $goal XP", style = IosType.Footnote, color = Ios.SecondaryLabel)
        }
    }
}

// ---------------------------------------------------------------------- Einstellungen

@Composable
private fun SettingsTab(vm: AppViewModel, st: AppState, course: Course) {
    var dialog by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 16.dp, bottom = 24.dp)) {
        LargeTitle("Einstellungen")
        Section(header = "Kurs") {
            ListRow("Ich lerne", value = "${course.flag} ${course.name}", chevron = true,
                leading = { IconTile("🌍", Ios.Blue) }, onClick = { vm.screen = Screen.CoursePicker })
        }
        Section(
            header = "Lernen",
            footer = if (!vm.audio.available) "Für ${course.name} ist auf diesem Computer keine Sprachausgabe installiert. " +
                    (if (DesktopPlatform.isWindows) "Du kannst sie in den Windows-Einstellungen unter „Zeit und Sprache“ → „Sprache“ nachinstallieren."
                    else "Installiere dafür das Paket „speech-dispatcher“ oder „espeak-ng“.") else null,
        ) {
            ListRow("Töne", leading = { IconTile("🔔", Ios.Red) },
                trailing = { IosSwitch(st.settings.sound, { v -> vm.repo.updateSettings { it.copy(sound = v) } }) })
            RowDivider(inset = 66.dp)
            ListRow("Hörübungen", leading = { IconTile("🎧", Ios.Purple) },
                trailing = { IosSwitch(st.settings.listen, { v -> vm.repo.updateSettings { it.copy(listen = v) } }) })
        }
        Section(header = "Anzeige", footer = "Macht alle Texte in der App größer.") {
            ListRow("Große Schrift", leading = { IconTile("🔍", Ios.Teal) },
                trailing = { IosSwitch(st.largeText, { vm.repo.setLargeText(it) }) })
        }
        Section(
            header = "Konto",
            footer = when {
                st.offline -> "Gerade keine Verbindung. Dein Fortschritt wird gespeichert, sobald du wieder online bist."
                st.pendingEvents > 0 -> "${st.pendingEvents} Ergebnis(se) werden gleich gespeichert."
                else -> "Dein Fortschritt ist sicher auf dem Server gespeichert."
            },
        ) {
            ListRow("Angemeldet als", value = st.username ?: "", leading = { IconTile("👤", Ios.SecondaryLabel) })
            RowDivider(inset = 66.dp)
            ListRow("Jetzt speichern", leading = { IconTile("🔄", Ios.Green) }, onClick = { vm.sync() },
                trailing = { if (st.syncing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Ios.SecondaryLabel) })
            RowDivider(inset = 66.dp)
            ListRow("Passwort ändern", leading = { IconTile("🔑", Ios.Orange) }, chevron = true, onClick = { dialog = "password" })
            RowDivider(inset = 66.dp)
            ListRow("Abmelden", titleColor = Ios.Blue, leading = { IconTile("🚪", Ios.Blue) }, onClick = { dialog = "logout" })
        }
        Section {
            ListRow("${course.name} von vorne beginnen", titleColor = Ios.Red, onClick = { dialog = "reset" })
            RowDivider()
            ListRow("Konto löschen", titleColor = Ios.Red, onClick = { dialog = "delete" })
        }
        Text("Parla ${BuildInfo.version} · ${st.serverUrl.removePrefix("https://")}",
            style = IosType.Footnote, color = Ios.TertiaryLabel, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(16.dp))
    }

    when (dialog) {
        "reset" -> IosAlert(
            "${course.name} von vorne beginnen?",
            "Alle Einheiten in ${course.name} werden zurückgesetzt. Deine XP und deine Serie bleiben.",
            "Zurücksetzen", { vm.repo.resetCourse(course.id); dialog = null }, { dialog = null }, destructive = true,
        )
        "logout" -> IosAlert(
            "Abmelden?",
            if (st.pendingEvents > 0) "Es gibt noch Ergebnisse, die nicht gespeichert sind. Ohne Internet gehen sie verloren."
            else "Dein Fortschritt bleibt auf dem Server. Du kannst dich jederzeit wieder anmelden.",
            "Abmelden", { dialog = null; vm.logout { } }, { dialog = null }, destructive = true,
        )
        "password" -> PasswordAlert(vm) { dialog = null }
        "delete" -> DeleteAlert(vm) { dialog = null }
    }
}

@Composable
private fun PasswordAlert(vm: AppViewModel, onDismiss: () -> Unit) {
    var old by remember { mutableStateOf(TextFieldValue("")) }
    var new by remember { mutableStateOf(TextFieldValue("")) }
    var msg by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf(false) }
    if (done) {
        IosAlert("Passwort geändert", "Auf deinen anderen Geräten musst du dich neu anmelden.", "OK", onDismiss, onDismiss, dismissText = null)
        return
    }
    IosAlert(
        "Passwort ändern", null, "Ändern",
        onConfirm = {
            if (new.text.length < 8) msg = "Das neue Passwort braucht mindestens 8 Zeichen."
            else vm.changePassword(old.text, new.text) { err -> if (err == null) done = true else msg = err }
        },
        onDismiss = onDismiss,
    ) {
        IosTextField(old, { old = it }, "Aktuelles Passwort", password = true)
        IosTextField(new, { new = it }, "Neues Passwort", password = true)
        msg?.let { Text(it, style = IosType.Footnote, color = Ios.RedDark) }
    }
}

@Composable
private fun DeleteAlert(vm: AppViewModel, onDismiss: () -> Unit) {
    var pw by remember { mutableStateOf(TextFieldValue("")) }
    var msg by remember { mutableStateOf<String?>(null) }
    IosAlert(
        "Konto löschen?", "Dein Konto und dein ganzer Fortschritt werden für immer gelöscht.", "Löschen",
        onConfirm = { vm.deleteAccount(pw.text) { err -> if (err == null) onDismiss() else msg = err } },
        onDismiss = onDismiss, destructive = true,
    ) {
        IosTextField(pw, { pw = it }, "Passwort zur Bestätigung", password = true)
        msg?.let { Text(it, style = IosType.Footnote, color = Ios.RedDark) }
    }
}
