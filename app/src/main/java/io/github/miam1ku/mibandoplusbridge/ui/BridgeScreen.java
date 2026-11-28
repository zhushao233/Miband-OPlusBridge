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
import android.graphics.drawable.ColorDrawable;
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
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;
import com.hchen.himiuix.MiuixBasicView;
import com.hchen.himiuix.MiuixViewGroup;
import com.hchen.himiuix.fill.MiuixFillView;
import com.hchen.himiuix.widget.MiuixTextView;
import com.itos.coui.host.CouiSwitchView;
import com.itos.coui.host.CouiTabBarView;
import com.itos.coui.host.CouiTopBarView;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared HiMiuix layout for every bridge screen.
 * Pages read top to bottom: status, next step, features, help, management, advanced.
 */
public final class BridgeScreen {
    private static final int TOP_BAR_HEIGHT_DP = 52;
    private static final int TAB_BAR_CLEARANCE_DP = 104;

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
    private int contentInsetLeft;
    private int contentInsetTop;
    private int contentInsetRight;
    private final AppCompatActivity activity;
    private final CouiTopBarView topBar;
    private final CouiNestedScrollView scroll;
    private final CouiTabBarView tabBar;
    private final View tabBarContainer;
    private final MiuixFillView pageHost;
    private final FrameLayout pageFrame;
    private final List<LinearLayout> pages = new ArrayList<>();
    private LinearLayout visiblePage;
    private AnimatorSet pageAnimator;
    private int pageAnimationToken;
    private int bottomClearance;
    private boolean predictiveBack;
    private LinearLayout predictiveSource;
    private LinearLayout predictiveTarget;
    private float lastPredictiveSampleProgress = -1f;
    private int predictiveSampleFrames;

    private BridgeScreen(AppCompatActivity activity, LinearLayout body) {
        this(activity, body, null, null, null, null, null, null);
    }

    private BridgeScreen(AppCompatActivity activity, LinearLayout body, CouiTopBarView topBar,
                         CouiNestedScrollView scroll, MiuixFillView pageHost, FrameLayout pageFrame,
                         CouiTabBarView tabBar, View tabBarContainer) {
        this.activity = activity;
        this.body = body;
        this.topBar = topBar;
        this.scroll = scroll;
        this.pageHost = pageHost;
        this.pageFrame = pageFrame;
        this.tabBar = tabBar;
        this.tabBarContainer = tabBarContainer;
    }

    public static BridgeScreen attach(AppCompatActivity activity, String title, boolean back) {
        configureEdgeToEdge(activity);

        FrameLayout root = new FrameLayout(activity);
        root.setBackgroundResource(surfaceRes(activity));

        CouiNestedScrollView scroll = new CouiNestedScrollView(activity);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setBackgroundResource(surfaceRes(activity));

        MiuixFillView body = new MiuixFillView(activity);
        body.setBackgroundResource(surfaceRes(activity));
        body.setPadding(0, dp(activity, TOP_BAR_HEIGHT_DP), 0,
                navigationBarHeight(activity) + dp(activity, 24));
        scroll.addView(body, new NestedScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        CouiTopBarView topBar = new CouiTopBarView(activity, scroll);
        topBar.setTitle(title);
        topBar.setBackVisible(back);
        topBar.setLargeTitle(!back);
        topBar.setContourGlowEnabled(UiStyle.contourGlow(activity));
        if (back) topBar.setOnBackClickListener(activity::finish);
        root.addView(topBar, new FrameLayout.LayoutParams(-1, -1));
        activity.setContentView(root);
        scroll.setOnContentMovedListener(topBar::captureBackdropNow);
        topBar.requestCapture();
        BridgeScreen screen = new BridgeScreen(activity, body, topBar, scroll, null, null, null, null);
        screen.applyTopBarPadding();

        ViewCompat.setOnApplyWindowInsetsListener(scroll, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            screen.contentInsetLeft = bars.left;
            screen.contentInsetTop = bars.top;
            screen.contentInsetRight = bars.right;
            body.setPadding(bars.left, bars.top + topBar.contentHeightPx(), bars.right,
                    Math.max(bars.bottom, navigationBarHeight(activity)) + dp(activity, 24));
            return insets;
        });
        return screen;
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
        shell.setBackgroundResource(surfaceRes(activity));

        CouiNestedScrollView scroll = new CouiNestedScrollView(activity);
        scroll.setFillViewport(false);
        scroll.setClipToPadding(false);
        scroll.setBackgroundResource(surfaceRes(activity));

        MiuixFillView pageHost = new MiuixFillView(activity);
        pageHost.setOrientation(LinearLayout.VERTICAL);
        pageHost.setBackgroundResource(surfaceRes(activity));
        pageHost.setPadding(0, dp(activity, TOP_BAR_HEIGHT_DP), 0, 0);
        FrameLayout pageFrame = new FrameLayout(activity);
        pageFrame.setClipChildren(true);
        pageFrame.setBackgroundResource(surfaceRes(activity));
        pageHost.addView(pageFrame, new LinearLayout.LayoutParams(-1, -2));
        scroll.addView(pageHost, new NestedScrollView.LayoutParams(-1, -2));
        shell.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        CouiTopBarView topBar = new CouiTopBarView(activity, scroll);
        topBar.setTitle(title);
        topBar.setBackVisible(false);
        topBar.setLargeTitle(true);
        topBar.setContourGlowEnabled(UiStyle.contourGlow(activity));
        shell.addView(topBar, new FrameLayout.LayoutParams(-1, -1));

        CouiTabBarView tabBar = new CouiTabBarView(activity, scroll);
        tabBar.setMenu(menuRes, checkedItemId);
        tabBar.setOnTabSelectedListener(itemId -> {
            if (listener != null) listener.onTabSelected(itemId);
        });
        tabBar.setRightAction(io.github.miam1ku.mibandoplusbridge.R.drawable.ic_sync,
                "同步手环",
                Color.WHITE,
                Color.rgb(0x41, 0x77, 0xF0));
        tabBar.setRightActionVisible(UiStyle.syncActionVisible(activity));
        tabBar.setContourGlowEnabled(UiStyle.contourGlow(activity));

        shell.addView(tabBar, new FrameLayout.LayoutParams(-1, -1));

        activity.setContentView(shell);
        scroll.setOnContentMovedListener(() -> {
            topBar.captureBackdropNow();
            tabBar.captureBackdropNow();
        });
        topBar.requestCapture();
        tabBar.requestCapture();
        BridgeScreen screen = new BridgeScreen(activity, pageHost, topBar, scroll, pageHost,
                pageFrame, tabBar, tabBar);
        screen.bottomClearance = dp(activity, TAB_BAR_CLEARANCE_DP);
        scroll.setMinimumContentOverflow(topBar.collapseRangePx());
        screen.applyTopBarPadding();
        ViewCompat.setOnApplyWindowInsetsListener(scroll, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            screen.contentInsetLeft = bars.left;
            screen.contentInsetTop = bars.top;
            screen.contentInsetRight = bars.right;
            pageHost.setPadding(bars.left, bars.top + topBar.contentHeightPx(),
                    bars.right, 0);
            screen.bottomClearance = dp(activity, TAB_BAR_CLEARANCE_DP) + bars.bottom;
            for (LinearLayout page : screen.pages) {
                page.setPadding(0, 0, 0, screen.bottomClearance);
                screen.fillPageViewport(page);
            }
            return insets;
        });
        return screen;
    }

    /** Adds a page below the main shell and returns helpers bound to that page. */
    public BridgeScreen page() {
        if (pageFrame == null) throw new IllegalStateException("PAGES_REQUIRE_TABS");
        LinearLayout page = new LinearLayout(activity);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundResource(surfaceRes(activity));
        page.setPadding(0, 0, 0, bottomClearance);
        page.setVisibility(View.GONE);
        pageFrame.addView(page, new FrameLayout.LayoutParams(-1, -2));
        page.post(() -> fillPageViewport(page));
        pages.add(page);
        return new BridgeScreen(activity, page);
    }

    public List<LinearLayout> pages() {
        return pages;
    }

    public void setPageChrome(String title, boolean back, Runnable onBack) {
        if (topBar == null) return;
        topBar.setTitle(title);
        topBar.setBackVisible(back);
        topBar.setLargeTitle(!back);
        if (scroll != null) {
            scroll.setMinimumContentOverflow(back ? 0 : topBar.collapseRangePx());
        }
        applyTopBarPadding();
        if (scroll != null) scroll.markContentDirty();
        if (back) {
            topBar.setOnBackClickListener(() -> {
                if (onBack != null) onBack.run();
            });
        } else {
            topBar.setOnBackClickListener(null);
        }
    }

    public void setContourGlowEnabled(boolean enabled) {
        if (tabBar != null) tabBar.setContourGlowEnabled(enabled);
        if (topBar != null) topBar.setContourGlowEnabled(enabled);
    }

    public void setSyncAction(Runnable action) {
        if (tabBar != null) tabBar.setOnRightActionClickListener(action);
    }

    public void setSyncActionVisible(boolean visible) {
        if (tabBar != null) tabBar.setRightActionVisible(visible);
    }

    public void setSyncActionSpinning(boolean spinning) {
        if (tabBar != null) tabBar.setRightActionSpinning(spinning);
    }

    public void setTabBarVisible(boolean visible) {
        if (tabBarContainer == null) return;
        if (visible) {
            tabBarContainer.setVisibility(View.VISIBLE);
            if (tabBar != null) tabBar.requestCapture();
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
        if (scroll == null) return;
        scroll.clearFlingImpulse();
        scroll.scrollTo(0, 0);
    }

    /** Swaps the visible page with one shared motion language for tabs and drill-down pages. */
    public void showPage(LinearLayout target, PageTransition transition, boolean animate) {
        if (target == null) return;
        if (predictiveBack && target == predictiveTarget && transition == PageTransition.BACK) {
            finishPredictiveBack();
            return;
        }
        discardPredictiveBack();
        boolean interrupting = pageAnimator != null;
        cancelPageAnimation();

        LinearLayout source = interrupting ? currentDominantPage() : visiblePage;
        fillPageViewport(target);
        fillPageViewport(source);
        if (!interrupting || transition != PageTransition.TAB) {
            for (LinearLayout page : pages) {
                if (page != source && page != target) hidePage(page);
            }
        }
        if (target == source && target.getVisibility() == View.VISIBLE && !interrupting
                && Math.abs(target.getTranslationX()) < 0.5f) return;

        boolean targetVisible = target.getVisibility() == View.VISIBLE;
        target.setVisibility(View.VISIBLE);
        if (!animate || source == null
                || (source == target && (transition != PageTransition.TAB || !interrupting))
                || !target.isLaidOut()) {
            completePage(target);
            return;
        }
        switch (transition) {
            case TAB -> slideTabs(target, targetVisible, source);
            case FORWARD -> {
                resetPage(target);
                resetPage(source);
                slideForward(source, target);
            }
            case BACK -> {
                resetPage(target);
                resetPage(source);
                slideBack(source, target);
            }
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
        lastPredictiveSampleProgress = -1f;
        predictiveSampleFrames = 0;
        for (LinearLayout page : pages) {
            if (page != predictiveSource && page != predictiveTarget) hidePage(page);
        }

        resetPage(predictiveTarget);
        fillPageViewport(predictiveTarget);
        fillPageViewport(predictiveSource);
        predictiveTarget.setVisibility(View.VISIBLE);
        predictiveTarget.setTranslationX(-pageWidth() * 0.32f);
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
        predictiveSource.setTranslationX(width * value);
        predictiveTarget.setTranslationX(-pageWidth() * 0.32f * (1f - value));
        if (tabBarContainer != null) {
            tabBarContainer.setAlpha(value);
            tabBarContainer.setTranslationY(dp(activity, 8) * (1f - value));
        }
        predictiveSampleFrames++;
        if (lastPredictiveSampleProgress < 0f
                || Math.abs(value - lastPredictiveSampleProgress) >= 0.02f
                || predictiveSampleFrames >= 3) {
            lastPredictiveSampleProgress = value;
            predictiveSampleFrames = 0;
            sampleBars();
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
                ObjectAnimator.ofFloat(target, View.TRANSLATION_X,
                        target.getTranslationX(), -pageWidth() * 0.32f));
        if (tabBarContainer != null) {
            restore.playTogether(
                    ObjectAnimator.ofFloat(tabBarContainer, View.ALPHA,
                            tabBarContainer.getAlpha(), 0f),
                    ObjectAnimator.ofFloat(tabBarContainer, View.TRANSLATION_Y,
                            tabBarContainer.getTranslationY(), dp(activity, 8)));
        }
        restore.setDuration(180);
        restore.setInterpolator(new FastOutSlowInInterpolator());
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
                ObjectAnimator.ofFloat(target, View.TRANSLATION_X,
                        target.getTranslationX(), 0f));
        if (tabBarContainer != null) {
            commit.playTogether(
                    ObjectAnimator.ofFloat(tabBarContainer, View.ALPHA,
                            tabBarContainer.getAlpha(), 1f),
                    ObjectAnimator.ofFloat(tabBarContainer, View.TRANSLATION_Y,
                            tabBarContainer.getTranslationY(), 0f));
        }
        commit.setDuration(190);
        commit.setInterpolator(new FastOutSlowInInterpolator());
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

    private void slideTabs(LinearLayout target, boolean targetVisible, LinearLayout dominant) {
        int targetIndex = pages.indexOf(target);
        float distance = pageWidth();
        boolean forward = dominant == null || dominant == target
                || pages.indexOf(dominant) < targetIndex;
        target.setTranslationX(targetVisible
                ? target.getTranslationX()
                : (forward ? distance : -distance));
        target.setTranslationZ(dp(activity, 1));

        List<Animator> slides = new ArrayList<>();
        for (LinearLayout page : pages) {
            if (page == target) {
                slides.add(ObjectAnimator.ofFloat(page, View.TRANSLATION_X,
                        page.getTranslationX(), 0f));
                continue;
            }
            int index = pages.indexOf(page);
            float end = index < targetIndex ? -distance : distance;
            if (page.getVisibility() != View.VISIBLE) continue;
            page.setTranslationZ(0f);
            float start = page.getTranslationX();
            if (Math.abs(start - end) < 0.5f) {
                page.setTranslationX(end);
                hidePage(page);
                continue;
            }
            slides.add(ObjectAnimator.ofFloat(page, View.TRANSLATION_X, start, end));
        }
        if (slides.isEmpty()) {
            completePage(target);
            return;
        }
        AnimatorSet animation = new AnimatorSet();
        animation.playTogether(slides);
        animation.setDuration(320);
        animation.setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f));
        runPageAnimation(animation, () -> completePage(target));
    }

    private LinearLayout currentDominantPage() {
        LinearLayout best = null;
        float bestDistance = Float.MAX_VALUE;
        for (LinearLayout page : pages) {
            if (page.getVisibility() != View.VISIBLE) continue;
            float distance = Math.abs(page.getTranslationX());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = page;
            }
        }
        return best == null ? visiblePage : best;
    }

    private void slideForward(LinearLayout source, LinearLayout target) {
        float distance = pageWidth();
        target.setTranslationX(distance);
        target.setTranslationZ(dp(activity, 1));
        source.setTranslationX(0f);
        source.setTranslationZ(0f);

        AnimatorSet animation = new AnimatorSet();
        animation.playTogether(
                ObjectAnimator.ofFloat(source, View.TRANSLATION_X, 0f, -distance * 0.32f),
                ObjectAnimator.ofFloat(target, View.TRANSLATION_X, distance, 0f));
        animation.setDuration(350);
        animation.setInterpolator(new FastOutSlowInInterpolator());
        runPageAnimation(animation, () -> completePage(target));
    }

    private void slideBack(LinearLayout source, LinearLayout target) {
        float distance = pageWidth();
        target.setTranslationX(-distance * 0.32f);
        target.setTranslationZ(0f);
        source.setTranslationX(0f);
        source.setTranslationZ(dp(activity, 1));

        AnimatorSet animation = new AnimatorSet();
        animation.playTogether(
                ObjectAnimator.ofFloat(source, View.TRANSLATION_X, 0f, distance),
                ObjectAnimator.ofFloat(target, View.TRANSLATION_X, -distance * 0.32f, 0f));
        animation.setDuration(320);
        animation.setInterpolator(new FastOutSlowInInterpolator());
        runPageAnimation(animation, () -> completePage(target));
    }

    private float pageWidth() {
        int width = pageFrame == null ? 0 : pageFrame.getWidth();
        return width > 0 ? width : dp(activity, 360);
    }

    private void fillPageViewport(LinearLayout page) {
        if (page == null || scroll == null || pageHost == null) return;
        int height = scroll.getHeight() - pageHost.getPaddingTop();
        if (height <= 0) return;
        page.setMinimumHeight(height);
        if (pageFrame != null) pageFrame.setMinimumHeight(height);
    }

    private void runPageAnimation(AnimatorSet animator, Runnable completion) {
        int token = ++pageAnimationToken;
        pageAnimator = animator;
        if (scroll != null) scroll.setAnimationSampling(true);
        animator.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                if (token != pageAnimationToken) return;
                pageAnimator = null;
                if (scroll != null) scroll.setAnimationSampling(false);
                completion.run();
                sampleBarsAfterLayout();
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
        if (scroll != null) scroll.setAnimationSampling(false);
    }

    private void completePage(LinearLayout target) {
        for (LinearLayout page : pages) {
            if (page != target) hidePage(page);
        }
        resetPage(target);
        target.setVisibility(View.VISIBLE);
        visiblePage = target;
        sampleBarsAfterLayout();
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
        sampleBarsAfterLayout();
    }

    /** Re-samples the page once the new chrome has been laid out. */
    private void sampleBarsAfterLayout() {
        if (scroll == null) return;
        scroll.markContentDirty();
        scroll.post(this::sampleBars);
    }

    private void sampleBars() {
        if (scroll != null) scroll.sampleContent();
    }

    /** Keeps the page content below the current top bar mode and window insets. */
    private void applyTopBarPadding() {
        if (body == null || topBar == null) return;
        body.setPadding(contentInsetLeft, contentInsetTop + topBar.contentHeightPx(),
                contentInsetRight, body.getPaddingBottom());
    }

    private void clearPredictivePages() {
        predictiveSource = null;
        predictiveTarget = null;
    }

    private static void configureEdgeToEdge(AppCompatActivity activity) {
        Window window = activity.getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.setBackgroundDrawable(new ColorDrawable(ContextCompat.getColor(activity,
                surfaceRes(activity))));
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        window.setNavigationBarContrastEnforced(false);
        boolean dark = (activity.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(window, window.getDecorView());
        controller.setAppearanceLightStatusBars(!dark);
        controller.setAppearanceLightNavigationBars(!dark);
    }

    private static int surfaceRes(Context context) {
        return UiStyle.material(context)
                ? io.github.miam1ku.mibandoplusbridge.R.color.surface
                : com.hchen.himiuix.R.color.miuix_theme_color;
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

        private final CouiSwitchView toggle;

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

            toggle = new CouiSwitchView(context);
            toggle.setContourGlowEnabled(UiStyle.contourGlow(context));
            LayoutParams toggleParams = new LayoutParams(-2, -2);
            toggleParams.leftMargin = dp(context, 14);
            addView(toggle, toggleParams);

            setOnClickListener(view -> toggle.toggle());
        }

        public void setChecked(boolean checked) {
            toggle.setChecked(checked);
        }

        public boolean isChecked() {
            return toggle.isChecked();
        }

        public void setOnStateChangeListener(OnStateChangeListener listener) {
            toggle.setOnCheckedChangeListener(checked -> {
                if (listener != null && !listener.onStateChange(checked)) {
                    toggle.setChecked(!checked);
                }
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
