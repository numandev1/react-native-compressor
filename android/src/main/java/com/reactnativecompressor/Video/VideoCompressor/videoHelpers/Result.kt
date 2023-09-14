package com.reactnativecompressor.Video.VideoCompressor.video

data class Result(
    val index: Int,
    val success: Boolean,
    val failureMessage: String?,
    val size: Long = 0,
    val path: String? = null,
)
