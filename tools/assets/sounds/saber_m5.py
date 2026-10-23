"""PLACEHOLDER sound recipes for the Milestone 5 sabers and legendary crafting (MILESTONE_5.md 10.5, 10.2, 11.1).

Owned by W7 (sabers, crafting and gear). Synthesised from scratch with the helpers in gen_sounds.py,
on its film-style saber blocks (film_hum, pass_by, saber_ignite, saber_retract; see the Lightsaber
section there); nothing is sampled. See events_m5.json for the event list. File names are the
placeholder names (<group>/<rest>), so sounds.json and the subtitles do not change.

The four hum profiles (10.5), one ignition, hum and retraction per emitter, all three built on the
emitter's own hum character (HUM_G, HUM_D, HUM_R, HUM_C below), so they sound like one blade:
  Guardian   deep and steady: 76 Hz, a dark motor with a sub-octave, little buzz, a slow, even throb;
             an unhurried ignition out of a low thud.
  Duelist    bright and thin: 112 Hz, the low end cut, a strong, bright buzz beating quickly against
             the motor; a hard, quick snap of an ignition.
  Reliquary  old and wavering: 86 Hz with an uneven pitch drift, power that sags and catches, crackle
             and a dirty buzz; it sputters twice before it catches.
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
# Each emitter's hum character (film_hum keywords over the M1 hum's).
HUM_G = dict(g.HUM, buzz=0.16, throb=0.25, throb_depth=0.18, growl=19.0, growl_depth=0.035, bright=1900.0,
             body=((240.0, 1.3, 2.4), (950.0, 2.0, 1.6)), sub=0.3, drift=0.001)
HUM_D = dict(g.HUM, buzz=0.32, beat=0.5, throb=1.25, throb_depth=0.25, growl=32.0, growl_depth=0.04, bright=3000.0,
             body=((340.0, 1.3, 1.8), (1400.0, 2.0, 1.9)), buzz_band=(2800.0, 1.1, 2.8), buzz_lp=5000.0,
             low_cut=170.0)
HUM_R = dict(g.HUM, buzz=0.3, throb=0.75, throb_depth=0.35, growl=21.0, growl_depth=0.1, bright=2100.0, drift=0.002,
             air=0.03)
HUM_C = dict(g.HUM, buzz=0.14, throb_depth=0.2, growl_depth=0.03, bright=1800.0, sub=0.2)


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


def steady(f0, n):
    """A held pitch, one value per sample."""
    return np.full(n, f0)


# ---------------------------------------------------------------- Guardian: deep and steady


def g_guardian_ignite(rng):
    """Deep and unhurried: a softer snap and a darker hiss, the hum climbing slowly out of a low thud and
    settling heavy."""
    x = g.saber_ignite(rng, FG, HUM_G, dur=1.3, start=0.62, rise=0.06, over=0.04, t_over=0.16, settle=0.14, out=0.11,
                       snap=0.7, hiss=0.7, sparks=0.5, hiss_fc=3000.0, hold=0.35, room=(0.8, 0.15))
    t = g.tt(len(x) / g.SR)
    return x + g.thump(t, 90, 40, 0.03, 0.18) * 0.5


def g_guardian_hum(rng):
    return g.film_hum(steady(FG, g.ns(LOOP)), rng, loop=True, **HUM_G)


def g_guardian_retract(rng):
    """The hum sinks slowly under a dark hiss and ends on a soft, low thud."""
    x = g.saber_retract(rng, FG, HUM_G, dur=1.0, end=0.45, hold=0.05, tau=0.2, decay=0.26, hiss=0.8, hiss_fc=2400.0,
                        click=0.0, room=(0.7, 0.14))
    t = g.tt(len(x) / g.SR)
    return x + g.thump(t, 70, 32, 0.02, 0.14, t0=0.42) * 0.35


# ---------------------------------------------------------------- Duelist: bright and thin, quick


def g_duelist_ignite(rng):
    """Fast and precise: a hard snap and a bright hiss, the thin hum there almost at once, and a high zing."""
    x = g.saber_ignite(rng, FD, HUM_D, dur=0.8, start=0.75, rise=0.018, over=0.07, t_over=0.06, settle=0.06, out=0.045,
                       snap=1.2, hiss=1.0, sparks=1.0, hiss_fc=5200.0, hold=0.25, room=(0.5, 0.12))
    t = g.tt(len(x) / g.SR)
    zing = np.sin(g.phase(2200 + 1400 * np.clip(t / 0.1, 0, 1), len(t))) * g.env_ad(t, 0.005, 0.07) * 0.15
    return x + zing


def g_duelist_hum(rng):
    return g.film_hum(steady(FD, g.ns(LOOP)), rng, loop=True, **HUM_D)


def g_duelist_retract(rng):
    """A fast, bright fall under a quick hiss, with a tiny click at the end."""
    return g.saber_retract(rng, FD, HUM_D, dur=0.6, end=0.5, hold=0.02, tau=0.09, decay=0.13, hiss=1.0,
                           hiss_fc=4200.0, click=0.3, t_click=0.22, room=(0.45, 0.12))


# ---------------------------------------------------------------- Reliquary: old, wavering, crackling


def g_reliquary_ignite(rng):
    """It sputters twice (two snaps of half-lit, crackling hum) before it catches with a third snap and the rush,
    and a crackle that settles."""
    t = g.tt(1.3)
    n = len(t)
    catch = 0.32
    gasps = g.bell(t, 0.06, 0.02, 0.03) * 0.55 + g.bell(t, 0.19, 0.02, 0.04) * 0.7
    sputter = g.film_hum(FR * (0.8 + 0.06 * np.sin(2 * np.pi * 9 * t)), rng, **HUM_R) * gasps * 0.8
    snaps = g.crack(t, rng, 0.05, 0.012, 1400) * 0.45 + g.crack(t, rng, 0.18, 0.012, 1400) * 0.5
    crackles = g.hp(g.crackle(n, 600 * np.exp(-np.maximum(t - catch, 0) / 0.2) * (t > 0.04) + 80, rng), 2500) * 0.5
    caught = g.saber_ignite(rng, FR, HUM_R, dur=1.3 - catch, start=0.66, rise=0.04, over=0.06, t_over=0.1, hold=0.3,
                            snap=0.9, hiss=0.9, sparks=1.0, room=None)
    return g.reverb(g.place(sputter + snaps + crackles, caught, catch), rng, 0.7, 0.15)


def g_reliquary_hum(rng):
    t = g.tt(LOOP)
    n = len(t)
    # An uneven waver: three slow drifts, each a whole number of cycles per loop.
    waver = (0.012 * np.sin(2 * np.pi * 0.5 * t) + 0.007 * np.sin(2 * np.pi * 1.25 * t + 1.1)
             + 0.004 * np.sin(2 * np.pi * 3.25 * t + 2.3))
    x = g.film_hum(FR * (1 + waver), rng, loop=True, **HUM_R)
    # The old crystal's power sags and catches.
    am = 1 + 0.09 * np.sin(2 * np.pi * 0.75 * t + 0.5) + 0.05 * np.sin(2 * np.pi * 2.75 * t)
    cr = g.hp(g.crackle(n, 22 * (1 + np.sin(2 * np.pi * 0.5 * t) ** 2), rng), 2200, loop=True) * 0.6
    buzz = g.bp(g.crackle(n, 140, rng), 900, 1.2, loop=True) * 0.25
    return x * am + cr + buzz


def g_reliquary_retract(rng):
    """A trembling fall: the hum dives with a shiver, fizzing, flickers once more and dies with a small pop."""
    t = g.tt(1.0)
    n = len(t)
    f = (0.32 + 0.68 * np.exp(-np.maximum(t - 0.05, 0) / 0.2)) * FR * (1 + 0.015 * np.sin(2 * np.pi * 7 * t))
    prox, _ = g.pass_curves(t, 0.0, 0.05, 0.14)
    tone = g.pass_by(g.film_hum(f, rng, **HUM_R), prox, comb=(0.3, 3.0), refl=0.7, floor=0.35, power=0.8)
    amp = np.minimum(1, t / 0.004) * np.exp(-np.maximum(t - 0.08, 0) / 0.25) * (1 - 0.6 * g.bell(t, 0.3, 0.02, 0.03))
    hiss = g.sweep(g.noise(n, rng), 2600 * np.exp(-t / 0.14) + 450, "bp", 1.3) * g.bell(t, 0.05, 0.015, 0.15) * 0.45
    fizz = g.hp(g.crackle(n, 700 * np.exp(-t / 0.25), rng), 2200) * 0.6
    pop = g.crack(t, rng, 0.46, 0.006, 1800) * 0.3
    return g.reverb(tone * amp * 0.6 + hiss + fizz + pop, rng, 0.6, 0.14)


# ---------------------------------------------------------------- Cantor: low, with a choir-like overtone


def g_cantor_ignite(rng):
    t = g.tt(1.6)
    n = len(t)
    blade = g.saber_ignite(rng, FC, HUM_C, dur=1.3, start=0.62, rise=0.055, over=0.04, t_over=0.15, settle=0.13,
                           out=0.1, snap=0.7, hiss=0.7, sparks=0.6, hiss_fc=3200.0, hold=0.35, room=None)
    # The choir answers as the blade settles: a breath, then a swell on the major chord and its octave.
    voices = choir((FC * 4, FC * 5, FC * 6, FC * 8), n, rng) * g.bell(t, 0.6, 0.25, 0.55) * 0.6
    breath = g.bp(g.noise(n, rng), 1800, 1.2) * g.bell(t, 0.32, 0.12, 0.25) * 0.15
    return g.reverb(g.place(voices + breath, blade, 0.0), rng, 1.4, 0.25, bright=6000)


def g_cantor_hum(rng):
    t = g.tt(LOOP)
    n = len(t)
    x = g.film_hum(FC * (1 + 0.003 * np.sin(2 * np.pi * 0.5 * t)), rng, loop=True, **HUM_C)
    # The choir breathes in and out twice a loop.
    swell = 0.55 + 0.25 * np.sin(2 * np.pi * 0.5 * t - np.pi / 2) + 0.08 * np.sin(2 * np.pi * 1.25 * t)
    voices = choir((FC * 4, FC * 5, FC * 6), n, rng, loop=True) * 0.55 * swell
    return g.reverb(x * 0.75 + voices, rng, 1.2, 0.18, loop=True, bright=6000)


def g_cantor_retract(rng):
    t = g.tt(1.4)
    n = len(t)
    blade = g.saber_retract(rng, FC, HUM_C, dur=1.0, end=0.45, hold=0.05, tau=0.2, decay=0.26, hiss=0.7,
                            hiss_fc=2600.0, click=0.0, room=None)
    # The choir sighs down a little and fades after the blade.
    sigh = 1 - 0.06 * np.clip((t - 0.1) / 0.7, 0, 1)
    voices = choir((FC * 4, FC * 5, FC * 6), n, rng, bend=sigh) * g.env_ad(t, 0.05, 0.45) * 0.45
    return g.reverb(g.place(voices, blade, 0.0), rng, 1.3, 0.25, bright=6000)


# ---------------------------------------------------------------- the off hand and the legendary bench


def g_offhand_swing(rng):
    """The off hand's quicker, lighter cut: a shorter pass, a little higher, on a thinner hum."""
    return g.make_swing(dict(dur=0.42, tp=0.16, f0=104, up=0.13, down=0.09, w1=0.06, w2=0.09, wh=3000,
                             hum=dict(low_cut=150.0)))(rng)


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
