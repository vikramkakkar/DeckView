package com.appeaser.deckview.views;

import android.animation.ValueAnimator;
import android.graphics.Rect;

import com.appeaser.deckview.helpers.DeckChildViewTransform;
import com.appeaser.deckview.utilities.ReferenceCountedTrigger;

/**
 * Created by Vikram on 01/04/2015.
 */
/* Common code related to view animations */
public class ViewAnimation {

    /* The animation context for a task view animation into Recents */
    public static class TaskViewEnterContext {
        // A trigger to run some logic when all the animations complete.  This works around the fact
        // that it is difficult to coordinate ViewPropertyAnimators
        public ReferenceCountedTrigger postAnimationTrigger;
        // An update listener to notify as the enter animation progresses (used for the home transition)
        public ValueAnimator.AnimatorUpdateListener updateListener;

        // These following properties are updated for each task view we start the enter animation on

        // Whether or not the current task occludes the launch target
        boolean currentTaskOccludesLaunchTarget;
        // The task rect for the current stack
        Rect currentTaskRect;
        // The transform of the current task view
        public DeckChildViewTransform currentTaskTransform;
        // The view index of the current task view
        public int currentStackViewIndex;
        // The total number of task views
        public int currentStackViewCount;

        public TaskViewEnterContext(ReferenceCountedTrigger t) {
            postAnimationTrigger = t;
        }
    }

    /* The animation context for a task view animation out of Recents */
    public static class TaskViewExitContext {
        // A trigger to run some logic when all the animations complete.  This works around the fact
        // that it is difficult to coordinate ViewPropertyAnimators
        public ReferenceCountedTrigger postAnimationTrigger;

        // The translationY to apply to a TaskView to move it off the bottom of the task stack
        public int offscreenTranslationY;

        public TaskViewExitContext(ReferenceCountedTrigger t) {
            postAnimationTrigger = t;
        }
    }

}
