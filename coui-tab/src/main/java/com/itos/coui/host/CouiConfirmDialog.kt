package com.itos.coui.host

import android.app.Activity
import android.content.res.Configuration
import android.graphics.RenderNode
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.itos.coui.backdrop.couiBackdrop
import com.itos.coui.backdrop.rememberCouiBackdrop
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.CouiDialogActionStyle
import com.itos.coui.dialog.CouiDialogDefaults
import com.itos.coui.dialog.CouiDialogPlacement
import com.itos.coui.dialog.Dialog
import com.itos.coui.effect.LocalCouiContourGlowEnabled
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
    private val neutralText: String?,
    private val accentColor: Int,
    private val contourGlowEnabled: Boolean,
    private val onDismiss: Runnable?,
    private val onConfirm: Runnable?,
    private val onNeutral: Runnable?,
) {
    private val composeView = ComposeView(activity)
    private val backdropNode = RenderNode("coui-confirm-dialog-backdrop")
    private var visible by mutableStateOf(true)
    private var finished = false
    private var backdropSource: View? = null
    private var backdropObserver: ViewTreeObserver? = null
    private var lastBackdropCaptureAt = 0L
    private val backdropPreDraw = ViewTreeObserver.OnPreDrawListener {
        captureBackdrop()
        true
    }
    private val backdropAttachListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(view: View) {
            backdropObserver = view.viewTreeObserver.also {
                it.addOnPreDrawListener(backdropPreDraw)
            }
        }

        override fun onViewDetachedFromWindow(view: View) {
            backdropObserver?.takeIf { it.isAlive }?.removeOnPreDrawListener(backdropPreDraw)
            backdropObserver = null
            backdropNode.discardDisplayList()
        }
    }

    fun show() {
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        backdropSource = content.getChildAt(0) ?: content
        composeView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnDetachedFromWindow,
        )
        composeView.addOnAttachStateChangeListener(backdropAttachListener)
        composeView.setContent {
            val dark = (
                activity.resources.configuration.uiMode and
                    Configuration.UI_MODE_NIGHT_MASK
                ) == Configuration.UI_MODE_NIGHT_YES
            val backdrop = rememberCouiBackdrop(drawSource = {
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawRenderNode(backdropNode)
                }
            })
            val material = remember(dark) {
                if (CouiDialogDefaults.isMaterialSupported()) {
                    CouiDialogDefaults.material(dark)
                } else {
                    null
                }
            }
            CompositionLocalProvider(
                LocalCouiContourGlowEnabled provides contourGlowEnabled,
            ) {
                CouiTheme(darkTheme = dark) {
                    Box(Modifier.fillMaxSize()) {
                        Box(
                            Modifier.fillMaxSize()
                                .graphicsLayer { alpha = 0f }
                                .couiBackdrop(backdrop),
                        )
                        Dialog(
                            onDismissRequest = { finish(Outcome.DISMISS) },
                            title = title,
                            message = message,
                            dismissAction = CouiDialogAction(
                                text = dismissText,
                                onClick = { finish(Outcome.DISMISS) },
                            ),
                            neutralAction = neutralText?.let { text ->
                                CouiDialogAction(
                                    text = text,
                                    onClick = { finish(Outcome.NEUTRAL) },
                                )
                            },
                            confirmAction = CouiDialogAction(
                                text = confirmText,
                                onClick = { finish(Outcome.CONFIRM) },
                                style = CouiDialogActionStyle.Primary,
                                colors = CouiDialogDefaults.primaryActionColors(Color(accentColor)),
                            ),
                            dark = dark,
                            colors = CouiDialogDefaults.themeColors(dark),
                            placement = CouiDialogPlacement.Center,
                            material = material,
                            backdrop = backdrop,
                            visible = visible,
                        )
                    }
                }
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

    private fun captureBackdrop() {
        val source = backdropSource ?: return
        if (!composeView.isAttachedToWindow || !source.isAttachedToWindow) return
        val width = composeView.width
        val height = composeView.height
        if (width <= 0 || height <= 0) return
        val now = SystemClock.uptimeMillis()
        if (now - lastBackdropCaptureAt < 16L) return
        lastBackdropCaptureAt = now
        try {
            val recording = backdropNode.beginRecording(width, height)
            try {
                val hostLocation = IntArray(2)
                val sourceLocation = IntArray(2)
                composeView.getLocationInWindow(hostLocation)
                source.getLocationInWindow(sourceLocation)
                recording.save()
                recording.translate(
                    (sourceLocation[0] - hostLocation[0]).toFloat(),
                    (sourceLocation[1] - hostLocation[1]).toFloat(),
                )
                source.draw(recording)
                recording.restore()
            } finally {
                backdropNode.endRecording()
            }
        } catch (_: RuntimeException) {
            backdropNode.discardDisplayList()
        }
    }

    private fun finish(outcome: Outcome) {
        if (finished) return
        finished = true
        visible = false
        composeView.postDelayed({
            (composeView.parent as? ViewGroup)?.removeView(composeView)
            when (outcome) {
                Outcome.CONFIRM -> onConfirm?.run()
                Outcome.NEUTRAL -> onNeutral?.run()
                Outcome.DISMISS -> onDismiss?.run()
            }
        }, 190L)
    }

    private enum class Outcome { DISMISS, CONFIRM, NEUTRAL }

    companion object {
        @JvmStatic
        fun show(
            activity: Activity,
            title: String,
            message: String,
            dismissText: String,
            confirmText: String,
            accentColor: Int,
            contourGlowEnabled: Boolean,
            onDismiss: Runnable?,
            onConfirm: Runnable?,
        ) {
            show(activity, title, message, dismissText, confirmText, null,
                accentColor, contourGlowEnabled, onDismiss, onConfirm, null)
        }

        @JvmStatic
        fun show(
            activity: Activity,
            title: String,
            message: String,
            dismissText: String,
            confirmText: String,
            neutralText: String?,
            accentColor: Int,
            contourGlowEnabled: Boolean,
            onDismiss: Runnable?,
            onConfirm: Runnable?,
            onNeutral: Runnable?,
        ) {
            CouiConfirmDialog(
                activity = activity,
                title = title,
                message = message,
                dismissText = dismissText,
                confirmText = confirmText,
                neutralText = neutralText,
                accentColor = accentColor,
                contourGlowEnabled = contourGlowEnabled,
                onDismiss = onDismiss,
                onConfirm = onConfirm,
                onNeutral = onNeutral,
            ).show()
        }
    }
}
