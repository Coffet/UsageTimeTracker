package app.usagetime.tracker.model;

public record AppUsage(String appId, String name, String category, long seconds, int goalMinutes) {
}
