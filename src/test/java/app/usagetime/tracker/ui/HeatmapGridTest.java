package app.usagetime.tracker.ui;

import app.usagetime.tracker.reports.WeekHeatmap;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeatmapGridTest {
    @Test
    void hoverDescriptionIncludesEachAppsTimeAndHandlesEmptyHours() {
        LocalDate date = LocalDate.of(2026, 6, 15);
        String description = HeatmapGrid.cellDescription(date, 10, new WeekHeatmap.Cell(95, List.of(
                new WeekHeatmap.AppTime("editor", "Editor", 65),
                new WeekHeatmap.AppTime("browser", "Browser", 30))));

        assertTrue(description.contains("1m 35s in the foreground"));
        assertTrue(description.contains("\nEditor - 1m 05s"));
        assertTrue(description.contains("\nBrowser - 30s"));
        assertTrue(HeatmapGrid.cellDescription(date, 11, new WeekHeatmap.Cell(0, List.of()))
                .contains("no tracked time"));
    }

    @Test
    void hourCellsAndDayTotalsFitWithinAvailableWidth() {
        for (double width : new double[]{600, 1000}) {
            GridPane grid = new GridPane();
            grid.setHgap(2);
            HeatmapGrid.configureColumns(grid);
            Region day = new Region();
            grid.add(day, 0, 0);
            Region[] cells = new Region[WeekHeatmap.HOURS];
            for (int hour = 0; hour < cells.length; hour++) {
                cells[hour] = new Region();
                cells[hour].setMaxWidth(Double.MAX_VALUE);
                grid.add(cells[hour], hour + 1, 0);
            }
            Region total = new Region();
            total.setMinWidth(64);
            total.setPrefWidth(64);
            grid.add(total, WeekHeatmap.HOURS + 1, 0);
            grid.resize(width, 20);
            grid.layout();

            assertTrue(total.getBoundsInParent().getMaxX() <= width);
            for (Region cell : cells) {
                assertEquals(cells[0].getWidth(), cell.getWidth(), 1);
                assertTrue(cell.getBoundsInParent().getMaxX() <= total.getBoundsInParent().getMinX());
            }
        }
    }
}
