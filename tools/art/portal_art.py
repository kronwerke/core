#!/usr/bin/env python3
"""Paints the textures of the farm world's portal: the frame block's three layers (iron cage,
stone shell, glowing core) and the stages and the deep layer of the portal's surface.
Run from the repository's root: python3 tools/art/portal_art.py"""
import math, random
from PIL import Image

OUT = 'src/main/resources/assets/kronwerke/textures/block/'
SRC = 'tools/art/src/'
random.seed(7)

def clamp(v): return max(0, min(255, int(round(v))))
def mix(a, b, k): return tuple(clamp(a[i] + (b[i] - a[i]) * k) for i in range(3))
def hexc(h): return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))

old = Image.open(SRC + 'grubenrahmen_flat.png').convert('RGBA')  # the first, painted-flat frame: the source of the palette
oldpx = old.load()

# ---- iron: the cage. The old picture's border, and plain iron where the panel was (seen on the beams' inner sides)
iron = Image.new('RGBA', (32, 32))
ip = iron.load()
for y in range(32):
    for x in range(32):
        d = min(x, y, 31 - x, 31 - y)
        if d < 4:
            ip[x, y] = oldpx[x, y]
        else:
            n = random.random()
            base = mix(hexc('3a3c43'), hexc('4c4f57'), n)
            ip[x, y] = base + (255,)
iron.save(OUT + 'grubenrahmen_iron.png')

# ---- stone: the shell behind the cage, with a window in every face and cracks running to it.
# The cracks are dark in the sleeping block and carry the core's light in the burning one.
stones = [oldpx[x, y][:3] for x in range(7, 25) for y in range(7, 25) if oldpx[x, y][2] < 0x60 and oldpx[x, y][0] > 0x10]
ground = [[random.choice(stones) for x in range(32)] for y in range(32)]
def stone(lit):
    im = Image.new('RGBA', (32, 32))
    sp = im.load()
    for y in range(32):
        for x in range(32):
            sp[x, y] = ground[y][x] + (255,)
    vein = [hexc('a8e4ff'), hexc('84d6ff'), hexc('5aa6d2'), hexc('3e6f93')] if lit else [hexc('35556f'), hexc('2d4761'), hexc('26394f'), hexc('1f2d40')]
    def crack(points, bright=0):
        for i in range(len(points) - 1):
            (x0, y0), (x1, y1) = points[i], points[i + 1]
            n = max(abs(x1 - x0), abs(y1 - y0), 1)
            for s_ in range(n + 1):
                x, y = round(x0 + (x1 - x0) * s_ / n), round(y0 + (y1 - y0) * s_ / n)
                for sx, sy in ((x, y), (31 - x, 31 - y), (31 - y, x), (y, 31 - x)):  # the same on all four sides
                    sp[sx, sy] = vein[min(3, bright + (0 if s_ % 3 else 1))] + (255,)
    # from the window's edge (10) out to the cage (4): one forked crack per side
    crack([(14, 9), (14, 8), (13, 7), (13, 6), (12, 5), (12, 4)], 0)
    crack([(13, 7), (15, 6), (16, 5)], 1)
    for y in range(32):
        for x in range(32):
            d = min(x, y, 31 - x, 31 - y)
            c = sp[x, y][:3]
            if d == 4: c = mix(c, hexc('060608'), 0.45)       # the cage's shadow; a fine seam where two frames join
            elif d == 9: c = mix(c, hexc('5aa6d2') if lit else hexc('2d4761'), 0.55 if lit else 0.35)   # the window's rim catches the core's light
            elif d >= 10: c = mix(c, hexc('2a5b86') if lit else hexc('101c2c'), 0.6)   # the window's walls, seen from the side
            sp[x, y] = c + (255,)
    return im
stone(False).save(OUT + 'grubenrahmen_stone.png')
stone(True).save(OUT + 'grubenrahmen_stone_lit.png')

# ---- core: the light inside, from the portal's own swirl. Unlit it sleeps and breathes, lit it burns
portal = Image.open(SRC + 'minenportal.png').convert('RGBA')
FRAMES = 16
def core(lit):
    """An eye of light in its socket: bright in the middle, dark at the rim, the portal's swirl
    moving through it and three faint rays turning. Only the middle 12 pixels show through the window."""
    im = Image.new('RGBA', (32, 32 * FRAMES))
    px = im.load()
    for f in range(FRAMES):
        src = portal.crop((0, 32 * (f * 2), 32, 32 * (f * 2) + 32)).load()
        t = f / FRAMES
        breath = 0.5 + 0.5 * math.sin(t * math.tau)
        for y in range(32):
            for x in range(32):
                r, g, b, _ = src[x, y]
                lum = (r + g + b) / 765
                dx, dy = x - 15.5, y - 15.5
                d = math.hypot(dx, dy) / 8.5              # 0 in the middle, 1 at the window's corner
                ray = 0.5 + 0.5 * math.sin(3 * math.atan2(dy, dx) + t * math.tau)
                if lit:
                    k = max(0.0, 1 - d * (0.80 - 0.10 * breath))
                    c = mix(hexc('1d5f9c'), hexc('84d6ff'), min(1, k * 1.5 + 0.25 * lum))
                    c = mix(c, hexc('f4fcff'), min(1, max(0.0, k - 0.35) * 1.9 + 0.12 * ray * k))
                    c = mix(c, (r, g, b), 0.18)
                else:
                    k = max(0.0, 1 - d * (1.05 - 0.15 * breath))
                    c = mix(hexc('070c16'), hexc('1c3a5e'), min(1, k * 1.2 + 0.3 * lum))
                    c = mix(c, hexc('4f93c4'), max(0.0, k - 0.55) * (1.1 + 0.6 * breath) + 0.05 * ray * k)
                px[x, 32 * f + y] = c + (255,)
    return im
core(False).save(OUT + 'grubenrahmen_core.png')
core(True).save(OUT + 'grubenrahmen_core_lit.png')
open(OUT + 'grubenrahmen_core.png.mcmeta', 'w').write('{"animation": {"frametime": 5, "interpolate": true}}')
open(OUT + 'grubenrahmen_core_lit.png.mcmeta', 'w').write('{"animation": {"frametime": 2, "interpolate": true}}')

# ---- the portal's surface while it forms: a thin veil, then half, then whole; and a layer deep inside
P = portal.load()
H = portal.height
def stage(k_alpha, cut):
    im = Image.new('RGBA', portal.size)
    px = im.load()
    for y in range(H):
        for x in range(32):
            r, g, b, a = P[x, y]
            lum = (r + g + b) / 765
            keep = 0 if lum < cut else min(1, (lum - cut) / max(0.01, 1 - cut) * 2.2)
            px[x, y] = (r, g, b, clamp(a * k_alpha * keep))
    return im
stage(0.55, 0.50).save(OUT + 'minenportal_s0.png')
stage(0.80, 0.30).save(OUT + 'minenportal_s1.png')
# the whole surface lets more through where it is dark, so that the deep layer shows
whole = Image.new('RGBA', portal.size)
wp = whole.load()
for y in range(H):
    for x in range(32):
        r, g, b, a = P[x, y]
        lum = (r + g + b) / 765
        wp[x, y] = (r, g, b, clamp(110 + 150 * min(1, lum * 1.5)))
whole.save(OUT + 'minenportal_front.png')
deep = Image.new('RGBA', portal.size)
dp = deep.load()
NF = H // 32
sparks = [(random.randrange(32), random.randrange(32), random.random()) for _ in range(9)]
for f in range(NF):
    g2 = (f * 5 + 11) % NF   # the swirl of another moment, turned over: it must not move with the front
    for y in range(32):
        for x in range(32):
            r, g, b, a = P[31 - x, 32 * g2 + (31 - y)]
            lum = (r + g + b) / 765
            c = mix(hexc('0a0f24'), hexc('27407a'), min(1, lum * 1.4))
            dp[x, 32 * f + y] = c + (255,)
    for sx, sy, ph in sparks:   # gold sparks that rise slowly and glimmer
        yy = int(sy - f * 32 / NF) % 32
        k = 0.5 + 0.5 * math.sin((f / NF + ph) * math.tau * 3)
        dp[sx, 32 * f + yy] = mix(hexc('7a5a1e'), hexc('ffd98a'), k) + (255,)
deep.save(OUT + 'minenportal_deep.png')
for n in ('minenportal_s0', 'minenportal_s1', 'minenportal_front', 'minenportal_deep'):
    open(OUT + n + '.png.mcmeta', 'w').write('{"animation": {"frametime": %d, "interpolate": true}}' % (4 if n.endswith('deep') else 2))

# ---- for shader packs (LabPBR): the cage keeps the old picture's relief, the core gives off light
on, os_ = Image.open(SRC + 'grubenrahmen_flat_n.png').convert('RGBA'), Image.open(SRC + 'grubenrahmen_flat_s.png').convert('RGBA')
n2, s2 = Image.new('RGBA', (32, 32), (128, 128, 255, 255)), Image.new('RGBA', (32, 32), os_.getpixel((1, 1)))
for y in range(32):
    for x in range(32):
        if min(x, y, 31 - x, 31 - y) < 4:
            n2.putpixel((x, y), on.getpixel((x, y)))
            s2.putpixel((x, y), os_.getpixel((x, y)))
n2.save(OUT + 'grubenrahmen_iron_n.png')
s2.save(OUT + 'grubenrahmen_iron_s.png')
for name, glow in (('grubenrahmen_core', 110), ('grubenrahmen_core_lit', 254)):
    Image.new('RGBA', (32, 32 * FRAMES), (20, 10, 0, glow)).save(OUT + name + '_s.png')
    open(OUT + name + '_s.png.mcmeta', 'w').write(open(OUT + name + '.png.mcmeta').read())
print('painted')
