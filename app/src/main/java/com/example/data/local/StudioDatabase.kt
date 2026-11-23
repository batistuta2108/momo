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

enum class StudioProjectType {
    MUSIC,
    PHOTO,
    VIDEO
}

@Entity(tableName = "studio_projects")
data class StudioProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val type: String,
    val summary: String,
    val exportedUri: String? = null,
    val metadataJson: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

@Dao
interface StudioProjectDao {
    @Query("SELECT * FROM studio_projects ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<StudioProjectEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(project: StudioProjectEntity): Long

    @Query("DELETE FROM studio_projects WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Database(
    entities = [StudioProjectEntity::class],
    version = 1,
    exportSchema = false
)
abstract class StudioDatabase : RoomDatabase() {
    abstract fun projectDao(): StudioProjectDao

    companion object {
        @Volatile
        private var INSTANCE: StudioDatabase? = null

        fun getInstance(context: Context): StudioDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    StudioDatabase::class.java,
                    "hydra_studio_bergama.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}

class StudioProjectRepository(private val dao: StudioProjectDao) {
    fun observeProjects(): Flow<List<StudioProjectEntity>> = dao.observeAll()

    suspend fun saveProject(
        title: String,
        type: StudioProjectType,
        summary: String,
        exportedUri: String? = null,
        metadataJson: String = ""
    ): Long {
        return dao.upsert(
            StudioProjectEntity(
                title = title,
                type = type.name,
                summary = summary,
                exportedUri = exportedUri,
                metadataJson = metadataJson,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteProject(id: Long) {
        dao.deleteById(id)
    }
}
