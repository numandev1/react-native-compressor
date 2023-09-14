package com.reactnativecompressor.Audio

import android.annotation.SuppressLint
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.reactnativecompressor.Video.VideoCompressor.video.OutputSurface
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer

class AudioCompressor {
    var path: String? = null
    private var outputPath: String? = null
    private var mBufferInfo: MediaCodec.BufferInfo? = null
    private var mMuxer: MediaMuxer? = null
    private var mEncoder: MediaCodec? = null
    private var mDecoder: MediaCodec? = null
    private var mTrackIndex = 0
    private var mInputSurface: OutputSurface? = null

    // bit rate, in bits per second
    private var mBitRate = -1
    private val TIMEOUT_USEC = 2500

    /***
     * trans video and audio  by mediacodec
     *
     */
    @SuppressLint("LongLogTag")
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    fun CompressAudio(sourcePath: String?, destinationPath: String?, nbitrate: Int): Boolean {
        path = sourcePath
        outputPath = destinationPath
        if (checkParmsError(sourcePath, destinationPath, nbitrate)) {
            return false
        }

        //get origin video info
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(path)
        //  String framecount = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT);
        val duration = java.lang.Long.valueOf(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)) * 1000
        val startTime: Long = -1
        val endTime: Long = -1
        mBitRate = nbitrate
        // NUM_FRAMES = Integer.getInteger(framecount);
        //IFRAME_INTERVAL = Integer.valueOf(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
        var error = false
        val videoStartTime: Long = -1
        val time = System.currentTimeMillis()
        val cacheFile = File(destinationPath)
        val inputFile = File(path)
        if (!inputFile.canRead()) {
            return false
        }
        var extractor: MediaExtractor? = null
        var mAudioExtractor: MediaExtractor? = null
        try {
            // video MediaExtractor
            extractor = MediaExtractor()
            extractor.setDataSource(inputFile.toString())

            // audio MediaExtractor
            mAudioExtractor = MediaExtractor()
            mAudioExtractor.setDataSource(inputFile.toString())
            mMuxer = try {
                MediaMuxer(outputPath!!, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            } catch (ioe: IOException) {
                throw RuntimeException("MediaMuxer creation failed", ioe)
            }
            val muxerAudioTrackIndex = 0
            val audioIndex = selectTrack(mAudioExtractor, true)
            if (audioIndex >= 0) {
                mAudioExtractor.selectTrack(audioIndex)
                mAudioExtractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                val trackFormat = mAudioExtractor.getTrackFormat(audioIndex)
                //        muxerAudioTrackIndex = mMuxer.addTrack(trackFormat);

                // extractor.unselectTrack(muxerAudioTrackIndex);
            }
            /**
             * mediacodec + surface + opengl
             */
            Log.d("CompressAudio", "CompressAudio: ")
            val videoIndex = selectTrack(extractor, true)
            if (videoIndex >= 0) {
                var videoTime: Long = -1
                var outputDone = false
                var inputDone = false
                var decoderDone = false
                val swapUV = 0
                var videoTrackIndex = MEDIATYPE_NOT_AUDIO_VIDEO
                extractor.selectTrack(videoIndex)
                if (startTime > 0) {
                    extractor.seekTo(startTime, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                } else {
                    extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                }
                val inputFormat = extractor.getTrackFormat(videoIndex)
                /**
                 * init mediacodec  / encoder and decoder
                 */
                prepareEncoder(inputFormat)
                var decoderInputBuffers: Array<ByteBuffer?>? = null
                var encoderOutputBuffers: Array<ByteBuffer?>? = null
                decoderInputBuffers = mDecoder!!.inputBuffers
                encoderOutputBuffers = mEncoder!!.outputBuffers
                while (!outputDone) {
                    if (!inputDone) {
                        var eof = false
                        val index = extractor.sampleTrackIndex
                        if (index == videoIndex) {
                            val inputBufIndex = mDecoder!!.dequeueInputBuffer(TIMEOUT_USEC.toLong())
                            if (inputBufIndex >= 0) {
                                var inputBuf: ByteBuffer?
                                inputBuf = if (Build.VERSION.SDK_INT < 21) {
                                    decoderInputBuffers[inputBufIndex]
                                } else {
                                    mDecoder!!.getInputBuffer(inputBufIndex)
                                }
                                val chunkSize = extractor.readSampleData(inputBuf!!, 0)
                                if (chunkSize < 0) {
                                    mDecoder!!.queueInputBuffer(inputBufIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                    inputDone = true
                                } else {
                                    mDecoder!!.queueInputBuffer(inputBufIndex, 0, chunkSize, extractor.sampleTime, 0)
                                    extractor.advance()
                                }
                            }
                        } else if (index == -1) {
                            eof = true
                        }
                        if (eof) {
                            val inputBufIndex = mDecoder!!.dequeueInputBuffer(TIMEOUT_USEC.toLong())
                            if (inputBufIndex >= 0) {
                                mDecoder!!.queueInputBuffer(inputBufIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            }
                        }
                    }
                    var decoderOutputAvailable = !decoderDone
                    var encoderOutputAvailable = true
                    while (decoderOutputAvailable || encoderOutputAvailable) {
                        val encoderStatus = mEncoder!!.dequeueOutputBuffer(mBufferInfo!!, TIMEOUT_USEC.toLong())
                        if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                            encoderOutputAvailable = false
                        } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                            if (Build.VERSION.SDK_INT < 21) {
                                encoderOutputBuffers = mEncoder!!.outputBuffers
                            }
                        } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            val newFormat = mEncoder!!.outputFormat
                            if (videoTrackIndex == MEDIATYPE_NOT_AUDIO_VIDEO) {
                                videoTrackIndex = mMuxer!!.addTrack(newFormat)
                                mTrackIndex = videoTrackIndex
                                mMuxer!!.start()
                            }
                        } else if (encoderStatus < 0) {
                            throw RuntimeException("unexpected result from mEncoder.dequeueOutputBuffer: $encoderStatus")
                        } else {
                            var encodedData: ByteBuffer?
                            encodedData = if (Build.VERSION.SDK_INT < 21) {
                                encoderOutputBuffers!![encoderStatus]
                            } else {
                                mEncoder!!.getOutputBuffer(encoderStatus)
                            }
                            if (encodedData == null) {
                                throw RuntimeException("encoderOutputBuffer $encoderStatus was null")
                            }
                            if (mBufferInfo!!.size > 1) {
                                if (mBufferInfo!!.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                                    mMuxer!!.writeSampleData(videoTrackIndex, encodedData, mBufferInfo!!)
                                } else if (videoTrackIndex == MEDIATYPE_NOT_AUDIO_VIDEO) {
                                    val csd = ByteArray(mBufferInfo!!.size)
                                    encodedData.limit(mBufferInfo!!.offset + mBufferInfo!!.size)
                                    encodedData.position(mBufferInfo!!.offset)
                                    encodedData[csd]
                                    var sps: ByteBuffer? = null
                                    var pps: ByteBuffer? = null
                                    for (a in mBufferInfo!!.size - 1 downTo 0) {
                                        if (a > 3) {
                                            if (csd[a].toInt() == 1 && csd[a - 1].toInt() == 0 && csd[a - 2].toInt() == 0 && csd[a - 3].toInt() == 0) {
                                                sps = ByteBuffer.allocate(a - 3)
                                                pps = ByteBuffer.allocate(mBufferInfo!!.size - (a - 3))
                                                sps.put(csd, 0, a - 3).position(0)
                                                pps.put(csd, a - 3, mBufferInfo!!.size - (a - 3)).position(0)
                                                break
                                            }
                                        } else {
                                            break
                                        }
                                    }
                                    val newFormat = MediaFormat.createAudioFormat(MIME_TYPE, nbitrate, 1)
                                    if (sps != null && pps != null) {
                                        newFormat.setByteBuffer("csd-0", sps)
                                        newFormat.setByteBuffer("csd-1", pps)
                                    }
                                    videoTrackIndex = mMuxer!!.addTrack(newFormat)
                                    mMuxer!!.start()
                                }
                            }
                            outputDone = mBufferInfo!!.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                            mEncoder!!.releaseOutputBuffer(encoderStatus, false)
                        }
                        if (encoderStatus != MediaCodec.INFO_TRY_AGAIN_LATER) {
                            continue
                        }
                        if (!decoderDone) {
                            val decoderStatus = mDecoder!!.dequeueOutputBuffer(mBufferInfo!!, TIMEOUT_USEC.toLong())
                            if (decoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                                decoderOutputAvailable = false
                            } else if (decoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                            } else if (decoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                                val newFormat = mDecoder!!.outputFormat
                                Log.e(TAG, "newFormat = $newFormat")
                            } else if (decoderStatus < 0) {
                                throw RuntimeException("unexpected result from mDecoder.dequeueOutputBuffer: $decoderStatus")
                            } else {
                                var doRender = false
                                doRender = mBufferInfo!!.size != 0
                                if (endTime > 0 && mBufferInfo!!.presentationTimeUs >= endTime) {
                                    inputDone = true
                                    decoderDone = true
                                    doRender = false
                                    mBufferInfo!!.flags = mBufferInfo!!.flags or MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                }
                                if (startTime > 0 && videoTime == -1L) {
                                    if (mBufferInfo!!.presentationTimeUs < startTime) {
                                        doRender = false
                                        Log.e(TAG, "drop frame startTime = " + startTime + " present time = " + mBufferInfo!!.presentationTimeUs)
                                    } else {
                                        videoTime = mBufferInfo!!.presentationTimeUs
                                    }
                                }
                                mDecoder!!.releaseOutputBuffer(decoderStatus, doRender)
                                if (mBufferInfo!!.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                    decoderOutputAvailable = false
                                    outputDone = true
                                    Log.e(TAG, "decoder stream end")
                                }
                            }
                        }
                    }
                }
            }
            extractor.unselectTrack(videoIndex)
            writeAudioTrack(mAudioExtractor, mMuxer!!, mBufferInfo, cacheFile, muxerAudioTrackIndex)
        } catch (e: Exception) {
            error = true
            Log.e(TAG, e.message!!)
        } finally {
            if (extractor != null) {
                extractor.release()
                extractor = null
            }
            if (mAudioExtractor != null) {
                mAudioExtractor.release()
                mAudioExtractor = null
            }
            Log.e(TAG, "time = " + (System.currentTimeMillis() - time))
        }
        Log.e(TAG + " Path", path + "")
        Log.e(TAG + " Path", cacheFile.path + "")
        Log.e(TAG + " Path", inputFile.path + "")
        releaseCoder()
        return if (error) false else true
    }

    private fun checkParmsError(sourcePath: String?, destinationPath: String?, nbitrate: Int): Boolean {
        return if (nbitrate <= 0) true else false
    }

    @SuppressLint("WrongConstant")
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Throws(Exception::class)
    private fun simpleReadAndWriteTrack(extractor: MediaExtractor, mediaMuxer: MediaMuxer, info: MediaCodec.BufferInfo, start: Long, end: Long, file: File, isAudio: Boolean): Long {
        val trackIndex = selectTrack(extractor, isAudio)
        if (trackIndex >= 0) {
            extractor.selectTrack(trackIndex)
            val trackFormat = extractor.getTrackFormat(trackIndex)
            val muxerTrackIndex = mediaMuxer.addTrack(trackFormat)
            if (!isAudio) mediaMuxer.start()
            val maxBufferSize = trackFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
            var inputDone = false
            if (start > 0) {
                extractor.seekTo(start, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            } else {
                extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            }
            val buffer = ByteBuffer.allocateDirect(maxBufferSize)
            var startTime: Long = -1
            while (!inputDone) {
                var eof = false
                val index = extractor.sampleTrackIndex
                if (index == trackIndex) {
                    info.size = extractor.readSampleData(buffer, 0)
                    if (info.size < 0) {
                        info.size = 0
                        eof = true
                    } else {
                        info.presentationTimeUs = extractor.sampleTime
                        if (start > 0 && startTime == -1L) {
                            startTime = info.presentationTimeUs
                        }
                        if (end < 0 || info.presentationTimeUs < end) {
                            info.offset = 0
                            info.flags = extractor.sampleFlags
                            mediaMuxer.writeSampleData(muxerTrackIndex, buffer, info)
                            extractor.advance()
                        } else {
                            eof = true
                        }
                    }
                } else if (index == -1) {
                    eof = true
                }
                if (eof) {
                    inputDone = true
                }
            }
            extractor.unselectTrack(trackIndex)
            return startTime
        }
        return -1
    }

    @SuppressLint("WrongConstant")
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Throws(Exception::class)
    private fun writeAudioTrack(extractor: MediaExtractor, mediaMuxer: MediaMuxer, info: MediaCodec.BufferInfo?, file: File, muxerTrackIndex: Int): Long {
        val trackIndex = selectTrack(extractor, true)
        if (trackIndex >= 0) {
            val trackFormat = extractor.getTrackFormat(trackIndex)
            val maxBufferSize = info!!.size
            val byteBuffer = ByteBuffer.allocate(maxBufferSize)
            var audioPresentationTimeUs: Long = 0
            val audioBufferInfo = MediaCodec.BufferInfo()
            extractor.selectTrack(mTrackIndex)
            /*
       * the last audio presentation time.
       */
            var lastEndAudioTimeUs: Long = 0
            while (true) {
                val readAudioSampleSize = extractor.readSampleData(byteBuffer, 0)
                if (readAudioSampleSize < 0) {
                    //if end of the stream, unselect
                    extractor.unselectTrack(mTrackIndex)
                    if (audioPresentationTimeUs >= 0) {
                        //if has reach the end of the video time ,just exit
                        break
                    } else {
                        //if not the end of the video time, just repeat.
                        lastEndAudioTimeUs += audioPresentationTimeUs
                        extractor.selectTrack(trackIndex)
                        continue
                    }
                }
                val audioSampleTime = extractor.sampleTime
                audioBufferInfo.size = readAudioSampleSize
                audioBufferInfo.presentationTimeUs = audioSampleTime + lastEndAudioTimeUs
                if (audioBufferInfo.presentationTimeUs > 0) {
                    extractor.unselectTrack(trackIndex)
                    break
                }
                audioPresentationTimeUs = 0
                audioBufferInfo.offset = 0
                audioBufferInfo.flags = extractor.sampleFlags
                mediaMuxer.writeSampleData(trackIndex, byteBuffer, audioBufferInfo)
                extractor.advance()
            }
            //


//      boolean inputDone = false;
//      if (start > 0) {
//        extractor.seekTo(start, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
//      } else {
//        extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
//      }
//      ByteBuffer buffer = ByteBuffer.allocateDirect(maxBufferSize);
//      long startTime = -1;
//
//      while (!inputDone) {
//
//        boolean eof = false;
//        int index = extractor.getSampleTrackIndex();
//        if (index == trackIndex) {
//          info.size = extractor.readSampleData(buffer, 0);
//
//          if (info.size < 0) {
//            info.size = 0;
//            eof = true;
//          } else {
//            info.presentationTimeUs = extractor.getSampleTime();
//            if (start > 0 && startTime == -1) {
//              startTime = info.presentationTimeUs;
//            }
//            if (end < 0 || info.presentationTimeUs < end) {
//              info.offset = 0;
//              info.flags = extractor.getSampleFlags();
//              mediaMuxer.writeSampleData(muxerTrackIndex, buffer, info);
//              extractor.advance();
//            } else {
//              eof = true;
//            }
//          }
//        } else if (index == -1) {
//          eof = true;
//        }
//        if (eof) {
//          inputDone = true;
//        }
//      }
//
//      extractor.unselectTrack(trackIndex);
//      return startTime;
        }
        return -1
    }

    private fun selectTrack(extractor: MediaExtractor, audio: Boolean): Int {
        val numTracks = extractor.trackCount
        for (i in 0 until numTracks) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (audio) {
                if (mime!!.startsWith("audio/")) {
                    return i
                }
            } else {
                if (mime!!.startsWith("video/")) {
                    return i
                }
            }
        }
        return MEDIATYPE_NOT_AUDIO_VIDEO
    }

    private fun selectEncoder(mime: String): String? {
        for (index in 0 until MediaCodecList.getCodecCount()) {
            val codecInfo = MediaCodecList.getCodecInfoAt(index)
            if (!codecInfo.isEncoder) {
                continue
            }
            for (type in codecInfo.supportedTypes) {
                if (type.equals(mime, ignoreCase = true)) {
                    return MIME_TYPE
                }
            }
        }
        return null
    }

    /**
     * Configures encoder and muxer state, and prepares the input Surface.
     */
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private fun prepareEncoder(format: MediaFormat) {
        mBufferInfo = MediaCodec.BufferInfo()
        val mAudioFormat = MediaFormat.createAudioFormat(MIME_TYPE, mBitRate, 1)
        if (VERBOSE) Log.d(TAG, "format: $mAudioFormat")
        mAudioFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, format.getInteger(MediaFormat.KEY_SAMPLE_RATE))
        mAudioFormat.setInteger(MediaFormat.KEY_BIT_RATE, format.getInteger(MediaFormat.KEY_BIT_RATE))
        mAudioFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, format.getInteger(MediaFormat.KEY_CHANNEL_COUNT))
        mAudioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        mAudioFormat.setString(MediaFormat.KEY_MIME, MIME_TYPE)
        mAudioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 10 * 1024)
        try {
            mEncoder = MediaCodec.createEncoderByType(MIME_TYPE)
            mEncoder!!.configure(mAudioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            mEncoder!!.start()
            Log.d(TAG, "prepareEncoder...")
        } catch (e: IOException) {
            e.printStackTrace()
        }
        try {
            mDecoder = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        } catch (e: IOException) {
            e.printStackTrace()
        }
        mDecoder!!.configure(format, null, null, 0)
        mDecoder!!.start()
        //    mTrackIndex = -1;
    }

    /**
     * Releases encoder resources.  May be called after partial / failed initialization.
     */
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private fun releaseCoder() {
        if (VERBOSE) Log.d(TAG, "releasing encoder objects")
        if (mEncoder != null) {
            mEncoder!!.stop()
            mEncoder!!.release()
            mEncoder = null
        }
        if (mDecoder != null) {
            mDecoder!!.stop()
            mDecoder!!.release()
            mDecoder = null
        }
        if (mInputSurface != null) {
            mInputSurface!!.release()
            mInputSurface = null
        }
        if (mMuxer != null) {
            mMuxer!!.stop()
            mMuxer!!.release()
            mMuxer = null
        }
    }

    companion object {
        private const val TAG = "AudioCompressor"
        private const val VERBOSE = true // lots of logging
        const val MIME_TYPE = MediaFormat.MIMETYPE_AUDIO_AAC
        private const val MEDIATYPE_NOT_AUDIO_VIDEO = -233
    }
}
