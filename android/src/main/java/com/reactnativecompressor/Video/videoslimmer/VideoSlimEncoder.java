package com.reactnativecompressor.Video.videoslimmer;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import android.media.MediaPlayer;
import android.os.Build;
import android.util.Log;
import android.view.Surface;

import com.reactnativecompressor.Video.videoslimmer.listner.SlimProgressListener;
import com.reactnativecompressor.Video.videoslimmer.muxer.CodecInputSurface;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

public class VideoSlimEncoder {

    private static final String TAG = "VideoSlimEncoder";
    private static final boolean VERBOSE = true;           // lots of logging
    public String path;
    private String outputPath;
    public final static String MIME_TYPE = "video/avc";
    private MediaCodec.BufferInfo mBufferInfo;
    private MediaMuxer mMuxer;
    private MediaCodec mEncoder;
    private MediaCodec mDecoder;
    private int mTrackIndex;
    private CodecInputSurface mInputSurface;
    // size of a frame, in pixels
    private int mWidth = -1;
    private int mHeight = -1;
    // bit rate, in bits per second
    private int mBitRate = -1;
    private static int FRAME_RATE = 25;               // 15fps
    private static int IFRAME_INTERVAL = 10;          // 10 seconds between I-frames
    private static final int MEDIATYPE_NOT_AUDIO_VIDEO = -233;
    private final int TIMEOUT_USEC = 2500;

    public  VideoSlimEncoder () {

    }


    /***
     * trans video and audio  by mediacodec
     *
     * */
    public boolean convertVideo(final String sourcePath, String destinationPath, int nwidth, int nheight, int nbitrate, SlimProgressListener listener) {

        this.path = sourcePath;
        this.outputPath = destinationPath;

        if (checkParmsError(sourcePath, destinationPath, nwidth, nheight, nbitrate)) {
            return false;
        }

        //get origin video info
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(path);
        String width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
        String height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
        String rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
      //  String framecount = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT);
        long duration = Long.valueOf(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)) * 1000;

        long startTime = -1;
        long endTime = -1;

        int originalWidth = Integer.valueOf(width);
        int originalHeight = Integer.valueOf(height);

        mBitRate = nbitrate;
        mWidth = nwidth;
        mHeight = nheight;


       // NUM_FRAMES = Integer.getInteger(framecount);
        //IFRAME_INTERVAL = Integer.valueOf(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));


        boolean error = false;
        long videoStartTime = -1;

        long time = System.currentTimeMillis();

        File cacheFile = new File(destinationPath);
        File inputFile = new File(path);
        if (!inputFile.canRead()) {

            return false;
        }

        MediaExtractor extractor = null;
        MediaExtractor mAudioExtractor = null;

        try {
            // video MediaExtractor
            extractor = new MediaExtractor();
            extractor.setDataSource(inputFile.toString());

            // audio MediaExtractor
            mAudioExtractor = new MediaExtractor();
            mAudioExtractor.setDataSource(inputFile.toString());
            try {
                mMuxer = new MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            } catch (IOException ioe) {


                throw new RuntimeException("MediaMuxer creation failed", ioe);
            }

            int muxerAudioTrackIndex = 0;


            int audioIndex = selectTrack(mAudioExtractor, true);
            if (audioIndex >= 0) {
                mAudioExtractor.selectTrack(audioIndex);
                mAudioExtractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                MediaFormat trackFormat = mAudioExtractor.getTrackFormat(audioIndex);
                muxerAudioTrackIndex = mMuxer.addTrack(trackFormat);

               // extractor.unselectTrack(muxerAudioTrackIndex);
            }

            /**
             * mediacodec + surface + opengl
             * */
            if (nwidth != originalWidth || nheight != originalHeight) {

                int videoIndex = selectTrack(extractor, false);

                if (videoIndex >= 0) {

                    long videoTime = -1;
                    boolean outputDone = false;
                    boolean inputDone = false;
                    boolean decoderDone = false;
                    int swapUV = 0;
                    int videoTrackIndex = MEDIATYPE_NOT_AUDIO_VIDEO;


                    extractor.selectTrack(videoIndex);
                    if (startTime > 0) {
                        extractor.seekTo(startTime, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                    } else {
                        extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                    }
                    MediaFormat inputFormat = extractor.getTrackFormat(videoIndex);


                    /**
                     ** init mediacodec  / encoder and decoder
                     **/
                    prepareEncoder(inputFormat);


                    ByteBuffer[] decoderInputBuffers = null;
                    ByteBuffer[] encoderOutputBuffers = null;


                    decoderInputBuffers = mDecoder.getInputBuffers();
                    encoderOutputBuffers = mEncoder.getOutputBuffers();


                    while (!outputDone) {
                        if (!inputDone) {
                            boolean eof = false;
                            int index = extractor.getSampleTrackIndex();
                            if (index == videoIndex) {
                                int inputBufIndex = mDecoder.dequeueInputBuffer(TIMEOUT_USEC);
                                if (inputBufIndex >= 0) {
                                    ByteBuffer inputBuf;
                                    if (Build.VERSION.SDK_INT < 21) {
                                        inputBuf = decoderInputBuffers[inputBufIndex];
                                    } else {
                                        inputBuf = mDecoder.getInputBuffer(inputBufIndex);
                                    }
                                    int chunkSize = extractor.readSampleData(inputBuf, 0);
                                    if (chunkSize < 0) {
                                        mDecoder.queueInputBuffer(inputBufIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                        inputDone = true;
                                    } else {
                                        mDecoder.queueInputBuffer(inputBufIndex, 0, chunkSize, extractor.getSampleTime(), 0);
                                        extractor.advance();
                                    }
                                }
                            } else if (index == -1) {
                                eof = true;
                            }
                            if (eof) {
                                int inputBufIndex = mDecoder.dequeueInputBuffer(TIMEOUT_USEC);
                                if (inputBufIndex >= 0) {
                                    mDecoder.queueInputBuffer(inputBufIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                    inputDone = true;
                                }
                            }
                        }

                        boolean decoderOutputAvailable = !decoderDone;
                        boolean encoderOutputAvailable = true;

                        while (decoderOutputAvailable || encoderOutputAvailable) {

                            int encoderStatus = mEncoder.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
                            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                                encoderOutputAvailable = false;
                            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                                if (Build.VERSION.SDK_INT < 21) {
                                    encoderOutputBuffers = mEncoder.getOutputBuffers();
                                }
                            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                                MediaFormat newFormat = mEncoder.getOutputFormat();
                                if (videoTrackIndex == MEDIATYPE_NOT_AUDIO_VIDEO) {
                                    videoTrackIndex = mMuxer.addTrack(newFormat);
                                    mTrackIndex = videoTrackIndex;
                                    mMuxer.start();
                                }
                            } else if (encoderStatus < 0) {
                                throw new RuntimeException("unexpected result from mEncoder.dequeueOutputBuffer: " + encoderStatus);
                            } else {
                                ByteBuffer encodedData;
                                if (Build.VERSION.SDK_INT < 21) {
                                    encodedData = encoderOutputBuffers[encoderStatus];
                                } else {
                                    encodedData = mEncoder.getOutputBuffer(encoderStatus);
                                }
                                if (encodedData == null) {
                                    throw new RuntimeException("encoderOutputBuffer " + encoderStatus + " was null");
                                }
                                if (mBufferInfo.size > 1) {
                                    if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                                        mMuxer.writeSampleData(videoTrackIndex, encodedData, mBufferInfo);
                                    } else if (videoTrackIndex == MEDIATYPE_NOT_AUDIO_VIDEO) {
                                        byte[] csd = new byte[mBufferInfo.size];
                                        encodedData.limit(mBufferInfo.offset + mBufferInfo.size);
                                        encodedData.position(mBufferInfo.offset);
                                        encodedData.get(csd);
                                        ByteBuffer sps = null;
                                        ByteBuffer pps = null;
                                        for (int a = mBufferInfo.size - 1; a >= 0; a--) {
                                            if (a > 3) {
                                                if (csd[a] == 1 && csd[a - 1] == 0 && csd[a - 2] == 0 && csd[a - 3] == 0) {
                                                    sps = ByteBuffer.allocate(a - 3);
                                                    pps = ByteBuffer.allocate(mBufferInfo.size - (a - 3));
                                                    sps.put(csd, 0, a - 3).position(0);
                                                    pps.put(csd, a - 3, mBufferInfo.size - (a - 3)).position(0);
                                                    break;
                                                }
                                            } else {
                                                break;
                                            }
                                        }

                                        MediaFormat newFormat = MediaFormat.createVideoFormat(MIME_TYPE, nwidth, nheight);
                                        if (sps != null && pps != null) {
                                            newFormat.setByteBuffer("csd-0", sps);
                                            newFormat.setByteBuffer("csd-1", pps);
                                        }
                                        videoTrackIndex = mMuxer.addTrack(newFormat);
                                        mMuxer.start();
                                    }
                                }
                                outputDone = (mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                                mEncoder.releaseOutputBuffer(encoderStatus, false);
                            }
                            if (encoderStatus != MediaCodec.INFO_TRY_AGAIN_LATER) {
                                continue;
                            }

                            if (!decoderDone) {
                                int decoderStatus = mDecoder.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
                                if (decoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                                    decoderOutputAvailable = false;
                                } else if (decoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {

                                } else if (decoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                                    MediaFormat newFormat = mDecoder.getOutputFormat();
                                    Log.e(TAG, "newFormat = " + newFormat);
                                } else if (decoderStatus < 0) {
                                    throw new RuntimeException("unexpected result from mDecoder.dequeueOutputBuffer: " + decoderStatus);
                                } else {
                                    boolean doRender = false;

                                    doRender = mBufferInfo.size != 0;

                                    if (endTime > 0 && mBufferInfo.presentationTimeUs >= endTime) {
                                        inputDone = true;
                                        decoderDone = true;
                                        doRender = false;
                                        mBufferInfo.flags |= MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                                    }
                                    if (startTime > 0 && videoTime == -1) {
                                        if (mBufferInfo.presentationTimeUs < startTime) {
                                            doRender = false;
                                            Log.e(TAG, "drop frame startTime = " + startTime + " present time = " + mBufferInfo.presentationTimeUs);
                                        } else {
                                            videoTime = mBufferInfo.presentationTimeUs;
                                        }
                                    }
                                    mDecoder.releaseOutputBuffer(decoderStatus, doRender);
                                    if (doRender) {
                                        boolean errorWait = false;
                                        try {
                                            mInputSurface.awaitNewImage();
                                        } catch (Exception e) {
                                            errorWait = true;
                                           if(e.getMessage().equals("java.lang.InterruptedException"))
                                           {
                                             return false;
                                           }
                                            Log.e(TAG, e.getMessage());
                                        }
                                        if (!errorWait) {

                                            mInputSurface.drawImage();
                                            mInputSurface.setPresentationTime(mBufferInfo.presentationTimeUs * 1000);

                                            if (listener != null) {
                                                listener.onProgress((float) mBufferInfo.presentationTimeUs / (float) duration * 100);
                                            }

                                            mInputSurface.swapBuffers();

                                        }
                                    }
                                    if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                        decoderOutputAvailable = false;
                                        Log.e(TAG, "decoder stream end");

                                        mEncoder.signalEndOfInputStream();

                                    }
                                }
                            }
                        }
                    }
                    if (videoTime != -1) {
                        videoStartTime = videoTime;
                    }


                }


                extractor.unselectTrack(videoIndex);

            } else {
                Log.e(TAG,"startvideorecord");
                long videoTime = simpleReadAndWriteTrack(extractor, mMuxer, mBufferInfo, startTime, endTime, cacheFile, false);
                if (videoTime != -1) {
                    videoStartTime = videoTime;
                }
            }

//            if (!error) {
//                Log.e(TAG,"startaudiorecord");
//                simpleReadAndWriteTrack(extractor, mMuxer, mBufferInfo, videoStartTime, endTime, cacheFile, true);
//            }

            writeAudioTrack(mAudioExtractor, mMuxer, mBufferInfo, videoStartTime, endTime, cacheFile, muxerAudioTrackIndex);

        } catch (Exception e) {
            error = true;
            Log.e(TAG, e.getMessage());
        } finally {
            if (extractor != null) {
                extractor.release();
                extractor = null;
            }


            if (mAudioExtractor != null) {
                mAudioExtractor.release();
                mAudioExtractor = null;
            }
            Log.e(TAG, "time = " + (System.currentTimeMillis() - time));
        }


        Log.e("ViratPath", path + "");
        Log.e("ViratPath", cacheFile.getPath() + "");
        Log.e("ViratPath", inputFile.getPath() + "");



        releaseCoder();

        if(error)
            return  false;
        else
            return true;
    }


    private boolean checkParmsError(String sourcePath, String destinationPath, int nwidth, int nheight, int nbitrate) {


        if (nwidth <= 0 || nheight <= 0 || nbitrate <= 0)
            return true;
        else
            return false;

    }


    private long simpleReadAndWriteTrack(MediaExtractor extractor, MediaMuxer mediaMuxer, MediaCodec.BufferInfo info, long start, long end, File file, boolean isAudio) throws Exception {
        int trackIndex = selectTrack(extractor, isAudio);
        if (trackIndex >= 0) {
            extractor.selectTrack(trackIndex);
            MediaFormat trackFormat = extractor.getTrackFormat(trackIndex);
            int muxerTrackIndex = mediaMuxer.addTrack(trackFormat);

            if(!isAudio)
             mediaMuxer.start();

            int maxBufferSize = trackFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
            boolean inputDone = false;
            if (start > 0) {
                extractor.seekTo(start, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
            } else {
                extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
            }
            ByteBuffer buffer = ByteBuffer.allocateDirect(maxBufferSize);
            long startTime = -1;

            while (!inputDone) {

                boolean eof = false;
                int index = extractor.getSampleTrackIndex();
                if (index == trackIndex) {
                    info.size = extractor.readSampleData(buffer, 0);

                    if (info.size < 0) {
                        info.size = 0;
                        eof = true;
                    } else {
                        info.presentationTimeUs = extractor.getSampleTime();
                        if (start > 0 && startTime == -1) {
                            startTime = info.presentationTimeUs;
                        }
                        if (end < 0 || info.presentationTimeUs < end) {
                            info.offset = 0;
                            info.flags = extractor.getSampleFlags();
                            mediaMuxer.writeSampleData(muxerTrackIndex, buffer, info);
                            extractor.advance();
                        } else {
                            eof = true;
                        }
                    }
                } else if (index == -1) {
                    eof = true;
                }
                if (eof) {
                    inputDone = true;
                }
            }

            extractor.unselectTrack(trackIndex);
            return startTime;
        }
        return -1;
    }


    private long writeAudioTrack(MediaExtractor extractor, MediaMuxer mediaMuxer, MediaCodec.BufferInfo info, long start, long end, File file,int muxerTrackIndex ) throws Exception {
        int trackIndex = selectTrack(extractor, true);
        if (trackIndex >= 0) {
            extractor.selectTrack(trackIndex);
            MediaFormat trackFormat = extractor.getTrackFormat(trackIndex);


            int maxBufferSize = trackFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
            boolean inputDone = false;
            if (start > 0) {
                extractor.seekTo(start, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
            } else {
                extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
            }
            ByteBuffer buffer = ByteBuffer.allocateDirect(maxBufferSize);
            long startTime = -1;

            while (!inputDone) {

                boolean eof = false;
                int index = extractor.getSampleTrackIndex();
                if (index == trackIndex) {
                    info.size = extractor.readSampleData(buffer, 0);

                    if (info.size < 0) {
                        info.size = 0;
                        eof = true;
                    } else {
                        info.presentationTimeUs = extractor.getSampleTime();
                        if (start > 0 && startTime == -1) {
                            startTime = info.presentationTimeUs;
                        }
                        if (end < 0 || info.presentationTimeUs < end) {
                            info.offset = 0;
                            info.flags = extractor.getSampleFlags();
                            mediaMuxer.writeSampleData(muxerTrackIndex, buffer, info);
                            extractor.advance();
                        } else {
                            eof = true;
                        }
                    }
                } else if (index == -1) {
                    eof = true;
                }
                if (eof) {
                    inputDone = true;
                }
            }

            extractor.unselectTrack(trackIndex);
            return startTime;
        }
        return -1;
    }


    private int selectTrack(MediaExtractor extractor, boolean audio) {
        int numTracks = extractor.getTrackCount();
        for (int i = 0; i < numTracks; i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (audio) {
                if (mime.startsWith("audio/")) {
                    return i;
                }
            } else {
                if (mime.startsWith("video/")) {
                    return i;
                }
            }
        }
        return MEDIATYPE_NOT_AUDIO_VIDEO;
    }


    /**
     * Configures encoder and muxer state, and prepares the input Surface.
     */
    private void prepareEncoder(MediaFormat inputFormat) {
        mBufferInfo = new MediaCodec.BufferInfo();

        MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, mWidth, mHeight);

        // Set some properties.  Failing to specify some of these can cause the MediaCodec
        // configure() call to throw an unhelpful exception.
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, mBitRate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);
        if (VERBOSE) Log.d(TAG, "format: " + format);

        // Create a MediaCodec encoder, and configure it with our format.  Get a Surface
        // we can use for input and wrap it with a class that handles the EGL work.
        //
        // If you want to have two EGL contexts -- one for display, one for recording --
        // you will likely want to defer instantiation of CodecInputSurface until after the
        // "display" EGL context is created, then modify the eglCreateContext call to
        // take eglGetCurrentContext() as the share_context argument.
        try {
            mEncoder = MediaCodec.createEncoderByType(MIME_TYPE);
        } catch (IOException e) {
            e.printStackTrace();
        }
        mEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mInputSurface = new CodecInputSurface(mEncoder.createInputSurface());
        mInputSurface.makeCurrent();
        mEncoder.start();

        try {
            mDecoder = MediaCodec.createDecoderByType(inputFormat.getString(MediaFormat.KEY_MIME));
        } catch (IOException e) {
            e.printStackTrace();
        }
        mInputSurface.createRender();
        mDecoder.configure(inputFormat, mInputSurface.getSurface(), null, 0);
        mDecoder.start();

        // Output filename.  Ideally this would use Context.getFilesDir() rather than a
        // hard-coded output directory.

        // Create a MediaMuxer.  We can't add the video track and start() the muxer here,
        // because our MediaFormat doesn't have the Magic Goodies.  These can only be
        // obtained from the encoder after it has started processing data.
        //
        // We're not actually interested in multiplexing audio.  We just want to convert
        // the raw H.264 elementary stream we get from MediaCodec into a .mp4 file.


        mTrackIndex = -1;
    }

    /**
     * Releases encoder resources.  May be called after partial / failed initialization.
     */
    private void releaseCoder() {
        if (VERBOSE) Log.d(TAG, "releasing encoder objects");
        if (mEncoder != null) {
            mEncoder.stop();
            mEncoder.release();
            mEncoder = null;
        }
        if (mDecoder != null) {
            mDecoder.stop();
            mDecoder.release();
            mDecoder = null;
        }
        if (mInputSurface != null) {
            mInputSurface.release();
            mInputSurface = null;
        }
        if (mMuxer != null) {
            mMuxer.stop();
            mMuxer.release();
            mMuxer = null;
        }
    }


}
