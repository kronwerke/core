package de.kronwerke.core.probe;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import de.kronwerke.core.KronwerkeCore;
import de.kronwerke.core.camera.CameraClient;
import de.kronwerke.core.client.TravelOverlay;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The test player's client side: a Minecraft client that is driven by lines in a file and
 * writes down what a player would see, so every function can be tried on the real server
 * without a person at the keyboard. Started with -Dkronwerke.probe=true (the camera
 * bootstrap's --tester); it does nothing on any other client.
 * <p>
 * Lines come in through {@code kronwerke/probe/in.txt} (one command each, see {@link #run}).
 * Answers go to {@code out.log}, pictures to {@code <name>.png}, and {@code timeline.log}
 * keeps what happens by itself: every screen that opens, every world and connection change,
 * the travel overlay's phases, every chat line, and every time the game stood still, with the
 * place in the code that held it up.
 */
public final class Probe {
    public static final boolean ON = CameraClient.PROBE;
    private static final Path DIR = FMLPaths.GAMEDIR.get().resolve("kronwerke").resolve("probe");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    /** A picture is padded to this size, so the console's API answers with a file and not inline. */
    private static final int PAD = 75_000, MAX = 170_000;

    private static final Deque<String> queue = new ArrayDeque<>();
    private static final Deque<String> chat = new ArrayDeque<>();
    private static final Map<KeyMapping, Integer> held = new HashMap<>();
    private static int wait;
    private static long frameCount, waitFrame;
    private static String lastScreen = "", lastLevel = "", lastPhase = "", lastServer = "";
    private static boolean optionsSet;

    private static volatile long lastFrame = System.nanoTime();
    private static volatile Thread main;
    private static long frames, frameWindowStart = System.currentTimeMillis();
    private static int fps, lowFps = 999;

    private Probe() {
    }

    public static void register() {
        if (!ON) return;
        try {
            Files.createDirectories(DIR);
        } catch (IOException e) {
            KronwerkeCore.LOGGER.warn("Probe: {}", e.toString());
        }
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> tick());
        NeoForge.EVENT_BUS.addListener((RenderFrameEvent.Post e) -> frame());
        NeoForge.EVENT_BUS.addListener((ClientChatReceivedEvent e) -> {
            boolean bar = e instanceof ClientChatReceivedEvent.System s && s.isOverlay();
            String line = (bar ? "[bar] " : "") + e.getMessage().getString();
            chat.addLast(stamp() + " " + line);
            while (chat.size() > 200) chat.removeFirst();
            event((bar ? "bar: " : "chat: ") + e.getMessage().getString());
        });
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn e) -> event("logging in"));
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> event("logging out"));
        Thread t = new Thread(Probe::watch, "kronwerke-probe");
        t.setDaemon(true);
        t.start();
        event("probe started");
    }

    // ---- what happens by itself ----

    private static void frame() {
        lastFrame = System.nanoTime();
        frames++;
        frameCount++;
        long now = System.currentTimeMillis();
        if (now - frameWindowStart >= 1000) {
            fps = (int) (frames * 1000 / (now - frameWindowStart));
            frames = 0;
            frameWindowStart = now;
            // while travelling the frame rate is the thing to know
            if (!"off".equals(TravelOverlay.phaseName())) event("fps " + fps);
            if (fps < lowFps) lowFps = fps;
        }
    }

    /** Samples the game thread whenever no frame came for a while, and says where it stood. */
    private static void watch() {
        Map<String, Integer> where = new LinkedHashMap<>();
        long stallStart = 0;
        while (true) {
            try {
                Thread.sleep(40);
            } catch (InterruptedException e) {
                return;
            }
            Thread m = main;
            if (m == null) continue;
            long gap = (System.nanoTime() - lastFrame) / 1_000_000;
            if (gap > 150) {
                if (stallStart == 0) stallStart = System.currentTimeMillis() - gap;
                where.merge(signature(m.getStackTrace()), 1, Integer::sum);
            } else if (stallStart != 0) {
                long took = System.currentTimeMillis() - stallStart;
                if (took >= 400) {
                    var top = where.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(4).toList();
                    String first = top.isEmpty() ? "" : top.get(0).getKey();
                    int cut = first.indexOf(" < ", first.indexOf(" < ") + 3);
                    event("STALL " + took + " ms in " + (cut > 0 ? first.substring(0, cut) : first));
                    if (took >= 1000) {
                        StringBuilder sb = new StringBuilder(stamp() + " STALL " + took + " ms\n");
                        for (var en : top) {
                            String k = en.getKey();
                            sb.append("  ").append(en.getValue()).append("x ").append(k.length() > 420 ? k.substring(0, 420) : k).append('\n');
                        }
                        append("stalls.log", sb.toString());
                    }
                }
                where.clear();
                stallStart = 0;
            }
        }
    }

    /** The first frames of a stack that are not the JDK's or a library's plumbing. */
    private static String signature(StackTraceElement[] st) {
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (StackTraceElement e : st) {
            String c = e.getClassName();
            if (c.startsWith("java.") || c.startsWith("jdk.") || c.startsWith("sun.") || c.startsWith("org.lwjgl.") || c.startsWith("com.google.")
                    || c.startsWith("it.unimi.") || c.startsWith("org.spongepowered.") || c.startsWith("io.netty.")) {
                if (n == 0 && sb.length() == 0) sb.append(c.substring(c.lastIndexOf('.') + 1)).append('.').append(e.getMethodName()).append(" < ");
                continue;
            }
            sb.append(c.substring(c.lastIndexOf('.') + 1)).append('.').append(e.getMethodName());
            if (++n >= 12) break;
            sb.append(" < ");
        }
        return sb.toString();
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        main = Thread.currentThread();
        if (!optionsSet) {
            optionsSet = true;
            mc.options.pauseOnLostFocus = false;
        }
        String screen = mc.screen == null ? "-" : mc.screen.getClass().getName();
        if (!screen.equals(lastScreen)) {
            lastScreen = screen;
            event("screen " + screen + (mc.screen == null ? "" : " \"" + mc.screen.getTitle().getString() + "\""));
        }
        String level = mc.level == null ? "-" : mc.level.dimension().location().toString();
        if (!level.equals(lastLevel)) {
            lastLevel = level;
            event("level " + level);
        }
        String phase = TravelOverlay.phaseName();
        if (!phase.equals(lastPhase)) {
            lastPhase = phase;
            event("overlay " + phase);
        }
        String server = server(mc);
        if (!server.equals(lastServer)) {
            lastServer = server;
            event("server " + server);
        }
        for (var it = held.entrySet().iterator(); it.hasNext(); ) {
            var en = it.next();
            if (en.getValue() <= 1) {
                en.getKey().setDown(false);
                it.remove();
            } else {
                en.getKey().setDown(true);
                en.setValue(en.getValue() - 1);
            }
        }
        reconnect(mc);
        readInput();
        if (wait > 0) {
            wait--;
            return;
        }
        if (frameCount < waitFrame) return;
        while (wait == 0 && frameCount >= waitFrame && !queue.isEmpty()) {
            String line = queue.removeFirst();
            try {
                String answer = run(mc, line);
                out("> " + line + (answer == null || answer.isEmpty() ? "" : "\n" + answer));
            } catch (Throwable t) {
                out("> " + line + "\nFAILED " + t);
                KronwerkeCore.LOGGER.warn("Probe: {} failed", line, t);
            }
        }
    }

    private static long offlineSince;
    private static boolean stayOut;

    /** Back onto the server after a restart or a kick, 30 seconds later; not after "disconnect". */
    private static void reconnect(Minecraft mc) {
        boolean out = mc.level == null && (mc.screen instanceof TitleScreen || mc.screen instanceof net.minecraft.client.gui.screens.DisconnectedScreen);
        if (!out || stayOut) {
            offlineSince = 0;
            return;
        }
        long now = System.currentTimeMillis();
        if (offlineSince == 0) offlineSince = now;
        if (now - offlineSince < 30_000) return;
        offlineSince = now;
        String addr = System.getProperty("kronwerke.camera.server", "127.0.0.1:25565");
        event("reconnecting to " + addr);
        ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString(addr), new ServerData("Kronwerke", addr, ServerData.Type.OTHER), false, null);
    }

    private static String server(Minecraft mc) {
        var c = mc.getConnection();
        if (c == null) return "-";
        return String.valueOf(c.getConnection().getRemoteAddress());
    }

    private static void readInput() {
        Path in = DIR.resolve("in.txt"), work = DIR.resolve("in.work");
        if (!Files.exists(in)) return;
        try {
            Files.move(in, work, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            for (String l : Files.readAllLines(work, StandardCharsets.UTF_8)) {
                for (String part : l.split(" ;; ")) if (!part.isBlank()) queue.addLast(part.strip());
            }
            Files.deleteIfExists(work);
        } catch (IOException e) {
            KronwerkeCore.LOGGER.warn("Probe: {}", e.toString());
        }
    }

    // ---- the commands ----

    /**
     * One line, one command:
     * <pre>
     * state                     where the client is (screen, world, position, server, overlay)
     * screen                    the open screen's widgets and slots, with their positions
     * inv                       the player's inventory
     * chat [n]                  the last chat lines
     * shot NAME [width]         a picture of the frame as the player sees it
     * say TEXT / cmd COMMAND    chat, or a command without the slash
     * key NAME [ticks]          a key of the keyboard (e, escape, f3, space ...), down and up
     * type TEXT                 characters into the open screen
     * click X Y [button]        the mouse on the open screen, in the screen's coordinates
     * move X Y                  the mouse pointer only (tooltips)
     * scroll X Y AMOUNT         the wheel
     * slot INDEX [button] [pickup|quick_move|swap|throw|clone]   a click on a slot of the open menu
     * tap MAPPING               one press of a game key (use, attack, drop, inventory, swap ...)
     * hold MAPPING TICKS        a game key held down (forward, back, left, right, jump, sneak, sprint, use, attack)
     * look YAW PITCH            turn the head
     * hotbar N                  select a hotbar slot (0-8)
     * close                     close the open screen the way Escape does
     * gui on|off                the HUD
     * connect HOST:PORT / disconnect
     * wait TICKS                before the next line
     * lang CODE                 the client's language (de_de), until it restarts; resources reload
     * mark TEXT                 a line in the timeline
     * log REGEX [n]             the last lines of the client's log that match
     * </pre>
     * Several commands fit in one line, separated by " ;; ".
     * <pre>
     * </pre>
     */
    private static String run(Minecraft mc, String line) throws Exception {
        String[] a = line.split(" ", 2);
        String rest = a.length > 1 ? a[1].strip() : "";
        String[] p = rest.isEmpty() ? new String[0] : rest.split(" +");
        switch (a[0]) {
            case "state":
                return state(mc);
            case "screen":
                return screen(mc);
            case "inv":
                return inv(mc);
            case "chat": {
                int n = p.length > 0 ? Integer.parseInt(p[0]) : 20;
                List<String> all = new ArrayList<>(chat);
                return String.join("\n", all.subList(Math.max(0, all.size() - n), all.size()));
            }
            case "shot":
                return shot(mc, p[0], p.length > 1 ? Integer.parseInt(p[1]) : 854);
            case "say":
                mc.player.connection.sendChat(rest);
                return null;
            case "cmd":
                mc.player.connection.sendCommand(rest.startsWith("/") ? rest.substring(1) : rest);
                return null;
            case "key": {
                int code = key(p[0]);
                long w = mc.getWindow().getWindow();
                mc.keyboardHandler.keyPress(w, code, 0, 1, 0);
                mc.keyboardHandler.keyPress(w, code, 0, 0, 0);
                return null;
            }
            case "type": {
                Method m = mc.keyboardHandler.getClass().getDeclaredMethod("charTyped", long.class, int.class, int.class);
                m.setAccessible(true);
                for (int cp : rest.codePoints().toArray()) m.invoke(mc.keyboardHandler, mc.getWindow().getWindow(), cp, 0);
                return null;
            }
            case "move":
                mouseMove(mc, Double.parseDouble(p[0]), Double.parseDouble(p[1]));
                return null;
            case "click": {
                // many screens work out what is under the mouse while they draw: move first, press two frames later
                mouseMove(mc, Double.parseDouble(p[0]), Double.parseDouble(p[1]));
                queue.addFirst("press " + rest);
                waitFrame = frameCount + 2;
                return null;
            }
            case "press": {
                int button = p.length > 2 ? Integer.parseInt(p[2]) : 0;
                double x = Double.parseDouble(p[0]), y = Double.parseDouble(p[1]);
                Screen sc = mc.screen;
                if (sc == null) return "no screen";
                // the screen's own path, as the mouse handler takes it: without the window's size in
                // between, which the camera's window (no display) does not report
                sc.afterMouseAction();
                boolean done = net.neoforged.neoforge.client.ClientHooks.onScreenMouseClickedPre(sc, x, y, button);
                if (!done) {
                    done = sc.mouseClicked(x, y, button);
                    done = net.neoforged.neoforge.client.ClientHooks.onScreenMouseClickedPost(sc, x, y, button, done);
                }
                if (mc.screen == sc) {
                    boolean up = net.neoforged.neoforge.client.ClientHooks.onScreenMouseReleasedPre(sc, x, y, button);
                    if (!up) {
                        up = sc.mouseReleased(x, y, button);
                        net.neoforged.neoforge.client.ClientHooks.onScreenMouseReleasedPost(sc, x, y, button, up);
                    }
                }
                return done ? "handled" : "not handled";
            }
            case "scroll": {
                mouseMove(mc, Double.parseDouble(p[0]), Double.parseDouble(p[1]));
                Method m = mc.mouseHandler.getClass().getDeclaredMethod("onScroll", long.class, double.class, double.class);
                m.setAccessible(true);
                m.invoke(mc.mouseHandler, mc.getWindow().getWindow(), 0.0, Double.parseDouble(p[2]));
                return null;
            }
            case "slot": {
                if (!(mc.screen instanceof AbstractContainerScreen<?> s)) return "no container screen";
                int button = p.length > 1 ? Integer.parseInt(p[1]) : 0;
                ClickType type = p.length > 2 ? ClickType.valueOf(p[2].toUpperCase(Locale.ROOT)) : ClickType.PICKUP;
                mc.gameMode.handleInventoryMouseClick(s.getMenu().containerId, Integer.parseInt(p[0]), button, type, mc.player);
                return null;
            }
            case "tap":
                KeyMapping.click(mapping(mc, p[0]).getKey());
                return null;
            case "hold": {
                KeyMapping k = mapping(mc, p[0]);
                k.setDown(true);
                held.put(k, Integer.parseInt(p[1]));
                return null;
            }
            case "look":
                mc.player.setYRot(Float.parseFloat(p[0]));
                mc.player.setXRot(Float.parseFloat(p[1]));
                return null;
            case "hotbar":
                mc.player.getInventory().selected = Integer.parseInt(p[0]);
                return null;
            case "close":
                if (mc.screen != null) mc.screen.onClose();
                return null;
            case "gui":
                mc.options.hideGui = rest.equals("off");
                return null;
            case "connect": {
                if (mc.level != null) return "still in a world: disconnect first";
                stayOut = false;
                ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString(rest), new ServerData("Kronwerke", rest, ServerData.Type.OTHER), false, null);
                return null;
            }
            case "disconnect":
                stayOut = true;
                if (mc.level != null) {
                    mc.level.disconnect();
                    mc.disconnect();
                    mc.setScreen(new TitleScreen());
                }
                return null;
            case "wait":
                wait = Integer.parseInt(p[0]);
                return null;
            case "lang": {
                // lang de_de: the client's language, for pictures of screens in the book's language.
                // The bootstrap sets en_us at every start, so this holds until the client restarts.
                if (mc.getLanguageManager().getLanguage(p[0]) == null) return "no such language";
                mc.getLanguageManager().setSelected(p[0]);
                mc.options.languageCode = p[0];
                mc.reloadResourcePacks();
                return "language " + p[0] + ", resources reload";
            }
            case "mark":
                event("MARK " + rest);
                return null;
            case "log": {
                // log REGEX [n]: the last lines of the client's own log that match
                int sp = rest.lastIndexOf(' ');
                int n = 15;
                String re = rest;
                if (sp > 0 && rest.substring(sp + 1).matches("[0-9]+")) {
                    n = Integer.parseInt(rest.substring(sp + 1));
                    re = rest.substring(0, sp);
                }
                java.util.regex.Pattern pat = java.util.regex.Pattern.compile(re);
                Deque<String> hits = new ArrayDeque<>();
                try (var lines = Files.lines(FMLPaths.GAMEDIR.get().resolve("logs/latest.log"), StandardCharsets.UTF_8)) {
                    int max = n;
                    lines.filter(l -> pat.matcher(l).find()).forEach(l -> {
                        hits.addLast(l.length() > 1500 ? l.substring(0, 1500) : l);
                        if (hits.size() > max) hits.removeFirst();
                    });
                }
                return String.join("\n", hits);
            }
            default:
                return "unknown command";
        }
    }

    private static String state(Minecraft mc) {
        StringBuilder sb = new StringBuilder();
        sb.append("screen=").append(mc.screen == null ? "-" : mc.screen.getClass().getName());
        if (mc.screen != null) sb.append(" title=\"").append(mc.screen.getTitle().getString()).append('"');
        var win = mc.getWindow();
        sb.append("\nwindow=").append(win.getScreenWidth()).append('x').append(win.getScreenHeight()).append(" frame=").append(win.getWidth()).append('x').append(win.getHeight())
                .append(" gui=").append(win.getGuiScaledWidth()).append('x').append(win.getGuiScaledHeight());
        sb.append("\nserver=").append(server(mc)).append(" overlay=").append(TravelOverlay.phaseName()).append(" fps=").append(fps)
                .append(" lowest=").append(lowFps).append(" menumusic=").append(de.kronwerke.core.client.MenuMusic.state());
        lowFps = 999;
        if (mc.level != null && mc.player != null) {
            var pl = mc.player;
            sb.append("\nlevel=").append(mc.level.dimension().location())
                    .append(String.format(Locale.ROOT, " pos=%.1f %.1f %.1f yaw=%.0f pitch=%.0f", pl.getX(), pl.getY(), pl.getZ(), pl.getYRot(), pl.getXRot()))
                    .append(" mode=").append(mc.gameMode == null ? "?" : mc.gameMode.getPlayerMode().getName())
                    .append(String.format(Locale.ROOT, " health=%.0f food=%d", pl.getHealth(), pl.getFoodData().getFoodLevel()))
                    .append("\nhand=").append(item(pl.getMainHandItem())).append(" hotbar=").append(pl.getInventory().selected)
                    .append(" players=").append(mc.getConnection() == null ? 0 : mc.getConnection().getOnlinePlayers().size());
            if (mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult b && b.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
                sb.append("\nlooking at ").append(BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(b.getBlockPos()).getBlock())).append(" ").append(b.getBlockPos().toShortString());
            }
        } else {
            sb.append("\nlevel=-");
        }
        return sb.toString();
    }

    private static String screen(Minecraft mc) {
        Screen s = mc.screen;
        if (s == null) return "no screen";
        StringBuilder sb = new StringBuilder(s.getClass().getName() + " \"" + s.getTitle().getString() + "\" " + s.width + "x" + s.height);
        widgets(sb, s.children(), 0);
        if (s instanceof AbstractContainerScreen<?> c) {
            sb.append("\nmenu ").append(c.getMenu().getClass().getSimpleName()).append(" carried=").append(item(c.getMenu().getCarried()));
            for (Slot slot : c.getMenu().slots) {
                if (!slot.hasItem()) continue;
                sb.append("\n  slot ").append(slot.index).append(" at ").append(c.getGuiLeft() + slot.x + 8).append(' ').append(c.getGuiTop() + slot.y + 8)
                        .append(' ').append(slot.container.getClass().getSimpleName()).append(' ').append(item(slot.getItem()));
            }
            sb.append("\n  slots ").append(c.getMenu().slots.size());
        }
        return sb.toString();
    }

    private static void widgets(StringBuilder sb, List<? extends GuiEventListener> list, int depth) {
        if (depth > 4) return;
        for (GuiEventListener l : list) {
            if (l instanceof AbstractWidget w) {
                if (!w.visible) continue;
                String msg = w.getMessage().getString();
                sb.append("\n  ").append("  ".repeat(depth)).append(w.getClass().getSimpleName()).append(" at ").append(w.getX() + w.getWidth() / 2).append(' ')
                        .append(w.getY() + w.getHeight() / 2).append(" size ").append(w.getWidth()).append('x').append(w.getHeight())
                        .append(w.active ? "" : " (off)").append(msg.isEmpty() ? "" : " \"" + (msg.length() > 160 ? msg.substring(0, 160) + "..." : msg) + "\"");
            } else {
                sb.append("\n  ").append("  ".repeat(depth)).append(l.getClass().getSimpleName());
            }
            if (l instanceof ContainerEventHandler h) widgets(sb, h.children(), depth + 1);
        }
    }

    private static String inv(Minecraft mc) {
        if (mc.player == null) return "no player";
        StringBuilder sb = new StringBuilder();
        var inv = mc.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (!inv.getItem(i).isEmpty()) sb.append(sb.length() == 0 ? "" : "\n").append(i).append(": ").append(item(inv.getItem(i)));
        }
        return sb.length() == 0 ? "empty" : sb.toString();
    }

    private static String item(ItemStack s) {
        if (s.isEmpty()) return "-";
        return BuiltInRegistries.ITEM.getKey(s.getItem()) + (s.getCount() > 1 ? " x" + s.getCount() : "");
    }

    private static int key(String name) {
        try {
            return Integer.parseInt(name);
        } catch (NumberFormatException e) {
            return InputConstants.getKey("key.keyboard." + name.toLowerCase(Locale.ROOT).replace('_', '.')).getValue();
        }
    }

    private static KeyMapping mapping(Minecraft mc, String name) {
        var o = mc.options;
        return switch (name) {
            case "use" -> o.keyUse;
            case "attack" -> o.keyAttack;
            case "forward" -> o.keyUp;
            case "back" -> o.keyDown;
            case "left" -> o.keyLeft;
            case "right" -> o.keyRight;
            case "jump" -> o.keyJump;
            case "sneak" -> o.keyShift;
            case "sprint" -> o.keySprint;
            case "drop" -> o.keyDrop;
            case "inventory" -> o.keyInventory;
            case "swap" -> o.keySwapOffhand;
            case "pick" -> o.keyPickItem;
            default -> {
                for (KeyMapping k : o.keyMappings) if (k.getName().equals(name) || k.getName().endsWith("." + name)) yield k;
                throw new IllegalArgumentException("no key mapping " + name);
            }
        };
    }

    /** Puts the pointer somewhere on the screen, in the screen's own (scaled) coordinates. */
    private static void mouseMove(Minecraft mc, double x, double y) throws Exception {
        var win = mc.getWindow();
        if (win.getScreenWidth() <= 0 || win.getScreenHeight() <= 0) {
            // a window without a display reports no size: the frame's size is the window's
            for (String f : new String[] {"width", "height"}) {
                Field fl = win.getClass().getDeclaredField(f);
                fl.setAccessible(true);
                fl.setInt(win, f.equals("width") ? win.getWidth() : win.getHeight());
            }
        }
        double px = x * win.getScreenWidth() / win.getGuiScaledWidth(), py = y * win.getScreenHeight() / win.getGuiScaledHeight();
        Method m = mc.mouseHandler.getClass().getDeclaredMethod("onMove", long.class, double.class, double.class);
        m.setAccessible(true);
        m.invoke(mc.mouseHandler, win.getWindow(), px, py);
        // onMove ignores the first move after a grab and anything while the window has no focus
        for (String f : new String[] {"xpos", "ypos"}) {
            Field fl = mc.mouseHandler.getClass().getDeclaredField(f);
            fl.setAccessible(true);
            fl.setDouble(mc.mouseHandler, f.equals("xpos") ? px : py);
        }
    }

    private static String shot(Minecraft mc, String name, int width) throws IOException {
        if (!name.matches("[a-zA-Z0-9_.-]{1,60}")) return "name: letters, digits, _ . -";
        BufferedImage img;
        try (NativeImage n = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            int w = n.getWidth(), h = n.getHeight();
            img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int abgr = n.getPixelRGBA(x, y);
                    img.setRGB(x, y, ((abgr & 0xFF) << 16) | (abgr & 0xFF00) | ((abgr >> 16) & 0xFF));
                }
            }
        }
        if (width < img.getWidth()) {
            int h = Math.max(1, img.getHeight() * width / img.getWidth());
            BufferedImage out = new BufferedImage(width, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(img, 0, 0, width, h, null);
            g.dispose();
            img = out;
        }
        // JPEG, and never more than the console's API passes on in one answer
        byte[] data = null;
        for (float q = 0.82f; q > 0.2f; q -= 0.15f) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            var writer = ImageIO.getImageWritersByFormatName("jpg").next();
            var param = writer.getDefaultWriteParam();
            param.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(q);
            try (var ios = ImageIO.createImageOutputStream(bytes)) {
                writer.setOutput(ios);
                writer.write(null, new javax.imageio.IIOImage(img, null, null), param);
            } finally {
                writer.dispose();
            }
            data = bytes.toByteArray();
            if (data.length <= MAX) break;
        }
        int size = data.length;
        if (size < PAD) data = java.util.Arrays.copyOf(data, PAD);
        Files.write(DIR.resolve(name + ".jpg"), data);
        return name + ".jpg " + img.getWidth() + "x" + img.getHeight() + " " + size + " bytes";
    }

    // ---- the files ----

    private static String stamp() {
        return LocalTime.now().format(TIME);
    }

    private static synchronized void event(String text) {
        append("timeline.log", stamp() + " " + text + "\n");
    }

    private static void out(String text) {
        append("out.log", stamp() + " " + text + "\n");
    }

    private static synchronized void append(String file, String text) {
        try {
            Files.writeString(DIR.resolve(file), text, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            KronwerkeCore.LOGGER.warn("Probe: {}", e.toString());
        }
    }
}
