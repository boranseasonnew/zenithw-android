package space.zenithw.app

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Ink=Color(0xFF101114)
val Panel=Color(0xFF191B20)
val PanelRaised=Color(0xFF23262D)
val Hairline=Color(0xFF343840)
val Silver=Color(0xFFECEEF1)
val Muted=Color(0xFF9B9FA8)
val Success=Color(0xFFB3D1BD)

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
@Composable fun ZenithTheme(content: @Composable ()->Unit) {
    MaterialTheme(colorScheme=darkColorScheme(
        primary=Silver,onPrimary=Ink,secondary=Silver,onSecondary=Ink,
        background=Ink,onBackground=Silver,surface=Panel,onSurface=Silver,
        surfaceVariant=PanelRaised,onSurfaceVariant=Muted,outline=Hairline,error=Color(0xFFE7ACA9)
    ),typography=typography,content=content)
}
