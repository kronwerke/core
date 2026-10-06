#!/usr/bin/env python3
"""The blocks the obelisk grows with every stage: textures, a glow layer for the parts that
shine in the dark, models and blockstates. Each block has a base texture that takes light
like any stone and a <name>_glow texture that is transparent except for the parts that
glow; the model draws the glow as a second cube at full brightness. Same input, same file.

  obelisk_brass    brass inlay, lines through the paving
  obelisk_pylon    a dark column with brass bands and a cyan core
  obelisk_lantern  a brass cage around cyan glass, pulsing
  obelisk_ember    blackstone with crimson veins that breathe
  obelisk_arch     slate with amethyst veins
  obelisk_crown    gilded stone with glyphs that glint
"""
import json
import math
import os
import random

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "..", "..", "src", "main", "resources", "assets", "kronwerke")

SLATE = (31, 27, 36)
SLATE_DARK = (20, 17, 24)
BLACK = (22, 19, 22)
BRASS = (212, 162, 74)
BRASS_DARK = (138, 100, 32)
BRASS_LIGHT = (246, 214, 140)
CYAN = (127, 230, 255)
CYAN_DEEP = (40, 150, 190)
CRIMSON = (230, 70, 60)
CRIMSON_DEEP = (120, 25, 30)
VIOLET = (170, 120, 230)
VIOLET_DEEP = (90, 50, 140)
GOLD = (222, 178, 92)
GOLD_DARK = (150, 110, 40)
GOLD_LIGHT = (255, 232, 170)


def clamp(c):
    return tuple(max(0, min(255, int(v))) for v in c)


def mix(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def stone(seed, base, amount=4, bevel=True):
    """A 16 px stone with grain and, when asked, a light top left edge and a dark bottom right edge."""
    rnd = random.Random(seed)
    im = Image.new("RGBA", (16, 16))
    px = im.load()
    for y in range(16):
        for x in range(16):
            n = rnd.randint(-amount, amount)
            # a few larger patches so it is not only grain
            patch = 3 if ((x * 7 + y * 13 + seed) % 11) < 2 else 0
            c = (base[0] + n + patch, base[1] + n + patch, base[2] + n + patch)
            if bevel:
                if x == 0 or y == 0:
                    c = mix(c, (255, 255, 255), 0.12)
                if x == 15 or y == 15:
                    c = mix(c, (0, 0, 0), 0.3)
            px[x, y] = clamp(c) + (255,)
    return im


def empty():
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


def put(im, x, y, c, a=255):
    if 0 <= x < 16 and 0 <= y < 16:
        im.putpixel((x, y), clamp(c) + (a,))


def brass_line(im, x0, y0, x1, y1):
    """A two pixel brass band with a dark seam and a light edge."""
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            edge = x == x0 or y == y0
            far = x == x1 or y == y1
            c = BRASS_LIGHT if edge else BRASS_DARK if far else BRASS
            put(im, x, y, c)


# ---- brass inlay ----

def brass():
    im = stone(11, SLATE)
    brass_line(im, 0, 7, 15, 8)
    brass_line(im, 7, 0, 8, 15)
    # rivets where the lines cross and at the ends
    for x, y in ((7, 7), (8, 8), (7, 8), (8, 7)):
        put(im, x, y, BRASS_LIGHT)
    return im, empty()


# ---- pylon ----

def pylon_side():
    im = stone(21, SLATE_DARK)
    brass_line(im, 0, 0, 15, 1)
    brass_line(im, 0, 14, 15, 15)
    brass_line(im, 0, 7, 15, 7)
    glow = empty()
    for y in range(2, 14):
        for x in (7, 8):
            put(im, x, y, CYAN_DEEP)
            put(glow, x, y, mix(CYAN_DEEP, CYAN, 0.6 + 0.4 * math.sin(y / 2.0)))
        put(im, 6, y, mix(SLATE_DARK, CYAN_DEEP, 0.4))
        put(im, 9, y, mix(SLATE_DARK, CYAN_DEEP, 0.4))
    return im, glow


def pylon_top():
    im = stone(22, SLATE_DARK)
    brass_line(im, 0, 0, 15, 1)
    brass_line(im, 0, 14, 15, 15)
    brass_line(im, 0, 0, 1, 15)
    brass_line(im, 14, 0, 15, 15)
    glow = empty()
    for y in range(6, 10):
        for x in range(6, 10):
            put(im, x, y, CYAN_DEEP)
            put(glow, x, y, CYAN)
    return im, glow


# ---- lantern, animated glow ----

def lantern(frames=8):
    im = stone(31, BRASS_DARK, 6, bevel=False)
    px = im.load()
    for y in range(16):
        for x in range(16):
            frame = x < 2 or x > 13 or y < 2 or y > 13 or x in (7, 8) or y in (7, 8)
            if frame:
                c = BRASS_LIGHT if (x in (0, 15) or y in (0, 15)) else BRASS if (x + y) % 2 else BRASS_DARK
                px[x, y] = clamp(c) + (255,)
            else:
                px[x, y] = clamp(CYAN_DEEP) + (255,)
    strip = Image.new("RGBA", (16, 16 * frames), (0, 0, 0, 0))
    for f in range(frames):
        g = empty()
        pulse = 0.6 + 0.4 * math.sin(f / frames * math.pi * 2)
        for y in range(16):
            for x in range(16):
                frame = x < 2 or x > 13 or y < 2 or y > 13 or x in (7, 8) or y in (7, 8)
                if frame:
                    continue
                d = math.hypot(x - 7.5, y - 7.5)
                c = mix(CYAN, (240, 255, 255), max(0.0, 1 - d / 4) * pulse)
                c = mix(CYAN_DEEP, c, pulse)
                put(g, x, y, c)
        strip.paste(g, (0, f * 16))
    return im, strip


# ---- ember, animated veins ----

def veins(seed, count, length):
    """Random walks over the texture, as a set of pixels with their distance along the vein."""
    rnd = random.Random(seed)
    out = {}
    for v in range(count):
        x, y = rnd.randint(0, 15), rnd.randint(0, 15)
        dx, dy = rnd.choice([(1, 0), (0, 1), (1, 1), (-1, 1)])
        for i in range(length):
            out[(x % 16, y % 16)] = i / length
            if rnd.random() < 0.3:
                dx, dy = rnd.choice([(1, 0), (0, 1), (-1, 0), (0, -1), (1, 1), (-1, 1)])
            x, y = x + dx, y + dy
    return out


def ember(frames=10):
    im = stone(41, BLACK, 5)
    vs = veins(42, 5, 9)
    for (x, y), t in vs.items():
        put(im, x, y, CRIMSON_DEEP)
    strip = Image.new("RGBA", (16, 16 * frames), (0, 0, 0, 0))
    for f in range(frames):
        g = empty()
        phase = f / frames
        for (x, y), t in vs.items():
            glow = 0.5 + 0.5 * math.sin((t - phase) * math.pi * 2)
            put(g, x, y, mix(CRIMSON_DEEP, CRIMSON, glow), int(140 + 100 * glow))
        strip.paste(g, (0, f * 16))
    return im, strip


# ---- arch ----

def arch():
    im = stone(51, SLATE, 4)
    glow = empty()
    vs = veins(52, 4, 7)
    for (x, y), t in vs.items():
        put(im, x, y, VIOLET_DEEP)
        put(glow, x, y, mix(VIOLET_DEEP, VIOLET, 0.5), 160)
    rnd = random.Random(53)
    for _ in range(5):
        x, y = rnd.randint(1, 14), rnd.randint(1, 14)
        put(im, x, y, VIOLET)
        put(glow, x, y, VIOLET)
    return im, glow


# ---- crown, glint ----

def crown(frames=12):
    im = stone(61, GOLD, 7, bevel=False)
    px = im.load()
    for y in range(16):
        for x in range(16):
            if x % 8 == 0 or y % 8 == 0:
                px[x, y] = clamp(GOLD_DARK) + (255,)
            elif x == 15 or y == 15 or x % 8 == 7 or y % 8 == 7:
                px[x, y] = clamp(mix(GOLD, GOLD_DARK, 0.5)) + (255,)
            elif x % 8 == 1 or y % 8 == 1:
                px[x, y] = clamp(GOLD_LIGHT) + (255,)
    glyphs = [(3, 3), (4, 4), (11, 3), (12, 4), (3, 11), (4, 12), (11, 11), (12, 12), (3, 4), (11, 4), (3, 12), (11, 12)]
    for x, y in glyphs:
        put(im, x, y, GOLD_DARK)
    strip = Image.new("RGBA", (16, 16 * frames), (0, 0, 0, 0))
    for f in range(frames):
        g = empty()
        sweep = f / frames * 32 - 8
        for x, y in glyphs:
            d = abs((x + y) - sweep)
            glint = max(0.0, 1 - d / 4)
            put(g, x, y, mix(GOLD_LIGHT, (255, 255, 240), glint), int(90 + 165 * glint))
        strip.paste(g, (0, f * 16))
    return im, strip


# ---- writing ----

def save(name, im, glow, frametime=None, glow_frametime=None):
    tex = os.path.join(ASSETS, "textures", "block")
    os.makedirs(tex, exist_ok=True)
    im.save(os.path.join(tex, name + ".png"))
    glow.save(os.path.join(tex, name + "_glow.png"))
    if glow_frametime:
        with open(os.path.join(tex, name + "_glow.png.mcmeta"), "w") as f:
            json.dump({"animation": {"frametime": glow_frametime, "interpolate": True}}, f, indent=2)


def model(name, textures, glow_textures, light):
    """textures: dict face -> texture name for the base cube; glow_textures the same for the glow cube."""
    def faces(prefix, cull):
        return {side: {"texture": "#" + prefix + "_" + key, "cullface": side} for side, key in
                (("north", "side"), ("south", "side"), ("east", "side"), ("west", "side"), ("up", "top"), ("down", "bottom"))}

    m = {
        "parent": "minecraft:block/block",
        "render_type": "minecraft:cutout",
        "textures": {"particle": "kronwerke:block/" + textures["side"]},
        "elements": [
            {"from": [0, 0, 0], "to": [16, 16, 16], "faces": faces("base", True)},
            {"from": [0, 0, 0], "to": [16, 16, 16], "neoforge_data": {"block_light": light, "sky_light": 15}, "faces": faces("glow", True)},
        ],
    }
    for key in ("side", "top", "bottom"):
        m["textures"]["base_" + key] = "kronwerke:block/" + textures[key]
        m["textures"]["glow_" + key] = "kronwerke:block/" + glow_textures[key]
    out = os.path.join(ASSETS, "models", "block")
    os.makedirs(out, exist_ok=True)
    with open(os.path.join(out, name + ".json"), "w") as f:
        json.dump(m, f, indent=1)
    bs = os.path.join(ASSETS, "blockstates")
    os.makedirs(bs, exist_ok=True)
    with open(os.path.join(bs, name + ".json"), "w") as f:
        json.dump({"variants": {"": {"model": "kronwerke:block/" + name}}}, f, indent=1)
    item = os.path.join(ASSETS, "models", "item")
    os.makedirs(item, exist_ok=True)
    with open(os.path.join(item, name + ".json"), "w") as f:
        json.dump({"parent": "kronwerke:block/" + name}, f, indent=1)


def same(name):
    return {"side": name, "top": name, "bottom": name}


if __name__ == "__main__":
    im, g = brass()
    save("obelisk_brass", im, g)
    model("obelisk_brass", same("obelisk_brass"), same("obelisk_brass_glow"), 0)

    im, g = pylon_side()
    save("obelisk_pylon", im, g)
    im2, g2 = pylon_top()
    save("obelisk_pylon_top", im2, g2)
    model("obelisk_pylon", {"side": "obelisk_pylon", "top": "obelisk_pylon_top", "bottom": "obelisk_pylon_top"},
          {"side": "obelisk_pylon_glow", "top": "obelisk_pylon_top_glow", "bottom": "obelisk_pylon_top_glow"}, 15)

    im, g = lantern()
    save("obelisk_lantern", im, g, glow_frametime=3)
    model("obelisk_lantern", same("obelisk_lantern"), same("obelisk_lantern_glow"), 15)

    im, g = ember()
    save("obelisk_ember", im, g, glow_frametime=4)
    model("obelisk_ember", same("obelisk_ember"), same("obelisk_ember_glow"), 15)

    im, g = arch()
    save("obelisk_arch", im, g)
    model("obelisk_arch", same("obelisk_arch"), same("obelisk_arch_glow"), 15)

    im, g = crown()
    save("obelisk_crown", im, g, glow_frametime=3)
    model("obelisk_crown", same("obelisk_crown"), same("obelisk_crown_glow"), 15)
    print("ok", os.path.abspath(ASSETS))
