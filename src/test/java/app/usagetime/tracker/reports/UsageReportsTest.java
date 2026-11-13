package app.usagetime.tracker.reports;

import app.usagetime.tracker.model.AppUsage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UsageReportsTest {
    @Test
    void groupsDurationsAndPlacesMissingCategoriesInUncategorized() {
        List<CategoryTotal> totals = UsageReports.byCategory(List.of(
                new AppUsage("editor", "Editor", "Work", 120, 0),
                new AppUsage("terminal", "Terminal", "Work", 60, 0),
                new AppUsage("music", "Music", "", 90, 0)
        ));

        assertEquals(List.of(
                new CategoryTotal("Work", 180),
                new CategoryTotal("Uncategorized", 90)
        ), totals);
    }
}
