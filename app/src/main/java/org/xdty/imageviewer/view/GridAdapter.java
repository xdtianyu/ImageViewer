package org.xdty.imageviewer.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.media.ThumbnailUtils;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import org.xdty.imageviewer.R;
import org.xdty.imageviewer.model.Config;
import org.xdty.imageviewer.model.ImageFile;
import org.xdty.imageviewer.utils.Utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantLock;

import jcifs.smb.SmbException;

/**
 * Created by ty on 15-4-26.
 */
public class GridAdapter extends BaseAdapter {

    public final static String TAG = "GridAdapter";

    private final ReentrantLock thumbnailLock = new ReentrantLock(true);
    private final ReentrantLock loadingLock = new ReentrantLock(true);

    private Context mContext;
    private ArrayList<ImageFile> mImageList;

    private ArrayList<String> mThumbnailList;

    private File mCacheDir;

    private Handler handler;

    private Bitmap pictureBitmap;
    private Bitmap folderBitmap;

    public GridAdapter(Context c, ArrayList<ImageFile> list) {
        mContext = c;
        mImageList = list;
        mCacheDir = new File(c.getCacheDir(), Config.thumbnailDir);
        if (!mCacheDir.exists()) {
            if (!mCacheDir.mkdirs()) {
                Log.e(TAG, "create thumbnail directory failed");
            }
        }
        handler = new Handler();
        mThumbnailList = new ArrayList<>();

        pictureBitmap = BitmapFactory.decodeResource(mContext.getResources(), R.mipmap.picture);
        folderBitmap = BitmapFactory.decodeResource(mContext.getResources(), R.mipmap.folder);
    }

    @Override
    public int getCount() {
        return mImageList.size();
    }

    @Override
    public Object getItem(int position) {
        return null;
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        ViewHolder viewHolder;

        if (convertView == null) {
            LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(R.layout.grid_item, parent, false);
            viewHolder = new ViewHolder();
            viewHolder.thumbnail = (ImageView) convertView.findViewById(R.id.thumbnail);
            viewHolder.lock = (ImageView) convertView.findViewById(R.id.lock);
            viewHolder.title = (TextView) convertView.findViewById(R.id.title);
            convertView.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) convertView.getTag();
            String name = (String) viewHolder.thumbnail.getTag();
            mThumbnailList.remove(name);
        }

        if (mImageList.size() > position && mImageList.get(position) != null) {
            try {
                ImageFile file = mImageList.get(position);
                viewHolder.title.setText(file.getName());
                viewHolder.thumbnail.setTag(file.getName());

//                BitmapDrawable bitmapDrawable = (BitmapDrawable) viewHolder.thumbnail.getDrawable();
//                if (bitmapDrawable != null && bitmapDrawable.getBitmap() != pictureBitmap && bitmapDrawable.getBitmap() != folderBitmap) {
//                    bitmapDrawable.getBitmap().recycle();
//                }

                mThumbnailList.add(file.getName());
                updateThumbnail(viewHolder.thumbnail, viewHolder.lock, position);
            } catch (IndexOutOfBoundsException e) {
                e.printStackTrace();
            }
        }

        return convertView;
    }

    // generate samba file md5 and thumbnail
    private void updateThumbnail(final ImageView imageView, final ImageView lock, final int position) {

        new Thread(new Runnable() {
            @Override
            public void run() {

                if (mImageList.size() <= position) {
                    return;
                }

                ImageFile imageFile = mImageList.get(position);

                final boolean isDirectory = imageFile.isDirectory();
                boolean isLocked = true;

                try {
                    if (imageFile.canRead() && imageFile.canWrite()) {
                        isLocked = false;
                    }
                } catch (SmbException e) {
                    e.printStackTrace();
                }

                final int visibility = isLocked?View.VISIBLE:View.GONE;
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (isDirectory) {
                            imageView.setImageBitmap(folderBitmap);
                        } else {
                            imageView.setImageBitmap(pictureBitmap);
                        }
                        lock.setVisibility(visibility);
                    }
                });

                // get file's md5 and check if thumbnail exist
                String md5 = Utils.md5(imageFile);
                File f = new File(mCacheDir, md5);

                if (f.exists()) {
                    thumbnailLock.lock();

                    // check if is scroll out
                    if (mImageList.size() > position && !mThumbnailList.contains(mImageList.get(position).getName())) {
                        thumbnailLock.unlock();
                        return;
                    }

                    // set thumbnail
                    try {
                        final Bitmap bitmap;
                        bitmap = BitmapFactory.decodeStream(new FileInputStream(f));
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (mImageList.size() > position && mImageList.get(position).getName().equals(imageView.getTag())) {
//                                    BitmapDrawable bitmapDrawable = (BitmapDrawable) imageView.getDrawable();
//
//                                    if (bitmapDrawable.getBitmap() != pictureBitmap && bitmapDrawable.getBitmap() != folderBitmap) {
//                                        bitmapDrawable.getBitmap().recycle();
//                                    }
                                    imageView.setImageBitmap(bitmap);
                                }
                            }
                        });
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } finally {
                        thumbnailLock.unlock();
                    }
                } else {
                    loadingLock.lock();

                    // check if is scroll out
                    if (mImageList.size() > position && !mThumbnailList.contains(mImageList.get(position).getName())) {
                        loadingLock.unlock();
                        return;
                    }

                    // generate folder thumbnail
                    if (isDirectory) {
                        try {
                            for (ImageFile file : imageFile.listFiles()) {
                                if (file.isImage()) {
                                    imageFile = file;
                                    break;
                                }
                            }
                        } catch (SmbException e) {
                            e.printStackTrace();
                            loadingLock.unlock();
                        }
                    }
                    // no image found, just return
                    if (isDirectory && imageFile.isDirectory()) {
                        loadingLock.unlock();
                        return;
                    }

                    // generate and set thumbnail
                    try {
                        Bitmap tmpBitmap = BitmapFactory.decodeStream(imageFile.getInputStream());
                        if (tmpBitmap != null) {
                            final Bitmap bitmap = ThumbnailUtils.extractThumbnail(tmpBitmap, imageView.getWidth(), imageView.getHeight());
//                            tmpBitmap.recycle();
                            if (f.createNewFile()) {
                                // save thumbnail to cache
                                FileOutputStream out = new FileOutputStream(f);
                                bitmap.compress(CompressFormat.JPEG, 90, out);
                                out.flush();
                                out.close();
                                handler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (mImageList.size() > position && mImageList.get(position).getName().equals(imageView.getTag())) {
//                                            BitmapDrawable bitmapDrawable = (BitmapDrawable) imageView.getDrawable();
//                                            if (bitmapDrawable.getBitmap() != pictureBitmap && bitmapDrawable.getBitmap() != folderBitmap) {
//                                                bitmapDrawable.getBitmap().recycle();
//                                            }
                                            imageView.setImageBitmap(bitmap);
                                        }
                                    }
                                });
                            }
                        }
                    } catch (IllegalArgumentException | IOException | IndexOutOfBoundsException e) {
                        e.printStackTrace();
                    } finally {
                        loadingLock.unlock();
                    }
                }
            }
        }).start();
    }

    public void clearThumbnailList() {
        mThumbnailList.clear();
    }

    private class ViewHolder {
        ImageView thumbnail;
        TextView title;
        ImageView lock;
    }
}