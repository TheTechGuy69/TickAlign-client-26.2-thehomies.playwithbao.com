package com.homies.tickalign.client.mixin;

import com.homies.tickalign.client.PacketScheduler;
import com.homies.tickalign.client.TickAlignClient;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public class ConnectionMixin {
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V",
            at = @At("HEAD"), cancellable = true)
    private void tickalign$onSend(Packet<?> packet, CallbackInfo ci) {
        PacketScheduler sched = TickAlignClient.getPacketScheduler();
        if (sched != null && sched.interceptOutgoing((Connection)(Object)this, packet))
            ci.cancel();
    }
}
