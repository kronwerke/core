package de.kronwerke.core.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/** A rune glyph that rises slowly, turns a little and fades, always fully lit. */
public class RuneParticle extends TextureSheetParticle {
    private final SpriteSet sprites;

    RuneParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
        super(level, x, y, z, dx, dy, dz);
        this.sprites = sprites;
        this.xd = dx;
        this.yd = dy;
        this.zd = dz;
        this.lifetime = 40 + random.nextInt(30);
        this.quadSize = 0.12f + random.nextFloat() * 0.08f;
        this.gravity = 0f;
        this.friction = 0.97f;
        this.hasPhysics = false;
        this.roll = random.nextFloat() * 0.4f - 0.2f;
        this.oRoll = roll;
        setSpriteFromAge(sprites);
        pickSprite(sprites);
        float shade = 0.85f + random.nextFloat() * 0.15f;
        setColor(shade, shade, 1f);
    }

    @Override
    public void tick() {
        super.tick();
        oRoll = roll;
        roll += 0.02f;
        float life = age / (float) lifetime;
        alpha = life < 0.2f ? life / 0.2f : 1f - (life - 0.2f) / 0.8f;
    }

    @Override
    public int getLightColor(float partialTick) {
        return 0xF000F0;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double dx, double dy, double dz) {
            return new RuneParticle(level, x, y, z, dx, dy, dz, sprites);
        }
    }
}
