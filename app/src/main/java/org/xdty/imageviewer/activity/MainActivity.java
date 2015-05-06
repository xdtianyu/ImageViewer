package org.xdty.imageviewer.activity;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;

import com.almeros.android.multitouch.RotateGestureDetector;

import org.xdty.imageviewer.R;
import org.xdty.imageviewer.model.Config;
import org.xdty.imageviewer.model.ImageFile;
import org.xdty.imageviewer.model.RotateType;
import org.xdty.imageviewer.model.SambaInfo;
import org.xdty.imageviewer.utils.ImageFileHelper;
import org.xdty.imageviewer.utils.Utils;
import org.xdty.imageviewer.view.GridAdapter;
import org.xdty.imageviewer.view.JazzyViewPager;

import java.io.BufferedInputStream;
import java.io.File;
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
import pl.droidsonroids.gif.GifDrawable;
import uk.co.senab.photoview.PhotoView;

import static org.xdty.imageviewer.utils.Utils.RotateBitmap;


public class MainActivity extends Activity implements ViewPager.OnPageChangeListener {

    public final static String TAG = "MainActivity";

    private final ReentrantLock imageLoadLock = new ReentrantLock(true);
    private TextView emptyText;
    private ArrayList<ImageFile> mImageFileList = new ArrayList<>();
    private GridAdapter gridAdapter;
    private ArrayDeque<PathInfo> mPathStack = new ArrayDeque<>();
    private String mCurrentPath = "";
    private JazzyViewPager mViewPager;
    private android.view.GestureDetector mClickDetector;
    private RotateGestureDetector mRotationDetector;
    private HashMap<Integer, Integer> rotationMap = new HashMap<>();
    private HashMap<Integer, Boolean> orientationMap = new HashMap<>();
    private Handler handler = new Handler();
    private GridView gridView;
    private int mGridPosition = -1;
    private RotateType rotateType = RotateType.ORIGINAL;
    private SambaInfo sambaInfo = new SambaInfo();
    private boolean isFileExplorerMode = false;
    private NtlmPasswordAuthentication smbAuth;

    private ArrayList<String> excludeList = new ArrayList<>();
    private Runnable hideSystemUIRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        setContentView(R.layout.activity_main);

        excludeList.add("Android");
        excludeList.add("storage");
        excludeList.add("tmp");

        emptyText = (TextView) findViewById(R.id.empty_dir);
        gridView = (GridView) findViewById(R.id.gridView);
        gridAdapter = new GridAdapter(this, mImageFileList);
        gridView.setAdapter(gridAdapter);

        mViewPager = (JazzyViewPager) findViewById(R.id.viewpager);
        mViewPager.setAdapter(new ViewPagerAdapter(mImageFileList));
        mViewPager.setOnPageChangeListener(MainActivity.this);
        mViewPager.setOffscreenPageLimit(2);
        mViewPager.setTransitionEffect(JazzyViewPager.TransitionEffect.Standard);

        // handle only single tap event, show or hide systemUI
        mClickDetector = new android.view.GestureDetector(this,
                new android.view.GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onSingleTapConfirmed(MotionEvent e) {
                        if (isSystemUIVisible()) {
                            hideSystemUIDelayed(200);
                        } else {
                            showSystemUI();
                            hideSystemUIDelayed(3000);
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
                if (mImageFileList.get(position).isDirectory()) {
                    mPathStack.push(new PathInfo(mCurrentPath, position));
                    mCurrentPath = mImageFileList.get(position).getPath();
                    updateFileGrid();
                    gridView.smoothScrollToPosition(0);
                    gridView.setSelection(0);
                } else {
                    mGridPosition = position;

                    mViewPager.setCurrentItem(position, false);
                    Log.d(TAG, "setCurrentItem:" + position);
                    mViewPager.setVisibility(View.VISIBLE);

                    if (rotateType == RotateType.ROTATE_IMAGE_FIT_SCREEN) {
                        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                    }

                    hideSystemUI();

                }
            }
        });

        gridView.setOnItemLongClickListener(new OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {

//                ImageView checkView = (ImageView)view.findViewById(R.id.check);
//                checkView.setVisibility(View.VISIBLE);
                return true;
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart");

        // reload config
        String serverPath = sambaInfo.build();
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        sambaInfo.server = sharedPreferences.getString(Config.SAMBA_SERVER, "");
        sambaInfo.folder = sharedPreferences.getString(Config.SAMBA_FOLDER, "");
        sambaInfo.username = sharedPreferences.getString(Config.SAMBA_USERNAME, "");
        sambaInfo.password = sharedPreferences.getString(Config.SAMBA_PASSWORD, "");

        smbAuth = new NtlmPasswordAuthentication("", sambaInfo.username, sambaInfo.password);
        rotateType = RotateType.build(sharedPreferences.getString(Config.ROTATE_TYPE, "2"));

        isFileExplorerMode = sharedPreferences.getBoolean(Config.FILE_EXPLORER_MODE, false);

        if (!serverPath.equals(sambaInfo.build())) {
            mCurrentPath = Config.ROOT_PATH;
        }
        updateFileGrid();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Log.d(TAG, "onConfigurationChanged");
    }

    @Override
    public void onBackPressed() {
        //Log.d(TAG, "onBackPressed");

        // TODO: restore position of parent grid

        if (mViewPager != null && mViewPager.getVisibility() == View.VISIBLE) {
            mViewPager.setVisibility(View.GONE);
//            mViewPager.removeAllViews();
//            mViewPager = null;

            if (getRequestedOrientation() != ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            }
            showSystemUI();
            System.gc();
        } else if (mPathStack.size() > 0) {
            PathInfo pathInfo = mPathStack.pop();
            mCurrentPath = pathInfo.path;
            mGridPosition = pathInfo.position;

            updateFileGrid();

            if (emptyText.getVisibility() == View.VISIBLE) {
                emptyText.setVisibility(View.GONE);
            }
        } else {
            super.onBackPressed();
        }

        // scroll grid to current image
        gridView.smoothScrollToPosition(mGridPosition);
        gridView.setSelection(mGridPosition);
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
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void updateFileGrid() {

        mImageFileList.clear();
        rotationMap.clear();
        orientationMap.clear();
        mViewPager.getAdapter().notifyDataSetChanged();

        if (mCurrentPath.equals(Config.ROOT_PATH)) {
            loadRootDir();
        } else {
            loadDir(mCurrentPath);
        }
    }

    private void loadRootDir() {

        new Thread(new Runnable() {
            @Override
            public void run() {

                String localRoot = Environment.getExternalStorageDirectory().getAbsolutePath();

                File root = new File(localRoot);
                if (root.isDirectory() || root.isFile()) {
                    mImageFileList.add(new ImageFile(root));
                }

                try {
                    SmbFile f = new SmbFile(sambaInfo.build(), smbAuth);
                    if (f.canRead()) {
                        if (f.isDirectory() || f.isFile() && Utils.isImage(f.getName())) {
                            mImageFileList.add(new ImageFile(f));
                        }
                    }
                } catch (MalformedURLException | SmbException e) {
                    e.printStackTrace();
                }

                notifyListChanged();

                if (mImageFileList.size() == 0) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            emptyText.setVisibility(View.VISIBLE);
                        }
                    });
                }
            }
        }).start();
    }

    private void loadDir(final String path) {

        new Thread(new Runnable() {
            @Override
            public void run() {

                try {
                    ImageFile root = new ImageFile(path, smbAuth);

                    if (root.exists() && root.isDirectory()) {
                        ImageFile[] files = root.listFiles();

                        // TODO: read sort config
                        // sort by filename
                        Arrays.sort(files, ImageFileHelper.NAME_COMPARATOR);

                        //Log.d(TAG, s.getName());
                        // only show images and directories
                        for (ImageFile f : files) {
                            // TODO: read exclude settings
                            if (!excludeList.contains(f.getName())) {
                                // TODO: read show only image config
                                if (f.isDirectory() && (isFileExplorerMode || f.hasImage()) ||
                                        f.isFile() && Utils.isImage(f.getName())) {
                                    mImageFileList.add(f);
                                }
                            }
                        }
                    }

                    notifyListChanged();

                    if (mImageFileList.size() == 0) {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                emptyText.setVisibility(View.VISIBLE);
                            }
                        });
                    }
                } catch (MalformedURLException | SmbException e) {
                    e.printStackTrace();
                }
            }
        }).start();

    }

    private void loadImage(final ImageFile file, final ImageView imageViewer, final int position) {

        new Thread(new Runnable() {
            @Override
            public void run() {

                if (mViewPager != null && position != mGridPosition) {
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                imageLoadLock.lock();
                Log.d(TAG, "start lock: " + position);
                InputStream inputStream = null;
                try {
                    inputStream = file.getInputStream();
                    final Bitmap tmpBitmap = BitmapFactory.decodeStream(inputStream);
                    final Bitmap bitmap;

                    switch (rotateType) {
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

//                    ByteArrayOutputStream out = new ByteArrayOutputStream();
//                    bitmap.compress(CompressFormat.JPEG, 90, out);
//                    final Bitmap compressed = BitmapFactory.decodeStream(new ByteArrayInputStream(out.toByteArray()));
//                    bitmap.recycle();

                    final GifDrawable gifFromStream;
                    if (file.isGif()) {
                        BufferedInputStream bis = null;
                        try {
                            bis = new BufferedInputStream(file.getInputStream(), (int) file.getContentLength());
                        } catch (IOException e) {
                            e.printStackTrace();
                        } finally {
                            gifFromStream = new GifDrawable(bis);
                        }
                    } else {
                        gifFromStream = null;
                    }

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            //imageViewer.setImageBitmap(compressed);
                            if (file.isGif()) {
                                imageViewer.setImageDrawable(gifFromStream);
                            } else {
                                imageViewer.setImageBitmap(bitmap);
                            }

                            Log.d(TAG, "set image bitmap: " + position);
                            int orientation = getRequestedOrientation();

                            if (mViewPager != null && position == mViewPager.getCurrentItem() && rotateType == RotateType.ROTATE_SCREEN_FIT_IMAGE) {
                                if (orientationMap.get(position) && orientation != ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
                                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                                } else if (!orientationMap.get(position) && orientation != ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                                }
                            }
                        }
                    });
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    Log.d(TAG, "release lock: " + position);
                    imageLoadLock.unlock();
                }
            }
        }).start();
    }

    private void notifyListChanged() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mViewPager != null) {
                    mViewPager.getAdapter().notifyDataSetChanged();
                }
                gridAdapter.clearThumbnailList();
                gridAdapter.notifyDataSetChanged();
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
        mGridPosition = position;
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

    private void hideSystemUIDelayed(int timeout) {

        if (hideSystemUIRunnable == null) {
            hideSystemUIRunnable = new Runnable() {
                @Override
                public void run() {
                    if (mViewPager != null && mViewPager.getVisibility() == View.VISIBLE) {
                        hideSystemUI();
                    }
                }
            };
        }

        handler.removeCallbacks(hideSystemUIRunnable);
        handler.postDelayed(hideSystemUIRunnable, timeout);
    }

    private boolean isSystemUIVisible() {
        return (getWindow().getDecorView().getSystemUiVisibility() & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0;
    }

    private class ViewPagerAdapter extends PagerAdapter {

        private ArrayList<ImageFile> fileList;

        public ViewPagerAdapter(ArrayList<ImageFile> files) {
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
                if (photoView.getDrawable() instanceof GifDrawable) {
                    Log.d(TAG, "photoView is GifDrawable");
                    GifDrawable gifDrawable = (GifDrawable) photoView.getDrawable();
                    gifDrawable.recycle();
                } else {
                    Log.d(TAG, "photoView is BitmapDrawable");
                    BitmapDrawable bitmapDrawable = (BitmapDrawable) photoView.getDrawable();
                    if (bitmapDrawable != null) {
                        bitmapDrawable.getBitmap().recycle();
                    }
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
            Log.d(TAG, "loadImage: " + position);
            loadImage(fileList.get(position), photoView, position);
            container.addView(photoView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            mViewPager.setObjectForPosition(photoView, position);

            return photoView;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            PhotoView photoView = (PhotoView) ((View) object).findViewWithTag(position);

            // release memory
            if (photoView != null) {

                if (photoView.getDrawable() instanceof GifDrawable) {
                    Log.d(TAG, "photoView is GifDrawable");
                    GifDrawable gifDrawable = (GifDrawable) photoView.getDrawable();
                    gifDrawable.recycle();
                } else {
                    Log.d(TAG, "photoView is BitmapDrawable");
                    BitmapDrawable bitmapDrawable = (BitmapDrawable) photoView.getDrawable();
                    if (bitmapDrawable != null) {
                        bitmapDrawable.getBitmap().recycle();
                    }
                }
                photoView.setImageDrawable(null);
            }

            container.removeView((View) object);
            unbindDrawables((View) object);
            object = null;
            System.gc();
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

    private class PathInfo {
        String path;
        int position;

        public PathInfo(String path, int position) {
            this.position = position;
            this.path = path;
        }
    }

}
