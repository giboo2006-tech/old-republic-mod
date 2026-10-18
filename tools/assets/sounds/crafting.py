"""PLACEHOLDER sound recipes for the crafting feature (Milestone 4, W5): the Engineering Bench
(mod fit and removal, the weld loop, the craft finishing, a masterwork, salvage), the medpac,
kolto injector and stims, the scanner, and the player's grenades.

Synthesised from scratch with the helpers in gen_sounds.py; nothing is sampled. The file names
are the placeholder names gen_sounds gave these ids in Phase 0 (<group>/<rest of the id>), so
sounds.json does not change. See events_m4.json for the event list and docs/assets/crafting.md
for the intent of each sound.
"""
import gen_sounds as g

np = g.np


def g_mod_fit(rng):
    """A mod seats: a sliding scrape, a firm double click, a short metal ring."""
    t = g.tt(0.45)
    slide = g.bp(g.noise(len(t), rng), 3200, 3) * g.env_ad(t, 0.01, 0.06) * 0.25
    x = slide + g.click(t, rng, 0.07) + g.click(t, rng, 0.1, 0.8) * 0.8
    x += g.metal_ring(t, rng, 2400, 5, 0.08, 0.1) * 0.2
    return g.reverb(x, rng, 0.35, 0.12)


def g_mod_remove(rng):
    """A mod comes out: the latch releases (a lower click) and the part slides free."""
    t = g.tt(0.45)
    x = g.click(t, rng, 0.01, 0.7) * 0.9
    slide = g.bp(g.noise(len(t), rng), 2400, 3) * g.env_ad(t, 0.02, 0.1, 0.06) * 0.3
    x += slide + g.metal_ring(t, rng, 1700, 4, 0.06, 0.03) * 0.12
    return g.reverb(x, rng, 0.35, 0.12)


def g_weld(rng):
    """Seamless 2 s welding loop: an arc's crackle over a mains buzz and a hiss."""
    L = 2.0
    t = g.tt(L)
    n = len(t)
    crack = g.crackle(n, 90, rng, loop=True)
    crack = g.bp(crack, 3800, 1.2, loop=True) * 0.9
    hiss = g.bp(g.noise(n, rng), 6000, 0.8, loop=True) * 0.35
    flutter = 0.75 + 0.25 * np.sin(2 * np.pi * 3 * t + rng.uniform(0, 6))
    buzz = (np.sign(np.sin(2 * np.pi * 120 * t)) * 0.5 + np.sin(2 * np.pi * 240 * t) * 0.3)
    buzz = g.lp(buzz, 900, loop=True) * 0.18
    return (crack + hiss) * flutter + buzz


def g_done(rng):
    """The craft finishes: the arc cuts out with a hiss, two clicks, and a two-note ready chime."""
    t = g.tt(1.2)
    hiss = g.bp(g.noise(len(t), rng), 5000, 1.0) * g.env_ad(t, 0.005, 0.25) * 0.35
    x = hiss + g.click(t, rng, 0.25) * 0.8 + g.click(t, rng, 0.31, 0.9) * 0.6
    for t0, f in ((0.42, 880.0), (0.58, 1318.5)):
        x += g.bell_partials(len(t), f, [1, 2.0, 3.01], [0.35, 0.18, 0.1], [1, 0.3, 0.12], rng, t0=t0) * 0.35
    return g.reverb(x, rng, 0.7, 0.18)


def g_masterwork(rng):
    """A masterwork: a bright rising triad of bells over a soft shimmer."""
    t = g.tt(2.2)
    x = np.zeros(len(t))
    for t0, f in ((0.0, 659.3), (0.12, 830.6), (0.24, 987.8), (0.42, 1318.5)):
        x += g.bell_partials(len(t), f, [1, 2.0, 2.76, 4.07], [0.9, 0.5, 0.3, 0.2], [1, 0.4, 0.2, 0.1], rng, t0=t0) * 0.3
    shimmer = g.bp(g.noise(len(t), rng), 7000, 2.0) * g.bell(t, 0.5, 0.3, 1.0) * 0.12
    return g.reverb(x + shimmer, rng, 1.6, 0.3)


def g_salvage(rng):
    """Parts come apart: a ratchet, then a clatter of plates and screws onto the bench."""
    t = g.tt(1.1)
    x = np.zeros(len(t))
    for k in range(6):
        x += g.click(t, rng, 0.02 + k * 0.03, 1.2) * 0.4
    for k in range(7):
        t0 = 0.25 + k * rng.uniform(0.05, 0.11)
        x += g.metal_ring(t, rng, rng.uniform(900, 2600), 5, rng.uniform(0.05, 0.12), t0) * rng.uniform(0.25, 0.5)
        x += g.thump(t, 220, 120, 0.004, 0.04, t0=t0) * 0.25
    return g.reverb(x, rng, 0.5, 0.15)


def g_medpac(rng):
    """A medpac: the seal tears, a cold spray, and a soft confirm tone."""
    t = g.tt(1.0)
    tear = g.bp(g.crackle(len(t), 400, rng), 2600, 1.5) * g.env_ad(t, 0.005, 0.12) * 0.6
    spray = g.bp(g.noise(len(t), rng), 4500, 0.9) * g.bell(t, 0.3, 0.06, 0.25) * 0.45
    tone = np.sin(2 * np.pi * 1046.5 * t) * g.env_ad(t, 0.01, 0.2, 0.62) * 0.18
    return g.reverb(tear + spray + tone, rng, 0.5, 0.15)


def g_kolto(rng):
    """A kolto injector: a pneumatic hiss and a few liquid bubbles."""
    t = g.tt(0.9)
    hiss = g.bp(g.noise(len(t), rng), 3800, 1.2) * g.env_ad(t, 0.003, 0.18, 0.02) * 0.6
    x = hiss + g.click(t, rng, 0.0, 1.1) * 0.6
    for k in range(6):
        t0 = 0.22 + k * rng.uniform(0.05, 0.09)
        f0 = rng.uniform(500, 900)
        f = f0 * (1 + 0.8 * np.clip((t - t0) / 0.04, 0, 1))
        x += np.sin(2 * np.pi * np.cumsum(f) / g.SR) * g.env_ad(t, 0.002, 0.03, t0) * 0.18
    return g.reverb(x, rng, 0.45, 0.15)


def g_stim(rng):
    """A stim: a sharp pneumatic snap, a short hiss, then two quick heartbeats."""
    t = g.tt(1.0)
    x = g.click(t, rng, 0.0, 1.3) + g.bp(g.noise(len(t), rng), 5200, 1.4) * g.env_ad(t, 0.002, 0.08) * 0.5
    for t0 in (0.35, 0.52):
        x += g.thump(t, 90, 45, 0.004, 0.09, t0=t0) * 0.8
    return g.reverb(x, rng, 0.4, 0.12)


def g_stim_crash(rng):
    """A stim wears off: a falling tone and slowing, softer heartbeats."""
    t = g.tt(1.6)
    f = 440 * (1 - 0.45 * np.clip(t / 1.2, 0, 1))
    tone = np.sin(2 * np.pi * np.cumsum(f) / g.SR) * g.env_ad(t, 0.05, 0.9) * 0.2
    x = tone
    for k, t0 in enumerate((0.2, 0.55, 1.0)):
        x += g.thump(t, 80, 40, 0.004, 0.1, t0=t0) * (0.7 - k * 0.18)
    return g.reverb(x, rng, 0.6, 0.15)


def g_scanner_pulse(rng):
    """The scanner sends a pulse: a rising sweep that rings outward and echoes."""
    t = g.tt(1.4)
    f = 600 + 1800 * np.clip(t / 0.18, 0, 1)
    sweep = np.sin(2 * np.pi * np.cumsum(f) / g.SR) * g.env_ad(t, 0.01, 0.25) * 0.5
    ring = g.bell_partials(len(t), 2400, [1, 1.5, 2.0], [0.5, 0.3, 0.2], [1, 0.3, 0.15], rng, t0=0.18) * 0.2
    return g.reverb(sweep + ring, rng, 1.2, 0.35)


def g_scanner_ping(rng):
    """The scanner marks a find: a short double beep."""
    t = g.tt(0.4)
    x = np.zeros(len(t))
    for t0, f in ((0.0, 1760.0), (0.11, 2093.0)):
        x += np.sin(2 * np.pi * f * t) * g.env_ad(t, 0.004, 0.06, t0) * 0.4
    return g.reverb(x, rng, 0.3, 0.12)


def g_grenade_throw(rng):
    """A grenade leaves the hand: the pin's click and a short whoosh."""
    t = g.tt(0.6)
    b = g.bell(t, 0.2, 0.1, 0.2)
    x = g.click(t, rng, 0.0, 1.4) * 0.8 + g.whoosh(t, rng, 500 + 1200 * b, 1.4, b) * 0.9
    return g.reverb(x, rng, 0.35, 0.1)


def g_grenade_stun(rng):
    """A stun grenade bursts: a sharp crack, an electric crackle that spreads, and a high ring."""
    t = g.tt(1.4)
    x = g.crack(t, rng, 0.0, 0.012, 1200) * 1.2 + g.thump(t, 140, 50, 0.003, 0.12) * 0.7
    zap = g.bp(g.crackle(len(t), 300, rng), 4200, 1.2) * g.env_ad(t, 0.005, 0.35, 0.01) * 0.8
    ring = np.sin(2 * np.pi * 3520 * t) * g.env_ad(t, 0.002, 0.6, 0.02) * 0.08
    return g.reverb(x + zap + ring, rng, 0.9, 0.25)


FILES = {
    "bench.mod_fit": {"bench/mod_fit": g_mod_fit},
    "bench.mod_remove": {"bench/mod_remove": g_mod_remove},
    "bench.weld": {"bench/weld": g_weld},
    "bench.done": {"bench/done": g_done},
    "bench.masterwork": {"bench/masterwork": g_masterwork},
    "bench.salvage": {"bench/salvage": g_salvage},
    "medpac.use": {"medpac/use": g_medpac},
    "kolto.inject": {"kolto/inject": g_kolto},
    "stim.inject": {"stim/inject": g_stim},
    "stim.crash": {"stim/crash": g_stim_crash},
    "scanner.pulse": {"scanner/pulse": g_scanner_pulse},
    "scanner.ping": {"scanner/ping": g_scanner_ping},
    "grenade.throw": {"grenade/throw": g_grenade_throw},
    "grenade.stun": {"grenade/stun": g_grenade_stun},
}
