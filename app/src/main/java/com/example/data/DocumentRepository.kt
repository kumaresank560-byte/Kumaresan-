package com.example.data

import kotlinx.coroutines.flow.Flow

class DocumentRepository(private val documentDao: DocumentDao) {
    val allDocuments: Flow<List<Document>> = documentDao.getAllDocuments()

    suspend fun getDocumentById(id: Long): Document? {
        return documentDao.getDocumentById(id)
    }

    fun getPagesForDocument(documentId: Long): Flow<List<DocumentPage>> {
        return documentDao.getPagesForDocument(documentId)
    }

    suspend fun getPagesForDocumentSync(documentId: Long): List<DocumentPage> {
        return documentDao.getPagesForDocumentSync(documentId)
    }

    suspend fun insertDocument(document: Document): Long {
        return documentDao.insertDocument(document)
    }

    suspend fun updateDocument(document: Document) {
        documentDao.updateDocument(document)
    }

    suspend fun deleteDocument(document: Document) {
        documentDao.deleteDocument(document)
    }

    suspend fun deleteDocumentById(id: Long) {
        documentDao.deleteDocumentById(id)
    }

    suspend fun insertPage(page: DocumentPage): Long {
        return documentDao.insertPage(page)
    }

    suspend fun updatePage(page: DocumentPage) {
        documentDao.updatePage(page)
    }

    suspend fun deletePage(page: DocumentPage) {
        documentDao.deletePage(page)
    }

    suspend fun deletePageById(id: Long) {
        documentDao.deletePageById(id)
    }

    suspend fun updatePageIndex(pageId: Long, newIndex: Int) {
        documentDao.updatePageIndex(pageId, newIndex)
    }

    suspend fun getUnsyncedDocuments(): List<Document> {
        return documentDao.getUnsyncedDocuments()
    }
}
