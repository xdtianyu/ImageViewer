package xyz.danoz.recyclerviewfastscroller.calculation.progress;

import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.view.View;

import java.util.Arrays;

import xyz.danoz.recyclerviewfastscroller.calculation.VerticalScrollBoundsProvider;

/**
 * Calculates scroll progress for a {@link RecyclerView} with a {@link LinearLayoutManager}
 */
public class VerticalLayoutManagerScrollProgressCalculator extends VerticalScrollProgressCalculator {

    private int lastSection = 0;
    private int deltaY = 0;
    private float deltaSection = 0;

    public VerticalLayoutManagerScrollProgressCalculator(
            VerticalScrollBoundsProvider scrollBoundsProvider, int handleHeight) {
        super(scrollBoundsProvider, handleHeight);
    }

    /**
     * @param recyclerView recycler that experiences a scroll event
     * @return the progress through the recycler view list content
     */
//    @Override
    public float calculateScrollProgress(RecyclerView recyclerView) {

        RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();

        int spanCount = 1;
        int lastFullyVisiblePosition = 0;

        if (layoutManager instanceof GridLayoutManager) {
            spanCount = ((GridLayoutManager) layoutManager).getSpanCount();
        }

        if (layoutManager instanceof LinearLayoutManager) {
            lastFullyVisiblePosition = ((LinearLayoutManager) layoutManager).
                    findLastCompletelyVisibleItemPosition();
        } else if (layoutManager instanceof StaggeredGridLayoutManager) {
            int[] into = ((StaggeredGridLayoutManager) layoutManager).
                    findLastCompletelyVisibleItemPositions(null);
            Arrays.sort(into);
            lastFullyVisiblePosition = into[into.length - 1];
            spanCount = ((StaggeredGridLayoutManager) layoutManager).getSpanCount();
        }

        View visibleChild = recyclerView.getChildAt(0);
        if (visibleChild == null) {
            return 0;
        }
        ViewHolder holder = recyclerView.getChildViewHolder(visibleChild);
        int itemHeight = holder.itemView.getHeight();
        int recyclerHeight = recyclerView.getHeight();
        int itemsInWindow = (recyclerHeight / itemHeight) * spanCount;

        int numItemsInList = recyclerView.getAdapter().getItemCount();
        int numScrollableSectionsInList = numItemsInList - itemsInWindow;
        int indexOfLastFullyVisibleItemInFirstSection = numItemsInList - numScrollableSectionsInList - 1;

        int currentSection = lastFullyVisiblePosition - indexOfLastFullyVisibleItemInFirstSection;

        return (float) currentSection / numScrollableSectionsInList;
    }

    /**
     * @param recyclerView recycler that experiences a scroll event
     * @param dx           The amount of horizontal scroll.
     * @param dy           The amount of vertical scroll.
     * @return the progress through the recycler view list content
     */
    public float calculateScrollProgress(RecyclerView recyclerView, int dx, int dy) {

        RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();

        int spanCount = 1;
        int lastFullyVisiblePosition = 0;

        if (layoutManager instanceof GridLayoutManager) {
            spanCount = ((GridLayoutManager) layoutManager).getSpanCount();
        }

        if (layoutManager instanceof LinearLayoutManager) {
            lastFullyVisiblePosition = ((LinearLayoutManager) layoutManager).
                    findLastCompletelyVisibleItemPosition();
        } else if (layoutManager instanceof StaggeredGridLayoutManager) {
            int[] into = ((StaggeredGridLayoutManager) layoutManager).
                    findLastCompletelyVisibleItemPositions(null);
            Arrays.sort(into);
            lastFullyVisiblePosition = into[into.length - 1];
            spanCount = ((StaggeredGridLayoutManager) layoutManager).getSpanCount();
        }

        View visibleChild = recyclerView.getChildAt(0);
        if (visibleChild == null) {
            return 0;

        }
        ViewHolder holder = recyclerView.getChildViewHolder(visibleChild);
        int itemHeight = holder.itemView.getHeight();
        int recyclerHeight = recyclerView.getHeight();
        int itemsInWindow = (recyclerHeight / itemHeight) * spanCount;

        // only LinearLayout supported

        int numItemsInList = recyclerView.getAdapter().getItemCount();

        int rowCount = numItemsInList / spanCount + (numItemsInList % spanCount != 0 ? 1 : 0);
        int recyclerFullHeight = itemHeight * rowCount;

        int numScrollableSectionsInList = numItemsInList - itemsInWindow;
        int indexOfLastFullyVisibleItemInFirstSection = numItemsInList - numScrollableSectionsInList - 1;

        int currentSection = lastFullyVisiblePosition - indexOfLastFullyVisibleItemInFirstSection;

        float deltaProgress = 0;

        /*
        ** Fixme: if spanCount is big enough e.g. 10, too fast scroll back will have a top offset
        ** Because deltaProgress is 0 (need to be negative) but still plus a deltaSection.
        */
        if (lastSection == currentSection) {
            deltaY += dy;
            deltaProgress = (float) deltaY / (recyclerFullHeight - recyclerHeight);
        } else {
            if (Math.abs(deltaY) < 30) {
                if (lastSection < currentSection) {
                    deltaSection = 0;
                } else {
                    // plus spanCount
                    deltaSection = (float) spanCount;
                }
            }
            lastSection = currentSection;

            deltaY = 0;
        }
        return (currentSection + deltaSection) / numScrollableSectionsInList + deltaProgress;
    }
}
