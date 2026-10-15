#!/usr/bin/env python3
"""Procedural PLACEHOLDER sound generator for the Old Republic mod.

Every sound here is synthesised from scratch with numpy (oscillators, filtered
noise, inharmonic partials, simple convolution reverb). Nothing is sampled or
copied from any recording. These are placeholders until a sound designer
replaces them; see docs/AUDIO_BRIEF.md for the target design of each event.

Output:
  src/main/resources/assets/oldrepublic/sounds/<group>/<name>.ogg   (mono, 44.1 kHz, Vorbis q4)
  src/main/resources/assets/oldrepublic/sounds.json
  src/main/lang-parts/sounds.json                                   (English subtitles)
  src/main/java/com/oldrepublic/registry/ModSounds.java             (only with --java)

Usage:
  python3 tools/assets/gen_sounds.py            # regenerate all audio + json
  python3 tools/assets/gen_sounds.py --only saber/swing1 --keep-wav /tmp/wavs
  python3 tools/assets/gen_sounds.py --java     # also rewrite ModSounds.java

Deterministic: every file uses its own RNG seeded from MASTER_SEED and the file
name, so regenerating gives identical audio (encoder permitting).
"""
import argparse
import json
import os
import shutil
import subprocess
import sys
import tempfile
import wave
import zlib
from pathlib import Path

import numpy as np

SR = 44100
MASTER_SEED = 0x0A5E_2026
PEAK = 10 ** (-3 / 20)  # -3 dBFS
ROOT = Path(__file__).resolve().parents[2]
SOUND_DIR = ROOT / "src/main/resources/assets/oldrepublic/sounds"
SOUNDS_JSON = ROOT / "src/main/resources/assets/oldrepublic/sounds.json"
LANG_PART = ROOT / "src/main/lang-parts/sounds.json"
JAVA_OUT = ROOT / "src/main/java/com/oldrepublic/registry/ModSounds.java"
NS = "oldrepublic"


# ----------------------------------------------------------------------------
# DSP helpers
# ----------------------------------------------------------------------------

def ns(d):
    return int(round(d * SR))


def tt(d):
    return np.arange(ns(d)) / SR


def _safe_f(f):
    return np.maximum(f, 1e-3)


def resp_bp(fc, q):
    return lambda f: 1.0 / np.sqrt(1.0 + (q * (_safe_f(f) / fc - fc / _safe_f(f))) ** 2)


def resp_lp(fc, order=2):
    return lambda f: 1.0 / np.sqrt(1.0 + (f / fc) ** (2 * order))


def resp_hp(fc, order=2):
    return lambda f: 1.0 / np.sqrt(1.0 + (fc / _safe_f(f)) ** (2 * order))


def resp_comb(delay, g):
    return lambda f: (1 - g) / (1.0 - g * np.exp(-2j * np.pi * f * delay))


def ffilt(x, resp, loop=False, pad=0.6):
    """Zero-phase FFT filter. loop=True filters circularly (keeps loops seamless)."""
    n = len(x)
    P = n if loop else n + ns(pad)
    X = np.fft.rfft(x, P)
    f = np.fft.rfftfreq(P, 1 / SR)
    return np.fft.irfft(X * resp(f), P)[:n]


def bp(x, fc, q=2.0, loop=False):
    return ffilt(x, resp_bp(fc, q), loop)


def lp(x, fc, order=2, loop=False):
    return ffilt(x, resp_lp(fc, order), loop)


def hp(x, fc, order=2, loop=False):
    return ffilt(x, resp_hp(fc, order), loop)


def comb(x, delay, g, loop=False):
    return ffilt(x, resp_comb(delay, g), loop, pad=1.0)


def sweep(x, fc, kind="bp", q=2.0, nb=16, loop=False):
    """Time-varying filter: a bank of static filters crossfaded along fc(t) in log frequency."""
    fc = np.broadcast_to(np.asarray(fc, float), x.shape)
    lo, hi = fc.min() * 0.97, fc.max() * 1.03
    mk = {"bp": lambda c: resp_bp(c, q), "lp": lambda c: resp_lp(c, 2), "hp": lambda c: resp_hp(c, 2)}[kind]
    centers = np.geomspace(lo, hi, nb)
    bands = np.stack([ffilt(x, mk(c), loop) for c in centers])
    pos = np.interp(np.log(fc), np.log(centers), np.arange(nb))
    i0 = np.clip(np.floor(pos).astype(int), 0, nb - 2)
    fr = pos - i0
    idx = np.arange(len(x))
    return bands[i0, idx] * (1 - fr) + bands[i0 + 1, idx] * fr


def phase(f, n, loop=False):
    f = np.broadcast_to(np.asarray(f, float), (n,)).copy()
    if loop:
        cyc = f.sum() / SR
        f *= max(1, round(cyc)) / cyc
    return 2 * np.pi * np.concatenate([[0.0], np.cumsum(f)[:-1]]) / SR


def saw(ph, fmax, nh=40, roll=1.0):
    k_max = int(max(1, min(nh, 15000 // max(fmax, 1))))
    out = np.zeros_like(ph)
    for k in range(1, k_max + 1):
        out += np.sin(k * ph) / k ** roll
    return out * 0.6


def pulse(ph, fmax, w=0.3, nh=30):
    k_max = int(max(1, min(nh, 15000 // max(fmax, 1))))
    out = np.zeros_like(ph)
    for k in range(1, k_max + 1):
        out += (2 / (k * np.pi)) * np.sin(k * np.pi * w) * np.cos(k * ph)
    return out


def noise(n, rng):
    return rng.standard_normal(n)


def env_ad(t, a, d, t0=0.0):
    """Linear attack a seconds starting at t0, exponential decay with time constant d."""
    tr = t - t0
    e = np.where(tr < 0, 0.0, np.where(tr < a, tr / max(a, 1e-6), np.exp(-(tr - a) / d)))
    return e


def bell(t, tp, w1, w2=None):
    w2 = w1 if w2 is None else w2
    w = np.where(t < tp, w1, w2)
    return np.exp(-0.5 * ((t - tp) / w) ** 2)


def sig(x):
    return 1 / (1 + np.exp(-x))


def crackle(n, rate, rng, loop=False):
    """Sparse random impulses; rate may be an array (impulses/second)."""
    rate = np.broadcast_to(np.asarray(rate, float), (n,))
    hits = rng.random(n) < rate / SR
    x = np.where(hits, rng.uniform(0.3, 1.0, n) * rng.choice([-1, 1], n), 0.0)
    return x


def bell_partials(n, f0, ratios, decays, amps, rng, t0=0.0, attack=0.002, detune=0.0015):
    t = np.arange(n) / SR
    out = np.zeros(n)
    for r, d, a in zip(ratios, decays, amps):
        f = f0 * r * (1 + rng.uniform(-detune, detune))
        p = rng.uniform(0, 2 * np.pi)
        tr = t - t0
        e = np.where(tr < 0, 0, np.minimum(1, tr / attack) * np.exp(-np.maximum(tr, 0) / d))
        out += a * e * np.sin(2 * np.pi * f * np.maximum(tr, 0) + p)
    return out


def reverb(x, rng, rt=1.2, mix=0.3, bright=5000, loop=False, predelay=0.012):
    n_ir = ns(rt)
    t = np.arange(n_ir) / SR
    ir = rng.standard_normal(n_ir) * np.exp(-6.9 * t / rt)
    ir = lp(ir, bright)
    ir[: ns(predelay)] = 0
    ir /= np.sqrt(np.sum(ir ** 2)) + 1e-12
    if loop:
        n = len(x)
        P = n
        irw = np.zeros(n)
        # wrap IR circularly into the loop length
        for i in range(0, n_ir, n):
            seg = ir[i:i + n]
            irw[: len(seg)] += seg
        wet = np.fft.irfft(np.fft.rfft(x) * np.fft.rfft(irw), n)
        return (1 - mix) * x + mix * wet
    P = len(x) + n_ir
    wet = np.fft.irfft(np.fft.rfft(x, P) * np.fft.rfft(ir, P), P)
    dry = np.concatenate([x, np.zeros(n_ir)])
    return (1 - mix) * dry + mix * wet


def place(buf, x, t0):
    i = ns(t0)
    if i >= len(buf):
        return buf
    m = min(len(x), len(buf) - i)
    buf[i:i + m] += x[:m]
    return buf


def place_circular(buf, x, t0):
    n = len(buf)
    i = ns(t0) % n
    idx = (np.arange(len(x)) + i) % n
    np.add.at(buf, idx, x)
    return buf


def norm(x):
    m = np.max(np.abs(x))
    return x / m if m > 0 else x


def sat(x, drive=1.5):
    return np.tanh(drive * x) / np.tanh(drive)


# ----------------------------------------------------------------------------
# Shared building blocks
# ----------------------------------------------------------------------------

def saber_tone(fcurve, rng, loop=False, bright=2400, grit=0.15):
    """Lightsaber-like buzz: two detuned saws beating slowly + pulse + sub, saturated."""
    n = len(fcurve)
    fmax = float(np.max(fcurve))
    a = saw(phase(fcurve, n, loop), fmax * 1.0)
    b = saw(phase(fcurve * 1.0068, n, loop), fmax * 1.01)
    c = pulse(phase(fcurve * 2.003, n, loop), fmax * 2.01, w=0.27) * 0.35
    s = np.sin(phase(fcurve * 0.5, n, loop)) * 0.2
    x = a + 0.85 * b + c + s
    x = sat(0.6 * x, 2.2)
    x = lp(x, bright, loop=loop) + 0.9 * bp(x, 380, 2.5, loop=loop) + 0.3 * bp(x, 1100, 3, loop=loop)
    if grit:
        x += grit * hp(noise(n, rng), 3500, loop=loop) * 0.15
    return x


def whoosh(t, rng, fc, q=1.4, amp=None):
    x = sweep(noise(len(t), rng), fc, "bp", q)
    return x * (1 if amp is None else amp)


def thump(t, f_hi, f_lo, tau, decay, t0=0.0, attack=0.004):
    n = len(t)
    tr = np.maximum(t - t0, 0)
    f = f_lo + (f_hi - f_lo) * np.exp(-tr / tau)
    ph = phase(f, n)
    return np.sin(ph) * env_ad(t, attack, decay, t0)


def crack(t, rng, t0=0.0, dur=0.01, hpf=900):
    x = noise(len(t), rng) * env_ad(t, 0.0005, dur, t0)
    return hp(x, hpf)


def metal_ring(t, rng, f0, n_part=6, decay=0.35, t0=0.0):
    ratios = np.sort(rng.uniform(1.0, 4.2, n_part))
    ratios[0] = 1.0
    decays = decay * rng.uniform(0.4, 1.0, n_part)
    amps = rng.uniform(0.3, 1.0, n_part) / np.arange(1, n_part + 1) ** 0.5
    return bell_partials(len(t), f0, ratios, decays, amps, rng, t0=t0)


CHIME_RATIOS = [1.0, 2.32, 4.25, 6.63, 9.38]


# ----------------------------------------------------------------------------
# Lightsaber
# ----------------------------------------------------------------------------
SABER_F = 92.0


def g_saber_hum(rng):
    L = 4.0
    t = tt(L)
    f = SABER_F * (1 + 0.004 * np.sin(2 * np.pi * 0.5 * t) + 0.0025 * np.sin(2 * np.pi * 1.25 * t + 1.0))
    x = saber_tone(f, rng, loop=True, grit=0.2)
    am = 1 + 0.07 * np.sin(2 * np.pi * 0.75 * t) + 0.04 * np.sin(2 * np.pi * 2.0 * t + 0.4)
    # faint electrical crackle, circular
    cr = hp(crackle(len(t), 6, rng), 4000, loop=True) * 0.25
    return x * am + cr


def g_saber_ignite(rng):
    t = tt(1.1)
    f = SABER_F + (38 - SABER_F) * np.exp(-t / 0.06) + 0.15 * SABER_F * bell(t, 0.18, 0.06, 0.15)
    amp = np.minimum(1, t / 0.1) * (1 - 0.45 * np.clip((t - 0.4) / 0.6, 0, 1))
    tone = saber_tone(f, rng, bright=2800) * amp
    snap = crack(t, rng, 0.0, 0.02, 1500) * 0.9
    sw = whoosh(t, rng, 250 * (14 ** np.clip(t / 0.25, 0, 1)) * np.where(t > 0.25, np.exp(-(t - 0.25) / 0.3), 1) + 200, 1.2,
                env_ad(t, 0.03, 0.12)) * 1.4
    sizzle = hp(crackle(len(t), 900 * np.exp(-t / 0.08), rng), 3000) * 0.6
    x = tone + snap + sw + sizzle
    return reverb(x, rng, 0.6, 0.12)


def g_saber_retract(rng):
    t = tt(0.85)
    f = 30 + (SABER_F - 30) * np.exp(-np.maximum(t - 0.05, 0) / 0.18)
    amp = np.minimum(1, t / 0.01) * np.exp(-np.maximum(t - 0.08, 0) / 0.22)
    tone = saber_tone(f, rng, bright=1800) * amp
    sw = whoosh(t, rng, 2600 * np.exp(-t / 0.15) + 180, 1.3, env_ad(t, 0.01, 0.14)) * 1.8
    fizz = hp(crackle(len(t), 400 * np.exp(-t / 0.12), rng), 2500) * 0.4
    clk = crack(t, rng, 0.38, 0.004, 2000) * 0.25
    return reverb(tone + sw + fizz + clk, rng, 0.5, 0.12)


SWING_VARIANTS = [
    dict(dur=0.55, tp=0.24, f0=92, up=0.16, down=0.11, w1=0.09, w2=0.12, wh=2300),
    dict(dur=0.65, tp=0.32, f0=86, up=0.20, down=0.13, w1=0.13, w2=0.13, wh=1800),
    dict(dur=0.45, tp=0.17, f0=99, up=0.13, down=0.10, w1=0.06, w2=0.10, wh=2800),
    dict(dur=0.78, tp=0.38, f0=90, up=0.22, down=0.16, w1=0.16, w2=0.17, wh=1600),
]


def make_swing(p):
    def g(rng):
        t = tt(p["dur"])
        tp = p["tp"]
        # doppler: higher while the blade approaches, lower after it passes, plus a swell at closest point
        b = bell(t, tp, p["w1"], p["w2"])
        f = p["f0"] * (1 + p["up"] - (p["up"] + p["down"]) * sig((t - tp) / 0.035)) * (1 + 0.1 * b)
        amp = 0.18 + 0.82 * b
        tone = saber_tone(f, rng, bright=1200) * amp
        bright = hp(saber_tone(f * 1.5, rng, bright=4000, grit=0), 900) * b ** 2 * 0.5
        fc = 350 + (p["wh"] - 350) * b
        wh = whoosh(t, rng, fc, 1.6, b ** 1.5) * 1.1
        x = (tone + bright + wh) * np.clip((p["dur"] - t) / 0.12, 0, 1) ** 1.5  # let the blade 'leave' smoothly
        return reverb(x, rng, 0.35, 0.08)
    return g


def make_clash(seed_f, hum_f):
    def g(rng):
        t = tt(0.9)
        cr = crack(t, rng, 0, 0.012, 700) * 1.4
        ring = metal_ring(t, rng, seed_f, 7, 0.35) * 0.7 + metal_ring(t, rng, seed_f * 2.37, 5, 0.18) * 0.35
        sizz = bp(hp(crackle(len(t), 2500 * np.exp(-t / 0.18), rng), 2500), 5200, 0.7) * 1.3
        f = hum_f * (1 + 0.7 * np.exp(-t / 0.05))
        hum = sat(saber_tone(f, rng, bright=3500) * 1.6, 2.5) * env_ad(t, 0.002, 0.22)
        burst = lp(noise(len(t), rng), 3000) * env_ad(t, 0.002, 0.05) * 0.8
        x = cr + ring + sizz + hum + burst
        return reverb(x, rng, 0.7, 0.15)
    return g


def make_deflect(f_start, f_end, ring_f):
    def g(rng):
        t = tt(0.5)
        f = f_end + (f_start - f_end) * np.exp(-t / 0.04)
        zap = saw(phase(f, len(t)), f_start, nh=6) * env_ad(t, 0.001, 0.09)
        ring = metal_ring(t, rng, ring_f, 5, 0.16) * 0.6
        sizz = hp(crackle(len(t), 1800 * np.exp(-t / 0.07), rng), 3000) * 0.9
        hum = saber_tone(SABER_F * 1.3 * (1 + 0.5 * np.exp(-t / 0.04)), rng, bright=3000) * env_ad(t, 0.002, 0.12)
        x = zap * 0.8 + ring + sizz + hum + crack(t, rng, 0, 0.006, 1500)
        return reverb(x, rng, 0.5, 0.12)
    return g


def g_saber_parry(rng):
    t = tt(1.4)
    ring = bell_partials(len(t), 1568, [1, 1.51, 2.42, 3.17, 4.6, 5.9], [0.9, 0.6, 0.45, 0.3, 0.2, 0.12],
                         [1, 0.6, 0.5, 0.35, 0.25, 0.2], rng)
    shimmer = (np.sin(2 * np.pi * 3136 * t) + np.sin(2 * np.pi * 3142 * t)) * env_ad(t, 0.003, 0.5) * 0.25
    cr = crack(t, rng, 0, 0.008, 1800) * 1.1
    hum = saber_tone(SABER_F * 1.4 * (1 + 0.4 * np.exp(-t / 0.03)), rng, bright=4000) * env_ad(t, 0.002, 0.1) * 0.8
    return reverb(ring + shimmer + cr + hum, rng, 1.2, 0.2)


def g_saber_guard_break(rng):
    t = tt(1.4)
    boom = thump(t, 75, 32, 0.12, 0.4) * 0.7
    cr = crack(t, rng, 0, 0.02, 500) * 1.6
    ring = metal_ring(t, rng, 720, 7, 0.5) * 0.6
    f = 40 + 110 * np.exp(-t / 0.35)
    stutter = 0.55 + 0.45 * np.sign(np.sin(2 * np.pi * (14 * t + 20 * t ** 2)))
    stutter = lp(stutter, 200)
    hum = sat(saber_tone(f, rng, bright=2500) * 2.0, 3.0) * stutter * env_ad(t, 0.003, 0.45)
    sizz = hp(crackle(len(t), 1500 * np.exp(-t / 0.4), rng), 2200) * 1.0
    x = boom + cr + ring + hum + sizz
    return reverb(x, rng, 1.0, 0.2)


def g_saber_hit(rng):
    t = tt(0.75)
    sear = bp(noise(len(t), rng), 3800, 0.9) * env_ad(t, 0.004, 0.18) * 0.9
    sizz = hp(crackle(len(t), 3000 * np.exp(-t / 0.22), rng), 3000) * 1.1
    hum = saber_tone(SABER_F * 1.2 * (1 + 0.35 * np.exp(-t / 0.05)), rng, bright=2600) * env_ad(t, 0.002, 0.15)
    th = thump(t, 110, 60, 0.04, 0.08) * 0.8
    return reverb(sear + sizz + hum + th, rng, 0.4, 0.1)


# ----------------------------------------------------------------------------
# Blaster
# ----------------------------------------------------------------------------

def g_blaster_charge(rng):
    t = tt(0.6)
    u = t / 0.6
    f = 480 * (2600 / 480) ** (u ** 1.6)
    vib_rate = 8 + 30 * u
    vib = 1 + (0.008 + 0.025 * u) * np.sin(2 * np.pi * np.cumsum(vib_rate) / SR)
    ff = f * vib
    a = saw(phase(ff, len(t)), 3000, nh=4, roll=1.5)
    b = np.sin(phase(ff * 1.5, len(t))) * 0.35
    amp = 0.35 + 0.65 * u ** 0.7
    hiss = sweep(noise(len(t), rng), 2000 * (3.5 ** u), "bp", 3) * 0.35 * u
    tick = hp(crackle(len(t), 40 + 400 * u ** 2, rng), 4000) * 0.4
    x = (a + b) * amp + hiss + tick
    x[-ns(0.012):] *= np.linspace(1, 0, ns(0.012))
    return x


def make_blaster_fire(f_start, f_end, tau, comb_hz):
    def g(rng):
        t = tt(0.55)
        f1 = f_end + (f_start - f_end) * np.exp(-t / tau)
        c1 = np.sin(phase(f1, len(t))) + 0.3 * np.sin(2 * phase(f1, len(t)))
        f2 = f_end * 1.2 + (f_start * 1.33 - f_end * 1.2) * np.exp(-np.maximum(t - 0.004, 0) / (tau * 0.8))
        c2 = np.sin(phase(f2, len(t))) * 0.6
        chirps = (c1 + c2) * env_ad(t, 0.001, 0.12)
        metal = comb(chirps, 1 / comb_hz, 0.82) * 0.8 + bp(chirps, comb_hz * 3.1, 8) * 1.2
        trans = crack(t, rng, 0, 0.006, 1200) * 0.8
        th = thump(t, 120, 50, 0.025, 0.05) * 0.6
        x = chirps + metal + trans + th
        return reverb(x, rng, 0.6, 0.18)
    return g


def g_blaster_flyby(rng):
    t = tt(0.8)
    tp = 0.34
    f0 = 520
    f = f0 * (1 + 0.14 * (1 - 2 * sig((t - tp) / 0.045)))
    amp = 1 / (1 + ((t - tp) / 0.075) ** 2)
    tone = lp(saw(phase(f, len(t)), 700, nh=12), 2500) * amp
    buzz = np.sin(phase(f * 2.01, len(t))) * amp * 0.3
    wh = whoosh(t, rng, 600 + 3000 * amp, 1.5, amp) * 0.8
    sizz = hp(crackle(len(t), 600 * amp, rng), 4000) * 0.4
    return tone + buzz + wh + sizz


def g_blaster_impact(rng):
    t = tt(0.6)
    cr = crack(t, rng, 0, 0.01, 900) * 1.4
    f = 200 + 1800 * np.exp(-t / 0.02)
    zap = np.sin(phase(f, len(t))) * env_ad(t, 0.001, 0.05) * 0.6
    sizz = hp(crackle(len(t), 2500 * np.exp(-t / 0.12), rng), 3000) * 1.0
    th = thump(t, 140, 55, 0.03, 0.09) * 0.5
    dust = lp(noise(len(t), rng), 2000) * env_ad(t, 0.003, 0.08) * 0.5
    return reverb(cr + zap + sizz + th + dust, rng, 0.6, 0.15)


# ----------------------------------------------------------------------------
# Force
# ----------------------------------------------------------------------------

def g_push_charge(rng):
    L = 3.0
    t = tt(L)
    n = len(t)
    base = noise(n, rng)
    x = np.zeros(n)
    # endlessly rising air rush (three staggered upward sweeps, Shepard-style, periodic over L)
    for k in range(3):
        ph = ((t / 1.5) + k / 3) % 1.0
        fc = 110 * 2 ** (3.2 * ph)
        w = np.sin(np.pi * ph) ** 2
        x += sweep(base if k == 0 else noise(n, rng), fc, "bp", 2.2, nb=14, loop=True) * w
    rumble = lp(noise(n, rng), 90, loop=True) * 2.5 * (1 + 0.2 * np.sin(2 * np.pi * t / L * 2))
    sub = np.sin(phase(np.full(n, 41.0), n, loop=True)) * 0.25 * (1 + 0.3 * np.sin(2 * np.pi * t * 3 / L))
    return x + rumble + sub


def make_push_release(p):
    def g(rng):
        t = tt(1.1)
        sub = sat(thump(t, 66 * p, 30 * p, 0.08, 0.25, attack=0.006) * 0.8, 1.8)
        fc = np.where(t < 0.08, 150 + 2400 * p * (t / 0.08) ** 1.5, 300 + 2250 * p * np.exp(-(t - 0.08) / 0.18))
        press = sweep(noise(len(t), rng), fc, "lp", nb=14) * env_ad(t, 0.015, 0.28) * 2.4
        fc2 = 300 * p + 1600 * p * bell(t, 0.12, 0.05, 0.18)
        wh = whoosh(t, rng, fc2, 1.4, bell(t, 0.12, 0.05, 0.22)) * 1.1
        echo = sweep(noise(len(t), rng), np.maximum(fc * 0.6, 120), "lp", nb=10) * env_ad(t, 0.02, 0.2, 0.13) * 0.6
        x = sub + press + wh + echo
        return reverb(x, rng, 1.2, 0.22, bright=3000)
    return g


def g_push_impact(rng):
    t = tt(0.5)
    th = thump(t, 95, 45, 0.04, 0.12) * 0.6
    body = bp(noise(len(t), rng), 420, 1.5) * env_ad(t, 0.002, 0.05) * 1.6
    dust = lp(noise(len(t), rng), 1500) * env_ad(t, 0.01, 0.15) * 1.0
    return reverb(th + body + dust, rng, 0.5, 0.15, bright=3000)


def g_push_slam(rng):
    t = tt(1.3)
    boom = sat(thump(t, 58, 27, 0.15, 0.5) * 0.8, 1.6)
    crunch = bp(noise(len(t), rng), 900, 0.8) * env_ad(t, 0.002, 0.07) * 2.0
    deb = bp(crackle(len(t), 260 * np.exp(-t / 0.35), rng), 1800, 0.9) * 4.0
    peb = np.zeros(len(t))
    for _ in range(14):
        t0 = rng.uniform(0.15, 1.0)
        peb += metal_ring(t, rng, rng.uniform(1800, 4200), 3, 0.025, t0) * rng.uniform(0.1, 0.35)
    dust = lp(noise(len(t), rng), 900) * env_ad(t, 0.05, 0.4) * 0.6
    return reverb(boom + crunch + deb + peb + dust, rng, 1.4, 0.25, bright=3500)


def g_force_fail(rng):
    t = tt(0.6)
    f = 95 + 45 * np.exp(-t / 0.12)
    tone = pulse(phase(f, len(t)), 140, w=0.5, nh=20)
    tone = comb(bp(tone, 310, 3.5), 1 / 220, 0.6) * env_ad(t, 0.005, 0.15)
    fizz = lp(hp(noise(len(t), rng), 1800), 3200)
    fizz = fizz * env_ad(t, 0.01, 0.12) * 0.5 + lp(crackle(len(t), 200 * np.exp(-t / 0.15), rng), 2500) * 0.8
    pff = lp(noise(len(t), rng), 500) * env_ad(t, 0.004, 0.04) * 0.5
    return reverb(tone * 1.5 + fizz + pff, rng, 0.3, 0.08, bright=2000)


# ----------------------------------------------------------------------------
# Awakening / meditation
# ----------------------------------------------------------------------------
VOWELS = [(300, 870, 2240), (400, 2000, 2550), (530, 1840, 2480), (660, 1720, 2410), (730, 1090, 2440),
          (570, 840, 2410), (440, 1020, 2240), (490, 1350, 1690)]


def whisper_voice(dur, rng, scale=1.0, t_start=0.0, t_end=None):
    t = tt(dur)
    n = len(t)
    t_end = dur if t_end is None else t_end
    # syllable timeline
    times, f1s, f2s, f3s = [], [], [], []
    env = np.zeros(n)
    sib = np.zeros(n)
    s = t_start + rng.uniform(0, 0.3)
    while s < t_end - 0.3:
        d = rng.uniform(0.16, 0.42)
        v = VOWELS[rng.integers(len(VOWELS))]
        times.append(s + d / 2)
        f1s.append(v[0] * scale)
        f2s.append(v[1] * scale)
        f3s.append(v[2] * scale)
        env += bell(t, s + d / 2, d / 3.2) * rng.uniform(0.5, 1.0)
        if rng.random() < 0.35:
            sib += bell(t, s + rng.choice([-0.5, 0.5]) * d * 0.7, 0.03) * rng.uniform(0.3, 0.7)
        s += d + rng.uniform(0.03, 0.22) + (rng.uniform(0.3, 0.7) if rng.random() < 0.18 else 0)
    if not times:
        return np.zeros(n)
    tr1 = np.interp(t, times, f1s)
    tr2 = np.interp(t, times, f2s)
    tr3 = np.interp(t, times, f3s)
    src = hp(noise(n, rng), 200)
    v = sweep(src, tr1, "bp", 6, nb=10) + 0.7 * sweep(src, tr2, "bp", 7, nb=12) + 0.3 * sweep(src, tr3, "bp", 8, nb=8)
    s_ = hp(noise(n, rng), 5000) * 0.4
    return v * env + s_ * sib


def g_awakening_whispers(rng):
    dur = 6.0
    t = tt(dur)
    x = np.zeros(len(t))
    for i, sc in enumerate([1.0, 0.86, 1.12, 0.94, 1.05]):
        v = whisper_voice(dur, rng, sc, t_start=0.2 + 0.35 * i, t_end=dur - 0.4 * (i % 2))
        x += norm(v) * rng.uniform(0.55, 1.0)
    swell = np.clip(t / 1.2, 0, 1) * np.clip((dur - t) / 1.0, 0, 1)
    x = x * swell
    return reverb(x, rng, 2.6, 0.5, bright=4500)[: ns(dur + 1.5)]


def g_awakening_drone(rng):
    L = 6.0
    t = tt(L)
    n = len(t)
    x = np.zeros(n)
    for f, a in [(55, 1.0), (55.5, 0.7), (82.5, 0.5), (110.33, 0.35), (164.83, 0.12)]:
        x += a * np.sin(phase(np.full(n, f), n, loop=True))
    sw = lp(saw(phase(np.full(n, 55.0), n, loop=True), 55, nh=20), 600, loop=True) * 0.8
    amp = 1 + 0.18 * np.sin(2 * np.pi * t / L) + 0.08 * np.sin(2 * np.pi * 3 * t / L + 1)
    air = lp(noise(n, rng), 220, loop=True) * 3.0 * (1 + 0.3 * np.sin(2 * np.pi * 2 * t / L))
    x = (x + sw) * amp + air
    return reverb(x, rng, 3.0, 0.35, loop=True, bright=1500)


def g_awakening_burst(rng):
    t = tt(1.9)
    fc = np.where(t < 0.12, 100 + 1100 * (t / 0.12), 200 + 1000 * np.exp(-(t - 0.12) / 0.35))
    bloom = sweep(noise(len(t), rng), fc, "lp", nb=12) * env_ad(t, 0.08, 0.45) * 2.2
    sub = thump(t, 50, 34, 0.2, 0.55, attack=0.03) * 0.5
    air = hp(noise(len(t), rng), 4000) * env_ad(t, 0.12, 0.45) * 0.25
    ring = bell_partials(len(t), 220, [1, 2.76, 5.4], [0.9, 0.5, 0.3], [0.2, 0.1, 0.05], rng, attack=0.08)
    return reverb(bloom + sub + air + ring, rng, 2.0, 0.35, bright=3000)


def g_meditation_loop(rng):
    L = 4.0
    t = tt(L)
    n = len(t)
    pad = np.zeros(n)
    for f, a in [(110, 0.6), (165, 0.45), (247.5, 0.3), (330, 0.22), (440.25, 0.08)]:
        for det in (0.0, 0.5):
            pad += a * np.sin(phase(np.full(n, f + det), n, loop=True))
    pad *= 0.8 + 0.2 * np.sin(2 * np.pi * t / L - np.pi / 2)
    # one slow breath per loop: inhale (brighter) then exhale (darker)
    br_env = np.clip(np.sin(2 * np.pi * t / L), 0, None) ** 1.5 + 0.8 * np.clip(-np.sin(2 * np.pi * t / L), 0, None) ** 1.2
    fc = 900 + 400 * np.sin(2 * np.pi * t / L)
    breath = sweep(noise(n, rng), fc, "bp", 0.9, nb=10, loop=True)
    breath = lp(breath, 2400, loop=True) * br_env * 0.6
    return reverb(pad * 0.5 + breath, rng, 2.5, 0.35, loop=True, bright=3000)


def g_meditation_complete(rng):
    t = tt(2.3)
    a = bell_partials(len(t), 659.25, [1, 2.0, 2.76, 4.07], [1.2, 0.7, 0.4, 0.25], [1, 0.3, 0.25, 0.1], rng, attack=0.006)
    b = bell_partials(len(t), 987.77, [1, 2.0, 2.76, 4.07], [1.1, 0.6, 0.35, 0.2], [0.8, 0.25, 0.2, 0.08], rng, t0=0.13, attack=0.006)
    air = hp(noise(len(t), rng), 5000) * env_ad(t, 0.05, 0.3) * 0.05
    return reverb(a + b + air, rng, 2.2, 0.35, bright=6000)


def g_meditation_break(rng):
    t = tt(0.7)
    fc = 1150 * np.exp(-t / 0.4) + 450
    ex = sweep(noise(len(t), rng), fc, "bp", 1.4, nb=10) * env_ad(t, 0.03, 0.2)
    pf = lp(noise(len(t), rng), 400) * env_ad(t, 0.003, 0.03) * 0.6
    return reverb(ex + pf, rng, 0.6, 0.15)


# ----------------------------------------------------------------------------
# Crystals, cave
# ----------------------------------------------------------------------------

def g_crystal_calling(rng):
    t = tt(1.7)
    lead = sweep(noise(len(t), rng), 3000 + 3000 * np.clip(t / 0.15, 0, 1), "bp", 4, nb=6) * bell(t, 0.12, 0.05, 0.02) * 0.2
    ping = bell_partials(len(t), 880, [1, 1.003, 2.0, 3.01, 4.2], [1.1, 1.1, 0.6, 0.35, 0.2], [1, 0.8, 0.25, 0.12, 0.06], rng,
                         t0=0.14, attack=0.015)
    return reverb(lead + ping, rng, 2.2, 0.45, bright=7000)


def g_crystal_resonance(rng):
    L = 4.0
    t = tt(L)
    n = len(t)
    x = np.zeros(n)
    for r, a in [(1, 1.0), (1.5, 0.5), (2.76, 0.35), (4.07, 0.2), (5.4, 0.12)]:
        f = 196 * r
        for det, aa in ((0.0, 1.0), (0.5 + 0.25 * (r > 2), 0.7)):
            x += a * aa * np.sin(phase(np.full(n, f + det), n, loop=True) + rng.uniform(0, 6.28))
    x *= 1 + 0.15 * np.sin(2 * np.pi * t / L * 2)
    shimmer = bp(noise(n, rng), 6000, 3, loop=True) * 0.15 * (1 + np.sin(2 * np.pi * t * 3 / L))
    return reverb(x * 0.4 + shimmer, rng, 1.8, 0.3, loop=True, bright=6000)


def make_chime(f0, decay):
    def g(rng):
        t = tt(decay + 0.9)
        ratios = [r * (1 + rng.uniform(-0.02, 0.02)) for r in CHIME_RATIOS]
        x = bell_partials(len(t), f0, ratios, [decay, decay * 0.6, decay * 0.35, decay * 0.2, decay * 0.12],
                          [1, 0.45, 0.3, 0.15, 0.08], rng, attack=0.003)
        x += crack(t, rng, 0, 0.002, 6000) * 0.08
        return reverb(x, rng, 2.0, 0.4, bright=7000)
    return g


def g_crystal_unstable(rng):
    t = tt(1.2)
    u = t / 1.2
    f = 300 * 4 ** (u ** 1.3)
    trem = 0.6 + 0.4 * np.sin(2 * np.pi * np.cumsum(6 + 26 * u ** 1.5) / SR)
    tone = (np.sin(phase(f, len(t))) + 0.4 * np.sin(phase(f * 2.76, len(t)))) * trem * (0.2 + 0.8 * u)
    crk = hp(crackle(len(t), 20 + 500 * u ** 2, rng), 3000) * 1.2
    glass = np.zeros(len(t))
    for _ in range(18):
        t0 = 1.2 * rng.random() ** 0.6
        glass += metal_ring(t, rng, rng.uniform(2500, 6000), 3, 0.05, t0) * 0.2 * (t0 / 1.2)
    hiss = sweep(noise(len(t), rng), 1500 + 4500 * u, "bp", 2) * u ** 2 * 0.4
    x = tone + crk + glass + hiss
    x[-ns(0.01):] *= np.linspace(1, 0, ns(0.01))
    return x


def g_crystal_discharge(rng):
    t = tt(1.1)
    cr = crack(t, rng, 0, 0.015, 600) * 1.6
    f = 180 + 3000 * np.exp(-t / 0.03)
    zap = saw(phase(f, len(t)), 3200, nh=5) * env_ad(t, 0.001, 0.06)
    shat = np.zeros(len(t))
    for _ in range(26):
        t0 = 0.02 + rng.exponential(0.12)
        shat += metal_ring(t, rng, rng.uniform(2000, 7000), 4, 0.08, t0) * rng.uniform(0.1, 0.4)
    boom = thump(t, 70, 35, 0.06, 0.25) * 0.45
    tail = hp(crackle(len(t), 800 * np.exp(-t / 0.3), rng), 2500) * 0.7
    return reverb(cr + zap + shat + boom + tail, rng, 1.5, 0.25, bright=6000)


def g_crystal_form(rng):
    t = tt(2.6)
    x = np.zeros(len(t))
    for i in range(32):
        t0 = 1.6 * (i / 32) ** 0.85 + rng.uniform(-0.03, 0.03)
        f = 600 * 4 ** (t0 / 1.6) * rng.uniform(0.9, 1.1)
        x += bell_partials(len(t), f, CHIME_RATIOS[:3], [0.25, 0.12, 0.06], [1, 0.3, 0.15], rng, t0=max(t0, 0)) * (0.25 + 0.4 * t0 / 1.6)
    grow = sum(np.sin(2 * np.pi * 196 * r * t) * a for r, a in [(1, 1), (1.5, 0.5), (2.76, 0.3)])
    grow *= np.clip(t / 1.8, 0, 1) ** 2 * np.exp(-np.maximum(t - 1.8, 0) / 0.5) * 0.5
    crunch = bp(crackle(len(t), 120 * np.clip(t / 1.6, 0, 1), rng), 3000, 1.0) * np.where(t < 1.8, 1, 0) * 1.2
    final = bell_partials(len(t), 1046.5, CHIME_RATIOS, [1.0, 0.6, 0.35, 0.2, 0.12], [1, 0.45, 0.3, 0.15, 0.08], rng, t0=1.8)
    return reverb(x + grow + crunch + final * 0.9, rng, 2.2, 0.38, bright=7000)


def g_ambient_cave(rng):
    L = 6.0
    t = tt(L)
    n = len(t)
    fc = 260 + 140 * np.sin(2 * np.pi * t / L) + 60 * np.sin(2 * np.pi * 3 * t / L + 0.7)
    wind = sweep(noise(n, rng), fc, "lp", nb=10, loop=True) * (1 + 0.25 * np.sin(2 * np.pi * 2 * t / L + 2))
    room = lp(noise(n, rng), 70, loop=True) * 1.5
    drips = np.zeros(n)
    for t0, f0 in [(0.7, 1300), (2.35, 1750), (3.1, 1100), (4.8, 1500), (5.25, 2100)]:
        d = tt(0.12)
        ff = f0 * (1 + 0.8 * np.clip(d / 0.015, 0, 1))
        drop = np.sin(phase(ff, len(d))) * env_ad(d, 0.001, 0.025) * rng.uniform(0.4, 1.0)
        place_circular(drips, drop, t0)
    drips = reverb(drips, rng, 2.5, 0.6, loop=True, bright=5000) * 0.6
    return wind * 0.9 + room + drips


# ----------------------------------------------------------------------------
# Trial
# ----------------------------------------------------------------------------

def g_trial_begin(rng):
    swell_len = 1.6
    t = tt(swell_len)
    src = bell_partials(len(t), 147, [1, 2.76, 5.4, 8.9], [0.8, 0.5, 0.3, 0.2], [1, 0.5, 0.3, 0.2], rng)
    src += lp(noise(len(t), rng), 2500) * env_ad(t, 0.002, 0.2) * 0.6
    wet = reverb(src, rng, 1.6, 0.85, bright=4000)[: len(t)]
    rev = wet[::-1] * np.clip(t / 0.3, 0, 1)
    tail = tt(0.9)
    hit = thump(tail, 62, 36, 0.1, 0.35) * 1.4 + lp(saw(phase(np.full(len(tail), 73.4), len(tail)), 73, nh=15), 500) * env_ad(tail, 0.003, 0.3) * 0.6
    hit = reverb(hit, rng, 1.2, 0.3, bright=2500)
    out = np.concatenate([norm(rev) * 0.9, np.zeros(len(hit))])
    out[len(rev):len(rev) + len(hit)] += norm(hit)
    return out


def g_trial_echo_form(rng):
    t = tt(1.9)
    fc = 300 + 2200 * np.exp(-t / 0.6) * (0.6 + 0.4 * np.sin(2 * np.pi * 3.2 * t))
    smoke = sweep(noise(len(t), rng), fc, "lp", nb=12) * env_ad(t, 0.35, 0.6) * 1.2
    f = 48 + 10 * np.exp(-t / 0.5)
    low = lp(saw(phase(f, len(t)), 60, nh=25) + saw(phase(f * 1.013, len(t)), 60, nh=25), 380) * env_ad(t, 0.4, 0.7) * 0.6
    breath = sweep(hp(noise(len(t), rng), 800), 1200 + 600 * np.sin(2 * np.pi * 1.3 * t), "bp", 2, nb=8) * env_ad(t, 0.5, 0.5) * 0.4
    return reverb(smoke + low + breath, rng, 1.8, 0.35, bright=3000)


def g_trial_echo_lunge(rng):
    t = tt(0.7)
    b = bell(t, 0.26, 0.09, 0.12)
    wh = whoosh(t, rng, 250 + 1300 * b, 1.3, b) * 1.4
    pre = sweep(noise(len(t), rng), 300 + 900 * np.clip(t / 0.22, 0, 1), "lp", nb=8) * (np.clip(t / 0.22, 0, 1) ** 2) * (t < 0.24) * 0.6
    f = 70 * (1 + 0.15 * b)
    growl = lp(saw(phase(f, len(t)), 90, nh=20), 600) * (0.6 + 0.4 * np.sin(2 * np.pi * 31 * t)) * b * 0.9
    return reverb(wh + pre + growl, rng, 0.8, 0.2, bright=3000)


def g_trial_complete(rng):
    dur = 3.4
    t = tt(dur)
    rise = 2 ** (1 / 12 * 2 * np.clip(t / 2.2, 0, 1))  # up a whole tone over 2.2 s
    voices = np.zeros(len(t))
    for f0 in [220.0, 277.18, 329.63, 440.0, 554.37]:
        for det in (-0.004, 0.004):
            vib = 1 + 0.006 * np.sin(2 * np.pi * (5.0 + rng.uniform(-0.4, 0.4)) * t + rng.uniform(0, 6))
            f = f0 * (1 + det) * rise * vib
            voices += saw(phase(f, len(t)), f0 * 1.2, nh=30, roll=1.2)
    ah = bp(voices, 800, 4) + 0.6 * bp(voices, 1150, 5) + 0.25 * bp(voices, 2900, 6)
    breath = bp(noise(len(t), rng), 1000, 1.5) * 0.15
    amp = np.clip(t / 2.0, 0, 1) ** 1.5 * np.clip((dur - t) / 0.9, 0, 1)
    shimmer = hp(noise(len(t), rng), 6000) * 0.05 * amp
    return reverb((ah + breath) * amp + shimmer, rng, 2.8, 0.4, bright=6000)


# ----------------------------------------------------------------------------
# Workbench, journal, poacher
# ----------------------------------------------------------------------------

def click(t, rng, t0, bright=1.0):
    ex = np.zeros(len(t))
    i = ns(t0)
    if i < len(t):
        ex[i] = 1.0
        ex[min(i + 1, len(t) - 1)] = -0.6
    x = sum(bp(ex, f * bright * rng.uniform(0.95, 1.05), 14) for f in (2200, 3700, 5100))
    x = x + bp(ex, 320, 3) * 0.8
    return x


def g_workbench_slot(rng):
    t = tt(0.35)
    x = click(t, rng, 0.005) + click(t, rng, 0.04, 0.85) * 0.7
    x += metal_ring(t, rng, 2900, 4, 0.06, 0.04) * 0.12
    return reverb(x, rng, 0.4, 0.12)


def g_workbench_assemble(rng):
    t = tt(3.0)
    x = np.zeros(len(t))
    for t0, b in [(0.02, 1.0), (0.2, 0.9), (0.34, 1.1), (0.64, 0.8), (0.8, 1.05), (1.08, 0.95)]:
        x += click(t, rng, t0, b) * rng.uniform(0.6, 1.0)
    for k in range(9):  # ratchet
        x += click(t, rng, 0.42 + k * 0.022, 1.25) * 0.35
    hum = sum(np.sin(2 * np.pi * 196 * r * t + rng.uniform(0, 6)) * a for r, a in [(1, 1), (1.5, 0.4), (2.76, 0.25), (4.07, 0.12)])
    hum *= np.clip((t - 0.9) / 0.9, 0, 1) ** 2 * np.exp(-np.maximum(t - 1.9, 0) / 0.15) * 0.5
    ign = g_saber_ignite(rng)
    ign = ign * np.exp(-np.maximum(np.arange(len(ign)) / SR - 0.5, 0) / 0.25)
    x += hum
    x = place(x, ign * 0.9, 1.95)
    return reverb(x, rng, 0.8, 0.15)


def rustle(t, rng, t0, t1, rate=60, fc=2800):
    n = len(t)
    g = np.zeros(n)
    for _ in range(int(rate * (t1 - t0))):
        c = rng.uniform(t0, t1)
        g += bell(t, c, rng.uniform(0.004, 0.02)) * rng.uniform(0.2, 1.0)
    src = noise(n, rng)
    return (bp(src, fc, 0.7) + 0.15 * hp(src, 5000)) * g


def g_journal_open(rng):
    t = tt(0.75)
    cover = bp(noise(len(t), rng), 180, 1.2) * env_ad(t, 0.003, 0.04) * 1.2 + thump(t, 110, 70, 0.02, 0.04) * 0.4
    x = cover + rustle(t, rng, 0.03, 0.5, 70, fc=2000) * 0.7
    return reverb(x, rng, 0.4, 0.1, bright=4000)


def g_journal_entry(rng):
    t = tt(1.7)
    fc = 1500 + 3500 * bell(t, 0.15, 0.06, 0.1)
    flip = sweep(noise(len(t), rng), fc, "bp", 0.9, nb=8) * bell(t, 0.15, 0.05, 0.08) * 0.8
    x = flip + rustle(t, rng, 0.02, 0.3, 50) * 0.4
    tone = bell_partials(len(t), 659.25, [1, 2.0, 3.0], [0.8, 0.4, 0.2], [1, 0.2, 0.08], rng, t0=0.28, attack=0.01)
    tone += bell_partials(len(t), 987.77, [1, 2.0, 3.0], [0.9, 0.4, 0.2], [0.8, 0.15, 0.06], rng, t0=0.42, attack=0.01)
    return reverb(x + tone * 0.35, rng, 1.4, 0.25, bright=6000)


def gear_rattle(t, rng, t0, t1, count):
    x = np.zeros(len(t))
    for _ in range(count):
        c = rng.uniform(t0, t1)
        x += metal_ring(t, rng, rng.uniform(1800, 5200), 3, rng.uniform(0.02, 0.06), c) * rng.uniform(0.2, 0.6)
    return x


def grunt(t, rng, t0, dur, f_hi, f_lo, f1=520, f2=1050):
    n = len(t)
    tr = np.clip((t - t0) / dur, 0, 1)
    f = f_hi + (f_lo - f_hi) * tr
    voiced = lp(saw(phase(f, n), f_hi, nh=30, roll=1.3), 1800)
    breath = noise(n, rng) * 0.9
    src = voiced * 0.45 + breath
    v = bp(src, f1, 4) + 0.6 * bp(src, f2, 5) + 0.15 * bp(src, 2500, 4)
    e = env_ad(t, 0.02, dur * 0.4, t0) * (t < t0 + dur * 1.6)
    return lp(v * e, 3500)


def make_poacher_hurt(f_hi, f1):
    def g(rng):
        t = tt(0.5)
        x = gear_rattle(t, rng, 0.0, 0.14, 9) + grunt(t, rng, 0.01, 0.18, f_hi, f_hi * 0.8, f1) * 1.5
        x += thump(t, 120, 70, 0.02, 0.04) * 0.3
        return reverb(x, rng, 0.4, 0.1, bright=4000)
    return g


def g_poacher_death(rng):
    t = tt(1.4)
    x = grunt(t, rng, 0.0, 0.55, 125, 78, 560, 980) * 1.0
    x += lp(noise(len(t), rng), 1200) * env_ad(t, 0.1, 0.25, 0.45) * 0.25  # exhale
    x += gear_rattle(t, rng, 0.25, 0.85, 16)
    x += thump(t, 90, 40, 0.05, 0.12, t0=0.78) * 0.6 + lp(noise(len(t), rng), 900) * env_ad(t, 0.003, 0.12, 0.78) * 0.6
    x += metal_ring(t, rng, 2400, 5, 0.12, 1.02) * 0.35
    return reverb(x, rng, 0.6, 0.12, bright=4000)


# ----------------------------------------------------------------------------
# Catalogue: event id -> files (generator), subtitle, json extras
# ----------------------------------------------------------------------------
LOOPS = {"saber/hum", "force/push_charge", "meditation/loop", "crystal/resonance", "ambient/cave", "awakening/drone"}


def E(eid, subtitle, files, **extras):
    return dict(id=eid, subtitle=subtitle, files=files, extras=extras)


EVENTS = [
    E("saber.ignite", "Lightsaber ignites", {"saber/ignite": g_saber_ignite}),
    E("saber.retract", "Lightsaber retracts", {"saber/retract": g_saber_retract}),
    E("saber.hum", "Lightsaber hums", {"saber/hum": g_saber_hum}, volume=0.6),
    E("saber.swing", "Lightsaber swings", {f"saber/swing{i + 1}": make_swing(p) for i, p in enumerate(SWING_VARIANTS)}),
    E("saber.clash", "Lightsabers clash", {"saber/clash1": make_clash(1240, 118), "saber/clash2": make_clash(1085, 104),
                                           "saber/clash3": make_clash(1420, 131)}),
    E("saber.deflect", "Lightsaber deflects bolt", {"saber/deflect1": make_deflect(2300, 600, 2800),
                                                    "saber/deflect2": make_deflect(2000, 520, 3300),
                                                    "saber/deflect3": make_deflect(2650, 700, 2450)}),
    E("saber.parry", "Lightsaber parries", {"saber/parry": g_saber_parry}),
    E("saber.guard_break", "Guard breaks", {"saber/guard_break": g_saber_guard_break}),
    E("saber.hit", "Lightsaber sears", {"saber/hit": g_saber_hit}),
    E("blaster.charge", "Blaster charges", {"blaster/charge": g_blaster_charge}),
    E("blaster.fire", "Blaster fires", {"blaster/fire1": make_blaster_fire(3200, 180, 0.035, 740),
                                        "blaster/fire2": make_blaster_fire(2750, 150, 0.045, 610)}),
    E("blaster.bolt_flyby", "Blaster bolt whizzes", {"blaster/bolt_flyby": g_blaster_flyby}),
    E("blaster.impact", "Blaster bolt hits", {"blaster/impact": g_blaster_impact}),
    E("force.push.charge", "Force gathers", {"force/push_charge": g_push_charge}, volume=0.8),
    E("force.push.release", "Force push", {f"force/push_release{i + 1}": make_push_release(p) for i, p in enumerate([0.94, 1.0, 1.06])}),
    E("force.push.impact", "Force push strikes", {"force/push_impact": g_push_impact}),
    E("force.push.slam", "Something slams into a wall", {"force/push_slam": g_push_slam}),
    E("force.fail", "Force power fizzles", {"force/fail": g_force_fail}),
    E("awakening.whispers", "Voices whisper", {"awakening/whispers": g_awakening_whispers}),
    E("awakening.drone", "Low drone", {"awakening/drone": g_awakening_drone}, volume=0.8),
    E("awakening.burst", "Force bursts outward", {"awakening/burst": g_awakening_burst}),
    E("meditation.loop", "Calm breathing", {"meditation/loop": g_meditation_loop}, volume=0.6),
    E("meditation.complete", "Meditation completes", {"meditation/complete": g_meditation_complete}),
    E("meditation.break", "Meditation breaks", {"meditation/break": g_meditation_break}),
    E("crystal.calling", "Something calls", {"crystal/calling": g_crystal_calling}),
    E("crystal.resonance", "Crystals resonate", {"crystal/resonance": g_crystal_resonance}, volume=0.6, attenuation_distance=16),
    E("crystal.chime", "Crystal chimes", {"crystal/chime1": make_chime(1046.5, 1.1), "crystal/chime2": make_chime(1318.5, 0.9),
                                          "crystal/chime3": make_chime(1174.7, 1.3), "crystal/chime4": make_chime(1568.0, 0.7)},
      attenuation_distance=16),
    E("crystal.unstable_charge", "Crystal crackles with energy", {"crystal/unstable_charge": g_crystal_unstable}),
    E("crystal.discharge", "Crystal discharges", {"crystal/discharge": g_crystal_discharge}),
    E("crystal.form", "Crystal forms", {"crystal/form": g_crystal_form}),
    E("ambient.cave", "Cave wind and drips", {"ambient/cave": g_ambient_cave}, volume=0.7),
    E("trial.begin", "Trial begins", {"trial/begin": g_trial_begin}),
    E("trial.echo_form", "Shadow Echo forms", {"trial/echo_form": g_trial_echo_form}),
    E("trial.echo_lunge", "Shadow Echo lunges", {"trial/echo_lunge": g_trial_echo_lunge}),
    E("trial.complete", "Trial complete", {"trial/complete": g_trial_complete}),
    E("workbench.slot", "Component clicks into place", {"workbench/slot": g_workbench_slot}),
    E("workbench.assemble", "Lightsaber assembled", {"workbench/assemble": g_workbench_assemble}),
    E("journal.open", "Journal opens", {"journal/open": g_journal_open}),
    E("journal.entry", "New journal entry", {"journal/entry": g_journal_entry}),
    E("poacher.hurt", "Poacher hurts", {"poacher/hurt1": make_poacher_hurt(135, 520), "poacher/hurt2": make_poacher_hurt(118, 470)}),
    E("poacher.death", "Poacher dies", {"poacher/death": g_poacher_death}),
]


# ----------------------------------------------------------------------------
# Rendering
# ----------------------------------------------------------------------------

def finish(x, loop):
    x = np.asarray(x, float)
    x = hp(x, 25, loop=loop)
    if not loop:
        # trim trailing near-silence, then fade
        thr = np.max(np.abs(x)) * 10 ** (-60 / 20)
        nz = np.nonzero(np.abs(x) > thr)[0]
        if len(nz):
            x = x[: min(len(x), nz[-1] + ns(0.02))]
        fi, fo = ns(0.003), ns(0.02)
        x[:fi] *= np.linspace(0, 1, fi)
        x[-fo:] *= np.linspace(1, 0, fo) ** 2
    x = x - (np.mean(x) if loop else 0)
    return x / (np.max(np.abs(x)) + 1e-12) * PEAK


def write_wav(path, x):
    pcm = np.clip(np.round(x * 32767), -32768, 32767).astype("<i2")
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())


def render(name, gen, tmp):
    rng = np.random.default_rng((zlib.crc32(name.encode()) ^ MASTER_SEED) & 0xFFFFFFFF)
    loop = name in LOOPS
    x = finish(gen(rng), loop)
    wav = Path(tmp) / (name.replace("/", "__") + ".wav")
    write_wav(wav, x)
    out = SOUND_DIR / f"{name}.ogg"
    out.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", str(wav), "-ac", "1", "-ar", str(SR), "-c:a", "libvorbis", "-q:a", "4",
                    "-map_metadata", "-1", "-fflags", "+bitexact", "-flags:a", "+bitexact", str(out)], check=True)
    return len(x) / SR, loop, wav


def sounds_json(durations):
    data = {}
    for ev in EVENTS:
        entries = []
        for name in ev["files"]:
            dur, loop = durations.get(name, (0, name in LOOPS))
            e = {"name": f"{NS}:{name}"}
            if loop and dur > 4.0:
                e["stream"] = True
            else:
                e["stream"] = False
            for k in ("volume", "attenuation_distance"):
                if k in ev["extras"]:
                    e[k] = ev["extras"][k]
            entries.append(e)
        data[ev["id"]] = {"subtitle": f"subtitles.{NS}.{ev['id']}", "sounds": entries}
    return data


def java_source():
    lines = [
        "package com.oldrepublic.registry;",
        "",
        "import com.oldrepublic.OldRepublicMod;",
        "import net.minecraft.core.Registry;",
        "import net.minecraft.core.registries.BuiltInRegistries;",
        "import net.minecraft.resources.Identifier;",
        "import net.minecraft.sounds.SoundEvent;",
        "",
        "/**",
        " * Every mod sound event. Ids match assets/oldrepublic/sounds.json; placeholder audio is generated by",
        " * tools/assets/gen_sounds.py (see docs/AUDIO_BRIEF.md). Generated with --java; keep in sync with the generator.",
        " */",
        "public final class ModSounds {",
    ]
    group = None
    for ev in EVENTS:
        g = ev["id"].split(".")[0]
        if g != group:
            lines.append(f"    // {g}")
            group = g
        const = ev["id"].replace(".", "_").upper()
        lines.append(f'    public static final SoundEvent {const} = register("{ev["id"]}");')
    lines += [
        "",
        "    private static SoundEvent register(String id) {",
        "        Identifier rl = OldRepublicMod.id(id);",
        "        return Registry.register(BuiltInRegistries.SOUND_EVENT, rl, SoundEvent.createVariableRangeEvent(rl));",
        "    }",
        "",
        "    public static void init() {}",
        "",
        "    private ModSounds() {}",
        "}",
        "",
    ]
    return "\n".join(lines)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--only", nargs="*", help="only render these file names (e.g. saber/hum)")
    ap.add_argument("--keep-wav", help="copy intermediate WAVs to this directory")
    ap.add_argument("--java", action="store_true", help="also (re)write ModSounds.java")
    ap.add_argument("--no-audio", action="store_true", help="only write json/lang/java")
    args = ap.parse_args()

    if not shutil.which("ffmpeg"):
        sys.exit("ffmpeg not found on PATH")

    durations = {}
    if not args.no_audio:
        with tempfile.TemporaryDirectory() as tmp:
            for ev in EVENTS:
                for name, gen in ev["files"].items():
                    if args.only and name not in args.only:
                        continue
                    dur, loop, wav = render(name, gen, tmp)
                    durations[name] = (dur, loop)
                    print(f"  {name:32s} {dur:5.2f}s{'  loop' if loop else ''}")
                    if args.keep_wav:
                        os.makedirs(args.keep_wav, exist_ok=True)
                        shutil.copy(wav, args.keep_wav)
    # loops have fixed lengths; fill in for json when not rendered this run
    fixed = {"saber/hum": 4.0, "force/push_charge": 3.0, "meditation/loop": 4.0, "crystal/resonance": 4.0,
             "ambient/cave": 6.0, "awakening/drone": 6.0}
    for k, v in fixed.items():
        durations.setdefault(k, (v, True))

    SOUNDS_JSON.write_text(json.dumps(sounds_json(durations), indent=2) + "\n")
    LANG_PART.parent.mkdir(parents=True, exist_ok=True)
    LANG_PART.write_text(json.dumps({f"subtitles.{NS}.{ev['id']}": ev["subtitle"] for ev in EVENTS}, indent=2) + "\n")
    if args.java:
        JAVA_OUT.parent.mkdir(parents=True, exist_ok=True)
        JAVA_OUT.write_text(java_source())
    print(f"wrote {SOUNDS_JSON.relative_to(ROOT)}, {LANG_PART.relative_to(ROOT)}" + (f", {JAVA_OUT.relative_to(ROOT)}" if args.java else ""))


if __name__ == "__main__":
    main()
