"""PLACEHOLDER sound recipes for the Milestone 5 powers: Force Stasis, Valor, Drain, Fear, Rage, Mind Trick, and the
Focus key's click.

Owned by W6 (powers). Synthesised from scratch with the helpers in gen_sounds.py (noise, filters, sweeps, partials,
thumps); nothing is sampled. See events_m5.json for the event list and docs/MILESTONE_5.md sections 9.1 and 9.3 to 9.8
for each power's intended sound. File names are the placeholder names (<group>/<rest>), so sounds.json does not change
when these replace them. Loops are seamless (loop=True filters and circular placement).

    python3 tools/assets/gen_sounds.py --m5 --feature force_m5
"""
import gen_sounds as g

np = g.np


# ---------------------------------------------------------------- stasis

def g_stasis_cast(rng):
    """A glassy chime: a struck pane of ice ringing high and clean, with a dry crackle of frost as it takes hold."""
    t = g.tt(1.8)
    n = len(t)
    ratios = [1.0, 2.76, 5.40, 8.93]
    glass = g.bell_partials(n, 1180, ratios, [0.9, 0.5, 0.25, 0.12], [1.0, 0.5, 0.3, 0.15], rng, attack=0.002)
    under = g.bell_partials(n, 590, [1.0, 2.0], [0.7, 0.35], [0.35, 0.12], rng, t0=0.01, attack=0.004)
    frost = g.bp(g.crackle(n, 900 * np.exp(-t / 0.12), rng), 6500, 1.2) * 0.35
    tick = g.crack(t, rng, 0.0, 0.003, 4000) * 0.25
    return g.reverb(glass + under + frost + tick, rng, 1.8, 0.35, bright=8000)


def g_stasis_loop(rng):
    """Seamless: a faint, high shimmer of still air over slow creaks of rime settling."""
    L = 3.0
    t = g.tt(L)
    n = len(t)
    shimmer = np.zeros(n)
    for f, a in [(2350.0, 1.0), (2362.0, 0.8), (3540.0, 0.35)]:
        shimmer += a * np.sin(g.phase(np.full(n, f), n, loop=True))
    shimmer *= 0.5 + 0.2 * np.sin(2 * np.pi * 2 * t / L) + 0.1 * np.sin(2 * np.pi * 5 * t / L + 1.3)
    air = g.bp(g.noise(n, rng), 5200, 1.5, loop=True) * 0.25
    creak = np.zeros(n)
    for k in range(5):
        dur = rng.uniform(0.08, 0.16)
        tc = g.tt(dur)
        c = g.bp(g.crackle(len(tc), 1400, rng), rng.uniform(2800, 4200), 3.0) * np.sin(np.pi * tc / dur)
        g.place_circular(creak, c * rng.uniform(0.5, 1.0), (k + rng.uniform(0.1, 0.8)) * L / 5)
    return shimmer * 0.12 + air + creak * 0.6


def g_stasis_break(rng):
    """The hold shatters: a crisp crack of ice and a patter of grit falling away."""
    t = g.tt(1.2)
    n = len(t)
    snap = g.crack(t, rng, 0.0, 0.008, 2500) * 1.4
    ring = g.bell_partials(n, 1420, [1.0, 2.63, 4.9], [0.18, 0.1, 0.05], [0.6, 0.3, 0.15], rng, attack=0.001)
    patter = g.bp(g.crackle(n, 600 * np.exp(-t / 0.25), rng), 5200, 1.0) * 0.8
    drop = g.whoosh(t, rng, 3000 * np.exp(-t / 0.2) + 600, 1.0, g.env_ad(t, 0.005, 0.15)) * 0.4
    return g.reverb(snap + ring + patter + drop, rng, 1.0, 0.25, bright=7500)


# ---------------------------------------------------------------- valor

def g_valor_cast(rng):
    """A low swell: a warm open chord rising out of a breath of air, then settling, unhurried."""
    t = g.tt(2.2)
    n = len(t)
    swell = np.clip(t / 0.7, 0, 1) ** 1.5 * np.exp(-np.maximum(t - 0.8, 0) / 0.55)
    chord = np.zeros(n)
    for f, a in [(98.0, 1.0), (147.0, 0.6), (196.0, 0.45), (246.9, 0.2), (98.6, 0.5)]:
        chord += a * np.sin(g.phase(f * (1 + 0.004 * np.sin(2 * np.pi * 0.7 * t)), n))
    warm = g.lp(chord, 900) * swell
    breath = g.sweep(g.noise(n, rng), 300 + 1200 * np.clip(t / 0.7, 0, 1), "bp", 1.0) * swell * 0.25
    lift = g.thump(t, 80, 50, 0.2, 0.5, t0=0.55, attack=0.15) * 0.35
    return g.reverb(warm * 0.6 + breath + lift, rng, 2.2, 0.35, bright=3000)


def g_valor_end(rng):
    """The courage settles: the chord's last breath falls away softly."""
    t = g.tt(1.2)
    n = len(t)
    env = g.env_ad(t, 0.08, 0.35)
    tone = (np.sin(g.phase(196.0 - 30 * t, n)) + 0.5 * np.sin(g.phase(147.0 - 20 * t, n))) * env
    air = g.bp(g.noise(n, rng), 700, 0.9) * g.env_ad(t, 0.05, 0.3) * 0.3
    return g.reverb(g.lp(tone, 800) * 0.5 + air, rng, 1.6, 0.3, bright=2500)


# ---------------------------------------------------------------- drain

def g_drain_loop(rng):
    """Seamless: a slow, wet pull of air through a narrow throat, throbbing twice a second (the pulses), over a dark drone."""
    L = 2.0
    t = g.tt(L)
    n = len(t)
    throb = 0.55 + 0.45 * np.sin(2 * np.pi * 4 * t / L - np.pi / 2) ** 2
    pull = g.bp(g.noise(n, rng), 520, 1.6, loop=True) * throb
    hiss = g.bp(g.noise(n, rng), 1900, 3.0, loop=True) * (0.3 + 0.3 * throb)
    drone = np.zeros(n)
    for f, a in [(61.0, 1.0), (61.5, 0.8), (122.0, 0.3)]:
        drone += a * np.sin(g.phase(np.full(n, f), n, loop=True))
    return pull + hiss * 0.5 + drone * 0.12 * (0.8 + 0.2 * throb)


def g_drain_end(rng):
    """The draw lets go: the pull sighs out downward and a soft, hollow thud follows."""
    t = g.tt(1.0)
    n = len(t)
    sigh = g.whoosh(t, rng, 900 * np.exp(-t / 0.25) + 180, 1.4, g.env_ad(t, 0.01, 0.25)) * 1.2
    thud = g.thump(t, 90, 48, 0.03, 0.18, t0=0.12) * 0.5
    return g.reverb(sigh + thud, rng, 1.0, 0.25, bright=3000)


# ---------------------------------------------------------------- fear

def g_fear_cast(rng):
    """A whispering rush: many breathy voices surge past at once on a cold gust, then thin out."""
    dur = 1.8
    t = g.tt(dur)
    n = len(t)
    voices = np.zeros(n)
    for i, sc in enumerate([0.9, 1.05, 1.18, 0.82]):
        v = g.whisper_voice(dur, rng, sc, t_start=0.02 + 0.05 * i, t_end=dur - 0.3)
        voices += g.norm(v) * rng.uniform(0.5, 0.9)
    surge = g.bell(t, 0.35, 0.12, 0.5)
    rush = g.whoosh(t, rng, 500 + 2600 * g.bell(t, 0.3, 0.1, 0.35), 1.2, surge) * 1.3
    low = g.lp(g.noise(n, rng), 140) * surge * 1.2
    return g.reverb(voices * surge * 0.7 + rush + low, rng, 1.8, 0.4, bright=4500)


# ---------------------------------------------------------------- rage

def g_rage_cast(rng):
    """A growl in the Force: a low, rough snarl rising under a hard push of pressure."""
    t = g.tt(1.4)
    n = len(t)
    f = 70 + 25 * g.bell(t, 0.35, 0.15, 0.4) + 6 * np.sin(2 * np.pi * 23 * t)
    rough = g.sat(g.saw(g.phase(f, n), 95) * (1 + 0.35 * g.noise(n, rng) * 0.3), 2.5)
    snarl = (g.bp(rough, 420, 2.0) + 0.6 * g.bp(rough, 900, 3.0) + 0.5 * g.lp(rough, 200)) * g.env_ad(t, 0.12, 0.45)
    push = g.thump(t, 85, 38, 0.04, 0.3, t0=0.05) * 0.8
    air = g.whoosh(t, rng, 300 + 900 * g.bell(t, 0.2, 0.08, 0.3), 1.0, g.bell(t, 0.2, 0.08, 0.35)) * 0.6
    return g.reverb(snarl * 0.8 + push + air, rng, 1.2, 0.25, bright=3500)


def g_rage_loop(rng):
    """Seamless, for the caster only: blood pounding in the ears (a heavy double beat) over a low, uneasy rumble."""
    L = 2.4
    t = g.tt(L)
    n = len(t)
    beats = np.zeros(n)
    beat = 0.6
    for k in range(4):
        b = g.thump(g.tt(0.3), 62, 40, 0.02, 0.07)
        g.place_circular(beats, b, k * beat)
        g.place_circular(beats, b * 0.6, k * beat + 0.17)
    rumble = g.lp(g.noise(n, rng), 120, loop=True) * (0.8 + 0.2 * np.sin(2 * np.pi * 2 * t / L))
    return g.lp(beats, 300, loop=True) * 1.2 + rumble * 0.6


def g_rage_end(rng):
    """The fury drains out: a long, falling breath and the rumble sinking away."""
    t = g.tt(1.4)
    n = len(t)
    breath = g.whoosh(t, rng, 1000 * np.exp(-t / 0.35) + 250, 1.0, g.env_ad(t, 0.05, 0.4)) * 0.9
    sink = g.lp(g.noise(n, rng), 160) * g.env_ad(t, 0.02, 0.5) * 1.2
    tone = np.sin(g.phase(80 - 30 * t, n)) * g.env_ad(t, 0.05, 0.4) * 0.3
    return g.reverb(breath + sink + tone, rng, 1.4, 0.3, bright=2500)


# ---------------------------------------------------------------- mind trick

def g_mind_trick_cast(rng):
    """A soft, low tone: one calm note that bends very slightly and fades, with air moving gently round it."""
    t = g.tt(1.5)
    n = len(t)
    f = 174.0 * (1 - 0.015 * np.clip(t / 0.6, 0, 1)) * (1 + 0.003 * np.sin(2 * np.pi * 4.5 * t))
    tone = (np.sin(g.phase(f, n)) + 0.25 * np.sin(g.phase(f * 2, n)) + 0.1 * np.sin(g.phase(f * 3, n))) * g.env_ad(t, 0.15, 0.45)
    air = g.bp(g.noise(n, rng), 900, 0.8) * g.bell(t, 0.25, 0.12, 0.35) * 0.12
    return g.reverb(tone * 0.6 + air, rng, 1.8, 0.4, bright=3500)


def g_mind_trick_fail(rng):
    """The mind resists: the same calm note starts, meets something hard and stops dead in a dull, muted knock."""
    t = g.tt(0.8)
    n = len(t)
    f = 174.0 * (1 + 0.04 * np.clip(t / 0.12, 0, 1))
    tone = np.sin(g.phase(f, n)) * g.env_ad(t, 0.05, 0.06)
    clash = np.sin(g.phase(np.full(n, 185.0), n)) * g.env_ad(t, 0.04, 0.05) * 0.6
    knock = g.thump(t, 140, 80, 0.01, 0.06, t0=0.09) * 0.6
    return g.reverb(g.lp(tone + clash, 1200) * 0.7 + knock, rng, 0.6, 0.2, bright=2500)


# ---------------------------------------------------------------- focus

def g_focus(rng):
    """A soft click with a faint, short tick of tone: the second bar comes forward (the local player only)."""
    t = g.tt(0.25)
    n = len(t)
    c = g.click(t, rng, 0.004, bright=0.7) * 0.5
    tick = np.sin(g.phase(np.full(n, 880.0), n)) * g.env_ad(t, 0.002, 0.03, t0=0.004) * 0.25
    return g.lp(c + tick, 5000)


FILES = {
    "force.stasis.cast": {"force/stasis_cast": g_stasis_cast},
    "force.stasis.loop": {"force/stasis_loop": g_stasis_loop},
    "force.stasis.break": {"force/stasis_break": g_stasis_break},
    "force.valor.cast": {"force/valor_cast": g_valor_cast},
    "force.valor.end": {"force/valor_end": g_valor_end},
    "force.drain.loop": {"force/drain_loop": g_drain_loop},
    "force.drain.end": {"force/drain_end": g_drain_end},
    "force.fear.cast": {"force/fear_cast": g_fear_cast},
    "force.rage.cast": {"force/rage_cast": g_rage_cast},
    "force.rage.loop": {"force/rage_loop": g_rage_loop},
    "force.rage.end": {"force/rage_end": g_rage_end},
    "force.mind_trick.cast": {"force/mind_trick_cast": g_mind_trick_cast},
    "force.mind_trick.fail": {"force/mind_trick_fail": g_mind_trick_fail},
    "force.focus": {"force/focus": g_focus},
}
