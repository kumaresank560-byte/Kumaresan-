package com.example.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "document_pages",
    foreignKeys = [
        ForeignKey(
            entity = Document::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["documentId"])]
)
data class DocumentPage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    val originalImagePath: String,
    val croppedImagePath: String? = null,
    val rotationDegrees: Int = 0,
    val filterType: String = "ORIGINAL", // "ORIGINAL", "GRAYSCALE", "MAGIC", "BW"
    val pageIndex: Int
)
