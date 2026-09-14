package fileorganizer.app.ui.screens

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DecimalFormat

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val _cacheSize = MutableStateFlow("0 B")
    val cacheSize: StateFlow<String> = _cacheSize.asStateFlow()

    private val _isClearingCache = MutableStateFlow(false)
    val isClearingCache: StateFlow<Boolean> = _isClearingCache.asStateFlow()

    private val _resultMessage = MutableStateFlow<String?>(null)
    val resultMessage: StateFlow<String?> = _resultMessage.asStateFlow()

    init {
        calculateCacheSize()
    }

    fun calculateCacheSize() {
        viewModelScope.launch {
            val size = withContext(Dispatchers.IO) {
                val context = getApplication<Application>()
                val cacheDir = context.cacheDir
                val externalCacheDir = context.externalCacheDir
                val totalSize = getDirSize(cacheDir) + getDirSize(externalCacheDir)
                formatSize(totalSize)
            }
            _cacheSize.value = size
        }
    }

    fun clearCache(successMessage: String) {
        viewModelScope.launch {
            _isClearingCache.value = true
            _resultMessage.value = null
            withContext(Dispatchers.IO) {
                val context = getApplication<Application>()
                deleteDirContent(context.cacheDir)
                deleteDirContent(context.externalCacheDir)
            }
            calculateCacheSize()
            _isClearingCache.value = false
            _resultMessage.value = successMessage
        }
    }

    fun clearResultMessage() {
        _resultMessage.value = null
    }

    private fun getDirSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0
        var size: Long = 0
        val files = dir.listFiles()
        if (files != null) {
            for (file in files) {
                size += if (file.isDirectory) {
                    getDirSize(file)
                } else {
                    file.length()
                }
            }
        }
        return size
    }

    private fun deleteDirContent(dir: File?) {
        if (dir == null || !dir.exists()) return
        val files = dir.listFiles()
        if (files != null) {
            for (file in files) {
                if (file.isDirectory) {
                    deleteDirContent(file)
                    file.delete()
                } else {
                    file.delete()
                }
            }
        }
    }

    private fun formatSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return DecimalFormat("#,##0.#").format(size / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
    }
}

