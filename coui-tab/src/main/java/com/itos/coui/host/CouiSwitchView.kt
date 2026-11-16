package com.itos.coui.host

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.itos.coui.effect.LocalCouiContourGlowEnabled
import com.itos.coui.switch.Switch
import com.itos.coui.theme.CouiTheme

/** Android View host for the unmodified COUI switch. */
class CouiSwitchView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {
    fun interface OnCheckedChangeListener {
        fun onCheckedChange(checked: Boolean)
    }

    private val composeView = ComposeView(context)
    private var checkedState by mutableStateOf(false)
    private var contourEnabled by mutableStateOf(true)
    private var listener: OnCheckedChangeListener? = null

    init {
        clipChildren = false
        clipToPadding = false
        composeView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnDetachedFromWindow,
        )
        addView(composeView, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        composeView.setContent {
            CompositionLocalProvider(LocalCouiContourGlowEnabled provides contourEnabled) {
                CouiTheme {
                    Switch(
                        checked = checkedState,
                        onCheckedChange = { next ->
                            checkedState = next
                            listener?.onCheckedChange(next)
                        },
                    )
                }
            }
        }
    }

    fun setChecked(checked: Boolean) {
        checkedState = checked
    }

    fun isChecked(): Boolean = checkedState

    fun toggle() {
        val next = !checkedState
        checkedState = next
        listener?.onCheckedChange(next)
    }

    fun setOnCheckedChangeListener(listener: OnCheckedChangeListener?) {
        this.listener = listener
    }

    fun setContourGlowEnabled(enabled: Boolean) {
        contourEnabled = enabled
    }
}
