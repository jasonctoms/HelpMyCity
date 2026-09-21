package dev.helpmycity.domain.util

/**
 * A shape check, not a delivery check: enough to tell someone their address is
 * missing an `@` before they submit, and nothing more.
 *
 * Shared by the mock sign-in and the profile form so the two agree on what they
 * accept -- an address that signs in should not be refused by the profile
 * screen, or the other way round.
 */
fun String.looksLikeEmail(): Boolean {
    val at = indexOf('@')
    return at > 0 && indexOf('.', startIndex = at) > at + 1 && !endsWith('.')
}
