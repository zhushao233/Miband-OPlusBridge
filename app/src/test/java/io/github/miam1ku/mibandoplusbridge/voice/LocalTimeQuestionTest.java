// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.miam1ku.mibandoplusbridge.voice;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class LocalTimeQuestionTest {
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-11-26T14:31:00Z"), ZoneId.of("Asia/Shanghai"));

    @Test public void answersLocalClockTime() {
        assertEquals("现在是 22:31，11月26日 星期四。",
                LocalTimeQuestion.answer("现在几点了", CLOCK));
        assertEquals("现在是 22:31，11月26日 星期四。",
                LocalTimeQuestion.answer("现在是什么时间", CLOCK));
    }

    @Test public void answersBothUnitedStatesCoastsWhenNoCityIsGiven() {
        String answer = LocalTimeQuestion.answer("美国现在是什么时间", CLOCK);
        assertTrue(answer.contains("09:31"));
        assertTrue(answer.contains("06:31"));
        assertTrue(answer.contains("11月26日"));
    }

    @Test public void answersTheRequestedUnitedStatesZone() {
        assertTrue(LocalTimeQuestion.answer("纽约现在几点", CLOCK).contains("09:31"));
        assertTrue(LocalTimeQuestion.answer("洛杉矶现在几点", CLOCK).contains("06:31"));
        assertTrue(LocalTimeQuestion.answer("芝加哥现在几点", CLOCK).contains("08:31"));
    }

    @Test public void countsDownToTheNextSpringFestival() {
        assertEquals("距离 2027 年春节（2月6日）还有 72 天。",
                LocalTimeQuestion.answer("离春节还有多久", CLOCK));
        assertEquals("2027 年春节是 2月6日，星期六。",
                LocalTimeQuestion.answer("春节是哪一天", CLOCK));
    }

    @Test public void doesNotClaimAlarmOrModelQuestions() {
        assertNull(LocalTimeQuestion.answer("设置明早七点的闹钟", CLOCK));
        assertNull(LocalTimeQuestion.answer("今天适合跑步吗", CLOCK));
    }
}
