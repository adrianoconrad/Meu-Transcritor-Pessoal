package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "processed_items")
data class ProcessedItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val originalText: String,
    val polishedText: String,
    val fillerWords: String,
    val repetitions: String,
    val syntaxCorrections: String,
    val logicalPunctuation: String,
    val colloquialToProfessional: String
)

@Dao
interface ProcessedItemDao {
    @Query("SELECT * FROM processed_items ORDER BY timestamp DESC")
    fun getAll(): Flow<List<ProcessedItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ProcessedItem): Long

    @Delete
    suspend fun delete(item: ProcessedItem)

    @Query("DELETE FROM processed_items")
    suspend fun deleteAll()
}

@Database(entities = [ProcessedItem::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun processedItemDao(): ProcessedItemDao
}
