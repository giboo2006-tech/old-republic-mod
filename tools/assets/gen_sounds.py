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

Milestone 2 and later: the shared event lists tools/assets/sounds/events_m2.json,
events_m3.json, events_m4.json and events_m5.json name every new sound event (id, owning feature,
subtitle, loop flag, json extras). Each feature owns a recipe module
tools/assets/sounds/<feature>.py that defines

  FILES = {"<event id>": {"<group>/<file>": generator, ...}, ...}

for every event of that feature (one or more variant files per event; a
generator takes a numpy Generator and returns mono float samples). Recipe
modules can `import gen_sounds as g` for the DSP helpers and building blocks.

Milestone 3: an events_m3.json entry may also set "stream" (written into
sounds.json as "stream": true; without it, loops longer than 4 s stream) and
"variants". Until the owning module has a FILES entry for an M3 id, the id is
rendered from a labelled placeholder recipe (m3_placeholder below) into
<group>/<rest of the id with dots as underscores>[1..variants], e.g.
boss.collector.hurl_lift -> boss/collector_hurl_lift. A worker's real recipe
should keep those file names, so sounds.json does not change.

Milestone 4: events_m4.json works the same way (same fields, same placeholder
file names), with its own placeholder recipe (m4_placeholder below; the M3 ids
keep m3_placeholder, so their audio does not change). M4 entries are appended
after the M3 ones in sounds.json, the subtitles and ModSounds.

Milestone 5: events_m5.json again works the same way, with m5_placeholder (the
M4 recipe with word lists for engines, ship weapons, alarms, chants and the
bosses' calls; M3 and M4 ids keep their own placeholders). M5 entries are
appended after the M4 ones.

Usage:
  python3 tools/assets/gen_sounds.py            # regenerate all audio + json
  python3 tools/assets/gen_sounds.py --only saber/swing1 --keep-wav /tmp/wavs
  python3 tools/assets/gen_sounds.py --feature force   # only one recipe module's files (M2 to M4)
  python3 tools/assets/gen_sounds.py --m2       # only the M2 files
  python3 tools/assets/gen_sounds.py --m3       # only the M3 files (placeholders where no recipe exists yet)
  python3 tools/assets/gen_sounds.py --m3 --feature boss   # only W5's M3 files
  python3 tools/assets/gen_sounds.py --m4       # only the M4 files (placeholders where no recipe exists yet)
  python3 tools/assets/gen_sounds.py --m4 --feature gear   # only W4's M4 files
  python3 tools/assets/gen_sounds.py --m5       # only the M5 files (placeholders where no recipe exists yet)
  python3 tools/assets/gen_sounds.py --m5 --feature flight   # only W1's M5 files
  python3 tools/assets/gen_sounds.py --placeholders   # list the M3 to M5 ids still on placeholder audio, write nothing
  python3 tools/assets/gen_sounds.py --placeholders --m4   # only the M4 ones
  python3 tools/assets/gen_sounds.py --java     # also rewrite ModSounds.java

Deterministic: every file uses its own RNG seeded from MASTER_SEED and the file
name, so regenerating gives identical audio (encoder permitting).
"""
import argparse
import importlib.util
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
RECIPE_DIR = Path(__file__).resolve().parent / "sounds"
EVENTS_M2 = RECIPE_DIR / "events_m2.json"
EVENTS_M3 = RECIPE_DIR / "events_m3.json"
EVENTS_M4 = RECIPE_DIR / "events_m4.json"
EVENTS_M5 = RECIPE_DIR / "events_m5.json"
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
    """The lightsaber tone on fcurve (Hz per sample): the film-style hum of film_hum (Lightsaber, below) with the
    M1 character, brightness bright and a hiss of grit, at the level of the original saber tone (RMS 0.75)."""
    return film_hum(fcurve, rng, loop=loop, **dict(HUM, bright=bright, air=0.004 + 0.04 * grit)) * 0.75


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
#
# The film-style recipe (the redesign of October 2026; docs/AUDIO_BRIEF.md, "Lightsaber"). Everything is
# synthesised here from scratch: no film recording is sampled, traced or reproduced. What it follows is the
# publicly described method behind the film sabers, not their sound: the hum is two layers, the warm hum of
# an idling motor and the hard buzz a picture tube induces in a microphone lead, a hair apart in pitch so
# that they beat slowly; a swing is that same hum carried past the listener (a Doppler bend, a swell at the
# closest point and the moving comb filter of a reflection, the "phasing"); an ignition is a snap and a hiss
# with the hum rushing in on a quick upward glide; a retraction is the hum diving and fading under a hiss; a
# clash is a crack, a spitting sizzle and a short harsh ring over both hums. The saber recipes in
# sounds/saber.py and sounds/saber_m5.py are built from these blocks.
# ----------------------------------------------------------------------------
SABER_F = 92.0
# The M1 hum's character (film_hum keywords); the M1 swings, clashes and the rest use the same hum.
HUM = dict(buzz=0.25, beat=0.25, throb=0.5, throb_depth=0.3, growl=24.0, growl_depth=0.05, bright=2400.0)


def _rms(x):
    return float(np.sqrt(np.mean(np.square(x)))) + 1e-12


def film_hum(f, rng, loop=False, motor=1.0, buzz=0.25, beat=0.25, throb=0.5, throb_depth=0.3, growl=24.0,
             growl_depth=0.05, bright=2400.0, body=((290.0, 1.3, 2.2), (1150.0, 2.2, 1.7)), tilt=0.8,
             buzz_band=(2000.0, 1.0, 2.5), buzz_lp=4000.0, spread=0.6, sub=0.0, low_cut=0.0, drift=0.0015,
             air=0.01):
    """The lightsaber hum on the fundamental f (Hz, one value per sample), at about unit RMS.

    The motor layer (level motor): a warm harmonic series (partials falling as k**-tilt, low-passed at
    bright and lifted by the housing resonances in body, each (centre, Q, gain)), a twin of every partial
    throb Hz off (a slow, even throb of depth throb_depth), a growl Hz flutter of growl_depth (the
    mechanism's ripple) and a light asymmetric saturation. The buzz layer (level buzz): a spike train beat
    Hz below the motor carrying every harmonic, each spike spread over about a millisecond (spread) so it
    buzzes without spiking the level, through a 450 Hz high-pass, the buzz_band resonance (centre, Q, gain)
    and a buzz_lp low-pass; it beats against the motor, slowly at the bottom and faster up the series. sub
    adds a sine an octave down, low_cut a high-pass for a thin blade, drift slow independent pitch drifts of
    the two layers, air a faint hiss; a soft saturation rounds the peaks. With loop=True every rate is a whole
    number of cycles over the length and every filter is circular, so the result loops seamlessly.
    """
    f = np.asarray(f, float)
    n = len(f)
    t = np.arange(n) / SR
    span = n / SR

    def whole(rate):
        return max(1, round(rate * span)) / span if loop else rate

    def wobble(cycles, depth):
        return depth * np.sin(2 * np.pi * whole(cycles / 4.0) * t + rng.uniform(0, 2 * np.pi))

    fm = f * (1 + wobble(1, drift) + wobble(3, drift * 0.4))
    fb = (f - beat) * (1 + wobble(2, drift * 0.6))
    ph, phb = phase(fm, n, loop), phase(fb, n, loop)
    twin = 2 * np.pi * whole(throb) * t
    mot = np.zeros(n)
    for k in range(1, int(max(1, min(48, 8000 // max(float(np.max(fm)), 1)))) + 1):
        fk = k * fm
        a = k ** -tilt * resp_lp(bright, 2)(fk)
        for fc, q, gain in body:
            a = a * (1 + (gain - 1) * resp_bp(fc, q)(fk))
        mot += a * np.sin(k * ph + rng.uniform(0, 2 * np.pi))
        mot += a * throb_depth * (1.0 if k <= 6 else 0.5) * np.sin(k * ph + twin + rng.uniform(0, 2 * np.pi))
    mot *= 1 + growl_depth * np.sin(2 * np.pi * whole(growl) * t + rng.uniform(0, 2 * np.pi))
    mot = sat(mot / _rms(mot) * 0.55 + 0.08, 1.4)
    mot -= np.mean(mot)
    kb = int(max(1, min(140, 11000 // max(float(np.max(fb)), 1))))
    bz = np.zeros(n)
    for k in range(1, kb + 1):
        fk = k * fb
        a = resp_hp(450, 2)(fk) * (1 + (buzz_band[2] - 1) * resp_bp(buzz_band[0], buzz_band[1])(fk)) * resp_lp(buzz_lp, 2)(fk)
        bz += a * np.cos(k * phb + np.pi * spread * k * k / kb)
    x = motor * mot / _rms(mot) + buzz * bz / _rms(bz)
    if sub:
        x += sub * np.sin(phase(fm * 0.5, n, loop))
    if low_cut:
        x = hp(x, low_cut, loop=loop)
    if air:
        x += air * 2.0 * hp(noise(n, rng), 2500, loop=loop)
    # A little speaker-and-tape compression: rounds the peaks (about 3 dB less crest) without changing the balance.
    x = sat(x / _rms(x) * 0.45, 1.3)
    return x / _rms(x)


def vdelay(x, d, loop=False):
    """x heard d seconds late (a number or one per sample), with cubic interpolation; silence before the start,
    or wrapped around when loop is set."""
    n = len(x)
    pos = np.arange(n) - np.broadcast_to(np.asarray(d, float), (n,)) * SR
    i = np.floor(pos).astype(int)
    fr = pos - i
    if loop:
        def at(k):
            return x[(i + k) % n]
    else:
        xp = np.concatenate([np.zeros(2), x, np.zeros(3)])

        def at(k):
            return xp[np.clip(i + k + 2, 0, len(xp) - 1)]
    p0, p1, p2, p3 = at(-1), at(0), at(1), at(2)
    return p1 + 0.5 * fr * (p2 - p0 + fr * (2 * p0 - 5 * p1 + 4 * p2 - p3 + fr * (3 * (p1 - p2) + p3 - p0)))


def pass_curves(t, tp, w1, w2):
    """A pass at tp: (proximity, bearing). Proximity is the closest distance over the distance (1 at tp, falling
    as 1/r); bearing runs from -1 (far, coming) through 0 to +1 (far, going). w1 and w2 are the closest
    distance over the speed before and after the pass, in seconds: small is close and quick."""
    s = (t - tp) / np.where(t < tp, w1, w2)
    r = np.sqrt(1 + s * s)
    return 1 / r, s / r


def doppler(t, tp, w1, w2, up, down):
    """The pitch ratio of a pass at tp: up to about 1 + up as the blade comes, through 1 at the closest point, to
    about 1 - down as it goes, easing back to 1 as the swing slows at either end."""
    _, bearing = pass_curves(t, tp, w1, w2)
    return 1 - np.where(bearing < 0, up, down) * bearing * bell(t, tp, 2.5 * w1, 2.5 * w2)


def pass_by(x, prox, comb=(0.3, 4.0), refl=0.8, sheen=0.6, warm=0.4, floor=0.1, power=1.6, loop=False):
    """x carried past the listener, prox (0 far to 1 closest, one per sample) saying how near: two reflections
    whose delays grow from comb[0] to comb[1] ms as it nears comb-filter it (the phasing), it brightens
    (sheen) and fills out (warm) up close, and its level swells from floor to 1 as prox**power."""
    d1 = (comb[0] + (comb[1] - comb[0]) * prox ** 1.5) * 1e-3
    d2 = (comb[0] * 1.8 + (comb[1] * 0.55 - comb[0] * 1.8) * prox ** 2.2) * 1e-3
    y = x + refl * vdelay(x, d1, loop) - 0.4 * refl * vdelay(x, d2, loop)
    y = y + sheen * prox ** 2 * hp(y, 1800, loop=loop) + warm * prox ** 2 * lp(y, 220, loop=loop)
    return y * (floor + (1 - floor) * prox ** power)


def snap_hiss(t, rng, t0=0.0, snap=1.0, hiss=1.0, sparks=1.0, fc=4200.0, tau=0.06):
    """An ignition's snap and hiss from t0: a sharp click with a small knock, a bright burst of hiss (a band at
    fc decaying with tau) and a spray of sparks."""
    n = len(t)
    click = hp(noise(n, rng) * env_ad(t, 0.0003, 0.0015, t0), 900) * 0.6 + thump(t, 170, 85, 0.008, 0.03, t0) * 0.3
    hs = bp(noise(n, rng), fc, 0.8) * env_ad(t, 0.003, tau, t0)
    sp = hp(crackle(n, 2600 * np.exp(-np.maximum(t - t0, 0) / 0.05) * (t >= t0), rng), 3000)
    return snap * click + hiss * 0.9 * hs + sparks * 0.7 * sp


def sizzle(t, rng, rate, t0=0.0, tau=0.15, lo=2500.0, hi=9000.0):
    """A sizzle from t0: sparks at rate a second and a flickering fizz, both from lo to hi Hz and decaying with tau."""
    n = len(t)
    e = np.where(t >= t0, np.exp(-np.maximum(t - t0, 0) / tau), 0.0)
    flicker = lp(noise(n, rng), 180)
    fizz = noise(n, rng) * e * (0.3 + 0.7 * np.abs(flicker) / (np.std(flicker) + 1e-9))
    return lp(hp(crackle(n, rate * e, rng) + 0.12 * fizz, lo), hi)


def spits(t, rng, t0, span, count, fc=3500.0):
    """count spitting bursts (3 to 15 ms of noise, each in its own band around fc) from t0 over span seconds,
    bunched toward the start."""
    out = np.zeros(len(t))
    src = noise(len(t), rng)
    for _ in range(count):
        c = t0 + span * rng.random() ** 1.8
        w = rng.uniform(0.003, 0.015)
        out += bp(src * np.exp(-0.5 * ((t - c) / (w / 2)) ** 2), fc * rng.uniform(0.6, 1.6), 1.2) * rng.uniform(0.3, 1.0)
    return out


def arc_fry(t, rng, rate=100.0, fc=2600.0):
    """An arc's frying buzz: noise let through in short gates about rate times a second, through a band at fc."""
    n = len(t)
    jitter = lp(noise(n, rng), 20)
    gate = np.clip(np.cos(phase(rate * (1 + 0.04 * jitter / (np.std(jitter) + 1e-9)), n)), 0, 1) ** 6
    return bp(noise(n, rng) * gate, fc, 0.9)


def saber_ignite(rng, f0, hum, dur=1.1, start=0.7, rise=0.035, over=0.05, t_over=0.1, settle=0.09, out=0.07,
                 snap=1.0, hiss=1.0, sparks=1.0, hiss_fc=4200.0, hold=0.3, tail=0.85, room=(0.5, 0.1)):
    """An ignition: snap_hiss, and the hum (film_hum keywords hum) rushing in from start x f0 on a quick glide that
    overshoots by over at t_over and settles, swelling and combing as the blade shoots out past the ear at out
    seconds, holding near full until hold seconds (the blade is out and the hum loop has started) and easing
    to tail of its level, then fading by dur while the loop takes over. room is the reverb (time, mix), or None
    for a dry result to mix into something larger."""
    t = tt(dur)
    f = f0 * (1 + (start - 1) * np.exp(-t / rise) + over * bell(t, t_over, rise, settle))
    prox, _ = pass_curves(t, out, out * 0.6, 0.18)
    tone = pass_by(film_hum(f, rng, **hum), prox, comb=(0.3, 3.0), refl=0.6, sheen=0.2, warm=0.15, floor=0.75, power=1.0)
    amp = (1 - np.exp(-t / 0.02)) * (tail + (1 - tail) * np.clip((hold - t) / hold, 0, 1))
    amp *= np.clip((dur - t) / max(dur - hold - 0.05, 0.05), 0, 1) ** 1.5
    x = tone * amp + snap_hiss(t, rng, 0.0, snap, hiss, sparks, hiss_fc)
    return reverb(x, rng, *room) if room else x


def saber_retract(rng, f0, hum, dur=0.8, end=0.5, hold=0.03, tau=0.14, decay=0.18, hiss=1.0, hiss_fc=3200.0,
                  click=0.25, t_click=0.42, room=(0.5, 0.1)):
    """A retraction: the hum (film_hum keywords hum) holds for hold seconds, then dives toward end x f0 (time
    constant tau) and fades (decay), combing away from the ear, under a falling hiss (the "shwoom"), with the
    emitter's small click at t_click. room is the reverb (time, mix), or None for a dry result."""
    t = tt(dur)
    f = f0 * (end + (1 - end) * np.exp(-np.maximum(t - hold, 0) / tau))
    prox, _ = pass_curves(t, 0.0, 0.05, 0.12)
    tone = pass_by(film_hum(f, rng, **hum), prox, comb=(0.3, 3.0), refl=0.7, floor=0.35, power=0.8)
    amp = np.minimum(1, t / 0.004) * np.exp(-np.maximum(t - hold - 0.02, 0) / decay)
    fc = hiss_fc * np.exp(-t / 0.12) + 500
    sh = sweep(noise(len(t), rng), fc, "bp", 1.3) * bell(t, 0.05, 0.015, 0.14) * 0.5
    x = tone * amp * 0.6 + hiss * sh + crack(t, rng, t_click, 0.003, 2000) * click
    return reverb(x, rng, *room) if room else x


def g_saber_hum(rng):
    t = tt(4.0)
    return film_hum(np.full(len(t), SABER_F), rng, loop=True, **HUM)


def g_saber_ignite(rng):
    return saber_ignite(rng, SABER_F, HUM)


def g_saber_retract(rng):
    return saber_retract(rng, SABER_F, HUM)


SWING_VARIANTS = [
    dict(dur=0.55, tp=0.24, f0=92, up=0.16, down=0.11, w1=0.09, w2=0.12, wh=2300),
    dict(dur=0.65, tp=0.32, f0=86, up=0.20, down=0.13, w1=0.13, w2=0.13, wh=1800),
    dict(dur=0.45, tp=0.17, f0=99, up=0.13, down=0.10, w1=0.06, w2=0.10, wh=2800),
    dict(dur=0.78, tp=0.38, f0=90, up=0.22, down=0.16, w1=0.16, w2=0.17, wh=1600),
]


def make_swing(p):
    """A swing: the hum carried past the listener (pass_by), closest at tp, its pitch bent by doppler (up, down,
    w1, w2); f0 its pitch, wh its brightness (1400 dark to 3000 bright). Optional keys: hum (film_hum keywords
    over HUM), comb and refl (pass_by)."""
    def g(rng):
        t = tt(p["dur"])
        prox, _ = pass_curves(t, p["tp"], p["w1"], p["w2"])
        f = p["f0"] * doppler(t, p["tp"], p["w1"], p["w2"], p["up"], p["down"])
        hum = dict(HUM, bright=1500 + 0.45 * p["wh"])
        hum.update(p.get("hum", {}))
        x = pass_by(film_hum(f, rng, **hum), prox, comb=p.get("comb", (0.25, 2.8 + 4000 / p["wh"])), refl=p.get("refl", 0.85),
                    floor=0.06, power=2.0)
        x = x * np.clip((p["dur"] - t) / 0.12, 0, 1) ** 1.5 * np.clip(t / 0.015, 0, 1)
        return reverb(x, rng, 0.35, 0.08)
    return g


def make_clash(seed_f, hum_f):
    """Blade on blade: the contact crack, a spitting sizzle with an arc's fry, a short harsh ring on seed_f and both
    hums (hum_f and a little above) jolted and overdriven."""
    def g(rng):
        t = tt(0.85)
        crk = crack(t, rng, 0.0, 0.004, 700) * 1.8 + thump(t, 160, 70, 0.01, 0.04) * 0.5
        burst = sizzle(t, rng, 5000, 0.0, 0.13) + spits(t, rng, 0.004, 0.32, 14)
        fry = arc_fry(t, rng, 100.0) * env_ad(t, 0.002, 0.12) * 0.7
        ring = sat(metal_ring(t, rng, seed_f, 6, 0.12) * 2.2, 2.0) * 0.45 + metal_ring(t, rng, seed_f * 2.37, 4, 0.06) * 0.25
        jolt = 1 - 0.16 * np.exp(-t / 0.05) + 0.05 * bell(t, 0.09, 0.03, 0.08)
        hums = film_hum(hum_f * jolt, rng, **HUM) + 0.8 * film_hum(hum_f * 1.09 * jolt, rng, **HUM)
        hums = sat(hums * 1.2, 2.5) * env_ad(t, 0.002, 0.2) * 0.8
        return reverb(crk + burst + fry + ring + hums, rng, 0.6, 0.14)
    return g


def make_deflect(f_start, f_end, ring_f):
    """A bolt batted away: a fast falling zap (f_start to f_end Hz), a short zing on ring_f, a spit of sparks and
    the blade's flick (a quick pass of the hum)."""
    def g(rng):
        t = tt(0.5)
        n = len(t)
        f = f_end + (f_start - f_end) * np.exp(-t / 0.035)
        zap = saw(phase(f, n), f_start, nh=8) * env_ad(t, 0.001, 0.07) + 0.5 * np.sin(phase(f * 1.5, n)) * env_ad(t, 0.001, 0.05)
        zing = bell_partials(n, ring_f, [1, 1.52, 2.11], [0.14, 0.08, 0.05], [1, 0.5, 0.3], rng) * 0.5
        prox, _ = pass_curves(t, 0.03, 0.015, 0.07)
        flick = film_hum(SABER_F * 1.15 * doppler(t, 0.03, 0.015, 0.07, 0.12, 0.1), rng, **HUM)
        flick = pass_by(flick, prox, comb=(0.3, 2.5), refl=0.7, floor=0.0, power=1.4) * np.exp(-t / 0.09)
        x = zap * 0.9 + zing + sizzle(t, rng, 2500, 0.0, 0.05) + flick * 0.5 + crack(t, rng, 0.0, 0.004, 1500) * 0.8
        return reverb(x, rng, 0.45, 0.12)
    return g


def g_saber_parry(rng):
    """The perfect parry: a tight contact (a crack, a brief spit of sparks, both hums jolted) under a clean, bright
    bell ring with a long shimmering tail, so it stands apart from a clash at once."""
    t = tt(1.4)
    n = len(t)
    ring = bell_partials(n, 1568, [1, 1.51, 2.42, 3.17, 4.6, 5.9], [0.9, 0.6, 0.45, 0.3, 0.2, 0.12],
                         [1, 0.6, 0.5, 0.35, 0.25, 0.2], rng)
    shimmer = (np.sin(2 * np.pi * 3136 * t) + np.sin(2 * np.pi * 3142 * t)) * env_ad(t, 0.003, 0.5) * 0.25
    contact = crack(t, rng, 0, 0.005, 1800) * 0.2 + sizzle(t, rng, 3000, 0.0, 0.05) * 0.3
    jolt = 1 + 0.3 * np.exp(-t / 0.03)
    hums = film_hum(SABER_F * 1.3 * jolt, rng, **HUM) + film_hum(SABER_F * 1.4 * jolt, rng, **HUM)
    return reverb(ring + shimmer + contact + sat(hums, 1.8) * env_ad(t, 0.002, 0.12) * 0.35, rng, 1.2, 0.2)


def g_saber_guard_break(rng):
    """The guard collapses: a heavy knock under a wide crack and a long spitting sizzle, a metallic groan that
    sags, and the hum overdriven, stuttering faster and faster as it dives."""
    t = tt(1.4)
    n = len(t)
    boom = thump(t, 75, 32, 0.12, 0.4) * 0.7
    hit = crack(t, rng, 0, 0.012, 500) * 1.6
    burst = (sizzle(t, rng, 2500, 0.0, 0.4) + spits(t, rng, 0.01, 0.6, 18) * 0.9
             + arc_fry(t, rng, 90.0) * env_ad(t, 0.003, 0.25) * 0.5)
    sag = 1 - 0.12 * np.clip(t / 0.9, 0, 1)
    groan = sum(a * np.sin(phase(f * sag, n) + rng.uniform(0, 2 * np.pi))
                for f, a in ((410.0, 1.0), (587.0, 0.6), (893.0, 0.4), (1297.0, 0.25))) * env_ad(t, 0.01, 0.5) * 0.25
    f = 40 + 110 * np.exp(-t / 0.35)
    stutter = lp(0.55 + 0.45 * np.sign(np.sin(2 * np.pi * (14 * t + 20 * t ** 2))), 200)
    hum = sat(film_hum(f, rng, **HUM) * 2.0, 3.0) * stutter * env_ad(t, 0.003, 0.45) * 0.6
    return reverb(boom + hit + burst + groan + hum, rng, 1.0, 0.2)


def g_saber_hit(rng):
    """The blade on a body or armour: a frying sear, a burst of sizzle, a hum bump and a muted knock."""
    t = tt(0.7)
    n = len(t)
    fry = 0.5 + 0.5 * np.clip(np.cos(phase(np.full(n, 95.0), n)), 0, 1) ** 4
    sear = bp(noise(n, rng) * fry, 3600, 0.9) * env_ad(t, 0.004, 0.16)
    bump = sat(film_hum(SABER_F * 1.15 * (1 - 0.12 * np.exp(-t / 0.04)), rng, **HUM) * 1.5, 1.8) * env_ad(t, 0.003, 0.14)
    knock = thump(t, 110, 60, 0.04, 0.08)
    return reverb(sear + sizzle(t, rng, 3000, 0.0, 0.2) + bump * 0.6 + knock * 0.8, rng, 0.4, 0.1)


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
# Milestone 3 placeholders (Phase 0). Each M3 id without a recipe in its owner's module gets a short,
# clearly synthetic stand-in chosen from words in the id, so every event is audible from day one.
# They are PLACEHOLDERS: the owning worker replaces them with a FILES entry of the same file names.
# ----------------------------------------------------------------------------

def placeholder_names(eid, variants=1):
    """File names a placeholder renders: <group>/<rest of the id, dots as underscores>[1..variants]."""
    group, _, rest = eid.partition(".")
    base = f"{group}/{rest.replace('.', '_')}"
    return [base] if variants <= 1 else [f"{base}{i + 1}" for i in range(variants)]


def ph_hum(rng, p):
    """Seamless 4 s hum: a detuned sine cluster over soft air."""
    L = 4.0
    t = tt(L)
    n = len(t)
    x = sum(a * np.sin(phase(np.full(n, f * p), n, loop=True) + rng.uniform(0, 6.28))
            for f, a in [(110, 1.0), (110.6, 0.7), (165, 0.4), (220.4, 0.25)])
    x = x * (1 + 0.15 * np.sin(2 * np.pi * 2 * t / L))
    air = lp(noise(n, rng), 400, loop=True) * 1.5
    return reverb(x * 0.4 + air, rng, 1.5, 0.3, loop=True, bright=3000)


def ph_bed(rng, p):
    """Seamless 8 s site bed: slow filtered wind over a low drone."""
    L = 8.0
    t = tt(L)
    n = len(t)
    fc = 300 * p + 150 * np.sin(2 * np.pi * t / L) + 60 * np.sin(2 * np.pi * 3 * t / L + 0.7)
    wind = sweep(noise(n, rng), fc, "lp", nb=10, loop=True)
    drone = sum(a * np.sin(phase(np.full(n, f * p), n, loop=True)) for f, a in [(55, 0.6), (82.5, 0.3)])
    return wind + drone * 0.15 + lp(noise(n, rng), 80, loop=True)


def ph_music(rng, p):
    """Seamless 16 s music stand-in: a slow minor progression on a saw pad with a soft pulse on every second."""
    L = 16.0
    t = tt(L)
    n = len(t)
    chords = [(110.0, 130.81, 164.81), (87.31, 110.0, 130.81), (73.42, 87.31, 110.0), (82.41, 103.83, 123.47)]
    pad = np.zeros(n)
    for k, chord in enumerate(chords):
        d = (t - (k * 4.0 + 2.0) + L / 2) % L - L / 2  # circular distance to the chord's centre
        w = np.where(np.abs(d) < 4.0, np.cos(np.pi * d / 8.0) ** 2, 0.0)  # neighbours sum to 1
        voice = sum(saw(phase(np.full(n, f * p * det), n, loop=True), f * p, nh=12) for f in chord for det in (0.997, 1.003))
        pad += voice * w
    pad = lp(pad, 900, loop=True)
    pulse_ = np.zeros(n)
    beat = thump(tt(0.5), 70 * p, 40 * p, 0.05, 0.12) * 0.8
    for i in range(int(L)):
        place_circular(pulse_, beat, float(i))
    return reverb(pad * 0.35 + pulse_, rng, 2.0, 0.3, loop=True, bright=3000)


def ph_chime(rng, p):
    t = tt(1.2)
    x = bell_partials(len(t), 660 * p, CHIME_RATIOS[:4], [0.8, 0.45, 0.25, 0.15], [1, 0.4, 0.25, 0.1], rng, attack=0.004)
    swish = whoosh(t, rng, 800 + 2000 * bell(t, 0.05, 0.03, 0.15), 1.2, env_ad(t, 0.01, 0.12)) * 0.4
    return reverb(x * 0.6 + swish, rng, 1.2, 0.25, bright=6000)


def ph_impact(rng, p):
    t = tt(1.0)
    boom = thump(t, 80 * p, 35 * p, 0.08, 0.3) * 0.8
    crunch = bp(noise(len(t), rng), 900 * p, 0.8) * env_ad(t, 0.002, 0.06) * 1.6
    deb = bp(crackle(len(t), 220 * np.exp(-t / 0.3), rng), 1800, 0.9) * 3.0
    dust = lp(noise(len(t), rng), 1000) * env_ad(t, 0.04, 0.3) * 0.5
    return reverb(boom + crunch + deb + dust, rng, 1.0, 0.22, bright=3500)


def ph_rise(rng, p):
    t = tt(0.9)
    u = t / 0.9
    f = 220 * p * 2 ** (1.5 * u)
    tone = (np.sin(phase(f, len(t))) + 0.3 * np.sin(phase(f * 2.01, len(t)))) * u ** 1.2
    hiss = sweep(noise(len(t), rng), 600 + 3000 * u, "bp", 2.0, nb=10) * u * 0.5
    x = tone * 0.6 + hiss
    x[-ns(0.01):] *= np.linspace(1, 0, ns(0.01))
    return x


def ph_click(rng, p):
    t = tt(0.4)
    static = bp(noise(len(t), rng), 2200 * p, 1.5) * env_ad(t, 0.005, 0.08) * 0.5
    return reverb(click(t, rng, 0.005, p) + click(t, rng, 0.07, p * 1.1) * 0.6 + static, rng, 0.3, 0.1)


def ph_voice(rng, p, death):
    t = tt(1.0 if death else 0.5)
    x = grunt(t, rng, 0.01, 0.5 if death else 0.18, 130 * p, (85 if death else 110) * p) * 1.4
    x += gear_rattle(t, rng, 0.0, 0.5 if death else 0.15, 12 if death else 7)
    return reverb(x, rng, 0.5, 0.12, bright=4000)


PH_IMPACT = {"burst", "slam", "shatter", "crumble", "break", "puff", "rip", "transition", "unsung", "execute", "crack",
             "collapses", "wrong", "sink", "release", "binders", "return", "throw", "hurl", "wave", "clash"}
PH_RISE = {"tell", "warn", "arm", "fuse", "strain", "gather", "grip", "hold", "hiss", "charge", "lift", "rise", "regrow",
           "extend", "retract", "wake", "start", "lunge", "danger", "form"}
PH_CLICK = {"radio", "use", "accept", "update", "equip", "kolto", "tend", "vent", "flare"}


def m3_placeholder(eid, loop, variant=0):
    """The placeholder generator for one file of an M3 id (stable pitch per id, nudged per variant)."""
    pitch = 2 ** (((zlib.crc32(eid.encode()) % 13) - 6) / 24) * (1 + 0.04 * variant)
    group = eid.split(".")[0]
    words = set(eid.replace(".", "_").split("_"))
    if group == "music":
        return lambda rng: ph_music(rng, pitch)
    if loop:
        return (lambda rng: ph_bed(rng, pitch)) if group == "ambient" else (lambda rng: ph_hum(rng, pitch))
    if "fire" in words:
        return make_blaster_fire(3000 * pitch, 170 * pitch, 0.035, 700 * pitch)
    if words & {"hurt", "death"}:
        if group in ("sentinel", "pylon"):
            return lambda rng: ph_impact(rng, pitch * 1.3)
        return lambda rng: ph_voice(rng, pitch, "death" in words)
    if words & PH_IMPACT:
        return lambda rng: ph_impact(rng, pitch)
    if words & PH_RISE:
        return lambda rng: ph_rise(rng, pitch)
    if words & PH_CLICK:
        return lambda rng: ph_click(rng, pitch)
    return lambda rng: ph_chime(rng, pitch)


# ----------------------------------------------------------------------------
# Milestone 4 placeholders (Phase 0), the M3 approach: each M4 id without a recipe in its owner's module gets a
# short, clearly synthetic stand-in chosen from words in the id and pitched per id. The word lists below add the
# M4 kinds of event (ships, gadgets, droids, beasts) and one more sketch (a whoosh); m3_placeholder is left as it
# was, so no M3 file changes. The owning worker replaces them with a FILES entry of the same file names.
# ----------------------------------------------------------------------------

def ph_whoosh(rng, p):
    """A short rush of air for movement: band-passed noise swelling up and away over a soft low push."""
    t = tt(0.8)
    fc = 500 * p + 2500 * p * bell(t, 0.28, 0.12, 0.3)
    x = whoosh(t, rng, fc, 1.2, bell(t, 0.28, 0.1, 0.3))
    x += thump(t, 90 * p, 50 * p, 0.05, 0.15, t0=0.22) * 0.3
    return reverb(x, rng, 0.6, 0.15, bright=5000)


PH4_FIRE = {"fire", "rifle"}
PH4_VOICE = {"hurt", "death", "idle", "growl", "howl", "snort", "bark"}
PH4_TELL = {"tell", "lock", "spool", "up", "arm", "alert", "overheat", "charge", "charged", "draw", "wave", "ignite",
            "climb"}
PH4_WHOOSH = {"speed", "jet", "hop", "fly", "whirl", "descent", "jump", "throw", "lunge", "pulse", "down", "rocket"}
PH4_IMPACT = PH_IMPACT | {"explode", "impact", "crush", "land", "shock", "cleave", "net", "stun", "flurry", "combo",
                          "spar", "crash", "salvage", "phase", "shield"}
PH4_CLICK = PH_CLICK | {"inject", "fit", "remove", "buy", "refuse", "pickup", "set", "recall", "ping", "scan", "chirp",
                        "step", "empty", "ramp", "done", "weld", "beep", "walk"}


def m4_placeholder(eid, loop, variant=0):
    """The placeholder generator for one file of an M4 id (stable pitch per id, nudged per variant)."""
    pitch = 2 ** (((zlib.crc32(eid.encode()) % 13) - 6) / 24) * (1 + 0.04 * variant)
    group = eid.split(".")[0]
    words = set(eid.replace(".", "_").split("_"))
    if group == "music":
        return lambda rng: ph_music(rng, pitch)
    if loop:
        return (lambda rng: ph_bed(rng, pitch)) if group == "ambient" else (lambda rng: ph_hum(rng, pitch))
    if words & PH4_FIRE:
        return make_blaster_fire(3000 * pitch, 170 * pitch, 0.035, 700 * pitch)
    if words & PH4_VOICE:
        if group == "droid":
            return lambda rng: ph_impact(rng, pitch * 1.3)
        return lambda rng: ph_voice(rng, pitch, bool(words & {"death", "howl"}))
    for wordset, make in ((PH4_TELL, ph_rise), (PH4_WHOOSH, ph_whoosh), (PH4_IMPACT, ph_impact), (PH_RISE, ph_rise),
                          (PH4_CLICK, ph_click)):
        if words & wordset:
            return lambda rng, make=make: make(rng, pitch)
    return lambda rng: ph_chime(rng, pitch)


PH5_FIRE = PH4_FIRE | {"cannon", "turret"}
PH5_VOICE = PH4_VOICE | {"roar", "shove"}
PH5_HUM = {"engine", "hum", "loop", "chant", "choir", "scan", "lock_tone", "warning", "alarm", "wind", "channel",
           "hologram", "lightning", "beam", "ring"}
PH5_TELL = PH4_TELL | {"lock_tone", "locked", "warning", "alarm", "call", "mark", "offer", "boost", "rod_charge",
                       "crescendo", "focus", "land_warn", "edge"}
PH5_WHOOSH = PH4_WHOOSH | {"thruster", "atmosphere", "launch", "step", "sweep", "burrow", "offhand_swing", "stride"}
PH5_IMPACT = PH4_IMPACT | {"hit_shield", "hit_hull", "strike", "blast", "roots", "fog", "silence", "unravel", "gate",
                           "door", "form", "generator_down", "bash", "block", "break", "end", "fail"}
PH5_CLICK = PH4_CLICK | {"comm", "buoy", "rescue", "refit", "upgrade_fit", "helm", "pulse", "tune"}


def m5_placeholder(eid, loop, variant=0):
    """The placeholder generator for one file of an M5 id (the M4 recipe with M5 word lists)."""
    pitch = 2 ** (((zlib.crc32(eid.encode()) % 13) - 6) / 24) * (1 + 0.04 * variant)
    group = eid.split(".")[0]
    last = eid.split(".")[-1]
    words = set(eid.replace(".", "_").split("_")) | {last}
    if group == "music":
        return lambda rng: ph_music(rng, pitch)
    if loop:
        return (lambda rng: ph_bed(rng, pitch)) if group == "ambient" else (lambda rng: ph_hum(rng, pitch))
    if words & PH5_FIRE:
        return make_blaster_fire(3000 * pitch, 170 * pitch, 0.035, 700 * pitch)
    if words & PH5_VOICE:
        return lambda rng: ph_voice(rng, pitch, bool(words & {"death", "howl"}))
    for wordset, make in ((PH5_TELL, ph_rise), (PH5_WHOOSH, ph_whoosh), (PH5_IMPACT, ph_impact), (PH5_HUM, ph_hum),
                          (PH5_CLICK, ph_click)):
        if words & wordset:
            return lambda rng, make=make: make(rng, pitch)
    return lambda rng: ph_chime(rng, pitch)


PLACEHOLDERS = {3: m3_placeholder, 4: m4_placeholder, 5: m5_placeholder}


# ----------------------------------------------------------------------------
# Milestone 2 and later: shared event lists + per-feature recipe modules
# ----------------------------------------------------------------------------
EVENT_LISTS = ((2, EVENTS_M2), (3, EVENTS_M3), (4, EVENTS_M4), (5, EVENTS_M5))


def load_recipe_module(feature, required):
    """The recipe module sounds/<feature>.py, or None when it does not exist and is not required."""
    path = RECIPE_DIR / f"{feature}.py"
    if not path.exists():
        if required:
            sys.exit(f"an event list names feature '{feature}' but {path.relative_to(ROOT)} does not exist")
        return None
    spec = importlib.util.spec_from_file_location(f"oldrepublic_sounds_{feature}", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def load_listed():
    """Events from events_m2.json to events_m5.json with their files from the owning recipe modules (validated).

    An M2 event must have a FILES entry. An M3, M4 or M5 event without one (or whose module does not exist yet) is
    rendered from its milestone's placeholder recipe and marked "placeholder".
    """
    modules = {}
    events = []
    seen_files = {name for ev in EVENTS for name in ev["files"]}
    seen_ids = {ev["id"] for ev in EVENTS}
    for milestone, path in EVENT_LISTS:
        for entry in json.loads(path.read_text())["events"]:
            eid, feature = entry["id"], entry["feature"]
            if eid in seen_ids:
                sys.exit(f"duplicate sound event id {eid} ({path.name})")
            seen_ids.add(eid)
            if feature not in modules:
                modules[feature] = load_recipe_module(feature, required=milestone == 2)
            files = getattr(modules[feature], "FILES", {}).get(eid) if modules[feature] else None
            placeholder = not files
            if placeholder:
                if milestone == 2:
                    sys.exit(f"sounds/{feature}.py has no FILES entry for {eid}")
                names = placeholder_names(eid, entry.get("variants", 1))
                make = PLACEHOLDERS[milestone]
                files = {name: make(eid, entry.get("loop", False), i) for i, name in enumerate(names)}
            for name in files:
                if name in seen_files:
                    sys.exit(f"sound file {name} is used twice")
                seen_files.add(name)
                if entry.get("loop"):
                    LOOPS.add(name)
            extras = {k: entry[k] for k in ("volume", "attenuation_distance", "stream") if k in entry}
            if extras.get("volume") == 1.0:
                del extras["volume"]
            ev = E(eid, entry["subtitle"], files, **extras)
            ev.update(feature=feature, milestone=milestone, placeholder=placeholder)
            events.append(ev)
    declared = {ev["id"] for ev in events}
    for feature, module in modules.items():
        unknown = set(getattr(module, "FILES", {})) - declared
        if unknown:
            sys.exit(f"sounds/{feature}.py defines events missing from the event lists: {sorted(unknown)}")
    return events


def ogg_duration(name):
    """Duration of an already generated file (for json flags when it is not rendered this run)."""
    path = SOUND_DIR / f"{name}.ogg"
    if not path.exists() or not shutil.which("ffprobe"):
        return 0.0
    out = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", str(path)],
                         capture_output=True, text=True)
    try:
        return float(out.stdout.strip())
    except ValueError:
        return 0.0


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


def sounds_json(events, durations):
    data = {}
    for ev in events:
        entries = []
        for name in ev["files"]:
            dur, loop = durations.get(name, (0, name in LOOPS))
            e = {"name": f"{NS}:{name}"}
            if "stream" in ev["extras"]:
                e["stream"] = bool(ev["extras"]["stream"])
            else:
                e["stream"] = bool(loop and dur > 4.0)
            for k in ("volume", "attenuation_distance"):
                if k in ev["extras"]:
                    e[k] = ev["extras"][k]
            entries.append(e)
        data[ev["id"]] = {"subtitle": f"subtitles.{NS}.{ev['id']}", "sounds": entries}
    return data


def java_source(events):
    lines = [
        "package com.oldrepublic.registry;",
        "",
        "import com.oldrepublic.OldRepublicMod;",
        "import net.minecraft.core.Holder;",
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
    consts = set()
    for ev in events:
        g = f"Milestone {ev['milestone']}: {ev['feature']}" if "feature" in ev else ev["id"].split(".")[0]
        if g != group:
            lines.append(f"\t// {g}")
            group = g
        const = ev["id"].replace(".", "_").upper()
        if const in consts:
            sys.exit(f"two sound events map to the constant {const}")
        consts.add(const)
        lines.append(f'\tpublic static final SoundEvent {const} = register("{ev["id"]}");')
    lines += [
        "",
        "\tprivate static SoundEvent register(String id) {",
        "\t\tIdentifier rl = OldRepublicMod.id(id);",
        "\t\treturn Registry.register(BuiltInRegistries.SOUND_EVENT, rl, SoundEvent.createVariableRangeEvent(rl));",
        "\t}",
        "",
        "\t/**",
        "\t * The registered holder of one of these sound events, for vanilla APIs that take a {@code Holder<SoundEvent>}",
        "\t * (armour materials, equip sounds). Every constant here is registered, so this is the registry's reference.",
        "\t */",
        "\tpublic static Holder<SoundEvent> holder(SoundEvent sound) {",
        "\t\treturn BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound);",
        "\t}",
        "",
        "\tpublic static void init() {}",
        "",
        "\tprivate ModSounds() {}",
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
    ap.add_argument("--feature", action="append", help="only render the files of this recipe module (repeatable)")
    ap.add_argument("--m2", action="store_true", help="only render the files of the M2 events")
    ap.add_argument("--m3", action="store_true", help="only render the files of the M3 events")
    ap.add_argument("--m4", action="store_true", help="only render the files of the M4 events")
    ap.add_argument("--m5", action="store_true", help="only render the files of the M5 events")
    ap.add_argument("--placeholders", action="store_true",
                    help="list the M3 to M5 ids still on placeholder audio and exit (--m3, --m4, --m5, --feature narrow it)")
    args = ap.parse_args()
    milestones = {m for m, on in ((2, args.m2), (3, args.m3), (4, args.m4), (5, args.m5)) if on}

    events = EVENTS + load_listed()
    pending = [ev for ev in events if ev.get("placeholder")]
    if args.placeholders:
        shown = [ev for ev in pending if (not milestones or ev["milestone"] in milestones)
                 and (not args.feature or ev["feature"] in args.feature)]
        for ev in shown:
            print(f"  M{ev['milestone']} {ev['feature']:10s} {ev['id']:34s} {', '.join(ev['files'])}")
        by_milestone = ", ".join(f"M{m}: {sum(ev['milestone'] == m for ev in shown)}" for m in (3, 4, 5))
        print(f"{len(shown)} M3 to M5 ids on placeholder audio ({by_milestone})")
        return

    if not shutil.which("ffmpeg"):
        sys.exit("ffmpeg not found on PATH")

    def wanted(ev, name):
        if args.only and name not in args.only:
            return False
        if args.feature and ev.get("feature") not in args.feature:
            return False
        if milestones and ev.get("milestone") not in milestones:
            return False
        return True

    durations = {}
    if not args.no_audio:
        with tempfile.TemporaryDirectory() as tmp:
            for ev in events:
                for name, gen in ev["files"].items():
                    if not wanted(ev, name):
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
    for ev in events:
        for name in ev["files"]:
            if name in LOOPS and name not in durations:
                durations[name] = (ogg_duration(name), True)

    SOUNDS_JSON.write_text(json.dumps(sounds_json(events, durations), indent=2) + "\n")
    LANG_PART.parent.mkdir(parents=True, exist_ok=True)
    LANG_PART.write_text(json.dumps({f"subtitles.{NS}.{ev['id']}": ev["subtitle"] for ev in events}, indent=2) + "\n")
    if args.java:
        JAVA_OUT.parent.mkdir(parents=True, exist_ok=True)
        JAVA_OUT.write_text(java_source(events))
    print(f"wrote {SOUNDS_JSON.relative_to(ROOT)}, {LANG_PART.relative_to(ROOT)}" + (f", {JAVA_OUT.relative_to(ROOT)}" if args.java else ""))
    if pending:
        print(f"{len(pending)} M3 to M5 ids still use placeholder recipes (--placeholders lists them)")


if __name__ == "__main__":
    # Recipe modules `import gen_sounds`; make that this running module rather than a second copy.
    sys.modules.setdefault("gen_sounds", sys.modules[__name__])
    main()
