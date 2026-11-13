package de.kronwerke.core.camera;

import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * What happens to a picture after it arrived: a copy with 256 colours (median cut, no
 * dithering) goes to shots/opt, about a quarter of the size, which is what the quest book
 * ships. /kw cam bundle strings the small copies together and cuts them into pieces the
 * console's files API can hand out in one answer, with an index of names and offsets.
 */
public final class ShotFiles {
    /** Below the console's answer limit once base64 encoded. */
    private static final int PIECE = 185_000;
    private static final int MIN_PIECE = 80_000;

    private ShotFiles() {
    }

    /** One worker for the picture work, so the server thread never waits for it. */
    public static final java.util.concurrent.ExecutorService WORKER = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Kronwerke camera files");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    /** The width the quest book gets; larger pictures are scaled down for the small copy. */
    public static final int BOOK_WIDTH = 480;

    public static void writeOptimised(Path shots, String name, byte[] png) throws IOException {
        Path dir = shots.resolve("opt");
        Files.createDirectories(dir);
        Files.write(dir.resolve(name + ".png"), palette(png));
    }

    /** Deletes every picture, small copy and bundle, before a fresh run. Returns the count. */
    public static int clear(Path shots) throws IOException {
        if (!Files.isDirectory(shots)) return 0;
        int[] n = {0};
        // the test world's scenes live in shots/world and are not pictures
        Path world = shots.resolve("world"), fresh = shots.resolve("world.new");
        try (var walk = Files.walk(shots)) {
            for (Path f : (Iterable<Path>) walk.sorted(java.util.Comparator.reverseOrder())::iterator) {
                if (f.equals(shots) || f.getFileName().toString().equals("shots.json")) continue;
                if (f.startsWith(world) || f.startsWith(fresh)) continue;
                if (Files.isRegularFile(f)) n[0]++;
                Files.deleteIfExists(f);
            }
        }
        return n[0];
    }

    /** Makes the small copies again from the full pictures in shots (prefix "" for all). */
    public static int reoptimise(Path shots, String prefix) throws IOException {
        int n = 0;
        if (!Files.isDirectory(shots)) return 0;
        try (var all = Files.newDirectoryStream(shots, "*.png")) {
            for (Path f : all) {
                String file = f.getFileName().toString();
                if (!file.startsWith(prefix)) continue;
                writeOptimised(shots, file.substring(0, file.length() - 4), Files.readAllBytes(f));
                n++;
            }
        }
        return n;
    }

    /** Writes shots/bundle/000.bin, 001.bin, ... and index.json; returns the number of pictures. */
    public static int bundle(Path shots, String prefix) throws IOException {
        Path opt = shots.resolve("opt"), out = shots.resolve("bundle");
        Files.createDirectories(out);
        try (var old = Files.newDirectoryStream(out)) {
            for (Path f : old) Files.deleteIfExists(f);
        }
        List<Path> files = new ArrayList<>();
        if (Files.isDirectory(opt)) {
            try (var all = Files.newDirectoryStream(opt, "*.png")) {
                for (Path f : all) if (f.getFileName().toString().startsWith(prefix)) files.add(f);
            }
        }
        files.sort(null);
        ByteArrayOutputStream all = new ByteArrayOutputStream();
        JsonArray index = new JsonArray();
        for (Path f : files) {
            byte[] b = Files.readAllBytes(f);
            JsonObject e = new JsonObject();
            String n = f.getFileName().toString();
            e.addProperty("name", n.substring(0, n.length() - 4));
            e.addProperty("offset", all.size());
            e.addProperty("length", b.length);
            index.add(e);
            all.write(b);
        }
        byte[] data = all.toByteArray();
        int size = data.length;
        int pieces = cut(out, data);
        JsonObject o = new JsonObject();
        o.addProperty("size", size);
        o.addProperty("pieces", pieces);
        o.add("files", index);
        Files.writeString(out.resolve("index.json"), o.toString(), StandardCharsets.UTF_8);
        return files.size();
    }

    /** Pieces of equal size, and none tiny: the reader handles small answers differently. */
    private static int cut(Path out, byte[] data) throws IOException {
        if (data.length < MIN_PIECE) data = Arrays.copyOf(data, MIN_PIECE);
        int pieces = Math.max(1, (data.length + PIECE - 1) / PIECE);
        int each = (data.length + pieces - 1) / pieces;
        for (int i = 0; i < pieces; i++) {
            int from = i * each;
            Files.write(out.resolve(String.format("%03d.bin", i)), Arrays.copyOfRange(data, from, Math.min(data.length, from + each)));
        }
        return pieces;
    }

    /** Pictures per contact sheet: two beside each other, four rows. */
    public static final int SHEET = 8;

    /**
     * Contact sheets for looking through a run before fetching anything in full: the small
     * copies whose name starts with prefix, sorted, eight to a sheet at their own size, as
     * JPEG, strung together and cut into pieces in shots/bundle like the bundle itself. A
     * sheet takes about a sixth of what its eight pictures take. Returns the number of
     * pictures; the names follow from the sorted list, the sheets carry no lettering (a
     * server need not have a font).
     */
    public static int sheets(Path shots, String prefix) throws IOException {
        Path opt = shots.resolve("opt"), out = shots.resolve("bundle");
        Files.createDirectories(out);
        try (var old = Files.newDirectoryStream(out)) {
            for (Path f : old) Files.deleteIfExists(f);
        }
        List<Path> files = new ArrayList<>();
        if (Files.isDirectory(opt)) {
            try (var all = Files.newDirectoryStream(opt, "*.png")) {
                for (Path f : all) if (f.getFileName().toString().startsWith(prefix)) files.add(f);
            }
        }
        files.sort(null);
        ByteArrayOutputStream all = new ByteArrayOutputStream();
        int w = BOOK_WIDTH, h = BOOK_WIDTH * 9 / 16;
        for (int i = 0; i < files.size(); i += SHEET) {
            int n = Math.min(SHEET, files.size() - i);
            BufferedImage sheet = new BufferedImage(2 * w, (n + 1) / 2 * h, BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = sheet.createGraphics();
            for (int k = 0; k < n; k++) {
                BufferedImage img = ImageIO.read(files.get(i + k).toFile());
                if (img != null) g.drawImage(img, (k % 2) * w, (k / 2) * h, w, h, null);
            }
            g.dispose();
            javax.imageio.ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
            javax.imageio.ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.8f);
            ByteArrayOutputStream one = new ByteArrayOutputStream();
            try (javax.imageio.stream.ImageOutputStream ios = ImageIO.createImageOutputStream(one)) {
                writer.setOutput(ios);
                writer.write(null, new javax.imageio.IIOImage(sheet, null, null), param);
            } finally {
                writer.dispose();
            }
            // a length in front of every sheet: JPEG data cannot be told apart by its markers alone
            byte[] b = one.toByteArray();
            all.write(new byte[] {(byte) (b.length >>> 24), (byte) (b.length >>> 16), (byte) (b.length >>> 8), (byte) b.length});
            all.write(b);
        }
        cut(out, all.toByteArray());
        return files.size();
    }

    // ---- 256 colours ------------------------------------------------------------------

    static byte[] palette(byte[] png) throws IOException {
        BufferedImage src = ImageIO.read(new ByteArrayInputStream(png));
        if (src.getWidth() > BOOK_WIDTH) {
            int h2 = Math.round(src.getHeight() * (float) BOOK_WIDTH / src.getWidth());
            BufferedImage small = new BufferedImage(BOOK_WIDTH, h2, BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = small.createGraphics();
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(src, 0, 0, BOOK_WIDTH, h2, null);
            g.dispose();
            src = small;
        }
        int w = src.getWidth(), h = src.getHeight();
        int[] px = src.getRGB(0, 0, w, h, null, 0, w);
        int[] pal = medianCut(px, 256);
        byte[] r = new byte[pal.length], g = new byte[pal.length], b = new byte[pal.length];
        for (int i = 0; i < pal.length; i++) {
            r[i] = (byte) (pal[i] >> 16);
            g[i] = (byte) (pal[i] >> 8);
            b[i] = (byte) pal[i];
        }
        IndexColorModel cm = new IndexColorModel(8, pal.length, r, g, b);
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_INDEXED, cm);
        byte[] dst = ((java.awt.image.DataBufferByte) out.getRaster().getDataBuffer()).getData();
        Map<Integer, Integer> cache = new HashMap<>();
        for (int i = 0; i < px.length; i++) {
            int c = px[i] & 0xFFFFFF;
            Integer k = cache.get(c);
            if (k == null) {
                k = nearest(pal, c);
                cache.put(c, k);
            }
            dst[i] = (byte) (int) k;
        }
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        javax.imageio.ImageWriter writer = ImageIO.getImageWritersByFormatName("png").next();
        javax.imageio.ImageWriteParam param = writer.getDefaultWriteParam();
        if (param.canWriteCompressed()) {
            param.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.0f); // the strongest deflate
        }
        try (javax.imageio.stream.ImageOutputStream ios = ImageIO.createImageOutputStream(o)) {
            writer.setOutput(ios);
            writer.write(null, new javax.imageio.IIOImage(out, null, null), param);
        } finally {
            writer.dispose();
        }
        return o.toByteArray();
    }

    private static int nearest(int[] pal, int c) {
        int cr = (c >> 16) & 255, cg = (c >> 8) & 255, cb = c & 255;
        int best = 0, bd = Integer.MAX_VALUE;
        for (int i = 0; i < pal.length; i++) {
            int dr = cr - ((pal[i] >> 16) & 255), dg = cg - ((pal[i] >> 8) & 255), db = cb - (pal[i] & 255);
            int d = 3 * dr * dr + 4 * dg * dg + 2 * db * db;
            if (d < bd) {
                bd = d;
                best = i;
            }
        }
        return best;
    }

    /** Median cut over the exact colours, weighted by how often each appears. */
    private static int[] medianCut(int[] px, int max) {
        Map<Integer, int[]> count = new HashMap<>();
        for (int p : px) count.computeIfAbsent(p & 0xFFFFFF, k -> new int[1])[0]++;
        int n = count.size();
        int[] col = new int[n], wt = new int[n];
        int i = 0;
        for (var e : count.entrySet()) {
            col[i] = e.getKey();
            wt[i++] = e.getValue()[0];
        }
        if (n <= max) return col;
        List<int[]> boxes = new ArrayList<>(); // {from, to} into the sorted arrays
        boxes.add(new int[]{0, n});
        int[] c2 = col.clone(), w2 = wt.clone();
        while (boxes.size() < max) {
            // split the box with the largest weighted spread
            int bi = -1;
            long bs = -1;
            int bch = 0;
            for (int k = 0; k < boxes.size(); k++) {
                int[] bx = boxes.get(k);
                if (bx[1] - bx[0] < 2) continue;
                int[] lo = {255, 255, 255}, hi = {0, 0, 0};
                long weight = 0;
                for (int j = bx[0]; j < bx[1]; j++) {
                    for (int ch = 0; ch < 3; ch++) {
                        int v = (c2[j] >> (16 - 8 * ch)) & 255;
                        lo[ch] = Math.min(lo[ch], v);
                        hi[ch] = Math.max(hi[ch], v);
                    }
                    weight += w2[j];
                }
                int ch = 0;
                for (int q = 1; q < 3; q++) if (hi[q] - lo[q] > hi[ch] - lo[ch]) ch = q;
                long score = (long) (hi[ch] - lo[ch]) * (long) Math.sqrt(weight);
                if (score > bs) {
                    bs = score;
                    bi = k;
                    bch = ch;
                }
            }
            if (bi < 0 || bs == 0) break;
            int[] bx = boxes.get(bi);
            final int shift = 16 - 8 * bch;
            Integer[] idx = new Integer[bx[1] - bx[0]];
            for (int j = 0; j < idx.length; j++) idx[j] = bx[0] + j;
            final int[] cc = c2, ww = w2;
            Arrays.sort(idx, (a, b) -> Integer.compare((cc[a] >> shift) & 255, (cc[b] >> shift) & 255));
            int[] nc = new int[idx.length], nw = new int[idx.length];
            long total = 0;
            for (int j = 0; j < idx.length; j++) {
                nc[j] = cc[idx[j]];
                nw[j] = ww[idx[j]];
                total += nw[j];
            }
            System.arraycopy(nc, 0, c2, bx[0], idx.length);
            System.arraycopy(nw, 0, w2, bx[0], idx.length);
            long acc = 0;
            int cut = bx[0] + 1;
            for (int j = 0; j < idx.length - 1; j++) {
                acc += nw[j];
                cut = bx[0] + j + 1;
                if (acc * 2 >= total) break;
            }
            boxes.set(bi, new int[]{bx[0], cut});
            boxes.add(new int[]{cut, bx[1]});
        }
        int[] pal = new int[boxes.size()];
        for (int k = 0; k < boxes.size(); k++) {
            int[] bx = boxes.get(k);
            long r = 0, g = 0, b = 0, t = 0;
            for (int j = bx[0]; j < bx[1]; j++) {
                r += (long) ((c2[j] >> 16) & 255) * w2[j];
                g += (long) ((c2[j] >> 8) & 255) * w2[j];
                b += (long) (c2[j] & 255) * w2[j];
                t += w2[j];
            }
            pal[k] = (int) ((r / t) << 16 | (g / t) << 8 | (b / t));
        }
        return pal;
    }
}
