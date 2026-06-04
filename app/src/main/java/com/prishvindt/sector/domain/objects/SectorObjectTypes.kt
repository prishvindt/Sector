package com.prishvindt.sector.domain.objects

enum class SectorObjectType(val wireName: String) {
    AZIMUTH_RAY("AZIMUTH_RAY"),
    SHARED_LOCATION("SHARED_LOCATION"),
    MAP_NOTE("MAP_NOTE"),
    LIVE_LOCATION("LIVE_LOCATION"),
    UNKNOWN("UNKNOWN");

    companion object {
        fun fromWireName(value: String?): SectorObjectType =
            entries.firstOrNull { it.wireName == value } ?: UNKNOWN
    }
}

enum class OwnerKind(val wireName: String) {
    ME("ME"),
    CONTACT("CONTACT"),
    UNKNOWN("UNKNOWN");

    companion object {
        fun fromWireName(value: String?): OwnerKind =
            entries.firstOrNull { it.wireName == value } ?: UNKNOWN
    }
}

enum class SourceKind(val wireName: String) {
    LOCAL("LOCAL"),
    IMPORTED_MESSAGE("IMPORTED_MESSAGE"),
    SERVER("SERVER"),
    LIVE("LIVE");

    companion object {
        fun fromWireName(value: String?): SourceKind =
            entries.firstOrNull { it.wireName == value } ?: LOCAL
    }
}

enum class SyncState(val wireName: String) {
    LOCAL_ONLY("LOCAL_ONLY"),
    PENDING_UPLOAD("PENDING_UPLOAD"),
    SYNCED("SYNCED"),
    FAILED("FAILED"),
    CONFLICT("CONFLICT");

    companion object {
        fun fromWireName(value: String?): SyncState =
            entries.firstOrNull { it.wireName == value } ?: LOCAL_ONLY
    }
}

enum class ObjectVisibility(val wireName: String) {
    PRIVATE("PRIVATE"),
    SHAREABLE("SHAREABLE"),
    SHARED_WITH_CONTACTS("SHARED_WITH_CONTACTS");

    companion object {
        fun fromWireName(value: String?): ObjectVisibility =
            entries.firstOrNull { it.wireName == value } ?: PRIVATE
    }
}

enum class EncryptionState(val wireName: String) {
    PLAIN_LOCAL("PLAIN_LOCAL"),
    ENCRYPTED_LOCAL("ENCRYPTED_LOCAL"),
    ENCRYPTED_FOR_CONTACTS("ENCRYPTED_FOR_CONTACTS"),
    UNSUPPORTED("UNSUPPORTED");

    companion object {
        fun fromWireName(value: String?): EncryptionState =
            entries.firstOrNull { it.wireName == value } ?: UNSUPPORTED
    }
}
