package de.kronwerke.core.obelisk;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Where the obelisk stands and which containers next to it feed it, with the player each
 * feeder counts for. Feeders are always in the obelisk's dimension.
 */
public class ObeliskData extends SavedData {
    public static final String NAME = "kronwerke_obelisk";
    public static final Factory<ObeliskData> FACTORY = new Factory<>(ObeliskData::new, ObeliskData::load, null);

    private String dimension;
    private BlockPos pos;
    private final Map<BlockPos, UUID> feeders = new LinkedHashMap<>();
    /** the leaderboard wall: its anchor, the way it faces, its size */
    private BlockPos board;
    private String boardFacing = "north";
    private int boardWidth, boardHeight;
    /** blocks the tiers added around the build with what stood there before, so a rebuild can put it back */
    private final Map<BlockPos, net.minecraft.world.level.block.state.BlockState> extras = new LinkedHashMap<>();
    private int builtTier;
    /** wall clock of the last deposit, for the slumber */
    private long lastDepositAt;
    /** player -> day of the last daily offering and the streak length */
    private final Map<UUID, int[]> streaks = new HashMap<>();

    public boolean isSet() {
        return pos != null && dimension != null;
    }

    public String dimension() {
        return dimension;
    }

    public BlockPos pos() {
        return pos;
    }

    public void set(String dimension, BlockPos pos) {
        this.dimension = dimension;
        this.pos = pos.immutable();
        feeders.clear();
        extras.clear();
        builtTier = 0;
        setDirty();
    }

    public void clear() {
        dimension = null;
        pos = null;
        feeders.clear();
        setDirty();
    }

    public BlockPos board() {
        return board;
    }

    public String boardFacing() {
        return boardFacing;
    }

    public int boardWidth() {
        return boardWidth;
    }

    public int boardHeight() {
        return boardHeight;
    }

    public void setBoard(BlockPos anchor, String facing, int width, int height) {
        board = anchor == null ? null : anchor.immutable();
        boardFacing = facing;
        boardWidth = width;
        boardHeight = height;
        setDirty();
    }

    public Map<BlockPos, UUID> feeders() {
        return feeders;
    }

    public Map<BlockPos, net.minecraft.world.level.block.state.BlockState> extras() {
        return extras;
    }

    public int builtTier() {
        return builtTier;
    }

    public void addExtra(BlockPos p, net.minecraft.world.level.block.state.BlockState before) {
        extras.putIfAbsent(p.immutable(), before);
        setDirty();
    }

    public void setBuiltTier(int tier) {
        builtTier = tier;
        setDirty();
    }

    public void clearExtras() {
        extras.clear();
        builtTier = 0;
        setDirty();
    }

    public long lastDepositAt() {
        return lastDepositAt;
    }

    public void touch(long now) {
        lastDepositAt = now;
        setDirty();
    }

    /** The day number (local days since the epoch) of the player's last daily offering, or -1. */
    public int streakDay(UUID player) {
        int[] s = streaks.get(player);
        return s == null ? -1 : s[0];
    }

    public int streak(UUID player) {
        int[] s = streaks.get(player);
        return s == null ? 0 : s[1];
    }

    public void setStreak(UUID player, int day, int length) {
        streaks.put(player, new int[]{day, length});
        setDirty();
    }

    public void clearStreaks() {
        streaks.clear();
        lastDepositAt = 0;
        setDirty();
    }

    public UUID feeder(BlockPos p) {
        return feeders.get(p);
    }

    public void addFeeder(BlockPos p, UUID owner) {
        feeders.put(p.immutable(), owner);
        setDirty();
    }

    public boolean removeFeeder(BlockPos p) {
        boolean had = feeders.remove(p) != null;
        if (had) setDirty();
        return had;
    }

    public static ObeliskData load(CompoundTag tag, HolderLookup.Provider provider) {
        ObeliskData d = new ObeliskData();
        if (tag.contains("dimension")) {
            d.dimension = tag.getString("dimension");
            d.pos = BlockPos.of(tag.getLong("pos"));
        }
        ListTag list = tag.getList("feeders", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag f = list.getCompound(i);
            d.feeders.put(BlockPos.of(f.getLong("pos")), f.getUUID("owner"));
        }
        if (tag.contains("board")) {
            CompoundTag b = tag.getCompound("board");
            d.board = BlockPos.of(b.getLong("pos"));
            d.boardFacing = b.getString("facing");
            d.boardWidth = b.getInt("width");
            d.boardHeight = b.getInt("height");
        }
        ListTag ex = tag.getList("extras", Tag.TAG_COMPOUND);
        for (int i = 0; i < ex.size(); i++) {
            CompoundTag c = ex.getCompound(i);
            d.extras.put(BlockPos.of(c.getLong("pos")), net.minecraft.nbt.NbtUtils.readBlockState(
                    provider.lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK), c.getCompound("before")));
        }
        d.builtTier = tag.getInt("builtTier");
        d.lastDepositAt = tag.getLong("lastDepositAt");
        ListTag st = tag.getList("streaks", Tag.TAG_COMPOUND);
        for (int i = 0; i < st.size(); i++) {
            CompoundTag c = st.getCompound(i);
            d.streaks.put(c.getUUID("player"), new int[]{c.getInt("day"), c.getInt("length")});
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        if (isSet()) {
            tag.putString("dimension", dimension);
            tag.putLong("pos", pos.asLong());
        }
        ListTag list = new ListTag();
        feeders.forEach((p, owner) -> {
            CompoundTag f = new CompoundTag();
            f.putLong("pos", p.asLong());
            f.putUUID("owner", owner);
            list.add(f);
        });
        tag.put("feeders", list);
        if (board != null) {
            CompoundTag b = new CompoundTag();
            b.putLong("pos", board.asLong());
            b.putString("facing", boardFacing);
            b.putInt("width", boardWidth);
            b.putInt("height", boardHeight);
            tag.put("board", b);
        }
        ListTag ex = new ListTag();
        extras.forEach((p, before) -> {
            CompoundTag c = new CompoundTag();
            c.putLong("pos", p.asLong());
            c.put("before", net.minecraft.nbt.NbtUtils.writeBlockState(before));
            ex.add(c);
        });
        tag.put("extras", ex);
        tag.putInt("builtTier", builtTier);
        tag.putLong("lastDepositAt", lastDepositAt);
        ListTag st = new ListTag();
        streaks.forEach((u, v) -> {
            CompoundTag c = new CompoundTag();
            c.putUUID("player", u);
            c.putInt("day", v[0]);
            c.putInt("length", v[1]);
            st.add(c);
        });
        tag.put("streaks", st);
        return tag;
    }
}
