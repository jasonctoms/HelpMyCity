package dev.helpmycity.ui.components

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
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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

private val HeroHeight = 216.dp
private val CompactHeight = 132.dp
private val EdgePadding = 16.dp

/**
 * Dark at the top so the buttons read, darkest at the bottom where the wordmark
 * and the tabs sit, and lightest across the middle so the photograph survives.
 */
private val HeaderScrim = Brush.verticalGradient(
    0f to Color.Black.copy(alpha = 0.45f),
    0.4f to Color.Black.copy(alpha = 0.25f),
    1f to Color.Black.copy(alpha = 0.85f),
)

/**
 * The band across the top of every screen: the city's photograph, its mark, and
 * -- where there is room for them -- the top-level tabs and the report button.
 *
 * This replaces `TopAppBar` rather than configuring one. A Material top bar is
 * a title and two icon slots at a fixed height, which is the right answer on a
 * phone and reads as a phone app everywhere else; a city deserves to look like
 * itself on a laptop.
 *
 * @param tabs the top-level destinations, drawn inside the header when
 *   [showTabs] is true and left to the bottom bar when it is not.
 * @param onBack non-null on a pushed screen, which gets the short header and
 *   its own title in place of the wordmark.
 * @param action the primary call to action, drawn beside the profile button on
 *   a wide window. Null leaves it to the floating action button.
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
) {
    val nested = onBack != null
    // The photograph runs under the status bar; only the content is inset.
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(modifier.fillMaxWidth().height((if (nested) CompactHeight else HeroHeight) + topInset)) {
        CityPhotoBackground(imageUrl = branding.headerImageUrl, scrim = HeaderScrim)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topInset),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = EdgePadding, end = EdgePadding, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack != null) {
                    HeaderIconButton(
                        icon = Res.drawable.ic_back,
                        description = stringResource(Res.string.nav_back),
                        onClick = onBack,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Spacer(Modifier.weight(1f))
                if (action != null) {
                    action()
                    Spacer(Modifier.width(12.dp))
                }
                if (onProfileClick != null) {
                    HeaderIconButton(
                        icon = Res.drawable.ic_person,
                        description = stringResource(Res.string.profile_open),
                        onClick = onProfileClick,
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            if (nested) {
                Text(
                    text = screenTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = OnCityPhoto,
                    modifier = Modifier.padding(
                        start = EdgePadding,
                        end = EdgePadding,
                        bottom = 16.dp,
                    ),
                )
            } else {
                Wordmark(cityName = cityName, branding = branding)
                if (showTabs) {
                    HeaderTabs(
                        tabs = tabs,
                        selected = selectedTab,
                        onClick = onTabClick,
                        modifier = Modifier.padding(start = EdgePadding - 4.dp, top = 14.dp),
                    )
                } else {
                    Spacer(Modifier.height(20.dp))
                }
            }
        }
    }
}

@Composable
private fun Wordmark(cityName: String, branding: CityBranding) {
    Column(modifier = Modifier.padding(horizontal = EdgePadding)) {
        CityMark(cityName = cityName, branding = branding)
        val tagline = branding.tagline
        if (tagline != null) {
            Text(
                text = tagline,
                style = MaterialTheme.typography.titleSmall,
                color = OnCityPhotoMuted,
                modifier = Modifier.padding(top = if (branding.logoUrl == null) 0.dp else 10.dp),
            )
        }
    }
}

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
