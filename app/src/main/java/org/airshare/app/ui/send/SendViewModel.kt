package org.airshare.app.ui.send

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.airshare.app.data.model.MediaCategory
import org.airshare.app.data.model.TransferFile
import org.airshare.app.data.repository.FileRepository
import org.airshare.app.engine.network.discovery.NsdDiscoveryManager
import org.airshare.app.engine.network.p2p.WifiDirectManager

class SendViewModel(application: Application) : AndroidViewModel(application) {

    private val fileRepository = FileRepository(application)
    val wifiDirectManager = WifiDirectManager(application)
    val nsdDiscoveryManager = NsdDiscoveryManager(
        application,
        "AirShare_${System.currentTimeMillis() % 10000}"
    )

    private val _selectedFiles = MutableStateFlow<Set<TransferFile>>(emptySet())
    val selectedFiles: StateFlow<Set<TransferFile>> = _selectedFiles.asStateFlow()

    private val _categoryFilesMap = MutableStateFlow<Map<MediaCategory, List<TransferFile>>>(emptyMap())
    val categoryFilesMap: StateFlow<Map<MediaCategory, List<TransferFile>>> = _categoryFilesMap.asStateFlow()

    private val _isLoadingCategory = MutableStateFlow<Map<MediaCategory, Boolean>>(emptyMap())
    val isLoadingCategory: StateFlow<Map<MediaCategory, Boolean>> = _isLoadingCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun setSearchQuery(query: String) {
        _searchQuery.value = query.trim()
    }

    fun toggleFileSelection(file: TransferFile) {
        val current = _selectedFiles.value.toMutableSet()
        val existing = current.find { it.id == file.id }
        if (existing != null) {
            current.remove(existing)
        } else {
            current.add(file)
        }
        _selectedFiles.value = current
    }

    fun selectAllForCategory(category: MediaCategory) {
        val files = _categoryFilesMap.value[category] ?: return
        val current = _selectedFiles.value.toMutableSet()
        current.addAll(files)
        _selectedFiles.value = current
    }

    fun clearSelection() {
        _selectedFiles.value = emptySet()
    }

    fun loadCategory(category: MediaCategory) {
        if (_categoryFilesMap.value[category] != null) return

        _isLoadingCategory.value = _isLoadingCategory.value.toMutableMap().apply { put(category, true) }

        viewModelScope.launch {
            val list = fileRepository.getFilesForCategory(category)
            _categoryFilesMap.value = _categoryFilesMap.value.toMutableMap().apply { put(category, list) }
            _isLoadingCategory.value = _isLoadingCategory.value.toMutableMap().apply { put(category, false) }
        }
    }

    fun startDiscovery() {
        wifiDirectManager.register()
        wifiDirectManager.discoverPeers()
        nsdDiscoveryManager.startDiscovery()
    }

    fun stopDiscovery() {
        wifiDirectManager.unregister()
        nsdDiscoveryManager.stopDiscovery()
    }

    override fun onCleared() {
        super.onCleared()
        stopDiscovery()
    }
}
