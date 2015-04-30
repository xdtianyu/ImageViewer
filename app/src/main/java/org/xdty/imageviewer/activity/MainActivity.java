package org.xdty.imageviewer.activity;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.ImageView;

import com.almeros.android.multitouch.RotateGestureDetector;

import org.xdty.imageviewer.R;
import org.xdty.imageviewer.model.Config;
import org.xdty.imageviewer.model.RotateType;
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
import java.util.HashMap;
import java.util.concurrent.locks.ReentrantLock;

import jcifs.smb.NtlmPasswordAuthentication;
import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;
import uk.co.senab.photoview.PhotoView;

import static org.xdty.imageviewer.utils.Utils.RotateBitmap;


public class MainActivity extends Activity implements ViewPager.OnPageChangeListener {

    public final static String TAG = "MainActivity";
    private final ReentrantLock sambaLock = new ReentrantLock(true);
    private ArrayList<SmbFile> mImageList = new ArrayList<>();
    private ImageAdapter imageAdapter;
    private ArrayDeque<String> mPathStack = new ArrayDeque<>();
    private String mCurrentPath = Config.server + Config.sharedFolder;
    private JazzyViewPager mViewPager;
    private android.view.GestureDetector mClickDetector;
    private RotateGestureDetector mRotationDetector;
    private HashMap<Integer, Integer> rotationMap = new HashMap<>();
    private HashMap<Integer, Boolean> orientationMap = new HashMap<>();
    private Handler handler = new Handler();

    private int mGridClickPosition = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        setContentView(R.layout.activity_main);

        GridView gridView = (GridView) findViewById(R.id.gridView);
        imageAdapter = new ImageAdapter(this, mImageList);
        gridView.setAdapter(imageAdapter);

        // handle only single tap event, show or hide systemUI
        mClickDetector = new android.view.GestureDetector(this,
                new android.view.GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onSingleTapConfirmed(MotionEvent e) {
                        if (isSystemUIVisible()) {
                            hideSystemUI();
                        } else {
                            showSystemUI();
                        }
                        return true;
                    }
                });

        // handle rotate gesture, rotate the image and save state.
        mRotationDetector = new RotateGestureDetector(this, new RotateGestureDetector.OnRotateGestureListener() {

            PhotoView photoView = null;
            int rotate = 0;

            @Override
            public boolean onRotate(RotateGestureDetector detector) {

                if (photoView != null) {
                    float degree = detector.getRotationDegreesDelta();
                    photoView.setRotation(-degree + rotate);
                }
                return false;
            }

            @Override
            public boolean onRotateBegin(RotateGestureDetector detector) {
                photoView = (PhotoView) mViewPager.findViewWithTag(mViewPager.getCurrentItem());
                if (photoView != null) {
                    int position = (int) photoView.getTag();
                    if (rotationMap.containsKey(position)) {
                        rotate = rotationMap.get(position);
                    }
                }
                return true;
            }

            @Override
            public void onRotateEnd(RotateGestureDetector detector) {
                if (photoView != null) {
                    // set rotation and save state
                    float degree = -detector.getRotationDegreesDelta() + rotate;

                    int n = Math.round(degree / 90);
                    n = n % 4;
                    if (n < 0) {
                        n += 4;
                    }
                    switch (n) {
                        case 0:
                            photoView.setRotation(0);
                            break;
                        case 1:
                            photoView.setRotation(90);
                            break;
                        case 2:
                            photoView.setRotation(180);
                            break;
                        case 3:
                            photoView.setRotation(270);
                            break;
                    }
                    rotationMap.put((int) photoView.getTag(), (int) photoView.getRotation());
                    rotate = 0;
                    photoView = null;
                }
            }
        });

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

                        mGridClickPosition = position;

                        mViewPager = (JazzyViewPager) findViewById(R.id.viewpager);
                        mViewPager.setAdapter(new ViewPagerAdapter(mImageList));
                        mViewPager.setOnPageChangeListener(MainActivity.this);
                        mViewPager.setOffscreenPageLimit(2);

                        mViewPager.setTransitionEffect(JazzyViewPager.TransitionEffect.Standard);
                        //mViewPager.setTransitionEffect(JazzyViewPager.TransitionEffect.Accordion);
                        mViewPager.getAdapter().notifyDataSetChanged();

                        mViewPager.setCurrentItem(position, false);
                        Log.d(TAG, "setCurrentItem:" + position);
                        mViewPager.setVisibility(View.VISIBLE);

                        // TODO: read config here
                        if (Config.rotateType == RotateType.ROTATE_IMAGE_FIT_SCREEN) {
                            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                        }

                        //getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
                        //getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

                        //getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
                        hideSystemUI();

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
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Log.d(TAG, "onConfigurationChanged");
    }

    @Override
    public void onBackPressed() {
        //Log.d(TAG, "onBackPressed");
        if (mViewPager != null && mViewPager.getVisibility() == View.VISIBLE) {
            mViewPager.setVisibility(View.GONE);
            mViewPager.removeAllViews();
            mViewPager = null;

            if (getRequestedOrientation() != ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            }
            showSystemUI();
        } else if (mPathStack.size() > 0) {
            mCurrentPath = mPathStack.pop();
            updateFileGrid();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        Log.d(TAG, "onWindowFocusChanged");
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {

        if (mViewPager != null && mViewPager.getVisibility() == View.VISIBLE) {
            // hide or show systemUI
            mClickDetector.onTouchEvent(ev);
            mRotationDetector.onTouchEvent(ev);
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
        rotationMap.clear();
        orientationMap.clear();
        new Thread(new Runnable() {
            @Override
            public void run() {
                NtlmPasswordAuthentication auth = new NtlmPasswordAuthentication("", Config.user, Config.password);
                try {
                    SmbFile file = new SmbFile(path, auth);
                    SmbFile[] lists = file.listFiles();

                    // sort by filename
                    Arrays.sort(lists, SmbFileHelper.NAME_COMPARATOR);

                    for (SmbFile s : lists) {
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

                if (mViewPager != null && position != mGridClickPosition) {
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                sambaLock.lock();
                Log.d(TAG, "start lock: " + position);
                InputStream inputStream = null;
                try {
                    inputStream = file.getInputStream();
                    final Bitmap tmpBitmap = BitmapFactory.decodeStream(inputStream);
                    final Bitmap bitmap;

                    // TODO: read settings here
                    switch (Config.rotateType) {
                        case ROTATE_SCREEN_FIT_IMAGE:
                            // rotate screen to fit image
                            if (tmpBitmap.getHeight() > tmpBitmap.getWidth()) {
                                // save status to orientationMap
                                orientationMap.put(position, true);
                            } else {
                                orientationMap.put(position, false);
                            }
                            bitmap = tmpBitmap;
                            break;
                        case ROTATE_IMAGE_FIT_SCREEN:
                            // rotate image to fit screen
                            if (tmpBitmap.getHeight() > tmpBitmap.getWidth()) {
                                bitmap = RotateBitmap(tmpBitmap, -90);
                                tmpBitmap.recycle();
                            } else {
                                bitmap = tmpBitmap;
                            }
                            break;
                        default:
                            bitmap = tmpBitmap;
                    }

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            imageViewer.setImageBitmap(bitmap);
                            Log.d(TAG, "set image bitmap: " + position);
                            int orientation = getRequestedOrientation();

                            if (mViewPager != null && position == mViewPager.getCurrentItem() && Config.rotateType == RotateType.ROTATE_SCREEN_FIT_IMAGE) {
                                if (orientationMap.get(position) && orientation != ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
                                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                                } else if (!orientationMap.get(position) && orientation != ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                                }
                            }

                            if (mGridClickPosition == position) {
                                mGridClickPosition = -1;
                            }
                        }
                    });
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    Log.d(TAG, "release lock: " + position);
                    sambaLock.unlock();
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
        //Log.d(TAG, "onPageScrolled:" +position);
    }

    @Override
    public void onPageSelected(int position) {
        Log.d(TAG, "onPageSelected:" + position);
    }

    @Override
    public void onPageScrollStateChanged(int state) {
        // rotate screen to fit image size
        if (state == ViewPager.SCROLL_STATE_IDLE) {
            final int position = mViewPager.getCurrentItem();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (orientationMap.get(position) != null) {
                        int orientation = getRequestedOrientation();

                        if (orientationMap.get(position) && orientation != ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
                            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                        } else if (!orientationMap.get(position) && orientation != ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                        }
                    }
                }
            }, 10);
        }
    }

    private void hideSystemUI() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    private void showSystemUI() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    private boolean isSystemUIVisible() {
        return (getWindow().getDecorView().getSystemUiVisibility() & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0;
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

            PhotoView photoView = (PhotoView) container.findViewWithTag(position);

            if (photoView != null) {
                BitmapDrawable bitmapDrawable = (BitmapDrawable) photoView.getDrawable();
                if (bitmapDrawable != null) {
                    bitmapDrawable.getBitmap().recycle();
                }
            }

            photoView = new PhotoView(container.getContext());
            photoView.setTag(position);

            // restore rotation
            if (rotationMap.containsKey(position)) {
                int rotate = rotationMap.get(position);
                photoView.setRotation(rotate);
            }

            Log.d(TAG, "getCurrentItem:" + mViewPager.getCurrentItem());
            Log.d(TAG, "loadSambaImage: " + position);
            loadSambaImage(fileList.get(position), photoView, position);
            container.addView(photoView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            mViewPager.setObjectForPosition(photoView, position);
            return photoView;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            PhotoView photoView = (PhotoView) ((View) object).findViewWithTag(position);

            // release memory
            if (photoView != null) {
                BitmapDrawable bitmapDrawable = (BitmapDrawable) photoView.getDrawable();
                if (bitmapDrawable != null) {
                    bitmapDrawable.getBitmap().recycle();
                }
                photoView.setImageDrawable(null);
            }

            container.removeView((View) object);
            unbindDrawables((View) object);
            object = null;
        }

        @Override
        public boolean isViewFromObject(View view, Object o) {
            return view == o;
        }

        protected void unbindDrawables(View view) {
            if (view.getBackground() != null) {
                view.getBackground().setCallback(null);
            }
            if (view instanceof ViewGroup) {
                for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
                    unbindDrawables(((ViewGroup) view).getChildAt(i));
                }
                ((ViewGroup) view).removeAllViews();
            }
        }
    }

}
