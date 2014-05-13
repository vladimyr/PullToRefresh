package net.neevek.android.widget;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import net.neevek.android.R;

/**
 * Created with IntelliJ IDEA.
 * User: neevek
 * Date: 11/17/13
 * Time: 8:25 PM
 */
public class PullToRefreshHeaderView extends RelativeLayout implements OverScrollListView.PullToRefreshCallback {
    private final static int ROTATE_ANIMATION_DURATION = 300;

    private View mArrowView;
    private TextView mTvRefresh;
    private ProgressBar mProgressBar;

    private Animation mAnimRotateUp;
    private Animation mAnimRotateDown;

    private String mPullText = "Pull to refresh";
    private String mReleaseText = "Release to refresh";
    private String mRefreshText = "Refreshing...";

    private int mArrowViewId = 0;
    private int mTvRefreshId = 0;
    private int mProgressBarId = 0;

    // TODO: add code based constructor!

    public PullToRefreshHeaderView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public PullToRefreshHeaderView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context, attrs);
    }

    public void init(Context context, AttributeSet attrs) {
        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.PullToRefreshHeaderView);

        mArrowViewId = ta.getResourceId(R.styleable.PullToRefreshHeaderView_arrow, 0);
        mTvRefreshId = ta.getResourceId(R.styleable.PullToRefreshHeaderView_message_label, 0);
        mProgressBarId = ta.getResourceId(R.styleable.PullToRefreshHeaderView_progress_indicator, 0);

        ta.recycle();

        mAnimRotateUp = new RotateAnimation(0, -180f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        mAnimRotateUp.setDuration(ROTATE_ANIMATION_DURATION);
        mAnimRotateUp.setFillAfter(true);
        mAnimRotateDown = new RotateAnimation(-180f, 0, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        mAnimRotateDown.setDuration(ROTATE_ANIMATION_DURATION);
        mAnimRotateDown.setFillAfter(true);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);

        if (mArrowView == null) {
            mArrowView = findViewById(mArrowViewId);
            mTvRefresh = (TextView)findViewById(mTvRefreshId);
            mProgressBar = (ProgressBar)findViewById(mProgressBarId);
        }
    }

    @Override
    public void onStartPulling() {
        mProgressBar.setVisibility(GONE);
        mArrowView.setVisibility(VISIBLE);
        mTvRefresh.setVisibility(VISIBLE);
        mTvRefresh.setText(mPullText);
    }

    /**
     * @param scrollY [screenHeight, 0]
     */
    @Override
    public void onPull(int scrollY) {
    }

    @Override
    public void onReachAboveHeaderViewHeight() {
        mProgressBar.setVisibility(GONE);
        mTvRefresh.setText(mReleaseText);
        mArrowView.startAnimation(mAnimRotateUp);
    }

    @Override
    public void onReachBelowHeaderViewHeight() {
        mProgressBar.setVisibility(GONE);
        mTvRefresh.setText(mPullText);
        mArrowView.startAnimation(mAnimRotateDown);
    }

    @Override
    public void onStartRefreshing() {
        mArrowView.clearAnimation();
        mArrowView.setVisibility(GONE);
        mProgressBar.setVisibility(VISIBLE);
        mTvRefresh.setText(mRefreshText);
    }

    @Override
    public void onEndRefreshing() {
        mProgressBar.setVisibility(GONE);
        mTvRefresh.setVisibility(GONE);
    }

    public void setPullText(String pullText) {
        mPullText = pullText;
    }

    public void setReleaseText(String releaseText) {
        mReleaseText = releaseText;
    }

    public void setRefreshText(String refreshText) {
        mRefreshText = refreshText;
    }
}
