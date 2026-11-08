package de.kronwerke.core.mixin;

import io.netty.channel.Channel;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Gives a load test bot's connection a channel of its own, so channel attributes work. */
@Mixin(Connection.class)
public interface ConnectionChannelAccessor {
    @Accessor("channel")
    void kronwerke$setChannel(Channel channel);
}
