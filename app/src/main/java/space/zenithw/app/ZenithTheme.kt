package space.zenithw.app

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Palette access stays source-compatible with existing screens while following
// the active Material color scheme, including a wallpaper-derived scheme.
val Ink: Color @Composable get()=MaterialTheme.colorScheme.background
val Panel: Color @Composable get()=MaterialTheme.colorScheme.surface
val PanelRaised: Color @Composable get()=MaterialTheme.colorScheme.surfaceVariant
val Hairline: Color @Composable get()=MaterialTheme.colorScheme.outline
val Silver: Color @Composable get()=MaterialTheme.colorScheme.onSurface
val Muted: Color @Composable get()=MaterialTheme.colorScheme.onSurfaceVariant
val Success: Color @Composable get()=MaterialTheme.colorScheme.tertiary

private val darkPalette=darkColorScheme(
    primary=Color(0xFFECEEF1),onPrimary=Color(0xFF101114),secondary=Color(0xFFECEEF1),onSecondary=Color(0xFF101114),
    background=Color(0xFF101114),onBackground=Color(0xFFECEEF1),surface=Color(0xFF191B20),onSurface=Color(0xFFECEEF1),
    surfaceVariant=Color(0xFF23262D),onSurfaceVariant=Color(0xFF9B9FA8),outline=Color(0xFF343840),
    tertiary=Color(0xFFB3D1BD),error=Color(0xFFE7ACA9))
private val lightPalette=lightColorScheme(
    primary=Color(0xFF252A34),onPrimary=Color.White,secondary=Color(0xFF465363),onSecondary=Color.White,
    background=Color(0xFFF7F8FA),onBackground=Color(0xFF171A20),surface=Color.White,onSurface=Color(0xFF171A20),
    surfaceVariant=Color(0xFFEDF0F4),onSurfaceVariant=Color(0xFF566171),outline=Color(0xFFCAD0D9),
    tertiary=Color(0xFF326C48),error=Color(0xFFAE3030))

private val typography=Typography(
    displaySmall=TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.Medium,fontSize=38.sp,lineHeight=43.sp,letterSpacing=(-1.5).sp),
    headlineMedium=TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.Medium,fontSize=29.sp,lineHeight=34.sp,letterSpacing=(-.8).sp),
    titleLarge=TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.Medium,fontSize=20.sp,lineHeight=26.sp),
    titleMedium=TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.Medium,fontSize=16.sp,lineHeight=23.sp),
    bodyLarge=TextStyle(fontFamily=FontFamily.SansSerif,fontSize=16.sp,lineHeight=24.sp),
    bodyMedium=TextStyle(fontFamily=FontFamily.SansSerif,fontSize=14.sp,lineHeight=21.sp),
    labelLarge=TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.Medium,fontSize=14.sp),
    labelSmall=TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.Medium,fontSize=11.sp,letterSpacing=.8.sp)
)
@Composable fun ZenithTheme(mode: String="dark",dynamicColors: Boolean=false,content: @Composable ()->Unit) {
    val dark=when(mode) { "system" -> isSystemInDarkTheme();"light" -> false;else -> true }
    val context=LocalContext.current
    val colors=when {
        dynamicColors && Build.VERSION.SDK_INT>=31 -> if(dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkPalette
        else -> lightPalette
    }
    val view=LocalView.current
    if(!view.isInEditMode) SideEffect {
        (view.context as? android.app.Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window,view).apply {
                isAppearanceLightStatusBars=!dark
                isAppearanceLightNavigationBars=!dark
            }
        }
    }
    MaterialTheme(colorScheme=colors,typography=typography,content=content)
}
