package org.xdty.imageviewer.utils;

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
}
