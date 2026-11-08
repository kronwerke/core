package de.kronwerke.core.loadtest;

import de.kronwerke.core.mixin.ConnectionChannelAccessor;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.registration.ChannelAttributes;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

/** The connection of a load test bot: never sends, never receives, never closes by itself. */
public final class BotConnection extends Connection {
    private static final SocketAddress ADDRESS = new InetSocketAddress("127.0.0.1", 0);

    public BotConnection() {
        super(PacketFlow.SERVERBOUND);
        ((ConnectionChannelAccessor) (Object) this).kronwerke$setChannel(new EmbeddedChannel());
        ChannelAttributes.setConnectionType(this, ConnectionType.NEOFORGE);
    }

    @Override
    public void send(Packet<?> packet, PacketSendListener listener, boolean flush) {
    }

    @Override
    public <T extends PacketListener> void setupInboundProtocol(ProtocolInfo<T> protocolInfo, T listener) {
    }

    @Override
    public void setupOutboundProtocol(ProtocolInfo<?> protocolInfo) {
    }

    @Override
    public void setReadOnly() {
    }

    @Override
    public void handleDisconnection() {
    }

    @Override
    public boolean isConnected() {
        return true;
    }

    @Override
    public SocketAddress getRemoteAddress() {
        return ADDRESS;
    }

    @Override
    public String getLoggableAddress(boolean logIps) {
        return "load test";
    }
}
