package de.kronwerke.core.share.ws;

import com.google.gson.JsonObject;
import de.kronwerke.core.KronwerkeCore;
import de.kronwerke.core.link.NetworkSync;
import de.kronwerke.core.link.Role;
import de.kronwerke.core.portal.AwayData;
import de.kronwerke.core.portal.Travel;
import de.kronwerke.core.share.Layer;
import de.kronwerke.core.share.Share;
import net.blay09.mods.waystones.api.TeleportDestination;
import net.blay09.mods.waystones.api.Waystone;
import net.blay09.mods.waystones.core.WaystoneImpl;
import net.blay09.mods.waystones.core.WaystoneManagerImpl;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Waystones between servers. Main and the Nether and End servers each send their own
 * waystones to the others (the mining world's do not travel, that world resets); every
 * server shows them in its list (the player file already knows which ones a player has
 * found), and choosing one moves the player to that server, standing at that waystone.
 * They are kept with a dimension of their own ({@code kronwerke:remote/<server>/...}), so
 * even without this layer a jump there could only fail. Before, only main sent its list,
 * so a Nether hub never showed up in the overworld (review R3-06).
 */
public final class WaystoneLayer implements Layer {
    private static final String REMOTE = "remote/";
    private int ticks;
    private String sent = "";
    private boolean received;
    private long sentAt;

    public static WaystoneLayer create() {
        return new WaystoneLayer();
    }

    @Override
    public String name() {
        return "waystones";
    }

    /** Whether a waystone in this server's list stands on main. */
    public static boolean isRemote(Waystone w) {
        ResourceLocation d = w.getDimension().location();
        return d.getNamespace().equals(KronwerkeCore.MOD_ID) && d.getPath().startsWith(REMOTE);
    }

    /** The server a remote waystone stands on; null for the old form, which is main's. */
    static String serverOf(Waystone w) {
        String[] parts = w.getDimension().location().getPath().split("/");
        return parts.length >= 4 ? parts[1] : null;
    }

    /** The player chose a waystone on another server. */
    public static void travel(ServerPlayer p, Waystone w) {
        JsonObject arrive = new JsonObject();
        arrive.addProperty("waystone", w.getWaystoneUid().toString());
        String at = serverOf(w);
        boolean moved = at == null ? Travel.moveTo(p, arrive) : Travel.moveTo(p, at, arrive);
        if (!moved) {
            p.sendSystemMessage(de.kronwerke.core.Text.t("portal.nowhere", "Die andere Welt ist gerade nicht erreichbar."));
        }
    }

    @Override
    public void tick(MinecraftServer server) {
        if (++ticks % 100 != 0) return;
        if (sends()) send(server, false);
        if (!Role.main() && !received) ask();
    }

    private static boolean sends() {
        return Role.main() || de.kronwerke.core.portal.Dimensions.dimensionServer();
    }

    private void send(MinecraftServer server, boolean always) {
        ListTag list = new ListTag();
        WaystoneManagerImpl.get(server).getWaystones().forEach(w -> {
            if (!isRemote(w)) list.add(WaystoneImpl.write(w, new CompoundTag(), server.registryAccess()));
        });
        CompoundTag all = new CompoundTag();
        all.put("w", list);
        String text = all.toString();
        long now = System.currentTimeMillis();
        if (!always && text.equals(sent) && now - sentAt < 60_000) return;
        sent = text;
        sentAt = now;
        JsonObject m = new JsonObject();
        m.addProperty("w", text);
        for (String peer : Share.peers()) Share.tell(this, peer, m);
    }

    @Override
    public void onState(String from, JsonObject state) {
    }

    @Override
    public void onTell(MinecraftServer server, String from, JsonObject data) {
        if (!data.has("w")) {
            // another server asks for the list (it just started)
            if (sends()) send(server, true);
            return;
        }
        CompoundTag all;
        try {
            all = TagParser.parseTag(data.get("w").getAsString());
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new IllegalArgumentException(e);
        }
        received = true;
        WaystoneManagerImpl manager = WaystoneManagerImpl.get(server);
        Set<UUID> seen = new HashSet<>();
        for (Tag t : all.getList("w", Tag.TAG_COMPOUND)) {
            Waystone w = WaystoneImpl.read((CompoundTag) t, server.registryAccess());
            if (!(w instanceof WaystoneImpl impl)) continue;
            Optional<Waystone> here = manager.getWaystoneById(w.getWaystoneUid());
            // one of this world's own waystones keeps its place
            if (here.isPresent() && !isRemote(here.get())) continue;
            ResourceLocation d = w.getDimension().location();
            impl.setDimension(ResourceKey.create(Registries.DIMENSION,
                    ResourceLocation.fromNamespaceAndPath(KronwerkeCore.MOD_ID, REMOTE + from + "/" + d.getNamespace() + "/" + d.getPath())));
            seen.add(w.getWaystoneUid());
            if (here.isPresent()) manager.updateWaystone(impl);
            else manager.addWaystone(impl);
        }
        List<Waystone> gone = new ArrayList<>();
        // only this sender's: the other servers' lists come on their own. The old form
        // without a server name was main's.
        boolean fromMain = de.kronwerke.core.link.NetworkSync.peer(from) != null && "main".equals(de.kronwerke.core.link.NetworkSync.peer(from).role());
        manager.getWaystones().forEach(w -> {
            if (!isRemote(w) || seen.contains(w.getWaystoneUid())) return;
            String at = serverOf(w);
            if (from.equals(at) || (at == null && fromMain)) gone.add(w);
        });
        for (Waystone w : gone) manager.removeWaystone(w);
    }

    /** main: where a player who chose this waystone on a side world arrives. */
    @Override
    public AwayData.Back arrive(MinecraftServer server, JsonObject arrive) {
        if (!arrive.has("waystone")) return null;
        Optional<Waystone> w = WaystoneManagerImpl.get(server).getWaystoneById(UUID.fromString(arrive.get("waystone").getAsString()));
        if (w.isEmpty() || isRemote(w.get())) return null;
        ServerLevel level = server.getLevel(w.get().getDimension());
        if (level == null) return null;
        Optional<TeleportDestination> to = w.get().resolveDestination(level);
        if (to.isEmpty()) return null;
        TeleportDestination t = to.get();
        ResourceKey<Level> dim = t.level().dimension();
        return new AwayData.Back(dim.location().toString(), t.location().x, t.location().y, t.location().z, t.direction().toYRot());
    }

    /** A side world asks the others for their lists until it has one. */
    private void ask() {
        for (String p : Share.peers()) Share.tell(this, p, new JsonObject());
    }

    @Override
    public JsonObject receive(MinecraftServer server, String from, JsonObject data) {
        return null;
    }

    @Override
    public JsonObject refund(MinecraftServer server, JsonObject data) {
        return null;
    }
}
