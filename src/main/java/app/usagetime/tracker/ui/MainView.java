package app.usagetime.tracker.ui;

import app.usagetime.tracker.export.ExportService;
import app.usagetime.tracker.model.AppUsage;
import app.usagetime.tracker.model.AppCategories;
import app.usagetime.tracker.model.SessionRecord;
import app.usagetime.tracker.model.TrackedApp;
import app.usagetime.tracker.reports.CategoryTotal;
import app.usagetime.tracker.reports.DayTimeline;
import app.usagetime.tracker.reports.ReportRange;
import app.usagetime.tracker.reports.UsageReports;
import app.usagetime.tracker.reports.WeekHeatmap;
import app.usagetime.tracker.storage.UsageRepository;
import app.usagetime.tracker.tracking.TrackingEngine;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.util.Duration;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

public final class MainView extends BorderPane {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault());
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault());
    private static final DateTimeFormatter SESSION_DAY = DateTimeFormatter.ofPattern("EEE h:mm a", Locale.getDefault());
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM);
    private static final int BLOCK_COLORS = 6;
    private static final int RECENT_LIMIT = 5;

    private final UsageRepository repository;
    private final TrackingEngine engine;
    private final Path databasePath;
    private final Consumer<String> statusWriter;
    private final Label statusLabel = new Label();
    private final Region statusDot = new Region();
    private final Label totalLabel = new Label();
    private final Label clockLabel = new Label();
    private final ClockFace clockFace = new ClockFace();
    private final Timeline clockTicker = new Timeline(
            new KeyFrame(Duration.seconds(1), event -> updateClock()));
    private final Label rangeLabel = new Label();
    private final Label goalNotice = new Label();
    private final DatePicker datePicker = new DatePicker(LocalDate.now());
    private final ToggleGroup periodGroup = new ToggleGroup();
    private final Button pauseButton = new Button();
    private final VBox page = new VBox(22);
    private TableView<AppUsage> overviewTable;
    private TableView<CategoryTotal> overviewCategoryTable;
    private TableView<SessionRecord> historyTable;
    private TableView<TrackedApp> unrecognizedAppsTable;
    private TimelineStrip timelineStrip;
    private HeatmapGrid heatmapGrid;
    private FlowPane timelineLegend;
    private Label timelineEmpty;
    private Label timelineCaption;
    private Label heatmapCaption;
    private Map<String, Integer> categoryColors = Map.of();
    private VBox recentList;
    private Label recentEmpty;
    private LocalDate displayedDate;
    private String displayedPeriod;
    private String selectedPage = "Overview";
    private String selectedPeriod = "Day";

    public MainView(
            UsageRepository repository,
            TrackingEngine engine,
            Path databasePath,
            Consumer<String> statusWriter
    ) {
        this.repository = repository;
        this.engine = engine;
        this.databasePath = databasePath;
        this.statusWriter = statusWriter;
        setTop(buildHeader());
        ScrollPane pageScroll = new ScrollPane(page);
        pageScroll.setFitToWidth(true);
        pageScroll.getStyleClass().add("page-scroll");
        setCenter(pageScroll);
        page.setPadding(new Insets(30, 32, 34, 32));
        page.setSpacing(18);
        page.getStyleClass().add("page-content");
        clockLabel.getStyleClass().add("clock-time");
        updateClock();
        clockTicker.setCycleCount(Timeline.INDEFINITE);
        sceneProperty().addListener((observable, oldScene, newScene) -> {
            clockTicker.stop();
            if (newScene != null) {
                updateClock();
                clockTicker.play();
            }
        });
        buildPage();
    }

    public Parent root() {
        return this;
    }

    private void updateClock() {
        LocalTime time = LocalTime.now();
        clockLabel.setText(time.format(CLOCK));
        clockLabel.setAccessibleText("Local time " + clockLabel.getText());
        clockFace.setTime(time, clockLabel.getText());
    }

    public void refresh() {
        if (selectedPage.equals("Overview")) {
            LocalDate selectedDate = datePicker.getValue() == null ? LocalDate.now() : datePicker.getValue();
            if (overviewTable != null
                    && selectedDate.equals(displayedDate)
                    && selectedPeriod.equals(displayedPeriod)) {
                updateOverviewData(selectedDate);
            } else {
                showOverview();
            }
        } else if (selectedPage.equals("History")) {
            if (historyTable == null) {
                showHistory();
            } else {
                updateHistoryData();
            }
        } else if (selectedPage.equals("Settings") && unrecognizedAppsTable != null) {
            updateUnrecognizedApps();
        }
    }

    public void setTrackingStatus(String status) {
        statusWriter.accept(status);
        statusLabel.setText(status);
        boolean paused = engine.isPaused();
        statusDot.getStyleClass().setAll("status-dot", paused ? "paused" : "tracking");
        pauseButton.setText(paused ? "Resume" : "Pause");
        pauseButton.setDisable(status.startsWith("Tracking stopped."));
    }

    public void installStylesheet(Scene scene, boolean dark) {
        String stylesheet = MainView.class.getResource(dark ? "/styles/dark.css" : "/styles/light.css")
                .toExternalForm();
        scene.getStylesheets().setAll(
                MainView.class.getResource("/styles/base.css").toExternalForm(), stylesheet);
    }

    private HBox buildHeader() {
        Label appName = new Label("UsageTime");
        appName.getStyleClass().add("brand-title");

        ToggleGroup navigationGroup = new ToggleGroup();
        HBox navigation = new HBox(4);
        navigation.setAlignment(Pos.CENTER_LEFT);
        navigation.getStyleClass().add("text-navigation");
        for (String pageName : List.of("Overview", "History", "Settings")) {
            ToggleButton button = new ToggleButton(pageName);
            button.getStyleClass().add("nav-item");
            button.setToggleGroup(navigationGroup);
            button.setSelected(pageName.equals("Overview"));
            button.setOnAction(event -> {
                if (!button.isSelected()) {
                    button.setSelected(true);
                }
                selectedPage = pageName;
                buildPage();
            });
            navigation.getChildren().add(button);
        }

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        pauseButton.setOnAction(event -> {
            engine.setPaused(!engine.isPaused());
            setTrackingStatus(engine.status());
        });
        pauseButton.setText(engine.isPaused() ? "Resume" : "Pause");
        pauseButton.getStyleClass().add("primary-button");
        statusDot.getStyleClass().setAll("status-dot", engine.isPaused() ? "paused" : "tracking");
        statusLabel.getStyleClass().add("status-text");
        statusLabel.setWrapText(true);
        statusLabel.setPrefWidth(180);
        statusLabel.setText(engine.status());
        HBox liveStatus = new HBox(7, statusDot, statusLabel);
        liveStatus.setAlignment(Pos.CENTER_LEFT);
        HBox header = new HBox(24, appName, navigation, spacer, liveStatus, pauseButton);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("app-header");
        return header;
    }

    private void buildPage() {
        page.getChildren().clear();
        switch (selectedPage) {
            case "History" -> showHistory();
            case "Settings" -> showSettings();
            default -> showOverview();
        }
    }

    private void showOverview() {
        LocalDate selectedDate = datePicker.getValue() == null ? LocalDate.now() : datePicker.getValue();
        displayedDate = selectedDate;
        displayedPeriod = selectedPeriod;

        Label title = new Label("Your time");
        title.getStyleClass().add("page-title");
        rangeLabel.setText(periodDescription(selectedDate));
        rangeLabel.getStyleClass().setAll("muted-copy");
        Label subtitle = new Label("A quieter view of your activity.");
        subtitle.getStyleClass().add("muted-copy");
        VBox heading = new VBox(4, title, subtitle);
        heading.getStyleClass().add("page-heading");

        HBox controls = new HBox(6);
        controls.setAlignment(Pos.CENTER_LEFT);
        controls.getStyleClass().add("report-controls");
        periodGroup.getToggles().clear();
        for (String period : List.of("Day", "Week", "Month")) {
            ToggleButton toggle = new ToggleButton(period);
            toggle.setToggleGroup(periodGroup);
            toggle.getStyleClass().add("period-option");
            toggle.setSelected(selectedPeriod.equals(period));
            toggle.setOnAction(event -> {
                if (!toggle.isSelected()) {
                    toggle.setSelected(true);
                }
                selectedPeriod = period;
                showOverview();
            });
            controls.getChildren().add(toggle);
        }
        datePicker.setOnAction(event -> showOverview());
        datePicker.getStyleClass().add("date-picker");
        Region controlSpacer = new Region();
        HBox.setHgrow(controlSpacer, Priority.ALWAYS);
        controls.getChildren().addAll(controlSpacer, datePicker);

        totalLabel.getStyleClass().setAll("total-time");
        rangeLabel.getStyleClass().setAll("muted-copy");
        Label summaryTitle = new Label("Tracked time");
        summaryTitle.getStyleClass().add("eyebrow");
        Label summaryCaption = new Label("Foreground applications only");
        summaryCaption.getStyleClass().add("muted-copy");
        VBox summary = new VBox(5, summaryTitle, totalLabel, rangeLabel, summaryCaption);
        summary.getStyleClass().add("summary");
        Label clockTitle = new Label("Local time");
        clockTitle.getStyleClass().add("eyebrow");
        VBox clock = new VBox(9, clockTitle, clockFace, clockLabel);
        clock.setAlignment(Pos.CENTER);
        clock.getStyleClass().add("clock-panel");
        clock.setMinWidth(218);
        clock.setPrefWidth(218);

        goalNotice.getStyleClass().setAll("goal-notice");

        Label timelineTitle = new Label("Time blocks");
        timelineTitle.getStyleClass().add("section-title");
        timelineCaption = new Label();
        timelineCaption.getStyleClass().add("muted-copy");
        timelineStrip = new TimelineStrip();
        HBox ticks = new HBox();
        ticks.getStyleClass().add("timeline-ticks");
        for (String tick : List.of("12 AM", "6 AM", "12 PM", "6 PM", "12 AM")) {
            if (!ticks.getChildren().isEmpty()) {
                Region tickSpacer = new Region();
                HBox.setHgrow(tickSpacer, Priority.ALWAYS);
                ticks.getChildren().add(tickSpacer);
            }
            Label tickLabel = new Label(tick);
            tickLabel.getStyleClass().add("timeline-tick");
            ticks.getChildren().add(tickLabel);
        }
        timelineLegend = new FlowPane(14, 7);
        timelineLegend.setAlignment(Pos.CENTER_LEFT);
        timelineLegend.getStyleClass().add("timeline-legend");
        timelineEmpty = new Label("No foreground time recorded for this day.");
        timelineEmpty.getStyleClass().add("muted-copy");
        VBox timelineSection = new VBox(8,
                new VBox(4, timelineTitle, timelineCaption),
                timelineStrip, ticks, timelineEmpty, timelineLegend);
        timelineSection.getStyleClass().add("timeline-section");
        VBox activity = new VBox(18, summary, timelineSection);
        activity.getStyleClass().add("activity-panel");
        activity.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(activity, Priority.ALWAYS);
        HBox feature = new HBox(0, clock, activity);
        feature.getStyleClass().add("feature-panel");

        Label heatmapTitle = new Label("Week rhythm");
        heatmapTitle.getStyleClass().add("section-title");
        heatmapCaption = new Label();
        heatmapCaption.getStyleClass().add("muted-copy");
        heatmapGrid = new HeatmapGrid();
        HBox heatmapLegend = new HBox(12);
        heatmapLegend.setAlignment(Pos.CENTER_LEFT);
        heatmapLegend.getStyleClass().add("heatmap-legend");
        String[] bucketLabels = {"0", "<15", "15-29", "30-44", "45+"};
        for (int bucket = 0; bucket < bucketLabels.length; bucket++) {
            Region swatch = new Region();
            swatch.getStyleClass().addAll("legend-swatch", "heat-" + bucket);
            Label text = new Label(bucketLabels[bucket]);
            text.getStyleClass().add("legend-label");
            HBox entry = new HBox(5, swatch, text);
            entry.setAlignment(Pos.CENTER_LEFT);
            heatmapLegend.getChildren().add(entry);
        }
        Label legendHint = new Label("minutes in the hour");
        legendHint.getStyleClass().add("legend-hint");
        heatmapLegend.getChildren().add(legendHint);
        VBox heatmapSection = new VBox(8,
                new VBox(4, heatmapTitle, heatmapCaption),
                heatmapGrid, heatmapLegend);
        heatmapSection.getStyleClass().add("journal-section");

        Label tableTitle = new Label("Applications");
        tableTitle.getStyleClass().add("section-title");
        Label tableHint = new Label("Time is credited only while an app is in the foreground. "
                + "Recognized apps are categorized locally.");
        tableHint.getStyleClass().add("muted-copy");
        tableHint.setWrapText(true);
        overviewTable = appTable(List.of());
        VBox applicationSection = new VBox(8, new VBox(4, tableTitle, tableHint), overviewTable);
        applicationSection.getStyleClass().add("journal-section");

        Label categoryTitle = new Label("Categories");
        categoryTitle.getStyleClass().add("section-title");
        Label categoryHint = new Label("Recognized locally; unknown apps remain uncategorized.");
        categoryHint.getStyleClass().add("muted-copy");
        categoryHint.setWrapText(true);
        overviewCategoryTable = categoryTable(List.of());
        VBox categorySection = new VBox(8, new VBox(4, categoryTitle, categoryHint), overviewCategoryTable);
        categorySection.getStyleClass().add("journal-section");
        HBox.setHgrow(applicationSection, Priority.ALWAYS);
        HBox.setHgrow(categorySection, Priority.ALWAYS);
        applicationSection.setMaxWidth(Double.MAX_VALUE);
        categorySection.setMaxWidth(Double.MAX_VALUE);
        applicationSection.setPrefWidth(560);
        categorySection.setPrefWidth(300);
        HBox breakdowns = new HBox(32, applicationSection, categorySection);

        Label recentTitle = new Label("Recent sessions");
        recentTitle.getStyleClass().add("section-title");
        recentList = new VBox();
        recentEmpty = new Label("Sessions will appear here as you use your computer.");
        recentEmpty.getStyleClass().add("muted-copy");
        VBox recentSection = new VBox(8, recentTitle, recentList, recentEmpty);
        recentSection.getStyleClass().add("journal-section");

        page.getChildren().setAll(heading, controls, goalNotice, feature,
                breakdowns, heatmapSection, recentSection);
        updateOverviewData(selectedDate);
    }
    private void updateOverviewData(LocalDate selectedDate) {
        if (overviewTable.getEditingCell() != null) {
            return;
        }
        ZoneId zone = ZoneId.systemDefault();
        ReportRange range = ReportRange.throughDate(selectedPeriod, selectedDate, zone);
        List<AppUsage> usage = repository.usageBetween(range.startInclusive(), range.endExclusive());
        overviewTable.getItems().setAll(usage);
        List<CategoryTotal> categories = UsageReports.byCategory(usage);
        overviewCategoryTable.getItems().setAll(categories);
        overviewTable.setPrefHeight(tableHeight(usage.size()));
        overviewCategoryTable.setPrefHeight(tableHeight(overviewCategoryTable.getItems().size()));
        long seconds = usage.stream().mapToLong(AppUsage::seconds).sum();
        totalLabel.setText(formatDuration(seconds));

        ReportRange dayRange = ReportRange.throughDate("Day", selectedDate, zone);
        List<AppUsage> dailyUsage = repository.usageBetween(dayRange.startInclusive(), dayRange.endExclusive());
        goalNotice.setText(goalMessage(dailyUsage));
        goalNotice.setVisible(!goalNotice.getText().isBlank());
        goalNotice.setManaged(goalNotice.isVisible());

        List<SessionRecord> daySessions =
                repository.sessionsBetween(dayRange.startInclusive(), dayRange.endExclusive());
        List<DayTimeline.Block> blocks = DayTimeline.blocksForDay(daySessions, selectedDate, zone);
        Map<String, Integer> colors = blockColors(blocks, categories);
        categoryColors = colors;
        overviewCategoryTable.refresh();
        timelineStrip.setBlocks(blocks, colors);
        timelineCaption.setText(selectedDate.format(DATE)
                + " - one block per foreground session, local time");
        timelineEmpty.setVisible(blocks.isEmpty());
        timelineEmpty.setManaged(blocks.isEmpty());
        timelineLegend.getChildren().clear();
        for (Map.Entry<String, Integer> entry : colors.entrySet()) {
            Region swatch = new Region();
            swatch.getStyleClass().addAll("legend-swatch", "block-color-" + entry.getValue());
            Label name = new Label(entry.getKey());
            name.getStyleClass().add("legend-label");
            HBox entryRow = new HBox(5, swatch, name);
            entryRow.setAlignment(Pos.CENTER_LEFT);
            timelineLegend.getChildren().add(entryRow);
        }

        LocalDate weekStart = selectedDate.minusDays(selectedDate.getDayOfWeek().getValue() - 1L);
        Instant weekFrom = weekStart.atStartOfDay(zone).toInstant();
        Instant weekTo = weekStart.plusDays(WeekHeatmap.DAYS).atStartOfDay(zone).toInstant();
        long[][] grid = WeekHeatmap.secondsPerHour(
                repository.sessionsBetween(weekFrom, weekTo), weekStart, zone);
        heatmapGrid.setWeek(weekStart, grid);
        heatmapCaption.setText("Minutes in the foreground by hour - week of " + weekStart.format(DATE)
                + ". Row ends show each day's total.");

        List<SessionRecord> recent = repository.sessionsBetween(range.startInclusive(), range.endExclusive())
                .stream().limit(RECENT_LIMIT).toList();
        recentList.getChildren().clear();
        for (SessionRecord session : recent) {
            Label name = new Label(session.name());
            name.getStyleClass().add("recent-app");
            Label period = new Label(recentLine(session, zone));
            period.getStyleClass().add("muted-copy");
            VBox details = new VBox(3, name, period);
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            Label duration = new Label(formatDuration(session.seconds()));
            duration.getStyleClass().add("recent-duration");
            HBox row = new HBox(12, details, spacer, duration);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("recent-row");
            recentList.getChildren().add(row);
        }
        recentEmpty.setVisible(recent.isEmpty());
        recentEmpty.setManaged(recent.isEmpty());
    }

    private String recentLine(SessionRecord session, ZoneId zone) {
        String start = SESSION_DAY.format(session.startedAt().atZone(zone));
        String end = session.inProgress()
                ? "in progress"
                : SESSION_DAY.format(session.endedAt().atZone(zone));
        return start + " - " + end;
    }

    private static Map<String, Integer> blockColors(List<DayTimeline.Block> blocks,
                                                     List<CategoryTotal> totals) {
        List<String> categories = java.util.stream.Stream.concat(blocks.stream()
                .map(block -> block.category() == null || block.category().isBlank()
                        ? "Uncategorized" : block.category()),
                totals.stream().map(CategoryTotal::name))
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        Map<String, Integer> colors = new LinkedHashMap<>();
        for (int i = 0; i < categories.size(); i++) {
            colors.put(categories.get(i), i % BLOCK_COLORS);
        }
        return colors;
    }

    private TableView<CategoryTotal> categoryTable(List<AppUsage> usage) {
        TableView<CategoryTotal> table = new TableView<>(
                FXCollections.observableArrayList(UsageReports.byCategory(usage)));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        Label placeholder = new Label("Categories appear as applications are tracked.");
        placeholder.getStyleClass().add("table-placeholder");
        table.setPlaceholder(placeholder);
        table.setPrefHeight(tableHeight(table.getItems().size()));
        TableColumn<CategoryTotal, String> category = column("Category", CategoryTotal::name);
        category.setCellFactory(ignored -> new TableCell<>() {
            private final Region swatch = new Region();
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                swatch.getStyleClass().setAll("legend-swatch",
                        "block-color-" + categoryColors.getOrDefault(value, 0));
                setText(value);
                setGraphic(swatch);
            }
        });
        table.getColumns().setAll(List.of(category, column("Time", item -> formatDuration(item.seconds()))));
        return table;
    }

    private TableView<AppUsage> appTable(List<AppUsage> usage) {
        TableView<AppUsage> table = new TableView<>(FXCollections.observableArrayList(usage));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        Label placeholder = new Label("No foreground time recorded yet. Keep using an app to start a session.");
        placeholder.getStyleClass().add("table-placeholder");
        table.setPlaceholder(placeholder);
        table.setPrefHeight(tableHeight(usage.size()));

        TableColumn<AppUsage, String> app = column("Application", AppUsage::name);
        app.setPrefWidth(250);

        TableColumn<AppUsage, String> category = column("Category", item ->
                item.category() == null || item.category().isBlank() ? "Uncategorized" : item.category());
        category.setPrefWidth(190);

        TableColumn<AppUsage, String> time = column("Time", item -> formatDuration(item.seconds()));
        time.setStyle("-fx-alignment: CENTER-RIGHT;");
        time.setPrefWidth(125);

        TableColumn<AppUsage, String> dailyGoalColumn = column("Daily goal (optional)", item ->
                item.goalMinutes() == 0 ? "" : Integer.toString(item.goalMinutes()));
        dailyGoalColumn.setCellFactory(ignored -> goalCell());
        dailyGoalColumn.setPrefWidth(180);
        dailyGoalColumn.setVisible(Boolean.parseBoolean(repository.setting("show_goals", "false")));

        table.getColumns().setAll(List.of(app, category, time, dailyGoalColumn));
        return table;
    }

    private TableCell<AppUsage, String> goalCell() {
        return new TableCell<>() {
            private final Button button = new Button();
            {
                getStyleClass().add("setting-cell");
                button.getStyleClass().addAll("secondary-button", "table-action");
                button.setOnAction(event -> {
                    AppUsage app = getTableRow().getItem();
                    if (app != null) {
                        editAppSetting(app.appId(), app.name(),
                                app.goalMinutes() == 0 ? "" : Integer.toString(app.goalMinutes()),
                                false, MainView.this::refresh);
                    }
                });
            }

            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || getTableRow().getItem() == null) {
                    setGraphic(null);
                    return;
                }
                AppUsage app = getTableRow().getItem();
                button.setText(value == null || value.isBlank() ? "Set goal" : value + " min");
                button.setAccessibleText("Edit optional daily goal for " + app.name());
                setGraphic(button);
            }
        };
    }

    private void editAppSetting(String appId, String appName, String initialValue,
                                boolean category, Runnable afterSave) {
        TextInputDialog dialog = new TextInputDialog(initialValue == null ? "" : initialValue);
        dialog.setTitle(category ? "App category" : "Optional daily goal");
        dialog.setHeaderText(appName);
        dialog.setContentText(category
                ? "Category name (leave blank to remove):"
                : "Minutes per day (leave blank for no goal):");
        if (!category) {
            Button save = (Button) dialog.getDialogPane().lookupButton(
                    dialog.getDialogPane().getButtonTypes().stream()
                            .filter(type -> type.getButtonData() == ButtonBar.ButtonData.OK_DONE)
                            .findFirst().orElseThrow());
            save.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
                try {
                    parseGoalMinutes(dialog.getEditor().getText());
                } catch (IllegalArgumentException error) {
                    event.consume();
                    Alert alert = new Alert(Alert.AlertType.ERROR, error.getMessage(), ButtonType.OK);
                    alert.setTitle("Invalid daily goal");
                    alert.setHeaderText("Invalid daily goal");
                    styleDialog(alert, dialog.getDialogPane().getScene().getWindow(), null);
                    alert.showAndWait();
                }
            });
        }
        styleDialog(dialog, getScene().getWindow(), null);
        dialog.showAndWait().ifPresent(value -> {
            try {
                if (category) {
                    repository.updateCategory(appId, value.trim());
                } else {
                    repository.updateGoal(appId, parseGoalMinutes(value));
                }
                afterSave.run();
            } catch (RuntimeException error) {
                showError("Could not save " + (category ? "category" : "daily goal"), error.getMessage());
            }
        });
    }

    static int parseGoalMinutes(String value) {
        try {
            int minutes = value.isBlank() ? 0 : Integer.parseInt(value.trim());
            if (minutes >= 0 && minutes <= 1440) {
                return minutes;
            }
        } catch (NumberFormatException ignored) {
            // Report the same validation message for non-numeric and out-of-range values.
        }
        throw new IllegalArgumentException("Enter a whole number from 0 to 1,440 minutes, or leave it blank.");
    }

    private void showHistory() {
        Label title = new Label("Recent activity");
        title.getStyleClass().add("page-title");
        Label hint = new Label("The last 30 days. Each row is one continuous foreground session; times are local.");
        hint.getStyleClass().add("muted-copy");
        VBox heading = new VBox(4, title, hint);
        heading.getStyleClass().add("page-heading");

        historyTable = new TableView<>(FXCollections.observableArrayList(lastMonthSessions()));
        historyTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        historyTable.setPlaceholder(new Label("Sessions will appear here as you use your computer."));
        TableColumn<SessionRecord, String> app = column("Application", SessionRecord::name);
        TableColumn<SessionRecord, String> category = column("Category",
                item -> item.category() == null ? "" : item.category());
        TableColumn<SessionRecord, String> started = column("Started", item -> localTime(item.startedAt()));
        TableColumn<SessionRecord, String> ended = column("Ended", item ->
                item.inProgress() ? "In progress" : localTime(item.endedAt()));
        TableColumn<SessionRecord, String> duration = column("Duration", item -> formatDuration(item.seconds()));
        duration.setStyle("-fx-alignment: CENTER-RIGHT;");
        historyTable.getColumns().setAll(List.of(app, category, started, ended, duration));
        VBox.setVgrow(historyTable, Priority.ALWAYS);
        page.getChildren().setAll(heading, historyTable);
    }

    private void updateHistoryData() {
        historyTable.getItems().setAll(lastMonthSessions());
    }

    private List<SessionRecord> lastMonthSessions() {
        LocalDate today = LocalDate.now();
        Instant from = today.minusDays(29).atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant to = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
        return repository.sessionsBetween(from, to);
    }

    private void showSettings() {
        Label title = new Label("Settings");
        title.getStyleClass().add("page-title");
        VBox heading = new VBox(4, title);
        heading.getStyleClass().add("page-heading");

        boolean dark = repository.setting("theme", "light").equals("dark");
        Label appearanceState = new Label(dark ? "Dark palette is active." : "Light palette is active.");
        appearanceState.getStyleClass().add("body-copy");
        appearanceState.setWrapText(true);
        Button appearanceButton = new Button(dark ? "Switch to light appearance" : "Switch to dark appearance");
        appearanceButton.getStyleClass().add("secondary-button");
        appearanceButton.setOnAction(event -> {
            boolean nextDark = !repository.setting("theme", "light").equals("dark");
            repository.saveSetting("theme", nextDark ? "dark" : "light");
            installStylesheet(getScene(), nextDark);
            appearanceButton.setText(nextDark ? "Switch to light appearance" : "Switch to dark appearance");
            appearanceState.setText(nextDark ? "Dark palette is active." : "Light palette is active.");
        });

        Label goalControlsState = new Label();
        goalControlsState.getStyleClass().add("body-copy");
        goalControlsState.setWrapText(true);
        Button goalControlsButton = new Button();
        goalControlsButton.getStyleClass().add("secondary-button");
        Runnable updateGoalControls = () -> {
            boolean show = Boolean.parseBoolean(repository.setting("show_goals", "false"));
            goalControlsState.setText(show
                    ? "Goal controls are shown in Overview. Existing reminders remain active."
                    : "Goal controls are hidden in Overview. Existing reminders remain active.");
            goalControlsButton.setText(show ? "Hide goal controls" : "Show goal controls");
        };
        updateGoalControls.run();
        goalControlsButton.setOnAction(event -> {
            try {
                boolean show = Boolean.parseBoolean(repository.setting("show_goals", "false"));
                repository.saveSetting("show_goals", Boolean.toString(!show));
                updateGoalControls.run();
            } catch (RuntimeException error) {
                showError("Could not save goal display preference", error.getMessage());
            }
        });

        Label tracking = new Label("Idle detection");
        tracking.getStyleClass().add("section-title");
        Label idleExplanation = new Label("Tracking pauses after 5 minutes without keyboard or mouse input.");
        idleExplanation.getStyleClass().add("body-copy");
        idleExplanation.setWrapText(true);

        Label privacy = new Label("What is stored");
        privacy.getStyleClass().add("section-title");
        Label privacyExplanation = new Label(
                "Application name, category, session start and end times, and your optional goals. " +
                        "No window titles, websites, keystrokes, screenshots, or cloud account.");
        privacyExplanation.getStyleClass().add("body-copy");
        privacyExplanation.setWrapText(true);

        Label data = new Label("Database location");
        data.getStyleClass().add("section-title");
        Label path = new Label(databasePath.toAbsolutePath().toString());
        path.getStyleClass().add("path-copy");
        path.setWrapText(true);

        Button csv = new Button("Export CSV");
        csv.getStyleClass().add("secondary-button");
        csv.setOnAction(event -> export(false));
        Button json = new Button("Export JSON");
        json.getStyleClass().add("secondary-button");
        json.setOnAction(event -> export(true));
        HBox exports = new HBox(10, csv, json);

        Label exportDescription = new Label("Exports include session times and app names. Files are created only when you choose a destination.");
        exportDescription.getStyleClass().add("muted-copy");
        exportDescription.setWrapText(true);

        Label appearance = new Label("Appearance");
        appearance.getStyleClass().add("section-title");
        VBox section = new VBox(10, appearance, appearanceState, appearanceButton);
        section.getStyleClass().add("settings-section");
        Label goalControlsTitle = new Label("Daily goals");
        goalControlsTitle.getStyleClass().add("section-title");
        VBox goalsSection = new VBox(10, goalControlsTitle, goalControlsState, goalControlsButton);
        goalsSection.getStyleClass().add("settings-section");
        Label categoriesTitle = new Label("Unrecognized applications");
        categoriesTitle.getStyleClass().add("section-title");
        Label categoriesHint = new Label(
                "Known apps are categorized automatically. Add a category only when an app is not recognized.");
        categoriesHint.getStyleClass().add("body-copy");
        categoriesHint.setWrapText(true);
        unrecognizedAppsTable = unrecognizedAppsTable();
        updateUnrecognizedApps();
        VBox categoriesSection = new VBox(10, categoriesTitle, categoriesHint, unrecognizedAppsTable);
        categoriesSection.getStyleClass().add("settings-section");
        Label yourData = new Label("Your data");
        yourData.getStyleClass().add("section-title");
        Button deleteData = new Button("Delete tracked history");
        deleteData.getStyleClass().add("destructive-button");
        deleteData.setOnAction(event -> deleteTrackedData());
        VBox idleSection = new VBox(8, tracking, idleExplanation);
        idleSection.getStyleClass().add("settings-section");
        VBox privacySection = new VBox(8, privacy, privacyExplanation);
        privacySection.getStyleClass().add("settings-section");
        VBox locationSection = new VBox(8, data, path);
        locationSection.getStyleClass().add("settings-section");
        VBox dataSection = new VBox(8, yourData, exports, exportDescription, deleteData);
        dataSection.getStyleClass().add("settings-section");
        page.getChildren().setAll(heading, section, goalsSection, categoriesSection, idleSection,
                privacySection, locationSection, dataSection);
    }

    private TableView<TrackedApp> unrecognizedAppsTable() {
        TableView<TrackedApp> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("No unrecognized applications."));
        TableColumn<TrackedApp, String> app = column("Application", TrackedApp::name);
        TableColumn<TrackedApp, String> category = column("Category", item ->
                item.category().isBlank() ? "Uncategorized" : item.category());
        TableColumn<TrackedApp, String> action = column("", TrackedApp::category);
        action.setCellFactory(ignored -> new TableCell<>() {
            private final Button button = new Button();
            {
                getStyleClass().add("setting-cell");
                button.getStyleClass().addAll("secondary-button", "table-action");
                button.setOnAction(event -> {
                    TrackedApp selected = getTableRow().getItem();
                    if (selected != null) {
                        editAppSetting(selected.appId(), selected.name(), selected.category(),
                                true, MainView.this::updateUnrecognizedApps);
                    }
                });
            }

            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                TrackedApp app = getTableRow().getItem();
                if (empty || app == null) {
                    setGraphic(null);
                    return;
                }
                button.setText(value == null || value.isBlank() ? "Add category" : "Change category");
                button.setAccessibleText("Edit category for " + app.name());
                setGraphic(button);
            }
        });
        table.getColumns().setAll(List.of(app, category, action));
        return table;
    }

    private void updateUnrecognizedApps() {
        List<TrackedApp> apps = repository.applications().stream()
                .filter(app -> AppCategories.detect(app.name()).isBlank())
                .toList();
        if (!unrecognizedAppsTable.getItems().equals(apps)) {
            unrecognizedAppsTable.getItems().setAll(apps);
        }
        unrecognizedAppsTable.setPrefHeight(tableHeight(apps.size()));
    }

    private void export(boolean json) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(json ? "Export usage as JSON" : "Export usage as CSV");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                json ? "JSON files" : "CSV files", json ? "*.json" : "*.csv"));
        chooser.setInitialFileName("usage-export." + (json ? "json" : "csv"));
        java.io.File file = chooser.showSaveDialog(getScene().getWindow());
        if (file == null) {
            return;
        }

        try {
            if (json) {
                ExportService.json(repository, file.toPath());
            } else {
                ExportService.csv(repository, file.toPath());
            }
            showInformation("Export complete", "Your export has been saved.");
        } catch (IOException error) {
            showError("Export failed", error.getMessage());
        }
    }
    private void deleteTrackedData() {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Delete tracked history");
        confirmation.setHeaderText("Delete all tracked usage and app settings?");
        confirmation.setContentText(
                "This removes all session history, categories, and daily goals from this device. " +
                        "Your appearance preference is kept. Tracking pauses and stays paused afterward. " +
                        "This cannot be undone.");
        confirmation.getButtonTypes().setAll(ButtonType.CANCEL, ButtonType.OK);
        styleDialog(confirmation, getScene().getWindow(), ButtonType.OK);
        confirmation.showAndWait().ifPresent(result -> {
            if (result != ButtonType.OK) {
                return;
            }
            try {
                engine.setPaused(true);
                repository.clearUsageData();
                setTrackingStatus(engine.status());
                showInformation("History deleted", "Tracked history was deleted.");
            } catch (RuntimeException error) {
                showError("Could not delete tracked history", error.getMessage());
            }
        });
    }

    private String periodDescription(LocalDate date) {
        LocalDate first = LocalDate.ofInstant(
                ReportRange.throughDate(selectedPeriod, date, ZoneId.systemDefault()).startInclusive(),
                ZoneId.systemDefault());
        return selectedPeriod.equals("Day")
                ? date.format(DATE) + " - local time"
                : first.format(DATE) + " to " + date.format(DATE) + " - local time";
    }

    private String goalMessage(List<AppUsage> usage) {
        return usage.stream()
                .filter(item -> item.goalMinutes() > 0 && item.seconds() >= item.goalMinutes() * 60L)
                .findFirst()
                .map(item -> "Daily goal reached for " + item.name() + ". Your day is still yours to shape.")
                .orElse("");
    }

    private static <T> TableColumn<T, String> column(String title, java.util.function.Function<T, String> getter) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(getter.apply(cell.getValue())));
        return column;
    }

    static String formatDuration(long seconds) {
        if (seconds < 60) {
            return seconds + "s";
        }
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long remainingSeconds = seconds % 60;
        if (hours > 0) {
            return String.format("%dh %02dm %02ds", hours, minutes, remainingSeconds);
        }
        return String.format("%dm %02ds", minutes, remainingSeconds);
    }

    private static double tableHeight(int itemCount) {
        return Math.min(240, 56 + Math.max(1, itemCount) * 34);
    }

    private static String localTime(Instant instant) {
        return TIME.format(instant.atZone(ZoneId.systemDefault()));
    }

    private void showInformation(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(title);
        styleDialog(alert, getScene().getWindow(), null);
        alert.show();
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message == null ? "An unexpected error occurred." : message);
        styleDialog(alert, getScene().getWindow(), null);
        alert.show();
    }

    private void styleDialog(Dialog<?> dialog, Window owner, ButtonType destructive) {
        dialog.initOwner(owner);
        dialog.setGraphic(null);
        dialog.getDialogPane().getStylesheets().setAll(getScene().getStylesheets());
        for (ButtonType type : dialog.getDialogPane().getButtonTypes()) {
            Button button = (Button) dialog.getDialogPane().lookupButton(type);
            String style = type == destructive ? "destructive-button"
                    : type.getButtonData() == ButtonBar.ButtonData.OK_DONE ? "primary-button"
                    : "secondary-button";
            button.getStyleClass().add(style);
        }
    }
}