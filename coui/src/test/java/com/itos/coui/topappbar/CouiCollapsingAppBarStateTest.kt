package com.itos.coui.topappbar

import org.junit.Assert.assertEquals
import org.junit.Test

class CouiCollapsingAppBarStateTest {
    @Test
    fun fractionMapsOntoTheRange() {
        val state = CouiCollapsingAppBarState(100f, 0f)

        state.setCollapsedFraction(0f)
        assertEquals(0f, state.offsetPx, 0.001f)

        state.setCollapsedFraction(1f)
        assertEquals(100f, state.offsetPx, 0.001f)
    }

    @Test
    fun fractionIsClampedToTheRange() {
        val state = CouiCollapsingAppBarState(100f, 0f)

        state.setCollapsedFraction(1.5f)
        assertEquals(100f, state.offsetPx, 0.001f)
        assertEquals(1f, state.collapsedFraction, 0.001f)

        state.setCollapsedFraction(-0.5f)
        assertEquals(0f, state.offsetPx, 0.001f)
        assertEquals(0f, state.collapsedFraction, 0.001f)
    }

    @Test
    fun repeatingTheSameFractionIsIdempotent() {
        val state = CouiCollapsingAppBarState(100f, 0f)

        state.setCollapsedFraction(0.37f)
        val firstOffset = state.offsetPx
        val firstContentOffset = state.contentOffsetPx

        state.setCollapsedFraction(0.37f)
        assertEquals(firstOffset, state.offsetPx, 0f)
        assertEquals(firstContentOffset, state.contentOffsetPx, 0f)
    }
}
