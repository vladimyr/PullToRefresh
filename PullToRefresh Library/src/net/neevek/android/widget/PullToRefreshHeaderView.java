package net.neevek.android.widget;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import net.neevek.android.R;

import java.util.concurrent.Callable;

/**
 * Created with IntelliJ IDEA.
 * User: neevek
 * Date: 11/17/13
 * Time: 8:25 PM
 */
public class PullToRefreshHeaderView extends RelativeLayout implements OverScrollListView.PullToRefreshCallback {
    public static final String TAG = "PullToRefreshHeaderView";

    private final static int ROTATE_ANIMATION_DURATION = 300;

    protected View mArrow;
    protected TextView mMessageLabel;
    protected ProgressBar mProgressIndicator;

    public View getArrow() {
        return mArrow;
    }

    public TextView getMessageLabel() {
        return mMessageLabel;
    }

    public ProgressBar getProgressIndicator() {
        return mProgressIndicator;
    }

    private Animation mAnimRotateUp;
    private Animation mAnimRotateDown;

    private String mPullText;
    private String mReleaseText;
    private String mRefreshText;

    private int mArrowId = 0;
    private int mMessageLabelId = 0;
    private int mProgressIndicatorId = 0;

    private int mRefreshThresholdHeight = 0;
    private Callable mInitialSetup;

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

    private String getString(TypedArray ta, int attr, String defVal) {
        final String val = ta.getString(attr);
        if (val == null)
            return defVal;
        return val;
    }

    private void init(Context context, AttributeSet attrs) {
        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.PullToRefreshHeaderView);

        mArrowId = ta.getResourceId(R.styleable.PullToRefreshHeaderView_arrow, 0);
        mMessageLabelId = ta.getResourceId(R.styleable.PullToRefreshHeaderView_message_label, 0);
        mProgressIndicatorId = ta.getResourceId(R.styleable.PullToRefreshHeaderView_progress_indicator, 0);

        mRefreshThresholdHeight = ta.getDimensionPixelSize(R.styleable.PullToRefreshHeaderView_refresh_threshold_height, 0);

        mPullText = getString(ta, R.styleable.PullToRefreshHeaderView_message_pull, "Pull to refresh");
        mReleaseText = getString(ta, R.styleable.PullToRefreshHeaderView_message_release, "Release to refresh");
        mRefreshText = getString(ta, R.styleable.PullToRefreshHeaderView_message_refresh, "Refreshing...");

        ta.recycle();

        mAnimRotateUp = new RotateAnimation(0, -180f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        mAnimRotateUp.setDuration(ROTATE_ANIMATION_DURATION);
        mAnimRotateUp.setFillAfter(true);
        mAnimRotateDown = new RotateAnimation(-180f, 0, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        mAnimRotateDown.setDuration(ROTATE_ANIMATION_DURATION);
        mAnimRotateDown.setFillAfter(true);
    }

    public int getRefreshThresholdHeight() {
        return mRefreshThresholdHeight;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);

        if (mArrow == null) {
            mArrow = findViewById(mArrowId);
            mMessageLabel = (TextView)findViewById(mMessageLabelId);
            mProgressIndicator = (ProgressBar)findViewById(mProgressIndicatorId);

            try {
                mInitialSetup.call();
            } catch (Exception e) {
                // ignore exception
            }
        }
    }

    public void initViewWithSetup(Callable setup) {
        if (null != mArrow) {
            try {
                setup.call();
            } catch (Exception e) {
                // ignore exception
            }
            return;
        }

        mInitialSetup = setup;
    }

    @Override
    public void onStartPulling() {
        if (mProgressIndicator != null)
            mProgressIndicator.setVisibility(GONE);
        if (mArrow != null)
            mArrow.setVisibility(VISIBLE);
        if (mMessageLabel != null) {
            mMessageLabel.setVisibility(VISIBLE);
            mMessageLabel.setText(mPullText);
        }
    }

    @Override
    public void onEndPulling() {

    }

    /**
     * @param scrollY [screenHeight, 0]
     */
    @Override
    public void onPull(int scrollY) {
    }

    @Override
    public void onReachAboveHeaderViewHeight() {
        if (mProgressIndicator != null)
            mProgressIndicator.setVisibility(GONE);
        if (mMessageLabel != null)
            mMessageLabel.setText(mReleaseText);
        if (mArrow != null)
            mArrow.startAnimation(mAnimRotateUp);
    }

    @Override
    public void onReachBelowHeaderViewHeight() {
        if (mProgressIndicator != null)
            mProgressIndicator.setVisibility(GONE);
        if (mMessageLabel != null)
            mMessageLabel.setText(mPullText);
        if (mArrow != null)
            mArrow.startAnimation(mAnimRotateDown);
    }

    @Override
    public void onStartRefreshing() {
        if (mArrow != null) {
            mArrow.clearAnimation();
            mArrow.setVisibility(GONE);
        }
        if (mProgressIndicator != null)
            mProgressIndicator.setVisibility(VISIBLE);
        if (mMessageLabel != null)
            mMessageLabel.setText(mRefreshText);
    }

    @Override
    public void onEndRefreshing() {
        if (mProgressIndicator != null)
            mProgressIndicator.setVisibility(GONE);
        if (mMessageLabel != null)
            mMessageLabel.setVisibility(GONE);
    }

    @Override
    public void onBeforeEndRefreshing() {

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
