package dev.helpmycity.domain.access

import dev.helpmycity.domain.model.Issue
import dev.helpmycity.domain.model.User
import dev.helpmycity.domain.model.UserRole

/**
 * Who is allowed to see an issue, and who is allowed to act on it.
 *
 * This is one file of pure functions with no framework. The rules are short
 * enough to read in one sitting, and they are the same rules a backend will have
 * to enforce, so they should be easy to copy. The repository applies them on
 * every read ([dev.helpmycity.data.repository.DefaultIssueRepository]) instead of
 * leaving it up to each screen to remember, because a screen that forgets would
 * leak a report that has not been triaged yet.
 *
 * None of this is a security boundary while the app is offline-first and the
 * database lives on the device. It is the _definition_ of the boundary, and the
 * backend enforces it once there is one.
 */

/**
 * Whether [this] is responsible for triaging [issue], meaning approving or
 * rejecting it, changing its status, and filing it with the city.
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
 * Approved issues are public. An issue that is still in triage (or got turned
 * down) is only visible to the person who filed it and the managers who can act on
 * it. That way a rejected report gets back to its submitter with the reason,
 * instead of just disappearing.
 */
fun User?.canSee(issue: Issue): Boolean = when {
    issue.review.isPublic -> true
    this == null -> false
    id == issue.submittedByUserId -> true
    else -> manages(issue)
}

/** Whether [this] filed [issue], which is what lets a resident see their own report. */
fun User?.submitted(issue: Issue): Boolean =
    this != null && id == issue.submittedByUserId

/** Whether to offer this user a review queue at all. Deciding which issues go in it is [manages]' job. */
val User?.isReviewer: Boolean get() = this?.role?.isReviewer == true
