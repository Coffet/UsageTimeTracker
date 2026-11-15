package app.usagetime.tracker.ui;

import app.usagetime.tracker.reports.WeekHeatmap;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.scene.AccessibleRole;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * Seven-day by twenty-four-hour grid of foreground minutes. Intensity is shown
 * as a stepped color ramp, but every cell also carries its exact value in a
 * tooltip and accessible text, each row ends with the day's total, and a legend
 * below maps every shade to a minute range.
 */
final class HeatmapGrid extends GridPane {
    private static final double CELL_HEIGHT = 20;
    private static final DateTimeFormatter CELL_DATE = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault());
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d", Locale.getDefault());
    private static final DateTimeFormatter FULL_DAY =
            DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault());
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("h a", Locale.getDefault());

    private final Region[][] cells = new Region[WeekHeatmap.DAYS][WeekHeatmap.HOURS];
    private final Tooltip[][] tooltips = new Tooltip[WeekHeatmap.DAYS][WeekHeatmap.HOURS];
    private final Label[] dayLabels = new Label[WeekHeatmap.DAYS];
    private final Label[] dayTotals = new Label[WeekHeatmap.DAYS];
    private LocalDate weekStart = LocalDate.now();

    HeatmapGrid() {
        getStyleClass().add("heatmap-grid");
        setHgap(2);
        setVgap(4);
        configureColumns(this);

        Label dayHeading = new Label("DAY");
        dayHeading.getStyleClass().add("heatmap-heading");
        add(dayHeading, 0, 0);
        Label totalHeading = new Label("TOTAL");
        totalHeading.getStyleClass().add("heatmap-heading");
        add(totalHeading, WeekHeatmap.HOURS + 1, 0);

        for (int hour = 0; hour < WeekHeatmap.HOURS; hour += 6) {
            Label axis = new Label(hourLabel(hour));
            axis.getStyleClass().add("heatmap-axis");
            if (hour != 0) {
                GridPane.setMargin(axis, new Insets(0, 0, 0, 4));
            }
            add(axis, hour + 1, 0, 6, 1);
        }
        for (int day = 0; day < WeekHeatmap.DAYS; day++) {
            Label dayLabel = new Label();
            dayLabel.getStyleClass().add("heatmap-day");
            dayLabels[day] = dayLabel;
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
                Tooltip tooltip = new Tooltip();
                tooltip.setWrapText(true);
                tooltip.setMaxWidth(400);
                tooltips[day][hour] = tooltip;
                Tooltip.install(cell, tooltip);
                if (hour > 0 && hour % 6 == 0) {
                    GridPane.setMargin(cell, new Insets(0, 0, 0, 4));
                }
                add(cell, hour + 1, day + 1);
            }
            Label total = new Label();
            total.getStyleClass().add("heatmap-total");
            dayTotals[day] = total;
            add(total, WeekHeatmap.HOURS + 1, day + 1);
        }
    }

    void setWeek(LocalDate weekStart, WeekHeatmap.Cell[][] hourlyCells) {
        this.weekStart = weekStart;
        for (int day = 0; day < WeekHeatmap.DAYS; day++) {
            long dayTotal = 0;
            LocalDate date = weekStart.plusDays(day);
            dayLabels[day].setText(date.format(DAY));
            dayLabels[day].setAccessibleText(date.format(FULL_DAY));
            dayLabels[day].getStyleClass().setAll("heatmap-day",
                    date.equals(LocalDate.now()) ? "heatmap-day-today" : "heatmap-day-other");
            for (int hour = 0; hour < WeekHeatmap.HOURS; hour++) {
                WeekHeatmap.Cell hourCell = hourlyCells[day][hour];
                long seconds = hourCell.seconds();
                dayTotal += seconds;
                Region cell = cells[day][hour];
                cell.getStyleClass().setAll("heat-cell", "heat-" + WeekHeatmap.intensityBucket(seconds));
                String description = cellDescription(date, hour, hourCell);
                cell.setAccessibleText(description);
                tooltips[day][hour].setText(description);
            }
            dayTotals[day].setText(MainView.formatDuration(dayTotal));
        }
    }

    static String cellDescription(LocalDate date, int hour, WeekHeatmap.Cell cell) {
        String header = date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.getDefault())
                + " " + date.format(CELL_DATE)
                + ", " + hourLabel(hour) + " to " + hourLabel((hour + 1) % 24) + " - "
                + (cell.seconds() == 0 ? "no tracked time"
                : MainView.formatDuration(cell.seconds()) + " in the foreground");
        StringBuilder description = new StringBuilder(header);
        for (WeekHeatmap.AppTime app : cell.apps()) {
            description.append("\n").append(app.name()).append(" - ").append(MainView.formatDuration(app.seconds()));
        }
        return description.toString();
    }

    static void configureColumns(GridPane grid) {
        ColumnConstraints dayColumn = new ColumnConstraints();
        dayColumn.setMinWidth(64);
        grid.getColumnConstraints().add(dayColumn);
        for (int hour = 0; hour < WeekHeatmap.HOURS; hour++) {
            ColumnConstraints hourColumn = new ColumnConstraints();
            hourColumn.setMinWidth(10);
            hourColumn.setPrefWidth(10);
            hourColumn.setMaxWidth(Double.MAX_VALUE);
            hourColumn.setHgrow(Priority.ALWAYS);
            grid.getColumnConstraints().add(hourColumn);
        }
        ColumnConstraints totalColumn = new ColumnConstraints();
        totalColumn.setMinWidth(64);
        totalColumn.setHalignment(HPos.RIGHT);
        grid.getColumnConstraints().add(totalColumn);
    }

    LocalDate weekStart() {
        return weekStart;
    }

    private static String hourLabel(int hour) {
        return java.time.LocalTime.of(hour, 0).format(HOUR);
    }
}