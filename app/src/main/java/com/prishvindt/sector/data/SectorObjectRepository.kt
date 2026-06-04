package com.prishvindt.sector.data

import com.prishvindt.sector.domain.GeoPoint
import com.prishvindt.sector.domain.LocationSharePayload
import com.prishvindt.sector.domain.objects.AzimuthRayPayloadV1
import com.prishvindt.sector.domain.objects.EncryptionState
import com.prishvindt.sector.domain.objects.LiveLocationPayloadV1
import com.prishvindt.sector.domain.objects.MapNotePayloadV1
import com.prishvindt.sector.domain.objects.ObjectVisibility
import com.prishvindt.sector.domain.objects.OwnerKind
import com.prishvindt.sector.domain.objects.SectorBundleFormat
import com.prishvindt.sector.domain.objects.SectorBundleObject
import com.prishvindt.sector.domain.objects.SectorBundleSender
import com.prishvindt.sector.domain.objects.SectorObjectPayloadJson
import com.prishvindt.sector.domain.objects.SectorObjectType
import com.prishvindt.sector.domain.objects.SharedLocationPayloadV1
import com.prishvindt.sector.domain.objects.SourceKind
import com.prishvindt.sector.domain.objects.SyncState
import com.prishvindt.sector.domain.objects.asObjectOrNull
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SectorObjectRepository(
    private val dao: SectorObjectDao,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
    private val deviceIdProvider: () -> String? = { null }
) {
    fun observeActiveObjects(): Flow<List<SectorObjectEntity>> =
        dao.observeActive()

    fun observeActiveObjects(type: SectorObjectType): Flow<List<SectorObjectEntity>> =
        dao.observeActiveByType(type.wireName)

    fun observeActiveAzimuthRays(): Flow<List<Measurement>> =
        observeActiveObjects(SectorObjectType.AZIMUTH_RAY)
            .map { objects -> objects.mapNotNull(SectorObjectEntity::toMeasurementOrNull) }

    fun observeImportedSharedLocations(): Flow<List<ImportedLocation>> =
        observeActiveObjects(SectorObjectType.SHARED_LOCATION)
            .map { objects ->
                objects
                    .filter { OwnerKind.fromWireName(it.ownerKind) != OwnerKind.ME }
                    .mapNotNull(SectorObjectEntity::toImportedLocationOrNull)
            }

    suspend fun activeObjects(): List<SectorObjectEntity> =
        dao.active()

    suspend fun activeObjects(type: SectorObjectType): List<SectorObjectEntity> =
        dao.activeByType(type.wireName)

    suspend fun activeObjectsByIds(objectIds: List<String>): List<SectorObjectEntity> {
        if (objectIds.isEmpty()) return emptyList()
        val activeById = dao.byIds(objectIds)
            .filter { it.deletedAt == null }
            .associateBy { it.objectId }
        return objectIds.mapNotNull(activeById::get)
    }

    suspend fun latestSelfAzimuthRay(): Measurement? =
        dao.latestActiveByTypeAndOwner(
            objectType = SectorObjectType.AZIMUTH_RAY.wireName,
            ownerKind = OwnerKind.ME.wireName
        )?.toMeasurementOrNull()

    suspend fun upsertObject(entity: SectorObjectEntity) {
        validateEntity(entity)
        dao.upsert(entity)
    }

    suspend fun softDeleteObject(objectId: String) {
        dao.softDelete(
            objectId = objectId,
            deletedAt = clock.millis(),
            syncState = SyncState.PENDING_UPLOAD.wireName
        )
    }

    suspend fun softDeleteAllActiveAzimuthRaysForClearAction() {
        dao.softDeleteActiveByType(
            objectType = SectorObjectType.AZIMUTH_RAY.wireName,
            deletedAt = clock.millis(),
            syncState = SyncState.PENDING_UPLOAD.wireName
        )
    }

    suspend fun clearAllObjects() {
        dao.clearAll()
    }

    suspend fun createLocalAzimuthRay(input: LocalAzimuthRayInput): SectorObjectEntity {
        val now = clock.millis()
        val payload = AzimuthRayPayloadV1(
            latitude = input.point.latitude,
            longitude = input.point.longitude,
            azimuth = input.azimuth,
            error = input.error,
            signal = input.signal,
            callsign = input.callsign.trim().takeIf { it.isNotBlank() }
        )
        val entity = baseEntity(
            objectId = newObjectId(),
            type = SectorObjectType.AZIMUTH_RAY,
            ownerKind = OwnerKind.ME,
            ownerId = null,
            sourceKind = SourceKind.LOCAL,
            visibility = ObjectVisibility.SHAREABLE,
            createdAt = now,
            updatedAt = now,
            payloadJson = SectorObjectPayloadJson.encode(payload)
        )
        dao.upsert(entity)
        return entity
    }

    suspend fun importAzimuthRayFromLegacy(measurement: Measurement): SectorObjectEntity {
        val createdAt = measurement.timestamp.toEpochMillisOrNull() ?: clock.millis()
        val payload = AzimuthRayPayloadV1(
            latitude = measurement.latitude,
            longitude = measurement.longitude,
            azimuth = measurement.azimuthDeg,
            error = measurement.azimuthErrorDeg,
            signal = measurement.signalDbm,
            callsign = measurement.callsign.trim().takeIf { it.isNotBlank() }
        )
        val entity = baseEntity(
            objectId = measurement.measurementId.takeIf { it.isUuidString() } ?: newObjectId(),
            type = SectorObjectType.AZIMUTH_RAY,
            ownerKind = OwnerKind.CONTACT,
            ownerId = ownerIdForCallsign(measurement.callsign),
            sourceKind = SourceKind.IMPORTED_MESSAGE,
            visibility = ObjectVisibility.SHAREABLE,
            createdAt = createdAt,
            updatedAt = clock.millis(),
            payloadJson = SectorObjectPayloadJson.encode(payload)
        )
        dao.upsert(entity)
        return entity
    }

    suspend fun createLocalSharedLocation(input: LocalSharedLocationInput): SectorObjectEntity {
        val now = clock.millis()
        val payload = SharedLocationPayloadV1(
            latitude = input.point.latitude,
            longitude = input.point.longitude,
            accuracyMeters = input.accuracyMeters,
            bearing = input.bearing,
            timestamp = input.timestampEpochSeconds,
            callsign = input.callsign.trim().takeIf { it.isNotBlank() }
        )
        val entity = baseEntity(
            objectId = newObjectId(),
            type = SectorObjectType.SHARED_LOCATION,
            ownerKind = OwnerKind.ME,
            ownerId = null,
            sourceKind = SourceKind.LOCAL,
            visibility = ObjectVisibility.SHAREABLE,
            createdAt = now,
            updatedAt = now,
            payloadJson = SectorObjectPayloadJson.encode(payload)
        )
        dao.upsert(entity)
        return entity
    }

    suspend fun importSharedLocationFromLegacy(payload: LocationSharePayload): SectorObjectEntity {
        val now = clock.millis()
        val ownerId = ownerIdForCallsign(payload.callsign)
        ownerId?.let {
            dao.softDeleteActiveByTypeAndOwner(
                objectType = SectorObjectType.SHARED_LOCATION.wireName,
                ownerKind = OwnerKind.CONTACT.wireName,
                ownerId = it,
                deletedAt = now,
                syncState = SyncState.LOCAL_ONLY.wireName
            )
        }
        val entity = baseEntity(
            objectId = newObjectId(),
            type = SectorObjectType.SHARED_LOCATION,
            ownerKind = OwnerKind.CONTACT,
            ownerId = ownerId,
            sourceKind = SourceKind.IMPORTED_MESSAGE,
            visibility = ObjectVisibility.SHAREABLE,
            createdAt = payload.timestampEpochSeconds * 1000L,
            updatedAt = now,
            payloadJson = SectorObjectPayloadJson.encode(
                SharedLocationPayloadV1(
                    latitude = payload.latitude,
                    longitude = payload.longitude,
                    accuracyMeters = payload.accuracyMeters,
                    bearing = null,
                    timestamp = payload.timestampEpochSeconds,
                    callsign = payload.callsign.trim().takeIf { it.isNotBlank() }
                )
            )
        )
        dao.upsert(entity)
        return entity
    }

    suspend fun importObjectsFromBundle(text: String): Result<SectorObjectImportResult> {
        val parsed = SectorBundleFormat.parse(text)
            .getOrElse { return Result.failure(it) }
        val imported = mutableListOf<SectorObjectEntity>()
        var skippedObjects = parsed.skippedObjects
        parsed.objects.forEach { item ->
            runCatching { item.toImportedEntity(parsed.sender) }
                .onSuccess { entity ->
                    softDeletePreviousActiveSharedLocationIfNeeded(entity)
                    dao.upsert(entity)
                    imported += entity
                }
                .onFailure { skippedObjects += 1 }
        }
        return Result.success(
            SectorObjectImportResult(
                imported = imported,
                skippedObjects = skippedObjects
            )
        )
    }

    suspend fun exportObjects(
        objects: List<SectorObjectEntity>,
        callsign: String
    ): Result<String> {
        if (objects.isEmpty()) {
            return Result.failure(NoSectorObjectsForExportException())
        }
        return runCatching {
            SectorBundleFormat.format(
                objects = objects.map { it.withExportCallsign(callsign) },
                sender = SectorBundleSender(
                    callsign = callsign.trim().takeIf { it.isNotBlank() },
                    deviceId = deviceIdProvider()
                ),
                createdAt = clock.millis()
            )
        }
    }

    suspend fun exportObjectsByIds(
        objectIds: List<String>,
        callsign: String
    ): Result<String> =
        exportObjects(activeObjectsByIds(objectIds), callsign)

    private suspend fun softDeletePreviousActiveSharedLocationIfNeeded(entity: SectorObjectEntity) {
        val ownerId = entity.ownerId?.takeIf { it.isNotBlank() } ?: return
        if (entity.deletedAt != null) return
        if (SectorObjectType.fromWireName(entity.objectType) != SectorObjectType.SHARED_LOCATION) return
        if (OwnerKind.fromWireName(entity.ownerKind) != OwnerKind.CONTACT) return
        dao.softDeleteActiveByTypeAndOwner(
            objectType = SectorObjectType.SHARED_LOCATION.wireName,
            ownerKind = OwnerKind.CONTACT.wireName,
            ownerId = ownerId,
            deletedAt = clock.millis(),
            syncState = SyncState.LOCAL_ONLY.wireName
        )
    }

    private fun SectorBundleObject.toImportedEntity(sender: SectorBundleSender): SectorObjectEntity {
        val now = clock.millis()
        val normalizedType = SectorObjectType.fromWireName(objectType)
        val payloadJson = SectorObjectPayloadJson.stringifyPayload(payload)
        validatePayload(
            type = normalizedType,
            payloadVersion = payloadVersion,
            payloadJson = payloadJson
        )
        val incomingOwner = OwnerKind.fromWireName(ownerKind)
        val storedOwnerKind = when (incomingOwner) {
            OwnerKind.ME -> OwnerKind.CONTACT
            OwnerKind.CONTACT -> OwnerKind.CONTACT
            OwnerKind.UNKNOWN -> OwnerKind.UNKNOWN
        }
        val storedOwnerId = ownerId?.takeIf { it.isNotBlank() }
            ?: ownerIdForCallsign(sender.callsign.orEmpty())
        return SectorObjectEntity(
            objectId = objectId.requireUuidString(),
            objectType = objectType.takeIf { it.isNotBlank() } ?: SectorObjectType.UNKNOWN.wireName,
            ownerKind = storedOwnerKind.wireName,
            ownerId = storedOwnerId,
            deviceId = deviceId?.takeIf { it.isNotBlank() } ?: sender.deviceId?.takeIf { it.isNotBlank() },
            sourceKind = SourceKind.IMPORTED_MESSAGE.wireName,
            createdAt = createdAt ?: now,
            updatedAt = now,
            deletedAt = deletedAt,
            syncState = SyncState.LOCAL_ONLY.wireName,
            visibility = visibility
                ?.let(ObjectVisibility::fromWireName)
                ?.wireName
                ?: ObjectVisibility.SHAREABLE.wireName,
            encryptionState = encryptionState
                ?.let(EncryptionState::fromWireName)
                ?.wireName
                ?: EncryptionState.PLAIN_LOCAL.wireName,
            payloadVersion = payloadVersion,
            payloadJson = payloadJson
        ).also(::validateEntity)
    }

    private fun SectorObjectEntity.withExportCallsign(callsign: String): SectorObjectEntity {
        if (SectorObjectType.fromWireName(objectType) != SectorObjectType.AZIMUTH_RAY) return this
        if (OwnerKind.fromWireName(ownerKind) != OwnerKind.ME) return this
        val exportCallsign = callsign.trim().takeIf { it.isNotBlank() } ?: return this
        val payload = SectorObjectPayloadJson.decodeAzimuthRay(payloadJson).getOrNull() ?: return this
        return copy(
            payloadJson = SectorObjectPayloadJson.encode(payload.copy(callsign = exportCallsign))
        )
    }

    private fun baseEntity(
        objectId: String,
        type: SectorObjectType,
        ownerKind: OwnerKind,
        ownerId: String?,
        sourceKind: SourceKind,
        visibility: ObjectVisibility,
        createdAt: Long,
        updatedAt: Long,
        payloadJson: String
    ): SectorObjectEntity =
        SectorObjectEntity(
            objectId = objectId.requireUuidString(),
            objectType = type.wireName,
            ownerKind = ownerKind.wireName,
            ownerId = ownerId,
            deviceId = deviceIdProvider(),
            sourceKind = sourceKind.wireName,
            createdAt = createdAt,
            updatedAt = updatedAt,
            deletedAt = null,
            syncState = SyncState.LOCAL_ONLY.wireName,
            visibility = visibility.wireName,
            encryptionState = EncryptionState.PLAIN_LOCAL.wireName,
            payloadVersion = 1,
            payloadJson = payloadJson
        ).also(::validateEntity)

    private fun validateEntity(entity: SectorObjectEntity) {
        entity.objectId.requireUuidString()
        require(entity.createdAt > 0L) { "created_at must be positive" }
        require(entity.updatedAt > 0L) { "updated_at must be positive" }
        require(entity.payloadVersion > 0) { "payload_version must be positive" }
        validatePayload(
            type = SectorObjectType.fromWireName(entity.objectType),
            payloadVersion = entity.payloadVersion,
            payloadJson = entity.payloadJson
        )
    }

    private fun validatePayload(
        type: SectorObjectType,
        payloadVersion: Int,
        payloadJson: String
    ) {
        if (type == SectorObjectType.UNKNOWN) {
            SectorObjectPayloadJson.parsePayloadJson(payloadJson).getOrThrow()
            return
        }
        require(payloadVersion == 1) { "Unsupported payload_version $payloadVersion" }
        when (type) {
            SectorObjectType.AZIMUTH_RAY ->
                SectorObjectPayloadJson.decodeAzimuthRay(payloadJson).getOrThrow()
            SectorObjectType.SHARED_LOCATION ->
                SectorObjectPayloadJson.decodeSharedLocation(payloadJson).getOrThrow()
            SectorObjectType.MAP_NOTE ->
                SectorObjectPayloadJson.decodeMapNote(payloadJson).getOrThrow()
            SectorObjectType.LIVE_LOCATION ->
                SectorObjectPayloadJson.decodeLiveLocation(payloadJson).getOrThrow()
            SectorObjectType.UNKNOWN -> Unit
        }
    }

    private fun newObjectId(): String =
        idFactory().requireUuidString()

    private fun ownerIdForCallsign(callsign: String): String? =
        callsign.trim()
            .takeIf { it.isNotBlank() }
            ?.lowercase(Locale.ROOT)

    private fun String.requireUuidString(): String {
        UUID.fromString(this)
        return this
    }

    private fun String.isUuidString(): Boolean =
        runCatching { UUID.fromString(this) }.isSuccess

    private fun String.toEpochMillisOrNull(): Long? =
        runCatching { OffsetDateTime.parse(this).toInstant().toEpochMilli() }.getOrNull()
}

fun SectorObjectEntity.toMeasurementOrNull(): Measurement? {
    if (SectorObjectType.fromWireName(objectType) != SectorObjectType.AZIMUTH_RAY) return null
    val payload = SectorObjectPayloadJson.decodeAzimuthRay(payloadJson).getOrNull() ?: return null
    val source = if (OwnerKind.fromWireName(ownerKind) == OwnerKind.ME) {
        MeasurementSource.SELF
    } else {
        MeasurementSource.IMPORTED
    }
    return Measurement(
        measurementId = objectId,
        callsign = payload.callsign.orEmpty(),
        latitude = payload.latitude,
        longitude = payload.longitude,
        accuracyM = null,
        satelliteCount = null,
        azimuthDeg = payload.azimuth,
        azimuthErrorDeg = payload.error,
        signalDbm = payload.signal,
        rangeKm = 15.0,
        timestamp = createdAt.toIsoOffsetDateTime(),
        source = source,
        active = deletedAt == null,
        note = null,
        colorArgb = null
    )
}

fun SectorObjectEntity.toImportedLocationOrNull(): ImportedLocation? {
    if (SectorObjectType.fromWireName(objectType) != SectorObjectType.SHARED_LOCATION) return null
    val payload = SectorObjectPayloadJson.decodeSharedLocation(payloadJson).getOrNull() ?: return null
    return ImportedLocation(
        locationKey = objectId,
        callsign = payload.callsign.orEmpty(),
        latitude = payload.latitude,
        longitude = payload.longitude,
        accuracyMeters = payload.accuracyMeters,
        timestampEpochSeconds = payload.timestamp,
        receivedAtEpochMillis = updatedAt
    )
}

private fun Long.toIsoOffsetDateTime(): String =
    Instant.ofEpochMilli(this)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

data class LocalAzimuthRayInput(
    val point: GeoPoint,
    val callsign: String,
    val azimuth: Double,
    val error: Double,
    val signal: Int?
)

data class LocalSharedLocationInput(
    val point: GeoPoint,
    val callsign: String,
    val accuracyMeters: Double?,
    val bearing: Double?,
    val timestampEpochSeconds: Long
)

data class SectorObjectImportResult(
    val imported: List<SectorObjectEntity>,
    val skippedObjects: Int
)

class NoSectorObjectsForExportException : IllegalStateException(
    "No sector objects for export"
)
