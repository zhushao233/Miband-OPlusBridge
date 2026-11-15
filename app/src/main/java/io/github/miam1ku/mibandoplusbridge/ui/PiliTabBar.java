package io.github.miam1ku.mibandoplusbridge.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.PathInterpolator;
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
    public interface OnTabSelectedListener {
        void onTabSelected(int itemId);
    }

    private final boolean compact;
    private final LinearLayout row;
    private final View indicator;
    private final List<Tab> tabs = new ArrayList<>();
    private int selectedIndex;
    private boolean laidOut;
    private OnTabSelectedListener listener;

    public PiliTabBar(Context context, @MenuRes int menuRes, int checkedItemId, boolean compact) {
        super(context);
        this.compact = compact;
        setClipToPadding(false);
        setPadding(dp(4), dp(4), dp(4), dp(4));

        indicator = new View(context);
        indicator.setBackground(BridgeScreen.roundedRect(selectionColor(context), dp(28), 0, 0));
        indicator.setElevation(dp(2));
        addView(indicator, new LayoutParams(dp(1), dp(1)));

        row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        addView(row, compact
                ? new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT)
                : new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        inflateMenu(menuRes);
        select(indexOf(checkedItemId), false);
    }

    public void setOnTabSelectedListener(OnTabSelectedListener listener) {
        this.listener = listener;
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
        int innerWidth = getWidth() - getPaddingLeft() - getPaddingRight();
        int innerHeight = getHeight() - getPaddingTop() - getPaddingBottom();
        int itemWidth = innerWidth / tabs.size();
        int inset = dp(4);
        int pillWidth = Math.max(0, itemWidth - inset * 2);
        int pillHeight = Math.max(0, innerHeight - inset * 2);

        LayoutParams params = (LayoutParams) indicator.getLayoutParams();
        params.width = pillWidth;
        params.height = pillHeight;
        indicator.setLayoutParams(params);
        indicator.setY(getPaddingTop() + inset);

        float targetX = getPaddingLeft() + selectedIndex * itemWidth + inset;
        indicator.animate().cancel();
        if (animate) {
            indicator.animate()
                    .x(targetX)
                    .setDuration(240)
                    .setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f))
                    .start();
        } else {
            indicator.setX(targetX);
        }
        laidOut = true;
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (width > 0 && height > 0) positionIndicator(false);
    }

    @Override
    protected void onDetachedFromWindow() {
        indicator.animate().cancel();
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
    }

    private static int selectionColor(Context context) {
        boolean dark = (context.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        int color = ContextCompat.getColor(context, R.color.on_surface);
        return (color & 0x00FFFFFF) | ((dark ? 0x26 : 0x1F) << 24);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
