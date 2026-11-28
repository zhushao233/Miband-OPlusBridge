package com.itos.coui.host

import android.content.Context
import android.content.res.Configuration
import android.graphics.Rect
import android.graphics.RecordingCanvas
import android.graphics.RenderNode
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.widget.FrameLayout
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.backdrop.couiBackdropSource
import com.itos.coui.backdrop.rememberCouiBackdrop
import com.itos.coui.effect.LocalCouiContourGlowEnabled
import com.itos.coui.theme.CouiTheme
import com.itos.coui.theme.LocalCouiContentColor
import com.itos.coui.theme.LocalCouiTextStyle
import com.itos.coui.topappbar.CouiTopAppBarDefaults
import com.itos.coui.topappbar.TopAppBar
import com.itos.coui.topappbar.TopAppBarButton
import kotlin.math.max

/**
 * Android View host for the COUI compact top app bar.
 *
 * The host stays full size so the backdrop node can feed the progressive blur
 * and its fade below the bar; touches outside the bar pass through to content.
 */
class CouiTopBarView @JvmOverloads constructor(
    context: Context,
    private val backdropSource: View,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {
    private val composeView = ComposeView(context)
    private val backdropNode = RenderNode("coui-top-bar-backdrop")
    private var titleText by mutableStateOf("")
    private var backShown by mutableStateOf(false)
    private var contourEnabled by mutableStateOf(true)
    private var onBackClick: Runnable? = null
    private var barGesture = false
    private var capturePosted = false
    private var capturing = false
    private var backdrop: CouiBackdrop? = null
    private val captureRunnable = Runnable {
        capturePosted = false
        captureBackdrop()
        invalidateComposeDraw()
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
            val dark = (LocalConfiguration.current.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
            val backdrop = rememberCouiBackdrop(drawSource = {
                drawIntoCanvas { canvas -> canvas.nativeCanvas.drawRenderNode(backdropNode) }
            })
            this@CouiTopBarView.backdrop = backdrop
            CompositionLocalProvider(LocalCouiContourGlowEnabled provides contourEnabled) {
                CouiTheme(darkTheme = dark) {
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize().couiBackdropSource(backdrop))
                        TopAppBar(
                            modifier = Modifier.align(Alignment.TopCenter),
                            title = {
                                BasicText(
                                    text = titleText,
                                    style = LocalCouiTextStyle.current.copy(
                                        color = LocalCouiContentColor.current,
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            navigationIcon = {
                                if (backShown) {
                                    val backIcon = ImageVector.vectorResource(
                                        R.drawable.coui_back_outlined,
                                    )
                                    TopAppBarButton(
                                        modifier = Modifier.semantics {
                                            contentDescription = "返回"
                                        },
                                        onClick = { onBackClick?.run() },
                                    ) {
                                        Image(
                                            painter = rememberVectorPainter(backIcon),
                                            contentDescription = null,
                                            modifier = Modifier.size(22.dp),
                                            colorFilter = ColorFilter.tint(
                                                CouiTheme.colorScheme.onSurface,
                                            ),
                                        )
                                    }
                                }
                            },
                            dark = dark,
                            backdrop = backdrop,
                        )
                    }
                }
            }
        }
    }

    fun setTitle(title: String) {
        titleText = title
    }

    fun setOnBackClickListener(listener: Runnable?) {
        onBackClick = listener
    }

    fun setBackVisible(visible: Boolean) {
        backShown = visible
    }

    fun setContourGlowEnabled(enabled: Boolean) {
        contourEnabled = enabled
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> barGesture = event.y <= topBarHeightPx()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val handled = barGesture && super.dispatchTouchEvent(event)
                barGesture = false
                return handled
            }
        }
        if (!barGesture) return false
        return super.dispatchTouchEvent(event)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        requestCapture()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        requestCapture()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(captureRunnable)
        capturePosted = false
        backdropNode.discardDisplayList()
        super.onDetachedFromWindow()
    }

    /** 在正文完成本帧绘制后重新采样，避免把本帧绘制前的旧内容喂给模糊材质。 */
    fun requestCapture() {
        if (capturePosted) return
        capturePosted = true
        postOnAnimation(captureRunnable)
    }

    /**
     * Samples the page immediately after it has been drawn, so the material
     * reads the same frame the user sees instead of a deferred snapshot.
     */
    fun captureBackdropNow() {
        if (capturing) return
        capturing = true
        try {
            captureBackdrop()
        } finally {
            capturing = false
        }
        invalidateComposeDraw()
    }

    /** Compose keeps its display list on the child view, so it must be invalidated too. */
    private fun invalidateComposeDraw() {
        composeView.invalidate()
        for (index in 0 until composeView.childCount) {
            composeView.getChildAt(index).invalidate()
        }
    }

    private fun topBarHeightPx(): Int {
        val insets = rootWindowInsets
        val systemTop = insets?.getInsets(WindowInsets.Type.systemBars())?.top ?: 0
        val cutoutTop = insets?.getInsets(WindowInsets.Type.displayCutout())?.top ?: 0
        val content = (CouiTopAppBarDefaults.height.value * resources.displayMetrics.density)
        return content.toInt() + max(systemTop, cutoutTop)
    }

    private fun captureBackdrop() {
        if (!isAttachedToWindow || !backdropSource.isAttachedToWindow) return
        val width = width
        val height = height
        if (width <= 0 || height <= 0) return
        try {
            val recording = backdropNode.beginRecording(width, height)
            try {
                val hostLocation = IntArray(2)
                val sourceLocation = IntArray(2)
                getLocationInWindow(hostLocation)
                backdropSource.getLocationInWindow(sourceLocation)
                val left = (sourceLocation[0] - hostLocation[0]).toFloat()
                val top = (sourceLocation[1] - hostLocation[1]).toFloat()
                drawSourceBackground(recording, left, top)
                recording.save()
                // View.draw() does not apply the view's own scroll; the parent
                // normally does it while building the display list.
                recording.translate(
                    left - backdropSource.scrollX,
                    top - backdropSource.scrollY,
                )
                backdropSource.draw(recording)
                recording.restore()
            } finally {
                backdropNode.endRecording()
            }
            backdropNode.setPosition(0, 0, width, height)
            backdrop?.requestSourceRecord()
        } catch (unsupported: RuntimeException) {
            backdropNode.discardDisplayList()
        }
    }

    /** Fills the area the scrolled content leaves behind before it is re-drawn. */
    private fun drawSourceBackground(recording: RecordingCanvas, left: Float, top: Float) {
        val background = backdropSource.background ?: return
        val bounds = Rect(background.bounds)
        recording.save()
        try {
            recording.translate(left, top)
            background.setBounds(0, 0, backdropSource.width, backdropSource.height)
            background.draw(recording)
        } finally {
            background.bounds = bounds
            recording.restore()
        }
    }
}
