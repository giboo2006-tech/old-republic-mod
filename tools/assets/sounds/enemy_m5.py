"""PLACEHOLDER sound recipes for the Milestone 5 foes and bosses (W8): the cartel enforcers, the Clan
Hask warriors, the Hymnsworn, the sand scours, the stormbacks, Darth Ulvaine and her Hymn, the First
Cantor and the Long Hymn, their two music loops, and the hologram's flicker.

Synthesised from scratch with the helpers in gen_sounds.py (oscillators, formant-filtered saws,
filtered noise and crackle, inharmonic partials, convolution reverb); nothing is sampled, and no voice,
score or effect from any film or game is imitated. See events_m5.json for the event list, and
docs/MILESTONE_5.md 12 and 15.3 to 15.4 (and docs/design/foes_m5.md) for each sound's use. File names
are the Phase 0 placeholder names (<group>/<rest of the id with dots as underscores>), so sounds.json
and the subtitles do not change.

Tells last as long as their tells (MILESTONE_5.md 12): hask.slam_tell 20 ticks, hask.bash_tell 15,
hymnsworn.call_tell 40, scour.burst_tell 15, stormback.charge_tell 20, stormback.roar 30,
boss.ulvaine.step_tell 15, boss.ulvaine.rod_charge 30, boss.cantor.beam_tell 40, boss.cantor.slam_tell
20, boss.cantor.mark 40 and boss.cantor.ring_tell 30.

Every loop is seamless (circular filters and reverb, tones with whole cycles, swells that repeat a
whole number of times, events placed circularly). The creature and boss loops are 3 to 4 seconds (they
are not streamed); the two music loops are eight bars each and stream.

    python3 tools/assets/gen_sounds.py --m5 --feature enemy_m5
"""
import gen_sounds as g

np = g.np

# Vowel formants (centre, q, gain) for the voices.
AH = [(720, 4.0, 1.0), (1150, 5.0, 0.5), (2600, 6.0, 0.14)]
OH = [(470, 4.0, 1.0), (820, 5.0, 0.5), (2500, 6.0, 0.1)]
EH = [(540, 4.0, 1.0), (1850, 5.0, 0.42), (2500, 6.0, 0.14)]
OO = [(330, 3.0, 1.0), (800, 4.0, 0.32), (2400, 6.0, 0.07)]
# The same, sung higher (Ulvaine and the choir's upper voices).
AH_HI = [(850, 4.0, 1.0), (1300, 5.0, 0.5), (2900, 6.0, 0.16)]
OO_HI = [(400, 3.0, 1.0), (900, 4.0, 0.35), (2700, 6.0, 0.08)]


# ---------------------------------------------------------------- building blocks

def midi(n):
    return 440.0 * 2 ** ((n - 69) / 12)


def tone(f, n, loop=False):
    """A sine at f (a number or a curve); whole cycles when looping."""
    return np.sin(g.phase(f, n, loop=loop))


def swell(t, length, k, ph=0.0):
    """A 0..1 swell repeating k whole times over a loop of {length} seconds."""
    return 0.5 + 0.5 * np.sin(2 * np.pi * k * t / length + ph)


def gate(t, t0, dur, attack=0.02, release=None):
    """1 between t0 and t0 + dur with a linear attack and release; 0 outside."""
    release = dur * 0.35 if release is None else release
    return np.clip((t - t0) / attack, 0, 1) * np.clip((t0 + dur - t) / release, 0, 1)


def mix(*parts):
    """Each (signal, gain): normalised, scaled and summed, so the balance does not depend on filter gains."""
    out = None
    for x, gain in parts:
        y = g.norm(np.asarray(x, float)) * gain
        if out is None:
            out = y
        else:
            n = max(len(out), len(y))
            out = np.pad(out, (0, n - len(out))) + np.pad(y, (0, n - len(y)))
    return out


def syllable(t, rng, t0, dur, f0, f1, vowel, breath=0.5, rough=0.0):
    """One voiced syllable: a saw gliding f0 to f1 through a vowel's formants, with breath and roughness."""
    n = len(t)
    u = np.clip((t - t0) / dur, 0, 1)
    f = f0 + (f1 - f0) * u
    src = g.saw(g.phase(f, n), max(f0, f1), nh=30, roll=1.2)
    if rough:
        src = src * (1 - rough + rough * (0.5 + 0.5 * np.sin(2 * np.pi * 33 * t + rng.uniform(0, 6))))
    src = src * 0.6 + g.noise(n, rng) * breath * 0.3
    v = sum(g.bp(src, fc, q) * a for fc, q, a in vowel)
    return v * gate(t, t0, dur, 0.02, dur * 0.45)


def helmet(x, bright=2300):
    """A clan warrior's voice through a sealed helmet: banded, with a small metallic box resonance."""
    y = g.bp(x, 820, 0.9) + 0.45 * g.bp(x, 1650, 2.5)
    y = g.comb(y, 0.0027, 0.38)
    return g.lp(y, bright)


def sand(t, rng, env, fc=2600, rate=2400, loop=False):
    """Pouring or spraying sand: dense fine grains (band-limited crackle) over a soft hiss."""
    n = len(t)
    grains = g.bp(g.crackle(n, rate, rng, loop=loop), fc, 0.8, loop=loop)
    hiss = g.bp(g.noise(n, rng), fc * 1.6, 1.0, loop=loop) * 0.04
    return (grains + hiss) * env


def grit(t, rng, t0, t1, count, fc=1300):
    """A few pebbles and grit lifting and falling: scattered dull clicks."""
    x = np.zeros(len(t))
    for _ in range(count):
        c = rng.uniform(t0, t1)
        x += g.bp(g.crack(t, rng, c, 0.004, 300), fc * rng.uniform(0.7, 1.4), 2.0) * rng.uniform(0.3, 1.0)
    return x


def static_crawl(t, rng, t0, t1, rate0=20, rate1=260, fc=3600):
    """Static crawling over the ground: crackle thickening from t0 to t1, with a low electric buzz rising."""
    n = len(t)
    u = np.clip((t - t0) / max(t1 - t0, 1e-3), 0, 1)
    live = (t >= t0) & (t <= t1 + 0.05)
    rate = (rate0 + (rate1 - rate0) * u ** 1.5) * live
    sparks = g.bp(g.crackle(n, rate, rng), fc, 0.9)
    f = 70 + 70 * u
    buzz = g.lp(g.pulse(g.phase(f, n), 160, w=0.15, nh=20), 900) * u ** 2 * live
    return g.norm(sparks) + g.norm(buzz) * 0.25


def bolt(t, rng, t0, size=1.0):
    """A lightning strike: a tearing crack, a burst of crackle and a short low boom."""
    n = len(t)
    tear = g.hp(g.noise(n, rng), 1500) * g.env_ad(t, 0.001, 0.05 * size, t0)
    fizz = g.bp(g.crackle(n, 1500 * np.exp(-np.maximum(t - t0, 0) / (0.12 * size)), rng), 4200, 0.8) * (t >= t0)
    boom = g.thump(t, 85, 30, 0.06, 0.45 * size, t0=t0 + 0.01)
    roll = g.lp(g.noise(n, rng), 140) * g.bell(t, t0 + 0.35 * size, 0.08, 0.6 * size) * (t >= t0)
    return mix((tear, 1.0), (fizz, 0.7), (boom, 0.8), (roll, 0.5 * size))


def zap(t, rng, t0, dur=0.12, fc=3200):
    """One arc of lightning between two points: a sizzling burst with a buzzing edge."""
    n = len(t)
    env = g.env_ad(t, 0.002, dur, t0)
    hiss = g.bp(g.noise(n, rng), fc, 0.7) * env
    buzz = g.pulse(g.phase(np.full(n, 140.0), n), 140, w=0.1, nh=40) * env
    snap = g.crack(t, rng, t0, 0.006, 2500)
    return g.norm(hiss) + g.norm(buzz) * 0.35 + g.norm(snap) * 0.8


def crystal(t, rng, f0, decay, t0=0.0, amp=1.0):
    """A struck crystal: inharmonic, glassy partials with a faint shimmer of beating."""
    ratios = [1.0, 2.76, 5.40, 8.93]
    out = g.bell_partials(len(t), f0, ratios, [decay, decay * 0.6, decay * 0.35, decay * 0.2], [1.0, 0.5, 0.3, 0.15],
                          rng, t0=t0, attack=0.003, detune=0.004)
    return out * amp


def growl(t, rng, t0, dur, f0, f1, formants=((300, 3.0, 1.0), (700, 4.0, 0.6)), rough=38.0):
    """A large beast's voice: a low buzz gliding f0 to f1, rough and breathy, through two formants."""
    n = len(t)
    u = np.clip((t - t0) / dur, 0, 1)
    f = f0 + (f1 - f0) * u
    voiced = g.saw(g.phase(f * (1 + 0.03 * np.sin(2 * np.pi * 4 * t)), n), max(f0, f1), nh=40, roll=1.1)
    am = 0.6 + 0.4 * np.sin(2 * np.pi * rough * t + rng.uniform(0, 6))
    src = voiced * am + g.noise(n, rng) * 0.5
    v = sum(g.bp(src, fc, q) * a for fc, q, a in formants)
    return v * gate(t, t0, dur, 0.05, dur * 0.5)


def chitter(t, rng, t0, dur, f0, f1, rate=34.0, fc=2300):
    """An insectile screech: a narrow pulse gliding f0 to f1, chopped fast, through a high formant."""
    n = len(t)
    u = np.clip((t - t0) / dur, 0, 1)
    f = f0 + (f1 - f0) * u
    src = g.pulse(g.phase(f, n), max(f0, f1), w=0.12, nh=24)
    chop = (0.5 + 0.5 * np.sign(np.sin(2 * np.pi * rate * t + rng.uniform(0, 6)))) * 0.8 + 0.2
    v = g.bp(src * chop, fc, 2.5) + 0.5 * g.bp(src * chop, fc * 1.9, 3.0) + 0.3 * g.bp(g.noise(n, rng), fc * 1.4, 1.2)
    return v * gate(t, t0, dur, 0.01, dur * 0.4)


def hoof(t, rng, t0, weight=1.0):
    """A heavy foot on basalt grit: a dull thud and a scrape."""
    thud = g.thump(t, 110, 45, 0.012, 0.07 * weight, t0=t0)
    scrape = g.bp(g.noise(len(t), rng), 1500, 0.8) * g.env_ad(t, 0.004, 0.06, t0)
    return g.norm(thud) * weight + g.norm(scrape) * 0.4


def choir(t, freqs, vowel, rng, loop=False, length=None, vibrato=0.005, breath=0.25, vowel_b=None):
    """Wordless voices on freqs, each with its own slow vibrato, through a vowel (morphing to vowel_b and
    back once per loop when given). Seamless when looping."""
    n = len(t)
    out_a = np.zeros(n)
    for i, f in enumerate(freqs):
        if loop:
            mod = 1 + vibrato * np.sin(2 * np.pi * (5 + 2 * i) * t / length + i * 1.7)
        else:
            mod = 1 + vibrato * np.sin(2 * np.pi * (4.7 + 0.6 * i) * t + i * 1.7)
        out_a += g.saw(g.phase(f * mod, n, loop=loop), float(np.max(f)) * 1.02, nh=24, roll=1.15)
    src = out_a / max(1, len(freqs)) + g.noise(n, rng) * breath * 0.2
    va = sum(g.bp(src, fc, q, loop=loop) * a for fc, q, a in vowel)
    if vowel_b is None:
        return va
    vb = sum(g.bp(src, fc, q, loop=loop) * a for fc, q, a in vowel_b)
    w = swell(t, length, 1) if loop else np.clip(t / t[-1], 0, 1)
    return va * (1 - w) + vb * w


# ---------------------------------------------------------------- the cartel enforcers

def g_cartel_bark(rng):
    """A gruff, wordless shout across a street: two clipped syllables, the second rising."""
    t = g.tt(0.9)
    v = syllable(t, rng, 0.0, 0.16, 152, 138, AH, 0.6, 0.3) + syllable(t, rng, 0.22, 0.32, 140, 168, EH, 0.6, 0.3)
    x = mix((g.sat(g.norm(v) * 1.6, 1.4), 1.0), (g.gear_rattle(t, rng, 0.0, 0.1, 3), 0.12))
    return g.reverb(x, rng, 0.6, 0.16, bright=4200)


def g_cartel_hurt(rng):
    """A clipped grunt, a buckle and a holster rattling."""
    t = g.tt(0.5)
    v = g.grunt(t, rng, 0.0, 0.16, 168, 126, 560, 1150)
    x = mix((v, 1.0), (g.gear_rattle(t, rng, 0.0, 0.12, 5), 0.25), (g.thump(t, 130, 70, 0.02, 0.04), 0.2))
    return g.reverb(x, rng, 0.35, 0.1, bright=4000)


def g_cartel_death(rng):
    """A long groan, the body going down and the weapon clattering away."""
    t = g.tt(1.7)
    v = g.grunt(t, rng, 0.0, 0.55, 152, 84, 500, 1000)
    fall = g.thump(t, 110, 45, 0.02, 0.14, t0=0.8)
    clatter = g.metal_ring(t, rng, 880, 5, 0.12, t0=0.88) + g.gear_rattle(t, rng, 0.8, 1.05, 6)
    return g.reverb(mix((v, 1.0), (fall, 0.6), (clatter, 0.3)), rng, 0.6, 0.15)


def g_cartel_shove(rng):
    """A shoulder and forearm driven into someone: a swish of cloth, a heavy thud, a hard breath out."""
    t = g.tt(0.7)
    n = len(t)
    swish = g.whoosh(t, rng, 500 + 1600 * g.bell(t, 0.06, 0.03, 0.08), 1.2, g.bell(t, 0.06, 0.03, 0.08))
    thud = g.thump(t, 140, 60, 0.015, 0.08, t0=0.1) + g.bp(g.noise(n, rng), 700, 0.9) * g.env_ad(t, 0.002, 0.04, 0.1) * 0.3
    huff = g.bp(g.noise(n, rng), 950, 1.5) * g.env_ad(t, 0.01, 0.08, 0.09)
    x = mix((swish, 0.5), (thud, 1.0), (huff, 0.35), (g.gear_rattle(t, rng, 0.1, 0.25, 4), 0.2))
    return g.reverb(x, rng, 0.4, 0.12)


# ---------------------------------------------------------------- Clan Hask

def g_hask_bark(rng):
    """A clan war-call through the helmet: three hard syllables, the last held and falling."""
    t = g.tt(1.2)
    v = (syllable(t, rng, 0.0, 0.13, 126, 120, AH, 0.5, 0.35) + syllable(t, rng, 0.17, 0.13, 132, 124, OH, 0.5, 0.35)
         + syllable(t, rng, 0.36, 0.45, 138, 110, AH, 0.5, 0.35))
    return g.reverb(helmet(g.sat(g.norm(v) * 1.5, 1.4)), rng, 0.7, 0.18)


def g_hask_hurt(rng):
    """A grunt through the helmet; plate knocking on plate."""
    t = g.tt(0.55)
    v = g.grunt(t, rng, 0.0, 0.16, 138, 105, 500, 1000)
    x = mix((helmet(v), 1.0), (g.metal_ring(t, rng, 520, 5, 0.08), 0.25), (g.gear_rattle(t, rng, 0.0, 0.12, 4), 0.2))
    return g.reverb(x, rng, 0.3, 0.1)


def g_hask_death(rng):
    """A long groan through the helmet, an armoured body falling, the jet pack's last sputter."""
    t = g.tt(1.9)
    n = len(t)
    v = g.grunt(t, rng, 0.0, 0.62, 132, 78, 460, 940)
    fall = g.thump(t, 100, 42, 0.02, 0.16, t0=0.8) + g.metal_ring(t, rng, 300, 6, 0.2, t0=0.82) * 0.4
    sputter = g.bp(g.crackle(n, 60 * g.env_ad(t, 0.05, 0.25, 1.05), rng), 1300, 1.0)
    x = mix((helmet(v), 1.0), (fall, 0.65), (g.gear_rattle(t, rng, 0.8, 1.0, 7), 0.25), (sputter, 0.2))
    return g.reverb(x, rng, 0.6, 0.15)


def g_hask_slam_tell(rng):
    """The jet-slam tell (20 ticks): the pack's turbine whines up as the warrior crouches, armour creaking."""
    t = g.tt(1.1)
    n = len(t)
    u = np.clip(t / 1.0, 0, 1)
    f = 420 + 1500 * u ** 1.6
    whine = (tone(f, n) + 0.35 * tone(f * 2.01, n)) * u * gate(t, 0.0, 1.05, 0.05, 0.08)
    roar = g.bp(g.noise(n, rng), 900, 0.6) * u ** 2 * gate(t, 0.0, 1.05, 0.05, 0.08)
    x = mix((whine, 0.6), (roar, 0.5), (g.gear_rattle(t, rng, 0.0, 0.25, 5), 0.2))
    return g.reverb(x, rng, 0.5, 0.12)


def g_hask_slam(rng):
    """The jet-slam lands: a crushing boot-first impact, a gout of flame and a burst of dust."""
    t = g.tt(1.6)
    n = len(t)
    impact = g.thump(t, 130, 34, 0.035, 0.35) + g.crack(t, rng, 0.0, 0.025, 600) * 0.6
    flame = g.bp(g.noise(n, rng), 800, 0.6) * g.env_ad(t, 0.01, 0.22)
    dust = g.bp(g.crackle(n, 600 * np.exp(-t / 0.35), rng), 1200, 0.7)
    return g.reverb(g.sat(mix((impact, 1.0), (flame, 0.5), (dust, 0.55)), 1.4), rng, 1.0, 0.24, bright=3000)


def g_hask_shield_block(rng):
    """A bolt splashing off clan iron: a bright clang, a sizzle and a spray of sparks."""
    t = g.tt(0.9)
    n = len(t)
    clang = g.metal_ring(t, rng, 640, 6, 0.3)
    sizzle = g.bp(g.noise(n, rng), 4200, 1.2) * g.env_ad(t, 0.002, 0.12)
    sparks = g.bp(g.crackle(n, 500 * np.exp(-t / 0.15), rng), 3600, 1.0)
    return g.reverb(mix((clang, 1.0), (sizzle, 0.45), (sparks, 0.5)), rng, 0.5, 0.15)


def g_hask_bash_tell(rng):
    """The shield drawn back (15 ticks): a leather strap pulled tight, iron scraping, a breath held."""
    t = g.tt(0.8)
    n = len(t)
    strap = g.bp(g.noise(n, rng), 900, 1.6) * g.bell(t, 0.12, 0.05, 0.08)
    scrape = g.sweep(g.noise(n, rng), 1400 + 1500 * np.clip(t / 0.6, 0, 1), "bp", 5.0, nb=8) * gate(t, 0.05, 0.6, 0.05, 0.15)
    creak = g.metal_ring(t, rng, 260, 4, 0.15, t0=0.05)
    breath = g.bp(g.noise(n, rng), 700, 1.0) * gate(t, 0.25, 0.45, 0.1, 0.1)
    return g.reverb(mix((strap, 0.5), (scrape, 0.6), (creak, 0.4), (helmet(breath), 0.3)), rng, 0.4, 0.12)


def g_hask_bash(rng):
    """The shield bash lands: a heavy, dull clang of iron on armour and a body knocked back."""
    t = g.tt(1.0)
    clang = g.metal_ring(t, rng, 300, 6, 0.25) + g.metal_ring(t, rng, 470, 4, 0.12) * 0.6
    thud = g.thump(t, 150, 55, 0.015, 0.1)
    x = mix((clang, 0.9), (thud, 1.0), (g.gear_rattle(t, rng, 0.02, 0.2, 6), 0.25), (g.crack(t, rng, 0.0, 0.01, 800), 0.4))
    return g.reverb(g.sat(x, 1.3), rng, 0.6, 0.15)


# ---------------------------------------------------------------- the Hymnsworn

def g_hymnsworn_chant(rng):
    """Seamless 4 s: three low voices on the Hymn's open fifth, a breathy murmur rising and falling,
    the vowel turning from oo to ah and back."""
    L = 4.0
    t = g.tt(L)
    n = len(t)
    voices = choir(t, [midi(45), midi(52), midi(57)], OO, rng, loop=True, length=L, vibrato=0.006, breath=0.6, vowel_b=AH)
    murmur = g.bp(g.noise(n, rng), 600, 1.5, loop=True) * swell(t, L, 4, 0.5) * 0.4
    stress = swell(t, L, 2) ** 2 * 0.35 + 0.65
    return g.reverb(mix((voices * stress, 1.0), (murmur, 0.18)), rng, 1.2, 0.3, loop=True, bright=3500)


def g_hymnsworn_hurt(rng):
    """A hoarse, breathy cry, cut short."""
    t = g.tt(0.5)
    v = syllable(t, rng, 0.0, 0.2, 210, 170, AH, 1.2, 0.2)
    return g.reverb(mix((v, 1.0), (g.bp(g.noise(len(t), rng), 2600, 1.0) * g.env_ad(t, 0.005, 0.06), 0.25)), rng, 0.45, 0.14)


def g_hymnsworn_death(rng):
    """A sung note that sags and breaks off, then a robed body folding to the ground."""
    t = g.tt(1.7)
    v = syllable(t, rng, 0.0, 0.85, 180, 120, OH, 0.7, 0.15)
    fall = g.thump(t, 95, 45, 0.02, 0.12, t0=0.95)
    cloth = g.bp(g.noise(len(t), rng), 1800, 0.8) * g.env_ad(t, 0.02, 0.1, 0.92)
    return g.reverb(mix((v, 1.0), (fall, 0.55), (cloth, 0.2)), rng, 0.9, 0.22)


def g_hymnsworn_call_tell(rng):
    """The storm-call tell (40 ticks): static thickening over a patch of ground, a buzz climbing under it
    and grit lifting."""
    t = g.tt(2.1)
    crawl = static_crawl(t, rng, 0.0, 2.0, 15, 320)
    lift = grit(t, rng, 0.6, 2.0, 14)
    return g.reverb(mix((crawl, 1.0), (lift, 0.35)), rng, 0.6, 0.18)


def g_hymnsworn_call_strike(rng):
    """The called bolt: a tearing crack, a burst of sparks and a short rolling boom."""
    t = g.tt(2.4)
    return g.reverb(bolt(t, rng, 0.0, 1.0), rng, 1.3, 0.25, bright=4000)


# ---------------------------------------------------------------- the sand scours

def g_scour_hiss(rng):
    """Seamless 4 s: sand shifting over something moving below, a slow breathy hiss and a low rumble."""
    L = 4.0
    t = g.tt(L)
    n = len(t)
    shift = sand(t, rng, 0.4 + 0.6 * swell(t, L, 2, 0.3), 2200, 900, loop=True)
    hiss = g.bp(g.noise(n, rng), 5200, 1.4, loop=True) * (0.3 + 0.7 * swell(t, L, 3, 1.1) ** 2)
    rumble = g.lp(g.noise(n, rng), 120, loop=True) * (0.5 + 0.5 * swell(t, L, 1, 2.0))
    return g.reverb(mix((shift, 1.0), (hiss, 0.4), (rumble, 0.5)), rng, 0.5, 0.15, loop=True, bright=4000)


def g_scour_burst_tell(rng):
    """The burst tell (15 ticks): the sand boils, grains seething faster with low bubbling knocks."""
    t = g.tt(0.8)
    n = len(t)
    u = np.clip(t / 0.75, 0, 1)
    seethe = sand(t, rng, u ** 1.3, 1800, 800 + 5000 * u)
    knocks = sum(g.thump(t, 90, 50, 0.01, 0.03, t0=rng.uniform(0.1, 0.72)) * rng.uniform(0.4, 1.0) for _ in range(9))
    return g.reverb(mix((seethe, 1.0), (knocks, 0.45)), rng, 0.4, 0.12)


def g_scour_burst(rng):
    """The scour erupts: a blast of sand, a shrieking chitter and the heavy body landing."""
    t = g.tt(1.3)
    n = len(t)
    blast_ = g.lp(g.noise(n, rng), 1500) * g.env_ad(t, 0.005, 0.15)
    spray = sand(t, rng, g.env_ad(t, 0.01, 0.45), 2800, 4000)
    shriek = chitter(t, rng, 0.03, 0.4, 900, 1250)
    land = g.thump(t, 110, 45, 0.02, 0.08, t0=0.18)
    return g.reverb(mix((blast_, 0.7), (spray, 1.0), (shriek, 0.7), (land, 0.5)), rng, 0.6, 0.16)


def g_scour_burrow(rng):
    """It sinks: claws digging, then sand pouring in and slumping over the hole."""
    t = g.tt(1.3)
    digs = sum(g.bp(g.noise(len(t), rng), 1600, 1.0) * g.env_ad(t, 0.004, 0.04, 0.05 + 0.11 * k) for k in range(4))
    pour = sand(t, rng, g.bell(t, 0.55, 0.15, 0.4), 1900, 3000)
    slump = g.lp(g.noise(len(t), rng), 300) * g.bell(t, 0.75, 0.1, 0.3)
    return g.reverb(mix((digs, 0.6), (pour, 1.0), (slump, 0.5)), rng, 0.4, 0.12)


def g_scour_hurt(rng):
    """A short, rasping chitter."""
    t = g.tt(0.45)
    return g.reverb(chitter(t, rng, 0.0, 0.22, 1050, 800, 38.0), rng, 0.3, 0.12)


def g_scour_death(rng):
    """A long screech that sags and rattles out, and a body slumping into sand."""
    t = g.tt(1.4)
    screech = chitter(t, rng, 0.0, 0.75, 1000, 420, 30.0, 2000)
    slump = sand(t, rng, g.bell(t, 0.9, 0.05, 0.25), 1700, 2500) + g.lp(g.thump(t, 90, 40, 0.02, 0.1, t0=0.82), 500)
    return g.reverb(mix((screech, 1.0), (slump, 0.6)), rng, 0.5, 0.14)


def g_scour_matriarch_sweep(rng):
    """The tail coiling with a dry rattle of plates, then a heavy sweep through sand."""
    t = g.tt(1.2)
    n = len(t)
    rattle = g.bp(g.crackle(n, 180 * gate(t, 0.0, 0.45, 0.05, 0.1), rng), 2600, 1.2)
    fc = 300 + 1100 * g.bell(t, 0.55, 0.08, 0.15)
    sweep_ = g.whoosh(t, rng, fc, 1.1, g.bell(t, 0.55, 0.08, 0.15))
    spray = sand(t, rng, g.bell(t, 0.62, 0.06, 0.25), 2400, 3000)
    return g.reverb(mix((rattle, 0.4), (sweep_, 1.0), (spray, 0.5)), rng, 0.6, 0.15)


def g_scour_matriarch_call(rng):
    """A deep, warbling call that breaks into chitters: the brood is summoned."""
    t = g.tt(1.8)
    n = len(t)
    f = 165 * (1 + 0.06 * np.sin(2 * np.pi * 7 * t))
    call = g.pulse(g.phase(f, n), 200, w=0.2, nh=30)
    call = (g.bp(call, 600, 3.0) + 0.6 * g.bp(call, 1500, 4.0)) * gate(t, 0.0, 1.0, 0.08, 0.4)
    answers = chitter(t, rng, 0.9, 0.25, 1200, 1350) + chitter(t, rng, 1.15, 0.2, 1350, 1150) * 0.7
    return g.reverb(mix((call, 1.0), (answers, 0.45)), rng, 1.0, 0.22)


# ---------------------------------------------------------------- the stormbacks

def g_stormback_idle(rng):
    """A low grumble from deep in the chest and a heavy breath out."""
    t = g.tt(1.5)
    n = len(t)
    v = growl(t, rng, 0.0, 0.8, 58, 50, ((260, 3.0, 1.0), (620, 4.0, 0.5)), 26.0)
    breath = g.bp(g.noise(n, rng), 500, 1.2) * gate(t, 0.75, 0.5, 0.05, 0.3)
    return g.reverb(mix((v, 1.0), (breath, 0.35)), rng, 0.6, 0.14)


def g_stormback_hurt(rng):
    """A short, angry bellow."""
    t = g.tt(0.7)
    return g.reverb(growl(t, rng, 0.0, 0.35, 92, 70, ((320, 3.0, 1.0), (780, 4.0, 0.6))), rng, 0.5, 0.14)


def g_stormback_death(rng):
    """A long bellow falling away, a crash of plates and a great body hitting the ground."""
    t = g.tt(2.6)
    v = growl(t, rng, 0.0, 1.3, 88, 42, ((300, 3.0, 1.0), (720, 4.0, 0.6)), 30.0)
    fall = g.thump(t, 80, 30, 0.05, 0.4, t0=1.25)
    plates = g.gear_rattle(t, rng, 1.25, 1.5, 8)
    return g.reverb(mix((v, 1.0), (fall, 0.8), (plates, 0.2)), rng, 0.9, 0.2)


def g_stormback_charge_tell(rng):
    """The charge tell (20 ticks): a snort, the head going down and a forefoot pawing grit twice."""
    t = g.tt(1.1)
    n = len(t)
    snort = g.bp(g.noise(n, rng), 700, 1.0) * g.env_ad(t, 0.01, 0.12)
    paw = hoof(t, rng, 0.4, 0.8) + hoof(t, rng, 0.75, 1.0) + grit(t, rng, 0.4, 0.95, 8, 1000)
    rumble = growl(t, rng, 0.2, 0.8, 52, 56, ((240, 3.0, 1.0),), 22.0)
    return g.reverb(mix((snort, 0.8), (paw, 1.0), (rumble, 0.45)), rng, 0.5, 0.14)


def g_stormback_impact(rng):
    """The charge hits: a crushing thud of head-plates, a crack, and a spit of static off the plates."""
    t = g.tt(1.3)
    n = len(t)
    thud = g.thump(t, 120, 38, 0.03, 0.3) + g.crack(t, rng, 0.0, 0.02, 700) * 0.5
    crunch = g.bp(g.noise(n, rng), 900, 0.8) * g.env_ad(t, 0.002, 0.08)
    static = g.bp(g.crackle(n, 300 * np.exp(-t / 0.25), rng), 3800, 1.0)
    return g.reverb(g.sat(mix((thud, 1.0), (crunch, 0.6), (static, 0.3)), 1.3), rng, 0.8, 0.2, bright=3200)


def g_stormback_roar(rng):
    """The roar to the sky (30 ticks): a rising bellow, and under it a whine of static gathering on the
    plates until it snaps."""
    t = g.tt(1.8)
    n = len(t)
    v = growl(t, rng, 0.0, 1.45, 70, 118, ((340, 3.0, 1.0), (860, 4.0, 0.7), (1900, 5.0, 0.2)), 36.0)
    u = np.clip(t / 1.5, 0, 1)
    whine = tone(300 + 900 * u ** 2, n) * u ** 2 * gate(t, 0.0, 1.5, 0.1, 0.05)
    sparks = g.bp(g.crackle(n, 20 + 400 * u ** 2, rng), 4000, 1.0) * (t < 1.55)
    snap = g.crack(t, rng, 1.5, 0.02, 1500)
    return g.reverb(mix((v, 1.0), (whine, 0.25), (sparks, 0.35), (snap, 0.5)), rng, 1.0, 0.22)


def g_stormback_charged(rng):
    """Seamless 4 s: charged plates crackling, a buzzing hum swelling and fading, a thin hiss."""
    L = 4.0
    t = g.tt(L)
    n = len(t)
    crack_ = g.bp(g.crackle(n, 45 + 60 * swell(t, L, 3, 0.4), rng, loop=True), 3400, 0.9, loop=True)
    hum = g.lp(g.pulse(g.phase(np.full(n, 110.0), n, loop=True), 110, w=0.2, nh=20), 1400, loop=True) * (0.4 + 0.6 * swell(t, L, 2))
    hiss = g.bp(g.noise(n, rng), 6500, 1.2, loop=True) * (0.5 + 0.5 * swell(t, L, 5, 2.0))
    return mix((crack_, 1.0), (hum, 0.3), (hiss, 0.15))


# ---------------------------------------------------------------- Darth Ulvaine

def g_ulvaine_hurt(rng):
    """A sharp, controlled breath and a short grunt, with a ring of song in it."""
    t = g.tt(0.55)
    v = g.grunt(t, rng, 0.0, 0.15, 240, 200, 680, 1500)
    ring = syllable(t, rng, 0.0, 0.18, 392, 370, AH_HI, 0.2)
    return g.reverb(mix((v, 1.0), (ring, 0.25)), rng, 0.6, 0.18)


def g_ulvaine_phase(rng):
    """Her song shifts: a low boom and a choir swelling into a new chord."""
    t = g.tt(2.8)
    n = len(t)
    boom = g.thump(t, 70, 32, 0.08, 0.6)
    swell_ = gate(t, 0.1, 2.4, 0.9, 1.0)
    chord = choir(t, [midi(57), midi(60), midi(64), midi(69)], AH_HI, rng, vibrato=0.006, breath=0.3) * swell_
    air = g.bp(g.noise(n, rng), 1200, 0.7) * gate(t, 0.0, 1.0, 0.4, 0.5)
    return g.reverb(mix((boom, 0.8), (chord, 1.0), (air, 0.2)), rng, 1.8, 0.35, bright=4500)


def g_ulvaine_step_tell(rng):
    """The Hymn-step tell (15 ticks): a violet shimmer, high tones trembling upward while the air draws in."""
    t = g.tt(0.8)
    n = len(t)
    u = np.clip(t / 0.75, 0, 1)
    shimmer = sum(tone(f * (1 + 0.5 * u), n) * (0.6 + 0.4 * np.sin(2 * np.pi * (11 + i * 3) * t)) for i, f in enumerate((1180, 1490, 1770)))
    shimmer *= u ** 1.5
    draw = g.whoosh(t, rng, 600 + 2400 * u ** 2, 1.4, u ** 2)
    return g.reverb(mix((shimmer, 0.6), (draw, 1.0)), rng, 0.6, 0.2, bright=7000)


def g_ulvaine_step(rng):
    """She steps out of the Hymn: a thump of displaced air, a sung note snapping shut, a gust outward."""
    t = g.tt(1.0)
    n = len(t)
    pop = g.thump(t, 140, 50, 0.01, 0.08)
    note = syllable(t, rng, 0.0, 0.12, 523, 494, AH_HI, 0.2)
    gust = g.whoosh(t, rng, 2400 * np.exp(-t / 0.2) + 400, 1.0, g.env_ad(t, 0.005, 0.25))
    return g.reverb(mix((pop, 0.9), (note, 0.4), (gust, 0.8)), rng, 0.8, 0.22)


def g_ulvaine_lightning(rng):
    """Seamless 3 s: Force lightning pouring from her hands, a dense crackle over a buzzing arc."""
    L = 3.0
    t = g.tt(L)
    n = len(t)
    dens = 260 + 200 * swell(t, L, 7, 0.3) + 120 * swell(t, L, 13, 1.7)
    crack_ = g.bp(g.crackle(n, dens, rng, loop=True), 2800, 0.7, loop=True)
    buzz = g.pulse(g.phase(np.full(n, 120.0), n, loop=True), 120, w=0.08, nh=60)
    buzz = g.bp(buzz, 1800, 0.8, loop=True) * (0.5 + 0.5 * swell(t, L, 9, 0.8))
    sizzle = g.hp(g.noise(n, rng), 4500, loop=True) * (0.4 + 0.6 * swell(t, L, 11, 2.3))
    return mix((crack_, 1.0), (buzz, 0.45), (sizzle, 0.25))


def g_ulvaine_rod_charge(rng):
    """A rod charging (30 ticks): its hum climbing and static crawling toward the patch it will strike."""
    t = g.tt(1.6)
    n = len(t)
    u = np.clip(t / 1.5, 0, 1)
    f = 90 + 210 * u ** 1.4
    hum = g.lp(g.pulse(g.phase(f, n), 320, w=0.2, nh=24), 1600) * (0.3 + 0.7 * u) * gate(t, 0.0, 1.55, 0.1, 0.05)
    crawl = static_crawl(t, rng, 0.1, 1.5, 20, 280)
    ring = g.metal_ring(t, rng, 1240, 5, 0.6)
    return g.reverb(mix((hum, 0.8), (crawl, 0.9), (ring, 0.2)), rng, 0.8, 0.2)


def g_ulvaine_rod_strike(rng):
    """The rod discharges into the floor: a crack and a sizzle (the bolt's own thunder plays with it)."""
    t = g.tt(1.4)
    n = len(t)
    snap = g.crack(t, rng, 0.0, 0.012, 1800)
    sizzle = g.bp(g.noise(n, rng), 3800, 0.9) * g.env_ad(t, 0.002, 0.18)
    sparks = g.bp(g.crackle(n, 900 * np.exp(-t / 0.2), rng), 4400, 0.8)
    scorch = g.thump(t, 110, 45, 0.02, 0.12)
    return g.reverb(mix((snap, 1.0), (sizzle, 0.6), (sparks, 0.6), (scorch, 0.5)), rng, 0.9, 0.2)


def g_ulvaine_pulse(rng):
    """A Hymn pulse rolling out over 40 ticks: a rising sung note riding a travelling wall of air and grit."""
    t = g.tt(2.3)
    n = len(t)
    u = np.clip(t / 2.0, 0, 1)
    note = syllable(t, rng, 0.0, 2.05, midi(64), midi(69), AH_HI, 0.25)
    wave = g.whoosh(t, rng, 300 + 1500 * u, 1.0, gate(t, 0.0, 2.05, 0.15, 0.5))
    lift = grit(t, rng, 0.2, 2.0, 22, 1500)
    low = g.thump(t, 65, 35, 0.1, 0.5)
    return g.reverb(mix((note, 0.6), (wave, 1.0), (lift, 0.3), (low, 0.5)), rng, 1.4, 0.28)


def g_ulvaine_crescendo(rng):
    """The Crescendo begins: the choir climbs over a deep drone and the resonators' glassy shimmer."""
    t = g.tt(3.8)
    n = len(t)
    u = np.clip(t / 3.4, 0, 1)
    climb = choir(t, [midi(57) * (1 + 0.12 * u), midi(64) * (1 + 0.12 * u), midi(69) * (1 + 0.12 * u)], AH_HI, rng,
                  vibrato=0.007, breath=0.3)
    climb *= u ** 1.4 * gate(t, 0.0, 3.5, 0.2, 0.25)
    drone = (tone(55.0, n) + 0.5 * tone(82.4, n)) * gate(t, 0.0, 3.6, 0.5, 0.5)
    glass = sum(crystal(t, rng, f, 1.2, t0=0.5 + 0.7 * k) for k, f in enumerate((1318, 1568, 1760, 2093)))
    return g.reverb(mix((climb, 1.0), (drone, 0.5), (glass, 0.3)), rng, 2.0, 0.35, bright=5000)


def g_ulvaine_resonator(rng):
    """A resonator rings: a clear, glassy strike with a long shimmer (it lights, or it breaks lower)."""
    t = g.tt(1.8)
    n = len(t)
    ring = crystal(t, rng, 1046, 1.1) + crystal(t, rng, 1568, 0.8) * 0.5
    tick = g.crack(t, rng, 0.0, 0.004, 3000)
    air = g.bp(g.noise(n, rng), 5000, 1.5) * g.env_ad(t, 0.01, 0.4)
    return g.reverb(mix((ring, 1.0), (tick, 0.35), (air, 0.12)), rng, 1.6, 0.3, bright=8000)


def g_ulvaine_blast(rng):
    """The Crescendo blast: every voice at once in one torn chord, a concussion and the hall shaking."""
    t = g.tt(3.2)
    n = len(t)
    chord = choir(t, [midi(45), midi(52), midi(57), midi(58), midi(64)], AH, rng, vibrato=0.01, breath=0.5)
    chord = g.sat(g.norm(chord) * 2.0, 1.8) * g.env_ad(t, 0.02, 0.6)
    boom = g.thump(t, 75, 26, 0.08, 0.8)
    shake = g.lp(g.noise(n, rng), 160) * gate(t, 0.0, 2.6, 0.05, 1.6)
    debris = g.bp(g.crackle(n, 300 * np.exp(-t / 0.9), rng), 1700, 0.8)
    return g.reverb(mix((chord, 0.8), (boom, 1.0), (shake, 0.6), (debris, 0.35)), rng, 2.2, 0.32, bright=3500)


def g_ulvaine_yield(rng):
    """The song breaks off; a knee and a hand on stone, robes settling, one long breath."""
    t = g.tt(2.4)
    n = len(t)
    cut = syllable(t, rng, 0.0, 0.32, midi(69), midi(66), AH_HI, 0.3) * (t < 0.3)
    knee = g.thump(t, 90, 45, 0.02, 0.12, t0=0.45) + g.thump(t, 120, 60, 0.01, 0.05, t0=0.62) * 0.5
    robes = g.bp(g.noise(n, rng), 1500, 0.8) * gate(t, 0.42, 0.5, 0.03, 0.35)
    breath = g.bp(g.noise(n, rng), 750, 1.0) * gate(t, 1.0, 1.2, 0.3, 0.7)
    return g.reverb(mix((cut, 0.6), (knee, 1.0), (robes, 0.3), (breath, 0.4)), rng, 1.2, 0.28)


def g_ulvaine_echo_fade(rng):
    """The echo comes apart: voices sliding down and thinning into shimmer and a breath of wind."""
    t = g.tt(2.8)
    n = len(t)
    u = np.clip(t / 2.4, 0, 1)
    slide = choir(t, [midi(64) * (1 - 0.3 * u), midi(69) * (1 - 0.3 * u)], AH_HI, rng, breath=0.4) * (1 - u) ** 1.2
    glint = sum(crystal(t, rng, rng.uniform(1800, 3200), 0.35, t0=rng.uniform(0.1, 1.8)) for _ in range(7))
    wind = g.bp(g.noise(n, rng), 900, 0.8) * gate(t, 0.6, 2.0, 0.6, 1.2)
    return g.reverb(mix((slide, 1.0), (glint, 0.25), (wind, 0.3)), rng, 1.6, 0.35, bright=6000)


# ---------------------------------------------------------------- the First Cantor and the Long Hymn

def g_cantor_hurt(rng):
    """A choral grunt: three voices thrown out of tune for an instant."""
    t = g.tt(0.7)
    v = choir(t, [midi(43), midi(44), midi(50)], OH, rng, vibrato=0.02, breath=0.6) * g.env_ad(t, 0.01, 0.12)
    return g.reverb(v, rng, 1.0, 0.35, bright=3500)


def g_cantor_phase(rng):
    """The Cantor's hymn shifts: a deep chord moving under a low boom, the choir following it."""
    t = g.tt(3.2)
    n = len(t)
    boom = g.thump(t, 60, 28, 0.1, 0.7)
    a = choir(t, [midi(38), midi(45), midi(50)], OO, rng, vibrato=0.004, breath=0.3) * gate(t, 0.0, 1.4, 0.3, 0.5)
    b = choir(t, [midi(37), midi(44), midi(49), midi(56)], AH, rng, vibrato=0.004, breath=0.3) * gate(t, 1.0, 2.0, 0.4, 0.9)
    organ = g.lp(g.saw(g.phase(np.full(n, midi(26)), n), 80, nh=30), 400) * gate(t, 0.0, 2.9, 0.3, 0.8)
    return g.reverb(mix((boom, 0.8), (a, 0.8), (b, 1.0), (organ, 0.5)), rng, 2.4, 0.38, bright=4000)


def g_cantor_beam_tell(rng):
    """The beam tell (40 ticks): dust lifting along a line, a thin sung tone gathering behind it."""
    t = g.tt(2.1)
    n = len(t)
    u = np.clip(t / 2.0, 0, 1)
    dust = g.bp(g.crackle(n, 30 + 600 * u, rng), 1900, 0.8) + g.bp(g.noise(n, rng), 3000, 1.0) * u * 0.3
    sung = syllable(t, rng, 0.0, 2.0, midi(74), midi(76), OO_HI, 0.2) * u ** 2
    return g.reverb(mix((dust, 1.0), (sung, 0.5)), rng, 1.2, 0.28)


def g_cantor_beam(rng):
    """Seamless 3 s: the verse beam sweeping, a bright sung fifth with a buzzing edge and rushing air."""
    L = 3.0
    t = g.tt(L)
    n = len(t)
    sung = choir(t, [midi(62), midi(69), midi(74)], AH_HI, rng, loop=True, length=L, vibrato=0.004, breath=0.2)
    edge = g.bp(g.pulse(g.phase(np.full(n, midi(50)), n, loop=True), 160, w=0.06, nh=60), 2400, 1.0, loop=True)
    air = g.bp(g.noise(n, rng), 1600, 0.6, loop=True) * (0.6 + 0.4 * swell(t, L, 3))
    return g.reverb(mix((sung, 1.0), (edge, 0.3), (air, 0.4)), rng, 1.0, 0.25, loop=True, bright=6000)


def g_cantor_slam_tell(rng):
    """The slam tell (20 ticks): his fists raised, air drawn up after them and a rumble building below."""
    t = g.tt(1.05)
    u = np.clip(t / 1.0, 0, 1)
    rise = g.whoosh(t, rng, 300 + 1700 * u ** 1.5, 1.2, u ** 1.5)
    rumble = g.lp(g.noise(len(t), rng), 110) * u ** 2
    intake = choir(t, [midi(41), midi(48)], OO, rng, breath=0.8) * u ** 2
    return g.reverb(mix((rise, 1.0), (rumble, 0.6), (intake, 0.3)), rng, 0.9, 0.24)


def g_cantor_slam(rng):
    """The slam: his fists driven down into stone, the floor cracking and debris raining in a deep hall."""
    t = g.tt(2.4)
    n = len(t)
    hit = g.thump(t, 120, 30, 0.05, 0.6) + g.crack(t, rng, 0.0, 0.03, 500) * 0.8
    stone = g.bp(g.noise(n, rng), 700, 0.8) * g.env_ad(t, 0.002, 0.12)
    debris = g.bp(g.crackle(n, 500 * np.exp(-t / 0.6), rng), 1500, 0.8)
    return g.reverb(g.sat(mix((hit, 1.0), (stone, 0.6), (debris, 0.5)), 1.4), rng, 2.0, 0.3, bright=3000)


def g_cantor_chain(rng):
    """Chain lightning: three arcs leaping one after another, each a little higher."""
    t = g.tt(1.2)
    arcs = sum(zap(t, rng, 0.02 + 0.16 * k, 0.12, 2800 + 600 * k) * (1.0 - 0.15 * k) for k in range(3))
    return g.reverb(arcs, rng, 0.8, 0.22)


def g_cantor_roots(rng):
    """Roots forcing up through stone: creaking wood, the floor cracking, a slow groan of the earth."""
    t = g.tt(1.6)
    n = len(t)
    creak_rate = 40 + 30 * np.sin(2 * np.pi * 1.7 * t)
    creak = g.bp(g.crackle(n, np.maximum(creak_rate, 5), rng), 650, 4.0) + g.bp(g.crackle(n, 25, rng), 1300, 5.0) * 0.5
    creak *= gate(t, 0.0, 1.4, 0.1, 0.5)
    cracks = sum(g.crack(t, rng, rng.uniform(0.05, 0.9), 0.01, 600) * rng.uniform(0.4, 1.0) for _ in range(5))
    groan = growl(t, rng, 0.1, 1.2, 40, 36, ((180, 3.0, 1.0),), 9.0)
    return g.reverb(mix((creak, 1.0), (cracks, 0.5), (groan, 0.5)), rng, 1.0, 0.24)


def g_cantor_fog(rng):
    """Mire fog spreading: a low wet bubbling and a cold hiss creeping outward."""
    t = g.tt(2.4)
    n = len(t)
    bubbles = sum(g.thump(t, rng.uniform(140, 260), rng.uniform(70, 110), 0.01, 0.04, t0=rng.uniform(0.05, 2.0)) * rng.uniform(0.3, 1.0)
                  for _ in range(18))
    hiss = g.sweep(g.noise(n, rng), 600 + 1800 * np.clip(t / 2.0, 0, 1), "lp", nb=8) * gate(t, 0.0, 2.2, 0.4, 0.9)
    return g.reverb(mix((bubbles, 0.7), (hiss, 1.0)), rng, 1.6, 0.3, bright=3000)


def g_cantor_mark(rng):
    """The basalt marks (40 ticks): static and grit at the marked spots, stone ticking as it heats."""
    t = g.tt(2.1)
    crawl = static_crawl(t, rng, 0.0, 2.0, 20, 240, 3000)
    ticks = grit(t, rng, 0.2, 2.0, 18, 900)
    hum = g.lp(tone(55 + 30 * np.clip(t / 2.0, 0, 1), len(t)), 300) * np.clip(t / 2.0, 0, 1) ** 2
    return g.reverb(mix((crawl, 1.0), (ticks, 0.45), (hum, 0.3)), rng, 1.0, 0.22)


def g_cantor_strike(rng):
    """Basalt erupts: lightning finds the marks, and the stone cracks and spits."""
    t = g.tt(2.2)
    n = len(t)
    stones = g.bp(g.crackle(n, 700 * np.exp(-np.maximum(t - 0.02, 0) / 0.3), rng), 1100, 0.8) * (t >= 0.02)
    return g.reverb(mix((bolt(t, rng, 0.0, 0.9), 1.0), (stones, 0.45)), rng, 1.6, 0.28, bright=3500)


def g_cantor_ring_tell(rng):
    """A ring about to sing (30 ticks): its stones trembling, rattling faster, a low note waking in them."""
    t = g.tt(1.6)
    n = len(t)
    u = np.clip(t / 1.5, 0, 1)
    rattle = g.bp(g.crackle(n, 30 + 500 * u ** 1.5, rng), 700, 1.2)
    rumble = g.lp(g.noise(n, rng), 90) * u
    note = tone(midi(38), n) * u ** 3 * gate(t, 0.0, 1.55, 0.2, 0.05)
    return g.reverb(mix((rattle, 1.0), (rumble, 0.5), (note, 0.4)), rng, 1.0, 0.22)


def g_cantor_ring(rng):
    """Seamless 4 s: a ring of stones singing, an inharmonic stone hum with a sung tone and dancing grit."""
    L = 4.0
    t = g.tt(L)
    n = len(t)
    stone = sum(a * tone(f, n, loop=True) for f, a in ((73.4, 1.0), (151.2, 0.5), (233.9, 0.3), (347.0, 0.15)))
    stone *= 0.7 + 0.3 * swell(t, L, 4)
    sung = choir(t, [midi(62), midi(69)], OO_HI, rng, loop=True, length=L, breath=0.25)
    dance = g.bp(g.crackle(n, 70 + 50 * swell(t, L, 6, 1.0), rng, loop=True), 1500, 0.9, loop=True)
    return g.reverb(mix((stone, 0.8), (sung, 0.6), (dance, 0.4)), rng, 1.4, 0.3, loop=True, bright=4000)


def g_cantor_silence(rng):
    """A verse silenced: the choir's chord cut off mid-breath, a hush, one soft clear chime."""
    t = g.tt(2.2)
    n = len(t)
    chord = choir(t, [midi(50), midi(57), midi(62)], AH, rng, breath=0.3) * (t < 0.25) * gate(t, 0.0, 0.25, 0.03, 0.02)
    hush = g.bp(g.noise(n, rng), 2200, 0.8) * g.bell(t, 0.32, 0.03, 0.2)
    chime = crystal(t, rng, 880, 1.3, t0=0.35)
    return g.reverb(mix((chord, 0.8), (hush, 0.3), (chime, 0.6)), rng, 1.8, 0.35, bright=7000)


def g_cantor_unravel(rng):
    """He unravels: the choir sliding apart and down, drifting glints of light, the engine falling still."""
    t = g.tt(3.6)
    n = len(t)
    u = np.clip(t / 3.2, 0, 1)
    voices = choir(t, [midi(50) * (1 - 0.25 * u), midi(57) * (1 - 0.3 * u), midi(62) * (1 - 0.18 * u)], AH, rng, breath=0.4)
    voices *= (1 - u) ** 1.3
    glints = sum(crystal(t, rng, rng.uniform(1500, 3600), 0.4, t0=rng.uniform(0.1, 2.8)) for _ in range(12))
    thrum = g.lp(g.pulse(g.phase(np.full(n, 41.2), n), 60, w=0.3, nh=10), 200) * (1 - u) ** 2
    return g.reverb(mix((voices, 1.0), (glints, 0.3), (thrum, 0.4)), rng, 2.4, 0.4, bright=6000)


def g_cantor_choir(rng):
    """Seamless 4 s: the choir echoes singing, four pale voices on a minor chord, oo turning to ah and back."""
    L = 4.0
    t = g.tt(L)
    voices = choir(t, [midi(50), midi(57), midi(62), midi(65)], OO_HI, rng, loop=True, length=L, vibrato=0.005, breath=0.35,
                   vowel_b=AH_HI)
    return g.reverb(voices * (0.8 + 0.2 * swell(t, L, 2)), rng, 1.8, 0.4, loop=True, bright=5000)


def g_cantor_engine(rng):
    """Seamless 4 s: the song-engine thrumming, a slow mechanical pulse under an organ drone and a
    metallic resonance."""
    L = 4.0
    t = g.tt(L)
    n = len(t)
    beat = g.thump(g.tt(0.5), 55, 32, 0.05, 0.12)
    pulse_ = np.zeros(n)
    for k in range(8):
        g.place_circular(pulse_, beat * (1.0 if k % 2 == 0 else 0.6), k * L / 8)
    drone = g.lp(g.saw(g.phase(np.full(n, midi(26)), n, loop=True), 80, nh=30) + g.saw(g.phase(np.full(n, midi(33)), n, loop=True), 120, nh=30) * 0.6,
                 500, loop=True)
    metal = sum(a * tone(f, n, loop=True) for f, a in ((311.0, 1.0), (467.0, 0.6), (731.0, 0.3))) * swell(t, L, 2, 0.5) ** 3
    return g.reverb(mix((pulse_, 1.0), (drone, 0.5), (metal, 0.15)), rng, 1.2, 0.3, loop=True, bright=3000)


# ---------------------------------------------------------------- the hologram

def g_story_hologram(rng):
    """Seamless 3 s: a projector's soft electrical hum, a thin whine and a flutter of interference."""
    L = 3.0
    t = g.tt(L)
    n = len(t)
    hum = sum(a * tone(f, n, loop=True) for f, a in ((60.0, 1.0), (120.0, 0.5), (180.0, 0.25), (240.0, 0.12)))
    whine = tone(3200.0, n, loop=True) * (0.6 + 0.4 * swell(t, L, 5, 0.7))
    flutter = g.bp(g.noise(n, rng), 2400, 0.8, loop=True) * swell(t, L, 7, 1.3) ** 6
    ticks = g.bp(g.crackle(n, 6, rng, loop=True), 5000, 1.5, loop=True)
    return mix((hum, 0.7), (whine, 0.08), (flutter, 0.3), (ticks, 0.25))


# ---------------------------------------------------------------- music (procedural placeholders)

def i_drum(rng, f_hi=95, f_lo=45, decay=0.3, skin=0.4):
    t = g.tt(decay * 3)
    return g.thump(t, f_hi, f_lo, 0.04, decay) + g.bp(g.noise(len(t), rng), 320, 1.0) * g.env_ad(t, 0.001, 0.04) * skin


def i_pluck(f, dur, rng):
    t = g.tt(dur)
    return g.bell_partials(len(t), f, [1, 2, 3, 4], [dur * 0.4, dur * 0.2, dur * 0.12, dur * 0.07], [1, 0.45, 0.2, 0.1], rng,
                           attack=0.004, detune=0.001)


def i_pad(freqs, dur, attack=0.6, release=1.0, bright=800):
    t = g.tt(dur + release)
    n = len(t)
    x = np.zeros(n)
    for f in freqs:
        for det in (0.995, 1.005):
            x += g.saw(g.phase(np.full(n, f * det), n), f, nh=10)
    env = np.clip(t / attack, 0, 1) * np.where(t > dur, np.exp(-(t - dur) / (release / 3)), 1.0)
    return g.lp(x, bright) * env / (2 * len(freqs))


def i_sung(f, dur, vowel, rng, release=0.4):
    """A sung note: a saw with vibrato that fades in after the attack, through a vowel."""
    t = g.tt(dur + release)
    n = len(t)
    vib = 1 + 0.008 * np.sin(2 * np.pi * 5.0 * t) * np.clip(t / 0.4, 0, 1)
    src = g.saw(g.phase(f * vib, n), f, nh=24, roll=1.15) + g.noise(n, rng) * 0.05
    v = sum(g.bp(src, fc, q) * a for fc, q, a in vowel)
    env = np.clip(t / 0.08, 0, 1) * np.where(t > dur, np.exp(-(t - dur) / (release / 3)), 1.0)
    return v * env


def i_bell(f, dur, rng):
    t = g.tt(dur)
    return g.bell_partials(len(t), f, [1.0, 2.0, 2.76, 5.4], [dur * 0.5, dur * 0.3, dur * 0.2, dur * 0.1], [1, 0.4, 0.3, 0.12], rng,
                           attack=0.003, detune=0.002)


def arrange(L, events):
    buf = np.zeros(g.ns(L))
    for t0, x in events:
        g.place_circular(buf, x, t0 % L)
    return buf


def g_music_ulvaine(rng):
    """"The Hymn's Edge" (92 BPM, 8 bars of A Phrygian): a duel. Frame drums in a limping three-three-two,
    a low plucked ostinato with the flattened second, a sung lead in a woman's range, a dark pad."""
    beat = 60 / 92
    bar = beat * 4
    L = bar * 8
    roots = [45, 45, 46, 45, 43, 45, 46, 40]
    drums, plucks, lead, pads = [], [], [], []
    figure = [0, 12, 1, 12, 7, 12, 1, 3]
    for b, root in enumerate(roots):
        t0 = b * bar
        for at, v, f in ((0.0, 1.0, 88), (1.5, 0.7, 96), (3.0, 0.8, 92)):
            drums.append((t0 + at * beat, i_drum(rng, f, 42, 0.32, 0.5) * v))
        drums.append((t0 + 3.5 * beat, i_drum(rng, 120, 60, 0.15, 0.8) * 0.4))
        for i, k in enumerate(figure):
            plucks.append((t0 + i * beat / 2, i_pluck(midi(root + k), 0.8, rng) * (0.65 if i % 2 == 0 else 0.4)))
        pads.append((t0, i_pad([midi(root + 12), midi(root + 19), midi(root + 24)], bar, 0.6, 1.0, 700)))
        if b in (2, 3, 6, 7):
            phrase = ((81, 0.0, 1.0), (82, 1.0, 0.5), (81, 1.5, 0.5), (79, 2.0, 2.0)) if b % 2 == 0 else \
                ((77, 0.0, 1.5), (76, 1.5, 0.5), (74, 2.0, 1.0), (76, 3.0, 1.0))
            for note, at, d in phrase:
                lead.append((t0 + at * beat, i_sung(midi(note), d * beat * 0.95, AH_HI, rng)))
    out = (arrange(L, drums) * 0.7 + arrange(L, plucks) * 0.45 + g.norm(arrange(L, lead)) * 0.4
           + g.norm(arrange(L, pads)) * 0.3)
    return g.reverb(out, rng, 2.0, 0.3, loop=True, bright=4000)


def g_music_cantor(rng):
    """"The Long Hymn" (66 BPM, 8 bars in D minor): slow and vast. A choir in four parts moving by
    whole bars, an organ pedal, a deep drum on each bar's first beat and a bell tolling every second bar."""
    beat = 60 / 66
    bar = beat * 4
    L = bar * 8
    chords = [(38, 50, 57, 62, 65), (37, 49, 56, 61, 64), (34, 50, 58, 62, 65), (36, 48, 55, 60, 64),
              (38, 50, 57, 62, 65), (41, 48, 57, 60, 65), (33, 49, 57, 61, 64), (38, 50, 57, 62, 62)]
    choirs, organ, drums, bells = [], [], [], []
    for b, chord in enumerate(chords):
        t0 = b * bar
        pedal, *voices = chord
        for k, note in enumerate(voices):
            choirs.append((t0, i_sung(midi(note), bar * 0.98, OO if k < 2 else AH, rng, 0.8)))
        organ.append((t0, i_pad([midi(pedal), midi(pedal + 12)], bar, 0.4, 0.8, 500)))
        drums.append((t0, i_drum(rng, 70, 32, 0.6, 0.3)))
        drums.append((t0 + 2.5 * beat, i_drum(rng, 80, 36, 0.4, 0.3) * 0.45))
        if b % 2 == 0:
            bells.append((t0 + beat, i_bell(midi(74), 3.0, rng)))
    out = g.norm(arrange(L, choirs)) * 0.6 + g.norm(arrange(L, organ)) * 0.35 + arrange(L, drums) * 0.6 + g.norm(arrange(L, bells)) * 0.2
    return g.reverb(out, rng, 3.0, 0.4, loop=True, bright=3500)


FILES = {
    "cartel.bark": {"cartel/bark": g_cartel_bark},
    "cartel.hurt": {"cartel/hurt": g_cartel_hurt},
    "cartel.death": {"cartel/death": g_cartel_death},
    "cartel.shove": {"cartel/shove": g_cartel_shove},
    "hask.bark": {"hask/bark": g_hask_bark},
    "hask.hurt": {"hask/hurt": g_hask_hurt},
    "hask.death": {"hask/death": g_hask_death},
    "hask.slam_tell": {"hask/slam_tell": g_hask_slam_tell},
    "hask.slam": {"hask/slam": g_hask_slam},
    "hask.shield_block": {"hask/shield_block": g_hask_shield_block},
    "hask.bash_tell": {"hask/bash_tell": g_hask_bash_tell},
    "hask.bash": {"hask/bash": g_hask_bash},
    "hymnsworn.chant": {"hymnsworn/chant": g_hymnsworn_chant},
    "hymnsworn.hurt": {"hymnsworn/hurt": g_hymnsworn_hurt},
    "hymnsworn.death": {"hymnsworn/death": g_hymnsworn_death},
    "hymnsworn.call_tell": {"hymnsworn/call_tell": g_hymnsworn_call_tell},
    "hymnsworn.call_strike": {"hymnsworn/call_strike": g_hymnsworn_call_strike},
    "scour.hiss": {"scour/hiss": g_scour_hiss},
    "scour.burst_tell": {"scour/burst_tell": g_scour_burst_tell},
    "scour.burst": {"scour/burst": g_scour_burst},
    "scour.burrow": {"scour/burrow": g_scour_burrow},
    "scour.hurt": {"scour/hurt": g_scour_hurt},
    "scour.death": {"scour/death": g_scour_death},
    "scour.matriarch.sweep": {"scour/matriarch_sweep": g_scour_matriarch_sweep},
    "scour.matriarch.call": {"scour/matriarch_call": g_scour_matriarch_call},
    "stormback.idle": {"stormback/idle": g_stormback_idle},
    "stormback.hurt": {"stormback/hurt": g_stormback_hurt},
    "stormback.death": {"stormback/death": g_stormback_death},
    "stormback.charge_tell": {"stormback/charge_tell": g_stormback_charge_tell},
    "stormback.impact": {"stormback/impact": g_stormback_impact},
    "stormback.roar": {"stormback/roar": g_stormback_roar},
    "stormback.charged": {"stormback/charged": g_stormback_charged},
    "boss.ulvaine.hurt": {"boss/ulvaine_hurt": g_ulvaine_hurt},
    "boss.ulvaine.phase": {"boss/ulvaine_phase": g_ulvaine_phase},
    "boss.ulvaine.step_tell": {"boss/ulvaine_step_tell": g_ulvaine_step_tell},
    "boss.ulvaine.step": {"boss/ulvaine_step": g_ulvaine_step},
    "boss.ulvaine.lightning": {"boss/ulvaine_lightning": g_ulvaine_lightning},
    "boss.ulvaine.rod_charge": {"boss/ulvaine_rod_charge": g_ulvaine_rod_charge},
    "boss.ulvaine.rod_strike": {"boss/ulvaine_rod_strike": g_ulvaine_rod_strike},
    "boss.ulvaine.pulse": {"boss/ulvaine_pulse": g_ulvaine_pulse},
    "boss.ulvaine.crescendo": {"boss/ulvaine_crescendo": g_ulvaine_crescendo},
    "boss.ulvaine.resonator": {"boss/ulvaine_resonator": g_ulvaine_resonator},
    "boss.ulvaine.blast": {"boss/ulvaine_blast": g_ulvaine_blast},
    "boss.ulvaine.yield": {"boss/ulvaine_yield": g_ulvaine_yield},
    "boss.ulvaine.echo_fade": {"boss/ulvaine_echo_fade": g_ulvaine_echo_fade},
    "boss.cantor.hurt": {"boss/cantor_hurt": g_cantor_hurt},
    "boss.cantor.phase": {"boss/cantor_phase": g_cantor_phase},
    "boss.cantor.beam_tell": {"boss/cantor_beam_tell": g_cantor_beam_tell},
    "boss.cantor.beam": {"boss/cantor_beam": g_cantor_beam},
    "boss.cantor.slam_tell": {"boss/cantor_slam_tell": g_cantor_slam_tell},
    "boss.cantor.slam": {"boss/cantor_slam": g_cantor_slam},
    "boss.cantor.chain": {"boss/cantor_chain": g_cantor_chain},
    "boss.cantor.roots": {"boss/cantor_roots": g_cantor_roots},
    "boss.cantor.fog": {"boss/cantor_fog": g_cantor_fog},
    "boss.cantor.mark": {"boss/cantor_mark": g_cantor_mark},
    "boss.cantor.strike": {"boss/cantor_strike": g_cantor_strike},
    "boss.cantor.ring_tell": {"boss/cantor_ring_tell": g_cantor_ring_tell},
    "boss.cantor.ring": {"boss/cantor_ring": g_cantor_ring},
    "boss.cantor.silence": {"boss/cantor_silence": g_cantor_silence},
    "boss.cantor.unravel": {"boss/cantor_unravel": g_cantor_unravel},
    "boss.cantor.choir": {"boss/cantor_choir": g_cantor_choir},
    "boss.cantor.engine": {"boss/cantor_engine": g_cantor_engine},
    "music.boss.ulvaine": {"music/boss_ulvaine": g_music_ulvaine},
    "music.boss.cantor": {"music/boss_cantor": g_music_cantor},
    "story.hologram": {"story/hologram": g_story_hologram},
}
