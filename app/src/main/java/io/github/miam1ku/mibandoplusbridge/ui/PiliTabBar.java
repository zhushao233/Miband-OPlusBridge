package io.github.miam1ku.mibandoplusbridge.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.PathInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import androidx.annotation.MenuRes;
import androidx.core.content.ContextCompat;
import io.github.miam1ku.mibandoplusbridge.R;
import java.util.ArrayList;
import java.util.List;

/**
 * Compact bottom navigation modelled after PiliGlass' floating liquid-glass dock.
 *
 * <p>The floating host supplies the translucent capsule; this view draws the
 * tabs, the sliding selection pill, and keeps icon/text colours consistent.
 */
public final class PiliTabBar extends FrameLayout {
    private static final float PRESS_SCALE_X = 1.40f;
    private static final float PRESS_SCALE_Y = 1.60f;
    public interface OnTabSelectedListener {
        void onTabSelected(int itemId);
    }

    private final boolean compact;
    private final boolean selectionGlass;
    private final LinearLayout row;
    private final LiquidGlassContainer indicator;
    private final List<Tab> tabs = new ArrayList<>();
    private final int touchSlop;
    private int selectedIndex;
    private boolean laidOut;
    private boolean dragging;
    private boolean pressFeedback;
    private boolean iconsRefracted;
    private float downX;
    private float downY;
    private float dragStartFraction;
    private float pressProgress;
    private float pressX;
    private float pressY;
    private int dragStartIndex;
    private ValueAnimator pressAnimator;
    private LiquidGlassContainer dockHost;
    private OnTabSelectedListener listener;

    public PiliTabBar(Context context, @MenuRes int menuRes, int checkedItemId,
                      boolean compact, View backdropSource) {
        super(context);
        this.compact = compact;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setClipToPadding(false);
        setClipChildren(false);
        setPadding(dp(4), dp(4), dp(4), dp(4));

        row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        selectionGlass = compact && UiStyle.glass(context);
        LayoutParams rowParams = compact
                ? new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT)
                : new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);

        indicator = new LiquidGlassContainer(
                context, backdropSource, compact, selectionGlass, true);
        if (selectionGlass) {
            addView(row, rowParams);
            addView(indicator, new LayoutParams(dp(1), dp(1)));
            row.bringToFront();
        } else {
            row.setElevation(dp(1));
            addView(indicator, new LayoutParams(dp(1), dp(1)));
            addView(row, rowParams);
        }

        inflateMenu(menuRes);
        select(indexOf(checkedItemId), false);
        applyPressProgress();
    }

    public void setOnTabSelectedListener(OnTabSelectedListener listener) {
        this.listener = listener;
    }

    public void setDockHost(LiquidGlassContainer dockHost) {
        this.dockHost = dockHost;
        indicator.setDockSurface(dockHost);
    }

    public void check(int itemId) {
        int index = indexOf(itemId);
        if (index >= 0 && index != selectedIndex) select(index, true);
    }

    public int getSelectedId() {
        if (tabs.isEmpty()) return View.NO_ID;
        return tabs.get(selectedIndex).id;
    }

    private void inflateMenu(int menuRes) {
        PopupMenu popup = new PopupMenu(getContext(), new View(getContext()));
        popup.getMenuInflater().inflate(menuRes, popup.getMenu());
        Menu menu = popup.getMenu();
        for (int index = 0; index < menu.size(); index++) {
            MenuItem item = menu.getItem(index);
            Drawable icon = item.getIcon();
            Tab tab = new Tab(getContext(), item.getItemId(), item.getTitle(), icon);
            tab.index = tabs.size();
            tabs.add(tab);
            row.addView(tab, compact
                    ? new LinearLayout.LayoutParams(dp(78), LayoutParams.MATCH_PARENT)
                    : new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));
        }
    }

    private int indexOf(int itemId) {
        for (int index = 0; index < tabs.size(); index++) {
            if (tabs.get(index).id == itemId) return index;
        }
        return tabs.isEmpty() ? -1 : 0;
    }

    private void select(int index, boolean animate) {
        if (tabs.isEmpty() || index < 0) return;
        int next = Math.min(index, tabs.size() - 1);
        int previous = selectedIndex;
        selectedIndex = next;
        for (int i = 0; i < tabs.size(); i++) tabs.get(i).setActive(i == selectedIndex);
        positionIndicator(animate && laidOut && previous != next);
    }

    private void positionIndicator(boolean animate) {
        if (tabs.isEmpty() || getWidth() == 0) {
            post(() -> positionIndicator(false));
            return;
        }
        updateIndicatorGeometry();
        float targetX = indicatorX(selectedIndex);
        indicator.animate().cancel();
        if (animate && laidOut && Math.abs(indicator.getX() - targetX) > 0.5f) {
            indicator.animate()
                    .x(targetX)
                    .setDuration(300)
                    .setInterpolator(new OvershootInterpolator(0.85f))
                    .start();
        } else {
            indicator.setX(targetX);
        }
        laidOut = true;
    }

    private void updateIndicatorGeometry() {
        int innerWidth = getWidth() - getPaddingLeft() - getPaddingRight();
        int innerHeight = getHeight() - getPaddingTop() - getPaddingBottom();
        int itemWidth = innerWidth / tabs.size();
        int horizontalInset = dp(2);
        int inset = dp(4);
        int baseWidth = Math.max(0, itemWidth - horizontalInset * 2);
        int baseHeight = Math.max(0, innerHeight - inset * 2);
        float scaleX = selectionGlass ? PRESS_SCALE_X : 1f;
        float scaleY = selectionGlass ? PRESS_SCALE_Y : 1f;
        int pillWidth = Math.round(baseWidth * scaleX);
        int pillHeight = Math.round(baseHeight * scaleY);

        LayoutParams params = (LayoutParams) indicator.getLayoutParams();
        if (params.width != pillWidth || params.height != pillHeight) {
            indicator.setLayoutParams(new LayoutParams(pillWidth, pillHeight));
            indicator.measure(
                    MeasureSpec.makeMeasureSpec(pillWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(pillHeight, MeasureSpec.EXACTLY));
            indicator.layout(0, 0, pillWidth, pillHeight);
        }
        indicator.setY(getPaddingTop() + inset - (pillHeight - baseHeight) * 0.5f);
    }

    private float indicatorX(float fraction) {
        int innerWidth = getWidth() - getPaddingLeft() - getPaddingRight();
        float itemWidth = innerWidth / (float) tabs.size();
        float baseWidth = itemWidth - dp(4);
        float scaleX = selectionGlass ? PRESS_SCALE_X : 1f;
        float left = getPaddingLeft() + fraction * itemWidth + dp(2);
        return left - (baseWidth * scaleX - baseWidth) * 0.5f;
    }

    private void moveIndicatorDuringDrag(float fraction) {
        if (tabs.isEmpty() || getWidth() == 0) return;
        updateIndicatorGeometry();
        indicator.animate().cancel();
        indicator.setX(indicatorX(fraction));
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            pressX = event.getX();
            pressY = event.getY();
            setPressFeedback(true);
        }
        boolean handled = super.dispatchTouchEvent(event);
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            setPressFeedback(false);
        }
        return handled;
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent event) {
        if (!compact || tabs.isEmpty()) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                dragStartIndex = selectedIndex;
                dragStartFraction = selectedIndex;
                dragging = false;
                return false;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getX() - downX;
                float dy = event.getY() - downY;
                if (!dragging && Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy)) {
                    dragging = true;
                    pressX = event.getX();
                    pressY = event.getY();
                    getParent().requestDisallowInterceptTouchEvent(true);
                    updateDrag(dx, pressX, pressY);
                    return true;
                }
                return false;
            default:
                return false;
        }
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (!compact) return super.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                if (dragging) {
                    pressX = event.getX();
                    pressY = event.getY();
                    updateDrag(pressX - downX, pressX, pressY);
                    return true;
                }
                break;
            case MotionEvent.ACTION_UP:
                if (dragging) {
                    finishDrag();
                    return true;
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                if (dragging) {
                    cancelDrag();
                    return true;
                }
                break;
            default:
                break;
        }
        return super.onTouchEvent(event);
    }

    private void updateDrag(float dx, float currentX, float currentY) {
        if (tabs.isEmpty() || getWidth() == 0) return;
        int innerWidth = getWidth() - getPaddingLeft() - getPaddingRight();
        float itemWidth = innerWidth / (float) tabs.size();
        float fraction = Math.max(0f, Math.min(tabs.size() - 1f,
                dragStartFraction + dx / itemWidth));
        int candidate = Math.round(fraction);
        if (candidate != selectedIndex) {
            selectedIndex = candidate;
            applyActiveTabs();
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        }
        moveIndicatorDuringDrag(fraction);
        pressX = currentX;
        pressY = currentY;
        applyPressProgress();
        if (dockHost != null) {
            float center = (tabs.size() - 1) / 2f;
            float normalized = center == 0f ? 0f : (fraction - center) / center;
            dockHost.setDragOffset(dp(3) * normalized);
        }
    }

    private void finishDrag() {
        int target = selectedIndex;
        selectedIndex = target;
        applyActiveTabs();
        positionIndicator(true);
        if (dockHost != null) dockHost.setDragOffset(0f);
        if (target != dragStartIndex && listener != null) {
            listener.onTabSelected(tabs.get(target).id);
        }
        dragging = false;
    }

    private void cancelDrag() {
        selectedIndex = dragStartIndex;
        applyActiveTabs();
        positionIndicator(false);
        if (dockHost != null) dockHost.setDragOffset(0f);
        dragging = false;
    }

    private void setPressFeedback(boolean pressed) {
        pressFeedback = pressed;
        if (pressed) setIconRefraction(true);
        if (pressAnimator != null) pressAnimator.cancel();
        pressAnimator = ValueAnimator.ofFloat(pressProgress, pressed ? 1f : 0f);
        pressAnimator.setDuration(pressed ? 150 : 320);
        pressAnimator.setInterpolator(pressed
                ? new PathInterpolator(0.2f, 0f, 0f, 1f)
                : new OvershootInterpolator(0.9f));
        pressAnimator.addUpdateListener(animation -> {
            pressProgress = Math.max(0f, Math.min(1f,
                    (float) animation.getAnimatedValue()));
            applyPressProgress();
            if (!pressFeedback && pressProgress <= 0.001f) setIconRefraction(false);
        });
        pressAnimator.start();
    }

    private void setIconRefraction(boolean enabled) {
        if (!selectionGlass || iconsRefracted == enabled) return;
        iconsRefracted = enabled;
        if (enabled) {
            indicator.setRefractionOverlay(row);
            indicator.bringToFront();
        } else {
            indicator.setRefractionOverlay(null);
            row.bringToFront();
        }
    }

    private void applyActiveTabs() {
        for (int i = 0; i < tabs.size(); i++) tabs.get(i).setActive(i == selectedIndex);
        if (pressFeedback) applyPressProgress();
    }

    private void applyPressProgress() {
        if (dockHost != null) {
            dockHost.setFloatingPressScale(pressProgress);
            dockHost.setInteraction(pressProgress, getX() + pressX, getY() + pressY);
        }
        if (selectionGlass) {
            float restX = 1f / PRESS_SCALE_X;
            float restY = 1f / PRESS_SCALE_Y;
            indicator.setScaleX(restX + (1f - restX) * pressProgress);
            indicator.setScaleY(restY + (1f - restY) * pressProgress);
        } else {
            indicator.setScaleX(1f + 0.06f * pressProgress);
            indicator.setScaleY(1f + 0.06f * pressProgress);
        }
        float localX = pressX - indicator.getX();
        float localY = pressY - indicator.getY();
        indicator.setInteraction(pressProgress, Math.max(0f, localX),
                Math.max(0f, localY));
        for (int i = 0; i < tabs.size(); i++) {
            tabs.get(i).setPressProgress(pressFeedback && i == selectedIndex
                    ? pressProgress : 0f);
        }
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (width > 0 && height > 0) positionIndicator(false);
    }

    @Override
    protected void onDetachedFromWindow() {
        indicator.animate().cancel();
        if (pressAnimator != null) pressAnimator.cancel();
        super.onDetachedFromWindow();
    }

    private final class Tab extends LinearLayout {
        final int id;
        final ImageView icon;
        final TextView label;
        int index;

        Tab(Context context, int id, CharSequence title, Drawable iconDrawable) {
            super(context);
            this.id = id;
            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER);
            setClickable(true);
            setFocusable(true);
            setDefaultFocusHighlightEnabled(false);
            setContentDescription(title);

            icon = new ImageView(context);
            icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            icon.setImageDrawable(iconDrawable);
            addView(icon, new LayoutParams(dp(24), dp(24)));

            label = new TextView(context);
            label.setText(title);
            label.setTextSize(12);
            label.setGravity(Gravity.CENTER);
            label.setSingleLine(true);
            label.setIncludeFontPadding(false);
            LayoutParams labelParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            labelParams.topMargin = dp(2);
            addView(label, labelParams);

            setOnClickListener(view -> {
                if (index != selectedIndex) {
                    select(index, true);
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                }
                if (listener != null) listener.onTabSelected(id);
            });
        }

        void setActive(boolean active) {
            int color = ContextCompat.getColor(getContext(),
                    active ? R.color.accent : R.color.on_surface_variant);
            icon.setImageTintList(ColorStateList.valueOf(color));
            icon.setSelected(active);
            label.setTextColor(color);
            label.setTypeface(active
                    ? Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    : Typeface.DEFAULT);
            setSelected(active);
        }

        void setPressProgress(float progress) {
            float scale = 1f + 0.2f * progress;
            setScaleX(scale);
            setScaleY(scale);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
