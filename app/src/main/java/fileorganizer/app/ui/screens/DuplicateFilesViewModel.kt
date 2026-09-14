package fileorganizer.app.ui.screens

import android.app.Application
import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class DuplicateFileItem(
    val id: Long,
    val name: String,
    val size: Long,
    val uri: Uri,
    val path: String,
    val dateAdded: Long
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
        viewModelScope.launch {
            _progress.value = 0f
            _isLoading.value = true
            val duplicates = withContext(Dispatchers.IO) {
                queryDuplicateFiles()
            }
            _allFiles.value = duplicates
            _currentPage.value = 0
            
            // Auto-select duplicates for deletion (keep the oldest one unselected)
            val toSelect = mutableSetOf<Long>()
            // group by size and name again to find which to select
            val groups = duplicates.groupBy { "${it.size}_${getBaseName(it.name)}" }
            for (group in groups.values) {
                // sort by date added, oldest first
                val sorted = group.sortedBy { it.dateAdded }
                if (sorted.size > 1) {
                    // add all except the first (oldest) to the selection
                    sorted.drop(1).forEach { toSelect.add(it.id) }
                }
            }
            _selectedFileIds.value = toSelect

            updatePagedFiles()
            _isLoading.value = false
        }
    }

    private fun queryDuplicateFiles(): List<DuplicateFileItem> {
        val fileList = mutableListOf<DuplicateFileItem>()
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
            e.printStackTrace()
        }

        var processedFiles = 0

        try {
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

                        if (fileList.none { it.path == data }) {
                            fileList.add(DuplicateFileItem(id, name, size, uri, data, dateAdded))
                        }
                        
                        processedFiles++
                        if (totalFiles > 0 && processedFiles % 50 == 0) { // Update every 50 items to reduce overhead
                            _progress.value = processedFiles.toFloat() / totalFiles.toFloat()
                        }
                    }
                }
            }
            if (totalFiles > 0) _progress.value = 1f
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        // Group by size and base name (ignoring common duplicate suffixes like " (1)" or "-1")
        val grouped = fileList.groupBy { "${it.size}_${getBaseName(it.name)}" }
        // Filter to only groups that have more than 1 file
        val duplicates = grouped.filter { it.value.size > 1 }.values.flatten()
        
        // Sort by size descending, then by name so duplicates are next to each other
        return duplicates.sortedWith(compareByDescending<DuplicateFileItem> { it.size }.thenBy { it.name })
    }

    private fun getBaseName(name: String): String {
        return name.replace(Regex("""(?: \(\d+\)|-\d+)(?=\.[^.]+$|$)"""), "")
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
            current.add(fileId)
        }
        _selectedFileIds.value = current
    }

    fun clearSelection() {
        _selectedFileIds.value = emptySet()
    }

    fun deleteSelectedFiles(successMsg: String, failMsg: String, permissionFailMsg: String) {
        viewModelScope.launch {
            val selected = _selectedFileIds.value
            if (selected.isEmpty()) return@launch

            val filesToDelete = _allFiles.value.filter { it.id in selected }
            val context = getApplication<Application>().applicationContext
            var successCount = 0
            var permissionDenied = false

            withContext(Dispatchers.IO) {
                filesToDelete.forEach { fileItem ->
                    try {
                        val file = File(fileItem.path)
                        if (file.exists()) {
                            file.delete()
                        }
                        // Also remove from MediaStore
                        context.contentResolver.delete(fileItem.uri, null, null)
                        successCount++
                    } catch (e: SecurityException) {
                        e.printStackTrace()
                        permissionDenied = true
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            clearSelection()
            loadFiles()
            
            if (permissionDenied && successCount == 0) {
                _resultMessage.value = permissionFailMsg
            } else if (successCount > 0) {
                _resultMessage.value = successMsg.replace("%1\$d", successCount.toString())
            } else {
                _resultMessage.value = failMsg
            }
        }
    }
}

