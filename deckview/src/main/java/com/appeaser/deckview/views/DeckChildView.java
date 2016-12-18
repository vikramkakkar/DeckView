package com.appeaser.deckview.views;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.ViewPropertyAnimatorUpdateListener;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;

import com.appeaser.deckview.R;
import com.appeaser.deckview.helpers.DeckChildViewTransform;
import com.appeaser.deckview.helpers.DeckViewConfig;
import com.appeaser.deckview.helpers.FakeShadowDrawable;
import com.appeaser.deckview.utilities.DVUtils;

/**
 * Created by Vikram on 02/04/2015.
 */
/* A task view */
public class DeckChildView<T> extends FrameLayout implements
        View.OnClickListener {

    /**
     * The TaskView callbacks
     */
    interface DeckChildViewCallbacks<T> {

        public void onDeckChildViewClicked(DeckChildView<T> dcv, T key);

        public void onDeckChildViewDismissed(DeckChildView<T> dcv);

        public void onDeckChildViewClipStateChanged(DeckChildView dcv);

        public void onDeckChildViewFocusChanged(DeckChildView<T> dcv, boolean focused);
    }

    DeckViewConfig mConfig;

    float mTaskProgress;
    ObjectAnimator mTaskProgressAnimator;
    float mMaxDimScale;
    int mDimAlpha;

    T mKey;
    boolean mIsFocused;
    boolean mFocusAnimationsEnabled;
    boolean mClipViewInStack;

    View mContent;
    public DeckChildViewThumbnail mThumbnailView;
    DeckChildViewCallbacks<T> mCb;

    // Optimizations
    ValueAnimator.AnimatorUpdateListener mUpdateDimListener =
            new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    setTaskProgress((Float) animation.getAnimatedValue());
                }
            };


    public DeckChildView(Context context) {
        this(context, null);
    }

    public DeckChildView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DeckChildView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mConfig = DeckViewConfig.getInstance();
        mMaxDimScale = mConfig.taskStackMaxDim / 255f;
        mClipViewInStack = true;
        setTaskProgress(getTaskProgress());
        if (mConfig.fakeShadows) {
            ViewCompat.setBackground(this, new FakeShadowDrawable(context.getResources(), mConfig));
        }
    }

    /**
     * Set callback
     */
    void setCallbacks(DeckChildViewCallbacks cb) {
        mCb = cb;
    }

    /**
     * Resets this TaskView for reuse.
     */
    void reset() {
        resetViewProperties();
        setClipViewInStack(false);
        setCallbacks(null);
    }

    /**
     * Gets the task
     */
    T getAttachedKey() {
        return mKey;
    }

    @Override
    protected void onFinishInflate() {
        // Bind the views
        mContent = findViewById(R.id.task_view_content);
        mThumbnailView = (DeckChildViewThumbnail) findViewById(R.id.task_view_thumbnail);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);

        int widthWithoutPadding = width - getPaddingLeft() - getPaddingRight();
        int heightWithoutPadding = height - getPaddingTop() - getPaddingBottom();

        // Measure the content
        mContent.measure(MeasureSpec.makeMeasureSpec(widthWithoutPadding, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(widthWithoutPadding, MeasureSpec.EXACTLY));

        // Measure the thumbnail to be square
        mThumbnailView.measure(
                MeasureSpec.makeMeasureSpec(widthWithoutPadding, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(widthWithoutPadding, MeasureSpec.EXACTLY));
        setMeasuredDimension(width, height);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            invalidateOutline();
        } else {
            // todo nightq now 自己加的不知道游泳没
            invalidate();
        }
    }

    /**
     * Synchronizes this view's properties with the task's transform
     */
    void updateViewPropertiesToTaskTransform(DeckChildViewTransform toTransform, int duration) {
        updateViewPropertiesToTaskTransform(toTransform, duration, null);
    }

    void updateViewPropertiesToTaskTransform(DeckChildViewTransform toTransform, int duration,
                                             ViewPropertyAnimatorUpdateListener updateCallback) {
        // Apply the transform
        toTransform.applyToTaskView(this, duration, mConfig.fastOutSlowInInterpolator, false,
                !mConfig.fakeShadows, updateCallback);

        // Update the task progress
        DVUtils.cancelAnimationWithoutCallbacks(mTaskProgressAnimator);
        if (duration <= 0) {
            setTaskProgress(toTransform.p);
        } else {
            mTaskProgressAnimator = ObjectAnimator.ofFloat(this, "taskProgress", toTransform.p);
            mTaskProgressAnimator.setDuration(duration);
            mTaskProgressAnimator.addUpdateListener(mUpdateDimListener);
            mTaskProgressAnimator.start();
        }
    }

    /**
     * Resets this view's properties
     */
    void resetViewProperties() {
        setLayerType(View.LAYER_TYPE_NONE, null);
        DeckChildViewTransform.reset(this);
    }

    /**
     * When we are un/filtering, this method will set up the transform that we are animating to,
     * in order to hide the task.
     */
    void prepareTaskTransformForFilterTaskHidden(DeckChildViewTransform toTransform) {
        // Fade the view out and slide it away
        toTransform.alpha = 0f;
        toTransform.translationY += 200;
        toTransform.translationZ = 0;
    }

    /**
     * When we are un/filtering, this method will setup the transform that we are animating from,
     * in order to show the task.
     */
    void prepareTaskTransformForFilterTaskVisible(DeckChildViewTransform fromTransform) {
        // Fade the view in
        fromTransform.alpha = 0f;
    }

    /**
     * Prepares this task view for the enter-recents animations.  This is called earlier in the
     * first layout because the actual animation into recents may take a long time.
     */
    void prepareEnterRecentsAnimation(boolean isTaskViewLaunchTargetTask,
                                      boolean occludesLaunchTarget, int offscreenY) {
        if (mConfig.launchedHasConfigurationChanged) {
            // Just load the views as-is
        } else if (mConfig.launchedFromAppWithThumbnail) {
            if (isTaskViewLaunchTargetTask) {
                // Set the dim to 0 so we can animate it in
            } else if (occludesLaunchTarget) {
                // Move the task view off screen (below) so we can animate it in
                setTranslationY(offscreenY);
            }

        } else if (mConfig.launchedFromHome) {
            // Move the task view off screen (below) so we can animate it in
            setTranslationY(offscreenY);
            ViewCompat.setTranslationZ(this, 0);
            setScaleX(1f);
            setScaleY(1f);
        }
    }

    /**
     * Animates this task view as it enters recents
     */
    void startEnterRecentsAnimation(final ViewAnimation.TaskViewEnterContext ctx) {
        Log.i(getClass().getSimpleName(), "startEnterRecentsAnimation");
        final DeckChildViewTransform transform = ctx.currentTaskTransform;
        int startDelay = 0;

        if (mConfig.launchedFromHome) {
            Log.i(getClass().getSimpleName(), "mConfig.launchedFromHome false");
            // Animate the tasks up
            int frontIndex = (ctx.currentStackViewCount - ctx.currentStackViewIndex - 1);
            int delay = mConfig.transitionEnterFromHomeDelay +
                    frontIndex * mConfig.taskViewEnterFromHomeStaggerDelay;

            setScaleX(transform.scale);
            setScaleY(transform.scale);
            if (!mConfig.fakeShadows) {
                ViewCompat.animate(this).translationZ(transform.translationZ);
            }
            ViewCompat.animate(this)
                    .translationY(transform.translationY)
                    .setStartDelay(delay)
                    .setUpdateListener(ctx.updateListener)
                    .setInterpolator(mConfig.quintOutInterpolator)
                    .setDuration(mConfig.taskViewEnterFromHomeDuration +
                            frontIndex * mConfig.taskViewEnterFromHomeStaggerDelay)
                    .withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            // Decrement the post animation trigger
                            ctx.postAnimationTrigger.decrement();
                        }
                    })
                    .start();
            ctx.postAnimationTrigger.increment();
            startDelay = delay;
        }

        // Enable the focus animations from this point onwards so that they aren't affected by the
        // window transitions
        postDelayed(new Runnable() {
            @Override
            public void run() {
                enableFocusAnimations();
            }
        }, startDelay);
    }

    /**
     * Animates this task view as it leaves recents by pressing home.
     */
    void startExitToHomeAnimation(ViewAnimation.TaskViewExitContext ctx) {
        ViewCompat.animate(this)
                .translationY(ctx.offscreenTranslationY)
                .setStartDelay(0)
                .setUpdateListener(null)
                .setInterpolator(mConfig.fastOutLinearInInterpolator)
                .setDuration(mConfig.taskViewExitToHomeDuration)
                .withEndAction(ctx.postAnimationTrigger.decrementAsRunnable())
                .start();
        ctx.postAnimationTrigger.increment();
    }

    /**
     * Animates this task view as it exits recents
     */
    void startLaunchTaskAnimation(final Runnable postAnimRunnable, boolean isLaunchingTask,
                                  boolean occludesLaunchTarget, boolean lockToTask) {
        if (isLaunchingTask) {
            // Animate the dim
            if (mDimAlpha > 0) {
                ObjectAnimator anim = ObjectAnimator.ofInt(this, "dim", 0);
                anim.setDuration(mConfig.taskViewExitToAppDuration);
                anim.setInterpolator(mConfig.fastOutLinearInInterpolator);
                anim.start();
            }
        } else {
            // If this is another view in the task grouping and is in front of the launch task,
            // animate it away first
            if (occludesLaunchTarget) {
                ViewCompat.animate(this)
                        .alpha(0f)
                        .translationY(getTranslationY() + mConfig.taskViewAffiliateGroupEnterOffsetPx)
                        .setStartDelay(0)
                        .setUpdateListener(null)
                        .setInterpolator(mConfig.fastOutLinearInInterpolator)
                        .setDuration(mConfig.taskViewExitToAppDuration)
                        .start();
            }
        }
    }

    /**
     * Animates the deletion of this task view
     */
    void startDeleteTaskAnimation(final Runnable r) {
        // Disabling clipping with the stack while the view is animating away
        setClipViewInStack(false);

        ViewCompat.animate(this)
                .translationX(mConfig.taskViewRemoveAnimTranslationXPx)
                .alpha(0f)
                .setStartDelay(0)
                .setUpdateListener(null)
                .setInterpolator(mConfig.fastOutSlowInInterpolator)
                .setDuration(mConfig.taskViewRemoveAnimDuration)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        // We just throw this into a runnable because starting a view property
                        // animation using layers can cause inconsisten results if we try and
                        // update the layers while the animation is running.  In some cases,
                        // the runnabled passed in may start an animation which also uses layers
                        // so we defer all this by posting this.
                        r.run();

                        // Re-enable clipping with the stack (we will reuse this view)
                        setClipViewInStack(true);
                    }
                })
                .start();
    }

    /**
     * Dismisses this task.
     */
    void dismissTask() {
        // Animate out the view and call the callback
        final DeckChildView<T> tv = this;
        startDeleteTaskAnimation(new Runnable() {
            @Override
            public void run() {
                if (mCb != null) {
                    mCb.onDeckChildViewDismissed(tv);
                }
            }
        });
    }

    /**
     * Returns whether this view should be clipped, or any views below should clip against this
     * view.
     */
    boolean shouldClipViewInStack() {
        return mClipViewInStack && (getVisibility() == View.VISIBLE);
    }

    /**
     * Sets whether this view should be clipped, or clipped against.
     */
    public void setClipViewInStack(boolean clip) {
        if (clip != mClipViewInStack) {
            mClipViewInStack = clip;
            if (mCb != null) {
                mCb.onDeckChildViewClipStateChanged(this);
            }
        }
    }

    /**
     * Sets the current task progress.
     */
    public void setTaskProgress(float p) {
        mTaskProgress = p;
        Log.e("ngihtq", "setTaskProgress p = " + p);
    }

    /**
     * Returns the current task progress.
     */
    public float getTaskProgress() {
        return mTaskProgress;
    }

    /**** View focus state ****/

    /**
     * Sets the focused task explicitly. We need a separate flag because requestFocus() won't happen
     * if the view is not currently visible, or we are in touch state (where we still want to keep
     * track of focus).
     */
    public void setFocusedTask(boolean animateFocusedState) {
        mIsFocused = true;
        // Update the thumbnail alpha with the focus
        // Call the callback
        if (mCb != null) {
            mCb.onDeckChildViewFocusChanged(this, true);
        }
        // Workaround, we don't always want it focusable in touch mode, but we want the first task
        // to be focused after the enter-recents animation, which can be triggered from either touch
        // or keyboard
        setFocusableInTouchMode(true);
        requestFocus();
        setFocusableInTouchMode(false);
        invalidate();
    }

    /**
     * Unsets the focused task explicitly.
     */
    void unsetFocusedTask() {
        mIsFocused = false;

        // Update the thumbnail alpha with the focus
        // Call the callback
        if (mCb != null) {
            mCb.onDeckChildViewFocusChanged(this, false);
        }
        invalidate();
    }

    /**
     * Updates the explicitly focused state when the view focus changes.
     */
    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        if (!gainFocus) {
            unsetFocusedTask();
        }
    }

    /**
     * Returns whether we have explicitly been focused.
     */
    public boolean isFocusedTask() {
        return mIsFocused || isFocused();
    }

    /**
     * Enables all focus animations.
     */
    void enableFocusAnimations() {
        boolean wasFocusAnimationsEnabled = mFocusAnimationsEnabled;
        mFocusAnimationsEnabled = true;
    }

    /**** TaskCallbacks Implementation ****/

    /**
     * Binds this task view to the task
     */
    public void onTaskBound(T key) {
        mKey = key;
    }

    private boolean isBound() {
        return mKey != null;
    }

    /**
     * Binds this task view to the task
     */
    public void onTaskUnbound() {
        mKey = null;
    }


    public void onDataLoaded(T key) {
        if (!isBound() || !mKey.equals(key)) {
            return;
        }
        // todo nightq now
    }

    public void onDataUnloaded() {
        if (mThumbnailView != null) {
            // Unbind each of the views from the task data and remove the task callback
            mThumbnailView.setImageBitmap(null);
        }
        // todo nightq now
    }

    /**
     * Enables/disables handling touch on this task view.
     */
    public void setTouchEnabled(boolean enabled) {
        setOnClickListener(enabled ? this : null);
    }

    /**
     * * View.OnClickListener Implementation ***
     */

    @Override
    public void onClick(final View v) {
        final DeckChildView<T> tv = this;
        final boolean delayViewClick = (v != this);
        if (!delayViewClick) {
            if (mCb != null) {
                mCb.onDeckChildViewClicked(tv, tv.getAttachedKey());
            }
        }
    }

}
