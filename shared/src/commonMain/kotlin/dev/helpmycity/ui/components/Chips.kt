package dev.helpmycity.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import dev.helpmycity.domain.model.IssueCategory
import dev.helpmycity.domain.model.IssuePriority
import dev.helpmycity.domain.model.IssueStatus
import dev.helpmycity.domain.model.ReviewState
import dev.helpmycity.ui.label
import dev.helpmycity.ui.labelOrNull
import dev.helpmycity.ui.theme.colorsFor
import helpmycity.shared.generated.resources.Res
import helpmycity.shared.generated.resources.edited_chip
import helpmycity.shared.generated.resources.field_category
import helpmycity.shared.generated.resources.field_priority
import org.jetbrains.compose.resources.stringResource

@Composable
fun StatusChip(status: IssueStatus, modifier: Modifier = Modifier) {
    val colors = colorsFor(status)
    Chip(
        text = status.label(),
        background = colors.container,
        foreground = colors.content,
        modifier = modifier,
    )
}

@Composable
fun PriorityChip(priority: IssuePriority, modifier: Modifier = Modifier) {
    val colors = colorsFor(priority)
    val text = priority.label()
    Chip(
        text = text,
        background = colors.container,
        foreground = colors.content,
        modifier = modifier,
        // A screen reader should hear "Priority: High", not a bare "High".
        semanticLabel = "${stringResource(Res.string.field_priority)}: $text",
    )
}

/**
 * Marks an issue that the public cannot see yet.
 *
 * Nothing is drawn for an approved issue: that is the ordinary case, and a chip
 * on every card would train people to ignore the one that matters. Rejected
 * borrows the error colors -- it is the one state that needs the submitter to
 * read something.
 */
@Composable
fun ReviewChip(state: ReviewState, modifier: Modifier = Modifier) {
    val text = state.labelOrNull() ?: return
    Chip(
        text = text,
        background = if (state == ReviewState.REJECTED) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.tertiaryContainer
        },
        foreground = if (state == ReviewState.REJECTED) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onTertiaryContainer
        },
        modifier = modifier,
    )
}

/**
 * Marks an issue a manager has corrected. Shown to everyone, so a resident
 * reading their own report can tell it was changed. Neutral colors: an edit is
 * housekeeping, not a warning.
 */
@Composable
fun EditedChip(modifier: Modifier = Modifier) {
    Chip(
        text = stringResource(Res.string.edited_chip),
        background = MaterialTheme.colorScheme.secondaryContainer,
        foreground = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier,
    )
}

@Composable
fun CategoryChip(category: IssueCategory, modifier: Modifier = Modifier) {
    val text = category.label()
    Chip(
        text = text,
        background = MaterialTheme.colorScheme.surfaceVariant,
        foreground = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
        semanticLabel = "${stringResource(Res.string.field_category)}: $text",
    )
}

@Composable
private fun Chip(
    text: String,
    background: Color,
    foreground: Color,
    modifier: Modifier = Modifier,
    semanticLabel: String? = null,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = foreground,
        modifier = modifier
            .then(
                if (semanticLabel != null) {
                    Modifier.clearAndSetSemantics { contentDescription = semanticLabel }
                } else {
                    Modifier
                }
            )
            .background(background, RoundedCornerShape(percent = 50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
