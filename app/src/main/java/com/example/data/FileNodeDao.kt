package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FileNodeDao {
    @Query("SELECT * FROM file_nodes ORDER BY isFolder DESC, name ASC")
    fun getAllNodes(): Flow<List<FileNode>>

    @Query("SELECT * FROM file_nodes WHERE parentId IS :parentId ORDER BY isFolder DESC, name ASC")
    fun getNodesByParent(parentId: Int?): Flow<List<FileNode>>

    @Query("SELECT * FROM file_nodes WHERE id = :id LIMIT 1")
    suspend fun getNodeById(id: Int): FileNode?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNode(node: FileNode): Long

    @Update
    suspend fun updateNode(node: FileNode)

    @Query("DELETE FROM file_nodes WHERE id = :id")
    suspend fun deleteNodeById(id: Int)

    @Query("DELETE FROM file_nodes WHERE parentId = :parentId")
    suspend fun deleteNodesByParent(parentId: Int)

    @Query("SELECT COUNT(*) FROM file_nodes")
    suspend fun getNodeCount(): Int
}
