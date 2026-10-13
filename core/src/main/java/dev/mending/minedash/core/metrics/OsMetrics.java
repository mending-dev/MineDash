package dev.mending.minedash.core.metrics;

import javax.management.AttributeNotFoundException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reads CPU and memory values of the operating system bean through JMX.
 * The com.sun.management API is not part of Java 8 compile targets, and attribute names
 * changed in Java 14, so attributes are read by name with fallbacks.
 */
final class OsMetrics {

    private final MBeanServer server = ManagementFactory.getPlatformMBeanServer();
    private final ObjectName name;
    // Attributes this JVM does not have, remembered so no exception is thrown every cycle
    private final Set<String> missing = ConcurrentHashMap.newKeySet();

    OsMetrics() {
        try {
            name = new ObjectName(ManagementFactory.OPERATING_SYSTEM_MXBEAN_NAME);
        } catch (MalformedObjectNameException e) {
            throw new IllegalStateException(e);
        }
    }

    /** CPU usage of this server process in percent, or -1 if unavailable. */
    double processCpu() {
        return load("ProcessCpuLoad");
    }

    /** CPU usage of the whole machine in percent, or -1 if unavailable. */
    double systemCpu() {
        return load("CpuLoad", "SystemCpuLoad");
    }

    /** Physical memory of the machine (or container) in bytes, or 0 if unavailable. */
    long totalPhysicalMemory() {
        for (String attribute : new String[]{"TotalMemorySize", "TotalPhysicalMemorySize"}) {
            Object value = read(attribute);
            if (value instanceof Number && ((Number) value).longValue() > 0) {
                return ((Number) value).longValue();
            }
        }
        return 0;
    }

    private double load(String... attributes) {
        for (String attribute : attributes) {
            Object value = read(attribute);
            if (value == null) {
                continue;
            }
            if (value instanceof Number) {
                double load = ((Number) value).doubleValue();
                // The JVM reports a negative value or NaN while it has no data yet
                if (!Double.isNaN(load) && load >= 0) {
                    return Math.round(Math.min(1.0, load) * 1000) / 10.0;
                }
            }
            return -1;
        }
        return -1;
    }

    /** Returns null if the attribute does not exist or cannot be read. */
    private Object read(String attribute) {
        if (missing.contains(attribute)) {
            return null;
        }
        try {
            return server.getAttribute(name, attribute);
        } catch (AttributeNotFoundException e) {
            missing.add(attribute);
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}