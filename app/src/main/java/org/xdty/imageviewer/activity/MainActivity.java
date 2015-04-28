package org.xdty.imageviewer.activity;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.ImageView;

import org.xdty.imageviewer.R;
import org.xdty.imageviewer.model.Config;
import org.xdty.imageviewer.utils.SmbFileHelper;
import org.xdty.imageviewer.utils.Utils;
import org.xdty.imageviewer.view.ImageAdapter;
import org.xdty.imageviewer.view.JazzyViewPager;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;

import jcifs.smb.NtlmPasswordAuthentication;
import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;
import uk.co.senab.photoview.PhotoView;

import static org.xdty.imageviewer.utils.Utils.RotateBitmap;


public class MainActivity extends Activity implements ViewPager.OnPageChangeListener {

    public final String TAG = "MainActivity";

    private ArrayList<SmbFile> mImageList = new ArrayList<>();

    private ImageAdapter imageAdapter;

    private ArrayDeque<String> mPathStack = new ArrayDeque<>();
    private String mCurrentPath = Config.server + Config.sharedFolder;

    private JazzyViewPager mViewPager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        GridView gridView = (GridView) findViewById(R.id.gridView);
        imageAdapter = new ImageAdapter(this, mImageList);
        gridView.setAdapter(imageAdapter);


        mViewPager = (JazzyViewPager)findViewById(R.id.viewpager);

        gridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                try {
                    if (mImageList.get(position).isDirectory()) {
                        mPathStack.push(mCurrentPath);
                        mCurrentPath = mImageList.get(position).getPath();
                        updateFileGrid();
                    } else {
                        // fixme: may have mem leak here.
                        mViewPager.setTransitionEffect(JazzyViewPager.TransitionEffect.Standard);
                        //mViewPager.setTransitionEffect(JazzyViewPager.TransitionEffect.Accordion);
                        mViewPager.setAdapter(new ViewPagerAdapter(mImageList));
                        mViewPager.setOffscreenPageLimit(2);
                        mViewPager.setCurrentItem(position);
                        mViewPager.setVisibility(View.VISIBLE);
                        mViewPager.setOnPageChangeListener(MainActivity.this);
                        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
                        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

                        //getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
                        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE);

                        //setContentView(R.layout.activity_main);
                    }
                } catch (SmbException e) {
                    e.printStackTrace();
                }

            }
        });

        updateFileGrid();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    public void onBackPressed() {
        //Log.d(TAG, "onBackPressed");
        if (mViewPager.getVisibility()==View.VISIBLE) {
            mViewPager.setVisibility(View.GONE);
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            mViewPager.setAdapter(null);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
        } else if (mPathStack.size()>0) {
            mCurrentPath = mPathStack.pop();
            updateFileGrid();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            if (mViewPager.getVisibility()==View.VISIBLE) {
                // hide navigation bar
                if (getWindow().getDecorView().getSystemUiVisibility()!=View.SYSTEM_UI_FLAG_LOW_PROFILE) {
                    getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE);
                }
            }
        }
        return super.dispatchTouchEvent(ev);
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

                    // sort by filename
                    Arrays.sort(lists, SmbFileHelper.NAME_COMPARATOR);

                    for (SmbFile s:lists) {
                        //Log.d(TAG, s.getName());
                        // only show images and directories
                        if (s.isDirectory() || s.isFile() && Utils.isImage(s.getName())) {
                            mImageList.add(s);
                        }
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

    private void loadSambaImage(final SmbFile file, final ImageView imageViewer, final int position) {

        new Thread(new Runnable() {
            @Override
            public void run() {
                InputStream inputStream = null;
                try {
                    inputStream = file.getInputStream();
                    final Bitmap tmpBitmap = BitmapFactory.decodeStream(inputStream);
                    final Bitmap bitmap;

                    if (tmpBitmap.getHeight()>tmpBitmap.getWidth()) {
                        bitmap =RotateBitmap(tmpBitmap, -90);
                    } else {
                        bitmap = tmpBitmap;
                    }

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            imageViewer.setImageBitmap(bitmap);
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

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

    }

    @Override
    public void onPageSelected(int position) {

    }

    @Override
    public void onPageScrollStateChanged(int state) {

    }

    private class ViewPagerAdapter extends PagerAdapter {

        private ArrayList<SmbFile> fileList;

        public ViewPagerAdapter(ArrayList<SmbFile> files) {
            fileList = files;
        }

        @Override
        public int getCount() {
            return fileList.size();
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            PhotoView photoView = new PhotoView(container.getContext());
            loadSambaImage(fileList.get(position), photoView, position);
            container.addView(photoView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            mViewPager.setObjectForPosition(photoView, position);
            return photoView;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            container.removeView((View)object);
        }

        @Override
        public boolean isViewFromObject(View view, Object o) {
            return view==o;
        }
    }

}
