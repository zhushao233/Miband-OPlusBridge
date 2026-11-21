package com.itos.coui.host

import android.app.Activity
import android.content.res.Configuration
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.CouiDialogActionStyle
import com.itos.coui.dialog.CouiDialogDefaults
import com.itos.coui.dialog.CouiDialogPlacement
import com.itos.coui.dialog.Dialog
import com.itos.coui.theme.CouiTheme

/**
 * Java-friendly host for the unmodified COUI centered confirmation dialog.
 *
 * The Compose window owns dismissal and animation; this small host only keeps the
 * composition attached until its exit animation has finished.
 */
class CouiConfirmDialog private constructor(
    private val activity: Activity,
    private val title: String,
    private val message: String,
    private val dismissText: String,
    private val confirmText: String,
    private val accentColor: Int,
    private val onDismiss: Runnable?,
    private val onConfirm: Runnable?,
) {
    private val composeView = ComposeView(activity)
    private var visible by mutableStateOf(true)
    private var finished = false

    fun show() {
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        composeView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnDetachedFromWindow,
        )
        composeView.setContent {
            val dark = (
                activity.resources.configuration.uiMode and
                    Configuration.UI_MODE_NIGHT_MASK
                ) == Configuration.UI_MODE_NIGHT_YES
            CouiTheme(darkTheme = dark) {
                Dialog(
                    onDismissRequest = { finish(confirmed = false) },
                    title = title,
                    message = message,
                    dismissAction = CouiDialogAction(
                        text = dismissText,
                        onClick = { finish(confirmed = false) },
                    ),
                    confirmAction = CouiDialogAction(
                        text = confirmText,
                        onClick = { finish(confirmed = true) },
                        style = CouiDialogActionStyle.Primary,
                        colors = CouiDialogDefaults.primaryActionColors(Color(accentColor)),
                    ),
                    dark = dark,
                    colors = CouiDialogDefaults.themeColors(dark),
                    placement = CouiDialogPlacement.Center,
                    visible = visible,
                )
            }
        }
        content.addView(
            composeView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    private fun finish(confirmed: Boolean) {
        if (finished) return
        finished = true
        visible = false
        composeView.postDelayed({
            (composeView.parent as? ViewGroup)?.removeView(composeView)
            if (confirmed) onConfirm?.run() else onDismiss?.run()
        }, 190L)
    }

    companion object {
        @JvmStatic
        fun show(
            activity: Activity,
            title: String,
            message: String,
            dismissText: String,
            confirmText: String,
            accentColor: Int,
            onDismiss: Runnable?,
            onConfirm: Runnable?,
        ) {
            CouiConfirmDialog(
                activity = activity,
                title = title,
                message = message,
                dismissText = dismissText,
                confirmText = confirmText,
                accentColor = accentColor,
                onDismiss = onDismiss,
                onConfirm = onConfirm,
            ).show()
        }
    }
}
