"""PLACEHOLDER sound recipes for the saber feature (combo, heavy, lunge, forms, throw, and the
milestone 3 singing-crystal hum layer).

Owned by the saber worker. Synthesised from scratch with the helpers in
gen_sounds.py (reusing its saber tone); see events_m2.json and events_m3.json for the
event lists.
"""
import gen_sounds as g

np = g.np
F = g.SABER_F


def g_finisher(rng):
    return g.make_swing(dict(dur=0.8, tp=0.34, f0=84, up=0.24, down=0.16, w1=0.14, w2=0.16, wh=1500))(rng)


def g_heavy_windup(rng):
    """A rising hum swell as the blade is drawn back."""
    t = g.tt(0.7)
    f = F * (1 + 0.25 * np.clip(t / 0.5, 0, 1))
    return g.reverb(g.saber_tone(f, rng, bright=2200) * np.clip(t / 0.5, 0, 1) ** 1.5 * np.clip((0.7 - t) / 0.1, 0, 1), rng, 0.4, 0.1)


def g_heavy_strike(rng):
    swing = g.make_swing(dict(dur=0.6, tp=0.2, f0=80, up=0.2, down=0.2, w1=0.08, w2=0.14, wh=1400))(rng)
    t = g.tt(len(swing) / g.SR)
    return swing + g.thump(t, 70, 35, 0.06, 0.2, t0=0.2) * 0.5


def g_lunge(rng):
    t = g.tt(0.7)
    b = g.bell(t, 0.3, 0.2, 0.12)
    tone = g.saber_tone(F * (1 + 0.2 * b), rng, bright=2000) * (0.2 + 0.8 * b)
    wh = g.whoosh(t, rng, 400 + 1600 * b, 1.4, b) * 1.2
    return g.reverb(tone + wh, rng, 0.4, 0.1)


def g_form_change(rng):
    """A hum-pitch shift."""
    t = g.tt(0.9)
    f = F * (1 + 0.12 * np.sin(np.pi * np.clip(t / 0.8, 0, 1)))
    return g.reverb(g.saber_tone(f, rng, bright=2600) * g.env_ad(t, 0.05, 0.4), rng, 0.6, 0.15)


def g_throw(rng):
    swing = g.make_swing(dict(dur=0.5, tp=0.18, f0=96, up=0.15, down=0.1, w1=0.07, w2=0.1, wh=2600))(rng)
    t = g.tt(len(swing) / g.SR)
    return swing + g.crack(t, rng, 0.0, 0.01, 1500) * 0.5


def g_throw_loop(rng):
    """Seamless spinning hum (10 rotations a second, as an amplitude and pitch wobble)."""
    L = 2.0
    t = g.tt(L)
    spin = np.sin(2 * np.pi * 10 * t)
    f = F * (1 + 0.04 * spin)
    return g.saber_tone(f, rng, loop=True, bright=2400) * (0.7 + 0.3 * spin)


def g_throw_catch(rng):
    t = g.tt(0.7)
    slap = g.lp(g.noise(len(t), rng), 1500) * g.env_ad(t, 0.002, 0.03) * 1.0
    hum = g.saber_tone(F * (1 + 0.1 * np.exp(-t / 0.1)), rng) * g.env_ad(t, 0.01, 0.25) * 0.8
    return g.reverb(slap + hum, rng, 0.4, 0.1)


def g_npc_windup(rng):
    t = g.tt(0.6)
    f = F * 0.9 * (1 + 0.3 * np.clip(t / 0.45, 0, 1))
    tone = g.saber_tone(f, rng, bright=3000) * np.clip(t / 0.45, 0, 1) ** 2
    glint = np.sin(2 * np.pi * 2637 * t) * g.env_ad(t, 0.005, 0.1, 0.45) * 0.3
    return g.reverb(tone + glint, rng, 0.5, 0.12, bright=7000)


def g_lightning_block(rng):
    t = g.tt(0.6)
    sizz = g.hp(g.crackle(len(t), 2000 * np.exp(-t / 0.2), rng), 2500) * 1.2
    hum = g.saber_tone(np.full(len(t), F * 1.3), rng, bright=3500) * g.env_ad(t, 0.005, 0.2)
    return g.reverb(sizz + hum, rng, 0.4, 0.1)


def g_hum_singing(rng):
    """Seamless 2 s layer over the hum for a blade lit by the singing (amber) crystal: a high
    crystal chord (E6, B6, a faint E7) whose detuned pairs beat slowly, a breathy air band and a
    shimmer that swells twice a loop. Every rate is a whole number of cycles per loop."""
    L = 2.0
    t = g.tt(L)
    n = len(t)
    x = np.zeros(n)
    # (frequency, beating partner offset in Hz, level)
    for f, beat, a in ((1318.5, 1.5, 1.0), (1975.5, 2.0, 0.55), (2637.0, 1.0, 0.22)):
        p0 = rng.uniform(0, 2 * np.pi)
        x += a * np.sin(g.phase(np.full(n, f), n, loop=True) + p0)
        x += a * 0.8 * np.sin(g.phase(np.full(n, f + beat), n, loop=True) + p0 + 1.3)
    # Slow shimmer: two swells a loop (1 Hz) plus a faster flutter (3 Hz); both divide the loop.
    shimmer = 0.75 + 0.18 * np.sin(2 * np.pi * 1.0 * t) + 0.07 * np.sin(2 * np.pi * 3.0 * t + 0.7)
    air = g.bp(g.noise(n, rng), 4200, 1.5, loop=True) * (0.6 + 0.4 * np.sin(2 * np.pi * 0.5 * t + 2.0)) * 0.25
    return g.reverb(x * shimmer + air, rng, 1.4, 0.35, loop=True, bright=9000)


FILES = {
    "saber.finisher": {"saber/finisher": g_finisher},
    "saber.heavy_windup": {"saber/heavy_windup": g_heavy_windup},
    "saber.heavy_strike": {"saber/heavy_strike": g_heavy_strike},
    "saber.lunge": {"saber/lunge": g_lunge},
    "saber.form_change": {"saber/form_change": g_form_change},
    "saber.throw": {"saber/throw": g_throw},
    "saber.throw_loop": {"saber/throw_loop": g_throw_loop},
    "saber.throw_catch": {"saber/throw_catch": g_throw_catch},
    "saber.npc_windup": {"saber/npc_windup": g_npc_windup},
    "saber.lightning_block": {"saber/lightning_block": g_lightning_block},
    "saber.hum.singing": {"saber/hum_singing": g_hum_singing},
}
