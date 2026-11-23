package app.tvfiles

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileNotFoundException

/** Read-only access to one file at a time, for the app the user opened it with. */
class FileProvider : ContentProvider() {
    override fun onCreate() = true

    override fun getType(uri: Uri): String = mimeType(fileOf(uri))

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw SecurityException("read-only")
        return ParcelFileDescriptor.open(fileOf(uri), ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        args: Array<String>?,
        sort: String?,
    ): Cursor {
        val file = fileOf(uri)
        val columns = (projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE))
            .filter { it == OpenableColumns.DISPLAY_NAME || it == OpenableColumns.SIZE }
        return MatrixCursor(columns.toTypedArray(), 1).apply {
            addRow(columns.map { if (it == OpenableColumns.SIZE) file.length() else file.name })
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()

    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<String>?): Int =
        throw UnsupportedOperationException()

    override fun delete(uri: Uri, selection: String?, args: Array<String>?): Int =
        throw UnsupportedOperationException()

    private fun fileOf(uri: Uri): File {
        val file = File(uri.path.orEmpty()).canonicalFile
        // Shared storage only, never this app's private files.
        if (!file.path.startsWith("/storage/")) throw FileNotFoundException(uri.toString())
        return file
    }

    companion object {
        fun uriFor(file: File): Uri = Uri.Builder().scheme("content")
            .authority("app.tvfiles.provider").path(file.canonicalPath).build()

        fun mimeType(file: File): String =
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase())
                ?: "application/octet-stream"
    }
}
