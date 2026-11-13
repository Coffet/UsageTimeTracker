package app.usagetime.tracker.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MainViewDurationTest {
    @Test
    void showsSecondsInsteadOfRoundingShortUsageDownToZeroMinutes() {
        assertEquals("0s", MainView.formatDuration(0));
        assertEquals("14s", MainView.formatDuration(14));
        assertEquals("59s", MainView.formatDuration(59));
        assertEquals("1m 00s", MainView.formatDuration(60));
        assertEquals("1m 30s", MainView.formatDuration(90));
        assertEquals("1h 01m 01s", MainView.formatDuration(3_661));
    }

    @Test
    void dailyGoalMayBeLeftBlankOrCleared() {
        assertEquals(0, MainView.parseGoalMinutes(""));
        assertEquals(0, MainView.parseGoalMinutes("  "));
        assertEquals(0, MainView.parseGoalMinutes("0"));
        assertEquals(90, MainView.parseGoalMinutes(" 90 "));
        assertEquals(1440, MainView.parseGoalMinutes("1440"));
    }

    @Test
    void rejectsInvalidDailyGoals() {
        assertThrows(IllegalArgumentException.class, () -> MainView.parseGoalMinutes("-1"));
        assertThrows(IllegalArgumentException.class, () -> MainView.parseGoalMinutes("1441"));
        assertThrows(IllegalArgumentException.class, () -> MainView.parseGoalMinutes("1.5"));
        assertThrows(IllegalArgumentException.class, () -> MainView.parseGoalMinutes("work"));
    }
}
