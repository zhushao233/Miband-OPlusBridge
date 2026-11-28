// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.miam1ku.mibandoplusbridge.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import androidx.core.widget.NestedScrollView;

/** NestedScrollView with platform overscroll stretch and the COUI scroll-edge haptic. */
public final class CouiNestedScrollView extends NestedScrollView {
    private static final int COUI_SCROLL_EDGE = 307;

    private boolean edgeFeedbackSent;
    private Runnable onContentMoved;
    private boolean contentSamplesRunning;

    public CouiNestedScrollView(Context context) {
        super(context);
        setOverScrollMode(View.OVER_SCROLL_ALWAYS);
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
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            edgeFeedbackSent = false;
        }
        return super.dispatchTouchEvent(event);
    }

    private void sendEdgeFeedback() {
        if (edgeFeedbackSent) return;
        edgeFeedbackSent = true;
        if (!performHapticFeedback(COUI_SCROLL_EDGE)) {
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        }
    }

    @Override
    protected void onOverScrolled(int scrollX, int scrollY, boolean clampedX, boolean clampedY) {
        super.onOverScrolled(scrollX, scrollY, clampedX, clampedY);
        if (!clampedY) return;
        sendEdgeFeedback();
    }
}
