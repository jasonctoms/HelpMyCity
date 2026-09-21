package dev.helpmycity.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * A labeled row of single-choice chips.
 *
 * Chips rather than a dropdown because the option sets are short and managers
 * are working one-handed on a phone -- everything is visible and reachable
 * without opening a menu, and chips carry a real selected state for screen
 * readers.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> OptionPicker(
    label: String,
    options: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    optionLabel: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.semantics { heading() },
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    enabled = enabled,
                    label = { Text(optionLabel(option)) },
                )
            }
        }
    }
}

/**
 * The same row of chips, choosing any number of them.
 *
 * What a manager's areas are: several districts, several neighborhoods, or none
 * at all. [enabled] is for the case where a wider assignment already covers
 * everything here -- the chips stay readable, so the assignment can be read
 * back, but they cannot be tapped into a state that would not mean anything.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> MultiOptionPicker(
    label: String,
    options: List<T>,
    selected: Set<T>,
    onToggle: (T) -> Unit,
    optionLabel: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    emptyLabel: String? = null,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.semantics { heading() },
        )
        if (options.isEmpty()) {
            emptyLabel?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = option in selected,
                    onClick = { onToggle(option) },
                    enabled = enabled,
                    label = { Text(optionLabel(option)) },
                )
            }
        }
    }
}
