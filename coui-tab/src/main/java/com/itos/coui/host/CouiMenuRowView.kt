package com.itos.coui.host

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.itos.coui.effect.LocalCouiContourGlowEnabled
import com.itos.coui.menu.CouiMenuItem
import com.itos.coui.menu.Menu
import com.itos.coui.menu.ProvideCouiMenuBackdrop
import com.itos.coui.menu.couiMenuAnchor
import com.itos.coui.menu.rememberCouiMenuAnchor
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text as CouiText
import com.itos.coui.theme.CouiTheme

/** Java-friendly COUI selection row backed by the original COUI menu. */
class CouiMenuRowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {
    fun interface OnValueSelectedListener {
        fun onValueSelected(value: String): Boolean
    }

    private val composeView = ComposeView(context)
    private var titleState by mutableStateOf("")
    private var summaryState by mutableStateOf("")
    private var selectedValueState by mutableStateOf("")
    private var valuesState by mutableStateOf<List<String>>(emptyList())
    private var labelsState by mutableStateOf<List<String>>(emptyList())
    private var contourEnabled by mutableStateOf(true)
    private var listener: OnValueSelectedListener? = null

    init {
        clipChildren = false
        clipToPadding = false
        composeView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnDetachedFromWindow,
        )
        addView(composeView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        composeView.setContent {
            val dark = (
                resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK
                ) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            var expanded by remember { mutableStateOf(false) }
            val anchor = rememberCouiMenuAnchor()
            val colors = CouiTheme.colorSchemeFor(dark)
            val items = valuesState.indices.map { index ->
                CouiMenuItem(
                    value = valuesState[index],
                    text = labelsState[index],
                    selected = valuesState[index] == selectedValueState,
                )
            }
            val selectedLabel = labelsState.getOrNull(valuesState.indexOf(selectedValueState))
                ?: selectedValueState

            CompositionLocalProvider(
                LocalCouiContourGlowEnabled provides contourEnabled,
            ) {
                CouiTheme(darkTheme = dark) {
                    ProvideCouiMenuBackdrop { backdropModifier ->
                        Box(backdropModifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 64.dp)
                                    .couiMenuAnchor(anchor)
                                    .semantics(mergeDescendants = true) {}
                                    .clickable(role = Role.DropdownList) {
                                        anchor.open { expanded = true }
                                    }
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    CouiText(
                                        text = titleState,
                                        kind = CouiTextKind.ItemTitle,
                                        color = colors.onSurface,
                                    )
                                    if (summaryState.isNotEmpty()) {
                                        Spacer(Modifier.height(2.dp))
                                        CouiText(
                                            text = summaryState,
                                            kind = CouiTextKind.ItemSummary,
                                            color = colors.onSurfaceVariant,
                                        )
                                    }
                                }
                                CouiText(
                                    text = selectedLabel,
                                    kind = CouiTextKind.ItemValue,
                                    color = colors.accentOnSurface,
                                )
                                Spacer(Modifier.width(8.dp))
                                CouiText(
                                    text = "⌄",
                                    kind = CouiTextKind.ItemValue,
                                    color = colors.onSurfaceVariant,
                                )
                            }
                            Menu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                                items = items,
                                onItemClick = { item ->
                                    expanded = false
                                    if (item.value != selectedValueState &&
                                        (listener?.onValueSelected(item.value) ?: true)
                                    ) {
                                        selectedValueState = item.value
                                    }
                                },
                                anchor = anchor,
                                headerText = titleState,
                            )
                        }
                    }
                }
            }
        }
    }

    fun setTitle(title: String) {
        titleState = title
    }

    fun setSummary(summary: String) {
        summaryState = summary
    }

    fun setItems(values: Array<String>, labels: Array<String>) {
        require(values.size == labels.size) { "COUI menu values and labels must have equal size" }
        valuesState = values.toList()
        labelsState = labels.toList()
    }

    fun setSelectedValue(value: String) {
        selectedValueState = value
    }

    fun setOnValueSelectedListener(listener: OnValueSelectedListener?) {
        this.listener = listener
    }

    fun setContourGlowEnabled(enabled: Boolean) {
        contourEnabled = enabled
    }
}
