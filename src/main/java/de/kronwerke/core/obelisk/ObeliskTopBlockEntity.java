package de.kronwerke.core.obelisk;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Only there so the client can draw the beam above the crystal. */
public class ObeliskTopBlockEntity extends BlockEntity {
    public ObeliskTopBlockEntity(BlockPos pos, BlockState state) {
        super(ObeliskBlocks.OBELISK_TOP_ENTITY.get(), pos, state);
    }
}
