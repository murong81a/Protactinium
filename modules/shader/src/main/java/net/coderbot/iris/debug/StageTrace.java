package net.coderbot.iris.debug;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Counts how often each rendering stage runs, dumped once a second.
 *
 * <p>Exists because a stage that never runs and a stage that runs with wrong data look identical
 * from a screenshot, and the two have entirely different fixes. Enabled with
 * {@code -Dactinium.stageTrace=true}; off by default so the counters cost one boolean test.
 */
public final class StageTrace {

    private static final Logger LOGGER = LogManager.getLogger("ProtactiniumShader");

    /**
     * On for now: this diagnostics session cannot ask the player to edit JVM arguments, and a stage
     * trace that is off is indistinguishable from a stage that never runs. Turned off once the
     * terrain defect is located.
     */
    private static final boolean ENABLED = true;

    private static final Map<String, AtomicLong> COUNTS = new ConcurrentHashMap<String, AtomicLong>();

    private static volatile long lastDump = System.nanoTime();

    private StageTrace() {
    }

    /** Records one execution of the named stage. */
    public static void count(String stage) {
        if (!ENABLED) {
            return;
        }
        COUNTS.computeIfAbsent(stage, ignored -> new AtomicLong()).incrementAndGet();
        tick();
    }

    /** Emits one line per second; called from count, so the busiest stage drives the clock. */
    private static void tick() {
        final long now = System.nanoTime();
        if (now - lastDump < 1000000000L) {
            return;
        }
        lastDump = now;
        final StringBuilder line = new StringBuilder("stage trace:");
        for (Map.Entry<String, AtomicLong> entry : COUNTS.entrySet()) {
            final long value = entry.getValue().getAndSet(0L);
            if (value != 0L) {
                line.append(' ').append(entry.getKey()).append('=').append(value);
            }
        }
        LOGGER.info(line.toString());
    }
}
