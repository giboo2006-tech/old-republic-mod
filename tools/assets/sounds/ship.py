"""PLACEHOLDER sound recipes for ships and travel (Milestone 4, W3): the courier shuttle's engines
through the flight (idle, spool, climb, descent, touchdown, ramp), the jump into and out of
hyperspace and the hyperdrive's hum, the repairs (a part fitted, a weld, the hulk powering up), the
damaged engine's crackle, and the landing beacon (set, refused, recall).

Synthesised from scratch with the helpers in gen_sounds.py; nothing is sampled, and no sound from
any game or film is imitated. The file names are the placeholder names gen_sounds gave these ids
in Phase 0 (<group>/<rest of the id>), so sounds.json does not change. The three loops (ship.idle,
ship.hyperspace, ship.damaged) are seamless: every filter and oscillator is circular. See
events_m4.json for the event list and docs/assets/travel.md for the intent of each sound.
"""
import gen_sounds as g

np = g.np


def turbine(f, n, loop=False):
    """A turbine's whine: a fundamental and two inharmonic partials over the curve f."""
    ph = g.phase(f, n, loop)
    return np.sin(ph) * 0.6 + np.sin(2.01 * ph) * 0.25 + np.sin(3.97 * ph) * 0.12


def g_idle(rng):
    """Seamless 3 s idle: a low engine hum, a soft turbine whine that wavers, and a rumble of air."""
    L = 3.0
    t = g.tt(L)
    n = len(t)
    hum = sum(a * np.sin(g.phase(np.full(n, f), n, loop=True) + rng.uniform(0, 6.28)) for f, a in ((48.0, 0.5), (96.0, 0.3), (144.0, 0.12)))
    whine_f = 820.0 * (1 + 0.006 * np.sin(2 * np.pi * t / L * 2) + 0.003 * np.sin(2 * np.pi * t / L * 5 + 1.0))
    whine = turbine(whine_f, n, loop=True) * 0.08
    air = g.lp(g.noise(n, rng), 260, loop=True) * 1.6
    hiss = g.bp(g.noise(n, rng), 3200, 1.0, loop=True) * 0.05
    return hum * (1 + 0.08 * np.sin(2 * np.pi * t / L * 3)) + whine + air + hiss


def g_spool(rng):
    """The engines spool up over two seconds: a whine climbing in pitch, a swelling rumble, latches clicking."""
    L = 2.2
    t = g.tt(L)
    n = len(t)
    rise = np.clip(t / 2.0, 0, 1)
    f = 260 + 1500 * rise ** 1.6
    whine = turbine(f, n) * (0.15 + 0.35 * rise)
    rumble = g.lp(g.noise(n, rng), 140) * (0.8 + 2.2 * rise)
    hiss = g.sweep(g.noise(n, rng), 900 + 3000 * rise, "bp", 1.5, nb=10) * 0.25 * rise
    clicks = g.click(t, rng, 0.15, 0.8) * 0.5 + g.click(t, rng, 0.42, 0.7) * 0.4
    x = (whine + rumble + hiss) * np.minimum(1, (L - t) / 0.12) + clicks
    return g.reverb(x, rng, 0.8, 0.15)


def g_climb(rng):
    """The climb: a roar of thrust swelling and holding, a thrust tone rising slowly, then easing away."""
    L = 3.2
    t = g.tt(L)
    n = len(t)
    env = np.minimum(1, t / 0.35) * np.minimum(1, (L - t) / 1.2)
    roar = g.sweep(g.noise(n, rng), 300 + 500 * np.clip(t / L, 0, 1), "lp", nb=10) * 2.4
    tone = turbine(1500 + 300 * t / L, n) * 0.12
    sub = np.sin(g.phase(np.full(n, 38.0), n)) * 0.35
    x = (roar + tone + sub) * env
    return g.reverb(g.sat(x * 0.7, 1.4), rng, 1.4, 0.2, bright=3500)


def g_descent(rng):
    """The descent: a repulsor whine falling in pitch over four seconds, the rumble settling with it."""
    L = 4.0
    t = g.tt(L)
    n = len(t)
    fall = np.clip(t / L, 0, 1)
    f = 1700 * (1 - fall) ** 1.3 + 420
    whine = turbine(f, n) * 0.28
    rumble = g.lp(g.noise(n, rng), 180) * (1.8 - 1.0 * fall)
    flutter = 1 + 0.12 * np.sin(2 * np.pi * 7 * t)
    env = np.minimum(1, t / 0.3) * np.minimum(1, (L - t) / 0.6)
    return g.reverb((whine * flutter + rumble) * env, rng, 1.0, 0.18)


def g_land(rng):
    """Touchdown: a heavy thump, the struts' metal creak, then a long pneumatic hiss."""
    L = 2.0
    t = g.tt(L)
    n = len(t)
    x = g.thump(t, 110, 42, 0.05, 0.25) * 1.1
    x += g.lp(g.noise(n, rng), 300) * g.env_ad(t, 0.004, 0.12) * 1.2
    x += g.metal_ring(t, rng, 420, 5, 0.25, 0.06) * 0.25
    x += g.metal_ring(t, rng, 610, 4, 0.18, 0.18) * 0.15
    hiss = g.bp(g.noise(n, rng), 4200, 0.9) * g.env_ad(t, 0.05, 0.45, 0.45) * 0.45
    return g.reverb(x + hiss, rng, 0.9, 0.2)


def g_ramp(rng):
    """The ramp moves: a hydraulic motor whirring under load, then a clunk as it seats."""
    L = 1.7
    t = g.tt(L)
    n = len(t)
    run = np.clip(t / 0.1, 0, 1) * np.clip((1.3 - t) / 0.08, 0, 1)
    motor = turbine(np.full(n, 190.0) * (1 + 0.04 * np.sin(2 * np.pi * 3 * t)), n) * 0.35 * run
    motor += g.bp(g.noise(n, rng), 1300, 2.5) * 0.25 * run
    clunk = g.thump(t, 160, 70, 0.02, 0.12, t0=1.32) * 0.9 + g.metal_ring(t, rng, 900, 4, 0.12, 1.32) * 0.2
    return g.reverb(motor + clunk, rng, 0.6, 0.15)


def g_jump_out(rng):
    """The jump: a pressure thump, then a tearing streak climbing to nothing, with a long tail."""
    L = 2.0
    t = g.tt(L)
    n = len(t)
    x = g.thump(t, 90, 30, 0.08, 0.35) * 1.2
    rise = np.clip((t - 0.05) / 0.9, 0, 1)
    streak = g.sweep(g.noise(n, rng), 300 + 7000 * rise ** 2, "bp", 3.0, nb=16) * 1.4
    streak *= g.env_ad(t, 0.08, 0.35, 0.05)
    zing = np.sin(g.phase(200 + 2600 * rise ** 2, n)) * 0.2 * g.env_ad(t, 0.05, 0.3, 0.05)
    return g.reverb(x + streak + zing, rng, 1.6, 0.3)


def g_jump_in(rng):
    """Arrival: a streak falling out of the high register into a heavy thump."""
    L = 1.8
    t = g.tt(L)
    n = len(t)
    fall = np.clip(t / 0.55, 0, 1)
    streak = g.sweep(g.noise(n, rng), 7000 * (1 - fall) ** 2 + 250, "bp", 3.0, nb=16) * 1.2
    streak *= np.minimum(1, t / 0.15) * np.clip((0.7 - t) / 0.2, 0, 1)
    x = streak + g.thump(t, 100, 34, 0.07, 0.3, t0=0.55) * 1.2
    return g.reverb(x, rng, 1.4, 0.28)


def g_hyperspace(rng):
    """Seamless 4 s hyperdrive hum: a deep drone beating slowly, an airy rush and a slow swell."""
    L = 4.0
    t = g.tt(L)
    n = len(t)
    drone = sum(a * np.sin(g.phase(np.full(n, f), n, loop=True) + rng.uniform(0, 6.28))
                for f, a in ((41.0, 0.5), (41.5, 0.4), (82.0, 0.25), (123.5, 0.1)))
    rush = g.sweep(g.noise(n, rng), 700 + 400 * np.sin(2 * np.pi * t / L) ** 2, "bp", 0.8, nb=8, loop=True) * 0.35
    swell = 1 + 0.15 * np.sin(2 * np.pi * t / L)
    sparkle = g.bp(g.noise(n, rng), 6500, 2.0, loop=True) * 0.04
    return g.reverb((drone + rush) * swell + sparkle, rng, 2.0, 0.3, loop=True, bright=2500)


def g_fit_part(rng):
    """A part seats: a heavy clank, a latch click and a short rising hum as it comes online."""
    L = 1.0
    t = g.tt(L)
    n = len(t)
    x = g.thump(t, 180, 80, 0.02, 0.1) * 0.7 + g.metal_ring(t, rng, 760, 6, 0.2) * 0.35
    x += g.click(t, rng, 0.14, 0.9) * 0.6
    hum_f = 180 + 120 * np.clip((t - 0.25) / 0.4, 0, 1)
    x += turbine(hum_f, n) * 0.18 * g.env_ad(t, 0.15, 0.25, 0.25)
    return g.reverb(x, rng, 0.6, 0.15)


def g_weld(rng):
    """A plate welded on: a short burst of arc crackle and hiss over a buzz, then the metal ticking as it cools."""
    L = 1.3
    t = g.tt(L)
    n = len(t)
    run = np.clip(t / 0.03, 0, 1) * np.clip((0.75 - t) / 0.05, 0, 1)
    crack = g.bp(g.crackle(n, 110, rng), 3600, 1.2) * 1.4
    hiss = g.bp(g.noise(n, rng), 6000, 0.8) * 0.3
    buzz = g.lp(np.sign(np.sin(2 * np.pi * 120 * t)) * 0.4, 800) * 0.2
    ticks = sum(g.click(t, rng, 0.85 + k * rng.uniform(0.08, 0.14), 1.3) * 0.18 for k in range(3))
    return g.reverb((crack + hiss + buzz) * run + ticks, rng, 0.5, 0.12)


def g_power_up(rng):
    """The hulk powers up: relays clack, a tone rises through the systems and settles into the idle hum."""
    L = 2.8
    t = g.tt(L)
    n = len(t)
    x = sum(g.click(t, rng, t0, 0.8) * 0.5 for t0 in (0.05, 0.18, 0.31))
    rise = np.clip((t - 0.35) / 1.2, 0, 1)
    tone_f = 90 + 330 * rise ** 1.5
    tone = (np.sin(g.phase(tone_f, n)) * 0.4 + np.sin(g.phase(tone_f * 2.0, n)) * 0.15) * g.env_ad(t, 0.4, 1.2, 0.35)
    whine = turbine(500 + 400 * rise, n) * 0.1 * rise * np.minimum(1, (L - t) / 0.8)
    chirps = sum(np.sin(2 * np.pi * f * t) * g.env_ad(t, 0.003, 0.05, t0) * 0.15 for t0, f in ((1.6, 1760.0), (1.72, 2349.0)))
    return g.reverb(x + tone + whine + chirps, rng, 1.0, 0.2)


def g_damaged(rng):
    """Seamless 2 s crackle of a damaged engine: sparks, an irregular sputter and a failing buzz."""
    L = 2.0
    t = g.tt(L)
    n = len(t)
    sparks = g.hp(g.crackle(n, 40, rng), 2500, loop=True) * 0.7
    sputter_env = 0.5 + 0.5 * np.sin(2 * np.pi * t / L * 3 + 0.5) * np.sin(2 * np.pi * t / L * 7)
    sputter = g.lp(g.noise(n, rng), 220, loop=True) * 1.5 * np.clip(sputter_env, 0, 1)
    buzz = g.bp(np.sign(np.sin(g.phase(np.full(n, 100.0), n, loop=True))), 600, 2.0, loop=True) * 0.12
    buzz *= np.clip(np.sin(2 * np.pi * t / L * 2) * 1.5, 0, 1)
    return sparks + sputter + buzz


def beeps(t, notes, length=0.09, level=0.35):
    """Short sine beeps at (start, frequency) pairs."""
    x = np.zeros(len(t))
    for t0, f in notes:
        win = ((t >= t0) & (t < t0 + length)).astype(float)
        x += np.sin(2 * np.pi * f * (t - t0)) * win * level * np.exp(-np.maximum(t - t0, 0) / (length * 1.5))
    return x


def g_beacon_set(rng):
    """The beacon takes: a latch click and two rising beeps."""
    t = g.tt(0.9)
    x = g.click(t, rng, 0.02, 1.0) * 0.6 + beeps(t, ((0.12, 1175.0), (0.26, 1568.0)))
    return g.reverb(x, rng, 0.5, 0.15)


def g_beacon_refuse(rng):
    """The beacon refuses: two low buzzing tones falling."""
    t = g.tt(0.8)
    n = len(t)
    x = np.zeros(n)
    for t0, f in ((0.02, 330.0), (0.22, 247.0)):
        win = ((t >= t0) & (t < t0 + 0.15)).astype(float)
        x += np.sign(np.sin(2 * np.pi * f * (t - t0))) * win * 0.18
    return g.reverb(g.lp(x, 2500), rng, 0.4, 0.12)


def g_beacon_recall(rng):
    """A recall is sent: three ascending beeps, then a distant engine swell far off."""
    L = 2.0
    t = g.tt(L)
    n = len(t)
    x = beeps(t, ((0.02, 988.0), (0.16, 1319.0), (0.30, 1760.0)))
    swell = g.sweep(g.noise(n, rng), 400 + 600 * np.clip((t - 0.5) / 1.2, 0, 1), "bp", 1.2, nb=8)
    x += swell * 0.35 * g.bell(t, 1.3, 0.4, 0.5)
    return g.reverb(x, rng, 1.2, 0.3)


FILES = {
    "ship.idle": {"ship/idle": g_idle},
    "ship.spool": {"ship/spool": g_spool},
    "ship.climb": {"ship/climb": g_climb},
    "ship.descent": {"ship/descent": g_descent},
    "ship.land": {"ship/land": g_land},
    "ship.ramp": {"ship/ramp": g_ramp},
    "ship.jump_out": {"ship/jump_out": g_jump_out},
    "ship.jump_in": {"ship/jump_in": g_jump_in},
    "ship.hyperspace": {"ship/hyperspace": g_hyperspace},
    "ship.fit_part": {"ship/fit_part": g_fit_part},
    "ship.weld": {"ship/weld": g_weld},
    "ship.power_up": {"ship/power_up": g_power_up},
    "ship.damaged": {"ship/damaged": g_damaged},
    "beacon.set": {"beacon/set": g_beacon_set},
    "beacon.refuse": {"beacon/refuse": g_beacon_refuse},
    "beacon.recall": {"beacon/recall": g_beacon_recall},
}
