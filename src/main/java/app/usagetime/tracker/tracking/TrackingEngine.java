package app.usagetime.tracker.tracking;

import app.usagetime.tracker.model.DetectedApp;
import app.usagetime.tracker.model.ForegroundSample;
import app.usagetime.tracker.platform.ForegroundProvider;
import app.usagetime.tracker.storage.UsageRepository;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class TrackingEngine implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(TrackingEngine.class.getName());
    private static final Duration IDLE_THRESHOLD = Duration.ofMinutes(5);
    private static final Duration MAX_SAMPLE_GAP = Duration.ofSeconds(30);
    private static final long POLL_SECONDS = 3;
    private static final long CHECKPOINT_MILLIS = 10_000;

    private final ForegroundProvider provider;
    private final UsageRepository repository;
    private final Clock clock;
    private final Consumer<String> statusListener;
    private final Runnable updateListener;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "usage-tracker");
        thread.setDaemon(true);
        return thread;
    });

    private ActiveSession active;
    private boolean paused;
    private boolean started;
    private boolean failed;
    private String status;
    private Instant lastSampleAt;
    private String lastSampleError;

    public TrackingEngine(
            ForegroundProvider provider,
            UsageRepository repository,
            Clock clock,
            Consumer<String> statusListener,
            Runnable updateListener
    ) {
        this.provider = Objects.requireNonNull(provider);
        this.repository = Objects.requireNonNull(repository);
        this.clock = Objects.requireNonNull(clock);
        this.statusListener = Objects.requireNonNull(statusListener);
        this.updateListener = Objects.requireNonNull(updateListener);
        status = provider.supportDescription();
    }

    public synchronized void start() {
        if (started) {
            return;
        }
        repository.recoverOpenSessions();
        started = true;
        publish(status);
        executor.scheduleWithFixedDelay(this::poll, 0, POLL_SECONDS, TimeUnit.SECONDS);
    }

    public synchronized void setPaused(boolean value) {
        if (paused == value) {
            return;
        }
        if (failed && !value) {
            throw new IllegalStateException("Tracking stopped after an error. Restart the application to resume.");
        }
        paused = value;
        if (paused) {
            endActive(clock.instant());
            publish("Tracking is paused. Your existing data remains on this device.");
        } else {
            publish(provider.supportDescription());
            executor.execute(this::poll);
        }
        updateListener.run();
    }

    public synchronized boolean isPaused() {
        return paused;
    }

    public synchronized String status() {
        return status;
    }

    synchronized void process(ForegroundSample sample, Instant now) {
        if (paused) {
            return;
        }

        if (lastSampleAt != null
                && Duration.between(lastSampleAt, now).compareTo(MAX_SAMPLE_GAP) > 0
                && active != null) {
            endActive(active.lastObservedAt);
        }
        lastSampleAt = now;

        DetectedApp observed = sample.app();
        boolean idle = sample.idleMillis() >= IDLE_THRESHOLD.toMillis();
        if (idle) {
            Instant idleStart = now.minusMillis(sample.idleMillis() - IDLE_THRESHOLD.toMillis());
            boolean changed = active != null;
            if (active != null) {
                endActive(max(active.startedAt, idleStart));
            }
            publish("Paused after 5 minutes without input.");
            if (changed) {
                updateListener.run();
            }
            return;
        }

        if (observed == null) {
            boolean changed = active != null;
            if (active != null) {
                endActive(now);
            }
            publish("Waiting for an active application.");
            if (changed) {
                updateListener.run();
            }
            return;
        }

        if (active == null || !active.app.id().equals(observed.id())) {
            if (active != null) {
                endActive(now);
            }
            long sessionId = repository.startSession(observed.id(), observed.name(), now);
            active = new ActiveSession(sessionId, observed, now);
            publish("Tracking " + observed.name());
            updateListener.run();
        } else {
            active.lastObservedAt = now;
            if (Duration.between(active.lastCheckpoint, now).toMillis() >= CHECKPOINT_MILLIS) {
                repository.checkpointSession(active.id, now);
                active.lastCheckpoint = now;
                updateListener.run();
            }
            publish("Tracking " + observed.name());
        }
    }

    synchronized void poll() {
        if (paused || failed) {
            return;
        }
        ForegroundSample sample;
        try {
            sample = provider.sample();
        } catch (IOException | RuntimeException error) {
            handleSamplingFailure(error);
            return;
        }

        lastSampleError = null;
        try {
            process(sample, clock.instant());
        } catch (RuntimeException error) {
            failed = true;
            active = null;
            executor.shutdown();
            LOGGER.log(Level.SEVERE, "Usage tracking stopped after an internal error", error);
            publish("Tracking stopped. Restart the app; the last saved checkpoint is preserved.");
            updateListener.run();
        }
    }

    private void handleSamplingFailure(Exception error) {
        String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        boolean hadActiveSession = active != null;
        if (!message.equals(lastSampleError)) {
            LOGGER.log(Level.WARNING, "Foreground usage sampling failed: " + message);
            lastSampleError = message;
        }
        try {
            if (active != null) {
                endActive(active.lastObservedAt);
            }
            publish("Tracking unavailable: " + message);
            if (hadActiveSession) {
                updateListener.run();
            }
        } catch (RuntimeException storageError) {
            failed = true;
            active = null;
            executor.shutdown();
            storageError.addSuppressed(error);
            LOGGER.log(Level.SEVERE, "Could not safely stop a usage session after a sampling error", storageError);
            publish("Tracking stopped. Restart the app; the last saved checkpoint is preserved.");
            updateListener.run();
        }
    }

    @Override
    public synchronized void close() {
        executor.shutdownNow();
        if (active != null && !failed) {
            endActive(clock.instant());
        }
        started = false;
    }

    private void endActive(Instant at) {
        if (active == null) {
            return;
        }
        Instant end = max(active.startedAt, at);
        repository.finishSession(active.id, end);
        active = null;
    }

    private void publish(String message) {
        if (message.equals(status)) {
            return;
        }
        status = message;
        statusListener.accept(message);
    }

    private static Instant max(Instant first, Instant second) {
        return first.isAfter(second) ? first : second;
    }

    private static final class ActiveSession {
        private final long id;
        private final DetectedApp app;
        private final Instant startedAt;
        private Instant lastCheckpoint;
        private Instant lastObservedAt;

        private ActiveSession(long id, DetectedApp app, Instant startedAt) {
            this.id = id;
            this.app = app;
            this.startedAt = startedAt;
            this.lastCheckpoint = startedAt;
            this.lastObservedAt = startedAt;
        }
    }
}
