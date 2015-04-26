package org.xdty.imageviewer.activity;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.ImageView;

import org.xdty.imageviewer.R;
import org.xdty.imageviewer.model.Config;
import org.xdty.imageviewer.view.ImageAdapter;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.util.ArrayDeque;
import java.util.ArrayList;

import jcifs.smb.NtlmPasswordAuthentication;
import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;


public class MainActivity extends Activity {

    public final String TAG = "MainActivity";

    private ArrayList<SmbFile> mImageList = new ArrayList<>();

    private ImageAdapter imageAdapter;

    private ArrayDeque<String> mPathStack = new ArrayDeque<>();
    private String mCurrentPath = Config.server + Config.sharedFolder;

    private ImageView imageViewer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        GridView gridView = (GridView) findViewById(R.id.gridView);
        imageAdapter = new ImageAdapter(this, mImageList);
        gridView.setAdapter(imageAdapter);

       imageViewer = (ImageView)findViewById(R.id.image_viewer);

        gridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                try {
                    if (mImageList.get(position).isDirectory()) {
                        mPathStack.push(mCurrentPath);
                        mCurrentPath = mImageList.get(position).getPath();
                        updateFileGrid();
                    } else {
                        loadSambaImage(mImageList.get(position));
                    }
                } catch (SmbException e) {
                    e.printStackTrace();
                }

            }
        });

        updateFileGrid();
    }

    @Override
    public void onBackPressed() {
        Log.d(TAG, "onBackPressed");
        if (imageViewer.getVisibility()==View.VISIBLE) {
            imageViewer.setVisibility(View.GONE);
        } else if (mPathStack.size()>0) {
            mCurrentPath = mPathStack.pop();
            updateFileGrid();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void updateFileGrid() {
        loadSambaDir(mCurrentPath);
    }

    private void loadSambaDir(final String path) {

        mImageList.clear();
        new Thread(new Runnable() {
            @Override
            public void run() {
                NtlmPasswordAuthentication auth = new NtlmPasswordAuthentication("", Config.user, Config.password);
                try {
                    SmbFile file = new SmbFile(path, auth);
                    SmbFile[] lists = file.listFiles();
                    for (SmbFile s:lists) {
                        Log.d(TAG, s.getName());
                        mImageList.add(s);
                    }
                    notifyListChanged();
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                } catch (SmbException e) {
                    e.printStackTrace();
                }
            }
        }).start();

    }

    private void loadSambaImage(final SmbFile file) {

        new Thread(new Runnable() {
            @Override
            public void run() {
                InputStream inputStream = null;
                try {
                    inputStream = file.getInputStream();
                    final Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            imageViewer.setImageBitmap(bitmap);
                            imageViewer.setVisibility(View.VISIBLE);
                        }
                    });
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void notifyListChanged() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                imageAdapter.notifyDataSetChanged();
            }
        });
    }


}
