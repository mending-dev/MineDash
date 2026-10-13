package dev.mending.minedash.core.metrics;

import java.util.List;

public final class MetricsResponse {

    /** Sampling interval in milliseconds. */
    public final int interval;
    /** Maximum number of samples the server keeps. */
    public final int capacity;
    /** True if the client's "since" was newer than anything known, so it must drop its samples. */
    public final boolean reset;
    public final long uptimeMs;
    public final LiveStats current;
    /** Only samples newer than the requested "since". */
    public final List<Sample> samples;

    MetricsResponse(int interval, int capacity, boolean reset, long uptimeMs, LiveStats current, List<Sample> samples) {
        this.interval = interval;
        this.capacity = capacity;
        this.reset = reset;
        this.uptimeMs = uptimeMs;
        this.current = current;
        this.samples = samples;
    }
}