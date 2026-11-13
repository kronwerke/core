#!/usr/bin/env python3
"""Draws the GUI sprites of the Kronwerke screens into assets/kronwerke/textures/gui/sprites.

The look is the one of the pack's menus (pack: tools/menus/art): brass frames with a light
edge at the top left and a dark one at the bottom right, rivets in the corners, night violet
fields inside, cream writing, pixel for pixel and without any smoothing. One texel is one GUI
pixel.

Minecraft 1.21 stitches everything under textures/gui/sprites into one atlas and draws a
sprite with GuiGraphics.blitSprite; a .mcmeta next to it with gui.scaling nine_slice keeps
the corners sharp whatever size the screen asks for. The middle of a nine-slice is tiled, so
every field whose pattern has a period (the 4 px dither) gets a middle that is a multiple of it.

    gui.py            writes the sprites
    gui.py sheet.png  also writes a contact sheet to look at them

Same input, same files.
"""
import json
import os
import sys

import numpy as np
from PIL import Image

from px import Canvas, cols, hexc

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "..", "src", "main", "resources", "assets", "kronwerke", "textures", "gui", "sprites")

# the palette of the menus
K = cols('#fff3c4', '#f7dc86', '#e5b451', '#b98530', '#8a5a24', '#5c3a1e')   # brass, light to dark
NIGHT = cols('#3a3046', '#2d2538', '#231d2c', '#1a1522')                      # the fields
RED = cols('#a8404a', '#86303e', '#662536', '#4a1c2c')
DARK = hexc('#15121f')                                                        # outlines
PIT = hexc('#120f19')                                                         # sunk fields
CREAM = hexc('#f2ede3')
GEMS = cols('#f06579', '#8bb6dc', '#bb98f4')

GLYPH = {
    'K': 'X...X X..X. X.X.. XX... X.X.. X..X. X...X',
    'R': 'XXXX. X...X X...X XXXX. X.X.. X..X. X...X',
    'O': '.XXX. X...X X...X X...X X...X X...X .XXX.',
    'N': 'X...X XX..X XX..X X.X.X X..XX X..XX X...X',
    'W': 'X...X X...X X...X X.X.X X.X.X XX.XX X...X',
    'E': 'XXXXX X.... X.... XXXX. X.... X.... XXXXX',
}

ICONS = {
    'chat': ['..........', '.XXXXXXXX.', 'XXXXXXXXXX', 'XX.XX.XX.X', 'XXXXXXXXXX', 'XXXXXXXXXX', '.XXXXXXXX.', '..XXX.....', '..XX......', '..X.......'],
    'globe': ['...XXXX...', '.XX.XX.XX.', '.X..XX..X.', 'XXXXXXXXXX', 'X...XX...X', 'X...XX...X', 'XXXXXXXXXX', '.X..XX..X.', '.XX.XX.XX.', '...XXXX...'],
    'cog': ['...XXXX...', '.X.XXXX.X.', '.XXXXXXXX.', 'XXXX..XXXX', 'XXX....XXX', 'XXX....XXX', 'XXXX..XXXX', '.XXXXXXXX.', '.X.XXXX.X.', '...XXXX...'],
    'map': ['XXX....XXX', 'X.XXXXXX.X', 'X.X..X.X.X', 'X.X..X.X.X', 'X.X.XX.X.X', 'X.X.X..X.X', 'X.XXX..X.X', 'X.X....X.X', 'X.XXXXXX.X', 'XXX....XXX'],
    'book': ['.XXXX.XXXX', 'XX..XXX..X', 'X.XX.X.XXX', 'X....X...X', 'X.XX.X.XXX', 'X....X...X', 'X.XX.X.XXX', 'X....X...X', 'XXXXXXXXXX', '....XX....'],
    'crown': ['..........', 'X...XX...X', 'X...XX...X', 'XX.XXXX.XX', 'XX.XXXX.XX', 'XXXXXXXXXX', 'XXXXXXXXXX', '..........', 'XXXXXXXXXX', 'XXXXXXXXXX'],
    'goal': ['...XXXX...', '.XX....XX.', '.X..XX..X.', 'X..X..X..X', 'X.X.XX.X.X', 'X.X.XX.X.X', 'X..X..X..X', '.X..XX..X.', '.XX....XX.', '...XXXX...'],
    'help': ['..XXXXXX..', '.XXX..XXX.', '.XX....XX.', '......XXX.', '.....XXX..', '....XXX...', '....XX....', '..........', '....XX....', '....XX....'],
    'head': ['..XXXXXX..', '.XXXXXXXX.', '.XX.XX.XX.', '.XXXXXXXX.', '.XXX..XXX.', '..XXXXXX..', '....XX....', '.XXXXXXXX.', 'XXXXXXXXXX', 'XXXXXXXXXX'],
    'shield': ['XXXXXXXXXX', 'XXXXXXXXXX', 'XX..XX..XX', 'XX..XX..XX', 'XXXXXXXXXX', 'XXXXXXXXXX', '.XXXXXXXX.', '.XXXXXXXX.', '..XXXXXX..', '....XX....'],
}

SHEET = []


def write(name, c, border=None):
    os.makedirs(OUT, exist_ok=True)
    c.image(1, rgba=True).save(os.path.join(OUT, name + ".png"), optimize=True)
    meta = os.path.join(OUT, name + ".png.mcmeta")
    if border is not None:
        with open(meta, "w") as f:
            json.dump({"gui": {"scaling": {"type": "nine_slice", "width": c.w, "height": c.h, "border": border}}}, f, indent=2)
    elif os.path.exists(meta):
        os.remove(meta)
    SHEET.append((name, c))


def rounded(c, x0, y0, x1, y1):
    """A rectangle without its four corner pixels."""
    return c.rect(x0 + 1, y0, x1 - 1, y1) | c.rect(x0, y0 + 1, x1, y1 - 1)


def rivet(c, x, y, R=K):
    c.fill(c.rect(x, y, x + 2, y + 2), R[1])
    c.px(x, y, R[0])
    c.px(x + 1, y + 1, R[4])


def frame(c, x0, y0, x1, y1, f, R=K):
    """A brass frame f pixels wide inside a dark outline: light at the top and left, dark at the
    bottom and right, a shadow line on the inside. Leaves the field inside untouched."""
    c.fill(rounded(c, x0, y0, x1, y1), DARK)
    for i in range(f):
        a, b, d, e = x0 + 1 + i, y0 + 1 + i, x1 - 1 - i, y1 - 1 - i
        last = i == f - 1
        top = R[1] if i == 0 else (R[3] if last and f > 2 else R[2])
        bot = R[4] if i == 0 else (R[2] if last and f > 2 else R[3])
        c.fill(c.rect(a, b, d, b + 1), top)          # top
        c.fill(c.rect(a, b, a + 1, e), top)          # left
        c.fill(c.rect(a, e - 1, d, e), bot)          # bottom
        c.fill(c.rect(d - 1, b + 1, d, e), bot)      # right
    c.fill(c.rect(x0 + 1, y0 + 1, x0 + 2, y0 + 2), R[0])
    g = f + 1
    c.fill(c.rect(x0 + g, y0 + g, x1 - g, y1 - g) ^ c.rect(x0 + g + 1, y0 + g + 1, x1 - g - 1, y1 - g - 1), R[5])
    c.fill(c.rect(x0 + g, y0 + g, x1 - g, y0 + g + 1) | c.rect(x0 + g, y0 + g, x0 + g + 1, y1 - g), R[5])


def panel():
    """The big frame every screen sits on: five pixels of brass with a rivet in each corner, night inside."""
    s, f = 48, 4
    c = Canvas(s, s)
    frame(c, 0, 0, s, s, f)
    inner = c.rect(f + 2, f + 2, s - f - 2, s - f - 2)
    c.dither(inner, NIGHT[2], hexc('#201a28'), 0.25)
    # the shade the frame throws on the field
    c.fill(c.rect(f + 2, f + 2, s - f - 2, f + 3) | c.rect(f + 2, f + 2, f + 3, s - f - 2), NIGHT[3])
    for x, y in ((2, 2), (s - 4, 2), (2, s - 4), (s - 4, s - 4)):
        rivet(c, x, y)
    # a small brass bracket inside each corner
    for sx, sy in ((1, 1), (-1, 1), (1, -1), (-1, -1)):
        x = f + 2 if sx > 0 else s - f - 3
        y = f + 2 if sy > 0 else s - f - 3
        for i in range(4):
            c.px(x + sx * i, y, K[4])
            c.px(x, y + sy * i, K[4])
        c.px(x, y, K[3])
    write("panel", c, 12)


def header():
    """The band under the top edge of the panel: a lighter night with a brass rail under it."""
    w, h = 16, 20
    c = Canvas(w, h)
    c.ramp(c.rect(0, 0, w, h - 3), [NIGHT[0], NIGHT[1], NIGHT[1]], c.yy / float(h - 3), soft=0.3)
    c.fill(c.rect(0, h - 3, w, h - 2), K[2])
    c.fill(c.rect(0, h - 2, w, h - 1), K[4])
    c.fill(c.rect(0, h - 1, w, h), DARK)
    write("header", c, {"left": 4, "right": 4, "top": 8, "bottom": 8})


def inset():
    """A sunk field for lists: dark, a shadow at the top left, a thin light at the bottom right."""
    s = 16
    c = Canvas(s, s)
    c.fill(c.rect(0, 0, s, s), PIT)
    c.fill(c.rect(0, 0, s, 1) | c.rect(0, 0, 1, s), hexc('#0a0810'))
    c.fill(c.rect(1, s - 1, s, s) | c.rect(s - 1, 1, s, s), NIGHT[0])
    c.fill(c.rect(1, 1, s - 1, 2) | c.rect(1, 1, 2, s - 1), hexc('#0e0b14'))
    write("inset", c, 4)


def plate(w, h, kind='plain', hover=False):
    """A button: brass frame, rivets, and a field by kind (plain night, primary brass, danger red, off)."""
    c = Canvas(w, h)
    R = K
    if hover:
        R = [K[0], K[0], K[1], K[2], K[3], K[4]]
    if kind == 'off':
        R = [tuple(int(v * 0.35 + 38) for v in col) for col in K]
    f = 2
    frame(c, 0, 0, w, h, f, R)
    field = c.rect(f + 2, f + 2, w - f - 2, h - f - 2)
    t = (c.yy - f - 2) / float(h - 2 * f - 4)
    if kind == 'primary':
        F = [K[0], K[1], K[2], K[3]] if hover else [K[1], K[2], K[2], K[3]]
        c.ramp(field, F, t, soft=0.3)
        c.fill(c.rect(f + 2, f + 2, w - f - 2, f + 3), K[0])
        c.fill(c.rect(f + 2, h - f - 3, w - f - 2, h - f - 2), K[4])
    else:
        I = RED if kind == 'danger' else NIGHT
        if kind == 'off':
            I = cols('#221d2a', '#1f1a27', '#1c1723', '#191520')
        if hover:
            I = [tuple(min(255, int(v * 1.22 + 8)) for v in col) for col in I]
        c.ramp(field, [I[1], I[2], I[3]], t, soft=0.3)
        c.fill(c.rect(f + 2, f + 2, w - f - 2, f + 3), I[3])
        c.fill(c.rect(f + 2, h - f - 3, w - f - 2, h - f - 2), I[0])
    return c


def button(name, kind, hover=False):
    # 26 wide with a border of 5: the middle is 16, a multiple of the dither's 4
    write(name, plate(26, 18, kind, hover), {"left": 5, "right": 5, "top": 5, "bottom": 5})


def tab(active):
    """A field that can be chosen: flat and dark, or framed in brass while it is the chosen one. Any size."""
    s = 16
    c = Canvas(s, s)
    if active:
        frame(c, 0, 0, s, s, 1, [K[0], K[1], K[2], K[3], K[4], K[5]])
        c.fill(c.rect(3, 3, s - 3, s - 3), NIGHT[1])
        c.fill(c.rect(3, s - 4, s - 3, s - 3), NIGHT[0])
        write("tab_active", c, 4)
    else:
        c.fill(rounded(c, 0, 0, s, s), DARK)
        c.fill(c.rect(1, 1, s - 1, s - 1), NIGHT[3])
        c.fill(c.rect(1, 1, s - 1, 2), NIGHT[1])
        write("tab", c, 4)


def slot():
    """An item slot, 18 px like the inventory's: sunk, with a thin brass shade at the bottom right."""
    s = 18
    c = Canvas(s, s)
    c.fill(c.rect(0, 0, s, s), DARK)
    c.fill(c.rect(1, 1, s - 1, s - 1), NIGHT[3])
    c.fill(c.rect(1, 1, s - 1, 2) | c.rect(1, 1, 2, s - 1), hexc('#0e0b14'))
    c.fill(c.rect(1, s - 1, s, s) | c.rect(s - 1, 1, s, s), K[5])
    write("slot", c, 2)


def bar():
    s = 8
    c = Canvas(s, s)
    c.fill(c.rect(0, 0, s, s), DARK)
    c.fill(c.rect(1, 1, s - 1, s - 1), NIGHT[3])
    c.fill(c.rect(1, 1, s - 1, 2), hexc('#0e0b14'))
    write("bar", c, 2)


def switch(on):
    """A small lever for a yes or no: the knob to the right and a lit field when on."""
    w, h = 24, 12
    c = Canvas(w, h)
    frame(c, 0, 0, w, h, 1, [K[1], K[2], K[3], K[3], K[4], K[5]])
    field = c.rect(3, 3, w - 3, h - 3)
    if on:
        c.ramp(field, [K[2], K[3], K[4]], (c.yy - 3) / 6.0, soft=0.3)
    else:
        c.fill(field, NIGHT[3])
    kx = w - 11 if on else 2
    knob = rounded(c, kx, 1, kx + 9, h - 1)
    c.fill(knob, DARK)
    face = c.rect(kx + 1, 2, kx + 8, h - 2)
    R = K if on else [tuple(int(v * 0.5 + 50) for v in col) for col in K]
    c.ramp(face, [R[0], R[1], R[2], R[3]], (c.yy - 2) / 8.0, soft=0.3)
    c.fill(c.rect(kx + 1, h - 3, kx + 8, h - 2), R[4])
    c.fill(c.rect(kx + 4, 4, kx + 5, h - 4), R[4])
    write("switch_on" if on else "switch_off", c)


def glyph_mask(c, ch, x, y, k):
    m = c.none()
    for r, row in enumerate(GLYPH[ch].split()):
        for q, v in enumerate(row):
            if v == 'X':
                m |= c.rect(x + q * k, y + r * k, x + q * k + k, y + r * k + k)
    return m


def crown_mark(c, cx, base, w, h):
    x0 = cx - w // 2
    pts = [(x0, base - 3), (x0 - 1, base - h + 3), (x0 + w * 0.22, base - h * 0.5), (cx - w * 0.2, base - h + 1),
           (cx - w * 0.08, base - h * 0.5), (cx, base - h - 1), (cx + w * 0.08, base - h * 0.5), (cx + w * 0.2, base - h + 1),
           (x0 + w * 0.78, base - h * 0.5), (x0 + w, base - h + 3), (x0 + w - 1, base - 3)]
    m = c.rect(x0, base - 4, x0 + w, base) | c.poly(pts)
    t = np.clip(0.95 - (c.xx - x0) / float(w) * 0.8 + (c.yy - (base - h)) / float(h) * 0.3, 0, 1)
    c.ramp(m, [K[0], K[1], K[2], K[3]], t, soft=0.3)
    c.fill(c.rect(x0, base - 4, x0 + w, base - 3), K[0])
    c.fill(c.rect(x0, base - 1, x0 + w, base), K[4])
    for i, gx in enumerate(np.linspace(x0 + 2, x0 + w - 4, 3)):
        c.fill(c.rect(gx, base - 3, gx + 2, base - 1), GEMS[i % len(GEMS)])
    return m


def brand():
    """The crown and KRONWERKE in raised brass letters, for the header of every screen."""
    k = 2
    text = 'KRONWERKE'
    lw, gap = 5 * k, 2
    cw = 16
    total = cw + 4 + len(text) * lw + (len(text) - 1) * gap
    W, H = total + 4, 18
    c = Canvas(W, H)
    x0, y0 = cw + 5, 1
    face = c.none()
    for i, ch in enumerate(text):
        face |= glyph_mask(c, ch, x0 + i * (lw + gap), y0, k)
    depth = c.shift(face, 0, 1) | c.shift(face, 0, 2) | c.shift(face, 1, 2)
    solid = face | depth
    grow = solid.copy()
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        grow |= c.shift(solid, dx, dy)
    c.fill(grow, DARK)
    c.fill(depth, K[4])
    c.ramp(face, [K[0], K[1], K[2], K[2], K[3]], (c.yy - y0) / float(7 * k), soft=0.35)
    c.fill(face & ~c.shift(face, 0, 1), K[0])
    c.fill(face & ~c.shift(face, 0, -1), K[3])
    m = Canvas(W, H)
    cm = crown_mark(m, 1 + cw // 2 + 1, 15, 14, 11)
    ring = cm.copy()
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        ring |= m.shift(cm, dx, dy)
    c.fill(ring & ~cm, DARK)
    c.paste(m)
    write("brand", c)


def icon(name):
    """A 10 px sign in cream; the screen tints it."""
    c = Canvas(10, 10)
    for y, row in enumerate(ICONS[name]):
        for x, v in enumerate(row):
            if v == 'X':
                c.px(x, y, CREAM)
    write("icon_" + name, c)


def gear():
    """The small gear that turns in the header while a goal runs: four frames make one tooth."""
    s = 16
    for i in range(4):
        c = Canvas(s, s)
        cx = cy = 7.5
        a0 = i * (2 * np.pi / 8) / 4
        ang = np.arctan2(c.yy - cy, c.xx - cx)
        d = np.sqrt((c.xx - cx) ** 2 + (c.yy - cy) ** 2)
        teeth = np.cos((ang - a0) * 8) > -0.15
        body = (d <= 5.4) | ((d <= 7.7) & teeth)
        hole = d <= 1.8
        m = body & ~hole
        t = np.clip((c.yy * 0.8 + c.xx * 0.4) / 19.0, 0, 1)
        c.ramp(m, [K[1], K[2], K[3], K[4]], t, soft=0.3)
        c.fill(c.edge(m) & (d > 3), K[4])
        c.fill(m & (d <= 3.0), K[4])
        c.fill(m & (d > 3.0) & (d <= 4.0), K[1])
        c.alpha = m
        write("gear_%d" % i, c)


def sheet(path):
    im = Image.new('RGBA', (1200, 520), (60, 84, 78, 255))
    x = y = 10
    rowh = 0
    for name, c in SHEET:
        tile = c.image(4, rgba=True)
        if x + tile.width > 1190:
            x, y, rowh = 10, y + rowh + 10, 0
        im.paste(tile, (x, y), tile)
        x += tile.width + 10
        rowh = max(rowh, tile.height)
    im.convert('RGB').save(path)


if __name__ == "__main__":
    panel()
    header()
    inset()
    button("button", 'plain')
    button("button_hover", 'plain', True)
    button("button_primary", 'primary')
    button("button_primary_hover", 'primary', True)
    button("button_danger", 'danger')
    button("button_danger_hover", 'danger', True)
    button("button_disabled", 'off')
    tab(False)
    tab(True)
    slot()
    bar()
    switch(True)
    switch(False)
    brand()
    for n in ICONS:
        icon(n)
    gear()
    if len(sys.argv) > 1:
        sheet(sys.argv[1])
    print("ok", OUT)
