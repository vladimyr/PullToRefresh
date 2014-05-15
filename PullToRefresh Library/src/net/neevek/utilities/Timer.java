package net.neevek.utilities;

import android.os.Handler;
import android.os.Looper;

/**
 * Created with IntelliJ IDEA.
 * User: Dario
 * Date: 15.5.2014.
 * Time: 11:29
 * To change this template use File | Settings | File Templates.
 */
public class Timer {
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private long mDelay;
    private Runnable mAction;

    public Timer(long delay) {
        mDelay = delay;
    }

    public Timer setAction(Runnable action) {
        mAction = action;
        return this;
    }

    public Timer start() {
        return restart();
    }

    public Timer restart() {
        mHandler.removeCallbacks(mAction);
        mHandler.postDelayed(mAction, mDelay);
        return this;
    }

    public Timer stop() {
        mHandler.removeCallbacks(mAction);
        return this;
    }
}
