package com.reactnativecompressor.Video.VideoCompressor

import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import com.facebook.react.bridge.ReactApplicationContext
import com.reactnativecompressor.Video.VideoCompressor.compressor.Compressor.compressVideo
import com.reactnativecompressor.Video.VideoCompressor.compressor.Compressor.isRunning
import com.reactnativecompressor.Video.VideoCompressor.video.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class VideoCompressorClass(private val context: ReactApplicationContext) {
  private var job: Job? = null

  @JvmOverloads
  fun start(
    srcPath: String,
    destPath: String,
    outputWidth: Int,
    outputHeight: Int,
    bitrate: Int,
    listener: CompressionListener,
  ) {
    val uris = mutableListOf<Uri>()
    val uri = Uri.parse(srcPath)
    uris.add(uri)
    val isStreamable: Boolean = false
    doVideoCompression(
      uris,
      isStreamable,
      outputWidth,
      outputHeight,
      bitrate,
      listener,
      destPath
    )
  }

  fun cancel() {
    job?.cancel()
    isRunning = false
  }

  private fun doVideoCompression(
    uris: List<Uri>,
    isStreamable: Boolean,
    outputWidth: Int,
    outputHeight: Int,
    bitrate: Int,
    listener: CompressionListener,
    destPath: String
  ) {
    var streamableFile: File? = null
    for (i in uris.indices) {
      job = CoroutineScope(Dispatchers.Main).launch {
        val desFile = File(destPath)

        if (isStreamable)
          streamableFile = File(destPath)

        desFile?.let {
          isRunning = true
          listener.onStart(i)
          val result = startCompression(
            i,
            uris[i],
            desFile.path,
            streamableFile?.path,
            outputWidth,
            outputHeight,
            bitrate,
            listener,
          )

          // Runs in Main(UI) Thread
          if (result.success) {
            listener.onSuccess(i, result.size, result.path)
          } else {
            listener.onFailure(i, result.failureMessage ?: "An error has occurred!")
          }
        }
      }
    }
  }

  private suspend fun startCompression(
    index: Int,
    srcUri: Uri,
    destPath: String,
    streamableFile: String? = null,
    outputWidth: Int,
    outputHeight: Int,
    bitrate: Int,
    listener: CompressionListener,
  ): Result = withContext(Dispatchers.Default) {
    return@withContext compressVideo(
      index,
      context,
      srcUri,
      destPath,
      streamableFile,
      outputWidth,
      outputHeight,
      bitrate,
      object : CompressionProgressListener {
        override fun onProgressChanged(index: Int, percent: Float) {
          listener.onProgress(index, percent)
        }

        override fun onProgressCancelled(index: Int) {
          listener.onCancelled(index)
        }
      },
    )
  }

  private fun getMediaPath(uri: Uri): String {
    val scheme = uri.scheme
    if (scheme == null || scheme == "file") {
      // The URI is a file URI, so use the path directly.
      return uri.path ?: ""
    } else {
      // The URI is not a file URI, so try to copy the content to a file.
      val resolver = context.contentResolver
      val projection = arrayOf(MediaStore.Video.Media.DATA)
      var cursor: Cursor? = null
      try {
        cursor = resolver.query(uri, projection, null, null, null)
        if (cursor != null && cursor.moveToFirst()) {
          val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
          return cursor.getString(columnIndex)
        }
      } catch (e: Exception) {
        // Handle the exception as needed.
      } finally {
        cursor?.close()
      }
    }

    // If all else fails, return an empty string or handle the error accordingly.
    return ""
  }
}
