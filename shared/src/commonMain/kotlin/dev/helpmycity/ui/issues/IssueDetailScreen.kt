package dev.helpmycity.ui.issues

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.helpmycity.domain.model.Issue
import dev.helpmycity.domain.model.IssueEdit
import dev.helpmycity.domain.model.IssueStatus
import dev.helpmycity.domain.model.IssueStatusChange
import dev.helpmycity.domain.model.ReviewState
import dev.helpmycity.domain.repository.ReviewOutcome
import dev.helpmycity.ui.components.CategoryChip
import dev.helpmycity.ui.components.EditedChip
import dev.helpmycity.ui.components.PriorityChip
import dev.helpmycity.ui.components.IssuePhotoStrip
import dev.helpmycity.ui.components.StatusField
import dev.helpmycity.ui.formatCoordinate
import dev.helpmycity.ui.formatTimestamp
import dev.helpmycity.ui.label
import helpmycity.shared.generated.resources.Res
import helpmycity.shared.generated.resources.common_cancel
import helpmycity.shared.generated.resources.common_unassigned
import helpmycity.shared.generated.resources.detail_change_status
import helpmycity.shared.generated.resources.detail_coordinates
import helpmycity.shared.generated.resources.detail_edit
import helpmycity.shared.generated.resources.detail_edited_by
import helpmycity.shared.generated.resources.detail_edited_fields
import helpmycity.shared.generated.resources.detail_edited_times
import helpmycity.shared.generated.resources.detail_department
import helpmycity.shared.generated.resources.detail_history
import helpmycity.shared.generated.resources.detail_history_created
import helpmycity.shared.generated.resources.detail_history_entry
import helpmycity.shared.generated.resources.detail_location
import helpmycity.shared.generated.resources.detail_not_found
import helpmycity.shared.generated.resources.detail_notes_source
import helpmycity.shared.generated.resources.detail_reported_by
import helpmycity.shared.generated.resources.detail_reported_on
import helpmycity.shared.generated.resources.detail_requested_action
import helpmycity.shared.generated.resources.detail_support
import helpmycity.shared.generated.resources.detail_supported
import helpmycity.shared.generated.resources.detail_support_count
import helpmycity.shared.generated.resources.ic_star
import helpmycity.shared.generated.resources.external_last_checked
import helpmycity.shared.generated.resources.external_manual_hint
import helpmycity.shared.generated.resources.external_none
import helpmycity.shared.generated.resources.external_record
import helpmycity.shared.generated.resources.external_reference
import helpmycity.shared.generated.resources.external_submitted_on
import helpmycity.shared.generated.resources.external_title
import helpmycity.shared.generated.resources.photo_section
import helpmycity.shared.generated.resources.resolution_confirm
import helpmycity.shared.generated.resources.resolution_edit
import helpmycity.shared.generated.resources.resolution_hint
import helpmycity.shared.generated.resources.resolution_label
import helpmycity.shared.generated.resources.resolution_required
import helpmycity.shared.generated.resources.resolution_title
import helpmycity.shared.generated.resources.review_approve
import helpmycity.shared.generated.resources.review_approved_note
import helpmycity.shared.generated.resources.review_issue_gone
import helpmycity.shared.generated.resources.review_not_permitted
import helpmycity.shared.generated.resources.review_pending_manager
import helpmycity.shared.generated.resources.review_pending_submitter
import helpmycity.shared.generated.resources.review_reject
import helpmycity.shared.generated.resources.review_reject_confirm
import helpmycity.shared.generated.resources.review_reject_reason_hint
import helpmycity.shared.generated.resources.review_reject_reason_label
import helpmycity.shared.generated.resources.review_reject_reason_required
import helpmycity.shared.generated.resources.review_rejected_reason
import helpmycity.shared.generated.resources.review_rejected_submitter
import helpmycity.shared.generated.resources.review_rejected_title
import helpmycity.shared.generated.resources.review_reviewed_by
import helpmycity.shared.generated.resources.review_section_title
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IssueDetailScreen(
    viewModel: IssueDetailViewModel,
    onEditClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val issue = state.issue

    if (issue == null) {
        Text(
            text = stringResource(Res.string.detail_not_found),
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
        StatusField(issue.status)
        Text(
            text = issue.title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PriorityChip(issue.priority)
            CategoryChip(issue.category)
            if (issue.lastEdit != null) EditedChip()
        }

        ReviewSection(state = state, viewModel = viewModel)
        if (issue.status == IssueStatus.COMPLETE) {
            issue.resolution?.let { ResolutionCard(it) }
        }
        Text(
            text = stringResource(
                Res.string.detail_reported_on,
                formatTimestamp(issue.createdAtMillis),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        issue.lastEdit?.let { EditedNote(it) }

        Text(text = issue.description, style = MaterialTheme.typography.bodyLarge)

        if (issue.requestedAction.isNotBlank()) {
            Section(title = stringResource(Res.string.detail_requested_action)) {
                Text(issue.requestedAction, style = MaterialTheme.typography.bodyMedium)
            }
        }

        Section(title = stringResource(Res.string.detail_location)) {
            Text(issue.location.description, style = MaterialTheme.typography.bodyMedium)
            state.neighborhoodName?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            issue.location.point?.let { point ->
                Text(
                    text = stringResource(
                        Res.string.detail_coordinates,
                        formatCoordinate(point.latitude),
                        formatCoordinate(point.longitude),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Section(title = stringResource(Res.string.detail_department)) {
            Text(
                text = state.department?.name ?: stringResource(Res.string.common_unassigned),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        issue.reporter?.let { reporter ->
            Section(title = stringResource(Res.string.detail_reported_by)) {
                listOfNotNull(reporter.name, reporter.email, reporter.phone).forEach {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        if (issue.notesSource.isNotBlank()) {
            Section(title = stringResource(Res.string.detail_notes_source)) {
                Text(issue.notesSource, style = MaterialTheme.typography.bodyMedium)
            }
        }

        SupportButton(
            count = issue.supportCount,
            supported = state.hasSupported,
            enabled = state.canSupport && !state.hasSupported,
            onClick = viewModel::addSupport,
        )

        if (state.canReview) {
            HorizontalDivider()
            OutlinedButton(onClick = { onEditClick(issue.id) }) {
                Text(stringResource(Res.string.detail_edit))
            }
            if (issue.review.isPublic) {
                StatusSection(issue = issue, viewModel = viewModel)
            }
            ExternalRequestSection(issue = issue, viewModel = viewModel)
        }

        if (state.photos.isNotEmpty()) {
            Section(title = stringResource(Res.string.photo_section)) {
                IssuePhotoStrip(photos = state.photos)
            }
        }

        HorizontalDivider()
        Section(title = stringResource(Res.string.detail_history)) {
            state.history.forEach { entry -> HistoryRow(entry) }
        }
    }
}

@Composable
private fun ResolutionCard(resolution: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(Res.string.resolution_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.semantics { heading() },
            )
            Text(resolution, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusSection(issue: Issue, viewModel: IssueDetailViewModel) {
    val completion by viewModel.completion.collectAsStateWithLifecycle()
    Section(title = stringResource(Res.string.detail_change_status)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IssueStatus.workflow.forEach { status ->
                FilterChip(
                    selected = status == issue.status,
                    onClick = { viewModel.changeStatus(status) },
                    label = { Text(status.label()) },
                )
            }
        }
        if (completion.isOpen) {
            CompletionForm(completion = completion, viewModel = viewModel)
        } else if (issue.status == IssueStatus.COMPLETE) {
            TextButton(onClick = viewModel::onEditResolution) {
                Text(stringResource(Res.string.resolution_edit))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompletionForm(completion: CompletionState, viewModel: IssueDetailViewModel) {
    OutlinedTextField(
        value = completion.resolution,
        onValueChange = viewModel::onResolutionChange,
        label = { Text(stringResource(Res.string.resolution_label)) },
        supportingText = {
            Text(
                stringResource(
                    if (completion.showResolutionRequired) {
                        Res.string.resolution_required
                    } else {
                        Res.string.resolution_hint
                    }
                )
            )
        },
        isError = completion.showResolutionRequired,
        minLines = 2,
        modifier = Modifier.fillMaxWidth(),
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = viewModel::confirmCompletion, enabled = completion.canSubmit) {
            Text(stringResource(Res.string.resolution_confirm))
        }
        TextButton(onClick = viewModel::onCompletionCancel) {
            Text(stringResource(Res.string.common_cancel))
        }
    }
}

/** Who last changed this report's text, and what they changed. Shown to everyone. */
@Composable
private fun EditedNote(edit: IssueEdit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = stringResource(
                Res.string.detail_edited_by,
                edit.editedByDisplayName.orEmpty(),
                formatTimestamp(edit.editedAtMillis, includeTime = true),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // `map` is inline, so the composable label lookup is legal inside it;
        // joinToString's transform would not be.
        val changed = edit.fields.sortedBy { it.ordinal }.map { it.label() }
        Text(
            text = stringResource(Res.string.detail_edited_fields, changed.joinToString(", ")),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Only worth saying beyond the edit named above.
        if (edit.revision > 1) {
            Text(
                text = pluralStringResource(
                    Res.plurals.detail_edited_times,
                    edit.revision,
                    edit.revision,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Triage, from both sides: a manager gets the decision, a submitter gets to know
 * where their report stands and why. Anyone else sees nothing here.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewSection(state: IssueDetailUiState, viewModel: IssueDetailViewModel) {
    val issue = state.issue ?: return
    val review = issue.review
    if (review.isPublic && !state.canReview) return

    val rejection by viewModel.rejection.collectAsStateWithLifecycle()
    val reviewError by viewModel.reviewError.collectAsStateWithLifecycle()
    val isRejected = review.state == ReviewState.REJECTED

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isRejected) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(
                    if (isRejected) Res.string.review_rejected_title else Res.string.review_section_title
                ),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.semantics { heading() },
            )

            when {
                isRejected -> {
                    if (state.isSubmitter) {
                        Text(
                            text = stringResource(Res.string.review_rejected_submitter),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    review.rejectionReason?.let {
                        Text(
                            text = stringResource(Res.string.review_rejected_reason, it),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                review.isPending -> Text(
                    text = stringResource(
                        if (state.canReview) {
                            Res.string.review_pending_manager
                        } else {
                            Res.string.review_pending_submitter
                        }
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )

                else -> Text(
                    text = stringResource(Res.string.review_approved_note),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            review.reviewedAtMillis?.let { at ->
                Text(
                    text = stringResource(
                        Res.string.review_reviewed_by,
                        review.reviewedByDisplayName.orEmpty(),
                        formatTimestamp(at, includeTime = true),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (!state.canReview) return@Column

            if (rejection.isOpen) {
                RejectionForm(rejection = rejection, viewModel = viewModel)
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Approving is also how a manager reverses their own
                    // rejection, so it stays available in every state but one.
                    if (!review.isPublic) {
                        Button(onClick = viewModel::approve) {
                            Text(stringResource(Res.string.review_approve))
                        }
                    }
                    if (!isRejected) {
                        OutlinedButton(onClick = viewModel::onRejectClick) {
                            Text(stringResource(Res.string.review_reject))
                        }
                    }
                }
            }

            reviewError?.let { outcome ->
                Text(
                    text = stringResource(outcome.messageResource()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/**
 * The reason box. `Send rejection` stays disabled until something is typed --
 * the rule is enforced in the repository, and this is how it reads on screen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RejectionForm(rejection: RejectionState, viewModel: IssueDetailViewModel) {
    OutlinedTextField(
        value = rejection.reason,
        onValueChange = viewModel::onRejectReasonChange,
        label = { Text(stringResource(Res.string.review_reject_reason_label)) },
        supportingText = {
            Text(
                stringResource(
                    if (rejection.showReasonRequired) {
                        Res.string.review_reject_reason_required
                    } else {
                        Res.string.review_reject_reason_hint
                    }
                )
            )
        },
        isError = rejection.showReasonRequired,
        minLines = 2,
        modifier = Modifier.fillMaxWidth(),
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = viewModel::confirmRejection,
            enabled = rejection.canSubmit,
        ) {
            Text(stringResource(Res.string.review_reject_confirm))
        }
        TextButton(onClick = viewModel::onRejectCancel) {
            Text(stringResource(Res.string.common_cancel))
        }
    }
}

private fun ReviewOutcome.messageResource() = when (this) {
    ReviewOutcome.NotPermitted -> Res.string.review_not_permitted
    ReviewOutcome.IssueNotFound -> Res.string.review_issue_gone
    ReviewOutcome.ReasonRequired -> Res.string.review_reject_reason_required
    ReviewOutcome.Recorded -> Res.string.review_approved_note
}

/**
 * Hand-off to the city's own request system. Hidden entirely when the
 * deployment has no such system -- see `CityProfile.externalRequestSystem`.
 */
@Composable
private fun ExternalRequestSection(issue: Issue, viewModel: IssueDetailViewModel) {
    val systemName = viewModel.externalSystemName ?: return
    var reference by remember(issue.id) { mutableStateOf("") }

    Section(title = stringResource(Res.string.external_title, systemName)) {
        val external = issue.externalReference
        if (external?.referenceNumber == null) {
            Text(
                text = stringResource(Res.string.external_none),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(Res.string.external_manual_hint, systemName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Most city portals have no API, so the manager files there and
            // pastes the reference back here. See ManualExternalRequestGateway.
            viewModel.externalSubmissionUrl(issue)?.let { url ->
                Text(
                    text = url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            OutlinedTextField(
                value = reference,
                onValueChange = { reference = it },
                label = { Text(stringResource(Res.string.external_reference)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(
                onClick = {
                    viewModel.recordExternalReference(reference)
                    reference = ""
                },
                enabled = reference.isNotBlank(),
            ) {
                Text(stringResource(Res.string.external_record))
            }
        } else {
            Text(external.referenceNumber, style = MaterialTheme.typography.bodyMedium)
            external.submittedAtMillis?.let {
                Text(
                    text = stringResource(Res.string.external_submitted_on, formatTimestamp(it)),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            external.lastCheckedAtMillis?.let {
                Text(
                    text = stringResource(Res.string.external_last_checked, formatTimestamp(it)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HistoryRow(entry: IssueStatusChange) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = if (entry.fromStatus == null) {
                    stringResource(Res.string.detail_history_created)
                } else {
                    stringResource(
                        Res.string.detail_history_entry,
                        entry.fromStatus.label(),
                        entry.toStatus.label(),
                    )
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            entry.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Text(
                text = listOfNotNull(
                    formatTimestamp(entry.changedAtMillis, includeTime = true),
                    entry.changedByDisplayName,
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Starring an issue: the action and its tally in one control, matching the
 * cards.
 *
 * The count shows even at zero -- "nobody else yet" is an answer, and a control
 * that changes shape on its first press is a worse one. A screen reader cannot
 * read a star, so it gets the action spelled out.
 */
@Composable
private fun SupportButton(count: Int, supported: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val action = stringResource(if (supported) Res.string.detail_supported else Res.string.detail_support)
    val tally = pluralStringResource(Res.plurals.detail_support_count, count, count)
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        // Disabled would grey out the one state worth showing off, so a starred
        // button keeps the accent colour.
        colors = if (supported) {
            ButtonDefaults.outlinedButtonColors(
                disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                disabledContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        } else {
            ButtonDefaults.outlinedButtonColors()
        },
        modifier = Modifier.semantics { contentDescription = "$action. $tally" },
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_star),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(count.toString(), style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { heading() },
        )
        content()
    }
}
