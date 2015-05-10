package org.xdty.imageviewer.model;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.util.ArrayList;

import jcifs.smb.NtlmPasswordAuthentication;
import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;

/**
 * Created by ty on 15-5-2.
 */
public class ImageFile {
    private SmbFile smbFile;
    private File localFile;

    public ImageFile(String path) throws MalformedURLException {
        new ImageFile(path, null);
    }

    public ImageFile(String path, NtlmPasswordAuthentication auth) throws MalformedURLException {

        if (path.startsWith(Config.SAMBA_PREFIX) && auth != null) {
            this.smbFile = new SmbFile(path, auth);
        } else {
            this.localFile = new File(path);
        }
    }

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

    public boolean isHiding() {
        boolean result = false;
        if (smbFile != null) {
            result = smbFile.getName().toLowerCase().startsWith(".");
        } else if (localFile != null) {
            result = localFile.getName().toLowerCase().startsWith(".");
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

    public boolean exists() throws SmbException {
        if (smbFile != null) {
            return smbFile.exists();
        } else {
            return localFile != null && localFile.exists();
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

    public boolean isGif() {
        boolean result = false;
        if (smbFile != null) {
            result = smbFile.getName().toLowerCase().endsWith(".gif");
        } else if (localFile != null) {
            result = localFile.getName().toLowerCase().endsWith(".gif");
        }
        return result;
    }

    public boolean isFile() throws SmbException {
        boolean result = false;
        if (smbFile != null) {
            result = smbFile.isFile();
        } else if (localFile != null) {
            result = localFile.isFile();
        }
        return result;
    }

    public ImageFile[] listFiles() throws SmbException {
        ArrayList<ImageFile> list = new ArrayList<>();
        if (smbFile != null) {
            SmbFile[] files = smbFile.listFiles();
            for (SmbFile file : files) {
                list.add(new ImageFile(file));
            }
        } else if (localFile != null) {
            File[] files = localFile.listFiles();
            for (File file : files) {
                list.add(new ImageFile(file));
            }
        }
        return list.toArray(new ImageFile[list.size()]);
    }

    public boolean hasImage() throws SmbException {
        return hasImage(2);
    }

    private boolean hasImage(int step) throws SmbException {
        boolean result = false;
        if (isDirectory() && step>0) {
            ImageFile[] files = listFiles();
            if (files.length > 0) {
                // TODO: optimize algorithms
                for (ImageFile f : files) {
                    if (f.isImage() || f.isDirectory() && f.hasImage(step-1)) {
                        result = true;
                        break;
                    }
                }
            }
        }

        if (step==0) {
            result = true;
        }

        return result;
    }

    public boolean isImage() {
        String name = getName().toLowerCase();
        if (name.endsWith(".png") ||
                name.endsWith(".jpg") ||
                name.endsWith(".bmp") ||
                name.endsWith(".gif")) {
            return true;
        } else {
            return false;
        }
    }

    public boolean isSamba() {
        return smbFile!=null;
    }
}
