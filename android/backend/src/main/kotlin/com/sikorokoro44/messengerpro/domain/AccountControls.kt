package com.sikorokoro44.messengerpro.domain

data class SessionDevice(
    val id: String,
    val label: String,
    val createdAt: Long,
    val lastSeenAt: Long,
    val isCurrent: Boolean,
)

enum class DataExportStatus { QUEUED, READY, FAILED }

/** Export contains only data owned by the account; the download URL is short-lived. */
data class DataExport(
    val id: String,
    val status: DataExportStatus,
    val requestedAt: Long,
    val downloadPath: String? = null,
)

/** Policy only. Device enforcement (biometric/UI lock) requires physical-device evidence. */
enum class AppLockMode { OFF, ON_RESUME, IMMEDIATE }

data class AccountControlPolicy(
    val lockMode: AppLockMode = AppLockMode.OFF,
    val exportRetentionDays: Int = 7,
) {
    init {
        require(exportRetentionDays in 1..30) { "Export retention must be 1..30 days" }
    }
}
