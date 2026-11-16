package com.itos.coui.host

import android.content.Context
import android.graphics.RenderNode
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.itos.coui.backdrop.rememberCouiBackdrop
import com.itos.coui.effect.LocalCouiContourGlowEnabled
import com.itos.coui.theme.CouiTheme
import com.itos.coui.topappbar.TopAppBarButton
import com.itos.coui.topappbar.TopAppBarSurface

/** Android View host for the unmodified COUI top app bar button. */
class CouiBackButtonView @JvmOverloads constructor(
    context: Context,
    private val backdropSource: View,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {
    private val composeView = ComposeView(context)
    private val backdropNode = RenderNode("coui-back-backdrop")
    private var contourEnabled by mutableStateOf(true)
    private var onBackClick: Runnable? = null
    private var lastCaptureAt = 0L
    private val preDraw = ViewTreeObserver.OnPreDrawListener {
        captureBackdrop()
        true
    }

    init {
        clipChildren = false
        clipToPadding = false
        composeView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnDetachedFromWindow,
        )
        composeView.clipChildren = false
        composeView.clipToPadding = false
        addView(composeView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        composeView.setContent {
            val backdrop = rememberCouiBackdrop(drawSource = {
                drawIntoCanvas { canvas -> canvas.nativeCanvas.drawRenderNode(backdropNode) }
            })
            CompositionLocalProvider(LocalCouiContourGlowEnabled provides contourEnabled) {
                CouiTheme {
                    TopAppBarSurface(
                        modifier = Modifier.fillMaxSize(),
                        backdrop = backdrop,
                        fadeHeight = 0.dp,
                        bottomExtension = 0.dp,
                        backgroundAlpha = 0f,
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            val backIcon = ImageVector.vectorResource(R.drawable.coui_back_outlined)
                            TopAppBarButton(
                                modifier = Modifier.semantics { contentDescription = "返回" },
                                onClick = { onBackClick?.run() },
                            ) {
                                Image(
                                    painter = rememberVectorPainter(backIcon),
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp),
                                    colorFilter = ColorFilter.tint(CouiTheme.colorScheme.onSurface),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    fun setOnBackClickListener(listener: Runnable?) {
        onBackClick = listener
    }

    fun setContourGlowEnabled(enabled: Boolean) {
        contourEnabled = enabled
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnPreDrawListener(preDraw)
    }

    override fun onDetachedFromWindow() {
        val observer = viewTreeObserver
        if (observer.isAlive) observer.removeOnPreDrawListener(preDraw)
        backdropNode.discardDisplayList()
        super.onDetachedFromWindow()
    }

    private fun captureBackdrop() {
        if (!isAttachedToWindow || !backdropSource.isAttachedToWindow) return
        val width = width
        val height = height
        if (width <= 0 || height <= 0) return
        val now = SystemClock.uptimeMillis()
        if (now - lastCaptureAt < 16L) return
        lastCaptureAt = now
        try {
            val recording = backdropNode.beginRecording(width, height)
            try {
                val hostLocation = IntArray(2)
                val sourceLocation = IntArray(2)
                getLocationInWindow(hostLocation)
                backdropSource.getLocationInWindow(sourceLocation)
                recording.save()
                recording.translate(
                    (sourceLocation[0] - hostLocation[0]).toFloat(),
                    (sourceLocation[1] - hostLocation[1]).toFloat(),
                )
                backdropSource.draw(recording)
                recording.restore()
            } finally {
                backdropNode.endRecording()
            }
            backdropNode.setPosition(0, 0, width, height)
        } catch (unsupported: RuntimeException) {
            backdropNode.discardDisplayList()
        }
    }
}
