package dev.helpmycity.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.compose.rememberFileSaverLauncher
import io.github.vinceglb.filekit.writeString
import kotlinx.coroutines.launch

@Composable
actual fun rememberCsvSaver(onFailed: () -> Unit): CsvSaver {
    val scope = rememberCoroutineScope()
    // The dialog answers with a file to write to; the text waits here until it does.
    var pending by remember { mutableStateOf<String?>(null) }
    val launcher = rememberFileSaverLauncher(
        dialogSettings = FileKitDialogSettings.createDefault(),
        onError = { onFailed() },
    ) { file ->
        val csv = pending
        pending = null
        if (file != null && csv != null) {
            scope.launch {
                runCatching { file.writeString(csv) }.onFailure { onFailed() }
            }
        }
    }
    return remember(launcher) {
        CsvSaver { fileName, csv ->
            pending = csv
            launcher.launch(suggestedName = fileName, defaultExtension = "csv")
        }
    }
}
