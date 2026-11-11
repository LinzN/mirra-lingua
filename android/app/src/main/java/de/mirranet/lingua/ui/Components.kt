package de.mirranet.lingua.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

// ---------------------------------------------------------------------- buttons

enum class ButtonStyle { Filled, Tinted, Plain, Destructive, DestructiveTinted, Success }

/**
 * iOS-style button: large (56 dp) rounded rectangle, sentence-case label, gentle press feedback.
 */
@Composable
fun IosButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ButtonStyle = ButtonStyle.Filled,
    enabled: Boolean = true,
    height: Dp = 56.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) 0.97f else 1f, label = "press")
    val (bg, fg) = when {
        !enabled -> Ios.Fill to Ios.TertiaryLabel
        style == ButtonStyle.Filled -> Ios.Blue to Color.White
        style == ButtonStyle.Success -> Ios.Green to Color.White
        style == ButtonStyle.Destructive -> Ios.Red to Color.White
        style == ButtonStyle.Tinted -> Ios.BlueTint to Ios.Blue
        style == ButtonStyle.DestructiveTinted -> Ios.RedTint to Ios.Red
        else -> Color.Transparent to Ios.Blue
    }
    Box(
        modifier
            .scale(scale)
            .heightIn(min = height)
            .clip(RoundedCornerShape(14.dp))
            .background(if (pressed && enabled && style != ButtonStyle.Plain) bg.copy(alpha = 0.85f) else bg)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = IosType.Headline, color = if (pressed && style == ButtonStyle.Plain) fg.copy(alpha = 0.5f) else fg,
            textAlign = TextAlign.Center)
    }
}

/** Round icon button, e.g. the close button in lessons or the speaker button. */
@Composable
fun CircleButton(onClick: () -> Unit, size: Dp = 40.dp, background: Color = Ios.Fill, content: @Composable () -> Unit) {
    Box(
        Modifier.size(size).clip(CircleShape).background(background).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** A simple speaker glyph drawn with lines, so no extra icon library is needed. */
@Composable
fun SpeakerIcon(color: Color, size: Dp = 24.dp, slow: Boolean = false) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val body = Path().apply {
            moveTo(w * 0.10f, h * 0.38f); lineTo(w * 0.30f, h * 0.38f); lineTo(w * 0.52f, h * 0.18f)
            lineTo(w * 0.52f, h * 0.82f); lineTo(w * 0.30f, h * 0.62f); lineTo(w * 0.10f, h * 0.62f); close()
        }
        drawPath(body, color)
        val stroke = Stroke(width = w * 0.08f, cap = StrokeCap.Round)
        drawArc(color, -45f, 90f, false, topLeft = Offset(w * 0.40f, h * 0.30f), size = Size(w * 0.30f, h * 0.40f), style = stroke)
        if (!slow) {
            drawArc(color, -50f, 100f, false, topLeft = Offset(w * 0.36f, h * 0.14f), size = Size(w * 0.56f, h * 0.72f), style = stroke)
        }
    }
}

// ---------------------------------------------------------------------- grouped lists

/** Grouped inset section: optional caption above, white rounded card, optional footnote below. */
@Composable
fun Section(
    header: String? = null,
    footer: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        header?.let {
            Text(it.uppercase(), style = IosType.SectionHeader, color = Ios.SecondaryLabel, modifier = Modifier.padding(start = 16.dp, bottom = 7.dp))
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Ios.Card), content = content)
        footer?.let {
            Text(it, style = IosType.Footnote, color = Ios.SecondaryLabel, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 7.dp))
        }
    }
}

/** Hairline separator inset to align with row text. */
@Composable
fun RowDivider(inset: Dp = 16.dp) {
    Box(Modifier.fillMaxWidth().padding(start = inset).height(0.6.dp).background(Ios.Separator))
}

/** Coloured rounded square with an emoji, like the icons in iOS Settings. */
@Composable
fun IconTile(emoji: String, color: Color, size: Dp = 36.dp, dimmed: Boolean = false) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.24f)).background(if (dimmed) Ios.Fill else color),
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, fontSize = (size.value * 0.52f).sp, color = if (dimmed) Color.Black.copy(alpha = 0.35f) else Color.Unspecified)
    }
}

/**
 * A list row: icon, title (+ subtitle), and on the right a value, a custom trailing view and/or a chevron.
 * Rows are at least 56 dp tall so they are easy to hit.
 */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    value: String? = null,
    titleColor: Color = Ios.Label,
    chevron: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.let {
            it()
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = IosType.Body, color = titleColor)
            subtitle?.let { Text(it, style = IosType.Subhead, color = Ios.SecondaryLabel) }
        }
        value?.let {
            Spacer(Modifier.width(8.dp))
            Text(it, style = IosType.Body, color = Ios.SecondaryLabel)
        }
        trailing?.let {
            Spacer(Modifier.width(8.dp))
            it()
        }
        if (chevron) {
            Spacer(Modifier.width(4.dp))
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = Ios.TertiaryLabel)
        }
    }
}

@Composable
fun IosSwitch(checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Switch(
        checked = checked, onCheckedChange = onChange, enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White, checkedTrackColor = Ios.Green, checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = Color.White, uncheckedTrackColor = Ios.Fill, uncheckedBorderColor = Color.Transparent,
        ),
    )
}

/** iOS segmented control. */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Ios.Fill).padding(3.dp)) {
        val segment = maxWidth / options.size
        val offset by animateDpAsState(segment * selected, label = "segment")
        Box(
            Modifier.offset(x = offset).width(segment).height(40.dp)
                .shadow(2.dp, RoundedCornerShape(8.dp)).clip(RoundedCornerShape(8.dp)).background(Color.White)
        )
        Row(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, o ->
                Box(
                    Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(8.dp)).clickable { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(o, style = IosType.Callout, fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
                }
            }
        }
    }
}

/** Thin rounded progress bar. */
@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier, color: Color = Ios.Green, height: Dp = 8.dp) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), label = "progress")
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height / 2)).background(Ios.Fill)) {
        Box(Modifier.fillMaxWidth(animated).fillMaxHeight().clip(RoundedCornerShape(height / 2)).background(color))
    }
}

// ---------------------------------------------------------------------- text input

/** iOS-style text field: grey filled rounded box, no outline. */
@Composable
fun IosTextField(
    value: TextFieldValue,
    onChange: (TextFieldValue) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Default,
    singleLine: Boolean = true,
    minHeight: Dp = 56.dp,
    readOnly: Boolean = false,
    textStyle: TextStyle = IosType.Body,
) {
    BasicTextField(
        value = value, onValueChange = onChange, readOnly = readOnly, singleLine = singleLine,
        textStyle = textStyle.copy(color = Ios.Label),
        cursorBrush = SolidColor(Ios.Blue),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (password) KeyboardType.Password else keyboardType, imeAction = imeAction,
            autoCorrectEnabled = false, capitalization = KeyboardCapitalization.None,
        ),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box(
                Modifier.fillMaxWidth().heightIn(min = minHeight).clip(RoundedCornerShape(12.dp)).background(Ios.FillLight)
                    .padding(horizontal = 16.dp, vertical = 15.dp),
                contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart,
            ) {
                if (value.text.isEmpty()) Text(placeholder, style = textStyle, color = Ios.TertiaryLabel)
                inner()
            }
        },
    )
}

// ---------------------------------------------------------------------- chips

/** Word chip for word banks: white, softly shadowed, large text. */
@Composable
fun WordChip(text: String, onClick: () -> Unit, used: Boolean = false) {
    val bg by animateColorAsState(if (used) Ios.Fill else Color.White, label = "chip")
    Box(
        Modifier
            .padding(vertical = 2.dp)
            .then(if (used) Modifier else Modifier.shadow(1.5.dp, RoundedCornerShape(12.dp)))
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, if (used) Color.Transparent else Ios.Separator, RoundedCornerShape(12.dp))
            .clickable(enabled = !used, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
    ) {
        Text(text, style = IosType.Body, fontSize = 19.sp, color = if (used) Color.Transparent else Ios.Label)
    }
}

// ---------------------------------------------------------------------- alert

/**
 * Centered iOS alert with up to two buttons. [content] can hold extra fields (e.g. a password).
 */
@Composable
fun IosAlert(
    title: String,
    message: String?,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissText: String? = "Abbrechen",
    destructive: Boolean = false,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.widthIn(max = 320.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White)) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, style = IosType.Headline, textAlign = TextAlign.Center)
                message?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = IosType.Callout, color = Ios.SecondaryLabel, textAlign = TextAlign.Center)
                }
                content?.let {
                    Spacer(Modifier.height(14.dp))
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp), content = it)
                }
            }
            Box(Modifier.fillMaxWidth().height(0.6.dp).background(Ios.Separator))
            Row(Modifier.fillMaxWidth().height(56.dp)) {
                if (dismissText != null) {
                    AlertButton(dismissText, Ios.Blue, FontWeight.Normal, Modifier.weight(1f), onDismiss)
                    Box(Modifier.width(0.6.dp).fillMaxHeight().background(Ios.Separator))
                }
                AlertButton(confirmText, if (destructive) Ios.Red else Ios.Blue, FontWeight.SemiBold, Modifier.weight(1f), onConfirm)
            }
        }
    }
}

@Composable
private fun AlertButton(text: String, color: Color, weight: FontWeight, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.fillMaxHeight().clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(text, style = IosType.Body, color = color, fontWeight = weight)
    }
}

// ---------------------------------------------------------------------- tab bar

data class TabItem(val label: String, val icon: ImageVector)

/** Bottom tab bar with icon + label, white with a hairline on top. */
@Composable
fun TabBar(items: List<TabItem>, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.97f))) {
        Box(Modifier.fillMaxWidth().height(0.6.dp).background(Ios.Separator))
        Row(Modifier.fillMaxWidth().navigationBarsPadding().height(64.dp)) {
            items.forEachIndexed { i, item ->
                val color = if (i == selected) Ios.Blue else Ios.SecondaryLabel
                Column(
                    Modifier.weight(1f).fillMaxHeight().clickable(
                        interactionSource = remember { MutableInteractionSource() }, indication = null,
                    ) { onSelect(i) },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    Icon(item.icon, contentDescription = item.label, tint = color, modifier = Modifier.size(28.dp))
                    Text(item.label, fontSize = 13.sp, color = color, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

/** Large title header used at the top of each tab. */
@Composable
fun LargeTitle(title: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.Bottom) {
        Text(title, style = IosType.LargeTitle, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** Small rounded label, e.g. "A2" or "+13 XP". */
@Composable
fun Badge(text: String, color: Color, background: Color) {
    Text(text, Modifier.clip(RoundedCornerShape(8.dp)).background(background).padding(horizontal = 8.dp, vertical = 3.dp),
        style = IosType.Footnote, fontWeight = FontWeight.SemiBold, color = color)
}

/** Small circle outline used for selection in option lists. */
@Composable
fun SelectionCircle(selected: Boolean, color: Color = Ios.Blue) {
    Box(
        Modifier.size(26.dp).clip(CircleShape)
            .background(if (selected) color else Color.Transparent)
            .border(2.dp, if (selected) color else Ios.Separator, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Box(Modifier.size(10.dp).clip(CircleShape).background(Color.White))
    }
}
