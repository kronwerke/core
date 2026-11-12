package de.kronwerke.core.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import de.kronwerke.core.KronwerkeCore;
import de.kronwerke.core.Text;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.FogRenderer;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Pictures while the game's own loop stands still. JEI builds its recipes and its search on
 * the game's thread, in one go, inside the handling of a packet: 20 seconds on a weak machine
 * with this pack (measured on 12.11.2026), and nothing is drawn in that time, so whatever was
 * on the screen freezes and the window counts as hung. JEI has no switch for doing this on the
 * side, and its plugins are not written for another thread.
 * <p>
 * So the work stays where it is and gives the screen a turn: mixins call {@link #pulse()} from
 * inside the loops (per plugin, per recipe, per item of the search), and whenever a frame is
 * due, one is drawn right there: the join picture or the stars of a move if one of them was on
 * the screen, otherwise just the small gear in the corner over the last picture of the world.
 * The same goes for the other long steps of coming in (tags, the creative tabs, the books)
 * as long as one of the two pictures covers the screen: then a picture drawn here is the same
 * one the game would draw, and nobody can tell them apart.
 * <p>
 * The window's events are taken off the queue so that it stays alive, but keys and clicks are
 * dropped: handling them would run the game's input code in the middle of that work. The keys
 * are read anew from the keyboard afterwards, so none stays stuck.
 * <p>
 * For now only in the test client (the probe is on) or with -Dkronwerke.loadpump=true: it is
 * seen in the real test client first.
 */
public final class LoadPump {
    private static final boolean ON = Boolean.getBoolean("kronwerke.loadpump")
            || (Boolean.getBoolean("kronwerke.probe") && !"false".equals(System.getProperty("kronwerke.loadpump")));
    /** A frame at most every 33 ms: 30 a second is smooth for the gears and costs the work little. */
    private static final long EVERY_NS = 33_000_000L;

    /** JEI is starting: pictures are drawn whatever is on the screen. */
    private static volatile boolean active;
    /** The game's last own frame was one of the loading pictures, whole: pictures are drawn when it stalls. */
    private static volatile boolean covered;
    private static boolean coveredNow, inside, inFrame, broken, pumping;
    private static Thread thread;
    private static long began, last, calls, next, drawing, lastReal;
    private static int frames, outside;
    private static String stage = "";
    private static final List<Gap> gaps = new ArrayList<>();

    private record Gap(long ms, String where) {
    }

    private LoadPump() {
    }

    public static void register() {
        if (!ON) return;
        NeoForge.EVENT_BUS.addListener((RenderFrameEvent.Pre e) -> {
            inFrame = true;
            coveredNow = false;
            thread = Thread.currentThread();
            // a frame of the game's own: whatever was being pumped is over, even if it ended in an error
            if (active) end();
            if (pumping) {
                pumping = false;
                Minecraft mc = Minecraft.getInstance();
                mc.getWindow().updateVsync(mc.options.enableVsync().get());
                keys(mc);
                long ms = (System.nanoTime() - lastReal) / 1_000_000;
                if (outside > 0 && ms >= 400) {
                    KronwerkeCore.LOGGER.info("The game stood still for {} ms behind a loading picture; {} pictures drawn meanwhile", ms, outside);
                }
                outside = 0;
            }
            lastReal = System.nanoTime();
            stalled();
        });
        NeoForge.EVENT_BUS.addListener((RenderFrameEvent.Post e) -> {
            inFrame = false;
            covered = coveredNow;
        });
        KronwerkeCore.LOGGER.info("Pictures while the game stands still are on (kronwerke.loadpump)");
    }

    // ---- what else holds the game: for finding the next place that needs a turn ----

    private static volatile long lastPicture = System.nanoTime();
    private static volatile Thread game;
    private static final java.util.concurrent.ConcurrentLinkedQueue<String> seen = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private static int reported;

    /** At every frame of the game's own: says where the game was if the last picture is long ago. */
    private static void stalled() {
        long now = System.nanoTime();
        long ms = (now - lastPicture) / 1_000_000;
        lastPicture = now;
        if (game == null) {
            game = Thread.currentThread();
            Thread t = new Thread(LoadPump::watch, "Kronwerke stall watch");
            t.setDaemon(true);
            t.start();
        }
        if (ms >= 400 && reported < 60) {
            reported++;
            KronwerkeCore.LOGGER.info("No picture for {} ms; the game's thread was in: {}", ms, String.join(" || ", seen));
        }
        seen.clear();
    }

    /** Looks at the game's thread whenever no picture came for a while and notes where it is. */
    private static void watch() {
        long lastLook = 0;
        while (true) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                return;
            }
            long now = System.nanoTime();
            if (now - lastPicture < 300_000_000L || now - lastLook < 700_000_000L || seen.size() >= 8) continue;
            lastLook = now;
            StackTraceElement[] st = game.getStackTrace();
            StringBuilder b = new StringBuilder();
            int n = 0;
            for (StackTraceElement el : st) {
                String c = el.getClassName();
                if (c.startsWith("java.") || c.startsWith("jdk.") || c.startsWith("sun.") || c.startsWith("it.unimi.") || c.startsWith("com.google.")) continue;
                int dot = c.lastIndexOf('.', c.lastIndexOf('.') - 1);
                b.append(n == 0 ? "" : " < ").append(dot > 0 ? c.substring(dot + 1) : c).append('.').append(el.getMethodName());
                if (++n >= 9) break;
            }
            seen.add(b.toString());
        }
    }

    /** The overlays, while the game draws a frame: this frame is a loading picture and nothing else. */
    static void covers() {
        if (inFrame) coveredNow = true;
    }

    /** JEI's start begins (on the game's thread). */
    public static void begin() {
        if (!ON || broken || active || inFrame || !RenderSystem.isOnRenderThread()) return;
        thread = Thread.currentThread();
        began = last = System.nanoTime();
        next = began + EVERY_NS;
        drawing = 0;
        frames = 0;
        calls = 0;
        gaps.clear();
        stage = "";
        JoinOverlay.note(Text.t("load.recipes", "Lade Rezepte").getString());
        active = true;
    }

    /** JEI's start is over. */
    public static void end() {
        if (!active) return;
        active = false;
        JoinOverlay.note("");
        long ms = (System.nanoTime() - began) / 1_000_000;
        gaps.sort(Comparator.comparingLong(Gap::ms).reversed());
        StringBuilder worst = new StringBuilder();
        for (int i = 0; i < Math.min(6, gaps.size()); i++) {
            worst.append(i == 0 ? "" : ", ").append(gaps.get(i).ms()).append(" ms (").append(gaps.get(i).where()).append(')');
        }
        KronwerkeCore.LOGGER.info("JEI's start took {} ms with {} pictures drawn meanwhile in {} ms ({} chances); longest waits for a chance: {}",
                ms, frames, drawing / 1_000_000, calls, worst.isEmpty() ? "none over 100 ms" : worst);
    }

    /** JEI turns to the next plugin: "Registering recipes", "farmersdelight:jei_plugin". */
    public static void stage(String title, String plugin) {
        if (!active) return;
        stage = title + ": " + plugin;
        int colon = plugin.indexOf(':');
        String mod = colon > 0 ? plugin.substring(0, colon) : plugin;
        JoinOverlay.note(Text.t("load.recipes", "Lade Rezepte").getString() + " · " + mod);
        pulse();
    }

    /** From inside long work on the game's thread, as often as it likes: draws a picture when one is due. */
    public static void pulse() {
        if (!ON) return;
        boolean jei = active;
        if (!jei && !covered) return;
        if (inside || inFrame || broken || Thread.currentThread() != thread) return;
        long now = System.nanoTime();
        if (jei) {
            calls++;
            if (now < next) return;
            if (now - began > 180_000_000_000L) {
                end();
                return;
            }
            // how long after a picture was due the work gave the next chance: where that is long, a hook is missing
            long late = (now - next) / 1_000_000;
            if (late >= 100) gaps.add(new Gap(late, stage));
        } else if (now < next || now - lastPicture < 80_000_000L) {
            // outside JEI only when the game is really late with its own next frame
            return;
        }
        inside = true;
        try {
            frame();
            if (jei) frames++;
            else outside++;
            // where a picture is dear (no graphics card: 50 ms and more), fewer are drawn: the
            // pictures get at most a fifth of the time, the work the rest
            long took = System.nanoTime() - now;
            if (jei) drawing += took;
            next = now + Math.max(EVERY_NS, took * 5);
        } catch (Throwable t) {
            // never again in this game: a picture is not worth a crash
            broken = true;
            active = false;
            JoinOverlay.note("");
            KronwerkeCore.LOGGER.warn("Pictures while the game stands still are off after an error", t);
        } finally {
            inside = false;
            last = System.nanoTime();
            if (!broken) {
                lastPicture = last;
                seen.clear();
            }
        }
    }

    /** One picture, drawn and shown outside the game's own loop. */
    private static void frame() {
        Minecraft mc = Minecraft.getInstance();
        Window win = mc.getWindow();
        if (win.getWidth() <= 0 || win.getHeight() <= 0) return; // minimised
        long now = System.currentTimeMillis();
        if (!pumping) {
            pumping = true;
            // without this every picture would wait for the screen's next refresh, and the work with it
            GLFW.glfwSwapInterval(0);
        }
        boolean travel = TravelOverlay.active();
        boolean whole = travel || JoinOverlay.whole();
        RenderTarget target = mc.getMainRenderTarget();
        target.bindWrite(true);
        // over the world only the corner is drawn anew: what the last frame showed stays
        RenderSystem.clear(whole ? 16640 : 256, Minecraft.ON_OSX);
        FogRenderer.setupNoFog();
        RenderSystem.enableCull();
        RenderSystem.backupProjectionMatrix();
        Matrix4f ortho = new Matrix4f().setOrtho(0, (float) (win.getWidth() / win.getGuiScale()),
                (float) (win.getHeight() / win.getGuiScale()), 0, 1000, ClientHooks.getGuiFarPlane());
        RenderSystem.setProjectionMatrix(ortho, VertexSorting.ORTHOGRAPHIC_Z);
        Matrix4fStack view = RenderSystem.getModelViewStack();
        view.pushMatrix();
        view.translation(0, 0, 10000F - ClientHooks.getGuiFarPlane());
        RenderSystem.applyModelViewMatrix();
        try {
            GuiGraphics g = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
            if (travel) TravelOverlay.pumped(g);
            else if (whole) JoinOverlay.pumped(g, now);
            else JoinOverlay.plate(g, now);
            g.flush();
        } finally {
            view.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
        }
        target.unbindWrite();
        target.blitToScreen(win.getWidth(), win.getHeight());
        show(win);
    }

    /** Shows the picture and empties the window's queue of events, with keys and mouse left out. */
    private static void show(Window win) {
        long w = win.getWindow();
        long key = GLFW.nglfwSetKeyCallback(w, 0), ch = GLFW.nglfwSetCharModsCallback(w, 0),
                button = GLFW.nglfwSetMouseButtonCallback(w, 0), cursor = GLFW.nglfwSetCursorPosCallback(w, 0),
                scroll = GLFW.nglfwSetScrollCallback(w, 0), drop = GLFW.nglfwSetDropCallback(w, 0);
        try {
            win.updateDisplay();
        } finally {
            GLFW.nglfwSetKeyCallback(w, key);
            GLFW.nglfwSetCharModsCallback(w, ch);
            GLFW.nglfwSetMouseButtonCallback(w, button);
            GLFW.nglfwSetCursorPosCallback(w, cursor);
            GLFW.nglfwSetScrollCallback(w, scroll);
            GLFW.nglfwSetDropCallback(w, drop);
        }
    }

    /** What was pressed or let go meanwhile was dropped: the keys are read from the keyboard and the mouse as they are now. */
    private static void keys(Minecraft mc) {
        if (mc.screen != null) return; // with a window open the game holds no key down anyway
        KeyMapping.setAll();
        long w = mc.getWindow().getWindow();
        for (KeyMapping k : mc.options.keyMappings) {
            InputConstants.Key key = k.getKey();
            if (key.getType() == InputConstants.Type.MOUSE && key.getValue() >= 0) {
                k.setDown(GLFW.glfwGetMouseButton(w, key.getValue()) == GLFW.GLFW_PRESS);
            }
        }
    }
}
