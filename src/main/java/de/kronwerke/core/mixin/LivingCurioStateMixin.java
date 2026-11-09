package de.kronwerke.core.mixin;

import de.kronwerke.core.perf.CurioState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Gives every living entity the two fields EmptyCurios keeps; nothing is saved. */
@Mixin(LivingEntity.class)
public abstract class LivingCurioStateMixin implements CurioState {

    @Unique
    private int kronwerke$curioAt = -1;
    @Unique
    private boolean kronwerke$curioNone;

    @Override
    public int kronwerke$curioCheckedAt() {
        return kronwerke$curioAt;
    }

    @Override
    public boolean kronwerke$curioEmpty() {
        return kronwerke$curioNone;
    }

    @Override
    public void kronwerke$setCurioState(int checkedAt, boolean empty) {
        kronwerke$curioAt = checkedAt;
        kronwerke$curioNone = empty;
    }
}
