package dev.helpmycity.domain.model

/**
 * What the submission form collects. Kept separate from [Issue] so the form
 * never has to invent an id, timestamps or sync state -- the repository owns those.
 */
data class IssueDraft(
    val title: String,
    val description: String,
    val requestedAction: String = "",
    val category: IssueCategory = IssueCategory.OTHER,
    val priority: IssuePriority = IssuePriority.MEDIUM,
    val locationDescription: String,
    val point: GeoPoint? = null,
    /** What geocoding made of [locationDescription] or [point], when it ran. */
    val geocodedAddress: String? = null,
    val neighborhood: String? = null,
    val departmentId: String? = null,
    val reporter: ReporterContact? = null,
    val notesSource: String = "",
)

/** Query shape for the list, board and map screens. */
data class IssueFilter(
    val statuses: Set<IssueStatus> = emptySet(),
    val categories: Set<IssueCategory> = emptySet(),
    val priorities: Set<IssuePriority> = emptySet(),
    val neighborhoods: Set<String> = emptySet(),
    val searchQuery: String = "",
) {
    val isEmpty: Boolean
        get() = statuses.isEmpty() &&
            categories.isEmpty() &&
            priorities.isEmpty() &&
            neighborhoods.isEmpty() &&
            searchQuery.isBlank()

    fun matches(issue: Issue): Boolean {
        if (statuses.isNotEmpty() && issue.status !in statuses) return false
        if (categories.isNotEmpty() && issue.category !in categories) return false
        if (priorities.isNotEmpty() && issue.priority !in priorities) return false
        if (neighborhoods.isNotEmpty() && issue.location.neighborhood !in neighborhoods) return false
        if (searchQuery.isNotBlank()) {
            val needle = searchQuery.trim()
            val haystack = listOfNotNull(
                issue.title,
                issue.description,
                issue.requestedAction,
                issue.location.description,
                issue.location.neighborhood,
                issue.externalReference?.referenceNumber,
            )
            if (haystack.none { it.contains(needle, ignoreCase = true) }) return false
        }
        return true
    }

    companion object {
        val None: IssueFilter = IssueFilter()
    }
}
