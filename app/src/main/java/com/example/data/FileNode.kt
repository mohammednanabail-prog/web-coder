package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "file_nodes")
data class FileNode(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val parentId: Int? = null,
    val isFolder: Boolean,
    val content: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)
