package dev.helpmycity.ui.map

import androidx.compose.foundation.Image
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpSize
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
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.expressions.dsl.switch
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.FillLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.BoundingBox
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Geometry
import org.maplibre.spatialk.geojson.MultiPolygon
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Polygon
import org.maplibre.spatialk.geojson.Position

/** The property every neighborhood outline carries, so a tap resolves to it. */
private const val PROP_NEIGHBORHOOD_ID = "neighborhoodId"

/** The property every marker carries, so a tap resolves back to a row. */
private const val PROP_ISSUE_ID = "issueId"

/** Drives the per-status marker color; holds an [IssueStatus.storageKey]. */
private const val PROP_STATUS = "status"

private const val BOUNDARY_HIT_LAYER_ID = "neighborhood-hit-areas"
private const val BOUNDARY_FILL_LAYER_ID = "neighborhood-fills"
private const val BOUNDARY_LINE_LAYER_ID = "neighborhood-outlines"
private const val MARKER_LAYER_ID = "issue-markers"
private const val GLYPH_LAYER_ID = "issue-marker-glyphs"

/**
 * Fixed rather than themed: the basemap is light in both themes, and dark
 * theme's primary all but vanishes on it. Purple, because no status uses it.
 */
private val BOUNDARY_COLOR = Color(0xFF6A3FB5)

private val MARKER_RADIUS = 10.dp
private val GLYPH_SIZE = DpSize(12.dp, 12.dp)

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
 * rows moved, and color and glyph come from style expressions over the feature's
 * status property so the renderer restyles without recomposition.
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
            neighborhoods = state.neighborhoods,
            selectedNeighborhoods = state.selectedNeighborhoods,
            settings = viewModel.settings,
            onIssueClick = onIssueClick,
            onNeighborhoodClick = viewModel::toggleNeighborhood,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        MapLegend(
            plottedCount = state.plotted.size,
            matchingCount = state.matchingCount,
            totalCount = total,
            isFiltered = state.selectedNeighborhoods.isNotEmpty(),
            boundarySource = viewModel.settings.boundarySource,
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
    neighborhoods: List<Neighborhood>,
    selectedNeighborhoods: Set<String>,
    settings: MapSettings,
    onIssueClick: (String) -> Unit,
    onNeighborhoodClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val features = remember(issues) { issues.toFeatureCollection() }
    val boundaries = remember(neighborhoods) { neighborhoods.toBoundaryFeatures() }
    val selectedBoundaries = remember(boundaries, selectedNeighborhoods) {
        FeatureCollection(boundaries.features.filter { it.neighborhoodId() in selectedNeighborhoods })
    }
    val markerColor = remember {
        switch(
            feature[PROP_STATUS].asString(),
            *IssueStatus.entries
                .map { case(it.storageKey, const(markerColorFor(it))) }
                .toTypedArray(),
            fallback = const(markerColorFor(IssueStatus.IN_REVIEW)),
        )
    }
    val markerGlyph = remember {
        switch(
            feature[PROP_STATUS].asString(),
            *IssueStatus.entries
                .map { case(it.storageKey, image(StatusGlyphPainter(it), GLYPH_SIZE)) }
                .toTypedArray(),
            fallback = image(StatusGlyphPainter(IssueStatus.IN_REVIEW), GLYPH_SIZE),
        )
    }

    val mapState = rememberMapState(
        baseStyle = BaseStyle.Uri(settings.styleUrl),
        initialCameraPosition = CameraPosition(
            target = settings.center?.toPosition() ?: Position(0.0, 0.0),
            zoom = if (settings.center != null) settings.defaultZoom else WORLD_ZOOM,
        ),
    ) {
        // Declared first so it draws under the markers, which also puts it
        // after them for clicks: a tap on a marker opens the issue instead.
        val boundarySource = rememberGeoJsonSource(data = GeoJsonData.Features(boundaries))
        FillLayer(
            id = BOUNDARY_HIT_LAYER_ID,
            source = boundarySource,
            opacity = const(0f),
            onClick = { clicked ->
                val neighborhoodId = clicked.firstNotNullOfOrNull { it.neighborhoodId() }
                if (neighborhoodId == null) {
                    ClickResult.Pass
                } else {
                    onNeighborhoodClick(neighborhoodId)
                    ClickResult.Consume
                }
            },
        )
        FillLayer(
            id = BOUNDARY_FILL_LAYER_ID,
            source = rememberGeoJsonSource(data = GeoJsonData.Features(selectedBoundaries)),
            color = const(BOUNDARY_COLOR),
            opacity = const(0.12f),
        )
        LineLayer(
            id = BOUNDARY_LINE_LAYER_ID,
            source = boundarySource,
            color = const(BOUNDARY_COLOR),
            opacity = const(0.7f),
            width = const(1.5.dp),
        )
        val source = rememberGeoJsonSource(data = GeoJsonData.Features(features))
        CircleLayer(
            id = MARKER_LAYER_ID,
            source = source,
            color = markerColor,
            radius = const(MARKER_RADIUS),
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
        // Always drawn, even where markers pile up: a hidden glyph would leave
        // that marker distinguishable by color alone.
        SymbolLayer(
            id = GLYPH_LAYER_ID,
            source = source,
            iconImage = markerGlyph,
            iconAllowOverlap = const(true),
            iconIgnorePlacement = const(true),
        )
    }

    // Frame the selection once there is something to frame, then leave the
    // camera alone until the selection changes: re-framing on every sync would
    // yank the map away from whoever is panning it. A selection frames its
    // whole area, and no selection the whole city, not just the issues, which
    // may be a few pins a block apart or none at all.
    val framedArea = if (selectedNeighborhoods.isEmpty()) boundaries else selectedBoundaries
    var hasFramed by remember(selectedNeighborhoods) { mutableStateOf(false) }
    LaunchedEffect(issues, framedArea) {
        if (hasFramed) return@LaunchedEffect
        val box = (issues.mapNotNull { it.location.point?.toPosition() } + framedArea.outerRings())
            .boundingBox() ?: return@LaunchedEffect
        hasFramed = true
        mapState.fitCameraToBounds(boundingBox = box, padding = FRAMING_PADDING)
    }

    if (isScreenSettled()) {
        MaplibreMap(modifier = modifier, state = mapState)
    } else {
        MapPlaceholder(modifier)
    }
}

/**
 * How many issues made it onto the map, and what the marker colors mean.
 *
 * Each swatch repeats its marker's color and glyph, and the status is spelled
 * out in text too -- the rule the chip colors in `ui/theme/StatusColors.kt`
 * follow as well.
 */
@Composable
private fun MapLegend(
    plottedCount: Int,
    matchingCount: Int,
    totalCount: Int,
    isFiltered: Boolean,
    boundarySource: String?,
    clearsFloatingActionButton: Boolean,
) {
    val legendTitle = stringResource(Res.string.map_legend_title)
    Surface(tonalElevation = 2.dp) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
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
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Inset on the end where there is a FAB: it floats over this corner
            // and nothing here scrolls out from under it.
            FlowRow(
                modifier = Modifier
                    .padding(end = if (clearsFloatingActionButton) FAB_INSET else 0.dp)
                    .semantics { contentDescription = legendTitle },
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                IssueStatus.boardOrder.forEach { LegendItem(it) }
            }
            if (boundarySource != null) {
                Text(
                    text = boundarySource,
                    modifier = Modifier.padding(end = if (clearsFloatingActionButton) FAB_INSET else 0.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                .size(16.dp)
                .clip(CircleShape)
                .background(markerColorFor(status)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = remember(status) { StatusGlyphPainter(status) },
                contentDescription = null,
                modifier = Modifier.size(10.dp),
            )
        }
        Text(text = status.label(), style = MaterialTheme.typography.labelMedium)
    }
}

private fun GeoPoint.toPosition(): Position = Position(longitude = longitude, latitude = latitude)

private fun Feature<*, JsonObject?>.issueId(): String? =
    properties?.get(PROP_ISSUE_ID)?.jsonPrimitive?.contentOrNull

private fun Feature<*, JsonObject?>.neighborhoodId(): String? =
    properties?.get(PROP_NEIGHBORHOOD_ID)?.jsonPrimitive?.contentOrNull

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

/**
 * Every neighborhood with a boundary, as one feature each. A boundary that
 * does not parse as GeoJSON is left off rather than failing the map.
 */
private fun List<Neighborhood>.toBoundaryFeatures(): FeatureCollection<Geometry, JsonObject?> =
    FeatureCollection(
        mapNotNull { neighborhood ->
            neighborhood.boundaryGeoJson
                ?.let(Geometry::fromJsonOrNull)
                ?.let { boundary ->
                    Feature(
                        geometry = boundary,
                        properties = JsonObject(mapOf(PROP_NEIGHBORHOOD_ID to JsonPrimitive(neighborhood.id))),
                    )
                }
        }
    )

/** Every vertex on the outside of each polygon; the holes are inside anyway. */
private fun FeatureCollection<Geometry, *>.outerRings(): List<Position> = features.flatMap { feature ->
    when (val geometry = feature.geometry) {
        is Polygon -> geometry.coordinates.firstOrNull().orEmpty()
        is MultiPolygon -> geometry.coordinates.flatMap { it.firstOrNull().orEmpty() }
        else -> emptyList()
    }
}

/** The tightest box containing every position, or null when there are none. */
private fun List<Position>.boundingBox(): BoundingBox? {
    if (isEmpty()) return null
    return BoundingBox(
        west = minOf { it.longitude },
        south = minOf { it.latitude },
        east = maxOf { it.longitude },
        north = maxOf { it.latitude },
    )
}
