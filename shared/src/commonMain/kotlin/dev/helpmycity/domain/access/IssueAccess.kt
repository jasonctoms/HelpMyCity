package dev.helpmycity.domain.access

import dev.helpmycity.domain.model.Issue
import dev.helpmycity.domain.model.User
import dev.helpmycity.domain.model.UserRole

/**
 * Who may see and who may act on an issue.
 *
 * One file, pure functions, no framework: the rules are short enough to read in
 * one sitting and they are the same rules a backend will have to enforce later,
 * so they should be quotable. The repository applies them on every read
 * ([dev.helpmycity.data.repository.DefaultIssueRepository]) rather
 * than leaving each screen to remember -- a screen that forgets leaks a report
 * that has not been triaged yet.
 *
 * Nothing here is a security boundary while the app is offline-first and the
 * database sits on the device. It is the *definition* of the boundary; the
 * backend enforces it once there is one.
 */

/**
 * Whether [this] is responsible for triaging [issue]: approving or rejecting it,
 * changing its status, and filing it with the city.
 *
 * Admins manage everything. Managers manage what their
 * [scope][dev.helpmycity.domain.model.ManagerScope] covers.
 */
fun User?.manages(issue: Issue): Boolean = when (this?.role) {
    UserRole.ADMIN -> true
    UserRole.MANAGER -> scope.covers(issue)
    else -> false
}

/**
 * Whether [issue] should appear for [this] at all.
 *
 * Approved issues are public. An issue still in triage -- or turned down -- is
 * visible only to the person who filed it and to the managers who can act on
 * it, so a rejected report reaches its submitter with the reason instead of
 * vanishing.
 */
fun User?.canSee(issue: Issue): Boolean = when {
    issue.review.isPublic -> true
    this == null -> false
    id == issue.submittedByUserId -> true
    else -> manages(issue)
}

/** Whether [this] filed [issue] -- what earns a resident sight of their own report. */
fun User?.submitted(issue: Issue): Boolean =
    this != null && id == issue.submittedByUserId

/** Whether to offer this user a review queue at all. Which issues is [manages]' job. */
val User?.isReviewer: Boolean get() = this?.role?.isReviewer == true
