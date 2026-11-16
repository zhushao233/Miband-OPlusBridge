package com.itos.coui.host

import android.content.Context
import android.graphics.RenderNode
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowInsets
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.vectorResource
import com.itos.coui.backdrop.couiBackdrop
import com.itos.coui.backdrop.rememberCouiBackdrop
import com.itos.coui.navigation.CouiNavigationItem
import com.itos.coui.navigation.NavigationBar
import com.itos.coui.theme.CouiTheme
import org.xmlpull.v1.XmlPullParser

/**
 * Android View host for the unmodified COUI [NavigationBar].
 *
 * The original component samples a Compose backdrop node. This host records
 * the existing page content into a RenderNode and exposes it as that node so
 * the COUI material, refraction and contour shaders keep working above the
 * regular View tree.
 */
class CouiTabBarView @JvmOverloads constructor(
    context: Context,
    private val backdropSource: View,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {
    fun interface OnTabSelectedListener {
        fun onTabSelected(itemId: Int)
    }

    private data class Tab(val itemId: Int, val title: String, val iconRes: Int)

    private val composeView = ComposeView(context)
    private val backdropNode = RenderNode("coui-tab-backdrop")
    private val tabs = mutableListOf<Tab>()
    private var tabsVersion by mutableIntStateOf(0)
    private var selectedIndex by mutableIntStateOf(0)
    private var listener: OnTabSelectedListener? = null
    private var lastCaptureAt = 0L
    private val preDraw = ViewTreeObserver.OnPreDrawListener {
        captureBackdrop()
        true
    }

    init {
        clipChildren = false
        composeView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnDetachedFromWindow,
        )
        addView(composeView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        composeView.setContent {
            val version = tabsVersion
            val currentTabs = remember(version) { tabs.toList() }
            val backdrop = rememberCouiBackdrop(drawSource = {
                drawIntoCanvas { canvas -> canvas.nativeCanvas.drawRenderNode(backdropNode) }
            })
            CouiTheme {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier.fillMaxSize()
                            .graphicsLayer { alpha = 0f }
                            .couiBackdrop(backdrop),
                    )
                    if (currentTabs.isNotEmpty()) {
                        val index = selectedIndex.coerceIn(0, currentTabs.lastIndex)
                        NavigationBar(
                            modifier = Modifier.fillMaxWidth(),
                            backdrop = backdrop,
                            items = currentTabs.map { tab ->
                                CouiNavigationItem(
                                    title = tab.title,
                                    icon = ImageVector.vectorResource(tab.iconRes),
                                )
                            },
                            selectedIndex = index,
                            onSelection = { next ->
                                val tab = currentTabs.getOrNull(next) ?: return@NavigationBar
                                selectedIndex = next
                                listener?.onTabSelected(tab.itemId)
                            },
                        )
                    }
                }
            }
        }
    }

    fun setOnTabSelectedListener(listener: OnTabSelectedListener?) {
        this.listener = listener
    }

    fun setMenu(menuRes: Int, checkedItemId: Int) {
        tabs.clear()
        val parser = context.resources.getXml(menuRes)
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "item") {
                val itemId = parser.getAttributeResourceValue(ANDROID_NS, "id", View.NO_ID)
                val iconRes = parser.getAttributeResourceValue(ANDROID_NS, "icon", 0)
                if (itemId != View.NO_ID && iconRes != 0) {
                    val titleRes = parser.getAttributeResourceValue(ANDROID_NS, "title", 0)
                    val title = if (titleRes != 0) {
                        context.getString(titleRes)
                    } else {
                        parser.getAttributeValue(ANDROID_NS, "title").orEmpty()
                    }
                    tabs.add(Tab(itemId, title, iconRes))
                }
            }
            event = parser.next()
        }
        selectedIndex = tabs.indexOfFirst { it.itemId == checkedItemId }.coerceAtLeast(0)
        tabsVersion++
    }

    fun check(itemId: Int) {
        val index = tabs.indexOfFirst { it.itemId == itemId }
        if (index >= 0) selectedIndex = index
    }

    fun getSelectedId(): Int = tabs.getOrNull(selectedIndex)?.itemId ?: View.NO_ID

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN && !isInsideNavigationBar(event.y)) {
            return false
        }
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

    private fun isInsideNavigationBar(y: Float): Boolean {
        val inset = rootWindowInsets
            ?.getInsets(WindowInsets.Type.tappableElement())?.bottom ?: 0
        return y >= height - dp(104) - inset
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

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}
