package org.xdty.imageviewer.utils;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.util.TypedValue;

import org.xdty.imageviewer.model.ImageFile;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Created by ty on 15-4-26.
 */
public class Utils {

    public static boolean isImage(String name) {
        name = name.toLowerCase();
        if (name.endsWith(".png") ||
                name.endsWith(".jpg") ||
                name.endsWith(".bmp") ||
                name.endsWith(".gif")) {
            return true;
        } else {
            return false;
        }
    }

    public static Bitmap RotateBitmap(Bitmap source, float angle) {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }

    public static int dpToPx(Resources res, int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, res.getDisplayMetrics());
    }

    public static String md5(String s) {
        try {
            // Create MD5 Hash
            MessageDigest digest = java.security.MessageDigest.getInstance("MD5");
            digest.update(s.getBytes());
            byte messageDigest[] = digest.digest();

            // Create Hex String
            StringBuilder hexString = new StringBuilder();
            for (byte aMessageDigest : messageDigest)
                hexString.append(Integer.toHexString(0xFF & aMessageDigest));
            return hexString.toString();

        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return "";
    }

    public static String md5(ImageFile imageFile) {
        return md5(imageFile.getPath() + imageFile.getLastModified() + imageFile.getContentLength());
    }

    public static String fillString(int count,char c) {
        StringBuilder sb = new StringBuilder( count );
        for( int i=0; i<count; i++ ) {
            sb.append( c );
        }
        return sb.toString();
    }
}
