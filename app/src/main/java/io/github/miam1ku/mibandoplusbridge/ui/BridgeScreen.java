// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.miam1ku.mibandoplusbridge.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.core.widget.NestedScrollView;
import com.hchen.himiuix.MiuixAppBar;
import com.hchen.himiuix.MiuixBasicView;
import com.hchen.himiuix.MiuixViewGroup;
import com.hchen.himiuix.fill.MiuixFillView;
import com.hchen.himiuix.helper.WindowInsetsHelper;
import com.hchen.himiuix.widget.MiuixTextView;
import com.google.android.material.materialswitch.MaterialSwitch;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared HiMiuix layout for every bridge screen.
 * Pages read top to bottom: status, next step, features, help, management, advanced.
 */
public final class BridgeScreen {
    /** Colour role of a chip, notice card, or status line. */
    public enum Tone { POSITIVE, ACTIVE, NEUTRAL, ATTENTION }

    /** Motion used when swapping the visible page. */
    public enum PageTransition { NONE, TAB, FORWARD, BACK }

    /** Position of a setup step relative to the current one. */
    public enum StepState { DONE, CURRENT, LATER }

    /** Receives the menu resource id of the selected main tab. */
    public interface TabListener { void onTabSelected(int itemId); }

    private enum ActionStyle { FILLED, OUTLINED, TEXT, DANGER }

    public final LinearLayout body;
    private final AppCompatActivity activity;
    private final MiuixAppBar appBar;
    private final NestedScrollView scroll;
    private final PiliTabBar tabBar;
    private final View tabBarContainer;
    private final MiuixFillView pageHost;
    private final FrameLayout pageFrame;
    private final List<LinearLayout> pages = new ArrayList<>();
    private LinearLayout visiblePage;
    private AnimatorSet pageAnimator;
    private int pageAnimationToken;
    private boolean predictiveBack;
    private LinearLayout predictiveSource;
    private LinearLayout predictiveTarget;

    private BridgeScreen(AppCompatActivity activity, LinearLayout body) {
        this(activity, body, null, null, null, null, null, null);
    }

    private BridgeScreen(AppCompatActivity activity, LinearLayout body, MiuixAppBar appBar,
                         NestedScrollView scroll, MiuixFillView pageHost, FrameLayout pageFrame,
                         PiliTabBar tabBar, View tabBarContainer) {
        this.activity = activity;
        this.body = body;
        this.appBar = appBar;
        this.scroll = scroll;
        this.pageHost = pageHost;
        this.pageFrame = pageFrame;
        this.tabBar = tabBar;
        this.tabBarContainer = tabBarContainer;
    }

    public static BridgeScreen attach(AppCompatActivity activity, String title, boolean back) {
        configureEdgeToEdge(activity);

        MiuixAppBar appBar = new MiuixAppBar(activity);
        appBar.setTitle(title);
        if (back) {
            appBar.getToolbar().setNavigationOnClickListener(view -> activity.finish());
        } else {
            appBar.getToolbar().setNavigationIcon(null);
        }

        NestedScrollView scroll = new NestedScrollView(activity);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);

        MiuixFillView body = new MiuixFillView(activity);
        body.setBackgroundResource(com.hchen.himiuix.R.color.miuix_theme_color);
        int topInset = body.getPaddingTop();
        int navigationHeight = navigationBarHeight(activity);
        body.setPadding(0, topInset + dp(activity, 4), 0,
                navigationHeight + dp(activity, 24));
        scroll.addView(body, new NestedScrollView.LayoutParams(-1, -2));
        appBar.addView(scroll, new MiuixAppBar.LayoutParams(-1, -1));
        activity.setContentView(appBar);

        ViewCompat.setOnApplyWindowInsetsListener(scroll, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            body.setPadding(bars.left, topInset + dp(activity, 4), bars.right,
                    Math.max(bars.bottom, navigationHeight) + dp(activity, 24));
            return insets;
        });
        appBar.post(() -> {
            appBar.targetStart(scroll);
            appBar.targetRegister(scroll);
        });
        return new BridgeScreen(activity, body);
    }

    /**
     * Main shell with a fixed AppBar and a three-item bottom navigator.
     * Pages are added with {@link #page()}; only one is visible at a time.
     */
    public static BridgeScreen attachTabs(AppCompatActivity activity, String title, int menuRes,
                                          int checkedItemId, TabListener listener) {
        configureEdgeToEdge(activity);

        FrameLayout shell = new FrameLayout(activity);
        shell.setClipChildren(false);
        shell.setBackgroundResource(UiStyle.material(activity)
                ? io.github.miam1ku.mibandoplusbridge.R.color.surface
                : com.hchen.himiuix.R.color.miuix_theme_color);
        FrameLayout contentRoot = new FrameLayout(activity);
        contentRoot.setBackgroundResource(UiStyle.material(activity)
                ? io.github.miam1ku.mibandoplusbridge.R.color.surface
                : com.hchen.himiuix.R.color.miuix_theme_color);
        shell.addView(contentRoot, new FrameLayout.LayoutParams(-1, -1));

        MiuixAppBar appBar = new MiuixAppBar(activity);
        appBar.setTitle(title);
        appBar.getToolbar().setNavigationIcon(null);

        NestedScrollView scroll = new NestedScrollView(activity);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);

        MiuixFillView pageHost = new MiuixFillView(activity);
        pageHost.setOrientation(LinearLayout.VERTICAL);
        pageHost.setBackgroundResource(UiStyle.material(activity)
                ? io.github.miam1ku.mibandoplusbridge.R.color.surface
                : com.hchen.himiuix.R.color.miuix_theme_color);
        int topInset = pageHost.getPaddingTop();
        pageHost.setPadding(0, topInset, 0, dp(activity, 104));
        FrameLayout pageFrame = new FrameLayout(activity);
        pageFrame.setClipChildren(true);
        pageHost.addView(pageFrame, new LinearLayout.LayoutParams(-1, -2));
        scroll.addView(pageHost, new NestedScrollView.LayoutParams(-1, -2));
        appBar.addView(scroll, new MiuixAppBar.LayoutParams(-1, -1));

        contentRoot.addView(appBar, new FrameLayout.LayoutParams(-1, -1));

        boolean floating = UiStyle.floating(activity);
        PiliTabBar tabBar = new PiliTabBar(
                activity, menuRes, checkedItemId, floating, contentRoot);
        tabBar.setOnTabSelectedListener(itemId -> {
            if (listener != null) listener.onTabSelected(itemId);
        });

        View tabBarContainer = tabBarContainer(activity, tabBar, floating, contentRoot);
        shell.addView(tabBarContainer, tabBarContainerParams(activity, floating));

        activity.setContentView(shell);
        ViewCompat.setOnApplyWindowInsetsListener(scroll, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            pageHost.setPadding(bars.left, topInset, bars.right, dp(activity, 104) + bars.bottom);
            return insets;
        });
        ViewCompat.setOnApplyWindowInsetsListener(tabBarContainer, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            FrameLayout.LayoutParams params =
                    (FrameLayout.LayoutParams) view.getLayoutParams();
            if (floating) {
                params.leftMargin = dp(activity, 16) + bars.left;
                params.rightMargin = dp(activity, 16) + bars.right;
                params.bottomMargin = dp(activity, 12) + bars.bottom;
                view.setPadding(0, 0, 0, 0);
            } else {
                params.leftMargin = 0;
                params.rightMargin = 0;
                params.bottomMargin = 0;
                params.height = dp(activity, 64) + bars.bottom;
                view.setPadding(bars.left, 0, bars.right, 0);
            }
            view.setLayoutParams(params);
            return insets;
        });
        appBar.post(() -> {
            appBar.targetStart(scroll);
            appBar.targetRegister(scroll);
        });
        return new BridgeScreen(activity, pageHost, appBar, scroll, pageHost, pageFrame,
                tabBar, tabBarContainer);
    }

    /** Adds a page below the main shell and returns helpers bound to that page. */
    public BridgeScreen page() {
        if (pageFrame == null) throw new IllegalStateException("PAGES_REQUIRE_TABS");
        LinearLayout page = new LinearLayout(activity);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setVisibility(View.GONE);
        pageFrame.addView(page, new FrameLayout.LayoutParams(-1, -2));
        pages.add(page);
        return new BridgeScreen(activity, page);
    }

    public List<LinearLayout> pages() {
        return pages;
    }

    public void setPageChrome(String title, boolean back, Runnable onBack) {
        if (appBar == null) return;
        appBar.setTitle(title);
        if (back) {
            appBar.getToolbar().setNavigationIcon(com.hchen.himiuix.R.drawable.miuix_back);
            appBar.getToolbar().setNavigationOnClickListener(view -> {
                if (onBack != null) onBack.run();
            });
        } else {
            appBar.getToolbar().setNavigationIcon(null);
            appBar.getToolbar().setNavigationOnClickListener(null);
        }
    }

    public void setTabBarVisible(boolean visible) {
        if (tabBarContainer == null) return;
        if (visible) {
            tabBarContainer.setVisibility(View.VISIBLE);
        } else {
            tabBarContainer.animate().cancel();
            tabBarContainer.setVisibility(View.GONE);
            tabBarContainer.setAlpha(1f);
            tabBarContainer.setTranslationY(0f);
        }
    }

    public void checkTab(int itemId) {
        if (tabBar != null) tabBar.check(itemId);
    }

    public void scrollToTop() {
        if (scroll != null) scroll.scrollTo(0, 0);
    }

    /** Swaps the visible page with one shared motion language for tabs and drill-down pages. */
    public void showPage(LinearLayout target, PageTransition transition, boolean animate) {
        if (target == null) return;
        if (predictiveBack && target == predictiveTarget && transition == PageTransition.BACK) {
            finishPredictiveBack();
            return;
        }
        discardPredictiveBack();
        cancelPageAnimation();

        LinearLayout source = visiblePage;
        for (LinearLayout page : pages) {
            if (page != source && page != target) hidePage(page);
        }
        if (target == source && target.getVisibility() == View.VISIBLE) return;

        target.setVisibility(View.VISIBLE);
        resetPage(target);
        if (source != null) resetPage(source);
        if (!animate || source == null || source == target || !target.isLaidOut()) {
            completePage(target);
            return;
        }
        switch (transition) {
            case TAB -> slideTabs(source, target);
            case FORWARD -> slideForward(source, target);
            case BACK -> slideBack(source, target);
            default -> completePage(target);
        }
    }

    /** Starts an interactive back preview over the primary page. */
    public boolean beginPredictiveBack(LinearLayout target) {
        if (target == null || visiblePage == null || target == visiblePage) return false;
        discardPredictiveBack();
        cancelPageAnimation();

        predictiveSource = visiblePage;
        predictiveTarget = target;
        predictiveBack = true;
        for (LinearLayout page : pages) {
            if (page != predictiveSource && page != predictiveTarget) hidePage(page);
        }

        resetPage(predictiveTarget);
        predictiveTarget.setVisibility(View.VISIBLE);
        predictiveTarget.setAlpha(0.72f);
        predictiveTarget.setTranslationX(-dp(activity, 14));
        predictiveTarget.setTranslationZ(0f);
        resetPage(predictiveSource);
        predictiveSource.setTranslationZ(dp(activity, 1));

        if (tabBarContainer != null) {
            tabBarContainer.animate().cancel();
            tabBarContainer.setVisibility(View.VISIBLE);
            tabBarContainer.setAlpha(0f);
            tabBarContainer.setTranslationY(dp(activity, 8));
        }
        return true;
    }

    /** Tracks the system back gesture between 0 and 1. */
    public void updatePredictiveBack(float progress) {
        if (!predictiveBack || predictiveSource == null || predictiveTarget == null) return;
        float value = Math.max(0f, Math.min(1f, progress));
        float width = Math.max(1, predictiveSource.getWidth());
        predictiveSource.setTranslationX(width * 0.92f * value);
        predictiveSource.setAlpha(1f - 0.58f * value);
        predictiveTarget.setTranslationX(-dp(activity, 14) * (1f - value));
        predictiveTarget.setAlpha(0.72f + 0.28f * value);
        if (tabBarContainer != null) {
            tabBarContainer.setAlpha(value);
            tabBarContainer.setTranslationY(dp(activity, 8) * (1f - value));
        }
    }

    /** Restores the secondary page when the user cancels the gesture. */
    public void cancelPredictiveBack() {
        if (!predictiveBack || predictiveSource == null || predictiveTarget == null) return;
        LinearLayout source = predictiveSource;
        LinearLayout target = predictiveTarget;
        predictiveBack = false;
        cancelPageAnimation();

        AnimatorSet restore = new AnimatorSet();
        restore.playTogether(
                ObjectAnimator.ofFloat(source, View.TRANSLATION_X,
                        source.getTranslationX(), 0f),
                ObjectAnimator.ofFloat(source, View.ALPHA, source.getAlpha(), 1f),
                ObjectAnimator.ofFloat(target, View.TRANSLATION_X,
                        target.getTranslationX(), -dp(activity, 14)),
                ObjectAnimator.ofFloat(target, View.ALPHA, target.getAlpha(), 0f));
        if (tabBarContainer != null) {
            restore.playTogether(
                    ObjectAnimator.ofFloat(tabBarContainer, View.ALPHA,
                            tabBarContainer.getAlpha(), 0f),
                    ObjectAnimator.ofFloat(tabBarContainer, View.TRANSLATION_Y,
                            tabBarContainer.getTranslationY(), dp(activity, 8)));
        }
        restore.setDuration(180);
        restore.setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f));
        runPageAnimation(restore, () -> {
            hidePage(target);
            resetPage(source);
            source.setVisibility(View.VISIBLE);
            visiblePage = source;
            clearPredictivePages();
            if (tabBarContainer != null) {
                tabBarContainer.setVisibility(View.GONE);
                tabBarContainer.setAlpha(1f);
                tabBarContainer.setTranslationY(0f);
            }
        });
    }

    private void finishPredictiveBack() {
        if (!predictiveBack || predictiveSource == null || predictiveTarget == null) return;
        LinearLayout source = predictiveSource;
        LinearLayout target = predictiveTarget;
        predictiveBack = false;
        cancelPageAnimation();

        float width = Math.max(1, source.getWidth());
        AnimatorSet commit = new AnimatorSet();
        commit.playTogether(
                ObjectAnimator.ofFloat(source, View.TRANSLATION_X,
                        source.getTranslationX(), width),
                ObjectAnimator.ofFloat(source, View.ALPHA, source.getAlpha(), 0f),
                ObjectAnimator.ofFloat(target, View.TRANSLATION_X,
                        target.getTranslationX(), 0f),
                ObjectAnimator.ofFloat(target, View.ALPHA, target.getAlpha(), 1f));
        if (tabBarContainer != null) {
            commit.playTogether(
                    ObjectAnimator.ofFloat(tabBarContainer, View.ALPHA,
                            tabBarContainer.getAlpha(), 1f),
                    ObjectAnimator.ofFloat(tabBarContainer, View.TRANSLATION_Y,
                            tabBarContainer.getTranslationY(), 0f));
        }
        commit.setDuration(190);
        commit.setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f));
        runPageAnimation(commit, () -> {
            completePage(target);
            clearPredictivePages();
            if (tabBarContainer != null) {
                tabBarContainer.setVisibility(View.VISIBLE);
                tabBarContainer.setAlpha(1f);
                tabBarContainer.setTranslationY(0f);
            }
        });
    }

    private void slideTabs(LinearLayout source, LinearLayout target) {
        boolean forward = pages.indexOf(target) > pages.indexOf(source);
        float distance = Math.max(dp(activity, 48), pageFrame.getWidth() * 0.34f);
        float targetStart = forward ? distance : -distance;
        float sourceEnd = forward ? -distance * 0.45f : distance * 0.45f;

        target.setAlpha(0.6f);
        target.setTranslationX(targetStart);
        source.setAlpha(1f);
        source.setTranslationX(0f);

        AnimatorSet animation = new AnimatorSet();
        animation.playTogether(
                ObjectAnimator.ofFloat(source, View.ALPHA, 1f, 0f),
                ObjectAnimator.ofFloat(source, View.TRANSLATION_X, 0f, sourceEnd),
                ObjectAnimator.ofFloat(target, View.ALPHA, 0.6f, 1f),
                ObjectAnimator.ofFloat(target, View.TRANSLATION_X, targetStart, 0f));
        animation.setDuration(280);
        animation.setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f));
        runPageAnimation(animation, () -> completePage(target));
    }

    private void slideForward(LinearLayout source, LinearLayout target) {
        target.setAlpha(0f);
        target.setTranslationX(dp(activity, 30));
        target.setTranslationZ(dp(activity, 1));
        source.setTranslationZ(0f);

        AnimatorSet animation = new AnimatorSet();
        animation.playTogether(
                ObjectAnimator.ofFloat(source, View.ALPHA, 1f, 0.25f),
                ObjectAnimator.ofFloat(source, View.TRANSLATION_X, 0f, -dp(activity, 22)),
                ObjectAnimator.ofFloat(target, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(target, View.TRANSLATION_X, dp(activity, 30), 0f));
        animation.setDuration(270);
        animation.setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f));
        runPageAnimation(animation, () -> completePage(target));
    }

    private void slideBack(LinearLayout source, LinearLayout target) {
        target.setAlpha(0.72f);
        target.setTranslationX(-dp(activity, 14));
        target.setTranslationZ(0f);
        source.setTranslationZ(dp(activity, 1));

        AnimatorSet animation = new AnimatorSet();
        animation.playTogether(
                ObjectAnimator.ofFloat(source, View.ALPHA, 1f, 0f),
                ObjectAnimator.ofFloat(source, View.TRANSLATION_X, 0f, dp(activity, 48)),
                ObjectAnimator.ofFloat(target, View.ALPHA, 0.72f, 1f),
                ObjectAnimator.ofFloat(target, View.TRANSLATION_X, -dp(activity, 14), 0f));
        animation.setDuration(250);
        animation.setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f));
        runPageAnimation(animation, () -> completePage(target));
    }

    private void runPageAnimation(AnimatorSet animator, Runnable completion) {
        int token = ++pageAnimationToken;
        pageAnimator = animator;
        animator.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                if (token != pageAnimationToken) return;
                pageAnimator = null;
                completion.run();
            }
        });
        animator.start();
    }

    private void cancelPageAnimation() {
        pageAnimationToken++;
        if (pageAnimator != null) {
            pageAnimator.cancel();
            pageAnimator = null;
        }
    }

    private void completePage(LinearLayout target) {
        for (LinearLayout page : pages) {
            if (page != target) hidePage(page);
        }
        resetPage(target);
        target.setVisibility(View.VISIBLE);
        visiblePage = target;
    }

    private void hidePage(LinearLayout page) {
        page.setVisibility(View.GONE);
        resetPage(page);
    }

    private void resetPage(LinearLayout page) {
        page.setAlpha(1f);
        page.setTranslationX(0f);
        page.setTranslationY(0f);
        page.setTranslationZ(0f);
        page.setScaleX(1f);
        page.setScaleY(1f);
    }

    private void discardPredictiveBack() {
        if (!predictiveBack) return;
        predictiveBack = false;
        if (predictiveTarget != null) hidePage(predictiveTarget);
        if (predictiveSource != null) {
            resetPage(predictiveSource);
            predictiveSource.setVisibility(View.VISIBLE);
            visiblePage = predictiveSource;
        }
        clearPredictivePages();
    }

    private void clearPredictivePages() {
        predictiveSource = null;
        predictiveTarget = null;
    }

    private static View tabBarContainer(AppCompatActivity activity, PiliTabBar tabBar,
                                        boolean floating, View backdropSource) {
        LiquidGlassContainer frame = new LiquidGlassContainer(
                activity, backdropSource, floating, floating && UiStyle.glass(activity));
        tabBar.setDockHost(frame);
        if (floating) {
            frame.setElevation(dp(activity, UiStyle.glass(activity) ? 12 : 7));
            frame.addView(tabBar, new FrameLayout.LayoutParams(-2, dp(activity, 64)));
        } else {
            frame.setElevation(0);
            frame.addView(tabBar, new FrameLayout.LayoutParams(-1, dp(activity, 64)));
        }
        return frame;
    }

    private static FrameLayout.LayoutParams tabBarContainerParams(
            AppCompatActivity activity, boolean floating) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                floating ? -2 : -1, floating ? -2 : dp(activity, 64));
        params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        if (floating) {
            params.leftMargin = dp(activity, 16);
            params.rightMargin = dp(activity, 16);
            params.bottomMargin = dp(activity, 12);
        }
        return params;
    }

    private static void configureEdgeToEdge(AppCompatActivity activity) {
        Window window = activity.getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        window.setNavigationBarContrastEnforced(false);
        boolean dark = (activity.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(window, window.getDecorView());
        controller.setAppearanceLightStatusBars(!dark);
        controller.setAppearanceLightNavigationBars(!dark);
        WindowInsetsHelper.initialWindowInsetsListener(activity);
    }

    // ---- text ----

    public TextView heading(String value) {
        return heading(body, value);
    }

    /** Section label between groups of cards. */
    public TextView heading(LinearLayout parent, String value) {
        MiuixTextView text = new MiuixTextView(activity);
        text.setText(value);
        text.setTextSize(17);
        text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        text.setTextColor(color(com.hchen.himiuix.R.color.miuix_switch_on));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.leftMargin = dp(activity, 28);
        params.rightMargin = dp(activity, 28);
        params.topMargin = dp(activity, 12);
        params.bottomMargin = dp(activity, 6);
        parent.addView(text, params);
        return text;
    }

    public TextView overline(LinearLayout parent, String value) {
        MiuixTextView text = new MiuixTextView(activity);
        text.setText(value);
        text.setTextSize(13);
        text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        text.setTextColor(toneColor(parent, com.hchen.himiuix.R.color.miuix_summary_color));
        text.setLetterSpacing(0.04f);
        addText(parent, text);
        return text;
    }

    public TextView title(LinearLayout parent, String value) {
        MiuixTextView text = new MiuixTextView(activity);
        text.setText(value);
        text.setTextSize(24);
        text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        text.setTextColor(toneColor(parent, com.hchen.himiuix.R.color.miuix_title_color));
        addText(parent, text);
        return text;
    }

    public TextView bodyText(String value) {
        return bodyText(body, value);
    }

    public TextView bodyText(LinearLayout parent, String value) {
        MiuixTextView text = new MiuixTextView(activity);
        text.setText(value);
        text.setTextSize(16);
        text.setTextColor(toneColor(parent, com.hchen.himiuix.R.color.miuix_title_color));
        text.setLineSpacing(0, 1.25f);
        addText(parent, text);
        return text;
    }

    public TextView caption(String value) {
        return caption(body, value);
    }

    public TextView caption(LinearLayout parent, String value) {
        MiuixTextView text = new MiuixTextView(activity);
        text.setText(value);
        text.setTextSize(14);
        text.setTextColor(toneColor(parent, com.hchen.himiuix.R.color.miuix_summary_color));
        text.setLineSpacing(0, 1.25f);
        addText(parent, text);
        return text;
    }

    public void muted(TextView text) {
        text.setTextColor(color(com.hchen.himiuix.R.color.miuix_summary_color));
    }

    /** Colours a status line with the tone's foreground role. */
    public void setTone(TextView text, Tone tone) {
        text.setTextColor(foreground(tone));
    }

    private void addText(LinearLayout parent, TextView text) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.leftMargin = dp(activity, 16);
        params.rightMargin = dp(activity, 16);
        params.bottomMargin = dp(activity, 10);
        parent.addView(text, params);
    }

    // ---- containers ----

    public LinearLayout card() {
        return card(body);
    }

    public LinearLayout card(LinearLayout parent) {
        return cardWith(parent, UiStyle.material(activity)
                ? color(io.github.miam1ku.mibandoplusbridge.R.color.surface_container_low)
                : color(com.hchen.himiuix.R.color.miuix_basic_background_color));
    }

    /** Card with a Miuix section title. */
    public LinearLayout section(String title) {
        MiuixCard inner = cardWith(body, UiStyle.material(activity)
                ? color(io.github.miam1ku.mibandoplusbridge.R.color.surface_container_low)
                : color(com.hchen.himiuix.R.color.miuix_basic_background_color));
        inner.setDividerTitle(title);
        return inner;
    }

    /** Tinted card for something the user can act on now. */
    public LinearLayout noticeCard(Tone tone) {
        MiuixCard inner = cardWith(body, background(tone));
        inner.setTag(tone);
        return inner;
    }

    /** Collapsible block. The returned layout starts hidden. */
    public LinearLayout expander(String title) {
        return expander(body, title, null);
    }

    public LinearLayout expander(LinearLayout parent, String title, Runnable onOpen) {
        LinearLayout toggleParent = parent;
        if (parent == body) {
            toggleParent = card(parent);
        }

        MiuixBasicView toggle = new MiuixBasicView(activity);
        toggle.setTitle(title);
        toggle.setSummary("展开");
        toggle.setIndicator(com.hchen.himiuix.R.drawable.miuix_up_down);
        toggleParent.addView(toggle, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setVisibility(View.GONE);
        parent.addView(content, new LinearLayout.LayoutParams(-1, -2));
        toggle.setOnClickListener(view -> {
            boolean open = content.getVisibility() != View.VISIBLE;
            content.setVisibility(open ? View.VISIBLE : View.GONE);
            toggle.setSummary(open ? "收起" : "展开");
            if (open && onOpen != null) onOpen.run();
        });
        return content;
    }

    private MiuixCard cardWith(LinearLayout parent, int fill) {
        MiuixCard card = new MiuixCard(activity);
        CardView nativeCard = card.findViewById(com.hchen.himiuix.R.id.miuix_card);
        nativeCard.setCardBackgroundColor(fill);
        boolean material = UiStyle.material(activity);
        nativeCard.setRadius(dp(activity, material ? 14 : 22));
        nativeCard.setCardElevation(dp(activity, material ? 1 : 0));
        if (card.content != null) {
            card.content.setPadding(0, dp(activity, 10), 0, dp(activity, 6));
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(activity, 4);
        parent.addView(card, params);
        return card;
    }

    // ---- small widgets ----

    /** Rounded Miuix status label. */
    public TextView chip(LinearLayout parent, String text, Tone tone) {
        MiuixTextView chip = new MiuixTextView(activity);
        chip.setTextSize(13);
        chip.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        int h = dp(activity, 11);
        int v = dp(activity, 4);
        chip.setPadding(h, v, h, v);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
        params.leftMargin = dp(activity, 16);
        params.bottomMargin = dp(activity, 12);
        parent.addView(chip, params);
        setChip(chip, text, tone);
        return chip;
    }

    public void setChip(TextView chip, String text, Tone tone) {
        chip.setText(text);
        GradientDrawable shape = rounded(background(tone), dp(activity, 999), Color.TRANSPARENT, 0);
        chip.setBackground(new RippleDrawable(ColorStateList.valueOf(foreground(tone)), shape, null));
        chip.setTextColor(onBackground(tone));
    }

    /** One line of a setup checklist: ✓ done, ● current, ○ later. */
    public TextView stepRow(LinearLayout parent, int index, String title, StepState state) {
        MiuixTextView row = new MiuixTextView(activity);
        row.setTextSize(15);
        row.setPadding(dp(activity, 16), dp(activity, 5), dp(activity, 16), dp(activity, 5));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(activity, 2);
        parent.addView(row, params);
        setStep(row, index, title, state);
        return row;
    }

    public void setStep(TextView row, int index, String title, StepState state) {
        String mark = switch (state) {
            case DONE -> "✓";
            case CURRENT -> "●";
            case LATER -> "○";
        };
        row.setText(mark + "  " + index + ". " + title);
        row.setTypeface(state == StepState.CURRENT
                ? Typeface.create("sans-serif-medium", Typeface.NORMAL) : Typeface.DEFAULT);
        row.setTextColor(state == StepState.CURRENT
                ? color(com.hchen.himiuix.R.color.miuix_switch_on)
                : color(com.hchen.himiuix.R.color.miuix_summary_color));
    }

    /** Text row with a Material switch that remains correct when built inside a hidden page. */
    public ToggleRow switchRow(LinearLayout parent, String title, String caption) {
        ToggleRow row = new ToggleRow(activity, title, caption);
        parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
        return row;
    }

    // ---- actions ----

    public ActionRow filled(String title, Runnable action) {
        return filled(body, title, action);
    }

    public ActionRow outlined(String title, Runnable action) {
        return outlined(body, title, action);
    }

    public ActionRow textButton(String title, Runnable action) {
        return textButton(body, title, action);
    }

    public ActionRow filled(LinearLayout parent, String title, Runnable action) {
        return action(parent, title, action, ActionStyle.FILLED);
    }

    public ActionRow outlined(LinearLayout parent, String title, Runnable action) {
        return action(parent, title, action, ActionStyle.OUTLINED);
    }

    public ActionRow textButton(LinearLayout parent, String title, Runnable action) {
        return action(parent, title, action, ActionStyle.TEXT);
    }

    /** Action row in the error role for removal. */
    public ActionRow dangerOutlined(LinearLayout parent, String title, Runnable action) {
        return action(parent, title, action, ActionStyle.DANGER);
    }

    private ActionRow action(LinearLayout parent, String title, Runnable action, ActionStyle style) {
        ActionRow row = new ActionRow(activity, style);
        row.setText(title);
        row.setOnClickListener(view -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.leftMargin = dp(activity, 12);
        params.rightMargin = dp(activity, 12);
        params.bottomMargin = dp(activity, 9);
        parent.addView(row, params);
        return row;
    }

    public MiuixBasicView navRow(String title, Runnable action) {
        return navRow(body, title, action);
    }

    /** Full-width Miuix list row with a trailing chevron. */
    public MiuixBasicView navRow(LinearLayout parent, String title, Runnable action) {
        MiuixBasicView row = new MiuixBasicView(activity);
        row.setTitle(title);
        row.setOnClickListener(view -> action.run());
        parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
        return row;
    }

    public void setLastChildMargin(LinearLayout parent, int space) {
        LinearLayout content = parent instanceof MiuixCard card ? card.content : parent;
        int count = content.getChildCount();
        if (count == 0) return;
        View last = content.getChildAt(count - 1);
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) last.getLayoutParams();
        params.bottomMargin = dp(activity, space);
        last.setLayoutParams(params);
    }

    public static final class ToggleRow extends LinearLayout {
        public interface OnStateChangeListener {
            boolean onStateChange(boolean checked);
        }

        private final MaterialSwitch toggle;
        private boolean updating;

        ToggleRow(Context context, String title, String summary) {
            super(context);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(dp(context, 20), dp(context, 14), dp(context, 16), dp(context, 14));
            setClickable(true);

            LinearLayout copy = new LinearLayout(context);
            copy.setOrientation(VERTICAL);
            addView(copy, new LayoutParams(0, -2, 1f));

            TextView titleView = new MiuixTextView(context);
            titleView.setText(title);
            titleView.setTextSize(16);
            titleView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            titleView.setTextColor(ContextCompat.getColor(context,
                    com.hchen.himiuix.R.color.miuix_title_color));
            copy.addView(titleView, new LayoutParams(-1, -2));

            TextView summaryView = new MiuixTextView(context);
            summaryView.setText(summary);
            summaryView.setTextSize(13);
            summaryView.setTextColor(ContextCompat.getColor(context,
                    com.hchen.himiuix.R.color.miuix_summary_color));
            summaryView.setLineSpacing(0, 1.15f);
            LayoutParams summaryParams = new LayoutParams(-1, -2);
            summaryParams.topMargin = dp(context, 3);
            copy.addView(summaryView, summaryParams);

            toggle = new MaterialSwitch(context);
            int on = ContextCompat.getColor(context, com.hchen.himiuix.R.color.miuix_switch_on);
            int off = ContextCompat.getColor(context, com.hchen.himiuix.R.color.miuix_switch_off);
            toggle.setTrackTintList(new ColorStateList(
                    new int[][]{{android.R.attr.state_checked}, {}},
                    new int[]{on, off}));
            toggle.setThumbTintList(ColorStateList.valueOf(Color.WHITE));
            LayoutParams toggleParams = new LayoutParams(-2, -2);
            toggleParams.leftMargin = dp(context, 14);
            addView(toggle, toggleParams);

            setOnClickListener(view -> toggle.toggle());
        }

        public void setChecked(boolean checked) {
            updating = true;
            toggle.setChecked(checked);
            updating = false;
        }

        public boolean isChecked() {
            return toggle.isChecked();
        }

        public void setOnStateChangeListener(OnStateChangeListener listener) {
            toggle.setOnCheckedChangeListener((button, checked) -> {
                if (updating || listener == null) return;
                if (listener.onStateChange(checked)) return;
                updating = true;
                button.setChecked(!checked);
                updating = false;
            });
        }
    }

    public static final class ActionRow extends MiuixTextView {
        private final Context context;
        private final ActionStyle style;

        ActionRow(Context context, ActionStyle style) {
            super(context);
            this.context = context;
            this.style = style;
            setGravity(Gravity.CENTER);
            setMinHeight(dp(context, 50));
            setPadding(dp(context, 16), dp(context, 12), dp(context, 16), dp(context, 12));
            setTextSize(16);
            setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            setClickable(true);
            setFocusable(true);
            restyle();
        }

        @Override
        public void setEnabled(boolean enabled) {
            super.setEnabled(enabled);
            setAlpha(enabled ? 1f : 0.45f);
            restyle();
        }

        private void restyle() {
            boolean dark = (context.getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
            boolean material = UiStyle.material(context);
            int blue = ContextCompat.getColor(context,
                    com.hchen.himiuix.R.color.miuix_switch_on);
            int soft = ContextCompat.getColor(context,
                    com.hchen.himiuix.R.color.miuix_edit_background);
            int error = dark ? Color.rgb(255, 107, 99)
                    : ContextCompat.getColor(context,
                    io.github.miam1ku.mibandoplusbridge.R.color.error);
            int errorSoft = dark ? Color.rgb(68, 35, 33)
                    : ContextCompat.getColor(context,
                    io.github.miam1ku.mibandoplusbridge.R.color.error_container);
            int radius = dp(context, material ? 999 : 15);
            Drawable background = switch (style) {
                case FILLED -> rounded(blue, radius, Color.TRANSPARENT, 0);
                case OUTLINED -> rounded(soft, radius, blue, 1);
                case TEXT -> rounded(Color.TRANSPARENT, radius, Color.TRANSPARENT, 0);
                case DANGER -> rounded(errorSoft, radius, error, 1);
            };
            int textColor = switch (style) {
                case FILLED -> Color.WHITE;
                case OUTLINED, TEXT -> blue;
                case DANGER -> error;
            };
            setTextColor(textColor);
            setBackground(new RippleDrawable(ColorStateList.valueOf(withAlpha(textColor, 38)),
                    background, null));
        }
    }

    private static final class MiuixCard extends MiuixViewGroup {
        final LinearLayout content;

        MiuixCard(Context context) {
            super(context);
            content = findViewById(com.hchen.himiuix.R.id.miuix_group);
        }

        @Override
        public void removeAllViews() {
            if (content != null) content.removeAllViews();
        }
    }

    private int background(Tone tone) {
        return switch (tone) {
            case POSITIVE -> color(com.hchen.himiuix.R.color.miuix_item_choose_background);
            case ACTIVE -> withAlpha(color(com.hchen.himiuix.R.color.miuix_switch_on), 30);
            case NEUTRAL -> color(com.hchen.himiuix.R.color.miuix_edit_background);
            case ATTENTION -> color(io.github.miam1ku.mibandoplusbridge.R.color.error_container);
        };
    }

    private int onBackground(Tone tone) {
        return switch (tone) {
            case POSITIVE, ACTIVE -> color(com.hchen.himiuix.R.color.miuix_switch_on);
            case NEUTRAL -> color(com.hchen.himiuix.R.color.miuix_summary_color);
            case ATTENTION -> color(io.github.miam1ku.mibandoplusbridge.R.color.on_error_container);
        };
    }

    private int foreground(Tone tone) {
        return switch (tone) {
            case POSITIVE, ACTIVE -> color(com.hchen.himiuix.R.color.miuix_switch_on);
            case NEUTRAL -> color(com.hchen.himiuix.R.color.miuix_summary_color);
            case ATTENTION -> color(io.github.miam1ku.mibandoplusbridge.R.color.error);
        };
    }

    private int toneColor(LinearLayout parent, int fallback) {
        Tone tone = tone(parent);
        return tone == null ? color(fallback) : onBackground(tone);
    }

    private Tone tone(LinearLayout parent) {
        Object tag = parent.getTag();
        return tag instanceof Tone value ? value : null;
    }

    private int color(int resource) {
        return ContextCompat.getColor(activity, resource);
    }

    public static GradientDrawable roundedRect(int fill, float radius, int stroke, int width) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(radius);
        if (width > 0) shape.setStroke(width, stroke);
        return shape;
    }

    private static GradientDrawable rounded(int fill, float radius, int stroke, int width) {
        return roundedRect(fill, radius, stroke, width);
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private static int navigationBarHeight(Context context) {
        int id = context.getResources().getIdentifier(
                "navigation_bar_height", "dimen", "android");
        return id == 0 ? 0 : context.getResources().getDimensionPixelSize(id);
    }

    public static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
