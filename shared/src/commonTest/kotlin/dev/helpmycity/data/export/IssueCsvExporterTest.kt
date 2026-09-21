package dev.helpmycity.data.export

import dev.helpmycity.domain.model.GeoPoint
import dev.helpmycity.domain.model.Issue
import dev.helpmycity.domain.model.IssueCategory
import dev.helpmycity.domain.model.IssueLocation
import dev.helpmycity.domain.model.IssuePriority
import dev.helpmycity.domain.model.IssueReview
import dev.helpmycity.domain.model.IssueStatus
import dev.helpmycity.domain.model.SyncMetadata
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IssueCsvExporterTest {

    private val exporter = IssueCsvExporter()

    private fun issue(title: String, location: String) = Issue(
        id = "issue-1",
        title = title,
        description = "d",
        requestedAction = "",
        category = IssueCategory.DRAINAGE,
        status = IssueStatus.REJECTED,
        priority = IssuePriority.LOW,
        location = IssueLocation(description = location, point = GeoPoint(33.2, -117.3)),
        departmentId = null,
        reporter = null,
        submittedByUserId = "user-1",
        review = IssueReview.rejected(
            reviewerId = "mgr-1",
            reviewerName = "Ana",
            atMillis = 3L,
            reason = "Already reported, see #12",
        ),
        notesSource = "",
        externalReference = null,
        supportCount = 0,
        createdAtMillis = 1L,
        updatedAtMillis = 2L,
        sync = SyncMetadata(),
    )

    @Test
    fun emptyExportStillCarriesTheHeaderRow() {
        val csv = exporter.toCsv(emptyList())

        assertEquals(1, csv.trim().lines().size)
        assertTrue(csv.startsWith("id,title,description"))
    }

    @Test
    fun valuesContainingCommasAndQuotesAreEscaped() {
        // "Calle Solimar & Calle Los Santos" style locations are common in the
        // real data, and a stray comma must not shift every later column.
        val csv = exporter.toCsv(listOf(issue("Flooding, again", """The "big" corner""")))
        val row = csv.trim().lines()[1]

        assertTrue(row.contains("\"Flooding, again\""), row)
        assertTrue(row.contains("\"The \"\"big\"\" corner\""), row)
    }

    @Test
    fun enumsExportAsStableStorageKeys() {
        val row = exporter.toCsv(listOf(issue("t", "l"))).trim().lines()[1]

        assertTrue(row.contains("drainage"), row)
        assertTrue(row.contains("rejected"), row)
        assertTrue(row.contains("low"), row)
    }

    /** An export that dropped the triage decision would lose why a report was turned down. */
    @Test
    fun theReviewDecisionAndItsReasonAreExported() {
        val csv = exporter.toCsv(listOf(issue("t", "l")))
        val row = csv.trim().lines()[1]

        assertTrue(csv.lines().first().contains("review_state,reviewed_by"), csv.lines().first())
        assertTrue(row.contains("rejected"), row)
        assertTrue(row.contains("Ana"), row)
        assertTrue(row.contains("\"Already reported, see #12\""), row)
    }
}
