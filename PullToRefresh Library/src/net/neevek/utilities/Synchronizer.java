package net.neevek.utilities;

import java.util.ArrayList;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: Dario
 * Date: 15.5.2014.
 * Time: 11:17
 * To change this template use File | Settings | File Templates.
 */
public class Synchronizer {
    public interface SynchronizationObserver {
        public void onSynced(Synchronizer synchronizer);
    }

    private int mCount;
    private int mChecks;
    public List<SynchronizationObserver> mObservers = new ArrayList<SynchronizationObserver>();

    public Synchronizer(int val) {
        mCount = mChecks = val;
    }

    public void recycle() {
        mChecks = mCount;
    }

    public Synchronizer(int val, SynchronizationObserver observer) {
        this(val);
        mObservers.add(observer);
    }

    public void notifySync() {
        if (mChecks <= 0)
            return;

        mChecks--;

        if (mChecks == 0) {
            for(SynchronizationObserver observer : mObservers)
                observer.onSynced(this);
        }
    }

    public void addObserver(SynchronizationObserver observer) {
        mObservers.add(observer);
    }

    public void removeAllObservers() {
        mObservers.clear();
    }
}
