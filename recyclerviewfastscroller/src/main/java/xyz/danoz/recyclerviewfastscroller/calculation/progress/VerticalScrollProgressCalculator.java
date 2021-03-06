package xyz.danoz.recyclerviewfastscroller.calculation.progress;

import android.view.MotionEvent;

import xyz.danoz.recyclerviewfastscroller.calculation.VerticalScrollBoundsProvider;

/**
 * Basic scroll progress calculator used to calculate vertical scroll progress from a touch event
 */
public abstract class VerticalScrollProgressCalculator implements TouchableScrollProgressCalculator {

    protected final VerticalScrollBoundsProvider mScrollBoundsProvider;
    protected final int mHandleHeight;

    public VerticalScrollProgressCalculator(VerticalScrollBoundsProvider scrollBoundsProvider, int handleHeight) {
        mScrollBoundsProvider = scrollBoundsProvider;
        mHandleHeight = handleHeight;
    }

    @Override
    public float calculateScrollProgress(MotionEvent event) {
        float y = event.getY();

        if (y <= mScrollBoundsProvider.getMinimumScrollY()) {
            return 0;
        } else if (y >= mScrollBoundsProvider.getMaximumScrollY()) {
            return 1;
        } else {
            return y / mScrollBoundsProvider.getMaximumScrollY();
        }
    }
}
