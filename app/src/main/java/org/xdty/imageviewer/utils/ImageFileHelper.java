package org.xdty.imageviewer.utils;

import org.xdty.imageviewer.model.ImageFile;

import java.util.Comparator;

/**
 * Created by ty on 15-4-26.
 */
public class ImageFileHelper {

    public static final Comparator<ImageFile> NAME_COMPARATOR = new Comparator<ImageFile>() {
        @Override
        public int compare(ImageFile lhs, ImageFile rhs) {
            return compare(lhs.getName(), rhs.getName());
        }

        private int compare(String lhs, String rhs) {
            if (lhs.equals(rhs)) {
                return 0;
            }

            if (lhs.endsWith("/")) {
                lhs = lhs.substring(0, lhs.length() - 1);
            }

            if (rhs.endsWith("/")) {
                rhs = rhs.substring(0, rhs.length()-1);
            }

            if (lhs.startsWith(rhs)) {
                return 1;
            } else if (rhs.startsWith(lhs)) {
                return -1;
            } else {
                // Fixme: parse 01-11. 1-10
                return lhs.compareTo(rhs);
            }
        }
    };

}
