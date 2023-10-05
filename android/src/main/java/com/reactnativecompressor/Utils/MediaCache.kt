package com.reactnativecompressor.Utils

import android.util.Log
import java.io.File

object MediaCache {
    private const val TAG = "MediaCache"
    private val completedImagePaths: MutableList<String> = ArrayList()
    fun addCompletedImagePath(imagePath: String?) {
        if (imagePath != null) {
            // Your code to add the imagePath to a list or perform other actions if needed.
            completedImagePaths.add(imagePath)
            Log.d(TAG, "Successfully added image path: $imagePath")
        }
    }

    @JvmStatic
    fun removeCompletedImagePath(imagePath: String?) {
        var imagePath = imagePath
        if (imagePath != null) {
            if (completedImagePaths.contains(imagePath)) {
                // Image path exists in the list, so remove it
                completedImagePaths.remove(imagePath)
                if (imagePath.startsWith("file://")) {
                    imagePath = imagePath.substring(7) // Remove "file://"
                }

                // Remove the image file
                val file = File(imagePath)
                if (file.exists()) {
                    if (file.delete()) {
                        Log.d(TAG, "Successfully deleted image file: $imagePath")
                    } else {
                        Log.d(TAG, "Failed to delete image file: $imagePath")
                    }
                } else {
                    Log.d(TAG, "Image file not found: $imagePath")
                }
            } else {
                Log.d(TAG, "Image path not found in the completedImagePaths list: $imagePath")
            }
        }
    }

    fun cleanupCache() {
        // Iterate through the list of completed image paths and delete the corresponding files
        for (imagePath in completedImagePaths) {
            val file = File(imagePath)
            if (file.exists()) {
                if (file.delete()) {
                    Log.d(TAG, "Successfully deleted image file during cache cleanup: $imagePath")
                } else {
                    Log.d(TAG, "Failed to delete image file during cache cleanup: $imagePath")
                }
            } else {
                Log.d(TAG, "Image file not found during cache cleanup: $imagePath")
            }
        }
        completedImagePaths.clear()
    }

  fun deleteFile(filePath: String) {
    val _filePath = filePath.replace("file://", "")
    val file = File(_filePath)

    if (file.exists()) {
      if (file.delete()) {
        println("File deleted successfully.")
      } else {
        println("File couldn't be deleted.")
      }
    } else {
      println("File not found.")
    }
  }
}
