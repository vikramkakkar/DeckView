package com.appeaser.deckview.views;

import android.content.Context;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;

import com.appeaser.deckview.helpers.DeckViewConfig;
import com.appeaser.deckview.helpers.DeckViewSwipeHelper;
import com.appeaser.deckview.utilities.DVConstants;

/**
 * Created by Vikram on 02/04/2015.
 */
/* Handles touch events for a TaskStackView. */
public class DeckViewTouchHandler implements DeckViewSwipeHelper.Callback {
    static int INACTIVE_POINTER_ID = -1;

    DeckViewConfig mConfig;
    DeckView mDeckView;
    DeckViewScroller mScroller;
    VelocityTracker mVelocityTracker;

    boolean mIsScrolling;

    float mInitialP;
    float mLastP;
    float mTotalPMotion;
    int mInitialMotionX, mInitialMotionY;
    int mLastMotionX, mLastMotionY;
    int mActivePointerId = INACTIVE_POINTER_ID;
    DeckChildView mActiveDeckChildView = null;

    int mMinimumVelocity;
    int mMaximumVelocity;
    // The scroll touch slop is used to calculate when we start scrolling
    int mScrollTouchSlop;
    // The page touch slop is used to calculate when we start swiping
    float mPagingTouchSlop;

    DeckViewSwipeHelper mSwipeHelper;
    boolean mInterceptedBySwipeHelper;

    public DeckViewTouchHandler(Context context, DeckView dv,
                                DeckViewConfig config, DeckViewScroller scroller) {
        ViewConfiguration configuration = ViewConfiguration.get(context);
        mMinimumVelocity = configuration.getScaledMinimumFlingVelocity();
        mMaximumVelocity = configuration.getScaledMaximumFlingVelocity();
        mScrollTouchSlop = configuration.getScaledTouchSlop();
        mPagingTouchSlop = configuration.getScaledPagingTouchSlop();
        mDeckView = dv;
        mScroller = scroller;
        mConfig = config;

        float densityScale = context.getResources().getDisplayMetrics().density;
        mSwipeHelper = new DeckViewSwipeHelper(DeckViewSwipeHelper.X, this,
                densityScale, mPagingTouchSlop);
        mSwipeHelper.setMinAlpha(1f);
    }

    /**
     * Velocity tracker helpers
     */
    void initOrResetVelocityTracker() {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        } else {
            mVelocityTracker.clear();
        }
    }

    void initVelocityTrackerIfNotExists() {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
    }

    void recycleVelocityTracker() {
        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
    }

    /**
     * Returns the view at the specified coordinates
     */
    DeckChildView findViewAtPoint(int x, int y) {
        int childCount = mDeckView.getChildCount();
        for (int i = childCount - 1; i >= 0; i--) {
            DeckChildView tv = (DeckChildView) mDeckView.getChildAt(i);
            if (tv.getVisibility() == View.VISIBLE) {
                if (mDeckView.isTransformedTouchPointInView(x, y, tv)) {
                    return tv;
                }
            }
        }
        return null;
    }

    /**
     * Constructs a simulated motion event for the current stack scroll.
     */
    MotionEvent createMotionEventForStackScroll(MotionEvent ev) {
        MotionEvent pev = MotionEvent.obtainNoHistory(ev);
        pev.setLocation(0, mScroller.progressToScrollRange(mScroller.getStackScroll()));
        return pev;
    }

    /**
     * Touch preprocessing for handling below
     */
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        // Return early if we have no children
        boolean hasChildren = (mDeckView.getChildCount() > 0);
        if (!hasChildren) {
            return false;
        }

        // Pass through to swipe helper if we are swiping
        mInterceptedBySwipeHelper = mSwipeHelper.onInterceptTouchEvent(ev);
        if (mInterceptedBySwipeHelper) {
            return true;
        }

        boolean wasScrolling = mScroller.isScrolling() ||
                (mScroller.mScrollAnimator != null && mScroller.mScrollAnimator.isRunning());
        int action = ev.getAction();
        switch (action & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN: {
                // Save the touch down info
                mInitialMotionX = mLastMotionX = (int) ev.getX();
                mInitialMotionY = mLastMotionY = (int) ev.getY();
                mInitialP = mLastP = mDeckView.getStackAlgorithm().screenYToCurveProgress(mLastMotionY);
                mActivePointerId = ev.getPointerId(0);
                mActiveDeckChildView = findViewAtPoint(mLastMotionX, mLastMotionY);
                // Stop the current scroll if it is still flinging
                mScroller.stopScroller();
                mScroller.stopBoundScrollAnimation();
                // Initialize the velocity tracker
                initOrResetVelocityTracker();
                mVelocityTracker.addMovement(createMotionEventForStackScroll(ev));
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                if (mActivePointerId == INACTIVE_POINTER_ID) break;

                // Initialize the velocity tracker if necessary
                initVelocityTrackerIfNotExists();
                mVelocityTracker.addMovement(createMotionEventForStackScroll(ev));

                int activePointerIndex = ev.findPointerIndex(mActivePointerId);
                int y = (int) ev.getY(activePointerIndex);
                int x = (int) ev.getX(activePointerIndex);
                if (Math.abs(y - mInitialMotionY) > mScrollTouchSlop) {
                    // Save the touch move info
                    mIsScrolling = true;
                    // Disallow parents from intercepting touch events
                    final ViewParent parent = mDeckView.getParent();
                    if (parent != null) {
                        parent.requestDisallowInterceptTouchEvent(true);
                    }
                }

                mLastMotionX = x;
                mLastMotionY = y;
                mLastP = mDeckView.getStackAlgorithm().screenYToCurveProgress(mLastMotionY);
                break;
            }
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP: {
                // Animate the scroll back if we've cancelled
                mScroller.animateBoundScroll();
                // Reset the drag state and the velocity tracker
                mIsScrolling = false;
                mActivePointerId = INACTIVE_POINTER_ID;
                mActiveDeckChildView = null;
                mTotalPMotion = 0;
                recycleVelocityTracker();
                break;
            }
        }

        return wasScrolling || mIsScrolling;
    }

    /**
     * Handles touch events once we have intercepted them
     */
    public boolean onTouchEvent(MotionEvent ev) {
        // Short circuit if we have no children
        boolean hasChildren = (mDeckView.getChildCount() > 0);
        if (!hasChildren) {
            return false;
        }

        // Pass through to swipe helper if we are swiping
        if (mInterceptedBySwipeHelper && mSwipeHelper.onTouchEvent(ev)) {
            return true;
        }

        // Update the velocity tracker
        initVelocityTrackerIfNotExists();

        int action = ev.getAction();
        switch (action & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN: {
                // Save the touch down info
                mInitialMotionX = mLastMotionX = (int) ev.getX();
                mInitialMotionY = mLastMotionY = (int) ev.getY();
                mInitialP = mLastP = mDeckView.getStackAlgorithm().screenYToCurveProgress(mLastMotionY);
                mActivePointerId = ev.getPointerId(0);
                mActiveDeckChildView = findViewAtPoint(mLastMotionX, mLastMotionY);
                // Stop the current scroll if it is still flinging
                mScroller.stopScroller();
                mScroller.stopBoundScrollAnimation();
                // Initialize the velocity tracker
                initOrResetVelocityTracker();
                mVelocityTracker.addMovement(createMotionEventForStackScroll(ev));
                // Disallow parents from intercepting touch events
                final ViewParent parent = mDeckView.getParent();
                if (parent != null) {
                    parent.requestDisallowInterceptTouchEvent(true);
                }
                break;
            }
            case MotionEvent.ACTION_POINTER_DOWN: {
                final int index = ev.getActionIndex();
                mActivePointerId = ev.getPointerId(index);
                mLastMotionX = (int) ev.getX(index);
                mLastMotionY = (int) ev.getY(index);
                mLastP = mDeckView.getStackAlgorithm().screenYToCurveProgress(mLastMotionY);
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                if (mActivePointerId == INACTIVE_POINTER_ID) break;

                mVelocityTracker.addMovement(createMotionEventForStackScroll(ev));

                int activePointerIndex = ev.findPointerIndex(mActivePointerId);
                int x = (int) ev.getX(activePointerIndex);
                int y = (int) ev.getY(activePointerIndex);
                int yTotal = Math.abs(y - mInitialMotionY);
                float curP = mDeckView.getStackAlgorithm().screenYToCurveProgress(y);
                float deltaP = mLastP - curP;
                if (!mIsScrolling) {
                    if (yTotal > mScrollTouchSlop) {
                        mIsScrolling = true;
                        // Disallow parents from intercepting touch events
                        final ViewParent parent = mDeckView.getParent();
                        if (parent != null) {
                            parent.requestDisallowInterceptTouchEvent(true);
                        }
                    }
                }
                if (mIsScrolling) {
                    float curStackScroll = mScroller.getStackScroll();
                    float overScrollAmount = mScroller.getScrollAmountOutOfBounds(curStackScroll + deltaP);
                    if (Float.compare(overScrollAmount, 0f) != 0) {
                        // Bound the overscroll to a fixed amount, and inversely scale the y-movement
                        // relative to how close we are to the max overscroll
                        float maxOverScroll = mConfig.taskStackOverscrollPct;
                        deltaP *= (1f - (Math.min(maxOverScroll, overScrollAmount)
                                / maxOverScroll));
                    }
                    mScroller.setStackScroll(curStackScroll + deltaP);
                }
                mLastMotionX = x;
                mLastMotionY = y;
                mLastP = mDeckView.getStackAlgorithm().screenYToCurveProgress(mLastMotionY);
                mTotalPMotion += Math.abs(deltaP);
                break;
            }
            case MotionEvent.ACTION_UP: {
                mVelocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
                int velocity = (int) mVelocityTracker.getYVelocity(mActivePointerId);
                if (mIsScrolling && (Math.abs(velocity) > mMinimumVelocity)) {
                    float overscrollRangePct = Math.abs((float) velocity / mMaximumVelocity);
                    int overscrollRange = (int) (Math.min(1f, overscrollRangePct) *
                            (DVConstants.Values.DView.TaskStackMaxOverscrollRange -
                                    DVConstants.Values.DView.TaskStackMinOverscrollRange));
                    mScroller.mScroller.fling(0,
                            mScroller.progressToScrollRange(mScroller.getStackScroll()),
                            0, velocity,
                            0, 0,
                            mScroller.progressToScrollRange(mDeckView.getStackAlgorithm().mMinScrollP),
                            mScroller.progressToScrollRange(mDeckView.getStackAlgorithm().mMaxScrollP),
                            0, DVConstants.Values.DView.TaskStackMinOverscrollRange +
                                    overscrollRange);
                    // Invalidate to kick off computeScroll
                    mDeckView.invalidate();
                } else if (mScroller.isScrollOutOfBounds()) {
                    // Animate the scroll back into bounds
                    mScroller.animateBoundScroll();
                }

                mActivePointerId = INACTIVE_POINTER_ID;
                mIsScrolling = false;
                mTotalPMotion = 0;
                recycleVelocityTracker();
                break;
            }
            case MotionEvent.ACTION_POINTER_UP: {
                int pointerIndex = ev.getActionIndex();
                int pointerId = ev.getPointerId(pointerIndex);
                if (pointerId == mActivePointerId) {
                    // Select a new active pointer id and reset the motion state
                    final int newPointerIndex = (pointerIndex == 0) ? 1 : 0;
                    mActivePointerId = ev.getPointerId(newPointerIndex);
                    mLastMotionX = (int) ev.getX(newPointerIndex);
                    mLastMotionY = (int) ev.getY(newPointerIndex);
                    mLastP = mDeckView.getStackAlgorithm().screenYToCurveProgress(mLastMotionY);
                    mVelocityTracker.clear();
                }
                break;
            }
            case MotionEvent.ACTION_CANCEL: {
                if (mScroller.isScrollOutOfBounds()) {
                    // Animate the scroll back into bounds
                    mScroller.animateBoundScroll();
                }
                mActivePointerId = INACTIVE_POINTER_ID;
                mIsScrolling = false;
                mTotalPMotion = 0;
                recycleVelocityTracker();
                break;
            }
        }
        return true;
    }

    /**
     * Handles generic motion events
     */
    public boolean onGenericMotionEvent(MotionEvent ev) {
        if ((ev.getSource() & InputDevice.SOURCE_CLASS_POINTER) ==
                InputDevice.SOURCE_CLASS_POINTER) {
            int action = ev.getAction();
            switch (action & MotionEvent.ACTION_MASK) {
                case MotionEvent.ACTION_SCROLL:
                    // Find the front most task and scroll the next task to the front
                    float vScroll = ev.getAxisValue(MotionEvent.AXIS_VSCROLL);
                    if (vScroll > 0) {
                        if (mDeckView.ensureFocusedTask()) {
                            mDeckView.focusNextTask(true, false);
                        }
                    } else {
                        if (mDeckView.ensureFocusedTask()) {
                            mDeckView.focusNextTask(false, false);
                        }
                    }
                    return true;
            }
        }
        return false;
    }

    /**
     * * SwipeHelper Implementation ***
     */

    @Override
    public View getChildAtPosition(MotionEvent ev) {
        return findViewAtPoint((int) ev.getX(), (int) ev.getY());
    }

    @Override
    public boolean canChildBeDismissed(View v) {
        return true;
    }

    @Override
    public void onBeginDrag(View v) {
        DeckChildView tv = (DeckChildView) v;
        // Disable clipping with the stack while we are swiping
        tv.setClipViewInStack(false);
        // Disallow touch events from this task view
        tv.setTouchEnabled(false);
        // Disallow parents from intercepting touch events
        final ViewParent parent = mDeckView.getParent();
        if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(true);
        }
    }

    @Override
    public void onSwipeChanged(View v, float delta) {
        // Do nothing
    }

    @Override
    public void onChildDismissed(View v) {
        DeckChildView tv = (DeckChildView) v;
        // Re-enable clipping with the stack (we will reuse this view)
        tv.setClipViewInStack(true);
        // Re-enable touch events from this task view
        tv.setTouchEnabled(true);
        // Remove the task view from the stack
        mDeckView.onDeckChildViewDismissed(tv);
    }

    @Override
    public void onSnapBackCompleted(View v) {
        DeckChildView tv = (DeckChildView) v;
        // Re-enable clipping with the stack
        tv.setClipViewInStack(true);
        // Re-enable touch events from this task view
        tv.setTouchEnabled(true);
    }

    @Override
    public void onDragCancelled(View v) {
        // Do nothing
    }
}