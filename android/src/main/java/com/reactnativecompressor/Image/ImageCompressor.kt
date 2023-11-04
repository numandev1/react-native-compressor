package com.reactnativecompressor.Image

import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import com.facebook.react.bridge.ReactApplicationContext
import com.reactnativecompressor.Utils.MediaCache
import com.reactnativecompressor.Utils.Utils.exifAttributes
import com.reactnativecompressor.Utils.Utils.generateCacheFilePath
import com.reactnativecompressor.Utils.Utils.slashifyFilePath
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.MalformedURLException

object ImageCompressor {
    fun getRNFileUrl(filePath: String?): String? {
        var filePath = filePath
        val returnAbleFile = File(filePath)
        try {
            filePath = returnAbleFile.toURL().toString()
        } catch (e: MalformedURLException) {
            e.printStackTrace()
        }
        return filePath
    }

    fun findActualSize(image: Bitmap, maxWidth: Int, maxHeight: Int): ImageSize {
        val width = image.width.toFloat()
        val height = image.height.toFloat()
        if (width > height) {
            val newHeight = Math.round(height / (width / maxWidth))
            val scale = newHeight / height
            return ImageSize(maxWidth, newHeight, scale)
        }
        val newWidth = Math.round(width / (height / maxHeight))
        val scale = newWidth / width
        return ImageSize(newWidth, maxHeight, scale)
    }

    fun decodeImage(value: String?): Bitmap {
        val data = Base64.decode(value, Base64.DEFAULT)
        return BitmapFactory.decodeByteArray(data, 0, data.size)
    }

    fun loadImage(value: String?): Bitmap {
        val uri = Uri.parse(value)
        val filePath = uri.path
        return BitmapFactory.decodeFile(filePath)
    }

    fun copyExifInfo(imagePath:String, outputUri:String){
      try {
        // for copy exif info
        val sourceExif = ExifInterface(imagePath)
        val compressedExif = ExifInterface(outputUri)
        for (tag in exifAttributes) {
          val compressedValue = compressedExif.getAttribute(tag)
          if(compressedValue==null)
          {
            val sourceValue = sourceExif.getAttribute(tag)
            if (sourceValue != null) {
              compressedExif.setAttribute(tag, sourceValue)
            }
          }
        }
        compressedExif.saveAttributes()
      } catch (e: Exception) {
        e.printStackTrace()
      }
    }

    fun encodeImage(imageDataByteArrayOutputStream: ByteArrayOutputStream, isBase64: Boolean, outputExtension: String?,imagePath: String?, reactContext: ReactApplicationContext?): String? {
        if (isBase64) {
            val imageData = imageDataByteArrayOutputStream.toByteArray()
            return Base64.encodeToString(imageData, Base64.DEFAULT)
        } else {
            val outputUri = generateCacheFilePath(outputExtension!!, reactContext!!)
            try {
                val fos = FileOutputStream(outputUri)
                imageDataByteArrayOutputStream.writeTo(fos)

              copyExifInfo(imagePath!!, outputUri)

                return getRNFileUrl(outputUri)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return ""
    }

    fun resize(image: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
      val size = findActualSize(image, maxWidth, maxHeight)
      val scaledImage = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
      val scaleMatrix = Matrix()
      val canvas = Canvas(scaledImage)
      val paint = Paint(Paint.FILTER_BITMAP_FLAG)
      scaleMatrix.setScale(size.scale, size.scale, 0f, 0f)
      paint.isDither = true
      paint.isAntiAlias = true
      paint.isFilterBitmap = true
      canvas.drawBitmap(image, scaleMatrix, paint)
      return scaledImage
    }

    fun compress(image: Bitmap?, output: ImageCompressorOptions.OutputType, quality: Float,disablePngTransparency:Boolean): ByteArrayOutputStream {
      var stream = ByteArrayOutputStream()
      if (output === ImageCompressorOptions.OutputType.jpg)
      {
        image!!.compress(CompressFormat.JPEG, Math.round(100 * quality), stream)
      }
      else
      {
        var bitmap = image
        if(disablePngTransparency)
        {
          image!!.compress(CompressFormat.JPEG, Math.round(100 * quality), stream)
          val byteArray: ByteArray = stream.toByteArray()
          stream=ByteArrayOutputStream()
          bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
        }
        bitmap!!.compress(CompressFormat.PNG, 100, stream)
      }
      return stream
    }

    fun manualCompressImage(imagePath: String?, options: ImageCompressorOptions, reactContext: ReactApplicationContext?): String? {
        val image = if (options.input === ImageCompressorOptions.InputType.base64) decodeImage(imagePath) else loadImage(imagePath)
        val resizedImage = resize(image, options.maxWidth, options.maxHeight)
        val imageDataByteArrayOutputStream = compress(resizedImage, options.output, options.quality,options.disablePngTransparency)
        val isBase64 = options.returnableOutputType === ImageCompressorOptions.ReturnableOutputType.base64
        return encodeImage(imageDataByteArrayOutputStream, isBase64, options.output.toString(),imagePath, reactContext)
    }

  fun isCompressedSizeLessThanActualFile(sourceFileUrl: String,compressedFileUrl: String?): Boolean {
    try {
      val sourceUri = Uri.parse(sourceFileUrl)
      val sourcePath = sourceUri.path
      val sourcefile = File(sourcePath)
      val sizeInBytesForSourceFile = sourcefile.length().toFloat()

      val compressedUri = Uri.parse(compressedFileUrl)
      val compressedPath = compressedUri.path
      val compressedfile = File(compressedPath)
      val sizeInBytesForcompressedFile = compressedfile.length().toFloat()

      if(sizeInBytesForcompressedFile<=sizeInBytesForSourceFile)
      {
        return true
      }
      return false
    } catch (exception: OutOfMemoryError) {
      exception.printStackTrace()
      return true
    }
  }

    fun autoCompressImage(imagePath: String?, compressorOptions: ImageCompressorOptions, reactContext: ReactApplicationContext?): String? {
        var imagePath = imagePath
        val autoCompressMaxHeight = compressorOptions.maxHeight.toFloat()
        val autoCompressMaxWidth = compressorOptions.maxWidth.toFloat()
        val isBase64 = compressorOptions.returnableOutputType === ImageCompressorOptions.ReturnableOutputType.base64
        val uri = Uri.parse(imagePath)
        imagePath = uri.path
        var scaledBitmap: Bitmap? = null
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        var bmp = BitmapFactory.decodeFile(imagePath, options)
        var actualHeight = options.outHeight
        var actualWidth = options.outWidth
        var imgRatio = actualWidth.toFloat() / actualHeight.toFloat()
        val maxRatio = autoCompressMaxWidth / autoCompressMaxHeight
        if (actualHeight > autoCompressMaxHeight || actualWidth > autoCompressMaxWidth) {
            if (imgRatio < maxRatio) {
                imgRatio = autoCompressMaxHeight / actualHeight
                actualWidth = (imgRatio * actualWidth).toInt()
                actualHeight = autoCompressMaxHeight.toInt()
            } else if (imgRatio > maxRatio) {
                imgRatio = autoCompressMaxWidth / actualWidth
                actualHeight = (imgRatio * actualHeight).toInt()
                actualWidth = autoCompressMaxWidth.toInt()
            } else {
                actualHeight = autoCompressMaxHeight.toInt()
                actualWidth = autoCompressMaxWidth.toInt()
            }
        }
        options.inSampleSize = calculateInSampleSize(options, actualWidth, actualHeight)
        options.inJustDecodeBounds = false
        options.inDither = false
        options.inPurgeable = true
        options.inInputShareable = true
        options.inTempStorage = ByteArray(16 * 1024)
        try {
            bmp = BitmapFactory.decodeFile(imagePath, options)
        } catch (exception: OutOfMemoryError) {
            exception.printStackTrace()
        }
        try {
            scaledBitmap = Bitmap.createBitmap(actualWidth, actualHeight, Bitmap.Config.ARGB_8888)
        } catch (exception: OutOfMemoryError) {
            exception.printStackTrace()
        }
        val ratioX = actualWidth / options.outWidth.toFloat()
        val ratioY = actualHeight / options.outHeight.toFloat()
        val middleX = actualWidth / 2.0f
        val middleY = actualHeight / 2.0f
        val scaleMatrix = Matrix()
        scaleMatrix.setScale(ratioX, ratioY, middleX, middleY)
        val canvas = Canvas(scaledBitmap!!)
        canvas.setMatrix(scaleMatrix)
        canvas.drawBitmap(bmp!!, middleX - bmp.width / 2, middleY - bmp.height / 2, Paint(Paint.FILTER_BITMAP_FLAG))
        if (bmp != null) {
            bmp.recycle()
        }
        scaledBitmap = correctImageOrientation(scaledBitmap, imagePath)
        val imageDataByteArrayOutputStream = compress(scaledBitmap, compressorOptions.output, compressorOptions.quality,compressorOptions.disablePngTransparency)
        val compressedImagePath=encodeImage(imageDataByteArrayOutputStream, isBase64, compressorOptions.output.toString(),imagePath, reactContext)
        if(isCompressedSizeLessThanActualFile(imagePath!!,compressedImagePath))
        {
        return  compressedImagePath
        }
       MediaCache.deleteFile(compressedImagePath!!)
       return slashifyFilePath(imagePath)
    }

    fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val heightRatio = Math.round(height.toFloat() / reqHeight.toFloat())
            val widthRatio = Math.round(width.toFloat() / reqWidth.toFloat())
            inSampleSize = if (heightRatio < widthRatio) heightRatio else widthRatio
        }
        val totalPixels = (width * height).toFloat()
        val totalReqPixelsCap = (reqWidth * reqHeight * 2).toFloat()
        while (totalPixels / (inSampleSize * inSampleSize) > totalReqPixelsCap) {
            inSampleSize++
        }
        return inSampleSize
    }

    fun correctImageOrientation(bitmap: Bitmap?, imagePath: String?): Bitmap? {
        return try {
            val exif = ExifInterface(imagePath!!)
            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                else -> return bitmap // No rotation needed
            }
            Bitmap.createBitmap(bitmap!!, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (e: IOException) {
            e.printStackTrace()
            bitmap // Return original bitmap if an error occurs
        }
    }
}
