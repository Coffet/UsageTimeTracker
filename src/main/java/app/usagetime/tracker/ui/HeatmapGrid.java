package app.usagetime.tracker.ui;

import app.usagetime.tracker.reports.WeekHeatmap;
import javafx.geometry.HPos;
import javafx.scene.AccessibleRole;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * Seven-day by twenty-four-hour grid of foreground minutes. Intensity is shown
 * as a stepped color ramp, but every cell also carries its exact value in a
 * tooltip and accessible text, each row ends with the day's total, and a legend
 * below maps every shade to a minute range.
 */
final class HeatmapGrid extends GridPane {
    private static final double CELL_HEIGHT = 15;

    private final Region[][] cells = new Region[WeekHeatmap.DAYS][WeekHeatmap.HOURS];
    private final Label[] dayTotals = new Label[WeekHeatmap.DAYS];
    private LocalDate weekStart = LocalDate.now();

    HeatmapGrid() {
        getStyleClass().add("heatmap-grid");
        setHgap(2);
        setVgap(3);

        ColumnConstraints dayColumn = new ColumnConstraints();
        dayColumn.setMinWidth(34);
        getColumnConstraints().add(dayColumn);
        for (int hour = 0; hour < WeekHeatmap.HOURS; hour++) {
            ColumnConstraints hourColumn = new ColumnConstraints();
            hourColumn.setHgrow(Priority.ALWAYS);
            hourColumn.setPercentWidth(100.0 / WeekHeatmap.HOURS);
            hourColumn.setMinWidth(10);
            getColumnConstraints().add(hourColumn);
        }
        ColumnConstraints totalColumn = new ColumnConstraints();
        totalColumn.setMinWidth(64);
        totalColumn.setHalignment(HPos.RIGHT);
        getColumnConstraints().add(totalColumn);

        for (int hour = 0; hour < WeekHeatmap.HOURS; hour += 3) {
            Label axis = new Label(hourLabel(hour));
            axis.getStyleClass().add("heatmap-axis");
            add(axis, hour + 1, 0);
        }
        for (int day = 0; day < WeekHeatmap.DAYS; day++) {
            Label dayLabel = new Label(dayName(day));
            dayLabel.getStyleClass().add("heatmap-axis");
            add(dayLabel, 0, day + 1);
            for (int hour = 0; hour < WeekHeatmap.HOURS; hour++) {
                Region cell = new Region();
                cell.getStyleClass().addAll("heat-cell", "heat-0");
                cell.setMinHeight(CELL_HEIGHT);
                cell.setPrefHeight(CELL_HEIGHT);
                cell.setMaxHeight(CELL_HEIGHT);
                cell.setMaxWidth(Double.MAX_VALUE);
                cell.setAccessibleRole(AccessibleRole.TEXT);
                cells[day][hour] = cell;
                add(cell, hour + 1, day + 1);
            }
            Label total = new Label();
            total.getStyleClass().add("heatmap-total");
            dayTotals[day] = total;
            add(total, WeekHeatmap.HOURS + 1, day + 1);
        }
    }

    void setWeek(LocalDate weekStart, long[][] secondsPerHour) {
        this.weekStart = weekStart;
        for (int day = 0; day < WeekHeatmap.DAYS; day++) {
            long dayTotal = 0;
            LocalDate date = weekStart.plusDays(day);
            for (int hour = 0; hour < WeekHeatmap.HOURS; hour++) {
                long seconds = secondsPerHour[day][hour];
                dayTotal += seconds;
                Region cell = cells[day][hour];
                cell.getStyleClass().setAll("heat-cell", "heat-" + WeekHeatmap.intensityBucket(seconds));
                String description = date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.getDefault())
                        + " " + date.format(java.time.format.DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))
                        + ", " + hourLabel(hour) + " to " + hourLabel((hour + 1) % 24) + " - "
                        + (seconds == 0 ? "no tracked time"
                        : MainView.formatDuration(seconds) + " in the foreground");
                cell.setAccessibleText(description);
                Tooltip.install(cell, new Tooltip(description));
            }
            dayTotals[day].setText(dayTotal == 0 ? "" : MainView.formatDuration(dayTotal));
        }
    }

    LocalDate weekStart() {
        return weekStart;
    }

    private static String dayName(int index) {
        return DayOfWeek.of(index + 1).getDisplayName(TextStyle.SHORT, Locale.getDefault());
    }

    private static String hourLabel(int hour) {
        return java.time.LocalTime.of(hour, 0)
                .format(java.time.format.DateTimeFormatter.ofPattern("h a", Locale.getDefault()));
    }
}