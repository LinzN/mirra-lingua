package de.mirranet.lingua.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ButtonShape = RoundedCornerShape(14.dp)

/** The chunky "3D" button of the classic design: a coloured face over a darker lip. */
@Composable
fun ChunkyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Palette.Green,
    lip: Color = Palette.GreenDark,
    textColor: Color = Color.White,
    enabled: Boolean = true,
    outlined: Boolean = false,
) {
    val face = when {
        !enabled -> Palette.Gray
        outlined -> Color.White
        else -> color
    }
    val lipColor = when {
        !enabled -> Color.Transparent
        outlined -> Palette.Gray
        else -> lip
    }
    Box(modifier.height(52.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .offset(y = 4.dp)
                .background(lipColor, ButtonShape)
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(ButtonShape)
                .background(face)
                .then(if (outlined) Modifier.border(BorderStroke(2.dp, Palette.Gray), ButtonShape) else Modifier)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text.uppercase(),
                color = if (!enabled) Palette.GrayDark else if (outlined) textColor else Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
                letterSpacing = 0.6.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

/** A word chip as used in word banks. */
@Composable
fun WordChip(text: String, onClick: () -> Unit, used: Boolean = false, selected: Boolean = false, error: Boolean = false) {
    val border = when {
        error -> Palette.Red
        selected -> Color(0xFF84D8FF)
        else -> Palette.Gray
    }
    val bg = when {
        used -> Palette.Gray
        error -> Palette.RedLight
        selected -> Palette.BlueLight
        else -> Color.White
    }
    Box(
        Modifier
            .padding(bottom = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(2.dp, if (used) Palette.Gray else border, RoundedCornerShape(12.dp))
            .clickable(enabled = !used, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Text(text, fontSize = 17.sp, color = if (used) Color.Transparent else if (selected) Palette.BlueDark else Palette.Text)
    }
}
