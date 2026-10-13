package dev.mending.minedash.core.metrics;

/** Values that are shown as numbers and bars but never graphed. */
public final class LiveStats {

    public final long heapCommitted;
    public final long heapMax;
    public final int maxPlayers;
    public final int plugins;
    public final long diskTotal;
    public final long diskFree;

    LiveStats(long heapCommitted, long heapMax, int maxPlayers, int plugins, long diskTotal, long diskFree) {
        this.heapCommitted = heapCommitted;
        this.heapMax = heapMax;
        this.maxPlayers = maxPlayers;
        this.plugins = plugins;
        this.diskTotal = diskTotal;
        this.diskFree = diskFree;
    }
}