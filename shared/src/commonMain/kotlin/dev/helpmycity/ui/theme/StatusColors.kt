package dev.helpmycity.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import dev.helpmycity.domain.model.IssuePriority
import dev.helpmycity.domain.model.IssueStatus

/**
 * Container/content pairs for priority chips.
 *
 * Color is never the only signal -- every chip carries its label as text too.
 * Each pair here clears WCAG AA (4.5:1) for body text in both the light and
 * dark scheme.
 */
data class ChipColors(val container: Color, val content: Color)

/**
 * Fill color for an issue's marker on the map, and for the dot beside its
 * status everywhere else, so a status is the same color on both.
 *
 * Deliberately not theme-aware: the markers sit on basemap tiles rather than on
 * an app surface, so these are picked to hold up against both light and dark
 * cartography and are drawn with a white stroke for separation. Each one also
 * carries a white glyph (`StatusGlyphPainter`), so every color here must hold
 * at least 3:1 against white, and `IssueMapScreen` still prints a labeled
 * legend -- color is never the only signal here either.
 */
fun markerColorFor(status: IssueStatus): Color = when (status) {
    IssueStatus.IN_REVIEW -> Color(0xFF5F6B76)
    IssueStatus.OPEN -> Color(0xFF0B6BCB)
    IssueStatus.IN_PROGRESS -> Color(0xFFB26A00)
    IssueStatus.COMPLETE -> Color(0xFF1E7A46)
    IssueStatus.REJECTED -> Color(0xFFB3261E)
}

@Composable
@ReadOnlyComposable
fun colorsFor(priority: IssuePriority): ChipColors {
    val dark = isSystemInDarkTheme()
    return when (priority) {
        IssuePriority.HIGH ->
            if (dark) ChipColors(Color(0xFF5C1414), Color(0xFFFFDAD6))
            else ChipColors(Color(0xFFFFDAD6), Color(0xFF6E1512))

        IssuePriority.MEDIUM ->
            if (dark) ChipColors(Color(0xFF4A3A00), Color(0xFFFFE9A8))
            else ChipColors(Color(0xFFFFF0C2), Color(0xFF3F3100))

        IssuePriority.LOW ->
            if (dark) ChipColors(Color(0xFF33383D), Color(0xFFD5DBE1))
            else ChipColors(Color(0xFFEBEEF1), Color(0xFF32373C))
    }
}
