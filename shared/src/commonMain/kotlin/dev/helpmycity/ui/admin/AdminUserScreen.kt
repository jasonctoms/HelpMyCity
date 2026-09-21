package dev.helpmycity.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.helpmycity.domain.model.Department
import dev.helpmycity.domain.model.Neighborhood
import dev.helpmycity.domain.model.UserRole
import dev.helpmycity.domain.repository.AssignmentOutcome
import dev.helpmycity.ui.components.MultiOptionPicker
import dev.helpmycity.ui.components.OptionPicker
import dev.helpmycity.ui.label
import helpmycity.shared.generated.resources.Res
import helpmycity.shared.generated.resources.admin_user_areas
import helpmycity.shared.generated.resources.admin_user_citywide
import helpmycity.shared.generated.resources.admin_user_citywide_body
import helpmycity.shared.generated.resources.admin_user_departments
import helpmycity.shared.generated.resources.admin_user_districts
import helpmycity.shared.generated.resources.admin_user_empty_scope
import helpmycity.shared.generated.resources.admin_user_neighborhoods
import helpmycity.shared.generated.resources.admin_user_no_departments
import helpmycity.shared.generated.resources.admin_user_no_districts
import helpmycity.shared.generated.resources.admin_user_no_neighborhoods
import helpmycity.shared.generated.resources.admin_user_not_found
import helpmycity.shared.generated.resources.admin_user_role
import helpmycity.shared.generated.resources.admin_user_role_admin_note
import helpmycity.shared.generated.resources.admin_user_role_locked
import helpmycity.shared.generated.resources.admin_user_role_resident_note
import helpmycity.shared.generated.resources.admin_user_save
import helpmycity.shared.generated.resources.admin_users_not_permitted
import helpmycity.shared.generated.resources.edit_cancel
import org.jetbrains.compose.resources.stringResource

/**
 * One person's role, and the slice of the city a manager answers for.
 *
 * The four area pickers are independent because cities delegate in all four
 * ways at once -- see [dev.helpmycity.domain.model.ManagerScope] --
 * and any one of them matching is enough to put a report in someone's queue.
 */
@Composable
fun AdminUserScreen(
    viewModel: AdminUserViewModel,
    onSaved: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val options by viewModel.options.collectAsStateWithLifecycle()
    val saveError by viewModel.saveError.collectAsStateWithLifecycle()

    if (!form.isLoaded) return

    val user = form.user
    if (!form.isPermitted || user == null) {
        Text(
            text = stringResource(
                if (saveError == AssignmentOutcome.UserNotFound) {
                    Res.string.admin_user_not_found
                } else {
                    Res.string.admin_users_not_permitted
                }
            ),
            modifier = modifier.padding(24.dp),
        )
        return
    }

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
                )
                user.email?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            OptionPicker(
                label = stringResource(Res.string.admin_user_role),
                options = UserRole.entries,
                selected = form.role,
                onSelect = viewModel::onRoleChange,
                optionLabel = { it.label() },
                enabled = !form.isSelf,
            )
            RoleNote(isSelf = form.isSelf, role = form.role)

            if (form.showsAreas) {
                HorizontalDivider()

                Text(
                    text = stringResource(Res.string.admin_user_areas),
                    style = MaterialTheme.typography.titleMedium,
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(Res.string.admin_user_citywide),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = stringResource(Res.string.admin_user_citywide_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = form.citywide, onCheckedChange = viewModel::onCitywideChange)
                }

                MultiOptionPicker(
                    label = stringResource(Res.string.admin_user_districts),
                    options = options.districts,
                    selected = form.districts,
                    onToggle = viewModel::onDistrictToggle,
                    optionLabel = { it },
                    enabled = form.areasEnabled,
                    emptyLabel = stringResource(Res.string.admin_user_no_districts),
                )

                MultiOptionPicker(
                    label = stringResource(Res.string.admin_user_neighborhoods),
                    options = options.neighborhoods.map(Neighborhood::id),
                    selected = form.neighborhoodIds,
                    onToggle = viewModel::onNeighborhoodToggle,
                    optionLabel = { id ->
                        options.neighborhoods.firstOrNull { it.id == id }?.name ?: id
                    },
                    enabled = form.areasEnabled,
                    emptyLabel = stringResource(Res.string.admin_user_no_neighborhoods),
                )

                MultiOptionPicker(
                    label = stringResource(Res.string.admin_user_departments),
                    options = options.departments.map(Department::id),
                    selected = form.departmentIds,
                    onToggle = viewModel::onDepartmentToggle,
                    optionLabel = { id ->
                        options.departments.firstOrNull { it.id == id }?.name ?: id
                    },
                    enabled = form.areasEnabled,
                    emptyLabel = stringResource(Res.string.admin_user_no_departments),
                )

                if (form.warnsEmptyScope) {
                    Text(
                        text = stringResource(Res.string.admin_user_empty_scope),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            saveError?.let { outcome ->
                Text(
                    text = stringResource(outcome.messageResource()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Button(
                onClick = { viewModel.save(onSaved) },
                enabled = !form.isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.admin_user_save))
            }

            TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.edit_cancel))
            }
        }
    }
}

/** Says why the role picker will not move, or what the chosen role already implies. */
@Composable
private fun RoleNote(isSelf: Boolean, role: UserRole) {
    val note = when {
        isSelf -> stringResource(Res.string.admin_user_role_locked)
        role == UserRole.ADMIN -> stringResource(Res.string.admin_user_role_admin_note)
        role == UserRole.RESIDENT -> stringResource(Res.string.admin_user_role_resident_note)
        else -> return
    }
    Text(
        text = note,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun AssignmentOutcome.messageResource() = when (this) {
    AssignmentOutcome.OwnRoleUnchangeable -> Res.string.admin_user_role_locked
    AssignmentOutcome.UserNotFound -> Res.string.admin_user_not_found
    // Saved and NoChanges never reach here: both leave the screen.
    else -> Res.string.admin_users_not_permitted
}
