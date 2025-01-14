package xyz.danoz.recyclerviewfastscroller.calculation.progress;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;
import android.view.View;

import java.util.Arrays;

import xyz.danoz.recyclerviewfastscroller.calculation.VerticalScrollBoundsProvider;

/**
 * Calculates scroll progress for a {@link RecyclerView} with a {@link LinearLayoutManager}
 */
public class GridLayoutManagerScrollProgressCalculator extends VerticalScrollProgressCalculator {

    public GridLayoutManagerScrollProgressCalculator(VerticalScrollBoundsProvider scrollBoundsProvider) {
        super(scrollBoundsProvider);
    }

    /**
     * @param recyclerView recycler that experiences a scroll event
     * @return the progress through the recycler view list content
     */
    @Override
    public float calculateScrollProgress(RecyclerView recyclerView) {
        RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();

        int spanCount = 1;
        int lastFullyVisiblePosition = 0;

        if (layoutManager instanceof GridLayoutManager) {
            spanCount = ((GridLayoutManager) layoutManager).getSpanCount();
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

        int currentSection = Math.max(0, lastFullyVisiblePosition - indexOfLastFullyVisibleItemInFirstSection);

        return (float) currentSection / numScrollableSectionsInList;
    }
}
