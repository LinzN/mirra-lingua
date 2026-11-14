package de.mirranet.parla.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.fadeIn
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.mirranet.parla.engine.Exercise
import kotlinx.coroutines.delay

@Composable
fun LessonScreen(vm: AppViewModel, s: LessonSession) {
    var confirmQuit by remember { mutableStateOf(false) }
    BackHandler { confirmQuit = !confirmQuit }
    val haptics = LocalHapticFeedback.current

    val ex = s.current
    // Per-exercise answer state; reset whenever the exercise changes.
    var answer by remember(s.position, ex) { mutableStateOf("") }
    var choice by remember(s.position, ex) { mutableStateOf<String?>(null) }
    var ready by remember(s.position, ex) { mutableStateOf(false) }
    val fb = s.feedback
    LaunchedEffect(fb) {
        if (fb != null && !fb.correct && !fb.skipped) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    // Keyboard: Enter checks / continues, 1–9 picks an option.
    KeyHandler { e ->
        if (confirmQuit) return@KeyHandler false
        val n = e.digit()
        when {
            e.isEnter() && fb != null -> { vm.next(s); true }
            e.isEnter() && ready -> { vm.check(s, answer, choice); true }
            n != null && fb == null && (ex is Exercise.Choice || ex is Exercise.Word) -> {
                val options = if (ex is Exercise.Choice) ex.options else (ex as Exercise.Word).options
                options.getOrNull(n - 1)?.let { picked ->
                    choice = picked; ready = true
                    vm.tap()
                    if (ex is Exercise.Word) vm.audio.speak(picked)
                }
                true
            }
            else -> false
        }
    }

    Box(Modifier.fillMaxSize().background(Color.White)) { PageWidth(760.dp) {
    Column(Modifier.fillMaxSize()) {
        // Top bar: close, progress, hearts
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            CircleButton({ confirmQuit = true }, size = 40.dp) {
                Icon(Icons.Rounded.Close, contentDescription = "Lektion beenden", tint = Ios.SecondaryLabel)
            }
            Spacer(Modifier.width(14.dp))
            ProgressBar(s.position.toFloat() / s.exercises.size, Modifier.weight(1f), color = Ios.Green, height = 10.dp)
            Spacer(Modifier.width(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                repeat(3) { i ->
                    Icon(Icons.Rounded.Favorite, contentDescription = null, modifier = Modifier.size(22.dp),
                        tint = if (i < s.hearts) Ios.Red else Ios.Fill)
                }
            }
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            val locked = fb != null
            when (ex) {
                is Exercise.Bank -> BankExercise(vm, s, ex, locked) { text -> answer = text; ready = text.isNotBlank() }
                is Exercise.Type -> {
                    Instruction(if (ex.toGerman) "Schreib das auf Deutsch" else "Schreib das auf ${s.course.name}")
                    Prompt(vm, s, ex.prompt, german = !ex.toGerman, speak = ex.toGerman)
                    AnswerField(s, targetLanguage = !ex.toGerman, locked) { answer = it; ready = it.isNotBlank() }
                }
                is Exercise.Listen -> ListenExercise(vm, s, ex, locked) { answer = it; ready = it.isNotBlank() }
                is Exercise.Choice -> {
                    Instruction("Was bedeutet das?")
                    Prompt(vm, s, ex.sentence.target[0], german = false, speak = true)
                    Options(ex.options, choice, locked) { choice = it; ready = true; vm.tap() }
                }
                is Exercise.Word -> {
                    Instruction("Welches Wort bedeutet „${ex.word.second}“?")
                    Options(ex.options, choice, locked) { choice = it; ready = true; vm.tap(); vm.audio.speak(it) }
                }
                is Exercise.Match -> MatchExercise(vm, ex, locked) { ready = true }
            }
            Spacer(Modifier.height(24.dp))
        }

        // Bottom: check button, or feedback panel
        if (fb == null) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                IosButton(if (ex is Exercise.Match && !ready) "Paare finden" else "Prüfen", { vm.check(s, answer, choice) },
                    Modifier.fillMaxWidth(), enabled = ready)
                if (ex !is Exercise.Match) {
                    IosButton("Weiß ich nicht", { vm.check(s, null, null, forceWrong = true) }, Modifier.fillMaxWidth(),
                        style = ButtonStyle.Plain, height = 44.dp)
                }
            }
        } else {
            val appear = remember(fb) { MutableTransitionState(false).apply { targetState = true } }
            AnimatedVisibility(visibleState = appear, enter = slideInVertically { it / 2 } + fadeIn()) {
                FeedbackPanel(fb) { vm.next(s) }
            }
        }
    }
    } }

    if (confirmQuit) {
        IosAlert(
            "Lektion beenden?", "Was du in dieser Lektion gemacht hast, wird nicht gespeichert.",
            confirmText = "Beenden", destructive = true, dismissText = "Weiterlernen",
            onConfirm = { confirmQuit = false; vm.quitLesson() }, onDismiss = { confirmQuit = false },
        )
    }
}

@Composable
private fun FeedbackPanel(fb: LessonSession.Feedback, onNext: () -> Unit) {
    // Three looks: correct (green), wrong (red), skipped with "Weiß ich nicht" (blue hint).
    val tint = when { fb.correct -> Ios.GreenTint; fb.skipped -> Ios.BlueTint; else -> Ios.RedTint }
    val color = when { fb.correct -> Ios.GreenDark; fb.skipped -> Ios.Blue; else -> Ios.RedDark }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)).background(tint)
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(when { fb.correct -> Ios.Green; fb.skipped -> Ios.Blue; else -> Ios.Red }), contentAlignment = Alignment.Center) {
                Icon(when { fb.correct -> Icons.Rounded.Check; fb.skipped -> Icons.Rounded.Info; else -> Icons.Rounded.Close },
                    contentDescription = null, tint = Color.White)
            }
            Spacer(Modifier.width(12.dp))
            Text(when { fb.correct -> "Richtig!"; fb.skipped -> "Kein Problem – so geht's"; else -> "Nicht ganz" }, style = IosType.Title3, color = color)
        }
        val detail = if (fb.correct) fb.note else fb.solution
        if (!detail.isNullOrEmpty()) {
            if (!fb.correct) Text("Richtig ist:", style = IosType.Subhead, color = color, modifier = Modifier.padding(top = 10.dp))
            Text(detail, style = IosType.Body, color = color, fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = if (fb.correct) 8.dp else 2.dp))
        }
        Spacer(Modifier.height(14.dp))
        IosButton("Weiter", onNext, Modifier.fillMaxWidth(), style = when { fb.correct -> ButtonStyle.Success; fb.skipped -> ButtonStyle.Filled; else -> ButtonStyle.Destructive })
    }
}

@Composable
private fun Instruction(text: String) {
    Text(text, style = IosType.Title2, modifier = Modifier.padding(top = 8.dp, bottom = 18.dp))
}

/** Prompt sentence; words with a known translation are dotted and show it when tapped. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Prompt(vm: AppViewModel, s: LessonSession, text: String, german: Boolean, speak: Boolean) {
    var hint by remember(text) { mutableStateOf<String?>(null) }
    if (speak) LaunchedEffect(text) { vm.audio.speak(text) }
    Row(
        Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp))
            .background(Color.White).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (speak) {
            CircleButton({ vm.audio.speakButton(text) }, size = 52.dp, background = Ios.BlueTint) { SpeakerIcon(Ios.Blue, 26.dp) }
            Spacer(Modifier.width(14.dp))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            text.split(' ').filter { it.isNotBlank() }.forEach { word ->
                val h = s.index.hint(word, german)
                Text(
                    word, style = IosType.Prompt,
                    textDecoration = if (h != null) TextDecoration.Underline else null,
                    modifier = if (h != null) Modifier.clickable { hint = "${word.trim(',', '.', '?', '!', '¿', '¡')} = $h" } else Modifier,
                )
            }
        }
    }
    Box(Modifier.heightIn(min = 44.dp).padding(top = 8.dp, bottom = 10.dp)) {
        if (hint != null) {
            Text(hint!!, Modifier.clip(RoundedCornerShape(10.dp)).background(Ios.BlueTint).padding(horizontal = 12.dp, vertical = 6.dp),
                style = IosType.Callout, color = Ios.Blue, fontWeight = FontWeight.Medium)
        } else if (s.index.course.gloss.isNotEmpty()) {
            Text("Tipp: Unterstrichene Wörter antippen für eine Übersetzung.", style = IosType.Footnote, color = Ios.TertiaryLabel)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BankExercise(vm: AppViewModel, s: LessonSession, ex: Exercise.Bank, locked: Boolean, onAnswer: (String) -> Unit) {
    val chosen = remember(ex) { mutableStateListOf<Int>() }
    Instruction(if (ex.toGerman) "Übersetze ins Deutsche" else "Übersetze auf ${s.course.name}")
    Prompt(vm, s, ex.prompt, german = !ex.toGerman, speak = ex.toGerman)
    // Answer area: a soft field that fills with tapped words.
    FlowRow(
        Modifier.fillMaxWidth().heightIn(min = 76.dp).clip(RoundedCornerShape(16.dp)).background(Ios.FillLight).padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (chosen.isEmpty()) {
            Text("Tippe die Wörter in der richtigen Reihenfolge an.", style = IosType.Callout, color = Ios.TertiaryLabel,
                modifier = Modifier.padding(6.dp))
        }
        chosen.forEach { i ->
            WordChip(ex.chips[i], { if (!locked) { chosen.remove(i); vm.tap(); onAnswer(chosen.joinToString(" ") { ex.chips[it] }) } })
        }
    }
    Spacer(Modifier.height(22.dp))
    FlowRow(
        Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ex.chips.forEachIndexed { i, chip ->
            WordChip(chip, {
                if (!locked) {
                    chosen.add(i)
                    vm.tap()
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
private fun AnswerField(s: LessonSession, targetLanguage: Boolean, locked: Boolean, onChange: (String) -> Unit) {
    var value by remember(s.position) { mutableStateOf(TextFieldValue("")) }
    IosTextField(
        value, { if (!locked) { value = it; onChange(it.text) } },
        placeholder = if (targetLanguage) "Antwort auf ${s.course.name}" else "Antwort auf Deutsch",
        singleLine = false, minHeight = 120.dp, readOnly = locked, textStyle = IosType.Body.copy(fontSize = 20.sp),
    )
    if (targetLanguage && s.course.chars.isNotEmpty()) {
        Text("Sonderzeichen", style = IosType.Footnote, color = Ios.SecondaryLabel, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            s.course.chars.forEach { c ->
                Box(
                    Modifier.size(width = 48.dp, height = 46.dp).clip(RoundedCornerShape(10.dp)).background(Ios.FillLight)
                        .clickable(enabled = !locked) {
                            val t = value.text
                            val start = value.selection.start.coerceIn(0, t.length)
                            val end = value.selection.end.coerceIn(start, t.length)
                            val text = t.substring(0, start) + c + t.substring(end)
                            value = TextFieldValue(text, TextRange(start + c.length))
                            onChange(text)
                        },
                    contentAlignment = Alignment.Center,
                ) { Text(c, style = IosType.Title3) }
            }
        }
    }
}

@Composable
private fun ListenExercise(vm: AppViewModel, s: LessonSession, ex: Exercise.Listen, locked: Boolean, onChange: (String) -> Unit) {
    val text = ex.sentence.target[0]
    LaunchedEffect(text) { delay(300); vm.audio.speak(text) }
    Instruction("Hör zu und schreib, was du hörst")
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircleButton({ vm.audio.speakButton(text) }, size = 96.dp, background = Ios.Blue) { SpeakerIcon(Color.White, 46.dp) }
            Text("Abspielen", style = IosType.Footnote, color = Ios.SecondaryLabel, modifier = Modifier.padding(top = 6.dp))
        }
        Spacer(Modifier.width(28.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircleButton({ vm.audio.speak(text, slow = true) }, size = 64.dp, background = Ios.BlueTint) { SpeakerIcon(Ios.Blue, 30.dp, slow = true) }
            Text("Langsam", style = IosType.Footnote, color = Ios.SecondaryLabel, modifier = Modifier.padding(top = 6.dp))
        }
    }
    Spacer(Modifier.height(14.dp))
    AnswerField(s, targetLanguage = true, locked, onChange)
    if (!locked) {
        IosButton("Ich kann gerade nicht zuhören", { vm.skipListening(s) }, Modifier.fillMaxWidth().padding(top = 8.dp),
            style = ButtonStyle.Plain, height = 44.dp)
    }
}

@Composable
private fun Options(options: List<String>, selected: String?, locked: Boolean, onSelect: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).border(1.dp, Ios.Separator, RoundedCornerShape(16.dp))) {
        options.forEachIndexed { i, o ->
            if (i > 0) RowDivider(inset = 58.dp)
            val on = o == selected
            Row(
                Modifier.fillMaxWidth().heightIn(min = 62.dp).background(if (on) Ios.BlueTint else Color.White)
                    .clickable(enabled = !locked) { onSelect(o) }.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SelectionCircle(on)
                Spacer(Modifier.width(16.dp))
                Text(o, style = IosType.Body, color = if (on) Ios.Blue else Ios.Label, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.weight(1f))
                if (i < 9) Text("${i + 1}", style = IosType.Footnote, color = Ios.TertiaryLabel)
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
            vm.tap()
            return
        }
        if (cur.second == k) {
            matched.add(k)
            vm.audio.pairFound(vm.state.value.settings.sound)
            sel = null
            if (matched.size == ex.pairs.size) onComplete()
        } else {
            error = setOf(cur, isLeft to k)
            vm.audio.feedback(false, vm.state.value.settings.sound)
            sel = null
        }
    }

    Instruction("Tippe auf zwei Wörter, die zusammengehören")
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(true to left, false to right).forEach { (isLeft, order) ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                order.forEach { k ->
                    val text = if (isLeft) ex.pairs[k].first else ex.pairs[k].second
                    val gone = k in matched
                    val isSel = sel == (isLeft to k)
                    val isErr = (isLeft to k) in error
                    val bg = when { gone -> Ios.GreenTint; isErr -> Ios.RedTint; isSel -> Ios.BlueTint; else -> Color.White }
                    val border = when { gone -> Color.Transparent; isErr -> Ios.Red; isSel -> Ios.Blue; else -> Ios.Separator }
                    Box(
                        Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(RoundedCornerShape(14.dp)).background(bg)
                            .border(if (isSel || isErr) 2.dp else 1.dp, border, RoundedCornerShape(14.dp))
                            .clickable(enabled = !gone) { tap(isLeft, k) }.padding(10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text, style = IosType.Body, textAlign = TextAlign.Center,
                            color = when { gone -> Ios.GreenDark; isSel -> Ios.Blue; else -> Ios.Label })
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
    KeyHandler { if (it.isEnter()) { vm.screen = Screen.Home; true } else false }
    Column(
        Modifier.fillMaxSize().background(Ios.GroupedBackground).padding(horizontal = 20.dp, vertical = 32.dp)
            .wrapContentWidth().widthIn(max = 560.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(110.dp).clip(CircleShape).background(if (r.goalReached) Ios.Orange else Ios.Green), contentAlignment = Alignment.Center) {
            Text(if (r.goalReached) "🏆" else "✓", fontSize = if (r.goalReached) 54.sp else 60.sp, color = Color.White, fontWeight = FontWeight.Bold)
        }
        Text(r.title, style = IosType.Title1, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 20.dp))
        r.detail?.let { Text(it, style = IosType.Callout, color = Ios.SecondaryLabel, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp)) }
        if (r.goalReached) Text("Tagesziel erreicht – super!", style = IosType.Headline, color = Ios.Orange, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ResultTile("+${r.xp}", "XP", Modifier.weight(1f))
            ResultTile(r.accuracy?.let { "$it %" } ?: "–", "richtig", Modifier.weight(1f))
            ResultTile("$streak", if (streak == 1) "Tag in Folge" else "Tage in Folge", Modifier.weight(1f))
        }
        Text("Heute: $todayXp von $goal XP", style = IosType.Subhead, color = Ios.SecondaryLabel, modifier = Modifier.padding(top = 16.dp))
        Spacer(Modifier.weight(1f))
        IosButton("Weiter", { vm.screen = Screen.Home }, Modifier.fillMaxWidth())
    }
}

@Composable
private fun ResultTile(value: String, label: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(Color.White).padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = IosType.Title2)
        Text(label, style = IosType.Footnote, color = Ios.SecondaryLabel, textAlign = TextAlign.Center)
    }
}

@Composable
fun FailScreen(vm: AppViewModel, f: Screen.Failed) {
    BackHandler { vm.screen = Screen.Home }
    KeyHandler { if (it.isEnter()) { vm.screen = Screen.Home; true } else false }
    Column(
        Modifier.fillMaxSize().background(Ios.GroupedBackground).padding(horizontal = 20.dp, vertical = 32.dp)
            .wrapContentWidth().widthIn(max = 560.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(110.dp).clip(CircleShape).background(Ios.RedTint), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Favorite, contentDescription = null, tint = Ios.Red, modifier = Modifier.size(56.dp))
        }
        Text("Keine Herzen mehr", style = IosType.Title1, modifier = Modifier.padding(top = 20.dp))
        Text("Kein Problem – Fehler gehören zum Lernen dazu. Die nächste Runde wird etwas leichter.",
            style = IosType.Callout, color = Ios.SecondaryLabel, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.weight(1f))
        IosButton("Noch einmal versuchen", f.retry, Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        IosButton("Zur Übersicht", { vm.screen = Screen.Home }, Modifier.fillMaxWidth(), style = ButtonStyle.Tinted)
    }
}
