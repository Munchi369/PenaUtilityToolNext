package io.github.munchi369.penautilitytoolnext.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font

internal val appFontFamily =
    FontFamily(
        listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).map { weight ->
            Font(
                resource = "fonts/InterVariable.ttf",
                weight = weight,
                variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
            )
        },
    )

internal val appTypography: Typography =
    Typography().let { defaults ->
        Typography(
            displayLarge = defaults.displayLarge.copy(fontFamily = appFontFamily),
            displayMedium = defaults.displayMedium.copy(fontFamily = appFontFamily),
            displaySmall = defaults.displaySmall.copy(fontFamily = appFontFamily),
            headlineLarge = defaults.headlineLarge.copy(fontFamily = appFontFamily),
            headlineMedium = defaults.headlineMedium.copy(fontFamily = appFontFamily),
            headlineSmall = defaults.headlineSmall.copy(fontFamily = appFontFamily),
            titleLarge = defaults.titleLarge.copy(fontFamily = appFontFamily),
            titleMedium = defaults.titleMedium.copy(fontFamily = appFontFamily),
            titleSmall = defaults.titleSmall.copy(fontFamily = appFontFamily),
            bodyLarge = defaults.bodyLarge.copy(fontFamily = appFontFamily),
            bodyMedium = defaults.bodyMedium.copy(fontFamily = appFontFamily),
            bodySmall = defaults.bodySmall.copy(fontFamily = appFontFamily),
            labelLarge = defaults.labelLarge.copy(fontFamily = appFontFamily),
            labelMedium = defaults.labelMedium.copy(fontFamily = appFontFamily),
            labelSmall = defaults.labelSmall.copy(fontFamily = appFontFamily),
        )
    }
