package de.kronwerke.core.link;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.kronwerke.core.KronwerkeCore;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.function.Consumer;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * The connection to the launcher's bus: one JSON object per line over loopback, signed in
 * with an HMAC over the launcher's nonce. Reconnects by itself; what arrives goes to the
 * handler on the bus thread (the handler moves it to the server thread).
 */
public final class Bus {
    private final String host;
    private final int port;
    private final Path keyFile;
    private final String server;
    private final Consumer<JsonObject> handler;
    private final Runnable connected;
    private volatile boolean running = true;
    private volatile Socket socket;
    private volatile BufferedWriter out;
    private Thread thread;
    /** Lines waiting for the writer thread, so the server thread never waits on the socket. */
    private final java.util.concurrent.LinkedBlockingQueue<String> outbox = new java.util.concurrent.LinkedBlockingQueue<>(20_000);
    private Thread writer;
    private volatile boolean dropWarned;

    private Bus(String host, int port, Path keyFile, String server, Consumer<JsonObject> handler, Runnable connected) {
        this.host = host;
        this.port = port;
        this.keyFile = keyFile;
        this.server = server;
        this.handler = handler;
        this.connected = connected;
    }

    /** The bus the launcher named, or null when the server runs without one. */
    public static Bus fromLauncher(Consumer<JsonObject> handler, Runnable connected) {
        String at = System.getProperty("launcher.bus", "");
        String key = System.getProperty("launcher.bus.key", "");
        int colon = at.lastIndexOf(':');
        if (at.isEmpty() || key.isEmpty() || colon < 0) return null;
        try {
            return new Bus(at.substring(0, colon), Integer.parseInt(at.substring(colon + 1)), Path.of(key), Role.server(), handler, connected);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public void start() {
        thread = new Thread(this::loop, "kronwerke-bus");
        thread.setDaemon(true);
        thread.start();
        writer = new Thread(this::write, "kronwerke-bus-out");
        writer.setDaemon(true);
        writer.start();
    }

    public void stop() {
        // what was sent last (checkpoints, goodbyes) still goes out, for up to three seconds
        long until = System.currentTimeMillis() + 3000;
        while (!outbox.isEmpty() && out != null && System.currentTimeMillis() < until) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                break;
            }
        }
        synchronized (this) {
            // the line being written finishes first
        }
        running = false;
        Socket s = socket;
        if (s != null) {
            try {
                s.close();
            } catch (IOException ignored) {
                // closing anyway
            }
        }
        if (thread != null) thread.interrupt();
        if (writer != null) writer.interrupt();
    }

    public boolean connected() {
        return out != null;
    }

    /**
     * Sends one message; dropped while the bus is away (state is sent again on reconnect). The
     * line is written by the bus's own writer thread: a player handoff can be a large line, and
     * the server thread used to write it itself, so a launcher that was slow to read held up
     * the whole tick.
     */
    public void send(JsonObject m) {
        if (out == null) return;
        if (!outbox.offer(m.toString()) && !dropWarned) {
            dropWarned = true;
            KronwerkeCore.LOGGER.warn("The launcher's bus does not read; messages are dropped until it does");
        }
    }

    private void write() {
        while (running) {
            String line;
            try {
                line = outbox.take();
            } catch (InterruptedException e) {
                return;
            }
            BufferedWriter w = out;
            if (w == null) continue; // away: dropped, the state is sent again on reconnect
            synchronized (this) {
                try {
                    w.write(line);
                    w.write('\n');
                    // more waiting: write them all before the flush
                    if (outbox.isEmpty()) w.flush();
                    dropWarned = false;
                } catch (IOException e) {
                    close();
                }
            }
        }
    }

    private void close() {
        out = null;
        Socket s = socket;
        if (s != null) {
            try {
                s.close();
            } catch (IOException ignored) {
                // gone
            }
        }
    }

    private void loop() {
        long wait = 1000;
        boolean told = false;
        while (running) {
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress(host, port), 5000);
                socket = s;
                BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                BufferedWriter w = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));
                JsonObject hello = JsonParser.parseString(in.readLine()).getAsJsonObject();
                String nonce = hello.get("nonce").getAsString();
                JsonObject auth = new JsonObject();
                auth.addProperty("op", "auth");
                auth.addProperty("server", server);
                auth.addProperty("mac", mac(Files.readString(keyFile).trim().getBytes(StandardCharsets.UTF_8), nonce + ":" + server));
                auth.addProperty("mod", "kronwerke-core");
                w.write(auth.toString());
                w.write('\n');
                w.flush();
                String first = in.readLine();
                if (first == null) throw new IOException("closed");
                JsonObject welcome = JsonParser.parseString(first).getAsJsonObject();
                if (!"welcome".equals(welcome.get("op").getAsString())) throw new IOException(first);
                outbox.clear(); // lines from before the reconnect belong to the old connection
                out = w;
                wait = 1000;
                told = false;
                KronwerkeCore.LOGGER.info("On the launcher's bus as {}", server);
                handler.accept(welcome);
                connected.run();
                for (String l; (l = in.readLine()) != null; ) {
                    try {
                        handler.accept(JsonParser.parseString(l).getAsJsonObject());
                    } catch (RuntimeException e) {
                        KronwerkeCore.LOGGER.debug("bus line {}: {}", l, e.toString());
                    }
                }
            } catch (IOException | RuntimeException e) {
                if (running && !told) {
                    KronwerkeCore.LOGGER.info("Launcher's bus not reachable ({}); trying again", e.getMessage());
                    told = true;
                }
            } finally {
                out = null;
                socket = null;
            }
            if (!running) return;
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                return;
            }
            wait = Math.min(15_000, wait * 2);
        }
    }

    static String mac(byte[] key, String text) {
        try {
            Mac m = Mac.getInstance("HmacSHA256");
            m.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(m.doFinal(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
