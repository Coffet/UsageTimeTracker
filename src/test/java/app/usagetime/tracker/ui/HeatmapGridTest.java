package app.usagetime.tracker.ui;

import app.usagetime.tracker.reports.WeekHeatmap;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeatmapGridTest {
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
