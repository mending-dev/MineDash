package dev.mending.minedash.core.metrics;

/** One data point of the history. Serialized to JSON as is. */
public final class Sample {

    public final long seq;
    public final long t;
    /** Server process CPU in percent, -1 if the JVM cannot report it. */
    public final double cpu;
    /** Whole system CPU in percent, -1 if the JVM cannot report it. */
    public final double systemCpu;
    public final long heapUsed;
    /** 0 to 20. 0 means the main thread is not responding, -1 means not measured yet. */
    public final double tps;
    public final int players;

    Sample(long seq, long t, double cpu, double systemCpu, long heapUsed, double tps, int players) {
        this.seq = seq;
        this.t = t;
        this.cpu = cpu;
        this.systemCpu = systemCpu;
        this.heapUsed = heapUsed;
        this.tps = tps;
        this.players = players;
    }
}