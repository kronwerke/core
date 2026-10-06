#!/usr/bin/env python3
"""Draws the textures of the obelisk's own masonry and the blocks of its grounds.

  plinth_side, plinth_top   the stepped plinth: carved panels with a brass crown, slabs on top
  trunk                      the monolith: large dark panels with corner pins and a mortar line
  shaft, cap, band, funnel   the tip, its cap, the brass band, the intake's mouth
  pedestal_side, board       the pedestals and the leaderboard wall
  paving, flagstone, shard   the grounds: engraved slabs that carry a faint rune glow, the ring
                             of flagstones, and the crystal that tops the arches

Same colours as tiers.py, same 16 by 16 pixels, same way of writing models. Same input,
same files.
"""
import math
import os
import random

from PIL import Image

from tiers import ASSETS, BRASS, BRASS_DARK, BRASS_LIGHT, CYAN, CYAN_DEEP, SLATE, SLATE_DARK, VIOLET, VIOLET_DEEP
from tiers import brass_line, clamp, empty, mix, model, put, same, save, stone

SLATE_LIGHT = (48, 43, 55)
GROUT = (14, 12, 17)


def bevel(im, x0, y0, x1, y1, light=0.16, dark=0.32, inset=False):
    """Light on the top and left edge, dark on the bottom and right; inset swaps them so the
    area reads as sunk into the stone."""
    px = im.load()
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            top_left = x == x0 or y == y0
            bottom_right = x == x1 or y == y1
            if not (top_left or bottom_right):
                continue
            c = px[x, y][:3]
            if inset:
                top_left, bottom_right = bottom_right, top_left
            if bottom_right and not top_left:
                c = mix(c, (0, 0, 0), dark)
            elif top_left:
                c = mix(c, (255, 255, 255), light)
            px[x, y] = clamp(c) + (255,)


def fill(im, x0, y0, x1, y1, base, seed, amount=3):
    rnd = random.Random(seed)
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            n = rnd.randint(-amount, amount)
            put(im, x, y, (base[0] + n, base[1] + n, base[2] + n))


def chisel(im, seed, count, base):
    """A few short diagonal chisel marks, a light pixel with a dark one under it."""
    rnd = random.Random(seed)
    px = im.load()
    for _ in range(count):
        x, y = rnd.randint(1, 13), rnd.randint(1, 13)
        if px[x, y][:3] != base and abs(px[x, y][0] - base[0]) > 6:
            continue
        put(im, x, y, mix(base, (255, 255, 255), 0.1))
        put(im, x + 1, y + 1, mix(base, (0, 0, 0), 0.25))


# ---- the plinth ----

def plinth_side():
    im = stone(101, SLATE, 3, bevel=False)
    # a brass crown along the top, as before, so the steps keep their lines
    brass_line(im, 0, 0, 15, 1)
    # a sunk panel with a raised frame
    fill(im, 2, 3, 13, 14, SLATE_LIGHT, 102, 2)
    bevel(im, 2, 3, 13, 14, light=0.14, dark=0.3)
    fill(im, 4, 5, 11, 12, SLATE_DARK, 103, 2)
    bevel(im, 4, 5, 11, 12, light=0.12, dark=0.3, inset=True)
    chisel(im, 104, 5, SLATE_DARK)
    # the block's own edges
    bevel(im, 0, 2, 15, 15, light=0.1, dark=0.35)
    return im


def plinth_top():
    im = Image.new("RGBA", (16, 16))
    fill(im, 0, 0, 15, 15, GROUT, 111, 1)
    # four slabs with a grout cross, each slab bevelled
    for sx, sy in ((0, 0), (8, 0), (0, 8), (8, 8)):
        fill(im, sx, sy, sx + 6, sy + 6, SLATE, 112 + sx + sy, 3)
        bevel(im, sx, sy, sx + 6, sy + 6, light=0.14, dark=0.3)
    # brass rivets where the grout lines meet and at the corners of the block
    for x, y in ((7, 7), (7, 15), (15, 7), (15, 15)):
        put(im, x, y, BRASS)
    chisel(im, 113, 4, SLATE)
    return im


# ---- the trunk and the tip ----

def trunk():
    im = stone(121, SLATE, 3, bevel=False)
    # a mortar line along the bottom and the right, so the trunk reads as fitted stone
    for i in range(16):
        put(im, i, 15, GROUT)
        put(im, 15, i, GROUT)
    bevel(im, 0, 0, 14, 14, light=0.12, dark=0.28)
    # brass pins in the corners of each panel
    for x, y in ((1, 1), (13, 1), (1, 13), (13, 13)):
        put(im, x, y, BRASS_DARK)
    # a faint vertical seam with a cold light deep in it
    for y in range(3, 12):
        put(im, 7, y, mix(SLATE_DARK, CYAN_DEEP, 0.18 + 0.1 * math.sin(y)))
        put(im, 8, y, mix(SLATE, (0, 0, 0), 0.25))
    chisel(im, 122, 6, SLATE)
    return im


def shaft():
    """The tip's shaft is a 12 wide box; its texture keeps brass edges where the box ends."""
    im = stone(131, SLATE_DARK, 3, bevel=False)
    brass_line(im, 2, 0, 3, 15)
    brass_line(im, 12, 0, 13, 15)
    for y in range(16):
        put(im, 7, y, mix(SLATE_DARK, CYAN_DEEP, 0.35 + 0.15 * math.sin(y / 1.5)))
        put(im, 8, y, mix(SLATE_DARK, CYAN_DEEP, 0.2))
    bevel(im, 4, 0, 11, 15, light=0.1, dark=0.25)
    return im


def cap():
    im = stone(141, SLATE_DARK, 3, bevel=False)
    brass_line(im, 0, 0, 15, 1)
    brass_line(im, 0, 14, 15, 15)
    brass_line(im, 0, 0, 1, 15)
    brass_line(im, 14, 0, 15, 15)
    fill(im, 4, 4, 11, 11, SLATE, 142, 2)
    bevel(im, 4, 4, 11, 11, light=0.12, dark=0.3, inset=True)
    for x, y in ((7, 7), (8, 7), (7, 8), (8, 8)):
        put(im, x, y, BRASS_LIGHT)
    return im


def band():
    """Horizontal brass bars, each with a light upper edge and a dark lower one."""
    im = Image.new("RGBA", (16, 16))
    rnd = random.Random(151)
    for y in range(16):
        bar = y % 4
        for x in range(16):
            n = rnd.randint(-5, 5)
            c = BRASS_LIGHT if bar == 0 else BRASS if bar in (1, 2) else BRASS_DARK
            c = (c[0] + n, c[1] + n, c[2] + n)
            if x in (0, 15):
                c = mix(c, (0, 0, 0), 0.2)
            put(im, x, y, c)
    return im


def funnel():
    im = Image.new("RGBA", (16, 16))
    fill(im, 0, 0, 15, 15, BRASS, 161, 6)
    bevel(im, 0, 0, 15, 15, light=0.2, dark=0.35)
    fill(im, 3, 3, 12, 12, SLATE_DARK, 162, 2)
    bevel(im, 3, 3, 12, 12, light=0.1, dark=0.35, inset=True)
    fill(im, 6, 6, 9, 9, GROUT, 163, 1)
    for x, y in ((7, 7), (8, 8)):
        put(im, x, y, mix(GROUT, CYAN_DEEP, 0.5))
    return im


# ---- pedestal and board ----

def pedestal_side():
    im = stone(171, SLATE, 3, bevel=False)
    brass_line(im, 0, 0, 15, 1)
    # a niche with a round top, sunk into the stone
    for y in range(4, 15):
        half = 3 if y > 6 else (2 if y > 4 else 1)
        for x in range(8 - half - 1, 8 + half + 1):
            put(im, x, y, mix(SLATE_DARK, (0, 0, 0), 0.2))
    for y in range(5, 15):
        half = 3 if y > 7 else (2 if y > 5 else 1)
        for x in range(8 - half, 8 + half):
            put(im, x, y, mix(SLATE_DARK, CYAN_DEEP, 0.06 + 0.1 * (y - 5) / 10))
    bevel(im, 0, 2, 15, 15, light=0.1, dark=0.35)
    return im


def board():
    im = stone(181, SLATE_DARK, 2, bevel=False)
    brass_line(im, 0, 0, 1, 15)
    brass_line(im, 14, 0, 15, 15)
    brass_line(im, 0, 0, 15, 1)
    # a smooth dark face for the carved gold letters, framed by a light groove
    fill(im, 3, 3, 12, 15, SLATE_DARK, 182, 1)
    bevel(im, 3, 3, 12, 15, light=0.1, dark=0.3, inset=True)
    return im


# ---- the grounds ----

RUNE = [
    "................",
    "................",
    "................",
    "......####......",
    ".....#....#.....",
    ".....#....#.....",
    "......####......",
    ".......##.......",
    ".......##.......",
    ".....######.....",
    "....#..##..#....",
    "....#..##..#....",
    ".......##.......",
    "................",
    "................",
    "................",
]


def paving():
    """A single engraved slab for the inner ring of the pavement. The rune is cut into it and
    glows faintly through the glow layer, so the pavement carries a little of the stone's light."""
    im = Image.new("RGBA", (16, 16))
    fill(im, 0, 0, 15, 15, GROUT, 191, 1)
    fill(im, 1, 1, 14, 14, SLATE, 192, 3)
    bevel(im, 1, 1, 14, 14, light=0.14, dark=0.3)
    glow = empty()
    for y in range(16):
        for x in range(16):
            if RUNE[y][x] == "#":
                put(im, x, y, mix(SLATE_DARK, (0, 0, 0), 0.35))
                put(glow, x, y, mix(CYAN_DEEP, SLATE_DARK, 0.25), 150)
    chisel(im, 193, 4, SLATE)
    return im, glow


def flagstone():
    """Two courses of flagstones, the lower course offset by half a stone."""
    im = Image.new("RGBA", (16, 16))
    fill(im, 0, 0, 15, 15, GROUT, 201, 1)
    stones = [(0, 0, 7, 6), (9, 0, 15, 6), (0, 8, 3, 15), (5, 8, 11, 15), (13, 8, 15, 15)]
    for i, (x0, y0, x1, y1) in enumerate(stones):
        fill(im, x0, y0, x1, y1, mix(SLATE, SLATE_LIGHT, 0.3 if i % 2 else 0.0), 202 + i, 3)
        bevel(im, x0, y0, x1, y1, light=0.13, dark=0.3)
    chisel(im, 210, 5, SLATE)
    return im


def shard(frames=8):
    """The crystal that tops the arches: violet glass with cyan facets and a light that slides
    across it, like the obelisk's own crystal but darker, as stone that has turned to glass."""
    base = Image.new("RGBA", (16, 16))
    rnd = random.Random(221)
    # facets: a few diagonal bands
    for y in range(16):
        for x in range(16):
            band_i = ((x + y) // 5) % 3
            c = (VIOLET_DEEP, mix(VIOLET_DEEP, VIOLET, 0.5), mix(VIOLET_DEEP, CYAN_DEEP, 0.35))[band_i]
            n = rnd.randint(-6, 6)
            c = (c[0] + n, c[1] + n, c[2] + n)
            # the facet edges catch the light
            if (x + y) % 5 == 0:
                c = mix(c, (255, 255, 255), 0.25)
            put(base, x, y, c)
    bevel(base, 0, 0, 15, 15, light=0.2, dark=0.35)
    strip = Image.new("RGBA", (16, 16 * frames))
    for f in range(frames):
        glow = empty()
        shift = f * 16 / frames
        for y in range(16):
            for x in range(16):
                s = ((x - y + shift) % 16) / 16.0
                shine = max(0.0, 1 - abs(s - 0.5) * 4)
                if shine > 0.1:
                    c = mix(VIOLET, CYAN, 0.5 + 0.5 * shine)
                    put(glow, x, y, c, int(90 + 130 * shine))
                elif (x + y) % 5 == 0:
                    put(glow, x, y, mix(VIOLET, CYAN, 0.4), 70)
        strip.paste(glow, (0, f * 16))
    return base, strip


def plain_model(name, textures):
    """A cube without a glow layer."""
    import json
    out = os.path.join(ASSETS, "models", "block")
    with open(os.path.join(out, name + ".json"), "w") as f:
        json.dump({"parent": "minecraft:block/cube_bottom_top", "textures": {"top": "kronwerke:block/" + textures["top"],
                                                                                 "side": "kronwerke:block/" + textures["side"],
                                                                                 "bottom": "kronwerke:block/" + textures["bottom"]}}, f, indent=1)
    with open(os.path.join(ASSETS, "blockstates", name + ".json"), "w") as f:
        json.dump({"variants": {"": {"model": "kronwerke:block/" + name}}}, f, indent=1)
    with open(os.path.join(ASSETS, "models", "item", name + ".json"), "w") as f:
        json.dump({"parent": "kronwerke:block/" + name}, f, indent=1)


def write_plain(name, im):
    im.save(os.path.join(ASSETS, "textures", "block", name + ".png"))


if __name__ == "__main__":
    write_plain("obelisk_plinth_side", plinth_side())
    write_plain("obelisk_plinth_top", plinth_top())
    write_plain("obelisk_trunk", trunk())
    write_plain("obelisk_shaft", shaft())
    write_plain("obelisk_cap", cap())
    write_plain("obelisk_band", band())
    write_plain("obelisk_funnel", funnel())
    write_plain("obelisk_pedestal_side", pedestal_side())
    write_plain("obelisk_board", board())

    im, g = paving()
    save("obelisk_paving", im, g)
    model("obelisk_paving", same("obelisk_paving"), same("obelisk_paving_glow"), 2)

    write_plain("obelisk_flagstone", flagstone())
    plain_model("obelisk_flagstone", same("obelisk_flagstone"))

    im, g = shard()
    save("obelisk_shard", im, g, glow_frametime=3)
    model("obelisk_shard", same("obelisk_shard"), same("obelisk_shard_glow"), 15)
    print("ok", os.path.abspath(ASSETS))
