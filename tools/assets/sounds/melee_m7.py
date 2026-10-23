"""PLACEHOLDER sound recipes for the Milestone 7 melee weapons (MILESTONE_7.md 9.1 and 11): the vibro-axe and the
force pike.

Owned by SD. Synthesised from scratch with the helpers in gen_sounds.py; nothing is sampled. File names are the
Phase 0 placeholder names (<group>/<rest>), so sounds.json and the subtitles do not change. They follow the vibro
family (vibroblade.swing, vibro.cleave: a whoosh with the edge's motor buzz) and the electrostaff's electric one
(electrostaff.charge, electrostaff.shock), each set apart from them:

  vibro_axe.swing    a heavy whoosh: a broad, deep rush of air peaking as the head passes (0.3 s), a low push
                     under it, and the axe's vibro edge (a 150 Hz buzz, lower and rougher than the vibroblade's
                     240 Hz) bent up as it comes and down as it goes. No thud: the hit has its own sounds.
  vibro_axe.sunder   a guard broken by the axe (Sunder): a hard, splintering crack over a heavy blow, the guard's
                     metal ringing out, the edge's buzz overdriven and dragged down, and debris skittering away.
                     Physical and loud, unlike the saber's own guard break (a sizzle and a diving hum).
  force_pike.charge  a rising buzz over the 30-tick charge (1.5 s): a pulse-wave buzz climbing from 80 to 210 Hz
                     through a resonance that opens from 500 Hz to 2.8 kHz, a thin whine above it and crackle
                     growing denser, ending on a small snap as the head is charged (at 1.5 s).
  force_pike.stun    an electric snap: a hard crack, a fast falling zap, a dense burst of sparks and a short,
                     stuttering buzz (the stun field) dying over 0.4 s, with a small knock of the hit.

    python3 tools/assets/gen_sounds.py --m7 --feature melee_m7
"""
import gen_sounds as g

np = g.np

AXE_F = 150.0      # the axe's vibro edge (the vibroblade's is 240 Hz)
PIKE_CHARGE = 1.5  # seconds: the stun thrust's 30-tick charge


def settle_end(x, fade=0.1):
    """x with its last fade seconds eased to silence (a raised cosine), so nothing is cut off before the reverb."""
    m = min(len(x), g.ns(fade))
    x = np.array(x, float)
    if m > 1:
        x[-m:] *= 0.5 + 0.5 * np.cos(np.linspace(0, np.pi, m))
    return x


def edge(t, f, rng):
    """The vibro edge's buzz on the pitch curve f, at unit RMS: a rough saw (a little jitter in its pitch) through the
    blade's two resonances (600 Hz and 1.4 kHz)."""
    n = len(t)
    jitter = g.lp(g.noise(n, rng), 30.0)
    jitter = 1 + 0.01 * jitter / (np.std(jitter) + 1e-9)
    raw = g.saw(g.phase(f * jitter, n), float(np.max(f)), nh=60)
    y = g.bp(raw, 1400.0, 1.4) + 0.6 * g.bp(raw, 600.0, 1.2)
    return y / (np.sqrt(np.mean(y ** 2)) + 1e-12)


def g_axe_swing(rng):
    """A heavy whoosh with the axe's low buzz passing."""
    t = g.tt(0.8)
    n = len(t)
    tp = 0.3
    b = g.bell(t, tp, 0.12, 0.16)
    air = g.whoosh(t, rng, 260 + 950 * b, 1.1, b) * 1.3
    push = g.lp(g.noise(n, rng), 300.0) * g.bell(t, tp, 0.1, 0.13) * 2.2
    f = AXE_F * g.doppler(t, tp, 0.06, 0.09, 0.07, 0.09)
    buzz = edge(t, f, rng) * b ** 1.5 * 0.3
    tear = g.hp(g.noise(n, rng), 3000) * g.bell(t, tp, 0.025, 0.05) * 0.12
    return g.reverb(settle_end(air + push + buzz + tear), rng, 0.45, 0.1)


def g_axe_sunder(rng):
    """A guard sundered: crack, blow, ring, the buzz dragged down and debris."""
    t = g.tt(1.3)
    n = len(t)
    crack = (g.crack(t, rng, 0.0, 0.006, 600) * 1.0
             + g.bp(g.noise(n, rng), 1300.0, 1.0) * g.env_ad(t, 0.0005, 0.02) * 1.6)
    crunch = g.bp(g.noise(n, rng), 850.0, 1.3) * g.env_ad(t, 0.002, 0.06) * 1.2
    splinter = g.bp(g.crackle(n, 2600 * np.exp(-t / 0.09), rng), 2600.0, 0.8) * 2.6
    blow = g.sat(g.thump(t, 110, 45, 0.05, 0.18) * 1.2, 1.8) * 0.45
    ring = g.metal_ring(t, rng, 520.0, 6, 0.45) * 0.55 + g.metal_ring(t, rng, 1930.0, 4, 0.12) * 0.25
    sag = 1 - 0.3 * np.clip(t / 0.35, 0, 1)
    buzz = g.sat(edge(t, AXE_F * sag, rng) * 0.4, 2.5) * g.env_ad(t, 0.002, 0.2) * 0.35
    collapse = g.sweep(g.noise(n, rng), 300 + 1700 * np.exp(-t / 0.12), "bp", 1.2) * g.env_ad(t, 0.004, 0.16) * 0.6
    debris = sum(g.metal_ring(t, rng, rng.uniform(1600, 3800), 3, 0.03, rng.uniform(0.12, 0.6))
                 * rng.uniform(0.06, 0.16) for _ in range(7))
    x = crack + crunch + splinter + blow + ring + buzz + collapse + debris
    return g.reverb(settle_end(x), rng, 0.9, 0.2, bright=5000)


def g_pike_charge(rng):
    """A rising buzz over the charge, a whine above it and crackle growing denser, ending on a small snap."""
    t = g.tt(PIKE_CHARGE + 0.15)
    n = len(t)
    u = np.clip(t / PIKE_CHARGE, 0, 1)
    f = 80 * (210 / 80) ** (u ** 1.3)
    buzz = g.pulse(g.phase(f, n), float(np.max(f)), w=0.22, nh=60)
    body = g.sweep(buzz, 500 * (2800 / 500) ** u, "bp", 2.2, nb=14) * 1.4 + g.bp(buzz, 300.0, 1.0) * 0.25
    whine = np.sin(g.phase(f * 11.0, n)) * u ** 2 * 0.06
    crackle = g.lp(g.hp(g.crackle(n, 15 + 700 * u ** 2.2, rng), 3200), 8000) * (0.25 + 0.75 * u)
    amp = (0.2 + 0.8 * u ** 0.9) * np.clip((PIKE_CHARGE + 0.012 - t) / 0.012, 0, 1)
    snap = g.crack(t, rng, PIKE_CHARGE, 0.006, 2500) * 0.22 + g.sizzle(t, rng, 2500, PIKE_CHARGE, 0.03) * 0.8
    x = (body + whine + crackle * 0.35) * amp + snap
    return g.reverb(settle_end(x, 0.05), rng, 0.45, 0.12, bright=7000)


def g_pike_stun(rng):
    """An electric snap: crack, falling zap, sparks and a stuttering buzz that dies away."""
    t = g.tt(0.9)
    n = len(t)
    crack = g.crack(t, rng, 0.0, 0.004, 2000) * 0.4
    fz = 380 + 3400 * np.exp(-t / 0.012)
    zap = g.saw(g.phase(fz, n), 3800, nh=10) * g.env_ad(t, 0.0005, 0.04) * 1.0
    sparks = g.sizzle(t, rng, 4500, 0.002, 0.06, lo=3000.0, hi=10000.0) * 0.9
    stutter = np.clip(np.cos(g.phase(np.full(n, 14.0), n)), 0, 1) ** 0.5
    field = g.bp(g.pulse(g.phase(np.full(n, 118.0), n), 118.0, w=0.3, nh=60), 1700.0, 1.0)
    field = field * (0.35 + 0.65 * stutter) * g.env_ad(t, 0.006, 0.2) * 2.6
    knock = g.thump(t, 160, 70, 0.012, 0.05, t0=0.004) * 0.45
    return g.reverb(settle_end(crack + zap + sparks + field + knock), rng, 0.6, 0.18, bright=7000)


FILES = {
    "vibro_axe.swing": {"vibro_axe/swing": g_axe_swing},
    "vibro_axe.sunder": {"vibro_axe/sunder": g_axe_sunder},
    "force_pike.charge": {"force_pike/charge": g_pike_charge},
    "force_pike.stun": {"force_pike/stun": g_pike_stun},
}
