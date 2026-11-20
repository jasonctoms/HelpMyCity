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
 * Status for the address lookup, shown where the person is already looking,
 * right under "Where is it?" on both the report form and the edit form.
 *
 * Null leaves the field's supporting slot empty instead of saving space for a line
 * that usually is not there.
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
