package com.factoryguard.demo;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StatsServiceTest {

    private StatsService make(int windowSeconds, boolean bugMode) {
        StatsService s = new StatsService();
        ReflectionTestUtils.setField(s, "version", "v-test");
        ReflectionTestUtils.setField(s, "bugMode", bugMode);
        ReflectionTestUtils.setField(s, "windowSeconds", windowSeconds);
        return s;
    }

    @Test
    void startsAtZero() {
        StatsService s = make(20, false);
        assertEquals(0.0, (double) s.snapshot().get("eventsPerMinute"), 0.01);
        assertEquals(0L, s.snapshot().get("processedTotal"));
    }

    @Test
    void convertsWindowCountToEventsPerMinute() {
        StatsService s = make(20, false);
        ReflectionTestUtils.setField(s, "connectedAtMillis", System.currentTimeMillis() - 20_000);
        for (int i = 0; i < 10; i++) {
            s.recordProcessed();
        }
        // 10 events in a 20 second window = 30 per minute
        assertEquals(30.0, (double) s.snapshot().get("eventsPerMinute"), 0.01);
        assertEquals(10L, s.snapshot().get("processedTotal"));
    }

    @Test
    void reportsVersionAndBugMode() {
        StatsService s = make(20, true);
        assertEquals("v-test", s.snapshot().get("version"));
        assertEquals(true, s.snapshot().get("bugMode"));
    }

    @Test
    void youngAppIsMeasuredOverItsOwnAgeNotTheFullWindow() {
        StatsService s = make(20, false);
        ReflectionTestUtils.setField(s, "connectedAtMillis", System.currentTimeMillis() - 10_000);
        for (int i = 0; i < 17; i++) {
            s.recordProcessed();
        }
        // 17 events in the 10 seconds since connecting = about 102 per minute, not 51
        assertEquals(102.0, (double) s.snapshot().get("eventsPerMinute"), 3.0);
    }

    @Test
    void notConnectedMeansZero() {
        StatsService s = make(20, false);
        s.recordProcessed();
        assertEquals(0.0, (double) s.snapshot().get("eventsPerMinute"), 0.01);
    }
}
