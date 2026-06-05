package com.prishvindt.sector.media.notes

import android.media.MediaPlayer
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class NoteAudioPlayer {
    private var player: MediaPlayer? = null
    private var currentPath: String? = null
    private val _state = MutableStateFlow(NoteAudioPlayerState())
    val state: StateFlow<NoteAudioPlayerState> = _state

    fun toggle(file: File) {
        val path = file.absolutePath
        if (currentPath != path) {
            release()
            player = MediaPlayer().apply {
                setDataSource(path)
                prepare()
                setOnCompletionListener {
                    _state.value = _state.value.copy(
                        isPlaying = false,
                        positionMs = duration.coerceAtLeast(0)
                    )
                }
            }
            currentPath = path
            _state.value = NoteAudioPlayerState(
                durationMs = player?.duration?.coerceAtLeast(0) ?: 0
            )
        }
        val mediaPlayer = player ?: return
        if (mediaPlayer.isPlaying) {
            mediaPlayer.pause()
            updateState()
        } else {
            mediaPlayer.start()
            updateState()
        }
    }

    fun seekTo(positionMs: Int) {
        player?.seekTo(positionMs.coerceAtLeast(0))
        updateState()
    }

    fun updateState() {
        val mediaPlayer = player
        _state.value = if (mediaPlayer == null) {
            NoteAudioPlayerState()
        } else {
            NoteAudioPlayerState(
                isPlaying = mediaPlayer.isPlaying,
                positionMs = mediaPlayer.currentPosition.coerceAtLeast(0),
                durationMs = mediaPlayer.duration.coerceAtLeast(0)
            )
        }
    }

    fun release() {
        player?.release()
        player = null
        currentPath = null
        _state.value = NoteAudioPlayerState()
    }
}

data class NoteAudioPlayerState(
    val isPlaying: Boolean = false,
    val positionMs: Int = 0,
    val durationMs: Int = 0
)
