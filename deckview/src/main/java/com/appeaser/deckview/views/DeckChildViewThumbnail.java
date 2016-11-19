package com.appeaser.deckview.views;

/**
 * Created by Vikram on 02/04/2015.
 */

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ImageView;

/**
 * The task thumbnail view.  It implements an image view that allows for animating the dim and
 * alpha of the thumbnail image.
 */
public class DeckChildViewThumbnail extends ImageView {

    public DeckChildViewThumbnail(Context context) {
        this(context, null);
    }

    public DeckChildViewThumbnail(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, widthMeasureSpec);
    }
}
