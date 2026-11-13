#!/usr/bin/env python3
"""Paints the picture of joining the server: an old works that still ticks.

Three brass gears mesh on a timber frame that has seen better days, next to a crooked little
house with light in the window. Moss sits on the beams and the roof, vines hang over the
gears, a broken wheel lies in the grass. All of it stands on a piece of ground that floats in
the night, so the picture has no edge and fits a window of any shape.

Pixel art on one grid of 320 by 200 (one texel is one GUI pixel at the size the menus are
drawn for), nothing smoothed. What is written into assets/kronwerke/textures/gui/join:

    back.png       the night behind: a dim moon, a few stars
    scene.png      the ground, the house, the frame's posts (behind the gears)
    front.png      what lies over the gears: braces, hubs, the crown, vines, moss, the lantern
    light.png      the warm light of window, door and lantern, drawn over everything and
                   flickered by the game
    gear_big.png   96 frames, one whole turn, 12 columns (eight frames move it by one tooth)
    gear_mid.png   64 frames, 8 columns
    gear_small.png 48 frames, 8 columns
    pendulum.png   13 frames from one end of the swing to the other
    logo.png       the name for under the picture

    join_art.py                 writes the textures
    join_art.py preview <dir>   also writes still.png and loop.gif there, to look at

The gears carry what turns with them (a mossy rim, a chipped tooth, verdigris), so a sheet is
a whole turn and not just one tooth. Their light does not turn: it comes from the upper left
in every frame. Same input, same files.
"""
import math
import os
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', 'textures'))
from px import Canvas, cols, hexc, noise2d  # noqa: E402

OUT = os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'kronwerke', 'textures', 'gui', 'join')
W, H = 320, 200

# ---- palette: the menus' brass and night, with moss, old wood and a warm window ----
OUTL = hexc('#15121f')
STONE = cols('#8a7f97', '#655a73', '#4b4159', '#3a3046', '#2d2538', '#231d2c')        # light to dark
WOOD = cols('#b8865a', '#8d5a3b', '#6b4030', '#4a2c26', '#33201f', '#241718')
BRASS = cols('#fff3c4', '#f7dc86', '#e5b451', '#b98530', '#8a5a24', '#5c3a1e')
DULL = cols('#d9c98f', '#b99d5a', '#93733a', '#6e5129', '#4c371f', '#34261a')           # brass that nobody polished
AGED = cols('#f0d58a', '#d9b25a', '#b98a3a', '#8f6628', '#66461f', '#43301c')           # the two smaller wheels
PATINA = cols('#8fd1b3', '#63a88f', '#3f7f72', '#2c5a57')
MOSS = cols('#c6dc6e', '#8fba4f', '#5d9443', '#3b6f3d', '#285238', '#1c3a31')
ROOF = cols('#8d6a86', '#6f4f6c', '#553a56', '#3f2b43', '#2c1f31')
EARTH = cols('#6b5247', '#54403a', '#3f2f30', '#2e2228', '#211920')
GLOW = cols('#fff1b8', '#ffd27a', '#ffa64d', '#d9662c', '#8f3a22')
NIGHT = hexc('#0d0b10')
SKY = cols('#1c1826', '#17131f', '#120f19')

# ---- where things are ----
GROUND_Y = 152
BIG = (196, 96, 38.0, 12)          # centre x, y, radius, teeth
MODULE = BIG[2] * 0.9 / 12
AT_MID, AT_SMALL = math.radians(-38), math.radians(152)
R_MID, R_SMALL = MODULE * 8 / 0.9, MODULE * 6 / 0.9
MID = (BIG[0] + math.cos(AT_MID) * MODULE * 20, BIG[1] + math.sin(AT_MID) * MODULE * 20, R_MID, 8)
SMALL = (BIG[0] + math.cos(AT_SMALL) * MODULE * 18, BIG[1] + math.sin(AT_SMALL) * MODULE * 18, R_SMALL, 6)
FRAMES_PER_TOOTH = 8
PEND = (int(round(MID[0])) + 1, int(round(MID[1])) + 6)   # where the pendulum hangs
PEND_LEN, PEND_SWING, PEND_FRAMES = 46, 15.0, 13


def rng(seed):
    return np.random.default_rng(seed)


# ======================================================================================
# gears

def gear_frame(size, r, teeth, angle, seed, kind):
    """One gear at one angle on a canvas of size by size, centre in the middle."""
    c = Canvas(size, size)
    cx = cy = (size - 1) / 2.0
    dx, dy = c.xx - cx, c.yy - cy
    d = np.sqrt(dx * dx + dy * dy)
    ang = np.arctan2(dy, dx)
    step = 2 * math.pi / teeth
    rel = (ang - angle) % (2 * math.pi)                       # the gear's own angle of every pixel
    tooth = np.floor(rel / step + 0.5).astype(int) % teeth
    off = np.abs(((rel / step + 0.5) % 1.0) - 0.5)             # 0 on a tooth's middle, 0.5 between two
    root = r * 0.80
    # a tooth: full height up to 0.15 of the step to each side, down to the root at 0.27
    height = np.clip((0.27 - off) / (0.27 - 0.15), 0, 1)
    tip = root + (r - root) * height
    # the chipped tooth: one of them lost its outer half long ago
    chipped = (tooth == (1 if kind != 'small' else 4))
    tip = np.where(chipped & (off < 0.27), np.minimum(tip, root + (r - root) * (0.45 + 0.5 * (rel % 0.37))), tip)
    inner = r * (0.54 if kind == 'big' else 0.56)
    body = (d <= tip) & (d >= inner)
    # spokes and hub
    nsp = {'big': 6, 'mid': 4, 'small': 3}[kind]
    hub = r * (0.30 if kind == 'big' else 0.26)
    sstep = 2 * math.pi / nsp
    srel = ((rel + sstep / 2) % sstep) - sstep / 2
    half = max(1.6, r * (0.075 if kind == 'big' else 0.10))
    spoke = (np.abs(np.sin(srel) * d) <= half) & (np.cos(srel) > 0) & (d < inner + 1) & (d > hub - 1)
    hubm = d <= hub
    hole = d <= max(1.6, r * 0.09)
    m = (body | spoke | hubm)
    if kind == 'big':
        # the big wheel's middle is open, the crown stands in it (drawn by the front layer)
        m = (body | spoke | ((d <= hub) & (d >= hub - 3.2)))
        hole = c.none()
    m &= ~hole

    R = BRASS if kind == 'big' else AGED
    # light from the upper left, the same in every frame
    lit = np.clip(0.5 - (dx * 0.6 + dy * 0.8) / (2.2 * r), 0, 1)
    c.ramp(m, [R[1], R[2], R[2], R[3], R[4]], 1 - lit, soft=0.25)
    # the rim between the teeth's roots and the inner edge is a band of its own
    band = (d <= root - 1.2) & (d >= inner + 1.6) & m
    c.ramp(band, [R[2], R[3], R[3], R[4]], 1 - lit, soft=0.25)
    # edges: light where the surface turns to the light, dark where it turns away
    e = c.edge(m)
    nx, ny = dx / np.maximum(d, 1e-6), dy / np.maximum(d, 1e-6)
    outward = np.where(d > (inner + root) / 2, 1.0, -1.0)      # the inner edge faces the middle
    face = -(nx * 0.6 + ny * 0.8) * outward
    c.fill(e & (face > 0.25), R[0] if kind == 'big' else R[1])
    c.fill(e & (face < -0.25), R[5])
    c.fill(c.edge(band) & band & (face * -1 > 0.3), R[1])
    # rivets on the rim, one per tooth, turning with it
    rr = (root + inner) / 2
    for i in range(teeth):
        a = angle + i * step + step / 2
        px_, py_ = cx + math.cos(a) * rr, cy + math.sin(a) * rr
        if kind == 'small':
            continue
        c.px(int(round(px_)), int(round(py_)), R[5])
        c.px(int(round(px_)) - 1, int(round(py_)) - 1, R[0])
    # what the years left on it, fixed to the gear: verdigris in a sector, moss on a stretch of the rim
    g = rng(seed)
    grain = noise2d(size, size, seed, period=5, octaves=2)
    # (the stains are placed by the gear's own angle, so they turn with it)
    sect = (rel > 2.1) & (rel < 3.4) & m & (d > inner + 1)
    c.fill(sect & (grain > (0.52 if kind == 'big' else 0.6)), PATINA[2])
    c.fill(sect & (grain > (0.66 if kind == 'big' else 0.74)), PATINA[1])
    sect2 = (rel > 4.9) & (rel < 5.5) & m
    c.fill(sect2 & (grain > 0.6) & (kind == 'big'), PATINA[3])
    mossy = (rel > 0.5) & (rel < 1.5) & (d >= root - 1.5) & (d <= r) & m if kind != 'small' else c.none()
    c.fill(mossy & (grain > 0.42), MOSS[3])
    c.fill(mossy & (grain > 0.58), MOSS[2])
    c.fill(mossy & (grain > 0.74), MOSS[1])
    # dark outline outside
    ring = c.edge(m, inner=False)
    c.fill(ring, OUTL)
    if kind != 'big':
        c.fill(d <= max(1.6, r * 0.09), OUTL)
    return c


def gear_sheet(name, spec, base, seed, kind, columns):
    """A whole turn, eight frames per tooth; frame i is the gear at base + i * step / 8 * dir."""
    cx, cy, r, teeth = spec
    size = int(2 * r) + 8
    size += size % 2
    n = teeth * FRAMES_PER_TOOTH
    rows = n // columns
    sheet = Image.new('RGBA', (columns * size, rows * size), (0, 0, 0, 0))
    step = 2 * math.pi / teeth
    direction = 1 if kind == 'big' else -1
    frames = []
    for i in range(n):
        c = gear_frame(size, r, teeth, base + direction * i * step / FRAMES_PER_TOOTH, seed, kind)
        im = c.image(1, rgba=True)
        sheet.paste(im, ((i % columns) * size, (i // columns) * size))
        frames.append(im)
    sheet.save(os.path.join(OUT, name + '.png'), optimize=True)
    return frames, size


def mesh_base(at, teeth):
    """The angle of a gear that meshes with the big one (at angle 0) from the direction at."""
    step, step12 = 2 * math.pi / teeth, 2 * math.pi / 12
    return at + math.pi - step * (0.5 - at / step12)


# ======================================================================================
# materials

def planks(c, mask, seed, width=5, R=WOOD, vertical=True):
    g = rng(seed)
    coord = c.xx if vertical else c.yy
    other = c.yy if vertical else c.xx
    lo = int(coord[mask].min()) if mask.any() else 0
    idx = (coord - lo) // width
    tone = g.integers(0, 3, 400)
    for i in np.unique(idx[mask]):
        pm = mask & (idx == i)
        t = int(tone[int(i) % 400])
        base = [R[2], R[3], R[2]][t]
        c.fill(pm, base)
        # the grain: a few darker streaks along the plank
        streak = noise2d(c.w, c.h, seed + int(i) * 7, period=3, octaves=1)
        stretched = np.repeat(streak[:1, :], c.h, axis=0) if vertical else np.repeat(streak[:, :1], c.w, axis=1)
        c.fill(pm & (stretched > 0.72), R[3] if t != 1 else R[4])
        c.fill(pm & (stretched < 0.2), R[1] if t == 0 else R[2])
        # the gap to the next plank
        c.fill(pm & (((coord - lo) % width) == width - 1), R[4])
    return idx


def shingles(c, mask, seed, R=ROOF, row=4, width=6):
    g = rng(seed)
    ys = c.yy[mask]
    if ys.size == 0:
        return
    y0 = int(ys.min())
    r = (c.yy - y0) // row
    shift = np.where(r % 2 == 0, 0, width // 2)
    col = (c.xx + shift) // width
    key = (r * 131 + col * 17) % 997
    tone = g.integers(0, 10, 997)
    t = tone[key]
    c.fill(mask, R[2])
    c.fill(mask & (t < 3), R[1])
    c.fill(mask & (t >= 8), R[3])
    # each shingle's lower edge and its right side are dark, its top is lit
    c.fill(mask & (((c.yy - y0) % row) == row - 1), R[4])
    c.fill(mask & (((c.xx + shift) % width) == width - 1), R[3])
    c.fill(mask & (((c.yy - y0) % row) == 0) & (t < 5), R[0])


def stones(c, mask, seed, R=STONE, row=5, width=9):
    g = rng(seed)
    ys = c.yy[mask]
    if ys.size == 0:
        return
    y0 = int(ys.min())
    r = (c.yy - y0) // row
    shift = np.where(r % 2 == 0, 0, width // 2)
    col = (c.xx + shift) // width
    key = (r * 71 + col * 29) % 499
    tone = g.integers(0, 10, 499)[key]
    c.fill(mask, R[3])
    c.fill(mask & (tone < 3), R[2])
    c.fill(mask & (tone >= 8), R[4])
    c.fill(mask & ((((c.yy - y0) % row) == row - 1) | (((c.xx + shift) % width) == width - 1)), R[5])
    c.fill(mask & (((c.yy - y0) % row) == 0) & (tone < 6), R[1])


def moss_on(c, mask, seed, amount=0.5, hang=3):
    """Moss that sits on the top edges of a shape and hangs down a little."""
    g = rng(seed)
    top = mask & ~c.shift(mask, 0, 1)
    n = noise2d(c.w, c.h, seed, period=7, octaves=2)
    grow = top & (n > 1 - amount)
    m = grow.copy()
    depth = (noise2d(c.w, c.h, seed + 1, period=4, octaves=1) * (hang + 1)).astype(int)
    for k in range(1, hang + 1):
        m |= c.shift(grow & (depth >= k), 0, k) & mask
    cap = c.shift(grow, 0, -1) & ~mask                         # a pixel of moss standing up over the edge
    cap &= (noise2d(c.w, c.h, seed + 2, period=3, octaves=1) > 0.45)
    m |= cap
    c.fill(m, MOSS[3])
    c.fill(m & ~c.shift(m, 0, 1), MOSS[2])
    c.fill(m & ~c.shift(m, 0, 1) & (n > 1 - amount * 0.55), MOSS[1])
    c.fill(cap & (n > 1 - amount * 0.3), MOSS[0])
    c.fill(m & ~c.shift(m, 0, -1) & mask, MOSS[4])
    return m


def vine(c, x, y, length, seed, sway=0):
    """A vine hanging from x, y: a thin stem that wanders, with leaves to both sides."""
    g = rng(seed)
    px_ = x
    for i in range(length):
        if i and g.random() < 0.3:
            px_ += int(g.integers(-1, 2))
        xx = px_ + (sway if i > length // 2 else 0)
        c.px(xx, y + i, MOSS[4] if i % 3 else MOSS[3])
        if i % 3 == 1:
            side = -1 if (i // 3) % 2 else 1
            c.px(xx + side, y + i, MOSS[2])
            if g.random() < 0.6:
                c.px(xx + side, y + i - 1, MOSS[1])
            if g.random() < 0.3:
                c.px(xx + 2 * side, y + i, MOSS[3])
    c.px(px_ + (sway if length > 1 else 0), y + length, MOSS[1])


def grass(c, x0, x1, y, seed, tall=4):
    g = rng(seed)
    for x in range(x0, x1):
        h = int(g.integers(0, tall + 1))
        if g.random() < 0.35:
            continue
        lean = int(g.integers(-1, 2))
        for k in range(h):
            xx = x + (lean if k >= 2 else 0)
            c.px(xx, y - 1 - k, MOSS[3] if k < h - 1 else (MOSS[1] if g.random() < 0.5 else MOSS[2]))


def beam(c, x0, y0, x1, y1, thick, seed, R=WOOD, cap=True):
    """A timber between two points, lit from above, with an outline."""
    m = c.line(x0, y0, x1, y1, width=thick)
    c.fill(m, R[2])
    n = noise2d(c.w, c.h, seed, period=4, octaves=1)
    c.fill(m & (n > 0.7), R[3])
    c.fill(m & ~c.shift(m, 0, 1) | (m & ~c.shift(m, 1, 0) & ~c.shift(m, 0, 1)), R[1])
    c.fill(m & ~c.shift(m, 0, -1), R[4])
    c.fill(c.edge(m, inner=False), OUTL)
    return m


# ======================================================================================
# the layers

def island(c):
    """The piece of ground everything stands on: flat on top, rock and roots below."""
    x = np.arange(W)
    left, right = 34, 292
    span = (x >= left) & (x <= right)
    # the underside: deepest under the middle, ragged
    t = np.clip((x - left) / float(right - left), 0, 1)
    belly = np.sin(t * math.pi) ** 0.55
    rag = noise2d(W, 1, 11, period=14, octaves=3)[0]
    rag2 = noise2d(W, 1, 12, period=5, octaves=1)[0]
    under = GROUND_Y + 5 + belly * (26 + 14 * rag) + 3 * rag2
    top = GROUND_Y + (noise2d(W, 1, 13, period=30, octaves=2)[0] * 3 - 1.5)
    top = np.round(top)
    m = (c.yy >= top[None, :]) & (c.yy <= under[None, :]) & span[None, :]
    depth = (c.yy - top[None, :]) / np.maximum(under - top, 1)[None, :]
    n = noise2d(W, H, 14, period=9, octaves=2)
    c.ramp(m, [EARTH[1], EARTH[2], EARTH[3], EARTH[4]], np.clip(depth * 1.1 + (n - 0.5) * 0.25, 0, 1), soft=0.3)
    # stones in the earth
    for sx, sy, sw, sh in ((70, 166, 9, 5), (112, 172, 12, 6), (150, 168, 7, 4), (188, 176, 14, 7), (232, 168, 10, 5), (258, 163, 7, 4), (96, 161, 6, 3), (212, 162, 6, 3)):
        st = c.ellipse(sx, sy, sw / 2.0, sh / 2.0) & m
        c.fill(st, STONE[4])
        c.fill(st & ~c.shift(st, 0, 1), STONE[3])
        c.fill(st & ~c.shift(st, 0, -1), STONE[5])
    # the turf: two rows of green, moss hanging over the edge
    turf = m & (c.yy <= top[None, :] + 2)
    c.fill(turf, MOSS[4])
    c.fill(m & (c.yy == top[None, :]), MOSS[3])
    c.fill(m & (c.yy == top[None, :]) & (n > 0.55), MOSS[2])
    hang = m & (c.yy > top[None, :] + 2) & (c.yy <= top[None, :] + 3 + (noise2d(W, H, 15, period=4, octaves=1) * 5).astype(int)[0][None, :])
    c.fill(hang & (noise2d(W, H, 16, period=5, octaves=1) > 0.4), MOSS[5])
    # roots and a few hanging threads under it
    g = rng(17)
    for rx in (58, 90, 131, 170, 205, 244, 270):
        ry = int(under[rx])
        ln = int(g.integers(5, 14))
        xx = rx
        for k in range(ln):
            if g.random() < 0.35:
                xx += int(g.integers(-1, 2))
            c.px(xx, ry + k, EARTH[3] if k < ln - 2 else EARTH[4])
    c.fill(c.edge(m, inner=False) & (c.yy > top[None, :]), OUTL)
    return top


def house(c, light):
    """The little house on the left, seen from its long side: stone foot, plank wall with a
    door that stands ajar and a round window with light, a big sagging shingle roof with a
    hole in it and moss all over, a leaning chimney. It is painted upright and then leant."""
    h = Canvas(W, H)
    hl = Canvas(W, H)
    x0, x1 = 62, 128
    foot_y, wall_y = GROUND_Y - 6, 110
    wall = h.rect(x0, wall_y, x1, foot_y)
    planks(h, wall, 21, width=6)
    # one plank is gone, one hangs loose over the gap
    gap = h.rect(x0 + 13, wall_y + 6, x0 + 18, foot_y - 6)
    h.fill(gap, WOOD[5])
    h.fill(h.rect(x0 + 13, wall_y + 6, x0 + 14, foot_y - 6), OUTL)
    loose = h.poly([(x0 + 17, wall_y + 3), (x0 + 22, wall_y + 3), (x0 + 26, foot_y - 3), (x0 + 21, foot_y - 2)])
    h.fill(loose, WOOD[2])
    h.fill(loose & ~h.shift(loose, 1, 0), WOOD[1])
    h.fill(loose & ~h.shift(loose, -1, 0), WOOD[4])
    # corner posts, a rail, shade under the eave
    for px_ in (x0, x1 - 4):
        h.fill(h.rect(px_, wall_y, px_ + 4, foot_y), WOOD[3])
        h.fill(h.rect(px_, wall_y, px_ + 1, foot_y), WOOD[2])
        h.fill(h.rect(px_ + 3, wall_y, px_ + 4, foot_y), WOOD[5])
    h.fill(wall & (h.yy < wall_y + 3), WOOD[5])
    h.fill(wall & (h.yy == wall_y + 3) & (h.xx % 2 == 0), WOOD[4])
    # the foot of stones
    foot = h.rect(x0 - 2, foot_y, x1 + 2, GROUND_Y + 1)
    stones(h, foot, 22, row=4, width=8)
    h.fill(h.rect(x0 - 2, foot_y, x1 + 2, foot_y + 1), STONE[1])
    # the door, ajar: dark inside, a strip of light, the leaf swung in a little
    dx0, dx1, dy0 = x0 + 31, x0 + 45, wall_y + 11
    h.fill(h.rect(dx0 - 2, dy0 - 2, dx1 + 2, foot_y), WOOD[3])
    h.fill(h.rect(dx0 - 2, dy0 - 2, dx1 + 2, dy0 - 1), WOOD[1])
    h.fill(h.rect(dx0, dy0, dx1, foot_y), OUTL)
    h.fill(h.rect(dx0 + 1, dy0 + 1, dx0 + 5, foot_y), GLOW[3])
    h.fill(h.rect(dx0 + 1, dy0 + 1, dx0 + 3, foot_y), GLOW[2])
    hl.fill(h.rect(dx0 + 1, dy0 + 3, dx0 + 2, foot_y - 1), GLOW[1])
    leaf = h.rect(dx0 + 5, dy0 + 1, dx1 - 1, foot_y)
    planks(h, leaf, 23, width=4)
    h.fill(h.rect(dx0 + 5, dy0 + 6, dx1 - 1, dy0 + 7) | h.rect(dx0 + 5, foot_y - 6, dx1 - 1, foot_y - 5), AGED[3])
    h.px(dx1 - 3, dy0 + 14, AGED[1])
    # light falls out of the door onto the step
    step = h.rect(dx0 - 3, foot_y, dx1 + 3, foot_y + 3)
    h.fill(step, STONE[2])
    h.fill(h.rect(dx0 - 3, foot_y, dx1 + 3, foot_y + 1), STONE[0])
    hl.fill(h.rect(dx0, foot_y, dx0 + 6, foot_y + 1), GLOW[1])
    # the round window: a brass ring, four panes, light behind
    wx, wy = x1 - 13, wall_y + 16
    ring = h.ellipse(wx, wy, 6.3)
    pane = h.ellipse(wx, wy, 4.4)
    h.fill(ring, AGED[3])
    h.fill(ring & ~h.shift(ring, 1, 1), AGED[1])
    h.fill(pane, GLOW[2])
    h.fill(pane & (h.yy <= wy), GLOW[1])
    h.fill(pane & ((h.xx == wx) | (h.yy == wy)), AGED[4])
    h.fill(h.edge(ring, inner=False), OUTL)
    hl.fill(pane & (h.yy < wy) & (h.xx < wx), GLOW[0])
    h.fill(h.edge(wall | foot, inner=False), OUTL)
    # the roof, seen on its long side: the ridge sags, the eave hangs over the wall
    xs = np.arange(W)
    t = np.clip((xs - (x0 - 8)) / float(x1 + 8 - (x0 - 8)), 0, 1)
    ridge = 82 + 3.2 * np.sin(t * math.pi) - 2 * t
    eave = 113 + 1.2 * np.sin(t * math.pi * 2)
    inset = (x0 + 1 - (x0 - 8)) * np.clip((eave[None, :] - h.yy) / (eave - ridge)[None, :], 0, 1)   # the sides lean in towards the ridge
    roof = (h.yy >= np.round(ridge)[None, :]) & (h.yy <= np.round(eave)[None, :]) & (h.xx >= x0 - 8 + inset * 0.8) & (h.xx <= x1 + 8 - inset * 0.8)
    shingles(h, roof, 25, row=4, width=6)
    # bare patches where shingles slid off, and the hole with its rafters
    bare = roof & (noise2d(W, H, 31, period=9, octaves=2) > 0.68) & (h.yy > ridge[None, :] + 4)
    h.fill(bare, ROOF[4])
    hole = h.ellipse(x1 - 14, 99, 9, 6) & roof
    h.fill(hole, OUTL)
    for k in range(4):
        h.fill(h.line(x1 - 26 + k * 6, 106, x1 - 22 + k * 6, 92, width=1) & hole, WOOD[3])
    h.fill(h.edge(hole) & hole & (h.yy < 99), ROOF[4])
    # the ridge board and the eave's dark edge
    rb = roof & (h.yy <= np.round(ridge)[None, :] + 1)
    h.fill(rb, WOOD[2])
    h.fill(roof & (h.yy == np.round(ridge)[None, :]), WOOD[1])
    h.fill(roof & (h.yy >= np.round(eave)[None, :] - 1), ROOF[4])
    h.fill(h.edge(roof, inner=False), OUTL)
    # the chimney, with a brass cap that has gone green
    ch = h.rect(x0 + 9, 64, x0 + 19, 86)
    stones(h, ch, 26, row=4, width=6)
    h.fill(h.rect(x0 + 9, 64, x0 + 10, 86), STONE[1])
    capm = h.rect(x0 + 7, 61, x0 + 21, 64)
    h.fill(capm, AGED[2])
    h.fill(h.rect(x0 + 7, 61, x0 + 21, 62), AGED[0])
    h.fill(h.rect(x0 + 12, 61, x0 + 17, 64), PATINA[2])
    h.fill(h.edge(ch | capm, inner=False) & ~roof, OUTL)
    # moss: thick on the roof, some on chimney and foot
    moss_on(h, roof & ~hole & ~rb, 27, amount=0.5, hang=3)
    moss_on(h, rb, 32, amount=0.8, hang=4)
    patch = roof & ~hole & (noise2d(W, H, 33, period=11, octaves=2) > 0.66)
    h.fill(patch, MOSS[4])
    h.fill(patch & (noise2d(W, H, 34, period=4, octaves=1) > 0.5), MOSS[3])
    h.fill(patch & ~h.shift(patch, 0, -1) & (noise2d(W, H, 35, period=3, octaves=1) > 0.4), MOSS[2])
    moss_on(h, capm, 28, amount=0.5, hang=1)
    moss_on(h, foot, 29, amount=0.5, hang=2)
    # ivy up the left corner and from the eave
    for k, (vx, vy, ln) in enumerate(((x0 + 1, wall_y + 2, 28), (x0 + 4, wall_y + 4, 17), (x0 - 4, 114, 12), (x1 + 4, 114, 9), (x0 + 52, 114, 6))):
        vine(h, vx, vy, ln, 36 + k)
    # the whole house leans to the left: every row is pushed over a little more towards the top
    for src, dst in ((h, c), (hl, light)):
        for y in range(H):
            sh = -int(round(max(0, (GROUND_Y - y)) * 0.045))
            row_a = np.roll(src.alpha[y], sh)
            row_c = np.roll(src.a[y], sh, axis=0)
            dst.a[y][row_a] = row_c[row_a]
            dst.alpha[y] |= row_a


def frame_back(c):
    """The posts that carry the gears, behind them: two leaning timbers and a cross beam."""
    bx, by, br, _ = BIG
    beam(c, 160, GROUND_Y + 1, 172, 44, 6, 41)
    beam(c, 250, GROUND_Y + 1, 232, 40, 6, 42)
    beam(c, 158, 47, 262, 41, 5, 43)
    # an iron band round the cracked left post, and the crack
    band = c.rect(159, 118, 169, 121)
    c.fill(band, STONE[2])
    c.fill(c.rect(159, 118, 169, 119), STONE[0])
    for k in range(9):
        c.px(165 - (k // 4), 100 + k * 2, OUTL)
    # the axle of the small gear runs into the house
    ax = c.rect(126, int(SMALL[1]) - 1, int(SMALL[0]), int(SMALL[1]) + 2)
    c.fill(ax, DULL[3])
    c.fill(c.rect(126, int(SMALL[1]) - 1, int(SMALL[0]), int(SMALL[1])), DULL[1])
    c.fill(c.edge(ax, inner=False), OUTL)


def crown(c, cx, base, w, h):
    x0 = cx - w // 2
    pts = [(x0, base - 3), (x0 - 1, base - h + 3), (x0 + w * 0.22, base - h * 0.5), (cx - w * 0.2, base - h + 1),
           (cx - w * 0.08, base - h * 0.5), (cx, base - h - 1), (cx + w * 0.08, base - h * 0.5), (cx + w * 0.2, base - h + 1),
           (x0 + w * 0.78, base - h * 0.5), (x0 + w, base - h + 3), (x0 + w - 1, base - 3)]
    m = c.rect(x0, base - 4, x0 + w, base) | c.poly(pts)
    t = np.clip(0.95 - (c.xx - x0) / float(w) * 0.8 + (c.yy - (base - h)) / float(h) * 0.3, 0, 1)
    c.ramp(m, [BRASS[0], BRASS[1], BRASS[2], BRASS[3]], t, soft=0.3)
    c.fill(c.rect(x0, base - 4, x0 + w, base - 3), BRASS[0])
    c.fill(c.rect(x0, base - 1, x0 + w, base), BRASS[4])
    for i, gx in enumerate(np.linspace(x0 + 2, x0 + w - 4, 3)):
        c.fill(c.rect(gx, base - 3, gx + 2, base - 1), cols('#f06579', '#8bb6dc', '#bb98f4')[i])
    c.fill(c.edge(m, inner=False), OUTL)


def front_layer(c, light):
    """Everything that lies over the gears."""
    bx, by, br, _ = BIG
    # the brace that holds the big gear's bearing: a timber across, in front, with bolts
    bm = beam(c, 150, 104, 246, 88, 5, 51)
    hole = c.ellipse(bx, by, 12.5)
    # (the bearing: a brass plate round the middle with the crown in it)
    c.alpha &= ~hole
    plate = c.ellipse(bx, by, 12.5)
    c.ramp(plate, [NIGHT_PLATE[0], NIGHT_PLATE[1], NIGHT_PLATE[2]], (c.yy - by + 12) / 24.0, soft=0.3)
    ringm = plate & ~c.ellipse(bx, by, 10.6)
    c.ramp(ringm, [BRASS[1], BRASS[2], BRASS[3], BRASS[4]], np.clip(0.5 + ((c.xx - bx) * 0.6 + (c.yy - by) * 0.8) / 22.0, 0, 1), soft=0.25)
    c.fill(c.edge(plate, inner=False), OUTL)
    crown(c, bx, by + 7, 16, 12)
    for px_, py_ in ((156, 102), (240, 88)):
        c.fill(c.rect(px_, py_, px_ + 2, py_ + 2), DULL[1])
        c.px(px_ + 1, py_ + 1, DULL[5])
    # hubs of the two small gears: a cap with a bolt
    for gx, gy, r in ((MID[0], MID[1], 4.2), (SMALL[0], SMALL[1], 3.4)):
        cap = c.ellipse(gx, gy, r)
        c.fill(cap, DULL[2])
        c.fill(cap & ~c.shift(cap, 1, 1), DULL[0])
        c.fill(cap & ~c.shift(cap, -1, -1), DULL[4])
        c.px(int(round(gx)), int(round(gy)), DULL[5])
        c.fill(c.edge(cap, inner=False), OUTL)
    # moss on the timbers, vines from the cross beam down over the wheels
    top_beam = c.line(158, 47, 262, 41, width=5)
    moss_layer = Canvas(W, H)
    moss_layer.alpha = top_beam.copy()
    mm = moss_on(c, top_beam, 52, amount=0.7, hang=3)
    moss_on(c, bm & ~hole, 53, amount=0.45, hang=2)
    for vx, vy, ln, s in ((178, 49, 22, 61), (186, 48, 34, 62), (214, 46, 15, 63), (226, 45, 41, 64), (247, 43, 19, 65), (255, 43, 30, 66), (166, 50, 12, 67)):
        vine(c, vx, vy, ln, s)
    # the lantern on an iron hook at the house's left eave
    lx, ly = 52, 119
    c.fill(c.line(57, 114, lx, 114) | c.line(lx, 114, lx, ly), OUTL)
    body = c.rect(lx - 3, ly, lx + 4, ly + 10)
    c.fill(body, OUTL)
    c.fill(c.rect(lx - 2, ly + 2, lx + 3, ly + 9), GLOW[2])
    c.fill(c.rect(lx - 1, ly + 3, lx + 2, ly + 8), GLOW[1])
    c.fill(c.rect(lx - 3, ly, lx + 4, ly + 2), AGED[2])
    c.fill(c.rect(lx - 2, ly - 1, lx + 3, ly), AGED[3])
    c.fill(c.rect(lx - 3, ly + 9, lx + 4, ly + 10), AGED[4])
    c.px(lx - 3, ly + 5, OUTL), c.px(lx + 3, ly + 5, OUTL)
    light.fill(c.rect(lx, ly + 4, lx + 1, ly + 7), GLOW[0])
    # a cobweb in the corner of post and beam
    for k in range(1, 8):
        if k % 2:
            c.px(170 + k, 50 + k, STONE[2])
        c.px(170 + k, 50, STONE[3]) if k % 3 == 0 else None
    for k in range(0, 6, 2):
        c.px(173 + k, 56 - k // 2, STONE[3])
    # a crow on the cross beam, looking at you
    bx_, by_ = 204, 38
    crow = c.rect(bx_, by_, bx_ + 6, by_ + 4) | c.rect(bx_ + 4, by_ - 3, bx_ + 8, by_ + 1) | c.rect(bx_ - 3, by_ + 1, bx_, by_ + 3)
    c.fill(crow, STONE[4])
    c.fill(c.rect(bx_ + 1, by_, bx_ + 5, by_ + 1) | c.rect(bx_ + 5, by_ - 3, bx_ + 7, by_ - 2), STONE[2])
    c.fill(c.edge(crow, inner=False), OUTL)
    c.px(bx_ + 6, by_ - 2, GLOW[1])
    c.px(bx_ + 8, by_ - 1, AGED[2])
    c.px(bx_ + 2, by_ + 4, AGED[3]), c.px(bx_ + 4, by_ + 4, AGED[3])


NIGHT_PLATE = cols('#3a3046', '#2d2538', '#231d2c')
# the lamps: x, y, how far their light reaches (lantern, window, door)
LAMPS = ((52.5, 124.5, 17), (113, 126, 12), (95, 138, 11))


def clutter(c, top):
    """What lies about on the ground: a broken wheel, a barrel, mushrooms, a crooked sign."""
    # the broken gear, half sunk, mossy
    gx, gy, r = 268, GROUND_Y + 3, 13
    d = np.sqrt((c.xx - gx) ** 2 + ((c.yy - gy) * 1.25) ** 2)
    ang = np.arctan2((c.yy - gy) * 1.25, c.xx - gx)
    teeth = (np.cos(ang * 9 + 0.6) > 0.1)
    m = ((d <= r - 3) | ((d <= r) & teeth)) & (d >= 4.5) & (c.yy <= GROUND_Y + 1) & ~((ang > -1.2) & (ang < -0.75))
    c.ramp(m, [DULL[1], DULL[2], DULL[3], DULL[4]], np.clip(0.5 + ((c.xx - gx) * 0.6 + (c.yy - gy) * 0.8) / (2.0 * r), 0, 1), soft=0.25)
    c.fill(c.edge(m, inner=False) & (c.yy <= GROUND_Y), OUTL)
    moss_on(c, m, 71, amount=0.75, hang=3)
    c.fill(m & (noise2d(W, H, 72, period=4, octaves=1) > 0.7), PATINA[2])
    # a barrel by the house
    bx0 = 133
    barrel = rounded_rect(c, bx0, GROUND_Y - 13, bx0 + 11, GROUND_Y + 1)
    planks(c, barrel, 73, width=3)
    for by_ in (GROUND_Y - 11, GROUND_Y - 3):
        c.fill(c.rect(bx0, by_, bx0 + 11, by_ + 1) & barrel, STONE[1])
        c.fill(c.rect(bx0, by_ + 1, bx0 + 11, by_ + 2) & barrel, STONE[4])
    c.fill(c.edge(barrel, inner=False) & (c.yy <= GROUND_Y), OUTL)
    moss_on(c, barrel, 74, amount=0.5, hang=1)
    # mushrooms
    for mx, my, big in ((176, GROUND_Y, 1), (181, GROUND_Y, 0), (283, GROUND_Y - 1, 0), (228, GROUND_Y, 1), (40, GROUND_Y, 0), (44, GROUND_Y, 1)):
        c.px(mx, my - 1, hexc('#e8dcc0'))
        if big:
            c.px(mx, my - 2, hexc('#e8dcc0'))
        cy = my - 2 - big
        capm = c.rect(mx - 1 - big, cy, mx + 2 + big, cy + 1) | c.rect(mx - big, cy - 1, mx + 1 + big, cy)
        c.fill(capm, GLOW[3])
        c.px(mx - big, cy - 1, GLOW[2])
    # a sign on a crooked post, its board hanging by one nail
    c.fill(c.line(293, GROUND_Y, 290, GROUND_Y - 20, width=2), WOOD[3])
    board = c.poly([(281, GROUND_Y - 21), (301, GROUND_Y - 17), (300, GROUND_Y - 10), (280, GROUND_Y - 14)])
    c.fill(board, WOOD[2])
    c.fill(board & ~c.shift(board, 0, 1), WOOD[1])
    c.fill(c.edge(board, inner=False) | c.edge(c.line(293, GROUND_Y, 290, GROUND_Y - 20, width=2), inner=False) & ~board, OUTL)
    c.fill(c.line(285, GROUND_Y - 17, 296, GROUND_Y - 15) | c.line(285, GROUND_Y - 14, 292, GROUND_Y - 13), WOOD[4])
    c.px(291, GROUND_Y - 17, DULL[1])
    # grass and flowers
    grass(c, 36, 290, GROUND_Y, 75, tall=3)
    grass(c, 146, 160, GROUND_Y, 76, tall=6)
    grass(c, 252, 284, GROUND_Y, 77, tall=6)
    grass(c, 36, 58, GROUND_Y, 78, tall=5)
    for fx, fy, col in ((52, GROUND_Y - 5, '#f06579'), (141, GROUND_Y - 6, '#f2ede3'), (262, GROUND_Y - 5, '#bb98f4'), (176, GROUND_Y - 3, '#f2ede3'), (240, GROUND_Y - 4, '#f06579')):
        c.px(fx, fy, hexc(col))


def rounded_rect(c, x0, y0, x1, y1):
    return c.rect(x0 + 1, y0, x1 - 1, y1) | c.rect(x0, y0 + 1, x1, y1 - 1)


def back_layer():
    c = Canvas(W, H)
    # a dim moon behind the house's chimney, banded, with its halo, and a few stars
    mx, my = 104, 52
    d = np.sqrt((c.xx - mx) ** 2 + (c.yy - my) ** 2)
    c.ramp(d <= 70, [SKY[0], SKY[1], SKY[2], NIGHT], np.clip((d - 22) / 48.0, 0, 1), soft=0.45)
    disc = d <= 25
    c.fill(disc, hexc('#2f2840'))
    c.fill(disc & (np.sqrt((c.xx - mx + 6) ** 2 + (c.yy - my + 5) ** 2) <= 19), hexc('#3a324c'))
    for cx_, cy_, r in ((112, 46, 4), (97, 62, 3.4), (108, 64, 2), (93, 47, 2.6)):
        c.fill(c.ellipse(cx_, cy_, r) & disc, hexc('#2a2338'))
    c.alpha = d <= 70
    g = rng(5)
    for _ in range(54):
        x, y = int(g.integers(4, W - 4)), int(g.integers(4, 150))
        if np.sqrt((x - mx) ** 2 + (y - my) ** 2) < 30:
            continue
        c.px(x, y, hexc('#f2ede3') if g.random() < 0.22 else hexc('#6b6280'))
    return c


def light_layer(lit, under):
    """The warm light: the pixels marked as lit, and around each lamp the surfaces it reaches,
    a step warmer. Only what is painted gets light (the night stays dark), and nothing where
    a gear turns, since this layer lies still."""
    out = Canvas(W, H)
    gears = np.zeros((H, W), bool)
    for gx, gy, r, _ in (BIG, MID, SMALL):
        gears |= np.sqrt((out.xx - gx) ** 2 + (out.yy - gy) ** 2) <= r + 2
    warm = np.array(GLOW[2], float)
    for (lx, ly, r) in LAMPS:
        d = np.sqrt((out.xx - lx) ** 2 + (out.yy - ly) ** 2)
        t = np.clip(1 - d / r, 0, 1)
        reach = under.alpha & ~gears & (d <= r)
        # two steps of warmth, the inner one stronger, the border between them dithered
        k = np.where(t * 1.5 > 0.6 + out.bayer * 0.5, 0.42, np.where(t * 2.2 > out.bayer * 0.9 + 0.15, 0.2, 0.0))
        mixed = (under.a.astype(float) * (1 - k[..., None]) + warm * k[..., None]).astype(np.uint8)
        sel = reach & (k > 0)
        out.a[sel] = mixed[sel]
        out.alpha |= sel
    out.a[lit.alpha] = lit.a[lit.alpha]
    out.alpha |= lit.alpha
    return out


def pendulum_sheet():
    """The pendulum under the middle gear: a rod and a brass bob, 13 frames across the swing."""
    size_w, size_h = 40, PEND_LEN + 12
    sheet = Image.new('RGBA', (size_w * PEND_FRAMES, size_h), (0, 0, 0, 0))
    frames = []
    for i in range(PEND_FRAMES):
        a = math.radians(-PEND_SWING + 2 * PEND_SWING * i / (PEND_FRAMES - 1))
        c = Canvas(size_w, size_h)
        ox, oy = size_w // 2, 2
        ex, ey = ox + math.sin(a) * PEND_LEN, oy + math.cos(a) * PEND_LEN
        rod = c.line(ox, oy, ex, ey, width=1)
        c.fill(rod, DULL[3])
        bob = c.ellipse(ex, ey, 4.6)
        c.ramp(bob, [BRASS[1], BRASS[2], BRASS[3], BRASS[4]], np.clip(0.5 + ((c.xx - ex) * 0.6 + (c.yy - ey) * 0.8) / 9.0, 0, 1), soft=0.25)
        c.px(int(round(ex)) - 1, int(round(ey)) - 2, BRASS[0])
        c.fill(c.edge(bob | rod, inner=False), OUTL)
        im = c.image(1, rgba=True)
        sheet.paste(im, (i * size_w, 0))
        frames.append(im)
    sheet.save(os.path.join(OUT, 'pendulum.png'), optimize=True)
    return frames, (size_w, size_h)


GLYPH = {
    'K': 'X...X X..X. X.X.. XX... X.X.. X..X. X...X',
    'R': 'XXXX. X...X X...X XXXX. X.X.. X..X. X...X',
    'O': '.XXX. X...X X...X X...X X...X X...X .XXX.',
    'N': 'X...X XX..X XX..X X.X.X X..XX X..XX X...X',
    'W': 'X...X X...X X...X X.X.X X.X.X XX.XX X...X',
    'E': 'XXXXX X.... X.... XXXX. X.... X.... XXXXX',
}
SMALLG = {'S': '.XX X.. .X. ..X XX.', 'E': 'XXX X.. XX. X.. XXX', 'A': '.X. X.X XXX X.X X.X', 'O': '.X. X.X X.X X.X .X.',
          'N': 'X.X XXX XXX X.X X.X', '2': 'XX. ..X .X. X.. XXX', ' ': '... ... ... ... ...'}


def logo():
    """KRONWERKE in raised brass letters and a small SEASON 2 plate under it."""
    k = 2
    text = 'KRONWERKE'
    lw, gap = 5 * k, 2
    total = len(text) * lw + (len(text) - 1) * gap
    Wc, Hc = total + 6, 34
    c = Canvas(Wc, Hc)
    x0, y0 = 3, 1
    face = c.none()
    for i, ch in enumerate(text):
        for r, row in enumerate(GLYPH[ch].split()):
            for q, v in enumerate(row):
                if v == 'X':
                    face |= c.rect(x0 + i * (lw + gap) + q * k, y0 + r * k, x0 + i * (lw + gap) + q * k + k, y0 + r * k + k)
    depth = c.shift(face, 0, 1) | c.shift(face, 0, 2) | c.shift(face, 1, 2)
    solid = face | depth
    grow = solid.copy()
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        grow |= c.shift(solid, dx, dy)
    c.fill(grow, OUTL)
    c.fill(depth, BRASS[4])
    c.ramp(face, [BRASS[0], BRASS[1], BRASS[2], BRASS[2], BRASS[3]], (c.yy - y0) / float(7 * k), soft=0.35)
    c.fill(face & ~c.shift(face, 0, 1), BRASS[0])
    c.fill(face & ~c.shift(face, 0, -1), BRASS[3])
    label = 'SEASON 2'
    pw = len(label) * 4 - 1 + 14
    px0, py0 = Wc // 2 - pw // 2, 22
    red = cols('#a8404a', '#86303e', '#662536', '#4a1c2c')
    c.fill(rounded_rect(c, px0, py0, px0 + pw, py0 + 11), OUTL)
    c.ramp(c.rect(px0 + 1, py0 + 1, px0 + pw - 1, py0 + 10), [red[0], red[1], red[2]], (c.yy - py0) / 11.0, soft=0.3)
    c.fill(c.rect(px0 + 1, py0 + 1, px0 + pw - 1, py0 + 2), BRASS[2])
    c.fill(c.rect(px0 + 1, py0 + 9, px0 + pw - 1, py0 + 10), BRASS[4])
    x = px0 + 7
    for ch in label:
        for r, row in enumerate(SMALLG[ch].split()):
            for q, v in enumerate(row):
                if v == 'X':
                    c.px(x + q, py0 + 4 + r, red[3])
                    c.px(x + q, py0 + 3 + r, hexc('#f2ede3'))
        x += 4
    return c


def build(preview=None):
    os.makedirs(OUT, exist_ok=True)
    scene, front, lit = Canvas(W, H), Canvas(W, H), Canvas(W, H)
    top = island(scene)
    frame_back(scene)
    house(scene, lit)
    clutter(scene, top)
    front_layer(front, lit)
    back = back_layer()
    under = Canvas(W, H)
    under.paste(scene)
    under.paste(front)
    light = light_layer(lit, under)
    for name, c in (('back', back), ('scene', scene), ('front', front), ('light', light), ('logo', logo())):
        c.image(1, rgba=True).save(os.path.join(OUT, name + '.png'), optimize=True)
    big, sb = gear_sheet('gear_big', BIG, 0.0, 81, 'big', 12)
    mid, sm = gear_sheet('gear_mid', MID, mesh_base(AT_MID, 8), 82, 'mid', 8)
    small, ss = gear_sheet('gear_small', SMALL, mesh_base(AT_SMALL, 6), 83, 'small', 8)
    pend, psize = pendulum_sheet()
    place = lambda spec, size: (int(round(spec[0] - (size - 1) / 2.0)), int(round(spec[1] - (size - 1) / 2.0)))
    print('for JoinOverlay: big', place(BIG, sb), sb, 'mid', place(MID, sm), sm, 'small', place(SMALL, ss), ss,
          'pendulum', (PEND[0] - psize[0] // 2, PEND[1] - 2), psize, PEND_FRAMES)
    if preview:
        os.makedirs(preview, exist_ok=True)

        def compose(f):
            im = Image.new('RGBA', (W, H), NIGHT + (255,))
            im.alpha_composite(back.image(1, rgba=True))
            im.alpha_composite(scene.image(1, rgba=True))
            sw = math.sin(f / 9.0)
            pf = int(round((sw * 0.5 + 0.5) * (PEND_FRAMES - 1)))
            im.alpha_composite(pend[pf], (PEND[0] - psize[0] // 2, PEND[1] - 2))
            for frames, size, spec in ((small, ss, SMALL), (mid, sm, MID), (big, sb, BIG)):
                fr = frames[f % len(frames)]
                im.alpha_composite(fr, (int(round(spec[0] - (size - 1) / 2.0)), int(round(spec[1] - (size - 1) / 2.0))))
            im.alpha_composite(front.image(1, rgba=True))
            im.alpha_composite(light.image(1, rgba=True))
            return im.convert('RGB')

        still = compose(0)
        still.resize((W * 4, H * 4), Image.NEAREST).save(os.path.join(preview, 'still.png'))
        seq = [compose(f).resize((W * 2, H * 2), Image.NEAREST) for f in range(0, 96)]
        seq[0].save(os.path.join(preview, 'loop.gif'), save_all=True, append_images=seq[1:], duration=110, loop=0)
        for f in (3, 7):
            compose(f).resize((W * 4, H * 4), Image.NEAREST).save(os.path.join(preview, 'f%d.png' % f))


if __name__ == '__main__':
    build(sys.argv[2] if len(sys.argv) > 2 and sys.argv[1] == 'preview' else None)
    print('ok', OUT)
