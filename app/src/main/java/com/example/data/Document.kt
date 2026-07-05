package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class Document(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdTime: Long = System.currentTimeMillis(),
    val pdfPath: String? = null,
    val isSynced: Boolean = false,
    val syncTime: Long? = null
)
