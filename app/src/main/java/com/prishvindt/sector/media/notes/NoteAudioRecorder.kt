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
            runCatching { mediaRecorder.stop() }
            mediaRecorder.release()
            recorder = null
            outputFile = null
            RecordedNoteAudio(
                filePath = file.absolutePath,
                sizeBytes = file.length(),
                durationMs = readDurationMs(file)
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

    companion object {
        const val MAX_DURATION_MS = 30_000L
    }
}

data class RecordedNoteAudio(
    val filePath: String,
    val sizeBytes: Long,
    val durationMs: Long?
)
