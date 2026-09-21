package dev.helpmycity.ui.components

import androidx.compose.runtime.Composable

/** Hands a CSV file to the user: a save dialog on a phone, a download in the browser. */
fun interface CsvSaver {
    /** [fileName] has no extension; `.csv` is added. */
    fun save(fileName: String, csv: String)
}

@Composable
expect fun rememberCsvSaver(onFailed: () -> Unit): CsvSaver
