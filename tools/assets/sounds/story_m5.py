"""PLACEHOLDER sound recipes for Chapter Five, "The Long Hymn" (Milestone 5).

Owned by W4 (story and factions). Synthesised from scratch with the helpers in gen_sounds.py
(oscillators, filtered noise, bell partials, simple reverb); nothing is sampled or imitated from
any recording. See events_m5.json for the event list and docs/MILESTONE_5.md sections 6.2, 6.6 and
15.3 for each moment. File names are the placeholder names (<group>/<rest of the id>), so
sounds.json and the subtitles do not change.

    python3 tools/assets/gen_sounds.py --m5 --feature story_m5
"""
import gen_sounds as g

np = g.np


def choir(t, rng, freqs, vowel, amp, detune=0.004):
    """A small wordless choir: detuned saws on each pitch, shaped by one vowel's three formants.

    freqs is one frequency per voice, or an array per sample for a moving line."""
    n = len(t)
    f1, f2, f3 = vowel
    out = np.zeros(n)

    for f in freqs:
        f = np.broadcast_to(np.asarray(f, float), (n,))
        for d in (-detune, 0.0, detune):
            vib = 1 + 0.0035 * np.sin(2 * np.pi * rng.uniform(4.6, 5.6) * t + rng.uniform(0, 6.3))
            out += g.saw(g.phase(f * (1 + d) * vib, n), float(f.max()), nh=30, roll=1.1)

    voiced = g.bp(out, f1, 5.0) + 0.6 * g.bp(out, f2, 7.0) + 0.25 * g.bp(out, f3, 9.0)
    breath = g.bp(g.noise(n, rng), f2, 3.0) * 0.08
    return (voiced + breath) * amp


# ---------------------------------------------------------------- the banner (lesson_complete 11)

def g_chapter_five(rng):
    """The Chapter Five banner (about 3.2 s): a low drone under a wordless choir that rises a fourth
    and settles on an open, unresolved chord, with one stone-like bell at the turn: the Hymn heard
    for the first time, beautiful and wrong."""
    t = g.tt(3.2)
    n = len(t)
    drone = sum(a * np.sin(g.phase(np.full(n, f), n)) for f, a in [(73.42, 0.7), (73.9, 0.45), (110.0, 0.3)])
    drone *= np.clip(t / 0.5, 0, 1) * np.exp(-np.maximum(t - 2.2, 0) / 0.6) * 0.5
    line = g.lp(np.where(t < 0.9, 293.66, 392.0), 6.0)
    voices = choir(t, rng, [line, line * 1.5, line * 0.75], g.VOWELS[4], 0.09)
    voices *= g.bell(t, 1.5, 0.75, 1.0)
    # The turn: a struck stone ringing a little flat, its partials inharmonic.
    stone = g.bell_partials(n, 587.33, [1.0, 2.41, 3.93, 5.12], [1.4, 0.7, 0.4, 0.25], [1.0, 0.5, 0.3, 0.15], rng,
                            t0=0.9, attack=0.006, detune=0.004) * 0.35
    shimmer = g.hp(g.noise(n, rng), 6500) * g.bell(t, 1.2, 0.4, 0.9) * 0.04
    return g.reverb(drone + voices + stone + shimmer, rng, 2.8, 0.45, bright=4200)


# ---------------------------------------------------------------- the verse vision (6.6)

def g_verse_vision(rng):
    """The verse vision (6 s, the vision's length): the world hushes (a muffled swell of air that
    closes over everything), then a distant choir sings three slow notes of the Hymn over a stone
    that hums and beats against itself, and the hush lifts as the vision fades."""
    dur = 6.0
    t = g.tt(dur)
    n = len(t)
    # The hush: low air that swells in, holds and lifts, with the top end rolled away.
    hush_env = np.clip(t / 0.8, 0, 1) * np.clip((dur - t) / 1.4, 0, 1)
    hush = g.lp(g.noise(n, rng), 380) * hush_env * 2.2
    # The stone's hum: two close sines beating slowly, a fifth above, very soft.
    hum = sum(a * np.sin(g.phase(np.full(n, f), n)) for f, a in [(98.0, 0.5), (98.7, 0.4), (147.0, 0.2)])
    hum *= hush_env * 0.35
    # The choir, far away: three notes of the Hymn, each swelling and fading into the next.
    notes = [(0.7, 392.0), (2.3, 349.23), (3.9, 311.13)]
    sung = np.zeros(n)

    for i, (t0, f) in enumerate(notes):
        env = g.bell(t, t0 + 0.75, 0.55, 0.85)
        sung += choir(t, rng, [f, f * 1.2599, f * 0.5], g.VOWELS[(i * 3) % len(g.VOWELS)], 0.06) * env

    sung = g.lp(sung, 2600)
    # A faint high glint as each note begins, like light on old glass.
    glint = sum(g.bell_partials(n, f * 4, g.CHIME_RATIOS[:3], [0.9, 0.5, 0.3], [0.25, 0.1, 0.05], rng, t0=t0 + 0.2, attack=0.02)
                for t0, f in notes) * 0.18
    out = g.reverb(hush + hum + sung + glint, rng, 3.2, 0.55, bright=3600)
    return out[: g.ns(dur + 1.2)]


FILES = {
    "story.chapter_five": {"story/chapter_five": g_chapter_five},
    "story.verse_vision": {"story/verse_vision": g_verse_vision},
}
