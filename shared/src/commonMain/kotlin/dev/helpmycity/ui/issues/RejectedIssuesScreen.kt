package dev.helpmycity.ui.issues

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.helpmycity.domain.model.Issue
import dev.helpmycity.ui.components.EmptyState
import dev.helpmycity.ui.components.IssueCard
import helpmycity.shared.generated.resources.Res
import helpmycity.shared.generated.resources.rejected_empty_body
import helpmycity.shared.generated.resources.rejected_empty_title
import helpmycity.shared.generated.resources.rejected_intro
import org.jetbrains.compose.resources.stringResource

@Composable
fun RejectedIssuesScreen(
    viewModel: RejectedIssuesViewModel,
    onIssueClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val issues by viewModel.issues.collectAsStateWithLifecycle()

    if (issues.isEmpty()) {
        EmptyState(
            title = stringResource(Res.string.rejected_empty_title),
            body = stringResource(Res.string.rejected_empty_body),
            modifier = modifier,
        )
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        item {
            Text(
                text = stringResource(Res.string.rejected_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(issues, key = Issue::id) { issue ->
            IssueCard(issue = issue, onClick = { onIssueClick(issue.id) })
        }
    }
}
