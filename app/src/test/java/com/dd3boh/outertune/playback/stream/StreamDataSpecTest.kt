/*
 * Copyright (C) 2025 OuterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package com.dd3boh.outertune.playback.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamDataSpecTest {

    private val chunkSize = 512 * 1024L

    @Test
    fun case1FirstChunkPreservesPositionZero() {
        val window = chunkWindowFor(dataSpecPosition = 0L, rangePolicy = RangePolicy.LegacyChunked(chunkSize))

        assertEquals(0L, window.position)
        assertEquals(chunkSize, window.length)
    }

    @Test
    fun case2SecondChunkPreservesPosition() {
        val window = chunkWindowFor(dataSpecPosition = 524288L, rangePolicy = RangePolicy.LegacyChunked(chunkSize))

        assertEquals(524288L, window.position)
        assertEquals(chunkSize, window.length)
    }

    @Test
    fun case3ThirdChunkPreservesPosition() {
        val window = chunkWindowFor(dataSpecPosition = 1048576L, rangePolicy = RangePolicy.LegacyChunked(chunkSize))

        assertEquals(1048576L, window.position)
        assertEquals(chunkSize, window.length)
    }

    @Test
    fun case4NonZeroUriOffsetIsNotDoubled() {
        // The absolute position is the only offset-relevant value; a non-zero uriPositionOffset
        // must not be added to it. The helper therefore never doubles the offset.
        val position = 1048576L
        val window = chunkWindowFor(dataSpecPosition = position, rangePolicy = RangePolicy.LegacyChunked(chunkSize))

        assertEquals(position, window.position)
        assertEquals(chunkSize, window.length)
        assertTrue(window.position != position * 2)
    }

    @Test
    fun chunkLengthUsesLegacyChunkSize() {
        assertEquals(chunkSize, chunkLengthFor(RangePolicy.LegacyChunked(RangePolicy.LEGACY_CHUNK_SIZE_BYTES)))
        assertEquals(512 * 1024L, chunkLengthFor(RangePolicy.LegacyChunked(RangePolicy.LEGACY_CHUNK_SIZE_BYTES)))
    }

    @Test
    fun futureExpiryIsNotExpired() {
        assertFalse(isStreamExpired(expiresAtEpochMs = 1_000_000L, nowMs = 999_999L))
    }

    @Test
    fun pastExpiryIsExpired() {
        assertTrue(isStreamExpired(expiresAtEpochMs = 999_999L, nowMs = 1_000_000L))
    }

    @Test
    fun equalTimestampIsExpired() {
        assertTrue(isStreamExpired(expiresAtEpochMs = 1_000_000L, nowMs = 1_000_000L))
    }

    @Test
    fun resolvedStreamKeepsHeadersForCacheHit() {
        val headers = mapOf("User-Agent" to "com.google.android.youtube/20.10.38")
        val stream = ResolvedStream(
            url = "https://example.invalid/stream",
            headers = headers,
            clientName = "ANDROID",
            expiresAtEpochMs = Long.MAX_VALUE,
            format = ResolvedFormat(
                itag = 140,
                mimeType = "audio/mp4",
                codecs = "mp4a.40.2",
                bitrate = 130000,
                sampleRate = 44100,
                contentLength = 12345678L,
                loudnessDb = -14.0,
                perceptualLoudnessDb = -13.5,
            ),
            rangePolicy = RangePolicy.LegacyChunked(RangePolicy.LEGACY_CHUNK_SIZE_BYTES),
            playbackTrackingUrl = null,
            videoLengthSeconds = "245",
        )

        assertTrue(stream.headers.isNotEmpty())
        assertEquals(headers, stream.headers)
        assertEquals("ANDROID", stream.clientName)
    }
}