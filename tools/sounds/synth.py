#!/usr/bin/env python3
"""Synthesizes the obelisk's own sounds and writes them as ogg into assets/kronwerke/sounds.

  hum       an eight second drone that loops, the stone's voice; pitch and volume are set in game
  riser     the four seconds of the intake, noise and a rising tone that end on the burst
  tear      the moment the sky opens, a low boom with a long shimmer
  fanfare   the roll call, three brass like chords over a bell
  whisper   the stone noticing a gaze, two seconds of breath and a bending tone

Everything is additive synthesis with numpy, no samples, so the same script makes the
same files. ffmpeg turns the wav into ogg.
"""
import os
import subprocess

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "..", "src", "main", "resources", "assets", "kronwerke", "sounds")
RATE = 44100


def t(seconds):
    return np.arange(int(seconds * RATE)) / RATE


def env(n, attack, release, hold=1.0):
    """A linear attack, a hold and an exponential release, over n samples."""
    a = int(attack * RATE)
    r = int(release * RATE)
    e = np.ones(n) * hold
    e[:a] = np.linspace(0, hold, a)
    tail = np.exp(-np.linspace(0, 6, r))
    e[n - r:] *= tail[: n - (n - r)] if r <= n else tail[:n]
    return e


def saw(freq, tt, harmonics=12):
    out = np.zeros_like(tt)
    for k in range(1, harmonics + 1):
        out += np.sin(2 * np.pi * freq * k * tt) / k
    return out


def lowpass(x, cutoff):
    """A one pole low pass, enough to take the edge off."""
    rc = 1.0 / (2 * np.pi * cutoff)
    dt = 1.0 / RATE
    a = dt / (rc + dt)
    y = np.zeros_like(x)
    acc = 0.0
    for i in range(len(x)):
        acc += a * (x[i] - acc)
        y[i] = acc
    return y


def normalize(x, peak=0.85):
    m = np.max(np.abs(x))
    return x / m * peak if m > 0 else x


def hum():
    tt = t(8.0)
    base = 55.0
    out = np.zeros_like(tt)
    for mult, amp in ((1, 1.0), (2, 0.5), (3, 0.3), (4.01, 0.12), (6, 0.08)):
        lfo = 1 + 0.02 * np.sin(2 * np.pi * (0.11 * mult) * tt)
        out += amp * np.sin(2 * np.pi * base * mult * lfo * tt)
    # a slow breath in the volume, one cycle per loop so it joins seamlessly
    out *= 0.75 + 0.25 * np.sin(2 * np.pi * tt / 8.0 - np.pi / 2)
    # a faint shimmer high up
    out += 0.05 * np.sin(2 * np.pi * 880 * tt) * (0.5 + 0.5 * np.sin(2 * np.pi * 0.25 * tt))
    # a little wind
    rng = np.random.default_rng(3)
    wind = lowpass(rng.normal(0, 1, len(tt)), 400) * 0.15
    out += wind
    return normalize(out, 0.6)


def riser():
    tt = t(4.0)
    rng = np.random.default_rng(5)
    noise = rng.normal(0, 1, len(tt))
    sweep = lowpass(noise, 200) * np.linspace(0.2, 1.0, len(tt)) ** 2
    # a tone rising two octaves
    freq = 110 * 2 ** (np.linspace(0, 2, len(tt)))
    phase = np.cumsum(freq) / RATE
    tone = np.sin(2 * np.pi * phase) * np.linspace(0.1, 0.9, len(tt)) ** 1.5
    tone += 0.4 * np.sin(2 * np.pi * phase * 1.5) * np.linspace(0, 0.6, len(tt)) ** 2
    out = sweep * 1.2 + tone
    out *= env(len(tt), 0.3, 0.15)
    return normalize(out, 0.8)


def tear():
    tt = t(5.0)
    boom = np.sin(2 * np.pi * 42 * tt * np.exp(-tt * 0.8)) * np.exp(-tt * 1.4)
    boom += 0.5 * np.sin(2 * np.pi * 84 * tt) * np.exp(-tt * 2.5)
    rng = np.random.default_rng(7)
    crack = lowpass(rng.normal(0, 1, len(tt)), 3000) * np.exp(-tt * 6) * 0.6
    shimmer = np.zeros_like(tt)
    for f, a in ((1320, 0.3), (1760, 0.25), (2217, 0.2), (2637, 0.15)):
        shimmer += a * np.sin(2 * np.pi * f * tt) * np.exp(-tt * 0.9) * (0.5 + 0.5 * np.sin(2 * np.pi * 3.1 * tt))
    out = boom * 1.4 + crack + shimmer * 0.4
    return normalize(out, 0.9)


def fanfare():
    tt = t(6.0)
    out = np.zeros_like(tt)
    # three chords in D: D major, G major over D, A major, then D again with the fifth on top
    chords = [((146.83, 185.0, 220.0), 0.0, 1.2), ((196.0, 246.94, 293.66), 1.2, 1.2), ((220.0, 277.18, 329.63), 2.4, 1.2), ((146.83, 220.0, 293.66, 369.99), 3.6, 2.4)]
    for notes, start, length in chords:
        s, e = int(start * RATE), int((start + length) * RATE)
        seg = tt[s:e] - start
        voice = np.zeros_like(seg)
        for f in notes:
            voice += saw(f, seg, 10) * 0.25 + saw(f * 2, seg, 6) * 0.08
        voice = lowpass(voice, 1800)
        voice *= env(len(seg), 0.06, min(0.6, length * 0.5))
        out[s:e] += voice
    # a bell on the last chord
    s = int(3.6 * RATE)
    seg = tt[s:] - 3.6
    bell = np.zeros_like(seg)
    for f, a in ((587.33, 0.5), (1174.66, 0.25), (1468.3, 0.15), (2349.3, 0.08)):
        bell += a * np.sin(2 * np.pi * f * seg) * np.exp(-seg * (1.2 + f / 1500))
    out[s:] += bell * 0.6
    return normalize(out, 0.9)


def whisper():
    tt = t(2.2)
    rng = np.random.default_rng(11)
    breath = lowpass(rng.normal(0, 1, len(tt)), 900) * (0.5 + 0.5 * np.sin(2 * np.pi * 1.3 * tt - np.pi / 2)) * 0.5
    # a tone that bends up a fifth and back, soft and hollow
    freq = 330 * 2 ** (0.58 * np.sin(np.pi * tt / 2.2) ** 2)
    phase = np.cumsum(freq) / RATE
    tone = (np.sin(2 * np.pi * phase) + 0.3 * np.sin(2 * np.pi * phase * 2.01)) * 0.35
    out = (breath + tone) * env(len(tt), 0.4, 0.9)
    return normalize(out, 0.55)


def write(name, data):
    os.makedirs(OUT, exist_ok=True)
    wav = os.path.join(OUT, name + ".wav")
    ogg = os.path.join(OUT, name + ".ogg")
    pcm = (np.clip(data, -1, 1) * 32767).astype(np.int16)
    import wave
    with wave.open(wav, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(pcm.tobytes())
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "libvorbis", "-q:a", "4", ogg], check=True)
    os.remove(wav)


if __name__ == "__main__":
    write("hum", hum())
    write("riser", riser())
    write("tear", tear())
    write("fanfare", fanfare())
    write("whisper", whisper())
    print("ok", os.path.abspath(OUT))
