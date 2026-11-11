package de.kronwerke.core.tab;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tags a team member sets by hand with /kw admin tag (or /kwtag), over the automatic rank.
 * Every icon of the Nautical Ranks pack that has a glyph can be used, plus Kronwerke's own
 * icons from Core's font (kronwerke:tags). The tag is a scoreboard team prefix, so it changes
 * at once in the tab list, in chat and above the head, with no relog and no pack update.
 *
 * Tags hold across the network: whoever sets one sends the whole list over the bus, and every
 * server keeps a copy in config/kronwerke/tags.json, so they survive restarts.
 */
public final class Tags {
    private static final Logger LOG = LoggerFactory.getLogger("Kronwerke/Tags");
    public static final String TOPIC = "kw.tags";
    private static final ResourceLocation OWN_FONT = ResourceLocation.fromNamespaceAndPath("kronwerke", "tags");

    /** One icon: its glyph, whether it lives in Core's own font, and the name colour beside it. */
    public record Tag(String id, char glyph, boolean own, ChatFormatting colour) {
        public MutableComponent badge() {
            Style s = Style.EMPTY.withColor(ChatFormatting.WHITE);
            if (own) s = s.withFont(OWN_FONT);
            // icon and space side by side under an empty root: as a child of the icon the space (and
            // whatever is appended after the badge) would inherit the icon's font, which has no
            // other glyph, and show as a box
            return Component.empty().append(Component.literal(String.valueOf(glyph)).withStyle(s)).append(Component.literal(" "));
        }
    }

    /** By id, in the order /kwtag suggests them. The automatic ranks use owner, admin, twitch and member. */
    public static final Map<String, Tag> ALL = new LinkedHashMap<>();

    static {
        // Nautical Ranks, seav style (minecraft:default, U+E800 to U+E819)
        add("member", 0xE800, ChatFormatting.WHITE);
        add("media", 0xE801, ChatFormatting.DARK_PURPLE);
        add("manager", 0xE802, ChatFormatting.DARK_PURPLE);
        add("helper", 0xE803, ChatFormatting.YELLOW);
        add("mvp", 0xE804, ChatFormatting.LIGHT_PURPLE);
        add("mvpplus", 0xE805, ChatFormatting.LIGHT_PURPLE);
        add("mvpplusplus", 0xE806, ChatFormatting.LIGHT_PURPLE);
        add("mod", 0xE807, ChatFormatting.BLUE);
        add("noob", 0xE808, ChatFormatting.GOLD);
        add("owner", 0xE809, ChatFormatting.GOLD);
        add("admin", 0xE80A, ChatFormatting.RED);
        add("pro", 0xE80B, ChatFormatting.AQUA);
        add("twitch", 0xE80C, ChatFormatting.LIGHT_PURPLE);
        add("og", 0xE80D, ChatFormatting.GREEN);
        add("builder", 0xE80E, ChatFormatting.AQUA);
        add("vip", 0xE80F, ChatFormatting.GOLD);
        add("vipplus", 0xE810, ChatFormatting.GOLD);
        add("dev", 0xE811, ChatFormatting.GOLD);
        add("designer", 0xE812, ChatFormatting.RED);
        add("god", 0xE813, ChatFormatting.LIGHT_PURPLE);
        add("elite", 0xE814, ChatFormatting.AQUA);
        add("eliteplus", 0xE815, ChatFormatting.AQUA);
        add("youtube", 0xE816, ChatFormatting.DARK_RED);
        add("broadcast", 0xE817, ChatFormatting.RED);
        add("partner", 0xE818, ChatFormatting.DARK_PURPLE);
        add("npc", 0xE819, ChatFormatting.BLUE);
        // Nautical Ranks, the older style (U+E81A to U+E81F)
        add("twitch2", 0xE81A, ChatFormatting.LIGHT_PURPLE);
        add("vip2", 0xE81B, ChatFormatting.GOLD);
        add("vipplus2", 0xE81C, ChatFormatting.GOLD);
        add("yellow", 0xE81D, ChatFormatting.YELLOW);
        add("youtube2", 0xE81E, ChatFormatting.DARK_RED);
        add("member2", 0xE81F, ChatFormatting.WHITE);
        // Kronwerke's own, in Core's font, same style as seav
        ALL.put("omi", new Tag("omi", '', true, ChatFormatting.AQUA));
    }

    private static void add(String id, int glyph, ChatFormatting colour) {
        ALL.put(id, new Tag(id, (char) glyph, false, colour));
    }

    /** uuid → tag id; set by hand, wins over the automatic rank. */
    private static final Map<UUID, String> set = new ConcurrentHashMap<>();
    /** uuid → last known name, so the list reads well and offline players can be named. */
    private static final Map<UUID, String> names = new ConcurrentHashMap<>();

    private Tags() {
    }

    public static Tag get(String id) {
        return id == null ? null : ALL.get(id);
    }

    /** The hand-set tag of a player, or null. */
    public static String of(UUID id) {
        return set.get(id);
    }

    public static Map<UUID, String> all() {
        return Map.copyOf(set);
    }

    public static String nameOf(UUID id) {
        return names.getOrDefault(id, id.toString());
    }

    /** Sets (tag) or clears (null) a player's tag here, keeps the file and tells the network. */
    public static void put(UUID id, String name, String tag) {
        if (tag == null) set.remove(id);
        else set.put(id, tag);
        if (name != null) names.put(id, name);
        save();
        de.kronwerke.core.link.NetworkSync.send("*", TOPIC, toJson(), null);
        TabList.reapply();
    }

    /** The whole list from another server: it replaces ours. */
    public static void onMessage(JsonObject d) {
        if (!read(d)) return; // main repeats its list every 30 seconds
        save();
        TabList.reapply();
    }

    public static JsonObject toJson() {
        JsonObject o = new JsonObject();
        for (var e : set.entrySet()) {
            JsonObject t = new JsonObject();
            t.addProperty("tag", e.getValue());
            t.addProperty("name", nameOf(e.getKey()));
            o.add(e.getKey().toString(), t);
        }
        return o;
    }

    /** Takes the list over; true when it differs from what we had. */
    private static boolean read(JsonObject o) {
        Map<UUID, String> now = new LinkedHashMap<>();
        for (var e : o.entrySet()) {
            try {
                UUID id = UUID.fromString(e.getKey());
                JsonObject t = e.getValue().getAsJsonObject();
                String tag = t.get("tag").getAsString();
                if (!ALL.containsKey(tag)) continue; // a tag from a newer Core: leave the rank automatic
                now.put(id, tag);
                if (t.has("name")) names.put(id, t.get("name").getAsString());
            } catch (RuntimeException ex) {
                LOG.warn("Skipping a broken tag entry {}", e.getKey());
            }
        }
        if (now.equals(set)) return false;
        set.keySet().retainAll(now.keySet());
        set.putAll(now);
        return true;
    }

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("kronwerke").resolve("tags.json");
    }

    public static void load() {
        Path f = file();
        if (!Files.exists(f)) return;
        try {
            JsonElement e = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8));
            if (e.isJsonObject()) read(e.getAsJsonObject());
        } catch (IOException | RuntimeException ex) {
            LOG.error("Could not read {}, tags stay automatic", f, ex);
        }
    }

    private static synchronized void save() {
        Path f = file();
        try {
            Files.createDirectories(f.getParent());
            // the servers may share this folder: each writes its own temporary file
            Path tmp = Files.createTempFile(f.getParent(), "tags", ".tmp");
            Files.writeString(tmp, toJson().toString(), StandardCharsets.UTF_8);
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            LOG.error("Could not write {}", f, ex);
        }
    }
}
