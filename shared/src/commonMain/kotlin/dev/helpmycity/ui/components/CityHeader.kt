package dev.helpmycity.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.helpmycity.deployment.CityBranding
import dev.helpmycity.ui.navigation.TopLevelRoute
import helpmycity.shared.generated.resources.Res
import helpmycity.shared.generated.resources.ic_back
import helpmycity.shared.generated.resources.ic_person
import helpmycity.shared.generated.resources.nav_back
import helpmycity.shared.generated.resources.profile_open
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private val BarHeight = 56.dp
private val EdgePadding = 16.dp
private val LogoHeight = 30.dp

/**
 * Dark enough all the way across for the buttons, the mark, and the tabs to
 * read over whatever the photograph has in it.
 */
private val HeaderScrim = Brush.verticalGradient(
    0f to Color.Black.copy(alpha = 0.5f),
    1f to Color.Black.copy(alpha = 0.75f),
)

/**
 * The band across the top of every screen: the city's photograph, its mark, and
 * -- where there is room for them -- the top-level tabs and the report button.
 *
 * This replaces `TopAppBar` rather than configuring one, so a city looks like
 * itself on a laptop and not like a phone app stretched to fit. It stays one
 * bar tall so the map beneath it keeps the room; the tabs add a second row only
 * when they are drawn here.
 *
 * @param tabs the top-level destinations, drawn inside the header when
 *   [showTabs] is true and left to the bottom bar when it is not.
 * @param showTabs also shows the tagline, since both need a wide window.
 * @param onBack non-null on a pushed screen, which gets its own title in place
 *   of the city's mark.
 * @param action the primary call to action, drawn beside the profile button on
 *   a wide window. Null leaves it to the floating action button.
 * @param status a small indicator drawn first among the header's buttons.
 */
@Composable
fun CityHeader(
    cityName: String,
    branding: CityBranding,
    screenTitle: String,
    tabs: List<TopLevelRoute>,
    selectedTab: TopLevelRoute?,
    onTabClick: (TopLevelRoute) -> Unit,
    showTabs: Boolean,
    onBack: (() -> Unit)?,
    onProfileClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
    status: (@Composable () -> Unit)? = null,
) {
    // The photograph runs under the status bar; only the content is inset.
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(modifier.fillMaxWidth()) {
        CityPhotoBackground(
            imageUrl = branding.headerImageUrl,
            scrim = HeaderScrim,
            modifier = Modifier.matchParentSize(),
        )
        Column(modifier = Modifier.fillMaxWidth().padding(top = topInset)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(BarHeight)
                    .padding(horizontal = EdgePadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Keyed on the title alone, so the leaving side still draws
                // whatever it showed while the new one fades in over it.
                val currentOnBack by rememberUpdatedState(onBack)
                AnimatedContent(
                    targetState = if (onBack != null) screenTitle else null,
                    transitionSpec = { HeaderFade },
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier.weight(1f),
                ) { title ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (title != null) {
                            HeaderIconButton(
                                icon = Res.drawable.ic_back,
                                description = stringResource(Res.string.nav_back),
                                onClick = { currentOnBack?.invoke() },
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = OnCityPhoto,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        } else {
                            CityMark(
                                cityName = cityName,
                                branding = branding,
                                logoHeight = LogoHeight,
                                nameStyle = MaterialTheme.typography.titleLarge,
                            )
                            val tagline = branding.tagline
                            if (showTabs && tagline != null) {
                                Text(
                                    text = tagline,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = OnCityPhotoMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(start = 16.dp),
                                )
                            }
                        }
                    }
                }
                if (status != null) {
                    Spacer(Modifier.width(8.dp))
                    status()
                }
                // Keyed on presence alone, so the leaving side keeps the button
                // it had while it fades, and a new lambda is not a new button.
                AnimatedContent(
                    targetState = action,
                    contentKey = { it != null },
                    transitionSpec = { HeaderFade },
                ) { current ->
                    if (current != null) {
                        Row {
                            Spacer(Modifier.width(12.dp))
                            current()
                        }
                    }
                }
                AnimatedContent(
                    targetState = onProfileClick,
                    contentKey = { it != null },
                    transitionSpec = { HeaderFade },
                ) { onClick ->
                    if (onClick != null) {
                        Row {
                            Spacer(Modifier.width(8.dp))
                            HeaderIconButton(
                                icon = Res.drawable.ic_person,
                                description = stringResource(Res.string.profile_open),
                                onClick = onClick,
                            )
                        }
                    }
                }
            }
            AnimatedVisibility(
                visible = showTabs && onBack == null,
                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
            ) {
                HeaderTabs(
                    tabs = tabs,
                    selected = selectedTab,
                    onClick = onTabClick,
                    modifier = Modifier.padding(start = EdgePadding - 4.dp),
                )
            }
        }
    }
}

private val HeaderFade = fadeIn(tween(220, delayMillis = 60)) togetherWith fadeOut(tween(120))

/**
 * The top-level destinations as a row of tabs along the bottom of the header,
 * the way a site puts its sections under its masthead.
 */
@Composable
private fun HeaderTabs(
    tabs: List<TopLevelRoute>,
    selected: TopLevelRoute?,
    onClick: (TopLevelRoute) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        tabs.forEach { route ->
            val isSelected = route == selected
            Column(
                modifier = Modifier
                    // So the underline below measures against this tab's label
                    // rather than against the width of the whole header.
                    .width(IntrinsicSize.Max)
                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                    .clickable { onClick(route) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(route.iconResource),
                        contentDescription = null,
                        tint = if (isSelected) OnCityPhoto else OnCityPhotoMuted,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(route.labelResource),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) OnCityPhoto else OnCityPhotoMuted,
                    )
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(
                            color = if (isSelected) OnCityPhoto else Color.Transparent,
                            shape = RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp),
                        )
                )
            }
        }
    }
}

/** An icon button legible over the photograph rather than over a surface. */
@Composable
private fun HeaderIconButton(
    icon: DrawableResource,
    description: String,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = Color.Black.copy(alpha = 0.28f),
            contentColor = OnCityPhoto,
        ),
    ) {
        Icon(painter = painterResource(icon), contentDescription = description)
    }
}
