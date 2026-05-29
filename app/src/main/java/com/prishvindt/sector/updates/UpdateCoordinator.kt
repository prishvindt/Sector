package com.prishvindt.sector.updates

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

class UpdateCoordinator(
    private val updateChecker: UpdateChecker,
    private val updateInstaller: UpdateInstaller
) {
    private val _status = MutableStateFlow(UpdateStatus())
    val status = _status.asStateFlow()

    private val _events = Channel<UpdateCoordinatorEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var updateCheckedOnce = false

    suspend fun checkOnceIfEnabled(updateChecksEnabled: Boolean) {
        if (!updateChecksEnabled || updateCheckedOnce) return
        updateCheckedOnce = true
        checkUpdates(silent = true)
    }

    suspend fun checkUpdates(silent: Boolean = false) {
        _status.update { it.copy(isChecking = true, lastError = null) }
        updateChecker.check()
            .onSuccess { info ->
                val expanded = !silent && info != null
                _status.value = UpdateStatus(
                    isChecking = false,
                    updateInfo = info,
                    expanded = expanded
                )
                if (!silent) {
                    if (info == null) {
                        _events.send(UpdateCoordinatorEvent.ShowMessage("Новых обновлений нет"))
                    } else {
                        _events.send(UpdateCoordinatorEvent.ShowUpdateBanner)
                        _events.send(UpdateCoordinatorEvent.ShowMessage("Обновление найдено"))
                    }
                }
            }
            .onFailure { error ->
                _status.update {
                    it.copy(isChecking = false, lastError = error.message)
                }
                if (!silent) {
                    _events.send(
                        UpdateCoordinatorEvent.ShowMessage(
                            "Не удалось проверить обновления. Проверьте интернет и попробуйте ещё раз."
                        )
                    )
                }
            }
    }

    fun toggleBanner() {
        _status.update { it.copy(expanded = !it.expanded) }
    }

    fun hideBanner() {
        _status.update {
            it.copy(
                updateInfo = null,
                expanded = false,
                downloadError = null,
                downloadProgress = null
            )
        }
    }

    suspend fun installUpdate() {
        val updateInfo = _status.value.updateInfo ?: return
        if (_status.value.isDownloading) return

        if (!updateInstaller.canInstallFromThisSource()) {
            updateInstaller.openInstallPermissionSettings()
                .onSuccess {
                    _events.send(
                        UpdateCoordinatorEvent.ShowMessage(
                            "Разрешите установку из этого источника, затем нажмите «Установить» снова"
                        )
                    )
                }
                .onFailure {
                    _events.send(
                        UpdateCoordinatorEvent.ShowMessage(
                            "Не удалось открыть настройки установки неизвестных приложений"
                        )
                    )
                }
            return
        }

        _status.update {
            it.copy(
                expanded = true,
                isDownloading = true,
                downloadProgress = null,
                downloadError = null
            )
        }
        updateInstaller.downloadApk(updateInfo) { progress ->
            _status.update { it.copy(downloadProgress = progress) }
        }.onSuccess { apkFile ->
            _status.update {
                it.copy(
                    isDownloading = false,
                    downloadProgress = null,
                    downloadError = null,
                    expanded = true
                )
            }
            updateInstaller.launchInstaller(apkFile)
                .onFailure {
                    _status.update { status ->
                        status.copy(
                            downloadError = "Не удалось открыть установщик обновления"
                        )
                    }
                    _events.send(UpdateCoordinatorEvent.ShowMessage("Не удалось открыть установщик обновления"))
                }
        }.onFailure {
            val message = "Не удалось скачать обновление. Проверьте интернет и попробуйте ещё раз."
            _status.update {
                it.copy(
                    isDownloading = false,
                    downloadProgress = null,
                    downloadError = message,
                    expanded = true
                )
            }
            _events.send(UpdateCoordinatorEvent.ShowMessage(message))
        }
    }

    suspend fun copyUpdateApkUrl() {
        _status.value.updateInfo?.apkUrl?.let { url ->
            _events.send(UpdateCoordinatorEvent.CopyText("APK", url))
        }
    }

    suspend fun openUpdateApkUrl() {
        _status.value.updateInfo?.apkUrl?.let { url ->
            _events.send(UpdateCoordinatorEvent.OpenUrl(url))
        }
    }
}

sealed interface UpdateCoordinatorEvent {
    data class ShowMessage(val message: String) : UpdateCoordinatorEvent
    data class CopyText(val label: String, val text: String) : UpdateCoordinatorEvent
    data class OpenUrl(val url: String) : UpdateCoordinatorEvent
    data object ShowUpdateBanner : UpdateCoordinatorEvent
}
