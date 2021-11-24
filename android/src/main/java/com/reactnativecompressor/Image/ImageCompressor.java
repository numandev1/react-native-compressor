package com.reactnativecompressor.Image;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.media.ExifInterface;
import android.net.Uri;
import android.util.Base64;

import com.facebook.react.bridge.ReactApplicationContext;
import com.reactnativecompressor.Image.utils.ImageCompressorOptions;
import com.reactnativecompressor.Image.utils.ImageSize;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;

import static com.reactnativecompressor.Utils.Utils.generateCacheFilePath;


public class ImageCompressor {

  public static String getRNFileUrl(String filePath) {
    File returnAbleFile= new File(filePath);
    try {
      filePath = returnAbleFile.toURL().toString();
    } catch (MalformedURLException e) {
      e.printStackTrace();
    }
    return filePath;
  }

  public static ImageSize findActualSize(Bitmap image, int maxWidth, int maxHeight) {
        final float width = (float) image.getWidth();
        final float height = (float) image.getHeight();

        if (width > height) {
            final int newHeight = Math.round(height / (width / maxWidth));
            final float scale = newHeight / height;

            return new ImageSize(maxWidth, newHeight, scale);
        }

        final int newWidth = Math.round(width / (height / maxHeight));
        final float scale = newWidth / width;

        return new ImageSize(newWidth, maxHeight, scale);
    }

    public static Bitmap decodeImage(String value) {
        final byte[] data = Base64.decode(value, Base64.DEFAULT);
        return BitmapFactory.decodeByteArray(data, 0, data.length);
    }

    public static Bitmap loadImage(String value) {
      Uri uri= Uri.parse(value);
      String filePath = uri.getPath();
      Bitmap bitmap = BitmapFactory.decodeFile(filePath);
        return bitmap;
    }



  public static String encodeImage(ByteArrayOutputStream imageDataByteArrayOutputStream, Boolean isBase64,String outputExtension, ReactApplicationContext reactContext) {
    if(isBase64)
    {
      byte[] imageData=imageDataByteArrayOutputStream.toByteArray();
      return Base64.encodeToString(imageData, Base64.DEFAULT);
    }
    else
    {
      String outputUri=generateCacheFilePath(outputExtension,reactContext);
      try {
        FileOutputStream fos=new FileOutputStream(outputUri);
        imageDataByteArrayOutputStream.writeTo(fos);
        return getRNFileUrl(outputUri);
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  return  "";
    }

  public static Bitmap resize(Bitmap image, int maxWidth, int maxHeight) {
        final ImageSize size = findActualSize(image, maxWidth, maxHeight);

        final Bitmap scaledImage = Bitmap.createBitmap(size.width, size.height, image.getConfig());
        final Matrix scaleMatrix = new Matrix();
        final Canvas canvas = new Canvas(scaledImage);
        final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);

        scaleMatrix.setScale(size.scale, size.scale, 0 ,0);

        paint.setDither(true);
        paint.setAntiAlias(true);
        paint.setFilterBitmap(true);

        canvas.drawBitmap(image, scaleMatrix, paint);

        return scaledImage;
    }

  public static ByteArrayOutputStream compress(Bitmap image, ImageCompressorOptions.OutputType output, float quality) {
        final ByteArrayOutputStream stream = new ByteArrayOutputStream();
        final Bitmap.CompressFormat format = output == ImageCompressorOptions.OutputType.jpg
                ? Bitmap.CompressFormat.JPEG
                : Bitmap.CompressFormat.PNG;

        image.compress(format, Math.round(100 * quality), stream);
        return stream;
    }

  public static String manualCompressImage(String imagePath,ImageCompressorOptions options, ReactApplicationContext reactContext) {
    final Bitmap image = options.input == ImageCompressorOptions.InputType.base64
      ? ImageCompressor.decodeImage(imagePath)
      : ImageCompressor.loadImage(imagePath);

    final Bitmap resizedImage = ImageCompressor.resize(image, options.maxWidth, options.maxHeight);
    final ByteArrayOutputStream imageDataByteArrayOutputStream = ImageCompressor.compress(resizedImage, options.output, options.quality);
    Boolean isBase64=options.returnableOutputType==ImageCompressorOptions.ReturnableOutputType.base64;

    String returnableResult = ImageCompressor.encodeImage(imageDataByteArrayOutputStream,isBase64,options.output.toString(),reactContext);
    return  returnableResult;
  }


  public static String autoCompressImage(String imagePath,ImageCompressorOptions compressorOptions, ReactApplicationContext reactContext) {
    String outputExtension=compressorOptions.output.toString();
    float autoCompressMaxHeight = compressorOptions.maxHeight;
    float autoCompressMaxWidth = compressorOptions.maxWidth;
    Boolean isBase64=compressorOptions.returnableOutputType==ImageCompressorOptions.ReturnableOutputType.base64;

    Uri uri= Uri.parse(imagePath);
    imagePath = uri.getPath();
    Bitmap scaledBitmap = null;

    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inJustDecodeBounds = true;
    Bitmap bmp = BitmapFactory.decodeFile(imagePath, options);

    int actualHeight = options.outHeight;
    int actualWidth = options.outWidth;

    float imgRatio = (float) actualWidth / (float) actualHeight;
    float maxRatio = autoCompressMaxWidth / autoCompressMaxHeight;

    if (actualHeight > autoCompressMaxHeight || actualWidth > autoCompressMaxWidth) {
      if (imgRatio < maxRatio) {
        imgRatio = autoCompressMaxHeight / actualHeight;
        actualWidth = (int) (imgRatio * actualWidth);
        actualHeight = (int) autoCompressMaxHeight;
      } else if (imgRatio > maxRatio) {
        imgRatio = autoCompressMaxWidth / actualWidth;
        actualHeight = (int) (imgRatio * actualHeight);
        actualWidth = (int) autoCompressMaxWidth;
      } else {
        actualHeight = (int) autoCompressMaxHeight;
        actualWidth = (int) autoCompressMaxWidth;

      }
    }

    options.inSampleSize = calculateInSampleSize(options, actualWidth, actualHeight);
    options.inJustDecodeBounds = false;
    options.inDither = false;
    options.inPurgeable = true;
    options.inInputShareable = true;
    options.inTempStorage = new byte[16 * 1024];

    try {
      bmp = BitmapFactory.decodeFile(imagePath, options);
    } catch (OutOfMemoryError exception) {
      exception.printStackTrace();

    }
    try {
      scaledBitmap = Bitmap.createBitmap(actualWidth, actualHeight, Bitmap.Config.RGB_565);
    } catch (OutOfMemoryError exception) {
      exception.printStackTrace();
    }

    float ratioX = actualWidth / (float) options.outWidth;
    float ratioY = actualHeight / (float) options.outHeight;
    float middleX = actualWidth / 2.0f;
    float middleY = actualHeight / 2.0f;

    Matrix scaleMatrix = new Matrix();
    scaleMatrix.setScale(ratioX, ratioY, middleX, middleY);

    Canvas canvas = new Canvas(scaledBitmap);
    canvas.setMatrix(scaleMatrix);
    canvas.drawBitmap(bmp, middleX - bmp.getWidth() / 2, middleY - bmp.getHeight() / 2, new Paint(Paint.FILTER_BITMAP_FLAG));

    if(bmp!=null)
    {
      bmp.recycle();
    }

    ExifInterface exif;
    try {
      exif = new ExifInterface(imagePath);
      int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 0);
      Matrix matrix = new Matrix();
      if (orientation == 6) {
        matrix.postRotate(90);
      } else if (orientation == 3) {
        matrix.postRotate(180);
      } else if (orientation == 8) {
        matrix.postRotate(270);
      }
      scaledBitmap = Bitmap.createBitmap(scaledBitmap, 0, 0, scaledBitmap.getWidth(), scaledBitmap.getHeight(), matrix, true);
    } catch (IOException e) {
      e.printStackTrace();
    }

    final ByteArrayOutputStream imageDataByteArrayOutputStream = ImageCompressor.compress(scaledBitmap, compressorOptions.output, compressorOptions.quality);
    String returnableResult = ImageCompressor.encodeImage(imageDataByteArrayOutputStream,isBase64,compressorOptions.output.toString(),reactContext);
    return  returnableResult;
  }

  public static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
    final int height = options.outHeight;
    final int width = options.outWidth;
    int inSampleSize = 1;

    if (height > reqHeight || width > reqWidth) {
      final int heightRatio = Math.round((float) height / (float) reqHeight);
      final int widthRatio = Math.round((float) width / (float) reqWidth);
      inSampleSize = heightRatio < widthRatio ? heightRatio : widthRatio;
    }
    final float totalPixels = width * height;
    final float totalReqPixelsCap = reqWidth * reqHeight * 2;

    while (totalPixels / (inSampleSize * inSampleSize) > totalReqPixelsCap) {
      inSampleSize++;
    }

    return inSampleSize;
  }
}
