"""PLACEHOLDER sound recipes for the gear feature (Milestone 4: blasters, vibroweapons, the
electrostaff and the jet pack).

Owned by W4. Synthesised from scratch with the helpers in gen_sounds.py (original, deterministic);
see events_m4.json for the event list and docs/MILESTONE_4.md sections 5, 6.4 and 13. The file
names are the Phase 0 placeholder names (<group>/<rest of the id, dots as underscores>[n]), so
sounds.json does not change.

The player's pistol and rifle reuse blaster.pistol_fire and blaster.fire (M2); the rifle's charge
whine reuses blaster.charge.

- blaster.repeater.fire (2 variants): a short, dry, low-chirping bolt, lighter than the rifle's,
  so a stream of them reads as a rattle rather than a row of single shots.
- blaster.rifle.charged: the heavy bolt: a deeper, longer chirp with a hard crack and a tail.
- blaster.overheat: the gun chokes: a sharp warning double-beep, a metallic clank and a long
  scream of steam that falls away.
- blaster.vent: a short hiss of steam with a latch clack.
- blaster.vent_perfect: a clean, bright hiss and a small rising chime (the timing was right).
- blaster.repeater.spin_up / spin_down: the barrel cluster whirring up to speed, and coasting down
  with a few ratchet clicks.
- vibro.flurry: a quick double whip of air over a high buzzing edge.
- vibro.cleave: a broad heavy sweep with a low buzz and a meaty thud.
- electrostaff.charge: 2 s of rising electric crackle and hum (not a loop; the visual arcs carry
  the loaded state).
- electrostaff.shock: a snapping discharge with a buzzing tail.
- electrostaff.clash: a blade held on the emitters: a hard contact snap, a sizzle and a ring.
- jetpack.burst: a short roar of thrust: a hard ignition pop, a rushing body and a fading rumble.
- jetpack.empty: a dry click and a little sputter.
"""
import gen_sounds as g

np = g.np


def make_repeater_fire(pitch):
    """A short dry bolt: a fast falling chirp through a tight metal comb, a crack and a small thump."""
    def gen(rng):
        t = g.tt(0.32)
        f = 1500 * pitch + (3400 - 1500) * pitch * np.exp(-t / 0.022)
        chirp = (np.sin(g.phase(f, len(t))) + 0.35 * np.sin(2 * g.phase(f, len(t)))) * g.env_ad(t, 0.001, 0.06)
        metal = g.comb(chirp, 1 / (820 * pitch), 0.7) * 0.6 + g.bp(chirp, 2600 * pitch, 6) * 0.8
        crack = g.crack(t, rng, 0, 0.004, 1500) * 0.7
        thump = g.thump(t, 140, 60, 0.02, 0.035) * 0.45
        return g.reverb(chirp + metal + crack + thump, rng, 0.35, 0.12)
    return gen


def g_rifle_charged(rng):
    """The heavy bolt: two stacked chirps an octave apart, a hard crack, a chest thump and a long tail."""
    t = g.tt(1.1)
    f1 = 210 + (2600 - 210) * np.exp(-t / 0.05)
    f2 = 420 + (4200 - 420) * np.exp(-t / 0.035)
    chirps = (np.sin(g.phase(f1, len(t))) + 0.5 * np.sin(g.phase(f2, len(t)))) * g.env_ad(t, 0.001, 0.2)
    metal = g.comb(chirps, 1 / 560, 0.85) * 0.7 + g.bp(chirps, 1900, 5) * 0.9
    crack = g.crack(t, rng, 0, 0.012, 900) * 1.1
    thump = g.thump(t, 110, 38, 0.04, 0.12) * 0.9
    tail = g.sweep(g.noise(len(t), rng), 3000 * np.exp(-t / 0.4) + 300, "bp", 1.5) * g.env_ad(t, 0.01, 0.35) * 0.3
    return g.reverb(chirps + metal + crack + thump + tail, rng, 1.0, 0.25)


def steam(t, rng, t0, dur, fc_from, fc_to, amp=1.0):
    """A jet of steam: bright band noise starting at t0, its colour sliding from fc_from to fc_to."""
    u = np.clip((t - t0) / dur, 0, 1)
    fc = fc_from + (fc_to - fc_from) * u
    env = np.where(t < t0, 0.0, np.minimum(1, (t - t0) / 0.015) * (1 - u) ** 1.3)
    return g.sweep(g.noise(len(t), rng), fc, "bp", 1.1) * env * amp + g.hp(g.noise(len(t), rng), 6000) * env * 0.25 * amp


def g_overheat(rng):
    """A warning double-beep, a metallic clank as the vents blow, then a long scream of steam."""
    t = g.tt(2.2)
    beeps = sum(np.sin(2 * np.pi * 1760 * t) * (g.env_ad(t, 0.004, 0.03, t0) * (t < t0 + 0.07)) for t0 in (0.0, 0.11)) * 0.35
    clank = g.metal_ring(t, rng, 420, 6, 0.25, 0.22) * 0.5 + g.thump(t, 160, 70, 0.02, 0.06, t0=0.22) * 0.6
    jet = steam(t, rng, 0.24, 1.9, 5200, 1800, 1.0)
    return g.reverb(beeps + clank + jet, rng, 0.8, 0.2, bright=6000)


def g_vent(rng):
    """A latch clack and a short hiss of steam."""
    t = g.tt(0.8)
    latch = g.click(t, rng, 0.0, 0.8) * 0.6 + g.thump(t, 220, 120, 0.01, 0.03) * 0.3
    hiss = steam(t, rng, 0.03, 0.6, 4200, 2200, 0.8)
    return g.reverb(latch + hiss, rng, 0.5, 0.15, bright=6000)


def g_vent_perfect(rng):
    """A clean bright hiss and a small two-note rising chime."""
    t = g.tt(1.0)
    latch = g.click(t, rng, 0.0, 1.1) * 0.5
    hiss = steam(t, rng, 0.02, 0.45, 6000, 3800, 0.6)
    chime = (g.bell_partials(len(t), 1318.5, [1, 2.0, 3.0], [0.35, 0.15, 0.08], [0.6, 0.25, 0.1], rng, t0=0.05, attack=0.004)
             + g.bell_partials(len(t), 1975.5, [1, 2.0, 3.0], [0.45, 0.18, 0.08], [0.7, 0.25, 0.1], rng, t0=0.14, attack=0.004))
    return g.reverb(latch + hiss + chime * 0.35, rng, 0.9, 0.25, bright=7000)


def whirr(t, rng, f, amp):
    """A spinning barrel cluster: a buzzy tone at f with its blade-pass harmonic and some air."""
    tone = g.saw(g.phase(f, len(t)), 2400, nh=10, roll=1.3) * 0.5 + np.sin(g.phase(f * 3, len(t))) * 0.25
    air = g.sweep(g.noise(len(t), rng), np.clip(f * 6, 300, 9000), "bp", 2.5) * 0.4
    return (g.lp(tone, 3500) + air) * amp


def g_spin_up(rng):
    """The barrels whirr up to speed in about 0.6 s, settling on a steady note."""
    t = g.tt(0.75)
    u = np.clip(t / 0.6, 0, 1)
    f = 40 + 260 * u ** 1.5
    amp = 0.25 + 0.75 * u ** 0.8
    amp[-g.ns(0.05):] *= np.linspace(1, 0.4, g.ns(0.05))
    clack = g.click(t, rng, 0.0, 0.7) * 0.4
    return g.reverb(whirr(t, rng, f, amp) + clack, rng, 0.4, 0.12)


def g_spin_down(rng):
    """The barrels coast down with a few ratchet clicks."""
    t = g.tt(1.0)
    u = np.clip(t / 0.9, 0, 1)
    f = 300 * (1 - u) ** 1.6 + 20
    amp = (1 - u) ** 1.2
    ratchet = sum(g.click(t, rng, t0, 0.6) * (0.4 - 0.25 * t0) for t0 in (0.35, 0.52, 0.66, 0.78, 0.88))
    return g.reverb(whirr(t, rng, f, amp) + ratchet, rng, 0.4, 0.12)


def edge_buzz(t, f, env):
    """The vibro edge's high buzz."""
    return (np.sin(g.phase(np.full(len(t), f), len(t))) * 0.5 + g.saw(g.phase(np.full(len(t), f * 0.5), len(t)), 4000, nh=8) * 0.3) * env


def g_flurry(rng):
    """Two quick whips of air over a high buzzing edge, the second sharper."""
    t = g.tt(0.55)
    whips = (g.whoosh(t, rng, 900 + 3200 * g.bell(t, 0.07, 0.02, 0.04), 1.6, g.bell(t, 0.07, 0.02, 0.05))
             + g.whoosh(t, rng, 1200 + 4200 * g.bell(t, 0.2, 0.02, 0.04), 1.8, g.bell(t, 0.2, 0.02, 0.05)) * 1.2)
    buzz = edge_buzz(t, 1480, g.bell(t, 0.07, 0.02, 0.05) + g.bell(t, 0.2, 0.02, 0.06)) * 0.3
    tick = g.metal_ring(t, rng, 3100, 4, 0.06, 0.21) * 0.25
    return g.reverb(whips + buzz + tick, rng, 0.4, 0.15)


def g_cleave(rng):
    """A broad heavy sweep with a low buzz, ending in a meaty thud."""
    t = g.tt(0.85)
    sweep = g.whoosh(t, rng, 400 + 1800 * g.bell(t, 0.18, 0.08, 0.08), 1.2, g.bell(t, 0.18, 0.08, 0.1)) * 1.1
    buzz = edge_buzz(t, 620, g.bell(t, 0.18, 0.08, 0.1)) * 0.35
    thud = g.thump(t, 150, 55, 0.03, 0.1, t0=0.27) * 0.9 + g.crack(t, rng, 0.27, 0.008, 700) * 0.4
    return g.reverb(sweep + buzz + thud, rng, 0.5, 0.15)


def zap(t, rng, t0, dur, amp=1.0):
    """An electric discharge: dense crackle and a buzzing 120 Hz family, starting at t0."""
    env = g.env_ad(t, 0.002, dur, t0)
    crackle = g.hp(g.crackle(len(t), 3000 * env + 50, rng), 2500) * env
    buzz = g.saw(g.phase(np.full(len(t), 120.0), len(t)), 5000, nh=30) * env * 0.4
    return (crackle + g.bp(buzz, 1800, 1.2)) * amp


def g_staff_charge(rng):
    """Two seconds of crackle and hum rising in density and pitch, ending in a small snap."""
    t = g.tt(2.1)
    u = np.clip(t / 2.0, 0, 1)
    hum = np.sin(g.phase(90 + 150 * u, len(t))) * 0.25 + g.saw(g.phase(180 + 300 * u, len(t)), 3000, nh=12) * 0.12
    crackle = g.hp(g.crackle(len(t), 40 + 900 * u ** 2, rng), 3000) * (0.3 + 0.7 * u)
    amp = 0.25 + 0.75 * u
    snap = zap(t, rng, 2.0, 0.05, 0.8)
    x = (hum + crackle) * amp + snap
    x[-g.ns(0.02):] *= np.linspace(1, 0, g.ns(0.02))
    return g.reverb(x, rng, 0.5, 0.15, bright=7000)


def g_staff_shock(rng):
    """A snapping discharge with a buzzing tail."""
    t = g.tt(0.9)
    snap = g.crack(t, rng, 0.0, 0.01, 2000) * 1.2
    body = zap(t, rng, 0.0, 0.25, 1.0)
    thump = g.thump(t, 200, 80, 0.015, 0.05) * 0.4
    return g.reverb(snap + body + thump, rng, 0.6, 0.2, bright=7000)


def g_staff_clash(rng):
    """A blade held on the emitters: a hard contact snap, a sizzle and a fading metallic ring."""
    t = g.tt(1.2)
    snap = g.crack(t, rng, 0.0, 0.006, 1500) * 1.1 + g.thump(t, 260, 110, 0.01, 0.04) * 0.5
    sizzle = zap(t, rng, 0.005, 0.35, 0.8)
    ring = g.metal_ring(t, rng, 760, 6, 0.6, 0.0) * 0.35
    return g.reverb(snap + sizzle + ring, rng, 0.9, 0.25, bright=6000)


def g_jet_burst(rng):
    """A short roar of thrust: an ignition pop, a rushing body that swells and a fading low rumble."""
    t = g.tt(1.1)
    pop = g.crack(t, rng, 0.0, 0.01, 400) * 0.8 + g.thump(t, 120, 45, 0.03, 0.08) * 0.7
    env = g.env_ad(t, 0.03, 0.35)
    roar = g.sweep(g.noise(len(t), rng), 500 + 1600 * env, "bp", 0.9) * env * 1.2
    rumble = g.lp(g.noise(len(t), rng), 160, 2) * g.env_ad(t, 0.04, 0.5) * 2.2
    crackle = g.hp(g.crackle(len(t), 300 * env + 10, rng), 3000) * env * 0.3
    return g.reverb(pop + roar + rumble + crackle, rng, 0.7, 0.18)


def g_jet_empty(rng):
    """A dry click and a little sputter of air: no charge left."""
    t = g.tt(0.45)
    click = g.click(t, rng, 0.0, 0.7) * 0.7
    sputter = sum(g.whoosh(t, rng, 900, 2.0, g.env_ad(t, 0.004, 0.025, t0)) * 0.35 for t0 in (0.06, 0.12, 0.17))
    return g.reverb(click + sputter, rng, 0.3, 0.1)


FILES = {
    "blaster.repeater.fire": {"blaster/repeater_fire1": make_repeater_fire(1.0), "blaster/repeater_fire2": make_repeater_fire(1.06)},
    "blaster.rifle.charged": {"blaster/rifle_charged": g_rifle_charged},
    "blaster.overheat": {"blaster/overheat": g_overheat},
    "blaster.vent": {"blaster/vent": g_vent},
    "blaster.vent_perfect": {"blaster/vent_perfect": g_vent_perfect},
    "blaster.repeater.spin_up": {"blaster/repeater_spin_up": g_spin_up},
    "blaster.repeater.spin_down": {"blaster/repeater_spin_down": g_spin_down},
    "vibro.flurry": {"vibro/flurry": g_flurry},
    "vibro.cleave": {"vibro/cleave": g_cleave},
    "electrostaff.charge": {"electrostaff/charge": g_staff_charge},
    "electrostaff.shock": {"electrostaff/shock": g_staff_shock},
    "electrostaff.clash": {"electrostaff/clash": g_staff_clash},
    "jetpack.burst": {"jetpack/burst": g_jet_burst},
    "jetpack.empty": {"jetpack/empty": g_jet_empty},
}
