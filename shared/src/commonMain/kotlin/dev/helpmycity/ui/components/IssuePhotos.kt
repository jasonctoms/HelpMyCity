package dev.helpmycity.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.helpmycity.domain.model.IssuePhoto
import helpmycity.shared.generated.resources.Res
import helpmycity.shared.generated.resources.photo_alt
import helpmycity.shared.generated.resources.photo_remove
import org.jetbrains.compose.resources.stringResource

private val THUMBNAIL_SIZE = 96.dp

/**
 * What Coil should load for a photo: the uploaded copy when there is one, the
 * local bytes otherwise.
 *
 * Preferring the URL lets Coil cache the response on disk and in memory, so a
 * synced device need not read the bytes out of SQLite at all. `ByteArray` is a
 * first-class Coil model, so the fallback needs no per-platform decoding.
 */
private val IssuePhoto.imageModel: Any?
    get() = remoteUrl ?: bytes

/**
 * A horizontal strip of an issue's photos.
 *
 * [onRemove] is null wherever photos are read-only -- the issue detail screen --
 * and supplied on the submission form, where a resident can still change their
 * mind.
 */
@Composable
fun IssuePhotoStrip(
    photos: List<IssuePhoto>,
    modifier: Modifier = Modifier,
    onRemove: ((String) -> Unit)? = null,
) {
    if (photos.isEmpty()) return
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        photos.forEach { photo ->
            PhotoThumbnail(
                model = photo.imageModel,
                caption = photo.caption,
                onRemove = onRemove?.let { remove -> { remove(photo.id) } },
            )
        }
    }
}

/**
 * One thumbnail.
 *
 * [model] is deliberately `Any?` rather than an `IssuePhoto`: the submission form
 * shows images that have no database row yet, so it passes the picked bytes
 * straight through.
 */
@Composable
fun PhotoThumbnail(
    model: Any?,
    modifier: Modifier = Modifier,
    caption: String? = null,
    onRemove: (() -> Unit)? = null,
) {
    // The remove action sits below the image rather than over it: a label on top
    // of an arbitrary photo has no contrast guarantee at all.
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier = Modifier.size(THUMBNAIL_SIZE).clip(RoundedCornerShape(8.dp)),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            AsyncImage(
                model = model,
                // The caption when there is one; otherwise a screen reader gets at
                // least "photo of the reported problem" rather than nothing.
                contentDescription = caption ?: stringResource(Res.string.photo_alt),
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(THUMBNAIL_SIZE),
            )
        }
        if (onRemove != null) {
            TextButton(onClick = onRemove) {
                Text(
                    text = stringResource(Res.string.photo_remove),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}
