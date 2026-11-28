// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.miam1ku.mibandoplusbridge.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class CouiOverscrollMathTest {
    private static final float DENSITY = 2.75f;

    @Test public void impactStaysBetweenTheConfiguredBounds() {
        float atRest = CouiNestedScrollView.impactForVelocity(0f, DENSITY);
        assertTrue(atRest >= 14f * DENSITY - 0.001f);
        assertTrue(atRest <= 72f * DENSITY + 0.001f);

        float extreme = CouiNestedScrollView.impactForVelocity(100_000f, DENSITY);
        assertTrue(extreme <= 72f * DENSITY + 0.001f);
    }

    @Test public void impactNeverDecreasesWithVelocity() {
        float previous = -1f;
        for (float velocity : new float[] {0f, 100f, 1_000f, 3_000f, 6_000f, 12_000f, 100_000f}) {
            float impact = CouiNestedScrollView.impactForVelocity(velocity, DENSITY);
            assertTrue("impact must not decrease at " + velocity, impact >= previous);
            previous = impact;
        }
    }

    @Test public void directionDoesNotChangeTheMagnitude() {
        assertEquals(CouiNestedScrollView.impactForVelocity(6_000f, DENSITY),
                CouiNestedScrollView.impactForVelocity(-6_000f, DENSITY), 0.0001f);
    }
}
