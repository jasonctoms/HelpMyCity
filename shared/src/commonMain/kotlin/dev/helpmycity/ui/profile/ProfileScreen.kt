package dev.helpmycity.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.helpmycity.domain.model.UserRole
import dev.helpmycity.ui.components.rememberCsvSaver
import dev.helpmycity.ui.label
import kotlinx.coroutines.launch
import helpmycity.shared.generated.resources.Res
import helpmycity.shared.generated.resources.profile_admin_everything
import helpmycity.shared.generated.resources.profile_areas
import helpmycity.shared.generated.resources.profile_areas_citywide
import helpmycity.shared.generated.resources.profile_areas_none
import helpmycity.shared.generated.resources.profile_edit
import helpmycity.shared.generated.resources.profile_export_csv
import helpmycity.shared.generated.resources.profile_export_csv_body
import helpmycity.shared.generated.resources.profile_export_csv_failed
import helpmycity.shared.generated.resources.profile_guest_body
import helpmycity.shared.generated.resources.profile_guest_title
import helpmycity.shared.generated.resources.profile_manage_users
import helpmycity.shared.generated.resources.profile_manage_users_body
import helpmycity.shared.generated.resources.profile_resident_note
import helpmycity.shared.generated.resources.profile_role
import helpmycity.shared.generated.resources.sign_out
import org.jetbrains.compose.resources.stringResource

/**
 * Who you are here, and what that lets you do.
 *
 * The hub the profile icon opens: who you are, the areas a manager
 * answers for, user administration and CSV export for an admin, and the way out.
 * A resident sees the first and the last of those, which is the point of showing
 * the role in plain words rather than only in the tabs that happen to appear.
 */
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onEditProfile: () -> Unit,
    onManageUsers: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var exportFailed by remember { mutableStateOf(false) }
    val csvSaver = rememberCsvSaver(onFailed = { exportFailed = true })
    val user = state.user ?: return

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = user.displayName,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
                user.email?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (user.isGuest) {
                Section(title = stringResource(Res.string.profile_guest_title)) {
                    Text(
                        text = stringResource(Res.string.profile_guest_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Section(title = stringResource(Res.string.profile_role)) {
                    Text(
                        text = user.role.label(),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    val note = when (user.role) {
                        UserRole.ADMIN -> stringResource(Res.string.profile_admin_everything)
                        UserRole.RESIDENT -> stringResource(Res.string.profile_resident_note)
                        UserRole.MANAGER -> null
                    }
                    note?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (state.showsAreas) {
                Section(title = stringResource(Res.string.profile_areas)) {
                    when {
                        state.isCitywide -> Text(
                            text = stringResource(Res.string.profile_areas_citywide),
                            style = MaterialTheme.typography.bodyLarge,
                        )

                        state.areaNames.isEmpty() -> Text(
                            text = stringResource(Res.string.profile_areas_none),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        else -> state.areaNames.forEach {
                            Text(text = it, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }

            if (state.canEditProfile) {
                OutlinedButton(onClick = onEditProfile, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.profile_edit))
                }
            }

            if (state.canManageUsers) {
                Card(onClick = onManageUsers, modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = stringResource(Res.string.profile_manage_users),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(Res.string.profile_manage_users_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (state.canExportIssues) {
                Card(
                    onClick = {
                        exportFailed = false
                        scope.launch {
                            val csv = viewModel.exportCsv() ?: return@launch
                            csvSaver.save(viewModel.exportFileName(), csv)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = stringResource(Res.string.profile_export_csv),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(Res.string.profile_export_csv_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (exportFailed) {
                            Text(
                                text = stringResource(Res.string.profile_export_csv_failed),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            TextButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.sign_out))
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.semantics { heading() },
        )
        content()
    }
}
