package com.prishvindt.sector.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeSectorObjectDao(
    initial: List<SectorObjectEntity> = emptyList()
) : SectorObjectDao {
    private val items = initial.toMutableList()
    private val state = MutableStateFlow(items.toList())

    fun snapshot(): List<SectorObjectEntity> = items.toList()

    override fun observeActive(): Flow<List<SectorObjectEntity>> =
        state.map { values -> values.activeSorted() }

    override fun observeActiveByType(objectType: String): Flow<List<SectorObjectEntity>> =
        state.map { values -> values.activeSorted().filter { it.objectType == objectType } }

    override suspend fun active(): List<SectorObjectEntity> =
        items.activeSorted()

    override suspend fun activeByType(objectType: String): List<SectorObjectEntity> =
        items.activeSorted().filter { it.objectType == objectType }

    override suspend fun latestActiveByTypeAndOwner(
        objectType: String,
        ownerKind: String
    ): SectorObjectEntity? =
        items.activeSorted()
            .firstOrNull { it.objectType == objectType && it.ownerKind == ownerKind }

    override suspend fun byIds(objectIds: List<String>): List<SectorObjectEntity> =
        items.filter { it.objectId in objectIds }

    override suspend fun upsert(entity: SectorObjectEntity) {
        val index = items.indexOfFirst { it.objectId == entity.objectId }
        if (index < 0) {
            items += entity
        } else {
            items[index] = entity
        }
        publish()
    }

    override suspend fun upsertAll(entities: List<SectorObjectEntity>) {
        entities.forEach { upsert(it) }
        publish()
    }

    override suspend fun softDelete(
        objectId: String,
        deletedAt: Long,
        syncState: String
    ) {
        updateMatching(
            predicate = { it.objectId == objectId && it.deletedAt == null },
            deletedAt = deletedAt,
            syncState = syncState
        )
    }

    override suspend fun softDeleteActiveByType(
        objectType: String,
        deletedAt: Long,
        syncState: String
    ) {
        updateMatching(
            predicate = { it.objectType == objectType && it.deletedAt == null },
            deletedAt = deletedAt,
            syncState = syncState
        )
    }

    override suspend fun softDeleteActiveByTypeAndOwner(
        objectType: String,
        ownerKind: String,
        ownerId: String,
        deletedAt: Long,
        syncState: String
    ) {
        updateMatching(
            predicate = {
                it.objectType == objectType &&
                    it.ownerKind == ownerKind &&
                    it.ownerId == ownerId &&
                    it.deletedAt == null
            },
            deletedAt = deletedAt,
            syncState = syncState
        )
    }

    override suspend fun clearAll() {
        items.clear()
        publish()
    }

    private fun updateMatching(
        predicate: (SectorObjectEntity) -> Boolean,
        deletedAt: Long,
        syncState: String
    ) {
        items.indices.forEach { index ->
            if (predicate(items[index])) {
                items[index] = items[index].copy(
                    deletedAt = deletedAt,
                    updatedAt = deletedAt,
                    syncState = syncState
                )
            }
        }
        publish()
    }

    private fun publish() {
        state.value = items.toList()
    }

    private fun List<SectorObjectEntity>.activeSorted(): List<SectorObjectEntity> =
        filter { it.deletedAt == null }.sortedByDescending { it.updatedAt }
}
