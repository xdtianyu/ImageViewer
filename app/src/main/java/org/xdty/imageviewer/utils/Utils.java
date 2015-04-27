package org.xdty.imageviewer.utils;

import android.graphics.Bitmap;
import android.graphics.Matrix;

/**
 * Created by ty on 15-4-26.
 */
public class Utils {

    public static boolean isImage(String name) {
        name = name.toLowerCase();
        if (name.endsWith(".png")||
                name.endsWith(".jpg")||
                name.endsWith(".bmp")||
                name.endsWith(".gif")) {
            return true;
        } else {
            return false;
        }
    }

    public static Bitmap RotateBitmap(Bitmap source, float angle)
    {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }
}
