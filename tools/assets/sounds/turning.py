"""PLACEHOLDER sound recipes for the turning and rank 4 (Milestone 5, section 8).

Owned by W5 (paths and ranks). Synthesised from scratch with the helpers in gen_sounds.py (oscillators, filtered
noise, bell partials, simple reverb); nothing is sampled or imitated from any recording or score. See events_m5.json for
the event list and docs/MILESTONE_5.md 15.3 and 15.4 for each moment. File names are the placeholder names
(<group>/<rest of the id with dots as underscores>), so sounds.json and the subtitles do not change.

    python3 tools/assets/gen_sounds.py --m5 --feature turning
"""
import gen_sounds as g

np = g.np


# ---------------------------------------------------------------- the offer (turning 0)

def g_turning_offer(rng):
    """The offer arrives (about 2.6 s): a low, slightly uneasy drone breathing in under the messenger's words, two soft
    bell notes a minor sixth apart that rise and hang without resolving (a question, not an answer), and a thin air
    swell behind them. Quiet and personal; the same for the Fall and the Return."""
    t = g.tt(2.6)
    n = len(t)
    f0 = 73.42
    drone_f = f0 * (1 + 0.004 * np.sin(2 * np.pi * 0.7 * t))
    drone = g.saw(g.phase(drone_f, n), f0, nh=10, roll=1.5) + 0.5 * np.sin(g.phase(drone_f * 1.5, n))
    drone = g.lp(drone, 420) * g.bell(t, 1.1, 0.6, 1.0) * 0.45
    bells = (g.bell_partials(n, 293.66, [1, 2.0, 2.92], [1.1, 0.5, 0.25], [1.0, 0.35, 0.18], rng, t0=0.25, attack=0.02)
             + g.bell_partials(n, 466.16, [1, 2.0, 2.92], [1.6, 0.6, 0.3], [0.85, 0.3, 0.15], rng, t0=0.85, attack=0.03))
    air = g.sweep(g.noise(n, rng), 900 + 1600 * g.bell(t, 1.2, 0.5, 0.9), "bp", q=1.2, nb=10) * g.bell(t, 1.2, 0.5, 0.9) * 0.12
    return g.reverb(drone + bells * 0.5 + air, rng, 2.0, 0.4, bright=4200)


# ---------------------------------------------------------------- the rite (lesson_complete 12)

def g_turning_rite(rng):
    """The rite is done (about 3.4 s): a deep struck gong and a low thump, then a slow pad whose chord turns on a
    pivot: two of its notes hold while the third steps up a half tone, so the same ground resolves into a different
    key (the path changes, the person does not). One low bell closes it on the new root."""
    t = g.tt(3.4)
    n = len(t)
    floor = g.thump(t, 70, 38, 0.12, 0.9, t0=0.02) * 0.7
    gong = g.metal_ring(t, rng, 110.0, 7, 1.6, t0=0.02) * 0.45
    # A minor (A, C, E) turning to F major (A, C, F): E steps up to F at 1.3 s, gliding.
    step = g.lp(np.where(t < 1.3, 329.63, 349.23), 6.0)
    pad = np.zeros(n)

    for f, a in ((220.0, 0.5), (261.63, 0.42), (None, 0.4)):
        freq = step if f is None else np.full(n, f)
        pad += a * (np.sin(g.phase(freq, n)) + 0.3 * np.sin(g.phase(freq * 2.0, n)) + 0.12 * np.sin(g.phase(freq * 3.0, n)))

    pad = g.lp(pad, 1800) * np.clip((t - 0.25) / 0.6, 0, 1) * np.exp(-np.maximum(t - 2.2, 0) / 0.8) * 0.55
    close = g.bell_partials(n, 174.61, [1, 2.0, 2.76, 4.07], [1.4, 0.7, 0.4, 0.2], [1.0, 0.4, 0.22, 0.1], rng, t0=2.05, attack=0.01)
    return g.reverb(floor + gong + pad + close * 0.4, rng, 2.8, 0.4, bright=4500)


# ---------------------------------------------------------------- rank 4 (rank_four, lesson_complete 13)

def g_rank_four(rng):
    """A new rank is bestowed (about 3.8 s): a low brass-like swell opening as it grows, a hummed vowel pad above it,
    and three bells climbing root, fifth and octave that settle on an open fifth, with a high chime as the name is
    given. Broader and brighter than the rank 3 sting: the last rank."""
    t = g.tt(3.8)
    n = len(t)
    f0 = 110.0
    brass_f = np.full(n, f0)
    brass = g.saw(g.phase(brass_f, n), f0, nh=16, roll=1.2) + 0.65 * g.saw(g.phase(brass_f * 1.4983, n), f0 * 1.5, nh=12, roll=1.2)
    brass = g.sweep(brass, 280 + 1300 * g.bell(t, 1.1, 0.5, 1.3), "lp", nb=10) * g.bell(t, 1.1, 0.45, 1.3) * 0.5
    # A hummed "oh" over the swell: a soft saw through two vowel formants.
    hum_f = 220.0 * (1 + 0.003 * np.sin(2 * np.pi * 4.8 * t))
    hum = g.saw(g.phase(hum_f, n), 220.0, nh=24, roll=1.0)
    hum = (g.bp(hum, 430, 6.0) + 0.6 * g.bp(hum, 820, 7.0)) * g.bell(t, 1.4, 0.6, 1.2) * 0.5
    bells = (g.bell_partials(n, 440.0, [1, 2.0, 2.76], [1.2, 0.5, 0.25], [1.0, 0.4, 0.2], rng, t0=0.45, attack=0.005)
             + g.bell_partials(n, 659.26, [1, 2.0, 2.76], [1.2, 0.5, 0.25], [0.9, 0.35, 0.18], rng, t0=0.85, attack=0.005)
             + g.bell_partials(n, 880.0, [1, 2.0, 2.76], [1.8, 0.7, 0.3], [1.0, 0.4, 0.2], rng, t0=1.3, attack=0.005))
    chime = g.bell_partials(n, 1318.5, g.CHIME_RATIOS[:3], [1.2, 0.5, 0.25], [0.5, 0.2, 0.08], rng, t0=1.75, attack=0.004)
    floor = g.thump(t, 85, 45, 0.1, 0.6, t0=0.03) * 0.45
    return g.reverb(brass + hum + bells * 0.5 + chime + floor, rng, 2.6, 0.38, bright=5500)


FILES = {
    "story.turning.offer": {"story/turning_offer": g_turning_offer},
    "story.turning.rite": {"story/turning_rite": g_turning_rite},
    "story.rank_four": {"story/rank_four": g_rank_four},
}
