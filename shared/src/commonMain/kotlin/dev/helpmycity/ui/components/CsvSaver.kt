package dev.helpmycity.ui.components

import androidx.compose.runtime.Composable

/** Hands a CSV file to the user, as a save dialog on a phone or a download in the browser. */
fun interface CsvSaver {
    /** [fileName] has no extension, and `.csv` gets added. */
    fun save(fileName: String, csv: String)
}

@Composable
expect fun rememberCsvSaver(onFailed: () -> Unit): CsvSaver
