// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.miam1ku.mibandoplusbridge.ui;

import android.content.Context;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import androidx.core.widget.NestedScrollView;

/** NestedScrollView with platform overscroll stretch and the COUI scroll-edge haptic. */
public final class CouiNestedScrollView extends NestedScrollView {
    private static final int COUI_SCROLL_EDGE = 307;

    private boolean edgeFeedbackSent;

    public CouiNestedScrollView(Context context) {
        super(context);
        setOverScrollMode(View.OVER_SCROLL_ALWAYS);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            edgeFeedbackSent = false;
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    protected void onOverScrolled(int scrollX, int scrollY, boolean clampedX, boolean clampedY) {
        super.onOverScrolled(scrollX, scrollY, clampedX, clampedY);
        if (!clampedY || edgeFeedbackSent) return;
        edgeFeedbackSent = true;
        if (!performHapticFeedback(COUI_SCROLL_EDGE)) {
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        }
    }
}
