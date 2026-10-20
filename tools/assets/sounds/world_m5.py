"""PLACEHOLDER sound recipes for the Milestone 5 planets (W3 worlds): the verse stones, Ghessar's
sandstorm, stormglass, the holdfast gate and the Hymnal's doors, the six biome beds, the Saltgate,
holdfast and Hymnal beds and the two planets' music.

Synthesised from scratch with the helpers in gen_sounds.py (filtered noise, formant-filtered saws,
inharmonic bell partials, oscillators, convolution reverb); nothing is sampled and nothing imitates a
recording or a film's sound. See tools/assets/sounds/events_m5.json for the event list and
docs/MILESTONE_5.md sections 3, 15.3 to 15.5 (and docs/design/planets_m5.md) for what each sound is
for. The file names are the Phase 0 placeholder names (<group>/<rest of the id with dots as
underscores>), so sounds.json and the subtitles do not change.

Every loop (the hum, the channel, the wind, the beds, the music) is built seamless: circular filters
and reverb, tones with a whole number of cycles, swells that repeat a whole number of times, events
placed circularly. Beds are 12 s and music 48 s, as in world_m4.
"""
import gen_sounds as g

np = g.np

BED = 12.0
MUSIC = 48.0
# Ghessar: a bright, open pentatonic on E (heat, distance). Tyrvann: a dark Aeolian on A with its
# flattened second (storm, old song).
GHESSAR_SCALE = [164.81, 185.0, 207.65, 246.94, 277.18, 329.63, 370.0, 415.3]
TYRVANN_SCALE = [110.0, 116.54, 130.81, 146.83, 164.81, 174.61, 196.0, 220.0]
# The verse's own chord (a low open fifth and its octave): the hum, the channel and the silence share it.
VERSE_CHORD = [98.0, 147.0, 196.0]


def tone(f, n, loop=True):
    """A sine at a fixed frequency (whole cycles when looping)."""
    return np.sin(g.phase(np.full(n, float(f)), n, loop=loop))


def loop_env(t, length, period, phase_=0.0):
    """A slow 0..1 swell that repeats a whole number of times over a loop of {length} seconds."""
    k = max(1, round(length / period))
    return 0.5 + 0.5 * np.sin(2 * np.pi * k * t / length + phase_)


def wind(t, rng, length, base, depth, q=None, period=6.0):
    """Seamless wind: noise through a filter whose cutoff swells slowly; q given = a whistling band."""
    n = len(t)
    fc = base + depth * loop_env(t, length, period) + depth * 0.4 * loop_env(t, length, period / 2.7, 1.3)
    if q is None:
        return g.sweep(g.noise(n, rng), fc, "lp", nb=10, loop=True)
    return g.sweep(g.noise(n, rng), fc, "bp", q, nb=10, loop=True)


def calls(rng, n, times, make):
    """Place short sounds circularly at the given times."""
    buf = np.zeros(n)
    for t0 in times:
        g.place_circular(buf, make(rng), t0)
    return buf


def voice(t, freqs, formants, loop=True, vibrato=0.004, length=None):
    """Wordless sung voices: saws with a slow vibrato through vowel formants (seamless when looping)."""
    n = len(t)
    out = np.zeros(n)
    for i, f in enumerate(freqs):
        if loop:
            k = 3 + i
            mod = 1 + vibrato * np.sin(2 * np.pi * k * t / length + i)
        else:
            mod = 1 + vibrato * np.sin(2 * np.pi * (4.6 + i * 0.7) * t + i)
        out += g.saw(g.phase(f * mod, n, loop=loop), f * 1.02, nh=24)
    shaped = np.zeros(n)
    for fc, q, a in formants:
        shaped += g.bp(out, fc, q, loop=loop) * a
    return shaped


VOWEL_OO = [(320, 3.0, 1.0), (800, 4.0, 0.35), (2400, 6.0, 0.08)]
VOWEL_AH = [(700, 3.0, 1.0), (1150, 4.0, 0.5), (2600, 6.0, 0.12)]


def rain(t, rng, rate, fc, hiss):
    """Seamless rain: dense soft drops (band-limited clicks) over a steady hiss."""
    n = len(t)
    drops = g.bp(g.crackle(n, rate, rng, loop=True), fc, 0.9, loop=True)
    return drops + g.bp(g.noise(n, rng), 5200, 0.8, loop=True) * hiss


def thunder(rng, dur=4.0, near=False):
    """A roll of thunder (filtered noise and crackle under a long swell); nearer = more crack."""
    d = g.tt(dur)
    roll = g.lp(g.noise(len(d), rng), 160 if near else 110) * g.bell(d, dur * 0.3, dur * 0.1, dur * 0.35) * 2.0
    rumble = g.lp(g.crackle(len(d), 90 * g.bell(d, dur * 0.25, dur * 0.1, dur * 0.2), rng), 500 if near else 280) * 2.0
    out = roll + rumble
    if near:
        out += g.crack(d, rng, dur * 0.18, 0.05, 1200) * 0.6
    return out


# ---------------------------------------------------------------- the verse stones

def g_verse_stone_hum(rng):
    """Seamless 4 s: the stone sings to itself, a low hum of wordless voices on an open fifth, breathing."""
    L = 4.0
    t = g.tt(L)
    n = len(t)
    choir = voice(t, VERSE_CHORD, VOWEL_OO, length=L) * (0.75 + 0.25 * loop_env(t, L, 4.0))
    under = (tone(49, n) * 0.5 + tone(98.5, n) * 0.25) * (0.8 + 0.2 * loop_env(t, L, 2.0, 1.0))
    air = g.lp(g.noise(n, rng), 300, loop=True) * 0.25
    return g.reverb(choir * 0.5 + under + air, rng, 2.2, 0.45, bright=2500, loop=True)


def g_verse_stone_pulse(rng):
    """The slowing hum: the air thumps outward, the chord swells through it and fades."""
    t = g.tt(2.4)
    n = len(t)
    whump = g.thump(t, 70, 32, 0.12, 0.4) * 1.2
    push = g.whoosh(t, rng, 200 + 500 * np.exp(-t / 0.25), 1.0, g.env_ad(t, 0.01, 0.3)) * 0.8
    swell = voice(t, VERSE_CHORD, VOWEL_AH, loop=False) * g.bell(t, 0.5, 0.18, 0.7) * 0.6
    return g.reverb(whump + push + swell, rng, 2.4, 0.4, bright=2200)


def g_verse_stone_channel(rng):
    """Seamless 3 s: the verse pulled toward the player, a rising, beating resonance and a breath through it."""
    L = 3.0
    t = g.tt(L)
    n = len(t)
    # Pairs a few hertz apart beat at whole numbers of cycles over the loop.
    beat = tone(392, n) + tone(396, n) * 0.9 + (tone(588, n) + tone(593.33, n) * 0.8) * 0.5 + tone(196, n) * 0.6
    breath = g.sweep(g.noise(n, rng), 900 + 1400 * loop_env(t, L, 1.5), "bp", 3.0, nb=10, loop=True) * 0.5
    motes = g.hp(g.crackle(n, 30, rng, loop=True), 5000, loop=True) * 0.3
    return g.reverb(beat * 0.35 * (0.7 + 0.3 * loop_env(t, L, 0.75)) + breath + motes, rng, 1.6, 0.4, bright=5000, loop=True)


def g_verse_stone_silence(rng):
    """The verse falls silent: the chord sinks an octave and closes down, grit sifts, the stone settles."""
    t = g.tt(3.2)
    n = len(t)
    glide = 1 - 0.5 * (1 - np.exp(-t / 0.9))
    choir = np.zeros(n)
    for i, f in enumerate(VERSE_CHORD):
        choir += g.saw(g.phase(f * glide, n), f, nh=24)
    choir = g.sweep(choir, 1400 * np.exp(-t / 0.8) + 120, "lp", nb=10) * g.env_ad(t, 0.02, 0.9) * 0.6
    grit = g.bp(g.crackle(n, 400 * np.exp(-t / 0.6), rng), 2600, 1.0) * 0.6
    settle = g.thump(t, 90, 45, 0.08, 0.3, t0=1.6) * 0.6
    return g.reverb(choir + grit + settle, rng, 2.6, 0.45, bright=2000)


# ---------------------------------------------------------------- weather and the strike

def g_sandstorm_wind(rng):
    """Seamless 8 s: a heavy wind howling through sand, whistling bands, hissing grains and grit."""
    L = 8.0
    t = g.tt(L)
    n = len(t)
    gusts = loop_env(t, L, 4.0) * 0.6 + loop_env(t, L, 1.6, 0.7) * 0.4
    body = wind(t, rng, L, 260, 900, period=4.0) * 1.6
    howl = wind(t, rng, L, 520, 300, q=9, period=8.0) * 0.45 + wind(t, rng, L, 840, 260, q=11, period=2.7) * 0.25
    sand = g.bp(g.noise(n, rng), 5200, 0.9, loop=True) * (0.3 + 0.7 * gusts) * 0.5
    grit = g.hp(g.crackle(n, 600 * (0.3 + 0.7 * gusts), rng, loop=True), 2500, loop=True) * 0.35
    return body + howl + sand + grit


def g_stormglass_form(rng):
    """Static snaps across the struck rock, then glass pushes up chiming."""
    t = g.tt(1.9)
    n = len(t)
    snaps = g.hp(g.crackle(n, 1600 * np.exp(-t / 0.18), rng), 2200) * 1.2
    buzz = g.bp(g.saw(g.phase(np.full(n, 120.0), n), 120, nh=30), 1800, 1.5) * g.env_ad(t, 0.005, 0.12) * 0.4
    glass = np.zeros(n)
    for k, f in enumerate((1180.0, 1490.0, 1760.0, 2210.0)):
        glass += g.bell_partials(n, f, g.CHIME_RATIOS[:3], [0.6, 0.3, 0.15], [1, 0.4, 0.15], rng, t0=0.12 + k * 0.07) * (0.7 - k * 0.1)
    grind = g.bp(g.noise(n, rng), 3000, 2.0) * g.bell(t, 0.25, 0.05, 0.12) * 0.3
    return g.reverb(snaps + buzz + glass * 0.5 + grind, rng, 1.6, 0.35, bright=7000)


# ---------------------------------------------------------------- doors

def g_holdfast_gate(rng):
    """Clan Hask's gate: heavy riveted plates scrape along their runners and shut against the stop."""
    t = g.tt(2.2)
    n = len(t)
    slide = g.bell(t, 0.8, 0.25, 0.5)
    scrape = g.sweep(g.noise(n, rng), 300 + 500 * slide, "bp", 4.0, nb=10) * slide * 1.2
    chatter = g.bp(g.crackle(n, 120 * slide, rng), 1100, 1.5) * 0.8
    ring = g.metal_ring(t, rng, 180, 6, 0.6, t0=1.35) * 0.6 + g.metal_ring(t, rng, 410, 5, 0.3, t0=1.36) * 0.3
    clank = g.thump(t, 110, 55, 0.05, 0.25, t0=1.35) * 0.9
    return g.reverb(scrape + chatter + ring + clank, rng, 1.4, 0.35, bright=3500)


def g_hymnal_door(rng):
    """The Hymnal's stone: a slab grinds aside over grit and settles with a deep thud."""
    t = g.tt(2.8)
    n = len(t)
    move = g.bell(t, 1.0, 0.35, 0.6)
    grind = g.lp(g.noise(n, rng), 260) * move * 2.0
    judder = g.lp(g.crackle(n, 45 * move, rng), 700) * 1.5
    grit = g.bp(g.crackle(n, 300 * move, rng), 3000, 1.0) * 0.35
    thud = g.thump(t, 80, 38, 0.08, 0.5, t0=1.9) * 1.0
    return g.reverb(grind + judder + grit + thud, rng, 2.4, 0.45, bright=1800)


# ---------------------------------------------------------------- Ghessar's beds (seamless, 12 s)

def g_ambient_ghessar_dunes(rng):
    """The dune sea: a warm, steady wind, sand hissing off the crests and trickling down."""
    t = g.tt(BED)
    n = len(t)
    air = wind(t, rng, BED, 220, 420, period=6.0) * 1.2
    hiss = g.bp(g.noise(n, rng), 4600, 1.0, loop=True) * (0.3 + 0.7 * loop_env(t, BED, 6.0, 0.6)) * 0.25
    trickle = g.hp(g.crackle(n, 300 * loop_env(t, BED, 4.0, 2.0) ** 3, rng, loop=True), 3000, loop=True) * 0.4
    return air + hiss + trickle


def g_ambient_ghessar_saltflat(rng):
    """The salt flats: thin high air over a vast emptiness and the crust ticking in the heat."""
    t = g.tt(BED)
    n = len(t)
    air = wind(t, rng, BED, 160, 220, period=12.0) * 0.8
    thin = wind(t, rng, BED, 1500, 400, q=7, period=6.0) * 0.12
    ticks = g.hp(g.crackle(n, 3.0, rng, loop=True), 4000, loop=True) * 2.0
    return air + thin + g.reverb(ticks, rng, 0.8, 0.4, bright=8000, loop=True)


def g_ambient_ghessar_canyons(rng):
    """The canyons: wind moaning through the narrows, a far rockfall echoing, a hawk-like cry high up."""
    t = g.tt(BED)
    n = len(t)
    moan = wind(t, rng, BED, 240, 120, q=12, period=6.0) * 0.8 + wind(t, rng, BED, 370, 160, q=10, period=4.0) * 0.45
    air = wind(t, rng, BED, 180, 300) * 0.8

    def rockfall(r):
        d = g.tt(1.4)
        return g.lp(g.crackle(len(d), 200 * g.bell(d, 0.3, 0.1, 0.4), r), 1600) * 1.5 + g.thump(d, 90, 50, 0.05, 0.2, t0=0.1) * 0.3

    def cry(r):
        d = g.tt(0.9)
        f = 1900 - 500 * (d / 0.9) + 60 * np.sin(2 * np.pi * 9 * d)
        return np.sin(g.phase(f, len(d))) * g.bell(d, 0.25, 0.08, 0.35)

    far = g.reverb(calls(rng, n, [4.0], rockfall) * 0.5 + calls(rng, n, [9.2], cry) * 0.12, rng, 2.8, 0.7, loop=True, bright=3000)
    return moan + air + far


# ---------------------------------------------------------------- Tyrvann's beds (seamless, 12 s)

def g_ambient_tyrvann_stormwood(rng):
    """The storm jungle: rain on broad leaves, gusts through the canopy and thunder rolling far off."""
    t = g.tt(BED)
    n = len(t)
    leaves = rain(t, rng, 1800, 3200, 0.25) * 0.8
    canopy = wind(t, rng, BED, 300, 650, period=4.0) * 1.0
    drips = g.bp(g.crackle(n, 6.0, rng, loop=True), 1400, 3.0, loop=True) * 1.2
    far = np.zeros(n)
    g.place_circular(far, thunder(rng), 6.0)
    return leaves + canopy + drips + g.reverb(far, rng, 3.0, 0.6, loop=True, bright=500) * 0.6


def g_ambient_tyrvann_mire(rng):
    """The black mire: rain on still water, mud bubbling, low croaks and a thin insect whine."""
    t = g.tt(BED)
    n = len(t)
    water = rain(t, rng, 700, 1600, 0.15) * 0.8

    def bubble(r):
        d = g.tt(0.25)
        f = 110 + 260 * (d / 0.25)
        return np.sin(g.phase(f, len(d))) * g.env_ad(d, 0.01, 0.06)

    def croak(r):
        d = g.tt(0.5)
        buzz = g.pulse(g.phase(np.full(len(d), r.uniform(22, 30)), len(d)), 30, 0.2, nh=30)
        return g.bp(buzz, r.uniform(350, 450), 2.0) * g.bell(d, 0.2, 0.06, 0.15)

    bubbles = calls(rng, n, [0.7, 0.95, 3.3, 6.1, 6.25, 9.8], bubble) * 0.5
    croaks = calls(rng, n, [2.0, 2.6, 7.4, 10.9], croak) * 0.6
    insects = g.bp(g.noise(n, rng), 4200, 9, loop=True) * (0.5 + 0.5 * tone(5, n)) * 0.08
    murk = g.lp(g.noise(n, rng), 140, loop=True) * 0.8
    return water + g.lp(bubbles, 900, loop=True) + croaks + insects + murk


def g_ambient_tyrvann_basalt(rng):
    """The basalt ridges: a hard wind over bare rock, rain driven sideways, static and nearer thunder."""
    t = g.tt(BED)
    n = len(t)
    air = wind(t, rng, BED, 280, 800, period=4.0) * 1.3
    whistle = wind(t, rng, BED, 950, 400, q=9, period=3.0) * 0.3
    sheets = rain(t, rng, 1200, 4200, 0.4) * 0.6 * (0.6 + 0.4 * loop_env(t, BED, 4.0))
    static = g.hp(g.crackle(n, 120 * loop_env(t, BED, 6.0, 1.0) ** 4, rng, loop=True), 4000, loop=True) * 0.4
    near = np.zeros(n)
    g.place_circular(near, thunder(rng, 3.5, near=True), 8.0)
    return air + whistle + sheets + static + g.reverb(near, rng, 2.4, 0.5, loop=True, bright=900) * 0.7


# ---------------------------------------------------------------- the hub and Hymnal beds (seamless, 12 s)

def g_ambient_saltgate(rng):
    """Saltgate: dry wind over the port, a docked freighter's idle hum, far clanks and a murmur of traders."""
    t = g.tt(BED)
    n = len(t)
    air = wind(t, rng, BED, 240, 400) * 0.8
    idle = (g.lp(g.saw(g.phase(np.full(n, 55.0), n, loop=True), 55, nh=12), 300, loop=True) * 0.4 + tone(110, n) * 0.1) \
        * (0.7 + 0.3 * loop_env(t, BED, 6.0))
    clanks = np.zeros(n)
    for t0 in (2.8, 8.4, 8.75):
        g.place_circular(clanks, g.metal_ring(g.tt(0.9), rng, rng.uniform(380, 720), 5, 0.25), t0)
    murmur = np.zeros(n)
    for t0 in (0.5, 3.9, 6.2, 9.6):
        dur = rng.uniform(1.4, 2.2)
        g.place_circular(murmur, g.whisper_voice(dur, rng, scale=1.0) * g.bell(g.tt(dur), dur / 2, dur * 0.35), t0)
    far = g.reverb(clanks * 0.2 + g.lp(murmur, 1100, loop=True) * 0.25, rng, 2.0, 0.6, loop=True, bright=2600)
    return air + idle * 0.5 + far


def g_ambient_holdfast(rng):
    """The holdfast: rain drumming on hide roofs, a fire crackling, the clan's voices low under the storm."""
    t = g.tt(BED)
    n = len(t)
    roofs = g.bp(g.crackle(n, 500, rng, loop=True), 900, 1.2, loop=True) * 0.9 + g.bp(g.noise(n, rng), 3000, 0.8, loop=True) * 0.1
    fire = g.bp(g.crackle(n, 25, rng, loop=True), 2200, 1.0, loop=True) * 0.8 + g.lp(g.noise(n, rng), 200, loop=True) * 0.4
    voices = np.zeros(n)
    for t0 in (1.2, 5.5, 9.1):
        dur = rng.uniform(1.6, 2.4)
        g.place_circular(voices, g.whisper_voice(dur, rng, scale=0.8) * g.bell(g.tt(dur), dur / 2, dur * 0.35), t0)
    gusts = wind(t, rng, BED, 220, 400, period=6.0) * 0.6
    return roofs + fire + g.lp(voices, 900, loop=True) * 0.25 + gusts


def g_ambient_hymnal(rng):
    """The Hymnal: a deep stone hush, a far choir held on one breath, slow drips into still water."""
    t = g.tt(BED)
    n = len(t)
    hush = (tone(55, n) + tone(55.5, n) * 0.8 + tone(82.5, n) * 0.3) * 0.25 * (0.7 + 0.3 * loop_env(t, BED, 12.0))
    choir = voice(t, [110.0, 165.0, 220.0], VOWEL_OO, length=BED, vibrato=0.003) * (0.4 + 0.6 * loop_env(t, BED, 6.0, 0.5)) * 0.25
    drips = np.zeros(n)
    for t0 in (1.1, 4.7, 7.3, 10.4):
        d = g.tt(0.4)
        f = rng.uniform(1700, 2600)
        drips = g.place_circular(drips, np.sin(g.phase(f * (1 + 0.3 * np.exp(-d / 0.02)), len(d))) * g.env_ad(d, 0.001, 0.05), t0)
    air = g.lp(g.noise(n, rng), 180, loop=True) * 0.5
    return g.reverb(hush + choir + drips * 0.3 + air, rng, 4.0, 0.55, loop=True, bright=2200)


# ---------------------------------------------------------------- music (seamless, 48 s)

def pad(t, rng, freqs, amp_period, bright):
    """A soft, slowly breathing chord of detuned sines and a filtered saw."""
    n = len(t)
    x = np.zeros(n)
    for f in freqs:
        for det in (0.0, 0.35):
            x += tone(f + det, n)
        x += g.lp(g.saw(g.phase(np.full(n, f), n, loop=True), f, nh=8), bright, loop=True) * 0.25
    return x * (0.6 + 0.4 * loop_env(t, MUSIC, amp_period))


def plucks(rng, n, scale, starts, f_mult):
    """Plucked-string notes from the scale (harmonic partials, quick decay), placed circularly."""
    buf = np.zeros(n)
    for t0 in starts:
        f = scale[rng.integers(len(scale))] * f_mult
        note = g.bell_partials(len(g.tt(1.6)), f, [1, 2, 3, 4, 5], [0.7, 0.45, 0.3, 0.2, 0.12], [1, 0.5, 0.3, 0.18, 0.1], rng, attack=0.003)
        g.place_circular(buf, note, t0)
    return buf


def g_music_ghessar(rng):
    """Ghessar: a wide drone on E under heat-haze, a sparse plucked line and a soft frame drum."""
    t = g.tt(MUSIC)
    n = len(t)
    drone = pad(t, rng, [82.41, 123.47, 164.81], 16.0, 700)
    starts = [k * 1.5 + 0.25 + rng.uniform(0, 0.15) for k in range(32) if rng.random() < 0.45]
    line = plucks(rng, n, GHESSAR_SCALE, starts, 1.0)
    drum = np.zeros(n)
    for k in range(32):
        d = g.tt(0.5)
        accent = 1.0 if k % 4 == 0 else 0.45 if k % 2 == 0 else 0.25
        hit = g.thump(d, 140, 70, 0.03, 0.18) * accent + g.bp(g.noise(len(d), rng), 1800, 1.5) * g.env_ad(d, 0.001, 0.02) * 0.25 * accent
        # Off the loop point, so the seam never falls on a hit.
        g.place_circular(drum, hit, k * 1.5 + 0.25)
    shimmer = g.bp(g.noise(n, rng), 6000, 2.0, loop=True) * 0.05 * loop_env(t, MUSIC, 12.0)
    return g.reverb(drone * 0.22 + line * 0.35 + drum * 0.35 + shimmer, rng, 3.2, 0.45, loop=True, bright=5000)


def g_music_tyrvann(rng):
    """Tyrvann: a dark drone on A, a slow low choir moving between two chords, thunder far behind it."""
    t = g.tt(MUSIC)
    n = len(t)
    drone = pad(t, rng, [55.0, 82.41, 110.0], 24.0, 400)
    half = (np.sin(2 * np.pi * t / MUSIC) > 0).astype(float)
    smooth = g.lp(half, 0.3, loop=True)
    chord_a = voice(t, [110.0, 130.81, 164.81], VOWEL_AH, length=MUSIC, vibrato=0.003)
    chord_b = voice(t, [116.54, 146.83, 174.61], VOWEL_AH, length=MUSIC, vibrato=0.003)
    choir = (chord_a * smooth + chord_b * (1 - smooth)) * (0.5 + 0.5 * loop_env(t, MUSIC, 12.0))
    beat = np.zeros(n)
    for k in range(24):
        d = g.tt(0.7)
        beat = g.place_circular(beat, g.thump(d, 70, 40, 0.05, 0.3) * (1.0 if k % 2 == 0 else 0.6), k * 2.0 + 0.5)
    storm = np.zeros(n)
    for t0 in (9.0, 31.0):
        g.place_circular(storm, thunder(rng, 5.0), t0)
    return g.reverb(drone * 0.25 + choir * 0.12 + beat * 0.4 + storm * 0.35, rng, 3.5, 0.45, loop=True, bright=2500)


FILES = {
    "verse_stone.hum": {"verse_stone/hum": g_verse_stone_hum},
    "verse_stone.pulse": {"verse_stone/pulse": g_verse_stone_pulse},
    "verse_stone.channel": {"verse_stone/channel": g_verse_stone_channel},
    "verse_stone.silence": {"verse_stone/silence": g_verse_stone_silence},
    "sandstorm.wind": {"sandstorm/wind": g_sandstorm_wind},
    "stormglass.form": {"stormglass/form": g_stormglass_form},
    "holdfast.gate": {"holdfast/gate": g_holdfast_gate},
    "hymnal.door": {"hymnal/door": g_hymnal_door},
    "music.ghessar": {"music/ghessar": g_music_ghessar},
    "music.tyrvann": {"music/tyrvann": g_music_tyrvann},
    "ambient.ghessar.dunes": {"ambient/ghessar_dunes": g_ambient_ghessar_dunes},
    "ambient.ghessar.saltflat": {"ambient/ghessar_saltflat": g_ambient_ghessar_saltflat},
    "ambient.ghessar.canyons": {"ambient/ghessar_canyons": g_ambient_ghessar_canyons},
    "ambient.tyrvann.stormwood": {"ambient/tyrvann_stormwood": g_ambient_tyrvann_stormwood},
    "ambient.tyrvann.mire": {"ambient/tyrvann_mire": g_ambient_tyrvann_mire},
    "ambient.tyrvann.basalt": {"ambient/tyrvann_basalt": g_ambient_tyrvann_basalt},
    "ambient.saltgate": {"ambient/saltgate": g_ambient_saltgate},
    "ambient.holdfast": {"ambient/holdfast": g_ambient_holdfast},
    "ambient.hymnal": {"ambient/hymnal": g_ambient_hymnal},
}
