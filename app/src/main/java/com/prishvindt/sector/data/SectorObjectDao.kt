package com.prishvindt.sector.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SectorObjectDao {
    @Query("SELECT * FROM sector_objects WHERE deleted_at IS NULL ORDER BY updated_at DESC")
    fun observeActive(): Flow<List<SectorObjectEntity>>

    @Query("SELECT * FROM sector_objects WHERE deleted_at IS NULL AND object_type = :objectType ORDER BY updated_at DESC")
    fun observeActiveByType(objectType: String): Flow<List<SectorObjectEntity>>

    @Query("SELECT * FROM sector_objects WHERE deleted_at IS NULL ORDER BY updated_at DESC")
    suspend fun active(): List<SectorObjectEntity>

    @Query("SELECT * FROM sector_objects WHERE deleted_at IS NULL AND object_type = :objectType ORDER BY updated_at DESC")
    suspend fun activeByType(objectType: String): List<SectorObjectEntity>

    @Query(
        """
        SELECT * FROM sector_objects
        WHERE deleted_at IS NULL AND object_type = :objectType AND owner_kind = :ownerKind
        ORDER BY created_at DESC
        LIMIT 1
        """
    )
    suspend fun latestActiveByTypeAndOwner(
        objectType: String,
        ownerKind: String
    ): SectorObjectEntity?

    @Query("SELECT * FROM sector_objects WHERE object_id IN (:objectIds)")
    suspend fun byIds(objectIds: List<String>): List<SectorObjectEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SectorObjectEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<SectorObjectEntity>)

    @Query(
        """
        UPDATE sector_objects
        SET deleted_at = :deletedAt, updated_at = :deletedAt, sync_state = :syncState
        WHERE object_id = :objectId AND deleted_at IS NULL
        """
    )
    suspend fun softDelete(
        objectId: String,
        deletedAt: Long,
        syncState: String
    )

    @Query(
        """
        UPDATE sector_objects
        SET deleted_at = :deletedAt, updated_at = :deletedAt, sync_state = :syncState
        WHERE deleted_at IS NULL AND object_type = :objectType
        """
    )
    suspend fun softDeleteActiveByType(
        objectType: String,
        deletedAt: Long,
        syncState: String
    )

    @Query(
        """
        UPDATE sector_objects
        SET deleted_at = :deletedAt, updated_at = :deletedAt, sync_state = :syncState
        WHERE deleted_at IS NULL
            AND object_type = :objectType
            AND owner_kind = :ownerKind
            AND owner_id = :ownerId
        """
    )
    suspend fun softDeleteActiveByTypeAndOwner(
        objectType: String,
        ownerKind: String,
        ownerId: String,
        deletedAt: Long,
        syncState: String
    )

    @Query("DELETE FROM sector_objects")
    suspend fun clearAll()
}
