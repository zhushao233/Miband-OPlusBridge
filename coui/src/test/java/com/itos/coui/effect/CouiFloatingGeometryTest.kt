package com.itos.coui.effect

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Test

class CouiFloatingGeometryTest {
    private val surface = CouiFloatingSurface(Rect(0f, 0f, 100f, 50f))

    @Test
    fun zeroPaddingKeepsTheKernelMargin() {
        assertEquals(11f, -surface.materialRect(0, 0f).top, 0.001f)
    }

    @Test
    fun smallPaddingStillKeepsTheKernelMargin() {
        assertEquals(11f, -surface.materialRect(0, 5f).top, 0.001f)
    }

    @Test
    fun largePaddingFollowsTheKernelMargin() {
        assertEquals(41f, -surface.materialRect(0, 40f).top, 0.001f)
    }

    @Test
    fun fractionalPaddingIsRoundedUp() {
        assertEquals(42f, -surface.materialRect(0, 40.2f).top, 0.001f)
    }
}
