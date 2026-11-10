package de.kronwerke.core.slot;

import com.mojang.authlib.GameProfile;
import de.kronwerke.core.KronwerkeCore;
import de.kronwerke.core.config.KronwerkeConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.UserWhiteList;
import net.minecraft.server.players.UserWhiteListEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Whitelist slot logic. Only players with a place of their own that the team or the bot
 * granted (streamers, Season 1 players) have slots; inviting a player adds them to the
 * vanilla whitelist and books one of those slots, revoking removes both.
 * <p>
 * Names are resolved once, at the edge: the bot sends a player's uuid along, players' own
 * requests are resolved off the server thread ({@link #resolve}), and everything here works
 * on resolved profiles.
 */
public final class SlotManager {
    private static final SlotManager INSTANCE = new SlotManager();

    public static final String STREAMER = "streamer", SEASON1 = "season1", TEAM = "team";

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
        cleanUp();
    }

    /**
     * Entries of players who never had a place of their own: before 0.27 every player who
     * opened the menu got one with five slots. Empty ones go; the ones that invited somebody
     * stay (their invites are real) but can invite nobody else, and the log names them.
     */
    private void cleanUp() {
        SlotData d = data();
        if (d.whitelisted().isEmpty()) {
            // before 0.27 nothing was tracked: every place and slot so far was made by Core
            for (SlotData.StreamerEntry e : d.streamers().values()) {
                if (e.granted) d.whitelisted().add(e.uuid);
                d.whitelisted().addAll(e.invited.keySet());
            }
            d.setDirty();
        }
        List<UUID> empty = new ArrayList<>();
        List<String> stray = new ArrayList<>();
        for (SlotData.StreamerEntry e : d.streamers().values()) {
            if (e.granted && e.kind.isEmpty()) {
                e.kind = e.allowance(KronwerkeConfig.DEFAULT_SLOTS.get()) > 2 ? STREAMER : SEASON1; // the old guess, once
                d.setDirty();
            }
            if (e.granted) continue;
            if (e.invited.isEmpty()) empty.add(e.uuid);
            else stray.add(e.name + " (" + e.invited.size() + ")");
        }
        empty.forEach(d::removeIfEmpty);
        if (!empty.isEmpty()) KronwerkeCore.LOGGER.info("Removed {} slot entries of players without a place of their own", empty.size());
        if (!stray.isEmpty()) KronwerkeCore.LOGGER.warn("Players without a place of their own who invited others (the team should look at them): {}", String.join(", ", stray));
    }

    // ---- who is what ----

    /** Whether the player holds a place of their own (streamer or Season 1), not a slot given by someone. */
    public boolean hasOwnPlace(UUID player) {
        SlotData.StreamerEntry e = data().find(player);
        return e != null && e.granted;
    }

    /** Whether the player is a streamer: a granted place of the streamer kind. */
    public boolean isStreamer(UUID player) {
        SlotData.StreamerEntry e = server == null ? null : data().find(player);
        return e != null && e.granted && e.kind.equals(STREAMER);
    }

    /** The player's own entry if they may hand out slots, or null. Never creates one. */
    public SlotData.StreamerEntry ownSlots(UUID player) {
        SlotData.StreamerEntry e = data().find(player);
        return e != null && e.granted ? e : null;
    }

    private SlotData data() {
        return server.overworld().getDataStorage().computeIfAbsent(SlotData.FACTORY, SlotData.NAME);
    }

    public int streamerCount() {
        if (server == null) return 0;
        int n = 0;
        for (SlotData.StreamerEntry e : data().streamers().values()) if (e.granted) n++;
        return n;
    }

    public SlotData.StreamerEntry find(UUID id) {
        return data().find(id);
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

    // ---- names ----

    /**
     * A profile for a name: with the uuid the bot sends, as given; without one, from the
     * profile cache (which may ask Mojang and block: only for commands of the team and the
     * old bot, never for a player's own request).
     */
    public Optional<GameProfile> profile(String name, UUID id) {
        if (id != null) return Optional.of(new GameProfile(id, name));
        return lookup(name);
    }

    public Optional<GameProfile> lookup(String playerName) {
        return server.getProfileCache() == null ? Optional.empty() : server.getProfileCache().get(playerName);
    }

    /** Resolves a name off the server thread and hands the answer back on it. */
    public void resolve(String name, Consumer<Optional<GameProfile>> then) {
        var cache = server.getProfileCache();
        if (cache == null || !name.matches("[A-Za-z0-9_]{1,16}")) {
            then.accept(Optional.empty());
            return;
        }
        cache.getAsync(name).whenComplete((p, err) -> server.execute(() -> then.accept(err != null || p == null ? Optional.empty() : p)));
    }

    /** Result type for invite/revoke so the command layer can build the message. */
    public enum Result { OK, NO_SLOTS_LEFT, ALREADY_INVITED, UNKNOWN_PLAYER, NOT_INVITED_BY_YOU, ALREADY_WHITELISTED, NOT_GRANTED }

    // ---- slots ----

    /**
     * Uses one of the streamer's slots for the player. A streamer needs a place of their own;
     * team marks the team acting for them (panel, admin command), which may also act for an
     * entry without one.
     */
    public Result invite(SlotData.StreamerEntry streamer, GameProfile profile, boolean team) {
        if (!team && !streamer.granted) return Result.NOT_GRANTED;
        UUID id = profile.getId();
        if (streamer.invited.containsKey(id)) return Result.ALREADY_INVITED;
        if (data().findInviter(id) != null) return Result.ALREADY_INVITED;
        UserWhiteList wl = server.getPlayerList().getWhiteList();
        if (wl.isWhiteListed(profile)) return Result.ALREADY_WHITELISTED;
        if (streamer.used() >= allowance(streamer)) return Result.NO_SLOTS_LEFT;

        streamer.invited.put(id, profile.getName());
        whitelist(profile);
        KronwerkeCore.LOGGER.info("{} invited {} ({}/{} slots used)", streamer.name, profile.getName(), streamer.used(), allowance(streamer));
        return Result.OK;
    }

    public Result revoke(SlotData.StreamerEntry streamer, GameProfile profile) {
        UUID id = profile.getId();
        if (!streamer.invited.containsKey(id)) return Result.NOT_INVITED_BY_YOU;
        String known = streamer.invited.remove(id);
        data().setDirty();
        unwhitelist(new GameProfile(id, known == null ? profile.getName() : known));
        server.kickUnlistedPlayers(server.createCommandSourceStack());
        KronwerkeCore.LOGGER.info("{} revoked {}", streamer.name, profile.getName());
        return Result.OK;
    }

    /**
     * Whitelists a player without using anyone's slot and gives them slots of their own.
     * Used for streamers and Season 1 players. A negative slot count keeps the config
     * default; an explicit count always applies, also to an existing entry.
     */
    public Result grant(GameProfile profile, int slots, String kind) {
        UUID id = profile.getId();
        if (data().findInviter(id) != null) return Result.ALREADY_INVITED;
        SlotData.StreamerEntry e = data().getOrCreate(id, profile.getName());
        if (e.granted) {
            // the same grant again (the bot's restore): only the numbers and the kind follow
            if (slots >= 0) e.slotOverride = slots;
            if (kind != null && !kind.isEmpty()) e.kind = kind;
            data().setDirty();
            return Result.ALREADY_WHITELISTED;
        }
        e.granted = true;
        e.slotOverride = slots;
        e.bonusSlots = 0;
        e.kind = kind == null || kind.isEmpty() ? (slots >= 0 && slots <= 2 ? SEASON1 : STREAMER) : kind;
        data().setDirty();
        whitelist(profile);
        KronwerkeCore.LOGGER.info("{} whitelisted by the team as {} with {} slots of their own", e.name, e.kind, allowance(e));
        return Result.OK;
    }

    /** Takes a granted place back, together with every slot the player gave away. */
    public Result ungrant(GameProfile profile) {
        SlotData.StreamerEntry e = data().find(profile.getId());
        if (e == null || !e.granted) return Result.NOT_GRANTED;
        for (var inv : new ArrayList<>(e.invited.entrySet())) unwhitelist(new GameProfile(inv.getKey(), inv.getValue()));
        e.invited.clear();
        e.granted = false;
        e.kind = "";
        e.slotOverride = -1;
        e.bonusSlots = 0;
        data().setDirty();
        unwhitelist(profile);
        data().removeIfEmpty(profile.getId());
        server.kickUnlistedPlayers(server.createCommandSourceStack());
        KronwerkeCore.LOGGER.info("{} lost their granted place", profile.getName());
        return Result.OK;
    }

    /** Moves an invited player to another streamer's slots, for the team when a streamer misbehaves. */
    public Result move(GameProfile profile, SlotData.StreamerEntry to) {
        UUID id = profile.getId();
        SlotData.StreamerEntry from = data().findInviter(id);
        if (from == null) return Result.NOT_INVITED_BY_YOU;
        if (from == to) return Result.ALREADY_INVITED;
        if (to.used() >= allowance(to)) return Result.NO_SLOTS_LEFT;
        from.invited.remove(id);
        to.invited.put(id, profile.getName());
        data().setDirty();
        KronwerkeCore.LOGGER.info("{} moved from {} to {}", profile.getName(), from.name, to.name);
        return Result.OK;
    }

    /**
     * After a failed revoke or ungrant: true when the player has nothing left on this server
     * (no slot from anyone, no place of their own), so a removal asked for by the bot counts as
     * done. A whitelist entry goes too, but only one Core made: a player the team whitelisted
     * by hand stays.
     */
    public boolean gone(GameProfile profile, Result r) {
        if (r == Result.OK) return false;
        if (r != Result.NOT_INVITED_BY_YOU && r != Result.NOT_GRANTED) return false;
        UUID id = profile.getId();
        if (data().findInviter(id) != null) return false;
        SlotData.StreamerEntry own = data().find(id);
        if (own != null && own.granted) return false;
        if (data().whitelisted().contains(id)) {
            unwhitelist(profile);
            server.kickUnlistedPlayers(server.createCommandSourceStack());
        }
        return true;
    }

    /** Sets the slot count without the bonus; the bonus stays on top. */
    public void setSlots(SlotData.StreamerEntry streamer, int slots) {
        streamer.slotOverride = Math.max(0, slots);
        data().setDirty();
    }

    /** Changes the total by delta, as the panel's buttons do: the bonus stays, the base moves. */
    public void addSlots(SlotData.StreamerEntry streamer, int delta) {
        int base = streamer.slotOverride >= 0 ? streamer.slotOverride : KronwerkeConfig.DEFAULT_SLOTS.get();
        setSlots(streamer, base + delta);
    }

    public void addBonus(SlotData.StreamerEntry streamer, int bonus) {
        streamer.bonusSlots = Math.max(0, streamer.bonusSlots + bonus);
        data().setDirty();
    }

    // ---- the vanilla whitelist ----

    private void whitelist(GameProfile profile) {
        UserWhiteList wl = server.getPlayerList().getWhiteList();
        if (!wl.isWhiteListed(profile)) {
            wl.add(new UserWhiteListEntry(profile));
            data().whitelisted().add(profile.getId());
            data().setDirty();
        }
    }

    private void unwhitelist(GameProfile profile) {
        server.getPlayerList().getWhiteList().remove(new UserWhiteListEntry(profile));
        if (data().whitelisted().remove(profile.getId())) data().setDirty();
    }
}
