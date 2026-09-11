package com.homies.tickalign.client;

import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;

import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * v3: Added sprint/slot packets, packet coalescing within same tick window,
 * graceful shutdown, better stats tracking.
 */
public class PacketScheduler {

    private static final long MAX_DELAY_NS = 20_000_000L;
    private static final long MIN_DELAY_NS = 1_000_000L;
    private static final long COALESCE_WINDOW_NS = 2_000_000L; // 2ms — batch nearby packets

    private static final Set<Class<? extends Packet<?>>> ALIGNED_PACKETS = Set.of(
        // Combat
        ServerboundInteractPacket.class,
        ServerboundSwingPacket.class,
        // Block interaction
        ServerboundUseItemOnPacket.class,
        ServerboundUseItemPacket.class,
        ServerboundPlayerActionPacket.class,
        // Sprint toggle (important for PvP sprint-reset)
        ServerboundPlayerCommandPacket.class,
        // Hotbar slot change during combat
        ServerboundSetCarriedItemPacket.class
    );

    private final ServerTickModel tickModel;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean bypassing = new AtomicBoolean(false);
    private volatile boolean enabled = true;

    // Stats
    private final AtomicLong packetsAligned = new AtomicLong(0);
    private final AtomicLong packetsPassed  = new AtomicLong(0);

    // Coalescing: track last scheduled send time to batch nearby packets
    private volatile long lastScheduledSendTime = 0;

    public PacketScheduler(ServerTickModel tickModel) {
        this.tickModel = tickModel;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "TickAlign-Scheduler");
            t.setDaemon(true);
            t.setPriority(Thread.MAX_PRIORITY); // timing-critical
            return t;
        });
    }

    public boolean interceptOutgoing(Connection connection, Packet<?> packet) {
        if (!enabled || bypassing.get() || !tickModel.isActive()) return false;
        if (!ALIGNED_PACKETS.contains(packet.getClass())) return false;

        long now = System.nanoTime();
        long delay = tickModel.optimalSendDelay(now);

        if (delay < MIN_DELAY_NS || delay > MAX_DELAY_NS) {
            packetsPassed.incrementAndGet();
            return false;
        }

        // Coalescing: if another packet was just scheduled within 2ms of the
        // same target time, use the same send time (batches related packets
        // like attack+swing that fire in the same frame)
        long sendTime = now + delay;
        if (Math.abs(sendTime - lastScheduledSendTime) < COALESCE_WINDOW_NS
                && lastScheduledSendTime > now) {
            delay = lastScheduledSendTime - now;
        } else {
            lastScheduledSendTime = sendTime;
        }

        packetsAligned.incrementAndGet();
        final long finalDelay = delay;
        scheduler.schedule(() -> {
            bypassing.set(true);
            try { connection.send(packet); }
            finally { bypassing.set(false); }
        }, finalDelay, TimeUnit.NANOSECONDS);
        return true;
    }

    public void setEnabled(boolean e)  { enabled = e; }
    public boolean isEnabled()         { return enabled; }
    public long getPacketsAligned()    { return packetsAligned.get(); }
    public long getPacketsPassed()    { return packetsPassed.get(); }

    public void shutdown() {
        scheduler.shutdownNow();
        try { scheduler.awaitTermination(100, TimeUnit.MILLISECONDS); }
        catch (InterruptedException ignored) {}
    }
}
