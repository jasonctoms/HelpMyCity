package dev.helpmycity.domain.model

import kotlinx.serialization.Serializable

/**
 * A field a manager may correct on a filed report.
 *
 * What is absent matters as much: [Issue.reporter] is the submitter's own
 * contact details, [Issue.status] and [Issue.review] have their own audited
 * paths, and [Issue.supportCount] belongs to the residents who pressed the
 * button.
 */
@Serializable
enum class EditableField(val storageKey: String) {
    TITLE("title"),
    DESCRIPTION("description"),
    REQUESTED_ACTION("requested_action"),
    CATEGORY("category"),
    PRIORITY("priority"),
    LOCATION("location"),
    NEIGHBORHOOD("neighborhood"),
    DEPARTMENT("department"),
    NOTES_SOURCE("notes_source"),
    ;

    companion object {
        /** Null for a key this version does not know, so an old row never crashes a list. */
        fun fromStorageKeyOrNull(key: String): EditableField? =
            entries.firstOrNull { it.storageKey == key }
    }
}

/**
 * That an issue has been edited since it was filed, by whom, and what changed.
 *
 * Null until the first edit: an untouched report carries no marker, so the
 * marker means something when it does appear.
 *
 * This is deliberately *not* private to managers. A resident reading their own
 * report -- or anyone reading the public list -- is shown that the text they are
 * reading is not exactly what was submitted, and who changed it. An edit that
 * only the editor can see is how a record stops being trustworthy.
 *
 * [fields] describes the most recent edit only; [revision] counts them all.
 */
@Serializable
data class IssueEdit(
    val editedByUserId: String?,
    val editedByDisplayName: String?,
    val editedAtMillis: Long,
    /** Number of edits so far. 1 for the first. */
    val revision: Int,
    /** What the most recent edit touched. Never empty -- an edit that changed nothing is not recorded. */
    val fields: Set<EditableField>,
)

/**
 * The fields an edit form submits. Kept separate from [Issue] the same way
 * [IssueDraft] is: the screen supplies what a person typed, and the repository
 * owns identity, timestamps, sync state and the audit record.
 */
data class IssueEditDraft(
    val title: String,
    val description: String,
    val requestedAction: String,
    val category: IssueCategory,
    val priority: IssuePriority,
    val locationDescription: String,
    val point: GeoPoint?,
    /** What geocoding made of the location. Derived, so it is not an audited edit. */
    val geocodedAddress: String?,
    val neighborhood: String?,
    val departmentId: String?,
    val notesSource: String,
) {
    companion object {
        /** Pre-fills the form with what the issue says today. */
        fun of(issue: Issue): IssueEditDraft = IssueEditDraft(
            title = issue.title,
            description = issue.description,
            requestedAction = issue.requestedAction,
            category = issue.category,
            priority = issue.priority,
            locationDescription = issue.location.description,
            point = issue.location.point,
            geocodedAddress = issue.location.geocodedAddress,
            neighborhood = issue.location.neighborhood,
            departmentId = issue.departmentId,
            notesSource = issue.notesSource,
        )
    }
}
