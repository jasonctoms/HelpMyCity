package dev.helpmycity.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.helpmycity.deployment.CityBranding

/**
 * The city's photo and the city's logo, for the two places the app shows them:
 * the header on every screen, and the sign-in page behind the form.
 *
 * Both sit on the same photo, so both draw their text in white over a scrim
 * instead of in scheme colors (see [OnCityPhoto]).
 */

/** Text and icons sit on a photo, so they are light in both schemes. */
internal val OnCityPhoto = Color.White
internal val OnCityPhotoMuted = Color.White.copy(alpha = 0.78f)

private val DefaultLogoHeight = 44.dp

/**
 * The city's photo under a scrim, filling whatever space it is given.
 *
 * The scrim is what makes white text safe on top of an image nobody checked, so
 * the caller gets to pick it. A header only darkens the band its wordmark sits
 * in, while sign-in darkens the whole page. Until the photo loads (or forever, for
 * a city that did not supply one), the scrim sits on the theme's primary color,
 * which white text also reads fine against.
 */
@Composable
fun CityPhotoBackground(imageUrl: String?, scrim: Brush, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary)) {
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(Modifier.fillMaxSize().background(scrim))
    }
}

/**
 * The city's logo if it has one, and its name in plain type if it does not.
 *
 * Never both, though. A logo almost always spells out the city already, and
 * putting the name next to it looks like a placeholder somebody forgot to remove.
 * A screen reader announces the name either way, and the name is also the
 * fallback when the logo will not load. A city's own CMS is exactly the kind of
 * host that refuses one request out of three, and a header should never end up
 * with no name at all.
 */
@Composable
fun CityMark(
    cityName: String,
    branding: CityBranding,
    modifier: Modifier = Modifier,
    logoHeight: Dp = DefaultLogoHeight,
    nameStyle: TextStyle = MaterialTheme.typography.displaySmall,
) {
    val logoUrl = branding.logoUrl
    var logoFailed by remember(logoUrl) { mutableStateOf(false) }

    if (logoUrl == null || logoFailed) {
        Text(
            text = cityName,
            style = nameStyle,
            fontWeight = FontWeight.Bold,
            // Tighter than Material's default, which is meant for a title on a
            // flat surface and not a name on top of a photo.
            letterSpacing = (-1).sp,
            color = OnCityPhoto,
            modifier = modifier,
        )
    } else {
        CityLogo(
            url = logoUrl,
            cityName = cityName,
            singleColor = branding.logoIsSingleColor,
            height = logoHeight,
            onFailed = { logoFailed = true },
            modifier = modifier,
        )
    }
}

@Composable
private fun CityLogo(
    url: String,
    cityName: String,
    singleColor: Boolean,
    height: Dp,
    onFailed: () -> Unit,
    modifier: Modifier,
) {
    val logo = @Composable { inner: Modifier ->
        AsyncImage(
            model = url,
            contentDescription = cityName,
            contentScale = ContentScale.Fit,
            alignment = Alignment.CenterStart,
            colorFilter = if (singleColor) ColorFilter.tint(OnCityPhoto) else null,
            onError = { onFailed() },
            modifier = inner.height(height).widthIn(max = 320.dp),
        )
    }
    if (singleColor) {
        logo(modifier)
    } else {
        // A logo with its own colors can't be recolored, so it gets a light plate
        // to sit on instead of the photo.
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.92f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            logo(Modifier)
        }
    }
}
