package com.prishvindt.sector.domain.notes

import com.prishvindt.sector.data.FakeSectorObjectDao
import com.prishvindt.sector.data.SectorObjectRepository
import com.prishvindt.sector.data.toMapNoteOrNull
import com.prishvindt.sector.domain.GeoPoint
import com.prishvindt.sector.domain.objects.MapNoteAttachmentPayloadV1
import com.prishvindt.sector.domain.objects.MapNoteAttachmentType
import com.prishvindt.sector.domain.objects.SectorObjectPayloadJson
import com.prishvindt.sector.domain.objects.SectorObjectType
import com.prishvindt.sector.map.MapObjectVisibilityPolicy
import com.prishvindt.sector.ui.common.MapDisplaySettings
import com.prishvindt.sector.data.DestinationMarkerType
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteManagerTest {
    private val fixedClock = Clock.fixed(
        Instant.parse("2026-06-04T10:00:00Z"),
        ZoneOffset.UTC
    )

    @Test
    fun createsMapNoteSectorObject() = runTest {
        val dao = FakeSectorObjectDao()
        val manager = manager(dao)
        val draft = manager.newDraft(GeoPoint(55.0, 37.0)).copy(text = "Текст")

        val result = manager.save(draft).getOrThrow() as NoteSaveResult.Saved

        val saved = dao.snapshot().single()
        val payload = SectorObjectPayloadJson.decodeMapNote(saved.payloadJson).getOrThrow()
        assertEquals(SectorObjectType.MAP_NOTE.wireName, saved.objectType)
        assertEquals("Заметка 1", result.note.title)
        assertEquals("Текст", payload.text)
        assertEquals(55.0, payload.latitude, 0.0)
    }

    @Test
    fun defaultTitleNumbersAreSequential() = runTest {
        val manager = manager(FakeSectorObjectDao())

        val first = manager.newDraft(GeoPoint(55.0, 37.0))
        val second = manager.newDraft(GeoPoint(55.0, 37.0))

        assertEquals("Заметка 1", first.title)
        assertEquals("Заметка 2", second.title)
    }

    @Test
    fun emptyDefaultNoteIsNotSaved() = runTest {
        val dao = FakeSectorObjectDao()
        val manager = manager(dao)

        val result = manager.save(manager.newDraft(GeoPoint(55.0, 37.0))).getOrThrow()

        assertEquals(NoteSaveResult.EmptySkipped, result)
        assertTrue(dao.snapshot().isEmpty())
    }

    @Test
    fun noteWithoutTextButWithPhotoIsSaved() = runTest {
        val dao = FakeSectorObjectDao()
        val manager = manager(dao)
        val draft = manager.newDraft(GeoPoint(55.0, 37.0)).copy(
            attachments = listOf(photoDraft("photo-1"))
        )

        manager.save(draft).getOrThrow()

        val payload = SectorObjectPayloadJson.decodeMapNote(dao.snapshot().single().payloadJson).getOrThrow()
        assertEquals("", payload.text)
        assertEquals(1, payload.attachments.size)
    }

    @Test
    fun rejectsMoreThanTwoPhotosAndOneAudio() = runTest {
        val manager = manager(FakeSectorObjectDao())
        val draft = manager.newDraft(GeoPoint(55.0, 37.0)).copy(
            attachments = listOf(
                photoDraft("photo-1"),
                photoDraft("photo-2"),
                photoDraft("photo-3"),
                audioDraft("audio-1")
            )
        )

        val result = manager.save(draft)

        assertTrue(result.isFailure)
    }

    @Test
    fun softDeleteNoteHidesItFromActiveObjects() = runTest {
        val dao = FakeSectorObjectDao()
        val manager = manager(dao)
        val saved = manager.save(
            manager.newDraft(GeoPoint(55.0, 37.0)).copy(text = "Текст")
        ).getOrThrow() as NoteSaveResult.Saved

        manager.delete(saved.note)

        assertTrue(repository(dao).activeObjects(SectorObjectType.MAP_NOTE).isEmpty())
        assertTrue(dao.snapshot().single().deletedAt != null)
    }

    @Test
    fun visibilityPolicyUsesNoteSettings() {
        val note = MapNote(
            objectId = "550e8400-e29b-41d4-a716-446655440000",
            point = GeoPoint(55.0, 37.0),
            title = "Заметка 1",
            text = "",
            createdAt = 1L,
            updatedAt = 1L,
            attachments = emptyList()
        )

        assertTrue(MapObjectVisibilityPolicy.shouldShowMapNote(note, displaySettings(showNotes = true)))
        assertFalse(MapObjectVisibilityPolicy.shouldShowMapNote(note, displaySettings(showNotes = false)))
        assertEquals("Заметка 1", MapObjectVisibilityPolicy.mapNoteLabel(note, displaySettings(showTitles = true)))
        assertEquals(null, MapObjectVisibilityPolicy.mapNoteLabel(note, displaySettings(showTitles = false)))
    }

    private fun manager(dao: FakeSectorObjectDao): NoteManager =
        NoteManager(
            repository = repository(dao),
            numberStore = FakeNoteNumberStore(),
            attachmentStorage = FakeNoteAttachmentStorage(),
            clock = fixedClock,
            attachmentIdFactory = { "attachment-${System.nanoTime()}" }
        )

    private fun repository(dao: FakeSectorObjectDao): SectorObjectRepository =
        SectorObjectRepository(
            dao = dao,
            clock = fixedClock,
            idFactory = { "550e8400-e29b-41d4-a716-446655440000" },
            deviceIdProvider = { "device-local" }
        )

    private fun photoDraft(id: String): NoteDraftAttachment =
        NoteDraftAttachment(
            attachmentId = id,
            type = MapNoteAttachmentType.PHOTO,
            localPath = null,
            sourceUri = "content://photo/$id",
            sourcePath = null,
            mimeType = "image/jpeg",
            sizeBytes = 100L,
            durationMs = null,
            createdAt = fixedClock.millis()
        )

    private fun audioDraft(id: String): NoteDraftAttachment =
        NoteDraftAttachment(
            attachmentId = id,
            type = MapNoteAttachmentType.AUDIO,
            localPath = null,
            sourceUri = null,
            sourcePath = "/tmp/$id.m4a",
            mimeType = "audio/mp4",
            sizeBytes = 100L,
            durationMs = 1_000L,
            createdAt = fixedClock.millis()
        )

    private fun displaySettings(
        showNotes: Boolean = true,
        showTitles: Boolean = true
    ): MapDisplaySettings =
        MapDisplaySettings(
            ownPointColor = 0xFF2F80ED.toInt(),
            gpsPointScale = 1f,
            destinationMarkerType = DestinationMarkerType.POINT,
            showSelfCallsign = true,
            showImportedCallsigns = true,
            showMapNotes = showNotes,
            showMapNoteTitles = showTitles,
            callsign = "R2ABC"
        )

    private class FakeNoteNumberStore : NoteNumberStore {
        private var next = 1
        override suspend fun reserveNextNoteNumber(): Int = next++
    }

    private class FakeNoteAttachmentStorage : NoteAttachmentStorage {
        override suspend fun persistAttachments(
            objectId: String,
            draftAttachments: List<NoteDraftAttachment>,
            previousAttachments: List<MapNoteAttachmentPayloadV1>
        ): PersistedNoteAttachments =
            PersistedNoteAttachments(
                attachments = draftAttachments.mapIndexed { index, draft ->
                    MapNoteAttachmentPayloadV1(
                        attachmentId = draft.attachmentId,
                        type = draft.type,
                        localPath = when (draft.type) {
                            MapNoteAttachmentType.PHOTO -> "notes/$objectId/photo_${index + 1}.jpg"
                            MapNoteAttachmentType.AUDIO -> "notes/$objectId/audio_1.m4a"
                        },
                        mimeType = draft.mimeType,
                        sizeBytes = draft.sizeBytes,
                        durationMs = draft.durationMs,
                        createdAt = draft.createdAt
                    )
                },
                removedAttachments = emptyList()
            )

        override suspend fun deleteNoteFiles(objectId: String) = Unit
        override fun deletePendingDraftFiles(draft: NoteDraft) = Unit
        override fun deletePendingDraftFile(attachment: NoteDraftAttachment) = Unit
    }
}
