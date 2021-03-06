package org.xdty.imageviewer2.view.adapter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.Handler;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.Adapter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.daimajia.androidanimations.library.BaseViewAnimator;
import com.daimajia.androidanimations.library.Techniques;
import com.daimajia.androidanimations.library.YoYo;

import org.xdty.imageviewer2.R;
import org.xdty.imageviewer2.model.Config;
import org.xdty.imageviewer2.model.ImageFile;
import org.xdty.imageviewer2.utils.Utils;
import org.xdty.imageviewer2.view.adapter.RecyclerViewAdapter.ViewHolder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantLock;

import jcifs.smb.SmbException;

/**
 * Created by ty on 15-5-13.
 */
public class RecyclerViewAdapter extends Adapter<ViewHolder> {

    public final static String TAG = "RecyclerViewAdapter";

    private final ReentrantLock thumbnailLock = new ReentrantLock(true);
    private final ReentrantLock loadingLock = new ReentrantLock(true);

    private Context mContext;
    private ArrayList<ImageFile> mImageList;

    private ArrayList<String> mThumbnailList;

    private File mCacheDir;

    private Handler handler;

    private Bitmap pictureBitmap;
    private Bitmap folderBitmap;

    private OnItemClickListener mListener;
    private String animationEffect;
    private int animationDuration = 450;

    public RecyclerViewAdapter(Context c, ArrayList<ImageFile> list, OnItemClickListener listener) {
        mContext = c;
        mImageList = list;
        mListener = listener;
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
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {

        LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.grid_item, parent, false);

        return new ViewHolder(view, mListener);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {

        mListener.onBindViewHolder(position);

        try {

            holder.position = position;

            ImageFile file = mImageList.get(position);
            String name = (String) holder.thumbnail.getTag();
            if (name == null || !name.equals(file.getName())) {
                mThumbnailList.remove(name);

                BitmapDrawable bitmapDrawable = (BitmapDrawable) holder.thumbnail.getDrawable();

                if (bitmapDrawable != null) {
                    Bitmap bitmap = bitmapDrawable.getBitmap();
                    if (bitmap != null && bitmap != folderBitmap && bitmap != pictureBitmap) {
                        bitmap.recycle();
                    }
                }

                holder.thumbnail.setImageBitmap(null);
                holder.title.setText(file.getName());
                holder.thumbnail.setTag(file.getName());

                mThumbnailList.add(file.getName());
                updateThumbnail(holder.thumbnail, holder.lock, position);
            }
        } catch (IndexOutOfBoundsException e) {
            e.printStackTrace();
        }

        // add grid animation
        if (animationEffect != null && !animationEffect.equals("Standard")) {
            BaseViewAnimator animator = Techniques.valueOf(animationEffect).getAnimator();
            try {
                YoYo.with(animator).duration(animationDuration).playOn(holder.itemView);
            } catch (NullPointerException e) {
                e.printStackTrace();
            }
        }

    }

    @Override
    public int getItemCount() {
        return mImageList.size();
    }

    // generate samba file md5 and thumbnail
    private void updateThumbnail(final ImageView imageView, final ImageView lockView,
            final int position) {

        new Thread(new Runnable() {
            @Override
            public void run() {

                if (mImageList.size() <= position) {
                    return;
                }

                ImageFile imageFile = mImageList.get(position);

                final boolean isDirectory = imageFile.isDirectory();
                boolean isWritable = false;

                try {
                    if (imageFile.canRead() && imageFile.canWrite()) {
                        isWritable = true;
                    }
                } catch (SmbException e) {
                    e.printStackTrace();
                }

                final int visibility = isWritable ? View.GONE : View.VISIBLE;
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (isDirectory) {
                            imageView.setImageBitmap(folderBitmap);
                        } else {
                            imageView.setImageBitmap(pictureBitmap);
                        }
                        lockView.setVisibility(visibility);
                    }
                });

                final String fileName = imageFile.getName();

                // get file's md5 and check if thumbnail exist
                String md5 = Utils.md5(imageFile);
                File f = new File(mCacheDir, md5);

                if (f.exists()) {
                    thumbnailLock.lock();

                    // check if is scroll out
                    if (mImageList.size() > position && !mThumbnailList.contains(fileName)) {
                        if (thumbnailLock.isHeldByCurrentThread()) {
                            thumbnailLock.unlock();
                        } else {
                            Log.e(TAG, "thumbnailLock is not held by current thread");
                        }

                        return;
                    }

                    // set thumbnail
                    try {
                        final Bitmap bitmap;
                        bitmap = BitmapFactory.decodeStream(new FileInputStream(f));
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (mImageList.size() > position &&
                                        mImageList.get(position).getName().equals(fileName) &&
                                        imageView.getTag().equals(fileName)) {
                                    imageView.setImageBitmap(bitmap);
                                }
                            }
                        });
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } finally {
                        if (thumbnailLock.isHeldByCurrentThread()) {
                            thumbnailLock.unlock();
                        } else {
                            Log.e(TAG, "thumbnailLock unlock error");
                        }

                    }
                } else {
                    loadingLock.lock();
                    // check if is scroll out
                    if (mImageList.size() > position && !mThumbnailList.contains(fileName)) {
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
                            return;
                        }
                    }
                    // no image found, just return
                    if (isDirectory && imageFile.isDirectory()) {
                        loadingLock.unlock();
                        return;
                    }

                    // generate and set thumbnail
                    try {
                        final Bitmap bitmap = Utils.decodeSampledBitmapFromStream(imageFile,
                                imageView.getWidth(), imageView.getHeight());
                        if (bitmap != null) {
                            if (f.createNewFile()) {
                                // save thumbnail to cache
                                FileOutputStream out = new FileOutputStream(f);
                                bitmap.compress(CompressFormat.JPEG, 90, out);
                                out.flush();
                                out.close();
                                handler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (mImageList.size() > position &&
                                                mImageList.get(position).getName().equals(
                                                        fileName) &&
                                                imageView.getTag().equals(fileName)) {
                                            imageView.setImageBitmap(bitmap);
                                        }
                                    }
                                });
                            }
                        } else {
                            Log.e(TAG, "generate thumbnail failed");
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

    public void setAnimator(String effect) {
        this.animationEffect = effect;
    }

    public void setAnimationDuration(int duration) {
        this.animationDuration = duration;
    }

    public interface OnItemClickListener {
        void onItemClicked(View view, int position);

        void onBindViewHolder(int position);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        int position = 0;

        TextView title;
        ImageView lock;
        ImageView thumbnail;
        OnItemClickListener listener;

        ViewHolder(View view, OnItemClickListener listener) {
            super(view);
            thumbnail = (ImageView) view.findViewById(R.id.thumbnail);
            lock = (ImageView) view.findViewById(R.id.lock);
            title = (TextView) view.findViewById(R.id.title);

            this.listener = listener;

            thumbnail.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            if (v instanceof ImageView) {
                // TODO: return file uri here
                listener.onItemClicked(v, position);
            }
        }
    }
}
