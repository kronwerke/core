package de.kronwerke.core.obelisk;

import de.kronwerke.core.config.KronwerkeConfig;
import de.kronwerke.core.goal.Goal;
import de.kronwerke.core.goal.GoalManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The obelisk at spawn, without a block of its own: any block an admin points at with
 * /kw admin obelisk set. Right click it to deposit what you hold, sneak and right click to
 * deposit everything that fits. A chest, barrel or any other container placed right next to
 * it becomes a feeder of the player who placed it: whatever a factory pipes into it goes into
 * the goal under that player's name. Everything stays server side, clients need nothing.
 */
public final class Obelisk {
    private static final Obelisk INSTANCE = new Obelisk();

    private MinecraftServer server;
    private int ticks;

    public static Obelisk get() {
        return INSTANCE;
    }

    public void init(MinecraftServer server) {
        this.server = server;
        this.ticks = 0;
    }

    public ObeliskData data() {
        return server.overworld().getDataStorage().computeIfAbsent(ObeliskData.FACTORY, ObeliskData.NAME);
    }

    private boolean isObelisk(Level level, BlockPos pos) {
        ObeliskData d = data();
        return d.isSet() && d.pos().equals(pos) && level.dimension().location().toString().equals(d.dimension());
    }

    private static int distance(BlockPos a, BlockPos b) {
        return Math.max(Math.abs(a.getX() - b.getX()), Math.max(Math.abs(a.getY() - b.getY()), Math.abs(a.getZ() - b.getZ())));
    }

    // ---- right click ----

    public void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (server == null || event.getLevel().isClientSide() || !isObelisk(event.getLevel(), event.getPos())) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getHand() != InteractionHand.MAIN_HAND || !(event.getEntity() instanceof ServerPlayer player)) return;

        GoalManager gm = GoalManager.get();
        List<Goal> active = gm.activeGoals();
        if (active.isEmpty()) {
            player.sendSystemMessage(Component.literal("The obelisk rests. No community goal is open.").withStyle(ChatFormatting.GRAY));
            return;
        }
        long taken;
        if (player.isShiftKeyDown()) {
            taken = gm.depositAll(player);
        } else {
            ItemStack held = player.getMainHandItem();
            taken = gm.deposit(player, held);
        }
        if (taken > 0) {
            player.sendSystemMessage(Component.literal("The obelisk took " + taken + ".").withStyle(ChatFormatting.GREEN));
            return;
        }
        Goal g = active.get(0);
        if (gm.isHeld(g)) {
            player.sendSystemMessage(Component.literal("The obelisk waits for the event. The last items go in together.").withStyle(ChatFormatting.LIGHT_PURPLE));
            return;
        }
        List<String> wanted = new ArrayList<>();
        for (Goal.PillarItem it : g.allItems()) {
            if (gm.progressData().progress(g.id(), it.item()) < gm.progressData().target(g.id(), it.item())) wanted.add(it.item());
        }
        player.sendSystemMessage(Component.literal("The obelisk wants: ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(String.join(", ", wanted)).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(". Sneak and right click to hand in everything that fits.").withStyle(ChatFormatting.GRAY)));
    }

    // ---- feeders ----

    public void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (server == null || !(event.getEntity() instanceof ServerPlayer player) || !(event.getLevel() instanceof ServerLevel level)) return;
        ObeliskData d = data();
        if (!d.isSet() || !level.dimension().location().toString().equals(d.dimension())) return;
        BlockPos pos = event.getPos();
        if (pos.equals(d.pos()) || distance(pos, d.pos()) > KronwerkeConfig.FEEDER_RADIUS.get()) return;
        if (level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null) == null) return;

        int max = KronwerkeConfig.FEEDERS_PER_PLAYER.get();
        long owned = d.feeders().values().stream().filter(u -> u.equals(player.getUUID())).count();
        if (owned >= max) {
            player.sendSystemMessage(Component.literal("You already have " + owned + " feeders at the obelisk. This one does not count.").withStyle(ChatFormatting.GRAY));
            return;
        }
        d.addFeeder(pos, player.getUUID());
        player.sendSystemMessage(Component.literal("This feeds the obelisk. Whatever goes in counts for you.").withStyle(ChatFormatting.GREEN));
    }

    public void onBreak(BlockEvent.BreakEvent event) {
        if (server == null || !(event.getLevel() instanceof ServerLevel level)) return;
        ObeliskData d = data();
        if (!d.isSet() || !level.dimension().location().toString().equals(d.dimension())) return;
        d.removeFeeder(event.getPos());
    }

    public void onServerTick(ServerTickEvent.Post event) {
        if (server == null) return;
        if (++ticks < KronwerkeConfig.FEEDER_INTERVAL.get() * 20) return;
        ticks = 0;
        drain();
    }

    /** Moves everything the active goal can take out of every feeder. Returns what was moved. */
    public long drain() {
        ObeliskData d = data();
        if (!d.isSet() || d.feeders().isEmpty() || GoalManager.get().activeGoals().isEmpty()) return 0;
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(d.dimension())));
        if (level == null) return 0;

        long moved = 0;
        List<BlockPos> gone = new ArrayList<>();
        for (Map.Entry<BlockPos, UUID> f : d.feeders().entrySet()) {
            BlockPos pos = f.getKey();
            if (!level.isLoaded(pos)) continue;
            IItemHandler h = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
            if (h == null) {
                gone.add(pos);
                continue;
            }
            UUID owner = f.getValue();
            Component name = Component.literal(nameOf(owner));
            Map<String, Long> delivered = new LinkedHashMap<>();
            for (int slot = 0; slot < h.getSlots(); slot++) {
                ItemStack peek = h.extractItem(slot, Integer.MAX_VALUE, true);
                if (peek.isEmpty()) continue;
                ItemStack copy = peek.copy();
                long taken = GoalManager.get().deposit(owner, name, copy, false);
                if (taken <= 0) continue;
                ItemStack out = h.extractItem(slot, (int) taken, false);
                moved += out.getCount();
                delivered.merge(out.getHoverName().getString(), (long) out.getCount(), Long::sum);
            }
            announce(name, delivered);
        }
        for (BlockPos p : gone) d.removeFeeder(p);
        return moved;
    }

    private void announce(Component name, Map<String, Long> delivered) {
        if (!KronwerkeConfig.BROADCAST_DEPOSITS.get()) return;
        delivered.forEach((item, n) -> {
            if (n < KronwerkeConfig.BROADCAST_DEPOSIT_MIN.get()) return;
            server.getPlayerList().broadcastSystemMessage(Component.literal("")
                    .append(name.copy().withStyle(ChatFormatting.WHITE))
                    .append(Component.literal("'s feeder delivered " + n + " ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(item).withStyle(ChatFormatting.AQUA)), false);
        });
    }

    public String nameOf(UUID id) {
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        if (online != null) return online.getGameProfile().getName();
        if (server.getProfileCache() != null) {
            var p = server.getProfileCache().get(id);
            if (p.isPresent()) return p.get().getName();
        }
        return id.toString();
    }
}
