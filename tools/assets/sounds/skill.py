"""PLACEHOLDER sound recipes for the skills feature (Force screen).

Owned by the skills worker. Synthesised from scratch with the helpers in
gen_sounds.py; see events_m2.json for the event list.
"""
import gen_sounds as g

np = g.np


def g_learn(rng):
    """A crystal click with a rising swell."""
    t = g.tt(1.4)
    clk = g.click(t, rng, 0.005, 1.3)
    swell = sum(np.sin(2 * np.pi * f * t) * a for f, a in [(392, 1), (588, 0.5), (784, 0.3)])
    swell *= np.clip(t / 0.5, 0, 1) ** 2 * np.exp(-np.maximum(t - 0.5, 0) / 0.3) * 0.4
    chime = g.make_chime(1568.0, 0.5)(rng)[: len(t)]
    out = clk + swell
    out[: len(chime)] += chime * 0.4
    return g.reverb(out, rng, 1.4, 0.3, bright=7000)


def g_slot(rng):
    t = g.tt(0.3)
    return g.reverb(g.click(t, rng, 0.004, 0.9) + g.click(t, rng, 0.05, 1.1) * 0.6, rng, 0.35, 0.1)


def g_respec(rng):
    """An unwinding: falling chimes over a fading pad."""
    t = g.tt(1.8)
    x = np.zeros(len(t))
    for i in range(10):
        t0 = 0.05 + i * 0.09
        x += g.bell_partials(len(t), 1400 * 0.92 ** i, g.CHIME_RATIOS[:3], [0.3, 0.15, 0.08], [1, 0.3, 0.15], rng, t0=t0) * 0.3
    pad = np.sin(2 * np.pi * 196 * t * (1 - 0.1 * t / 1.8)) * g.env_ad(t, 0.05, 0.6) * 0.3
    return g.reverb(x + pad, rng, 1.8, 0.35, bright=6000)


FILES = {
    "skill.learn": {"skill/learn": g_learn},
    "skill.slot": {"skill/slot": g_slot},
    "skill.respec": {"skill/respec": g_respec},
}
