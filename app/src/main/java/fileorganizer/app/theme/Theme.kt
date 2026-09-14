package fileorganizer.app.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
private val FileKitDarkColorScheme = darkColorScheme(
    primary = PureWhite,
    onPrimary = DeepMidnight,
    
    secondary = NeonYellowDark,
    onSecondary = DeepMidnight,
    
    background = DeepMidnight,
    onBackground = TextPrimary,
    
    surface = SurfaceDark,
    onSurface = TextPrimary,
    
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextSecondary,
    
    primaryContainer = SurfaceBright,
    onPrimaryContainer = PureWhite
)

private val FileKitLightColorScheme = lightColorScheme(
    primary = Color(0xFF202124), // Using a standard dark color for primary to fix text/icon colors
    onPrimary = PureWhite,
    
    secondary = NeonYellowLight,
    onSecondary = PureWhite,
    
    background = Color(0xFFF8F9FA),
    onBackground = Color(0xFF202124),
    
    surface = PureWhite,
    onSurface = Color(0xFF202124),
    
    surfaceVariant = Color(0xFFF1F3F4),
    onSurfaceVariant = Color(0xFF5F6368),
    
    primaryContainer = Color(0xFFF1F4F6),
    onPrimaryContainer = Color(0xFF202124)
)

@Composable
fun FileKitTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // We disable dynamic color by default so the custom Neon/Midnight palette is always applied
    dynamicColor: Boolean = false, 
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> FileKitDarkColorScheme
        else -> FileKitLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme, 
        typography = Typography, 
        content = content
    )
}

