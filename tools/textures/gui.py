#!/usr/bin/env python3
"""Draws the GUI sprites of the Kronwerke screens into assets/kronwerke/textures/gui/sprites.

Minecraft 1.21 stitches everything under textures/gui/sprites into one atlas and draws a
sprite with GuiGraphics.blitSprite; a .mcmeta next to it with gui.scaling nine_slice makes
the corners stay sharp whatever size the screen asks for. The palette is the obelisk's:
dark slate, brass and gold, a little cyan and purple. Same input, same file.
"""
import json
import os
import random

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "..", "src", "main", "resources", "assets", "kronwerke", "textures", "gui", "sprites")

SLATE = (24, 20, 30)
SLATE_DARK = (16, 13, 20)
SLATE_LIGHT = (36, 30, 44)
BRASS = (212, 162, 74)
BRASS_DARK = (138, 100, 32)
BRASS_LIGHT = (246, 214, 140)
SHADOW = (8, 6, 10)
RED = (139, 47, 58)
RED_LIGHT = (184, 74, 86)
PURPLE = (154, 111, 214)


def grain(im, amount=4, seed=1):
    rnd = random.Random(seed)
    px = im.load()
    w, h = im.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            n = rnd.randint(-amount, amount)
            px[x, y] = (max(0, min(255, r + n)), max(0, min(255, g + n)), max(0, min(255, b + n)), a)
    return im


def nine_slice(name, im, border):
    os.makedirs(OUT, exist_ok=True)
    im.save(os.path.join(OUT, name + ".png"), optimize=True)
    meta = {"gui": {"scaling": {"type": "nine_slice", "width": im.width, "height": im.height, "border": border}}}
    with open(os.path.join(OUT, name + ".png.mcmeta"), "w") as f:
        json.dump(meta, f, indent=2)


def plain(name, im):
    os.makedirs(OUT, exist_ok=True)
    im.save(os.path.join(OUT, name + ".png"), optimize=True)


def panel():
    """The big frame: slate with a brass border and a small diamond in every corner."""
    s = 48
    im = Image.new("RGBA", (s, s), SLATE + (248,))
    grain(im, 3, 2)
    d = ImageDraw.Draw(im)
    # shadow, outer frame, inner line
    d.rectangle((0, 0, s - 1, s - 1), outline=SHADOW + (255,))
    d.rectangle((1, 1, s - 2, s - 2), outline=BRASS_DARK + (255,))
    d.rectangle((2, 2, s - 3, s - 3), outline=BRASS + (255,))
    d.rectangle((3, 3, s - 4, s - 4), outline=BRASS_DARK + (255,))
    d.rectangle((5, 5, s - 6, s - 6), outline=SLATE_LIGHT + (255,))
    # corner diamonds
    for cx, cy in ((7, 7), (s - 8, 7), (7, s - 8), (s - 8, s - 8)):
        d.polygon([(cx, cy - 3), (cx + 3, cy), (cx, cy + 3), (cx - 3, cy)], fill=BRASS + (255,), outline=BRASS_DARK + (255,))
        d.point((cx, cy), fill=BRASS_LIGHT + (255,))
    nine_slice("panel", im, 12)


def inset():
    """A darker field inside the panel, for lists and cards."""
    s = 16
    im = Image.new("RGBA", (s, s), SLATE_DARK + (255,))
    grain(im, 2, 3)
    d = ImageDraw.Draw(im)
    d.rectangle((0, 0, s - 1, s - 1), outline=SHADOW + (255,))
    d.line((1, s - 1, s - 1, s - 1), fill=SLATE_LIGHT + (255,))
    d.line((s - 1, 1, s - 1, s - 1), fill=SLATE_LIGHT + (255,))
    nine_slice("inset", im, 4)


def card(name, fill, edge, top):
    """A raised field: a light line at the top, a dark one at the bottom."""
    s = 16
    im = Image.new("RGBA", (s, s), fill + (255,))
    grain(im, 2, 4)
    d = ImageDraw.Draw(im)
    d.rectangle((0, 0, s - 1, s - 1), outline=edge + (255,))
    d.line((1, 1, s - 2, 1), fill=top + (255,))
    d.line((1, s - 2, s - 2, s - 2), fill=SHADOW + (255,))
    nine_slice(name, im, 4)


def header():
    s = 16
    im = Image.new("RGBA", (s, s), SLATE_LIGHT + (255,))
    grain(im, 3, 5)
    d = ImageDraw.Draw(im)
    d.line((0, s - 1, s - 1, s - 1), fill=BRASS_DARK + (255,))
    d.line((0, s - 2, s - 1, s - 2), fill=BRASS + (255,))
    nine_slice("header", im, 4)


def slot():
    """An item slot, 18 px like the inventory's, dark with a bevel."""
    s = 18
    im = Image.new("RGBA", (s, s), (30, 25, 36, 255))
    d = ImageDraw.Draw(im)
    d.rectangle((0, 0, s - 1, s - 1), outline=SHADOW + (255,))
    d.line((1, 1, s - 2, 1), fill=SLATE_DARK + (255,))
    d.line((1, 1, 1, s - 2), fill=SLATE_DARK + (255,))
    d.line((1, s - 2, s - 2, s - 2), fill=SLATE_LIGHT + (255,))
    d.line((s - 2, 1, s - 2, s - 2), fill=SLATE_LIGHT + (255,))
    nine_slice("slot", im, 2)


def bar():
    s = 8
    im = Image.new("RGBA", (s, s), (42, 36, 53, 255))
    d = ImageDraw.Draw(im)
    d.rectangle((0, 0, s - 1, s - 1), outline=SHADOW + (255,))
    nine_slice("bar", im, 2)


def ring(name, fg, size=32, thickness=4):
    """A full ring, drawn white so the screen can tint it."""
    im = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.ellipse((1, 1, size - 2, size - 2), outline=fg + (255,), width=thickness)
    plain(name, im)


if __name__ == "__main__":
    panel()
    inset()
    header()
    card("button", SLATE_LIGHT, BRASS_DARK, (58, 49, 70))
    card("button_hover", (46, 38, 58), BRASS, (72, 60, 88))
    card("button_primary", BRASS, BRASS_DARK, BRASS_LIGHT)
    card("button_primary_hover", (226, 178, 90), BRASS_DARK, (255, 228, 160))
    card("button_danger", RED, (90, 26, 34), RED_LIGHT)
    card("button_danger_hover", RED_LIGHT, (90, 26, 34), (214, 110, 120))
    card("button_disabled", (26, 22, 32), (40, 34, 48), (34, 28, 42))
    card("tab", SLATE_DARK, SLATE_DARK, SLATE_DARK)
    card("tab_active", SLATE_LIGHT, BRASS_DARK, BRASS)
    slot()
    bar()
    print("ok", OUT)
