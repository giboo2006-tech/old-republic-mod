"""Sound recipes for the world feature, Milestone 3 (site ambience, the puzzle kit, tomb vents, terminals).

Owned by the world worker (W1). Synthesised from scratch with the helpers in gen_sounds.py (filtered
noise, impulses, oscillators, inharmonic partials and convolution reverb); nothing is sampled. These are
the designed stand-ins until a sound designer replaces them; docs/assets/world.md describes each target.
The file names are the placeholder names (<group>/<rest of the id>), so sounds.json does not change.

The four site beds loop seamlessly: every modulation is periodic in the loop length, oscillators use
whole cycles (g.phase(..., loop=True)), filters and reverb wrap circularly, and one-off events in a bed
are placed with g.place_circular.

Render: python3 tools/assets/gen_sounds.py --m3 --feature world
"""
import gen_sounds as g

np = g.np


def _wave(t, L, k, offset=0.0):
    """A sine with exactly k cycles per loop of length L (keeps beds seamless)."""
    return np.sin(2 * np.pi * k * t / L + offset)


def _smooth(t, a, b):
    """0 before a, 1 after b, a smooth ramp between."""
    u = np.clip((t - a) / (b - a), 0, 1)
    return u * u * (3 - 2 * u)


def _tone(n, f, loop=False):
    return np.sin(g.phase(np.full(n, float(f)), n, loop=loop))


def _stick_slip(t, rate):
    """One impulse per cycle of a (time-varying) rate: the regular catch-and-release of a creak."""
    cyc = np.floor(g.phase(rate, len(t)) / (2 * np.pi))
    return (np.diff(cyc, prepend=0.0) > 0).astype(float)


def _beep(t, f, t0, dur):
    gate = np.clip((t - t0) / 0.004, 0, 1) * np.clip((t0 + dur - t) / 0.012, 0, 1)
    return np.sin(2 * np.pi * f * t) * gate


def _stones(t, rng, count, t_lo, t_hi, f_lo=120, f_hi=300, amp=0.4):
    """A scatter of small stone knocks (short pitched thumps) between t_lo and t_hi."""
    x = np.zeros(len(t))
    for _ in range(count):
        t0 = rng.uniform(t_lo, t_hi)
        f = rng.uniform(f_lo, f_hi)
        x += g.thump(t, f, f * 0.5, 0.015, rng.uniform(0.03, 0.09), t0=t0) * rng.uniform(0.4, 1.0) * amp
    return x


# ---------------------------------------------------------------- site beds (seamless, streamed)

def g_jedi_ruin(rng):
    """12 s: wind moving through gaps in old stone, a whistle in a crack, leaves stirring, a faint warm resonance."""
    L = 12.0
    t = g.tt(L)
    n = len(t)
    gust = 0.6 + 0.25 * _wave(t, L, 1) + 0.15 * _wave(t, L, 3, 1.1)
    fc = 380 + 260 * _wave(t, L, 1, 0.4) + 90 * _wave(t, L, 4, 2.0)
    wind = g.sweep(g.noise(n, rng), fc, "bp", 0.9, nb=10, loop=True) * gust
    whistle = g.bp(g.noise(n, rng), 740, 18, loop=True) * (0.5 + 0.5 * _wave(t, L, 2, 0.3)) ** 2 * 2.0
    leaves = np.zeros(n)
    seg = g.tt(0.9)
    for t0 in (0.8, 2.9, 4.4, 6.7, 8.2, 10.6):
        leaves = g.place_circular(leaves, g.rustle(seg, rng, 0.05, 0.75, 45, fc=3200) * rng.uniform(0.15, 0.3), t0 + rng.uniform(-0.3, 0.3))
    air = g.lp(g.noise(n, rng), 120, loop=True) * 1.2
    drone = (0.5 * _tone(n, 110, True) + 0.3 * _tone(n, 165, True) + 0.15 * _tone(n, 220.5, True)) * (0.8 + 0.2 * _wave(t, L, 1))
    return g.reverb(wind + whistle + leaves + air + drone * 0.06, rng, 1.8, 0.25, loop=True, bright=4000)


def g_sith_tomb(rng):
    """12 s: hollow, low moaning air in the passages, a sub rumble, a beating minor-second drone, rare drips."""
    L = 12.0
    t = g.tt(L)
    n = len(t)
    moan_fc = 170 + 50 * _wave(t, L, 1) + 25 * _wave(t, L, 3, 0.8)
    moan = g.sweep(g.noise(n, rng), moan_fc, "bp", 6, nb=12, loop=True) * (0.35 + 0.65 * (0.5 + 0.5 * _wave(t, L, 2, 2.2)) ** 2) * 3.0
    moan2 = g.sweep(g.noise(n, rng), moan_fc * 1.49, "bp", 8, nb=12, loop=True) * (0.5 + 0.5 * _wave(t, L, 3)) ** 2 * 1.5
    sub = g.lp(g.noise(n, rng), 45, loop=True) * 3.0
    drone = (_tone(n, 73.42, True) + 0.7 * _tone(n, 77.78, True)) * 0.06
    drips = np.zeros(n)
    for t0, f0 in [(1.9, 1150), (5.3, 1480), (9.4, 980)]:
        d = g.tt(0.12)
        ff = f0 * (1 + 0.7 * np.clip(d / 0.015, 0, 1))
        drips = g.place_circular(drips, np.sin(g.phase(ff, len(d))) * g.env_ad(d, 0.001, 0.025) * 0.5, t0)
    return g.reverb(moan + moan2 + sub + drone + drips, rng, 3.0, 0.45, loop=True, bright=2500)


def g_wreck(rng):
    """10 s: wind over torn hull plates, two thin whistles, a slow metal groan, ticks of cooling metal."""
    L = 10.0
    t = g.tt(L)
    n = len(t)
    fc = 900 + 400 * _wave(t, L, 1, 0.5) + 150 * _wave(t, L, 5)
    wind = g.sweep(g.noise(n, rng), fc, "bp", 0.7, nb=10, loop=True) * (0.6 + 0.4 * (0.5 + 0.5 * _wave(t, L, 2)) ** 2)
    whistle = (g.bp(g.noise(n, rng), 1320, 25, loop=True) * (0.5 + 0.5 * _wave(t, L, 3, 1.0)) ** 3 * 2.5
               + g.bp(g.noise(n, rng), 2010, 30, loop=True) * (0.5 + 0.5 * _wave(t, L, 2, 2.5)) ** 4 * 1.8)
    groan = g.comb(g.lp(g.noise(n, rng), 300, loop=True), 1 / 97.0, 0.85, loop=True) * (0.5 + 0.5 * _wave(t, L, 1, 1.7)) ** 2 * 0.6
    ticks = np.zeros(n)
    seg = g.tt(0.15)
    for t0 in (1.3, 1.45, 4.1, 6.6, 6.72, 8.9):
        ticks = g.place_circular(ticks, g.click(seg, rng, 0.002, rng.uniform(0.7, 1.2)) * rng.uniform(0.05, 0.12), t0)
    air = g.lp(g.noise(n, rng), 90, loop=True)
    return g.reverb(wind + whistle + groan + ticks + air, rng, 2.0, 0.3, loop=True, bright=5000)


def g_outpost(rng):
    """8 s: mains hum and a generator thrum, a ventilation fan, two soft console beeps."""
    L = 8.0
    t = g.tt(L)
    n = len(t)
    hum = sum(a * _tone(n, f, True) for f, a in [(60, 1.0), (120, 0.5), (180, 0.3), (240, 0.12)])
    thrum = 0.8 + 0.2 * _wave(t, L, round(7 * L))
    fan = g.bp(g.noise(n, rng), 420, 1.2, loop=True) * 0.5 + g.lp(g.noise(n, rng), 150, loop=True) * 0.6
    beeps = np.zeros(n)
    seg = g.tt(0.2)
    beeps = g.place_circular(beeps, _beep(seg, 1250, 0.0, 0.08) * 0.06, 2.6)
    beeps = g.place_circular(beeps, _beep(seg, 1660, 0.0, 0.06) * 0.05, 6.1)
    return g.reverb(hum * thrum * 0.35 + fan + beeps, rng, 0.8, 0.15, loop=True, bright=4000)


# ---------------------------------------------------------------- site one-shots

def g_ruin_chime(rng):
    """A distant, soft chime knocking twice in the wind (heard through stone: dark and far)."""
    t = g.tt(3.2)
    n = len(t)
    f0 = 784.0
    x = g.bell_partials(n, f0, g.CHIME_RATIOS[:4], [1.6, 0.9, 0.5, 0.3], [1, 0.35, 0.2, 0.08], rng, attack=0.004)
    x += g.bell_partials(n, f0 * 1.5, g.CHIME_RATIOS[:3], [1.2, 0.6, 0.3], [1, 0.3, 0.1], rng, t0=0.35, attack=0.004) * 0.5
    return g.reverb(g.lp(x, 3000), rng, 3.0, 0.55, bright=3000)


def g_tomb_whisper(rng):
    """Two breathy, wordless voices overlapping, dark and far down a passage."""
    dur = 2.6
    t = g.tt(dur)
    a = g.norm(g.whisper_voice(dur, rng, 0.85, t_start=0.1, t_end=dur - 0.3))
    b = g.norm(g.whisper_voice(dur, rng, 1.08, t_start=0.45, t_end=dur - 0.1)) * 0.6
    swell = np.clip(t / 0.5, 0, 1) * np.clip((dur - t) / 0.6, 0, 1)
    return g.reverb(g.lp((a + b) * swell, 3500), rng, 2.4, 0.55, bright=3000)


def g_wreck_creak(rng):
    """A hull plate creaking under its own weight: a slow stick-slip through metal resonances and a low groan."""
    t = g.tt(2.2)
    n = len(t)
    env = g.bell(t, 0.9, 0.35, 0.6)
    ex = _stick_slip(t, 18 + 30 * env) * env * (0.7 + 0.3 * rng.random(n))
    creak = sum(g.bp(ex, f, 12) * a for f, a in [(310, 1.0), (620, 0.8), (1180, 0.5), (1730, 0.3)]) * 8
    groan = g.comb(g.lp(g.noise(n, rng), 250), 1 / 63.0, 0.8) * env * 0.4
    return g.reverb(creak + groan, rng, 1.6, 0.35, bright=4000)


# ---------------------------------------------------------------- puzzle kit

def g_veil_reveal(rng):
    """A soft granular hiss: fine grains sifting from mortar, with a faint high tone as the seams catch the light."""
    t = g.tt(1.6)
    n = len(t)
    env = g.bell(t, 0.45, 0.25, 0.5)
    grains = g.hp(g.crackle(n, 900 * env + 50, rng), 2500) * 0.9
    hiss = g.sweep(g.noise(n, rng), 4000 + 2500 * env, "bp", 1.5, nb=8) * env * 0.35
    shimmer = g.bell_partials(n, 2093.0, [1, 2.01], [0.5, 0.3], [0.15, 0.06], rng, t0=0.3, attack=0.2)
    return g.reverb(grains + hiss + shimmer, rng, 1.2, 0.3, bright=6000)


def g_veil_crumble(rng):
    """The veiled stones fall in on themselves: a soft thud, a run of crumbling grains and chunks, settling dust."""
    t = g.tt(1.8)
    n = len(t)
    thud = g.thump(t, 85, 40, 0.06, 0.25) * 0.8
    crumble = g.bp(g.crackle(n, 1400 * np.exp(-t / 0.35) + 40, rng), 1500, 0.8) * 2.2
    chunks = _stones(t, rng, 7, 0.04, 0.6, 140, 260, 0.35)
    dust = g.lp(g.noise(n, rng), 1800) * g.env_ad(t, 0.08, 0.5) * 0.5
    return g.reverb(thud + crumble + chunks + dust, rng, 1.4, 0.3, bright=3500)


def _grind(t, rng, fc, env):
    """Stone dragging on stone: band-passed noise roughened by slow random catches."""
    n = len(t)
    rough = 0.6 + 0.4 * g.sat(g.lp(g.noise(n, rng), 25) * 20, 1.2)
    return g.sweep(g.noise(n, rng), fc, "bp", 2.5, nb=10) * rough * env


def g_counterweight_sink(rng):
    """A heavy block grinds down into its alcove, grit spilling, and settles with a low thump."""
    t = g.tt(1.8)
    n = len(t)
    env = np.clip(t / 0.08, 0, 1) * np.where(t < 1.15, 1.0, np.exp(-(t - 1.15) / 0.08))
    grind = _grind(t, rng, 420 - 220 * np.clip(t / 1.2, 0, 1), env) * 1.2
    settle = g.thump(t, 70, 35, 0.06, 0.35, t0=1.15)
    grit = g.bp(g.crackle(n, 300 * env + 50 * np.exp(-np.maximum(t - 1.15, 0) / 0.3), rng), 2200, 1.0) * 1.2
    return g.reverb(grind + settle + grit, rng, 1.3, 0.3, bright=3000)


def g_counterweight_warn(rng):
    """The stone catches and slips: a stuttering scrape with grit trickling (it is about to rise)."""
    t = g.tt(1.4)
    n = len(t)
    env = np.clip(t / 0.1, 0, 1) * np.clip((1.3 - t) / 0.2, 0, 1)
    stutter = g.lp(0.5 + 0.5 * np.sign(np.sin(2 * np.pi * 11 * t)), 60)
    scrape = g.bp(g.noise(n, rng), 330, 2.0) * stutter * env * 1.4
    trickle = g.bp(g.crackle(n, 180 * env + 20, rng), 2600, 1.2) * 0.9
    return g.reverb(scrape + trickle, rng, 1.0, 0.25, bright=3000)


def g_counterweight_rise(rng):
    """The block grinds back up, a lighter pitch rising, and settles flush with a soft knock."""
    t = g.tt(1.8)
    n = len(t)
    env = np.clip(t / 0.1, 0, 1) * np.where(t < 1.2, 1.0, np.exp(-(t - 1.2) / 0.06))
    grind = _grind(t, rng, 200 + 220 * np.clip(t / 1.2, 0, 1), env)
    knock = g.thump(t, 120, 60, 0.03, 0.15, t0=1.2) * 0.7
    dust = g.lp(g.noise(n, rng), 1200) * g.env_ad(t, 0.05, 0.4, t0=1.1) * 0.4
    return g.reverb(grind + knock + dust, rng, 1.3, 0.3, bright=3000)


def g_step_extend(rng):
    """A ledge slides out of the wall and stops with a solid thunk."""
    t = g.tt(0.9)
    n = len(t)
    env = np.clip(t / 0.03, 0, 1) * np.where(t < 0.45, 1.0, np.exp(-(t - 0.45) / 0.03))
    slide = g.sweep(g.noise(n, rng), 600 + 300 * np.clip(t / 0.45, 0, 1), "bp", 2.0, nb=8) * env
    stop = g.thump(t, 140, 70, 0.02, 0.1, t0=0.46) * 0.9 + g.crack(t, rng, 0.46, 0.004, 1200) * 0.3
    grit = g.bp(g.crackle(n, 200 * env, rng), 2400, 1.0) * 0.6
    return g.reverb(slide + stop + grit, rng, 0.9, 0.25, bright=3500)


def g_step_retract(rng):
    """The ledge draws back into the wall, quicker and lower, with a muffled stop."""
    t = g.tt(0.8)
    n = len(t)
    env = np.clip(t / 0.03, 0, 1) * np.where(t < 0.4, 1.0, np.exp(-(t - 0.4) / 0.03))
    slide = g.sweep(g.noise(n, rng), 900 - 350 * np.clip(t / 0.4, 0, 1), "bp", 2.0, nb=8) * env
    stop = g.thump(t, 100, 50, 0.025, 0.12, t0=0.41) * 0.7
    grit = g.bp(g.crackle(n, 160 * env, rng), 2200, 1.0) * 0.5
    return g.reverb(slide + stop + grit, rng, 0.9, 0.25, bright=3000)


URN_NOTE = 329.63  # E4; the client pitches it to D, E, F and G for the four steps (a minor tetrachord)


def g_urn_echo(rng):
    """A clay urn singing one breathy, hollow note (E4) with a slow swell and a long, soft tail."""
    t = g.tt(2.4)
    n = len(t)
    env = np.clip(t / 0.18, 0, 1) ** 1.5 * np.exp(-np.maximum(t - 0.25, 0) / 0.9)
    f = URN_NOTE * (1 + 0.003 * np.sin(2 * np.pi * 5 * t))
    ph = g.phase(f, n)
    tone = (np.sin(ph) + 0.25 * np.sin(2 * ph) + 0.08 * np.sin(3 * ph)) * env
    hollow = g.bp(g.noise(n, rng), URN_NOTE, 25) * env * 6
    air = g.bp(g.noise(n, rng), URN_NOTE * 2.9, 4) * env * 0.15
    return g.reverb(tone * 0.5 + hollow + air, rng, 2.2, 0.45, bright=4000)


def g_urn_wrong(rng):
    """A low rumble through the ceiling and a rattle of pebbles and dust coming down."""
    t = g.tt(2.4)
    n = len(t)
    rumble = g.lp(g.noise(n, rng), 90) * g.env_ad(t, 0.15, 0.8) * 3.0
    boom = g.thump(t, 60, 32, 0.1, 0.6) * 0.8
    rattle = g.bp(g.crackle(n, 600 * g.bell(t, 0.7, 0.3, 0.6) + 20, rng), 2600, 1.2) * 2.0
    stones = _stones(t, rng, 9, 0.3, 1.4, 200, 420, 0.25)
    dust = g.sweep(g.noise(n, rng), 1500 * np.exp(-t / 0.8) + 300, "lp", nb=8) * g.env_ad(t, 0.3, 0.7) * 0.6
    return g.reverb(rumble + boom + rattle + stones + dust, rng, 1.8, 0.35, bright=3000)


def g_anchor_strain(rng):
    """An iron ring dragged against its stone: a tightening creak, a chain shiver, grit."""
    t = g.tt(1.3)
    n = len(t)
    env = np.clip(t / 0.15, 0, 1) * np.exp(-np.maximum(t - 1.0, 0) / 0.08)
    ex = _stick_slip(t, 22 + 40 * np.clip(t / 1.0, 0, 1)) * env * (0.7 + 0.3 * rng.random(n))
    creak = sum(g.bp(ex, f, 10) * a for f, a in [(420, 1.0), (890, 0.7), (1460, 0.5), (2350, 0.3)]) * 6
    chain = g.gear_rattle(t, rng, 0.1, 1.0, 10) * 0.4
    stone = g.bp(g.crackle(n, 120 * env, rng), 1800, 1.0) * 0.5
    return g.reverb(creak + chain + stone, rng, 1.0, 0.25, bright=4500)


def g_anchor_release(rng):
    """The ring gives: an iron clank and a bounce, a mechanism thunking open behind the stone, grit."""
    t = g.tt(1.5)
    n = len(t)
    ring = g.metal_ring(t, rng, 520, 6, 0.45) * 0.9 + g.metal_ring(t, rng, 760, 5, 0.25, t0=0.09) * 0.5
    hit = g.thump(t, 110, 55, 0.03, 0.12) * 0.6 + g.crack(t, rng, 0, 0.006, 1500) * 0.8
    mechanism = g.thump(t, 80, 40, 0.05, 0.2, t0=0.2) * 0.5
    grit = g.bp(g.crackle(n, 500 * np.exp(-t / 0.25), rng), 2400, 1.0) * 1.2
    return g.reverb(ring + hit + mechanism + grit, rng, 1.2, 0.3, bright=4500)


def g_rubble_crumble(rng):
    """A jammed heap gives way: a heavy slump, stones tumbling over each other, pebbles rolling off, dust."""
    t = g.tt(2.4)
    n = len(t)
    boom = g.thump(t, 70, 30, 0.12, 0.6) * 0.9
    rocks = _stones(t, rng, 12, 0.0, 0.9, 110, 300, 0.45)
    rattle = g.bp(g.crackle(n, 900 * np.exp(-t / 0.5) + 30, rng), 1900, 0.9) * 2.5
    roll = g.bp(g.crackle(n, 250 * g.bell(t, 0.9, 0.3, 0.6), rng), 3200, 1.5) * 1.2
    dust = g.lp(g.noise(n, rng), 900) * g.env_ad(t, 0.1, 0.8) * 0.6
    return g.reverb(boom + rocks + rattle + roll + dust, rng, 1.6, 0.3, bright=3000)


def g_seal_open(rng):
    """A sealed face cracks, crumbles away in grains and chunks, and the air behind it breathes out."""
    t = g.tt(2.6)
    n = len(t)
    crk = g.crack(t, rng, 0.0, 0.012, 700) * 1.4 + g.thump(t, 95, 45, 0.05, 0.25) * 0.7
    crumble = g.bp(g.crackle(n, 1100 * g.bell(t, 0.5, 0.15, 0.6) + 30, rng), 1400, 0.8) * 2.2
    chunks = _stones(t, rng, 6, 0.35, 1.2, 130, 260, 0.35)
    exhale = g.sweep(g.noise(n, rng), 900 * np.exp(-np.maximum(t - 0.2, 0) / 0.6) + 150, "lp", nb=8) * g.bell(t, 0.6, 0.25, 0.8) * 0.9
    return g.reverb(crk + crumble + chunks + exhale, rng, 2.0, 0.4, bright=3000)


def g_solved(rng):
    """A soft resolving chord: D, G and A held, the G settling to F sharp, under a quiet bell."""
    t = g.tt(2.8)
    n = len(t)
    sus_env = np.clip(t / 0.12, 0, 1) * (1 - _smooth(t, 0.55, 0.9))
    res_env = _smooth(t, 0.55, 0.9) * np.exp(-np.maximum(t - 1.0, 0) / 0.8)
    held = np.clip(t / 0.12, 0, 1) * np.exp(-np.maximum(t - 1.0, 0) / 0.8)

    def voice(f):
        return sum(np.sin(2 * np.pi * f * d * t + rng.uniform(0, 6.28)) for d in (0.998, 1.002)) * 0.5 + 0.15 * np.sin(4 * np.pi * f * t)

    pad = (voice(293.66) + voice(440.0)) * held + voice(392.0) * sus_env + voice(369.99) * res_env
    bell = g.bell_partials(n, 587.33, [1, 2.0, 3.0], [1.0, 0.5, 0.25], [1, 0.3, 0.1], rng, t0=0.7, attack=0.01) * 0.3
    return g.reverb(pad * 0.4 + bell, rng, 2.4, 0.45, bright=6000)


def g_pedestal_ready(rng):
    """The pedestal wakes: stone shifts, a low hum swells inside it, then one warm bell."""
    t = g.tt(2.4)
    n = len(t)
    hum = sum(a * _tone(n, f) for f, a in [(110, 1.0), (110.7, 0.7), (165, 0.4), (220, 0.2)]) * g.bell(t, 0.8, 0.5, 0.7) * 0.5
    stone = g.lp(g.noise(n, rng), 200) * g.bell(t, 0.5, 0.3, 0.4) * 0.8
    chime = g.bell_partials(n, 523.25, g.CHIME_RATIOS[:3], [1.2, 0.6, 0.3], [1, 0.3, 0.12], rng, t0=0.9, attack=0.02) * 0.45
    return g.reverb(hum + stone + chime, rng, 2.0, 0.4, bright=5000)


# ---------------------------------------------------------------- tomb vent

def g_vent_hiss(rng):
    """The tell: gas hissing up through the grate, rising for about 15 ticks, grit rattling on the bars."""
    t = g.tt(0.95)
    n = len(t)
    u = np.clip(t / 0.75, 0, 1)
    cut = np.where(t < 0.78, 1.0, np.exp(-(t - 0.78) / 0.04))
    hiss = g.sweep(g.noise(n, rng), 1800 + 3500 * u, "bp", 1.2, nb=10) * u ** 1.5 * cut
    grit = g.bp(g.crackle(n, 60 + 400 * u, rng), 2600, 1.0) * 0.8 * u * cut
    gurgle = g.lp(g.noise(n, rng), 300) * u * cut * 0.4
    return g.reverb(hiss + grit + gurgle, rng, 0.8, 0.2, bright=5000)


def g_vent_puff(rng):
    """The puff: a dull whump of foul air, a breathy rush that thins out, a little grit."""
    t = g.tt(1.4)
    n = len(t)
    whump = g.thump(t, 75, 38, 0.05, 0.25) * 0.9
    burst = g.sweep(g.noise(n, rng), 1400 * np.exp(-t / 0.12) + 250, "lp", nb=8) * g.env_ad(t, 0.01, 0.35) * 1.4
    tail = g.bp(g.noise(n, rng), 600, 0.8) * g.env_ad(t, 0.05, 0.6, t0=0.05) * 0.4
    grit = g.bp(g.crackle(n, 300 * np.exp(-t / 0.15), rng), 2200, 1.0) * 0.8
    return g.reverb(whump + burst + tail + grit, rng, 1.2, 0.3, bright=2500)


# ---------------------------------------------------------------- terminal

def g_terminal_use(rng):
    """A key click, two rising console tones and a short data trill."""
    t = g.tt(0.6)
    n = len(t)
    beeps = _beep(t, 1318.5, 0.02, 0.06) + _beep(t, 1760.0, 0.1, 0.07) * 0.9
    gate = np.clip((t - 0.2) / 0.005, 0, 1) * np.clip((0.32 - t) / 0.01, 0, 1)
    trill = g.pulse(g.phase(np.full(n, 2400.0), n), 2400, 0.4, nh=6) * gate * (0.5 + 0.5 * np.sign(np.sin(2 * np.pi * 40 * t))) * 0.12
    return g.reverb(g.click(t, rng, 0.005) * 0.5 + beeps * 0.35 + trill, rng, 0.4, 0.12, bright=6000)


FILES = {
    "ambient.jedi_ruin": {"ambient/jedi_ruin": g_jedi_ruin},
    "ambient.ruin_chime": {"ambient/ruin_chime": g_ruin_chime},
    "ambient.sith_tomb": {"ambient/sith_tomb": g_sith_tomb},
    "ambient.tomb_whisper": {"ambient/tomb_whisper": g_tomb_whisper},
    "ambient.wreck": {"ambient/wreck": g_wreck},
    "ambient.wreck_creak": {"ambient/wreck_creak": g_wreck_creak},
    "ambient.outpost": {"ambient/outpost": g_outpost},
    "puzzle.veil_reveal": {"puzzle/veil_reveal": g_veil_reveal},
    "puzzle.veil_crumble": {"puzzle/veil_crumble": g_veil_crumble},
    "puzzle.counterweight_sink": {"puzzle/counterweight_sink": g_counterweight_sink},
    "puzzle.counterweight_warn": {"puzzle/counterweight_warn": g_counterweight_warn},
    "puzzle.counterweight_rise": {"puzzle/counterweight_rise": g_counterweight_rise},
    "puzzle.step_extend": {"puzzle/step_extend": g_step_extend},
    "puzzle.step_retract": {"puzzle/step_retract": g_step_retract},
    "puzzle.urn_echo": {"puzzle/urn_echo": g_urn_echo},
    "puzzle.urn_wrong": {"puzzle/urn_wrong": g_urn_wrong},
    "puzzle.anchor_strain": {"puzzle/anchor_strain": g_anchor_strain},
    "puzzle.anchor_release": {"puzzle/anchor_release": g_anchor_release},
    "puzzle.rubble_crumble": {"puzzle/rubble_crumble": g_rubble_crumble},
    "puzzle.seal_open": {"puzzle/seal_open": g_seal_open},
    "puzzle.solved": {"puzzle/solved": g_solved},
    "puzzle.pedestal_ready": {"puzzle/pedestal_ready": g_pedestal_ready},
    "vent.hiss": {"vent/hiss": g_vent_hiss},
    "vent.puff": {"vent/puff": g_vent_puff},
    "terminal.use": {"terminal/use": g_terminal_use},
}
