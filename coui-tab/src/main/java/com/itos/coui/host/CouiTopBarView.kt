package com.itos.coui.host

import android.content.Context
import android.content.res.Configuration
import android.graphics.RenderNode
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
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
            val dark = (LocalConfiguration.current.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
            val backdrop = rememberCouiBackdrop(drawSource = {
                drawIntoCanvas { canvas -> canvas.nativeCanvas.drawRenderNode(backdropNode) }
            })
            CompositionLocalProvider(LocalCouiContourGlowEnabled provides contourEnabled) {
                CouiTheme(darkTheme = dark) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        TopAppBar(
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
        viewTreeObserver.addOnPreDrawListener(preDraw)
    }

    override fun onDetachedFromWindow() {
        val observer = viewTreeObserver
        if (observer.isAlive) observer.removeOnPreDrawListener(preDraw)
        backdropNode.discardDisplayList()
        super.onDetachedFromWindow()
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
