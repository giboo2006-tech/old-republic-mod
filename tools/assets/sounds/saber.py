"""PLACEHOLDER sound recipes for the saber feature (combo, heavy, lunge, forms, throw, and the
milestone 3 singing-crystal hum layer).

Owned by the saber worker. Synthesised from scratch with the helpers in gen_sounds.py, on its
film-style saber blocks (film_hum, pass_by, doppler, saber_ignite, sizzle; see the Lightsaber
section there): every swing-like sound is the hum carried past the listener, every contact a
crack and a sizzle over the hum. The singing-crystal layer keeps its own idea. See
events_m2.json and events_m3.json for the event lists.
"""
import gen_sounds as g

np = g.np
F = g.SABER_F


def g_finisher(rng):
    """The combo's third step: a heavier, longer pass that chops down (a deep drop after the closest point)."""
    return g.make_swing(dict(dur=0.8, tp=0.34, f0=84, up=0.2, down=0.22, w1=0.14, w2=0.12, wh=1500))(rng)


def g_heavy_windup(rng):
    """A rising swell as the blade is drawn back past the head: the hum climbs a fifth of its pitch and
    combs closer and closer, then cuts off for the strike."""
    t = g.tt(0.7)
    u = np.clip(t / 0.5, 0, 1)
    x = g.pass_by(g.film_hum(F * (1 + 0.22 * u ** 1.3), rng, **g.HUM), u ** 2, comb=(0.3, 3.5), refl=0.7, floor=0.0,
                  power=1.0)
    return g.reverb(x * u ** 0.5 * np.clip((0.7 - t) / 0.12, 0, 1), rng, 0.4, 0.1)


def g_heavy_strike(rng):
    """A slow, deep pass with weight: a thump where it lands."""
    swing = g.make_swing(dict(dur=0.6, tp=0.2, f0=80, up=0.2, down=0.2, w1=0.08, w2=0.14, wh=1400))(rng)
    t = g.tt(len(swing) / g.SR)
    return swing + g.thump(t, 70, 35, 0.06, 0.2, t0=0.2) * 0.5


def g_lunge(rng):
    """A fast dash: the hum rushing in from afar (a long approach, high pitch) and past in a blink."""
    return g.make_swing(dict(dur=0.7, tp=0.3, f0=F, up=0.2, down=0.13, w1=0.2, w2=0.07, wh=2000))(rng)


def g_form_change(rng):
    """A hum-pitch shift as the stance settles: the hum bends up 12% and back while the blade turns
    once past the ear."""
    t = g.tt(0.9)
    f = F * (1 + 0.12 * np.sin(np.pi * np.clip(t / 0.8, 0, 1)))
    prox, _ = g.pass_curves(t, 0.4, 0.15, 0.2)
    x = g.pass_by(g.film_hum(f, rng, **dict(g.HUM, bright=2600)), prox, comb=(0.3, 3.5), refl=0.75, floor=0.3, power=1.2)
    return g.reverb(x * g.env_ad(t, 0.05, 0.4), rng, 0.6, 0.15)


def g_throw(rng):
    """The throw: a snap as the blade leaves the hand, a fast bright pass and the hum falling away."""
    swing = g.make_swing(dict(dur=0.5, tp=0.18, f0=96, up=0.15, down=0.14, w1=0.07, w2=0.16, wh=2600))(rng)
    t = g.tt(len(swing) / g.SR)
    return swing + g.crack(t, rng, 0.0, 0.006, 1500) * 0.3 + g.sizzle(t, rng, 1500, 0.0, 0.04) * 0.6


def g_throw_loop(rng):
    """Seamless spinning hum: the blade circling its hilt 8 times a second (whole cycles per loop),
    each turn a small pass (a Doppler wobble, a swell and the comb's sweep)."""
    L = 2.0
    t = g.tt(L)
    ang = 2 * np.pi * 8.0 * t
    # The blade's centre circles the hilt (radius 1) three radii from the listener.
    r = np.sqrt(9 + 1 - 6 * np.cos(ang))
    prox = 2 / r
    f = F * (1 - 0.05 * 3 * np.sin(ang) / r)
    x = g.film_hum(f, rng, loop=True, **g.HUM)
    return g.pass_by(x, prox, comb=(0.4, 2.5), refl=0.5, floor=0.5, power=1.2, loop=True)


def g_throw_catch(rng):
    """The hilt slapping into the palm as the blade arrives: a slap and the hum settling from just above
    its pitch."""
    t = g.tt(0.7)
    slap = g.lp(g.noise(len(t), rng), 1500) * g.env_ad(t, 0.002, 0.03) + g.thump(t, 190, 95, 0.01, 0.04) * 0.5
    prox, _ = g.pass_curves(t, 0.0, 0.05, 0.25)
    hum = g.pass_by(g.film_hum(F * (1 + 0.1 * np.exp(-t / 0.1)), rng, **g.HUM), prox, comb=(0.3, 3.0), refl=0.6,
                    floor=0.35, power=0.7)
    return g.reverb(slap + hum * g.env_ad(t, 0.01, 0.25) * np.clip((0.7 - t) / 0.2, 0, 1) * 0.8, rng, 0.4, 0.1)


def g_npc_windup(rng):
    """The NPC attack tell: the blade drawn back, the hum rising 30% and combing closer, and a bright
    glint at 0.45 s that cuts through combat."""
    t = g.tt(0.6)
    u = np.clip(t / 0.45, 0, 1)
    tone = g.film_hum(F * 0.9 * (1 + 0.3 * u), rng, **dict(g.HUM, bright=3000))
    # u, then a 12 ms fade at 0.6 s: the hum still cuts off for the strike, without a click.
    tone = g.pass_by(tone, u ** 2, comb=(0.3, 3.0), refl=0.7, floor=0.0, power=1.0) * u * np.clip((0.6 - t) / 0.012, 0, 1)
    glint = np.sin(2 * np.pi * 2637 * t) * g.env_ad(t, 0.005, 0.1, 0.45) * 0.3
    return g.reverb(tone + glint, rng, 0.5, 0.12, bright=7000)


def g_lightning_block(rng):
    """A blade holding off lightning (a lock), one pulse of it (it repeats every half second while the bolt
    holds): a crack as the bolt lands, then a crackling, fizzing sizzle and an arc's fry grinding over a
    strained hum, raised and rough, dying away before the next pulse."""
    t = g.tt(0.6)
    env = np.clip(t / 0.01, 0, 1) * np.exp(-t / 0.22) * np.clip((0.55 - t) / 0.15, 0, 1)
    strain = g.film_hum(F * 1.18 * (1 + 0.015 * np.sin(2 * np.pi * 7 * t)), rng, **g.HUM)
    strain = g.sat(strain * (1 + 0.35 * np.sin(2 * np.pi * 33 * t)) * 1.4, 2.2) * env
    land = g.crack(t, rng, 0.0, 0.008, 1200) * 0.5
    sizz = g.sizzle(t, rng, 3000, 0.0, 0.22) + g.spits(t, rng, 0.0, 0.35, 7) * 0.6
    fry = g.arc_fry(t, rng, 120.0) * env * 0.45
    return g.reverb(land + strain * 0.3 + sizz * 0.8 + fry, rng, 0.4, 0.1)


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


# ---------------------------------------------------------------- Milestone 4 (W5): saberstaff and lens


def g_staff_ignite(rng):
    """The saberstaff's second blade: the ignition's snap-hiss and rush, quicker and a little higher,
    with the first blade's hum beating against it as the two settle."""
    x = g.saber_ignite(rng, F * 1.06, g.HUM, dur=1.0, start=0.72, rise=0.03, t_over=0.09, snap=0.9, hiss=0.8)
    t = g.tt(len(x) / g.SR)
    steady = g.film_hum(np.full(len(t), F), rng, **g.HUM) * g.env_ad(t, 0.2, 0.5) * np.clip((1.3 - t) / 0.4, 0, 1)
    return x + steady * 0.25


def g_staff_whirl(rng):
    """The whirl: two blades passing in a full turn, a doubled pass with a spinning hum between them
    and a low thump as it ends."""
    a = g.make_swing(dict(dur=0.75, tp=0.22, f0=82, up=0.18, down=0.16, w1=0.1, w2=0.14, wh=1500))(rng)
    b = g.make_swing(dict(dur=0.75, tp=0.42, f0=88, up=0.18, down=0.16, w1=0.1, w2=0.14, wh=1900))(rng)
    n = max(len(a), len(b))
    x = np.zeros(n)
    x[:len(a)] += a
    x[:len(b)] += b * 0.9
    t = g.tt(n / g.SR)
    spin = np.sin(2 * np.pi * 6 * t)
    hum = g.pass_by(g.film_hum(F * (1 + 0.05 * spin), rng, **g.HUM), 0.5 + 0.5 * spin, comb=(0.3, 2.5), refl=0.6,
                    floor=0.4, power=1.0)
    hum = hum * g.bell(t, 0.32, 0.15, 0.25) * 0.4
    return g.reverb(x + hum + g.thump(t, 75, 38, 0.006, 0.18, t0=0.55) * 0.4, rng, 0.5, 0.12)


def g_lens_fit(rng):
    """A lens seats in the emitter: a fine click and a glassy two-note chime."""
    t = g.tt(0.9)
    x = g.click(t, rng, 0.0, 1.3) * 0.8 + g.click(t, rng, 0.03, 1.1) * 0.5
    for t0, f in ((0.05, 2093.0), (0.12, 3136.0)):
        x += g.bell_partials(len(t), f, [1, 2.32, 4.25], [0.4, 0.2, 0.1], [1, 0.35, 0.15], rng, t0=t0) * 0.25
    return g.reverb(x, rng, 0.6, 0.2, bright=8000)


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
    # Milestone 4 (the Phase 0 placeholder file names). workbench.tune stays a placeholder: crystal
    # tuning is cut from M4 (spec MILESTONE_4.md 5.7).
    "saber.staff_ignite": {"saber/staff_ignite": g_staff_ignite},
    "saber.staff_whirl": {"saber/staff_whirl": g_staff_whirl},
    "workbench.lens_fit": {"workbench/lens_fit": g_lens_fit},
}
