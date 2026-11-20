package dev.helpmycity.domain.util

/**
 * This only checks the shape of an address, and has no idea if mail would actually
 * get delivered. It is enough to tell someone they forgot the `@` before they
 * submit, and that's about it.
 *
 * The mock sign-in and the profile form both use it so they agree on what they
 * accept. An address that works for signing in should not get refused by the
 * profile screen, or the other way around.
 */
fun String.looksLikeEmail(): Boolean {
    val at = indexOf('@')
    return at > 0 && indexOf('.', startIndex = at) > at + 1 && !endsWith('.')
}
