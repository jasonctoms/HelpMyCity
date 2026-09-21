package dev.helpmycity.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.helpmycity.domain.model.UserRole
import dev.helpmycity.ui.components.EmptyState
import dev.helpmycity.ui.label
import helpmycity.shared.generated.resources.Res
import helpmycity.shared.generated.resources.admin_users_count
import helpmycity.shared.generated.resources.admin_users_empty_body
import helpmycity.shared.generated.resources.admin_users_empty_title
import helpmycity.shared.generated.resources.admin_users_no_areas
import helpmycity.shared.generated.resources.admin_users_not_permitted
import helpmycity.shared.generated.resources.admin_users_you
import helpmycity.shared.generated.resources.profile_areas_citywide
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Everyone this deployment has seen, and what each of them may do.
 *
 * Reads rather than edits: the assignment itself is one person at a time on
 * [AdminUserScreen], because a role changed by a stray tap in a list is a role
 * nobody meant to change.
 */
@Composable
fun AdminUsersScreen(
    viewModel: AdminUsersViewModel,
    onUserClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (!state.isPermitted) {
        Text(
            text = stringResource(Res.string.admin_users_not_permitted),
            modifier = modifier.padding(24.dp),
        )
        return
    }

    if (state.users.isEmpty()) {
        EmptyState(
            title = stringResource(Res.string.admin_users_empty_title),
            body = stringResource(Res.string.admin_users_empty_body),
            modifier = modifier,
        )
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = pluralStringResource(
                Res.plurals.admin_users_count,
                state.users.size,
                state.users.size,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(state.users, key = { it.user.id }) { row ->
                UserCard(row = row, onClick = { onUserClick(row.user.id) })
            }
        }
    }
}

@Composable
private fun UserCard(row: AdminUserRow, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = row.user.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (row.isSelf) {
                    Text(
                        text = stringResource(Res.string.admin_users_you),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            row.user.email?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = row.user.role.label(),
                style = MaterialTheme.typography.labelLarge,
            )
            // Only a manager's areas mean anything: an admin already reaches
            // everything and a resident reviews nothing.
            if (row.user.role == UserRole.MANAGER) {
                Text(
                    text = when {
                        row.user.scope.citywide ->
                            stringResource(Res.string.profile_areas_citywide)

                        row.areaNames.isEmpty() -> stringResource(Res.string.admin_users_no_areas)
                        else -> row.areaNames.joinToString(" · ")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
