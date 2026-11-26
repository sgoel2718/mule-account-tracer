package com.muletracer.trace;

/**
 * Limits for a trace.
 *
 * @param maxHops       stop following money after this many layers (banks typically trace 5-8)
 * @param windowSeconds only follow debits within this long after the first fraudulent credit
 */
public record TraceConfig(int maxHops, long windowSeconds) {

    public TraceConfig {
        if (maxHops < 1) {
            throw new IllegalArgumentException("maxHops must be >= 1");
        }
        if (windowSeconds <= 0) {
            throw new IllegalArgumentException("windowSeconds must be > 0");
        }
    }

    public static TraceConfig defaults() {
        return new TraceConfig(8, 72 * 3600L);
    }
}
