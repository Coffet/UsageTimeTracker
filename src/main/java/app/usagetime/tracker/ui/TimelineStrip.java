package app.usagetime.tracker.ui;

import app.usagetime.tracker.reports.DayTimeline;
import javafx.scene.AccessibleRole;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Region;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Horizontal 24-hour strip that draws each foreground session as one block.
 * Block position and width follow local wall-clock minutes; every block carries
 * a tooltip and accessible text so the visualization never relies on color alone.
 */
final class TimelineStrip extends Region {
    private static final double STRIP_HEIGHT = 26;
    private static final double MIN_BLOCK_WIDTH = 3;
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault());

    private List<DayTimeline.Block> blocks = List.of();
    private final List<Region> rendered = new ArrayList<>();

    TimelineStrip() {
        getStyleClass().add("timeline-strip");
        setMinHeight(STRIP_HEIGHT);
        setPrefHeight(STRIP_HEIGHT);
        setMaxHeight(STRIP_HEIGHT);
        setMaxWidth(Double.MAX_VALUE);
        setAccessibleRole(AccessibleRole.TEXT);
    }

    void setBlocks(List<DayTimeline.Block> blocks, Map<String, Integer> categoryColors) {
        this.blocks = List.copyOf(blocks);
        rendered.clear();
        getChildren().clear();
        if (blocks.isEmpty()) {
            setAccessibleText("No foreground time recorded this day.");
            return;
        }
        setAccessibleText(blocks.size() + " time blocks this day.");
        for (DayTimeline.Block block : blocks) {
            Region cell = new Region();
            cell.getStyleClass().add("timeline-block");
            String category = block.category() == null || block.category().isBlank()
                    ? "Uncategorized" : block.category();
            cell.getStyleClass().add("block-color-" + categoryColors.getOrDefault(category, 0));
            String description = block.name() + ", " + category + ", "
                    + minuteLabel(block.startMinute()) + " to " + minuteLabel(block.endMinuteExclusive())
                    + (block.inProgress() ? ", in progress" : "");
            cell.setAccessibleRole(AccessibleRole.TEXT);
            cell.setAccessibleText(description);
            Tooltip.install(cell, new Tooltip(description));
            rendered.add(cell);
            getChildren().add(cell);
        }
        requestLayout();
    }

    @Override
    protected void layoutChildren() {
        double width = getWidth();
        double height = getHeight();
        for (int i = 0; i < rendered.size(); i++) {
            DayTimeline.Block block = blocks.get(i);
            double x = width * block.startMinute() / DayTimeline.MINUTES_PER_DAY;
            double end = width * block.endMinuteExclusive() / DayTimeline.MINUTES_PER_DAY;
            double w = Math.max(MIN_BLOCK_WIDTH, end - x);
            if (x + w > width) {
                w = width - x;
            }
            rendered.get(i).resizeRelocate(snapPositionX(x), snapSizeY(1),
                    snapSizeX(w), snapSizeY(height - 2));
        }
    }

    static String minuteLabel(int minuteOfDay) {
        if (minuteOfDay >= DayTimeline.MINUTES_PER_DAY) {
            return "midnight";
        }
        return LocalTime.of(minuteOfDay / 60, minuteOfDay % 60).format(TIME);
    }
}