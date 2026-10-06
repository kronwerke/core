#!/usr/bin/env python3
"""Draws the animated textures of the obelisk: the rune band whose glyph glows and lets a
light run through it, and the crystal that shimmers. Minecraft plays a texture that is a
vertical strip of frames when a .mcmeta next to it says so. Same input, same file.
"""
import json
import os
import random

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "..", "src", "main", "resources", "assets", "kronwerke", "textures", "block")

SLATE = (31, 27, 36)
SLATE_DARK = (22, 19, 26)
CYAN = (127, 230, 255)
CYAN_DEEP = (40, 150, 190)
GOLD = (212, 162, 74)

# the glyph of the runes, 16 x 16, "." is stone, "#" the rune, "o" its soft edge
GLYPH = [
    "................",
    "......oooo......",
    ".....o####o.....",
    ".....o#..#o.....",
    ".....o#..#o.....",
    "......o##o......",
    "......o##o......",
    ".....o####o.....",
    "....o#o..o#o....",
    "....o#....#o....",
    "....o#o..o#o....",
    ".....o####o.....",
    "......o##o......",
    "......o##o......",
    ".......oo.......",
    "................",
]


def stone(seed):
    rnd = random.Random(seed)
    im = Image.new("RGBA", (16, 16))
    px = im.load()
    for y in range(16):
        for x in range(16):
            n = rnd.randint(-4, 4)
            c = SLATE_DARK if (x in (0, 15) or y in (0, 15)) else SLATE
            px[x, y] = (c[0] + n, c[1] + n, c[2] + n, 255)
    return im


def mix(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def runes(frames=12):
    """The glyph pulses, and a bright band runs down it once per cycle."""
    strip = Image.new("RGBA", (16, 16 * frames))
    for f in range(frames):
        im = stone(3)
        px = im.load()
        phase = f / frames
        pulse = 0.55 + 0.45 * (0.5 + 0.5 * __import__("math").sin(phase * 6.283))
        band_y = phase * 20 - 2
        for y in range(16):
            for x in range(16):
                ch = GLYPH[y][x]
                if ch == ".":
                    continue
                d = abs(y - band_y)
                band = max(0.0, 1 - d / 3.0)
                if ch == "#":
                    c = mix(CYAN_DEEP, CYAN, min(1.0, pulse * 0.8 + band))
                    if band > 0.6:
                        c = mix(c, (235, 255, 255), band - 0.6)
                    px[x, y] = c + (255,)
                else:
                    c = mix(SLATE, CYAN_DEEP, 0.35 * pulse + 0.4 * band)
                    px[x, y] = c + (255,)
        strip.paste(im, (0, f * 16))
    return strip


def crystal(frames=8):
    """Cyan glass with a shimmer that slides across."""
    strip = Image.new("RGBA", (16, 16 * frames))
    rnd = random.Random(5)
    base = [[rnd.randint(-10, 10) for _ in range(16)] for _ in range(16)]
    for f in range(frames):
        im = Image.new("RGBA", (16, 16))
        px = im.load()
        shift = f * 16 / frames
        for y in range(16):
            for x in range(16):
                # a diagonal shimmer that wraps
                s = ((x + y + shift) % 16) / 16.0
                shine = max(0.0, 1 - abs(s - 0.5) * 5)
                t = 0.35 + 0.25 * ((x - 8) ** 2 + (y - 8) ** 2) / 128.0
                c = mix(CYAN, (210, 250, 255), shine * 0.9)
                c = mix(c, (60, 170, 210), t)
                n = base[y][x]
                c = tuple(max(0, min(255, c[i] + n)) for i in range(3))
                a = 235 if shine < 0.5 else 250
                if x in (0, 15) or y in (0, 15):
                    c = mix(c, (235, 255, 255), 0.5)
                px[x, y] = c + (a,)
        strip.paste(im, (0, f * 16))
    return strip


# four small glyphs for the rune particle, 8 x 8, cyan on nothing
PARTICLE_GLYPHS = [
    ["..####..", ".#....#.", ".#....#.", "..####..", "...##...", "..#..#..", ".#....#.", "........"],
    ["...##...", "..#..#..", ".#....#.", "#......#", ".#....#.", "..#..#..", "...##...", "........"],
    ["#......#", ".#....#.", "..#..#..", "...##...", "...##...", "..#..#..", ".#....#.", "#......#"],
    ["..####..", ".#....#.", "#..##..#", "#.#..#.#", "#.#..#.#", "#..##..#", ".#....#.", "..####.."],
]


def particle_glyph(rows):
    im = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
    px = im.load()
    for y in range(8):
        for x in range(8):
            if rows[y][x] == "#":
                px[x, y] = (200, 250, 255, 255)
    # a soft halo one pixel around the glyph
    out = im.copy()
    op = out.load()
    for y in range(8):
        for x in range(8):
            if im.getpixel((x, y))[3] == 0:
                near = any(0 <= x + dx < 8 and 0 <= y + dy < 8 and im.getpixel((x + dx, y + dy))[3] > 0 for dx in (-1, 0, 1) for dy in (-1, 0, 1))
                if near:
                    op[x, y] = (120, 220, 255, 110)
    return out


def ring_glyph(rows, size):
    """One particle glyph blown up to size pixels, white with a soft halo, for the ground rings."""
    im = particle_glyph(rows).resize((size, size), Image.NEAREST)
    px = im.load()
    for y in range(size):
        for x in range(size):
            r, g, b, a = px[x, y]
            if a:
                px[x, y] = (255, 255, 255, a)
    return im


def rune_rings(size=512):
    """Two circles of light drawn on the pavement around the obelisk, white so the game can
    tint them. The outer one carries glyphs, the inner one thin lines and ticks; they turn
    against each other in the renderer. The texture covers 16 by 16 blocks, so a block is
    size / 16 pixels."""
    import math
    from PIL import ImageDraw
    block = size / 16.0
    outer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(outer)
    c = size / 2.0

    def circle(im_draw, radius, width, alpha):
        im_draw.ellipse([c - radius, c - radius, c + radius, c + radius], outline=(255, 255, 255, alpha), width=width)

    circle(d, 5.95 * block, max(2, int(block * 0.2)), 255)
    circle(d, 4.95 * block, max(1, int(block * 0.1)), 190)
    glyph = int(block * 1.0)
    for i in range(20):
        a = i / 20.0 * 2 * math.pi
        g = ring_glyph(PARTICLE_GLYPHS[i % 4], glyph).rotate(-math.degrees(a) - 90, resample=Image.NEAREST, expand=True)
        r = 5.5 * block
        x = c + math.cos(a) * r - g.width / 2
        y = c + math.sin(a) * r - g.height / 2
        outer.alpha_composite(g, (int(x), int(y)))
    inner = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(inner)
    circle(d, 4.3 * block, max(1, int(block * 0.14)), 240)
    circle(d, 3.2 * block, max(1, int(block * 0.08)), 170)
    for i in range(36):
        a = i / 36.0 * 2 * math.pi
        long_tick = i % 3 == 0
        r0 = (4.3 - (0.45 if long_tick else 0.2)) * block
        r1 = 4.3 * block
        d.line([c + math.cos(a) * r0, c + math.sin(a) * r0, c + math.cos(a) * r1, c + math.sin(a) * r1], fill=(255, 255, 255, 240 if long_tick else 170), width=max(1, int(block * 0.1)))
    for i in range(8):
        a = i / 8.0 * 2 * math.pi + math.pi / 8
        r = 3.75 * block
        g = ring_glyph(PARTICLE_GLYPHS[(i + 2) % 4], int(block * 0.6)).rotate(-math.degrees(a) - 90, resample=Image.NEAREST, expand=True)
        inner.alpha_composite(g, (int(c + math.cos(a) * r - g.width / 2), int(c + math.sin(a) * r - g.height / 2)))
    return halo(outer, block), halo(inner, block)


def halo(im, block):
    """A soft glow under the sharp lines, so the rings read as light on stone, not as paint."""
    from PIL import ImageFilter
    glow = im.filter(ImageFilter.GaussianBlur(block * 0.5))
    px = glow.load()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            px[x, y] = (255, 255, 255, min(255, int(a * 2.4)))
    glow.alpha_composite(im)
    return glow


def write(name, im, frametime, interpolate=True):
    os.makedirs(OUT, exist_ok=True)
    im.save(os.path.join(OUT, name + ".png"), optimize=True)
    with open(os.path.join(OUT, name + ".png.mcmeta"), "w") as f:
        json.dump({"animation": {"frametime": frametime, "interpolate": interpolate}}, f, indent=2)


if __name__ == "__main__":
    write("obelisk_runes", runes(), 4)
    write("obelisk_crystal", crystal(), 3)
    pdir = os.path.join(OUT, "..", "particle")
    os.makedirs(pdir, exist_ok=True)
    for i, rows in enumerate(PARTICLE_GLYPHS):
        particle_glyph(rows).save(os.path.join(pdir, f"rune_{i}.png"))
    edir = os.path.join(OUT, "..", "effect")
    os.makedirs(edir, exist_ok=True)
    outer, inner = rune_rings()
    outer.save(os.path.join(edir, "rune_ring_outer.png"), optimize=True)
    inner.save(os.path.join(edir, "rune_ring_inner.png"), optimize=True)
    print("ok", OUT)
