package com.reactnativecompressor.Image;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.util.Base64;

import com.facebook.react.bridge.ReactApplicationContext;
import com.reactnativecompressor.Image.utils.ImageCompressorOptions;
import com.reactnativecompressor.Image.utils.ImageSize;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import static com.reactnativecompressor.Utils.Utils.generateCacheFilePath;


public class ImageCompressor {
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
      String filePath=value;
    if(value.indexOf("file:/")>-1)
    {
      filePath=value.substring( value.indexOf( ':' ) + 1 );
    }
      Bitmap bitmap = BitmapFactory.decodeFile(filePath);
        return bitmap;
    }



  public static String encodeImage(ByteArrayOutputStream imageDataByteArrayOutputStream, Boolean isBase64, Bitmap bitmapImage,String outputExtension, ReactApplicationContext reactContext) {
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
        return "file:/"+outputUri;
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
}
