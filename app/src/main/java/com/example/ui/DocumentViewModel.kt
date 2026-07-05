package com.example.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.Document
import com.example.data.DocumentPage
import com.example.data.DocumentRepository
import com.example.util.CloudSyncManager
import com.example.util.ImageProcessing
import com.example.util.PdfGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.*

class DocumentViewModel(private val repository: DocumentRepository) : ViewModel() {

    // Document List States
    val allDocuments: StateFlow<List<Document>> = repository.allDocuments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val filteredDocuments: StateFlow<List<Document>> = combine(allDocuments, searchQuery) { docs, query ->
        if (query.isBlank()) {
            docs
        } else {
            docs.filter { it.title.contains(query, ignoreCase = true) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active Scanning Session Page States
    // Holds the raw, un-cropped captured pages for the current document scanning flow
    private val _activePages = MutableStateFlow<List<TempPage>>(emptyList())
    val activePages: StateFlow<List<TempPage>> = _activePages.asStateFlow()

    // Screen navigation/editing states
    private val _editingPageIndex = MutableStateFlow<Int>(-1)
    val editingPageIndex: StateFlow<Int> = _editingPageIndex.asStateFlow()

    // Sync state exposures
    val syncStatus: StateFlow<CloudSyncManager.SyncState> = CloudSyncManager.syncStatus
    val syncLogs: StateFlow<List<String>> = CloudSyncManager.syncLogs

    // Configuration preferences
    private val _endpoint = MutableStateFlow("")
    val endpoint: StateFlow<String> = _endpoint.asStateFlow()

    private val _provider = MutableStateFlow("TNSCURE")
    val provider: StateFlow<String> = _provider.asStateFlow()

    private val _autoSync = MutableStateFlow(true)
    val autoSync: StateFlow<Boolean> = _autoSync.asStateFlow()

    fun initPrefs(context: Context) {
        _endpoint.value = CloudSyncManager.getEndpoint(context)
        _provider.value = CloudSyncManager.getProvider(context)
        _autoSync.value = CloudSyncManager.isAutoSyncEnabled(context)
    }

    fun saveConfig(context: Context, provider: String, endpoint: String, token: String, autoSync: Boolean) {
        CloudSyncManager.saveConfig(context, provider, endpoint, token, autoSync)
        _endpoint.value = endpoint
        _provider.value = provider
        _autoSync.value = autoSync
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // Temporary captured Page Model
    data class TempPage(
        val originalUri: Uri,
        val rotation: Int = 0,
        val filter: String = "ORIGINAL",
        // Normalized points (0f..1f) representing corners on the Canvas
        val topLeft: PointF = PointF(0.1f, 0.1f),
        val topRight: PointF = PointF(0.9f, 0.1f),
        val bottomRight: PointF = PointF(0.9f, 0.9f),
        val bottomLeft: PointF = PointF(0.1f, 0.9f)
    )

    fun startNewScan() {
        _activePages.value = emptyList()
        _editingPageIndex.value = -1
    }

    fun addTempPage(uri: Uri) {
        val list = _activePages.value.toMutableList()
        list.add(TempPage(uri))
        _activePages.value = list
        _editingPageIndex.value = list.size - 1
    }

    fun updateCropPoints(index: Int, tl: PointF, tr: PointF, br: PointF, bl: PointF) {
        val list = _activePages.value.toMutableList()
        if (index in list.indices) {
            list[index] = list[index].copy(topLeft = tl, topRight = tr, bottomRight = br, bottomLeft = bl)
            _activePages.value = list
        }
    }

    fun rotateActivePage(index: Int) {
        val list = _activePages.value.toMutableList()
        if (index in list.indices) {
            val newRotation = (list[index].rotation + 90) % 360
            list[index] = list[index].copy(rotation = newRotation)
            _activePages.value = list
        }
    }

    fun applyFilterToActivePage(index: Int, filter: String) {
        val list = _activePages.value.toMutableList()
        if (index in list.indices) {
            list[index] = list[index].copy(filter = filter)
            _activePages.value = list
        }
    }

    fun removeActivePage(index: Int) {
        val list = _activePages.value.toMutableList()
        if (index in list.indices) {
            list.removeAt(index)
            _activePages.value = list
            _editingPageIndex.value = if (list.isNotEmpty()) 0 else -1
        }
    }

    fun reorderPages(fromIndex: Int, toIndex: Int) {
        val list = _activePages.value.toMutableList()
        if (fromIndex in list.indices && toIndex in list.indices) {
            Collections.swap(list, fromIndex, toIndex)
            _activePages.value = list
        }
    }

    fun setEditingPageIndex(index: Int) {
        _editingPageIndex.value = index
    }

    fun triggerManualSync(context: Context) {
        viewModelScope.launch {
            CloudSyncManager.performSync(context, repository)
        }
    }

    /**
     * Compiles and processes all pages, applies crop coordinates, applies selected image filters,
     * persists them locally, saves document and pages meta to SQLite database via Room, generates
     * the offline PDF, and kicks off cloud sync asynchronously!
     */
    fun saveScanSession(context: Context, documentTitle: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val pages = _activePages.value
            if (pages.isEmpty()) return@launch

            withContext(Dispatchers.IO) {
                // 1. Create and save Document row
                val finalTitle = documentTitle.trim().ifEmpty { "Scan ${UUID.randomUUID().toString().take(5)}" }
                val docId = repository.insertDocument(Document(title = finalTitle))

                val savedPagePaths = mutableListOf<String>()

                // 2. Process each temporary page: Crop perspective, rotate, filter, save Jpeg
                for ((index, page) in pages.withIndex()) {
                    val originalBitmap = loadBitmapFromUri(context, page.originalUri) ?: continue
                    
                    // a. Perspective Warp Crop
                    val cropped = ImageProcessing.warpPerspective(
                        originalBitmap,
                        page.topLeft, page.topRight, page.bottomRight, page.bottomLeft
                    )
                    originalBitmap.recycle()

                    // b. Rotation
                    val rotated = ImageProcessing.rotateBitmap(cropped, page.rotation.toFloat())
                    if (rotated != cropped) cropped.recycle()

                    // c. Filter Enhancement
                    val filtered = ImageProcessing.applyFilter(rotated, page.filter)
                    if (filtered != rotated) rotated.recycle()

                    // d. Save processed bitmap to file system
                    val savedPath = ImageProcessing.saveBitmapToFile(context, filtered)
                    filtered.recycle()

                    // Save original file placeholder or just use saved path
                    val originalPlaceholderPath = saveOriginalUriToTempFile(context, page.originalUri) ?: savedPath

                    // e. Save Page to Room Database
                    repository.insertPage(
                        DocumentPage(
                            documentId = docId,
                            originalImagePath = originalPlaceholderPath,
                            croppedImagePath = savedPath,
                            rotationDegrees = page.rotation,
                            filterType = page.filter,
                            pageIndex = index
                        )
                    )

                    savedPagePaths.add(savedPath)
                }

                // 3. Generate offline PDF document
                val pdfPath = PdfGenerator.generatePdf(context, savedPagePaths, finalTitle)

                // 4. Update Document row with PDF path
                val updatedDoc = repository.getDocumentById(docId)?.copy(pdfPath = pdfPath)
                if (updatedDoc != null) {
                    repository.updateDocument(updatedDoc)
                }

                // 5. If auto-sync is active, start sync in background
                if (CloudSyncManager.isAutoSyncEnabled(context)) {
                    viewModelScope.launch {
                        CloudSyncManager.performSync(context, repository)
                    }
                }
            }

            // Cleanup active list and trigger callback
            startNewScan()
            onSuccess()
        }
    }

    fun deleteDocument(context: Context, doc: Document) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                // Delete associated local files to free space (PDF and images)
                doc.pdfPath?.let { path ->
                    val file = File(path)
                    if (file.exists()) file.delete()
                }

                // Room has CASCADE configured on Foreign Keys, deleting doc automatically deletes child page rows
                val pages = repository.getPagesForDocumentSync(doc.id)
                for (page in pages) {
                    page.croppedImagePath?.let { path ->
                        val file = File(path)
                        if (file.exists()) file.delete()
                    }
                    val originalFile = File(page.originalImagePath)
                    if (originalFile.exists()) originalFile.delete()
                }

                repository.deleteDocument(doc)
            }
        }
    }

    fun renameDocument(doc: Document, newTitle: String) {
        viewModelScope.launch {
            if (newTitle.trim().isNotEmpty()) {
                repository.updateDocument(doc.copy(title = newTitle.trim(), isSynced = false))
            }
        }
    }

    private fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(inputStream)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun saveOriginalUriToTempFile(context: Context, uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val directory = File(context.filesDir, "raw_scans").apply { mkdirs() }
            val file = File(directory, "RAW_${UUID.randomUUID()}.jpg")
            val outputStream = FileOutputStream(file)
            inputStream.copyTo(outputStream)
            inputStream.close()
            outputStream.close()
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

@Suppress("UNCHECKED_CAST")
class DocumentViewModelFactory(private val repository: DocumentRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DocumentViewModel::class.java)) {
            return DocumentViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
