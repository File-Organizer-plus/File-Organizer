package fileorganizer.app.ui.screens

import android.app.Application
import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fileorganizer.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

data class DuplicateFileItem(
    val id: Long,
    val name: String,
    val size: Long,
    val uri: Uri,
    val path: String,
    val dateAdded: Long,
    val contentHash: String = ""
)

class DuplicateFilesViewModel(application: Application) : AndroidViewModel(application) {
    private val _allFiles = MutableStateFlow<List<DuplicateFileItem>>(emptyList())

    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    private val _pagedFiles = MutableStateFlow<List<DuplicateFileItem>>(emptyList())
    val pagedFiles: StateFlow<List<DuplicateFileItem>> = _pagedFiles.asStateFlow()

    private val _selectedFileIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedFileIds: StateFlow<Set<Long>> = _selectedFileIds.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _searchFailed = MutableStateFlow(false)
    val searchFailed: StateFlow<Boolean> = _searchFailed.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _resultMessage = MutableStateFlow<String?>(null)
    val resultMessage: StateFlow<String?> = _resultMessage.asStateFlow()

    fun clearResultMessage() {
        _resultMessage.value = null
    }

    val totalPages: Int
        get() = Math.ceil(_allFiles.value.size.toDouble() / pageSize).toInt()

    private val pageSize = 20 // increased page size for duplicates

    fun loadFiles() {
        if (_isBusy.value) return

        _isBusy.value = true
        _progress.value = 0f
        _isLoading.value = true
        _searchFailed.value = false

        viewModelScope.launch {
            try {
                val duplicates = try {
                    withContext(Dispatchers.IO) {
                        queryDuplicateFiles()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    _searchFailed.value = true
                    _progress.value = 0f
                    emptyList()
                }

                _allFiles.value = duplicates
                _currentPage.value = 0

                // Auto-select verified duplicates for deletion (keep the oldest one unselected)
                val toSelect = mutableSetOf<Long>()
                val groups = duplicates.groupBy { it.contentHash }
                for (group in groups.values) {
                    val sorted = group.sortedBy { it.dateAdded }
                    if (sorted.size > 1) {
                        sorted.drop(1).forEach { toSelect.add(it.id) }
                    }
                }
                _selectedFileIds.value = toSelect

                updatePagedFiles()
            } finally {
                _isLoading.value = false
                _isBusy.value = false
            }
        }
    }

    private fun queryDuplicateFiles(): List<DuplicateFileItem> {
        val fileList = mutableListOf<DuplicateFileItem>()
        val seenMediaStoreIds = mutableSetOf<Long>()
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.DATE_ADDED
        )

        // Query all files > 0 bytes
        val selection = "${MediaStore.Files.FileColumns.SIZE} > ?"
        val selectionArgs = arrayOf("0")
        val sortOrder = "${MediaStore.Files.FileColumns.SIZE} DESC"

        val context = getApplication<Application>().applicationContext

        val urisToQuery = listOf(
            MediaStore.Files.getContentUri("external"),
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        )

        var totalFiles = 0
        try {
            for (collection in urisToQuery) {
                context.contentResolver.query(
                    collection,
                    arrayOf(MediaStore.Files.FileColumns._ID),
                    selection,
                    selectionArgs,
                    null
                )?.use { cursor ->
                    totalFiles += cursor.count
                }
            }
        } catch (e: Exception) {
            // Counting is only for progress display. A failure here must not make
            // the actual file search fail.
            e.printStackTrace()
            totalFiles = 0
        }

        var processedFiles = 0

        // This is the actual search. Let query/cursor exceptions propagate so the
        // UI can distinguish a failed search from a successful search with no results.
        for (collection in urisToQuery) {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
                val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn) ?: "Unknown"
                    val size = cursor.getLong(sizeColumn)
                    val data = cursor.getString(dataColumn) ?: ""
                    val dateAdded = cursor.getLong(dateAddedColumn)
                    val uri = ContentUris.withAppendedId(collection, id)

                    // The same MediaStore row can appear through Files, Images, or Video.
                    // De-duplicate by its stable MediaStore ID instead of DATA/path,
                    // which may be blank or unavailable on modern Android versions.
                    if (seenMediaStoreIds.add(id)) {
                        fileList.add(DuplicateFileItem(id, name, size, uri, data, dateAdded))
                    }

                    processedFiles++
                    if (totalFiles > 0 && processedFiles % 50 == 0) {
                        // Scanning files is the first half of duplicate detection.
                        _progress.value = (processedFiles.toFloat() / totalFiles.toFloat()) * 0.5f
                    }
                }
            }
        }

        // Size is only a fast pre-filter. Files are considered duplicates only when
        // their SHA-256 content hash also matches.
        val candidateGroups = fileList.groupBy { it.size }.values.filter { it.size > 1 }
        val candidateCount = candidateGroups.sumOf { it.size }
        var hashedFiles = 0
        val verifiedDuplicates = mutableListOf<DuplicateFileItem>()

        for (group in candidateGroups) {
            val filesByHash = mutableMapOf<String, MutableList<DuplicateFileItem>>()

            for (item in group) {
                // Keep the progress moving while a large candidate file is being read.
                // The hash algorithm itself is unchanged; this callback only reports
                // how much of the current file has already been processed.
                val hash = calculateSha256(item) { fileProgress ->
                    if (candidateCount > 0) {
                        _progress.value = 0.5f +
                            ((hashedFiles.toFloat() + fileProgress) / candidateCount.toFloat()) * 0.5f
                    }
                }
                if (hash != null) {
                    filesByHash.getOrPut(hash) { mutableListOf() }
                        .add(item.copy(contentHash = hash))
                }

                hashedFiles++
                if (candidateCount > 0) {
                    _progress.value = 0.5f + (hashedFiles.toFloat() / candidateCount.toFloat()) * 0.5f
                }
            }

            filesByHash.values
                .filter { it.size > 1 }
                .forEach { verifiedDuplicates.addAll(it) }
        }

        _progress.value = 1f

        // Sort by size descending, then by content hash and name so each verified
        // duplicate group stays together in the list.
        return verifiedDuplicates.sortedWith(
            compareByDescending<DuplicateFileItem> { it.size }
                .thenBy { it.contentHash }
                .thenBy { it.name }
        )
    }

    private fun calculateSha256(
        item: DuplicateFileItem,
        onProgress: ((Float) -> Unit)? = null
    ): String? {
        val context = getApplication<Application>().applicationContext

        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val inputStream = try {
                context.contentResolver.openInputStream(item.uri)
            } catch (e: Exception) {
                null
            }

            val readAndHash: (java.io.InputStream) -> Unit = { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var bytesRead = 0L
                // Report at most about 100 times per large file and never more often
                // than every 256 KiB so progress updates do not slow down hashing.
                val reportStep = maxOf(256L * 1024L, item.size / 100L)
                var nextReport = reportStep

                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                    bytesRead += read

                    if (item.size > 0 && bytesRead >= nextReport) {
                        onProgress?.invoke(
                            (bytesRead.toFloat() / item.size.toFloat()).coerceAtMost(1f)
                        )
                        nextReport = bytesRead + reportStep
                    }
                }

                if (item.size > 0) {
                    onProgress?.invoke(1f)
                }
            }

            if (inputStream != null) {
                inputStream.use(readAndHash)
            } else if (item.path.isNotBlank()) {
                FileInputStream(item.path).use(readAndHash)
            } else {
                return null
            }

            digest.digest().joinToString("") { byte -> "%02x".format(byte) }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun nextPage() {
        if (_currentPage.value < totalPages - 1) {
            _currentPage.value++
            updatePagedFiles()
        }
    }

    fun previousPage() {
        if (_currentPage.value > 0) {
            _currentPage.value--
            updatePagedFiles()
        }
    }

    private fun updatePagedFiles() {
        val start = _currentPage.value * pageSize
        val end = Math.min(start + pageSize, _allFiles.value.size)
        if (start < _allFiles.value.size) {
            _pagedFiles.value = _allFiles.value.subList(start, end)
        } else {
            _pagedFiles.value = emptyList()
        }
    }

    fun toggleSelection(fileId: Long) {
        val current = _selectedFileIds.value.toMutableSet()
        if (current.contains(fileId)) {
            current.remove(fileId)
        } else {
            val file = _allFiles.value.firstOrNull { it.id == fileId } ?: return
            val group = _allFiles.value.filter { it.contentHash == file.contentHash }
            val selectedInGroup = group.count { it.id in current }

            // Never allow every copy in a verified duplicate group to become selected.
            if (selectedInGroup >= group.size - 1) return
            current.add(fileId)
        }
        _selectedFileIds.value = current
    }

    fun clearSelection() {
        _selectedFileIds.value = emptySet()
    }

    private data class DeleteAttemptResult(
        val deleted: Boolean,
        val permissionDenied: Boolean
    )

    private fun deleteFileAndConfirm(fileItem: DuplicateFileItem): DeleteAttemptResult {
        val context = getApplication<Application>().applicationContext
        val file = fileItem.path.takeIf { it.isNotBlank() }?.let(::File)
        val existedOnDiskBefore = file?.exists() == true
        var permissionDenied = false

        try {
            context.contentResolver.delete(fileItem.uri, null, null)
        } catch (e: SecurityException) {
            e.printStackTrace()
            permissionDenied = true
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Fallback for devices/storage locations where deleting through MediaStore
        // does not remove the underlying file itself.
        if (file != null && file.exists()) {
            try {
                file.delete()
            } catch (e: SecurityException) {
                e.printStackTrace()
                permissionDenied = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Do not trust delete() return paths alone. Confirm the file is really gone.
        val confirmedDeleted = if (existedOnDiskBefore) {
            file?.exists() == false
        } else {
            isUriUnavailable(fileItem.uri)
        }

        return DeleteAttemptResult(
            deleted = confirmedDeleted,
            permissionDenied = permissionDenied && !confirmedDeleted
        )
    }

    private fun isUriUnavailable(uri: Uri): Boolean {
        val context = getApplication<Application>().applicationContext
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { false } ?: true
        } catch (e: java.io.FileNotFoundException) {
            true
        } catch (e: SecurityException) {
            false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun deleteSelectedFiles(successMsg: String, failMsg: String, permissionFailMsg: String) {
        if (_isBusy.value) return

        val selected = _selectedFileIds.value
        if (selected.isEmpty()) return

        _isBusy.value = true

        viewModelScope.launch {
            try {
                // Final safety boundary: even if selection state becomes inconsistent,
                // never pass every member of a verified duplicate group to deletion.
                val filesToDelete = _allFiles.value
                    .groupBy { it.contentHash }
                    .values
                    .flatMap { group ->
                        val selectedInGroup = group.filter { it.id in selected }
                        if (selectedInGroup.size >= group.size) {
                            val protectedId = group.minByOrNull { it.dateAdded }?.id
                            selectedInGroup.filterNot { it.id == protectedId }
                        } else {
                            selectedInGroup
                        }
                    }

                var successCount = 0
                var permissionDenied = false

                withContext(Dispatchers.IO) {
                    filesToDelete.forEach { fileItem ->
                        val result = deleteFileAndConfirm(fileItem)
                        if (result.deleted) {
                            successCount++
                        }
                        if (result.permissionDenied) {
                            permissionDenied = true
                        }
                    }
                }

                clearSelection()

                val failedCount = filesToDelete.size - successCount
                _resultMessage.value = when {
                    successCount > 0 && failedCount > 0 -> {
                        getApplication<Application>().getString(
                            R.string.delete_partial_result,
                            successCount,
                            failedCount
                        )
                    }
                    permissionDenied && successCount == 0 -> permissionFailMsg
                    successCount > 0 -> successMsg.replace("%1\$d", successCount.toString())
                    else -> failMsg
                }
            } finally {
                _isBusy.value = false
            }

            loadFiles()
        }
    }
}
