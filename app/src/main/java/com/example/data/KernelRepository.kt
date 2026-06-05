package com.example.data

import kotlinx.coroutines.flow.Flow

class KernelRepository(private val dao: ProcessedItemDao) {
    val allItems: Flow<List<ProcessedItem>> = dao.getAll()

    suspend fun insert(item: ProcessedItem): Long {
        return dao.insert(item)
    }

    suspend fun delete(item: ProcessedItem) {
        dao.delete(item)
    }

    suspend fun deleteAll() {
        dao.deleteAll()
    }
}
