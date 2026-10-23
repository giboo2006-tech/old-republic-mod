"""PLACEHOLDER sound recipes for Milestone 5 space (W2): ship cannons and turrets, the fighters' guns, missiles and
their lock and warning tones, shield and hull hits, shields failing and recharging, the fighters' engines (the Hymn
interceptor's singing one), explosions, the gunship's drone, generator and launches, the comm, the scan, the nav buoy,
the orbit's ambience and the orbit music.

Synthesised from scratch with the helpers in gen_sounds.py (oscillators, filtered noise, sweeps, partials, thumps);
nothing is sampled or imitated from any film or game. See events_m5.json for the event list and docs/MILESTONE_5.md
15.1 and 15.4 for each sound's intent. File names are the placeholder names (<group>/<rest>), so sounds.json does not
change when these replace them. Loops are seamless (loop=True filters and phases, circular placement).

    python3 tools/assets/gen_sounds.py --m5 --feature space
"""
import gen_sounds as g

np = g.np


# ---------------------------------------------------------------- building blocks

def laser(rng, f0, f1, tau, dur, bright=6000, comb_hz=340.0, thump_amount=0.6, grit=0.25):
    """A ship laser: a saw-and-sine tone diving fast from f0 to f1, a springy metallic comb twang, a short
    thump of the gun's recoil and a crack at the muzzle."""
    t = g.tt(dur)
    n = len(t)
    f = f1 + (f0 - f1) * np.exp(-t / tau)
    ph = g.phase(f, n)
    tone = (g.saw(ph, f0, nh=12) * 0.6 + np.sin(ph) * 0.8) * g.env_ad(t, 0.002, dur * 0.22)
    twang = g.comb(tone, 1.0 / comb_hz, 0.6) * 0.55
    x = g.lp(tone + twang, bright)
    x += g.thump(t, 190, 48, 0.03, 0.09) * thump_amount
    x += g.crack(t, rng, 0.0, 0.004, 2500) * grit
    return x


def beep(dur, f, w=0.35, fade=0.006):
    """A short pulse-wave beep with soft edges."""
    t = g.tt(dur)
    n = len(t)
    x = g.pulse(g.phase(np.full(n, f), n), f, w=w, nh=12) * 0.5 + np.sin(g.phase(np.full(n, f), n)) * 0.5
    env = np.minimum(1, np.minimum(t / fade, (dur - t) / fade))
    return x * np.clip(env, 0, 1)


def blast(rng, dur, low, high, debris, rumble_tau):
    """An explosion in the films' manner (sound in space): a bright noise burst closing to a rumble, a deep thump
    and a crackle of debris."""
    t = g.tt(dur)
    n = len(t)
    burst = g.sweep(g.noise(n, rng), high * np.exp(-t / 0.18) + low, "lp")
    burst *= g.env_ad(t, 0.004, rumble_tau)
    thump = g.thump(t, 110, 32, 0.05, rumble_tau * 1.2) * 1.4
    crackle = g.bp(g.crackle(n, debris * np.exp(-t / (dur * 0.4)), rng), 3200, 0.8) * 0.8
    return burst + thump + crackle


def whine(t, f, nh=8):
    n = len(t)
    return g.saw(g.phase(f, n), float(np.max(f)), nh=nh)


# ---------------------------------------------------------------- weapons

def g_cannon_fire1(rng):
    """The ship's cannons: a heavy, punchy bolt with a metallic spring and a recoil thump."""
    x = laser(rng, 2600, 220, 0.045, 0.45, 6000, 340, 0.8)
    return g.reverb(x, rng, 0.5, 0.15, bright=5000)


def g_cannon_fire2(rng):
    x = laser(rng, 2400, 260, 0.05, 0.45, 5600, 310, 0.85)
    return g.reverb(x, rng, 0.5, 0.15, bright=5000)


def g_cannon_overheat(rng):
    """The barrels vent: a hiss of coolant, two falling warning beeps and the tick of hot metal."""
    t = g.tt(1.4)
    n = len(t)
    hiss = g.bp(g.noise(n, rng), 5200, 0.8) * g.env_ad(t, 0.06, 0.45) * 0.7
    hiss += g.bp(g.noise(n, rng), 2400, 1.2) * g.env_ad(t, 0.02, 0.2) * 0.3
    beeps = np.zeros(n)
    g.place(beeps, beep(0.12, 880), 0.02)
    g.place(beeps, beep(0.16, 640), 0.2)
    ticks = g.metal_ring(t, rng, 2300, 5, 0.08, t0=0.5) * 0.3 + g.metal_ring(t, rng, 2650, 5, 0.06, t0=0.85) * 0.2
    return g.reverb(hiss + beeps * 0.35 + ticks, rng, 0.7, 0.2, bright=6000)


def g_turret_fire(rng):
    """The dorsal turret: a lighter double zap."""
    t = g.tt(0.5)
    x = np.zeros(len(t))
    g.place(x, laser(rng, 3200, 420, 0.03, 0.3, 7000, 520, 0.35, 0.2), 0.0)
    g.place(x, laser(rng, 3000, 400, 0.03, 0.3, 7000, 500, 0.3, 0.15) * 0.75, 0.08)
    return g.reverb(x, rng, 0.45, 0.15, bright=6000)


def g_fighter_fire1(rng):
    """A fighter's lasers, heard across the dark: thinner, with more room around them."""
    x = laser(rng, 2200, 380, 0.035, 0.35, 4500, 450, 0.3, 0.15)
    return g.reverb(x, rng, 0.9, 0.3, bright=4000)


def g_fighter_fire2(rng):
    x = laser(rng, 1900, 340, 0.04, 0.35, 4200, 420, 0.3, 0.15)
    return g.reverb(x, rng, 0.9, 0.3, bright=4000)


def g_missile_lock_tone(rng):
    """Seamless 2 s: the seeker's tone, a steady train of short beeps (the client raises its pitch as the lock
    builds and holds it high once locked)."""
    L = 2.0
    buf = np.zeros(g.ns(L))
    for k in range(8):
        g.place_circular(buf, beep(0.07, 1040, 0.4), k * L / 8)
    return buf


def g_missile_locked(rng):
    """Locked: a rising two-tone chirp, the second tone ringing down (clear, not shrill)."""
    t = g.tt(0.6)
    x = np.zeros(len(t))
    g.place(x, beep(0.1, 1500, 0.4) * np.exp(-g.tt(0.1) / 0.08), 0.0)
    g.place(x, beep(0.38, 2000, 0.4) * np.exp(-g.tt(0.38) / 0.14), 0.11)
    return x


def g_missile_launch(rng):
    """The rack lets go: a clunk, the motor's crack and a roaring rush that tears away."""
    t = g.tt(1.6)
    n = len(t)
    clunk = g.thump(t, 160, 60, 0.02, 0.09) * 0.9 + g.metal_ring(t, rng, 520, 5, 0.1) * 0.25
    ignite = g.crack(t, rng, 0.06, 0.012, 1200) * 0.8
    roar_fc = 500 + 2600 * g.bell(t, 0.25, 0.08, 0.5)
    roar = g.sweep(g.noise(n, rng), roar_fc, "lp") * g.env_ad(t, 0.05, 0.5, 0.06) * 1.2
    rush = g.whoosh(t, rng, 2400 * np.exp(-t / 0.5) + 500, 1.6, g.env_ad(t, 0.08, 0.4, 0.06)) * 0.5
    return g.reverb(clunk + ignite + roar + rush, rng, 0.8, 0.2, bright=5000)


def g_missile_warning(rng):
    """Seamless 1 s: the incoming-missile alarm, two tones alternating fast, each beep falling away before the next
    (urgent without being a solid wall of square wave)."""
    L = 1.0
    buf = np.zeros(g.ns(L))
    for k in range(8):
        g.place_circular(buf, beep(0.11, 1280 if k % 2 else 960, 0.4) * np.exp(-g.tt(0.11) / 0.05), k * L / 8)
    return buf


# ---------------------------------------------------------------- hits and shields

def g_bolt_hit_shield(rng):
    """A bolt splashing on a shield: a crackling fizz that rings high, over a soft low whump."""
    t = g.tt(0.9)
    n = len(t)
    ring = g.bell_partials(n, 2400, [1.0, 1.41, 1.83, 2.6], [0.25, 0.18, 0.12, 0.08], [0.8, 0.5, 0.4, 0.25], rng, attack=0.002) * 0.5
    fizz = g.sweep(g.noise(n, rng), 6500 * np.exp(-t / 0.12) + 1200, "bp", 1.8) * g.env_ad(t, 0.002, 0.15)
    sparks = g.bp(g.crackle(n, 900 * np.exp(-t / 0.1), rng), 5000, 1.0) * 0.6
    whump = g.thump(t, 140, 60, 0.04, 0.12) * 0.6
    return g.reverb(ring + fizz + sparks + whump, rng, 0.7, 0.2, bright=7000)


def g_bolt_hit_hull(rng):
    """A bolt into plating: a clang, a spray of sparks and a dull thud through the frame."""
    t = g.tt(1.0)
    n = len(t)
    clang = g.metal_ring(t, rng, 420, 7, 0.22) * 0.7
    crack = g.crack(t, rng, 0.0, 0.006, 1500) * 0.9
    sparks = g.bp(g.crackle(n, 1400 * np.exp(-t / 0.12), rng), 4200, 0.9) * 0.7
    thud = g.thump(t, 120, 45, 0.03, 0.15) * 0.9
    return g.reverb(clang + crack + sparks + thud, rng, 0.6, 0.18, bright=5000)


def g_shield_down(rng):
    """The shield fails: its whine falls away through crackling discharges and ends in a dead clunk."""
    t = g.tt(1.7)
    n = len(t)
    f = 120 + 1300 * np.exp(-t / 0.35)
    tone = g.lp(whine(t, f), 2500) * g.env_ad(t, 0.01, 0.6) * 0.6
    zaps = g.bp(g.crackle(n, 300 * np.exp(-t / 0.5), rng), 2800, 0.7) * 1.0
    clunk = g.thump(t, 100, 40, 0.03, 0.2, t0=1.0) * 0.8
    return g.reverb(tone + zaps + clunk, rng, 0.9, 0.25, bright=5000)


def g_shield_recharge(rng):
    """The shield comes back: a rising, brightening whine with a shimmer and a soft chime as it settles."""
    t = g.tt(1.6)
    n = len(t)
    f = 180 + 1400 * (1 - np.exp(-t / 0.5))
    tone = g.lp(whine(t, f, 6), 3000) * np.clip(t / 0.3, 0, 1) * np.exp(-np.maximum(t - 1.0, 0) / 0.2) * 0.5
    shimmer = g.bp(g.noise(n, rng), 6000, 3) * np.clip(t / 1.0, 0, 1) * np.exp(-np.maximum(t - 1.0, 0) / 0.15) * 0.3
    chime = g.bell_partials(n, 1760, [1.0, 2.0, 3.0], [0.5, 0.25, 0.12], [0.5, 0.25, 0.12], rng, t0=1.0) * 0.5
    return g.reverb(tone + shimmer + chime, rng, 1.0, 0.25, bright=7000)


# ---------------------------------------------------------------- engines

def g_fighter_engine(rng):
    """Seamless 3 s: a fighter's engine, a rushing roar under a hard twin whine that wavers."""
    L = 3.0
    t = g.tt(L)
    n = len(t)
    roar = g.lp(g.noise(n, rng), 900, loop=True) * 0.9 + g.bp(g.noise(n, rng), 2600, 2, loop=True) * 0.25
    wob = 1 + 0.01 * np.sin(2 * np.pi * 3 * t / L * 3)
    whine_a = g.saw(g.phase(420 * wob, n, loop=True), 430, nh=8) * 0.35
    whine_b = np.sin(g.phase(np.full(n, 846.0), n, loop=True)) * 0.15
    return roar + whine_a + whine_b


def g_hymn_interceptor_engine(rng):
    """Seamless 4 s: the Hymn interceptor's engine sings: a vowel-shaped wail with a slow vibrato and a fainter voice a
    fifth above, over a thin rushing roar."""
    L = 4.0
    t = g.tt(L)
    n = len(t)
    vib = 1 + 0.016 * np.sin(2 * np.pi * 5 * t / L * 4)
    voice = g.saw(g.phase(330 * vib, n, loop=True), 340, nh=24)
    upper = g.saw(g.phase(495 * vib, n, loop=True), 510, nh=16) * 0.4
    sung = voice + upper
    formants = g.bp(sung, 700, 4, loop=True) + 0.7 * g.bp(sung, 1150, 5, loop=True) + 0.35 * g.bp(sung, 2650, 6, loop=True)
    swell = 0.8 + 0.2 * np.sin(2 * np.pi * t / L)
    breath = g.bp(g.noise(n, rng), 1800, 1.5, loop=True) * 0.08
    roar = g.lp(g.noise(n, rng), 700, loop=True) * 0.35
    return formants * swell * 0.8 + breath + roar


def g_gunship_hum(rng):
    """Seamless 4 s: the gunship's drone, two low beating saws, a throb and the whine of machinery far inside."""
    L = 4.0
    t = g.tt(L)
    n = len(t)
    low = g.saw(g.phase(np.full(n, 55.0), n, loop=True), 55, nh=20) + g.saw(g.phase(np.full(n, 55.5), n, loop=True), 56, nh=20)
    low += 0.5 * g.saw(g.phase(np.full(n, 82.5), n, loop=True), 83, nh=12)
    low = g.lp(low, 420, loop=True)
    throb = 0.75 + 0.25 * np.sin(2 * np.pi * 2 * t / L)
    machine = np.sin(g.phase(np.full(n, 1760.0), n, loop=True)) * 0.03 + g.bp(g.noise(n, rng), 900, 3, loop=True) * 0.08
    return low * throb * 0.6 + machine


# ---------------------------------------------------------------- destruction

def g_fighter_explode(rng):
    """A fighter blows apart: a bright burst closing to a rumble, a deep thump and debris crackling away."""
    x = blast(rng, 2.4, 260, 6500, 900, 0.45)
    return g.reverb(x, rng, 1.4, 0.3, bright=4500)


def g_gunship_generator_down(rng):
    """The shield generator dies: a long power-down whine through heavy arcing, a thump and a last zap."""
    t = g.tt(2.6)
    n = len(t)
    f = 40 + 880 * np.exp(-t / 0.6)
    tone = g.lp(whine(t, f, 10), 1800) * g.env_ad(t, 0.02, 1.0) * 0.6
    arcs = g.bp(g.crackle(n, 500 * np.exp(-t / 0.8), rng), 2400, 0.6) * 1.2
    thump = g.thump(t, 90, 30, 0.05, 0.5, t0=0.25) * 1.0
    zap = g.crack(t, rng, 1.6, 0.03, 1800) * 0.6
    return g.reverb(tone + arcs + thump + zap, rng, 1.6, 0.3, bright=4500)


def g_gunship_explode(rng):
    """The gunship's end: a huge blast, secondary explosions rolling through the hull and a long rumble."""
    t = g.tt(4.6)
    n = len(t)
    x = blast(rng, 4.6, 140, 5000, 700, 1.1) * 1.2
    for t0, amp in ((0.45, 0.6), (0.95, 0.5), (1.7, 0.4)):
        sub = blast(rng, 1.6, 220, 4000, 500, 0.35) * amp
        g.place(x, sub, t0)
    rumble = g.lp(g.noise(n, rng), 160) * g.env_ad(t, 0.2, 1.6) * 1.2
    return g.reverb(x + rumble, rng, 2.2, 0.35, bright=3500)


def g_gunship_launch(rng):
    """The bay opens and fighters drop out: hydraulic clunks, a hiss of venting, two engines spooling up."""
    t = g.tt(2.0)
    n = len(t)
    clunks = g.thump(t, 130, 50, 0.02, 0.12) * 0.9 + g.thump(t, 120, 45, 0.02, 0.12, t0=0.25) * 0.8
    clunks += g.metal_ring(t, rng, 300, 6, 0.2) * 0.25
    hiss = g.bp(g.noise(n, rng), 3200, 1.0) * g.env_ad(t, 0.05, 0.3, 0.1) * 0.5
    spool = np.zeros(n)
    for t0 in (0.55, 0.8):
        ts = g.tt(1.0)
        f = 300 + 700 * (1 - np.exp(-ts / 0.3))
        spool_one = g.lp(whine(ts, f, 6), 3000) * g.env_ad(ts, 0.1, 0.4) * 0.4
        spool_one += g.lp(g.noise(len(ts), rng), 900) * g.env_ad(ts, 0.05, 0.4) * 0.5
        g.place(spool, spool_one, t0)
    return g.reverb(clunks + hiss + spool, rng, 1.2, 0.3, bright=4500)


# ---------------------------------------------------------------- comm, scan, buoy

def g_comm(rng):
    """The comm opens: a click, a burst of static and the squelch closing."""
    t = g.tt(0.45)
    n = len(t)
    click = g.crack(t, rng, 0.0, 0.002, 2000) * 0.8 + g.crack(t, rng, 0.3, 0.002, 2500) * 0.5
    static = g.bp(g.noise(n, rng), 1800, 0.7) * g.env_ad(t, 0.01, 0.12, 0.01) * 0.6
    static += g.bp(g.crackle(n, 2000 * np.exp(-t / 0.15), rng), 3000, 0.8) * 0.4
    squelch = beep(0.05, 2400, 0.5) * 0.15
    out = click + static
    g.place(out, squelch, 0.32)
    return out


def g_scan(rng):
    """Seamless 2 s: a scanner sweeping over the ship, two rising chirps a second with a faint warble."""
    L = 2.0
    buf = np.zeros(g.ns(L))
    for k in range(2):
        ts = g.tt(0.55)
        f = 600 + 800 * (ts / 0.55)
        chirp = np.sin(g.phase(f, len(ts))) * g.env_ad(ts, 0.02, 0.18)
        g.place_circular(buf, chirp, k * L / 2)
    n = len(buf)
    t = np.arange(n) / g.SR
    warble = np.sin(g.phase(np.full(n, 2200.0), n, loop=True)) * (0.04 + 0.03 * np.sin(2 * np.pi * 8 * t / L))
    return g.reverb(buf, rng, 0.6, 0.25, loop=True, bright=6000) + warble


def g_buoy(rng):
    """The nav buoy's ping: a clear bell tone that rings out into the dark."""
    t = g.tt(1.8)
    n = len(t)
    ping = g.bell_partials(n, 1600, [1.0, 2.0, 3.01], [0.6, 0.3, 0.15], [1.0, 0.35, 0.15], rng, attack=0.003)
    return g.reverb(ping, rng, 1.6, 0.35, bright=8000)


# ---------------------------------------------------------------- ambience and music

def g_ambient_space(rng):
    """Seamless 16 s: the void heard from the cockpit: a low hull hum, the air recyclers' hiss, the frame creaking
    now and then and far electronic chirps."""
    L = 16.0
    t = g.tt(L)
    n = len(t)
    hum = (np.sin(g.phase(np.full(n, 48.0), n, loop=True)) + 0.5 * np.sin(g.phase(np.full(n, 72.0), n, loop=True))) * 0.25
    hum *= 0.85 + 0.15 * np.sin(2 * np.pi * 2 * t / L)
    air = g.lp(g.noise(n, rng), 600, loop=True) * 0.35
    creaks = np.zeros(n)
    for k, at in enumerate((2.1, 6.8, 11.5)):
        dur = rng.uniform(0.3, 0.6)
        tc = g.tt(dur)
        c = g.bp(g.noise(len(tc), rng), rng.uniform(300, 700), 6) * np.sin(np.pi * tc / dur) ** 2
        g.place_circular(creaks, c * 0.6, at)
    chirps = np.zeros(n)
    for at in (4.4, 9.2, 14.0):
        tc = g.tt(0.12)
        f = rng.uniform(2200, 3200)
        g.place_circular(chirps, np.sin(g.phase(np.full(len(tc), f), len(tc))) * np.sin(np.pi * tc / 0.12) * 0.05, at)
    return hum + air + g.reverb(creaks + chirps, rng, 1.5, 0.4, loop=True, bright=4000)


def midi(note):
    return 440.0 * 2 ** ((note - 69) / 12)


def pad(freqs, dur, release=3.0, bright=900):
    t = g.tt(dur + release)
    n = len(t)
    x = np.zeros(n)
    for f in freqs:
        for det in (0.996, 1.004):
            x += g.saw(g.phase(np.full(n, f * det), n), f, nh=10)
    env = np.clip(t / 2.0, 0, 1) * np.where(t > dur, np.exp(-(t - dur) / (release / 3)), 1.0)
    return g.lp(x, bright) * env / (2 * len(freqs))


def g_music_space(rng):
    """"Orbit" (seamless, 32 s, D minor, no beat): slow pads drifting through four chords, a sub drone, and a sparse
    bell figure like distant beacons."""
    L = 32.0
    chords = [[50, 53, 57, 64], [46, 50, 53, 57], [43, 46, 50, 57], [45, 50, 52, 57]]
    buf = np.zeros(g.ns(L))
    for i, chord in enumerate(chords):
        g.place_circular(buf, pad([midi(m) for m in chord], 8.0) * 0.9, i * 8.0)
    n = len(buf)
    t = np.arange(n) / g.SR
    drone = np.sin(g.phase(np.full(n, midi(38)), n, loop=True)) * (0.18 + 0.05 * np.sin(2 * np.pi * t / L))
    bells = np.zeros(n)
    figure = [(1.0, 74), (3.0, 77), (5.5, 81), (9.0, 70), (11.0, 74), (17.0, 79), (19.5, 74), (25.0, 76), (27.0, 81), (29.5, 76)]
    for at, note in figure:
        tb = g.tt(3.0)
        bell_x = g.bell_partials(len(tb), midi(note), [1.0, 2.76, 5.4], [1.2, 0.5, 0.2], [0.6, 0.15, 0.05], rng, attack=0.004)
        g.place_circular(bells, bell_x * 0.25, at)
    return g.reverb(buf + drone + bells, rng, 3.5, 0.45, loop=True, bright=4500)


FILES = {
	"space.cannon.fire": {"space/cannon_fire1": g_cannon_fire1, "space/cannon_fire2": g_cannon_fire2},
	"space.cannon.overheat": {"space/cannon_overheat": g_cannon_overheat},
	"space.turret.fire": {"space/turret_fire": g_turret_fire},
	"space.fighter.fire": {"space/fighter_fire1": g_fighter_fire1, "space/fighter_fire2": g_fighter_fire2},
	"space.missile.lock_tone": {"space/missile_lock_tone": g_missile_lock_tone},
	"space.missile.locked": {"space/missile_locked": g_missile_locked},
	"space.missile.launch": {"space/missile_launch": g_missile_launch},
	"space.missile.warning": {"space/missile_warning": g_missile_warning},
	"space.bolt.hit_shield": {"space/bolt_hit_shield": g_bolt_hit_shield},
	"space.bolt.hit_hull": {"space/bolt_hit_hull": g_bolt_hit_hull},
	"space.shield.down": {"space/shield_down": g_shield_down},
	"space.shield.recharge": {"space/shield_recharge": g_shield_recharge},
	"space.fighter.engine": {"space/fighter_engine": g_fighter_engine},
	"space.hymn_interceptor.engine": {"space/hymn_interceptor_engine": g_hymn_interceptor_engine},
	"space.fighter.explode": {"space/fighter_explode": g_fighter_explode},
	"space.gunship.hum": {"space/gunship_hum": g_gunship_hum},
	"space.gunship.generator_down": {"space/gunship_generator_down": g_gunship_generator_down},
	"space.gunship.explode": {"space/gunship_explode": g_gunship_explode},
	"space.gunship.launch": {"space/gunship_launch": g_gunship_launch},
	"space.comm": {"space/comm": g_comm},
	"space.scan": {"space/scan": g_scan},
	"space.buoy": {"space/buoy": g_buoy},
	"ambient.space": {"ambient/space": g_ambient_space},
	"music.space": {"music/space": g_music_space},
}
