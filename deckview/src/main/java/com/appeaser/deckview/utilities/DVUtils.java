package com.appeaser.deckview.utilities;

import android.animation.Animator;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;

import com.appeaser.deckview.helpers.DeckViewConfig;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;

/**
 * Created by Vikram on 02/04/2015.
 */
public class DVUtils {
    // Reflection methods for altering shadows
    private static Method sPropertyMethod;

    static {
        try {
            Class<?> c = Class.forName("android.view.GLES20Canvas");
            sPropertyMethod = c.getDeclaredMethod("setProperty", String.class, String.class);
            if (!sPropertyMethod.isAccessible()) sPropertyMethod.setAccessible(true);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
    }

    /**
     * Calculates a consistent animation duration (ms) for all animations depending on the movement
     * of the object being animated.
     */
    public static int calculateTranslationAnimationDuration(int distancePx) {
        return calculateTranslationAnimationDuration(distancePx, 100);
    }

    public static int calculateTranslationAnimationDuration(int distancePx, int minDuration) {
        DeckViewConfig config = DeckViewConfig.getInstance();
        return Math.max(minDuration, (int) (1000f /* ms/s */ *
                (Math.abs(distancePx) / config.animationPxMovementPerSecond)));
    }

    /**
     * Scales a rect about its centroid
     */
    public static void scaleRectAboutCenter(Rect r, float scale) {
        if (scale != 1.0f) {
            int cx = r.centerX();
            int cy = r.centerY();
            r.offset(-cx, -cy);
            r.left = (int) (r.left * scale + 0.5f);
            r.top = (int) (r.top * scale + 0.5f);
            r.right = (int) (r.right * scale + 0.5f);
            r.bottom = (int) (r.bottom * scale + 0.5f);
            r.offset(cx, cy);
        }
    }

    /**
     * Maps a coorindate in a descendant view into the parent.
     */
    public static float mapCoordInDescendentToSelf(View descendant, View root,
                                                   float[] coord, boolean includeRootScroll) {
        ArrayList<View> ancestorChain = new ArrayList<View>();

        float[] pt = {coord[0], coord[1]};

        View v = descendant;
        while (v != root && v != null) {
            ancestorChain.add(v);
            v = (View) v.getParent();
        }
        ancestorChain.add(root);

        float scale = 1.0f;
        int count = ancestorChain.size();
        for (int i = 0; i < count; i++) {
            View v0 = ancestorChain.get(i);
            // For TextViews, scroll has a meaning which relates to the text position
            // which is very strange... ignore the scroll.
            if (v0 != descendant || includeRootScroll) {
                pt[0] -= v0.getScrollX();
                pt[1] -= v0.getScrollY();
            }

            v0.getMatrix().mapPoints(pt);
            pt[0] += v0.getLeft();
            pt[1] += v0.getTop();
            scale *= v0.getScaleX();
        }

        coord[0] = pt[0];
        coord[1] = pt[1];
        return scale;
    }

    /**
     * Maps a coordinate in the root to a descendent.
     */
    public static float mapCoordInSelfToDescendent(View descendant, View root,
                                                   float[] coord, Matrix tmpInverseMatrix) {
        ArrayList<View> ancestorChain = new ArrayList<View>();

        float[] pt = {coord[0], coord[1]};

        View v = descendant;
        while (v != root) {
            ancestorChain.add(v);
            v = (View) v.getParent();
        }
        ancestorChain.add(root);

        float scale = 1.0f;
        int count = ancestorChain.size();
        tmpInverseMatrix.set(IDENTITY_MATRIX);
        for (int i = count - 1; i >= 0; i--) {
            View ancestor = ancestorChain.get(i);
            View next = i > 0 ? ancestorChain.get(i - 1) : null;

            pt[0] += ancestor.getScrollX();
            pt[1] += ancestor.getScrollY();

            if (next != null) {
                pt[0] -= next.getLeft();
                pt[1] -= next.getTop();
                next.getMatrix().invert(tmpInverseMatrix);
                tmpInverseMatrix.mapPoints(pt);
                scale *= next.getScaleX();
            }
        }

        coord[0] = pt[0];
        coord[1] = pt[1];
        return scale;
    }

    /**
     * Calculates the constrast between two colors, using the algorithm provided by the WCAG v2.
     */
    public static float computeContrastBetweenColors(int bg, int fg) {
        float bgR = Color.red(bg) / 255f;
        float bgG = Color.green(bg) / 255f;
        float bgB = Color.blue(bg) / 255f;
        bgR = (bgR < 0.03928f) ? bgR / 12.92f : (float) Math.pow((bgR + 0.055f) / 1.055f, 2.4f);
        bgG = (bgG < 0.03928f) ? bgG / 12.92f : (float) Math.pow((bgG + 0.055f) / 1.055f, 2.4f);
        bgB = (bgB < 0.03928f) ? bgB / 12.92f : (float) Math.pow((bgB + 0.055f) / 1.055f, 2.4f);
        float bgL = 0.2126f * bgR + 0.7152f * bgG + 0.0722f * bgB;

        float fgR = Color.red(fg) / 255f;
        float fgG = Color.green(fg) / 255f;
        float fgB = Color.blue(fg) / 255f;
        fgR = (fgR < 0.03928f) ? fgR / 12.92f : (float) Math.pow((fgR + 0.055f) / 1.055f, 2.4f);
        fgG = (fgG < 0.03928f) ? fgG / 12.92f : (float) Math.pow((fgG + 0.055f) / 1.055f, 2.4f);
        fgB = (fgB < 0.03928f) ? fgB / 12.92f : (float) Math.pow((fgB + 0.055f) / 1.055f, 2.4f);
        float fgL = 0.2126f * fgR + 0.7152f * fgG + 0.0722f * fgB;

        return Math.abs((fgL + 0.05f) / (bgL + 0.05f));
    }

    /**
     * Returns the base color overlaid with another overlay color with a specified alpha.
     */
    public static int getColorWithOverlay(int baseColor, int overlayColor, float overlayAlpha) {
        return Color.rgb(
                (int) (overlayAlpha * Color.red(baseColor) +
                        (1f - overlayAlpha) * Color.red(overlayColor)),
                (int) (overlayAlpha * Color.green(baseColor) +
                        (1f - overlayAlpha) * Color.green(overlayColor)),
                (int) (overlayAlpha * Color.blue(baseColor) +
                        (1f - overlayAlpha) * Color.blue(overlayColor)));
    }

    /**
     * Sets some private shadow properties.
     */
    public static void setShadowProperty(String property, String value)
            throws IllegalAccessException, InvocationTargetException {
        sPropertyMethod.invoke(null, property, value);
    }

    /**
     * Cancels an animation ensuring that if it has listeners, onCancel and onEnd
     * are not called.
     */
    public static void cancelAnimationWithoutCallbacks(Animator animator) {
        if (animator != null) {
            animator.removeAllListeners();
            animator.cancel();
        }
    }

    public static Matrix IDENTITY_MATRIX = new Matrix() {
        void oops() {
            throw new IllegalStateException("Matrix can not be modified");
        }

        @Override
        public void set(Matrix src) {
            oops();
        }

        @Override
        public void reset() {
            oops();
        }

        @Override
        public void setTranslate(float dx, float dy) {
            oops();
        }

        @Override
        public void setScale(float sx, float sy, float px, float py) {
            oops();
        }

        @Override
        public void setScale(float sx, float sy) {
            oops();
        }

        @Override
        public void setRotate(float degrees, float px, float py) {
            oops();
        }

        @Override
        public void setRotate(float degrees) {
            oops();
        }

        @Override
        public void setSinCos(float sinValue, float cosValue, float px, float py) {
            oops();
        }

        @Override
        public void setSinCos(float sinValue, float cosValue) {
            oops();
        }

        @Override
        public void setSkew(float kx, float ky, float px, float py) {
            oops();
        }

        @Override
        public void setSkew(float kx, float ky) {
            oops();
        }

        @Override
        public boolean setConcat(Matrix a, Matrix b) {
            oops();
            return false;
        }

        @Override
        public boolean preTranslate(float dx, float dy) {
            oops();
            return false;
        }

        @Override
        public boolean preScale(float sx, float sy, float px, float py) {
            oops();
            return false;
        }

        @Override
        public boolean preScale(float sx, float sy) {
            oops();
            return false;
        }

        @Override
        public boolean preRotate(float degrees, float px, float py) {
            oops();
            return false;
        }

        @Override
        public boolean preRotate(float degrees) {
            oops();
            return false;
        }

        @Override
        public boolean preSkew(float kx, float ky, float px, float py) {
            oops();
            return false;
        }

        @Override
        public boolean preSkew(float kx, float ky) {
            oops();
            return false;
        }

        @Override
        public boolean preConcat(Matrix other) {
            oops();
            return false;
        }

        @Override
        public boolean postTranslate(float dx, float dy) {
            oops();
            return false;
        }

        @Override
        public boolean postScale(float sx, float sy, float px, float py) {
            oops();
            return false;
        }

        @Override
        public boolean postScale(float sx, float sy) {
            oops();
            return false;
        }

        @Override
        public boolean postRotate(float degrees, float px, float py) {
            oops();
            return false;
        }

        @Override
        public boolean postRotate(float degrees) {
            oops();
            return false;
        }

        @Override
        public boolean postSkew(float kx, float ky, float px, float py) {
            oops();
            return false;
        }

        @Override
        public boolean postSkew(float kx, float ky) {
            oops();
            return false;
        }

        @Override
        public boolean postConcat(Matrix other) {
            oops();
            return false;
        }

        @Override
        public boolean setRectToRect(RectF src, RectF dst, ScaleToFit stf) {
            oops();
            return false;
        }

        @Override
        public boolean setPolyToPoly(float[] src, int srcIndex, float[] dst, int dstIndex,
                                     int pointCount) {
            oops();
            return false;
        }

        @Override
        public void setValues(float[] values) {
            oops();
        }
    };
}
