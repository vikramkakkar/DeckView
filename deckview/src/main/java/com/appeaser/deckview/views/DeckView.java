package com.appeaser.deckview.views;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.accessibility.AccessibilityEvent;
import android.widget.FrameLayout;

import com.appeaser.deckview.R;
import com.appeaser.deckview.helpers.DeckChildViewTransform;
import com.appeaser.deckview.helpers.DeckViewConfig;
import com.appeaser.deckview.utilities.DVConstants;
import com.appeaser.deckview.utilities.DVUtils;
import com.appeaser.deckview.utilities.DozeTrigger;
import com.appeaser.deckview.utilities.ReferenceCountedTrigger;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

/**
 * Created by Vikram on 02/04/2015.
 */
/* The visual representation of a task stack view */
public class DeckView<T> extends FrameLayout implements /*TaskStack.TaskStackCallbacks,*/
        DeckChildView.DeckChildViewCallbacks<T>, DeckViewScroller.DeckViewScrollerCallbacks,
        ViewPool.ViewPoolConsumer<DeckChildView<T>, T> {

    DeckViewConfig mConfig;

    DeckViewLayoutAlgorithm<T> mLayoutAlgorithm;
    DeckViewScroller mStackScroller;
    DeckViewTouchHandler mTouchHandler;
    ViewPool<DeckChildView<T>, T> mViewPool;
    ArrayList<DeckChildViewTransform> mCurrentTaskTransforms = new ArrayList<DeckChildViewTransform>();
    DozeTrigger mUIDozeTrigger;
    Rect mTaskStackBounds = new Rect();
    int mFocusedTaskIndex = -1;
    int mPrevAccessibilityFocusedIndex = -1;

    // Optimizations
    int mStackViewsAnimationDuration;
    boolean mStackViewsDirty = true;
    boolean mStackViewsClipDirty = true;
    boolean mAwaitingFirstLayout = true;
    boolean mStartEnterAnimationRequestedAfterLayout;
    boolean mStartEnterAnimationCompleted;
    ViewAnimation.TaskViewEnterContext mStartEnterAnimationContext;
    int[] mTmpVisibleRange = new int[2];
    float[] mTmpCoord = new float[2];
    Matrix mTmpMatrix = new Matrix();
    Rect mTmpRect = new Rect();
    DeckChildViewTransform mTmpTransform = new DeckChildViewTransform();
    HashMap<T, DeckChildView> mTmpTaskViewMap = new HashMap<T, DeckChildView>();
    LayoutInflater mInflater;

    // A convenience update listener to request updating clipping of tasks
    ValueAnimator.AnimatorUpdateListener mRequestUpdateClippingListener =
            new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    requestUpdateStackViewsClip();
                }
            };

    public DeckView(Context context) {
        this(context, null);
    }

    public DeckView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DeckView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public DeckView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        DeckViewConfig.reinitialize(getContext());
        mConfig = DeckViewConfig.getInstance();
    }

    public void initialize(Callback<T> callback) {
        mCallback = callback;
        requestLayout();

        mViewPool = new ViewPool<DeckChildView<T>, T>(getContext(), this);
        mInflater = LayoutInflater.from(getContext());
        mLayoutAlgorithm = new DeckViewLayoutAlgorithm<T>(mConfig);
        mStackScroller = new DeckViewScroller(getContext(), mConfig, mLayoutAlgorithm);
        mStackScroller.setCallbacks(this);
        mTouchHandler = new DeckViewTouchHandler(getContext(), this, mConfig, mStackScroller);

        mUIDozeTrigger = new DozeTrigger(mConfig.taskBarDismissDozeDelaySeconds, new Runnable() {
            @Override
            public void run() {
                // Show the task bar dismiss buttons
                int childCount = getChildCount();
                for (int i = 0; i < childCount; i++) {
                    DeckChildView tv = (DeckChildView) getChildAt(i);
                    tv.startNoUserInteractionAnimation();
                }
            }
        });
    }

    /**
     * Resets this TaskStackView for reuse.
     */
    void reset() {
        // Reset the focused task
        resetFocusedTask();

        // Return all the views to the pool
        int childCount = getChildCount();
        for (int i = childCount - 1; i >= 0; i--) {
            DeckChildView<T> tv = (DeckChildView) getChildAt(i);
            mViewPool.returnViewToPool(tv);
        }

        // Mark each task view for relayout
        if (mViewPool != null) {
            Iterator<DeckChildView<T>> iter = mViewPool.poolViewIterator();
            if (iter != null) {
                while (iter.hasNext()) {
                    DeckChildView tv = iter.next();
                    tv.reset();
                }
            }
        }

        // Reset the stack state
        mStackViewsDirty = true;
        mStackViewsClipDirty = true;
        mAwaitingFirstLayout = true;
        mPrevAccessibilityFocusedIndex = -1;
        if (mUIDozeTrigger != null) {
            mUIDozeTrigger.stopDozing();
            mUIDozeTrigger.resetTrigger();
        }
        mStackScroller.reset();
    }

    /**
     * Requests that the views be synchronized with the model
     */
    void requestSynchronizeStackViewsWithModel() {
        requestSynchronizeStackViewsWithModel(0);
    }

    void requestSynchronizeStackViewsWithModel(int duration) {
        if (!mStackViewsDirty) {
            invalidate();
            mStackViewsDirty = true;
        }
        if (mAwaitingFirstLayout) {
            // Skip the animation if we are awaiting first layout
            mStackViewsAnimationDuration = 0;
        } else {
            mStackViewsAnimationDuration = Math.max(mStackViewsAnimationDuration, duration);
        }
    }

    /**
     * Requests that the views clipping be updated.
     */
    void requestUpdateStackViewsClip() {
        if (!mStackViewsClipDirty) {
            invalidate();
            mStackViewsClipDirty = true;
        }
    }

    /**
     * Finds the child view given a specific task.
     */
    public DeckChildView getChildViewForTask(T key) {
        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            DeckChildView tv = (DeckChildView) getChildAt(i);
            if (tv.getAttachedKey().equals(key)) {
                return tv;
            }
        }
        return null;
    }

    /**
     * Returns the stack algorithm for this task stack.
     */
    public DeckViewLayoutAlgorithm getStackAlgorithm() {
        return mLayoutAlgorithm;
    }

    /**
     * Gets the stack transforms of a list of tasks, and returns the visible range of tasks.
     */
    private boolean updateStackTransforms(ArrayList<DeckChildViewTransform> taskTransforms,
                                          ArrayList<T> data,
                                          float stackScroll,
                                          int[] visibleRangeOut,
                                          boolean boundTranslationsToRect) {
        int taskTransformCount = taskTransforms.size();
        int taskCount = data.size();
        int frontMostVisibleIndex = -1;
        int backMostVisibleIndex = -1;

        // We can reuse the task transforms where possible to reduce object allocation
        if (taskTransformCount < taskCount) {
            // If there are less transforms than tasks, then add as many transforms as necessary
            for (int i = taskTransformCount; i < taskCount; i++) {
                taskTransforms.add(new DeckChildViewTransform());
            }
        } else if (taskTransformCount > taskCount) {
            // If there are more transforms than tasks, then just subset the transform list
            taskTransforms.subList(0, taskCount);
        }

        // Update the stack transforms
        DeckChildViewTransform prevTransform = null;
        for (int i = taskCount - 1; i >= 0; i--) {
            DeckChildViewTransform transform =
                    mLayoutAlgorithm.getStackTransform(data.get(i),
                            stackScroll, taskTransforms.get(i), prevTransform);
            if (transform.visible) {
                if (frontMostVisibleIndex < 0) {
                    frontMostVisibleIndex = i;
                }
                backMostVisibleIndex = i;
            } else {
                if (backMostVisibleIndex != -1) {
                    // We've reached the end of the visible range, so going down the rest of the
                    // stack, we can just reset the transforms accordingly
                    while (i >= 0) {
                        taskTransforms.get(i).reset();
                        i--;
                    }
                    break;
                }
            }

            if (boundTranslationsToRect) {
                transform.translationY = Math.min(transform.translationY,
                        mLayoutAlgorithm.mViewRect.bottom);
            }
            prevTransform = transform;
        }
        if (visibleRangeOut != null) {
            visibleRangeOut[0] = frontMostVisibleIndex;
            visibleRangeOut[1] = backMostVisibleIndex;
        }
        return frontMostVisibleIndex != -1 && backMostVisibleIndex != -1;
    }

    /**
     * Synchronizes the views with the model
     */
    boolean synchronizeStackViewsWithModel() {
        if (mStackViewsDirty) {
            // Get all the task transforms
            ArrayList<T> data = mCallback.getData();
            float stackScroll = mStackScroller.getStackScroll();
            int[] visibleRange = mTmpVisibleRange;
            boolean isValidVisibleRange = updateStackTransforms(mCurrentTaskTransforms,
                    data, stackScroll, visibleRange, false);

            // Return all the invisible children to the pool
            mTmpTaskViewMap.clear();
            int childCount = getChildCount();
            for (int i = childCount - 1; i >= 0; i--) {
                DeckChildView<T> tv = (DeckChildView) getChildAt(i);
                T key = tv.getAttachedKey();
                int taskIndex = data.indexOf(key);

                if (visibleRange[1] <= taskIndex
                        && taskIndex <= visibleRange[0]) {
                    mTmpTaskViewMap.put(key, tv);
                } else {
                    mViewPool.returnViewToPool(tv);
                }
            }

            for (int i = visibleRange[0]; isValidVisibleRange && i >= visibleRange[1]; i--) {
                T key = data.get(i);
                DeckChildViewTransform transform = mCurrentTaskTransforms.get(i);
                DeckChildView tv = mTmpTaskViewMap.get(key);

                if (tv == null) {
                    // TODO Check
                    tv = mViewPool.pickUpViewFromPool(key, key);

                    if (mStackViewsAnimationDuration > 0) {
                        // For items in the list, put them in start animating them from the
                        // approriate ends of the list where they are expected to appear
                        if (Float.compare(transform.p, 0f) <= 0) {
                            mLayoutAlgorithm.getStackTransform(0f, 0f, mTmpTransform, null);
                        } else {
                            mLayoutAlgorithm.getStackTransform(1f, 0f, mTmpTransform, null);
                        }
                        tv.updateViewPropertiesToTaskTransform(mTmpTransform, 0);
                    }
                }

                // Animate the task into place
                tv.updateViewPropertiesToTaskTransform(mCurrentTaskTransforms.get(i),
                        mStackViewsAnimationDuration, mRequestUpdateClippingListener);
            }

            // Reset the request-synchronize params
            mStackViewsAnimationDuration = 0;
            mStackViewsDirty = false;
            mStackViewsClipDirty = true;
            return true;
        }
        return false;
    }

    /**
     * Updates the clip for each of the task views.
     */
    void clipTaskViews() {
        // Update the clip on each task child
        if (DVConstants.DebugFlags.App.EnableTaskStackClipping) {
            int childCount = getChildCount();
            for (int i = 0; i < childCount - 1; i++) {
                DeckChildView tv = (DeckChildView) getChildAt(i);
                DeckChildView nextTv = null;
                DeckChildView tmpTv = null;
                int clipBottom = 0;
                if (tv.shouldClipViewInStack()) {
                    // Find the next view to clip against
                    int nextIndex = i;
                    while (nextIndex < getChildCount()) {
                        tmpTv = (DeckChildView) getChildAt(++nextIndex);
                        if (tmpTv != null && tmpTv.shouldClipViewInStack()) {
                            nextTv = tmpTv;
                            break;
                        }
                    }

                    // Clip against the next view, this is just an approximation since we are
                    // stacked and we can make assumptions about the visibility of the this
                    // task relative to the ones in front of it.
                    if (nextTv != null) {
                        // Map the top edge of next task view into the local space of the current
                        // task view to find the clip amount in local space
                        mTmpCoord[0] = mTmpCoord[1] = 0;
                        DVUtils.mapCoordInDescendentToSelf(nextTv, this, mTmpCoord, false);
                        DVUtils.mapCoordInSelfToDescendent(tv, this, mTmpCoord, mTmpMatrix);
                        clipBottom = (int) Math.floor(tv.getMeasuredHeight() - mTmpCoord[1]
                                - nextTv.getPaddingTop() - 1);
                    }
                }
                tv.getViewBounds().setClipBottom(clipBottom);
            }
            if (getChildCount() > 0) {
                // The front most task should never be clipped
                DeckChildView tv = (DeckChildView) getChildAt(getChildCount() - 1);
                tv.getViewBounds().setClipBottom(0);
            }
        }
        mStackViewsClipDirty = false;
    }

    /**
     * The stack insets to apply to the stack contents
     */
    public void setStackInsetRect(Rect r) {
        mTaskStackBounds.set(r);
    }

    /**
     * Updates the min and max virtual scroll bounds
     */
    void updateMinMaxScroll(boolean boundScrollToNewMinMax, boolean launchedWithAltTab,
                            boolean launchedFromHome) {
        // Compute the min and max scroll values
        mLayoutAlgorithm.computeMinMaxScroll(mCallback.getData(), launchedWithAltTab, launchedFromHome);

        // Debug logging
        if (boundScrollToNewMinMax) {
            mStackScroller.boundScroll();
        }
    }

    /**
     * Returns the scroller.
     */
    public DeckViewScroller getScroller() {
        return mStackScroller;
    }

    /**
     * Focuses the task at the specified index in the stack
     */
    void focusTask(int childIndex, boolean scrollToNewPosition, final boolean animateFocusedState) {
        // Return early if the task is already focused
        if (childIndex == mFocusedTaskIndex) return;

        ArrayList<T> data = mCallback.getData();

        if (0 <= childIndex && childIndex < data.size()) {
            mFocusedTaskIndex = childIndex;

            // Focus the view if possible, otherwise, focus the view after we scroll into position
            T key = data.get(childIndex);
            DeckChildView tv = getChildViewForTask(key);
            Runnable postScrollRunnable = null;
            if (tv != null) {
                tv.setFocusedTask(animateFocusedState);
            } else {
                postScrollRunnable = new Runnable() {
                    @Override
                    public void run() {

                        DeckChildView tv = getChildViewForTask(mCallback.getData().get(mFocusedTaskIndex));
                        if (tv != null) {
                            tv.setFocusedTask(animateFocusedState);
                        }
                    }
                };
            }

            // Scroll the view into position (just center it in the curve)
            if (scrollToNewPosition) {
                float newScroll = mLayoutAlgorithm.getStackScrollForTask(key) - 0.5f;
                newScroll = mStackScroller.getBoundedStackScroll(newScroll);
                mStackScroller.animateScroll(mStackScroller.getStackScroll(), newScroll, postScrollRunnable);
            } else {
                if (postScrollRunnable != null) {
                    postScrollRunnable.run();
                }
            }

        }
    }

    /**
     * Ensures that there is a task focused, if nothing is focused, then we will use the task
     * at the center of the visible stack.
     */
    public boolean ensureFocusedTask() {
        if (mFocusedTaskIndex < 0) {
            // If there is no task focused, then find the task that is closes to the center
            // of the screen and use that as the currently focused task
            int x = mLayoutAlgorithm.mStackVisibleRect.centerX();
            int y = mLayoutAlgorithm.mStackVisibleRect.centerY();
            int childCount = getChildCount();
            for (int i = childCount - 1; i >= 0; i--) {
                DeckChildView tv = (DeckChildView) getChildAt(i);
                tv.getHitRect(mTmpRect);
                if (mTmpRect.contains(x, y)) {
                    mFocusedTaskIndex = i;
                    break;
                }
            }
            // If we can't find the center task, then use the front most index
            if (mFocusedTaskIndex < 0 && childCount > 0) {
                mFocusedTaskIndex = childCount - 1;
            }
        }
        return mFocusedTaskIndex >= 0;
    }

    /**
     * Focuses the next task in the stack.
     *
     * @param animateFocusedState determines whether to actually draw the highlight along with
     *                            the change in focus, as well as whether to scroll to fit the
     *                            task into view.
     */
    public void focusNextTask(boolean forward, boolean animateFocusedState) {
        // Find the next index to focus
        int numTasks = mCallback.getData().size();
        if (numTasks == 0) return;

        int direction = (forward ? -1 : 1);
        int newIndex = mFocusedTaskIndex + direction;
        if (newIndex >= 0 && newIndex <= (numTasks - 1)) {
            newIndex = Math.max(0, Math.min(numTasks - 1, newIndex));
            focusTask(newIndex, true, animateFocusedState);
        }
    }

    /**
     * Dismisses the focused task.
     */
    public void dismissFocusedTask() {
        // Return early if the focused task index is invalid
        if (mFocusedTaskIndex < 0 || mFocusedTaskIndex >= mCallback.getData().size()) {
            mFocusedTaskIndex = -1;
            return;
        }

        //Long id = mAdapter.getItemId(mFocusedTaskIndex);
        T key = mCallback.getData().get(mFocusedTaskIndex);
        DeckChildView tv = getChildViewForTask(key);
        tv.dismissTask();
    }

    /**
     * Resets the focused task.
     */
    void resetFocusedTask() {
        if ((0 <= mFocusedTaskIndex) && (mFocusedTaskIndex < mCallback.getData().size())) {
            DeckChildView tv = getChildViewForTask(mCallback.getData().get(mFocusedTaskIndex));
            if (tv != null) {
                tv.unsetFocusedTask();
            }
        }
        mFocusedTaskIndex = -1;
    }

    @Override
    public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
        super.onInitializeAccessibilityEvent(event);
        int childCount = getChildCount();
        if (childCount > 0) {
            DeckChildView<T> backMostTask = (DeckChildView) getChildAt(0);
            DeckChildView<T> frontMostTask = (DeckChildView) getChildAt(childCount - 1);
            event.setFromIndex(mCallback.getData().indexOf(backMostTask.getAttachedKey()));
            event.setToIndex(mCallback.getData().indexOf(frontMostTask.getAttachedKey()));
        }
        event.setItemCount(mCallback.getData().size());
        event.setScrollY(mStackScroller.mScroller.getCurrY());
        event.setMaxScrollY(mStackScroller.progressToScrollRange(mLayoutAlgorithm.mMaxScrollP));
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return mTouchHandler.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        return mTouchHandler.onTouchEvent(ev);
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent ev) {
        return mTouchHandler.onGenericMotionEvent(ev);
    }

    @Override
    public void computeScroll() {
        mStackScroller.computeScroll();
        // Synchronize the views
        synchronizeStackViewsWithModel();
        clipTaskViews();
        // Notify accessibility
        sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SCROLLED);
    }

    /**
     * Computes the stack and task rects
     */
    public void computeRects(int windowWidth, int windowHeight, Rect taskStackBounds,
                             boolean launchedWithAltTab, boolean launchedFromHome) {
        // Compute the rects in the stack algorithm
        mLayoutAlgorithm.computeRects(windowWidth, windowHeight, taskStackBounds);

        // Update the scroll bounds
        updateMinMaxScroll(false, launchedWithAltTab, launchedFromHome);
    }

    public int getCurrentChildIndex() {
        if (getChildCount() == 0)
            return -1;

        DeckChildView<T> frontMostChild = (DeckChildView) getChildAt(getChildCount() / 2);

        if (frontMostChild != null) {
            return mCallback.getData().indexOf(frontMostChild.getAttachedKey());
        }

        return -1;
    }

    /**
     * Focuses the task at the specified index in the stack
     */
    public void scrollToChild(int childIndex) {
        if (getCurrentChildIndex() == childIndex)
            return;

        if (0 <= childIndex && childIndex < mCallback.getData().size()) {
            // Scroll the view into position (just center it in the curve)
            float newScroll = mLayoutAlgorithm.getStackScrollForTask(
                    mCallback.getData().get(childIndex)) - 0.5f;
            newScroll = mStackScroller.getBoundedStackScroll(newScroll);
            mStackScroller.setStackScroll(newScroll);
            //Alternate (animated) way
            //mStackScroller.animateScroll(mStackScroller.getStackScroll(), newScroll, null);
        }
    }

    /**
     * Computes the maximum number of visible tasks and thumbnails.  Requires that
     * updateMinMaxScrollForStack() is called first.
     */
    public DeckViewLayoutAlgorithm.VisibilityReport computeStackVisibilityReport() {
        return mLayoutAlgorithm.computeStackVisibilityReport(mCallback.getData());
    }

    /**
     * This is called with the full window width and height to allow stack view children to
     * perform the full screen transition down.
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);

        Rect _taskStackBounds = new Rect();
        mConfig.getTaskStackBounds(width, height, mConfig.systemInsets.top,
                mConfig.systemInsets.right, _taskStackBounds);

        setStackInsetRect(_taskStackBounds);

        // Compute our stack/task rects
        Rect taskStackBounds = new Rect(mTaskStackBounds);
        taskStackBounds.bottom -= mConfig.systemInsets.bottom;
        computeRects(width, height, taskStackBounds, mConfig.launchedWithAltTab,
                mConfig.launchedFromHome);

        // If this is the first layout, then scroll to the front of the stack and synchronize the
        // stack views immediately to load all the views
        if (mAwaitingFirstLayout) {
            mStackScroller.setStackScrollToInitialState();
            requestSynchronizeStackViewsWithModel();
            synchronizeStackViewsWithModel();
        }

        // Measure each of the TaskViews
        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            DeckChildView tv = (DeckChildView) getChildAt(i);
            if (tv.getBackground() != null) {
                tv.getBackground().getPadding(mTmpRect);
            } else {
                mTmpRect.setEmpty();
            }
            tv.measure(
                    MeasureSpec.makeMeasureSpec(
                            mLayoutAlgorithm.mTaskRect.width() + mTmpRect.left + mTmpRect.right,
                            MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(
                            mLayoutAlgorithm.mTaskRect.height() + mTmpRect.top + mTmpRect.bottom,
                            MeasureSpec.EXACTLY));
        }

        setMeasuredDimension(width, height);
    }

    /**
     * This is called with the size of the space not including the top or right insets, or the
     * search bar height in portrait (but including the search bar width in landscape, since we want
     * to draw under it.
     */
    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        // Layout each of the children
        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            DeckChildView tv = (DeckChildView) getChildAt(i);
            if (tv.getBackground() != null) {
                tv.getBackground().getPadding(mTmpRect);
            } else {
                mTmpRect.setEmpty();
            }
            tv.layout(mLayoutAlgorithm.mTaskRect.left - mTmpRect.left,
                    mLayoutAlgorithm.mTaskRect.top - mTmpRect.top,
                    mLayoutAlgorithm.mTaskRect.right + mTmpRect.right,
                    mLayoutAlgorithm.mTaskRect.bottom + mTmpRect.bottom);
        }

        if (mAwaitingFirstLayout) {
            mAwaitingFirstLayout = false;
            onFirstLayout();
        }
    }

    /**
     * Handler for the first layout.
     */
    void onFirstLayout() {
        int offscreenY = mLayoutAlgorithm.mViewRect.bottom -
                (mLayoutAlgorithm.mTaskRect.top - mLayoutAlgorithm.mViewRect.top);

        int childCount = getChildCount();

        // Prepare the first view for its enter animation
        for (int i = childCount - 1; i >= 0; i--) {
            DeckChildView tv = (DeckChildView) getChildAt(i);
            // TODO: The false needs to go!
            tv.prepareEnterRecentsAnimation(i == childCount - 1, false, offscreenY);
        }

        // If the enter animation started already and we haven't completed a layout yet, do the
        // enter animation now
        if (mStartEnterAnimationRequestedAfterLayout) {
            startEnterRecentsAnimation(mStartEnterAnimationContext);
            mStartEnterAnimationRequestedAfterLayout = false;
            mStartEnterAnimationContext = null;
        }

        // When Alt-Tabbing, focus the previous task (but leave the animation until we finish the
        // enter animation).
        if (mConfig.launchedWithAltTab) {
            if (mConfig.launchedFromAppWithThumbnail) {
                focusTask(Math.max(0, mCallback.getData().size() - 2), false,
                        mConfig.launchedHasConfigurationChanged);
            } else {
                focusTask(Math.max(0, mCallback.getData().size() - 1), false,
                        mConfig.launchedHasConfigurationChanged);
            }
        }

        // Start dozing
        mUIDozeTrigger.startDozing();
    }

    void showDeck(Context context) {
        // Try and start the enter animation (or restart it on configuration changed)
        ReferenceCountedTrigger t = new ReferenceCountedTrigger(context, null, null, null);
        ViewAnimation.TaskViewEnterContext ctx = new ViewAnimation.TaskViewEnterContext(t);

        // We have to increment/decrement the post animation trigger in case there are no children
        // to ensure that it runs
        ctx.postAnimationTrigger.increment();
        startEnterRecentsAnimation(ctx);
        ctx.postAnimationTrigger.decrement();
    }

    /**
     * Requests this task stacks to start it's enter-recents animation
     */
    public void startEnterRecentsAnimation(ViewAnimation.TaskViewEnterContext ctx) {
        // If we are still waiting to layout, then just defer until then
        if (mAwaitingFirstLayout) {
            mStartEnterAnimationRequestedAfterLayout = true;
            mStartEnterAnimationContext = ctx;
            return;
        }

        if (mCallback.getData().size() > 0) {
            int childCount = getChildCount();

            // Animate all the task views into view
            for (int i = childCount - 1; i >= 0; i--) {
                DeckChildView<T> tv = (DeckChildView) getChildAt(i);
                T key = tv.getAttachedKey();
                ctx.currentTaskTransform = new DeckChildViewTransform();
                ctx.currentStackViewIndex = i;
                ctx.currentStackViewCount = childCount;
                ctx.currentTaskRect = mLayoutAlgorithm.mTaskRect;
                // TODO: this needs to go
                ctx.currentTaskOccludesLaunchTarget = false;
                ctx.updateListener = mRequestUpdateClippingListener;
                mLayoutAlgorithm.getStackTransform(key, mStackScroller.getStackScroll(),
                        ctx.currentTaskTransform, null);
                tv.startEnterRecentsAnimation(ctx);
            }

            // Add a runnable to the post animation ref counter to clear all the views
            ctx.postAnimationTrigger.addLastDecrementRunnable(new Runnable() {
                @Override
                public void run() {
                    mStartEnterAnimationCompleted = true;
                    // Poke the dozer to restart the trigger after the animation completes
                    mUIDozeTrigger.poke();
                }
            });
        }
    }

    @Override
    public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        // Update the configuration with the latest system insets and trigger a relayout
        // mConfig.updateSystemInsets(insets.getSystemWindowInsets());
        mConfig.updateSystemInsets(new Rect(insets.getSystemWindowInsetLeft(),
                insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(),
                insets.getSystemWindowInsetBottom()));
        requestLayout();
        return insets.consumeSystemWindowInsets();
    }

    void hideDeck(Context context, Runnable finishRunnable) {
        ReferenceCountedTrigger exitTrigger = new ReferenceCountedTrigger(context,
                null, finishRunnable, null);
        ViewAnimation.TaskViewExitContext exitCtx =
                new ViewAnimation.TaskViewExitContext(exitTrigger);

        exitCtx.postAnimationTrigger.increment();
        startExitToHomeAnimation(
                new ViewAnimation.TaskViewExitContext(exitTrigger));
        exitCtx.postAnimationTrigger.decrement();
    }

    /**
     * Requests this task stacks to start it's exit-recents animation.
     */
    public void startExitToHomeAnimation(ViewAnimation.TaskViewExitContext ctx) {
        // Stop any scrolling
        mStackScroller.stopScroller();
        mStackScroller.stopBoundScrollAnimation();
        // Animate all the task views out of view
        ctx.offscreenTranslationY = mLayoutAlgorithm.mViewRect.bottom -
                (mLayoutAlgorithm.mTaskRect.top - mLayoutAlgorithm.mViewRect.top);
        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            DeckChildView tv = (DeckChildView) getChildAt(i);
            tv.startExitToHomeAnimation(ctx);
        }
    }

    /**
     * Animates a task view in this stack as it launches.
     */
    public void startLaunchTaskAnimation(DeckChildView tv, Runnable r, boolean lockToTask) {
        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            DeckChildView t = (DeckChildView) getChildAt(i);
            if (t == tv) {
                t.setClipViewInStack(false);
                t.startLaunchTaskAnimation(r, true, true, lockToTask);
            } else {
                // TODO: the false needs to go
                t.startLaunchTaskAnimation(null, false, false, lockToTask);
            }
        }
    }

    /**
     * Final callback after Recents is finally hidden.
     */
    void onRecentsHidden() {
        reset();
    }

    public boolean isTransformedTouchPointInView(float x, float y, View child) {
        // TODO: confirm if this is the right approach
        if (child == null)
            return false;

        final Rect frame = new Rect();
        child.getHitRect(frame);

        return frame.contains((int) x, (int) y);
    }

    /**
     * Pokes the dozer on user interaction.
     */
    void onUserInteraction() {
        // Poke the doze trigger if it is dozing
        mUIDozeTrigger.poke();
    }

    /**
     * * ViewPoolConsumer Implementation ***
     */

    @Override
    public DeckChildView createView(Context context) {
        return (DeckChildView) mInflater.inflate(R.layout.deck_child_view, this, false);
    }

    @Override
    public void prepareViewToEnterPool(DeckChildView<T> tv) {
        T key = tv.getAttachedKey();

        mCallback.unloadViewData(key);
        tv.onTaskUnbound();
        tv.onDataUnloaded();

        // Detach the view from the hierarchy
        detachViewFromParent(tv);

        // Reset the view properties
        tv.resetViewProperties();

        // Reset the clip state of the task view
        tv.setClipViewInStack(false);
    }

    @Override
    public void prepareViewToLeavePool(DeckChildView<T> dcv, T key, boolean isNewView) {
        // It is possible for a view to be returned to the view pool before it is laid out,
        // which means that we will need to relayout the view when it is first used next.
        boolean requiresRelayout = dcv.getWidth() <= 0 && !isNewView;

        // Rebind the task and request that this task's data be filled into the TaskView
        dcv.onTaskBound(key);

        // Load the task data
        mCallback.loadViewData(new WeakReference<DeckChildView<T>>(dcv), key);

        // If the doze trigger has already fired, then update the state for this task view
        if (mUIDozeTrigger.hasTriggered()) {
            dcv.setNoUserInteractionState();
        }

        // If we've finished the start animation, then ensure we always enable the focus animations
        if (mStartEnterAnimationCompleted) {
            dcv.enableFocusAnimations();
        }

        // Find the index where this task should be placed in the stack
        int insertIndex = -1;
        int position = mCallback.getData().indexOf(key);
        if (position != -1) {
            int childCount = getChildCount();
            for (int i = 0; i < childCount; i++) {
                T otherKey = ((DeckChildView<T>) getChildAt(i)).getAttachedKey();
                int pos = mCallback.getData().indexOf(otherKey);
                if (position < pos) {
                    insertIndex = i;
                    break;
                }
            }
        }


        // Add/attach the view to the hierarchy
        if (isNewView) {
            addView(dcv, insertIndex);
        } else {
            attachViewToParent(dcv, insertIndex, dcv.getLayoutParams());
            if (requiresRelayout) {
                dcv.requestLayout();
            }
        }

        // Set the new state for this view, including the callbacks and view clipping
        dcv.setCallbacks(this);
        dcv.setTouchEnabled(true);
        dcv.setClipViewInStack(true);
    }

    @Override
    public boolean hasPreferredData(DeckChildView<T> tv, T preferredData) {
        return (tv.getAttachedKey() != null && tv.getAttachedKey().equals(preferredData));
    }

    /**
     * * DeckChildCallbacks Implementation ***
     */

    @Override
    public void onDeckChildViewAppIconClicked(DeckChildView tv) {
        //
    }

    @Override
    public void onDeckChildViewAppInfoClicked(DeckChildView tv) {
        //
    }

    @Override
    public void onDeckChildViewClicked(DeckChildView<T> dcv, T key) {
        // Cancel any doze triggers
        mUIDozeTrigger.stopDozing();
        mCallback.onItemClick(key);
    }

    @Override
    public void onDeckChildViewDismissed(DeckChildView<T> dcv) {
        boolean taskWasFocused = dcv.isFocusedTask();

        T key = dcv.getAttachedKey();
        int taskIndex = mCallback.getData().indexOf(key);

        onStackTaskRemoved(dcv);

        // If the dismissed task was focused, then we should focus the new task in the same index
        if (taskIndex != -1 && taskWasFocused) {
            int nextTaskIndex = Math.min(mCallback.getData().size() - 1, taskIndex - 1);
            if (nextTaskIndex >= 0) {
                DeckChildView nextTv = getChildViewForTask(mCallback.getData().get(nextTaskIndex));
                if (nextTv != null) {
                    // Focus the next task, and only animate the visible state if we are launched
                    // from Alt-Tab
                    nextTv.setFocusedTask(mConfig.launchedWithAltTab);
                }
            }
        }
    }

    public void onStackTaskRemoved(DeckChildView<T> removedView) {
        // Remove the view associated with this task, we can't rely on updateTransforms
        // to work here because the task is no longer in the list
        if (removedView != null) {
            T key = removedView.getAttachedKey();
            int removedPosition = mCallback.getData().indexOf(key);
            mViewPool.returnViewToPool(removedView);

            // Notify the callback that we've removed the task and it can clean up after it
            mCallback.onViewDismissed(key);
        }

        /*
        // Get the stack scroll of the task to anchor to (since we are removing something, the front
        // most task will be our anchor task)
        T anchorTask = null;
        float prevAnchorTaskScroll = 0;
        boolean pullStackForward = mCallback.getData().size() > 0;
        if (pullStackForward) {
            anchorTask = mCallback.getData().get(mCallback.getData().size() - 1);
            prevAnchorTaskScroll = mLayoutAlgorithm.getStackScrollForTask(anchorTask);
        }

        // Update the min/max scroll and animate other task views into their new positions
        updateMinMaxScroll(true, mConfig.launchedWithAltTab, mConfig.launchedFromHome);

        // Offset the stack by as much as the anchor task would otherwise move back
        if (pullStackForward) {
            float anchorTaskScroll = mLayoutAlgorithm.getStackScrollForTask(anchorTask);
            mStackScroller.setStackScroll(mStackScroller.getStackScroll() + (anchorTaskScroll
                    - prevAnchorTaskScroll));
            mStackScroller.boundScroll();
        }

        // Animate all the tasks into place
        requestSynchronizeStackViewsWithModel(200);

        T newFrontMostTask = mCallback.getData().get(mCallback.getData().size() - 1);
        // Update the new front most task
        if (newFrontMostTask != null) {
            DeckChildView<T> frontTv = getChildViewForTask(newFrontMostTask);
            if (frontTv != null) {
                frontTv.onTaskBound(newFrontMostTask);
            }
        }

        // If there are no remaining tasks
        if (mCallback.getData().size() == 0) {
            mCallback.onNoViewsToDeck();
        }
        */
    }

    public void notifyDataSetChanged() {
        // Get the stack scroll of the task to anchor to (since we are removing something, the front
        // most task will be our anchor task)
        T anchorTask = null;
        float prevAnchorTaskScroll = 0;
        boolean pullStackForward = mCallback.getData().size() > 0;
        if (pullStackForward) {
            anchorTask = mCallback.getData().get(mCallback.getData().size() - 1);
            prevAnchorTaskScroll = mLayoutAlgorithm.getStackScrollForTask(anchorTask);
        }

        // Update the min/max scroll and animate other task views into their new positions
        updateMinMaxScroll(true, mConfig.launchedWithAltTab, mConfig.launchedFromHome);

        // Offset the stack by as much as the anchor task would otherwise move back
        if (pullStackForward) {
            float anchorTaskScroll = mLayoutAlgorithm.getStackScrollForTask(anchorTask);
            mStackScroller.setStackScroll(mStackScroller.getStackScroll() + (anchorTaskScroll
                    - prevAnchorTaskScroll));
            mStackScroller.boundScroll();
        }

        // Animate all the tasks into place
        requestSynchronizeStackViewsWithModel(200);

        T newFrontMostTask = mCallback.getData().size() > 0 ?
                mCallback.getData().get(mCallback.getData().size() - 1)
                : null;
        // Update the new front most task
        if (newFrontMostTask != null) {
            DeckChildView<T> frontTv = getChildViewForTask(newFrontMostTask);
            if (frontTv != null) {
                frontTv.onTaskBound(newFrontMostTask);
            }
        }

        // If there are no remaining tasks
        if (mCallback.getData().size() == 0) {
            mCallback.onNoViewsToDeck();
        }
    }

    @Override
    public void onDeckChildViewClipStateChanged(DeckChildView tv) {
        if (!mStackViewsDirty) {
            invalidate();
        }
    }

    @Override
    public void onDeckChildViewFocusChanged(DeckChildView<T> tv, boolean focused) {
        if (focused) {
            mFocusedTaskIndex = mCallback.getData().indexOf(tv.getAttachedKey());
        }
    }

    /**
     * * TaskStackViewScroller.TaskStackViewScrollerCallbacks ***
     */

    @Override
    public void onScrollChanged(float p) {
        mUIDozeTrigger.poke();
        requestSynchronizeStackViewsWithModel();
        postInvalidateOnAnimation();
    }

    public void notifyDataSetChangedOld() {
        ArrayList<T> data = mCallback.getData();

        // Get the stack scroll of the task to anchor to (since we are removing something, the front
        // most task will be our anchor task)
        T anchorTask = null;
        float prevAnchorTaskScroll = 0;
        boolean pullStackForward = data.size() > 0;
        if (pullStackForward) {
            anchorTask = data.get(data.size() - 1);
            prevAnchorTaskScroll = mLayoutAlgorithm.getStackScrollForTask(anchorTask);
        }

        // Update the min/max scroll and animate other task views into their new positions
        updateMinMaxScroll(true, mConfig.launchedWithAltTab, mConfig.launchedFromHome);

        // Offset the stack by as much as the anchor task would otherwise move back
        if (pullStackForward) {
            float anchorTaskScroll = mLayoutAlgorithm.getStackScrollForTask(anchorTask);
            mStackScroller.setStackScroll(mStackScroller.getStackScroll() + (anchorTaskScroll
                    - prevAnchorTaskScroll));
            mStackScroller.boundScroll();
        }

        // Animate all the tasks into place
        requestSynchronizeStackViewsWithModel(200);

        T newFrontMostTask = data.get(data.size() - 1);
        // Update the new front most task
        if (newFrontMostTask != null) {
            DeckChildView<T> frontTv = getChildViewForTask(newFrontMostTask);
            if (frontTv != null) {
                frontTv.onTaskBound(newFrontMostTask);
            }
        }

        // If there are no remaining tasks
        if (mCallback.getData().size() == 0)
            mCallback.onNoViewsToDeck();
    }

    Callback<T> mCallback;

    public interface Callback<T> {
        public ArrayList<T> getData();

        public void loadViewData(WeakReference<DeckChildView<T>> dcv, T item);

        public void unloadViewData(T item);

        public void onViewDismissed(T item);

        public void onItemClick(T item);

        public void onNoViewsToDeck();
    }
}
