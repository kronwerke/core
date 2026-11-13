package de.kronwerke.core.net;

import com.google.gson.JsonObject;
import de.kronwerke.core.KronwerkeCore;
import de.kronwerke.core.Text;
import de.kronwerke.core.link.NetworkSync;
import de.kronwerke.core.link.Role;
import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Base64;
import java.util.UUID;

/**
 * The /kw hub on a side world. Season, goals and places are main's; a side world asks main for
 * the hub of the player who opened it and shows exactly that, so the menu looks and reads the
 * same in the farm world or the Nether as at home. What needs the player's things on main
 * (giving to the goal, the whitelist) says so in the hub's own line instead of doing nothing.
 */
public final class HubLink {
    public static final String TOPIC = "kw.hub";

    private HubLink() {
    }

    /** Side world: this player's hub from main; why is "" or "main_only". */
    public static void ask(ServerPlayer p, String why) {
        NetworkSync.Peer main = NetworkSync.peerByRole("main");
        if (main == null || !main.running()) {
            p.sendSystemMessage(Text.t("network.main_down", "Die Hauptwelt ist gerade nicht erreichbar.").withStyle(net.minecraft.ChatFormatting.GRAY));
            return;
        }
        JsonObject d = new JsonObject();
        d.addProperty("t", "ask");
        d.addProperty("uuid", p.getUUID().toString());
        d.addProperty("admin", p.hasPermissions(2));
        d.addProperty("why", why);
        NetworkSync.send(main.name(), TOPIC, d, null);
    }

    public static void onMessage(MinecraftServer server, String from, JsonObject d) {
        try {
            String t = d.get("t").getAsString();
            UUID id = UUID.fromString(d.get("uuid").getAsString());
            if (t.equals("ask") && Role.main()) {
                String why = d.has("why") ? d.get("why").getAsString() : "";
                Component message = switch (why) {
                    case "main_only" -> Text.t("network.main_only", "Das geht nur in der Hauptwelt.");
                    default -> Component.empty();
                };
                KwNetwork.HubPayload hub = KwNetwork.hub(id, d.get("admin").getAsBoolean(), message, !why.isEmpty());
                RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
                try {
                    KwNetwork.HubPayload.CODEC.encode(buf, hub);
                    byte[] bytes = new byte[buf.readableBytes()];
                    buf.readBytes(bytes);
                    JsonObject r = new JsonObject();
                    r.addProperty("t", "view");
                    r.addProperty("uuid", id.toString());
                    r.addProperty("hub", Base64.getEncoder().encodeToString(bytes));
                    NetworkSync.send(from, TOPIC, r, null);
                } finally {
                    buf.release();
                }
            } else if (t.equals("view")) {
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                if (p == null) return;
                RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(Base64.getDecoder().decode(d.get("hub").getAsString())), server.registryAccess());
                try {
                    PacketDistributor.sendToPlayer(p, KwNetwork.HubPayload.CODEC.decode(buf));
                } finally {
                    buf.release();
                }
            }
        } catch (RuntimeException e) {
            KronwerkeCore.LOGGER.warn("The hub between servers: {}", e.toString());
        }
    }
}
