package app.usagetime.tracker.platform.windows;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class WindowsProviderTest {
    @Test
    void hwndHasPublicNoArgConstructorForJna() throws ReflectiveOperationException {
        assertNotNull(WindowsProvider.HWND.class.getConstructor().newInstance());
    }
}
