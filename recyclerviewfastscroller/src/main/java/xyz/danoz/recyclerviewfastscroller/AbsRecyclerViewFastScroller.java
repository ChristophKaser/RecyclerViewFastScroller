package xyz.danoz.recyclerviewfastscroller;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Handler;
import android.os.Message;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.OnScrollListener;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.SectionIndexer;

import java.lang.ref.WeakReference;

import xyz.danoz.recyclerviewfastscroller.calculation.progress.ScrollProgressCalculator;
import xyz.danoz.recyclerviewfastscroller.calculation.progress.TouchableScrollProgressCalculator;
import xyz.danoz.recyclerviewfastscroller.sectionindicator.SectionIndicator;

/**
 * Defines a basic widget that will allow for fast scrolling a RecyclerView using the basic paradigm of
 * a handle and a bar.
 * TODO: More specifics and better support for effectively extending this base class
 */
public abstract class AbsRecyclerViewFastScroller extends FrameLayout implements RecyclerViewScroller {

    /**
     * Animation
     **/
    private static final int DURATION_FADE_IN = 150;
    private static final int DURATION_FADE_OUT = 300;
    private static final int FADE_TIMEOUT = 1500;
    private HideHandler mHideHandler;

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
     * A special listener that corresponds to when the user is grabbing the handle
     */
    protected FastScrollListener mFastScrollListener;

    /* TODO:
     *      Consider making RecyclerView final and should be passed in using a custom attribute
     *      This could allow for some type checking on the section indicator wrt the adapter of the RecyclerView
    */
    private RecyclerView mRecyclerView;
    private SectionIndicator mSectionIndicator;

    /**
     * If I had my druthers, AbsRecyclerViewFastScroller would implement this as an interface, but Android has made
     * {@link OnScrollListener} an abstract class instead of an interface. Hmmm
     */
    protected OnScrollListener mOnScrollListener;

    private boolean shouldCreateScrollProgressCalculator;

    public AbsRecyclerViewFastScroller(Context context) {
        this(context, null, 0);
    }

    public AbsRecyclerViewFastScroller(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AbsRecyclerViewFastScroller(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        TypedArray attributes = getContext().getTheme().obtainStyledAttributes(attrs, STYLEABLE, 0, 0);

        try {
            int layoutResource = attributes.getResourceId(R.styleable.AbsRecyclerViewFastScroller_rfs_fast_scroller_layout,
                    getLayoutResourceId());
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            inflater.inflate(layoutResource, this, true);

            // Default to hiding the view so it can be shown on scroll and hidden again
            setAlpha(0);

            mBar = findViewById(R.id.scroll_bar);
            mHandle = findViewById(R.id.scroll_handle);

            Drawable barDrawable = attributes.getDrawable(R.styleable.AbsRecyclerViewFastScroller_rfs_barBackground);
            int barColor = attributes.getColor(R.styleable.AbsRecyclerViewFastScroller_rfs_barColor, Color.GRAY);
            applyCustomAttributesToView(mBar, barDrawable, barColor);

            Drawable handleDrawable = attributes.getDrawable(R.styleable.AbsRecyclerViewFastScroller_rfs_handleBackground);
            int handleColor = attributes.getColor(R.styleable.AbsRecyclerViewFastScroller_rfs_handleColor, Color.GRAY);
            applyCustomAttributesToView(mHandle, handleDrawable, handleColor);
        } finally {
            attributes.recycle();
        }
        mHideHandler = new HideHandler(this);
        setOnTouchListener(new FastScrollerTouchListener(this));
    }

    private void applyCustomAttributesToView(View view, Drawable drawable, int color) {
        if (drawable != null) {
            setViewBackground(view, drawable);
        } else {
            view.setBackgroundColor(color);
        }
    }

    public interface FastScrollListener {
        void notifyScrollState(boolean scrolling);
    }

    public void setFastScrollListener(FastScrollListener fastScrollListener) {
        mFastScrollListener = fastScrollListener;
    }

    public void notifyScrollState(boolean scrolling) {
        if (mFastScrollListener != null) {
            mFastScrollListener.notifyScrollState(scrolling);
        }
        if (scrolling) {
            // While scrolling, show forever.
            show(0);
        } else {
            // Once scrolling stops, hide after a short time.
            show(FADE_TIMEOUT);
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

    public void setSectionIndicator(SectionIndicator sectionIndicator) {
        mSectionIndicator = sectionIndicator;
    }

    @Nullable
    public SectionIndicator getSectionIndicator() {
        return mSectionIndicator;
    }

    @Override
    public void scrollTo(float scrollProgress, boolean fromTouch) {
        int position = getPositionFromScrollProgress(scrollProgress);

        if (mRecyclerView == null) {
            return;
        }

        mRecyclerView.scrollToPosition(position);

        updateSectionIndicator(position, scrollProgress);
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
        return mRecyclerView == null ? 0 : (int) (mRecyclerView.getAdapter().getItemCount() * scrollProgress);
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
                        scrollProgress = scrollProgressCalculator.calculateScrollProgress(recyclerView);
                    }
                    moveHandleToPosition(scrollProgress);
                }

                public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                    // While dragging, always show the handle.
                    if (newState == RecyclerView.SCROLL_STATE_DRAGGING)
                        show(0);
                        // Once dragging stops, show the handle only a little more, then fade out.
                    else if (newState == RecyclerView.SCROLL_STATE_IDLE)
                        show(FADE_TIMEOUT);
                }
            };
        }
        return mOnScrollListener;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        shouldCreateScrollProgressCalculator = true;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        if (mRecyclerView == null) {
            return;
        }

        if (getScrollProgressCalculator() == null || shouldCreateScrollProgressCalculator) {
            shouldCreateScrollProgressCalculator = false;
            onCreateScrollProgressCalculator();
        }

        // synchronize the handle position to the RecyclerView
        float scrollProgress = getScrollProgressCalculator().calculateScrollProgress(mRecyclerView);
        moveHandleToPosition(scrollProgress);
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

    private void show(int timeout) {
        animate()
                .alpha(1f)
                .setDuration(DURATION_FADE_IN);

        Message msg = mHideHandler.obtainMessage(1);
        mHideHandler.removeMessages(1);
        if (timeout != 0) {
            mHideHandler.sendMessageDelayed(msg, timeout);
        }
    }

    static class HideHandler extends Handler {
        private final WeakReference<AbsRecyclerViewFastScroller> weakReference;

        HideHandler(AbsRecyclerViewFastScroller scroller) {
            weakReference = new WeakReference<>(scroller);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1:
                    AbsRecyclerViewFastScroller scroller = weakReference.get();
                    if (scroller == null) break;
                    scroller.animate()
                            .alpha(0f)
                            .setDuration(DURATION_FADE_OUT);
                    break;
            }
        }
    }
}
