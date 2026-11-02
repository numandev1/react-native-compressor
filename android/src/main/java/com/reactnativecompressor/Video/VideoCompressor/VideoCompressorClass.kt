package com.reactnativecompressor.Video.VideoCompressor

import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import com.facebook.react.bridge.ReactApplicationContext
import com.reactnativecompressor.Video.VideoCompressor.compressor.Compressor
import com.reactnativecompressor.Video.VideoCompressor.compressor.Compressor.compressVideo
import com.reactnativecompressor.Video.VideoCompressor.video.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class VideoCompressorClass(private val context: ReactApplicationContext) {
  // Cancellation is scoped to this compression (#300). The pipeline checks the
  // flag and returns a Result; the coroutine itself is never cancelled, so the
  // listener always gets exactly one terminal callback.
  private val cancelled = AtomicBoolean(false)

  @JvmOverloads
  fun start(
    srcPath: String,
    destPath: String,
    outputWidth: Int,
    outputHeight: Int,
    bitrate: Int,
    frameRate: Int,
    stripAudio: Boolean = false,
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
      frameRate,
      stripAudio,
      listener,
      destPath
    )
  }

  /** Stops this compression at the next check. The listener still gets exactly one terminal callback. */
  fun cancel() {
    cancelled.set(true)
  }

  private fun deleteOutputs(destPath: String) {
    val file = File(destPath)
    runCatching { file.delete() }
    runCatching { Compressor.getStreamableOutputFile(file).delete() }
  }

  private fun doVideoCompression(
    uris: List<Uri>,
    isStreamable: Boolean,
    outputWidth: Int,
    outputHeight: Int,
    bitrate: Int,
    frameRate: Int,
    stripAudio: Boolean,
    listener: CompressionListener,
    destPath: String
  ) {
    var streamableFile: File? = null
    for (i in uris.indices) {
      CoroutineScope(Dispatchers.Main).launch {
        val desFile = File(destPath)

        if (isStreamable)
          streamableFile = File(destPath)

        listener.onStart(i)
        // A throw from the pipeline (e.g. malformed metadata) must still settle
        // the Promise, so report it as a failure.
        val result = try {
          startCompression(
            i,
            uris[i],
            desFile.path,
            streamableFile?.path,
            outputWidth,
            outputHeight,
            bitrate,
            frameRate,
            stripAudio,
            listener,
          )
        } catch (e: Throwable) {
          Result(i, success = false, failureMessage = e.message ?: "An error has occurred!")
        }

        // Runs in Main(UI) Thread. Exactly one terminal callback; failed and
        // cancelled compressions leave no partial output behind.
        when {
          cancelled.get() -> {
            deleteOutputs(desFile.path)
            listener.onCancelled(i)
          }
          result.success -> listener.onSuccess(i, result.size, result.path)
          else -> {
            deleteOutputs(desFile.path)
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
    frameRate: Int,
    disableAudio: Boolean = false,
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
      frameRate,
      disableAudio,
      cancelled,
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
