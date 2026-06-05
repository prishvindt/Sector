package com.prishvindt.sector.media.notes

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class NoteAudioRecorder(
    private val context: Context
) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    val isRecording: Boolean
        get() = recorder != null

    fun start(): Result<File> =
        runCatching {
            cancel()
            val dir = File(context.filesDir, "notes/_pending").apply { mkdirs() }
            val file = File(dir, "audio_${System.currentTimeMillis()}.m4a")
            val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mediaRecorder.setAudioEncodingBitRate(128_000)
            mediaRecorder.setAudioSamplingRate(44_100)
            mediaRecorder.setOutputFile(file.absolutePath)
            mediaRecorder.prepare()
            mediaRecorder.start()
            recorder = mediaRecorder
            outputFile = file
            file
        }

    fun stopAndKeep(): Result<RecordedNoteAudio> =
        runCatching {
            val mediaRecorder = recorder ?: error("Recording is not active")
            val file = outputFile ?: error("Output file is missing")
            recorder = null
            outputFile = null
            try {
                mediaRecorder.stop()
            } catch (cause: RuntimeException) {
                runCatching { mediaRecorder.release() }
                deleteInvalidOutput(file)
                throw IllegalStateException("Audio recording is too short or invalid", cause)
            }
            runCatching { mediaRecorder.release() }
            val sizeBytes = file.length()
            require(sizeBytes >= MIN_VALID_AUDIO_BYTES) {
                deleteInvalidOutput(file)
                "Audio recording is too short or invalid"
            }
            val durationMs = readDurationMs(file)
            require(durationMs == null || durationMs > 0L) {
                deleteInvalidOutput(file)
                "Audio recording is too short or invalid"
            }
            RecordedNoteAudio(
                filePath = file.absolutePath,
                sizeBytes = sizeBytes,
                durationMs = durationMs
            )
        }

    fun cancel() {
        val file = outputFile
        recorder?.let { mediaRecorder ->
            runCatching { mediaRecorder.stop() }
            runCatching { mediaRecorder.release() }
        }
        recorder = null
        outputFile = null
        file?.delete()
    }

    private fun readDurationMs(file: File): Long? =
        runCatching {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
            retriever.release()
            duration
        }.getOrNull()

    private fun deleteInvalidOutput(file: File) {
        runCatching { file.delete() }
    }

    companion object {
        const val MAX_DURATION_MS = 30_000L
        private const val MIN_VALID_AUDIO_BYTES = 128L
    }
}

data class RecordedNoteAudio(
    val filePath: String,
    val sizeBytes: Long,
    val durationMs: Long?
)
