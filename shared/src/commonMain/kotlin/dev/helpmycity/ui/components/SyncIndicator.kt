package dev.helpmycity.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.helpmycity.data.sync.SyncStatus
import helpmycity.shared.generated.resources.Res
import helpmycity.shared.generated.resources.common_close
import helpmycity.shared.generated.resources.ic_close
import helpmycity.shared.generated.resources.ic_cloud_done
import helpmycity.shared.generated.resources.ic_cloud_off
import helpmycity.shared.generated.resources.ic_sync_problem
import helpmycity.shared.generated.resources.sync_failed
import helpmycity.shared.generated.resources.sync_no_backend
import helpmycity.shared.generated.resources.sync_now
import helpmycity.shared.generated.resources.sync_status
import helpmycity.shared.generated.resources.sync_synced
import helpmycity.shared.generated.resources.sync_syncing
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private val FailedTint = Color(0xFFFFB4AB)

/**
 * Where the user's data is, as one small icon in the header. Tapping it says so
 * in words, and offers a manual sync when there is a backend to sync with.
 *
 * With no backend configured the honest answer is "changes stay on this
 * device", not a spinner that implies an upload is happening.
 */
@Composable
fun SyncIndicator(status: SyncStatus, onSyncNow: () -> Unit, modifier: Modifier = Modifier) {
    if (status == SyncStatus.Idle) return
    var expanded by remember { mutableStateOf(false) }
    val message = when (status) {
        SyncStatus.Idle, SyncStatus.Syncing -> stringResource(Res.string.sync_syncing)
        SyncStatus.NoBackend -> stringResource(Res.string.sync_no_backend)
        is SyncStatus.Synced -> stringResource(Res.string.sync_synced)
        is SyncStatus.Failed -> stringResource(Res.string.sync_failed, status.message)
    }
    Box(modifier) {
        IconButton(
            onClick = { expanded = true },
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = Color.Black.copy(alpha = 0.28f),
                contentColor = if (status is SyncStatus.Failed) FailedTint else OnCityPhoto,
            ),
        ) {
            val description = "${stringResource(Res.string.sync_status)}: $message"
            when (status) {
                SyncStatus.Idle, SyncStatus.Syncing -> CircularProgressIndicator(
                    color = OnCityPhoto,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp),
                )
                SyncStatus.NoBackend -> Icon(painterResource(Res.drawable.ic_cloud_off), description)
                is SyncStatus.Synced -> Icon(painterResource(Res.drawable.ic_cloud_done), description)
                is SyncStatus.Failed -> Icon(painterResource(Res.drawable.ic_sync_problem), description)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Row(
                modifier = Modifier.widthIn(max = 320.dp).padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (status is SyncStatus.Failed) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.weight(1f, fill = false).padding(vertical = 8.dp),
                )
                IconButton(onClick = { expanded = false }) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_close),
                        contentDescription = stringResource(Res.string.common_close),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            if (status is SyncStatus.Synced || status is SyncStatus.Failed) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.sync_now)) },
                    onClick = {
                        expanded = false
                        onSyncNow()
                    },
                )
            }
        }
    }
}
