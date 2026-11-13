package app.usagetime.tracker.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppCategoriesTest {
    @Test
    void recognizesCommonCrossPlatformAppNames() {
        assertEquals("Browser", AppCategories.detect("Firefox.exe"));
        assertEquals("Browser", AppCategories.detect("Google Chrome"));
        assertEquals("Development", AppCategories.detect("code"));
        assertEquals("Development", AppCategories.detect("Unity.exe"));
        assertEquals("Communication", AppCategories.detect("Discord.exe"));
        assertEquals("System", AppCategories.detect("Finder"));
    }

    @Test
    void doesNotGuessFromPartialNames() {
        assertEquals("", AppCategories.detect("My Firefox Tool.exe"));
        assertEquals("", AppCategories.detect("Process 1234"));
    }
}
