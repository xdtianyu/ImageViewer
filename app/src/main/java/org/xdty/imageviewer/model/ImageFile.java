package org.xdty.imageviewer.model;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;

/**
 * Created by ty on 15-5-2.
 */
public class ImageFile {
    private SmbFile smbFile;
    private File localFile;

    public ImageFile(SmbFile smbFile) {
        this.smbFile = smbFile;
    }

    public ImageFile(File localFile) {
        this.localFile = localFile;
    }

    public boolean isDirectory() {
        boolean result = false;
        try {
            result = smbFile != null && smbFile.isDirectory() || localFile != null && localFile.isDirectory();
        } catch (SmbException e) {
            e.printStackTrace();
        }
        return result;
    }

    public String getName() {
        if (smbFile != null) {
            return smbFile.getName();
        } else if (localFile != null) {
            return localFile.getName();
        } else {
            return null;
        }
    }

    public boolean canRead() throws SmbException {
        if (smbFile != null) {
            return smbFile.canRead();
        } else {
            return localFile != null && localFile.canRead();
        }
    }

    public boolean canWrite() throws SmbException {
        if (smbFile != null) {
            return smbFile.canWrite();
        } else {
            return localFile != null && localFile.canWrite();
        }
    }

    public long getLastModified() {
        if (smbFile != null) {
            return smbFile.getLastModified();
        } else if (localFile != null) {
            return localFile.lastModified();
        } else {
            return -1;
        }
    }

    public long getContentLength() {
        if (smbFile != null) {
            return smbFile.getContentLength();
        } else if (localFile != null) {
            return localFile.length();
        } else {
            return -1;
        }
    }

    public String getPath() {
        if (smbFile != null) {
            return smbFile.getPath();
        } else if (localFile != null) {
            return localFile.getPath();
        } else {
            return null;
        }
    }

    public InputStream getInputStream() throws IOException {
        if (smbFile != null) {
            return smbFile.getInputStream();
        } else if (localFile != null) {
            return new FileInputStream(localFile);
        } else {
            return null;
        }
    }
}
