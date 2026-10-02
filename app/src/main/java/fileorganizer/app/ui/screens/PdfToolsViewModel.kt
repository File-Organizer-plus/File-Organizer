package fileorganizer.app.ui.screens

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
                val context = getApplication<Application>()
                val tempFiles = mutableListOf<File>()
                var outputUri: Uri? = null

                try {
                    val merger = PDFMergerUtility()
                    var expectedPageCount = 0

                    // Copy and validate every selected PDF before creating the output.
                    // If one input cannot be opened or parsed, stop the whole merge
                    // instead of silently skipping it and producing an incomplete file.
                    uris.forEachIndexed { index, uri ->
                        val inputStream = context.contentResolver.openInputStream(uri)
                            ?: throw IllegalStateException("Unable to open PDF ${index + 1}")

                        val tempFile = File.createTempFile("merge_pdf_${index}_", ".pdf", context.cacheDir)
                        tempFiles.add(tempFile)

                        inputStream.use { input ->
                            tempFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }

                        // Large PDFs should not be parsed entirely in RAM just for
                        // validation. Keep up to 32 MiB in memory and spill the rest
                        // into the app cache when necessary.
                        PDDocument.load(
                            tempFile,
                            createMergeMemoryUsage(context)
                        ).use { document ->
                            if (document.numberOfPages <= 0) {
                                throw IllegalStateException("PDF ${index + 1} has no pages")
                            }
                            expectedPageCount += document.numberOfPages
                        }

                        merger.addSource(tempFile)
                    }

                    val fileName = "Merged_PDF_${System.currentTimeMillis()}.pdf"
                    val (outputStream, createdUri) = fileorganizer.app.utils.StorageUtils.createPdfOutputStream(context, fileName)
                    outputUri = createdUri
                    merger.destinationStream = outputStream

                    outputStream.use { output ->
                        // Use mixed memory instead of unrestricted main-memory-only.
                        // Normal files stay fast in RAM; large merges can spill to cache
                        // instead of risking an OutOfMemoryError.
                        merger.mergeDocuments(createMergeMemoryUsage(context))
                        output.flush()
                    }

                    val savedUri = outputUri
                        ?: throw IllegalStateException("Merged PDF could not be reopened")

                    if (!verifySavedPdfWithRetry(
                            context = context,
                            uri = savedUri,
                            expectedPageCount = expectedPageCount,
                            useMixedMemory = true
                        )
                    ) {
                        throw IllegalStateException("Merged PDF page count does not match")
                    }

                    successMsg.replace("%1\$s", fileName)
                } catch (t: Throwable) {
                    t.printStackTrace()

                    // Remove an incomplete output when possible so a failed merge does
                    // not leave behind a file that looks valid to the user.
                    outputUri?.let { uri ->
                        try {
                            if (uri.scheme == "file") {
                                uri.path?.let { File(it).delete() }
                            } else {
                                context.contentResolver.delete(uri, null, null)
                            }
                        } catch (cleanupError: Throwable) {
                            cleanupError.printStackTrace()
                        }
                    }

                    failMsg.replace("%1\$s", t.message ?: "")
                } finally {
                    tempFiles.forEach { tempFile ->
                        try {
                            tempFile.delete()
                        } catch (cleanupError: Throwable) {
                            cleanupError.printStackTrace()
                        }
                    }
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
                val context = getApplication<Application>()
                var tempFile: File? = null
                var outputUri: Uri? = null

                try {
                    // Parse only the compact ranges first. Do not expand a value such
                    // as 1-9999999 into millions of integers before the PDF is opened.
                    val pageRanges = parsePageRanges(pagesString)
                        ?: return@withContext invalidMsg

                    tempFile = File.createTempFile("temp_pdf", ".pdf", context.cacheDir)
                    val inputStream = context.contentResolver.openInputStream(uri)
                        ?: return@withContext failMsg.replace("%1\$s", "Unable to open PDF")

                    inputStream.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }

                    PDDocument.load(
                        tempFile,
                        createMergeMemoryUsage(context)
                    ).use { document ->
                        val totalPages = document.numberOfPages

                        // Validate range boundaries before expanding them. A huge range
                        // outside the real document is rejected immediately with no
                        // large allocation in memory.
                        if (totalPages <= 0 || pageRanges.any {
                                it.first !in 1..totalPages || it.last !in 1..totalPages
                            }
                        ) {
                            return@withContext invalidMsg
                        }

                        val pagesToRemove = linkedSetOf<Int>()
                        pageRanges.forEach { range -> pagesToRemove.addAll(range) }

                        if (pagesToRemove.size >= totalPages) {
                            return@withContext allPagesMsg
                        }

                        // Remove from highest page number to lowest so indexes do not
                        // shift while pages are being deleted.
                        pagesToRemove.sortedDescending().forEach { pageNumber ->
                            document.removePage(pageNumber - 1)
                        }

                        val expectedPageCount = totalPages - pagesToRemove.size
                        if (document.numberOfPages != expectedPageCount) {
                            throw IllegalStateException("PDF page deletion could not be verified")
                        }

                        val fileName = "Edited_PDF_${System.currentTimeMillis()}.pdf"
                        val (outputStream, createdUri) = fileorganizer.app.utils.StorageUtils.createPdfOutputStream(context, fileName)
                        outputUri = createdUri

                        outputStream.use { output ->
                            document.save(output)
                            output.flush()
                        }

                        val savedUri = outputUri
                            ?: throw IllegalStateException("Saved PDF could not be reopened")

                        if (!verifySavedPdfWithRetry(
                                context = context,
                                uri = savedUri,
                                expectedPageCount = expectedPageCount,
                                useMixedMemory = true
                            )
                        ) {
                            throw IllegalStateException("Saved PDF page count does not match")
                        }

                        successMsg.replace("%1\$s", fileName)
                    }
                } catch (t: Throwable) {
                    t.printStackTrace()

                    // If output creation or final verification fails, remove the
                    // incomplete edited PDF so the user is not left with a bad file.
                    outputUri?.let { uri ->
                        try {
                            if (uri.scheme == "file") {
                                uri.path?.let { File(it).delete() }
                            } else {
                                context.contentResolver.delete(uri, null, null)
                            }
                        } catch (cleanupError: Throwable) {
                            cleanupError.printStackTrace()
                        }
                    }

                    failMsg.replace("%1\$s", t.message ?: "")
                } finally {
                    tempFile?.delete()
                }
            }
            _isLoading.value = false
            _resultMessage.value = result
        }
    }

    private fun createMergeMemoryUsage(context: Application): MemoryUsageSetting {
        return MemoryUsageSetting
            .setupMixed(32L * 1024L * 1024L)
            .setTempDir(context.cacheDir)
    }

    private suspend fun verifySavedPdfWithRetry(
        context: Application,
        uri: Uri,
        expectedPageCount: Int,
        useMixedMemory: Boolean = false
    ): Boolean {
        if (verifySavedPdfOnce(context, uri, expectedPageCount, useMixedMemory)) {
            return true
        }

        // Only wait when the first verification fails. Normal successful saves
        // have no added delay.
        delay(100)
        return verifySavedPdfOnce(context, uri, expectedPageCount, useMixedMemory)
    }

    private fun verifySavedPdfOnce(
        context: Application,
        uri: Uri,
        expectedPageCount: Int,
        useMixedMemory: Boolean = false
    ): Boolean {
        return try {
            val savedInput = if (uri.scheme == "file") {
                uri.path?.let { File(it).inputStream() }
            } else {
                context.contentResolver.openInputStream(uri)
            } ?: return false

            savedInput.use { savedStream ->
                val document = if (useMixedMemory) {
                    PDDocument.load(savedStream, createMergeMemoryUsage(context))
                } else {
                    PDDocument.load(savedStream)
                }

                document.use { savedDocument ->
                    savedDocument.numberOfPages == expectedPageCount
                }
            }
        } catch (t: Throwable) {
            t.printStackTrace()
            false
        }
    }

    /**
     * Parses comma-separated pages and ranges such as "1,3,5-7" without expanding
     * ranges into individual page numbers. Returns null when any part is malformed.
     * Reversed ranges remain accepted to preserve the existing RTL-input behavior.
     */
    private fun parsePageRanges(input: String): List<IntRange>? {
        val ranges = mutableListOf<IntRange>()
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

                ranges.add(if (start <= end) start..end else end..start)
            } else {
                val page = trimmed.toIntOrNull() ?: return null
                if (page <= 0) return null
                ranges.add(page..page)
            }
        }

        return ranges.takeIf { it.isNotEmpty() }
    }
}
