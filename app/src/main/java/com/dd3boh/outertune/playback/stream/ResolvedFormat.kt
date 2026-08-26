/*
 * Copyright (C) 2025 OuterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package com.dd3boh.outertune.playback.stream

/**
 * Format information carried by a [ResolvedStream], independent of any InnerTube model.
 */
data class ResolvedFormat(
    val itag: Int,
    val mimeType: String,
    val codecs: String,
    val bitrate: Int,
    val sampleRate: Int?,
    val contentLength: Long?,
    val loudnessDb: Double?,
    val perceptualLoudnessDb: Double?,
)

/**
 * Describes how Media3 should request bytes from the resolved stream.
 *
 * Currently only models the existing legacy behavior (a fixed chunk size); richer policies such as
 * bounded ranges will be added in later phases.
 */
sealed interface RangePolicy {
    data class LegacyChunked(val chunkSizeBytes: Long) : RangePolicy

    companion object {
        /**
         * Legacy chunk size kept in sync with the value historically used by [com.dd3boh.outertune.playback.MusicService].
         */
        const val LEGACY_CHUNK_SIZE_BYTES = 512 * 1024L
    }
}