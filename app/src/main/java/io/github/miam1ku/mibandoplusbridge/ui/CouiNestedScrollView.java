// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.miam1ku.mibandoplusbridge.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import androidx.core.widget.NestedScrollView;

/** NestedScrollView with displacement overscroll bounce and the COUI scroll-edge haptic. */
public final class CouiNestedScrollView extends NestedScrollView {
    private static final int COUI_SCROLL_EDGE = 307;
    private static final float OVERSCROLL_RATIO = 0.55f;
    private static final float MAX_OVERSCROLL_DP = 96f;
    private static final long SETTLE_DURATION_MS = 260L;

    private boolean edgeFeedbackSent;
    private Runnable onContentMoved;
    private boolean contentSamplesRunning;
    private float lastTouchY;
    private float overscroll;
    private int overscrollDirection;
    private ValueAnimator settleAnimator;
    private final float maxOverscroll;

    public CouiNestedScrollView(Context context) {
        super(context);
        setOverScrollMode(View.OVER_SCROLL_NEVER);
        maxOverscroll = MAX_OVERSCROLL_DP * getResources().getDisplayMetrics().density;
    }

    /** Called after this frame's content is drawn so hosts can sample the settled frame. */
    public void setOnContentMovedListener(Runnable listener) {
        onContentMoved = listener;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        sampleContent();
    }

    /**
     * Runs the sample callback once, suppressing the nested dispatchDraw calls
     * that the callback itself performs while re-recording the page content.
     */
    public void sampleContent() {
        if (contentSamplesRunning || onContentMoved == null) return;
        contentSamplesRunning = true;
        try {
            onContentMoved.run();
        } finally {
            contentSamplesRunning = false;
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                edgeFeedbackSent = false;
                lastTouchY = event.getY();
                overscrollDirection = 0;
                cancelSettle();
                setOverscroll(0f);
                break;
            case MotionEvent.ACTION_MOVE: {
                float y = event.getY();
                float delta = y - lastTouchY;
                lastTouchY = y;
                updateOverscroll(delta);
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                settleOverscroll();
                overscrollDirection = 0;
                break;
            default:
                break;
        }
        return super.dispatchTouchEvent(event);
    }

    /**
     * Moves the page with the finger once it is already at an edge, so the
     * platform stretch effect is never part of what the bars sample.
     */
    private void updateOverscroll(float delta) {
        if (overscrollDirection == 0) {
            if (delta > 0f && !canScrollVertically(-1)) {
                overscrollDirection = 1;
                sendEdgeFeedback();
            } else if (delta < 0f && !canScrollVertically(1)) {
                overscrollDirection = -1;
                sendEdgeFeedback();
            } else {
                return;
            }
        }
        if ((overscrollDirection > 0 && delta < 0f)
                || (overscrollDirection < 0 && delta > 0f)) {
            settleOverscroll();
            overscrollDirection = 0;
            return;
        }
        float resistance = 1f - Math.min(1f, Math.abs(overscroll) / maxOverscroll);
        float next = overscroll + delta * OVERSCROLL_RATIO * Math.max(0.08f, resistance);
        setOverscroll(Math.max(-maxOverscroll, Math.min(maxOverscroll, next)));
    }

    private void setOverscroll(float value) {
        overscroll = value;
        View content = getChildAt(0);
        if (content != null) content.setTranslationY(value);
    }

    private void settleOverscroll() {
        if (overscroll == 0f) return;
        cancelSettle();
        settleAnimator = ValueAnimator.ofFloat(overscroll, 0f);
        settleAnimator.setDuration(SETTLE_DURATION_MS);
        settleAnimator.setInterpolator(new DecelerateInterpolator(1.7f));
        settleAnimator.addUpdateListener(animation ->
                setOverscroll((float) animation.getAnimatedValue()));
        settleAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                settleAnimator = null;
                setOverscroll(0f);
                sampleContent();
            }
        });
        settleAnimator.start();
    }

    private void cancelSettle() {
        if (settleAnimator == null) return;
        settleAnimator.cancel();
        settleAnimator = null;
    }

    private void sendEdgeFeedback() {
        if (edgeFeedbackSent) return;
        edgeFeedbackSent = true;
        if (!performHapticFeedback(COUI_SCROLL_EDGE)) {
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        cancelSettle();
        setOverscroll(0f);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onOverScrolled(int scrollX, int scrollY, boolean clampedX, boolean clampedY) {
        super.onOverScrolled(scrollX, scrollY, clampedX, clampedY);
        if (!clampedY) return;
        sendEdgeFeedback();
    }
}
