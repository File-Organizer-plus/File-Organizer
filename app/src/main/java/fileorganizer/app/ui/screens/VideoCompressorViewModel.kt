package fileorganizer.app.ui.screens

import android.app.Application
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.abedelazizshe.lightcompressorlibrary.CompressionListener
import com.abedelazizshe.lightcompressorlibrary.VideoCompressor
import com.abedelazizshe.lightcompressorlibrary.VideoQuality
import com.abedelazizshe.lightcompressorlibrary.config.Configuration
import com.abedelazizshe.lightcompressorlibrary.config.SaveLocation
import com.abedelazizshe.lightcompressorlibrary.config.SharedStorageConfiguration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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

    fun startCompression(context: Context, quality: CompressQuality, successMsg: String, failMsg: String, cancelMsg: String) {
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
                        val outputIsValid = verifyCompressedOutput(context, path, size)
                        _isCompressing.value = false

                        if (outputIsValid) {
                            _progress.value = 100f
                            _resultMessage.value = successMsg
                        } else {
                            deleteInvalidOutput(context, path)
                            _progress.value = 0f
                            _resultMessage.value = failMsg.replace(
                                "%1\$s",
                                "Compressed video could not be verified after saving"
                            )
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
