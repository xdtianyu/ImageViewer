package xyz.danoz.recyclerviewfastscroller;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.OnScrollListener;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.SectionIndexer;

import xyz.danoz.recyclerviewfastscroller.calculation.progress.ScrollProgressCalculator;
import xyz.danoz.recyclerviewfastscroller.calculation.progress.TouchableScrollProgressCalculator;
import xyz.danoz.recyclerviewfastscroller.sectionindicator.SectionIndicator;

/**
 * Defines a basic widget that will allow for fast scrolling a RecyclerView using the basic paradigm of
 * a handle and a bar.
 * <p/>
 * TODO: More specifics and better support for effectively extending this base class
 */
public abstract class AbsRecyclerViewFastScroller extends FrameLayout implements RecyclerViewScroller {

    private static final int[] STYLEABLE = R.styleable.AbsRecyclerViewFastScroller;
    /**
     * The long bar along which a handle travels
     */
    protected final View mBar;
    /**
     * The handle that signifies the user's progress in the list
     */
    protected final View mHandle;
    /**
     * If I had my druthers, AbsRecyclerViewFastScroller would implement this as an interface, but Android has made
     * {@link OnScrollListener} an abstract class instead of an interface. Hmmm
     */
    protected OnScrollListener mOnScrollListener;
    /* TODO:
     *      Consider making RecyclerView final and should be passed in using a custom attribute
     *      This could allow for some type checking on the section indicator wrt the adapter of the RecyclerView
    */
    private RecyclerView mRecyclerView;
    private SectionIndicator mSectionIndicator;

    public AbsRecyclerViewFastScroller(Context context) {
        this(context, null, 0);
    }

    public AbsRecyclerViewFastScroller(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AbsRecyclerViewFastScroller(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        TypedArray attributes = getContext().getTheme().obtainStyledAttributes(attrs, STYLEABLE, 0,
                0);

        try {
            int layoutResource = attributes.getResourceId(
                    R.styleable.AbsRecyclerViewFastScroller_rfs_fast_scroller_layout,
                    getLayoutResourceId());
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(
                    Context.LAYOUT_INFLATER_SERVICE);
            inflater.inflate(layoutResource, this, true);

            mBar = findViewById(R.id.scroll_bar);
            mHandle = findViewById(R.id.scroll_handle);

            Drawable barDrawable = attributes.getDrawable(
                    R.styleable.AbsRecyclerViewFastScroller_rfs_barBackground);
            int barColor = attributes.getColor(R.styleable.AbsRecyclerViewFastScroller_rfs_barColor,
                    Color.GRAY);
            applyCustomAttributesToView(mBar, barDrawable, barColor);

            Drawable handleDrawable = attributes.getDrawable(
                    R.styleable.AbsRecyclerViewFastScroller_rfs_handleBackground);
            int handleColor = attributes.getColor(
                    R.styleable.AbsRecyclerViewFastScroller_rfs_handleColor, Color.GRAY);
            applyCustomAttributesToView(mHandle, handleDrawable, handleColor);
        } finally {
            attributes.recycle();
        }

        setOnTouchListener(new FastScrollerTouchListener(this));
    }

    private void applyCustomAttributesToView(View view, Drawable drawable, int color) {
        if (drawable != null) {
            setViewBackground(view, drawable);
        } else {
            view.setBackgroundColor(color);
        }
    }

    /**
     * Provides the ability to programmatically set the color of the fast scroller's handle
     *
     * @param color for the handle to be
     */
    public void setHandleColor(int color) {
        mHandle.setBackgroundColor(color);
    }

    /**
     * Provides the ability to programmatically set the background drawable of the fast scroller's handle
     *
     * @param drawable for the handle's background
     */
    public void setHandleBackground(Drawable drawable) {
        setViewBackground(mHandle, drawable);
    }

    /**
     * Provides the ability to programmatically set the color of the fast scroller's bar
     *
     * @param color for the bar to be
     */
    public void setBarColor(int color) {
        mBar.setBackgroundColor(color);
    }

    /**
     * Provides the ability to programmatically set the background drawable of the fast scroller's bar
     *
     * @param drawable for the bar's background
     */
    public void setBarBackground(Drawable drawable) {
        setViewBackground(mBar, drawable);
    }

    @TargetApi(VERSION_CODES.JELLY_BEAN)
    private void setViewBackground(View view, Drawable background) {
        if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN) {
            view.setBackground(background);
        } else {
            //noinspection deprecation
            view.setBackgroundDrawable(background);
        }
    }

    @Override
    public void setRecyclerView(RecyclerView recyclerView) {
        mRecyclerView = recyclerView;
    }

    /**
     * Classes that extend AbsFastScroller must implement their own {@link OnScrollListener} to respond to scroll
     * events when the {@link #mRecyclerView} is scrolled NOT using the fast scroller.
     *
     * @return an implementation for responding to scroll events from the {@link #mRecyclerView}
     */
    @NonNull
    public OnScrollListener getOnScrollListener() {
        if (mOnScrollListener == null) {
            mOnScrollListener = new OnScrollListener() {
                @Override
                public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                    float scrollProgress = 0;
                    ScrollProgressCalculator scrollProgressCalculator = getScrollProgressCalculator();
                    if (scrollProgressCalculator != null) {
                        //scrollProgress = scrollProgressCalculator.calculateScrollProgress(recyclerView);
                        scrollProgress = scrollProgressCalculator.calculateScrollProgress(
                                recyclerView, dx, dy);
                    }
                    moveHandleToPosition(scrollProgress);
                }
            };
        }
        return mOnScrollListener;
    }

    @Override
    public void scrollTo(float scrollProgress, boolean fromTouch) {
        int position = getPositionFromScrollProgress(scrollProgress);
        //mRecyclerView.scrollToPosition(position);
        RecyclerView.LayoutManager layoutManager = mRecyclerView.getLayoutManager();

        // calculate top offset
        int offset = getOffsetFromScrollProgress(scrollProgress);
        Log.e("aa", "" + position);
        if (layoutManager instanceof LinearLayoutManager) {
            ((LinearLayoutManager) layoutManager).scrollToPositionWithOffset(position, offset);

        } else if (layoutManager instanceof StaggeredGridLayoutManager) {
            ((StaggeredGridLayoutManager) layoutManager).scrollToPositionWithOffset(position,
                    offset);
        }

        updateSectionIndicator(position, scrollProgress);
    }

    public void enableOnScrollerListener(boolean enable) {
        if (enable) {
            mRecyclerView.setOnScrollListener(getOnScrollListener());
        } else {
            mRecyclerView.setOnScrollListener(null);
        }
    }

    @Nullable
    public SectionIndicator getSectionIndicator() {
        return mSectionIndicator;
    }

    public void setSectionIndicator(SectionIndicator sectionIndicator) {
        mSectionIndicator = sectionIndicator;
    }

    public int getSpanCount() {
        int spanCount = 1;

        RecyclerView.LayoutManager layoutManager = mRecyclerView.getLayoutManager();
        if (layoutManager instanceof GridLayoutManager) {
            spanCount = ((GridLayoutManager) layoutManager).getSpanCount();
        } else if (layoutManager instanceof StaggeredGridLayoutManager) {
            spanCount = ((StaggeredGridLayoutManager) layoutManager).getSpanCount();
        }
        return spanCount;
    }

    public int getFirstItemHeight() {
        View visibleChild = mRecyclerView.getChildAt(0);
        if (visibleChild == null) {
            return 0;
        }

        RecyclerView.ViewHolder holder = mRecyclerView.getChildViewHolder(visibleChild);
        return holder.itemView.getHeight();
    }

    private void updateSectionIndicator(int position, float scrollProgress) {
        if (mSectionIndicator != null) {
            mSectionIndicator.setProgress(scrollProgress);
            if (mRecyclerView.getAdapter() instanceof SectionIndexer) {
                SectionIndexer indexer = ((SectionIndexer) mRecyclerView.getAdapter());
                int section = indexer.getSectionForPosition(position);
                Object[] sections = indexer.getSections();
                mSectionIndicator.setSection(sections[section]);
            }
        }
    }

    private int getPositionFromScrollProgress(float scrollProgress) {
        return (int) getCountFromScrollProgress(scrollProgress);
    }

    private double getCountFromScrollProgress(float scrollProgress) {

        int recyclerHeight = mRecyclerView.getHeight();
        int itemsInWindow = (recyclerHeight / getFirstItemHeight()) * getSpanCount();
        int numItemsInList = mRecyclerView.getAdapter().getItemCount();

        return (numItemsInList - itemsInWindow) * scrollProgress;
    }

    private int getOffsetFromScrollProgress(float scrollProgress) {
        double count = getCountFromScrollProgress(scrollProgress);
        double offset = count - Math.floor(count);

        int itemHeight = getFirstItemHeight();

        float spanStair = ((float) ((int) count) % getSpanCount()) / getSpanCount();

        return -(int) ((offset * (1.0 / getSpanCount()) + spanStair) * itemHeight);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        if (getScrollProgressCalculator() == null) {
            onCreateScrollProgressCalculator();
        }

        // synchronize the handle position to the RecyclerView
        if (false) {
            float scrollProgress = getScrollProgressCalculator().calculateScrollProgress(
                    mRecyclerView, 0, 0);
            moveHandleToPosition(scrollProgress);
        }
    }

    /**
     * Sub classes have to override this method and create the ScrollProgressCalculator instance in this method.
     */
    protected abstract void onCreateScrollProgressCalculator();

    /**
     * Takes a touch event and determines how much scroll progress this translates into
     *
     * @param event touch event received by the layout
     * @return scroll progress, or fraction by which list is scrolled [0 to 1]
     */
    public float getScrollProgress(MotionEvent event) {
        ScrollProgressCalculator scrollProgressCalculator = getScrollProgressCalculator();
        if (scrollProgressCalculator != null) {
            return getScrollProgressCalculator().calculateScrollProgress(event);
        }
        return 0;
    }

    /**
     * Define a layout resource for your implementation of AbsFastScroller
     * Currently must contain a handle view (R.id.scroll_handle) and a bar (R.id.scroll_bar)
     *
     * @return a resource id corresponding to the chosen layout.
     */
    protected abstract int getLayoutResourceId();

    /**
     * Define a ScrollProgressCalculator for your implementation of AbsFastScroller
     *
     * @return a chosen implementation of {@link ScrollProgressCalculator}
     */
    @Nullable
    protected abstract TouchableScrollProgressCalculator getScrollProgressCalculator();

    /**
     * Moves the handle of the scroller by specific progress amount
     *
     * @param scrollProgress fraction by which to move scroller [0 to 1]
     */
    public abstract void moveHandleToPosition(float scrollProgress);

}