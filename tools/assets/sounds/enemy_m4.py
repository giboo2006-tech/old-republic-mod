"""PLACEHOLDER sound recipes for the Milestone 4 foes: the Mandalorian hunter, the Mk IV security
droid, the hollowhorn, the ash hound, Torvin Kaast's fight and its three music loops.

Owned by W6 (Force and foes). Synthesised from scratch with the helpers in gen_sounds.py (noise,
filters, sweeps, thumps, inharmonic partials, convolution reverb); nothing is sampled, and no
voice or score from any film or game is imitated. See events_m4.json for the event list and
docs/MILESTONE_4.md sections 10 and 13.3 to 13.4 for each sound's intended use. File names are the
placeholder names (<group>/<rest>), so sounds.json does not change when these replace them.

The music is procedural placeholder music, labelled as such: three seamless loops from a small
sequencer (frame drums, low plucks, metal taps, saw stabs, a reedy lead) whose lengths are whole
bars, so they loop on the beat.

    python3 tools/assets/gen_sounds.py --m4 --feature enemy_m4
"""
import gen_sounds as g

np = g.np


# ---------------------------------------------------------------- building blocks

def helmet(x, bright=2600):
    """A voice heard through a sealed helmet: band-limited, with a short metallic box resonance."""
    y = g.bp(x, 900, 0.9) + 0.5 * g.bp(x, 1800, 2.5)
    y = g.comb(y, 0.0021, 0.35)
    return g.lp(y, bright)


def jet_roar(t, rng, env, fc=900):
    """A jet pack's thrust: filtered noise with a low rumble and a little crackle."""
    n = len(t)
    roar = g.bp(g.noise(n, rng), fc, 0.6) + 0.6 * g.lp(g.noise(n, rng), 220)
    pop = g.bp(g.crackle(n, 120, rng), 1600, 1.0) * 1.5
    return (roar + pop) * env


def servo(t, f0, f1, t0, dur, rng):
    """A droid servo: a buzzy whine gliding from f0 to f1."""
    n = len(t)
    tr = np.clip((t - t0) / dur, 0, 1)
    f = f0 + (f1 - f0) * tr
    x = g.pulse(g.phase(f, n), max(f0, f1), w=0.2, nh=12) * 0.3 + g.sweep(g.noise(n, rng), f * 3, "bp", 6, nb=6) * 0.2
    return x * np.where((t >= t0) & (t <= t0 + dur), 1.0, 0.0) * (0.5 + 0.5 * np.sin(np.pi * tr))


def hoof(t, rng, t0, weight=1.0):
    """One hoof on packed earth: a dull thud with a little grit."""
    thud = g.thump(t, 120, 55, 0.01, 0.06 * weight, t0=t0) * weight
    grit = g.bp(g.noise(len(t), rng), 1400, 0.8) * g.env_ad(t, 0.002, 0.03, t0) * 0.35
    return thud + grit


def blast(t, rng, t0=0.0, size=1.0):
    """An explosion: a deep thump, a broadband crack and falling debris."""
    n = len(t)
    boom = g.thump(t, 90, 32, 0.05, 0.5 * size, t0=t0) * 1.5
    body = g.lp(g.noise(n, rng), 900) * g.env_ad(t, 0.004, 0.25 * size, t0) * 1.4
    snap = g.crack(t, rng, t0, 0.02, 1200) * 0.8
    debris = g.bp(g.crackle(n, 400 * np.exp(-np.maximum(t - t0, 0) / 0.4), rng), 2200, 0.9) * (t > t0 + 0.05) * 1.2
    return g.sat(boom + body + snap + debris, 1.4)


def beep(t, f, t0, dur):
    """A short square-ish beep."""
    n = len(t)
    return g.pulse(g.phase(np.full(n, f), n), f, w=0.5, nh=8) * ((t >= t0) & (t < t0 + dur)) * 0.4


def growl_voice(t, rng, t0, dur, f0, rough=40.0, f1=420, f2=900):
    """A beast's growl: a low voiced buzz with fast amplitude roughness, shaped by two formants."""
    n = len(t)
    voiced = g.saw(g.phase(np.full(n, f0) * (1 + 0.04 * np.sin(2 * np.pi * 3 * t)), n), f0, nh=40, roll=1.1)
    rough_am = 0.6 + 0.4 * np.sin(2 * np.pi * rough * t + rng.uniform(0, 6))
    src = voiced * rough_am + g.noise(n, rng) * 0.4
    v = g.bp(src, f1, 3) + 0.7 * g.bp(src, f2, 4)
    return v * g.env_ad(t, 0.05, dur * 0.5, t0) * (t < t0 + dur * 1.5)


def midi(n):
    return 440.0 * 2 ** ((n - 69) / 12)


# ---------------------------------------------------------------- the hunter

def g_jet_tell(rng):
    """A rising turbine whine and a few sputters of pilot flame at the pack."""
    t = g.tt(1.1)
    n = len(t)
    f = 500 + 1900 * np.clip(t / 1.0, 0, 1) ** 1.5
    whine = np.sin(g.phase(f, n)) * 0.35 + np.sin(g.phase(f * 2.01, n)) * 0.12
    whine *= np.clip(t / 0.15, 0, 1) * g.env_ad(t, 0.9, 0.08)
    sputter = g.bp(g.crackle(n, 30 + 90 * t, rng), 1200, 1.2) * 1.6
    return g.reverb(whine + sputter * np.clip(t / 0.5, 0, 1), rng, 0.5, 0.12)


def g_flamer_tell(rng):
    """The wrist comes up with a gauntlet click; a pilot flame catches and hisses."""
    t = g.tt(0.8)
    clk = g.click(t, rng, 0.01, 0.9) * 0.7
    catch = g.bp(g.noise(len(t), rng), 2400, 1.0) * g.env_ad(t, 0.004, 0.05, 0.12) * 0.8
    hiss = g.bp(g.noise(len(t), rng), 4200, 1.5) * np.clip((t - 0.13) / 0.08, 0, 1) * g.env_ad(t, 0.4, 0.3, 0.13) * 0.5
    return g.reverb(clk + catch + hiss, rng, 0.35, 0.1)


def g_rocket_lock(rng):
    """A targeting tone that beeps faster and settles on a steady lock."""
    t = g.tt(1.2)
    x = np.zeros(len(t))
    at, gap = 0.0, 0.18
    while at < 0.85:
        x += beep(t, 1760, at, 0.05)
        at += gap
        gap *= 0.8
    x += beep(t, 1760, 0.88, 0.28)
    return g.reverb(g.bp(x, 1800, 1.2), rng, 0.3, 0.08)


def g_net_tell(rng):
    """Two dry gauntlet clicks and a spring winding tight."""
    t = g.tt(0.6)
    x = g.click(t, rng, 0.01, 1.1) + g.click(t, rng, 0.12, 0.95) * 0.9
    for k in range(6):
        x += g.click(t, rng, 0.22 + k * 0.03, 1.4) * 0.3
    return g.reverb(x, rng, 0.3, 0.1)


def g_jet(rng):
    """The hop: a hard burst of thrust that trails off as he lands."""
    t = g.tt(1.4)
    env = g.env_ad(t, 0.03, 0.45)
    lift = g.thump(t, 70, 40, 0.03, 0.15) * 0.8
    return g.reverb(jet_roar(t, rng, env) * 1.3 + lift, rng, 0.7, 0.18)


def g_flamer(rng):
    """Seamless: a roaring gout of flame with a fluttering edge."""
    L = 2.0
    t = g.tt(L)
    n = len(t)
    roar = g.bp(g.noise(n, rng), 700, 0.6, loop=True) * 1.2
    edge = g.bp(g.noise(n, rng), 2600, 1.2, loop=True) * (0.6 + 0.3 * np.sin(2 * np.pi * 7 * t / L) + 0.1 * np.sin(2 * np.pi * 23 * t / L))
    low = g.lp(g.noise(n, rng), 140, loop=True) * 1.8
    pops = g.bp(g.crackle(n, 40, rng, loop=True), 1800, 1.0, loop=True) * 1.5
    return roar + edge * 0.6 + low + pops


def g_rocket(rng):
    """A small rocket leaving the wrist: a pop, then a sustained, receding hiss."""
    t = g.tt(1.3)
    pop = g.thump(t, 160, 70, 0.01, 0.05) * 0.9 + g.crack(t, rng, 0.0, 0.01) * 0.6
    burn = g.sweep(g.noise(len(t), rng), 2600 * np.exp(-t / 0.8) + 600, "bp", 1.0, nb=10) * g.env_ad(t, 0.02, 0.5) * 1.1
    return g.reverb(pop + burn, rng, 0.6, 0.15)


def g_explode(rng):
    """The rocket's blast."""
    t = g.tt(2.0)
    return g.reverb(blast(t, rng, 0.0, 1.0), rng, 1.2, 0.25, bright=3000)


def g_net(rng):
    """A weighted net whips open and drops over its catch: a rope swish and a slap."""
    t = g.tt(0.9)
    swish = g.whoosh(t, rng, 600 + 1800 * g.bell(t, 0.12, 0.05, 0.1), 1.0, g.bell(t, 0.12, 0.05, 0.1)) * 1.2
    slap = g.bp(g.noise(len(t), rng), 900, 0.8) * g.env_ad(t, 0.003, 0.05, 0.26) * 1.3
    weights = sum(g.thump(t, 140, 90, 0.01, 0.03, t0=0.28 + k * 0.04) for k in range(4)) * 0.35
    return g.reverb(swish + slap + weights, rng, 0.4, 0.12)


def make_hunter_hurt(f_hi, f1):
    def gen(rng):
        """A clipped grunt through the helmet, armour rattling."""
        t = g.tt(0.55)
        v = g.grunt(t, rng, 0.0, 0.16, f_hi, f_hi * 0.75, f1, f1 * 2.1)
        return g.reverb(helmet(v) * 2.0 + g.gear_rattle(t, rng, 0.0, 0.12, 4) * 0.25, rng, 0.3, 0.1)
    return gen


def g_hunter_death(rng):
    """A long groan through the helmet, armour hitting the ground."""
    t = g.tt(1.6)
    v = g.grunt(t, rng, 0.0, 0.6, 150, 90, 480, 980)
    fall = g.thump(t, 100, 45, 0.02, 0.15, t0=0.75) * 0.8 + g.gear_rattle(t, rng, 0.75, 0.95, 7) * 0.35
    return g.reverb(helmet(v) * 2.2 + fall, rng, 0.6, 0.15)


def g_hunter_bark(rng):
    """A wordless call through the helmet: two short syllables, flat and commanding."""
    t = g.tt(0.8)
    v = g.grunt(t, rng, 0.0, 0.12, 170, 160, 600, 1300) + g.grunt(t, rng, 0.2, 0.2, 155, 120, 540, 1150)
    return g.reverb(helmet(v) * 2.0, rng, 0.5, 0.12)


# ---------------------------------------------------------------- the droid

def g_spin_up(rng):
    """The arm barrels spin up: a rising motor whir with a rattle of bearings."""
    t = g.tt(1.1)
    n = len(t)
    f = 60 + 340 * np.clip(t / 1.0, 0, 1) ** 1.3
    motor = g.pulse(g.phase(f, n), 400, w=0.3, nh=16) * 0.3 + np.sin(g.phase(f * 4, n)) * 0.2
    bearings = g.bp(g.crackle(n, f * 0.8, rng), 3000, 1.5) * 0.8
    return g.reverb((motor + bearings) * g.env_ad(t, 1.0, 0.05), rng, 0.4, 0.1)


def g_droid_fire(rng):
    """A short, hard repeater shot with a metallic snap."""
    t = g.tt(0.45)
    zap = g.sweep(g.noise(len(t), rng), 3800 * np.exp(-t / 0.05) + 500, "bp", 2.0, nb=8) * g.env_ad(t, 0.002, 0.06) * 1.4
    snap = g.metal_ring(t, rng, 1300, 4, 0.04) * 0.5
    body = g.thump(t, 220, 90, 0.01, 0.05) * 0.6
    return g.reverb(zap + snap + body, rng, 0.4, 0.12)


def g_droid_shield(rng):
    """The plate shield locks forward with a clank and a crackle of sparks."""
    t = g.tt(0.9)
    clank = g.metal_ring(t, rng, 420, 6, 0.25) * 0.8 + g.thump(t, 140, 70, 0.01, 0.08) * 0.7
    sparks = g.bp(g.crackle(len(t), 300 * np.exp(-t / 0.3), rng), 3600, 1.0) * 1.4
    return g.reverb(clank + sparks, rng, 0.5, 0.15)


def g_droid_death(rng):
    """Sparks, a falling hum as the power drains, and a small burst."""
    t = g.tt(2.0)
    n = len(t)
    f = 220 * np.exp(-t / 0.7) + 40
    hum = (np.sin(g.phase(f, n)) + 0.4 * g.pulse(g.phase(f, n), 220, w=0.2, nh=10)) * g.env_ad(t, 0.01, 0.7) * 0.5
    sparks = g.bp(g.crackle(n, 180, rng), 3200, 1.0) * np.clip(1.1 - t / 1.1, 0, 1) * 1.2
    pop = blast(t, rng, 1.05, 0.4) * 0.6
    return g.reverb(hum + sparks + pop, rng, 0.8, 0.2)


def g_droid_hurt(rng):
    """A clang on plate and a brief crackle."""
    t = g.tt(0.5)
    x = g.metal_ring(t, rng, 760, 5, 0.12) * 0.8 + g.bp(g.crackle(len(t), 400 * np.exp(-t / 0.08), rng), 3000, 1.0) * 1.2
    return g.reverb(x, rng, 0.35, 0.1)


def g_droid_step(rng):
    """A heavy metal foot set down, a servo settling."""
    t = g.tt(0.45)
    x = g.thump(t, 110, 50, 0.01, 0.06) * 0.9 + g.metal_ring(t, rng, 260, 4, 0.05) * 0.35
    x += servo(t, 300, 220, 0.03, 0.12, rng) * 0.6
    return g.reverb(x, rng, 0.3, 0.08)


def g_droid_alert(rng):
    """A flat two-tone electronic alarm."""
    t = g.tt(0.9)
    x = beep(t, 880, 0.0, 0.18) + beep(t, 660, 0.22, 0.18) + beep(t, 880, 0.44, 0.18)
    return g.reverb(g.bp(x, 900, 0.9), rng, 0.4, 0.12)


# ---------------------------------------------------------------- the hollowhorn

def g_hollowhorn_snort(rng):
    """The charge tell: a heavy snort and a hoof scraping dirt."""
    t = g.tt(1.0)
    snort = g.bp(g.noise(len(t), rng), 500, 1.2) * g.env_ad(t, 0.02, 0.08) * 1.5
    scrape = g.bp(g.noise(len(t), rng), 1600, 0.8) * (g.bell(t, 0.4, 0.05) + g.bell(t, 0.65, 0.05)) * 0.9
    return g.reverb(snort + scrape + hoof(t, rng, 0.4, 0.6) + hoof(t, rng, 0.65, 0.6), rng, 0.5, 0.12)


def g_hollowhorn_charge(rng):
    """A galloping rush of hooves and a bellow."""
    t = g.tt(1.6)
    x = np.zeros(len(t))
    for k in range(10):
        x += hoof(t, rng, 0.05 + k * 0.14, 1.0) + hoof(t, rng, 0.11 + k * 0.14, 0.7)
    bellow = growl_voice(t, rng, 0.0, 0.5, 95, 18, 380, 760) * 0.7
    return g.reverb(x + bellow, rng, 0.6, 0.15)


def g_hollowhorn_impact(rng):
    """Horns into a wall: a deep crack and a dazed grunt."""
    t = g.tt(1.0)
    hit = g.thump(t, 140, 40, 0.02, 0.2) * 1.4 + g.crack(t, rng, 0.0, 0.03, 600) * 1.0
    grit = g.bp(g.crackle(len(t), 250 * np.exp(-t / 0.2), rng), 1800, 0.9)
    groan = growl_voice(t, rng, 0.25, 0.35, 85, 10, 360, 700) * 0.5
    return g.reverb(g.sat(hit + grit + groan, 1.3), rng, 0.7, 0.18)


def g_hollowhorn_idle(rng):
    """A low, contented lowing."""
    t = g.tt(1.4)
    return g.reverb(growl_voice(t, rng, 0.0, 0.9, 110, 6, 450, 880) * 0.9, rng, 0.8, 0.2)


def g_hollowhorn_hurt(rng):
    """A short, pained bellow."""
    t = g.tt(0.6)
    return g.reverb(growl_voice(t, rng, 0.0, 0.25, 150, 25, 520, 1050), rng, 0.4, 0.12)


def g_hollowhorn_death(rng):
    """A falling bellow and the body settling into the grass."""
    t = g.tt(1.8)
    n = len(t)
    f = 140 * np.exp(-t / 0.9) + 60
    voiced = g.saw(g.phase(f, n), 140, nh=30, roll=1.1)
    v = (g.bp(voiced, 440, 3) + 0.6 * g.bp(voiced, 900, 4)) * g.env_ad(t, 0.05, 0.5)
    fall = g.thump(t, 90, 40, 0.03, 0.2, t0=0.9) + g.bp(g.noise(n, rng), 800, 0.7) * g.env_ad(t, 0.01, 0.12, 0.9) * 0.4
    return g.reverb(v + fall, rng, 0.8, 0.2)


# ---------------------------------------------------------------- the ash hound

def g_hound_growl(rng):
    """The lunge tell: a low, rattling growl through bared teeth."""
    t = g.tt(0.8)
    return g.reverb(growl_voice(t, rng, 0.0, 0.55, 75, 55, 600, 1400) * 1.1, rng, 0.4, 0.1)


def g_hound_howl(rng):
    """A thin, rising and falling howl that carries."""
    t = g.tt(2.4)
    n = len(t)
    f = 330 + 160 * g.bell(t, 0.7, 0.3, 0.8) + 8 * np.sin(2 * np.pi * 5 * t)
    voiced = g.saw(g.phase(f, n), 500, nh=20, roll=1.6) + g.noise(n, rng) * 0.08
    v = g.bp(voiced, 900, 2.0) + 0.5 * g.bp(voiced, 1700, 4)
    return g.reverb(v * g.env_ad(t, 0.15, 0.9), rng, 1.8, 0.35, bright=4000)


def g_hound_lunge(rng):
    """A snarl, claws on stone and a snap of jaws."""
    t = g.tt(0.7)
    snarl = growl_voice(t, rng, 0.0, 0.18, 120, 70, 700, 1600) * 0.9
    claws = g.bp(g.crackle(len(t), 150 * g.bell(t, 0.05, 0.03), rng), 4000, 1.5) * 1.5
    snap = g.click(t, rng, 0.28, 0.7) * 0.9 + g.thump(t, 300, 150, 0.005, 0.02, t0=0.28) * 0.4
    return g.reverb(snarl + claws + snap, rng, 0.4, 0.12)


def g_hound_idle(rng):
    """Quick, dry panting."""
    t = g.tt(1.2)
    x = np.zeros(len(t))
    for k in range(6):
        x += g.bp(g.noise(len(t), rng), 1400 if k % 2 == 0 else 1100, 1.2) * g.bell(t, 0.08 + k * 0.17, 0.02, 0.05)
    return g.reverb(x * 0.8, rng, 0.3, 0.1)


def g_hound_hurt(rng):
    """A sharp yelp."""
    t = g.tt(0.4)
    n = len(t)
    f = 900 * np.exp(-t / 0.08) + 450
    voiced = g.saw(g.phase(f, n), 900, nh=14, roll=1.4)
    v = (g.bp(voiced, 1200, 2) + 0.4 * g.bp(voiced, 2400, 3)) * g.env_ad(t, 0.005, 0.07)
    return g.reverb(v, rng, 0.3, 0.1)


def g_hound_death(rng):
    """A whine that falls away, and a light body dropping."""
    t = g.tt(1.2)
    n = len(t)
    f = 700 * np.exp(-t / 0.35) + 200
    voiced = g.saw(g.phase(f, n), 700, nh=16, roll=1.4)
    v = (g.bp(voiced, 1000, 2) + 0.4 * g.bp(voiced, 2000, 3)) * g.env_ad(t, 0.01, 0.3)
    drop = g.thump(t, 120, 60, 0.01, 0.08, t0=0.55) * 0.7
    return g.reverb(v + drop, rng, 0.5, 0.15)


# ---------------------------------------------------------------- Torvin Kaast

def g_kaast_hurt(rng):
    """A curt, controlled grunt through the helmet; heavier plate."""
    t = g.tt(0.6)
    v = g.grunt(t, rng, 0.0, 0.18, 120, 95, 470, 1000)
    return g.reverb(helmet(v, 2300) * 2.2 + g.gear_rattle(t, rng, 0.0, 0.15, 5) * 0.3, rng, 0.35, 0.1)


def g_kaast_hop(rng):
    """His heavier jet: a deep blast of thrust that carries him across the ring."""
    t = g.tt(1.8)
    env = g.env_ad(t, 0.05, 0.7)
    return g.reverb(jet_roar(t, rng, env, 650) * 1.4 + g.thump(t, 60, 35, 0.04, 0.25) * 0.9, rng, 0.9, 0.2)


def g_kaast_rifle(rng):
    """A heavy slug rifle crack with a long tail across the camp."""
    t = g.tt(1.2)
    crk = g.crack(t, rng, 0.0, 0.012, 700) * 1.6
    body = g.thump(t, 200, 70, 0.01, 0.07) * 1.0
    zing = g.sweep(g.noise(len(t), rng), 3000 * np.exp(-t / 0.12) + 900, "bp", 3.0, nb=8) * g.env_ad(t, 0.002, 0.1) * 0.6
    return g.reverb(g.sat(crk + body + zing, 1.5), rng, 1.3, 0.3, bright=3500)


def g_kaast_phase(rng):
    """A change of tactics: a gauntlet comm click, a short burst of static and the axe's strap pulled tight."""
    t = g.tt(1.2)
    clk = g.click(t, rng, 0.0, 0.8)
    static = g.bp(g.noise(len(t), rng), 2200, 0.7) * g.env_ad(t, 0.01, 0.12, 0.05) * 0.6
    strap = g.bp(g.noise(len(t), rng), 900, 1.5) * g.bell(t, 0.55, 0.08, 0.12) * 0.7
    low = g.thump(t, 70, 40, 0.05, 0.4, t0=0.5) * 0.7
    return g.reverb(clk + static + strap + low, rng, 1.0, 0.25)


def g_kaast_mine_arm(rng):
    """A mine pressed into the ground: a twist, a latch and a rising arming tone."""
    t = g.tt(1.0)
    twist = g.bp(g.noise(len(t), rng), 1500, 1.0) * g.bell(t, 0.08, 0.04) * 0.7
    latch = g.click(t, rng, 0.18, 1.0)
    tone = np.sin(g.phase(900 + 900 * np.clip((t - 0.25) / 0.6, 0, 1), len(t))) * g.env_ad(t, 0.05, 0.4, 0.25) * 0.3
    return g.reverb(twist + latch + tone, rng, 0.4, 0.1)


def g_kaast_mine_beep(rng):
    """One arming blink: a short, high beep."""
    t = g.tt(0.25)
    return g.reverb(beep(t, 2093, 0.0, 0.06), rng, 0.2, 0.08)


def g_kaast_mine_burst(rng):
    """A ground mine going off: sharper and dirtier than the rocket."""
    t = g.tt(1.8)
    dirt = g.bp(g.crackle(len(t), 600 * np.exp(-t / 0.5), rng), 900, 0.7) * 1.4
    return g.reverb(blast(t, rng, 0.0, 0.8) + dirt, rng, 1.0, 0.22, bright=2800)


def g_kaast_net_throw(rng):
    """A gauntlet launcher's thump and the net spinning out."""
    t = g.tt(0.9)
    thump = g.thump(t, 180, 80, 0.01, 0.05) * 0.9 + g.click(t, rng, 0.0, 0.8) * 0.6
    spin = g.whoosh(t, rng, 500 + 900 * g.bell(t, 0.3, 0.1, 0.3), 1.2, g.bell(t, 0.3, 0.1, 0.3)) * (0.7 + 0.3 * np.sin(2 * np.pi * 14 * t))
    return g.reverb(thump + spin, rng, 0.5, 0.12)


def g_kaast_axe_draw(rng):
    """The vibro-axe comes off his back: a scrape of steel and a rising, rasping hum."""
    t = g.tt(1.3)
    n = len(t)
    scrape = g.sweep(g.noise(n, rng), 1800 + 2600 * np.clip(t / 0.35, 0, 1), "bp", 4.0, nb=10) * g.env_ad(t, 0.02, 0.15) * 1.0
    f = 95 + 25 * np.clip((t - 0.3) / 0.5, 0, 1)
    hum = (g.saw(g.phase(f, n), 120, nh=40) + g.saw(g.phase(f * 1.013, n), 120, nh=40)) * 0.25
    hum = g.bp(hum, 600, 1.2) * np.clip((t - 0.3) / 0.4, 0, 1) * g.env_ad(t, 0.7, 0.3, 0.3)
    return g.reverb(scrape + hum, rng, 0.6, 0.15)


def g_kaast_combo(rng):
    """One axe swing: a heavy rasping whoosh."""
    t = g.tt(0.7)
    n = len(t)
    sw = g.whoosh(t, rng, 300 + 900 * g.bell(t, 0.16, 0.06, 0.1), 1.6, g.bell(t, 0.16, 0.06, 0.1)) * 1.4
    rasp = g.bp(g.saw(g.phase(np.full(n, 105.0), n), 105, nh=40), 700, 1.0) * g.bell(t, 0.16, 0.06, 0.1) * 0.4
    return g.reverb(sw + rasp, rng, 0.45, 0.12)


def g_kaast_charge(rng):
    """A shoulder charge: heavy boots, a grunt and armour clattering."""
    t = g.tt(1.0)
    steps = sum(g.thump(t, 110, 50, 0.01, 0.05, t0=0.05 + k * 0.17) for k in range(5)) * 0.8
    v = helmet(g.grunt(t, rng, 0.0, 0.25, 130, 110, 500, 1050)) * 1.6
    return g.reverb(steps + v + g.gear_rattle(t, rng, 0.0, 0.8, 10) * 0.25, rng, 0.5, 0.12)


def g_kaast_slam_tell(rng):
    """He rises on his jets: a building roar and a whine climbing toward the dive."""
    t = g.tt(2.2)
    n = len(t)
    env = np.clip(t / 1.8, 0, 1) ** 1.2 * g.env_ad(t, 1.9, 0.1)
    whine = np.sin(g.phase(400 + 1600 * np.clip(t / 2.0, 0, 1) ** 2, n)) * 0.25 * env
    return g.reverb(jet_roar(t, rng, env, 700) * 1.2 + whine, rng, 1.0, 0.22)


def g_kaast_slam(rng):
    """The dive lands: a crushing impact and a spray of earth."""
    t = g.tt(2.0)
    impact = g.thump(t, 120, 30, 0.04, 0.5) * 1.8 + g.crack(t, rng, 0.0, 0.03, 500) * 1.2
    earth = g.bp(g.crackle(len(t), 700 * np.exp(-t / 0.5), rng), 1100, 0.7) * 1.5
    return g.reverb(g.sat(impact + earth, 1.5), rng, 1.3, 0.28, bright=2800)


def g_kaast_helmet_crack(rng):
    """The visor cracks: a sharp crack of plate and a tinkle of falling fragments."""
    t = g.tt(1.0)
    crk = g.crack(t, rng, 0.0, 0.015, 1500) * 1.4 + g.metal_ring(t, rng, 1700, 6, 0.15) * 0.5
    bits = g.gear_rattle(t, rng, 0.12, 0.6, 8) * 0.4
    return g.reverb(crk + bits, rng, 0.6, 0.18)


def g_kaast_yield(rng):
    """The axe dropped into the dirt, a knee on the ground, a long tired breath."""
    t = g.tt(2.2)
    axe = g.metal_ring(t, rng, 340, 6, 0.3) * 0.6 + g.thump(t, 100, 50, 0.01, 0.1) * 0.8
    knee = g.thump(t, 90, 45, 0.02, 0.12, t0=0.6) * 0.8 + g.gear_rattle(t, rng, 0.6, 0.8, 6) * 0.3
    breath = helmet(g.bp(g.noise(len(t), rng), 700, 0.8) * g.env_ad(t, 0.3, 0.4, 1.0)) * 1.2
    return g.reverb(axe + knee + breath, rng, 0.9, 0.2)


def g_kaast_walk_away(rng):
    """Unhurried armoured footsteps fading out."""
    t = g.tt(3.0)
    x = np.zeros(len(t))
    for k in range(6):
        at = 0.1 + k * 0.45
        x += (g.thump(t, 100, 50, 0.01, 0.05, t0=at) + g.gear_rattle(t, rng, at, at + 0.05, 2) * 0.2) * (1.0 - k * 0.14)
    return g.reverb(x, rng, 1.0, 0.25)


# ---------------------------------------------------------------- music (procedural placeholders)

def i_drum(rng, f_hi=95, f_lo=45, decay=0.3, skin=0.4):
    t = g.tt(decay * 3)
    return g.thump(t, f_hi, f_lo, 0.04, decay) + g.bp(g.noise(len(t), rng), 320, 1.0) * g.env_ad(t, 0.001, 0.04) * skin


def i_tap(rng, f0):
    """A metal tap: a scrap of plate struck with a rod."""
    t = g.tt(0.6)
    return g.metal_ring(t, rng, f0, 5, 0.18) * 0.6 + g.click(t, rng, 0.0, 1.0) * 0.3


def i_pluck(f, dur, rng):
    """A low, dark plucked string."""
    t = g.tt(dur)
    return g.bell_partials(len(t), f, [1, 2, 3, 4], [dur * 0.4, dur * 0.2, dur * 0.12, dur * 0.07], [1, 0.45, 0.2, 0.1], rng,
                           attack=0.004, detune=0.001)


def i_stab(f, dur):
    t = g.tt(dur)
    n = len(t)
    x = g.saw(g.phase(np.full(n, f), n), f, nh=20) + g.saw(g.phase(np.full(n, f * 1.007), n), f, nh=20)
    x = g.sweep(x, 220 + 1500 * np.exp(-t / 0.12), "lp", nb=8)
    return g.sat(x * g.env_ad(t, 0.01, dur * 0.45), 2.0)


def i_reed(f, dur):
    """A reedy lead: a narrow pulse with slow vibrato, softly filtered."""
    t = g.tt(dur)
    n = len(t)
    vib = 1 + 0.006 * np.sin(2 * np.pi * 5.2 * t) * np.clip(t / 0.3, 0, 1)
    x = g.pulse(g.phase(f * vib, n), f, w=0.18, nh=18)
    x = g.lp(x, 2400) + 0.3 * g.bp(x, 1300, 3)
    env = np.clip(t / 0.06, 0, 1) * np.where(t > dur - 0.12, np.clip((dur - t) / 0.12, 0, 1), 1.0)
    return x * env * 0.5


def i_pad(freqs, dur, attack=0.5, release=1.0, bright=800):
    t = g.tt(dur + release)
    n = len(t)
    x = np.zeros(n)
    for f in freqs:
        for det in (0.995, 1.005):
            x += g.saw(g.phase(np.full(n, f * det), n), f, nh=10)
    env = np.clip(t / attack, 0, 1) * np.where(t > dur, np.exp(-(t - dur) / (release / 3)), 1.0)
    return g.lp(x, bright) * env / (2 * len(freqs))


def arrange(L, events):
    buf = np.zeros(g.ns(L))
    for t0, x in events:
        g.place_circular(buf, x, t0 % L)
    return buf


def g_music_hunt(rng):
    """Phase 1, "The Hunt" (88 BPM, 8 bars of E minor): patient and stalking. A frame-drum heartbeat, a
    low plucked ostinato, metal taps off the beat and a sparse reedy call answered by silence."""
    beat = 60 / 88
    bar = beat * 4
    L = bar * 8
    roots = [40, 40, 43, 40, 38, 40, 36, 35]
    drums, plucks, taps, lead, pads = [], [], [], [], []
    figure = [0, 7, 12, 7, 10, 7, 3, 7]
    for b, root in enumerate(roots):
        t0 = b * bar
        drums.append((t0, i_drum(rng, 85, 40, 0.35) * 1.0))
        drums.append((t0 + beat * 0.6, i_drum(rng, 80, 40, 0.25) * 0.55))
        drums.append((t0 + beat * 2.5, i_drum(rng, 90, 42, 0.3) * 0.7))
        for i, k in enumerate(figure):
            plucks.append((t0 + i * beat / 2, i_pluck(midi(root + 12 + k), 0.9, rng) * (0.6 if i % 2 == 0 else 0.35)))
        taps.append((t0 + beat * 1.5, i_tap(rng, 2400) * 0.35))
        taps.append((t0 + beat * 3.5, i_tap(rng, 2650) * 0.25))
        pads.append((t0, i_pad([midi(root + 12), midi(root + 19)], bar, 0.8, 1.0, 600)))
        if b in (1, 5):
            for n_, at, d in ((76, 0.0, 1.4), (74, 1.5, 0.5), (71, 2.0, 1.8)):
                lead.append((t0 + at * beat, i_reed(midi(n_), d * beat)))
    mix = arrange(L, drums) * 0.7 + arrange(L, plucks) * 0.45 + arrange(L, taps) * 0.5 + arrange(L, lead) * 0.35 + arrange(L, pads) * 0.4
    return g.reverb(mix, rng, 2.0, 0.3, loop=True, bright=3800)


def g_music_trap(rng):
    """Phase 2, "The Trap" (100 BPM, 8 bars of B minor): tense and ticking. A muted metal tick on every
    eighth like an arming fuse, a pulsing low drone, a two-note bass trap that snaps shut each bar, and
    dissonant taps."""
    beat = 60 / 100
    bar = beat * 4
    L = bar * 8
    roots = [35, 35, 36, 35, 35, 35, 38, 36]
    ticks, bass, drums, taps = [], [], [], []
    for b, root in enumerate(roots):
        t0 = b * bar
        for i in range(8):
            ticks.append((t0 + i * beat / 2, i_tap(rng, 3600 if i % 2 == 0 else 3300) * (0.3 if i % 2 == 0 else 0.18)))
        bass.append((t0, i_pluck(midi(root), 1.2, rng) * 0.9))
        bass.append((t0 + beat * 2.75, i_pluck(midi(root + 1), 0.8, rng) * 0.7))
        drums.append((t0 + beat * 3.0, i_drum(rng, 110, 45, 0.2, 0.7) * 0.8))
        drums.append((t0 + beat * 3.5, i_drum(rng, 120, 50, 0.15, 0.8) * 0.6))
        if b % 2 == 1:
            taps.append((t0 + beat * 1.0, i_tap(rng, 1900) * 0.4))
            taps.append((t0 + beat * 1.25, i_tap(rng, 2015) * 0.35))
    n = g.ns(L)
    t = np.arange(n) / g.SR
    drone = np.sin(g.phase(np.full(n, midi(23)), n, loop=True)) + 0.5 * np.sin(g.phase(np.full(n, midi(30)), n, loop=True))
    drone = drone * (0.6 + 0.4 * (0.5 + 0.5 * np.cos(2 * np.pi * (L / beat) * t / L)))
    mix = arrange(L, ticks) * 0.6 + arrange(L, bass) * 0.6 + arrange(L, drums) * 0.6 + arrange(L, taps) * 0.45 + drone * 0.15
    return g.reverb(mix, rng, 1.8, 0.28, loop=True, bright=4000)


def g_music_last_stand(rng):
    """Phase 3, "The Last Stand" (124 BPM, 8 bars of E Phrygian): the axe out. Driving drums, low saw
    stabs on the off-beats, a defiant reed line and clanging metal accents."""
    beat = 60 / 124
    bar = beat * 4
    L = bar * 8
    roots = [40, 41, 40, 43, 40, 41, 38, 40]
    drums, stabs, lead, taps = [], [], [], []
    line = [(76, 0.0, 0.5), (77, 0.5, 0.5), (76, 1.0, 1.0), (74, 2.0, 0.5), (72, 2.5, 0.5), (71, 3.0, 1.0)]
    for b, root in enumerate(roots):
        t0 = b * bar
        for at, v, f in ((0.0, 1.0, 90), (1.0, 0.6, 100), (1.5, 0.5, 110), (2.0, 0.9, 85), (3.0, 0.6, 100), (3.5, 0.7, 120)):
            drums.append((t0 + at * beat, i_drum(rng, f, 40, 0.25, 0.6) * v))
        for at in (0.5, 1.5, 2.5, 3.5):
            stabs.append((t0 + at * beat, (i_stab(midi(root), beat * 0.45) + i_stab(midi(root + 7), beat * 0.45) * 0.6) * 0.45))
        if b % 2 == 0:
            for n_, at, d in line:
                lead.append((t0 + at * beat, i_reed(midi(n_ + (root - 40)), d * beat * 0.95)))
        taps.append((t0, i_tap(rng, 1500) * 0.5))
    mix = arrange(L, drums) * 0.75 + arrange(L, stabs) * 0.55 + arrange(L, lead) * 0.4 + arrange(L, taps) * 0.4
    return g.reverb(mix, rng, 1.6, 0.25, loop=True, bright=3600)


FILES = {
    "mandalorian.jet_tell": {"mandalorian/jet_tell": g_jet_tell},
    "mandalorian.flamer_tell": {"mandalorian/flamer_tell": g_flamer_tell},
    "mandalorian.rocket_lock": {"mandalorian/rocket_lock": g_rocket_lock},
    "mandalorian.net_tell": {"mandalorian/net_tell": g_net_tell},
    "mandalorian.jet": {"mandalorian/jet": g_jet},
    "mandalorian.flamer": {"mandalorian/flamer": g_flamer},
    "mandalorian.rocket": {"mandalorian/rocket": g_rocket},
    "mandalorian.explode": {"mandalorian/explode": g_explode},
    "mandalorian.net": {"mandalorian/net": g_net},
    "mandalorian.hurt": {"mandalorian/hurt": make_hunter_hurt(160, 560)},
    "mandalorian.death": {"mandalorian/death": g_hunter_death},
    "mandalorian.bark": {"mandalorian/bark": g_hunter_bark},
    "droid.spin_up": {"droid/spin_up": g_spin_up},
    "droid.fire": {"droid/fire": g_droid_fire},
    "droid.shield": {"droid/shield": g_droid_shield},
    "droid.death": {"droid/death": g_droid_death},
    "droid.hurt": {"droid/hurt": g_droid_hurt},
    "droid.step": {"droid/step": g_droid_step},
    "droid.alert": {"droid/alert": g_droid_alert},
    "hollowhorn.snort": {"hollowhorn/snort": g_hollowhorn_snort},
    "hollowhorn.charge": {"hollowhorn/charge": g_hollowhorn_charge},
    "hollowhorn.impact": {"hollowhorn/impact": g_hollowhorn_impact},
    "hollowhorn.idle": {"hollowhorn/idle": g_hollowhorn_idle},
    "hollowhorn.hurt": {"hollowhorn/hurt": g_hollowhorn_hurt},
    "hollowhorn.death": {"hollowhorn/death": g_hollowhorn_death},
    "ash_hound.growl": {"ash_hound/growl": g_hound_growl},
    "ash_hound.howl": {"ash_hound/howl": g_hound_howl},
    "ash_hound.lunge": {"ash_hound/lunge": g_hound_lunge},
    "ash_hound.idle": {"ash_hound/idle": g_hound_idle},
    "ash_hound.hurt": {"ash_hound/hurt": g_hound_hurt},
    "ash_hound.death": {"ash_hound/death": g_hound_death},
    "boss.kaast.hurt": {"boss/kaast_hurt": g_kaast_hurt},
    "boss.kaast.hop": {"boss/kaast_hop": g_kaast_hop},
    "boss.kaast.rifle": {"boss/kaast_rifle": g_kaast_rifle},
    "boss.kaast.phase": {"boss/kaast_phase": g_kaast_phase},
    "boss.kaast.mine_arm": {"boss/kaast_mine_arm": g_kaast_mine_arm},
    "boss.kaast.mine_beep": {"boss/kaast_mine_beep": g_kaast_mine_beep},
    "boss.kaast.mine_burst": {"boss/kaast_mine_burst": g_kaast_mine_burst},
    "boss.kaast.net_throw": {"boss/kaast_net_throw": g_kaast_net_throw},
    "boss.kaast.axe_draw": {"boss/kaast_axe_draw": g_kaast_axe_draw},
    "boss.kaast.combo": {"boss/kaast_combo": g_kaast_combo},
    "boss.kaast.charge": {"boss/kaast_charge": g_kaast_charge},
    "boss.kaast.slam_tell": {"boss/kaast_slam_tell": g_kaast_slam_tell},
    "boss.kaast.slam": {"boss/kaast_slam": g_kaast_slam},
    "boss.kaast.helmet_crack": {"boss/kaast_helmet_crack": g_kaast_helmet_crack},
    "boss.kaast.yield": {"boss/kaast_yield": g_kaast_yield},
    "boss.kaast.walk_away": {"boss/kaast_walk_away": g_kaast_walk_away},
    "music.kaast.hunt": {"music/kaast_hunt": g_music_hunt},
    "music.kaast.trap": {"music/kaast_trap": g_music_trap},
    "music.kaast.last_stand": {"music/kaast_last_stand": g_music_last_stand},
}
