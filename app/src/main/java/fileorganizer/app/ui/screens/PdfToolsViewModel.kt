package fileorganizer.app.ui.screens

import android.app.Application
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class PdfToolsViewModel(application: Application) : AndroidViewModel(application) {

    init {
        PDFBoxResourceLoader.init(application)
    }

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _resultMessage = MutableStateFlow<String?>(null)
    val resultMessage: StateFlow<String?> = _resultMessage.asStateFlow()

    fun clearResult() {
        _resultMessage.value = null
    }

    fun setResultMessage(message: String) {
        _resultMessage.value = message
    }

    fun mergePdfs(uris: List<Uri>, successMsg: String, failMsg: String, minFilesMsg: String) {
        if (uris.size < 2) {
            _resultMessage.value = minFilesMsg
            return
        }

        _isLoading.value = true
        _resultMessage.value = null

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val context = getApplication<Application>()
                    val merger = PDFMergerUtility()
                    val fileName = "Merged_PDF_${System.currentTimeMillis()}.pdf"
                    val (outputStream, _) = fileorganizer.app.utils.StorageUtils.createPdfOutputStream(context, fileName)
                    merger.destinationStream = outputStream

                    uris.forEach { uri ->
                        val inputStream = context.contentResolver.openInputStream(uri)
                        if (inputStream != null) {
                            merger.addSource(inputStream)
                        }
                    }

                    merger.mergeDocuments(com.tom_roush.pdfbox.io.MemoryUsageSetting.setupMainMemoryOnly())
                    outputStream.flush()
                    outputStream.close()
                    successMsg.replace("%1\$s", fileName)
                } catch (t: Throwable) {
                    t.printStackTrace()
                    failMsg.replace("%1\$s", t.message ?: "")
                }
            }
            _isLoading.value = false
            _resultMessage.value = result
        }
    }

    fun deletePages(uri: Uri, pagesString: String, successMsg: String, failMsg: String, emptyMsg: String, invalidMsg: String, allPagesMsg: String) {
        if (pagesString.isBlank()) {
            _resultMessage.value = emptyMsg
            return
        }

        _isLoading.value = true
        _resultMessage.value = null

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val context = getApplication<Application>()
                    val pagesToRemove = parsePagesString(pagesString)
                    if (pagesToRemove.isEmpty()) {
                        return@withContext invalidMsg
                    }

                    val tempFile = File.createTempFile("temp_pdf", ".pdf", context.cacheDir)
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }

                    val document = PDDocument.load(tempFile)
                    val totalPages = document.numberOfPages
                    val validPagesToRemove = pagesToRemove.filter { it in 1..totalPages }
                    
                    if (validPagesToRemove.size == totalPages) {
                        document.close()
                        tempFile.delete()
                        return@withContext allPagesMsg
                    }

                    val fileName = "Edited_PDF_${System.currentTimeMillis()}.pdf"
                    val (outputStream, _) = fileorganizer.app.utils.StorageUtils.createPdfOutputStream(context, fileName)

                    val merger = PDFMergerUtility()
                    merger.destinationStream = outputStream

                    val splitter = com.tom_roush.pdfbox.multipdf.Splitter()
                    val splitDocs = splitter.split(document)

                    val pagesToKeep = (1..totalPages).filter { it !in validPagesToRemove }

                    for (pageNum in pagesToKeep) {
                        val singlePageDoc = splitDocs[pageNum - 1]
                        val baos = java.io.ByteArrayOutputStream()
                        singlePageDoc.save(baos)
                        singlePageDoc.close()
                        
                        val bais = java.io.ByteArrayInputStream(baos.toByteArray())
                        merger.addSource(bais)
                    }

                    merger.mergeDocuments(com.tom_roush.pdfbox.io.MemoryUsageSetting.setupMainMemoryOnly())
                    outputStream.flush()
                    outputStream.close()
                    document.close()
                    tempFile.delete()

                    successMsg.replace("%1\$s", fileName)
                } catch (t: Throwable) {
                    t.printStackTrace()
                    failMsg.replace("%1\$s", t.message ?: "")
                }
            }
            _isLoading.value = false
            _resultMessage.value = result
        }
    }

    private fun parsePagesString(input: String): List<Int> {
        val result = mutableSetOf<Int>()
        try {
            val parts = input.split(",")
            for (part in parts) {
                val trimmed = part.trim()
                if (trimmed.contains("-")) {
                    val rangeParts = trimmed.split("-")
                    if (rangeParts.size == 2) {
                        val start = rangeParts[0].trim().toInt()
                        val end = rangeParts[1].trim().toInt()
                        if (start <= end) {
                            for (i in start..end) result.add(i)
                        } else {
                            for (i in end..start) result.add(i) // Handle RTL flipped input
                        }
                    }
                } else if (trimmed.isNotEmpty()) {
                    result.add(trimmed.toInt())
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result.toList()
    }
}

