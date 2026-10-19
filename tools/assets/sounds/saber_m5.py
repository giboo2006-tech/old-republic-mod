"""PLACEHOLDER sound recipes for the Milestone 5 sabers and legendary crafting (MILESTONE_5.md 10.5, 10.2, 11.1).

Owned by W7 (sabers, crafting and gear). Synthesised from scratch with the helpers in gen_sounds.py,
building on its M1 saber tone; nothing is sampled. See events_m5.json for the event list. File names
are the placeholder names (<group>/<rest>), so sounds.json and the subtitles do not change.

The four hum profiles (10.5), one ignition, hum and retraction per emitter:
  Guardian   deep and steady: 76 Hz, a heavier sub and a slow, even swell.
  Duelist    bright and thin: 112 Hz, the low end thinned, a quick snapping ignition.
  Reliquary  old and wavering: 86 Hz with an uneven pitch drift and crackle; it sputters twice
             before it catches.
  Cantor     low (70 Hz) with a choir-like overtone: three wordless voices on the 4th, 5th and 6th
             harmonics (a major chord) through "ah" formants, breathing in and out with the hum.
Hums are seamless 4 s loops: every modulation runs a whole number of cycles per loop and every
filter is circular.

Also: saber.offhand_swing (the off hand's quicker, lighter cut, 10.2) and bench.legendary (a
legendary piece is finished: a set-down thump, a rising gold chime and a choir swell, 11.1).

    python3 tools/assets/gen_sounds.py --m5 --feature saber_m5
"""
import gen_sounds as g

np = g.np

FG = 76.0   # Guardian
FD = 112.0  # Duelist
FR = 86.0   # Reliquary
FC = 70.0   # Cantor
LOOP = 4.0
# The "ah" formants of the Cantor's choir: (centre, Q, level).
VOWEL = ((750.0, 4.0, 1.0), (1150.0, 5.0, 0.6), (2600.0, 6.0, 0.25))


def choir(f0s, n, rng, loop=False, bend=None, vib=0.004):
    """A wordless choir: each pitch sung by three slightly detuned voices (soft saws with a 5 Hz
    vibrato), through the VOWEL formants. {bend} multiplies every pitch over time."""
    t = np.arange(n) / g.SR
    x = np.zeros(n)
    for f0 in f0s:
        for d in (0.0, 0.0035, -0.0042):
            f = f0 * (1 + d) * (1 + vib * np.sin(2 * np.pi * 5.0 * t + rng.uniform(0, 2 * np.pi)))
            if bend is not None:
                f = f * bend
            x += g.saw(g.phase(f, n, loop) + rng.uniform(0, 2 * np.pi), f0 * 1.02, nh=24, roll=1.6)
    out = np.zeros(n)
    for fc, q, a in VOWEL:
        out += g.bp(x, fc, q, loop=loop) * a
    return out / (np.max(np.abs(out)) + 1e-9)


def rise(t, t0, dur, start, peak):
    """The ignition's whoosh centre: from {start} up {peak} times by {dur}, then falling away."""
    up = start * (peak ** np.clip((t - t0) / dur, 0, 1))
    return up * np.where(t > t0 + dur, np.exp(-(t - t0 - dur) / 0.3), 1)


# ---------------------------------------------------------------- Guardian: deep and steady


def g_guardian_ignite(rng):
    t = g.tt(1.3)
    n = len(t)
    f = FG + (30 - FG) * np.exp(-t / 0.09) + 0.1 * FG * g.bell(t, 0.24, 0.08, 0.2)
    amp = np.minimum(1, t / 0.14) * (1 - 0.4 * np.clip((t - 0.5) / 0.7, 0, 1))
    tone = g.saber_tone(f, rng, bright=2000) * amp
    sub = np.sin(g.phase(f * 0.5, n)) * amp * 0.4
    thud = g.thump(t, 90, 40, 0.03, 0.18) * 0.7
    sw = g.whoosh(t, rng, rise(t, 0.0, 0.3, 200, 10) + 160, 1.1, g.env_ad(t, 0.04, 0.16)) * 1.1
    sizzle = g.hp(g.crackle(n, 500 * np.exp(-t / 0.1), rng), 2800) * 0.35
    return g.reverb(tone + sub + thud + sw + sizzle, rng, 0.8, 0.15)


def g_guardian_hum(rng):
    t = g.tt(LOOP)
    n = len(t)
    f = FG * (1 + 0.002 * np.sin(2 * np.pi * 0.25 * t))
    x = g.saber_tone(f, rng, loop=True, bright=1900, grit=0.1)
    sub = np.sin(g.phase(f * 0.5, n, loop=True)) * 0.35
    am = 1 + 0.03 * np.sin(2 * np.pi * 0.5 * t)
    return x * am + sub


def g_guardian_retract(rng):
    t = g.tt(1.0)
    n = len(t)
    f = 26 + (FG - 26) * np.exp(-np.maximum(t - 0.06, 0) / 0.24)
    amp = np.minimum(1, t / 0.01) * np.exp(-np.maximum(t - 0.1, 0) / 0.28)
    tone = g.saber_tone(f, rng, bright=1500) * amp
    sub = np.sin(g.phase(f * 0.5, n)) * amp * 0.35
    sw = g.whoosh(t, rng, 1800 * np.exp(-t / 0.2) + 150, 1.2, g.env_ad(t, 0.01, 0.18)) * 1.4
    thud = g.thump(t, 70, 32, 0.02, 0.14, t0=0.42) * 0.35
    return g.reverb(tone + sub + sw + thud, rng, 0.7, 0.14)


# ---------------------------------------------------------------- Duelist: bright and thin, quick


def g_duelist_ignite(rng):
    t = g.tt(0.8)
    n = len(t)
    f = FD + (50 - FD) * np.exp(-t / 0.03) + 0.18 * FD * g.bell(t, 0.1, 0.035, 0.09)
    amp = np.minimum(1, t / 0.05) * (1 - 0.45 * np.clip((t - 0.25) / 0.5, 0, 1))
    tone = g.hp(g.saber_tone(f, rng, bright=3800), 160) * amp
    snap = g.crack(t, rng, 0.0, 0.012, 2400) * 1.1
    sw = g.whoosh(t, rng, rise(t, 0.0, 0.12, 500, 12) + 300, 1.6, g.env_ad(t, 0.015, 0.08)) * 1.3
    zing = np.sin(g.phase(2200 + 1400 * np.clip(t / 0.1, 0, 1), n)) * g.env_ad(t, 0.005, 0.07) * 0.15
    return g.reverb(tone + snap + sw + zing, rng, 0.5, 0.12, bright=7000)


def g_duelist_hum(rng):
    t = g.tt(LOOP)
    f = FD * (1 + 0.005 * np.sin(2 * np.pi * 1.0 * t) + 0.003 * np.sin(2 * np.pi * 2.5 * t + 0.6))
    x = g.hp(g.saber_tone(f, rng, loop=True, bright=3600, grit=0.25), 180, loop=True)
    shimmer = g.bp(x, 2400, 3.0, loop=True) * 0.4
    am = 1 + 0.06 * np.sin(2 * np.pi * 1.5 * t + 0.3)
    return (x + shimmer) * am


def g_duelist_retract(rng):
    t = g.tt(0.6)
    f = 40 + (FD - 40) * np.exp(-np.maximum(t - 0.03, 0) / 0.12)
    amp = np.minimum(1, t / 0.008) * np.exp(-np.maximum(t - 0.05, 0) / 0.15)
    tone = g.hp(g.saber_tone(f, rng, bright=3000), 150) * amp
    sw = g.whoosh(t, rng, 3400 * np.exp(-t / 0.1) + 250, 1.5, g.env_ad(t, 0.008, 0.1)) * 1.6
    clk = g.crack(t, rng, 0.22, 0.003, 3000) * 0.3
    return g.reverb(tone + sw + clk, rng, 0.45, 0.12, bright=7000)


# ---------------------------------------------------------------- Reliquary: old, wavering, crackling


def g_reliquary_ignite(rng):
    """It sputters twice before it catches."""
    t = g.tt(1.3)
    n = len(t)
    catch = 0.32
    f = FR + (36 - FR) * np.exp(-np.maximum(t - catch, 0) / 0.07) + 0.12 * FR * g.bell(t, catch + 0.18, 0.06, 0.15)
    f = f * (1 + 0.01 * np.sin(2 * np.pi * 3.0 * t))
    sputter = g.bell(t, 0.06, 0.02, 0.03) * 0.55 + g.bell(t, 0.19, 0.02, 0.04) * 0.7
    amp = np.maximum(sputter, np.clip((t - catch) / 0.1, 0, 1)) * (1 - 0.4 * np.clip((t - catch - 0.4) / 0.6, 0, 1))
    tone = g.saber_tone(f, rng, bright=2400, grit=0.3) * amp
    snaps = (g.crack(t, rng, 0.05, 0.015, 1400) * 0.7 + g.crack(t, rng, 0.18, 0.015, 1400) * 0.8
             + g.crack(t, rng, catch, 0.02, 1500) * 0.9)
    crackles = g.hp(g.crackle(n, 600 * np.exp(-np.maximum(t - catch, 0) / 0.2) + 80, rng), 2500) * 0.6
    sw = g.whoosh(t, rng, rise(t, catch, 0.25, 240, 12) + 200, 1.2, g.env_ad(t, 0.03, 0.12, t0=catch)) * 1.2
    return g.reverb(tone + snaps + crackles + sw, rng, 0.7, 0.15)


def g_reliquary_hum(rng):
    t = g.tt(LOOP)
    n = len(t)
    # An uneven waver: three slow drifts, each a whole number of cycles per loop.
    waver = (0.012 * np.sin(2 * np.pi * 0.5 * t) + 0.007 * np.sin(2 * np.pi * 1.25 * t + 1.1)
             + 0.004 * np.sin(2 * np.pi * 3.25 * t + 2.3))
    x = g.saber_tone(FR * (1 + waver), rng, loop=True, bright=2100, grit=0.35)
    # The old crystal's power sags and catches.
    am = 1 + 0.09 * np.sin(2 * np.pi * 0.75 * t + 0.5) + 0.05 * np.sin(2 * np.pi * 2.75 * t)
    cr = g.hp(g.crackle(n, 22 * (1 + np.sin(2 * np.pi * 0.5 * t) ** 2), rng), 2200, loop=True) * 0.6
    buzz = g.bp(g.crackle(n, 140, rng), 900, 1.2, loop=True) * 0.25
    return x * am + cr + buzz


def g_reliquary_retract(rng):
    t = g.tt(1.0)
    n = len(t)
    f = (28 + (FR - 28) * np.exp(-np.maximum(t - 0.05, 0) / 0.2)) * (1 + 0.015 * np.sin(2 * np.pi * 7 * t))
    # A last flicker as it dies.
    amp = np.minimum(1, t / 0.01) * np.exp(-np.maximum(t - 0.08, 0) / 0.25) * (1 - 0.6 * g.bell(t, 0.3, 0.02, 0.03))
    tone = g.saber_tone(f, rng, bright=1700, grit=0.35) * amp
    sw = g.whoosh(t, rng, 2200 * np.exp(-t / 0.16) + 170, 1.3, g.env_ad(t, 0.01, 0.15)) * 1.5
    fizz = g.hp(g.crackle(n, 700 * np.exp(-t / 0.25), rng), 2200) * 0.6
    pop = g.crack(t, rng, 0.46, 0.006, 1800) * 0.4
    return g.reverb(tone + sw + fizz + pop, rng, 0.6, 0.14)


# ---------------------------------------------------------------- Cantor: low, with a choir-like overtone


def g_cantor_ignite(rng):
    t = g.tt(1.6)
    n = len(t)
    f = FC + (30 - FC) * np.exp(-t / 0.08) + 0.1 * FC * g.bell(t, 0.22, 0.07, 0.18)
    amp = np.minimum(1, t / 0.12) * (1 - 0.4 * np.clip((t - 0.5) / 0.9, 0, 1))
    tone = g.saber_tone(f, rng, bright=1900) * amp
    sub = np.sin(g.phase(f * 0.5, n)) * amp * 0.3
    sw = g.whoosh(t, rng, rise(t, 0.0, 0.28, 220, 10) + 170, 1.1, g.env_ad(t, 0.04, 0.15)) * 1.0
    snap = g.crack(t, rng, 0.0, 0.02, 1400) * 0.7
    # The choir answers as the blade settles: a breath, then a swell on the major chord and its octave.
    voices = choir((FC * 4, FC * 5, FC * 6, FC * 8), n, rng) * g.bell(t, 0.6, 0.25, 0.55) * 0.6
    breath = g.bp(g.noise(n, rng), 1800, 1.2) * g.bell(t, 0.32, 0.12, 0.25) * 0.15
    return g.reverb(tone + sub + sw + snap + voices + breath, rng, 1.4, 0.25, bright=6000)


def g_cantor_hum(rng):
    t = g.tt(LOOP)
    n = len(t)
    f = FC * (1 + 0.003 * np.sin(2 * np.pi * 0.5 * t))
    x = g.saber_tone(f, rng, loop=True, bright=1700, grit=0.12)
    sub = np.sin(g.phase(f * 0.5, n, loop=True)) * 0.3
    # The choir breathes in and out twice a loop.
    swell = 0.55 + 0.25 * np.sin(2 * np.pi * 0.5 * t - np.pi / 2) + 0.08 * np.sin(2 * np.pi * 1.25 * t)
    voices = choir((FC * 4, FC * 5, FC * 6), n, rng, loop=True) * 0.55 * swell
    return g.reverb(x + sub + voices, rng, 1.2, 0.18, loop=True, bright=6000)


def g_cantor_retract(rng):
    t = g.tt(1.4)
    n = len(t)
    f = 26 + (FC - 26) * np.exp(-np.maximum(t - 0.05, 0) / 0.22)
    amp = np.minimum(1, t / 0.01) * np.exp(-np.maximum(t - 0.1, 0) / 0.26)
    tone = g.saber_tone(f, rng, bright=1500) * amp
    sub = np.sin(g.phase(f * 0.5, n)) * amp * 0.3
    sw = g.whoosh(t, rng, 1900 * np.exp(-t / 0.18) + 150, 1.2, g.env_ad(t, 0.01, 0.16)) * 1.3
    # The choir sighs down a little and fades after the blade.
    sigh = 1 - 0.06 * np.clip((t - 0.1) / 0.7, 0, 1)
    voices = choir((FC * 4, FC * 5, FC * 6), n, rng, bend=sigh) * g.env_ad(t, 0.05, 0.45) * 0.45
    return g.reverb(tone + sub + sw + voices, rng, 1.3, 0.25, bright=6000)


# ---------------------------------------------------------------- the off hand and the legendary bench


def g_offhand_swing(rng):
    """The off hand's quicker, lighter cut: a shorter swing, a little higher, a thinner whoosh."""
    return g.make_swing(dict(dur=0.42, tp=0.16, f0=104, up=0.13, down=0.09, w1=0.06, w2=0.09, wh=3000))(rng)


def g_bench_legendary(rng):
    """The piece set down, a rising gold chime (C5 E5 G5 C6) and a choir swell under a fall of sparks."""
    t = g.tt(2.6)
    n = len(t)
    x = g.thump(t, 110, 55, 0.02, 0.25) * 0.5
    for t0, f in ((0.05, 523.25), (0.2, 659.25), (0.35, 783.99), (0.55, 1046.5)):
        x += g.bell_partials(n, f, g.CHIME_RATIOS, [1.4, 0.6, 0.35, 0.2, 0.12], [1, 0.45, 0.25, 0.12, 0.06], rng, t0=t0) * 0.32
    x += choir((261.63, 329.63, 392.0), n, rng) * g.bell(t, 0.9, 0.35, 0.8) * 0.35
    sparkle = g.hp(g.crackle(n, 60 * g.bell(t, 0.7, 0.2, 0.6), rng), 6000) * 0.5
    return g.reverb(x + sparkle, rng, 1.8, 0.3, bright=9000)


FILES = {
    "saber.guardian.ignite": {"saber/guardian_ignite": g_guardian_ignite},
    "saber.guardian.hum": {"saber/guardian_hum": g_guardian_hum},
    "saber.guardian.retract": {"saber/guardian_retract": g_guardian_retract},
    "saber.duelist.ignite": {"saber/duelist_ignite": g_duelist_ignite},
    "saber.duelist.hum": {"saber/duelist_hum": g_duelist_hum},
    "saber.duelist.retract": {"saber/duelist_retract": g_duelist_retract},
    "saber.reliquary.ignite": {"saber/reliquary_ignite": g_reliquary_ignite},
    "saber.reliquary.hum": {"saber/reliquary_hum": g_reliquary_hum},
    "saber.reliquary.retract": {"saber/reliquary_retract": g_reliquary_retract},
    "saber.cantor.ignite": {"saber/cantor_ignite": g_cantor_ignite},
    "saber.cantor.hum": {"saber/cantor_hum": g_cantor_hum},
    "saber.cantor.retract": {"saber/cantor_retract": g_cantor_retract},
    "saber.offhand_swing": {"saber/offhand_swing": g_offhand_swing},
    "bench.legendary": {"bench/legendary": g_bench_legendary},
}
