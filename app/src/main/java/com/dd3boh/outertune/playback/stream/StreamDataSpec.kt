/*
 * Copyright (C) 2025 OuterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package com.dd3boh.outertune.playback.stream

import androidx.core.net.toUri
import androidx.media3.datasource.DataSpec

/**
 * A byte window that Media3 will request, derived from a [RangePolicy].
 */
internal data class ChunkWindow(
    val position: Long,
    val length: Long,
)

/**
 * Chunk length in bytes implied by a [RangePolicy].
 */
internal fun chunkLengthFor(rangePolicy: RangePolicy): Long = when (rangePolicy) {
    is RangePolicy.LegacyChunked -> rangePolicy.chunkSizeBytes
}

/**
 * Computes the byte window Media3 will request given the absolute position it already knows.
 *
 * The offset passed to [DataSpec.subrange] is always 0, so [DataSpec.position] is preserved and
 * only the requested length is capped.
 */
internal fun chunkWindowFor(
    dataSpecPosition: Long,
    rangePolicy: RangePolicy,
): ChunkWindow = ChunkWindow(
    position = dataSpecPosition,
    length = chunkLengthFor(rangePolicy),
)

/**
 * True when a resolved stream's TTL has already elapsed.
 */
internal fun isStreamExpired(expiresAtEpochMs: Long, nowMs: Long): Boolean =
    expiresAtEpochMs <= nowMs

/**
 * Applies the resolved stream's URL, headers and range policy to [this] DataSpec.
 *
 * Media3's [DataSpec.position] already carries the absolute chunk offset (which becomes the HTTP
 * Range), while [DataSpec.uriPositionOffset] is normally 0 for a direct remote URI. To limit only
 * the requested length we pass 0 to [DataSpec.subrange], preserving [DataSpec.position] unchanged.
 */
internal fun DataSpec.withResolvedStream(resolvedStream: ResolvedStream): DataSpec {
    val chunkLength = chunkLengthFor(resolvedStream.rangePolicy)
    return withUri(resolvedStream.url.toUri())
        .withRequestHeaders(resolvedStream.headers)
        .subrange(0L, chunkLength)
}
