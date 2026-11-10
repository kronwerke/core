package de.kronwerke.core.slot;

import com.mojang.authlib.GameProfile;
import de.kronwerke.core.KronwerkeCore;
import de.kronwerke.core.config.KronwerkeConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.UserWhiteList;
import net.minecraft.server.players.UserWhiteListEntry;

import java.util.Optional;
import java.util.UUID;

/**
 * Whitelist slot logic. Every streamer has an allowance; inviting a player adds them to the
 * vanilla whitelist and books the slot, revoking removes both.
 */
public final class SlotManager {
    private static final SlotManager INSTANCE = new SlotManager();

    private MinecraftServer server;

    public static SlotManager get() {
        return INSTANCE;
    }

    public void init(MinecraftServer server) {
        this.server = server;
        // Only the real server: a singleplayer world must never lock out its own player.
        if (server.isDedicatedServer() && KronwerkeConfig.ENFORCE_WHITELIST.get() && !server.getPlayerList().isUsingWhitelist()) {
            server.getPlayerList().setUsingWhiteList(true);
            KronwerkeCore.LOGGER.info("Whitelist turned on by Kronwerke Core.");
        }
    }

    /** Whether the player holds a place of their own (streamer or Season 1), not a slot given by someone. */
    public boolean hasOwnPlace(java.util.UUID player) {
        SlotData.StreamerEntry e = data().find(player);
        return e != null && e.granted;
    }

    private SlotData data() {
        return server.overworld().getDataStorage().computeIfAbsent(SlotData.FACTORY, SlotData.NAME);
    }

    public int streamerCount() {
        return server == null ? 0 : data().streamers().size();
    }

    public SlotData.StreamerEntry entry(UUID streamer, String name) {
        return data().getOrCreate(streamer, name);
    }

    public SlotData.StreamerEntry entryByName(String name) {
        return data().findByName(name);
    }

    public SlotData.StreamerEntry inviterOf(UUID player) {
        return data().findInviter(player);
    }

    public Iterable<SlotData.StreamerEntry> allStreamers() {
        return data().streamers().values();
    }

    public int allowance(SlotData.StreamerEntry e) {
        return e.allowance(KronwerkeConfig.DEFAULT_SLOTS.get());
    }

    public Optional<GameProfile> lookup(String playerName) {
        return server.getProfileCache() == null ? Optional.empty() : server.getProfileCache().get(playerName);
    }

    /** Result type for invite/revoke so the command layer can build the message. */
    public enum Result { OK, NO_SLOTS_LEFT, ALREADY_INVITED, UNKNOWN_PLAYER, NOT_INVITED_BY_YOU, ALREADY_WHITELISTED, NOT_GRANTED }

    public Result invite(SlotData.StreamerEntry streamer, String playerName) {
        Optional<GameProfile> profile = lookup(playerName);
        if (profile.isEmpty()) return Result.UNKNOWN_PLAYER;
        UUID id = profile.get().getId();

        if (streamer.invited.containsKey(id)) return Result.ALREADY_INVITED;
        if (data().findInviter(id) != null) return Result.ALREADY_INVITED;
        UserWhiteList wl = server.getPlayerList().getWhiteList();
        if (wl.isWhiteListed(profile.get())) return Result.ALREADY_WHITELISTED;
        if (streamer.used() >= allowance(streamer)) return Result.NO_SLOTS_LEFT;

        streamer.invited.put(id, profile.get().getName());
        data().setDirty();
        wl.add(new UserWhiteListEntry(profile.get()));
        KronwerkeCore.LOGGER.info("{} invited {} ({}/{} slots used)", streamer.name, profile.get().getName(), streamer.used(), allowance(streamer));
        return Result.OK;
    }

    public Result revoke(SlotData.StreamerEntry streamer, String playerName) {
        Optional<GameProfile> profile = lookup(playerName);
        if (profile.isEmpty()) return Result.UNKNOWN_PLAYER;
        UUID id = profile.get().getId();
        if (!streamer.invited.containsKey(id)) return Result.NOT_INVITED_BY_YOU;

        streamer.invited.remove(id);
        data().setDirty();
        server.getPlayerList().getWhiteList().remove(new UserWhiteListEntry(profile.get()));
        server.kickUnlistedPlayers(server.createCommandSourceStack());
        KronwerkeCore.LOGGER.info("{} revoked {}", streamer.name, profile.get().getName());
        return Result.OK;
    }

    /**
     * Whitelists a player without using anyone's slot and gives them slots of their own.
     * Used for streamers and Season 1 players. A negative slot count keeps the config
     * default, and an existing entry keeps its allowance.
     */
    public Result grant(String playerName, int slots) {
        Optional<GameProfile> profile = lookup(playerName);
        if (profile.isEmpty()) return Result.UNKNOWN_PLAYER;
        UUID id = profile.get().getId();
        SlotData.StreamerEntry inviter = data().findInviter(id);
        if (inviter != null) return Result.ALREADY_INVITED;
        boolean fresh = data().find(id) == null;
        SlotData.StreamerEntry e = entry(id, profile.get().getName());
        if (e.granted) return Result.ALREADY_WHITELISTED;
        e.granted = true;
        if (fresh && slots >= 0) e.slotOverride = slots;
        data().setDirty();
        server.getPlayerList().getWhiteList().add(new UserWhiteListEntry(profile.get()));
        KronwerkeCore.LOGGER.info("{} whitelisted by the team with {} slots of their own", e.name, allowance(e));
        return Result.OK;
    }

    /** Takes a granted place back, together with every slot the player gave away. */
    public Result ungrant(String playerName) {
        Optional<GameProfile> profile = lookup(playerName);
        if (profile.isEmpty()) return Result.UNKNOWN_PLAYER;
        SlotData.StreamerEntry e = data().find(profile.get().getId());
        if (e == null || !e.granted) return Result.NOT_GRANTED;
        UserWhiteList wl = server.getPlayerList().getWhiteList();
        for (UUID invited : e.invited.keySet()) {
            wl.remove(new UserWhiteListEntry(new GameProfile(invited, e.invited.get(invited))));
        }
        e.invited.clear();
        e.granted = false;
        data().setDirty();
        wl.remove(new UserWhiteListEntry(profile.get()));
        server.kickUnlistedPlayers(server.createCommandSourceStack());
        KronwerkeCore.LOGGER.info("{} lost their granted place", e.name);
        return Result.OK;
    }

    /** Moves an invited player to another streamer's slots, for the team when a streamer misbehaves. */
    public Result move(String playerName, SlotData.StreamerEntry to) {
        Optional<GameProfile> profile = lookup(playerName);
        if (profile.isEmpty()) return Result.UNKNOWN_PLAYER;
        UUID id = profile.get().getId();
        SlotData.StreamerEntry from = data().findInviter(id);
        if (from == null) return Result.NOT_INVITED_BY_YOU;
        if (from == to) return Result.ALREADY_INVITED;
        if (to.used() >= allowance(to)) return Result.NO_SLOTS_LEFT;
        from.invited.remove(id);
        to.invited.put(id, profile.get().getName());
        data().setDirty();
        KronwerkeCore.LOGGER.info("{} moved from {} to {}", profile.get().getName(), from.name, to.name);
        return Result.OK;
    }

    /**
     * After a failed revoke or ungrant: true when the player has nothing left on this server
     * (no slot from anyone, no place of their own, not on the whitelist, or no such account), so
     * a removal asked for by the bot counts as done. Without this the bot kept a player it could
     * never remove ("elchi_dev" stayed linked after the server had already forgotten them).
     */
    public boolean gone(String playerName, Result r) {
        if (r == Result.OK) return false;
        if (r == Result.UNKNOWN_PLAYER) return true;
        if (r != Result.NOT_INVITED_BY_YOU && r != Result.NOT_GRANTED) return false;
        Optional<GameProfile> profile = lookup(playerName);
        if (profile.isEmpty()) return true;
        UUID id = profile.get().getId();
        if (data().findInviter(id) != null) return false;
        SlotData.StreamerEntry own = data().find(id);
        if (own != null && own.granted) return false;
        UserWhiteList wl = server.getPlayerList().getWhiteList();
        if (wl.isWhiteListed(profile.get())) {
            wl.remove(new UserWhiteListEntry(profile.get()));
            server.kickUnlistedPlayers(server.createCommandSourceStack());
        }
        return true;
    }

    public void setSlots(SlotData.StreamerEntry streamer, int slots) {
        streamer.slotOverride = slots;
        data().setDirty();
    }

    public void addBonus(SlotData.StreamerEntry streamer, int bonus) {
        streamer.bonusSlots = Math.max(0, streamer.bonusSlots + bonus);
        data().setDirty();
    }
}
