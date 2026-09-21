package dev.helpmycity.ui.issues

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.helpmycity.domain.model.IssueCategory
import dev.helpmycity.domain.model.IssuePriority
import dev.helpmycity.ui.components.OptionPicker
import dev.helpmycity.ui.components.PhotoThumbnail
import dev.helpmycity.ui.map.LocationPickerMap
import dev.helpmycity.ui.label
import helpmycity.shared.generated.resources.Res
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.launch
import helpmycity.shared.generated.resources.duplicate_warning
import helpmycity.shared.generated.resources.field_category
import helpmycity.shared.generated.resources.field_department
import helpmycity.shared.generated.resources.field_description
import helpmycity.shared.generated.resources.field_description_hint
import helpmycity.shared.generated.resources.field_location
import helpmycity.shared.generated.resources.field_location_hint
import helpmycity.shared.generated.resources.field_neighborhood
import helpmycity.shared.generated.resources.location_pin_add
import helpmycity.shared.generated.resources.location_pin_edit
import helpmycity.shared.generated.resources.location_pin_hide
import helpmycity.shared.generated.resources.photo_add
import helpmycity.shared.generated.resources.field_priority
import helpmycity.shared.generated.resources.field_reporter_email
import helpmycity.shared.generated.resources.field_reporter_hint
import helpmycity.shared.generated.resources.field_reporter_name
import helpmycity.shared.generated.resources.field_reporter_phone
import helpmycity.shared.generated.resources.field_reporter_section
import helpmycity.shared.generated.resources.field_requested_action
import helpmycity.shared.generated.resources.field_title
import helpmycity.shared.generated.resources.field_title_hint
import helpmycity.shared.generated.resources.form_error_description_required
import helpmycity.shared.generated.resources.form_error_location_required
import helpmycity.shared.generated.resources.form_error_title_required
import helpmycity.shared.generated.resources.new_issue_submit
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** The public submission form. No account required, three mandatory fields. */
@Composable
fun NewIssueScreen(
    viewModel: NewIssueViewModel,
    onSubmitted: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val options by viewModel.options.collectAsStateWithLifecycle()
    val duplicateCount by viewModel.possibleDuplicateCount.collectAsStateWithLifecycle()
    // Reading the picked files is suspending, so the picker callback hands off here.
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OutlinedTextField(
            value = form.title,
            onValueChange = viewModel::onTitleChange,
            label = { Text(stringResource(Res.string.field_title)) },
            placeholder = { Text(stringResource(Res.string.field_title_hint)) },
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
            placeholder = { Text(stringResource(Res.string.field_description_hint)) },
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

        // Pin drop, collapsed by default: it is optional, and an always-open map
        // would push the rest of the form off a phone screen.
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

        if (duplicateCount > 0) {
            Text(
                text = pluralStringResource(
                    Res.plurals.duplicate_warning,
                    duplicateCount,
                    duplicateCount,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        val photoPicker = rememberFilePickerLauncher(
            type = FileKitType.Image,
            mode = FileKitMode.Multiple(),
        ) { picked ->
            if (picked.isNullOrEmpty()) return@rememberFilePickerLauncher
            scope.launch { viewModel.onPhotosPicked(picked.map { it.readBytes() }) }
        }
        TextButton(onClick = { photoPicker.launch() }) {
            Text(stringResource(Res.string.photo_add))
        }
        if (form.photos.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                form.photos.forEach { photo ->
                    PhotoThumbnail(
                        model = photo.bytes,
                        onRemove = { viewModel.onPhotoRemoved(photo.id) },
                    )
                }
            }
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

        HorizontalDivider()

        Text(
            text = stringResource(Res.string.field_reporter_section),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(Res.string.field_reporter_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = form.reporterName,
            onValueChange = viewModel::onReporterNameChange,
            label = { Text(stringResource(Res.string.field_reporter_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = form.reporterEmail,
            onValueChange = viewModel::onReporterEmailChange,
            label = { Text(stringResource(Res.string.field_reporter_email)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = form.reporterPhone,
            onValueChange = viewModel::onReporterPhoneChange,
            label = { Text(stringResource(Res.string.field_reporter_phone)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = { viewModel.submit(onSubmitted) },
            enabled = !form.isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.new_issue_submit))
        }
    }
}
