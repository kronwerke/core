package de.kronwerke.core.loadtest;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * A player without a client, moved by the server. Every bot starts at the world spawn and
 * moves only into chunks the server already has, like a real client, which cannot fly
 * into chunks it has not been sent: a bot that outruns the chunk generation waits at the
 * edge, and how often that happens is part of the measurement.
 *
 * A settler flies to its spot and stands there on the ground, like a player in a base; a
 * traveller crosses the test area at elytra speed and turns back at its edge.
 */
public final class LoadBot extends ServerPlayer {
    final boolean traveller;
    final double centerX, centerZ, radius, speed, flyY, targetX, targetZ;
    private double heading;
    private boolean arrived;
    long waited; // ticks spent at the edge of the loaded world

    LoadBot(MinecraftServer server, ServerLevel level, GameProfile profile, boolean traveller,
            double centerX, double centerZ, double radius, double speed, double flyY, double targetX, double targetZ) {
        super(server, level, profile, ClientInformation.createDefault());
        this.traveller = traveller;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.radius = radius;
        this.speed = speed;
        this.flyY = flyY;
        this.targetX = targetX;
        this.targetZ = targetZ;
        this.heading = RandomSource.create().nextDouble() * Math.PI * 2;
    }

    @Override
    public void tick() {
        if (traveller) wander();
        else if (!arrived) approach();
        else if (tickCount % 20 == 0) serverLevel().getChunkSource().move(this);
        super.tick();
        doTick();
    }

    private void approach() {
        double dx = targetX - getX(), dz = targetZ - getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist < speed + 0.5) {
            LevelChunk chunk = chunkAt(targetX, targetZ);
            if (chunk == null || !ready(targetX, targetZ)) {
                waited++;
                serverLevel().getChunkSource().move(this);
                return;
            }
            int x = Mth.floor(targetX), z = Mth.floor(targetZ);
            int y = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, x & 15, z & 15) + 1;
            if (serverLevel().dimensionType().hasCeiling()) y = floor(chunk, x, z); // the Nether: on a cave floor, not on the roof
            step(targetX, y, targetZ);
            arrived = true;
            return;
        }
        heading = Math.atan2(dz, dx);
        step(getX() + dx / dist * speed, flyY, getZ() + dz / dist * speed);
    }

    /** The highest floor under the ceiling with room for a player, or the middle when there is none. */
    private static int floor(LevelChunk chunk, int x, int z) {
        var pos = new net.minecraft.core.BlockPos.MutableBlockPos(x, 0, z);
        for (int y = 118; y > chunk.getMinBuildHeight() + 2; y--) {
            pos.setY(y);
            if (!chunk.getBlockState(pos).isAir()) continue;
            pos.setY(y + 1);
            if (!chunk.getBlockState(pos).isAir()) continue;
            pos.setY(y - 1);
            if (chunk.getBlockState(pos).isSolid()) return y;
        }
        return 64;
    }

    private void wander() {
        RandomSource random = getRandom();
        double dx = getX() - centerX, dz = getZ() - centerZ;
        if (dx * dx + dz * dz > radius * radius) {
            // back towards the middle, with some spread so travellers do not share a line
            heading = Math.atan2(-dz, -dx) + (random.nextDouble() - 0.5) * 1.2;
        } else if (random.nextInt(200) == 0) {
            heading += (random.nextDouble() - 0.5) * 1.5;
        }
        step(getX() + Math.cos(heading) * speed, flyY, getZ() + Math.sin(heading) * speed);
    }

    /**
     * Moves there if every chunk within two chunks of it is loaded; otherwise waits and
     * lets the server load them. The margin matters: some mods look at the blocks around a
     * player every second (Malum's weeping well check reaches up to two chunks out) and load
     * a missing chunk on the server thread.
     */
    private void step(double x, double y, double z) {
        if (!ready(x, z)) {
            waited++;
            serverLevel().getChunkSource().move(this);
            return;
        }
        setYRot((float) (Mth.RAD_TO_DEG * heading) - 90f);
        double vx = x - getX(), vz = z - getZ();
        absMoveTo(x, y, z);
        setDeltaMovement(vx, 0, vz);
        serverLevel().getChunkSource().move(this);
    }

    private boolean ready(double x, double z) {
        int cx = Mth.floor(x) >> 4, cz = Mth.floor(z) >> 4;
        for (int i = -2; i <= 2; i++) {
            for (int j = -2; j <= 2; j++) {
                if (serverLevel().getChunkSource().getChunkNow(cx + i, cz + j) == null) return false;
            }
        }
        return true;
    }

    private LevelChunk chunkAt(double x, double z) {
        return serverLevel().getChunkSource().getChunkNow(Mth.floor(x) >> 4, Mth.floor(z) >> 4);
    }
}
