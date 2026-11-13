// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.miam1ku.mibandoplusbridge.ui;

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
import android.view.Window;
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
import com.hchen.himiuix.MiuixSwitchView;
import com.hchen.himiuix.MiuixViewGroup;
import com.hchen.himiuix.fill.MiuixFillView;
import com.hchen.himiuix.helper.WindowInsetsHelper;
import com.hchen.himiuix.widget.MiuixTextView;

/**
 * Shared HiMiuix layout for every bridge screen.
 * Pages read top to bottom: status, next step, features, help, management, advanced.
 */
public final class BridgeScreen {
    /** Colour role of a chip, notice card, or status line. */
    public enum Tone { POSITIVE, ACTIVE, NEUTRAL, ATTENTION }

    /** Position of a setup step relative to the current one. */
    public enum StepState { DONE, CURRENT, LATER }

    private enum ActionStyle { FILLED, OUTLINED, TEXT, DANGER }

    public final LinearLayout body;
    private final AppCompatActivity activity;

    private BridgeScreen(AppCompatActivity activity, LinearLayout body) {
        this.activity = activity;
        this.body = body;
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
        return cardWith(parent, color(com.hchen.himiuix.R.color.miuix_basic_background_color));
    }

    /** Card with a Miuix section title. */
    public LinearLayout section(String title) {
        MiuixCard inner = cardWith(body,
                color(com.hchen.himiuix.R.color.miuix_basic_background_color));
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

    /** Miuix switch row with its caption as the native summary. */
    public MiuixSwitchView switchRow(LinearLayout parent, String title, String caption) {
        MiuixSwitchView toggle = new MiuixSwitchView(activity);
        toggle.setTitle(title);
        toggle.setSummary(caption);
        parent.addView(toggle, new LinearLayout.LayoutParams(-1, -2));
        return toggle;
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
            Drawable background = switch (style) {
                case FILLED -> rounded(blue, dp(context, 15), Color.TRANSPARENT, 0);
                case OUTLINED -> rounded(soft, dp(context, 15), blue, 1);
                case TEXT -> rounded(Color.TRANSPARENT, dp(context, 15), Color.TRANSPARENT, 0);
                case DANGER -> rounded(errorSoft, dp(context, 15), error, 1);
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

    private static GradientDrawable rounded(int fill, float radius, int stroke, int width) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(radius);
        if (width > 0) shape.setStroke(width, stroke);
        return shape;
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
