package app.usagetime.tracker.reports;

import app.usagetime.tracker.model.AppUsage;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class UsageReports {
    private UsageReports() {
    }

    public static List<CategoryTotal> byCategory(List<AppUsage> usage) {
        Map<String, Long> totals = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (AppUsage item : usage) {
            String category = item.category() == null || item.category().isBlank()
                    ? "Uncategorized"
                    : item.category();
            totals.merge(category, item.seconds(), Long::sum);
        }
        return totals.entrySet().stream()
                .map(entry -> new CategoryTotal(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingLong(CategoryTotal::seconds).reversed())
                .toList();
    }
}
