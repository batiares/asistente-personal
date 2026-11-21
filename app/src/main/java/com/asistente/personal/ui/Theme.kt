package com.asistente.personal.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.asistente.personal.R

/** Paleta neón: amarillo de alerta, cian de interfaz, magenta y rojo de peligro sobre negro azulado. */
object Neon {
    val Yellow = Color(0xFFFCEE0A)
    val Cyan = Color(0xFF00F0FF)
    val Magenta = Color(0xFFFF2A6D)
    val Red = Color(0xFFFF003C)
    val Violet = Color(0xFFB026FF)
    val Green = Color(0xFF39FF88)

    val Bg = Color(0xFF07070D)
    val Panel = Color(0xFF10101B)
    val PanelHi = Color(0xFF1B1830)
    val Text = Color(0xFFECEAF6)
    val Muted = Color(0xFF8C89A8)
}

val Rajdhani = FontFamily(
    Font(R.font.rajdhani_medium, FontWeight.Medium),
    Font(R.font.rajdhani_semibold, FontWeight.SemiBold),
    Font(R.font.rajdhani_bold, FontWeight.Bold),
)
val Mono = FontFamily(Font(R.font.share_tech_mono))

private val Base = TextStyle(fontFamily = Rajdhani, fontWeight = FontWeight.Medium, color = Neon.Text)

private val CyberTypography = Typography(
    displaySmall = Base.copy(fontWeight = FontWeight.Bold, fontSize = 40.sp, letterSpacing = 4.sp),
    headlineSmall = Base.copy(fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = 1.sp),
    titleLarge = Base.copy(fontWeight = FontWeight.Bold, fontSize = 22.sp),
    titleMedium = Base.copy(fontWeight = FontWeight.Bold, fontSize = 19.sp),
    titleSmall = Base.copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
    bodyLarge = Base.copy(fontSize = 18.sp, lineHeight = 24.sp),
    bodyMedium = Base.copy(fontSize = 16.sp, lineHeight = 21.sp),
    bodySmall = Base.copy(fontSize = 15.sp, lineHeight = 19.sp, color = Neon.Muted),
    labelLarge = TextStyle(fontFamily = Mono, fontSize = 14.sp, letterSpacing = 1.5.sp),
    labelMedium = TextStyle(fontFamily = Mono, fontSize = 12.sp, letterSpacing = 1.5.sp),
    labelSmall = TextStyle(fontFamily = Mono, fontSize = 11.sp, letterSpacing = 1.sp),
)

@Composable
fun AsistenteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Neon.Yellow, onPrimary = Color.Black,
            secondary = Neon.Cyan, onSecondary = Color.Black,
            tertiary = Neon.Magenta, onTertiary = Color.Black,
            background = Neon.Bg, onBackground = Neon.Text,
            surface = Neon.Panel, onSurface = Neon.Text,
            surfaceVariant = Neon.PanelHi, onSurfaceVariant = Neon.Muted,
            outline = Neon.Cyan, error = Neon.Red,
        ),
        typography = CyberTypography,
        content = content,
    )
}
