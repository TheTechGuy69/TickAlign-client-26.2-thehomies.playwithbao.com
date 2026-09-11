package com.homies.tickalign.client;

import java.nio.ByteBuffer;

public final class Protocol {
    public static final byte TICK_SYNC      = 0x01;
    public static final byte PING_REQUEST   = 0x02;
    public static final byte PONG_RESPONSE  = 0x03;
    public static final byte SYNC_STATE     = 0x04;
    public static final byte DISABLE        = 0x05;
    public static final byte SERVER_CONFIG  = 0x06;

    public static byte type(byte[] d) { return d[0]; }

    public record TickSyncData(int tickNumber, long serverNanos) {}
    public static TickSyncData decodeTickSync(byte[] d) {
        ByteBuffer b = ByteBuffer.wrap(d); b.get();
        return new TickSyncData(b.getInt(), b.getLong());
    }

    public record PingRequestData(int pingId, long serverSendNanos) {}
    public static PingRequestData decodePingRequest(byte[] d) {
        ByteBuffer b = ByteBuffer.wrap(d); b.get();
        return new PingRequestData(b.getInt(), b.getLong());
    }

    public record SyncStateData(long rttNanos, long tickIntervalNanos) {}
    public static SyncStateData decodeSyncState(byte[] d) {
        ByteBuffer b = ByteBuffer.wrap(d); b.get();
        return new SyncStateData(b.getLong(), b.getLong());
    }

    public record DisableData(boolean disabled) {}
    public static DisableData decodeDisable(byte[] d) {
        return new DisableData(d[1] == 1);
    }

    public record ServerConfigData(long msptNanos, int syncInterval, int pingInterval) {}
    public static ServerConfigData decodeServerConfig(byte[] d) {
        ByteBuffer b = ByteBuffer.wrap(d); b.get();
        return new ServerConfigData(b.getLong(), b.getInt(), b.getInt());
    }

    public static byte[] pongResponse(int pingId, long origNanos) {
        return ByteBuffer.allocate(13).put(PONG_RESPONSE).putInt(pingId).putLong(origNanos).array();
    }

    private Protocol() {}
}
