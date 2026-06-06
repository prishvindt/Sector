package com.prishvindt.sector.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sector_objects")
data class SectorObjectEntity(
    @PrimaryKey
    @ColumnInfo(name = "object_id")
    val objectId: String,
    @ColumnInfo(name = "object_type")
    val objectType: String,
    @ColumnInfo(name = "owner_kind")
    val ownerKind: String,
    @ColumnInfo(name = "owner_id")
    val ownerId: String?,
    @ColumnInfo(name = "device_id")
    val deviceId: String?,
    @ColumnInfo(name = "source_kind")
    val sourceKind: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    @ColumnInfo(name = "deleted_at")
    val deletedAt: Long?,
    @ColumnInfo(name = "sync_state")
    val syncState: String,
    val visibility: String,
    @ColumnInfo(name = "encryption_state")
    val encryptionState: String,
    @ColumnInfo(name = "payload_version")
    val payloadVersion: Int,
    @ColumnInfo(name = "payload_json")
    val payloadJson: String
)
