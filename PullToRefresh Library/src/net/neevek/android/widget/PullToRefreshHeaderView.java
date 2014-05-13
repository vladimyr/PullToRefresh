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

    private View mArrow;
    private TextView mMessageLabel;
    private ProgressBar mProgressIndicator;

    private Animation mAnimRotateUp;
    private Animation mAnimRotateDown;

    private String mPullText = "Pull to refresh";
    private String mReleaseText = "Release to refresh";
    private String mRefreshText = "Refreshing...";

    private int mArrowId = 0;
    private int mMessageLabelId = 0;
    private int mProgressIndicatorId = 0;

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

        mArrowId = ta.getResourceId(R.styleable.PullToRefreshHeaderView_arrow, 0);
        mMessageLabelId = ta.getResourceId(R.styleable.PullToRefreshHeaderView_message_label, 0);
        mProgressIndicatorId = ta.getResourceId(R.styleable.PullToRefreshHeaderView_progress_indicator, 0);

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

        if (mArrow == null) {
            mArrow = findViewById(mArrowId);
            mMessageLabel = (TextView)findViewById(mMessageLabelId);
            mProgressIndicator = (ProgressBar)findViewById(mProgressIndicatorId);
        }
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
