"""PLACEHOLDER sound recipes for the holocron feature (Milestone 3).

Owned by the force and holocron worker (W3; written by its powers half). Synthesised from scratch
with the helpers in gen_sounds.py; see events_m3.json for the event list and docs/MILESTONE_3.md
sections 4.4 to 4.8 and 13.3 for each moment. The palette: old stone and crystal. A holocron's
voice is a soft, slightly beating crystal tone (the hum) over stone grit; its seams open with a
small unlocking click and a bloom of that tone; the gatekeeper forms out of a shimmer and folds
back into it. Nothing here is a fanfare.

Every file keeps its placeholder name (holocron/<rest of the id>), so sounds.json and the
subtitles stay byte-identical.
"""
import gen_sounds as g

np = g.np

# The holocron's tone: a soft D with a fifth and a ninth, slightly detuned so it beats slowly.
TONE = [(146.83, 1.0), (220.0, 0.55), (329.6, 0.3), (440.0, 0.12)]


def tone_pad(t, env, detune=0.4, scale=1.0):
    """The holocron chord under an envelope, each partial doubled a fraction of a hertz apart."""
    n = len(t)
    x = np.zeros(n)
    for f, a in TONE:
        for det, aa in ((0.0, 1.0), (detune, 0.7)):
            x += a * aa * np.sin(g.phase(np.full(n, f * scale + det), n))
    return x * env


def grind(t, rng, env, fc=900):
    """Stone sliding on stone: band-passed grit."""
    return g.bp(g.crackle(len(t), 600, rng), fc, 0.8) * env * 1.6 + g.lp(g.noise(len(t), rng), 300) * env * 0.6


def click(t, rng, t0, bright=4000):
    """A small mechanism catching: a tight tick with a tiny ring."""
    tick = g.hp(g.crack(t, rng, t0, 0.003, 1200), 600)
    ring = g.bell_partials(len(t), bright / 2, [1, 1.7], [0.05, 0.03], [0.3, 0.12], rng, t0=t0, attack=0.001)
    return tick + ring


def g_rise(rng):
    """The holocron lifts from its cradle: stone grit, then the tone rising softly underneath."""
    t = g.tt(1.8)
    env_grind = g.bell(t, 0.35, 0.15, 0.4)
    rise = np.clip(t / 1.2, 0, 1)
    n = len(t)
    lift = sum(a * np.sin(g.phase(f * (0.8 + 0.2 * rise), n)) for f, a in TONE[:3]) * g.bell(t, 1.1, 0.5, 0.4) * 0.35
    dust = g.sweep(g.noise(n, rng), 300 + 900 * rise, "bp", 1.0, nb=8) * g.bell(t, 0.6, 0.3, 0.5) * 0.25
    return g.reverb(grind(t, rng, env_grind) + lift + dust, rng, 1.8, 0.35, bright=3500)


def g_open(rng):
    """The seams unlock (three quick clicks) and the tone blooms out of them."""
    t = g.tt(2.2)
    clicks = click(t, rng, 0.02) + click(t, rng, 0.11, 4600) * 0.8 + click(t, rng, 0.19, 5200) * 0.7
    bloom = tone_pad(t, g.env_ad(t, 0.25, 0.7, t0=0.22), scale=2.0) * 0.35
    shimmer = g.bp(g.noise(len(t), rng), 6000, 3) * g.env_ad(t, 0.2, 0.5, t0=0.22) * 0.06
    return g.reverb(clicks + bloom + shimmer, rng, 2.2, 0.4, bright=6000)


def g_gatekeeper_form(rng):
    """A shimmer gathers and a figure settles into it: rising airy noise, then a warm chord fading in."""
    t = g.tt(2.4)
    u = np.clip(t / 1.2, 0, 1)
    shimmer = g.sweep(g.noise(len(t), rng), 1500 + 5000 * u, "bp", 3.0, nb=10) * g.bell(t, 1.0, 0.5, 0.3) * 0.3
    pad = tone_pad(t, g.bell(t, 1.5, 0.5, 0.6), scale=1.0) * 0.3
    sparkle = g.hp(g.crackle(len(t), 40 * g.bell(t, 0.9, 0.4, 0.3), rng), 5000) * 0.5
    return g.reverb(shimmer + pad + sparkle, rng, 2.5, 0.45, bright=6500)


def g_gatekeeper_fade(rng):
    """The figure folds back: the chord sinks and thins into a last breath of shimmer."""
    t = g.tt(2.0)
    n = len(t)
    sink = np.clip(t / 1.4, 0, 1)
    pad = sum(a * np.sin(g.phase(f * (1.0 - 0.12 * sink), n)) for f, a in TONE) * g.env_ad(t, 0.05, 0.55) * 0.35
    shimmer = g.sweep(g.noise(n, rng), 6000 - 4500 * sink, "bp", 3.0, nb=10) * g.bell(t, 0.5, 0.2, 0.5) * 0.25
    return g.reverb(pad + shimmer, rng, 2.4, 0.45, bright=5000)


def g_hum(rng):
    """Seamless: the holocron's tone, very soft, beating slowly, with a faint crystalline air above."""
    L = 4.0
    t = g.tt(L)
    n = len(t)
    x = np.zeros(n)
    for f, a in TONE:
        for det, aa in ((0.0, 1.0), (0.5, 0.7)):
            x += a * aa * np.sin(g.phase(np.full(n, f + det), n, loop=True) + rng.uniform(0, 6.28))
    x *= 0.85 + 0.15 * np.sin(2 * np.pi * t / L)
    air = g.bp(g.noise(n, rng), 5200, 3, loop=True) * 0.06 * (1 + 0.5 * np.sin(2 * np.pi * 2 * t / L))
    return g.reverb(x * 0.4 + air, rng, 1.6, 0.3, loop=True, bright=5000)


def g_seal(rng):
    """The seams close: a soft stone settle and a long dust exhale."""
    t = g.tt(1.4)
    settle = g.thump(t, 120, 60, 0.04, 0.12) * 0.6 + click(t, rng, 0.0, 2800) * 0.5
    exhale = g.sweep(g.noise(len(t), rng), 1400 * np.exp(-t / 0.4) + 250, "bp", 1.1, nb=10) * g.env_ad(t, 0.06, 0.45) * 0.7
    fade = tone_pad(t, g.env_ad(t, 0.005, 0.25), scale=2.0) * 0.12
    return g.reverb(settle + exhale + fade, rng, 1.4, 0.3, bright=3500)


def g_break(rng):
    """Pale stone splits along its seams: a crack, a crunch, the light escaping and guttering out."""
    t = g.tt(2.0)
    n = len(t)
    split = g.crack(t, rng, 0, 0.015, 700) * 1.4 + g.thump(t, 140, 60, 0.03, 0.1) * 0.5
    crunch = g.bp(g.crackle(n, 700 * np.exp(-t / 0.15), rng), 1800, 0.8) * 2.5
    gutter = np.clip(t / 0.08, 0, 1) * np.exp(-t / 0.7) * (0.6 + 0.4 * np.sign(np.sin(2 * np.pi * (14 - 8 * np.clip(t, 0, 1)) * t)))
    escape = sum(a * np.sin(g.phase(f * 2 * (1.0 - 0.3 * np.clip(t / 1.5, 0, 1)), n)) for f, a in TONE) * gutter * 0.3
    chips = g.hp(g.crackle(n, 60 * np.exp(-t / 0.5), rng), 3000) * 0.6
    return g.reverb(split + crunch + escape + chips, rng, 1.8, 0.35, bright=5000)


def g_listen(rng):
    """Motes flow into the listener: a gentle descending cascade of small chimes and a breath in."""
    t = g.tt(2.0)
    n = len(t)
    cascade = np.zeros(n)
    for i, f in enumerate((1318.5, 1174.7, 987.8, 880.0, 784.0)):
        cascade += g.bell_partials(n, f, [1, 2.0], [0.35, 0.15], [0.35, 0.08], rng, t0=0.08 + i * 0.14, attack=0.004)
    breath = g.sweep(g.noise(n, rng), 600 + 800 * np.clip(t / 1.0, 0, 1), "bp", 1.3, nb=8) * g.bell(t, 0.9, 0.4, 0.4) * 0.3
    return g.reverb(cascade + breath, rng, 2.0, 0.4, bright=6000)


def g_echo(rng):
    """A faint, far-off chord in a large hall, then the small tick of a shard settling."""
    t = g.tt(2.4)
    far = g.lp(tone_pad(t, g.bell(t, 0.5, 0.3, 0.7), scale=2.0), 2200) * 0.3
    shard = click(t, rng, 1.3, 5200) * 0.6
    return g.reverb(far + shard, rng, 3.2, 0.6, bright=4000)


def g_archive_return(rng):
    """The holocrons dim and settle into a travelling case: soft tones fading, cloth, a clasp."""
    t = g.tt(1.8)
    dim = tone_pad(t, g.env_ad(t, 0.02, 0.4), scale=2.0) * 0.2
    cloth = g.bp(g.noise(len(t), rng), 1800, 1.0) * g.bell(t, 0.55, 0.15, 0.2) * 0.35
    exhale = g.lp(g.noise(len(t), rng), 700) * g.bell(t, 0.8, 0.2, 0.3) * 0.4
    clasp = click(t, rng, 1.15, 3000) * 0.9 + g.thump(t, 220, 120, 0.02, 0.05, t0=1.15) * 0.3
    return g.reverb(dim + cloth + exhale + clasp, rng, 1.2, 0.3, bright=4000)


def g_archive_keep(rng):
    """Their seams warm and motes drift into the keeper: the chord swells warmly and rests."""
    t = g.tt(2.2)
    warm = tone_pad(t, g.bell(t, 1.0, 0.45, 0.6), scale=1.0) * 0.35
    lift = tone_pad(t, g.bell(t, 1.2, 0.5, 0.5), scale=2.0) * 0.12
    drift = g.bp(g.noise(len(t), rng), 2600, 2.0) * g.bell(t, 1.1, 0.4, 0.4) * 0.08
    return g.reverb(warm + lift + drift, rng, 2.2, 0.4, bright=4500)


def g_unmake(rng):
    """The seams flare and drain, the shells crack and slump, and a low chord resolves under it all."""
    t = g.tt(2.8)
    n = len(t)
    flare = g.sweep(g.noise(n, rng), 5000 * np.exp(-t / 0.3) + 600, "bp", 1.5, nb=10) * g.env_ad(t, 0.04, 0.35) * 0.6
    drain = sum(a * np.sin(g.phase(f * 2 * (1.0 - 0.45 * np.clip(t / 1.2, 0, 1)), n)) for f, a in TONE) * g.env_ad(t, 0.05, 0.6) * 0.25
    cracks = g.bp(g.crackle(n, 250 * g.bell(t, 0.9, 0.2, 0.3), rng), 1500, 0.8) * 2.0 + g.thump(t, 70, 40, 0.1, 0.3, t0=1.1) * 0.5
    sting = sum(a * np.sin(g.phase(np.full(n, f), n)) for f, a in [(73.4, 1.0), (110.0, 0.5), (146.8, 0.3)]) * g.env_ad(t, 0.3, 0.9, t0=1.2) * 0.4
    return g.reverb(flare + drain + cracks + sting, rng, 2.4, 0.4, bright=3500)


FILES = {
    "holocron.rise": {"holocron/rise": g_rise},
    "holocron.open": {"holocron/open": g_open},
    "holocron.gatekeeper_form": {"holocron/gatekeeper_form": g_gatekeeper_form},
    "holocron.gatekeeper_fade": {"holocron/gatekeeper_fade": g_gatekeeper_fade},
    "holocron.hum": {"holocron/hum": g_hum},
    "holocron.seal": {"holocron/seal": g_seal},
    "holocron.break": {"holocron/break": g_break},
    "holocron.listen": {"holocron/listen": g_listen},
    "holocron.echo": {"holocron/echo": g_echo},
    "holocron.archive_return": {"holocron/archive_return": g_archive_return},
    "holocron.archive_keep": {"holocron/archive_keep": g_archive_keep},
    "holocron.unmake": {"holocron/unmake": g_unmake},
}
