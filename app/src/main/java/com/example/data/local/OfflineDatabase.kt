package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "offline_vault_queue")
data class OfflineVaultEntity(
    @PrimaryKey val localId: String,
    val userId: String,
    val title: String,
    val cipherTextBase64: String,
    val ivBase64: String,
    val authTagHash: String,
    val category: String,
    val isEncrypted: Boolean,
    val isPinned: Boolean,
    val pendingSync: Boolean,
    val updatedAtEpochMs: Long
)

@Dao
interface OfflineVaultDao {
    @Query("SELECT * FROM offline_vault_queue WHERE userId = :userId ORDER BY isPinned DESC, updatedAtEpochMs DESC")
    fun observeLocalItems(userId: String): Flow<List<OfflineVaultEntity>>

    @Query("SELECT * FROM offline_vault_queue WHERE userId = :userId AND pendingSync = 1")
    suspend fun getPendingSyncItems(userId: String): List<OfflineVaultEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItem(item: OfflineVaultEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<OfflineVaultEntity>)

    @Query("UPDATE offline_vault_queue SET pendingSync = 0 WHERE localId = :localId")
    suspend fun markSynced(localId: String)

    @Query("DELETE FROM offline_vault_queue WHERE localId = :localId")
    suspend fun deleteById(localId: String)
}

@Database(entities = [OfflineVaultEntity::class], version = 1, exportSchema = false)
abstract class AuraLocalDatabase : RoomDatabase() {
    abstract fun offlineVaultDao(): OfflineVaultDao

    companion object {
        @Volatile
        private var INSTANCE: AuraLocalDatabase? = null

        fun getInstance(context: Context): AuraLocalDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AuraLocalDatabase::class.java,
                    "auraglass_offline_vault.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
