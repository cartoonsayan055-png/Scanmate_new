package com.synthbyte.scanmate.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "documents")
data class Document(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val timestamp: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val type: String = "SCAN", // SCAN, PDF, IMAGE
    val isArchived: Boolean = false,
    val isFavorite: Boolean = false,
    val isPinned: Boolean = false,
    val workspace: String = "Inbox",
    val ocrText: String? = null,
    val category: String = "General",
    val tags: String = ""
) : Serializable

@Entity(
    tableName = "pages",
    foreignKeys = [
        ForeignKey(
            entity = Document::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("documentId")]
)
data class Page(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    val imagePath: String,
    val pageOrder: Int
) : Serializable

@Entity(tableName = "qr_history")
data class QrHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val value: String,
    val type: String = "GENERATED", // GENERATED or SCANNED
    val timestamp: Long = System.currentTimeMillis()
) : Serializable
