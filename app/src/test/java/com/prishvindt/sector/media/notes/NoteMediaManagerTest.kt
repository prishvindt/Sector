package com.prishvindt.sector.media.notes

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteMediaManagerTest {
    @Test
    fun existingLocalNoteMediaRequiresIncludedExistingFileInsideFilesDir() {
        val baseDir = Files.createTempDirectory("sector-note-media").toFile()
        try {
            val filesDir = File(baseDir, "files").apply { mkdirs() }
            File(filesDir, "notes/note-1/photo_1.jpg").apply {
                parentFile?.mkdirs()
                writeBytes(byteArrayOf(1, 2, 3))
            }
            File(baseDir, "outside.jpg").writeBytes(byteArrayOf(1, 2, 3))

            assertTrue(
                hasExistingLocalNoteMedia(
                    filesDir = filesDir,
                    mediaIncluded = true,
                    localPath = "notes/note-1/photo_1.jpg"
                )
            )
            assertFalse(
                hasExistingLocalNoteMedia(
                    filesDir = filesDir,
                    mediaIncluded = false,
                    localPath = "notes/note-1/photo_1.jpg"
                )
            )
            assertFalse(
                hasExistingLocalNoteMedia(
                    filesDir = filesDir,
                    mediaIncluded = true,
                    localPath = ""
                )
            )
            assertFalse(
                hasExistingLocalNoteMedia(
                    filesDir = filesDir,
                    mediaIncluded = true,
                    localPath = "notes/note-1/missing.jpg"
                )
            )
            assertFalse(
                hasExistingLocalNoteMedia(
                    filesDir = filesDir,
                    mediaIncluded = true,
                    localPath = "../outside.jpg"
                )
            )
        } finally {
            baseDir.deleteRecursively()
        }
    }
}
