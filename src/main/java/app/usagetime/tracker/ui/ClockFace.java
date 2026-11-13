package app.usagetime.tracker.ui;

import javafx.scene.AccessibleRole;
import javafx.scene.layout.Region;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;

import java.time.LocalTime;

final class ClockFace extends Region {
    private static final double SIZE = 152;
    private static final double CENTER = SIZE / 2;
    private final Line hourHand = hand("clock-hour");
    private final Line minuteHand = hand("clock-minute");
    private final Line secondHand = hand("clock-second");

    ClockFace() {
        setMinSize(SIZE, SIZE);
        setPrefSize(SIZE, SIZE);
        setMaxSize(SIZE, SIZE);
        setAccessibleRole(AccessibleRole.TEXT);

        Circle face = new Circle(CENTER, CENTER, 69);
        face.getStyleClass().add("clock-face");
        getChildren().add(face);
        for (int hour = 0; hour < 12; hour++) {
            double angle = hour * Math.PI / 6;
            Line mark = new Line(
                    CENTER + Math.sin(angle) * 58, CENTER - Math.cos(angle) * 58,
                    CENTER + Math.sin(angle) * 63, CENTER - Math.cos(angle) * 63);
            mark.getStyleClass().add("clock-mark");
            getChildren().add(mark);
        }
        Circle pin = new Circle(CENTER, CENTER, 3);
        pin.getStyleClass().add("clock-pin");
        getChildren().addAll(hourHand, minuteHand, secondHand, pin);
    }

    void setTime(LocalTime time, String description) {
        double seconds = time.getSecond();
        double minutes = time.getMinute() + seconds / 60;
        double hours = time.getHour() % 12 + minutes / 60;
        pointHand(hourHand, hours / 12, 32);
        pointHand(minuteHand, minutes / 60, 48);
        pointHand(secondHand, seconds / 60, 51);
        setAccessibleText("Local time " + description);
    }

    private static Line hand(String style) {
        Line line = new Line(CENTER, CENTER, CENTER, CENTER);
        line.getStyleClass().add(style);
        return line;
    }

    private static void pointHand(Line line, double rotation, double length) {
        double angle = rotation * 2 * Math.PI;
        line.setEndX(CENTER + Math.sin(angle) * length);
        line.setEndY(CENTER - Math.cos(angle) * length);
    }
}
