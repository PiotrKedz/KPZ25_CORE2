package com.example.innertemp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Your custom colors
val TealPrimary = Color(0xFF4D7F86)        // #4d7f86
val DarkTeal = Color(0xFF013137)           // #013137 - for cards
val MediumTeal = Color(0xFF1A5A63)         // #1a5a63
val LightTeal = Color(0xFF7BB3BA)          // Light teal for text
val LimeAccent = Color(0xFFE8F54A)         // #e8f54a - for buttons

// Light theme colors
val LightPrimary = LimeAccent              // Buttons are lime
val LightOnPrimary = DarkTeal              // Dark teal text on lime buttons
val LightPrimaryContainer = DarkTeal       // Cards are dark teal
val LightOnPrimaryContainer = Color.White  // White text on dark teal cards

val LightSecondary = TealPrimary
val LightOnSecondary = Color.White
val LightSecondaryContainer = LightTeal
val LightOnSecondaryContainer = DarkTeal

val LightTertiary = MediumTeal
val LightOnTertiary = Color.White
val LightTertiaryContainer = Color(0xFF669197)
val LightOnTertiaryContainer = DarkTeal

val LightError = Color(0xFFBA1A1A)
val LightOnError = Color.White
val LightErrorContainer = Color(0xFFFFDAD6)
val LightOnErrorContainer = Color(0xFF410002)

val LightBackground = Color(0xFF669197)   // Light background
val LightOnBackground = DarkTeal           // Dark teal text
val LightSurface = DarkTeal                // Dark teal surface (cards will use this)
val LightOnSurface = Color.White           // White text on dark teal surface
val LightSurfaceVariant = Color(0xFF669197)
val LightOnSurfaceVariant = MediumTeal
val LightOutline = Color(0xFF6F797A)
val LightOutlineVariant = Color(0xFFBFC8CA)

// Dark theme colors
val DarkPrimary = LimeAccent               // Buttons are lime
val DarkOnPrimary = DarkTeal               // Dark teal text on lime buttons
val DarkPrimaryContainer = DarkTeal        // Cards are dark teal
val DarkOnPrimaryContainer = LightTeal     // Light teal text on dark teal cards

val DarkSecondary = LightTeal
val DarkOnSecondary = DarkTeal
val DarkSecondaryContainer = MediumTeal
val DarkOnSecondaryContainer = Color(0xFFE6F3F5)

val DarkTertiary = TealPrimary
val DarkOnTertiary = Color.White
val DarkTertiaryContainer = Color(0xFF2C3D00)
val DarkOnTertiaryContainer = Color(0xFFF4F9C4)

val DarkError = Color(0xFFFFB4AB)
val DarkOnError = Color(0xFF690005)
val DarkErrorContainer = Color(0xFF93000A)
val DarkOnErrorContainer = Color(0xFFFFDAD6)

val DarkBackground = Color(0xFF0F1415)     // Dark background
val DarkOnBackground = LightTeal           // Light teal text
val DarkSurface = DarkTeal                 // Dark teal surface (cards will use this)
val DarkOnSurface = LightTeal              // Light teal text on dark teal surface
val DarkSurfaceVariant = Color(0xFF3F484A)
val DarkOnSurfaceVariant = Color(0xFFBFC8CA)
val DarkOutline = Color(0xFF899294)
val DarkOutlineVariant = Color(0xFF3F484A)

// Temperature indicator colors
val Blue = Color(0xFF2196F3)        // Cold temperatures
val Green = TealPrimary             // Normal temperatures
val Red = Color(0xFFE53935)         // Hot temperatures

// Status colors
val WarningColor = Color(0xFFF59E0B)
val SuccessColor = TealPrimary
val InfoColor = MediumTeal

// Custom semantic colors for your specific use case
val ButtonColor = LimeAccent        // Lime buttons
val CardColor = DarkTeal           // Dark teal cards
val TextOnCard = LightTeal         // Light teal text on cards (dark theme)
val TextOnCardLight = Color.White  // White text on cards (light theme)

// Helper function to get card content color based on theme
@Composable
fun getCardContentColor(): Color {
    return if (isSystemInDarkTheme()) LightTeal else Color.White
}