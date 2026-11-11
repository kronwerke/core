package de.kronwerke.core.lock;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import de.kronwerke.core.KronwerkeCore;
import de.kronwerke.core.config.KronwerkeConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Stage locks for every machine, not only the crafting table. Chapters stops a player from
 * crafting a locked item by hand, but a mixer, an autocrafter, a ritual or any other machine
 * made it anyway. Now every recipe whose result belongs to a stage the server has not opened
 * yet is held back when the recipes load: no machine, ritual or autocrafter finds it, and JEI
 * (which hid them already) shows nothing new. When a stage opens, the recipes load again and
 * the stage's recipes are back.
 * <p>
 * Which stage an item belongs to comes from the stage files the pack ships for Chapters
 * (data/kronwerke/chapters/stages/stageN.json). The open stage is kept in
 * kronwerke/stage.txt, because the recipes load before the world and its goals do.
 * <p>
 * So nobody wonders why something does not work: when a crafting grid, a machine or a ritual
 * looks for a recipe and only a held back one would have fit, the player who did it, or the
 * players next to the machine, are told which stage it needs.
 */
public final class MachineLocks {

    private static final Path STAGE_FILE = FMLPaths.GAMEDIR.get().resolve("kronwerke").resolve("stage.txt");

    private record Held(RecipeHolder<?> holder, int stage) {
    }

    /** Held back recipes by type, with the stage each needs, for the messages. */
    private static volatile Map<RecipeType<?>, List<Held>> held = Map.of();
    private static volatile int appliedStage = -1;
    private static int heldCount;
    /** Every recipe of the last real load, before holding back, and the ones with a stage: a new stage filters these again instead of reloading. */
    private static volatile List<RecipeHolder<?>> loadedAll = List.of();
    private static volatile List<Held> staged = List.of();
    /** The listeners mods add to a data reload (they clear their recipe caches there), from the last reload. */
    private static volatile List<net.minecraft.server.packs.resources.PreparableReloadListener> modListeners = List.of();
    private static final Map<Long, Long> toldAt = new HashMap<>();
    private static final Map<UUID, Long> toldPlayerAt = new HashMap<>();
    /** When a machine's misses were last compared with the held back recipes: at most every two seconds. */
    private static final Map<Long, Long> scannedAt = new HashMap<>();

    private MachineLocks() {
    }

    public static boolean on() {
        // a singleplayer world has every stage open: nothing is held back there
        if (de.kronwerke.core.single.Singleplayer.ON) return false;
        return KronwerkeConfig.SPEC.isLoaded() && KronwerkeConfig.MACHINE_LOCKS.get();
    }

    public static int appliedStage() {
        return appliedStage;
    }

    public static int heldCount() {
        return heldCount;
    }

    // ---- the stage the recipes were loaded for ----

    public static int readStage() {
        try {
            return Math.max(1, Integer.parseInt(Files.readString(STAGE_FILE).trim())); // as many stages as the goals open
        } catch (IOException | NumberFormatException e) {
            return 1;
        }
    }

    public static void writeStage(int stage) {
        try {
            Files.createDirectories(STAGE_FILE.getParent());
            Files.writeString(STAGE_FILE, Integer.toString(stage));
        } catch (IOException e) {
            KronwerkeCore.LOGGER.warn("Could not keep the open stage in {}: {}", STAGE_FILE, e.toString());
        }
    }

    // ---- loading ----

    /** The stage of every item the stage files list (the lowest stage that lists it). */
    static Map<ResourceLocation, Integer> itemStages(ResourceManager rm) {
        Map<ResourceLocation, Integer> out = new HashMap<>();
        for (Map.Entry<ResourceLocation, Resource> e : rm.listResources("chapters/stages", id -> id.getPath().endsWith(".json")).entrySet()) {
            String file = e.getKey().getPath();
            String name = file.substring(file.lastIndexOf('/') + 1, file.length() - 5);
            if (!name.startsWith("stage")) continue;
            int stage;
            try {
                stage = Integer.parseInt(name.substring(5));
            } catch (NumberFormatException ex) {
                continue;
            }
            try (Reader r = e.getValue().openAsReader()) {
                JsonElement root = JsonParser.parseReader(r);
                if (!(root instanceof JsonObject o) || !o.has("items")) continue;
                for (JsonElement it : o.getAsJsonArray("items")) {
                    ResourceLocation id = ResourceLocation.tryParse(it.getAsString());
                    if (id != null) out.merge(id, stage, Math::min);
                }
            } catch (IOException | RuntimeException ex) {
                KronwerkeCore.LOGGER.warn("Stage file {} could not be read: {}", e.getKey(), ex.toString());
            }
        }
        return out;
    }

    /**
     * Called as the server's recipes load: answers the recipes that stay, keeps the others for
     * the messages. With the locks off, or without stage files, everything stays.
     */
    public static Collection<RecipeHolder<?>> filter(Collection<RecipeHolder<?>> all, ResourceManager rm, HolderLookup.Provider registries,
                                                     Map<ResourceLocation, JsonElement> json) {
        held = Map.of();
        heldCount = 0;
        loadedAll = List.of();
        staged = List.of();
        if (!on()) {
            appliedStage = -1;
            return all;
        }
        Map<ResourceLocation, Integer> stages = itemStages(rm);
        int open = readStage();
        appliedStage = open;
        if (stages.isEmpty()) return all;
        List<RecipeHolder<?>> keep = new ArrayList<>(all.size());
        Map<RecipeType<?>, List<Held>> back = new IdentityHashMap<>();
        Map<ResourceLocation, Integer> fluids = fluidStages(stages);
        List<Held> withStage = new ArrayList<>();
        for (RecipeHolder<?> h : all) {
            Integer need = stageOf(h.value(), registries, stages);
            // a result that is no item (a fluid, a chemical) or that the recipe only knows from
            // its input: the recipe file names it. An open item result keeps the recipe, even
            // when a chance byproduct is of a later stage.
            if (need == null && json != null) need = stageOfJson(json.get(h.id()), stages, fluids);
            if (need != null && need == OPEN) need = null;
            if (need != null) withStage.add(new Held(h, need));
            if (need != null && need > open) {
                back.computeIfAbsent(h.value().getType(), t -> new ArrayList<>()).add(new Held(h, need));
                heldCount++;
            } else {
                keep.add(h);
            }
        }
        held = back;
        loadedAll = new ArrayList<>(all); // all is the manager's own view, which changes with replaceRecipes
        staged = withStage;
        KronwerkeCore.LOGGER.info("Stage {} is open: {} recipes of later stages are held back from every machine and crafting grid", open, heldCount);
        return keep;
    }

    /**
     * Clients always get every recipe, the held back ones too: JEI and the recipe book show
     * them as before (Chapters hides the locked items), and a new stage needs nothing new on
     * the client. Before, the whole recipe set went out again and every client's JEI rebuilt,
     * which froze the game for everyone for several seconds (review CORE-39).
     */
    public static java.util.Collection<RecipeHolder<?>> forClients(java.util.Collection<RecipeHolder<?>> sent) {
        Map<RecipeType<?>, List<Held>> h = held;
        if (h.isEmpty()) return sent;
        List<RecipeHolder<?>> out = new ArrayList<>(sent.size() + heldCount);
        out.addAll(sent);
        for (List<Held> l : h.values()) for (Held x : l) out.add(x.holder());
        return out;
    }

    /** Kept from the newest data reload; see {@link #reopen}. */
    public static void onAddReloadListeners(net.neoforged.neoforge.event.AddReloadListenerEvent e) {
        List<net.minecraft.server.packs.resources.PreparableReloadListener> keep = new ArrayList<>();
        for (var l : e.getListeners()) {
            String name = nameOf(l);
            for (String p : RECIPE_CACHES) {
                if (name.startsWith(p)) {
                    keep.add(l);
                    break;
                }
            }
        }
        modListeners = keep;
    }

    /**
     * The reload listeners that only forget a mod's own recipe list, by class name. Only these
     * run on a new stage: a data listener (Apotheosis' affixes and elites, Silent Gear's parts)
     * that runs outside a real reload loses its registry context and loads nothing, so running
     * every listener empties those mods (seen in a test on 11.11.). Mods that keep recipes and
     * forget them on a tag update instead are in {@link #forgetOnTags}.
     */
    private static final String[] RECIPE_CACHES = {
            "mekanism.common.ReloadListener",
            "com.simibubi.create.foundation.recipe.RecipeFinder",
            "com.simibubi.create.foundation.recipe.trie.RecipeTrieFinder",
            "com.simibubi.create.content.kinetics.belt.BeltHelper",
            "me.desht.pneumaticcraft.common.recipes.PneumaticCraftRecipeType$CacheReloadListener",
            "vazkii.botania.common.handler.BotaniaRecipeIngredientsCache",
            "vazkii.botania.common.block.block_entity.CraftyCrateBlockEntity",
            "xfacthd.framedblocks.common.crafting.FramingSawRecipeCache",
            "plus.dragons.createcentralkitchen.common.packager.UnpackingRecipeCache",
            "plus.dragons.createenchantmentindustry.integration.apothic_enchanting.common.processing.infuser.InfuserBlockEntity",
            "org.violetmoon.zeta.util.handler.RecipeCrawlHandler",
            "com.aetherteam.aether.data.ReloadListeners$RecipeReloadListener",
            "com.hollingsworth.arsnouveau.common.event.EventHandler",
    };

    /** Mods that count reloads by tag updates and build their recipe lists again when the count moved (Immersive Engineering). */
    private static void forgetOnTags() {
        try {
            Class.forName("blusunrize.immersiveengineering.api.crafting.cache.CachedRecipeList")
                    .getMethod("onTagsUpdated", net.neoforged.neoforge.event.TagsUpdatedEvent.class).invoke(null, (Object) null);
        } catch (ClassNotFoundException e) {
            // not in the pack
        } catch (ReflectiveOperationException | RuntimeException e) {
            KronwerkeCore.LOGGER.warn("Immersive Engineering's recipe lists could not be renewed: {}", e.toString());
        }
    }

    /** The class of a mod's listener, behind NeoForge's wrapper. */
    static String nameOf(net.minecraft.server.packs.resources.PreparableReloadListener l) {
        try {
            java.lang.reflect.Field f = l.getClass().getDeclaredField("wrapped");
            f.setAccessible(true);
            Object w = f.get(l);
            if (w != null) return w.getClass().getName();
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // not NeoForge's wrapper
        }
        return l.getClass().getName();
    }

    /**
     * A new stage without reloading the data: the recipes of the last load are filtered again
     * for the stage and handed to the recipe manager, which takes milliseconds instead of a
     * reload of every data pack (5 to 6 seconds with the server standing still). The reload
     * listeners of the mods that keep their own recipe lists (Mekanism, Create and others, see
     * {@link #RECIPE_CACHES}) run after it, off the server thread where they can. Clients
     * already have every recipe and get nothing. Answers false when there is nothing to filter
     * (the locks were off at the last load): then the full reload is the way.
     */
    public static boolean reopen(MinecraftServer server, int stage) {
        List<RecipeHolder<?>> all = loadedAll;
        if (all.isEmpty()) return false;
        long t0 = System.nanoTime();
        java.util.Set<RecipeHolder<?>> back = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        Map<RecipeType<?>, List<Held>> byType = new IdentityHashMap<>();
        for (Held h : staged) {
            if (h.stage() <= stage) continue;
            back.add(h.holder());
            byType.computeIfAbsent(h.holder().value().getType(), x -> new ArrayList<>()).add(h);
        }
        List<RecipeHolder<?>> keep = new ArrayList<>(all.size() - back.size());
        for (RecipeHolder<?> h : all) if (!back.contains(h)) keep.add(h);
        server.getRecipeManager().replaceRecipes(keep);
        forgetOnTags();
        held = byType;
        heldCount = back.size();
        appliedStage = stage;
        long ms = (System.nanoTime() - t0) / 1_000_000;
        KronwerkeCore.LOGGER.info("Stage {} is open: recipes filtered again in {} ms, {} held back", stage, ms, heldCount);
        List<net.minecraft.server.packs.resources.PreparableReloadListener> mods = modListeners;
        if (!mods.isEmpty()) {
            long t1 = System.nanoTime();
            // started from a worker thread like a real reload: some listeners wait in reload()
            // for work they hand to the server thread, which would never come if they ran on it
            java.util.concurrent.CompletableFuture.supplyAsync(() -> net.minecraft.server.packs.resources.SimpleReloadInstance.of(server.getResourceManager(), mods,
                            net.minecraft.Util.backgroundExecutor(), server, java.util.concurrent.CompletableFuture.completedFuture(net.minecraft.util.Unit.INSTANCE)).done(),
                    net.minecraft.Util.backgroundExecutor())
                    .thenCompose(f -> f)
                    .whenComplete((v, err) -> {
                        if (err != null) KronwerkeCore.LOGGER.warn("The mods' reload after stage {} failed: {}", stage, err.toString());
                        else KronwerkeCore.LOGGER.info("The mods' recipe caches are new for stage {} ({} listeners, {} ms)", stage, mods.size(), (System.nanoTime() - t1) / 1_000_000);
                    });
        }
        return true;
    }

    /**
     * The stage of fluids and chemicals: a fluid whose bucket is locked is locked with it, and
     * locks.fluids names the rest (Mekanism's chemicals have no bucket), as id=stage.
     */
    static Map<ResourceLocation, Integer> fluidStages(Map<ResourceLocation, Integer> items) {
        Map<ResourceLocation, Integer> out = new HashMap<>();
        for (var f : BuiltInRegistries.FLUID.entrySet()) {
            var bucket = f.getValue().getBucket();
            if (bucket == null || bucket == net.minecraft.world.item.Items.AIR) continue;
            Integer st = items.get(BuiltInRegistries.ITEM.getKey(bucket));
            if (st != null) out.put(f.getKey().location(), st);
        }
        for (String e : de.kronwerke.core.config.KronwerkeConfig.LOCKED_FLUIDS.get()) {
            int i = e.indexOf('=');
            if (i <= 0) continue;
            ResourceLocation id = ResourceLocation.tryParse(e.substring(0, i).trim());
            try {
                if (id != null) out.merge(id, Integer.parseInt(e.substring(i + 1).trim()), Math::min);
            } catch (NumberFormatException ex) {
                KronwerkeCore.LOGGER.warn("locks.fluids: {} is not id=stage", e);
            }
        }
        return out;
    }

    /** The highest stage among the outputs a recipe file names (keys with result or output in them). */
    static Integer stageOfJson(JsonElement recipe, Map<ResourceLocation, Integer> items, Map<ResourceLocation, Integer> fluids) {
        if (!(recipe instanceof JsonObject o)) return null;
        Integer best = null;
        for (var e : o.entrySet()) {
            String k = e.getKey().toLowerCase(java.util.Locale.ROOT);
            if (!k.contains("result") && !k.contains("output")) continue;
            Integer s = outputs(e.getValue(), items, fluids);
            if (s != null && (best == null || s > best)) best = s;
        }
        return best;
    }

    private static Integer outputs(JsonElement el, Map<ResourceLocation, Integer> items, Map<ResourceLocation, Integer> fluids) {
        Integer best = null;
        if (el instanceof JsonPrimitive p && p.isString()) {
            ResourceLocation id = ResourceLocation.tryParse(p.getAsString());
            if (id != null) best = items.containsKey(id) ? items.get(id) : fluids.get(id);
        } else if (el instanceof JsonObject o) {
            for (String key : new String[]{"id", "item", "fluid", "chemical", "gas", "infuse_type", "pigment", "slurry"}) {
                if (o.get(key) instanceof JsonPrimitive p && p.isString()) {
                    ResourceLocation id = ResourceLocation.tryParse(p.getAsString());
                    Integer s = id == null ? null : items.containsKey(id) ? items.get(id) : fluids.get(id);
                    if (s != null && (best == null || s > best)) best = s;
                }
            }
            for (var e : o.entrySet()) {
                if (e.getValue().isJsonObject() || e.getValue().isJsonArray()) {
                    Integer s = outputs(e.getValue(), items, fluids);
                    if (s != null && (best == null || s > best)) best = s;
                }
            }
        } else if (el instanceof com.google.gson.JsonArray a) {
            for (JsonElement x : a) {
                Integer s = outputs(x, items, fluids);
                if (s != null && (best == null || s > best)) best = s;
            }
        }
        return best;
    }

    private static Integer stageOf(Recipe<?> recipe, HolderLookup.Provider registries, Map<ResourceLocation, Integer> stages) {
        ItemStack result;
        try {
            result = recipe.getResultItem(registries);
        } catch (RuntimeException e) {
            return null; // some recipes only know their result from an input
        }
        if (result == null || result.isEmpty()) return null;
        Integer st = stages.get(BuiltInRegistries.ITEM.getKey(result.getItem()));
        return st == null ? OPEN : st;
    }

    /** stageOf for a recipe whose item result no stage locks. */
    private static final int OPEN = 0;

    // ---- stage changes ----

    /** Every few seconds: when another stage is open than the recipes were loaded for, they load again. */
    public static void tick(MinecraftServer server, int currentStage) {
        if (!on() || appliedStage < 0 || currentStage == appliedStage) return;
        // not in the middle of the rite that opened the stage
        if (de.kronwerke.core.obelisk.ObeliskRite.running()) return;
        writeStage(currentStage);
        if (reopen(server, currentStage)) return;
        KronwerkeCore.LOGGER.info("Stage {} is open now (the recipes were loaded for stage {}); the recipes load again", currentStage, appliedStage);
        appliedStage = currentStage; // once, even while the reload runs
        // the whole recipe set goes out again: the server holds a few seconds and every client's
        // recipe viewer rebuilds; players hear it first instead of wondering about the freeze
        server.getPlayerList().broadcastSystemMessage(de.kronwerke.core.Text.t("locks.reload",
                "Die Rezepte von Stufe %s werden geladen. Das dauert ein paar Sekunden, danach baut sich JEI neu auf.", currentStage)
                .withStyle(net.minecraft.ChatFormatting.GRAY), false);
        server.reloadResources(server.getPackRepository().getSelectedIds()).exceptionally(t -> {
            KronwerkeCore.LOGGER.warn("Reloading the recipes for stage {} failed: {}", currentStage, t.toString());
            return null;
        });
    }

    // ---- telling the player ----

    /** A crafting grid, machine or ritual found no recipe: was it a held back one, and whom to tell. */
    /**
     * A crafting grid, machine or ritual found no recipe. Answers a held back recipe to use after
     * all when a player does it who already has its stage (an admin with the bypass), and tells
     * otherwise, when a held back recipe would have fit, who did it or who stands next to the
     * machine which stage it needs.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <I extends RecipeInput> RecipeHolder<?> missed(RecipeType<?> type, I input, Level level) {
        if (!(level instanceof ServerLevel sl) || held.isEmpty() || input.isEmpty()) return null;
        ServerPlayer actor = ActingContext.player();
        BlockPos at = actor == null ? ActingContext.blockEntity() : null;
        if (actor == null && at == null) return null;
        long now = System.currentTimeMillis();
        if (actor != null) {
            Long last = toldPlayerAt.get(actor.getUUID());
            if (last != null && now - last < 3000 && !hasAnyLaterStage(actor)) return null;
        } else {
            Long last = toldAt.get(at.asLong());
            if (last != null && now - last < 10_000) return null;
            Long scanned = scannedAt.put(at.asLong(), now);
            if (scanned != null && now - scanned < 2000) return null;
            if (scannedAt.size() > 8192) scannedAt.clear();
        }
        List<Held> list = held.get(type);
        if (list == null) return null;
        Held hit = null;
        for (Held h : list) {
            try {
                if (((Recipe) h.holder().value()).matches(input, level)) {
                    hit = h;
                    break;
                }
            } catch (RuntimeException ignored) {
                // a recipe that cannot judge this input
            }
        }
        if (hit == null) return null;
        if (actor != null && hasStage(actor, hit.stage())) return hit.holder(); // the bypass: crafts as if open
        Component what = resultName(hit, sl);
        Component msg = message(hit.stage(), actor != null && ActingContext.inGrid(), what);
        if (actor != null) {
            tellActor(actor, msg, now);
        } else {
            toldAt.put(at.asLong(), now);
            if (toldAt.size() > 4096) toldAt.clear();
            // named, and only for who stands at it: a hall of machines must not talk to everyone
            Component machine = sl.getBlockState(at).getBlock().getName().copy().withStyle(ChatFormatting.WHITE);
            Component named = Component.translatableWithFallback("kronwerke.machinelock.named", "%s: %s", machine, msg).withStyle(ChatFormatting.GRAY);
            de.kronwerke.core.net.LockHintPayload hint = new de.kronwerke.core.net.LockHintPayload(at, false, msg);
            for (ServerPlayer p : sl.players()) {
                if (p.blockPosition().distSqr(at) > 8 * 8) continue;
                // over the machine's own window or floating over the block; the action bar without Core
                if (p.connection.hasChannel(de.kronwerke.core.net.LockHintPayload.TYPE)) net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p, hint);
                else p.displayClientMessage(named, true);
            }
        }
        return null;
    }

    private static void tellActor(ServerPlayer actor, Component msg, long now) {
        toldPlayerAt.put(actor.getUUID(), now);
        // over the window the player works in (a machine, a crafting grid); the action bar
        // when no window is open (an altar, a ritual) or the client has no Core
        boolean window = actor.containerMenu != actor.inventoryMenu || ActingContext.inGrid();
        if (window && actor.connection.hasChannel(de.kronwerke.core.net.LockHintPayload.TYPE)) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(actor, new de.kronwerke.core.net.LockHintPayload(BlockPos.ZERO, true, msg));
        } else {
            actor.displayClientMessage(msg, true);
        }
    }

    /**
     * A machine with its own recipe list (Mekanism) refused an input a player put in: when a
     * held back recipe of that machine would have taken it, the player hears which stage it
     * needs. Only for a player's click, never for pipes and hoppers, and at most every three
     * seconds per player.
     */
    public static void missedInput(RecipeType<?> type, Level level, java.util.function.Predicate<Object> fits) {
        ServerPlayer actor = ActingContext.player();
        if (actor == null || !(level instanceof ServerLevel sl) || held.isEmpty()) return;
        long now = System.currentTimeMillis();
        Long last = toldPlayerAt.get(actor.getUUID());
        if (last != null && now - last < 3000) return;
        List<Held> list = held.get(type);
        if (list == null) return;
        for (Held h : list) {
            boolean hit;
            try {
                hit = fits.test(h.holder().value());
            } catch (RuntimeException e) {
                hit = false;
            }
            if (!hit || hasStage(actor, h.stage())) continue;
            tellActor(actor, message(h.stage(), false, resultName(h, sl)), now);
            return;
        }
    }

    private static final boolean CHAPTERS = net.neoforged.fml.ModList.get().isLoaded("chapters");

    /** Whether the player has the stage already (Chapters' stages; the admin bypass gives them all). */
    static boolean hasStage(ServerPlayer p, int stage) {
        if (!CHAPTERS) return false;
        try {
            return com.gabinx.chapters.compat.ftb.EffectiveStages.snapshot(p).has(ResourceLocation.fromNamespaceAndPath("kronwerke", "stage" + stage));
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    private static boolean hasAnyLaterStage(ServerPlayer p) {
        return hasStage(p, appliedStage + 1);
    }

    private static Component message(int need, boolean grid, Component what) {
        Component stage = LockedItems.stageName(ResourceLocation.fromNamespaceAndPath("kronwerke", "stage" + need))
                .copy().withStyle(ChatFormatting.YELLOW);
        if (what != null) {
            return grid
                    ? Component.translatableWithFallback("kronwerke.machinelock.grid_item", "%s kannst du erst ab %s herstellen.", what, stage).withStyle(ChatFormatting.GRAY)
                    : Component.translatableWithFallback("kronwerke.machinelock.machine_item", "%s gibt es hier erst ab %s.", what, stage).withStyle(ChatFormatting.GRAY);
        }
        return grid
                ? Component.translatableWithFallback("kronwerke.machinelock.grid", "Das kannst du erst ab %s herstellen.", stage).withStyle(ChatFormatting.GRAY)
                : Component.translatableWithFallback("kronwerke.machinelock.machine", "Diese Maschine stellt das erst ab %s her.", stage).withStyle(ChatFormatting.GRAY);
    }

    /** The name of what the held back recipe makes, or null when it makes no item (fluids, chemicals). */
    @SuppressWarnings({"rawtypes"})
    private static Component resultName(Held hit, ServerLevel level) {
        try {
            net.minecraft.world.item.ItemStack out = ((Recipe) hit.holder().value()).getResultItem(level.registryAccess());
            return out.isEmpty() ? null : out.getHoverName().copy().withStyle(ChatFormatting.WHITE);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
