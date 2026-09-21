package dev.helpmycity.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.download
import kotlinx.coroutines.launch

@Composable
actual fun rememberCsvSaver(onFailed: () -> Unit): CsvSaver {
    val scope = rememberCoroutineScope()
    return remember {
        CsvSaver { fileName, csv ->
            scope.launch {
                runCatching { FileKit.download(csv.encodeToByteArray(), "$fileName.csv") }
                    .onFailure { onFailed() }
            }
        }
    }
}
