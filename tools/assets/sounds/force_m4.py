"""PLACEHOLDER sound recipes for the Milestone 4 powers: Force Speed, Force Barrier, Force Choke.

Owned by W6 (Force and foes). Synthesised from scratch with the helpers in gen_sounds.py (noise,
filters, sweeps, thumps); nothing is sampled. See events_m4.json for the event list and
docs/MILESTONE_4.md sections 9.1 to 9.3 for each power's intended sound. File names are the
placeholder names (<group>/<rest>), so sounds.json does not change when these replace them.

    python3 tools/assets/gen_sounds.py --m4 --feature force_m4
"""
import gen_sounds as g

np = g.np


# ---------------------------------------------------------------- speed

def g_speed_start(rng):
    """A sharp breath in through the teeth, then a whoosh that leans forward."""
    t = g.tt(0.8)
    breath = g.bp(g.noise(len(t), rng), 3200, 1.6) * g.env_ad(t, 0.04, 0.12) * 0.9
    rush = g.whoosh(t, rng, 300 + 1700 * g.bell(t, 0.28, 0.12, 0.25), 1.3, g.bell(t, 0.28, 0.12, 0.3)) * 1.4
    kick = g.thump(t, 70, 45, 0.02, 0.08, t0=0.18) * 0.4
    return g.reverb(breath + rush + kick, rng, 0.5, 0.12, bright=4000)


def g_speed_loop(rng):
    """Seamless: soft wind past the ears, gusting slowly."""
    L = 3.0
    t = g.tt(L)
    n = len(t)
    gust = 0.75 + 0.18 * np.sin(2 * np.pi * 2 * t / L) + 0.07 * np.sin(2 * np.pi * 5 * t / L + 0.8)
    wind = g.bp(g.noise(n, rng), 650, 0.7, loop=True) * gust
    whistle = g.bp(g.noise(n, rng), 1900, 6.0, loop=True) * (0.3 + 0.2 * np.sin(2 * np.pi * 3 * t / L + 2.1))
    low = g.lp(g.noise(n, rng), 160, loop=True) * 1.5
    return wind + whistle * 0.6 + low


def g_speed_end(rng):
    """The wind falls away with an easy exhale."""
    t = g.tt(0.7)
    fall = g.whoosh(t, rng, 1400 * np.exp(-t / 0.18) + 200, 1.2, g.env_ad(t, 0.01, 0.22)) * 1.3
    exhale = g.bp(g.noise(len(t), rng), 1100, 1.0) * g.env_ad(t, 0.06, 0.2, t0=0.1) * 0.5
    return g.reverb(fall + exhale, rng, 0.5, 0.12, bright=3500)


def g_speed_burst(rng):
    """A short, fast tear of air and a push off the ground."""
    t = g.tt(0.55)
    tear = g.whoosh(t, rng, 900 + 3200 * g.bell(t, 0.07, 0.03, 0.12), 1.8, g.bell(t, 0.07, 0.03, 0.15)) * 1.7
    push = g.thump(t, 95, 40, 0.01, 0.07) * 0.8
    grit = g.bp(g.crackle(len(t), 300, rng), 2600, 1.0) * g.env_ad(t, 0.005, 0.08) * 1.2
    return g.reverb(tear + push + grit, rng, 0.45, 0.12)


# ---------------------------------------------------------------- barrier

def g_barrier_start(rng):
    """A deep inhale and a pressure swell that settles into a held weight."""
    t = g.tt(1.1)
    inhale = g.sweep(g.noise(len(t), rng), 300 + 1500 * np.clip(t / 0.45, 0, 1) ** 2, "bp", 1.2, nb=12) \
        * np.clip(t / 0.45, 0, 1) * np.exp(-np.maximum(t - 0.45, 0) / 0.1) * 1.3
    swell = g.thump(t, 70, 46, 0.25, 0.4, t0=0.35, attack=0.12) * 0.9
    hum = np.sin(g.phase(np.full(len(t), 58.0), len(t))) * g.bell(t, 0.7, 0.25, 0.35) * 0.3
    return g.reverb(inhale + swell + hum, rng, 1.0, 0.2, bright=2500)


def g_barrier_loop(rng):
    """Seamless: a low hum of held air with a slow beat; the client lowers its pitch as energy runs low."""
    L = 2.0
    t = g.tt(L)
    n = len(t)
    hum = np.zeros(n)
    for f, a in [(55.0, 1.0), (55.5, 0.7), (110.0, 0.35), (165.5, 0.12)]:
        hum += a * np.sin(g.phase(np.full(n, f), n, loop=True))
    hum *= 0.86 + 0.14 * np.sin(2 * np.pi * 2 * t / L)
    air = g.bp(g.noise(n, rng), 300, 0.9, loop=True) * 0.45
    dust = g.bp(g.crackle(n, 12, rng), 2500, 1.0, loop=True) * 0.4
    return hum * 0.5 + air + dust


def g_barrier_impact(rng):
    """Something stopped dead in the air: a muffled thud and a spit of sparks."""
    t = g.tt(0.45)
    thud = g.thump(t, 140, 60, 0.008, 0.07) * 1.0
    spit = g.bp(g.crackle(len(t), 1400 * np.exp(-t / 0.05), rng), 3600, 1.2) * 1.6
    press = g.lp(g.noise(len(t), rng), 700) * g.env_ad(t, 0.002, 0.06) * 0.6
    return g.reverb(thud + spit + press, rng, 0.5, 0.15, bright=4500)


def g_barrier_pulse(rng):
    """The held air let go all at once: a thump and an outward rush with dust in it."""
    t = g.tt(1.0)
    boom = g.sat(g.thump(t, 90, 36, 0.02, 0.22), 1.5)
    rush = g.whoosh(t, rng, 1800 * np.exp(-t / 0.15) + 220, 1.1, g.env_ad(t, 0.005, 0.3)) * 1.6
    dust = g.bp(g.crackle(len(t), 500 * np.exp(-t / 0.2), rng), 2000, 0.9) * 1.4
    return g.reverb(boom + rush + dust, rng, 1.0, 0.22, bright=3200)


def g_barrier_break(rng):
    """The barrier giving way: a sharp crack of air, a stumble of pressure, falling grit."""
    t = g.tt(0.9)
    crack = g.crack(t, rng, 0.0, 0.012, 1200) * 1.6
    sag = g.thump(t, 120, 30, 0.01, 0.25) * 0.9
    wobble = np.sin(g.phase(70 * np.exp(-t / 0.3) + 25, len(t))) * g.env_ad(t, 0.01, 0.3) * 0.4
    grit = g.bp(g.crackle(len(t), 700 * np.exp(-t / 0.25), rng), 2400, 0.9) * 1.2
    return g.reverb(crack + sag + wobble + grit, rng, 0.9, 0.2, bright=3500)


# ---------------------------------------------------------------- choke

def g_choke_start(rng):
    """A tight, low pressure closing in: a sinking tone and a squeezed rush."""
    t = g.tt(0.8)
    tone = np.sin(g.phase(110 * np.exp(-t / 0.4) + 48, len(t))) * g.env_ad(t, 0.05, 0.35) * 0.8
    squeeze = g.sweep(g.noise(len(t), rng), 1600 * np.exp(-t / 0.2) + 250, "bp", 2.5, nb=12) * g.env_ad(t, 0.02, 0.25) * 1.2
    knot = g.thump(t, 80, 50, 0.03, 0.15, t0=0.12) * 0.5
    return g.reverb(tone + squeeze + knot, rng, 0.7, 0.16, bright=2600)


def g_choke_loop(rng):
    """Seamless: a strained creak of held pressure over a low, uneasy drone."""
    L = 2.0
    t = g.tt(L)
    n = len(t)
    drone = np.zeros(n)
    for f, a in [(62.0, 1.0), (63.0, 0.7), (93.0, 0.25)]:
        drone += a * np.sin(g.phase(np.full(n, f), n, loop=True))
    drone *= 0.8 + 0.2 * np.sin(2 * np.pi * 4 * t / L)
    creak = g.bp(g.crackle(n, 90, rng), 900, 3.0, loop=True) * (0.6 + 0.4 * np.sin(2 * np.pi * 3 * t / L + 0.4)) * 1.6
    strain = g.bp(g.noise(n, rng), 1500, 4.0, loop=True) * 0.35
    return drone * 0.4 + creak + strain


def g_choke_release(rng):
    """The grip opens: pressure drains away and the body drops with a soft thud."""
    t = g.tt(0.8)
    drain = g.sweep(g.noise(len(t), rng), 400 + 1400 * np.clip(t / 0.2, 0, 1), "bp", 1.2, nb=10) * g.env_ad(t, 0.03, 0.18) * 0.9
    drop = g.thump(t, 85, 40, 0.01, 0.12, t0=0.3) * 0.9
    dust = g.lp(g.noise(len(t), rng), 1200) * g.env_ad(t, 0.01, 0.15, t0=0.31) * 0.4
    return g.reverb(drain + drop + dust, rng, 0.6, 0.15)


def g_choke_crush(rng):
    """A closing fist: a hard crunch of pressure and a heavier drop."""
    t = g.tt(0.9)
    crunch = g.bp(g.crackle(len(t), 1600 * np.exp(-t / 0.05), rng), 1300, 0.8) * 2.4
    clamp = g.sat(g.thump(t, 110, 38, 0.006, 0.12), 1.6)
    drop = g.thump(t, 80, 36, 0.01, 0.15, t0=0.35) * 0.9
    return g.reverb(crunch + clamp + drop, rng, 0.8, 0.2, bright=3000)


FILES = {
    "force.speed.start": {"force/speed_start": g_speed_start},
    "force.speed.loop": {"force/speed_loop": g_speed_loop},
    "force.speed.end": {"force/speed_end": g_speed_end},
    "force.speed.burst": {"force/speed_burst": g_speed_burst},
    "force.barrier.start": {"force/barrier_start": g_barrier_start},
    "force.barrier.loop": {"force/barrier_loop": g_barrier_loop},
    "force.barrier.impact": {"force/barrier_impact": g_barrier_impact},
    "force.barrier.pulse": {"force/barrier_pulse": g_barrier_pulse},
    "force.barrier.break": {"force/barrier_break": g_barrier_break},
    "force.choke.start": {"force/choke_start": g_choke_start},
    "force.choke.loop": {"force/choke_loop": g_choke_loop},
    "force.choke.release": {"force/choke_release": g_choke_release},
    "force.choke.crush": {"force/choke_crush": g_choke_crush},
}
