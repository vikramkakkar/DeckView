package com.appeaser.deckview.helpers;

/**
 * Created by Vikram on 02/04/2015.
 */

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;

import com.appeaser.deckview.R;
import com.appeaser.deckview.utilities.DVConstants;

/**
 * Configuration helper
 */
public class DeckViewConfig {
    static DeckViewConfig sInstance;
    static int sPrevConfigurationHashCode;

    /**
     * Levels of svelte in increasing severity/austerity.
     */
    // No svelting.
    public static final int SVELTE_NONE = 0;
    // Limit thumbnail cache to number of visible thumbnails when Recents was loaded, disable
    // caching thumbnails as you scroll.
    public static final int SVELTE_LIMIT_CACHE = 1;
    // Disable the thumbnail cache, load thumbnails asynchronously when the activity loads and
    // evict all thumbnails when hidden.
    public static final int SVELTE_DISABLE_CACHE = 2;
    // Disable all thumbnail loading.
    public static final int SVELTE_DISABLE_LOADING = 3;

    /**
     * Animations
     */
    public float animationPxMovementPerSecond;

    /**
     * Interpolators
     */
    public Interpolator fastOutSlowInInterpolator;
    public Interpolator fastOutLinearInInterpolator;
    public Interpolator linearOutSlowInInterpolator;
    public Interpolator quintOutInterpolator;

    /**
     * Filtering
     */
    public int filteringCurrentViewsAnimDuration;
    public int filteringNewViewsAnimDuration;

    /**
     * Insets
     */
    public Rect systemInsets = new Rect();
    public Rect displayRect = new Rect();

    /**
     * Layout
     */
    boolean isLandscape;

    /**
     * Task stack
     */
    public int taskStackScrollDuration;
    public int taskStackMaxDim;
    public int taskStackTopPaddingPx;
    public float taskStackWidthPaddingPct;
    public float taskStackOverscrollPct;

    /**
     * Transitions
     */
    public int transitionEnterFromAppDelay;
    public int transitionEnterFromHomeDelay;

    /**
     * Task view animation and styles
     */
    public int taskViewEnterFromAppDuration;
    public int taskViewEnterFromHomeDuration;
    public int taskViewEnterFromHomeStaggerDelay;
    public int taskViewExitToAppDuration;
    public int taskViewExitToHomeDuration;
    public int taskViewRemoveAnimDuration;
    public int taskViewRemoveAnimTranslationXPx;
    public int taskViewTranslationZMinPx;
    public int taskViewTranslationZMaxPx;
    public int taskViewRoundedCornerRadiusPx;
    public int taskViewHighlightPx;
    public int taskViewAffiliateGroupEnterOffsetPx;
    public float taskViewThumbnailAlpha;

    /**
     * Task bar colors
     */
    public int taskBarViewDefaultBackgroundColor;
    public int taskBarViewLightTextColor;
    public int taskBarViewDarkTextColor;
    public int taskBarViewHighlightColor;
    public float taskBarViewAffiliationColorMinAlpha;

    /**
     * Task bar size & animations
     */
    public int taskBarHeight;
    public int taskBarDismissDozeDelaySeconds;

    /**
     * Nav bar scrim
     */
    public int navBarScrimEnterDuration;

    /**
     * Launch states
     */
    public boolean launchedWithAltTab;
    public boolean launchedWithNoRecentTasks;
    public boolean launchedFromAppWithThumbnail;
    public boolean launchedFromHome;
    public boolean launchedFromSearchHome;
    public boolean launchedReuseTaskStackViews;
    public boolean launchedHasConfigurationChanged;
    public int launchedToTaskId;
    public int launchedNumVisibleTasks;
    public int launchedNumVisibleThumbnails;

    /**
     * Misc *
     */
    public boolean useHardwareLayers;
    public int altTabKeyDelay;
    public boolean fakeShadows;

    /**
     * Dev options and global settings
     */
    public boolean debugModeEnabled;
    public int svelteLevel;

    /**
     * Private constructor
     */
    private DeckViewConfig(Context context) {
        // Properties that don't have to be reloaded with each configuration change can be loaded
        // here.

        // Interpolators
        fastOutSlowInInterpolator = AnimationUtils.loadInterpolator(context,
                R.interpolator.fast_out_slow_in);
        fastOutLinearInInterpolator = AnimationUtils.loadInterpolator(context,
                R.interpolator.fast_out_linear_in);
        linearOutSlowInInterpolator = AnimationUtils.loadInterpolator(context,
                R.interpolator.linear_out_slow_in);
        quintOutInterpolator = AnimationUtils.loadInterpolator(context,
                R.interpolator.decelerate_quint);
    }

    /**
     * Updates the configuration to the current context
     */
    public static DeckViewConfig reinitialize(Context context) {
        if (sInstance == null) {
            sInstance = new DeckViewConfig(context);
        }
        int configHashCode = context.getResources().getConfiguration().hashCode();
        if (sPrevConfigurationHashCode != configHashCode) {
            sInstance.update(context);
            sPrevConfigurationHashCode = configHashCode;
        }

        sInstance.updateOnReinitialize(context);
        return sInstance;
    }

    /**
     * Returns the current recents configuration
     */
    public static DeckViewConfig getInstance() {
        return sInstance;
    }

    /**
     * Updates the state, given the specified context
     */
    void update(Context context) {
        SharedPreferences settings = context.getSharedPreferences(context.getPackageName(), 0);
        Resources res = context.getResources();
        DisplayMetrics dm = res.getDisplayMetrics();

        // Debug mode
        debugModeEnabled = settings.getBoolean(DVConstants.Values.App.Key_DebugModeEnabled, false);

        // Layout
        isLandscape = res.getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;

        // Insets
        displayRect.set(0, 0, dm.widthPixels, dm.heightPixels);

        // Animations
        animationPxMovementPerSecond =
                res.getDimensionPixelSize(R.dimen.animation_movement_in_dps_per_second);

        // Filtering
        filteringCurrentViewsAnimDuration =
                res.getInteger(R.integer.filter_animate_current_views_duration);
        filteringNewViewsAnimDuration =
                res.getInteger(R.integer.filter_animate_new_views_duration);

        // Task stack
        taskStackScrollDuration =
                res.getInteger(R.integer.animate_deck_scroll_duration);
        TypedValue widthPaddingPctValue = new TypedValue();
        res.getValue(R.dimen.deck_width_padding_percentage, widthPaddingPctValue, true);
        taskStackWidthPaddingPct = widthPaddingPctValue.getFloat();
        TypedValue stackOverscrollPctValue = new TypedValue();
        res.getValue(R.dimen.deck_overscroll_percentage, stackOverscrollPctValue, true);
        taskStackOverscrollPct = stackOverscrollPctValue.getFloat();
        taskStackMaxDim = res.getInteger(R.integer.max_deck_view_dim);
        taskStackTopPaddingPx = res.getDimensionPixelSize(R.dimen.deck_top_padding);

        // Transition
        transitionEnterFromAppDelay =
                res.getInteger(R.integer.enter_from_app_transition_duration);
        transitionEnterFromHomeDelay =
                res.getInteger(R.integer.enter_from_home_transition_duration);

        // Task view animation and styles
        taskViewEnterFromAppDuration =
                res.getInteger(R.integer.task_enter_from_app_duration);
        taskViewEnterFromHomeDuration =
                res.getInteger(R.integer.task_enter_from_home_duration);
        taskViewEnterFromHomeStaggerDelay =
                res.getInteger(R.integer.task_enter_from_home_stagger_delay);
        taskViewExitToAppDuration =
                res.getInteger(R.integer.task_exit_to_app_duration);
        taskViewExitToHomeDuration =
                res.getInteger(R.integer.task_exit_to_home_duration);
        taskViewRemoveAnimDuration =
                res.getInteger(R.integer.animate_task_view_remove_duration);
        taskViewRemoveAnimTranslationXPx =
                res.getDimensionPixelSize(R.dimen.task_view_remove_anim_translation_x);
        taskViewRoundedCornerRadiusPx =
                res.getDimensionPixelSize(R.dimen.task_view_rounded_corners_radius);
        taskViewHighlightPx = res.getDimensionPixelSize(R.dimen.task_view_highlight);
        taskViewTranslationZMinPx = res.getDimensionPixelSize(R.dimen.task_view_z_min);
        taskViewTranslationZMaxPx = res.getDimensionPixelSize(R.dimen.task_view_z_max);
        taskViewAffiliateGroupEnterOffsetPx =
                res.getDimensionPixelSize(R.dimen.task_view_affiliate_group_enter_offset);
        TypedValue thumbnailAlphaValue = new TypedValue();
        res.getValue(R.dimen.task_view_thumbnail_alpha, thumbnailAlphaValue, true);
        taskViewThumbnailAlpha = thumbnailAlphaValue.getFloat();

        // Task bar colors
        taskBarViewDefaultBackgroundColor =
                res.getColor(R.color.task_bar_default_background_color);
        taskBarViewLightTextColor =
                res.getColor(R.color.task_bar_light_text_color);
        taskBarViewDarkTextColor =
                res.getColor(R.color.task_bar_dark_text_color);
        taskBarViewHighlightColor =
                res.getColor(R.color.task_bar_highlight_color);
        TypedValue affMinAlphaPctValue = new TypedValue();
        res.getValue(R.dimen.task_affiliation_color_min_alpha_percentage, affMinAlphaPctValue, true);
        taskBarViewAffiliationColorMinAlpha = affMinAlphaPctValue.getFloat();

        // Task bar size & animations
        taskBarHeight = res.getDimensionPixelSize(R.dimen.deck_child_header_bar_height);
        taskBarDismissDozeDelaySeconds =
                res.getInteger(R.integer.task_bar_dismiss_delay_seconds);

        // Nav bar scrim
        navBarScrimEnterDuration =
                res.getInteger(R.integer.nav_bar_scrim_enter_duration);

        // Misc
        useHardwareLayers = res.getBoolean(R.bool.config_use_hardware_layers);
        altTabKeyDelay = res.getInteger(R.integer.deck_alt_tab_key_delay);
        fakeShadows = res.getBoolean(R.bool.config_fake_shadows);
        svelteLevel = res.getInteger(R.integer.deck_svelte_level);
    }

    /**
     * Updates the system insets
     */
    public void updateSystemInsets(Rect insets) {
        systemInsets.set(insets);
    }

    /**
     * Updates the search bar app widget
     */
    public void updateSearchBarAppWidgetId(Context context, int appWidgetId) {

    }

    /**
     * Updates the states that need to be re-read whenever we re-initialize.
     */
    void updateOnReinitialize(Context context/*, SystemServicesProxy ssp*/) {

    }

    /**
     * Called when the configuration has changed, and we want to reset any configuration specific
     * members.
     */
    public void updateOnConfigurationChange() {
        // Reset this flag on configuration change to ensure that we recreate new task views
        launchedReuseTaskStackViews = false;
        // Set this flag to indicate that the configuration has changed since Recents last launched
        launchedHasConfigurationChanged = true;
    }

    /**
     * Returns the task stack bounds in the current orientation. These bounds do not account for
     * the system insets.
     */
    public void getTaskStackBounds(int windowWidth, int windowHeight, int topInset, int rightInset,
                                   Rect taskStackBounds) {
        taskStackBounds.set(0, 0, windowWidth, windowHeight);
    }
}