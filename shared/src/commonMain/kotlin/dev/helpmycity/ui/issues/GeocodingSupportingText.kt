package dev.helpmycity.ui.issues

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import helpmycity.shared.generated.resources.Res
import helpmycity.shared.generated.resources.geocode_no_match
import helpmycity.shared.generated.resources.geocode_resolved
import helpmycity.shared.generated.resources.geocode_searching
import helpmycity.shared.generated.resources.geocode_unavailable
import org.jetbrains.compose.resources.stringResource

/**
 * The address lookup, reported where the person is already looking -- under
 * "Where is it?", on the report form and the edit form alike.
 *
 * Null leaves the field's supporting slot empty rather than reserving space for
 * a line that is usually absent.
 */
internal fun geocodingSupportingText(status: GeocodingStatus): (@Composable () -> Unit)? =
    when (status) {
        GeocodingStatus.Idle -> null
        GeocodingStatus.Searching -> {
            { Text(stringResource(Res.string.geocode_searching)) }
        }

        is GeocodingStatus.Resolved -> {
            { Text(stringResource(Res.string.geocode_resolved, status.address)) }
        }

        GeocodingStatus.NoMatch -> {
            { Text(stringResource(Res.string.geocode_no_match)) }
        }

        GeocodingStatus.Unavailable -> {
            { Text(stringResource(Res.string.geocode_unavailable)) }
        }
    }
