"""PLACEHOLDER sound recipes for the force feature (Pull, Jump, Heal, Lightning; Milestone 3: Sense, Lift).

Owned by the force worker. Synthesised from scratch with the helpers in
gen_sounds.py; see events_m2.json and events_m3.json for the event lists and
docs/MILESTONE_2.md section 8 and docs/MILESTONE_3.md sections 10.1, 10.2 and 13
for each power's intended sound.
"""
import gen_sounds as g

np = g.np


# ---------------------------------------------------------------- pull

def make_pull_cast(p):
    """An inhaled, reversed pressure swell."""
    def gen(rng):
        t = g.tt(0.9)
        fc = 200 + 2200 * p * np.clip(t / 0.35, 0, 1) ** 2
        swell = g.sweep(g.noise(len(t), rng), fc, "lp", nb=12) * np.clip(t / 0.35, 0, 1) ** 2 * np.exp(-np.maximum(t - 0.35, 0) / 0.08) * 2.0
        sub = g.thump(t, 40 * p, 70 * p, 0.2, 0.2, t0=0.3) * 0.5
        return g.reverb(swell + sub, rng, 0.9, 0.2, bright=3000)
    return gen


def g_pull_drag(rng):
    t = g.tt(0.6)
    grit = g.bp(g.crackle(len(t), 400, rng), 1400, 0.8) * 2.0
    wh = g.whoosh(t, rng, 300 + 900 * g.bell(t, 0.25, 0.1, 0.2), 1.2, g.bell(t, 0.25, 0.1, 0.2))
    return g.reverb((grit + wh) * g.env_ad(t, 0.03, 0.25), rng, 0.5, 0.15)


def g_pull_catch(rng):
    t = g.tt(0.5)
    return g.reverb(g.thump(t, 90, 40, 0.04, 0.12) * 0.9 + g.lp(g.noise(len(t), rng), 900) * g.env_ad(t, 0.003, 0.08) * 0.6,
                    rng, 0.4, 0.12)


def g_pull_disarm(rng):
    t = g.tt(0.7)
    wrench = g.metal_ring(t, rng, 1300, 6, 0.25) * 0.6 + g.crack(t, rng, 0, 0.01, 1000)
    scrape = g.bp(g.noise(len(t), rng), 2600, 1.5) * g.env_ad(t, 0.01, 0.12) * 0.5
    return g.reverb(wrench + scrape, rng, 0.6, 0.15)


# ---------------------------------------------------------------- jump

def g_jump_gather(rng):
    """Seamless low rising rush (loops while the leap is held)."""
    L = 2.0
    t = g.tt(L)
    n = len(t)
    x = np.zeros(n)
    for k in range(2):
        ph = ((t / L) + k / 2) % 1.0
        x += g.sweep(g.noise(n, rng), 90 * 2 ** (2.5 * ph), "bp", 2.0, nb=12, loop=True) * np.sin(np.pi * ph) ** 2
    rumble = g.lp(g.noise(n, rng), 80, loop=True) * 2.0
    return x + rumble


def g_jump_launch(rng):
    t = g.tt(0.9)
    th = g.sat(g.thump(t, 70, 32, 0.08, 0.2) * 0.9, 1.6)
    wh = g.whoosh(t, rng, 300 + 1800 * g.bell(t, 0.12, 0.04, 0.2), 1.3, g.bell(t, 0.12, 0.05, 0.25)) * 1.2
    return g.reverb(th + wh, rng, 0.8, 0.2, bright=3000)


def make_jump_land(weight):
    def gen(rng):
        t = g.tt(0.6)
        th = g.thump(t, 80 * weight, 38, 0.04, 0.1 * weight) * 0.8
        dust = g.lp(g.noise(len(t), rng), 1200) * g.env_ad(t, 0.004, 0.08 * weight) * 0.8
        return g.reverb(th + dust, rng, 0.4, 0.12)
    return gen


def g_jump_slam(rng):
    t = g.tt(1.3)
    boom = g.sat(g.thump(t, 55, 26, 0.15, 0.5), 1.8)
    deb = g.bp(g.crackle(len(t), 300 * np.exp(-t / 0.3), rng), 1600, 0.9) * 4.0
    wave = g.sweep(g.noise(len(t), rng), 1800 * np.exp(-t / 0.25) + 150, "lp", nb=10) * g.env_ad(t, 0.01, 0.3) * 1.5
    return g.reverb(boom + deb + wave, rng, 1.3, 0.25, bright=3000)


def g_jump_glide(rng):
    """Seamless airy loop."""
    L = 2.0
    t = g.tt(L)
    n = len(t)
    fc = 900 + 300 * np.sin(2 * np.pi * t / L)
    return g.sweep(g.noise(n, rng), fc, "bp", 0.8, nb=10, loop=True) * (1 + 0.2 * np.sin(2 * np.pi * 2 * t / L))


# ---------------------------------------------------------------- heal

def g_heal_start(rng):
    """A warm breathy swell."""
    t = g.tt(1.0)
    breath = g.sweep(g.noise(len(t), rng), 600 + 900 * np.clip(t / 0.5, 0, 1), "bp", 1.2, nb=10) * g.bell(t, 0.45, 0.2, 0.25) * 0.8
    pad = sum(np.sin(2 * np.pi * f * t) * a for f, a in [(261.6, 1), (392, 0.5)]) * g.bell(t, 0.55, 0.25, 0.3) * 0.3
    return g.reverb(breath + pad, rng, 1.5, 0.3, bright=5000)


def g_heal_loop(rng):
    """Seamless soft tonal pad."""
    L = 4.0
    t = g.tt(L)
    n = len(t)
    pad = np.zeros(n)
    for f, a in [(261.6, 0.6), (392.0, 0.4), (523.25, 0.25)]:
        for det in (0.0, 0.5):
            pad += a * np.sin(g.phase(np.full(n, f + det), n, loop=True))
    pad *= 0.85 + 0.15 * np.sin(2 * np.pi * 2 * t / L)
    shimmer = g.bp(g.noise(n, rng), 5000, 3, loop=True) * 0.08
    return g.reverb(pad * 0.5 + shimmer, rng, 2.0, 0.3, loop=True, bright=5000)


def g_heal_end(rng):
    t = g.tt(1.4)
    tone = g.bell_partials(len(t), 523.25, [1, 2.0, 3.0], [1.0, 0.5, 0.25], [1, 0.25, 0.1], rng, attack=0.03)
    return g.reverb(tone * 0.6, rng, 1.8, 0.35, bright=6000)


def g_heal_ward(rng):
    return g.make_chime(1760.0, 0.6)(rng)


def g_heal_interrupt(rng):
    """A broken breath and a dull drop."""
    t = g.tt(0.8)
    breath = g.sweep(g.noise(len(t), rng), 1200 - 700 * np.clip(t / 0.3, 0, 1), "bp", 1.3, nb=8) * g.env_ad(t, 0.01, 0.12)
    stutter = 0.5 + 0.5 * np.sign(np.sin(2 * np.pi * 18 * t))
    drop = g.thump(t, 110, 50, 0.08, 0.2, t0=0.12) * 0.7
    return g.reverb(breath * g.lp(stutter, 150) + drop, rng, 0.6, 0.15)


# ---------------------------------------------------------------- lightning

def g_lightning_charge(rng):
    t = g.tt(0.6)
    u = t / 0.6
    crk = g.hp(g.crackle(len(t), 100 + 1500 * u ** 2, rng), 2500) * 1.5
    buzz = g.saw(g.phase(120 + 60 * u, len(t)), 180, nh=30) * u ** 2 * 0.3
    return g.reverb(crk + g.bp(buzz, 1800, 1.0), rng, 0.4, 0.1, bright=7000)


def g_lightning_loop(rng):
    """Seamless sustained crackle and hiss."""
    L = 2.0
    t = g.tt(L)
    n = len(t)
    crk = g.hp(g.crackle(n, 1800, rng), 2500, loop=True) * 1.4
    hiss = g.bp(g.noise(n, rng), 4500, 1.0, loop=True) * 0.25
    buzz = g.bp(g.saw(g.phase(np.full(n, 150.0), n, loop=True), 150, nh=40), 2000, 1.2, loop=True) * 0.3
    return crk + hiss + buzz * (0.8 + 0.2 * np.sin(2 * np.pi * 4 * t / L))


def g_lightning_impact(rng):
    t = g.tt(0.4)
    sizzle = g.hp(g.crackle(len(t), 2500 * np.exp(-t / 0.1), rng), 3000) * 1.5
    return g.reverb(sizzle + g.crack(t, rng, 0, 0.005, 1500), rng, 0.3, 0.1)


def g_lightning_release(rng):
    t = g.tt(0.8)
    snap = g.crack(t, rng, 0, 0.012, 800) * 1.4
    tail = g.hp(g.crackle(len(t), 1200 * np.exp(-t / 0.15), rng), 2500)
    return g.reverb(snap + tail, rng, 0.6, 0.15, bright=6000)


# ---------------------------------------------------------------- sense (Milestone 3)

def g_sense_cast(rng):
    """An inhale through the teeth, then a soft resonant swell that opens and settles."""
    t = g.tt(1.6)
    inhale_env = g.bell(t, 0.45, 0.22, 0.12)
    inhale = g.sweep(g.noise(len(t), rng), 500 + 1400 * np.clip(t / 0.5, 0, 1), "bp", 1.3, nb=10) * inhale_env * 0.7
    swell_env = g.bell(t, 0.75, 0.25, 0.4)
    pad = sum(np.sin(g.phase(np.full(len(t), f), len(t))) * a for f, a in [(196.0, 1.0), (293.7, 0.55), (392.0, 0.25), (587.3, 0.08)])
    air = g.bp(g.noise(len(t), rng), 3200, 2.5) * swell_env * 0.05
    return g.reverb(inhale + pad * swell_env * 0.35 + air, rng, 2.2, 0.4, bright=4000)


def g_sense_reveal(rng):
    """A soft tick: a pebble set down on stone, with a short, faint resonance."""
    t = g.tt(0.45)
    tick = g.lp(g.crack(t, rng, 0, 0.004, 1500), 5000) * 0.9
    ring = g.bell_partials(len(t), 1174.7, [1, 2.0, 2.9], [0.12, 0.06, 0.04], [0.35, 0.12, 0.06], rng, attack=0.002)
    return g.reverb(tick + ring, rng, 0.9, 0.3, bright=5000)


def g_sense_danger(rng):
    """A prickle: a short bristle of fine dry crackle over a thin rising hiss, cut off sharply."""
    t = g.tt(0.5)
    u = np.clip(t / 0.3, 0, 1)
    env = np.where(t < 0.3, u ** 1.5, np.exp(-(t - 0.3) / 0.03))
    bristle = g.hp(g.crackle(len(t), 300 + 2200 * u, rng), 4500) * env * 1.4
    hiss = g.sweep(g.noise(len(t), rng), 2500 + 3500 * u, "bp", 3.0, nb=8) * env * 0.35
    return g.reverb(bristle + hiss, rng, 0.4, 0.15, bright=8000)


# ---------------------------------------------------------------- lift (Milestone 3)

def g_lift_start(rng):
    """Pressure gathering under something heavy: a low swell, a lurch, loose grit shaken off."""
    t = g.tt(1.0)
    swell = g.sweep(g.noise(len(t), rng), 120 + 500 * np.clip(t / 0.4, 0, 1), "lp", nb=10) * g.bell(t, 0.35, 0.18, 0.3) * 2.2
    lurch = g.thump(t, 70, 45, 0.15, 0.25, t0=0.28) * 0.6
    grit = g.bp(g.crackle(len(t), 40 + 260 * g.bell(t, 0.4, 0.1, 0.25), rng), 1800, 0.9) * 1.6
    return g.reverb(swell + lurch + grit, rng, 0.9, 0.2, bright=3500)


def g_lift_loop(rng):
    """Seamless: a strained low hum of held weight, slowly wavering, with a trickle of falling grit."""
    L = 3.0
    t = g.tt(L)
    n = len(t)
    hum = np.zeros(n)
    for f, a in [(68.0, 1.0), (68.6, 0.8), (102.0, 0.4), (136.3, 0.15)]:
        hum += a * np.sin(g.phase(np.full(n, f), n, loop=True))
    hum *= 0.85 + 0.15 * np.sin(2 * np.pi * 2 * t / L) + 0.05 * np.sin(2 * np.pi * 5 * t / L + 1)
    air = g.bp(g.noise(n, rng), 420, 1.2, loop=True) * (0.9 + 0.1 * np.sin(2 * np.pi * t / L)) * 0.5
    grit = g.bp(g.crackle(n, 25, rng), 2200, 1.0, loop=True) * 0.8
    return hum * 0.45 + air + grit


def g_lift_slam(rng):
    """The held target driven into the ground: a heavy thud, a crunch of debris, a dust breath."""
    t = g.tt(1.2)
    boom = g.sat(g.thump(t, 85, 34, 0.06, 0.3), 1.7)
    crunch = g.bp(g.crackle(len(t), 900 * np.exp(-t / 0.08), rng), 1400, 0.8) * 3.0
    dust = g.sweep(g.noise(len(t), rng), 1500 * np.exp(-t / 0.2) + 200, "lp", nb=10) * g.env_ad(t, 0.01, 0.25) * 1.2
    return g.reverb(boom + crunch + dust, rng, 1.1, 0.22, bright=3000)


def g_lift_hurl(rng):
    """A heavy body flung: a rush of displaced air that passes, with tumbling grit."""
    t = g.tt(0.9)
    rush = g.whoosh(t, rng, 250 + 1400 * g.bell(t, 0.2, 0.07, 0.25), 1.2, g.bell(t, 0.2, 0.06, 0.3)) * 1.5
    tumble = g.bp(g.crackle(len(t), 120 * g.bell(t, 0.3, 0.1, 0.3), rng), 1100, 0.9) * 1.5
    shove = g.thump(t, 90, 50, 0.05, 0.12) * 0.5
    return g.reverb(rush + tumble + shove, rng, 0.8, 0.2, bright=3500)


FILES = {
    "force.pull.cast": {"force/pull_cast1": make_pull_cast(1.0), "force/pull_cast2": make_pull_cast(0.9)},
    "force.pull.drag": {"force/pull_drag": g_pull_drag},
    "force.pull.catch": {"force/pull_catch": g_pull_catch},
    "force.pull.disarm": {"force/pull_disarm": g_pull_disarm},
    "force.jump.gather": {"force/jump_gather": g_jump_gather},
    "force.jump.launch": {"force/jump_launch": g_jump_launch},
    "force.jump.land": {"force/jump_land_soft": make_jump_land(1.0), "force/jump_land_heavy": make_jump_land(1.6)},
    "force.jump.slam": {"force/jump_slam": g_jump_slam},
    "force.jump.glide": {"force/jump_glide": g_jump_glide},
    "force.heal.start": {"force/heal_start": g_heal_start},
    "force.heal.loop": {"force/heal_loop": g_heal_loop},
    "force.heal.end": {"force/heal_end": g_heal_end},
    "force.heal.ward": {"force/heal_ward": g_heal_ward},
    "force.heal.interrupt": {"force/heal_interrupt": g_heal_interrupt},
    "force.lightning.charge": {"force/lightning_charge": g_lightning_charge},
    "force.lightning.loop": {"force/lightning_loop": g_lightning_loop},
    "force.lightning.impact": {"force/lightning_impact": g_lightning_impact},
    "force.lightning.release": {"force/lightning_release": g_lightning_release},
    # Milestone 3 (placeholder file names kept, so sounds.json and the subtitles stay unchanged)
    "force.sense.cast": {"force/sense_cast": g_sense_cast},
    "force.sense.reveal": {"force/sense_reveal": g_sense_reveal},
    "force.sense.danger": {"force/sense_danger": g_sense_danger},
    "force.lift.start": {"force/lift_start": g_lift_start},
    "force.lift.loop": {"force/lift_loop": g_lift_loop},
    "force.lift.slam": {"force/lift_slam": g_lift_slam},
    "force.lift.hurl": {"force/lift_hurl": g_lift_hurl},
}
