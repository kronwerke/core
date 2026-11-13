#!/usr/bin/env python3
"""The music of the menus: a quiet loop of two minutes forty, written here and synthesised with numpy.

    menu.py [out.ogg]      writes assets/kronwerke/sounds/menu.ogg (or the file given); with a
                           second argument "levels" it prints how loud every voice is

It plays on the title screen, in the menus outside a world and while the game connects
(client/MenuMusic.java). It is meant to be heard without being listened to: 72 beats a minute,
no drums, nothing sudden, D minor that leans on F major. The tune is the trailer's theme taken
slowly (D F A, G F E) on a music box, over a soft pad, a harp in eighths and a low string.

    bars  1 -  8   the pad alone, the music box looks for the tune
    bars  9 - 16   the tune, the harp comes in
    bars 17 - 24   a low string under answers of the music box; ends on the dominant
    bars 25 - 32   the tune again, an octave of small bells over it
    bars 33 - 40   the same thoughts in F major
    bars 41 - 48   everything thins out to the pad, and the first bar follows without a seam

Forty-eight bars of ten thirds of a second are 160 seconds to the sample. What rings on after
the last bar (notes, the room) is laid over the beginning, so the loop has no cut. Same script,
same file; ffmpeg turns the wav into ogg.
"""
import math
import os
import subprocess
import sys
import wave

import numpy as np
from scipy import signal

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "..", "src", "main", "resources", "assets", "kronwerke", "sounds", "menu.ogg")
SR = 44100
BEAT = 60.0 / 72.0
BAR = 4 * BEAT
BARS = 48
N = int(round(BARS * BAR * SR))
TAIL = 9 * SR
RNG = np.random.default_rng(7227)

NOTE = {'C': 0, 'D': 2, 'E': 4, 'F': 5, 'G': 7, 'A': 9, 'B': 11}


def hz(name):
    step = NOTE[name[0]]
    rest = name[1:]
    while rest[0] in '#b':
        step += 1 if rest[0] == '#' else -1
        rest = rest[1:]
    return 440.0 * 2 ** ((12 * (int(rest) + 1) + step - 69) / 12.0)


def clock(dur):
    return np.arange(int(dur * SR)) / SR


def lowpass(x, f, order=2):
    return signal.sosfilt(signal.butter(order, f, 'low', fs=SR, output='sos'), x, axis=0)


def highpass(x, f, order=2):
    return signal.sosfilt(signal.butter(order, f, 'high', fs=SR, output='sos'), x, axis=0)


class Bus:
    def __init__(self, name, room):
        self.name, self.room = name, room
        self.buf = np.zeros((N + TAIL, 2))

    def add(self, sig, at, gain=1.0, pan=0.0):
        i = int(round(at * SR))
        if sig.ndim == 1:
            left, right = math.cos((pan + 1) * math.pi / 4), math.sin((pan + 1) * math.pi / 4)
            sig = np.stack([sig * left, sig * right], axis=1)
        sig = sig[:N + TAIL - i]
        self.buf[i:i + len(sig)] += sig * gain


# ---- the voices ------------------------------------------------------------------------

def box(f, dur):
    """The music box: a clear tone, a few partials that die sooner, a soft start."""
    t = clock(dur)
    tau = 0.9 * (520.0 / f) ** 0.4
    out = np.zeros_like(t)
    for ratio, amp, life in ((1, 1.0, 1.0), (2, 0.3, 0.55), (3.01, 0.1, 0.32), (4.2, 0.05, 0.2)):
        out += amp * np.sin(2 * math.pi * f * ratio * t) * np.exp(-t / (tau * life))
    out *= np.minimum(1.0, t / 0.004)
    out[-int(0.05 * SR):] *= np.linspace(1, 0, int(0.05 * SR))
    return out


def harp(f, dur=1.6):
    """A plucked string, dull: the high harmonics go at once."""
    t = clock(dur)
    out = np.zeros_like(t)
    for n in range(1, 7):
        out += (1.0 / n ** 1.6) * np.sin(2 * math.pi * f * n * t + n * 0.7) * np.exp(-t * (1.6 + n * 1.5))
    out *= np.minimum(1.0, t / 0.006)
    out[-int(0.05 * SR):] *= np.linspace(1, 0, int(0.05 * SR))
    return out


def pad(f, dur):
    """Soft strings far away: three voices a little apart, more than a second to come, two to go; stereo."""
    rel = 2.4
    t = clock(dur + rel)
    out = np.zeros((len(t), 2))
    for cents, pan in ((-6, -0.8), (0, 0.0), (6, 0.8)):
        ff = f * 2 ** (cents / 1200.0)
        v = np.zeros_like(t)
        for n in range(1, 6):
            v += (1.0 / n ** 1.7) * np.sin(2 * math.pi * ff * n * t + pan * n + f)
        left, right = math.cos((pan + 1) * math.pi / 4), math.sin((pan + 1) * math.pi / 4)
        out[:, 0] += v * left
        out[:, 1] += v * right
    env = np.minimum(1.0, t / 1.3) ** 1.5 * np.minimum(1.0, np.maximum(0.0, (dur + rel - t) / rel)) ** 1.5
    return out * (env * (1.0 + 0.05 * np.sin(2 * math.pi * 0.21 * t + f)))[:, None]


def string(f, dur):
    """The low string: a slow bow, a little vibrato once the note stands."""
    t = clock(dur + 0.5)
    ph = 2 * math.pi * f * t + (f * 0.0045 / 4.6) * np.clip((t - 0.5) / 0.6, 0, 1) * np.sin(2 * math.pi * 4.6 * t)
    out = np.zeros_like(t)
    for n in range(1, 8):
        out += (1.0 / n ** 1.3) * np.sin(n * ph + n)
    env = np.minimum(1.0, t / 0.45) * np.minimum(1.0, np.maximum(0.0, (dur + 0.5 - t) / 0.6))
    return lowpass(out * env, 1800)


def deep(f, dur):
    """The bass: almost a sine, no edge."""
    t = clock(dur + 0.8)
    out = np.sin(2 * math.pi * f * t) + 0.25 * np.sin(2 * math.pi * 2 * f * t)
    env = np.minimum(1.0, t / 0.25) * np.minimum(1.0, np.maximum(0.0, (dur + 0.8 - t) / 0.9))
    return out * env


# ---- the piece -------------------------------------------------------------------------

CHORD = {                          # root for the bass, the pad's notes, the harp's four
    'Dm9': ('D2', ('D3', 'A3', 'E4', 'F4', 'A4'), ('D3', 'A3', 'F4', 'E5')),
    'Bbmaj7': ('Bb1', ('Bb2', 'F3', 'A3', 'D4', 'F4'), ('Bb2', 'F3', 'D4', 'A4')),
    'Fmaj7': ('F2', ('F3', 'C4', 'E4', 'A4'), ('F3', 'C4', 'A4', 'E5')),
    'Cadd9': ('C2', ('C3', 'G3', 'D4', 'E4', 'G4'), ('C3', 'G3', 'E4', 'D5')),
    'Gm7': ('G2', ('G2', 'D3', 'F3', 'Bb3', 'D4'), ('G2', 'D3', 'Bb3', 'F4')),
    'Dm': ('D2', ('D3', 'A3', 'D4', 'F4'), ('D3', 'A3', 'F4', 'D5')),
    'Asus': ('A1', ('A2', 'E3', 'A3', 'D4', 'E4'), ('A2', 'E3', 'D4', 'A4')),
    'A': ('A1', ('A2', 'E3', 'A3', 'C#4', 'E4'), ('A2', 'E3', 'C#4', 'A4')),
    'F': ('F2', ('F3', 'C4', 'F4', 'A4'), ('F3', 'C4', 'A4', 'F5')),
    'C/E': ('E2', ('E3', 'G3', 'C4', 'E4', 'G4'), ('E3', 'G3', 'E4', 'C5')),
    'Dm7': ('D2', ('D3', 'A3', 'C4', 'F4', 'A4'), ('D3', 'A3', 'F4', 'C5')),
}
# the harmony, one entry a bar
HARMONY = (['Dm9', 'Dm9', 'Bbmaj7', 'Bbmaj7', 'Fmaj7', 'Fmaj7', 'Cadd9', 'Cadd9'] * 2
           + ['Gm7', 'Gm7', 'Dm', 'Dm', 'Bbmaj7', 'Bbmaj7', 'Asus', 'A']
           + ['Dm9', 'Dm9', 'Bbmaj7', 'Bbmaj7', 'Fmaj7', 'Fmaj7', 'Cadd9', 'Cadd9']
           + ['F', 'F', 'C/E', 'C/E', 'Dm7', 'Dm7', 'Bbmaj7', 'Bbmaj7']
           + ['Dm9', 'Dm9', 'Bbmaj7', 'Bbmaj7', 'Fmaj7', 'Fmaj7', 'Cadd9', 'Cadd9'])
TUNE = (('D5', 0, 1), ('F5', 1, 1), ('A5', 2, 2)), (('G5', 0, 1), ('F5', 1, 1), ('E5', 2, 2)), \
       (('D5', 0, 1), ('F5', 1, 1), ('Bb5', 2, 2)), (('A5', 0, 1), ('G5', 1, 1), ('F5', 2, 2)), \
       (('A5', 0, 1.5), ('G5', 1.5, 0.5), ('F5', 2, 2)), (('C5', 0, 1), ('F5', 1, 1), ('A5', 2, 2)), \
       (('G5', 0, 2), ('E5', 2, 1), ('D5', 3, 1)), (('E5', 0, 3),)
# the music box per bar (bars count from 0): note, beat, beats
BOX = {
    1: (('A4', 0, 1), ('D5', 1, 1), ('F5', 2, 2)), 2: (('D5', 0, 2), ('F5', 2, 1), ('G5', 3, 1)), 3: (('A5', 0, 3),),
    4: (('G5', 0, 1), ('F5', 1, 1), ('C5', 2, 2)), 5: (('A4', 1, 1), ('C5', 2, 2)), 6: (('E5', 0, 2), ('D5', 2, 1), ('C5', 3, 1)), 7: (('D5', 0, 3),),
    17: (('D6', 2, 2),), 19: (('A5', 2, 2),), 21: (('F5', 1, 1), ('D6', 2, 2)), 23: (('E5', 0, 2), ('A5', 2, 2)),
    32: (('C6', 0, 2), ('A5', 2, 2)), 33: (('F5', 0, 1), ('G5', 1, 1), ('A5', 2, 2)), 34: (('G5', 0, 2), ('E5', 2, 2)),
    35: (('C5', 0, 1), ('D5', 1, 1), ('E5', 2, 2)), 36: (('F5', 0, 2), ('D5', 2, 2)), 37: (('A4', 0, 1), ('C5', 1, 1), ('D5', 2, 2)),
    38: (('D5', 0, 1), ('F5', 1, 1), ('A5', 2, 2)), 39: (('F5', 0, 4),),
    41: (('A4', 2, 1), ('D5', 3, 1)), 43: (('F5', 0, 3),), 45: (('C5', 2, 2),),
}
for k in range(8):
    BOX[8 + k] = TUNE[k]
    BOX[24 + k] = TUNE[k]
STRING = {16: (('G3', 0, 4),), 17: (('Bb3', 0, 2), ('A3', 2, 2)), 18: (('F3', 0, 4),), 19: (('D3', 0, 4),),
          20: (('D3', 0, 2), ('F3', 2, 2)), 21: (('Bb3', 0, 4),), 22: (('A3', 0, 4),), 23: (('A3', 0, 2), ('C#4', 2, 2)),
          36: (('F3', 0, 4),), 37: (('A3', 0, 4),), 38: (('D4', 0, 4),), 39: (('C4', 0, 2), ('A3', 2, 2))}


def compose():
    b = {n: Bus(n, room) for n, room in (('pad', 0.5), ('box', 0.7), ('harp', 0.55), ('string', 0.5), ('bass', 0.1))}
    for bar in range(BARS):
        t0 = bar * BAR
        name = HARMONY[bar]
        root, notes, four = CHORD[name]
        first = bar == 0 or HARMONY[bar - 1] != name
        # the pad holds every chord for as long as it stands
        if first:
            length = 1
            while bar + length < BARS and HARMONY[bar + length] == name:
                length += 1
            thin = 0.8 if bar >= 44 else 1.0
            for k, n in enumerate(notes):
                b['pad'].add(pad(hz(n), length * BAR), t0, 0.036 * thin * (0.8 if k == 0 else 1.0))
            if bar >= 8:
                rf = hz(root)
                rf = rf * 2 if rf < 60 else rf
                b['bass'].add(deep(rf, length * BAR * 0.96), t0, 0.1 if 16 <= bar < 40 else 0.075)
        # the harp: eighths up and down the chord, softer on the off beats, quieter still when it leaves
        if 8 <= bar < 16 or 24 <= bar < 44:
            level = 0.15 if bar < 40 else 0.15 * (44 - bar) / 5.0
            order = (0, 2, 1, 3, 2, 1, 3, 2)
            for k in range(8):
                f = hz(four[order[k]]) * (2 if k >= 5 else 1)
                b['harp'].add(harp(f), t0 + k * BEAT / 2 + RNG.uniform(-0.006, 0.006), level * (1.0 if k % 2 == 0 else 0.72) * RNG.uniform(0.9, 1.05),
                              -0.45 + 0.9 * (k / 7.0))
        for note, beat, beats in BOX.get(bar, ()):
            f = hz(note)
            at = t0 + beat * BEAT + RNG.uniform(-0.008, 0.008)
            b['box'].add(box(f, max(2.5, beats * BEAT + 2.0)), at, 0.2 if 8 <= bar < 40 else 0.16, 0.12)
            if 24 <= bar < 32:                      # small bells an octave over the tune
                b['box'].add(box(f * 2, 2.0), at + BEAT / 2, 0.04, -0.35)
        for note, beat, beats in STRING.get(bar, ()):
            b['string'].add(string(hz(note), beats * BEAT), t0 + beat * BEAT, 0.09, -0.2)
    return b


def room(send):
    """A wide, dull hall: the send convolved with three seconds of decaying noise."""
    t = clock(3.2)
    out = np.zeros_like(send)
    for ch in (0, 1):
        ir = RNG.normal(0, 1, len(t)) * np.exp(-t / 0.7)
        ir[:int(0.02 * SR)] = 0
        ir = highpass(lowpass(ir, 4200), 250)
        ir /= np.sqrt((ir ** 2).sum())
        out[:, ch] = signal.fftconvolve(send[:, ch], ir)[:len(send)]
    return out


def master(buses):
    dry = sum(bus.buf for bus in buses.values())
    send = sum(bus.buf * bus.room for bus in buses.values())
    mix = highpass(lowpass(dry + room(send) * 0.8, 9000), 35)
    mix[:TAIL] += mix[N:N + TAIL]                    # what rings on after the end lies over the beginning
    mix = mix[:N]
    mix *= 0.5 / np.abs(mix).max()
    return mix


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else OUT
    buses = compose()
    mix = master(buses)
    if len(sys.argv) > 2:
        for bus in buses.values():
            print('%-7s %.1f dB' % (bus.name, 20 * math.log10(math.sqrt((bus.buf ** 2).mean()) + 1e-9)))
        for k in range(0, BARS, 8):
            seg = mix[int(k * BAR * SR):int((k + 8) * BAR * SR)]
            print('bars %2d-%2d  %.1f dB rms, peak %.2f' % (k + 1, k + 8, 20 * math.log10(math.sqrt((seg ** 2).mean())), np.abs(seg).max()))
        print('seam: last sample %.4f, first %.4f' % (mix[-1, 0], mix[0, 0]))
    wav = out[:-4] + '.wav'
    with wave.open(wav, 'wb') as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes((np.clip(mix, -1, 1) * 32767).astype('<i2').tobytes())
    # 32 kHz is enough for a piece that has nothing above 9 kHz, and the game holds all of it in memory (see MenuMusic)
    subprocess.check_call(['ffmpeg', '-y', '-loglevel', 'error', '-i', wav, '-ar', '32000', '-c:a', 'libvorbis', '-q:a', '4', out])
    os.remove(wav)
    print('wrote', out, '%.1f s' % (len(mix) / SR), '%d KB' % (os.path.getsize(out) // 1024))


if __name__ == '__main__':
    main()
