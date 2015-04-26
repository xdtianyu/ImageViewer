package org.xdty.imageviewer.utils;

import java.util.Comparator;

import jcifs.smb.SmbFile;

/**
 * Created by ty on 15-4-26.
 */
public class SmbFileHelper {

    public static final Comparator<SmbFile> NAME_COMPARATOR = new Comparator<SmbFile>() {
        @Override
        public int compare(SmbFile lhs, SmbFile rhs) {
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
