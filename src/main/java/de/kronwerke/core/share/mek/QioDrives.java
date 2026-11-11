package de.kronwerke.core.share.mek;

import mekanism.api.Action;
import mekanism.common.content.qio.IQIODriveHolder;
import mekanism.common.content.qio.QIODriveData;
import mekanism.common.content.qio.QIOFrequency;
import mekanism.common.inventory.slot.QIODriveSlot;
import mekanism.common.lib.frequency.TileComponentFrequency;
import mekanism.common.lib.inventory.HashedItem;
import mekanism.common.registries.MekanismItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * The two drives Core puts into every QIO frequency, so that a frequency is one storage on
 * every server of the network (see {@link QioLayer}). Neither stands in the world: their
 * holder is this class, not a drive array.
 * <ul>
 * <li>The mirror shows what the frequency holds on the other servers. Nothing can be put into
 * it, and taking from it gives nothing at once but asks the server that has the things to
 * send them.</li>
 * <li>The transit is a small real drive: what was asked for lands here, and what is put into
 * the frequency on a server without room in real drives waits here until a server with room
 * takes it.</li>
 * </ul>
 */
public final class QioDrives {
    private QioDrives() {
    }

    /** Called by QioDriveNewMixin where Mekanism makes the data of a drive it was given. */
    public static QIODriveData create(QIODriveData.QIODriveKey key) {
        if (!(key.holder() instanceof Holder h)) return null;
        if (key.driveSlot() == 0) {
            h.transit = new Transit(key, h);
            return h.transit;
        }
        h.mirror = new Mirror(key, h);
        return h.mirror;
    }

    public static final class Holder implements IQIODriveHolder {
        final QIOFrequency freq;
        final String key;
        final QioLayer layer;
        final ItemStack transitStack, mirrorStack;
        private final List<QIODriveSlot> slots;
        Transit transit;
        Mirror mirror;
        boolean dirty;

        Holder(QioLayer layer, QIOFrequency freq, String key, ItemStack transitStack) {
            this.layer = layer;
            this.freq = freq;
            this.key = key;
            this.transitStack = transitStack == null || transitStack.isEmpty() ? new ItemStack(MekanismItems.BASE_QIO_DRIVE.get()) : transitStack;
            this.mirrorStack = new ItemStack(MekanismItems.BASE_QIO_DRIVE.get());
            this.slots = List.of(slot(0), slot(1));
        }

        private QIODriveSlot slot(int i) {
            return new QIODriveSlot(this, i, () -> null, null, 0, 0) {
                @Override
                public ItemStack getStack() {
                    return i == 0 ? transitStack : mirrorStack;
                }
            };
        }

        @Override
        public List<QIODriveSlot> getDriveSlots() {
            return slots;
        }

        @Override
        public void onDataUpdate() {
            dirty = true;
        }

        @Override
        public void save(int slot, QIODriveData data) {
            // the mirror is other servers' things: only the transit is kept
            if (slot == 0) IQIODriveHolder.super.save(slot, data);
        }

        @Override
        public QIOFrequency getQIOFrequency() {
            return freq;
        }

        @Override
        public TileComponentFrequency getFrequencyComponent() {
            return null;
        }

        @Override
        public BlockPos getBlockPos() {
            return BlockPos.ZERO;
        }

        @Override
        public Level getLevel() {
            return null;
        }

        /** Whether a real drive of this frequency on this server can still take something. */
        boolean physicalRoom() {
            for (QIODriveData d : freq.getAllDrives()) {
                if (d == transit || d == mirror) continue;
                if (d.getTotalCount() < d.getCountCapacity()) return true;
            }
            return false;
        }

        long physicalCapacity() {
            long c = 0;
            for (QIODriveData d : freq.getAllDrives()) if (d != transit && d != mirror) c += d.getCountCapacity();
            return c;
        }

        int physicalTypes() {
            int c = 0;
            for (QIODriveData d : freq.getAllDrives()) if (d != transit && d != mirror) c += d.getTypeCapacity();
            return c;
        }

        long physicalCount() {
            long c = 0;
            for (QIODriveData d : freq.getAllDrives()) if (d != transit && d != mirror) c += d.getTotalCount();
            return c;
        }
    }

    /** A small real drive without a block; closed to Mekanism while a real drive here has room. */
    public static final class Transit extends QIODriveData {
        private final Holder holder;
        boolean open;

        Transit(QIODriveData.QIODriveKey key, Holder holder) {
            super(key);
            this.holder = holder;
        }

        @Override
        public long add(HashedItem type, long amount, Action action) {
            if (!open && holder != null && holder.physicalRoom()) return amount;
            return super.add(type, amount, action);
        }
    }

    /** What the other servers hold: shown, never filled, and asked for when something is taken. */
    public static final class Mirror extends QIODriveData {
        private final Holder holder;
        long count;
        long capacity;
        int types;

        Mirror(QIODriveData.QIODriveKey key, Holder holder) {
            super(key);
            this.holder = holder;
        }

        @Override
        public long add(HashedItem type, long amount, Action action) {
            return amount;
        }

        @Override
        public long remove(HashedItem type, long amount, Action action) {
            if (action.execute() && amount > 0 && holder != null) holder.layer.want(holder, type, amount);
            return 0;
        }

        @Override
        public long getCountCapacity() {
            return capacity;
        }

        @Override
        public int getTypeCapacity() {
            return types;
        }

        @Override
        public long getTotalCount() {
            return count;
        }

        @Override
        public int getTotalTypes() {
            return getItemMap().size();
        }
    }
}
