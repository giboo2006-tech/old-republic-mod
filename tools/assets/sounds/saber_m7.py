"""PLACEHOLDER sound recipes for the Milestone 7 sabers (MILESTONE_7.md 11): the ignition, hum and retraction of
the five new emitters, and the two set effects (7.2).

Owned by SD. Synthesised from scratch with the helpers in gen_sounds.py, on its film-style saber blocks (film_hum,
pass_by, pass_curves, snap_hiss; see the Lightsaber section there) and the way saber_m5.py builds an emitter: one
voice per emitter (its hum and the layers that make it that emitter), and the ignition, hum and retraction all
built from that voice, so the three sound like one blade. Nothing is sampled. File names are the Phase 0
placeholder names (<group>/<rest>), so sounds.json and the subtitles do not change.

  Vanguard  heavier and lower than the Guardian: 66 Hz, a dark, dense motor with a strong low-mid body, a
            sub-octave and a slow mechanical chug; air rushing through the shroud's vent slots under it. A
            heavy clunk of an ignition, the hum hauled up from 55% of its pitch while the vents exhale.
  Warden    raw and unstable: 81 Hz with a rough buzz; the quillons' own hum a quarter hertz off, flickering,
            and their venting: crackle that comes in clusters, short spits of frying noise and the odd sag of
            the blade's power. It catches in three snaps (the blade, then each quillon).
  Watchman  clean and old-fashioned: 104 Hz, a mellow, hollow tone (its odd harmonics favoured, like an old
            valve set), a light, narrow buzz, no grit, a slow even flywheel wow. An old switch's two-part
            click and a smooth glide.
  Penitent  low and menacing: 60 Hz, dark, with a deep slow throb and a slow growl (the hum's period doubling,
            deepening and easing twice a loop, the blade snarling as it deepens). A slow, heavy ignition out of
            a low thud; it sinks away growling.
  Choir     harmonic and choral: 98 Hz (G) with a choir singing the hum's own harmonics in open fifths and
            octaves (G3 D4 G4 D5, the 2nd, 3rd, 4th and 6th) on an "oh", the parts breathing one after
            another; unlike the Cantor's major "ah", it sounds like an organ's mixture.
  saber.set_pulse  the Long Hymn's pulse: an indrawn swell into one resonant chord on the Choir's open fifths,
            sung and struck at once, a push of air and a low thump.
  saber.set_mark   Curator's Eye: a short, bright ping (a lens-focus chirp into a glassy ping and a fifth).

Hums are seamless 4 s loops: every modulation runs a whole number of cycles per loop, every filter is circular
and every placed event wraps around the loop. The pitches are in SaberAudio's table (the swing is pitched to the
blade's hum).

    python3 tools/assets/gen_sounds.py --m7 --feature saber_m7
"""
import gen_sounds as g

np = g.np

LOOP = 4.0
FV = 66.0    # Vanguard
FW = 81.0    # Warden
FWA = 104.0  # Watchman
FP = 60.0    # Penitent
FCH = 98.0   # Choir

# Each emitter's hum character (film_hum keywords over the M1 hum's).
HUM_V = dict(g.HUM, buzz=0.2, beat=0.3, throb=0.25, throb_depth=0.26, growl=16.0, growl_depth=0.06, bright=2000.0,
             tilt=0.7, body=((200.0, 1.2, 2.6), (700.0, 1.8, 1.9), (1500.0, 2.4, 1.3)), buzz_band=(1600.0, 1.1, 2.4),
             buzz_lp=3400.0, sub=0.2, drift=0.001)
HUM_W = dict(g.HUM, buzz=0.3, beat=0.35, throb=0.75, throb_depth=0.3, growl=28.0, growl_depth=0.07, bright=2600.0,
             body=((300.0, 1.3, 2.0), (1250.0, 2.0, 1.8)), buzz_band=(2400.0, 1.0, 2.8), buzz_lp=4800.0, drift=0.002,
             air=0.02)
# The quillons' hum: the same crystal, thinner and buzzier (their short blades), its low end cut.
HUM_WQ = dict(HUM_W, motor=0.5, buzz=0.6, low_cut=350.0, air=0.0)
HUM_WA = dict(g.HUM, buzz=0.2, beat=0.25, throb=0.5, throb_depth=0.22, growl=24.0, growl_depth=0.02, bright=2300.0,
              tilt=1.0, body=((320.0, 1.2, 1.9), (1050.0, 2.0, 1.4)), buzz_band=(1800.0, 1.4, 2.6), buzz_lp=4200.0,
              spread=0.9, drift=0.0008, air=0.004)
HUM_P = dict(g.HUM, buzz=0.22, beat=0.5, throb=0.25, throb_depth=0.4, growl=9.0, growl_depth=0.1, bright=1700.0,
             tilt=0.75, body=((180.0, 1.2, 2.4), (620.0, 1.6, 2.1), (1300.0, 2.4, 1.4)), buzz_band=(1300.0, 1.0, 2.6),
             buzz_lp=2800.0, sub=0.15, drift=0.002)
HUM_CH = dict(g.HUM, buzz=0.16, throb=0.5, throb_depth=0.2, growl_depth=0.02, bright=2300.0, tilt=0.9, drift=0.001)
# The Choir's parts: the hum's 2nd, 3rd, 4th and 6th harmonics (open fifths and octaves), and its "oh" vowel:
# (centre, Q, level). The Cantor (saber_m5.py) sings the 4th, 5th and 6th (a major chord) on an "ah".
ORGANUM = (2, 3, 4, 6)
OH = ((480.0, 4.5, 1.0), (860.0, 5.5, 0.55), (2500.0, 7.0, 0.14))


# ---------------------------------------------------------------- helpers


def cyc(rate, n, loop):
    """rate in Hz, or, when loop, the nearest whole number of cycles over n samples (so a loop stays seamless)."""
    if not loop:
        return rate
    span = n / g.SR
    return max(1, round(rate * span)) / span


def lfo(rate, t, loop, phase0=0.0):
    """A sine at rate Hz (whole cycles over t when loop)."""
    return np.sin(2 * np.pi * cyc(rate, len(t), loop) * t + phase0)


def bump(t, c, w, loop):
    """A gaussian bump centred at c seconds, w wide; wrapped around the loop when loop."""
    d = t - c
    if loop:
        span = len(t) / g.SR
        d = (d + span / 2) % span - span / 2
    return np.exp(-0.5 * (d / w) ** 2)


def wander(n, rng, cutoff, loop):
    """A slow random wander of unit standard deviation: noise low-passed at cutoff Hz (circularly when loop)."""
    x = g.lp(g.noise(n, rng), cutoff, order=4, loop=loop)
    return x / (np.std(x) + 1e-9)


def steady(f0, n):
    """A held pitch, one value per sample."""
    return np.full(n, float(f0))


def settle_end(x, fade=0.12):
    """x with its last fade seconds eased to silence (a raised cosine), so nothing is cut off before the reverb."""
    m = min(len(x), g.ns(fade))
    x = np.array(x, float)
    if m > 1:
        x[-m:] *= 0.5 + 0.5 * np.cos(np.linspace(0, np.pi, m))
    return x


def wet(x, rng, rt, mix, bright=5000.0):
    """A one-shot's reverb (gen_sounds.reverb), after easing its end (settle_end)."""
    return g.reverb(settle_end(x), rng, rt, mix, bright=bright)


def ignite(rng, f0, tone, dur=1.1, start=0.7, rise=0.035, over=0.05, t_over=0.1, settle=0.09, out=0.07, snap=1.0,
           hiss=1.0, sparks=1.0, hiss_fc=4200.0, hold=0.3, tail=0.85, room=(0.5, 0.1), **voice):
    """gen_sounds.saber_ignite with an emitter's own voice: snap_hiss, and tone(f, rng, **voice) rushing in from
    start x f0 on a quick glide that overshoots by over at t_over and settles, combing as the blade shoots out
    past the ear at out seconds, holding until hold and easing to tail, then fading by dur under the loop. room is
    the reverb (time, mix), or None for a dry result."""
    t = g.tt(dur)
    f = f0 * (1 + (start - 1) * np.exp(-t / rise) + over * g.bell(t, t_over, rise, settle))
    prox, _ = g.pass_curves(t, out, out * 0.6, 0.18)
    x = g.pass_by(tone(f, rng, **voice), prox, comb=(0.3, 3.0), refl=0.6, sheen=0.2, warm=0.15, floor=0.75, power=1.0)
    amp = (1 - np.exp(-t / 0.02)) * (tail + (1 - tail) * np.clip((hold - t) / hold, 0, 1))
    amp *= np.clip((dur - t) / max(dur - hold - 0.05, 0.05), 0, 1) ** 1.5
    x = x * amp + g.snap_hiss(t, rng, 0.0, snap, hiss, sparks, hiss_fc)
    return wet(x, rng, *room) if room else x


def retract(rng, f0, tone, dur=0.8, end=0.5, hold=0.03, tau=0.14, decay=0.18, hiss=1.0, hiss_fc=3200.0, click=0.25,
            t_click=0.42, room=(0.5, 0.1), **voice):
    """gen_sounds.saber_retract with an emitter's own voice: tone(f, rng, **voice) holds for hold seconds, then dives
    toward end x f0 (time constant tau) and fades (decay), combing away from the ear, under a falling hiss, with the
    emitter's small click at t_click. room is the reverb (time, mix), or None for a dry result."""
    t = g.tt(dur)
    f = f0 * (end + (1 - end) * np.exp(-np.maximum(t - hold, 0) / tau))
    prox, _ = g.pass_curves(t, 0.0, 0.05, 0.12)
    x = g.pass_by(tone(f, rng, **voice), prox, comb=(0.3, 3.0), refl=0.7, floor=0.35, power=0.8)
    amp = np.minimum(1, t / 0.004) * np.exp(-np.maximum(t - hold - 0.02, 0) / decay)
    fc = hiss_fc * np.exp(-t / 0.12) + 500
    sh = g.sweep(g.noise(len(t), rng), fc, "bp", 1.3) * g.bell(t, 0.05, 0.015, 0.14) * 0.5
    x = x * amp * 0.6 + hiss * sh + g.crack(t, rng, t_click, 0.003, 2000) * click
    return wet(x, rng, *room) if room else x


# ---------------------------------------------------------------- Vanguard: heavy, industrial, vented


def vanguard_tone(f, rng, loop=False, vents=1.0):
    """The Vanguard's voice on the pitch curve f: a heavy, dark, dense motor (HUM_V) and, under it, air forced
    through the shroud's vent slots (two soft bands of rushing noise, 900 Hz and 2.3 kHz) breathing twice a loop."""
    x = g.film_hum(f, rng, loop=loop, **HUM_V)
    n = len(x)
    t = np.arange(n) / g.SR
    air = g.bp(g.noise(n, rng), 900.0, 2.2, loop=loop) + 0.55 * g.bp(g.noise(n, rng), 2300.0, 2.8, loop=loop)
    breath = 0.6 + 0.4 * lfo(0.5, t, loop, rng.uniform(0, 2 * np.pi))
    x = x + vents * 0.07 * air / g._rms(air) * breath
    return x / g._rms(x)


def g_vanguard_ignite(rng):
    """Heavy and powerful: a deep clunk under a dark snap, the hum hauled up from 55% of its pitch on a slow glide
    while the vents exhale, settling heavy; a larger room."""
    dur = 1.5
    t = g.tt(dur)
    n = len(t)
    x = ignite(rng, FV, vanguard_tone, dur=dur, start=0.55, rise=0.07, over=0.04, t_over=0.17, settle=0.15, out=0.12,
               snap=0.8, hiss=0.7, sparks=0.45, hiss_fc=2800.0, hold=0.36, room=None)
    clunk = g.thump(t, 115, 42, 0.035, 0.16) * 0.55 + g.bp(g.noise(n, rng), 420.0, 3.0) * g.env_ad(t, 0.001, 0.03) * 0.5
    exhale = (g.sweep(g.noise(n, rng), 500 + 1400 * g.bell(t, 0.12, 0.05, 0.2), "bp", 1.6)
              * g.bell(t, 0.13, 0.05, 0.22) * 0.35)
    return wet(x + clunk + exhale, rng, 0.9, 0.16)


def g_vanguard_hum(rng):
    return vanguard_tone(steady(FV, g.ns(LOOP)), rng, loop=True)


def g_vanguard_retract(rng):
    """The heavy hum sinks slowly while the vents exhale, and the emitter shuts with a low clunk."""
    dur = 1.2
    t = g.tt(dur)
    n = len(t)
    x = retract(rng, FV, vanguard_tone, dur=dur, end=0.4, hold=0.05, tau=0.2, decay=0.26, hiss=0.75, hiss_fc=2400.0,
                click=0.0, room=None)
    exhale = g.sweep(g.noise(n, rng), 450 + 1100 * np.exp(-t / 0.18), "bp", 1.5) * g.bell(t, 0.12, 0.04, 0.25) * 0.3
    clunk = (g.thump(t, 95, 40, 0.02, 0.12, t0=0.46) * 0.4
             + g.bp(g.noise(n, rng), 380.0, 3.0) * g.env_ad(t, 0.001, 0.025, 0.46) * 0.3)
    return wet(x + exhale + clunk, rng, 0.8, 0.15)


# ---------------------------------------------------------------- Warden: raw, unstable, the quillons venting


def quillon_vent(n, rng, loop=False, amount=1.0):
    """The Warden's quillons venting: (a sum to add, an envelope to multiply the hum by). Crackle that comes in
    clusters (its rate follows a slow random wander), short spits of frying noise in four bands, bursts of an arc's
    fry when the wander peaks, and the odd brief sag of the blade's power (5 to 20%), three or four a second.
    amount scales how unstable it is (a number, or one per sample for the ignition and retraction)."""
    t = np.arange(n) / g.SR
    span = n / g.SR
    amt = np.broadcast_to(np.asarray(amount, float), (n,))
    w = wander(n, rng, 2.5, loop)
    crk = g.lp(g.hp(g.crackle(n, 170 * np.exp(0.8 * w) * amt, rng), 2200, loop=loop), 9500, loop=loop)
    bands = (1900.0, 2700.0, 3700.0, 5000.0)
    acc = [np.zeros(n) for _ in bands]
    src = g.noise(n, rng)
    for _ in range(int(round(8 * span))):
        c = rng.uniform(0, span)
        acc[rng.integers(len(bands))] += src * bump(t, c, rng.uniform(0.0015, 0.007), loop) * rng.uniform(0.25, 1.0)
    spit = sum(g.bp(a, fc, 1.4, loop=loop) for a, fc in zip(acc, bands)) * amt
    gate = np.clip(np.cos(g.phase(steady(97.0, n), n, loop)), 0, 1) ** 6
    fry = g.bp(g.noise(n, rng) * gate, 2500.0, 0.9, loop=loop) * np.clip(w - 0.7, 0, 1.5) * amt
    sag = np.ones(n)
    for _ in range(int(round(3.5 * span))):
        sag -= rng.uniform(0.05, 0.2) * bump(t, rng.uniform(0, span), rng.uniform(0.01, 0.03), loop)
    sag = 1 - (1 - np.clip(sag, 0.55, 1)) * np.minimum(amt, 1.5)
    return crk * 0.5 + spit * 1.1 + fry * 0.35, sag


def warden_tone(f, rng, loop=False, unstable=1.0):
    """The Warden's voice on the pitch curve f: a raw, buzzy hum (HUM_W) with the quillons' own hum a quarter hertz
    above it (thinner and buzzier, flickering as they vent, so the two beat and phase unevenly), and the venting of
    quillon_vent over both."""
    x = g.film_hum(f, rng, loop=loop, **HUM_W)
    n = len(x)
    q = g.film_hum(f + 0.25, rng, loop=loop, **HUM_WQ)
    flicker = np.clip(0.55 + 0.3 * wander(n, rng, 6.0, loop), 0.1, 1.0)
    vent, sag = quillon_vent(n, rng, loop, unstable)
    x = (x + 0.35 * q * flicker) * sag + vent
    # The same speaker-and-tape compression as film_hum's: it rounds the crackle's peaks, not its density.
    x = g.sat(x / g._rms(x) * 0.42, 1.25)
    return x / g._rms(x)


def g_warden_ignite(rng):
    """It catches roughly: the blade's snap, then each quillon snapping alight (60 and 95 ms later, each with a spit
    of sparks), the raw hum rushing in over a burst of sizzle that settles into the vents' crackle."""
    dur = 1.35
    t = g.tt(dur)
    unstable = 1.0 + 1.2 * np.exp(-t / 0.25)
    x = ignite(rng, FW, warden_tone, dur=dur, start=0.65, rise=0.04, over=0.06, t_over=0.11, settle=0.09, out=0.08,
               snap=1.1, hiss=0.9, sparks=1.2, hiss_fc=4600.0, hold=0.32, room=None, unstable=unstable)
    for t0, a in ((0.06, 0.7), (0.095, 0.6)):
        x = x + g.snap_hiss(t, rng, t0, snap=a, hiss=0.5 * a, sparks=1.2 * a, fc=5200.0, tau=0.03)
        x = x + g.spits(t, rng, t0, 0.12, 5) * 0.3 * a
    return wet(x + g.sizzle(t, rng, 2600, 0.02, 0.18) * 0.6, rng, 0.6, 0.13)


def g_warden_hum(rng):
    return warden_tone(steady(FW, g.ns(LOOP)), rng, loop=True)


def g_warden_retract(rng):
    """The quillons cut out first (two small pops), then the blade dives, sputtering harder as it dies, under a
    fizzing hiss, with the emitter's click."""
    dur = 1.05
    t = g.tt(dur)
    unstable = 1.0 + 0.8 * np.clip(t / 0.3, 0, 1)
    x = retract(rng, FW, warden_tone, dur=dur, end=0.45, hold=0.03, tau=0.12, decay=0.17, hiss=0.9, hiss_fc=3800.0,
                click=0.3, t_click=0.4, room=None, unstable=unstable)
    for t0, a in ((0.0, 0.4), (0.035, 0.35)):
        x = x + g.crack(t, rng, t0, 0.003, 2500) * a + g.spits(t, rng, t0, 0.06, 3) * 0.25 * a
    fizz = g.hp(g.crackle(len(t), 900 * np.exp(-t / 0.2), rng), 2500) * 0.5 * np.exp(-t / 0.3)
    return wet(x + fizz, rng, 0.6, 0.13)


# ---------------------------------------------------------------- Watchman: clean and old-fashioned


def watchman_tone(f, rng, loop=False):
    """The Watchman's voice on the pitch curve f: a clean, mellow hum (HUM_WA: little buzz in a narrow band, no grit,
    a steady pitch) turning on a slow, even flywheel wow, made hollow like an old valve set (a comb half a period
    long favours its odd harmonics by about 6 dB) and rolled off above 5.5 kHz like an old speaker."""
    n = len(f)
    t = np.arange(n) / g.SR
    wow = 1 + 0.002 * lfo(0.5, t, loop, rng.uniform(0, 2 * np.pi))
    fw = f * wow
    x = g.film_hum(fw, rng, loop=loop, **HUM_WA)
    x = x - 0.35 * g.vdelay(x, 0.5 / fw, loop)
    x = g.sat(x / g._rms(x) * 0.45 + 0.1, 1.15)
    x = g.lp(x - np.mean(x), 5500.0, loop=loop)
    return x / g._rms(x)


def g_watchman_ignite(rng):
    """Clean and measured: an old switch's crisp two-part click, a clean, soft snap and a short hiss, the hollow tone
    gliding smoothly up from 70% with a small overshoot; an old stone hall."""
    dur = 1.25
    t = g.tt(dur)
    x = ignite(rng, FWA, watchman_tone, dur=dur, start=0.7, rise=0.04, over=0.05, t_over=0.1, settle=0.1, out=0.075,
               snap=0.75, hiss=0.6, sparks=0.25, hiss_fc=3600.0, hold=0.3, room=None)
    switch = g.click(t, rng, 0.0, 0.9) * 0.35 + g.click(t, rng, 0.012, 1.15) * 0.25
    return wet(x + switch, rng, 0.8, 0.15)


def g_watchman_hum(rng):
    return watchman_tone(steady(FWA, g.ns(LOOP)), rng, loop=True)


def g_watchman_retract(rng):
    """A smooth, clean fall under a soft hiss, and the old switch clicking off."""
    dur = 0.95
    t = g.tt(dur)
    x = retract(rng, FWA, watchman_tone, dur=dur, end=0.5, hold=0.03, tau=0.13, decay=0.2, hiss=0.6, hiss_fc=3000.0,
                click=0.0, room=None)
    switch = g.click(t, rng, 0.4, 0.85) * 0.45 + g.click(t, rng, 0.412, 1.1) * 0.3
    return wet(x + switch, rng, 0.7, 0.14)


# ---------------------------------------------------------------- Penitent: low, menacing, a slow growl


def penitent_tone(f, rng, loop=False, growl=1.0):
    """The Penitent's voice on the pitch curve f: a low, dark hum (HUM_P: a deep slow throb, a 9 Hz flutter, a dark
    buzz beating half a hertz off) with a slow growl: an amplitude ripple at half its pitch doubles its period (a
    darker series between its harmonics), deepening and easing twice a loop, and the blade snarls (more drive) as
    it deepens, over a faint low breath. growl scales the ripple (a number, or one per sample)."""
    x = g.film_hum(f, rng, loop=loop, **HUM_P)
    n = len(x)
    t = np.arange(n) / g.SR
    swell = 0.5 + 0.5 * lfo(0.5, t, loop, rng.uniform(0, 2 * np.pi))
    depth = np.asarray(growl, float) * (0.08 + 0.34 * swell)
    x = x * (1 + depth * np.cos(g.phase(f / 2, n, loop) + rng.uniform(0, 2 * np.pi)))
    x = g.sat(x / g._rms(x) * (0.42 + 0.18 * swell), 1.6)
    breath = g.bp(g.noise(n, rng), 260.0, 1.2, loop=loop)
    x = x / g._rms(x) + 0.06 * breath / g._rms(breath) * (0.4 + 0.6 * swell)
    return x / g._rms(x)


def g_penitent_ignite(rng):
    """Slow and menacing: a dark snap over a low thud, the hum dragged up from half its pitch on a slow, heavy glide,
    its growl swelling as it settles; a large, dark room."""
    dur = 1.75
    t = g.tt(dur)
    growl = 1.0 + 0.8 * g.bell(t, 0.4, 0.15, 0.4)
    x = ignite(rng, FP, penitent_tone, dur=dur, start=0.5, rise=0.08, over=0.035, t_over=0.2, settle=0.18, out=0.14,
               snap=0.75, hiss=0.6, sparks=0.35, hiss_fc=2400.0, hold=0.42, room=None, growl=growl)
    thud = g.thump(t, 80, 34, 0.05, 0.25) * 0.5
    return wet(x + thud, rng, 1.1, 0.18, bright=3500)


def g_penitent_hum(rng):
    return penitent_tone(steady(FP, g.ns(LOOP)), rng, loop=True)


def g_penitent_retract(rng):
    """A long, low dive, the growl deepening as the blade sinks, under a dark hiss; a soft, low thud."""
    dur = 1.45
    t = g.tt(dur)
    growl = 1.0 + 1.2 * np.clip(t / 0.4, 0, 1)
    x = retract(rng, FP, penitent_tone, dur=dur, end=0.35, hold=0.06, tau=0.24, decay=0.3, hiss=0.65, hiss_fc=2000.0,
                click=0.0, room=None, growl=growl)
    thud = g.thump(t, 70, 30, 0.03, 0.18, t0=0.55) * 0.4
    return wet(x + thud, rng, 1.0, 0.18, bright=3500)


# ---------------------------------------------------------------- Choir: harmonic and choral


def sing(f, parts, rng, loop=False, swell=None, vib=0.0035):
    """Wordless voices on the given harmonics of the pitch curve f: three slightly detuned singers per part (soft saws
    with a 5 Hz vibrato), through the OH formants; swell (one envelope per part, or None) shapes each part. Peak 1."""
    n = len(f)
    t = np.arange(n) / g.SR
    x = np.zeros(n)
    for i, h in enumerate(parts):
        part = np.zeros(n)
        for d in (0.0, 0.0035, -0.0042):
            fv = f * h * (1 + d) * (1 + vib * lfo(5.0, t, loop, rng.uniform(0, 2 * np.pi)))
            part += g.saw(g.phase(fv, n, loop) + rng.uniform(0, 2 * np.pi), float(np.max(fv)), nh=24, roll=1.6)
        x += part * (1.0 if swell is None else swell[i])
    out = sum(g.bp(x, fc, q, loop=loop) * a for fc, q, a in OH)
    return out / (np.max(np.abs(out)) + 1e-9)


def choir_tone(f, rng, loop=False, voices=1.0):
    """The Choir's voice on the pitch curve f: a smooth, warm hum (HUM_CH) under the choir singing its own harmonics
    in open fifths and octaves (ORGANUM, "oh"), each part breathing once a loop a quarter of a loop after the one
    below it, so the chord turns slowly. voices scales the choir (a number, or one per sample)."""
    x = g.film_hum(f, rng, loop=loop, **HUM_CH)
    n = len(x)
    t = np.arange(n) / g.SR
    if not np.any(voices):
        return x * 0.75
    swell = [0.62 + 0.38 * lfo(0.25, t, loop, -np.pi / 2 + i * np.pi / 2) for i in range(len(ORGANUM))]
    return x * 0.75 + sing(f, ORGANUM, rng, loop, swell) * 0.6 * np.asarray(voices, float)


def g_choir_ignite(rng):
    """A soft snap with a glint on the hum's 16th harmonic, the warm hum rising, and the choir drawing breath and
    swelling onto its open fifths as the blade settles; a long, bright reverb."""
    dur = 2.4
    t = g.tt(dur)
    n = len(t)
    blade = ignite(rng, FCH, choir_tone, dur=1.5, start=0.66, rise=0.05, over=0.04, t_over=0.14, settle=0.13, out=0.1,
                   snap=0.65, hiss=0.65, sparks=0.5, hiss_fc=3400.0, hold=0.38, room=None, voices=0.0)
    swell = [g.bell(t, 0.55 + 0.06 * i, 0.22, 0.6) for i in range(len(ORGANUM))]
    voices = sing(steady(FCH, n), ORGANUM, rng, swell=swell) * 0.7
    breath = g.bp(g.noise(n, rng), 1600.0, 1.2) * g.bell(t, 0.3, 0.1, 0.22) * 0.12
    glint = g.bell_partials(n, FCH * 16, [1, 2.0, 3.0], [0.4, 0.2, 0.1], [1, 0.3, 0.12], rng, t0=0.02) * 0.08
    return wet(g.place(voices + breath + glint, blade, 0.0), rng, 1.5, 0.25, bright=6500)


def g_choir_hum(rng):
    x = choir_tone(steady(FCH, g.ns(LOOP)), rng, loop=True)
    return g.reverb(x, rng, 1.2, 0.18, loop=True, bright=6000)


def g_choir_retract(rng):
    """The blade sinks away under a soft hiss; the choir sighs down a little, part by part, and fades after it."""
    dur = 2.0
    t = g.tt(dur)
    n = len(t)
    blade = retract(rng, FCH, choir_tone, dur=1.0, end=0.45, hold=0.05, tau=0.2, decay=0.26, hiss=0.65, hiss_fc=2600.0,
                    click=0.0, room=None, voices=0.0)
    sigh = 1 - 0.05 * np.clip((t - 0.08) / 0.8, 0, 1)
    swell = [g.env_ad(t, 0.03, 0.5 - 0.06 * i) for i in range(len(ORGANUM))]
    voices = sing(FCH * sigh, ORGANUM, rng, swell=swell) * 0.5
    return wet(g.place(voices, blade, 0.0), rng, 1.4, 0.26, bright=6000)


# ---------------------------------------------------------------- the sets' effects (7.2)


def g_set_pulse(rng):
    """The Long Hymn's pulse: an indrawn swell, then one resonant chord on the Choir's open fifths (its hum's 2nd, 3rd,
    4th, 6th and 8th harmonics struck like a bell, each with a twin that beats, and sung for an instant), a push of
    air outward and a low thump; a shimmer fades in a long reverb."""
    dur = 2.4
    t = g.tt(dur)
    n = len(t)
    t0 = 0.09
    u = np.clip(t / t0, 0, 1)
    draw = (g.sweep(g.noise(n, rng), 300 + 1500 * u, "lp", nb=8) * u ** 2
            * np.clip((t0 + 0.012 - t) / 0.012, 0, 1) * 0.45)
    ring = np.zeros(n)
    tr = np.maximum(t - t0, 0)
    env_on = np.clip((t - t0) / 0.05, 0, 1) ** 1.5
    for h, decay, a in ((2, 0.9, 1.0), (3, 0.7, 0.8), (4, 0.55, 0.6), (6, 0.4, 0.4), (8, 0.3, 0.25)):
        for beat, aa in ((0.0, 1.0), (0.7, 0.6)):
            ring += a * aa * np.sin(2 * np.pi * (FCH * h + beat) * tr + rng.uniform(0, 2 * np.pi)) * np.exp(-tr / decay)
    ring *= env_on
    sung = sing(steady(FCH, n), ORGANUM, rng) * env_on * np.exp(-tr / 0.32) * 0.7
    push = g.sweep(g.noise(n, rng), 300 + 1600 * np.exp(-tr / 0.15), "bp", 1.2) * g.env_ad(t, 0.01, 0.2, t0) * 0.5
    thump = g.thump(t, 90, 40, 0.05, 0.2, t0=t0) * 0.35
    shimmer = g.bp(g.noise(n, rng), 6000.0, 2.0) * g.env_ad(t, 0.04, 0.35, t0) * 0.08
    return wet(draw + ring * 0.3 + sung + push + thump + shimmer, rng, 1.6, 0.3, bright=6500)


def g_set_mark(rng):
    """Curator's Eye marks a foe: a short, bright ping. A tiny lens-focus chirp up into a clean, glassy ping at
    2.7 kHz, its fifth a hair later, a soft tick under it; a short, bright tail, never a bell's long ring. A parry
    sets the mark, so the ping sits between the parry bell's partials (1568 Hz times 1, 1.51 and 2.42) and clear of
    its 3.1 kHz shimmer."""
    dur = 0.7
    t = g.tt(dur)
    n = len(t)
    chirp = np.sin(g.phase(1900 + 800 * np.clip(t / 0.018, 0, 1), n)) * g.env_ad(t, 0.002, 0.012) * 0.25
    ping = g.bell_partials(n, 2700.0, [1, 2.0, 3.0], [0.15, 0.07, 0.04], [1, 0.25, 0.1], rng, t0=0.016, attack=0.0015)
    fifth = g.bell_partials(n, 4050.0, [1, 2.0], [0.09, 0.045], [0.4, 0.08], rng, t0=0.05, attack=0.0015)
    tick = g.click(t, rng, 0.012, 1.3) * 0.3
    return wet(chirp + ping + fifth + tick, rng, 0.6, 0.18, bright=9000)


FILES = {
    "saber.vanguard.ignite": {"saber/vanguard_ignite": g_vanguard_ignite},
    "saber.vanguard.hum": {"saber/vanguard_hum": g_vanguard_hum},
    "saber.vanguard.retract": {"saber/vanguard_retract": g_vanguard_retract},
    "saber.warden.ignite": {"saber/warden_ignite": g_warden_ignite},
    "saber.warden.hum": {"saber/warden_hum": g_warden_hum},
    "saber.warden.retract": {"saber/warden_retract": g_warden_retract},
    "saber.watchman.ignite": {"saber/watchman_ignite": g_watchman_ignite},
    "saber.watchman.hum": {"saber/watchman_hum": g_watchman_hum},
    "saber.watchman.retract": {"saber/watchman_retract": g_watchman_retract},
    "saber.penitent.ignite": {"saber/penitent_ignite": g_penitent_ignite},
    "saber.penitent.hum": {"saber/penitent_hum": g_penitent_hum},
    "saber.penitent.retract": {"saber/penitent_retract": g_penitent_retract},
    "saber.choir.ignite": {"saber/choir_ignite": g_choir_ignite},
    "saber.choir.hum": {"saber/choir_hum": g_choir_hum},
    "saber.choir.retract": {"saber/choir_retract": g_choir_retract},
    "saber.set_pulse": {"saber/set_pulse": g_set_pulse},
    "saber.set_mark": {"saber/set_mark": g_set_mark},
}
