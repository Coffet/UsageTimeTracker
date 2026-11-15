# UsageTime Tracker

A quiet, local-first desktop app-usage tracker. It records foreground application names and session times, pauses after five idle minutes, and keeps reports on this device.

> **MVP status:** the tracking engine, SQLite history, reports, categories, per-app goals, themes, and CSV/JSON export are implemented. OS-specific foreground detection has different prerequisites; see [Platform support](#platform-support). Native packaging scripts and draft-release CI are included. Public installers still require signing credentials; the MVP has no self-updater.

## What it tracks

- Foreground application identity/name and start/end times
- Local categories and optional daily goals
- Idle pauses after five minutes without input
- Daily, week-to-date, and month-to-date app and custom-category totals; a 30-day session list
- A 24-hour time-block timeline, category breakdown, and a week-by-hour heatmap with per-app hover details, sub-minute activity, a minute-range legend, and per-day totals
- Gentle in-app focus-goal reminders and a confirmed action to delete tracked history

It does **not** capture window titles, URLs, keystrokes, screenshots, document names, or send usage data to a service. Nothing is transmitted automatically. The application stores its SQLite database in `~/.usage-time-tracker/usage.db` on macOS/Linux and `%USERPROFILE%\.usage-time-tracker\usage.db` on Windows.

## Platform support

| Platform/session | Foreground app | Idle | Notes |
|---|---|---|---|
| Windows 10/11 | Win32 foreground window and process ID | `GetLastInputInfo` | Uses JNA; no external utility required |
| macOS | System Events frontmost process | `IOHIDSystem` idle time | `osascript` and `ioreg`; macOS may ask to allow Automation access |
| Linux X11 | EWMH active window and process ID | `xprintidle` | Requires `xprop` and `xprintidle` |
| Linux Wayland | Not generally available | — | Compositor-specific portals/APIs are not yet implemented; the app reports the limitation instead of claiming complete tracking |

Foreground detection is sampled every three seconds, so very brief switches can be missed. After a successful sample gap longer than 30 seconds, the app ends the previous interval at its last confirmed sample instead of charging the gap. Known lock-screen and screensaver processes are excluded; session lock detection otherwise depends on the desktop provider, and the five-minute idle rule is the fallback. If the OS or desktop does not provide the required information, tracking status displays an error; it does not substitute guessed data. A crash recovery closes an interrupted session at its last 10-second checkpoint. Exports label any still-open session as in progress rather than inventing an end time.

Only the foreground application receives time; other apps can remain open without accumulating usage. Switching between apps ends one foreground session and starts another, and multiple windows of the same application are grouped by their detected application identity. Displayed durations include seconds so a live session does not appear as `0m` or lose the remainder after a whole minute.

The app categorizes a limited set of recognized application names locally; unknown applications remain uncategorized. In Settings, add a category for an unrecognized app; use the same name to group apps, or clear the name to remove the assignment. In Settings, select **Show goal controls** to reveal optional per-app daily goals in Overview, and leave a goal blank to remove it. Hiding the goal controls does not remove existing goals or disable their reminders.

The Overview shows a live local clock, the selected period's total, a daily timeline, application and category tables, a weekly heatmap, and recent sessions. Navigation and tracking status remain at the top.

## Run from source

Requirements: JDK 17 and an internet connection for the first build. The Maven wrapper downloads the project's Maven version automatically. Java 17 is the source, CI, and release target. Java 21 is not currently used; the project can be upgraded if you decide to later.

```powershell
.\mvnw.cmd test
.\mvnw.cmd javafx:run
```

On macOS or Linux, use `sh ./mvnw test` and `sh ./mvnw javafx:run` instead.

On Linux X11, install `x11-utils` and `xprintidle`. The database can be deleted from the Settings page's displayed location after quitting the app. CSV and versioned JSON exports are explicit user-initiated file saves.

## Architecture

```text
JavaFX UI
  ├─ Overview: 24-hour time-block timeline, week-by-hour heatmap with legend,
  │  app/category totals, and recent sessions
  ├─ History: the last 30 days of foreground sessions
  ├─ Settings: appearance, privacy, exports, deletion
  └─ UI-safe status and goal feedback
Application services
  ├─ TrackingEngine: sampling, idle cutoff, transitions, recovery
  └─ ExportService: CSV / schema-versioned JSON
Platform adapter
  ├─ Windows Win32/JNA
  ├─ macOS System Events + HID idle time
  └─ Linux X11 utilities (Wayland explicitly unsupported)
Storage
  └─ Versioned SQLite: applications, sessions, local preferences
```

The tracking engine and storage APIs are independent of JavaFX. `ForegroundProvider` isolates the platform adapter. SQLite writes are serialized, parameterized, and checkpoint active sessions so a crash does not invent time while the application was closed.

### Project layout

```text
src/main/java/app/usagetime/tracker/
  model/       immutable records
  platform/    OS selection and native/system integrations
  tracking/    polling engine and session state
  storage/     repository interface and SQLite implementation
  export/      CSV and JSON writers
  ui/          JavaFX screens
src/main/resources/styles/  light and dark themes
src/test/                   engine, database, and export tests
scripts/                    native packaging helpers
.github/workflows/          test and release workflows
```

## Build and package

Build on the **target OS and architecture** using JDK 17 and the Maven wrapper. The scripts run tests and write packages to `target/dist`. They run `clean`, which deletes the entire `target` directory, including previous installers: copy any files you want to keep elsewhere first.

### Windows: EXE, MSI, and portable ZIP

Install JDK 17 and [WiX 3.14](https://github.com/wixtoolset/wix3/releases/tag/wix3141rtm). Add the JDK and WiX `bin` directories to `PATH`. From the project directory in PowerShell:

```powershell
java -version
.\mvnw.cmd -version
jpackage --version
Get-Command candle, light
.\scripts\package.ps1
```

The EXE and MSI offer an installation-folder chooser. The ZIP is a portable application: extract it and run `UsageTimeTracker.exe` inside the extracted folder.

### macOS: DMG

Install a native JDK 17. Build separately on an Intel Mac and an Apple Silicon Mac to produce both DMGs. From the project directory in Terminal:

```sh
java -version
sh ./mvnw -version
export JAVA_HOME="$(java -XshowSettings:properties -version 2>&1 | sed -n 's/^[[:space:]]*java\.home = //p')"
cd scripts
bash package.sh
```

Copy the application from the DMG to a location of your choice. Signed distribution also requires Xcode command-line tools, an Apple signing identity, and notarization.

### Linux: DEB, RPM, and AppImage

Install JDK 17, `fakeroot`, `rpm`, and `curl`. For example, on Ubuntu:

```sh
sudo apt install openjdk-17-jdk fakeroot rpm curl x11-utils xprintidle
```

From the project directory:

```sh
java -version
sh ./mvnw -version
export JAVA_HOME="$(java -XshowSettings:properties -version 2>&1 | sed -n 's/^[[:space:]]*java\.home = //p')"
cd scripts
bash package.sh
```

The AppImage build targets x86-64. DEB/RPM locations are controlled by the package manager; the AppImage can be placed anywhere. The DEB declares its GTK, `x11-utils`, and `xprintidle` dependencies; RPM and AppImage users must make GTK 3, `xprop`, and `xprintidle` available separately. Linux foreground tracking requires X11; Wayland is not supported.

All packages use the same application code, a trimmed, compressed Java runtime, and only the SQLite native libraries needed on the build platform. Unsigned local packages are for development/testing, not public distribution.

### CI and releases

- Pull requests and pushes run the JUnit suite on Windows, macOS, and Linux.
- Pushing a `v*` tag runs tests and builds OS-native packages on native GitHub-hosted runners, then creates a draft GitHub Release with the artifacts.
- The draft includes SHA-256 checksums for downloaded installers.
- Draft release notes summarize the features, platform requirements, and unsigned-package status.
- Before public distribution, configure signing/notarization credentials as protected GitHub Actions secrets and add the corresponding signing steps to the release workflow. The workflow never receives signing secrets on pull requests.
- Use a protected release environment and require maintainers to review/promote the draft. Third-party GitHub Actions are pinned to commits; the AppImage packaging binary is SHA-256 verified. Do not publish unsigned builds as trusted releases.

### Updates

The current MVP does not self-update. Release packages are unsigned until platform signing identities and notarization credentials are configured; draft CI releases should not be treated as trusted installers. After signing is configured, the safe update path is a manually initiated download from a signed release; Linux users can also use their package manager. Do not download or execute an update silently. Add an opt-in version check only after the release URL and signature-verification policy are established; validate signatures before offering an installer.

## Development

- JDK 17 is the development, CI, and release target.
- Run `.\mvnw.cmd test` on Windows or `sh ./mvnw test` on macOS/Linux before submitting changes.
- Keep platform-specific code behind `ForegroundProvider`; unavailable tracking APIs should produce a visible limitation, never fabricated data.
- Keep collection local and limited to the data described above.

See [CONTRIBUTING.md](CONTRIBUTING.md) for change and test expectations.
