package com.reactnativecompressor.Video

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class VideoCompressionProfile(
    val width: Int,
    val height: Int,
    val bitrate: Int,
    val frameRate: Int,
)

object VideoCompressionProfileFactory {
    // Fallback when the source frame rate cannot be detected.
    private const val DEFAULT_FRAME_RATE = 30
    // Hard upper bound. 60 fps covers every modern phone capture (24/25/30/
    // 50/60). Capping at 30 — the previous behaviour — silently halved 60
    // fps recordings and made the output look choppy.
    private const val MAX_FRAME_RATE = 60

    fun createAuto(
        sourceWidth: Int,
        sourceHeight: Int,
        sourceBitrate: Int,
        sourceFrameRate: Int,
        maxSize: Float,
    ): VideoCompressionProfile {
        val dimensions = scaleWithin(sourceWidth, sourceHeight, maxSize.roundToInt())
        val frameRate = normalizeFrameRate(sourceFrameRate)
        val bitrate = estimateBitrate(
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
            sourceBitrate = sourceBitrate,
            sourceFrameRate = sourceFrameRate,
            targetWidth = dimensions.first,
            targetHeight = dimensions.second,
            targetFrameRate = frameRate,
        )

        return VideoCompressionProfile(
            width = dimensions.first,
            height = dimensions.second,
            bitrate = bitrate,
            frameRate = frameRate,
        )
    }

    fun createManual(
        sourceWidth: Int,
        sourceHeight: Int,
        sourceBitrate: Int,
        sourceFrameRate: Int,
        maxSize: Float,
        requestedBitrate: Float,
    ): VideoCompressionProfile {
        val dimensions = scaleWithin(sourceWidth, sourceHeight, maxSize.roundToInt())
        val frameRate = normalizeFrameRate(sourceFrameRate)
        val bitrate = if (requestedBitrate > 0f) {
            requestedBitrate.roundToInt().coerceAtLeast(1)
        } else {
            estimateBitrate(
                sourceWidth = sourceWidth,
                sourceHeight = sourceHeight,
                sourceBitrate = sourceBitrate,
                sourceFrameRate = sourceFrameRate,
                targetWidth = dimensions.first,
                targetHeight = dimensions.second,
                targetFrameRate = frameRate,
            )
        }

        return VideoCompressionProfile(
            width = dimensions.first,
            height = dimensions.second,
            bitrate = bitrate,
            frameRate = frameRate,
        )
    }

    fun normalizeDimension(value: Int): Int {
        val positive = value.coerceAtLeast(2)
        return if (positive % 2 == 0) positive else positive - 1
    }

    private fun scaleWithin(sourceWidth: Int, sourceHeight: Int, requestedMaxSize: Int): Pair<Int, Int> {
        val safeWidth = normalizeDimension(sourceWidth)
        val safeHeight = normalizeDimension(sourceHeight)
        val longSide = max(safeWidth, safeHeight)
        val boundedMaxSize = requestedMaxSize.coerceAtLeast(2)

        if (longSide <= boundedMaxSize) {
            return Pair(safeWidth, safeHeight)
        }

        val scale = boundedMaxSize.toFloat() / longSide.toFloat()
        val width = normalizeDimension((safeWidth * scale).roundToInt())
        val height = normalizeDimension((safeHeight * scale).roundToInt())
        return Pair(width, height)
    }

    private fun normalizeFrameRate(sourceFrameRate: Int): Int {
        if (sourceFrameRate <= 0) {
            return DEFAULT_FRAME_RATE
        }

        return sourceFrameRate.coerceIn(1, MAX_FRAME_RATE)
    }

    private fun estimateBitrate(
        sourceWidth: Int,
        sourceHeight: Int,
        sourceBitrate: Int,
        sourceFrameRate: Int,
        targetWidth: Int,
        targetHeight: Int,
        targetFrameRate: Int,
    ): Int {
        // WhatsApp-style bitrate envelope. The previous floors/ceilings
        // were ~2-3x larger and produced "compressed" outputs that were
        // still 20-40 MB for short clips. These bands target ~1.5 Mbps at
        // 720p, which matches WhatsApp's typical output size while keeping
        // visual quality acceptable for chat playback.
        val targetLongSide = max(targetWidth, targetHeight)
        val floor = when {
            targetLongSide >= 1920 -> 2_000_000
            targetLongSide >= 1280 -> 1_200_000
            targetLongSide >= 960 -> 900_000
            targetLongSide >= 720 -> 700_000
            else -> 500_000
        }
        val ceiling = when {
            targetLongSide >= 1920 -> 3_500_000
            targetLongSide >= 1280 -> 2_000_000
            targetLongSide >= 960 -> 1_500_000
            targetLongSide >= 720 -> 1_200_000
            else -> 900_000
        }

        if (sourceBitrate <= 0) {
            return floor
        }

        val sourcePixels = sourceWidth.toLong() * sourceHeight.toLong()
        val targetPixels = targetWidth.toLong() * targetHeight.toLong()
        val pixelRatio = if (sourcePixels == 0L) 1.0 else targetPixels.toDouble() / sourcePixels.toDouble()
        val sourceFps = max(sourceFrameRate, 1)
        val frameRateRatio = targetFrameRate.toDouble() / sourceFps.toDouble()
        val scaledBitrate = (sourceBitrate * pixelRatio * max(frameRateRatio, 0.85)).roundToInt()
        val sourceCap = (sourceBitrate * 0.95).roundToInt().coerceAtLeast(floor)

        return scaledBitrate.coerceAtLeast(floor).coerceAtMost(min(ceiling, sourceCap))
    }
}
