package org.xdty.imageviewer.view;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import org.xdty.imageviewer.R;

import java.util.ArrayList;

import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;

/**
 * Created by ty on 15-4-26.
 */
public class ImageAdapter extends BaseAdapter {

    private Context mContext;
    private ArrayList<SmbFile> mImageList;

    public ImageAdapter(Context c, ArrayList<SmbFile> list) {
        mContext = c;
        mImageList = list;
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

        if (convertView==null) {
            LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(R.layout.grid_item, null);
        }

        if (mImageList.size()>position && convertView.getTag()!=mImageList.get(position).getPath()) {
            ImageView imageView = (ImageView)convertView.findViewById(R.id.image);
            ImageView lockView = (ImageView)convertView.findViewById(R.id.lock);
            TextView textView = (TextView)convertView.findViewById(R.id.title);

            SmbFile file = mImageList.get(position);
            try {
                if (file.isDirectory()) {
                    imageView.setImageResource(R.mipmap.folder);
                } else {
                    imageView.setImageResource(R.mipmap.file);
                }
                if (file.canRead() && file.canWrite()) {
                    lockView.setVisibility(View.GONE);
                }
                textView.setText(file.getName());
                convertView.setTag(file.getName());
            } catch (SmbException e) {
                e.printStackTrace();
            }
        }
        return convertView;
    }
}