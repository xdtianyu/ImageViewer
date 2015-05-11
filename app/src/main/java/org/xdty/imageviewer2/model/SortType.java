package org.xdty.imageviewer2.model;

/**
 * Created by ty on 15-5-10.
 */
public enum SortType {
    FILE_NAME,
    LAST_MODIFIED,
    CONTENT_LENGTH;

    public static SortType build(String s) {
        SortType type = FILE_NAME;
        switch (s) {
            case "0":
                type = FILE_NAME;
                break;
            case "1":
                type = LAST_MODIFIED;
                break;
            case "2":
                type = CONTENT_LENGTH;
                break;
        }
        return type;
    }
}
