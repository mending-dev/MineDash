package dev.mending.minedash.core.metrics;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Keeps a short history of server metrics.
 * JVM and OS values are read on a background thread. Everything that needs the Bukkit API
 * (players, plugins, TPS) is read by a task on the server main thread, because the Bukkit API
 * is not thread-safe.
 */
public final class MetricsCollector {

    public static final int INTERVAL_MS = 2000;
    /** 150 samples at 2 seconds = 5 minutes. */
    public static final int CAPACITY = 150;

    private static final long DISK_REFRESH_MS = 30_000;
    /** If the main thread did not report for this long, it is considered frozen. */
    private static final long TICK_STALE_MS = 5_000;

    private final Plugin plugin;
    private final Logger logger;
    private final OsMetrics os = new OsMetrics();
    private final MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
    private final RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
    private final File diskRoot;

    private final Object lock = new Object();
    private final ArrayDeque<Sample> history = new ArrayDeque<>();
    private long nextSeq = 1;
    private volatile LiveStats live = new LiveStats(0, 0, 0, 0, 0, 0);

    // Written by the main thread, read by the sampler thread
    private volatile int players;
    private volatile int maxPlayers;
    private volatile int plugins;
    private volatile double tps = -1;
    private volatile long lastTickMillis;

    // Main thread only
    private long lastTickNanos;
    private double smoothedTps = -1;
    private boolean tickErrorLogged;

    // Sampler thread only
    private long diskRefreshedAt;
    private long diskTotal;
    private long diskFree;
    private boolean sampleErrorLogged;

    private volatile ServerInfo info;
    private ScheduledExecutorService executor;
    private BukkitTask tickTask;

    /** Call on the main thread. */
    public MetricsCollector(Plugin plugin, Logger logger) {
        this.plugin = plugin;
        this.logger = logger;
        this.diskRoot = Bukkit.getWorldContainer().getAbsoluteFile();
    }

    /** Call on the main thread. */
    public void start() {
        info = ServerInfo.collect(os);
        readServerData();

        // 20 ticks = one second when the server runs at full speed
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);

        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "MineDash-Metrics");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleAtFixedRate(this::sampleSafely, 0, INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    public ServerInfo getInfo() {
        return info;
    }

    /** Returns all samples with a sequence number greater than "since". */
    public MetricsResponse read(long since) {
        long from = Math.max(0, since);
        synchronized (lock) {
            long latest = history.isEmpty() ? 0 : history.getLast().seq;
            // The client is ahead of us, e.g. after a plugin reload. Send everything again.
            boolean reset = from > latest;
            if (reset) {
                from = 0;
            }

            List<Sample> samples = new ArrayList<>();
            Iterator<Sample> newestFirst = history.descendingIterator();
            while (newestFirst.hasNext()) {
                Sample sample = newestFirst.next();
                if (sample.seq <= from) {
                    break;
                }
                samples.add(sample);
            }
            Collections.reverse(samples);
            return new MetricsResponse(INTERVAL_MS, CAPACITY, reset, runtime.getUptime(), live, samples);
        }
    }

    // ---------------------------------------------------------------- main thread

    private void tick() {
        try {
            long now = System.nanoTime();
            // The first run only sets the baseline, startup time would distort it
            if (lastTickNanos != 0) {
                double seconds = (now - lastTickNanos) / 1_000_000_000.0;
                if (seconds > 0) {
                    double measured = Math.min(20.0, 20.0 / seconds);
                    smoothedTps = smoothedTps < 0 ? measured : smoothedTps * 0.6 + measured * 0.4;
                    tps = smoothedTps;
                }
            }
            lastTickNanos = now;
            lastTickMillis = System.currentTimeMillis();
            readServerData();
        } catch (RuntimeException e) {
            if (!tickErrorLogged) {
                tickErrorLogged = true;
                logger.log(Level.WARNING, "Reading server data failed", e);
            }
        }
    }

    private void readServerData() {
        players = Bukkit.getOnlinePlayers().size();
        maxPlayers = Bukkit.getMaxPlayers();
        plugins = Bukkit.getPluginManager().getPlugins().length;
    }

    // ---------------------------------------------------------------- sampler thread

    /** A scheduled executor stops silently after an exception, so everything is caught here. */
    private void sampleSafely() {
        try {
            sample();
        } catch (Throwable t) {
            if (!sampleErrorLogged) {
                sampleErrorLogged = true;
                logger.log(Level.WARNING, "Collecting metrics failed", t);
            }
        }
    }

    private void sample() {
        long now = System.currentTimeMillis();
        MemoryUsage heap = memory.getHeapMemoryUsage();
        long heapMax = heap.getMax() > 0 ? heap.getMax() : heap.getCommitted();
        double cpu = os.processCpu();
        double systemCpu = os.systemCpu();
        refreshDisk(now);

        double tpsValue = tps;
        long lastTick = lastTickMillis;
        if (lastTick != 0 && now - lastTick > TICK_STALE_MS) {
            tpsValue = 0;
        }

        synchronized (lock) {
            history.addLast(new Sample(nextSeq++, now, cpu, systemCpu, heap.getUsed(), round2(tpsValue), players));
            while (history.size() > CAPACITY) {
                history.removeFirst();
            }
            live = new LiveStats(heap.getCommitted(), heapMax, maxPlayers, plugins, diskTotal, diskFree);
        }
    }

    private void refreshDisk(long now) {
        if (now - diskRefreshedAt < DISK_REFRESH_MS) {
            return;
        }
        diskRefreshedAt = now;
        diskTotal = diskRoot.getTotalSpace();
        diskFree = diskRoot.getUsableSpace();
    }

    private static double round2(double value) {
        return Math.round(value * 100) / 100.0;
    }
}