package app.usagetime.tracker.model;

public record DetectedApp(String id, String name) {
    public DetectedApp {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Application id must not be blank");
        }
        if (name == null || name.isBlank()) {
            name = id;
        }
    }
}
