package fileorganizer.app.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ImagesToPdfViewModel : ViewModel() {
    private val _selectedImages = MutableStateFlow<List<Uri>>(emptyList())
    val selectedImages = _selectedImages.asStateFlow()

    private val _isConverting = MutableStateFlow(false)
    val isConverting = _isConverting.asStateFlow()

    private val _progress = MutableStateFlow(0)
    val progress = _progress.asStateFlow()

    private val _resultMessage = MutableStateFlow<String?>(null)
    val resultMessage = _resultMessage.asStateFlow()

    fun addImages(uris: List<Uri>, maxLimitMsg: String) {
        val current = _selectedImages.value.toMutableList()
        current.addAll(uris)
        if (current.size > 3) {
            _selectedImages.value = current.take(3)
            _resultMessage.value = maxLimitMsg
        } else {
            _selectedImages.value = current
        }
    }

    fun removeImage(uri: Uri) {
        val current = _selectedImages.value.toMutableList()
        current.remove(uri)
        _selectedImages.value = current
    }
    
    fun moveImageUp(uri: Uri) {
        val current = _selectedImages.value.toMutableList()
        val index = current.indexOf(uri)
        if (index > 0) {
            current.removeAt(index)
            current.add(index - 1, uri)
            _selectedImages.value = current
        }
    }
    
    fun moveImageDown(uri: Uri) {
        val current = _selectedImages.value.toMutableList()
        val index = current.indexOf(uri)
        if (index < current.size - 1) {
            current.removeAt(index)
            current.add(index + 1, uri)
            _selectedImages.value = current
        }
    }

    fun clearImages() {
        _selectedImages.value = emptyList()
        _resultMessage.value = null
        _progress.value = 0
    }

    fun convertToPdf(context: Context, successMsg: String, failMsg: String) {
        val imagesToConvert = _selectedImages.value.toList()
        if (imagesToConvert.isEmpty()) return
        
        viewModelScope.launch {
            _isConverting.value = true
            _progress.value = 0
            _resultMessage.value = null
            
            try {
                withContext(Dispatchers.IO) {
                    val pdfDocument = PdfDocument()
                    val tempImages = mutableListOf<File>()
                    var outputUri: Uri? = null

                    try {
                        // Copy every selected image to app cache first. This avoids opening
                        // gallery/cloud-provider URIs repeatedly during decode and EXIF reads.
                        imagesToConvert.forEachIndexed { index, uri ->
                            tempImages.add(copyImageToTempWithRetry(context, uri, index + 1))
                        }

                        tempImages.forEachIndexed { index, tempImage ->
                            val options = BitmapFactory.Options().apply {
                                inJustDecodeBounds = true
                            }
                            BitmapFactory.decodeFile(tempImage.absolutePath, options)

                            if (options.outWidth <= 0 || options.outHeight <= 0) {
                                throw IllegalStateException("Image ${index + 1} could not be decoded")
                            }

                            // Calculate downsampling to avoid OutOfMemoryError.
                            val reqWidth = 1500
                            val reqHeight = 2000
                            var inSampleSize = 1
                            if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
                                val halfHeight = options.outHeight / 2
                                val halfWidth = options.outWidth / 2
                                while ((halfHeight / inSampleSize) >= reqHeight &&
                                    (halfWidth / inSampleSize) >= reqWidth
                                ) {
                                    inSampleSize *= 2
                                }
                            }

                            val decodeOptions = BitmapFactory.Options().apply {
                                this.inSampleSize = inSampleSize
                            }

                            val decodedBitmap = BitmapFactory.decodeFile(tempImage.absolutePath, decodeOptions)
                                ?: throw IllegalStateException("Image ${index + 1} could not be decoded")

                            val rotatedBitmap = try {
                                val exif = ExifInterface(tempImage.absolutePath)
                                val orientation = exif.getAttributeInt(
                                    ExifInterface.TAG_ORIENTATION,
                                    ExifInterface.ORIENTATION_NORMAL
                                )
                                val matrix = Matrix()
                                when (orientation) {
                                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> {
                                        matrix.postScale(-1f, 1f)
                                    }
                                    ExifInterface.ORIENTATION_ROTATE_180 -> {
                                        matrix.postRotate(180f)
                                    }
                                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                                        matrix.postScale(-1f, 1f)
                                        matrix.postRotate(180f)
                                    }
                                    ExifInterface.ORIENTATION_TRANSPOSE -> {
                                        matrix.postScale(-1f, 1f)
                                        matrix.postRotate(270f)
                                    }
                                    ExifInterface.ORIENTATION_ROTATE_90 -> {
                                        matrix.postRotate(90f)
                                    }
                                    ExifInterface.ORIENTATION_TRANSVERSE -> {
                                        matrix.postScale(-1f, 1f)
                                        matrix.postRotate(90f)
                                    }
                                    ExifInterface.ORIENTATION_ROTATE_270 -> {
                                        matrix.postRotate(270f)
                                    }
                                }
                                if (!matrix.isIdentity) {
                                    val result = Bitmap.createBitmap(
                                        decodedBitmap,
                                        0,
                                        0,
                                        decodedBitmap.width,
                                        decodedBitmap.height,
                                        matrix,
                                        true
                                    )
                                    if (result != decodedBitmap) {
                                        decodedBitmap.recycle()
                                    }
                                    result
                                } else {
                                    decodedBitmap
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                decodedBitmap
                            }

                            val scaledBitmap = scaleBitmapIfNecessary(rotatedBitmap)
                            try {
                                val pageInfo = PdfDocument.PageInfo.Builder(
                                    scaledBitmap.width,
                                    scaledBitmap.height,
                                    index + 1
                                ).create()
                                val page = pdfDocument.startPage(pageInfo)
                                page.canvas.drawBitmap(scaledBitmap, 0f, 0f, null)
                                pdfDocument.finishPage(page)
                            } finally {
                                if (scaledBitmap != rotatedBitmap && !scaledBitmap.isRecycled) {
                                    scaledBitmap.recycle()
                                }
                                if (!rotatedBitmap.isRecycled) {
                                    rotatedBitmap.recycle()
                                }
                            }

                            withContext(Dispatchers.Main) {
                                _progress.value = ((index + 1) * 90) / imagesToConvert.size
                            }
                        }
                        
                        val timeStamp = SimpleDateFormat(
                            "yyyyMMdd_HHmmss",
                            Locale.getDefault()
                        ).format(Date())
                        val fileName = "FileKit_Images_$timeStamp.pdf"
                        
                        val (outputStream, createdUri) =
                            fileorganizer.app.utils.StorageUtils.createPdfOutputStream(context, fileName)
                        outputUri = createdUri

                        outputStream.use { stream ->
                            pdfDocument.writeTo(stream)
                            stream.flush()
                        }

                        val savedUri = outputUri
                            ?: throw IllegalStateException("PDF output location could not be verified")

                        if (!verifySavedPdfWithRetry(context, savedUri, imagesToConvert.size)) {
                            throw IllegalStateException("Saved PDF could not be verified")
                        }

                        withContext(Dispatchers.Main) {
                            _progress.value = 100
                            _resultMessage.value = successMsg.replace("%1\$s", fileName)
                            _selectedImages.value = emptyList()
                        }
                    } catch (t: Throwable) {
                        outputUri?.let { uri -> deleteOutput(context, uri) }
                        throw t
                    } finally {
                        try {
                            pdfDocument.close()
                        } catch (closeError: Throwable) {
                            closeError.printStackTrace()
                        }

                        tempImages.forEach { tempFile ->
                            try {
                                tempFile.delete()
                            } catch (cleanupError: Throwable) {
                                cleanupError.printStackTrace()
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
                t.printStackTrace()
                withContext(Dispatchers.Main) {
                    _progress.value = 0
                    _resultMessage.value = failMsg.replace("%1\$s", t.message ?: "")
                }
            } finally {
                withContext(Dispatchers.Main) {
                    _isConverting.value = false
                }
            }
        }
    }

    private fun copyImageToTempWithRetry(
        context: Context,
        uri: Uri,
        imageNumber: Int
    ): File {
        var lastError: Throwable? = null

        repeat(2) { attempt ->
            val tempFile = File.createTempFile("image_to_pdf_${imageNumber}_", ".img", context.cacheDir)
            try {
                val input = context.contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("Unable to read image $imageNumber")

                input.use { source ->
                    tempFile.outputStream().use { target ->
                        source.copyTo(target)
                        target.flush()
                    }
                }

                if (tempFile.length() <= 0L) {
                    throw IllegalStateException("Image $imageNumber is empty")
                }

                return tempFile
            } catch (t: Throwable) {
                lastError = t
                tempFile.delete()
                if (attempt == 0) {
                    try {
                        Thread.sleep(75)
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                    }
                }
            }
        }

        throw IllegalStateException(
            "Unable to read image $imageNumber after retry",
            lastError
        )
    }

    private fun verifySavedPdfWithRetry(
        context: Context,
        uri: Uri,
        expectedPageCount: Int
    ): Boolean {
        repeat(2) { attempt ->
            if (verifySavedPdf(context, uri, expectedPageCount)) return true
            if (attempt == 0) {
                try {
                    Thread.sleep(100)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
        }
        return false
    }

    private fun verifySavedPdf(context: Context, uri: Uri, expectedPageCount: Int): Boolean {
        val descriptor = try {
            if (uri.scheme == "file") {
                val path = uri.path ?: return false
                val file = File(path)
                if (!file.exists() || !file.isFile || file.length() <= 0L) return false
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            } else {
                context.contentResolver.openFileDescriptor(uri, "r")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } ?: return false

        return try {
            val renderer = PdfRenderer(descriptor)
            try {
                renderer.pageCount == expectedPageCount && renderer.pageCount > 0
            } finally {
                renderer.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            try {
                descriptor.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun deleteOutput(context: Context, uri: Uri) {
        try {
            if (uri.scheme == "file") {
                uri.path?.let { File(it).delete() }
            } else {
                context.contentResolver.delete(uri, null, null)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun scaleBitmapIfNecessary(bitmap: Bitmap): Bitmap {
        val maxWidth = 1500
        val maxHeight = 2000
        
        if (bitmap.width <= maxWidth && bitmap.height <= maxHeight) {
            return bitmap
        }
        
        val ratioBitmap = bitmap.width.toFloat() / bitmap.height.toFloat()
        val ratioMax = maxWidth.toFloat() / maxHeight.toFloat()

        var finalWidth = maxWidth
        var finalHeight = maxHeight
        if (ratioMax > ratioBitmap) {
            finalWidth = (maxHeight.toFloat() * ratioBitmap).toInt()
        } else {
            finalHeight = (maxWidth.toFloat() / ratioBitmap).toInt()
        }
        
        return Bitmap.createScaledBitmap(bitmap, finalWidth, finalHeight, true)
    }
}
