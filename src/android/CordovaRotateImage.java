package com.JointGreens;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.apache.cordova.CallbackContext;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.os.Handler;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.text.SimpleDateFormat;
import java.util.Date;



/**
 * This class echoes a string called from JavaScript.
 */
public class CordovaRotateImage extends CordovaPlugin {
    private String imageUriString;
    private Uri imageUri;
    private Bitmap fixedBitmap;
    private PluginResult result;
    private String folderName;
    private String fileName;
    private static final String TIME_FORMAT = "yyyyMMdd_HHmmss";

    @Override
    public boolean execute(String action, JSONArray args, final CallbackContext callbackContext) throws JSONException {
        Context context = this.cordova.getActivity().getApplicationContext();
        Log.d("rotateImage action: ", action);
        folderName = "JointGreensImages";
        fileName = null;
        if(action.equals("correctOrientation")) {
            JSONObject jsonObject = args.getJSONObject(0);
            imageUriString = "file://" + jsonObject.getString("imageUri");
            //fileName = imageUriString;
            imageUri = Uri.parse(imageUriString);
            Log.d("rotateImage uri:", imageUriString);

            try {
                fixedBitmap = handleSamplingAndRotationBitmap(context, imageUri);
                Uri rotatedFile = saveFile(fixedBitmap);
               result = new PluginResult(PluginResult.Status.OK, rotatedFile.toString());
               callbackContext.sendPluginResult(result);
            } catch (IOException e) {
                Log.d("image err", e.toString());
                result = new PluginResult(PluginResult.Status.ERROR, "Rotate Image Error!" + e.toString());
                callbackContext.sendPluginResult(result);
            }
        } else {
            result = new PluginResult(PluginResult.Status.ERROR, "incorrect action");
            callbackContext.sendPluginResult(result);
        }
        return true;
    }

    public static Bitmap handleSamplingAndRotationBitmap(Context context, Uri selectedImage)
            throws IOException {
        int MAX_HEIGHT = 1024;
        int MAX_WIDTH = 1024;

        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        InputStream imageStream = context.getContentResolver().openInputStream(selectedImage);
        BitmapFactory.decodeStream(imageStream, null, options);
        imageStream.close();

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, MAX_WIDTH, MAX_HEIGHT);

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        imageStream = context.getContentResolver().openInputStream(selectedImage);
        Bitmap img = BitmapFactory.decodeStream(imageStream, null, options);

        img = rotateImageIfRequired(context, img, selectedImage);
        return img;
    }

    private static int calculateInSampleSize(BitmapFactory.Options options,
                                             int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {

            // Calculate ratios of height and width to requested height and width
            final int heightRatio = Math.round((float) height / (float) reqHeight);
            final int widthRatio = Math.round((float) width / (float) reqWidth);

            // Choose the smallest ratio as inSampleSize value, this will guarantee a final image
            // with both dimensions larger than or equal to the requested height and width.
            inSampleSize = heightRatio < widthRatio ? heightRatio : widthRatio;

            // This offers some additional logic in case the image has a strange
            // aspect ratio. For example, a panorama may have a much larger
            // width than height. In these cases the total pixels might still
            // end up being too large to fit comfortably in memory, so we should
            // be more aggressive with sample down the image (=larger inSampleSize).

            final float totalPixels = width * height;

            // Anything more than 2x the requested pixels we'll sample down further
            final float totalReqPixelsCap = reqWidth * reqHeight * 2;

            while (totalPixels / (inSampleSize * inSampleSize) > totalReqPixelsCap) {
                inSampleSize++;
            }
        }
        return inSampleSize;
    }

    private static Bitmap rotateImageIfRequired(Context context, Bitmap img, Uri selectedImage) throws IOException {

        InputStream input = context.getContentResolver().openInputStream(selectedImage);
        ExifInterface ei;
        if (Build.VERSION.SDK_INT > 23)
            ei = new ExifInterface(input);
        else
            ei = new ExifInterface(selectedImage.getPath());

        int orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        Log.d("rotateImage or: ", String.valueOf(orientation));
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                Log.d("rotateImage", "rotate 90");
                return rotateImage(img, 90);
            case ExifInterface.ORIENTATION_ROTATE_180:
                Log.d("rotateImage", "rotate 180");
                return rotateImage(img, 180);
            case ExifInterface.ORIENTATION_ROTATE_270:
                Log.d("rotateImage", "rotate 270");
                return rotateImage(img, 270);
            default:
                Log.d("rotateImage", "rotate none");
                return img;
        }
    }

    private static Bitmap rotateImage(Bitmap img, int degree) {
        Matrix matrix = new Matrix();
        matrix.postRotate(degree);
        Bitmap rotatedImg = Bitmap.createBitmap(img, 0, 0, img.getWidth(), img.getHeight(), matrix, true);
        img.recycle();
        return rotatedImg;
    }

    private Uri saveFile(Bitmap bitmap) {
        File folder = new File(this.getTempDirectoryPath());
        boolean success = true;
        if (!folder.exists()) {
            success = folder.mkdir();
        }

        if (success) {
            String timeStamp = new SimpleDateFormat(TIME_FORMAT).format(new Date());
            Log.d("File timeStamp: ", timeStamp);
            fileName = "IMG_" + timeStamp + ".jpg";
            Log.d("File Name: ", fileName);
            File file = new File(folder, fileName);
            if (file.exists()) file.delete();
            try {
                FileOutputStream out = new FileOutputStream(file);
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
                out.flush();
                out.close();
            } catch (Exception e) {
                Log.e("Protonet", e.toString());
            }
            return Uri.fromFile(file);
        }
        return null;
    }

    private String getTempDirectoryPath() {
        File cache = null;

        // SD Card Mounted
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            cache = cordova.getActivity().getExternalCacheDir();
            // cache = new File(Environment.getExternalStorageDirectory().getAbsolutePath() +
            //         "/Android/data/" + cordova.getActivity().getPackageName() + "/cache/");
        } else {
            // Use internal storage
            cache = cordova.getActivity().getCacheDir();
        }

        // Create the cache directory if it doesn't exist
        cache.mkdirs();
        return cache.getAbsolutePath();
    }
}