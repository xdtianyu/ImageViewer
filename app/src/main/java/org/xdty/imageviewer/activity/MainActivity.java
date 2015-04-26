package org.xdty.imageviewer.activity;

import android.app.Activity;
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
import java.util.ArrayList;

import jcifs.smb.NtlmPasswordAuthentication;
import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;


public class MainActivity extends Activity {

    public final String TAG = "MainActivity";

    private ArrayList<SmbFile> mImageList = new ArrayList<>();

    private ImageAdapter imageAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        GridView gridView = (GridView) findViewById(R.id.gridView);
        imageAdapter = new ImageAdapter(this, mImageList);
        gridView.setAdapter(imageAdapter);

        final ImageView imageView = (ImageView)findViewById(R.id.image_viewer);

        gridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                try {
                    if (mImageList.get(position).isDirectory()) {
                        loadSambaDir(mImageList.get(position).getPath());
                    } else {
                        SmbFile file = mImageList.get(position);
                        InputStream inputStream = file.getInputStream();
                        imageView.setImageBitmap(BitmapFactory.decodeStream(inputStream));
                        imageView.setVisibility(View.VISIBLE);
                    }
                } catch (SmbException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        });

        loadSambaDir(Config.server + Config.sharedFolder);
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

    private void notifyListChanged() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                imageAdapter.notifyDataSetChanged();
            }
        });
    }


}
