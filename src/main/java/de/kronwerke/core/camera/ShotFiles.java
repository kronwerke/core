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

    public static void writeOptimised(Path shots, String name, byte[] png) throws IOException {
        Path dir = shots.resolve("opt");
        Files.createDirectories(dir);
        Files.write(dir.resolve(name + ".png"), palette(png));
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
        // pieces of equal size, and none tiny: the reader handles small answers differently
        if (data.length < MIN_PIECE) data = Arrays.copyOf(data, MIN_PIECE);
        int pieces = Math.max(1, (data.length + PIECE - 1) / PIECE);
        int each = (data.length + pieces - 1) / pieces;
        for (int i = 0; i < pieces; i++) {
            int from = i * each;
            Files.write(out.resolve(String.format("%03d.bin", i)), Arrays.copyOfRange(data, from, Math.min(data.length, from + each)));
        }
        JsonObject o = new JsonObject();
        o.addProperty("size", size);
        o.addProperty("pieces", pieces);
        o.add("files", index);
        Files.writeString(out.resolve("index.json"), o.toString(), StandardCharsets.UTF_8);
        return files.size();
    }

    // ---- 256 colours ------------------------------------------------------------------

    static byte[] palette(byte[] png) throws IOException {
        BufferedImage src = ImageIO.read(new ByteArrayInputStream(png));
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
        ImageIO.write(out, "png", o);
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
