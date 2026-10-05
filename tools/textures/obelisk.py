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


def write(name, im, frametime, interpolate=True):
    os.makedirs(OUT, exist_ok=True)
    im.save(os.path.join(OUT, name + ".png"), optimize=True)
    with open(os.path.join(OUT, name + ".png.mcmeta"), "w") as f:
        json.dump({"animation": {"frametime": frametime, "interpolate": interpolate}}, f, indent=2)


if __name__ == "__main__":
    write("obelisk_runes", runes(), 4)
    write("obelisk_crystal", crystal(), 3)
    print("ok", OUT)
