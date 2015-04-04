package com.appeaser.deckview.views;

import android.graphics.Outline;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewOutlineProvider;

import com.appeaser.deckview.helpers.DeckViewConfig;

/**
 * Created by Vikram on 02/04/2015.
 */
/* An outline provider that has a clip and outline that can be animated. */
public class AnimateableDeckChildViewBounds extends ViewOutlineProvider {

    DeckViewConfig mConfig;

    DeckChildView mSourceView;
    Rect mClipRect = new Rect();
    Rect mClipBounds = new Rect();
    int mCornerRadius;
    float mAlpha = 1f;
    final float mMinAlpha = 0.25f;

    public AnimateableDeckChildViewBounds(DeckChildView source, int cornerRadius) {
        mConfig = DeckViewConfig.getInstance();
        mSourceView = source;
        mCornerRadius = cornerRadius;
        setClipBottom(getClipBottom());
    }

    @Override
    public void getOutline(View view, Outline outline) {
        outline.setAlpha(mMinAlpha + mAlpha / (1f - mMinAlpha));
        outline.setRoundRect(mClipRect.left, mClipRect.top,
                mSourceView.getWidth() - mClipRect.right,
                mSourceView.getHeight() - mClipRect.bottom,
                mCornerRadius);
    }

    /**
     * Sets the view outline alpha.
     */
    void setAlpha(float alpha) {
        if (Float.compare(alpha, mAlpha) != 0) {
            mAlpha = alpha;
            mSourceView.invalidateOutline();
        }
    }

    /**
     * Sets the bottom clip.
     */
    public void setClipBottom(int bottom) {
        if (bottom != mClipRect.bottom) {
            mClipRect.bottom = bottom;
            mSourceView.invalidateOutline();
            updateClipBounds();
            if (!mConfig.useHardwareLayers) {
                mSourceView.mThumbnailView.updateThumbnailVisibility(
                        bottom - mSourceView.getPaddingBottom());
            }
        }
    }

    /**
     * Returns the bottom clip.
     */
    public int getClipBottom() {
        return mClipRect.bottom;
    }

    private void updateClipBounds() {
        mClipBounds.set(mClipRect.left, mClipRect.top,
                mSourceView.getWidth() - mClipRect.right,
                mSourceView.getHeight() - mClipRect.bottom);
        mSourceView.setClipBounds(mClipBounds);
    }
}
