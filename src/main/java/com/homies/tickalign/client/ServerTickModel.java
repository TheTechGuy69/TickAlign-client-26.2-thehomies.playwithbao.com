package com.homies.tickalign.client;

/**
 * Models the server tick clock from the client's perspective.
 *
 * v3: tick arrival histogram, phase drift prediction, connection quality grading
 */
public class ServerTickModel {

    private static final long DEFAULT_TICK_INTERVAL = 50_000_000L;
    private static final double PHASE_ALPHA = 0.15;
    private static final double JITTER_ALPHA = 0.15;
    private static final double DRIFT_ALPHA  = 0.05;
    private static final long MIN_SAFETY = 1_000_000L;
    private static final long MAX_SAFETY = 8_000_000L;

    private volatile long tickIntervalNanos = DEFAULT_TICK_INTERVAL;
    private volatile long rttNanos = 50_000_000L;
    private volatile long anchorClientNanos = 0;
    private volatile boolean locked = false;
    private volatile boolean serverDisabled = false;

    private volatile long rttJitterNanos = 3_000_000L;
    private long lastRttSample = 0;
    private long lastAnchorAdj = 0;
    private long driftVelocity = 0;

    private final long[] arrivalErrors = new long[16];
    private int arrivalIdx = 0;
    private int arrivalCount = 0;
    private int samples = 0;
    private boolean loggedLock = false;

    public enum Quality { EXCELLENT, GOOD, FAIR, POOR, UNLOCKED }

    public void onTickSync(int tickNumber, long clientReceiveNanos) {
        long est = clientReceiveNanos - (rttNanos / 2);
        if (!locked) {
            anchorClientNanos = est;
            if (++samples >= 3) locked = true;
        } else {
            long predicted = nearestBoundary(est);
            long error = est - predicted;

            arrivalErrors[arrivalIdx] = Math.abs(error);
            arrivalIdx = (arrivalIdx + 1) % arrivalErrors.length;
            if (arrivalCount < arrivalErrors.length) arrivalCount++;

            long adj = (long)(PHASE_ALPHA * error);
            driftVelocity = (long)(DRIFT_ALPHA * (adj - lastAnchorAdj) + (1 - DRIFT_ALPHA) * driftVelocity);
            lastAnchorAdj = adj;
            anchorClientNanos += adj + driftVelocity;
        }
    }

    public void onSyncState(long newRtt, long newInterval) {
        if (lastRttSample > 0) {
            long dev = Math.abs(newRtt - lastRttSample);
            rttJitterNanos = (long)(JITTER_ALPHA * dev + (1 - JITTER_ALPHA) * rttJitterNanos);
        }
        lastRttSample = newRtt;
        rttNanos = newRtt;
        if (newInterval > 0) tickIntervalNanos = newInterval;
    }

    public void onServerDisable(boolean disabled) { serverDisabled = disabled; }

    public void reset() {
        locked = false; serverDisabled = false; loggedLock = false;
        samples = 0; rttNanos = 50_000_000L; rttJitterNanos = 3_000_000L;
        lastRttSample = 0; driftVelocity = 0; lastAnchorAdj = 0;
        arrivalCount = 0; arrivalIdx = 0;
        tickIntervalNanos = DEFAULT_TICK_INTERVAL;
    }

    public long nanosUntilNextTick(long clientNow) {
        if (!locked) return -1;
        long phase = (clientNow - anchorClientNanos) % tickIntervalNanos;
        if (phase < 0) phase += tickIntervalNanos;
        return tickIntervalNanos - phase;
    }

    public long getSafetyMarginNanos() {
        long safety = (arrivalCount >= 8) ? percentile(90) : rttJitterNanos * 2;
        return Math.max(MIN_SAFETY, Math.min(MAX_SAFETY, safety));
    }

    public long optimalSendDelay(long clientNow) {
        if (!locked || serverDisabled) return -1;
        long safety = getSafetyMarginNanos();
        long travel = rttNanos / 2;
        long until  = nanosUntilNextTick(clientNow);
        if (until < 0) return -1;
        long target = until - safety;
        if (target < 0) target += tickIntervalNanos;
        long delay = target - travel;
        if (delay < 0 || delay > tickIntervalNanos) return 0;
        return delay;
    }

    public Quality getQuality() {
        if (!locked) return Quality.UNLOCKED;
        double r = (double) rttJitterNanos / tickIntervalNanos;
        if (r < 0.05) return Quality.EXCELLENT;
        if (r < 0.15) return Quality.GOOD;
        if (r < 0.30) return Quality.FAIR;
        return Quality.POOR;
    }

    public boolean isActive() {
        return locked && !serverDisabled && getQuality() != Quality.POOR;
    }

    private long nearestBoundary(long t) {
        long el = t - anchorClientNanos;
        return anchorClientNanos + Math.round((double) el / tickIntervalNanos) * tickIntervalNanos;
    }

    private long percentile(int p) {
        long[] s = new long[arrivalCount];
        System.arraycopy(arrivalErrors, 0, s, 0, arrivalCount);
        java.util.Arrays.sort(s);
        return s[Math.min(s.length - 1, (int)(s.length * p / 100.0))];
    }

    public boolean isLocked()          { return locked; }
    public boolean isServerDisabled()  { return serverDisabled; }
    public long getRttNanos()          { return rttNanos; }
    public long getRttJitterNanos()    { return rttJitterNanos; }
    public long getTickIntervalNanos() { return tickIntervalNanos; }
    public boolean shouldLogLock()     { if (locked && !loggedLock) { loggedLock = true; return true; } return false; }
}
