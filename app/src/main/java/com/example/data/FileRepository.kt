package com.example.data

import kotlinx.coroutines.flow.Flow

class FileRepository(private val fileNodeDao: FileNodeDao) {
    val allNodes: Flow<List<FileNode>> = fileNodeDao.getAllNodes()

    fun getNodesByParent(parentId: Int?): Flow<List<FileNode>> = fileNodeDao.getNodesByParent(parentId)

    suspend fun getNodeById(id: Int): FileNode? = fileNodeDao.getNodeById(id)

    suspend fun insertNode(node: FileNode): Int = fileNodeDao.insertNode(node).toInt()

    suspend fun updateNode(node: FileNode) = fileNodeDao.updateNode(node)

    suspend fun deleteNode(node: FileNode) {
        fileNodeDao.deleteNodeById(node.id)
        if (node.isFolder) {
            // Recursively delete children
            fileNodeDao.deleteNodesByParent(node.id)
        }
    }

    suspend fun getNodeCount(): Int = fileNodeDao.getNodeCount()
}
