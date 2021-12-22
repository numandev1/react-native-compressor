package com.reactnativecompressor.Video.videoslimmer;

public class VideoSlimmer {


    public static VideoSlimTask convertVideo(String srcPath, String destPath, int outputWidth, int outputHeight, int bitrate, ProgressListener listener) {
        VideoSlimTask task = new VideoSlimTask(listener);
        task.execute(srcPath, destPath, outputWidth, outputHeight, bitrate);
        return task;
    }

    public static interface ProgressListener {

        void onStart();
        void onFinish(boolean result);
        void onProgress(float progress);
        void onError(String errorMessage);
    }

}
