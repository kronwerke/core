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
 * inside JEI's loops (per plugin, per recipe, per item of the search), and whenever a frame is
 * due, one is drawn right there: the join picture or the stars of a move if one of them was on
 * the screen, otherwise just the small gear in the corner over the last picture of the world.
 * The window's events are taken off the queue so that it stays alive, but keys and clicks are
 * dropped: handling them would run the game's input code in the middle of JEI's start. The
 * keys are read anew from the keyboard afterwards, so none stays stuck.
 * <p>
 * For now only with -Dkronwerke.loadpump=true: it is seen in the real test client first.
 */
public final class LoadPump {
    private static final boolean ON = Boolean.getBoolean("kronwerke.loadpump");
    /** A frame at most every 33 ms: 30 a second is smooth for the gears and costs the work little. */
    private static final long EVERY_NS = 33_000_000L;

    private static volatile boolean active;
    private static boolean inside, inFrame, broken;
    private static Thread thread;
    private static long began, last, calls;
    private static int frames;
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
            // a frame of the game's own: whatever was being pumped is over, even if it ended in an error
            if (active) end();
            stalled();
        });
        NeoForge.EVENT_BUS.addListener((RenderFrameEvent.Post e) -> inFrame = false);
        KronwerkeCore.LOGGER.info("Pictures during JEI's start are on (kronwerke.loadpump)");
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

    /** JEI's start begins (on the game's thread). */
    public static void begin() {
        if (!ON || broken || active || inFrame || !RenderSystem.isOnRenderThread()) return;
        thread = Thread.currentThread();
        began = last = System.nanoTime();
        frames = 0;
        calls = 0;
        gaps.clear();
        stage = "";
        // without this every picture would wait for the screen's next refresh, and JEI with it
        GLFW.glfwSwapInterval(0);
        JoinOverlay.note(Text.t("load.recipes", "Lade Rezepte").getString());
        active = true;
    }

    /** JEI's start is over. */
    public static void end() {
        if (!active) return;
        active = false;
        JoinOverlay.note("");
        Minecraft mc = Minecraft.getInstance();
        mc.getWindow().updateVsync(mc.options.enableVsync().get());
        if (frames > 0) keys(mc);
        long ms = (System.nanoTime() - began) / 1_000_000;
        gaps.sort(Comparator.comparingLong(Gap::ms).reversed());
        StringBuilder worst = new StringBuilder();
        for (int i = 0; i < Math.min(6, gaps.size()); i++) {
            worst.append(i == 0 ? "" : ", ").append(gaps.get(i).ms()).append(" ms (").append(gaps.get(i).where()).append(')');
        }
        KronwerkeCore.LOGGER.info("JEI's start took {} ms with {} pictures drawn meanwhile ({} chances); longest waits: {}",
                ms, frames, calls, worst.isEmpty() ? "none over 100 ms" : worst);
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
        if (!active) return;
        if (inside || Thread.currentThread() != thread) return;
        calls++;
        long now = System.nanoTime();
        if (now - last < EVERY_NS) return;
        if (now - began > 180_000_000_000L) {
            end();
            return;
        }
        long waited = (now - last) / 1_000_000;
        if (waited >= 100) gaps.add(new Gap(waited, stage));
        inside = true;
        try {
            frame();
            frames++;
        } catch (Throwable t) {
            // never again in this game: a picture is not worth a crash
            broken = true;
            active = false;
            JoinOverlay.note("");
            KronwerkeCore.LOGGER.warn("Pictures during JEI's start are off after an error", t);
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
