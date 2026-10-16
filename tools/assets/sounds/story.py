"""PLACEHOLDER sound recipes for the story feature (Chapter Two).

Owned by the quest worker. Synthesised from scratch with the helpers in
gen_sounds.py; see events_m2.json for the event list.
"""
import gen_sounds as g

np = g.np


def g_two_voices(rng):
    """Seamless 6 s bed: a warm pad and a cold, hollow drone that trade places."""
    L = 6.0
    t = g.tt(L)
    n = len(t)
    warm = sum(a * np.sin(g.phase(np.full(n, f), n, loop=True)) for f, a in [(220, 0.6), (330, 0.4), (440.5, 0.2)])
    cold = g.lp(g.saw(g.phase(np.full(n, 73.5), n, loop=True), 74, nh=20), 400, loop=True)
    trade = 0.5 + 0.5 * np.sin(2 * np.pi * t / L)
    air = g.lp(g.noise(n, rng), 300, loop=True) * 2.0
    return g.reverb(warm * trade + cold * 0.6 * (1 - trade) + air, rng, 2.5, 0.35, loop=True, bright=3000)


def g_lesson_complete(rng):
    t = g.tt(2.0)
    a = g.bell_partials(len(t), 587.33, [1, 2.0, 2.76], [1.0, 0.5, 0.3], [1, 0.25, 0.15], rng, attack=0.006)
    b = g.bell_partials(len(t), 880.0, [1, 2.0, 2.76], [1.2, 0.5, 0.3], [0.8, 0.2, 0.1], rng, t0=0.16, attack=0.006)
    return g.reverb(a + b, rng, 2.0, 0.35, bright=6000)


def g_duel_begin(rng):
    t = g.tt(1.4)
    hit = g.thump(t, 70, 38, 0.1, 0.4) * 1.2
    ring = g.metal_ring(t, rng, 980, 6, 0.5, 0.02) * 0.4
    return g.reverb(hit + ring, rng, 1.4, 0.3, bright=3500)


def g_duel_end(rng):
    t = g.tt(1.6)
    ring = g.metal_ring(t, rng, 740, 6, 0.7) * 0.5
    fall = g.sweep(g.noise(len(t), rng), 1500 * np.exp(-t / 0.5) + 200, "lp", nb=10) * g.env_ad(t, 0.02, 0.4) * 0.8
    return g.reverb(ring + fall, rng, 1.6, 0.3, bright=4000)


FILES = {
    "story.two_voices": {"story/two_voices": g_two_voices},
    "story.lesson_complete": {"story/lesson_complete": g_lesson_complete},
    "story.duel_begin": {"story/duel_begin": g_duel_begin},
    "story.duel_end": {"story/duel_end": g_duel_end},
}
