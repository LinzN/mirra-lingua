package de.mirranet.lingua.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.mirranet.lingua.engine.Exercise
import kotlinx.coroutines.delay

@Composable
fun LessonScreen(vm: AppViewModel, s: LessonSession) {
    var confirmQuit by remember { mutableStateOf(false) }
    BackHandler { confirmQuit = true }

    val ex = s.current
    // Per-exercise answer state; reset whenever the exercise changes.
    var answer by remember(s.position, ex) { mutableStateOf("") }
    var choice by remember(s.position, ex) { mutableStateOf<String?>(null) }
    var ready by remember(s.position, ex) { mutableStateOf(false) }
    val fb = s.feedback

    Column(Modifier.fillMaxSize().background(Color.White).statusBarsPadding().navigationBarsPadding().imePadding()) {
        // Top bar: close, progress, hearts
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("✕", fontSize = 22.sp, color = Palette.GrayDark, modifier = Modifier.clickable { confirmQuit = true }.padding(4.dp))
            Spacer(Modifier.width(14.dp))
            Box(Modifier.weight(1f).height(16.dp).clip(RoundedCornerShape(8.dp)).background(Palette.Gray)) {
                Box(Modifier.fillMaxWidth(s.position.toFloat() / s.exercises.size).fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp)).background(Palette.Green))
            }
            Spacer(Modifier.width(14.dp))
            Text("❤️".repeat(s.hearts) + "🤍".repeat(3 - s.hearts), fontSize = 17.sp)
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            val locked = fb != null
            when (ex) {
                is Exercise.Bank -> BankExercise(vm, s, ex, locked) { text -> answer = text; ready = text.isNotBlank() }
                is Exercise.Type -> {
                    Title("Übersetze diesen Satz")
                    Prompt(vm, s, ex.prompt, german = !ex.toGerman, speak = ex.toGerman)
                    AnswerField(vm, s, targetLanguage = !ex.toGerman, locked) { answer = it; ready = it.isNotBlank() }
                }
                is Exercise.Listen -> ListenExercise(vm, s, ex, locked) { answer = it; ready = it.isNotBlank() }
                is Exercise.Choice -> {
                    Title("Wähle die richtige Übersetzung")
                    Prompt(vm, s, ex.sentence.target[0], german = false, speak = true)
                    Options(ex.options, choice, locked) { choice = it; ready = true }
                }
                is Exercise.Word -> {
                    Title("Welches davon bedeutet „${ex.word.second}“?")
                    Options(ex.options, choice, locked) { choice = it; ready = true; vm.audio.speak(it) }
                }
                is Exercise.Match -> MatchExercise(vm, ex, locked) { ready = true }
            }
            Spacer(Modifier.height(20.dp))
        }

        // Bottom bar: check / feedback
        val barColor = when {
            fb == null -> Color.White
            fb.correct -> Palette.GreenLight
            else -> Palette.RedLight
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(if (fb == null) Palette.Gray else barColor))
        Column(Modifier.fillMaxWidth().background(barColor).padding(16.dp)) {
            if (fb != null) {
                Text(if (fb.correct) "Richtig! 🎉" else "Richtige Lösung:", fontWeight = FontWeight.Black, fontSize = 20.sp,
                    color = if (fb.correct) Palette.GreenDark else Palette.RedDark)
                val detail = if (fb.correct) fb.note else fb.solution
                if (!detail.isNullOrEmpty()) {
                    Text(detail, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                        color = if (fb.correct) Palette.GreenDark else Palette.RedDark, modifier = Modifier.padding(top = 4.dp))
                }
                Spacer(Modifier.height(12.dp))
                ChunkyButton("Weiter", { vm.next(s) }, Modifier.fillMaxWidth(),
                    color = if (fb.correct) Palette.Green else Palette.Red, lip = if (fb.correct) Palette.GreenDark else Palette.RedDark)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (ex !is Exercise.Match) {
                        ChunkyButton("Überspringen", { vm.check(s, null, null, forceWrong = true) }, Modifier.weight(1f),
                            outlined = true, textColor = Palette.Muted)
                    }
                    ChunkyButton("Überprüfen", { vm.check(s, answer, choice) }, Modifier.weight(1f), enabled = ready)
                }
            }
        }
    }

    if (confirmQuit) {
        AlertDialog(
            onDismissRequest = { confirmQuit = false }, containerColor = Color.White,
            title = { Text("Lektion beenden?", fontWeight = FontWeight.Black) },
            text = { Text("Dein Fortschritt in dieser Lektion geht verloren.") },
            confirmButton = { TextButton(onClick = { confirmQuit = false }) { Text("Weiterlernen", color = Palette.Blue, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { confirmQuit = false; vm.quitLesson() }) { Text("Beenden", color = Palette.RedDark) } },
        )
    }
}

@Composable
private fun Title(text: String) {
    Text(text, fontSize = 22.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 4.dp, bottom = 18.dp))
}

/** Prompt sentence; words with a known translation are dotted and show it when tapped. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Prompt(vm: AppViewModel, s: LessonSession, text: String, german: Boolean, speak: Boolean) {
    var hint by remember(text) { mutableStateOf<String?>(null) }
    if (speak) LaunchedEffect(text) { vm.audio.speak(text) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
        if (speak) {
            SpeakerButton { vm.audio.speak(text) }
            Spacer(Modifier.width(12.dp))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            text.split(' ').filter { it.isNotBlank() }.forEach { word ->
                val h = s.index.hint(word, german)
                Text(
                    word, fontSize = 20.sp,
                    textDecoration = if (h != null) TextDecoration.Underline else null,
                    modifier = if (h != null) Modifier.clickable { hint = "${word.trim(',', '.', '?', '!')} = $h" } else Modifier,
                )
            }
        }
    }
    Box(Modifier.heightIn(min = 30.dp).padding(bottom = 10.dp)) {
        hint?.let {
            Text(it, Modifier.clip(RoundedCornerShape(10.dp)).border(2.dp, Palette.Gray, RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp), fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

@Composable
private fun SpeakerButton(big: Boolean = false, slow: Boolean = false, onClick: () -> Unit) {
    val size = if (big) 90.dp else if (slow) 56.dp else 46.dp
    Box(
        Modifier.size(size).clip(RoundedCornerShape(if (big) 20.dp else 14.dp))
            .background(if (slow) Color.White else Palette.Blue)
            .border(if (slow) 2.dp else 0.dp, Palette.Gray, RoundedCornerShape(if (big) 20.dp else 14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(if (slow) "🐢" else "🔊", fontSize = if (big) 38.sp else 20.sp)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BankExercise(vm: AppViewModel, s: LessonSession, ex: Exercise.Bank, locked: Boolean, onAnswer: (String) -> Unit) {
    val chosen = remember(ex) { mutableStateListOf<Int>() }
    Title("Übersetze diesen Satz")
    Prompt(vm, s, ex.prompt, german = !ex.toGerman, speak = ex.toGerman)
    FlowRow(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        chosen.forEach { i ->
            WordChip(ex.chips[i], { if (!locked) { chosen.remove(i); onAnswer(chosen.joinToString(" ") { ex.chips[it] }) } })
        }
    }
    Box(Modifier.fillMaxWidth().height(2.dp).background(Palette.Gray))
    Spacer(Modifier.height(24.dp))
    FlowRow(
        Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ex.chips.forEachIndexed { i, chip ->
            WordChip(chip, {
                if (!locked) {
                    chosen.add(i)
                    if (!ex.toGerman) vm.audio.speak(chip)
                    onAnswer(chosen.joinToString(" ") { ex.chips[it] })
                }
            }, used = i in chosen)
        }
    }
}

/** Text answer with buttons for the target language's special characters. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnswerField(vm: AppViewModel, s: LessonSession, targetLanguage: Boolean, locked: Boolean, onChange: (String) -> Unit) {
    var value by remember(s.position) { mutableStateOf(TextFieldValue("")) }
    OutlinedTextField(
        value = value,
        onValueChange = { if (!locked) { value = it; onChange(it.text) } },
        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
        placeholder = { Text(if (targetLanguage) "Auf ${s.course.name} eingeben" else "Auf Deutsch eingeben") },
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 18.sp),
        readOnly = locked,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Color(0xFFF7F7F7), focusedBorderColor = Palette.Blue),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
    )
    if (targetLanguage && s.course.chars.isNotEmpty()) {
        FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            s.course.chars.forEach { c ->
                Box(
                    Modifier.clip(RoundedCornerShape(10.dp)).border(2.dp, Palette.Gray, RoundedCornerShape(10.dp))
                        .clickable(enabled = !locked) {
                            val t = value.text
                            val start = value.selection.start.coerceIn(0, t.length)
                            val end = value.selection.end.coerceIn(start, t.length)
                            val text = t.substring(0, start) + c + t.substring(end)
                            value = TextFieldValue(text, TextRange(start + c.length))
                            onChange(text)
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) { Text(c, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun ListenExercise(vm: AppViewModel, s: LessonSession, ex: Exercise.Listen, locked: Boolean, onChange: (String) -> Unit) {
    val text = ex.sentence.target[0]
    LaunchedEffect(text) { delay(250); vm.audio.speak(text) }
    Title("Schreib, was du hörst")
    Row(Modifier.fillMaxWidth().padding(bottom = 22.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
        SpeakerButton(big = true) { vm.audio.speak(text) }
        Spacer(Modifier.width(16.dp))
        SpeakerButton(slow = true) { vm.audio.speak(text, slow = true) }
    }
    AnswerField(vm, s, targetLanguage = true, locked, onChange)
    if (!locked) {
        TextButton(onClick = { vm.skipListening(s) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text("KANN GERADE NICHT ZUHÖREN", color = Palette.Blue, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
        }
    }
}

@Composable
private fun Options(options: List<String>, selected: String?, locked: Boolean, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        options.forEachIndexed { i, o ->
            val on = o == selected
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(if (on) Palette.BlueLight else Color.White)
                    .border(2.dp, if (on) Color(0xFF84D8FF) else Palette.Gray, RoundedCornerShape(14.dp))
                    .clickable(enabled = !locked) { onSelect(o) }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(28.dp).border(2.dp, if (on) Color(0xFF84D8FF) else Palette.Gray, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center) {
                    Text("${i + 1}", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = if (on) Palette.BlueDark else Palette.GrayDark)
                }
                Spacer(Modifier.width(14.dp))
                Text(o, fontSize = 17.sp, color = if (on) Palette.BlueDark else Palette.Text)
            }
        }
    }
}

@Composable
private fun MatchExercise(vm: AppViewModel, ex: Exercise.Match, locked: Boolean, onComplete: () -> Unit) {
    val left = remember(ex) { ex.pairs.indices.shuffled() }
    val right = remember(ex) { ex.pairs.indices.shuffled() }
    val matched = remember(ex) { mutableStateListOf<Int>() }
    var sel by remember(ex) { mutableStateOf<Pair<Boolean, Int>?>(null) } // (isLeft, pairIndex)
    var error by remember(ex) { mutableStateOf<Set<Pair<Boolean, Int>>>(emptySet()) }
    LaunchedEffect(error) { if (error.isNotEmpty()) { delay(450); error = emptySet() } }

    fun tap(isLeft: Boolean, k: Int) {
        if (locked || k in matched) return
        if (isLeft) vm.audio.speak(ex.pairs[k].first)
        val cur = sel
        if (cur == null || cur.first == isLeft) {
            sel = isLeft to k
            return
        }
        if (cur.second == k) {
            matched.add(k)
            sel = null
            if (matched.size == ex.pairs.size) onComplete()
        } else {
            error = setOf(cur, isLeft to k)
            vm.audio.feedback(false, vm.state.value.settings.sound)
            sel = null
        }
    }

    Title("Finde die passenden Paare")
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(true to left, false to right).forEach { (isLeft, order) ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                order.forEach { k ->
                    val text = if (isLeft) ex.pairs[k].first else ex.pairs[k].second
                    val gone = k in matched
                    val isSel = sel == (isLeft to k)
                    val isErr = (isLeft to k) in error
                    Box(
                        Modifier.fillMaxWidth().heightIn(min = 54.dp).clip(RoundedCornerShape(14.dp))
                            .background(when { isErr -> Palette.RedLight; isSel -> Palette.BlueLight; else -> Color.White })
                            .border(2.dp, when { gone -> Palette.Gray.copy(alpha = 0.4f); isErr -> Palette.Red; isSel -> Color(0xFF84D8FF); else -> Palette.Gray },
                                RoundedCornerShape(14.dp))
                            .clickable(enabled = !gone) { tap(isLeft, k) }.padding(10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text, fontSize = 17.sp, textAlign = TextAlign.Center,
                            color = if (gone) Palette.Gray else if (isSel) Palette.BlueDark else Palette.Text)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------- end screens

@Composable
fun ResultScreen(vm: AppViewModel, r: Screen.Result, streak: Int, todayXp: Int, goal: Int) {
    BackHandler { vm.screen = Screen.Home }
    Column(
        Modifier.fillMaxSize().background(Color.White).statusBarsPadding().navigationBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Text(if (r.goalReached) "🏆" else "🎉", fontSize = 72.sp)
        Text(r.title, fontSize = 28.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
        r.detail?.let { Text(it, color = Palette.Muted, fontSize = 17.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp)) }
        Text(if (r.mistakes == 0) "Ohne einen einzigen Fehler!" else "${r.mistakes} Fehler", color = Palette.Muted, fontSize = 17.sp,
            modifier = Modifier.padding(top = 4.dp))
        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Pill("+${r.xp} XP")
            Pill("🔥 $streak ${if (streak == 1) "Tag" else "Tage"}")
        }
        Text(if (r.goalReached) "Tagesziel erreicht!" else "Heute: $todayXp / $goal XP",
            color = if (r.goalReached) Palette.Orange else Palette.Muted, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 16.dp))
        Spacer(Modifier.height(32.dp))
        ChunkyButton("Weiter", { vm.screen = Screen.Home }, Modifier.fillMaxWidth())
    }
}

@Composable
private fun Pill(text: String) {
    Text(text, Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFFFFF4CC)).padding(horizontal = 14.dp, vertical = 8.dp),
        color = Color(0xFFB78800), fontWeight = FontWeight.Black, fontSize = 17.sp)
}

@Composable
fun FailScreen(vm: AppViewModel, f: Screen.Failed) {
    BackHandler { vm.screen = Screen.Home }
    Column(
        Modifier.fillMaxSize().background(Color.White).statusBarsPadding().navigationBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Text("💔", fontSize = 72.sp)
        Text("Keine Herzen mehr", fontSize = 28.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 12.dp))
        Text("Kein Problem – Fehler gehören dazu. Versuch es gleich noch einmal.", color = Palette.Muted, fontSize = 17.sp,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(32.dp))
        ChunkyButton("Nochmal versuchen", f.retry, Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        ChunkyButton("Zurück zum Baum", { vm.screen = Screen.Home }, Modifier.fillMaxWidth(), outlined = true, textColor = Palette.Blue)
    }
}
