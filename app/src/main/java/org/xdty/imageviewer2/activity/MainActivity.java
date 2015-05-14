package org.xdty.imageviewer2.activity;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
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
import com.daimajia.androidanimations.library.Techniques;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xdty.imageviewer2.R;
import org.xdty.imageviewer2.model.Config;
import org.xdty.imageviewer2.model.ImageFile;
import org.xdty.imageviewer2.model.RotateType;
import org.xdty.imageviewer2.model.SambaInfo;
import org.xdty.imageviewer2.model.SortType;
import org.xdty.imageviewer2.utils.ImageFileHelper;
import org.xdty.imageviewer2.utils.Utils;
import org.xdty.imageviewer2.view.JazzyViewPager;
import org.xdty.imageviewer2.view.adapter.GridAdapter;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.MalformedURLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;
import pl.droidsonroids.gif.GifDrawable;
import uk.co.senab.photoview.PhotoView;

import static org.xdty.imageviewer2.utils.Utils.RotateBitmap;


public class MainActivity extends Activity implements ViewPager.OnPageChangeListener {

    public final static String TAG = "MainActivity";

    private final ReentrantLock imageLoadLock = new ReentrantLock(true);
    private TextView emptyText;
    private ArrayList<ImageFile> mImageFileList = new ArrayList<>();
    private ArrayList<ImageFile> mImageList = new ArrayList<>();
    private GridAdapter gridAdapter;
    private ArrayDeque<PathInfo> mPathStack = new ArrayDeque<>();
    private String mCurrentPath = Config.ROOT_PATH;
    private JazzyViewPager mViewPager;
    private android.view.GestureDetector mClickDetector;
    private RotateGestureDetector mRotationDetector;
    private HashMap<Integer, Integer> rotationMap = new HashMap<>();
    private HashMap<Integer, Boolean> orientationMap = new HashMap<>();
    private Handler handler = new Handler();
    private GridView gridView;
    private int mGridPosition = -1;
    private RotateType rotateType = RotateType.ORIGINAL;
    private SortType localSortType = SortType.FILE_NAME;
    private SortType networkSortType = SortType.FILE_NAME;
    private SambaInfo currentSambaInfo = new SambaInfo();
    private boolean isFileExplorerMode = false;
    private boolean isShowHidingFiles = false;
    private boolean isReverseLocalSort = false;
    private boolean isReverseNetworkSort = false;
    private ArrayList<String> excludeList = new ArrayList<>();
    private Runnable hideSystemUIRunnable;
    private boolean isMenuOpened = false;
    private boolean updateGridOnBack = false;
    private ArrayList<SambaInfo> sambaInfoList = new ArrayList<>();
    private int lastPagePosition = -1;
    private AlertDialog detailDialog;

    @Override
    protected void onDestroy() {
        if (detailDialog != null) {
            detailDialog.cancel();
        }
        super.onDestroy();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        setContentView(R.layout.activity_main);

        System.setProperty("jcifs.smb.client.responseTimeout", "10000");
        System.setProperty("jcifs.smb.client.soTimeout", "10000");

        excludeList.add("Android");
        excludeList.add("storage");
        excludeList.add("tmp");

        emptyText = (TextView) findViewById(R.id.empty_dir);
        gridView = (GridView) findViewById(R.id.gridView);
        gridAdapter = new GridAdapter(this, mImageFileList);
        gridView.setAdapter(gridAdapter);

        mViewPager = (JazzyViewPager) findViewById(R.id.viewpager);
        mViewPager.setOnPageChangeListener(MainActivity.this);
        mViewPager.setOffscreenPageLimit(2);
        mViewPager.setTransitionEffect(JazzyViewPager.TransitionEffect.Standard);

        // handle only single tap event, show or hide systemUI
        mClickDetector = new android.view.GestureDetector(this,
                new android.view.GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onSingleTapConfirmed(MotionEvent e) {
                        if (isSystemUIVisible()) {
                            if (!isMenuOpened) {
                                hideSystemUIDelayed(0);
                            }
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

                    if (mCurrentPath.equals(Config.ROOT_PATH)) {
                        for (SambaInfo sambaInfo : sambaInfoList) {
                            if (sambaInfo.build().equals(mImageFileList.get(position).getPath())) {
                                currentSambaInfo = sambaInfo;
                                break;
                            }
                        }

                    }
                    mCurrentPath = mImageFileList.get(position).getPath();

                    updateFileGrid();
                    gridView.smoothScrollToPosition(0);
                    gridView.setSelection(0);
                } else {
                    mGridPosition = position;

                    mViewPager.setVisibility(View.VISIBLE);
                    mViewPager.setAdapter(new ViewPagerAdapter(mImageList));
                    mViewPager.setCurrentItem(mImageList.indexOf(mImageFileList.get(position)), false);
                    Log.d(TAG, "setCurrentItem:" + position);

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
        String serverPath = currentSambaInfo.build();
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        String servers = sharedPreferences.getString(Config.SERVERS, "");
        try {
            JSONObject jsonServers = new JSONObject(servers);

            JSONArray jsonArray = jsonServers.getJSONArray("server");
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = (JSONObject) jsonArray.get(i);
                if (currentSambaInfo.id == jsonObject.getInt("id")) {
                    currentSambaInfo = new SambaInfo(jsonObject);
                    break;
                }
                if (i == jsonArray.length() - 1) {
                    currentSambaInfo = new SambaInfo();
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        rotateType = RotateType.build(sharedPreferences.getString(Config.ROTATE_TYPE, "2"));
        localSortType = SortType.build(sharedPreferences.getString(Config.LOCAL_SORT_TYPE, "1"));
        networkSortType = SortType.build(sharedPreferences.getString(Config.NETWORK_SORT_TYPE, "0"));

        isFileExplorerMode = sharedPreferences.getBoolean(Config.FILE_EXPLORER_MODE, true);
        isShowHidingFiles = sharedPreferences.getBoolean(Config.SHOW_HIDING_FILES, false);
        isReverseLocalSort = sharedPreferences.getBoolean(Config.REVERSE_LOCAL_SORT, false);
        isReverseNetworkSort = sharedPreferences.getBoolean(Config.REVERSE_NETWORK_SORT, false);

        // set grid animation
        String effect = sharedPreferences.getString(Config.GRID_EFFECT, "STANDARD");
        gridAdapter.setAnimator(effect);


        if (!serverPath.equals(currentSambaInfo.build())) {
            mCurrentPath = Config.ROOT_PATH;
        }

        if (mViewPager == null || mViewPager.getVisibility() != View.VISIBLE) {
            updateFileGrid();
        } else {
            updateGridOnBack = true;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        isMenuOpened = false;
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

            if (updateGridOnBack) {
                updateFileGrid();
                updateGridOnBack = false;
            }

            mViewPager.setAdapter(null);
            lastPagePosition = -1;

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
    public boolean onMenuOpened(int featureId, Menu menu) {
        cancelHideSystemUIDelayed();
        isMenuOpened = true;
        return super.onMenuOpened(featureId, menu);
    }

    @Override
    public void onPanelClosed(int featureId, Menu menu) {
        isMenuOpened = false;
        hideSystemUIDelayed(3000);
        super.onPanelClosed(featureId, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        isMenuOpened = false;

        boolean result = true;

        int id = item.getItemId();

        switch (id) {
            case R.id.action_settings:
                Intent intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
                break;
            case R.id.action_detail:
                showDetailDialog();
                break;
            default:
                result = false;
                break;
        }

        return result || super.onOptionsItemSelected(item);
    }

    private void showDetailDialog() {
        int position = mViewPager.getCurrentItem();

        ImageFile imageFile = mImageList.get(position);

        View view = getLayoutInflater().inflate(R.layout.dialog_detail, (ViewGroup) findViewById(android.R.id.content), false);

        TextView path = (TextView) view.findViewById(R.id.detail_path);
        TextView type = (TextView) view.findViewById(R.id.detail_type);
        TextView size = (TextView) view.findViewById(R.id.detail_size);
        TextView resolution = (TextView) view.findViewById(R.id.detail_resolution);
        TextView date = (TextView) view.findViewById(R.id.detail_date);

        path.setText(imageFile.getPath());
        type.setText(imageFile.getMimeType());
        size.setText(imageFile.getFormattedSize());
        resolution.setText("" + imageFile.getImageWidth() + "x" + imageFile.getImageHeight());
        date.setText(imageFile.getFormattedDate());

        AlertDialog.Builder builder = new Builder(this);
        builder.setTitle("" + position + "/" + mImageList.size());
        builder.setView(view);
        builder.setCancelable(true);
        builder.setPositiveButton(android.R.string.ok, null);
        detailDialog = builder.create();
        detailDialog.show();
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {

        if (mViewPager.getVisibility() == View.VISIBLE) {
            menu.findItem(R.id.action_detail).setVisible(true);
        } else {
            menu.findItem(R.id.action_detail).setVisible(false);
        }

        return super.onPrepareOptionsMenu(menu);
    }

    private void updateFileGrid() {

        mImageFileList.clear();
        mImageList.clear();
        rotationMap.clear();
        orientationMap.clear();
        if (mViewPager.getAdapter() != null) {
            mViewPager.getAdapter().notifyDataSetChanged();
        }

        if (mCurrentPath.equals(Config.ROOT_PATH)) {
            emptyText.setVisibility(View.GONE);
            loadRootDir();
        } else {
            loadDir(mCurrentPath);
        }
    }

    private void loadRootDir() {

        new Thread(new Runnable() {
            @Override
            public void run() {

                // add local directory
                String localRoot = Environment.getExternalStorageDirectory().getAbsolutePath();

                File root = new File(localRoot);
                if (root.isDirectory() || root.isFile()) {
                    mImageFileList.add(new ImageFile(root));
                }

                // add samba directories
                SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
                String servers = sharedPreferences.getString(Config.SERVERS, "");
                if (servers != null && !servers.isEmpty()) {

                    try {
                        JSONObject jsonServers = new JSONObject(servers);
                        JSONArray jsonArray = jsonServers.getJSONArray("server");
                        for (int i = 0; i < jsonArray.length(); i++) {
                            JSONObject jsonObject = (JSONObject) jsonArray.get(i);

                            SambaInfo samba = new SambaInfo(jsonObject);

                            try {
                                SmbFile f = new SmbFile(samba.build(), samba.auth());
                                if (f.canRead()) {
                                    if (f.isDirectory() || f.isFile() && Utils.isImage(f.getName())) {
                                        mImageFileList.add(new ImageFile(f));
                                        sambaInfoList.add(samba);
                                        notifyListChanged();
                                    }
                                }
                            } catch (MalformedURLException | SmbException e) {
                                e.printStackTrace();
                            }
                        }

                    } catch (JSONException e) {
                        e.printStackTrace();
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
            }
        }).start();
    }

    private void loadDir(final String path) {

        new Thread(new Runnable() {
            @Override
            public void run() {

                try {
                    ImageFile root = new ImageFile(path, currentSambaInfo.auth());

                    if (root.exists() && root.isDirectory()) {
                        ImageFile[] files = root.listFiles();

                        // TODO: read sort config
                        // sort by filename

                        switch (root.isSamba() ? networkSortType : localSortType) {
                            case FILE_NAME:
                                Arrays.sort(files, ImageFileHelper.NAME_COMPARATOR);
                                break;
                            case LAST_MODIFIED:
                                Arrays.sort(files, ImageFileHelper.LAST_MODIFIED_COMPARATOR);
                                break;
                            case CONTENT_LENGTH:
                                Arrays.sort(files, ImageFileHelper.CONTENT_LENGTH_COMPARATOR);
                                break;
                        }

                        if (root.isSamba() ? isReverseNetworkSort : isReverseLocalSort) {
                            List<ImageFile> list = Arrays.asList(files);
                            Collections.reverse(list);
                            files = (ImageFile[]) list.toArray();
                        }

                        //Log.d(TAG, s.getName());
                        // only show images and directories
                        for (ImageFile f : files) {
                            // TODO: read exclude settings
                            if (!excludeList.contains(f.getName())) {
                                // TODO: read show only image config
                                if (f.isDirectory() && (isFileExplorerMode || f.hasImage()) ||
                                        f.isFile() && Utils.isImage(f.getName())) {
                                    if (isShowHidingFiles || (!isShowHidingFiles && !f.isHiding())) {
                                        mImageFileList.add(f);
                                    }
                                }
                            }
                        }

                        for (ImageFile f : files) {
                            if (f.isImage()) {
                                if (isShowHidingFiles || (!isShowHidingFiles && !f.isHiding())) {
                                    mImageList.add(f);
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

    private void loadImage(final ImageFile file, final ImageView imageView, final int position,
                           final boolean isHighQuality, final boolean autoRotate) {

        final WeakReference<ImageView> imageViewWeakReference = new WeakReference<>(imageView);

        new Thread(new Runnable() {
            @Override
            public void run() {

                if (mViewPager != null && mImageFileList.indexOf(mImageList.get(position)) != mGridPosition) {
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                imageLoadLock.lock();

                try {
                    if (imageViewWeakReference.get() == null || mViewPager.getVisibility() == View.GONE) {
                        return;
                    }

                    if (!isHighQuality && mViewPager.getCurrentItem() == position) {
                        loadImage(file, imageView, position, true, false);
                        lastPagePosition = position;
                    }

                    Log.d(TAG, "start lock: " + position);

                    BitmapFactory.Options options = new BitmapFactory.Options();

                    options.inJustDecodeBounds = true;
                    BitmapFactory.decodeStream(file.getInputStream(), null, options);

                    int imageHeight = options.outHeight;
                    int imageWidth = options.outWidth;

                    file.setImageHeight(imageHeight);
                    file.setImageWidth(imageWidth);

                    options.inJustDecodeBounds = false;

                    final Bitmap originBitmap;
                    final Bitmap bitmap;

                    if (!isHighQuality) {
                        options.inPreferredConfig = Bitmap.Config.RGB_565;
                        options.inSampleSize = Config.IMAGE_SIMPLE_SIZE;
                        originBitmap = BitmapFactory.decodeStream(file.getInputStream(), null, options);
                    } else if (imageHeight > Config.MAX_IMAGE_SIZE || imageWidth > Config.MAX_IMAGE_SIZE) {
                        // resize image to accepted size
                        Bitmap tmpBitmap = BitmapFactory.decodeStream(file.getInputStream());

                        double heightScale = Config.MAX_IMAGE_SIZE / (imageHeight * 1.0);
                        double widthScale = Config.MAX_IMAGE_SIZE / (imageWidth * 1.0);

                        double scale = heightScale < widthScale ? heightScale : widthScale;
                        //originBitmap = ThumbnailUtils.extractThumbnail(tmpBitmap, (int)(imageWidth*scale),(int)(imageHeight*scale));
                        originBitmap = Bitmap.createScaledBitmap(tmpBitmap, (int) (imageWidth * scale), (int) (imageHeight * scale), true);
                        tmpBitmap.recycle();
                    } else {
                        originBitmap = BitmapFactory.decodeStream(file.getInputStream());
                    }

                    switch (rotateType) {
                        case ROTATE_SCREEN_FIT_IMAGE:
                            // rotate screen to fit image
                            if (originBitmap.getHeight() > originBitmap.getWidth()) {
                                // save status to orientationMap
                                orientationMap.put(position, true);
                            } else {
                                orientationMap.put(position, false);
                            }
                            bitmap = null;
                            break;
                        case ROTATE_IMAGE_FIT_SCREEN:
                            // rotate image to fit screen
                            if (originBitmap.getHeight() > originBitmap.getWidth()) {
                                bitmap = RotateBitmap(originBitmap, -90);
                                originBitmap.recycle();
                            } else {
                                bitmap = null;
                            }
                            break;
                        default:
                            bitmap = null;
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

                            ImageView imageViewer = imageViewWeakReference.get();

                            if (imageViewer != null && mViewPager.getVisibility() == View.VISIBLE) {
                                //imageViewer.setImageBitmap(compressed);
                                try {
                                    if (file.isGif()) {
                                        imageViewer.setImageDrawable(gifFromStream);
                                    } else {
                                        if (bitmap != null) {
                                            imageViewer.setImageBitmap(bitmap);
                                        } else {
                                            imageViewer.setImageBitmap(originBitmap);
                                        }
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    if (gifFromStream != null) {
                                        gifFromStream.recycle();
                                    }
                                    if (bitmap != null) {
                                        bitmap.recycle();
                                    }
                                    if (originBitmap != null) {
                                        originBitmap.recycle();
                                    }
                                }

                                Log.d(TAG, "set image bitmap: " + position);
                                int orientation = getRequestedOrientation();

                                if (autoRotate) {
                                    if (mViewPager != null && position == mViewPager.getCurrentItem() && rotateType == RotateType.ROTATE_SCREEN_FIT_IMAGE) {
                                        if (orientationMap.get(position) && orientation != ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
                                            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                                        } else if (!orientationMap.get(position) && orientation != ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                                            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                                        }
                                    }
                                }
                            } else {
                                if (gifFromStream != null) {
                                    gifFromStream.recycle();
                                }
                                if (bitmap != null) {
                                    bitmap.recycle();
                                }
                                if (originBitmap != null) {
                                    originBitmap.recycle();
                                }
                            }
                        }
                    });
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    Log.d(TAG, "release lock: " + position);
                    if (imageLoadLock.isHeldByCurrentThread()) {
                        imageLoadLock.unlock();
                    } else {
                        Log.e(TAG, "release imageLoadLock error");
                    }
                }
            }
        }).start();
    }

    private void notifyListChanged() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mViewPager != null && mViewPager.getAdapter() != null) {
                    mViewPager.getAdapter().notifyDataSetChanged();
                }
                gridAdapter.clearThumbnailList();
                gridAdapter.notifyDataSetChanged();
            }
        });
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        //Log.d(TAG, "onPageScrolled:" + position);
    }

    @Override
    public void onPageSelected(int position) {
        //Log.d(TAG, "onPageSelected:" + position);
        mGridPosition = mImageFileList.indexOf(mImageList.get(position));
    }

    @Override
    public void onPageScrollStateChanged(int state) {
        //Log.d(TAG, "onPageScrollStateChanged:" + state);
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

            // reload high quality image
            if (lastPagePosition != position) {
                PhotoView photoView = (PhotoView) mViewPager.findViewWithTag(position);
                if (!mImageList.get(position).isGif()) {
                    loadImage(mImageList.get(position), photoView, position, true, false);
                }

                if (lastPagePosition != -1 && lastPagePosition != position) {
                    if (!mImageList.get(lastPagePosition).isGif()) {
                        loadImage(mImageList.get(lastPagePosition),
                                (PhotoView) mViewPager.findViewWithTag(lastPagePosition), lastPagePosition, false, false);
                    }
                }
                lastPagePosition = position;
            }
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

    private void cancelHideSystemUIDelayed() {
        if (hideSystemUIRunnable != null) {
            handler.removeCallbacks(hideSystemUIRunnable);
        }
    }

    private boolean isSystemUIVisible() {
        return (getWindow().getDecorView().getSystemUiVisibility() & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0;
    }

    private class ViewPagerAdapter extends PagerAdapter {

        private ArrayList<ImageFile> imageList;

        public ViewPagerAdapter(ArrayList<ImageFile> files) {
            imageList = files;
        }

        @Override
        public int getCount() {
            return imageList.size();
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
            loadImage(imageList.get(position), photoView, position, false, true);
            container.addView(photoView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            mViewPager.setObjectForPosition(photoView, position);

            return photoView;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            PhotoView photoView = (PhotoView) ((View) object).findViewWithTag(position);
            Log.d(TAG, "destroyItem");

            // release memory
            if (photoView != null) {

                if (photoView.getDrawable() instanceof GifDrawable) {
                    GifDrawable gifDrawable = (GifDrawable) photoView.getDrawable();
                    gifDrawable.recycle();
                    gifDrawable.setCallback(null);

                } else {
                    BitmapDrawable bitmapDrawable = (BitmapDrawable) photoView.getDrawable();
                    if (bitmapDrawable != null) {
                        bitmapDrawable.getBitmap().recycle();
                        bitmapDrawable.setCallback(null);
                    }
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

    private class PathInfo {
        String path;
        int position;

        public PathInfo(String path, int position) {
            this.position = position;
            this.path = path;
        }
    }

}
