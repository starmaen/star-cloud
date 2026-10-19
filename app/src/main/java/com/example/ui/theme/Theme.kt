package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
  primary = StarBlueLight,
  onPrimary = DeepSpaceDark,
  primaryContainer = StarBlueDark,
  onPrimaryContainer = Color.White,
  secondary = StarlightGold,
  onSecondary = DeepSpaceDark,
  secondaryContainer = Color(0xFF78350F),
  onSecondaryContainer = StarlightGoldLight,
  tertiary = CosmicCyanLight,
  onTertiary = DeepSpaceDark,
  background = DeepSpaceDark,
  onBackground = Color(0xFFF1F5F9),
  surface = DeepSpaceSurface,
  onSurface = Color(0xFFF1F5F9),
  surfaceVariant = DeepSpaceSurfaceVariant,
  onSurfaceVariant = Color(0xFFCBD5E1),
  outline = Color(0xFF475569)
)

private val LightColorScheme = lightColorScheme(
  primary = StarBluePrimary,
  onPrimary = Color.White,
  primaryContainer = Color(0xFFDBEAFE),
  onPrimaryContainer = Color(0xFF1E3A8A),
  secondary = StarlightGold,
  onSecondary = Color.White,
  secondaryContainer = Color(0xFFFEF3C7),
  onSecondaryContainer = Color(0xFF78350F),
  tertiary = CosmicCyan,
  onTertiary = Color.White,
  background = CleanLightBackground,
  onBackground = Color(0xFF0F172A),
  surface = CleanLightSurface,
  onSurface = Color(0xFF0F172A),
  surfaceVariant = CleanLightSurfaceVariant,
  onSurfaceVariant = Color(0xFF475569),
  outline = Color(0xFFCBD5E1)
)

@Composable
fun StarCloudTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme = when {
    dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
      val context = LocalContext.current
      if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
    darkTheme -> DarkColorScheme
    else -> LightColorScheme
  }

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    content = content
  )
}
