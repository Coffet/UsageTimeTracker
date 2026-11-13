package app.usagetime.tracker.model;

import java.util.Locale;

public final class AppCategories {
    private AppCategories() {
    }

    public static String detect(String appName) {
        String name = appName.trim().toLowerCase(Locale.ROOT);
        if (name.endsWith(".exe")) {
            name = name.substring(0, name.length() - 4);
        }
        return switch (name) {
            case "firefox", "chrome", "google chrome", "chromium", "msedge",
                    "microsoft edge", "brave", "brave browser", "safari", "opera", "vivaldi" -> "Browser";
            case "code", "code - insiders", "code-insiders", "visual studio code",
                    "devenv", "visual studio", "idea", "idea64", "intellij idea",
                    "webstorm", "webstorm64", "rider", "rider64", "xcode", "unity",
                    "unity hub", "godot" -> "Development";
            case "winword", "microsoft word", "excel", "microsoft excel",
                    "powerpnt", "microsoft powerpoint", "libreoffice", "soffice.bin",
                    "obsidian", "notion" -> "Productivity";
            case "discord", "slack", "teams", "ms-teams", "microsoft teams",
                    "zoom", "telegram", "signal" -> "Communication";
            case "photoshop", "illustrator", "figma", "blender", "gimp", "inkscape" -> "Creative";
            case "spotify", "vlc", "vlc media player" -> "Media";
            case "explorer", "finder", "nautilus", "dolphin" -> "System";
            default -> "";
        };
    }
}
