package dev.helpmycity.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.helpmycity.domain.model.IssueStatus
import dev.helpmycity.ui.label
import dev.helpmycity.ui.theme.markerColorFor
import helpmycity.shared.generated.resources.Res
import helpmycity.shared.generated.resources.status_field
import org.jetbrains.compose.resources.stringResource

/** Where an issue stands, set apart from the chips that describe it. */
@Composable
fun StatusField(status: IssueStatus, modifier: Modifier = Modifier) {
    val text = status.label()
    val description = stringResource(Res.string.status_field, text)
    val rejected = status == IssueStatus.REJECTED
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(if (rejected) MaterialTheme.colorScheme.error else markerColorFor(status)),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (rejected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}
