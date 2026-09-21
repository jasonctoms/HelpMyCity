package dev.helpmycity.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.helpmycity.deployment.MapSettings
import dev.helpmycity.domain.model.GeoPoint
import dev.helpmycity.domain.model.Issue
import dev.helpmycity.domain.model.IssueStatus
import dev.helpmycity.domain.model.Neighborhood
import dev.helpmycity.ui.label
import dev.helpmycity.ui.theme.markerColorFor
import helpmycity.shared.generated.resources.Res
import helpmycity.shared.generated.resources.map_filter_all_neighborhoods
import helpmycity.shared.generated.resources.map_legend_title
import helpmycity.shared.generated.resources.map_located_count
import helpmycity.shared.generated.resources.map_located_count_filtered
import helpmycity.shared.generated.resources.map_no_matching_issues
import helpmycity.shared.generated.resources.map_no_located_issues
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.expressions.dsl.asString
import org.maplibre.compose.expressions.dsl.case
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.switch
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.BoundingBox
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position

/** The property every marker carries, so a tap resolves back to a row. */
private const val PROP_ISSUE_ID = "issueId"

/** Drives the per-status marker color; holds an [IssueStatus.storageKey]. */
private const val PROP_STATUS = "status"

private const val MARKER_LAYER_ID = "issue-markers"

/** Zoomed all the way out, for a deployment with no center and no located issues. */
private const val WORLD_ZOOM = 1.0

private val FRAMING_PADDING = PaddingValues(48.dp)

/** Roughly the width of the Scaffold's extended FAB, which floats over the legend. */
private val FAB_INSET = 180.dp

/**
 * Every located issue, plotted.
 *
 * Issues go to MapLibre as one GeoJSON source rather than as per-issue
 * composables, so redrawing after a sync is a single `setData` however many
 * rows moved, and color comes from a style expression over the feature's status
 * property so the renderer recolors without recomposition.
 *
 * Rows with no point are reported as a count rather than hidden: an empty map
 * and a map of a city with no reports look identical otherwise.
 *
 * @param clearsFloatingActionButton whether the legend has to stay out from
 *   under the Scaffold's report button. False where there is none -- the wide
 *   layout moves that action into the header. See
 *   [dev.helpmycity.ui.issues.IssueWorkspaceScreen].
 */
@Composable
fun IssueMapScreen(
    viewModel: IssueMapViewModel,
    onIssueClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    clearsFloatingActionButton: Boolean = true,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val total by viewModel.totalIssues.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        if (state.neighborhoods.isNotEmpty()) {
            NeighborhoodFilterRow(
                neighborhoods = state.neighborhoods,
                selected = state.selectedNeighborhoods,
                onToggle = viewModel::toggleNeighborhood,
                onClear = viewModel::clearNeighborhoods,
            )
        }
        IssueMap(
            issues = state.plotted,
            settings = viewModel.settings,
            // Changing the filter is a request to look somewhere else, so it
            // re-frames; a background sync is not, so it does not.
            framingKey = state.selectedNeighborhoods,
            onIssueClick = onIssueClick,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        MapLegend(
            plottedCount = state.plotted.size,
            matchingCount = state.matchingCount,
            totalCount = total,
            isFiltered = state.selectedNeighborhoods.isNotEmpty(),
            clearsFloatingActionButton = clearsFloatingActionButton,
        )
    }
}

/**
 * The map's neighborhood filter.
 *
 * Multi-select with nothing-means-everything, so "show me the whole city" stays
 * one tap away. Neighborhoods come from the city profile via the repository, so
 * this composable never names one.
 */
@Composable
private fun NeighborhoodFilterRow(
    neighborhoods: List<Neighborhood>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = selected.isEmpty(),
            onClick = onClear,
            label = { Text(stringResource(Res.string.map_filter_all_neighborhoods)) },
        )
        neighborhoods.forEach { neighborhood ->
            FilterChip(
                selected = neighborhood.id in selected,
                onClick = { onToggle(neighborhood.id) },
                label = { Text(neighborhood.name) },
            )
        }
    }
}

@Composable
private fun IssueMap(
    issues: List<Issue>,
    settings: MapSettings,
    framingKey: Any,
    onIssueClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val features = remember(issues) { issues.toFeatureCollection() }
    val markerColor = remember {
        switch(
            feature[PROP_STATUS].asString(),
            *IssueStatus.entries
                .map { case(it.storageKey, const(markerColorFor(it))) }
                .toTypedArray(),
            fallback = const(markerColorFor(IssueStatus.SUBMITTED)),
        )
    }

    val mapState = rememberMapState(
        baseStyle = BaseStyle.Uri(settings.styleUrl),
        initialCameraPosition = CameraPosition(
            target = settings.center?.toPosition() ?: Position(0.0, 0.0),
            zoom = if (settings.center != null) settings.defaultZoom else WORLD_ZOOM,
        ),
    ) {
        val source = rememberGeoJsonSource(data = GeoJsonData.Features(features))
        CircleLayer(
            id = MARKER_LAYER_ID,
            source = source,
            color = markerColor,
            radius = const(8.dp),
            strokeWidth = const(2.dp),
            strokeColor = const(Color.White),
            onClick = { clicked ->
                val issueId = clicked.firstNotNullOfOrNull { it.issueId() }
                if (issueId == null) {
                    ClickResult.Pass
                } else {
                    onIssueClick(issueId)
                    ClickResult.Consume
                }
            },
        )
    }

    // Frame the issues once they arrive, then leave the camera alone until
    // [framingKey] changes: re-framing on every sync would yank the map away
    // from whoever is panning it.
    var hasFramed by remember(framingKey) { mutableStateOf(false) }
    LaunchedEffect(issues, framingKey) {
        if (hasFramed) return@LaunchedEffect
        val box = issues.boundingBox() ?: return@LaunchedEffect
        hasFramed = true
        mapState.fitCameraToBounds(boundingBox = box, padding = FRAMING_PADDING)
    }

    MaplibreMap(modifier = modifier, state = mapState)
}

/**
 * How many issues made it onto the map, and what the marker colors mean.
 *
 * The markers differ only by color, which is not a signal every resident can
 * use, so the same information is spelled out in text -- the rule the chip
 * colors in `ui/theme/StatusColors.kt` follow too.
 */
@Composable
private fun MapLegend(
    plottedCount: Int,
    matchingCount: Int,
    totalCount: Int,
    isFiltered: Boolean,
    clearsFloatingActionButton: Boolean,
) {
    Surface(tonalElevation = 2.dp) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = when {
                    plottedCount == 0 && isFiltered ->
                        stringResource(Res.string.map_no_matching_issues)

                    plottedCount == 0 -> stringResource(Res.string.map_no_located_issues)

                    // Filtered, the denominator that matters is the selection,
                    // not the city: "2 of 3 in Libby Lake", not "2 of 12".
                    // Quantified on the denominator, which is what names
                    // the noun: "1 of 1 issue", "1 of 12 issues".
                    isFiltered -> pluralStringResource(
                        Res.plurals.map_located_count_filtered,
                        matchingCount,
                        plottedCount,
                        matchingCount,
                    )

                    else -> pluralStringResource(
                        Res.plurals.map_located_count,
                        totalCount,
                        plottedCount,
                        totalCount,
                    )
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(Res.string.map_legend_title),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.semantics { heading() },
            )
            // Inset on the end where there is a FAB: it floats over this corner
            // and nothing here scrolls out from under it.
            FlowRow(
                modifier = Modifier.padding(end = if (clearsFloatingActionButton) FAB_INSET else 0.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                IssueStatus.entries.forEach { LegendItem(it) }
            }
        }
    }
}

@Composable
private fun LegendItem(status: IssueStatus) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(markerColorFor(status))
        )
        Text(text = status.label(), style = MaterialTheme.typography.labelMedium)
    }
}

private fun GeoPoint.toPosition(): Position = Position(longitude = longitude, latitude = latitude)

private fun Feature<*, JsonObject?>.issueId(): String? =
    properties?.get(PROP_ISSUE_ID)?.jsonPrimitive?.contentOrNull

/** One point feature per issue, carrying only what the style and a tap need. */
private fun List<Issue>.toFeatureCollection(): FeatureCollection<Point, JsonObject> =
    FeatureCollection(
        mapNotNull { issue ->
            val point = issue.location.point ?: return@mapNotNull null
            Feature(
                geometry = Point(point.toPosition()),
                properties = JsonObject(
                    mapOf(
                        PROP_ISSUE_ID to JsonPrimitive(issue.id),
                        PROP_STATUS to JsonPrimitive(issue.status.storageKey),
                    )
                ),
            )
        }
    )

/** The tightest box containing every issue, or null when there are none. */
private fun List<Issue>.boundingBox(): BoundingBox? {
    val points = mapNotNull { it.location.point }
    if (points.isEmpty()) return null
    return BoundingBox(
        west = points.minOf { it.longitude },
        south = points.minOf { it.latitude },
        east = points.maxOf { it.longitude },
        north = points.maxOf { it.latitude },
    )
}
