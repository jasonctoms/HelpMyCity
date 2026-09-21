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
 * The city's photograph and the city's mark, for the two places the app wears
 * them: the header on every screen, and the sign-in page behind the form.
 *
 * Both sit on the same photograph, so both draw their text in white over a
 * scrim rather than in scheme colors -- see [OnCityPhoto].
 */

/** Text and icons sit on a photograph, so they are light in both schemes. */
internal val OnCityPhoto = Color.White
internal val OnCityPhotoMuted = Color.White.copy(alpha = 0.78f)

private val DefaultLogoHeight = 44.dp

/**
 * The city's photograph under a scrim, filling whatever it is given.
 *
 * The scrim is what makes white text safe over an image nobody vetted, so it is
 * the caller's to choose: a header darkens only the band its wordmark sits in,
 * while sign-in darkens the whole page. Until the photo arrives -- and forever,
 * for a city that supplied none -- the scrim falls on the theme's primary,
 * which white also reads against.
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
 * The city's mark where it has one, its name set in type where it does not.
 *
 * Never both: a logo almost always spells the city out already, and setting the
 * name beside it reads as a placeholder someone forgot to remove. The name is
 * what a screen reader announces either way, and it is also the fallback when
 * the logo will not load -- a city's own CMS is exactly the sort of host that
 * refuses one request in three, and a header must not end up nameless.
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
            // Tighter than Material's default, which is set for a title sitting
            // on a flat surface rather than a name sitting on a photograph.
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
        // A mark that carries its own colors cannot be recolored, so it gets a
        // light plate to sit on instead of the photograph.
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
