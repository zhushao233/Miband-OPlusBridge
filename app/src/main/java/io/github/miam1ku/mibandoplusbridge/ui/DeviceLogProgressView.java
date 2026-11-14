// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.miam1ku.mibandoplusbridge.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/** A small progress view whose indeterminate and determinate draw modes cannot overlap. */
public final class DeviceLogProgressView extends View {
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private ValueAnimator waitingAnimator;
    private boolean indeterminate = true;
    private boolean determinateOnly;
    private int progress;

    public DeviceLogProgressView(Context context) {
        this(context, null);
    }

    public DeviceLogProgressView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DeviceLogProgressView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        trackPaint.setColor(com.google.android.material.color.MaterialColors.getColor(
                context, com.google.android.material.R.attr.colorSurfaceVariant, 0));
        progressPaint.setColor(com.google.android.material.color.MaterialColors.getColor(
                context, com.google.android.material.R.attr.colorPrimary, 0));
    }

    public void setIndeterminate(boolean value) {
        if (value && determinateOnly) return;
        if (indeterminate == value) {
            if (value) startAnimationIfAttached();
            return;
        }
        indeterminate = value;
        if (value) {
            startAnimationIfAttached();
        } else {
            cancelWaiting();
        }
        invalidate();
    }

    public void setProgressCompat(int value, boolean animated) {
        determinateOnly = true;
        indeterminate = false;
        cancelWaiting();
        progress = Math.max(0, Math.min(100, value));
        invalidate();
    }

    /** Exact percentage-to-pixel mapping, independent of the framework progress drawable. */
    public static float fillWidth(int width, int percent) {
        if (width <= 0 || percent <= 0) return 0;
        return width * Math.min(100, percent) / 100f;
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (indeterminate && !determinateOnly) startAnimationIfAttached();
    }

    @Override protected void onDetachedFromWindow() {
        cancelWaiting();
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float viewHeight = getHeight();
        float viewWidth = getWidth();
        if (viewHeight <= 0 || viewWidth <= 0) return;

        float trackHeight = Math.min(viewHeight, dp(4));
        float top = (viewHeight - trackHeight) / 2f;
        float radius = trackHeight / 2f;
        rect.set(0, top, viewWidth, top + trackHeight);
        canvas.drawRoundRect(rect, radius, radius, trackPaint);

        if (indeterminate) {
            float segment = viewWidth * 0.26f;
            float travel = Math.max(0, viewWidth - segment);
            float phase = waitingAnimator == null ? 0f : waitingAnimator.getAnimatedFraction();
            float left = travel * phase;
            rect.set(left, top, left + segment, top + trackHeight);
        } else {
            float filled = fillWidth((int) viewWidth, progress);
            if (filled <= 0) return;
            rect.set(0, top, filled, top + trackHeight);
        }
        canvas.drawRoundRect(rect, radius, radius, progressPaint);
    }

    private void startAnimationIfAttached() {
        if (determinateOnly || !isAttachedToWindow()) return;
        if (waitingAnimator == null) {
            waitingAnimator = ValueAnimator.ofFloat(0f, 1f);
            waitingAnimator.setDuration(1_100);
            waitingAnimator.setRepeatCount(ValueAnimator.INFINITE);
            waitingAnimator.setRepeatMode(ValueAnimator.REVERSE);
            waitingAnimator.addUpdateListener(value -> invalidate());
        }
        if (!waitingAnimator.isStarted()) waitingAnimator.start();
    }

    private void cancelWaiting() {
        ValueAnimator animator = waitingAnimator;
        waitingAnimator = null;
        if (animator != null) {
            animator.removeAllUpdateListeners();
            animator.cancel();
        }
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
