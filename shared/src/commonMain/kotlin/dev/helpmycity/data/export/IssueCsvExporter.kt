package dev.helpmycity.data.export

import dev.helpmycity.domain.model.Issue
import org.koin.core.annotation.Single

/**
 * CSV export of the full issue table, so a deployment is never locked in.
 *
 * Reads the local database, which after a sync holds every issue the user can
 * see, and needs no backend. Writing the string to a file or a download is
 * platform work; this produces the bytes.
 */
@Single
class IssueCsvExporter {

    fun toCsv(issues: List<Issue>): String = buildString {
        appendLine(HEADERS.joinToString(","))
        issues.forEach { issue ->
            appendLine(
                listOf(
                    issue.id,
                    issue.title,
                    issue.description,
                    issue.requestedAction,
                    issue.category.storageKey,
                    issue.status.storageKey,
                    issue.resolution.orEmpty(),
                    issue.priority.storageKey,
                    issue.location.description,
                    issue.location.point?.latitude?.toString().orEmpty(),
                    issue.location.point?.longitude?.toString().orEmpty(),
                    issue.location.geocodedAddress.orEmpty(),
                    issue.location.neighborhood.orEmpty(),
                    issue.location.councilDistrict.orEmpty(),
                    issue.location.censusTract.orEmpty(),
                    issue.location.policePrecinct.orEmpty(),
                    issue.departmentId.orEmpty(),
                    issue.reporter?.name.orEmpty(),
                    issue.reporter?.email.orEmpty(),
                    issue.reporter?.phone.orEmpty(),
                    issue.submittedByUserId.orEmpty(),
                    issue.review.state.storageKey,
                    issue.review.reviewedByDisplayName.orEmpty(),
                    issue.review.reviewedAtMillis?.toString().orEmpty(),
                    issue.review.rejectionReason.orEmpty(),
                    issue.lastEdit?.editedByDisplayName.orEmpty(),
                    issue.lastEdit?.editedAtMillis?.toString().orEmpty(),
                    issue.lastEdit?.revision?.toString().orEmpty(),
                    issue.lastEdit?.fields?.joinToString(" ") { it.storageKey }.orEmpty(),
                    issue.notesSource,
                    issue.externalReference?.referenceNumber.orEmpty(),
                    issue.supportCount.toString(),
                    issue.createdAtMillis.toString(),
                    issue.updatedAtMillis.toString(),
                ).joinToString(",", transform = ::escape)
            )
        }
    }

    /** RFC 4180: quote when the value contains a comma, quote or newline; double any inner quote. */
    private fun escape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }

    private companion object {
        val HEADERS = listOf(
            "id", "title", "description", "requested_action", "category", "status",
            "resolution", "priority", "location_description", "latitude", "longitude",
            "geocoded_address", "neighborhood",
            "council_district", "census_tract", "police_precinct", "department_id",
            "reporter_name", "reporter_email", "reporter_phone", "submitted_by_user_id",
            "review_state", "reviewed_by", "reviewed_at", "rejection_reason",
            "edited_by", "edited_at", "edit_revision", "edited_fields", "notes_source",
            "external_reference", "support_count", "created_at", "updated_at",
        )
    }
}
