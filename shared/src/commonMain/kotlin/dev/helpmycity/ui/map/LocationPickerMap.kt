package dev.helpmycity.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.helpmycity.deployment.MapSettings
import dev.helpmycity.domain.model.GeoPoint
import dev.helpmycity.ui.formatCoordinate
import dev.helpmycity.ui.theme.markerColorFor
import dev.helpmycity.domain.model.IssueStatus
import helpmycity.shared.generated.resources.Res
import helpmycity.shared.generated.resources.location_pin_approximate
import helpmycity.shared.generated.resources.location_pin_clear
import helpmycity.shared.generated.resources.location_pin_coordinates
import helpmycity.shared.generated.resources.location_pin_hint
import kotlinx.serialization.json.JsonObject
import org.jetbrains.compose.resources.stringResource
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.interaction.MapInteractions
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position

private const val PICKED_LAYER_ID = "picked-location"

/** Tall enough to place a pin on a street, short enough to leave the form usable. */
private val PICKER_HEIGHT = 240.dp

/**
 * Tap-to-place location picker: how an issue gets a point out of the
 * descriptions residents give ("Libby Lake Park frontage").
 *
 * Tap rather than a center crosshair -- one gesture, it reads to a screen reader
 * as "set location", and it records nothing for someone who only panned the map
 * to look around.
 *
 * [isApproximate] marks a pin that geocoding placed from the address rather than
 * a finger, so the hint invites a correction instead of implying precision.
 */
@Composable
fun LocationPickerMap(
    point: GeoPoint?,
    settings: MapSettings,
    onPointChange: (GeoPoint?) -> Unit,
    modifier: Modifier = Modifier,
    isApproximate: Boolean = false,
) {
    val mapState = rememberMapState(
        baseStyle = BaseStyle.Uri(settings.styleUrl),
        initialCameraPosition = CameraPosition(
            target = (point ?: settings.center)?.toPickerPosition() ?: Position(0.0, 0.0),
            zoom = if (point != null || settings.center != null) {
                settings.defaultZoom + PIN_ZOOM_BOOST
            } else {
                1.0
            },
        ),
    ) {
        val features = if (point == null) {
            FeatureCollection<Point, JsonObject>()
        } else {
            FeatureCollection(
                listOf(Feature(Point(point.toPickerPosition()), JsonObject(emptyMap())))
            )
        }
        val source = rememberGeoJsonSource(data = GeoJsonData.Features(features))
        CircleLayer(
            id = PICKED_LAYER_ID,
            source = source,
            color = const(markerColorFor(IssueStatus.SUBMITTED)),
            radius = const(9.dp),
            strokeWidth = const(3.dp),
            strokeColor = const(Color.White),
        )
    }

    // Held across recomposition so a new callback identity does not rebuild the
    // interaction config, which would cancel a gesture in progress.
    val currentOnPointChange by rememberUpdatedState(onPointChange)
    val interactions = remember {
        MapInteractions {
            callbacks {
                click {
                    onEvent { event ->
                        val position = event.position ?: return@onEvent ClickResult.Pass
                        currentOnPointChange(GeoPoint(position.latitude, position.longitude))
                        ClickResult.Consume
                    }
                }
            }
        }
    }

    // A geocoded pin can land well outside the current view, so follow it. A pin
    // the resident tapped is already where they are looking, and moving there
    // would yank the map out from under them.
    LaunchedEffect(point, isApproximate) {
        if (point == null || !isApproximate) return@LaunchedEffect
        mapState.animateCameraPosition(
            CameraPosition(
                target = point.toPickerPosition(),
                zoom = settings.defaultZoom + PIN_ZOOM_BOOST,
            )
        )
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        MaplibreMap(
            modifier = Modifier
                .fillMaxWidth()
                .height(PICKER_HEIGHT)
                .clip(RoundedCornerShape(12.dp)),
            state = mapState,
            interactions = interactions,
        )

        if (point == null) {
            Text(
                text = stringResource(Res.string.location_pin_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = stringResource(
                    Res.string.location_pin_coordinates,
                    formatCoordinate(point.latitude),
                    formatCoordinate(point.longitude),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (isApproximate) {
                Text(
                    text = stringResource(Res.string.location_pin_approximate),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { onPointChange(null) }) {
                Text(stringResource(Res.string.location_pin_clear))
            }
        }
    }
}

/** A pin is placed on a street, so open closer in than the overview map does. */
private const val PIN_ZOOM_BOOST = 2.0

private fun GeoPoint.toPickerPosition(): Position =
    Position(longitude = longitude, latitude = latitude)

