package dev.helpmycity.data.remote.external

import dev.helpmycity.domain.model.ExternalReference
import dev.helpmycity.domain.model.Issue

/**
 * Hand-off to whatever request-a-service system the city already runs.
 *
 * Most cities have one, almost none expose an API for it, and their
 * follow-through is uneven -- so this app stays the system of record and the
 * city's queue is treated as a downstream destination we push to and poll. The
 * common case is therefore *manual*: a manager files the request on the city's
 * site and records the reference number here. [ManualExternalRequestGateway]
 * covers exactly that, and an API-backed implementation drops in behind the same
 * interface for a city that offers one.
 *
 * See `:cityConfig` for a configured instance.
 */
interface ExternalRequestGateway {

    /**
     * How the city's system is named in the UI, or null when the deployment has
     * no external system -- in which case the hand-off section is hidden.
     */
    val systemDisplayName: String?

    /** Stable key persisted on [ExternalReference.system]. Never a display string. */
    val systemId: String

    /** True when submissions can be pushed programmatically rather than by hand. */
    val supportsAutomatedSubmission: Boolean

    /** The URL a manager should open to file this issue with the city. */
    fun submissionUrl(issue: Issue): String?

    /** Records the reference number a manager copied back from the city's system. */
    suspend fun recordManualSubmission(
        issueId: String,
        referenceNumber: String,
        submittedAtMillis: Long,
    ): ExternalReference

    /** Re-checks a city-side status. Manual by default: the manager types it in. */
    suspend fun refreshStatus(reference: ExternalReference): ExternalReference
}

/**
 * Manual hand-off to a city system reachable at a fixed URL.
 *
 * Enough for any city whose request portal is "a web form a human fills in",
 * which is most of them, so a fork usually configures this rather than writing
 * its own gateway.
 */
open class ManualExternalRequestGateway(
    override val systemId: String,
    override val systemDisplayName: String,
    private val requestUrl: String,
) : ExternalRequestGateway {

    override val supportsAutomatedSubmission: Boolean = false

    override fun submissionUrl(issue: Issue): String = requestUrl

    override suspend fun recordManualSubmission(
        issueId: String,
        referenceNumber: String,
        submittedAtMillis: Long,
    ): ExternalReference = ExternalReference(
        system = systemId,
        referenceNumber = referenceNumber,
        submittedAtMillis = submittedAtMillis,
        lastCheckedAtMillis = submittedAtMillis,
    )

    override suspend fun refreshStatus(reference: ExternalReference): ExternalReference = reference
}

/** For a deployment with no city system to hand off to. The UI hides the section. */
object NoExternalRequestGateway : ExternalRequestGateway {
    override val systemDisplayName: String? = null
    override val systemId: String = "none"
    override val supportsAutomatedSubmission: Boolean = false

    override fun submissionUrl(issue: Issue): String? = null

    override suspend fun recordManualSubmission(
        issueId: String,
        referenceNumber: String,
        submittedAtMillis: Long,
    ): ExternalReference = ExternalReference(
        system = systemId,
        referenceNumber = referenceNumber,
        submittedAtMillis = submittedAtMillis,
        lastCheckedAtMillis = submittedAtMillis,
    )

    override suspend fun refreshStatus(reference: ExternalReference): ExternalReference = reference
}
