"""PLACEHOLDER sound recipes for the Forge (MILESTONE_7.md 5.2 and 11): forging or re-forging a hilt part, and
studying one.

Owned by SD. Synthesised from scratch with the helpers in gen_sounds.py; nothing is sampled. File names are the
Phase 0 placeholder names (<group>/<rest>), so sounds.json and the subtitles do not change.

  workbench.forge  a hammer on hot metal with a short hiss: two blows (the first dull, on the soft hot billet;
                   the second harder, so the anvil rings out under it), then the part quenched in a short hiss
                   of steam with a few spits. Plays with sparks at the bench on Forge and Re-forge.
  workbench.study  a soft scanner sweep and click: a quiet tone sweeping up across the part and back with a
                   light flutter, a breath of filtered noise following it, then a soft click as the design is
                   recorded and a tiny confirming blip. Played in the bench screen, so it stays soft.

    python3 tools/assets/gen_sounds.py --m7 --feature forge_m7
"""
import gen_sounds as g

np = g.np

# A struck anvil's ring: the modes of a free steel bar (1, 2.76, 5.40, 8.93 times the lowest).
ANVIL = [1.0, 2.76, 5.40, 8.93]


def settle_end(x, fade=0.12):
    """x with its last fade seconds eased to silence (a raised cosine), so nothing is cut off before the reverb."""
    m = min(len(x), g.ns(fade))
    x = np.array(x, float)
    if m > 1:
        x[-m:] *= 0.5 + 0.5 * np.cos(np.linspace(0, np.pi, m))
    return x


def blow(t, rng, t0, power=1.0, ring=0.3, f_anvil=1180.0):
    """One hammer blow on hot metal at t0: the face's sharp crack, the hammer's weight (a low knock), the hot billet's
    dull, damped thunk (soft metal barely rings) and, at level ring, the anvil ringing under it (ANVIL modes on
    f_anvil, the high ones dying first)."""
    n = len(t)
    crack = g.crack(t, rng, t0, 0.004, 1400) * 0.45
    knock = g.thump(t, 230, 120, 0.006, 0.04, t0=t0) * 0.8
    thunk = g.bp(g.noise(n, rng), 620.0, 1.6) * g.env_ad(t, 0.0008, 0.03, t0) * 1.6
    bar = g.bell_partials(n, f_anvil, ANVIL, [0.55, 0.28, 0.12, 0.06], [1.0, 0.6, 0.35, 0.15], rng, t0=t0,
                          attack=0.0006)
    return (crack + knock + thunk + bar * ring) * power


def steam(t, rng, t0, dur, amp=1.0):
    """A short hiss of steam from t0: bright band noise that bursts, flutters and falls away over about dur seconds,
    with a few spits of boiling water in it."""
    n = len(t)
    tr = t - t0
    env = np.where(tr < 0, 0.0, np.minimum(1, np.maximum(tr, 0) / 0.012) * np.exp(-np.maximum(tr, 0) / (dur * 0.45)))
    wobble = g.lp(g.noise(n, rng), 40.0, order=4)
    flutter = 0.7 + 0.3 * wobble / (np.max(np.abs(wobble)) + 1e-9)
    fc = 5200 - 1600 * np.clip(tr / dur, 0, 1)
    hiss = g.sweep(g.noise(n, rng), fc, "bp", 1.2, nb=10) + 0.3 * g.hp(g.noise(n, rng), 7000)
    spits = g.bp(g.crackle(n, 320 * env, rng), 2800.0, 1.0) * 1.6
    return (hiss * flutter + spits) * env * amp


def g_forge(rng):
    """A hammer on hot metal with a short hiss: a dull blow, a harder ringing one, and the quench."""
    t = g.tt(1.55)
    x = blow(t, rng, 0.015, power=0.85, ring=0.22, f_anvil=1180.0)
    x += blow(t, rng, 0.35, power=1.0, ring=0.5, f_anvil=1195.0)
    x += steam(t, rng, 0.62, 0.55, amp=0.55)
    return g.reverb(settle_end(x), rng, 0.7, 0.16, bright=6000)


def g_study(rng):
    """A soft scanner sweep and click: the tone sweeps up across the part and back (with a light flutter and a breath
    of noise following it), then a soft click as the design is recorded and a tiny blip."""
    t = g.tt(1.1)
    n = len(t)
    span = 0.6
    u = np.clip(t / span, 0, 1)
    f = 880 + 820 * np.sin(np.pi * u) ** 1.3
    env = np.sin(np.pi * u) ** 0.9 * (t < span)
    flutter = 1 + 0.2 * np.sin(2 * np.pi * 21 * t)
    tone = (np.sin(g.phase(f, n)) + 0.22 * np.sin(g.phase(2 * f, n))) * env * flutter * 0.1
    breath = g.sweep(g.noise(n, rng), f * 2.3, "bp", 3.5, nb=12) * env * 0.12
    clk = g.click(t, rng, 0.64, 1.05) * 3.0 + g.click(t, rng, 0.655, 0.8) * 1.2
    blip = np.sin(2 * np.pi * 2093.0 * t) * g.env_ad(t, 0.003, 0.045, 0.69) * 0.12
    return g.reverb(settle_end(tone + breath + clk + blip), rng, 0.6, 0.2, bright=7000)


FILES = {
    "workbench.forge": {"workbench/forge": g_forge},
    "workbench.study": {"workbench/study": g_study},
}
