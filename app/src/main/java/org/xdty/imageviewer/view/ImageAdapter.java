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
import org.xdty.imageviewer.utils.Utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;

/**
 * Created by ty on 15-4-26.
 */
public class ImageAdapter extends BaseAdapter {

    public final static String TAG = "ImageAdapter";

    private Context mContext;
    private ArrayList<SmbFile> mImageList;

    private File mCacheDir;

    private Handler handler;

    public ImageAdapter(Context c, ArrayList<SmbFile> list) {
        mContext = c;
        mImageList = list;
        mCacheDir = new File(c.getCacheDir(), Config.thumbnailDir);
        if (!mCacheDir.exists()) {
            if (!mCacheDir.mkdirs()) {
                Log.e(TAG, "create thumbnail directory failed");
            }
        }
        handler = new Handler();
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
        }

        if (viewHolder.thumbnail.getTag() != mImageList.get(position).getPath()) {
            SmbFile file = mImageList.get(position);
            try {
                if (file.isDirectory()) {
                    viewHolder.thumbnail.setImageResource(R.mipmap.folder);
                } else {
                    viewHolder.thumbnail.setImageResource(R.mipmap.picture);

                    updateThumbnail(viewHolder.thumbnail, position);
                }
                if (file.canRead() && file.canWrite()) {
                    viewHolder.lock.setVisibility(View.GONE);
                }
                viewHolder.title.setText(file.getName());
                viewHolder.thumbnail.setTag(file.getName());
            } catch (SmbException e) {
                e.printStackTrace();
            }
        }
        return convertView;
    }

    // TODO: generate samba file md5 and thumbnail
    private void updateThumbnail(final ImageView imageView, final int position) {

        new Thread(new Runnable() {
            @Override
            public void run() {

                // get file's md5 and check if thumbnail exist
                String md5 = Utils.md5(mImageList.get(position));
                File f = new File(mCacheDir, md5);
                try {
                    if (f.exists()) {
                        final Bitmap bitmap = BitmapFactory.decodeStream(new FileInputStream(f));
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                imageView.setImageBitmap(bitmap);
                            }
                        });

                    } else {
                        Bitmap tmpBitmap = BitmapFactory.decodeStream(mImageList.get(position).getInputStream());
                        final Bitmap bitmap = ThumbnailUtils.extractThumbnail(tmpBitmap, imageView.getWidth(), imageView.getHeight());
                        if (f.createNewFile()) {
                            FileOutputStream out = new FileOutputStream(f);
                            bitmap.compress(CompressFormat.JPEG, 90, out);
                            out.flush();
                            out.close();
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    imageView.setImageBitmap(bitmap);
                                }
                            });
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                // generate thumbnail and save to cache
            }
        }).start();
    }

    private class ViewHolder {
        ImageView thumbnail;
        TextView title;
        ImageView lock;
    }
}