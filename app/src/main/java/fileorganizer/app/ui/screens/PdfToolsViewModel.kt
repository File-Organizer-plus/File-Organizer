package fileorganizer.app.ui.screens

import android.app.Application
import android.net.Uri
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
                var tempFile: File? = null
                try {
                    val context = getApplication<Application>()
                    val pagesToRemove = parsePagesString(pagesString)
                        ?: return@withContext invalidMsg

                    tempFile = File.createTempFile("temp_pdf", ".pdf", context.cacheDir)
                    val inputStream = context.contentResolver.openInputStream(uri)
                        ?: return@withContext failMsg.replace("%1\$s", "Unable to open PDF")

                    inputStream.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }

                    PDDocument.load(tempFile).use { document ->
                        val totalPages = document.numberOfPages

                        // Reject the entire request if even one page number is outside
                        // the selected PDF. Never silently ignore invalid pages and
                        // perform a partial deletion.
                        if (totalPages <= 0 || pagesToRemove.any { it !in 1..totalPages }) {
                            return@withContext invalidMsg
                        }

                        if (pagesToRemove.size >= totalPages) {
                            return@withContext allPagesMsg
                        }

                        // Remove from highest page number to lowest so indexes do not
                        // shift while pages are being deleted.
                        pagesToRemove.sortedDescending().forEach { pageNumber ->
                            document.removePage(pageNumber - 1)
                        }

                        if (document.numberOfPages != totalPages - pagesToRemove.size) {
                            throw IllegalStateException("PDF page deletion could not be verified")
                        }

                        val fileName = "Edited_PDF_${System.currentTimeMillis()}.pdf"
                        val (outputStream, outputUri) = fileorganizer.app.utils.StorageUtils.createPdfOutputStream(context, fileName)
                        outputStream.use { output ->
                            document.save(output)
                            output.flush()
                        }

                        // Verify that the saved PDF can actually be opened and contains
                        // the expected number of pages before reporting success.
                        val savedInput = outputUri?.let { context.contentResolver.openInputStream(it) }
                            ?: throw IllegalStateException("Saved PDF could not be reopened")

                        savedInput.use { savedStream ->
                            PDDocument.load(savedStream).use { savedDocument ->
                                if (savedDocument.numberOfPages != totalPages - pagesToRemove.size) {
                                    throw IllegalStateException("Saved PDF page count does not match")
                                }
                            }
                        }

                        successMsg.replace("%1\$s", fileName)
                    }
                } catch (t: Throwable) {
                    t.printStackTrace()
                    failMsg.replace("%1\$s", t.message ?: "")
                } finally {
                    tempFile?.delete()
                }
            }
            _isLoading.value = false
            _resultMessage.value = result
        }
    }

    /**
     * Parses comma-separated pages and ranges such as "1,3,5-7".
     * Returns null when any part of the input is malformed. Reversed ranges are
     * still accepted to preserve the existing RTL-input behavior.
     */
    private fun parsePagesString(input: String): Set<Int>? {
        val result = linkedSetOf<Int>()
        val parts = input.split(",")

        if (parts.isEmpty() || parts.any { it.trim().isEmpty() }) {
            return null
        }

        for (part in parts) {
            val trimmed = part.trim()
            if (trimmed.contains("-")) {
                val rangeParts = trimmed.split("-")
                if (rangeParts.size != 2) return null

                val start = rangeParts[0].trim().toIntOrNull() ?: return null
                val end = rangeParts[1].trim().toIntOrNull() ?: return null
                if (start <= 0 || end <= 0) return null

                val range = if (start <= end) start..end else end..start
                result.addAll(range)
            } else {
                val page = trimmed.toIntOrNull() ?: return null
                if (page <= 0) return null
                result.add(page)
            }
        }

        return result.takeIf { it.isNotEmpty() }
    }
}
