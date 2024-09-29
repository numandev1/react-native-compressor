package com.reactnativecompressor.Video.VideoCompressor.video

import android.media.MediaCodec
import android.media.MediaFormat
import org.mp4parser.support.Matrix
import java.io.File
import java.util.*

class Mp4Movie {

    private var matrix = Matrix.ROTATE_0
    private val tracks = ArrayList<Track>()
    private var cacheFile: File? = null

    fun getMatrix(): Matrix? = matrix

    fun setCacheFile(file: File) {
        cacheFile = file
    }

    fun setRotation(angle: Int) {
        when (angle) {
            0 -> {
                matrix = Matrix.ROTATE_0
            }
            90 -> {
                matrix = Matrix.ROTATE_90
            }
            180 -> {
                matrix = Matrix.ROTATE_180
            }
            270 -> {
                matrix = Matrix.ROTATE_270
            }
        }
    }

    fun getTracks(): ArrayList<Track> = tracks

    fun getCacheFile(): File? = cacheFile

    fun addSample(trackIndex: Int, offset: Long, bufferInfo: MediaCodec.BufferInfo) {
        if (trackIndex < 0 || trackIndex >= tracks.size) {
            return
        }
        val track = tracks[trackIndex]
        track.addSample(offset, bufferInfo)
    }

    fun addTrack(mediaFormat: MediaFormat, isAudio: Boolean): Int {
        tracks.add(Track(tracks.size, mediaFormat, isAudio))
        return tracks.size - 1
    }
}
