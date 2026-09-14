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

data class LargeFileItem(
    val id: Long,
    val name: String,
    val size: Long,
    val uri: Uri,
    val path: String
)

class LargeFilesViewModel(application: Application) : AndroidViewModel(application) {
    private val _allFiles = MutableStateFlow<List<LargeFileItem>>(emptyList())
    
    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    private val _pagedFiles = MutableStateFlow<List<LargeFileItem>>(emptyList())
    val pagedFiles: StateFlow<List<LargeFileItem>> = _pagedFiles.asStateFlow()

    private val _selectedFileIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedFileIds: StateFlow<Set<Long>> = _selectedFileIds.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _resultMessage = MutableStateFlow<String?>(null)
    val resultMessage: StateFlow<String?> = _resultMessage.asStateFlow()
    
    fun clearResultMessage() {
        _resultMessage.value = null
    }

    val totalPages: Int
        get() = Math.ceil(_allFiles.value.size.toDouble() / pageSize).toInt()

    private val pageSize = 10

    fun loadFiles() {
        viewModelScope.launch {
            _isLoading.value = true
            val files = withContext(Dispatchers.IO) {
                queryLargeFiles()
            }
            _allFiles.value = files
            _currentPage.value = 0
            updatePagedFiles()
            _isLoading.value = false
        }
    }

    private fun queryLargeFiles(): List<LargeFileItem> {
        val fileList = mutableListOf<LargeFileItem>()
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATA
        )

        // Looking for files larger than 10MB to include images and videos
        val selection = "${MediaStore.Files.FileColumns.SIZE} > ?"
        val selectionArgs = arrayOf((10 * 1024 * 1024).toString())
        val sortOrder = "${MediaStore.Files.FileColumns.SIZE} DESC"

        val context = getApplication<Application>().applicationContext
        
        val urisToQuery = listOf(
            MediaStore.Files.getContentUri("external"),
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        )

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

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idColumn)
                        val name = cursor.getString(nameColumn) ?: "Unknown"
                        val size = cursor.getLong(sizeColumn)
                        val data = cursor.getString(dataColumn) ?: ""
                        val uri = ContentUris.withAppendedId(collection, id)

                        if (fileList.none { it.path == data }) {
                            fileList.add(LargeFileItem(id, name, size, uri, data))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        fileList.sortByDescending { it.size }
        return fileList
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

    fun selectAllInCurrentPage() {
        val currentSelected = _selectedFileIds.value.toMutableSet()
        val currentPageFileIds = _pagedFiles.value.map { it.id }
        
        val allSelected = currentPageFileIds.isNotEmpty() && currentPageFileIds.all { currentSelected.contains(it) }
        
        if (allSelected) {
            currentPageFileIds.forEach { currentSelected.remove(it) }
        } else {
            currentSelected.addAll(currentPageFileIds)
        }
        
        _selectedFileIds.value = currentSelected
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

