package com.reactnativecompressor.Video.VideoCompressor

import androidx.annotation.MainThread
import androidx.annotation.WorkerThread

/**
 * Interface for listening to video compression events.
 */
interface CompressionListener {
  /**
   * Called when video compression starts.
   *
   * @param index The index of the video being compressed.
   */
  @MainThread
  fun onStart(index: Int)

  /**
   * Called when video compression succeeds.
   *
   * @param index The index of the video that was compressed.
   * @param size The size of the compressed video.
   * @param path The path to the compressed video file (nullable).
   */
  @MainThread
  fun onSuccess(index: Int, size: Long, path: String?)

  /**
   * Called when video compression fails.
   *
   * @param index The index of the video that failed to compress.
   * @param failureMessage A message describing the reason for the failure.
   */
  @MainThread
  fun onFailure(index: Int, failureMessage: String)

  /**
   * Called to report the progress of video compression.
   *
   * @param index The index of the video being compressed.
   * @param percent The progress percentage (0.0 to 100.0).
   */
  @WorkerThread
  fun onProgress(index: Int, percent: Float)

  /**
   * Called when video compression is canceled.
   *
   * @param index The index of the video that was canceled.
   */
  @WorkerThread
  fun onCancelled(index: Int)
}

/**
 * Interface for tracking the progress of video compression.
 */
interface CompressionProgressListener {
  /**
   * Called when the progress of video compression changes.
   *
   * @param index The index of the video being compressed.
   * @param percent The current progress percentage (0.0 to 100.0).
   */
  fun onProgressChanged(index: Int, percent: Float)

  /**
   * Called when the compression progress is canceled.
   *
   * @param index The index of the video compression that was canceled.
   */
  fun onProgressCancelled(index: Int)
}
