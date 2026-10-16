"""PLACEHOLDER sound recipes for the identity feature (alignment, rank, dialogue, settlers).

Owned by the identity worker. Synthesised from scratch with the helpers in
gen_sounds.py; see events_m2.json for the event list and docs/AUDIO_BRIEF.md for
the variation rules. Replace with final designs as they are made.
"""
import gen_sounds as g

np = g.np


def g_alignment_light(rng):
    """A soft rising two-note tone with air."""
    t = g.tt(1.6)
    a = g.bell_partials(len(t), 523.25, [1, 2.0, 3.0], [0.9, 0.4, 0.2], [1, 0.25, 0.1], rng, attack=0.04)
    b = g.bell_partials(len(t), 783.99, [1, 2.0, 3.0], [1.0, 0.45, 0.2], [0.9, 0.2, 0.08], rng, t0=0.18, attack=0.04)
    air = g.hp(g.noise(len(t), rng), 5000) * g.env_ad(t, 0.2, 0.4) * 0.05
    return g.reverb(a + b + air, rng, 2.0, 0.4, bright=6500)


def g_alignment_dark(rng):
    """A low falling murmur."""
    t = g.tt(1.6)
    f = 98 * (1 - 0.18 * np.clip(t / 1.2, 0, 1))
    tone = g.lp(g.saw(g.phase(f, len(t)), 100, nh=20) + g.saw(g.phase(f * 1.01, len(t)), 100, nh=20), 500)
    breath = g.sweep(g.noise(len(t), rng), 700 - 300 * np.clip(t / 1.4, 0, 1), "bp", 1.5, nb=8) * 0.5
    return g.reverb((tone * 0.5 + breath) * g.env_ad(t, 0.15, 0.6), rng, 1.8, 0.35, bright=2500)


def g_rank_up(rng):
    """A warm swell resolving into a bright chord."""
    t = g.tt(2.6)
    swell = g.sweep(g.noise(len(t), rng), 300 + 2500 * np.clip(t / 0.8, 0, 1), "lp", nb=10) * g.bell(t, 0.7, 0.3, 0.2) * 0.8
    chord = sum(g.bell_partials(len(t), f, [1, 2.0, 2.76], [1.4, 0.7, 0.4], [1, 0.3, 0.15], rng, t0=0.75, attack=0.01)
                for f in (392.0, 493.88, 587.33))
    return g.reverb(swell + chord * 0.6, rng, 2.4, 0.4, bright=6000)


def g_dialogue_open(rng):
    """A short holo flicker on."""
    t = g.tt(0.45)
    sw = g.sweep(g.noise(len(t), rng), 800 + 3000 * np.clip(t / 0.12, 0, 1), "bp", 3, nb=8) * g.env_ad(t, 0.01, 0.08) * 0.6
    tone = np.sin(2 * np.pi * 1318.5 * t) * g.env_ad(t, 0.005, 0.12, 0.06) * 0.3
    return g.reverb(sw + tone, rng, 0.6, 0.15, bright=7000)


def g_dialogue_select(rng):
    """A crisp interface click."""
    t = g.tt(0.25)
    return g.reverb(g.click(t, rng, 0.005, 1.1), rng, 0.3, 0.1)


def make_settler_hurt(f_hi, f1):
    def gen(rng):
        t = g.tt(0.45)
        return g.reverb(g.grunt(t, rng, 0.01, 0.16, f_hi, f_hi * 0.85, f1) * 1.5, rng, 0.4, 0.1, bright=4000)
    return gen


def g_settler_death(rng):
    t = g.tt(1.1)
    x = g.grunt(t, rng, 0.0, 0.5, 190, 120, 600, 1150)
    x += g.lp(g.noise(len(t), rng), 1100) * g.env_ad(t, 0.1, 0.25, 0.45) * 0.25
    x += g.thump(t, 90, 45, 0.05, 0.1, t0=0.7) * 0.4
    return g.reverb(x, rng, 0.6, 0.12, bright=4000)


def g_settler_freed(rng):
    """Rope snapping, then a relieved breath."""
    t = g.tt(0.9)
    snap = g.crack(t, rng, 0.02, 0.01, 1200) * 1.2 + g.rustle(t, rng, 0.03, 0.2, 60, fc=1800) * 0.6
    sigh = g.sweep(g.noise(len(t), rng), 1100 - 500 * np.clip((t - 0.3) / 0.5, 0, 1), "bp", 1.2, nb=8) * g.env_ad(t, 0.08, 0.25, 0.3) * 0.5
    return g.reverb(snap + sigh, rng, 0.5, 0.12)


FILES = {
    "identity.alignment_light": {"identity/alignment_light": g_alignment_light},
    "identity.alignment_dark": {"identity/alignment_dark": g_alignment_dark},
    "identity.rank_up": {"identity/rank_up": g_rank_up},
    "dialogue.open": {"dialogue/open": g_dialogue_open},
    "dialogue.select": {"dialogue/select": g_dialogue_select},
    "settler.hurt": {"settler/hurt1": make_settler_hurt(210, 640), "settler/hurt2": make_settler_hurt(180, 580)},
    "settler.death": {"settler/death": g_settler_death},
    "settler.freed": {"settler/freed": g_settler_freed},
}
