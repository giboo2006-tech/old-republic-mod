"""PLACEHOLDER sound recipes for the Milestone 4 planets (W1 worlds): waystones, the Heartstone, the
expeditions, the six biome beds, the two hub beds and the two planets' music.

Synthesised from scratch with the helpers in gen_sounds.py (filtered noise, inharmonic bell partials,
oscillators, convolution reverb); nothing is sampled. See tools/assets/sounds/events_m4.json for the
event list and docs/MILESTONE_4.md sections 3.2, 3.3, 13.3 and 13.4 (and docs/design/planets.md) for
what each sound is for. The file names are the Phase 0 placeholder names
(<group>/<rest of the id with dots as underscores>), so sounds.json and the subtitles do not change.

Every loop (waystone.corrupt, the beds, the music) is built seamless: circular filters and reverb,
tones with a whole number of cycles, events placed circularly.
"""
import gen_sounds as g

np = g.np

# A Dorian scale on D for Selvaren (cool, old, open), a Phrygian one on C for Varkhast (dry, severe).
SELVAREN_SCALE = [146.83, 164.81, 174.61, 196.0, 220.0, 246.94, 261.63, 293.66]
VARKHAST_SCALE = [130.81, 138.59, 155.56, 174.61, 196.0, 207.65, 233.08, 261.63]


def tone(f, n, loop=True):
    """A sine at a fixed frequency (whole cycles when looping)."""
    return np.sin(g.phase(np.full(n, float(f)), n, loop=loop))


def loop_env(t, length, period, phase_=0.0):
    """A slow 0..1 swell that repeats a whole number of times over the loop."""
    k = max(1, round(length / period))
    return 0.5 + 0.5 * np.sin(2 * np.pi * k * t / length + phase_)


def chirp(rng, f0, f1, dur, wob=0.0):
    """A short bird-like call: a sine glide with a little warble (original, not a recording)."""
    t = g.tt(dur)
    f = f0 + (f1 - f0) * (t / dur) + wob * np.sin(2 * np.pi * 28 * t)
    return np.sin(g.phase(f, len(t))) * g.bell(t, dur * 0.4, dur * 0.18, dur * 0.3)


# ---------------------------------------------------------------- waystones

def g_waystone_corrupt(rng):
    """Seamless 4 s: a low beating drone with a slow throb every 2 s (the veins' pulse), and grit."""
    L = 4.0
    t = g.tt(L)
    n = len(t)
    drone = tone(55, n) + tone(58.27, n) * 0.8 + tone(82.4, n) * 0.25 + tone(116.5, n) * 0.12
    throb = loop_env(t, L, 2.0, -np.pi / 2) ** 3
    grit = g.bp(g.crackle(n, 30, rng, loop=True), 900, 1.2, loop=True) * 1.5
    breath = g.lp(g.noise(n, rng), 220, loop=True) * (0.4 + 0.6 * throb) * 1.8
    return g.reverb(drone * (0.45 + 0.55 * throb) * 0.6 + breath + grit * 0.4, rng, 1.6, 0.35, bright=1800, loop=True)


def g_waystone_cleanse(rng):
    """The darkness drains out of the stone (a falling, filtered rumble) as a clear tone rises through it."""
    t = g.tt(2.6)
    n = len(t)
    drain = g.sweep(g.noise(n, rng), 600 * np.exp(-t / 0.7) + 60, "lp", nb=10) * g.env_ad(t, 0.05, 0.6) * 2.2
    low = g.thump(t, 70, 40, 0.3, 0.5) * 0.5
    rise = g.bell_partials(n, 220, [1, 2.0, 3.01, 4.2], [1.4, 1.0, 0.6, 0.4], [1, 0.4, 0.2, 0.1], rng, t0=0.35, attack=0.4)
    shimmer = g.bp(g.noise(n, rng), 5000, 3) * g.bell(t, 0.9, 0.3, 0.6) * 0.15
    return g.reverb(drain + low + rise * 0.7 + shimmer, rng, 2.4, 0.4, bright=6000)


def g_waystone_hum(rng):
    """A low, bell-like hum that blooms and fades: the stone's own voice."""
    t = g.tt(3.6)
    n = len(t)
    hum = g.bell_partials(n, 110, [1, 2.0, 2.76, 4.07, 5.4], [2.2, 1.6, 1.0, 0.6, 0.4], [1, 0.5, 0.3, 0.15, 0.08], rng,
                          attack=0.35, detune=0.003)
    beat = 1 + 0.12 * np.sin(2 * np.pi * 1.3 * t)
    air = g.lp(g.noise(n, rng), 400) * g.bell(t, 0.6, 0.3, 1.2) * 0.15
    return g.reverb(hum * beat + air, rng, 2.6, 0.4, bright=4000)


# ---------------------------------------------------------------- Heartstone

def g_heartstone_wave(rng):
    """Corruption wells up from the ground: a deep surge, a sub thump and a dissonant swell."""
    t = g.tt(2.8)
    n = len(t)
    surge = g.sweep(g.noise(n, rng), 80 + 500 * g.bell(t, 0.8, 0.45, 0.9), "lp", nb=12) * g.bell(t, 0.8, 0.45, 0.9) * 3.0
    sub = g.thump(t, 50, 28, 0.25, 0.7, t0=0.2) * 0.9
    dis = (np.sin(g.phase(np.full(n, 61.7), n)) + np.sin(g.phase(np.full(n, 65.4), n)) * 0.8) * g.bell(t, 1.0, 0.5, 0.8) * 0.4
    grit = g.bp(g.crackle(n, 80 * g.bell(t, 0.9, 0.4, 0.6), rng), 1200, 1.0) * 1.2
    return g.reverb(surge + sub + dis + grit, rng, 2.0, 0.35, bright=2500)


def g_heartstone_cleansed(rng):
    """A long exhale: a reversed swell opens into a warm, open chord that rings away."""
    t = g.tt(5.0)
    n = len(t)
    breath = g.sweep(g.noise(n, rng), 300 + 1500 * g.bell(t, 0.9, 0.6, 1.2), "bp", 1.2, nb=10) * g.bell(t, 0.9, 0.6, 1.4) * 0.9
    chord = np.zeros(n)
    for f, a in ((98.0, 1.0), (147.0, 0.7), (196.0, 0.55), (293.7, 0.3), (392.0, 0.18)):
        chord += g.bell_partials(n, f, [1, 2.0, 3.0], [2.8, 1.8, 1.0], [a, a * 0.3, a * 0.12], rng, t0=0.7, attack=0.6, detune=0.002)
    return g.reverb(breath + chord * 0.5, rng, 3.2, 0.45, bright=5500)


# ---------------------------------------------------------------- expeditions

def g_hollow_chime(rng):
    """One Hollow crystal at its root pitch (the client pitches it to the tone): bright, glassy, long."""
    t = g.tt(2.6)
    ratios = [r * (1 + rng.uniform(-0.01, 0.01)) for r in g.CHIME_RATIOS]
    x = g.bell_partials(len(t), 659.3, ratios, [1.6, 0.9, 0.5, 0.3, 0.2], [1, 0.4, 0.25, 0.12, 0.06], rng, attack=0.004)
    x += g.bell_partials(len(t), 329.6, [1, 2.0], [1.2, 0.6], [0.25, 0.1], rng, attack=0.02)
    x += g.crack(t, rng, 0, 0.002, 7000) * 0.06
    return g.reverb(x, rng, 2.8, 0.5, bright=7500)


def g_hollow_answer(rng):
    """The Hollow's crystals answer together: a cascade of chimes settling on an open chord."""
    t = g.tt(4.4)
    n = len(t)
    x = np.zeros(n)
    notes = [659.3, 784.0, 987.8, 1318.5, 880.0, 1174.7, 1568.0]
    for i in range(22):
        t0 = 0.05 + 1.3 * (i / 22) ** 0.8 + rng.uniform(-0.02, 0.02)
        f = notes[rng.integers(len(notes))] * rng.choice([0.5, 1.0])
        x += g.bell_partials(n, f, g.CHIME_RATIOS[:3], [0.9, 0.5, 0.25], [1, 0.3, 0.12], rng, t0=max(t0, 0)) * rng.uniform(0.2, 0.45)
    chord = sum(g.bell_partials(n, f, g.CHIME_RATIOS, [2.2, 1.2, 0.6, 0.35, 0.2], [1, 0.4, 0.25, 0.12, 0.06], rng, t0=1.45)
                for f in (329.6, 493.9, 659.3))
    return g.reverb(x + chord * 0.45, rng, 3.0, 0.5, bright=7500)


def g_rift_whisper(rng):
    """Breathy, wordless whispers over a low glassy drone (about 2.4 s; the client repeats it while held)."""
    dur = 2.4
    t = g.tt(dur)
    n = len(t)
    voice = g.whisper_voice(dur, rng, scale=0.85) * 1.4 + g.whisper_voice(dur, rng, scale=1.1, t_start=0.4) * 0.8
    glass = g.bell_partials(n, 196.0, [1, 2.76, 5.4], [1.5, 0.9, 0.5], [0.3, 0.15, 0.08], rng, attack=0.5, detune=0.004)
    drone = (np.sin(g.phase(np.full(n, 49.0), n)) + 0.6 * np.sin(g.phase(np.full(n, 51.9), n))) * g.bell(t, 1.2, 0.8, 0.8) * 0.3
    return g.reverb(voice + glass + drone, rng, 2.2, 0.5, bright=3500)


def g_rift_bleed(rng):
    """The crystal turns: a glass crack, a groan sliding down and a dark bloom that rings out."""
    t = g.tt(3.0)
    n = len(t)
    crack = g.crack(t, rng, 0.02, 0.012, 1500) * 1.4
    shards = np.zeros(n)
    for _ in range(14):
        t0 = 0.02 + rng.exponential(0.08)
        shards += g.metal_ring(t, rng, rng.uniform(2200, 6500), 4, 0.06, t0) * rng.uniform(0.1, 0.35)
    groan_f = 160 * np.exp(-t / 1.4) + 45
    groan = g.lp(g.saw(g.phase(groan_f, n), 200, nh=12), 500) * g.env_ad(t, 0.15, 0.9) * 0.6
    bloom = g.bell_partials(n, 98.0, [1, 1.5, 2.76], [2.0, 1.2, 0.7], [0.6, 0.3, 0.15], rng, t0=0.25, attack=0.3)
    return g.reverb(crack + shards + groan + bloom, rng, 2.4, 0.4, bright=4000)


# ---------------------------------------------------------------- biome beds (seamless, 12 s)

BED = 12.0


def wind(t, rng, base, depth, q=None, period=6.0, lowpass=True):
    """Seamless wind: noise through a filter whose cutoff swells slowly; q given = a whistling band."""
    n = len(t)
    fc = base + depth * loop_env(t, BED, period) + depth * 0.4 * loop_env(t, BED, period / 2.7, 1.3)
    if q is None:
        return g.sweep(g.noise(n, rng), fc, "lp" if lowpass else "bp", nb=10, loop=True)
    return g.sweep(g.noise(n, rng), fc, "bp", q, nb=10, loop=True)


def rustle(t, rng, rate, fc):
    """Leaves or grass: dense soft clicks, band-limited, swelling with the wind."""
    n = len(t)
    return g.bp(g.crackle(n, rate * (0.4 + 0.6 * loop_env(t, BED, 6.0)), rng, loop=True), fc, 0.8, loop=True)


def calls(rng, n, times, make):
    """Place short calls circularly at the given times."""
    buf = np.zeros(n)
    for t0 in times:
        g.place_circular(buf, make(rng), t0)
    return buf


def g_ambient_selvaren_highwood(rng):
    """Wind high in the silver trees, leaves, a few birds calling far off."""
    t = g.tt(BED)
    n = len(t)
    air = wind(t, rng, 300, 500) * 1.2
    leaves = rustle(t, rng, 900, 3800) * 0.9
    birds = calls(rng, n, [1.3, 1.55, 4.8, 7.2, 7.42, 10.1],
                  lambda r: chirp(r, r.uniform(2600, 3400), r.uniform(3200, 4200), r.uniform(0.08, 0.16), 120))
    birds = g.reverb(birds, rng, 1.5, 0.5, loop=True, bright=6000) * 0.25
    return air + leaves + birds


def g_ambient_selvaren_meadow(rng):
    """An open upland breeze over heather, grass hiss and a thin insect hum."""
    t = g.tt(BED)
    n = len(t)
    air = wind(t, rng, 450, 700, period=4.0) * 1.3
    grass = rustle(t, rng, 1500, 5200) * 0.7
    insects = g.bp(g.noise(n, rng), 4600, 8, loop=True) * (0.6 + 0.4 * tone(7, n)) * 0.15
    lark = calls(rng, n, [2.4, 2.6, 2.75, 8.9],
                 lambda r: chirp(r, r.uniform(3600, 4400), r.uniform(4000, 5000), r.uniform(0.05, 0.09), 200))
    return air + grass + insects + g.reverb(lark, rng, 1.0, 0.4, loop=True) * 0.15


def g_ambient_selvaren_tarn(rng):
    """Water lapping at a stone shore in the fog, reeds, a long low call across the lake."""
    t = g.tt(BED)
    n = len(t)
    laps = np.zeros(n)
    for k in range(9):
        t0 = k * BED / 9 + rng.uniform(-0.2, 0.2)
        d = g.tt(0.9)
        lap = g.lp(g.noise(len(d), rng), 700) * g.env_ad(d, 0.08, 0.25) * rng.uniform(0.6, 1.0)
        g.place_circular(laps, lap, t0)
    reeds = rustle(t, rng, 500, 2600) * 0.5
    fog = wind(t, rng, 200, 200, period=12.0) * 0.8

    def call(r):
        d = g.tt(1.6)
        f = 520 + 140 * np.sin(np.pi * d / 1.6)
        return np.sin(g.phase(f, len(d))) * g.bell(d, 0.7, 0.35, 0.5)

    distant = g.reverb(g.lp(calls(rng, n, [6.5], call), 1800, loop=True), rng, 3.0, 0.7, loop=True, bright=2500) * 0.12
    return laps * 0.9 + reeds + fog + distant


def g_ambient_varkhast_redrock(rng):
    """Dry wind over red rock: a whistle through the hoodoos and grit skittering."""
    t = g.tt(BED)
    n = len(t)
    air = wind(t, rng, 250, 600) * 1.3
    whistle = wind(t, rng, 900, 500, q=9, period=4.0) * 0.35
    grit = g.bp(g.crackle(n, 240 * (0.3 + 0.7 * loop_env(t, BED, 4.0)), rng, loop=True), 2400, 1.0, loop=True) * 0.7
    return air + whistle + grit


def g_ambient_varkhast_ashfall(rng):
    """Ash hissing as it falls: a soft, steady high hiss over a low wind, with faint crackle."""
    t = g.tt(BED)
    n = len(t)
    hiss = g.bp(g.noise(n, rng), 6000, 0.9, loop=True) * (0.8 + 0.2 * loop_env(t, BED, 3.0)) * 0.35
    air = wind(t, rng, 160, 260, period=12.0) * 1.1
    crackle = g.hp(g.crackle(n, 120, rng, loop=True), 3000, loop=True) * 0.3
    return hiss + air + crackle


def g_ambient_varkhast_tomb_valley(rng):
    """Wind moaning along the carved valley, and far thunder that brings no rain."""
    t = g.tt(BED)
    n = len(t)
    moan = wind(t, rng, 180, 160, q=12, period=6.0) * 0.9 + wind(t, rng, 270, 200, q=10, period=4.0) * 0.5
    air = wind(t, rng, 150, 250) * 0.9
    d = g.tt(4.0)
    roll = g.lp(g.noise(len(d), rng), 120) * g.bell(d, 1.2, 0.4, 1.4) * 2.0 + g.lp(g.crackle(len(d), 60 * g.bell(d, 1.0, 0.3, 0.8), rng), 300) * 2
    thunder = np.zeros(n)
    g.place_circular(thunder, roll, 5.0)
    return moan + air + g.reverb(thunder, rng, 2.5, 0.6, loop=True, bright=600) * 0.6


# ---------------------------------------------------------------- hub beds (seamless, 12 s)

def g_ambient_greenwatch(rng):
    """The enclave stirs: wind in the clearing, a distant mallet on wood, a faint chime, birds."""
    t = g.tt(BED)
    n = len(t)
    air = wind(t, rng, 350, 400) * 0.9
    leaves = rustle(t, rng, 500, 3400) * 0.5
    taps = np.zeros(n)
    for t0 in (2.0, 2.45, 2.9, 7.5, 7.95):
        d = g.tt(0.15)
        tap = g.bp(g.noise(len(d), rng), 900, 3) * g.env_ad(d, 0.001, 0.02) + g.thump(d, 220, 160, 0.01, 0.03) * 0.5
        g.place_circular(taps, tap, t0)
    chime = calls(rng, n, [5.2], lambda r: g.bell_partials(len(g.tt(2.5)), 880, g.CHIME_RATIOS[:3], [1.2, 0.6, 0.3], [1, 0.3, 0.1], r))
    birds = calls(rng, n, [0.6, 9.3, 9.5], lambda r: chirp(r, r.uniform(2800, 3500), r.uniform(3300, 4100), 0.12, 100))
    far = g.reverb(g.lp(taps, 2500, loop=True) * 0.25 + chime * 0.08 + birds * 0.15, rng, 1.8, 0.6, loop=True, bright=4000)
    return air + leaves + far


def g_ambient_proving(rng):
    """The Proving: wind off the cliff, a distant tannoy (wordless, filtered), boots and clanks far off."""
    t = g.tt(BED)
    n = len(t)
    air = wind(t, rng, 220, 400) * 0.9

    def tannoy(r):
        dur = 1.8
        d = g.tt(dur)
        voice = g.whisper_voice(dur, r, scale=1.0) + g.bp(g.saw(g.phase(np.full(len(d), 120.0), len(d)), 120, nh=20), 900, 2) * 0.3
        return g.bp(g.sat(voice * 2.0, 2.0), 1400, 1.4) * g.bell(d, dur / 2, dur * 0.4)

    pa = calls(rng, n, [1.5, 7.8], tannoy)
    clanks = np.zeros(n)
    for t0 in (4.2, 10.6):
        clanks = g.place_circular(clanks, g.metal_ring(g.tt(0.8), rng, rng.uniform(500, 900), 5, 0.2), t0)
    march = np.zeros(n)
    for k in range(16):
        d = g.tt(0.1)
        step = g.lp(g.noise(len(d), rng), 500) * g.env_ad(d, 0.002, 0.02)
        g.place_circular(march, step, 3.0 + k * 0.5)
    far = g.reverb(pa * 0.25 + clanks * 0.15 + march * 0.2, rng, 2.4, 0.7, loop=True, bright=2200)
    return air + g.lp(far, 3000, loop=True)


# ---------------------------------------------------------------- music (seamless, 48 s)

MUSIC = 48.0


def pad(t, rng, freqs, amp_period, bright):
    """A soft, slowly breathing chord of detuned sines and a filtered saw."""
    n = len(t)
    x = np.zeros(n)
    for f in freqs:
        for det in (0.0, 0.35):
            x += tone(f + det, n)
        x += g.lp(g.saw(g.phase(np.full(n, f), n, loop=True), f, nh=8), bright, loop=True) * 0.25
    return x * (0.6 + 0.4 * loop_env(t, MUSIC, amp_period))


def melody(rng, n, scale, starts, length, f_mult, partials):
    """Sparse bell-like notes from the scale, placed circularly."""
    buf = np.zeros(n)
    for t0 in starts:
        f = scale[rng.integers(len(scale))] * f_mult
        note = g.bell_partials(len(g.tt(length)), f, partials, [length * 0.5, length * 0.3, length * 0.15],
                               [1, 0.3, 0.1], rng, attack=0.02)
        g.place_circular(buf, note, t0)
    return buf


def g_music_selvaren(rng):
    """Selvaren: an open Dorian pad that shifts between two chords, with a slow, sparse bell melody."""
    t = g.tt(MUSIC)
    n = len(t)
    half = (np.sin(2 * np.pi * t / MUSIC) > 0).astype(float)
    smooth = g.lp(half, 0.3, loop=True)
    chord_a = pad(t, rng, [73.42, 110.0, 146.83, 220.0], 12.0, 900)
    chord_b = pad(t, rng, [65.41, 98.0, 130.81, 196.0], 12.0, 900)
    harmony = chord_a * smooth + chord_b * (1 - smooth)
    starts = [k * 3.0 + rng.uniform(0, 0.4) for k in range(16) if rng.random() < 0.7]
    bells = melody(rng, n, SELVAREN_SCALE, starts, 3.0, 2.0, [1, 2.0, 3.01])
    air = g.lp(g.noise(n, rng), 300, loop=True) * 0.2
    return g.reverb(harmony * 0.25 + bells * 0.35 + air, rng, 3.5, 0.45, loop=True, bright=4500)


def g_music_varkhast(rng):
    """Varkhast: a low Phrygian drone, a slow frame-drum pulse and a severe low brass-like line."""
    t = g.tt(MUSIC)
    n = len(t)
    drone = pad(t, rng, [65.41, 98.0, 130.81], 16.0, 500)
    drums = np.zeros(n)
    for k in range(24):
        d = g.tt(0.6)
        hit = g.thump(d, 90, 50, 0.04, 0.25) * (1.0 if k % 4 == 0 else 0.5) + g.lp(g.noise(len(d), rng), 600) * g.env_ad(d, 0.002, 0.04) * 0.3
        g.place_circular(drums, hit, k * 2.0)
    line = np.zeros(n)
    for k in range(6):
        f = VARKHAST_SCALE[rng.integers(len(VARKHAST_SCALE))] / 2
        d = g.tt(5.0)
        note = g.lp(g.saw(g.phase(np.full(len(d), f), len(d)), f, nh=16), 700) * g.bell(d, 2.0, 1.0, 1.6)
        g.place_circular(line, note, k * 8.0 + 1.0)
    return g.reverb(drone * 0.25 + drums * 0.5 + line * 0.25, rng, 3.0, 0.4, loop=True, bright=3000)


FILES = {
    "waystone.corrupt": {"waystone/corrupt": g_waystone_corrupt},
    "waystone.cleanse": {"waystone/cleanse": g_waystone_cleanse},
    "waystone.hum": {"waystone/hum": g_waystone_hum},
    "heartstone.wave": {"heartstone/wave": g_heartstone_wave},
    "heartstone.cleansed": {"heartstone/cleansed": g_heartstone_cleansed},
    "hollow.chime": {"hollow/chime": g_hollow_chime},
    "hollow.answer": {"hollow/answer": g_hollow_answer},
    "rift.whisper": {"rift/whisper": g_rift_whisper},
    "rift.bleed": {"rift/bleed": g_rift_bleed},
    "ambient.selvaren.highwood": {"ambient/selvaren_highwood": g_ambient_selvaren_highwood},
    "ambient.selvaren.meadow": {"ambient/selvaren_meadow": g_ambient_selvaren_meadow},
    "ambient.selvaren.tarn": {"ambient/selvaren_tarn": g_ambient_selvaren_tarn},
    "ambient.varkhast.redrock": {"ambient/varkhast_redrock": g_ambient_varkhast_redrock},
    "ambient.varkhast.ashfall": {"ambient/varkhast_ashfall": g_ambient_varkhast_ashfall},
    "ambient.varkhast.tomb_valley": {"ambient/varkhast_tomb_valley": g_ambient_varkhast_tomb_valley},
    "ambient.greenwatch": {"ambient/greenwatch": g_ambient_greenwatch},
    "ambient.proving": {"ambient/proving": g_ambient_proving},
    "music.selvaren": {"music/selvaren": g_music_selvaren},
    "music.varkhast": {"music/varkhast": g_music_varkhast},
}
