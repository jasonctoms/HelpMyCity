package dev.helpmycity.ui.issues

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.helpmycity.domain.model.Issue
import dev.helpmycity.domain.model.IssueStatus
import dev.helpmycity.ui.components.IssueCard
import dev.helpmycity.ui.label
import helpmycity.shared.generated.resources.Res
import helpmycity.shared.generated.resources.board_column_empty
import org.jetbrains.compose.resources.stringResource

/**
 * Kanban by status, the view managers work from.
 *
 * Read-only: columns show where everything sits, and status changes happen on
 * the detail screen.
 */
@Composable
fun IssueBoardScreen(
    viewModel: IssueListViewModel,
    onIssueClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val issues by viewModel.allIssues.collectAsStateWithLifecycle()
    val byStatus = issues.groupBy(Issue::status)

    Row(
        modifier = modifier
            .fillMaxSize()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IssueStatus.boardOrder.forEach { status ->
            BoardColumn(
                status = status,
                issues = byStatus[status].orEmpty(),
                onIssueClick = onIssueClick,
            )
        }
    }
}

@Composable
private fun BoardColumn(
    status: IssueStatus,
    issues: List<Issue>,
    onIssueClick: (String) -> Unit,
) {
    Column(
        modifier = Modifier.width(280.dp).fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "${status.label()} (${issues.size})",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 12.dp).semantics { heading() },
        )
        if (issues.isEmpty()) {
            Text(
                text = stringResource(Res.string.board_column_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                items(issues, key = Issue::id) { issue ->
                    IssueCard(issue = issue, onClick = { onIssueClick(issue.id) })
                }
            }
        }
    }
}
