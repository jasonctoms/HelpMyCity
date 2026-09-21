package dev.helpmycity.ui.issues

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.helpmycity.domain.model.IssueCategory
import dev.helpmycity.domain.model.IssuePriority
import dev.helpmycity.domain.repository.EditOutcome
import dev.helpmycity.ui.components.OptionPicker
import dev.helpmycity.ui.label
import dev.helpmycity.ui.map.LocationPickerMap
import helpmycity.shared.generated.resources.Res
import helpmycity.shared.generated.resources.detail_not_found
import helpmycity.shared.generated.resources.edit_cancel
import helpmycity.shared.generated.resources.edit_not_permitted
import helpmycity.shared.generated.resources.edit_notice
import helpmycity.shared.generated.resources.edit_save
import helpmycity.shared.generated.resources.field_category
import helpmycity.shared.generated.resources.field_department
import helpmycity.shared.generated.resources.field_description
import helpmycity.shared.generated.resources.field_location
import helpmycity.shared.generated.resources.field_location_hint
import helpmycity.shared.generated.resources.field_neighborhood
import helpmycity.shared.generated.resources.field_notes_source
import helpmycity.shared.generated.resources.field_priority
import helpmycity.shared.generated.resources.field_requested_action
import helpmycity.shared.generated.resources.field_title
import helpmycity.shared.generated.resources.form_error_description_required
import helpmycity.shared.generated.resources.form_error_location_required
import helpmycity.shared.generated.resources.form_error_title_required
import helpmycity.shared.generated.resources.location_pin_add
import helpmycity.shared.generated.resources.location_pin_edit
import helpmycity.shared.generated.resources.location_pin_hide
import helpmycity.shared.generated.resources.review_issue_gone
import org.jetbrains.compose.resources.stringResource

/**
 * A manager's completeness pass over a filed report: fix the category, name the
 * right department, tidy a description typed at a bus stop.
 *
 * The notice at the top earns its place -- every save is stamped on the issue
 * and shown to everyone, and the manager should know that before typing.
 */
@Composable
fun EditIssueScreen(
    viewModel: EditIssueViewModel,
    onSaved: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val options by viewModel.options.collectAsStateWithLifecycle()
    val saveError by viewModel.saveError.collectAsStateWithLifecycle()

    if (!form.isLoaded) return

    if (!form.isPermitted) {
        Text(
            text = stringResource(
                if (saveError == EditOutcome.IssueNotFound) {
                    Res.string.detail_not_found
                } else {
                    Res.string.edit_not_permitted
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
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(Res.string.edit_notice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = form.title,
            onValueChange = viewModel::onTitleChange,
            label = { Text(stringResource(Res.string.field_title)) },
            isError = form.titleError,
            supportingText = if (form.titleError) {
                { Text(stringResource(Res.string.form_error_title_required)) }
            } else {
                null
            },
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = form.description,
            onValueChange = viewModel::onDescriptionChange,
            label = { Text(stringResource(Res.string.field_description)) },
            minLines = 3,
            isError = form.descriptionError,
            supportingText = if (form.descriptionError) {
                { Text(stringResource(Res.string.form_error_description_required)) }
            } else {
                null
            },
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = form.locationDescription,
            onValueChange = viewModel::onLocationChange,
            label = { Text(stringResource(Res.string.field_location)) },
            placeholder = { Text(stringResource(Res.string.field_location_hint)) },
            isError = form.locationError,
            supportingText = if (form.locationError) {
                { Text(stringResource(Res.string.form_error_location_required)) }
            } else {
                geocodingSupportingText(form.geocoding)
            },
            modifier = Modifier.fillMaxWidth(),
        )

        // Collapsed by default: an always-open map would push the rest of the
        // form off a phone screen.
        TextButton(onClick = viewModel::onTogglePointPicker) {
            Text(
                stringResource(
                    if (form.isPickingPoint) {
                        Res.string.location_pin_hide
                    } else if (form.point == null) {
                        Res.string.location_pin_add
                    } else {
                        Res.string.location_pin_edit
                    }
                )
            )
        }
        if (form.isPickingPoint) {
            LocationPickerMap(
                point = form.point,
                isApproximate = form.isPointApproximate,
                settings = viewModel.mapSettings,
                onPointChange = viewModel::onPointChange,
            )
        }

        OutlinedTextField(
            value = form.requestedAction,
            onValueChange = viewModel::onRequestedActionChange,
            label = { Text(stringResource(Res.string.field_requested_action)) },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )

        OptionPicker(
            label = stringResource(Res.string.field_category),
            options = IssueCategory.entries,
            selected = form.category,
            onSelect = viewModel::onCategoryChange,
            optionLabel = { it.label() },
        )

        OptionPicker(
            label = stringResource(Res.string.field_priority),
            options = IssuePriority.entries,
            selected = form.priority,
            onSelect = viewModel::onPriorityChange,
            optionLabel = { it.label() },
        )

        if (options.neighborhoods.isNotEmpty()) {
            OptionPicker(
                label = stringResource(Res.string.field_neighborhood),
                options = options.neighborhoods,
                selected = options.neighborhoods.firstOrNull { it.id == form.neighborhood },
                onSelect = { viewModel.onNeighborhoodChange(it.id) },
                optionLabel = { it.name },
            )
        }

        if (options.departments.isNotEmpty()) {
            OptionPicker(
                label = stringResource(Res.string.field_department),
                options = options.departments,
                selected = options.departments.firstOrNull { it.id == form.departmentId },
                onSelect = { viewModel.onDepartmentChange(it.id) },
                optionLabel = { it.name },
            )
        }

        // A manager's own note -- "walk audit", "ownership unclear" -- which is
        // why it is not on the resident's form.
        OutlinedTextField(
            value = form.notesSource,
            onValueChange = viewModel::onNotesSourceChange,
            label = { Text(stringResource(Res.string.field_notes_source)) },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )

        saveError?.let { outcome ->
            Text(
                text = stringResource(
                    when (outcome) {
                        EditOutcome.NotPermitted -> Res.string.edit_not_permitted
                        else -> Res.string.review_issue_gone
                    }
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Button(
            onClick = { viewModel.save(onSaved) },
            enabled = !form.isSaving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.edit_save))
        }
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.edit_cancel))
        }
    }
}
