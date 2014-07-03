package net.neevek.android.widget;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Build;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.*;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.Scroller;
import net.neevek.utilities.Synchronizer;
import net.neevek.android.R;
import net.neevek.utilities.Timer;

import java.lang.reflect.Type;

/**
 * @author neevek <i at neevek.net>
 * @version v1.0.0 finished on Nov. 24, 2013 (a rainy Sunday in GuangZhou)
 * @version v1.0.3 finished at 2:49 a.m. on Dec. 5, 2013
 *
 * This class implements the bounce effect & pull-to-refresh feature for
 * ListView(the implementation can also be applied to ExpandableListView).
 *
 * For the bounce effect, the implementation simply intercepts touch events
 * and detects if the scrolling has reached the top or bottom edge, if so, we
 * call scrollTo() to scroll the entire the ListView off the screen, and then
 * with a Scroller, we compute the Y scroll positions and create a smooth
 * bounce effect.
 *
 * For pull-to-refresh, the implementation uses a header view which implements
 * the PullToRefreshCallback interface as the indicator view for displaying
 * "Pull to refresh", "Release to refresh", "Loading..." and an arrow image.
 * Of course, you can implement PullToRefreshCallback and write your own
 * PullToRefreshHeaderView, as long as you follow some requirements for
 * the layout of the header view, take the default PullToRefreshHeaderView
 * as a reference.
 *
 * NOTE: If you do not want the pull-to-refresh feature, you can still use
 *       OverScrollListView, in that case, OverScrollListView only offers
 *       you the bounce effect, and that is why it has the name. just remember
 *       not to call setPullToRefreshHeaderView()
 */

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
class Revealer implements ValueAnimator.AnimatorUpdateListener {
    private final int mDuration;
    private final View mHeaderView;

    private ValueAnimator mCurrAnimator;

    public Revealer(View headerView,int duration) {
        mHeaderView = headerView;
        mDuration = duration;
    }

    public void startRevealing(int fromY, int toY, final RevealerListener listener) {
        mCurrAnimator = ValueAnimator.ofInt(fromY, toY);
        mCurrAnimator.setDuration(mDuration);
        mCurrAnimator.addUpdateListener(this);

        final Revealer self = this;

        if (listener != null)
            mCurrAnimator.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {
                    listener.onRevealingStart(self);
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    listener.onRevealingEnd(self);
                }

                @Override
                public void onAnimationCancel(Animator animation) {

                }

                @Override
                public void onAnimationRepeat(Animator animation) {

                }
            });

        mCurrAnimator.start();
    }

    public void stop() {
        mCurrAnimator.end();
        mCurrAnimator = null;
    }

    @Override
    public void onAnimationUpdate(ValueAnimator animation) {
        mHeaderView.getLayoutParams().height = (Integer) animation.getAnimatedValue();
        mHeaderView.requestLayout();
    }

    public static interface RevealerListener {
        public abstract void onRevealingStart(Revealer revealer);
        public abstract void onRevealingEnd(Revealer revealer);
    }
}

public class OverScrollListView extends ListView {
    private final static int DEFAULT_MAX_OVER_SCROLL_DURATION = 350;
    private static final int REVEALER_ANIMATION_DURATION = 350;

    private static final boolean USE_MIN_REFRESH_DURATION = false;

    // boucing for a normal touch scroll gesture(happens right after the finger leaves the screen)
    private Scroller mScroller;

    private float mLastY;
    private boolean mIsTouching;
    private boolean mIsBeingTouchScrolled;

    // a threshold to tell whether the user is touch-scrolling
    private int mTouchSlop;
    private int mMinimumVelocity;
    private int mMaximumVelocity;

    // the top-level layout of the header view
    protected PullToRefreshCallback mOrigHeaderView;

    // the layout, of which we will do adjust the height, and on which
    // we call requestLayout() to cause the view hierarchy to be redrawn
    protected View mHeaderView;
    // for convenient adjustment of the header view height
    private ViewGroup.LayoutParams mHeaderViewLayoutParams;
    // the original height of the header view
    protected int mHeaderViewHeight;

    // user of this pull-to-refresh ListView certainly will register a
    // a listener, which will be called when a "refresh" action should
    // be initiated.
    private OnRefreshListener mOnRefreshListener;
    private boolean mIsRefreshing;
    // is finishRefreshing() has just been called?
    private boolean mCancellingRefreshing;
    private boolean mHideHeaderViewWithoutAnimation;

    private PullToLoadMoreCallback mSavedFooterView;
    private PullToLoadMoreCallback mFooterView;
    private boolean mIsLoadingMore;
    private OnLoadMoreListener mOnLoadMoreListener;

    private int mLoadingMorePullDistanceThreshold;

    private float mScreenDensity;

    private boolean mMarkAutoRefresh;

    private Object mBizContextForRefresh;

    private Context mContext;
    private Revealer mRevealer;
    protected int mHeaderThresholdHeight = 0;
    private Timer mRefreshTimer = null;

    private Synchronizer mRefreshSync;
    private RefreshObserver mRefreshObserver;
    protected RelativeLayout mHeaderView2;

    private int dp2px(float dpVal) {
        final DisplayMetrics dm = this.getResources().getDisplayMetrics();
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dpVal, dm));
    }

    public OverScrollListView(Context context) {
        super(context);
        init(context, null);
    }

    public OverScrollListView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public OverScrollListView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        mContext = context;

        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.OverScrollListView);
        setupRefreshTimer(USE_MIN_REFRESH_DURATION
                ? ta.getInt(R.styleable.OverScrollListView_min_refresh_duration, 0)
                : 0);
        ta.recycle();

        mScreenDensity = context.getResources().getDisplayMetrics().density;
        mLoadingMorePullDistanceThreshold = (int)(mScreenDensity * 50);

        mScroller = new Scroller(context, new DecelerateInterpolator(1.3f));

        // on Android 2.3.3, disabling overscroll makes ListView behave weirdly
        if (Build.VERSION.SDK_INT > 10) {
            // disable the glow effect at the edges when overscrolling.
            setOverScrollMode(OVER_SCROLL_NEVER);
        }

        final ViewConfiguration configuration = ViewConfiguration.get(getContext());

        mTouchSlop = configuration.getScaledTouchSlop();
        mMinimumVelocity = configuration.getScaledMinimumFlingVelocity();
        mMaximumVelocity = configuration.getScaledMaximumFlingVelocity();
    }

    protected void setupRefreshTimer(int minRefreshDuration) {
        if (minRefreshDuration == 0) {
            mRefreshSync = new Synchronizer(1);
            mRefreshTimer = null;
        }
        else {
            mRefreshSync = new Synchronizer(2);
            mRefreshTimer = new Timer(minRefreshDuration);
        }

        mRefreshSync.addObserver(new Synchronizer.SynchronizationObserver() {
            @Override
            public void onSynced(Synchronizer synchronizer) {
                finishRefreshingInternal();
                mRefreshSync.recycle();
            }
        });
    }

    public PullToRefreshHeaderView setPullToRefreshHeaderView(int headerLayout) {
        if (mOrigHeaderView != null) {
            return null;
        }

        // TODO: add proper checking for invalid resource IDs!

        // create Pull2Refresh header
        final RelativeLayout origHeaderView = new RelativeLayout(mContext);
        origHeaderView.setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        origHeaderView.setBackgroundColor(Color.TRANSPARENT);

        // create LinearLayout used as
        // container for provided view
        final LinearLayout headerContainer = new LinearLayout(mContext);
        headerContainer.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0));
        headerContainer.setGravity(Gravity.BOTTOM);

        // set header view at header container
        mHeaderView = headerContainer;

        origHeaderView.addView(headerContainer);

        // inflate provided layout
        LayoutInflater.from(mContext).inflate(headerLayout, headerContainer);
        final PullToRefreshHeaderView headerView = (PullToRefreshHeaderView) headerContainer.getChildAt(0);

        mHeaderThresholdHeight = headerView.getRefreshThresholdHeight();
        mOrigHeaderView = headerView;

        // set dimensions
        headerView.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
        headerContainer.getLayoutParams().height = headerView.getMeasuredHeight();

        // add header view to the list
        mHeaderView2 = origHeaderView;
        addHeaderView(origHeaderView);

        return headerView;

        /*
        if (!(headerView instanceof PullToRefreshCallback)) {
            throw new IllegalArgumentException("Pull-to-refresh header view must implement PullToRefreshCallback");
        }

        mOrigHeaderView = (PullToRefreshCallback)headerView;

        if (headerView instanceof ViewGroup) {
            mHeaderView = ((ViewGroup) headerView).getChildAt(0);    // pay attention to this
            if (mHeaderView == null || (!(mHeaderView instanceof LinearLayout) && !(mHeaderView instanceof RelativeLayout))) {
                throw new IllegalArgumentException("Pull-to-refresh header view must have " +
                        "the following layout hierachy: LinearLayout->LinearLayout->[either a LinearLayout or RelativeLayout]");
            }
        } else {
            throw new IllegalArgumentException("Pull-to-refresh header view must have " +
                    "the following layout hierarchy: LinearLayout->LinearLayout->[either a LinearLayout or RelativeLayout]");
        }
        addHeaderView(headerView);
        */
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);

        if (mHeaderViewHeight == 0 && mHeaderView != null) {
            mHeaderViewLayoutParams = mHeaderView.getLayoutParams();

            // create revealer
            mRevealer = new Revealer(mHeaderView, REVEALER_ANIMATION_DURATION);

            // after the first "laying-out", we get the original height of header view
            mHeaderViewHeight = mHeaderViewLayoutParams.height;

            if (mMarkAutoRefresh) {
                mMarkAutoRefresh = false;
                post(new Runnable() {
                    @Override
                    public void run() {
                        startRefreshManually(null);
                    }
                });
            } else {
                // set the header height to 0 in advance. "post(Runnable)" below is queued up
                // to run in the main thread, which may delay for some time
                mHeaderViewLayoutParams.height = 0;
                // hide the header view
                post(new Runnable() {
                    @Override
                    public void run() {
                        setHeaderViewHeightInternal(0);
                    }
                });
            }
        }
    }

    public void setPullToLoadMoreFooterView(View footerView) {
        if (!(footerView instanceof PullToLoadMoreCallback)) {
            throw new IllegalArgumentException("Pull-to-load-more footer view must implement PullToLoadMoreCallback");
        }

        mSavedFooterView = (PullToLoadMoreCallback)footerView;
        ((View)mSavedFooterView).setVisibility(GONE);

        addFooterView(footerView);

        footerView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mIsLoadingMore && mFooterView != null) {
                    mIsLoadingMore = true;
                    mFooterView.onStartLoadingMore();

                    if (mOnLoadMoreListener != null) {
                        mOnLoadMoreListener.onLoadMore();
                    }
                }
            }
        });
    }

    public void setOnRefreshListener(OnRefreshListener listener) {
        mOnRefreshListener = listener;
    }

    public void setOnLoadMoreListener(OnLoadMoreListener listener) {
        mOnLoadMoreListener = listener;
    }

    public interface RefreshObserver {
        public abstract void onRefreshFinished();
    }

    public void finishRefreshing(RefreshObserver observer) {
        mRefreshObserver = observer;
        mRefreshSync.notifySync();
    }

    public void finishRefreshingInternal() {
        if (mIsRefreshing) {
            mCancellingRefreshing = true;
            mIsRefreshing = false;

            mRefreshObserver.onRefreshFinished();
            mOrigHeaderView.onBeforeEndRefreshing();

            mScroller.forceFinished(true);

            // hide the header view, with a smooth bouncing effect
            springBack(-mHeaderViewHeight + getScrollY());
//            setSelection(0);
        }
    }

    public void finishRefreshingAndHideHeaderViewWithoutAnimation() {
        if (mIsRefreshing) {
            mCancellingRefreshing = true;
            mHideHeaderViewWithoutAnimation = true;
            mIsRefreshing = false;

            mScroller.forceFinished(true);
            // hide the header view, with a smooth bouncing effect
            springBack(getScrollY());
        }
    }

    public void finishLoadingMore() {
        if (mIsLoadingMore) {
            mIsLoadingMore = false;

            if (mFooterView != null) {
                mFooterView.onEndLoadingMore();
            }
        }
    }

    public void resetLoadMoreFooterView() {
        if (mSavedFooterView != null) {
            mFooterView = mSavedFooterView;
        }

        if (mFooterView != null) {
            mFooterView.onReset();
        }
    }

    public void enableLoadMore(boolean enable) {
        if (enable) {
            if (mSavedFooterView != null) {
                mFooterView = mSavedFooterView;
                ((View)mFooterView).setVisibility(VISIBLE);
            }
        } else if (mFooterView != null) {
            ((View)mFooterView).setVisibility(GONE);
            mSavedFooterView = mFooterView;
            mFooterView = null;
        }
    }

    public boolean isLoadingMoreEnabled() {
        return mFooterView != null;
    }

    protected boolean overScrollBy(int deltaX, int deltaY, int scrollX, int scrollY, int scrollRangeX, int scrollRangeY, int maxOverScrollX, int maxOverScrollY, boolean isTouchEvent) {
        if (!isTouchEvent && mScroller.isFinished() && mVelocityTracker != null) {
            mVelocityTracker.computeCurrentVelocity((int)(16 * mScreenDensity), mMaximumVelocity);
            int yVelocity = (int) mVelocityTracker.getYVelocity(0);

            if ((Math.abs(yVelocity) > mMinimumVelocity)) {
                mScroller.fling(0, getScrollY(), 0, -yVelocity, 0, 0, -mMaximumVelocity, mMaximumVelocity);
                postInvalidate();
            }
        }
        return true;
    }

    private void initOrResetVelocityTracker() {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        } else {
            mVelocityTracker.clear();
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // for whatever reason, stop the scroller when the user *might*
                // start new touch-scroll gestures.
                mScroller.forceFinished(true);

                mLastY = ev.getRawY();
                mIsTouching = true;
                mCancellingRefreshing = false;

                initOrResetVelocityTracker();
                mVelocityTracker.addMovement(ev);
                break;
        }
        return super.onInterceptTouchEvent(ev);
    }


    private VelocityTracker mVelocityTracker;

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        mVelocityTracker.addMovement(ev);

        switch (ev.getAction()) {
            case MotionEvent.ACTION_MOVE:
                float y = ev.getRawY();
                int deltaY = (int)(y - mLastY);

                if (deltaY == 0) {
                    return true;
                }

                if (mIsBeingTouchScrolled) {
                    if (getChildCount() > 0) {
                        handleTouchScroll(deltaY);
                    }

                    mLastY = y;
                } else if (Math.abs(deltaY) > mTouchSlop) {
                    // check if the delta-y has exceeded the threshold
                    mIsBeingTouchScrolled = true;
                    mLastY = y;
                    break;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mIsTouching = false;
                mIsBeingTouchScrolled = false;

                // 'getScrollY != 0' means that content of the ListView is off screen.
                // Or if it is not in "refreshing" state while height of the header view
                // is greater than 0, we must set it to 0 with a smooth bounce effect
                if ((getScrollY() != 0 || (!mIsRefreshing && getCurrentHeaderViewHeight() > 0))) {
                    springBack();

                    // it is safe to digest the touch events here
                    return true;
                }

                break;
        }

        int curScrollY = getScrollY();

        // if not in 'refreshing' state or scrollY is less than zero, and height of
        // header view is greater than zero. we should keep the the first item of the
        // ListView always at the top(we are decreasing height of the header view, without
        // calling setSelection(0), we will decrease height of the header view and scroll
        // the ListView itself at the same time, which will cause scrolling too fast
        // when decreasing height of the header view)
        if ((!mIsRefreshing  && getCurrentHeaderViewHeight() > 0) || curScrollY < 0) {
//            setSelection(0);
            return true;
        } else if (curScrollY > 0) {
//            setSelection(getCount() - 1);
            return true;
        }

        // let the original ListView handle the touch events
        return super.onTouchEvent(ev);
    }

    private void handleTouchScroll(int deltaY) {
        boolean reachTopEdge = reachTopEdge();
        boolean reachBottomEdge = reachBottomEdge();
        if (!reachTopEdge && !reachBottomEdge) {
            // since we are at the middle of the ListView, we don't
            // need to handle any touch events
            return;
        }

        final int scrollY = getScrollY();

        int listViewHeight = getHeight();
        // 0.4f is just a number that gives OK effect out of many tests. it means nothing special
        float scale = ((float)listViewHeight - Math.abs(scrollY) - getCurrentHeaderViewHeight()) / getHeight() * 0.4f;

        int newDeltaY = Math.round(deltaY * scale);

        if (newDeltaY != 0) {
            deltaY = newDeltaY;
        }

        if (reachTopEdge) {
            if (deltaY > 0) {
                scrollDown(deltaY);
            } else {
                scrollUp(deltaY);
            }
        } else {
            if (deltaY > 0) {
                if (scrollY > 0) {
                    // when scrollY is greater than 0, it means we reach the bottom of the list
                    // and the ListView is scrolled off the screen from the bottom, now we
                    // scrollDown() to scroll it back, otherwise, we just let the original ListView
                    // handle the scroll_down events
                    scrollDown(Math.min(deltaY, scrollY));
                }
            } else {
                scrollUp(deltaY);
            }
        }
    }

    private boolean reachTopEdge() {
        int childCount = getChildCount();
        if (childCount > 0) {
            return (getFirstVisiblePosition() == 0) && (getChildAt(0).getTop() == 0);
        } else {
            return true;
        }
    }

    private boolean reachBottomEdge() {
        int childCount = getChildCount();
        if (childCount > 0) {
            return (getLastVisiblePosition() == getCount() - 1) &&
                    (getChildAt(childCount - 1).getBottom() <= getHeight());
        }
        return true;
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void springBack() {
        int scrollY = getScrollY();

        int curHeaderViewHeight = getCurrentHeaderViewHeight();
        if (curHeaderViewHeight >= mHeaderThresholdHeight && mHeaderViewHeight > 0) {

            if (!mIsRefreshing && mOrigHeaderView != null) {
                final int remainingHeight = mHeaderViewHeight - curHeaderViewHeight;

                if (remainingHeight > 0 && mRevealer != null) {
                    mRevealer.startRevealing(curHeaderViewHeight, mHeaderViewHeight, new Revealer.RevealerListener() {
                        @Override
                        public void onRevealingStart(Revealer revealer) {

                        }

                        @Override
                        public void onRevealingEnd(Revealer revealer) {
                            triggerRefresh();
                        }
                    });
                    mIsRefreshing = true;
                } else {
                    triggerRefresh();
                }
            }
        } else {
            scrollY -= curHeaderViewHeight;
        }

        if (scrollY != 0) {
            if (mFooterView != null && !mIsLoadingMore) {
                if (scrollY >= mLoadingMorePullDistanceThreshold) {
                    mIsLoadingMore = true;
                    mFooterView.onStartLoadingMore();

                    if (mOnLoadMoreListener != null) {
                        mOnLoadMoreListener.onLoadMore();
                    }
                } else if (scrollY > 0) {
                    mFooterView.onCancelPulling();
                }
            }

            if (!mCancellingRefreshing) {
                springBack(scrollY);
            }
        }
    }

    private void triggerRefresh() {
        mIsRefreshing = true;

        if (mRefreshTimer != null) {
            mRefreshTimer.setAction(new Runnable() {
                @Override
                public void run() {
                    mRefreshSync.notifySync();
                }
            });
            mRefreshTimer.start();
        }

        mOrigHeaderView.onStartRefreshing();

        if (mOnRefreshListener != null) {
            mOnRefreshListener.onRefresh(mBizContextForRefresh);
            mBizContextForRefresh = null;
        }
    }

    public void startRefreshManually(Object bizContextForRefresh) {
        if (!mIsRefreshing && mOrigHeaderView != null && mHeaderViewHeight > 0) {
            mBizContextForRefresh = bizContextForRefresh;

            mMarkAutoRefresh = false;
            setHeaderViewHeight(mHeaderViewHeight);

            triggerRefresh();
        } else {
            mMarkAutoRefresh = true;
        }
    }

    public void startLoadingMoreManually() {
        if (!isLoadingMoreEnabled()) {
            enableLoadMore(true);
        }

        if (mFooterView != null) {
            mIsLoadingMore = true;
            mFooterView.onStartLoadingMore();

            if (mOnLoadMoreListener != null) {
                mOnLoadMoreListener.onLoadMore();
            }
        }
    }

    private void springBack(int scrollY) {
        mScroller.startScroll(0, scrollY, 0, -scrollY, DEFAULT_MAX_OVER_SCROLL_DURATION);
        postInvalidate();
    }

    @Override
    public void computeScroll() {
        if (mScroller.computeScrollOffset()) {
            int scrollY = getScrollY();

            // if not in "refreshing" state, we must decrease height of the
            // header view to 0
            if (!mHideHeaderViewWithoutAnimation && !mIsRefreshing && getCurrentHeaderViewHeight() > 0) {
                scrollY -= getCurrentHeaderViewHeight();
            }

            final int deltaY = mScroller.getCurrY() - scrollY;

            if (deltaY != 0) {
                if (deltaY < 0) {
                    scrollDown(-deltaY);

                } else {
                    scrollUp(-deltaY);
                }
            } else if (mCancellingRefreshing && scrollY == 0) {
                if (mHideHeaderViewWithoutAnimation) {
                    mHideHeaderViewWithoutAnimation = false;

                    mHeaderViewLayoutParams.height = 0;
                    requestLayout();
                }

                if (mOrigHeaderView != null) {
                    mOrigHeaderView.onEndRefreshing();
                }

                notifyRefreshAnimationEnd();
            }

            postInvalidate();

        } else if (!mIsTouching && (getScrollY() != 0 || (!mIsRefreshing && getCurrentHeaderViewHeight() != 0))) {
            springBack();
        }

        super.computeScroll();
    }

    /**
     * scrollDown() does 2 things:
     *
     * 1. check if height of the header view is greater than 0, if so, decrease it to 0
     *
     * 2. scroll content of the ListView off the screen any there's any deltaY left(i.e.
     *    deltaY is not 0)
     */
    private void scrollDown(int deltaY) {
        if (!mIsRefreshing && getScrollY() <= 0 && reachTopEdge()) {
            final int curHeaderViewHeight = getCurrentHeaderViewHeight();
            if (curHeaderViewHeight < mHeaderViewHeight) {
                int newHeaderViewHeight = curHeaderViewHeight + deltaY;
                if (newHeaderViewHeight < mHeaderViewHeight) {
                    setHeaderViewHeight(newHeaderViewHeight);
                    return ;
                } else {
                    setHeaderViewHeight(mHeaderViewHeight);
                    deltaY = newHeaderViewHeight - mHeaderViewHeight;
                }
            }
        }

        scrollBy(0, -deltaY);
    }

    /**
     * scrollUp() does 3 things:
     *
     * 1. if scrollY is less than 0, it means we have scrolled the list off the screen
     *    from the top, now we scroll back and make the list to reach the top edge of
     *    the screen.
     *
     * 2. check height of the header view and see if it is greater than 0, if so, we
     *    decrease it and make it zero.
     *
     * 3. now check if we have scrolled the list to reach the bottom of the screen, if so
     *    we scroll the list off the screen from the bottom.
     */
    private void scrollUp(int deltaY) {
        final int scrollY = getScrollY();
        if (scrollY < 0) {
            if (scrollY < deltaY) {     // both scrollY and deltaY are less than 0
                scrollBy(0, -deltaY);
                return;
            } else {
                scrollTo(0, 0);
                deltaY -= scrollY;

                if (deltaY == 0) {
                    return;
                }
            }
        }

        if (!mIsRefreshing) {
            int curHeaderViewHeight = getCurrentHeaderViewHeight();
            if (curHeaderViewHeight > 0) {

                int newHeaderViewHeight = curHeaderViewHeight + deltaY;
                if (newHeaderViewHeight > 0) {
                    setHeaderViewHeight(newHeaderViewHeight);

                    return;
                } else {
                    setHeaderViewHeight(0);

                    deltaY = newHeaderViewHeight;
                }
            }
        }

        if (reachBottomEdge()) {
            scrollBy(0, -deltaY);
        }
    }

    @Override
    public void scrollTo(int x, int y) {
        int oldScrollY = getScrollY();

        super.scrollTo(x, y);

        if (mOrigHeaderView != null && y < 0 && !mIsRefreshing) {
            int curTotalScrollY = getCurrentHeaderViewHeight() + (-y);
            mOrigHeaderView.onPull(curTotalScrollY);
        } else if (mFooterView != null && !mIsLoadingMore) {
            int halfPullDistanceThreshold = mLoadingMorePullDistanceThreshold / 2;
            if (y > halfPullDistanceThreshold) {
                if (oldScrollY <= halfPullDistanceThreshold) {
                    mFooterView.onStartPulling();
                } else if (oldScrollY < mLoadingMorePullDistanceThreshold && y >= mLoadingMorePullDistanceThreshold) {
                    mFooterView.onReachAboveRefreshThreshold();
                } else if (oldScrollY >= mLoadingMorePullDistanceThreshold && y < mLoadingMorePullDistanceThreshold) {
                    mFooterView.onReachBelowRefreshThreshold();
                }
            } else {
                mFooterView.onCancelPulling();
            }
        }
    }

    private void setHeaderViewHeight(int height) {
        if (mHeaderViewLayoutParams != null && (mHeaderViewLayoutParams.height != 0 || height != 0)) {
            setHeaderViewHeightInternal(height);
        }
    }

    private void setHeaderViewHeightInternal(int height) {
        int oldHeight = mHeaderViewLayoutParams.height;

        mHeaderViewLayoutParams.height = height;

        // if mHeaderView is visible(I mean within the confines of the visible screen), we should
        // request the mHeaderView to re-layout itself, if mHeaderView is not visible, we should
        // redraw the ListView itself, which ensures correct scroll position of the ListView.
        if (mHeaderView.isShown()) {
            mHeaderView.requestLayout();
        } else {
            invalidate();
        }

        if (mOrigHeaderView != null && !mIsRefreshing && !mCancellingRefreshing) {
            if (oldHeight == 0 && height > 0) {
                mOrigHeaderView.onStartPulling();
            } else if (oldHeight > 0 && height == 0) {
                mOrigHeaderView.onEndPulling();
            }
            mOrigHeaderView.onPull(height);

            if (oldHeight < mHeaderThresholdHeight && height >= mHeaderThresholdHeight) {
                mOrigHeaderView.onReachAboveHeaderViewHeight();
            } else if (oldHeight >= mHeaderThresholdHeight && height < mHeaderThresholdHeight) {
                if (height != 0) {  // initial setup
                    mOrigHeaderView.onReachBelowHeaderViewHeight();
                }
            }
        } else if (mCancellingRefreshing && height == 0) {
            if (mOrigHeaderView != null) {
                mOrigHeaderView.onEndRefreshing();
            }

            notifyRefreshAnimationEnd();
        }
    }

    private void notifyRefreshAnimationEnd() {
        mCancellingRefreshing = false;
        if (mOnRefreshListener != null) {
            mOnRefreshListener.onRefreshAnimationEnd();
        }
    }

    private int getCurrentHeaderViewHeight() {
        if (mHeaderViewLayoutParams != null) {
            return mHeaderViewLayoutParams.height;
        }
        return 0;
    }

    // see http://stackoverflow.com/a/9173866/668963
    @Override
    protected void onDetachedFromWindow() {
        try {
            super.onDetachedFromWindow();
        } catch(IllegalArgumentException iae) {
            // Workaround for http://code.google.com/p/android/issues/detail?id=22751
        }
    }

    // see http://stackoverflow.com/a/8433777/668963
    @Override
    protected void dispatchDraw(Canvas canvas) {
        try {
            super.dispatchDraw(canvas);
        } catch (IndexOutOfBoundsException e) {
            e.printStackTrace();
            // ignore this exception
        }
    }

    /**
     * The listener to be registered through OverScrollListView.setOnRefreshListener()
     */
    public static interface OnRefreshListener {
        void onRefresh(Object bizContext);
        void onRefreshAnimationEnd();
    }

    /**
     * The interface to be implemented by header view to be used with OverScrollListView
     */
    public interface PullToRefreshCallback {
        void onStartPulling();
        void onEndPulling();

        // scrollY = how far have we pulled?
        void onPull(int scrollY);

        void onReachAboveHeaderViewHeight();
        void onReachBelowHeaderViewHeight();

        void onStartRefreshing();
        void onEndRefreshing();

        void onBeforeEndRefreshing();
    }

    public static interface OnLoadMoreListener {
        void onLoadMore();
    }

    public interface PullToLoadMoreCallback {
        void onReset();
        void onStartPulling();

        void onReachAboveRefreshThreshold();
        void onReachBelowRefreshThreshold();

        void onStartLoadingMore();
        void onEndLoadingMore();

        void onCancelPulling();
    }
}
