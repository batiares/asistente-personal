package com.asistente.personal.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

/** Esquinas cortadas en diagonal: la firma visual del tema. */
val PanelShape = CutCornerShape(topEnd = 18.dp, bottomStart = 18.dp)
val ButtonShape = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp)

/** Rejilla tenue de fondo + líneas de escaneo encima del contenido. */
fun Modifier.cyberBackdrop(): Modifier = this
    .background(Neon.Bg)
    .drawBehind {
        val step = 32.dp.toPx()
        val grid = Neon.Cyan.copy(alpha = 0.05f)
        var x = 0f
        while (x < size.width) { drawLine(grid, Offset(x, 0f), Offset(x, size.height), 1f); x += step }
        var y = 0f
        while (y < size.height) { drawLine(grid, Offset(0f, y), Offset(size.width, y), 1f); y += step }
        // halo magenta arriba y cian abajo
        drawRect(Brush.verticalGradient(listOf(Neon.Magenta.copy(alpha = 0.18f), Color.Transparent), endY = size.height * 0.35f))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Neon.Cyan.copy(alpha = 0.10f)), startY = size.height * 0.7f))
    }
    .drawWithContent {
        drawContent()
        val step = 3.dp.toPx()
        var y = 0f
        while (y < size.height) {
            drawLine(Color.Black.copy(alpha = 0.16f), Offset(0f, y), Offset(size.width, y), 1f)
            y += step
        }
    }

@Composable
fun CyberPanel(
    modifier: Modifier = Modifier,
    accent: Color = Neon.Cyan,
    glow: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .then(if (glow) Modifier.shadow(18.dp, PanelShape, ambientColor = accent, spotColor = accent) else Modifier)
            .clip(PanelShape)
            .background(Brush.linearGradient(listOf(Neon.PanelHi, Neon.Panel)))
            .border(1.dp, Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.12f), accent.copy(alpha = 0.5f))), PanelShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .drawBehind { drawRect(accent, size = Size(4.dp.toPx(), size.height)) }
            .padding(start = 18.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

@Composable
fun CyberButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Neon.Yellow,
    filled: Boolean = true,
) {
    Box(
        modifier
            .heightIn(min = 44.dp)
            .clip(ButtonShape)
            .then(if (filled) Modifier.background(color) else Modifier.border(1.5.dp, color, ButtonShape))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = if (filled) Color.Black else color,
        )
    }
}

@Composable
fun Tag(text: String, color: Color) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = Color.Black,
        modifier = Modifier
            .background(color, CutCornerShape(bottomEnd = 6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
fun SectionHeader(text: String, color: Color = Neon.Cyan, trailing: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        Text("// " + text.uppercase(), style = MaterialTheme.typography.labelLarge, color = color)
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(Brush.horizontalGradient(listOf(color, Color.Transparent)))
        )
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            Text(trailing.uppercase(), style = MaterialTheme.typography.labelMedium, color = Neon.Muted)
        }
    }
}

/** Texto con aberración cromática (copias cian y magenta desfasadas). */
@Composable
fun GlitchText(text: String, style: TextStyle, color: Color = Neon.Yellow, modifier: Modifier = Modifier) {
    Box(modifier) {
        Text(text, style = style, color = Neon.Cyan.copy(alpha = 0.75f),
            modifier = Modifier.offset(x = (-2).dp, y = 1.dp).clearAndSetSemantics {})
        Text(text, style = style, color = Neon.Magenta.copy(alpha = 0.75f),
            modifier = Modifier.offset(x = 2.dp, y = (-1).dp).clearAndSetSemantics {})
        Text(text, style = style, color = color)
    }
}

/** Punto que parpadea (estado "en línea" / "en vivo"). */
@Composable
fun Blink(color: Color, modifier: Modifier = Modifier) {
    val a by rememberInfiniteTransition(label = "blink").animateFloat(
        initialValue = 1f, targetValue = 0.2f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "alpha",
    )
    Text("●", color = color, style = MaterialTheme.typography.labelLarge, modifier = modifier.alpha(a))
}

/** Bloque de aviso con pasos numerados. */
@Composable
fun Steps(vararg steps: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        steps.forEachIndexed { i, s ->
            Row {
                Text("%02d ".format(i + 1), style = MaterialTheme.typography.labelLarge, color = Neon.Yellow,
                    modifier = Modifier.padding(top = 2.dp))
                Text(s, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
