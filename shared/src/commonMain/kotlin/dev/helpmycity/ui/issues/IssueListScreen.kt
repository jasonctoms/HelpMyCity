package dev.helpmycity.ui.issues

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.helpmycity.domain.model.Issue
import dev.helpmycity.domain.model.IssueStatus
import dev.helpmycity.ui.components.EmptyState
import dev.helpmycity.ui.components.IssueCard
import dev.helpmycity.ui.label
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import helpmycity.shared.generated.resources.Res
import helpmycity.shared.generated.resources.issues_empty_body
import helpmycity.shared.generated.resources.issues_empty_filtered_body
import helpmycity.shared.generated.resources.issues_empty_filtered_title
import helpmycity.shared.generated.resources.issues_empty_title
import helpmycity.shared.generated.resources.issues_filter_clear
import helpmycity.shared.generated.resources.issues_filter_open_only
import helpmycity.shared.generated.resources.issues_search_hint
import org.jetbrains.compose.resources.stringResource

/**
 * @param clearsFloatingActionButton whether the last card has to stay out from
 *   under the Scaffold's report button. False where there is none -- the wide
 *   layout moves that action into the header. See [IssueWorkspaceScreen].
 */
@Composable
fun IssueListScreen(
    viewModel: IssueListViewModel,
    onIssueClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    clearsFloatingActionButton: Boolean = true,
) {
    val issues by viewModel.issues.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = filter.searchQuery,
            onValueChange = viewModel::onSearchQueryChange,
            label = { Text(stringResource(Res.string.issues_search_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )

        // Horizontally scrollable so the five status filters plus "open only"
        // stay reachable on a narrow phone in either language.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = filter.openOnly,
                onClick = viewModel::toggleOpenOnly,
                label = { Text(stringResource(Res.string.issues_filter_open_only)) },
            )
            IssueStatus.boardOrder.forEach { status ->
                FilterChip(
                    selected = status in filter.statuses,
                    onClick = { viewModel.toggleStatus(status) },
                    label = { Text(status.label()) },
                )
            }
        }

        if (!filter.isEmpty) {
            TextButton(
                onClick = viewModel::clearFilters,
                modifier = Modifier.padding(horizontal = 8.dp),
            ) {
                Text(stringResource(Res.string.issues_filter_clear))
            }
        }

        IssueList(
            issues = issues,
            isFiltered = !filter.isEmpty,
            onIssueClick = onIssueClick,
            clearsFloatingActionButton = clearsFloatingActionButton,
        )
    }
}

@Composable
private fun IssueList(
    issues: List<Issue>,
    isFiltered: Boolean,
    onIssueClick: (String) -> Unit,
    clearsFloatingActionButton: Boolean,
) {
    if (issues.isEmpty()) {
        EmptyState(
            title = stringResource(
                if (isFiltered) Res.string.issues_empty_filtered_title else Res.string.issues_empty_title
            ),
            body = stringResource(
                if (isFiltered) Res.string.issues_empty_filtered_body else Res.string.issues_empty_body
            ),
        )
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 16.dp,
            // Extra, where there is a floating action button for the last card
            // to clear.
            bottom = if (clearsFloatingActionButton) 88.dp else 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(issues, key = Issue::id) { issue ->
            IssueCard(issue = issue, onClick = { onIssueClick(issue.id) })
        }
    }
}
