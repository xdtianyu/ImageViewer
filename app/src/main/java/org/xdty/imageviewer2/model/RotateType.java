package org.xdty.imageviewer2.model;

/**
 * Created by ty on 15-4-30.
 */
public enum RotateType {
    ORIGINAL,
    ROTATE_IMAGE_FIT_SCREEN,
    ROTATE_SCREEN_FIT_IMAGE;

    public static RotateType build(String s) {
        RotateType type = ORIGINAL;
        switch (s) {
            case "0":
                type = ORIGINAL;
                break;
            case "1":
                type = ROTATE_IMAGE_FIT_SCREEN;
                break;
            case "2":
                type = ROTATE_SCREEN_FIT_IMAGE;
                break;
        }
        return type;
    }
}
