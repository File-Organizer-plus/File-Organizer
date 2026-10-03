package fileorganizer.app.utils

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

object StorageUtils {

    const val SUB_FOLDER_NAME = "FileKit PDF"

    @Volatile
    private var lastPdfOutputUri: Uri? = null

    /**
     * Creates an OutputStream for saving a PDF document.
     * Compatible with Android 7.0 up to Android 14/15 (Scoped Storage aware).
     * On Android 10+ (API 29+), uses MediaStore without requiring storage permissions.
     * On older Android versions, creates files in public Documents directory.
     */
    fun createPdfOutputStream(context: Context, fileName: String): Pair<OutputStream, Uri?> {
        val safeFileName = if (fileName.endsWith(".pdf", ignoreCase = true)) fileName else "$fileName.pdf"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var createdUri: Uri? = null

            try {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, safeFileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOCUMENTS}/$SUB_FOLDER_NAME")
                }
                val contentUri = MediaStore.Files.getContentUri("external")
                createdUri = context.contentResolver.insert(contentUri, contentValues)

                if (createdUri != null) {
                    val stream = context.contentResolver.openOutputStream(createdUri)
                    if (stream != null) {
                        lastPdfOutputUri = createdUri
                        return Pair(stream, createdUri)
                    }

                    // MediaStore row was created but no writable stream was returned.
                    // Remove the empty entry before falling back to another save path.
                    cleanupFailedMediaStoreEntry(context, createdUri)
                    createdUri = null
                }
            } catch (e: Exception) {
                e.printStackTrace()

                // If insertion succeeded before a later failure, do not leave an
                // empty or unusable PDF entry visible in MediaStore.
                createdUri?.let { uri ->
                    cleanupFailedMediaStoreEntry(context, uri)
                }
            }
        }

        // Fallback for Android 9 and lower, or if MediaStore insertion/opening fails
        val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val targetDir = File(documentsDir, SUB_FOLDER_NAME)
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        val targetFile = File(targetDir, safeFileName)
        return try {
            val uri = Uri.fromFile(targetFile)
            lastPdfOutputUri = uri
            Pair(FileOutputStream(targetFile), uri)
        } catch (e: Exception) {
            // Absolute fallback: App-specific external documents directory
            val appDocDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
            val fallbackFile = File(appDocDir, safeFileName)
            val uri = Uri.fromFile(fallbackFile)
            lastPdfOutputUri = uri
            Pair(FileOutputStream(fallbackFile), uri)
        }
    }

    private fun cleanupFailedMediaStoreEntry(context: Context, uri: Uri) {
        try {
            context.contentResolver.delete(uri, null, null)
        } catch (cleanupError: Exception) {
            cleanupError.printStackTrace()
        }
    }

    /**
     * Opens the most recently created PDF output with the user's PDF viewer.
     * Content URIs are passed through directly. Legacy file URIs are converted to a
     * FileProvider URI so Android 7+ can grant safe temporary read access.
     *
     * The historical function name is kept to avoid touching unrelated UI code.
     */
    fun createOpenFolderIntent(context: Context): Intent {
        val savedUri = lastPdfOutputUri
            ?: throw IllegalStateException("No PDF output is available to open")

        val viewUri = if (savedUri.scheme == "file") {
            val path = savedUri.path
                ?: throw IllegalStateException("Saved PDF path is unavailable")
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                File(path)
            )
        } else {
            savedUri
        }

        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(viewUri, "application/pdf")
            clipData = ClipData.newRawUri("PDF", viewUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
