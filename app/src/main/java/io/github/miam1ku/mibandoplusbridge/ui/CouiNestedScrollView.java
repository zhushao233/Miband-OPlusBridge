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
    private static final long FAST_SWIPE_MAX_MS = 450L;
    private static final float FAST_SWIPE_MIN_DP_PER_SECOND = 800f;

    private boolean edgeFeedbackSent;
    private Runnable onContentMoved;
    private boolean contentSamplesRunning;
    private boolean contentDirty = true;
    private boolean animationSampling;
    private long lastSampleFrame = Long.MIN_VALUE;
    private float lastTouchY;
    private long gestureDownTime;
    private float gestureDownY;
    private boolean edgeDecided;
    private boolean suppressOverscroll;
    private float fastEdgeSpeed;
    private int fastEdgeDirection;
    private float pendingFlingVelocity;
    private float overscroll;
    private int overscrollDirection;
    private ValueAnimator settleAnimator;
    private ValueAnimator impactAnimator;
    private final float maxOverscroll;
    private final float density;
    private int minContentOverflow;

    public CouiNestedScrollView(Context context) {
        super(context);
        setOverScrollMode(View.OVER_SCROLL_NEVER);
        density = getResources().getDisplayMetrics().density;
        maxOverscroll = MAX_OVERSCROLL_DP * density;
    }

    /** Called after this frame's content is drawn so hosts can sample the settled frame. */
    public void setOnContentMovedListener(Runnable listener) {
        onContentMoved = listener;
    }

    /**
     * Keeps short pages scrollable far enough for the large title to collapse.
     * The extra range is blank space below the page, not a layout shift.
     */
    public void setMinimumContentOverflow(int px) {
        minContentOverflow = Math.max(0, px);
        updateChildMinimumHeight();
        contentDirty = true;
    }

    /** Drops a pending edge impulse before an external scroll reset. */
    public void clearFlingImpulse() {
        pendingFlingVelocity = 0f;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (contentDirty || animationSampling) sampleContent();
    }

    /**
     * Runs the sample callback at most once per frame. Re-entrant dispatchDraw calls made
     * while the callback records the page are suppressed, and repeated host calls in the
     * same frame are dropped by the frame guard.
     */
    public void sampleContent() {
        if (contentSamplesRunning || onContentMoved == null) return;
        long frameTime = getDrawingTime();
        if (frameTime == lastSampleFrame) return;
        lastSampleFrame = frameTime;
        contentDirty = false;
        contentSamplesRunning = true;
        try {
            onContentMoved.run();
        } finally {
            contentSamplesRunning = false;
        }
    }

    /** Marks the page as changed so the next draw refreshes the blur samples. */
    public void markContentDirty() {
        contentDirty = true;
    }

    /** Keeps sampling every frame while a host page animation moves the content. */
    public void setAnimationSampling(boolean enabled) {
        animationSampling = enabled;
        if (enabled) contentDirty = true;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                edgeFeedbackSent = false;
                lastTouchY = event.getY();
                gestureDownY = lastTouchY;
                gestureDownTime = event.getEventTime();
                edgeDecided = false;
                suppressOverscroll = false;
                fastEdgeSpeed = 0f;
                fastEdgeDirection = 0;
                pendingFlingVelocity = 0f;
                overscrollDirection = 0;
                cancelSettle();
                setOverscroll(0f);
                break;
            case MotionEvent.ACTION_MOVE: {
                float y = event.getY();
                float delta = y - lastTouchY;
                lastTouchY = y;
                updateOverscroll(delta, event.getEventTime());
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (suppressOverscroll && overscroll == 0f && fastEdgeSpeed > 0f) {
                    impactOverscroll(fastEdgeDirection * impactForVelocity(fastEdgeSpeed * density));
                }
                settleOverscroll();
                edgeDecided = false;
                suppressOverscroll = false;
                fastEdgeSpeed = 0f;
                fastEdgeDirection = 0;
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
    private void updateOverscroll(float delta, long now) {
        if (overscrollDirection == 0) {
            int edge = delta > 0f && !canScrollVertically(-1) ? 1
                    : delta < 0f && !canScrollVertically(1) ? -1 : 0;
            if (edge == 0) {
                edgeDecided = false;
                suppressOverscroll = false;
                fastEdgeSpeed = 0f;
                fastEdgeDirection = 0;
                return;
            }
            sendEdgeFeedback();
            if (!edgeDecided) {
                long elapsed = now - gestureDownTime;
                if (elapsed < 32L) return;
                edgeDecided = true;
                float speed = Math.abs(lastTouchY - gestureDownY) / elapsed * 1000f / density;
                if (elapsed <= FAST_SWIPE_MAX_MS && speed >= FAST_SWIPE_MIN_DP_PER_SECOND) {
                    suppressOverscroll = true;
                    fastEdgeSpeed = speed;
                    fastEdgeDirection = edge;
                    return;
                }
            }
            if (suppressOverscroll) return;
            overscrollDirection = edge;
        }
        if ((overscrollDirection > 0 && delta < 0f)
                || (overscrollDirection < 0 && delta > 0f)) {
            settleOverscroll();
            overscrollDirection = 0;
            edgeDecided = false;
            suppressOverscroll = false;
            return;
        }
        if (suppressOverscroll) return;
        float resistance = 1f - Math.min(1f, Math.abs(overscroll) / maxOverscroll);
        float next = overscroll + delta * OVERSCROLL_RATIO * Math.max(0.08f, resistance);
        setOverscroll(Math.max(-maxOverscroll, Math.min(maxOverscroll, next)));
    }

    private void setOverscroll(float value) {
        overscroll = value;
        contentDirty = true;
        View content = getChildAt(0);
        if (content != null) content.setTranslationY(value);
    }

    @Override
    public void fling(int velocityY) {
        contentDirty = true;
        if (impactAnimator != null || overscroll != 0f) {
            pendingFlingVelocity = 0f;
        } else if (velocityY > 0 && !canScrollVertically(1)) {
            pendingFlingVelocity = 0f;
            impactOverscroll(-impactForVelocity(velocityY));
        } else if (velocityY < 0 && !canScrollVertically(-1)) {
            pendingFlingVelocity = 0f;
            impactOverscroll(impactForVelocity(velocityY));
        } else {
            pendingFlingVelocity = velocityY;
        }
        super.fling(velocityY);
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        contentDirty = true;
        float velocity = pendingFlingVelocity;
        if (velocity > 0f && !canScrollVertically(1)) {
            pendingFlingVelocity = 0f;
            impactOverscroll(-impactForVelocity(velocity));
        } else if (velocity < 0f && !canScrollVertically(-1)) {
            pendingFlingVelocity = 0f;
            impactOverscroll(impactForVelocity(velocity));
        }
    }

    /** The harder the fling, the deeper the rebound at the edge. */
    private float impactForVelocity(float velocityPx) {
        float speedDp = Math.abs(velocityPx) / density;
        float fraction = Math.min(1f, speedDp / 6000f);
        return (14f + 58f * (float) Math.sqrt(fraction)) * density;
    }

    private void impactOverscroll(float target) {
        if (target == 0f) return;
        cancelSettle();
        impactAnimator = ValueAnimator.ofFloat(overscroll, target);
        impactAnimator.setDuration(80L);
        impactAnimator.setInterpolator(new DecelerateInterpolator(1.4f));
        impactAnimator.addUpdateListener(animation ->
                setOverscroll((float) animation.getAnimatedValue()));
        impactAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                impactAnimator = null;
                settleOverscroll();
            }
        });
        impactAnimator.start();
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
        if (impactAnimator != null) {
            impactAnimator.cancel();
            impactAnimator = null;
        }
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
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateChildMinimumHeight();
        contentDirty = true;
    }

    @Override
    protected void onOverScrolled(int scrollX, int scrollY, boolean clampedX, boolean clampedY) {
        super.onOverScrolled(scrollX, scrollY, clampedX, clampedY);
        if (!clampedY) return;
        sendEdgeFeedback();
    }

    private void updateChildMinimumHeight() {
        View content = getChildAt(0);
        if (content == null) return;
        int target = getHeight() + minContentOverflow;
        if (content.getMinimumHeight() != target) content.setMinimumHeight(target);
    }
}
