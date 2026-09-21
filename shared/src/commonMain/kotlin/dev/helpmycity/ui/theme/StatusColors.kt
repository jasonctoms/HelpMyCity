package dev.helpmycity.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import dev.helpmycity.domain.model.IssuePriority
import dev.helpmycity.domain.model.IssueStatus

/**
 * Container/content pairs for status and priority chips.
 *
 * Color is never the only signal -- every chip carries its label as text too.
 * Each pair here clears WCAG AA (4.5:1) for body text in both the light and
 * dark scheme.
 */
data class ChipColors(val container: Color, val content: Color)

@Composable
@ReadOnlyComposable
fun colorsFor(status: IssueStatus): ChipColors {
    val dark = isSystemInDarkTheme()
    return when (status) {
        IssueStatus.SUBMITTED ->
            if (dark) ChipColors(Color(0xFF3A3F45), Color(0xFFDCE3EA))
            else ChipColors(Color(0xFFE4E8EC), Color(0xFF2B3136))

        IssueStatus.OPENED ->
            if (dark) ChipColors(Color(0xFF00405C), Color(0xFFC7E7FF))
            else ChipColors(Color(0xFFCDE8FA), Color(0xFF00344C))

        IssueStatus.NEEDS_NEXT_STEPS ->
            if (dark) ChipColors(Color(0xFF5B4300), Color(0xFFFFE0A3))
            else ChipColors(Color(0xFFFFE7B8), Color(0xFF4A3600))

        IssueStatus.APPROVED_PENDING ->
            if (dark) ChipColors(Color(0xFF4A2B6B), Color(0xFFE9D6FF))
            else ChipColors(Color(0xFFEADDFF), Color(0xFF3B2158))

        IssueStatus.CLOSED_COMPLETE ->
            if (dark) ChipColors(Color(0xFF14432A), Color(0xFFB8EFCB))
            else ChipColors(Color(0xFFCDF0DA), Color(0xFF0C3A22))
    }
}

/**
 * Fill color for an issue's marker on the map.
 *
 * Deliberately not theme-aware: these sit on basemap tiles rather than on an
 * app surface, so they are picked to hold up against both light and dark
 * cartography and are drawn with a white stroke for separation. Lightness
 * varies along with hue so the five stay tellable apart without color vision,
 * and `IssueMapScreen` still prints a labeled legend -- color is never the
 * only signal here either.
 */
fun markerColorFor(status: IssueStatus): Color = when (status) {
    IssueStatus.SUBMITTED -> Color(0xFF5F6B76)
    IssueStatus.OPENED -> Color(0xFF0B6BCB)
    IssueStatus.NEEDS_NEXT_STEPS -> Color(0xFFB26A00)
    IssueStatus.APPROVED_PENDING -> Color(0xFF6E3FAF)
    IssueStatus.CLOSED_COMPLETE -> Color(0xFF1E7A46)
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
