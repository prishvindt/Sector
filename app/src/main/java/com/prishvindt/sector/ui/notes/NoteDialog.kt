package com.prishvindt.sector.ui.notes

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.ImageView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.prishvindt.sector.domain.notes.MAP_NOTE_MAX_ATTACHMENTS
import com.prishvindt.sector.domain.notes.MAP_NOTE_MAX_AUDIO
import com.prishvindt.sector.domain.notes.MAP_NOTE_MAX_PHOTOS
import com.prishvindt.sector.domain.notes.NoteDraft
import com.prishvindt.sector.domain.notes.NoteDraftAttachment
import com.prishvindt.sector.domain.objects.MapNoteAttachmentType
import com.prishvindt.sector.media.notes.NoteAudioPlayer
import com.prishvindt.sector.media.notes.NoteAudioRecorder
import com.prishvindt.sector.media.notes.RecordedNoteAudio
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

@Composable
fun NoteDialog(
    draft: NoteDraft,
    onTitleChange: (String) -> Unit,
    onTextChange: (String) -> Unit,
    onPhotoPicked: (Uri) -> Unit,
    onPrepareCameraCapture: () -> Uri?,
    onCameraCaptureResult: (Boolean) -> Unit,
    onAudioRecorded: (RecordedNoteAudio) -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val recorder = remember(context) { NoteAudioRecorder(context.applicationContext) }
    var recordingStartedAt by remember { mutableLongStateOf(0L) }
    var recordingRemainingSeconds by remember { mutableStateOf(30) }
    var previewPhoto by remember { mutableStateOf<NoteDraftAttachment?>(null) }
    var deletingAttachmentId by remember { mutableStateOf<String?>(null) }
    val isRecording = recordingStartedAt > 0L

    fun startRecording() {
        deletingAttachmentId = null
        recorder.start()
            .onSuccess {
                recordingStartedAt = System.currentTimeMillis()
                recordingRemainingSeconds = 30
            }
            .onFailure { onShowMessage(it.message ?: "Не удалось начать запись") }
    }

    fun stopRecordingAndKeep() {
        recorder.stopAndKeep()
            .onSuccess(onAudioRecorded)
            .onFailure { onShowMessage(it.message ?: "Не удалось сохранить аудио") }
        recordingStartedAt = 0L
        recordingRemainingSeconds = 30
    }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let(onPhotoPicked)
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        onCameraCaptureResult(success)
    }
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startRecording()
        } else {
            onShowMessage("Для записи аудио нужно разрешение микрофона")
        }
    }

    DisposableEffect(Unit) {
        onDispose { recorder.cancel() }
    }

    LaunchedEffect(isRecording, recordingStartedAt) {
        while (recordingStartedAt > 0L) {
            val elapsedMs = System.currentTimeMillis() - recordingStartedAt
            val remainingMs = (NoteAudioRecorder.MAX_DURATION_MS - elapsedMs).coerceAtLeast(0L)
            recordingRemainingSeconds = ((remainingMs + 999L) / 1000L)
                .toInt()
                .coerceIn(0, 30)
            if (elapsedMs >= NoteAudioRecorder.MAX_DURATION_MS) {
                stopRecordingAndKeep()
            } else {
                delay(250L)
            }
        }
    }

    previewPhoto?.let { photo ->
        NotePhotoPreviewDialog(
            uri = photo.previewUri(context),
            onDismiss = { previewPhoto = null }
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 560.dp)
                        .verticalScroll(rememberScrollState())
                        .clickable(
                            enabled = deletingAttachmentId != null,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { deletingAttachmentId = null }
                        )
                        .padding(start = 20.dp, top = 20.dp, end = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = draft.title,
                        onValueChange = onTitleChange,
                        singleLine = true,
                        label = { Text("Название") }
                    )

                    if (draft.attachments.isNotEmpty() || isRecording) {
                        NoteAttachmentArea(
                            draft = draft,
                            isRecording = isRecording,
                            recordingRemainingSeconds = recordingRemainingSeconds,
                            deletingAttachmentId = deletingAttachmentId,
                            onDeletingAttachmentChange = { deletingAttachmentId = it },
                            onOpenPhoto = { previewPhoto = it },
                            onRemoveAttachment = {
                                deletingAttachmentId = null
                                onRemoveAttachment(it)
                            }
                        )
                    }

                    OutlinedTextField(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        value = draft.text,
                        onValueChange = onTextChange,
                        label = { Text("Текст") }
                    )

                    NoteAttachmentActions(
                        isRecording = isRecording,
                        onPickPhoto = {
                            deletingAttachmentId = null
                            if (draft.canAddPhoto()) {
                                photoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            } else {
                                onShowMessage("Лимит: максимум 2 фото и 1 аудио")
                            }
                        },
                        onTakePhoto = {
                            deletingAttachmentId = null
                            if (draft.canAddPhoto()) {
                                onPrepareCameraCapture()?.let(cameraLauncher::launch)
                            } else {
                                onShowMessage("Лимит: максимум 2 фото и 1 аудио")
                            }
                        },
                        onToggleRecording = {
                            if (isRecording) {
                                stopRecordingAndKeep()
                            } else if (!draft.canAddAudio()) {
                                onShowMessage("Лимит: максимум 1 аудио на заметку")
                            } else if (
                                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                                PackageManager.PERMISSION_GRANTED
                            ) {
                                startRecording()
                            } else {
                                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    )
                }

                NoteDialogButtons(
                    isNew = draft.isNew,
                    saveEnabled = !isRecording,
                    deleteEnabled = !isRecording,
                    onDismiss = onDismiss,
                    onSave = onSave,
                    onDelete = onDelete
                )
            }
        }
    }
}

@Composable
private fun NoteAttachmentActions(
    isRecording: Boolean,
    onPickPhoto: () -> Unit,
    onTakePhoto: () -> Unit,
    onToggleRecording: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AttachmentActionButton(
            icon = Icons.Filled.Image,
            contentDescription = "Выбрать фото",
            onClick = onPickPhoto
        )
        AttachmentActionButton(
            icon = Icons.Filled.PhotoCamera,
            contentDescription = "Сделать фото",
            onClick = onTakePhoto
        )
        AttachmentActionButton(
            icon = if (isRecording) Icons.Filled.Stop else Icons.Filled.Mic,
            contentDescription = if (isRecording) "Остановить запись" else "Записать аудио",
            active = isRecording,
            onClick = onToggleRecording
        )
    }
}

@Composable
private fun AttachmentActionButton(
    icon: ImageVector,
    contentDescription: String,
    active: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (active) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (active) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer
                }
            )
        }
    }
}

@Composable
private fun NoteDialogButtons(
    isNew: Boolean,
    saveEnabled: Boolean,
    deleteEnabled: Boolean,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onDelete: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!isNew && onDelete != null) {
            TextButton(
                enabled = deleteEnabled,
                onClick = onDelete
            ) {
                Text("Удалить")
            }
            Spacer(Modifier.weight(1f))
        } else {
            Spacer(Modifier.weight(1f))
        }
        TextButton(onClick = onDismiss) {
            Text("Отмена")
        }
        Spacer(Modifier.width(4.dp))
        Button(
            enabled = saveEnabled,
            onClick = onSave
        ) {
            Text("Сохранить")
        }
    }
}

@Composable
private fun NoteAttachmentArea(
    draft: NoteDraft,
    isRecording: Boolean,
    recordingRemainingSeconds: Int,
    deletingAttachmentId: String?,
    onDeletingAttachmentChange: (String?) -> Unit,
    onOpenPhoto: (NoteDraftAttachment) -> Unit,
    onRemoveAttachment: (String) -> Unit
) {
    val context = LocalContext.current
    val photos = draft.attachments.filter { it.type == MapNoteAttachmentType.PHOTO }
    val audio = draft.attachments.firstOrNull { it.type == MapNoteAttachmentType.AUDIO }
    val audioFile = audio?.audioFile(context)
    val player = remember(audio?.attachmentId) { NoteAudioPlayer() }
    val playerState by player.state.collectAsState()
    var showAudioProgress by remember(audio?.attachmentId) { mutableStateOf(false) }

    DisposableEffect(player) {
        onDispose { player.release() }
    }
    LaunchedEffect(playerState.isPlaying) {
        while (playerState.isPlaying) {
            delay(250L)
            player.updateState()
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            photos.forEach { photo ->
                NotePhotoTile(
                    attachment = photo,
                    selectedForDelete = deletingAttachmentId == photo.attachmentId,
                    onClick = {
                        if (deletingAttachmentId == photo.attachmentId) {
                            onDeletingAttachmentChange(null)
                        } else {
                            onOpenPhoto(photo)
                        }
                    },
                    onLongPress = { onDeletingAttachmentChange(photo.attachmentId) },
                    onDelete = { onRemoveAttachment(photo.attachmentId) }
                )
            }
            when {
                isRecording -> RecordingTile(recordingRemainingSeconds)
                audio != null -> NoteAudioTile(
                    selectedForDelete = deletingAttachmentId == audio.attachmentId,
                    isPlaying = playerState.isPlaying,
                    enabled = audioFile?.exists() == true,
                    onClick = {
                        if (deletingAttachmentId == audio.attachmentId) {
                            onDeletingAttachmentChange(null)
                        } else {
                            showAudioProgress = true
                            audioFile?.takeIf(File::exists)?.let(player::toggle)
                        }
                    },
                    onLongPress = { onDeletingAttachmentChange(audio.attachmentId) },
                    onDelete = {
                        player.release()
                        showAudioProgress = false
                        onRemoveAttachment(audio.attachmentId)
                    }
                )
            }
        }

        if (audio != null && showAudioProgress) {
            NoteAudioProgress(
                positionMs = playerState.positionMs,
                durationMs = playerDuration(playerState.durationMs, audio),
                enabled = audioFile?.exists() == true,
                onSeek = player::seekTo
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteMediaTile(
    selectedForDelete: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onDelete: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .size(54.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress
            )
    ) {
        content()
        if (selectedForDelete) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.7f))
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Удалить вложение",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun NotePhotoTile(
    attachment: NoteDraftAttachment,
    selectedForDelete: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val uri = attachment.previewUri(context)
    NoteMediaTile(
        selectedForDelete = selectedForDelete,
        onClick = onClick,
        onLongPress = onLongPress,
        onDelete = onDelete
    ) {
        if (uri != null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    ImageView(ctx).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                    }
                },
                update = { imageView -> imageView.setImageURI(uri) }
            )
        } else {
            Text(
                text = "нет",
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NoteAudioTile(
    selectedForDelete: Boolean,
    isPlaying: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onDelete: () -> Unit
) {
    NoteMediaTile(
        selectedForDelete = selectedForDelete,
        onClick = onClick,
        onLongPress = onLongPress,
        onDelete = onDelete
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(34.dp)
                .background(
                    color = if (enabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Пауза" else "Проиграть",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(23.dp)
            )
        }
    }
}

@Composable
private fun RecordingTile(remainingSeconds: Int) {
    Box(
        modifier = Modifier
            .size(54.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = remainingSeconds.coerceIn(0, 30).toString(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun NoteAudioProgress(
    positionMs: Int,
    durationMs: Int,
    enabled: Boolean,
    onSeek: (Int) -> Unit
) {
    val activeColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.outlineVariant
    val progress = if (durationMs > 0) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .pointerInput(enabled, durationMs) {
                if (enabled && durationMs > 0) {
                    detectTapGestures { offset ->
                        onSeek(offset.toAudioPosition(size.width, durationMs))
                    }
                }
            }
            .pointerInput(enabled, durationMs) {
                if (enabled && durationMs > 0) {
                    detectDragGestures { change, _ ->
                        onSeek(change.position.toAudioPosition(size.width, durationMs))
                    }
                }
            }
    ) {
        val centerY = size.height / 2f
        val progressX = size.width * progress
        drawLine(
            color = trackColor,
            start = Offset(0f, centerY),
            end = Offset(size.width, centerY),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawLine(
            color = activeColor,
            start = Offset(0f, centerY),
            end = Offset(progressX, centerY),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawCircle(
            color = activeColor,
            radius = 5.dp.toPx(),
            center = Offset(progressX, centerY)
        )
    }
}

@Composable
private fun NotePhotoPreviewDialog(
    uri: Uri?,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (uri != null) {
                    AndroidView(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        factory = { ctx ->
                            ImageView(ctx).apply {
                                adjustViewBounds = true
                                scaleType = ImageView.ScaleType.FIT_CENTER
                            }
                        },
                        update = { it.setImageURI(uri) }
                    )
                } else {
                    Text(
                        text = "Медиа недоступно",
                        modifier = Modifier.align(Alignment.Center),
                        color = Color.White
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Закрыть",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

private fun NoteDraft.canAddPhoto(): Boolean =
    attachments.count { it.type == MapNoteAttachmentType.PHOTO } < MAP_NOTE_MAX_PHOTOS &&
        attachments.size < MAP_NOTE_MAX_ATTACHMENTS

private fun NoteDraft.canAddAudio(): Boolean =
    attachments.count { it.type == MapNoteAttachmentType.AUDIO } < MAP_NOTE_MAX_AUDIO &&
        attachments.size < MAP_NOTE_MAX_ATTACHMENTS

private fun NoteDraftAttachment.previewUri(context: Context): Uri? =
    sourceUri?.let(Uri::parse)
        ?: sourcePath?.let { Uri.fromFile(File(it)) }
        ?: localPath
            ?.takeIf { mediaIncluded && it.isNotBlank() }
            ?.let { File(context.filesDir, it) }
            ?.takeIf(File::exists)
            ?.let(Uri::fromFile)

private fun NoteDraftAttachment.audioFile(context: Context): File? =
    sourcePath?.let(::File)
        ?: localPath
            ?.takeIf { mediaIncluded && it.isNotBlank() }
            ?.let { File(context.filesDir, it) }

private fun playerDuration(playerDurationMs: Int, attachment: NoteDraftAttachment): Int =
    playerDurationMs.takeIf { it > 0 }
        ?: attachment.durationMs?.toInt()
        ?: 0

private fun Offset.toAudioPosition(width: Int, durationMs: Int): Int =
    ((x / width.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f) * durationMs).roundToInt()
