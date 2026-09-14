package fileorganizer.app.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.pdf.PdfDocument
import android.media.ExifInterface
import android.net.Uri
import android.os.Environment
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
        if (_selectedImages.value.isEmpty()) return
        
        viewModelScope.launch {
            _isConverting.value = true
            _progress.value = 0
            _resultMessage.value = null
            
            try {
                withContext(Dispatchers.IO) {
                    val pdfDocument = PdfDocument()
                    val imagesCount = _selectedImages.value.size
                    
                    _selectedImages.value.forEachIndexed { index, uri ->
                        val options = BitmapFactory.Options().apply {
                            inJustDecodeBounds = true
                        }
                        context.contentResolver.openInputStream(uri)?.use { inputStream ->
                            BitmapFactory.decodeStream(inputStream, null, options)
                        }

                        // Calculate downsampling to avoid OutOfMemoryError
                        val reqWidth = 1500
                        val reqHeight = 2000
                        var inSampleSize = 1
                        if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
                            val halfHeight = options.outHeight / 2
                            val halfWidth = options.outWidth / 2
                            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                                inSampleSize *= 2
                            }
                        }

                        val decodeOptions = BitmapFactory.Options().apply {
                            this.inSampleSize = inSampleSize
                        }

                        val decodedBitmap = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                            BitmapFactory.decodeStream(inputStream, null, decodeOptions)
                        }

                        if (decodedBitmap != null) {
                            // Handle EXIF rotation
                            val rotatedBitmap = try {
                                var orientation = ExifInterface.ORIENTATION_NORMAL
                                context.contentResolver.openInputStream(uri)?.use { exifStream ->
                                    val exif = ExifInterface(exifStream)
                                    orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                                }
                                val matrix = Matrix()
                                when (orientation) {
                                    ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                                    ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                                    ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                                }
                                if (!matrix.isIdentity) {
                                    val result = Bitmap.createBitmap(decodedBitmap, 0, 0, decodedBitmap.width, decodedBitmap.height, matrix, true)
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

                            // Scale down if still larger than max dimensions
                            val scaledBitmap = scaleBitmapIfNecessary(rotatedBitmap)
                            
                            val pageInfo = PdfDocument.PageInfo.Builder(scaledBitmap.width, scaledBitmap.height, index + 1).create()
                            val page = pdfDocument.startPage(pageInfo)
                            
                            page.canvas.drawBitmap(scaledBitmap, 0f, 0f, null)
                            pdfDocument.finishPage(page)
                            
                            if (scaledBitmap != rotatedBitmap) {
                                scaledBitmap.recycle()
                            }
                            if (!rotatedBitmap.isRecycled) {
                                rotatedBitmap.recycle()
                            }
                        }
                        
                        withContext(Dispatchers.Main) {
                            _progress.value = ((index + 1) * 100) / imagesCount
                        }
                    }
                    
                    val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    val fileName = "FileKit_Images_$timeStamp.pdf"
                    
                    val (outputStream, _) = fileorganizer.app.utils.StorageUtils.createPdfOutputStream(context, fileName)
                    outputStream.use { stream ->
                        pdfDocument.writeTo(stream)
                        stream.flush()
                    }
                    
                    pdfDocument.close()
                    
                    withContext(Dispatchers.Main) {
                        _resultMessage.value = successMsg.replace("%1\$s", fileName)
                        _selectedImages.value = emptyList()
                    }
                }
            } catch (t: Throwable) {
                t.printStackTrace()
                withContext(Dispatchers.Main) {
                    _resultMessage.value = failMsg.replace("%1\$s", t.message ?: "")
                }
            } finally {
                withContext(Dispatchers.Main) {
                    _isConverting.value = false
                }
            }
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

