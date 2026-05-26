package com.prishvindt.sector.updates

data class UpdateInfo(
    val latestVersion: String,
    val versionCode: Int,
    val apkUrl: String,
    val changelog: List<String>,
    val mandatory: Boolean
)

data class UpdateStatus(
    val isChecking: Boolean = false,
    val updateInfo: UpdateInfo? = null,
    val lastError: String? = null,
    val expanded: Boolean = false
)
