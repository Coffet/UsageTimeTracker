package app.usagetime.tracker.ui;

import javafx.scene.shape.Line;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClockFaceTest {
    @Test
    void showsTheTimeWithMovingHandsAndAccessibleText() {
        ClockFace clock = new ClockFace();
        clock.setTime(LocalTime.of(12, 0), "12:00:00");
        Line hour = hand(clock, "clock-hour");
        Line minute = hand(clock, "clock-minute");
        Line second = hand(clock, "clock-second");
        assertEquals(76, hour.getEndX(), 0.001);
        assertEquals(44, hour.getEndY(), 0.001);
        assertEquals(28, minute.getEndY(), 0.001);
        assertEquals(25, second.getEndY(), 0.001);

        clock.setTime(LocalTime.of(3, 15, 30), "3:15:30");
        assertTrue(hour.getEndX() > 107);
        assertTrue(hour.getEndY() > 76);
        assertTrue(minute.getEndX() > 123);
        assertTrue(second.getEndY() > 126);
        assertEquals("Local time 3:15:30", clock.getAccessibleText());
    }

    private static Line hand(ClockFace clock, String style) {
        return clock.getChildrenUnmodifiable().stream()
                .filter(node -> node.getStyleClass().contains(style))
                .map(Line.class::cast)
                .findFirst().orElseThrow();
    }
}
