// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.miam1ku.mibandoplusbridge.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import io.github.miam1ku.mibandoplusbridge.R;
import io.github.miam1ku.mibandoplusbridge.notify.SleepProtectionSchedule;
import java.util.Locale;

/** A two-handle whole-hour sleep window. The highlighted segment wraps across midnight. */
public final class SleepWindowSlider extends View {
    public interface OnRangeChangedListener {
        void onRangeChanged(int startMinute, int endMinute, boolean fromUser);
    }

    private static final int START_HANDLE = 0;
    private static final int END_HANDLE = 1;

    private final float density;
    private final int accent;
    private final int inactiveTrack;
    private final int handleCenter;
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint activePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handleCorePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bubblePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bubbleTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path bubblePointer = new Path();
    private final RectF bubble = new RectF();

    private int startMinute = SleepProtectionSchedule.DEFAULT_START_MINUTE;
    private int endMinute = SleepProtectionSchedule.DEFAULT_END_MINUTE;
    private int activeHandle = -1;
    private float downX;
    private boolean moved;
    private OnRangeChangedListener listener;

    public SleepWindowSlider(Context context) {
        this(context, null);
    }

    public SleepWindowSlider(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        accent = context.getColor(R.color.accent);
        inactiveTrack = context.getColor(R.color.outline);
        handleCenter = context.getColor(R.color.surface);

        trackPaint.setColor(inactiveTrack);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);
        trackPaint.setStrokeWidth(dp(5));
        activePaint.setColor(accent);
        activePaint.setStrokeCap(Paint.Cap.ROUND);
        activePaint.setStrokeWidth(dp(7));
        handlePaint.setColor(accent);
        handleCorePaint.setColor(handleCenter);
        bubblePaint.setColor(accent);
        bubbleTextPaint.setColor(context.getColor(R.color.on_primary));
        bubbleTextPaint.setTextSize(dp(12));
        bubbleTextPaint.setTextAlign(Paint.Align.CENTER);
        bubbleTextPaint.setTypeface(android.graphics.Typeface.create(
                "sans-serif-medium", android.graphics.Typeface.NORMAL));
        tickPaint.setColor(inactiveTrack);

        setMinimumHeight(Math.round(dp(116)));
        setFocusable(true);
        setClickable(true);
        setContentDescription("睡眠保护开始与结束时间");
    }

    public void setOnRangeChangedListener(OnRangeChangedListener listener) {
        this.listener = listener;
    }

    public void setRange(int startMinute, int endMinute) {
        int start = SleepProtectionSchedule.normalize(startMinute);
        int end = SleepProtectionSchedule.normalize(endMinute);
        if (!SleepProtectionSchedule.isValid(start, end)) return;
        this.startMinute = start;
        this.endMinute = end;
        updateAccessibilityDescription();
        invalidate();
    }

    public int startMinute() {
        return startMinute;
    }

    public int endMinute() {
        return endMinute;
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = resolveSize(Math.round(dp(280)), widthMeasureSpec);
        int height = resolveSize(Math.round(dp(116)), heightMeasureSpec);
        setMeasuredDimension(width, height);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float left = dp(28);
        float right = Math.max(left + 1, getWidth() - dp(28));
        float trackY = dp(76);
        float startX = xForMinute(left, right, startMinute);
        float endX = xForMinute(left, right, endMinute);

        drawTicks(canvas, left, right, trackY);
        float inset = activePaint.getStrokeWidth() / 2f;
        canvas.drawLine(left + inset, trackY, right - inset, trackY, trackPaint);
        if (startX <= endX) {
            canvas.drawLine(startX, trackY, endX, trackY, activePaint);
        } else {
            canvas.drawLine(left + inset, trackY, endX, trackY, activePaint);
            canvas.drawLine(startX, trackY, right - inset, trackY, activePaint);
        }

        drawBubbles(canvas, left, right, startX, endX);
        drawHandle(canvas, startX, trackY, activeHandle == START_HANDLE);
        drawHandle(canvas, endX, trackY, activeHandle == END_HANDLE);
    }

    private void drawTicks(Canvas canvas, float left, float right, float trackY) {
        tickPaint.setStrokeWidth(dp(1));
        for (int hour = 0; hour <= 24; hour += 6) {
            float x = left + (right - left) * hour / 24f;
            canvas.drawLine(x, trackY - dp(10), x, trackY - dp(7), tickPaint);
        }
    }

    private void drawHandle(Canvas canvas, float x, float y, boolean active) {
        float radius = dp(10) * (active ? 1.2f : 1f);
        canvas.drawCircle(x, y, radius + dp(2), handlePaint);
        canvas.drawCircle(x, y, radius - dp(3), handleCorePaint);
    }

    private void drawBubbles(Canvas canvas, float left, float right, float startX, float endX) {
        String startLabel = SleepProtectionSchedule.label(startMinute);
        String endLabel = SleepProtectionSchedule.label(endMinute);
        float height = dp(28);
        float gap = dp(6);
        float startWidth = bubbleWidth(startLabel);
        float endWidth = bubbleWidth(endLabel);
        float startCenter = clamp(startX, left + startWidth / 2, right - startWidth / 2);
        float endCenter = clamp(endX, left + endWidth / 2, right - endWidth / 2);

        float startLeft = startCenter - startWidth / 2;
        float startRight = startCenter + startWidth / 2;
        float endLeft = endCenter - endWidth / 2;
        float endRight = endCenter + endWidth / 2;
        if (startRight + gap > endLeft) {
            float overlap = (startRight + gap - endLeft) / 2f;
            startLeft = clamp(startLeft - overlap, left, right - startWidth);
            startRight = startLeft + startWidth;
            endLeft = clamp(endLeft + overlap, left, right - endWidth);
            endRight = endLeft + endWidth;
            if (startRight + gap > endLeft) {
                startLeft = clamp(endLeft - gap - startWidth, left, right - startWidth);
                startRight = startLeft + startWidth;
            }
        }
        drawBubble(canvas, startLeft, dp(8), startWidth, height, startX, startLabel);
        drawBubble(canvas, endLeft, dp(8), endWidth, height, endX, endLabel);
    }

    private void drawBubble(Canvas canvas, float left, float top, float width, float height,
                            float pointerX, String value) {
        bubble.set(left, top, left + width, top + height);
        float radius = dp(10);
        canvas.drawRoundRect(bubble, radius, radius, bubblePaint);

        float pointerCenter = clamp(pointerX, bubble.left + dp(9), bubble.right - dp(9));
        bubblePointer.reset();
        bubblePointer.moveTo(pointerCenter - dp(4), bubble.bottom - dp(1));
        bubblePointer.lineTo(pointerCenter, bubble.bottom + dp(5));
        bubblePointer.lineTo(pointerCenter + dp(4), bubble.bottom - dp(1));
        bubblePointer.close();
        canvas.drawPath(bubblePointer, bubblePaint);

        Paint.FontMetrics metrics = bubbleTextPaint.getFontMetrics();
        float baseline = bubble.centerY() - (metrics.ascent + metrics.descent) / 2f;
        canvas.drawText(value, bubble.centerX(), baseline, bubbleTextPaint);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN -> {
                if (!isEnabled()) return false;
                downX = event.getX();
                moved = false;
                activeHandle = nearestHandle(downX);
                getParent().requestDisallowInterceptTouchEvent(true);
                updateFromTouch(downX);
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_MOVE -> {
                if (activeHandle < 0) return false;
                moved |= Math.abs(event.getX() - downX) >= dp(3);
                updateFromTouch(event.getX());
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_UP -> {
                if (activeHandle < 0) return false;
                updateFromTouch(event.getX());
                activeHandle = -1;
                getParent().requestDisallowInterceptTouchEvent(false);
                invalidate();
                if (!moved) performClick();
                return true;
            }
            case MotionEvent.ACTION_CANCEL -> {
                activeHandle = -1;
                getParent().requestDisallowInterceptTouchEvent(false);
                invalidate();
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    @Override public boolean performClick() {
        super.performClick();
        return true;
    }

    private int nearestHandle(float x) {
        float left = dp(28);
        float right = Math.max(left + 1, getWidth() - dp(28));
        float startX = xForMinute(left, right, startMinute);
        float endX = xForMinute(left, right, endMinute);
        if (Math.abs(x - startX) <= dp(30) || Math.abs(x - endX) <= dp(30)) {
            return Math.abs(x - startX) <= Math.abs(x - endX) ? START_HANDLE : END_HANDLE;
        }
        int minute = minuteForX(left, right, x);
        int startDistance = SleepProtectionSchedule.forwardMinutes(startMinute, minute);
        int endDistance = SleepProtectionSchedule.forwardMinutes(endMinute, minute);
        return startDistance <= endDistance ? START_HANDLE : END_HANDLE;
    }

    private void updateFromTouch(float x) {
        float left = dp(28);
        float right = Math.max(left + 1, getWidth() - dp(28));
        int candidate = snapHour(minuteForX(left, right, x));
        boolean changed;
        if (activeHandle == START_HANDLE) {
            int latestStart = SleepProtectionSchedule.normalize(
                    endMinute - SleepProtectionSchedule.MIN_DURATION_MINUTES);
            if (SleepProtectionSchedule.forwardMinutes(candidate, endMinute)
                    < SleepProtectionSchedule.MIN_DURATION_MINUTES) {
                candidate = latestStart;
            }
            changed = startMinute != candidate;
            startMinute = candidate;
        } else {
            int earliestEnd = SleepProtectionSchedule.normalize(
                    startMinute + SleepProtectionSchedule.MIN_DURATION_MINUTES);
            if (SleepProtectionSchedule.forwardMinutes(startMinute, candidate)
                    < SleepProtectionSchedule.MIN_DURATION_MINUTES) {
                candidate = earliestEnd;
            }
            changed = endMinute != candidate;
            endMinute = candidate;
        }
        if (!changed) return;
        updateAccessibilityDescription();
        if (listener != null) listener.onRangeChanged(startMinute, endMinute, true);
    }

    private int minuteForX(float left, float right, float x) {
        float fraction = clamp((x - left) / (right - left), 0f, 1f);
        return Math.round(fraction * SleepProtectionSchedule.MINUTES_PER_DAY);
    }

    private float xForMinute(float left, float right, int minute) {
        return left + (right - left) * SleepProtectionSchedule.normalize(minute)
                / SleepProtectionSchedule.MINUTES_PER_DAY;
    }

    private static int snapHour(int minute) {
        int hour = Math.round(minute / 60f);
        if (hour >= 24) return 0;
        return hour * 60;
    }

    private float bubbleWidth(String value) {
        return Math.max(dp(56), bubbleTextPaint.measureText(value) + dp(18));
    }

    private void updateAccessibilityDescription() {
        setContentDescription(String.format(Locale.ROOT, "睡眠保护 %s 到 %s",
                SleepProtectionSchedule.label(startMinute),
                SleepProtectionSchedule.label(endMinute)));
    }

    private float dp(float value) {
        return value * density;
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
