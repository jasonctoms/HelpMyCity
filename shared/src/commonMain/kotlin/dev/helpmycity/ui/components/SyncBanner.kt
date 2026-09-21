package dev.helpmycity.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.helpmycity.data.sync.SyncStatus
import helpmycity.shared.generated.resources.Res
import helpmycity.shared.generated.resources.sync_failed
import helpmycity.shared.generated.resources.sync_no_backend
import helpmycity.shared.generated.resources.sync_synced
import helpmycity.shared.generated.resources.sync_syncing
import org.jetbrains.compose.resources.stringResource

/**
 * Tells the user where their data actually is.
 *
 * With no backend configured this is the honest answer -- "changes stay on this
 * device" -- rather than a spinner that implies an upload is happening.
 */
@Composable
fun SyncBanner(status: SyncStatus, modifier: Modifier = Modifier) {
    val message = when (status) {
        SyncStatus.Idle -> return
        SyncStatus.Syncing -> stringResource(Res.string.sync_syncing)
        SyncStatus.NoBackend -> stringResource(Res.string.sync_no_backend)
        is SyncStatus.Synced -> stringResource(Res.string.sync_synced)
        is SyncStatus.Failed -> stringResource(Res.string.sync_failed, status.message)
    }
    val isProblem = status is SyncStatus.Failed
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = if (isProblem) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = modifier
            .fillMaxWidth()
            .background(
                if (isProblem) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
