package fileorganizer.app.ui.screens

import android.app.Application
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.abedelazizshe.lightcompressorlibrary.CompressionListener
import com.abedelazizshe.lightcompressorlibrary.VideoCompressor
import com.abedelazizshe.lightcompressorlibrary.VideoQuality
import com.abedelazizshe.lightcompressorlibrary.config.Configuration
import com.abedelazizshe.lightcompressorlibrary.config.SaveLocation
import com.abedelazizshe.lightcompressorlibrary.config.SharedStorageConfiguration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class CompressQuality {
    VERY_HIGH, HIGH, MEDIUM, LOW
}

class VideoCompressorViewModel(application: Application) : AndroidViewModel(application) {

    private val _selectedUri = MutableStateFlow<Uri?>(null)
    val selectedUri: StateFlow<Uri?> = _selectedUri.asStateFlow()

    private val _isCompressing = MutableStateFlow(false)
    val isCompressing: StateFlow<Boolean> = _isCompressing.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _resultMessage = MutableStateFlow<String?>(null)
    val resultMessage: StateFlow<String?> = _resultMessage.asStateFlow()

    fun selectVideo(uri: Uri?) {
        _selectedUri.value = uri
        _resultMessage.value = null
        _progress.value = 0f
    }

    fun clearResult() {
        _resultMessage.value = null
    }

    fun startCompression(
        context: Context,
        quality: CompressQuality,
        successMsg: String,
        failMsg: String,
        cancelMsg: String,
        notSmallerMsg: String
    ) {
        val uri = selectedUri.value ?: return

        _isCompressing.value = true
        _progress.value = 0f
        _resultMessage.value = null

        val videoQuality = when(quality) {
            CompressQuality.VERY_HIGH -> VideoQuality.VERY_HIGH
            CompressQuality.HIGH -> VideoQuality.HIGH
            CompressQuality.MEDIUM -> VideoQuality.MEDIUM
            CompressQuality.LOW -> VideoQuality.LOW
        }

        viewModelScope.launch {
            // Query metadata only; do not read the entire source video just to get
            // its size, so this check does not add a meaningful delay.
            val originalSize = withContext(Dispatchers.IO) {
                getUriSize(context, uri)
            }

            VideoCompressor.start(
                context = context,
                uris = listOf(uri),
                isStreamable = false,
                storageConfiguration = SharedStorageConfiguration(
                    saveAt = SaveLocation.movies,
                    subFolderName = "FileKit"
                ),
                configureWith = Configuration(
                    quality = videoQuality,
                    videoNames = listOf("FileKit_compressed_${System.currentTimeMillis()}.mp4"),
                    isMinBitrateCheckEnabled = false,
                    disableAudio = false,
                    keepOriginalResolution = true
                ),
                listener = object : CompressionListener {
                    override fun onProgress(index: Int, percent: Float) {
                        _progress.value = percent
                    }

                    override fun onStart(index: Int) {
                        _isCompressing.value = true
                    }

                    override fun onSuccess(index: Int, size: Long, path: String?) {
                        viewModelScope.launch {
                            val outputIsValid = verifyCompressedOutputWithRetry(context, path, size)

                            if (!outputIsValid) {
                                deleteInvalidOutput(context, path)
                                _isCompressing.value = false
                                _progress.value = 0f
                                _resultMessage.value = failMsg.replace(
                                    "%1\$s",
                                    "Compressed video could not be verified after saving"
                                )
                                return@launch
                            }

                            val outputSize = withContext(Dispatchers.IO) {
                                getCompressedOutputSize(context, path, size)
                            }

                            // A valid output is not a useful compression result when
                            // it is the same size or larger than the source. Remove the
                            // extra copy instead of reporting a misleading success.
                            if (originalSize > 0L && outputSize > 0L && outputSize >= originalSize) {
                                deleteInvalidOutput(context, path)
                                _isCompressing.value = false
                                _progress.value = 0f
                                _resultMessage.value = notSmallerMsg
                                return@launch
                            }

                            _isCompressing.value = false
                            _progress.value = 100f
                            _resultMessage.value = successMsg
                        }
                    }

                    override fun onFailure(index: Int, failureMessage: String) {
                        _isCompressing.value = false
                        _progress.value = 0f
                        _resultMessage.value = failMsg.replace("%1\$s", failureMessage)
                    }

                    override fun onCancelled(index: Int) {
                        _isCompressing.value = false
                        _progress.value = 0f
                        _resultMessage.value = cancelMsg
                    }
                }
            )
        }
    }

    private suspend fun verifyCompressedOutputWithRetry(
        context: Context,
        path: String?,
        reportedSize: Long
    ): Boolean = withContext(Dispatchers.IO) {
        if (verifyCompressedOutput(context, path, reportedSize)) {
            return@withContext true
        }

        // Only wait when the first verification fails. Normal successful
        // compressions have no added delay.
        delay(100)
        verifyCompressedOutput(context, path, reportedSize)
    }

    private fun verifyCompressedOutput(context: Context, path: String?, reportedSize: Long): Boolean {
        if (path.isNullOrBlank() || reportedSize <= 0L) return false

        val outputUri = pathToUri(path)

        val hasSavedData = try {
            if (outputUri.scheme == "content") {
                context.contentResolver.openInputStream(outputUri)?.use { input ->
                    input.read() != -1
                } == true
            } else {
                val filePath = outputUri.path ?: path
                val file = File(filePath)
                file.exists() && file.isFile && file.length() > 0L
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }

        if (!hasSavedData) return false

        val retriever = MediaMetadataRetriever()
        return try {
            if (outputUri.scheme == "content") {
                retriever.setDataSource(context, outputUri)
            } else {
                val filePath = outputUri.path ?: path
                retriever.setDataSource(filePath)
            }

            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0

            duration > 0L && width > 0 && height > 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun getCompressedOutputSize(context: Context, path: String?, reportedSize: Long): Long {
        if (path.isNullOrBlank()) return reportedSize

        val outputUri = pathToUri(path)
        val actualSize = if (outputUri.scheme == "content") {
            getUriSize(context, outputUri)
        } else {
            val filePath = outputUri.path ?: path
            File(filePath).takeIf { it.exists() && it.isFile }?.length() ?: -1L
        }

        return if (actualSize > 0L) actualSize else reportedSize
    }

    private fun getUriSize(context: Context, uri: Uri): Long {
        if (uri.scheme == "file") {
            return uri.path?.let { File(it).takeIf(File::exists)?.length() } ?: -1L
        }

        try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                val sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeColumn >= 0 && cursor.moveToFirst() && !cursor.isNull(sizeColumn)) {
                    val size = cursor.getLong(sizeColumn)
                    if (size > 0L) return size
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
                if (descriptor.length > 0L) return descriptor.length
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                if (descriptor.statSize > 0L) return descriptor.statSize
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return -1L
    }

    private fun deleteInvalidOutput(context: Context, path: String?) {
        if (path.isNullOrBlank()) return

        val outputUri = pathToUri(path)
        try {
            if (outputUri.scheme == "content") {
                context.contentResolver.delete(outputUri, null, null)
            } else {
                val filePath = outputUri.path ?: path
                File(filePath).takeIf { it.exists() }?.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun pathToUri(path: String): Uri {
        return when {
            path.startsWith("content://", ignoreCase = true) -> Uri.parse(path)
            path.startsWith("file://", ignoreCase = true) -> Uri.parse(path)
            else -> Uri.fromFile(File(path))
        }
    }

    fun cancelCompression() {
        VideoCompressor.cancel()
    }
}
