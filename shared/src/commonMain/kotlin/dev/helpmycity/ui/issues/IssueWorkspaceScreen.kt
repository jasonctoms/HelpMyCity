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
 * The panes keep their own filters rather than sharing one: they filter on
 * different things -- a search and a status here, neighborhoods there -- and a
 * merged control would have to invent a meaning for combinations neither screen
 * has had to answer for. Each pane is the same composable it is on a phone, so
 * the narrow layout is a fallback rather than a second implementation.
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
            // Fixed rather than proportional: the list is a column of cards and
            // stops reading better past about this, so the map takes the rest of
            // whatever the window has.
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
