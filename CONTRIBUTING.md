# Contributing

## Requirements

- JDK 17
- Maven 3.9+
- For Linux X11 manual tracking tests: `x11-utils` and `xprintidle`

## Before opening a change

1. Run `mvn test`.
2. Keep OS-specific detection behind `ForegroundProvider`.
3. Add focused tests for behavior changes, especially idle boundaries, session recovery, exports, and local-time period boundaries.
4. Do not add collection of window titles, web addresses, keystrokes, screenshots, or document names.
5. If platform support is partial, expose that in the UI and update the support table in `README.md`.

The application has no cloud backend. Do not add automatic data uploads or silent installation/update behavior.
