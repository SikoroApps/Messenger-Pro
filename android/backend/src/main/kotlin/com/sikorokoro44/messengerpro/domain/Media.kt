package com.sikorokoro44.messengerpro.domain

/** Local validation before any upload; the server must repeat these limits. */
object MediaPolicy {
    const val MAX_BYTES: Long = 25L * 1024 * 1024

    private val EXTENSIONS = mapOf(
        "image/jpeg" to setOf("jpg", "jpeg"),
        "image/png" to setOf("png"),
        "image/webp" to setOf("webp"),
        "video/mp4" to setOf("mp4"),
        "audio/mpeg" to setOf("mp3"),
        "application/pdf" to setOf("pdf"),
    )

    val allowedMimeTypes: Set<String> get() = EXTENSIONS.keys

    fun validate(mimeType: String?, sizeBytes: Long, extension: String?): MediaValidation {
        val normalized = mimeType?.lowercase()?.substringBefore(";")
        val allowedExtensions = EXTENSIONS[normalized] ?: return MediaValidation.UNSUPPORTED_TYPE
        if (sizeBytes <= 0L || sizeBytes > MAX_BYTES) return MediaValidation.INVALID_SIZE
        if (extension != null && extension.lowercase() !in allowedExtensions) {
            return MediaValidation.EXTENSION_MISMATCH
        }
        return MediaValidation.ACCEPTED
    }
}

enum class MediaValidation {
    ACCEPTED,
    UNSUPPORTED_TYPE,
    INVALID_SIZE,
    EXTENSION_MISMATCH,
}
