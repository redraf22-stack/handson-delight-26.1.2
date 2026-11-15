package com.pockyl.handson_delight.client;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import com.pockyl.handson_delight.HandsOnDelight;

/**
 * For looking at the client while porting ({@code -Dhandson.diag}): lines that must not flood the log are written at most once a
 * second. A mixin's own static fields are not initialised (the mixin class is never loaded on its own), so state kept for a
 * mixin lives here.
 */
public final class SceneDebug {
    private static final boolean ON = Boolean.getBoolean("handson.diag");
    private static final Map<String, Long> LAST = new HashMap<>();

    private SceneDebug() {
    }

    /** Writes {@code message} under {@code key}, at most once a second, when the diagnostics are on. */
    public static void once(String key, Supplier<String> message) {
        if (!ON) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = LAST.get(key);
        if (last != null && now - last < 1000L) {
            return;
        }
        LAST.put(key, now);
        HandsOnDelight.LOGGER.warn("DIAG {}", message.get());
    }
}
