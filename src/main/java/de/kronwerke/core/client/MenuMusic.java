package de.kronwerke.core.client;

import de.kronwerke.core.config.KronwerkeClientConfig;
import de.kronwerke.core.mixin.MusicManagerAccessor;
import de.kronwerke.core.obelisk.KwSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.SelectMusicEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The music of the menus: one quiet piece (tools/sounds/menu.py) that plays on the title
 * screen, in the menus outside a world and while the game connects, on under the join picture,
 * and fades away when the world is there. It takes the place of the game's own menu music.
 *
 * <p>It is a sound of its own and not a track of the game's music manager, because that one
 * can only start and stop: a piece that is cut off the moment the world appears is exactly
 * the kind of music that annoys. This one comes in over two seconds and leaves over four. It
 * loops without a seam and follows the Music slider. The client's setting menuMusic turns it
 * off, and the game's own menu music is back.
 *
 * <p>The piece is not streamed (sounds.json): a streamed sound is fed by the game's ticks, and
 * there are none for seconds on end while a world loads or JEI builds its recipes, so it
 * would run dry and stop in the middle of the join. Held in memory whole (about 20 MB), the
 * sound card's own thread plays it on whatever the game is busy with.
 */
public final class MenuMusic {
    private static final float LEVEL = 0.7f;
    private static final int FADE_IN = 40, FADE_OUT = 80;
    private static Track track;
    private static boolean handedOver = true;
    private static int retryIn;

    private MenuMusic() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> tick(Minecraft.getInstance()));
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, MenuMusic::select);
    }

    /** Outside a world, and on for as long as the join picture stands whole. Not on a move between two servers of the network. */
    static boolean wanted(Minecraft mc) {
        if (!KronwerkeClientConfig.menuMusic()) return false;
        // with the music or everything turned down to nothing the game throws every such sound out again at once
        if (mc.options.getSoundSourceVolume(SoundSource.MUSIC) <= 0f || mc.options.getSoundSourceVolume(SoundSource.MASTER) <= 0f) return false;
        if (JoinOverlay.whole()) return true;
        return mc.level == null && !TravelOverlay.active() && !TravelOverlay.moving();
    }

    private static void tick(Minecraft mc) {
        boolean want = wanted(mc);
        if (want) {
            handedOver = false;
            if (track == null || track.isStopped() || !mc.getSoundManager().isActive(track)) {
                // a new one after the first frame, after the sounds were loaded again, after it had faded away;
                // not more often than every two seconds, for a game whose sound does not run at all
                if (retryIn > 0) {
                    retryIn--;
                    return;
                }
                retryIn = 40;
                if (track != null) track.end();
                track = new Track();
                mc.getSoundManager().play(track);
            }
            track.up = true;
        } else if (track != null) {
            track.up = false;
            if (track.isStopped() || !mc.getSoundManager().isActive(track)) track = null;
        }
        if (!want && !handedOver && mc.level != null) {
            // the world is there: the game's own music waits five to ten minutes instead of starting at once
            handedOver = true;
            ((MusicManagerAccessor) mc.getMusicManager()).kronwerke$nextSongDelay(6000 + mc.level.random.nextInt(6000));
        }
    }

    /** The game's own menu music stays silent while this one is on. */
    private static void select(SelectMusicEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null && KronwerkeClientConfig.menuMusic()) e.overrideMusic(null);
    }

    /** MenuMusicKeepMixin: the game is about to stop every sound; with no world there is only this music to stop, and it stays. */
    public static boolean keepsPlaying(Minecraft mc) {
        return mc.level == null && track != null && !track.isStopped() && KronwerkeClientConfig.menuMusic();
    }

    /** For the probe: "off", or how loud it is right now (0..1 of its own level). */
    public static String state() {
        Track t = track;
        if (t == null || t.isStopped()) return "off";
        return String.format(java.util.Locale.ROOT, "%.2f%s", t.gain, Minecraft.getInstance().getSoundManager().isActive(t) ? "" : " (not playing)");
    }

    private static final class Track extends AbstractTickableSoundInstance {
        boolean up = true;
        float gain;

        Track() {
            super(KwSounds.MENU_MUSIC.get(), SoundSource.MUSIC, SoundInstance.createUnseededRandom());
            this.looping = true;
            this.delay = 0;
            this.relative = true;
            this.attenuation = Attenuation.NONE;
            this.volume = 0.001f;
        }

        @Override
        public void tick() {
            gain = up ? Math.min(1f, gain + 1f / FADE_IN) : gain - 1f / FADE_OUT;
            if (gain <= 0f) {
                stop();
                return;
            }
            // equal steps of the fader are not equal steps to the ear: square it
            this.volume = Math.max(0.001f, LEVEL * gain * gain);
        }

        void end() {
            stop();
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }
    }
}
