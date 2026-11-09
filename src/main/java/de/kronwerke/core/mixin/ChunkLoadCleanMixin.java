package de.kronwerke.core.mixin;

import de.kronwerke.core.config.KronwerkeConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla marks every chunk it reads from disk as changed (ChunkSerializer.read calls
 * setLightCorrect, which sets the flag), so every chunk a player flew through is serialized
 * and written again when it unloads, on the server thread. In the load tests that was a tenth
 * of the tick (/kw admin dirty showed 15 500 such marks in two minutes against a few hundred
 * real changes). A fully generated chunk now starts out unchanged after reading; block
 * changes, block entities, attachments and light changes still mark it as before.
 * Chunks that ask to be saved ("shouldSave") and chunks still being generated are left alone.
 */
@Mixin(ChunkSerializer.class)
public abstract class ChunkLoadCleanMixin {

    @Inject(method = "read", at = @At("RETURN"))
    private static void kronwerke$startClean(ServerLevel level, PoiManager poi, RegionStorageInfo info, ChunkPos pos, CompoundTag tag,
                                             CallbackInfoReturnable<ProtoChunk> cir) {
        if (cir.getReturnValue() instanceof ImposterProtoChunk full && !tag.getBoolean("shouldSave")
                && KronwerkeConfig.CLEAN_CHUNK_LOADS.get()) {
            full.setUnsaved(false);
        }
    }
}
