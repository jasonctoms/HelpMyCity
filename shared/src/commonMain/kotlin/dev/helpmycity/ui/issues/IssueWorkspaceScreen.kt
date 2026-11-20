package dev.helpmycity.ui.issues

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.helpmycity.ui.map.IssueMapScreen
import dev.helpmycity.ui.map.IssueMapViewModel

/**
 * The issue list and the map as one screen, for a window with room for both.
 *
 * The panes keep their own filters instead of sharing one. They filter on
 * different things (a search and a status on one side, neighborhoods on the
 * other), and a merged control would have to make up a meaning for combinations
 * neither screen has ever had to deal with. Each pane is the same composable it is
 * on a phone, so the narrow layout is a fallback and not a second implementation.
 */
@Composable
fun IssueWorkspaceScreen(
    listViewModel: IssueListViewModel,
    mapViewModel: IssueMapViewModel,
    onIssueClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxSize()) {
        IssueListScreen(
            viewModel = listViewModel,
            onIssueClick = onIssueClick,
            // Fixed instead of proportional. The list is a column of cards, and it
            // stops getting easier to read past about this width, so the map gets
            // whatever is left of the window.
            modifier = Modifier.width(ListPaneWidth),
            clearsFloatingActionButton = false,
        )
        VerticalDivider()
        IssueMapScreen(
            viewModel = mapViewModel,
            onIssueClick = onIssueClick,
            modifier = Modifier.weight(1f),
            clearsFloatingActionButton = false,
        )
    }
}

private val ListPaneWidth = 420.dp
