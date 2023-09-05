package com.reactnativecompressor.Utils

import android.util.Log
import com.facebook.react.bridge.ReactApplicationContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicReference

class Downloader {
  companion object {
    private const val TAG = "react-native-compessor"

    fun downloadMediaWithProgress(mediaUrl: String?, uuid: String,progressDivider:Int, reactContext: ReactApplicationContext): String? {
      downloadCompression[0] = 0
      val client = OkHttpClient()
      val request: Request = Request.Builder()
        .url(mediaUrl!!)
        .build()
      val semaphore = Semaphore(0) // Semaphore to wait for the download to complete
      val filePathRef = AtomicReference<String?>(null) // To store the file path

      // Perform the request asynchronously
      client.newCall(request).enqueue(object : Callback {
        @Throws(IOException::class)
        override fun onResponse(call: Call, response: Response) {
          if (response.isSuccessful) {
            val responseBody = response.body
            if (responseBody != null) {
              var fileExtension = "unknown" // Default extension

              // Detect the file extension based on the Content-Type header
              val contentType = response.header("Content-Type")
              if (contentType != null) {
                if (contentType == "image/jpeg") {
                  fileExtension = "jpg"
                } else if (contentType == "image/png") {
                  fileExtension = "png"
                } else if (contentType == "video/mp4") {
                  fileExtension = "mp4"
                }
              }
              val cacheDir = reactContext.cacheDir
              val randomFileName = UUID.randomUUID().toString() + "." + fileExtension
              val mediaFile = File(cacheDir, randomFileName)
              try {
                FileOutputStream(mediaFile).use { fos ->
                  val inputStream = BufferedInputStream(responseBody.byteStream())
                  val buffer = ByteArray(4096)
                  var bytesRead: Int
                  var totalBytesRead: Long = 0
                  var totalBytes = responseBody.contentLength()
                  if (totalBytes <= 0) {
                    totalBytes = 31457280
                  }
                  Log.d(TAG, "$totalBytesRead totalBytesRead $totalBytes")
                  Log.d(TAG, "$response responseBody $responseBody")
                  while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    fos.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead.toLong()
                    val progressRatio = totalBytesRead.toDouble() / totalBytes
                    val progress = (progressRatio * 100).toInt()
                    sendProgressUpdate(progress, uuid, progressDivider)
                  }
                  fos.flush()
                  val filePath = "file://" + mediaFile.absolutePath
                  MediaCache.addCompletedImagePath(filePath)
                  filePathRef.set(filePath) // Set the file path
                }
              } catch (e: IOException) {
                e.printStackTrace()
                sendErrorResult(e.message, uuid)
              } finally {
                semaphore.release() // Release the semaphore when download is complete
              }
            }
          } else {
            sendErrorResult("Failed to download media: " + response.message, uuid)
            semaphore.release() // Release the semaphore in case of error
          }
        }

        override fun onFailure(call: Call, e: IOException) {
          e.printStackTrace()
          sendErrorResult(e.message, uuid)
          semaphore.release() // Release the semaphore in case of failure
        }
      })
      try {
        semaphore.acquire() // Wait for the download to complete
      } catch (e: InterruptedException) {
        e.printStackTrace()
      }
      return filePathRef.get() // Return the file path
    }

    val downloadCompression = intArrayOf(0)
    private fun sendProgressUpdate(progress: Int, uuid: String, progressDivider:Int=0) {
      val roundProgress = Math.round(progress.toFloat())
      if (progressDivider==0||(roundProgress % progressDivider == 0 && roundProgress > downloadCompression[0])) {
        EventEmitterHandler.emitDownloadProgress(progress / 100.0,uuid)
        Log.d(TAG, "downloadProgress: " + progress / 100.0)
        downloadCompression[0] = roundProgress
      }
    }

    private fun sendErrorResult(error: String?, uuid: String?) {
      EventEmitterHandler.emitDownloadProgressError(uuid,error)
    }
  }
}
