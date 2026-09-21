package dev.helpmycity.ui.issues

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import helpmycity.shared.generated.resources.review_queue_count
import helpmycity.shared.generated.resources.review_queue_empty_body
import helpmycity.shared.generated.resources.review_queue_empty_title
import helpmycity.shared.generated.resources.review_queue_no_scope_body
import helpmycity.shared.generated.resources.review_queue_no_scope_title
import org.jetbrains.compose.resources.stringResource

/**
 * A manager's inbox: reports filed in their areas that nobody has triaged yet.
 *
 * A list that opens the issue detail, with no per-card approve: a one-tap
 * decision on a report nobody opened is how review turns into a rubber stamp.
 */
@Composable
fun ReviewQueueScreen(
    viewModel: ReviewQueueViewModel,
    onIssueClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (state.waiting.isEmpty()) {
        EmptyState(
            title = stringResource(
                if (state.hasNoScope) {
                    Res.string.review_queue_no_scope_title
                } else {
                    Res.string.review_queue_empty_title
                }
            ),
            body = stringResource(
                if (state.hasNoScope) {
                    Res.string.review_queue_no_scope_body
                } else {
                    Res.string.review_queue_empty_body
                }
            ),
            modifier = modifier,
        )
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = stringResource(Res.string.review_queue_count, state.waiting.size),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyColumn(
            // Extra bottom padding so the last card clears the floating action button.
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(state.waiting, key = Issue::id) { issue ->
                IssueCard(issue = issue, onClick = { onIssueClick(issue.id) })
            }
        }
    }
}
