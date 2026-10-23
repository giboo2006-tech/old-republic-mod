"""PLACEHOLDER sound recipes for the Milestone 7 blasters (MILESTONE_7.md 9.1 and 11): the scatter blaster and the
blaster carbine.

Owned by SD. Synthesised from scratch with the helpers in gen_sounds.py; nothing is sampled. File names are the
Phase 0 placeholder names (<group>/<rest>), so sounds.json and the subtitles do not change. Both belong to the
blaster family (blaster.fire, blaster.pistol_fire, blaster.repeater.fire: a fast falling chirp ringing in a
springy metal comb over a tight transient), each set apart from it:

  blaster.scatter.fire  a short, wide blast with a scatter of small zips: a cluster of four detuned chirps falling
                        together (2.5 kHz to 170 Hz) through a lower comb than the rifle's (430 Hz against 740
                        and 610 Hz) and a 700 Hz body, a broad burst of noise falling from bright to dark, a low
                        punch with its 260 Hz partner, then six small zips (one for each pellet: a bright, buzzy
                        chirp falling away from 3.8 to 5.8 kHz) scattered from 15 to 120 ms as the blast clears.
                        The widest spectrum of the blasters. About 0.65 s, well inside the 18-tick (0.9 s) rate.
  blaster.carbine.fire  one bolt of the burst, sharp and light: a crisp click, a fast chirp falling from 4.8 kHz
                        to 520 Hz (starting higher, falling faster and ending higher than the pistol's 3.6 kHz to
                        260 Hz), a short bright ring at 1.15 kHz and only a small knock beneath. The bolts of a
                        burst are 2 ticks (0.1 s) apart, so the chirp is over in about 60 ms and the whole bolt
                        in about 0.26 s; three of them read as a quick trill, not a smear.

    python3 tools/assets/gen_sounds.py --m7 --feature blaster_m7
"""
import gen_sounds as g

np = g.np

PELLETS = 6             # the scatter blaster's pellets per shot (MILESTONE_7.md 9.1)
ZIPS = (0.015, 0.12)    # seconds: when the pellets' zips are heard, as the blast clears


def chirp(t, f_hi, f_lo, tau, t0=0.0):
    """A blaster chirp from t0: a tone (with a little second harmonic) falling from f_hi to f_lo with time constant
    tau, silent before t0."""
    tr = np.maximum(t - t0, 0)
    f = f_lo + (f_hi - f_lo) * np.exp(-tr / tau)
    ph = g.phase(f, len(t))
    return (np.sin(ph) + 0.3 * np.sin(2 * ph)) * (t >= t0)


def zip_(t, rng, t0, f_hi, amp):
    """One pellet's small zip from t0: a bright, slightly buzzy chirp falling away from f_hi to a third of it in
    about 40 ms."""
    tr = np.maximum(t - t0, 0)
    f = f_hi * (0.33 + 0.67 * np.exp(-tr / 0.014))
    buzz = np.sin(g.phase(f, len(t))) + 0.5 * g.saw(g.phase(f, len(t)), f_hi, nh=4, roll=1.4)
    return buzz * g.env_ad(t, 0.001, 0.03, t0) * amp


def g_scatter_fire(rng):
    """A short, wide blast with a scatter of small zips."""
    t = g.tt(0.5)
    n = len(t)
    # The blast: detuned chirps falling together, ringing in a low, wide comb.
    cluster = sum(chirp(t, 2500 * d, 170 * d, 0.045 * d, t0) * a
                  for d, t0, a in ((1.0, 0.0, 1.0), (0.94, 0.004, 0.7), (1.07, 0.007, 0.6), (0.88, 0.011, 0.45)))
    cluster = cluster * g.env_ad(t, 0.002, 0.1)
    metal = g.comb(cluster, 1 / 430, 0.8) * 0.5 + g.bp(cluster, 1500, 4) * 0.4 + g.bp(cluster, 700, 2) * 0.6
    # Its width: a broad burst of noise falling from bright to dark, and a crack.
    burst = g.sweep(g.noise(n, rng), 400 + 2800 * np.exp(-t / 0.03), "bp", 0.7, nb=12) * g.env_ad(t, 0.002, 0.07) * 1.3
    crack = g.crack(t, rng, 0.0, 0.006, 1000) * 0.45
    punch = g.thump(t, 130, 50, 0.03, 0.07) * 0.6 + g.bp(g.noise(n, rng), 260.0, 0.9) * g.env_ad(t, 0.002, 0.06) * 1.6
    # The pellets: one small zip each, scattered over ZIPS.
    starts = np.sort(rng.uniform(ZIPS[0], ZIPS[1], PELLETS))
    zips = sum(zip_(t, rng, t0, rng.uniform(3800, 5800), rng.uniform(0.5, 0.75)) for t0 in starts)
    x = cluster * 0.5 + metal + burst + crack + punch + zips
    return g.reverb(x, rng, 0.55, 0.16, bright=5500)


def g_carbine_fire(rng):
    """One bolt of the burst: a crisp click, a fast high chirp, a short bright ring and a small knock."""
    t = g.tt(0.22)
    n = len(t)
    c = chirp(t, 4800, 520, 0.015) + 0.45 * chirp(t, 6200, 1000, 0.012, 0.002)
    c = c * g.env_ad(t, 0.001, 0.028)
    metal = g.comb(c, 1 / 1150, 0.65) * 0.5 + g.bp(c, 3300, 7) * 0.7
    click = g.crack(t, rng, 0.0, 0.003, 2200) * 0.4
    knock = (g.thump(t, 230, 120, 0.012, 0.025) * 0.22
             + g.bp(g.noise(n, rng), 500.0, 1.5) * g.env_ad(t, 0.001, 0.015) * 0.3)
    return g.reverb(c + metal + click + knock, rng, 0.28, 0.09, bright=6500)


FILES = {
    "blaster.scatter.fire": {"blaster/scatter_fire": g_scatter_fire},
    "blaster.carbine.fire": {"blaster/carbine_fire": g_carbine_fire},
}
