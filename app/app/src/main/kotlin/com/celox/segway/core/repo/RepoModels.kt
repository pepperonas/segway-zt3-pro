package com.celox.segway.core.repo

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Data classes for the cfw.sh-hosted firmware repositories.
 *
 * Endpoints (per the SHU decompile):
 *   • https://apps-data.cfw.sh/shfw/v8/releases     – list available SHFW builds
 *   • https://apps-data.cfw.sh/shfw/v8/config       – per-build metadata
 *   • https://apps-data.cfw.sh/shfw/v8/fetch        – binary fetch
 *   • https://apps-content.cfw.sh/repo/v4/<x>.zip   – bootstrap / model DB
 *
 * NOTE: The exact wire format of the v8 endpoints is not publicly documented.
 * The schemas below mirror what is plausibly returned (release list with
 * id/version/target/size/sha256/url). Adjust once the real responses are observed.
 */

@Serializable
data class FirmwareRelease(
    val id: String,
    val target: FirmwareTarget,
    val version: String,
    val displayName: String? = null,
    val sizeBytes: Long? = null,
    val sha256: String? = null,
    @SerialName("url") val downloadUrl: String? = null,
    val releaseNotes: String? = null,
    val isStable: Boolean = true,
    val publishedAt: String? = null,
)

enum class FirmwareTarget {
    @SerialName("vcu")  VCU,
    @SerialName("mcu")  MCU,
    @SerialName("ble")  BLE,
    @SerialName("dash") DASH,
    @SerialName("shfw") SHFW,
}

@Serializable
data class ReleasesResponse(
    val releases: List<FirmwareRelease> = emptyList()
)
