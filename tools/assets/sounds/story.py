"""PLACEHOLDER sound recipes for the story feature (Chapter Two; Milestone 3: Chapter Three, sites and missions).

Owned by the quest worker (W2). Synthesised from scratch with the helpers in
gen_sounds.py; see events_m2.json and events_m3.json for the event lists and
docs/MILESTONE_3.md sections 3.7, 5.7, 6 and 13.3 for each moment. The M3 files
keep the placeholder names (<group>/<rest of the id>), so sounds.json does not
change when these replace the placeholders.
"""
import gen_sounds as g

np = g.np


def g_two_voices(rng):
    """Seamless 6 s bed: a warm pad and a cold, hollow drone that trade places."""
    L = 6.0
    t = g.tt(L)
    n = len(t)
    warm = sum(a * np.sin(g.phase(np.full(n, f), n, loop=True)) for f, a in [(220, 0.6), (330, 0.4), (440.5, 0.2)])
    cold = g.lp(g.saw(g.phase(np.full(n, 73.5), n, loop=True), 74, nh=20), 400, loop=True)
    trade = 0.5 + 0.5 * np.sin(2 * np.pi * t / L)
    air = g.lp(g.noise(n, rng), 300, loop=True) * 2.0
    return g.reverb(warm * trade + cold * 0.6 * (1 - trade) + air, rng, 2.5, 0.35, loop=True, bright=3000)


def g_lesson_complete(rng):
    t = g.tt(2.0)
    a = g.bell_partials(len(t), 587.33, [1, 2.0, 2.76], [1.0, 0.5, 0.3], [1, 0.25, 0.15], rng, attack=0.006)
    b = g.bell_partials(len(t), 880.0, [1, 2.0, 2.76], [1.2, 0.5, 0.3], [0.8, 0.2, 0.1], rng, t0=0.16, attack=0.006)
    return g.reverb(a + b, rng, 2.0, 0.35, bright=6000)


def g_duel_begin(rng):
    t = g.tt(1.4)
    hit = g.thump(t, 70, 38, 0.1, 0.4) * 1.2
    ring = g.metal_ring(t, rng, 980, 6, 0.5, 0.02) * 0.4
    return g.reverb(hit + ring, rng, 1.4, 0.3, bright=3500)


def g_duel_end(rng):
    t = g.tt(1.6)
    ring = g.metal_ring(t, rng, 740, 6, 0.7) * 0.5
    fall = g.sweep(g.noise(len(t), rng), 1500 * np.exp(-t / 0.5) + 200, "lp", nb=10) * g.env_ad(t, 0.02, 0.4) * 0.8
    return g.reverb(ring + fall, rng, 1.6, 0.3, bright=4000)


# ---------------------------------------------------------------- Milestone 3: discovery

def make_site_discovered(root, mode):
    """A discovery sting (about 2 s): a soft low swell opens into a rising two-note bell figure over a held fifth.

    The three variants share the shape and differ in key and colour (warm, hollow, metallic), so the
    random pick never sounds like a different event.
    """
    def gen(rng):
        t = g.tt(2.4)
        n = len(t)
        swell = g.lp(g.noise(n, rng), 260) * g.bell(t, 0.28, 0.16, 0.6) * 1.6
        pad = sum(a * np.sin(g.phase(np.full(n, root * r), n)) for r, a in [(0.5, 0.5), (0.75, 0.3), (1.0, 0.25)])
        pad *= np.clip(t / 0.3, 0, 1) * np.exp(-np.maximum(t - 0.4, 0) / 0.9) * 0.5
        ratios = [1, 2.0, 2.76] if mode != "metal" else [1, 2.32, 4.25, 6.63]
        decays = [0.9, 0.45, 0.3, 0.2][:len(ratios)]
        amps = [1, 0.3, 0.18, 0.1][:len(ratios)]
        second = 1.5 if mode == "warm" else (1.2 if mode == "hollow" else 1.335)
        a = g.bell_partials(n, root * 2, ratios, decays, amps, rng, t0=0.18, attack=0.01)
        b = g.bell_partials(n, root * 2 * second, ratios, [d * 1.4 for d in decays], amps, rng, t0=0.42, attack=0.01)
        glint = g.hp(g.crackle(n, 30 * g.bell(t, 0.6, 0.2, 0.4), rng), 5000) * 0.25
        return g.reverb(swell + pad + 0.7 * a + 0.6 * b + glint, rng, 2.2, 0.38, bright=5000)
    return gen


# ---------------------------------------------------------------- Milestone 3: Chapter Three

def g_ones_that_sing(rng):
    """Seamless 8 s dream bed (streamed): crystals singing inside stone, slow beating clusters over a
    deep drone, with gusts that snap like banners in a far wind."""
    L = 8.0
    t = g.tt(L)
    n = len(t)
    choir = np.zeros(n)
    for f, a in [(523.25, 0.5), (659.25, 0.35), (783.99, 0.3), (1046.5, 0.18), (1318.5, 0.1)]:
        for det in (0.997, 1.003):
            choir += a * np.sin(g.phase(np.full(n, f * det), n, loop=True) + rng.uniform(0, 6.28))
    swell = 0.55 + 0.45 * np.sin(2 * np.pi * t / L - 1.2) ** 2
    choir = g.lp(choir, 3200, loop=True) * swell
    drone = sum(a * np.sin(g.phase(np.full(n, f), n, loop=True)) for f, a in [(65.4, 0.6), (98.0, 0.3), (130.8, 0.15)])
    wind_fc = 500 + 400 * np.sin(2 * np.pi * 2 * t / L) ** 2
    wind = g.sweep(g.noise(n, rng), wind_fc, "bp", 0.9, nb=10, loop=True) * 0.8
    snaps = np.zeros(n)
    for k, t0 in enumerate((1.3, 3.9, 6.4)):
        burst = g.bp(g.noise(g.ns(0.35), rng), 900 + 300 * k, 1.4) * np.exp(-np.arange(g.ns(0.35)) / g.ns(0.06))
        g.place_circular(snaps, burst * 0.9, t0)
    shimmer = g.bp(g.noise(n, rng), 7000, 3, loop=True) * 0.12 * (1 + np.sin(2 * np.pi * 4 * t / L))
    x = choir * 0.45 + drone * 0.35 + wind + snaps + shimmer
    return g.reverb(x, rng, 3.0, 0.4, loop=True, bright=4000)


def g_calling_holocron(rng):
    """A crystal hum swell: a held cluster rises, blooms and settles (about 2.5 s)."""
    t = g.tt(2.6)
    n = len(t)
    env = g.bell(t, 0.9, 0.45, 0.8)
    f = 392.0 * (1 + 0.012 * np.clip(t / 0.9, 0, 1))
    hum = sum(a * np.sin(g.phase(f * r, n)) for r, a in [(1, 0.6), (1.5, 0.35), (2.0, 0.25), (3.01, 0.12)])
    beat = 1 + 0.25 * np.sin(2 * np.pi * 5.5 * t)
    air = g.sweep(g.noise(n, rng), 1800 + 2400 * env, "bp", 3.0, nb=10) * 0.25
    return g.reverb(hum * env * beat * 0.6 + air * env, rng, 2.0, 0.4, bright=6000)


def g_calling_reliquary(rng):
    """A deep resonance under stone: a low hum with slow overtones that swell and beat (about 3.2 s)."""
    t = g.tt(3.2)
    n = len(t)
    env = np.clip(t / 0.8, 0, 1) ** 1.5 * np.exp(-np.maximum(t - 1.6, 0) / 0.7)
    low = sum(a * np.sin(g.phase(np.full(n, f), n)) for f, a in [(55.0, 0.7), (82.4, 0.35), (110.2, 0.25), (164.8, 0.12)])
    ring = g.bell_partials(n, 220.0, [1, 2.01, 2.97, 4.12], [1.6, 1.0, 0.7, 0.5], [0.4, 0.25, 0.15, 0.1], rng, t0=0.5, attack=0.4)
    rumble = g.lp(g.noise(n, rng), 90) * 1.6 * env
    beat = 1 + 0.3 * np.sin(2 * np.pi * 1.7 * t)
    return g.reverb(g.sat((low * beat + ring) * env * 0.7 + rumble, 1.3), rng, 2.6, 0.45, bright=1800)


def g_calling_fall(rng):
    """A distant crackle: embers and a far fire, a soft low roll under it (about 2.4 s)."""
    t = g.tt(2.4)
    n = len(t)
    env = g.bell(t, 0.7, 0.3, 0.9)
    pops = g.bp(g.crackle(n, 70 * env, rng), 2400, 1.1) * 1.8
    hiss = g.bp(g.noise(n, rng), 3200, 0.8) * 0.12 * env
    roll = g.lp(g.noise(n, rng), 120) * 0.9 * g.bell(t, 0.9, 0.5, 0.9)
    far = g.lp(pops + hiss, 3600)
    return g.reverb(far + roll, rng, 2.4, 0.55, bright=2500, predelay=0.04)


def g_collector_fallen(rng):
    """The Collector beaten: a heavy low impact, crystal shards falling away, and a slow resolving chord (about 3.5 s)."""
    t = g.tt(3.6)
    n = len(t)
    hit = g.sat(g.thump(t, 70, 30, 0.12, 0.6) * 1.2, 1.6)
    shards = np.zeros(n)
    for k in range(9):
        t0 = 0.15 + k * 0.12 + rng.uniform(0, 0.05)
        f0 = 2400 * 2 ** (-k / 6) * rng.uniform(0.95, 1.05)
        shards += g.bell_partials(n, f0, [1, 2.32, 4.25], [0.35, 0.2, 0.1], [0.6, 0.25, 0.1], rng, t0=t0) * (1 - k / 12)
    chord = sum(a * np.sin(g.phase(np.full(n, f), n)) for f, a in [(146.8, 0.5), (220.0, 0.35), (293.7, 0.3), (369.99, 0.18)])
    chord *= np.clip((t - 0.9) / 0.8, 0, 1) * np.exp(-np.maximum(t - 2.2, 0) / 0.6) * 0.55
    return g.reverb(hit + shards * 0.5 + chord, rng, 2.6, 0.4, bright=4500)


def g_rank_three(rng):
    """A new rank conferred: a three-bell ascent (root, fifth, octave) over a warm swelling pad (about 3.2 s)."""
    t = g.tt(3.4)
    n = len(t)
    bells = np.zeros(n)
    for k, (f, t0) in enumerate([(392.0, 0.05), (587.33, 0.38), (783.99, 0.72)]):
        bells += g.bell_partials(n, f, [1, 2.0, 2.76, 5.4], [1.6, 0.8, 0.5, 0.2], [1, 0.3, 0.2, 0.06], rng, t0=t0, attack=0.006) * (0.8 + 0.1 * k)
    pad = sum(a * np.sin(g.phase(np.full(n, f), n)) for f, a in [(196.0, 0.5), (293.7, 0.35), (392.0, 0.25), (493.9, 0.12)])
    pad *= np.clip((t - 0.3) / 1.0, 0, 1) * np.exp(-np.maximum(t - 2.0, 0) / 0.7) * 0.45
    low = g.thump(t, 60, 40, 0.2, 0.9) * 0.4
    return g.reverb(bells * 0.6 + pad + low, rng, 2.8, 0.4, bright=6000)


def g_cairn_touch(rng):
    """A cairn settling into dust: a soft stone thud, a trickle of grit, then one small rising glint (about 1.6 s)."""
    t = g.tt(1.6)
    n = len(t)
    thud = g.thump(t, 110, 55, 0.04, 0.12) * 0.7
    grit_rate = 380 * np.exp(-t / 0.35)
    grit = g.bp(g.crackle(n, grit_rate, rng), 1700, 0.9) * 1.6 + g.lp(g.noise(n, rng), 700) * g.env_ad(t, 0.01, 0.3) * 0.35
    f = 900 * 2 ** (np.clip((t - 0.55) / 0.6, 0, 1) * 1.0)
    glint = np.sin(g.phase(f, n)) * g.bell(t, 0.9, 0.12, 0.35) * 0.3
    return g.reverb(thud + grit + glint, rng, 1.4, 0.3, bright=4000)


def g_blade_touch(rng):
    """A lit blade lowered to a shoulder: a close, slow hum swell with a soft contact shimmer (about 1.6 s)."""
    t = g.tt(1.6)
    n = len(t)
    env = g.bell(t, 0.55, 0.3, 0.5)
    f = 92.0 * (1 + 0.04 * g.bell(t, 0.55, 0.25, 0.4))
    hum = g.saber_tone(f, rng, grit=0.05) * env * 0.7
    contact = g.bp(g.noise(n, rng), 5200, 4) * g.bell(t, 0.55, 0.03, 0.18) * 0.25
    tone = g.bell_partials(n, 740, [1, 2.0, 3.01], [0.6, 0.35, 0.2], [0.3, 0.1, 0.05], rng, t0=0.52, attack=0.02)
    return g.reverb(hum + contact + tone, rng, 1.6, 0.3, bright=3500)


def g_tablet_break(rng):
    """A seal cracks, then a stone tablet snaps in two and drops with a puff of dust (about 1.6 s)."""
    t = g.tt(1.7)
    n = len(t)
    seal = g.crack(t, rng, 0.02, 0.006, 2500) * 0.6 + g.bp(g.noise(n, rng), 1400, 2) * g.env_ad(t, 0.001, 0.03, 0.02) * 0.3
    snap = g.crack(t, rng, 0.42, 0.02, 600) * 1.4 + g.thump(t, 180, 70, 0.02, 0.08, t0=0.42) * 0.6
    ring = g.metal_ring(t, rng, 820, 5, 0.12, t0=0.42) * 0.15
    drop = g.thump(t, 90, 45, 0.03, 0.15, t0=0.78) * 0.8 + g.thump(t, 110, 50, 0.03, 0.1, t0=0.9) * 0.5
    dust = g.lp(g.noise(n, rng), 900) * g.env_ad(t, 0.02, 0.25, 0.8) * 0.4
    grit = g.bp(g.crackle(n, 200 * np.exp(-np.maximum(t - 0.8, 0) / 0.25) * (t > 0.78), rng), 2000, 1.0)
    return g.reverb(seal + snap + ring + drop + dust + grit, rng, 1.3, 0.28, bright=4000)


# ---------------------------------------------------------------- Milestone 3: missions

def g_mission_accept(rng):
    """Accept: a holo-terminal confirmation, a soft click and two rising tones (about 0.6 s)."""
    t = g.tt(0.7)
    n = len(t)
    click = g.bp(g.noise(n, rng), 3200, 2) * g.env_ad(t, 0.0005, 0.006) * 0.6
    a = np.sin(g.phase(np.full(n, 660.0), n)) * g.env_ad(t, 0.004, 0.09, 0.03)
    b = np.sin(g.phase(np.full(n, 990.0), n)) * g.env_ad(t, 0.004, 0.16, 0.13)
    sheen = np.sin(g.phase(np.full(n, 1980.0), n)) * g.env_ad(t, 0.004, 0.08, 0.13) * 0.15
    return g.reverb(click + 0.5 * a + 0.55 * b + sheen, rng, 0.8, 0.22, bright=7000)


def g_mission_update(rng):
    """Progress: a single soft blip with a tick under it, quieter and shorter than accept (about 0.35 s)."""
    t = g.tt(0.45)
    n = len(t)
    tick = g.bp(g.noise(n, rng), 2600, 3) * g.env_ad(t, 0.0005, 0.004) * 0.5
    f = 880.0 * (1 + 0.05 * np.exp(-t / 0.02))
    blip = np.sin(g.phase(f, n)) * g.env_ad(t, 0.003, 0.07, 0.01) * 0.6
    return g.reverb(tick + blip, rng, 0.6, 0.18, bright=7000)


def g_mission_complete(rng):
    """Completion banner: a three-note rising bell arpeggio and a short warm pad, distinct from
    story.lesson_complete's two-bell flourish (about 2.2 s)."""
    t = g.tt(2.3)
    n = len(t)
    notes = np.zeros(n)
    for k, (f, t0) in enumerate([(523.25, 0.0), (659.25, 0.12), (783.99, 0.24)]):
        notes += g.bell_partials(n, f, [1, 2.0, 2.76], [0.9 + 0.2 * k, 0.4, 0.25], [1, 0.25, 0.12], rng, t0=t0, attack=0.005)
    pad = sum(a * np.sin(g.phase(np.full(n, f), n)) for f, a in [(261.6, 0.5), (392.0, 0.3), (523.25, 0.18)])
    pad *= np.clip((t - 0.2) / 0.3, 0, 1) * np.exp(-np.maximum(t - 0.6, 0) / 0.5) * 0.4
    return g.reverb(notes * 0.55 + pad, rng, 1.8, 0.32, bright=6500)


FILES = {
    "story.two_voices": {"story/two_voices": g_two_voices},
    "story.lesson_complete": {"story/lesson_complete": g_lesson_complete},
    "story.duel_begin": {"story/duel_begin": g_duel_begin},
    "story.duel_end": {"story/duel_end": g_duel_end},
    # Milestone 3
    "site.discovered": {
        "site/discovered1": make_site_discovered(261.63, "warm"),
        "site/discovered2": make_site_discovered(233.08, "hollow"),
        "site/discovered3": make_site_discovered(293.66, "metal"),
    },
    "story.ones_that_sing": {"story/ones_that_sing": g_ones_that_sing},
    "story.calling_holocron": {"story/calling_holocron": g_calling_holocron},
    "story.calling_reliquary": {"story/calling_reliquary": g_calling_reliquary},
    "story.calling_fall": {"story/calling_fall": g_calling_fall},
    "story.collector_fallen": {"story/collector_fallen": g_collector_fallen},
    "story.rank_three": {"story/rank_three": g_rank_three},
    "story.cairn_touch": {"story/cairn_touch": g_cairn_touch},
    "story.blade_touch": {"story/blade_touch": g_blade_touch},
    "story.tablet_break": {"story/tablet_break": g_tablet_break},
    "mission.accept": {"mission/accept": g_mission_accept},
    "mission.update": {"mission/update": g_mission_update},
    "mission.complete": {"mission/complete": g_mission_complete},
}
