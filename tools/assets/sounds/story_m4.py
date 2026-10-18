"""PLACEHOLDER sound recipes for Chapter Four, the hubs and the vendors (Milestone 4).

Owned by W2 (story, vendors, economy). Synthesised from scratch with the helpers in
gen_sounds.py (oscillators, filtered noise, bell partials, simple reverb); nothing is sampled
or imitated from any recording. See events_m4.json for the event list and docs/MILESTONE_4.md
sections 7.7, 7.8, 8.2 and 13.3 for each moment. File names are the placeholder names
(<group>/<rest of the id>), so sounds.json and the subtitles do not change.

    python3 tools/assets/gen_sounds.py --m4 --feature story_m4
"""
import gen_sounds as g

np = g.np


# ---------------------------------------------------------------- banners (lesson_complete 8 and 9)

def g_chapter_four(rng):
    """The Chapter Four banner (about 3 s): a low muted-brass swell under a falling-then-rising
    three-bell figure that settles on an open fifth: resolved, but with weight (the hunter beaten,
    the report made)."""
    t = g.tt(3.0)
    n = len(t)
    f0 = 146.83
    brass_f = np.full(n, f0)
    brass = g.saw(g.phase(brass_f, n), f0, nh=14, roll=1.3) + 0.6 * g.saw(g.phase(brass_f * 1.4983, n), f0 * 1.5, nh=10, roll=1.3)
    brass = g.sweep(brass, 300 + 900 * g.bell(t, 0.7, 0.35, 1.1), "lp", nb=10) * g.bell(t, 0.7, 0.3, 1.0) * 0.55
    bells = (g.bell_partials(n, 587.33, [1, 2.0, 2.76], [1.0, 0.45, 0.25], [1.0, 0.45, 0.25], rng, t0=0.05, attack=0.005)
             + g.bell_partials(n, 493.88, [1, 2.0, 2.76], [1.0, 0.45, 0.25], [0.9, 0.4, 0.2], rng, t0=0.32, attack=0.005)
             + g.bell_partials(n, 659.26, [1, 2.0, 2.76], [1.6, 0.6, 0.3], [1.1, 0.5, 0.25], rng, t0=0.62, attack=0.005))
    floor = g.thump(t, 90, 48, 0.08, 0.5, t0=0.02) * 0.5
    return g.reverb(brass + bells * 0.55 + floor, rng, 2.4, 0.35, bright=5000)


def g_planet_arrive(rng):
    """The new-world banner (about 2.6 s): an airy swell like wind over open ground, a slow
    upward fourth in soft sine pads and a single high chime as the name settles."""
    t = g.tt(2.6)
    n = len(t)
    air = g.sweep(g.noise(n, rng), 500 + 1800 * g.bell(t, 0.9, 0.5, 1.0), "bp", q=0.9, nb=10) * g.bell(t, 0.9, 0.45, 0.9) * 0.6
    pad_f = np.where(t < 0.55, 261.63, 349.23)
    glide = g.lp(pad_f, 8.0)
    pad = sum(a * np.sin(g.phase(glide * r, n)) for r, a in [(1.0, 0.5), (1.5, 0.25), (2.0, 0.15)])
    pad *= np.clip(t / 0.4, 0, 1) * np.exp(-np.maximum(t - 1.2, 0) / 0.7) * 0.6
    chime = g.bell_partials(n, 1046.5, g.CHIME_RATIOS[:3], [1.1, 0.5, 0.25], [0.6, 0.25, 0.1], rng, t0=0.9, attack=0.004)
    return g.reverb(air + pad + chime, rng, 2.2, 0.4, bright=6500)


# ---------------------------------------------------------------- the seeker droid (seeker_scan)

def g_seeker_scan(rng):
    """The scan (2 s, the scan's length): a thin electronic buzz whose filter sweeps down and up
    once every 1.5 s, the scan line's period, over a faint servo whirr and a soft tick at each turn."""
    t = g.tt(2.0)
    n = len(t)
    f = 820 + 14 * np.sin(2 * np.pi * 31 * t)
    buzz = g.pulse(g.phase(f, n), 900, w=0.18, nh=18)
    sweep_fc = 1400 + 1000 * np.cos(2 * np.pi * t / 1.5)
    buzz = g.sweep(buzz, sweep_fc, "bp", q=3.0, nb=12) * 0.5
    servo = g.bp(g.noise(n, rng), 2600, 5.0) * (0.12 + 0.05 * np.sin(2 * np.pi * 9 * t))
    ticks = g.click(t, rng, 0.75, 1.2) * 0.2 + g.click(t, rng, 1.5, 1.2) * 0.2
    shape = np.clip(t / 0.06, 0, 1) * np.clip((2.0 - t) / 0.2, 0, 1)
    return g.reverb((buzz + servo + ticks) * shape, rng, 0.5, 0.12, bright=7000)


def g_seeker_chirp(rng):
    """One chirp (about 0.3 s): two quick upward FM blips, a little droid voice; played twice."""
    t = g.tt(0.3)
    n = len(t)
    out = np.zeros(n)

    for t0, f_lo, f_hi in [(0.0, 1500, 2600), (0.09, 1900, 3300)]:
        tr = np.maximum(t - t0, 0)
        f = f_lo + (f_hi - f_lo) * np.clip(tr / 0.06, 0, 1)
        mod = 1 + 0.25 * np.sin(2 * np.pi * 55 * tr)
        out += np.sin(g.phase(f * mod, n)) * g.env_ad(t, 0.004, 0.035, t0)

    return g.reverb(out * 0.8, rng, 0.35, 0.15, bright=8000)


def g_seeker_fly(rng):
    """The droid climbing away (about 1.6 s): a repulsor hum that rises in pitch and thins out
    as it leaves, with a breath of air on the way up."""
    t = g.tt(1.6)
    n = len(t)
    f = 180 * (1 + 0.9 * (t / 1.6) ** 1.5)
    hum = g.saw(g.phase(f, n), 360, nh=10, roll=1.4) + 0.4 * np.sin(g.phase(f * 2.01, n))
    hum = g.sweep(hum, 1600 - 900 * (t / 1.6), "lp", nb=8)
    air = g.sweep(g.noise(n, rng), 700 + 1500 * (t / 1.6), "bp", q=1.2, nb=8) * 0.25
    level = np.clip(t / 0.05, 0, 1) * np.exp(-t / 0.75)
    return g.reverb((hum * 0.6 + air) * level, rng, 0.9, 0.25, bright=5000)


# ---------------------------------------------------------------- vendors and credits

def g_vendor_buy(rng):
    """A purchase (about 0.6 s): three quick chits counted onto a counter, then a soft two-tone
    accept beep from the vendor's slate."""
    t = g.tt(0.6)
    n = len(t)
    chits = sum(g.click(t, rng, t0, 1.3) * a for t0, a in [(0.0, 0.5), (0.05, 0.42), (0.1, 0.36)])
    tones = np.zeros(n)

    for t0, f in [(0.2, 988.0), (0.3, 1318.5)]:
        tones += np.sin(g.phase(np.full(n, f), n)) * g.env_ad(t, 0.004, 0.06, t0)

    return g.reverb(chits * 0.6 + tones * 0.35, rng, 0.4, 0.12, bright=7000)


def g_vendor_refuse(rng):
    """A refusal (about 0.45 s): two low, short buzzes from the slate, the second lower."""
    t = g.tt(0.45)
    n = len(t)
    out = np.zeros(n)

    for t0, f in [(0.0, 220.0), (0.16, 174.6)]:
        gate = ((t >= t0) & (t < t0 + 0.11)).astype(float)
        gate = g.lp(gate, 120)
        out += g.lp(g.pulse(g.phase(np.full(n, f), n), f, w=0.4, nh=20), 1800) * gate

    return g.reverb(out * 0.5, rng, 0.3, 0.1, bright=4000)


def g_credits_pickup(rng):
    """Credits picked up (about 0.35 s): two or three bright clinks of small metal-edged chits."""
    t = g.tt(0.35)
    count = int(rng.integers(2, 4))
    out = np.zeros(len(t))

    for i in range(count):
        t0 = 0.012 + i * rng.uniform(0.035, 0.06)
        out += g.metal_ring(t, rng, rng.uniform(2600, 3400), 4, 0.05, t0=t0) * (0.8 - 0.15 * i)
        out += g.click(t, rng, t0, 1.4) * 0.25

    return g.reverb(out * 0.6, rng, 0.25, 0.1, bright=9000)


# ---------------------------------------------------------------- the hubs

def g_apprentice_spar(rng):
    """Apprentices sparring with training sabers (about 0.7 s): a low-power blade thwack with a
    short buzz and a dull knock, quieter and duller than a duelling clash."""
    t = g.tt(0.7)
    n = len(t)
    f = 95 + 25 * np.exp(-t / 0.08)
    buzz = g.saber_tone(f, rng, bright=1500, grit=0.05) * g.env_ad(t, 0.004, 0.12)
    knock = g.thump(t, 220, 120, 0.02, 0.06) * 0.6
    swish = g.whoosh(t, rng, 600 + 900 * g.bell(t, 0.0, 0.06), 1.2, g.env_ad(t, 0.003, 0.05)) * 0.3
    return g.reverb(buzz * 0.4 + knock + swish, rng, 0.6, 0.2, bright=4000)


FILES = {
    "story.chapter_four": {"story/chapter_four": g_chapter_four},
    "planet.arrive": {"planet/arrive": g_planet_arrive},
    "seeker.scan": {"seeker/scan": g_seeker_scan},
    "seeker.chirp": {"seeker/chirp": g_seeker_chirp},
    "seeker.fly": {"seeker/fly": g_seeker_fly},
    "vendor.buy": {"vendor/buy": g_vendor_buy},
    "vendor.refuse": {"vendor/refuse": g_vendor_refuse},
    "credits.pickup": {"credits/pickup": g_credits_pickup},
    "hub.apprentice_spar": {"hub/apprentice_spar": g_apprentice_spar},
}
