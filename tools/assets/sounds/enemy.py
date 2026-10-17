"""PLACEHOLDER sound recipes for the enemy feature.

Milestone 2 (Sith Acolyte, Poacher Captain, vibroblade, blaster pistol): owned by
the saber worker; see events_m2.json. Milestone 3 (Republic and Imperial troopers,
the frag grenade): owned by W4; see events_m3.json and docs/MILESTONE_3.md section
13.3. The M3 recipes keep the Phase 0 placeholder file names (trooper/<id>,
grenade/<id>), so sounds.json does not change. Everything is synthesised from
scratch with the helpers in gen_sounds.py.
"""
import gen_sounds as g

np = g.np


def g_shove_windup(rng):
    """Dark-tinted gathering pressure."""
    t = g.tt(0.85)
    u = np.clip(t / 0.8, 0, 1)
    gather = g.sweep(g.noise(len(t), rng), 120 + 700 * u ** 2, "lp", nb=10) * u ** 2 * 2.0
    growl = g.lp(g.saw(g.phase(55 + 10 * u, len(t)), 70, nh=20), 400) * u * 0.4
    return g.reverb(gather + growl, rng, 1.0, 0.2, bright=2500)


def g_shove(rng):
    t = g.tt(0.9)
    th = g.sat(g.thump(t, 60, 28, 0.08, 0.22), 1.8)
    press = g.sweep(g.noise(len(t), rng), 1800 * np.exp(-t / 0.2) + 150, "lp", nb=10) * g.env_ad(t, 0.01, 0.25) * 2.0
    return g.reverb(th + press, rng, 1.0, 0.25, bright=2500)


def make_hurt(f_hi, f1):
    def gen(rng):
        t = g.tt(0.45)
        return g.reverb(g.grunt(t, rng, 0.01, 0.17, f_hi, f_hi * 0.8, f1) * 1.5, rng, 0.4, 0.1, bright=4000)
    return gen


def make_death(f_hi, f_lo):
    def gen(rng):
        t = g.tt(1.2)
        x = g.grunt(t, rng, 0.0, 0.55, f_hi, f_lo, 540, 1000)
        x += g.thump(t, 90, 40, 0.05, 0.12, t0=0.75) * 0.6
        return g.reverb(x, rng, 0.6, 0.12, bright=4000)
    return gen


def make_yield(f_hi):
    """A winded exhale and a knee on the ground."""
    def gen(rng):
        t = g.tt(1.0)
        exhale = g.sweep(g.noise(len(t), rng), 900 - 400 * np.clip(t / 0.6, 0, 1), "bp", 1.2, nb=8) * g.env_ad(t, 0.05, 0.3) * 0.6
        voice = g.grunt(t, rng, 0.0, 0.3, f_hi, f_hi * 0.85, 520, 980) * 0.6
        knee = g.thump(t, 100, 50, 0.03, 0.08, t0=0.45) * 0.5
        return g.reverb(exhale + voice + knee, rng, 0.5, 0.12)
    return gen


def g_captain_heavy_windup(rng):
    t = g.tt(0.8)
    return g.reverb(g.grunt(t, rng, 0.0, 0.6, 170, 210, 650, 1200) * 1.6, rng, 0.6, 0.15, bright=4000)


def make_vibro_swing(p):
    def gen(rng):
        t = g.tt(0.45)
        b = g.bell(t, 0.18, 0.06, 0.1)
        buzz = g.bp(g.saw(g.phase(np.full(len(t), 240.0 * p), len(t)), 240 * p, nh=40), 2200, 1.5) * b * 0.5
        return g.reverb(g.whoosh(t, rng, 600 + 2400 * b * p, 1.5, b) + buzz, rng, 0.35, 0.08)
    return gen


def g_vibro_hit(rng):
    t = g.tt(0.5)
    body = g.bp(g.noise(len(t), rng), 600, 1.2) * g.env_ad(t, 0.002, 0.05) * 1.5
    buzz = g.bp(g.saw(g.phase(np.full(len(t), 240.0), len(t)), 240, nh=40), 2500, 1.5) * g.env_ad(t, 0.002, 0.12) * 0.6
    return g.reverb(body + buzz + g.thump(t, 120, 60, 0.02, 0.05) * 0.4, rng, 0.4, 0.1)


FILES = {
    "acolyte.shove_windup": {"acolyte/shove_windup": g_shove_windup},
    "acolyte.shove": {"acolyte/shove": g_shove},
    "acolyte.hurt": {"acolyte/hurt1": make_hurt(150, 560), "acolyte/hurt2": make_hurt(135, 520)},
    "acolyte.death": {"acolyte/death": make_death(140, 85)},
    "acolyte.yield": {"acolyte/yield": make_yield(140)},
    "captain.heavy_windup": {"captain/heavy_windup": g_captain_heavy_windup},
    "captain.hurt": {"captain/hurt1": make_hurt(190, 640), "captain/hurt2": make_hurt(175, 600)},
    "captain.death": {"captain/death": make_death(185, 110)},
    "captain.yield": {"captain/yield": make_yield(185)},
    "vibroblade.swing": {"vibroblade/swing1": make_vibro_swing(1.0), "vibroblade/swing2": make_vibro_swing(0.9)},
    "vibroblade.hit": {"vibroblade/hit": g_vibro_hit},
    "blaster.pistol_fire": {"blaster/pistol_fire1": g.make_blaster_fire(3600, 260, 0.025, 880),
                            "blaster/pistol_fire2": g.make_blaster_fire(3300, 230, 0.03, 800)},
}


# ---------------------------------------------------------------- M3: trooper rifles

def g_trooper_republic_charge(rng):
    """Clean capacitor whine: a bright rising sine and triangle pair with a light hiss (0.6 s, the charge tell)."""
    t = g.tt(0.6)
    u = t / 0.6
    f = 700 * (2600 / 700) ** (u ** 1.4)
    vib = 1 + 0.006 * np.sin(2 * np.pi * np.cumsum(10 + 25 * u) / g.SR)
    tone = np.sin(g.phase(f * vib, len(t))) + 0.35 * np.sin(g.phase(f * vib * 2.0, len(t))) + 0.15 * np.sin(g.phase(f * vib * 3.01, len(t)))
    amp = 0.25 + 0.75 * u ** 0.8
    hiss = g.sweep(g.noise(len(t), rng), 2500 * 2 ** u, "bp", 3) * 0.25 * u
    tick = g.hp(g.crackle(len(t), 30 + 200 * u ** 2, rng), 5000) * 0.25
    x = tone * amp * 0.5 + hiss + tick
    x[-g.ns(0.012):] *= np.linspace(1, 0, g.ns(0.012))
    return x


def g_trooper_imperial_charge(rng):
    """A deeper, buzzing build with a growl under it: the heavy Imperial rifle gathering one strong bolt."""
    t = g.tt(0.6)
    u = t / 0.6
    f = 180 * (900 / 180) ** (u ** 1.7)
    buzz = g.lp(g.saw(g.phase(f, len(t)), f.max(), nh=30, roll=1.1), 2600) * (0.3 + 0.7 * u)
    growl = g.lp(g.saw(g.phase(np.full(len(t), 55.0) * (1 + 0.3 * u), len(t)), 80, nh=12), 300) * 0.5 * u
    sizz = g.hp(g.crackle(len(t), 80 + 900 * u ** 2, rng), 3000) * 0.5 * u
    x = buzz * 0.6 + growl + sizz
    x[-g.ns(0.012):] *= np.linspace(1, 0, g.ns(0.012))
    return g.reverb(x, rng, 0.4, 0.12, bright=3000)


def g_trooper_republic_fire(rng):
    """A crisp, bright bolt with a short mechanical clack (bursts of three)."""
    t = g.tt(0.55)
    bolt = g.make_blaster_fire(4300, 320, 0.02, 1020)(rng)
    clack = (g.click(t, rng, 0.012, 1.2) * 0.5 + g.metal_ring(t, rng, 3400, 4, 0.03, 0.012) * 0.15)
    return g.place(bolt, clack, 0.0)


def g_trooper_imperial_fire(rng):
    """A heavier, lower bolt with a chest-felt thump and a longer tail (one strong shot)."""
    t = g.tt(0.8)
    bolt = g.make_blaster_fire(2700, 140, 0.045, 560)(rng)
    body = g.sat(g.thump(t, 110, 45, 0.04, 0.12), 1.6) * 0.7
    rumble = g.lp(g.noise(len(t), rng), 300) * g.env_ad(t, 0.004, 0.18) * 0.6
    return g.place(bolt * 1.0, g.reverb(body + rumble, rng, 0.6, 0.2, bright=2500), 0.0)


def g_trooper_vent(rng):
    """Hot gas released from the rifle vents: a hiss that falls away, then cooling pings."""
    t = g.tt(1.2)
    fc = 1800 + 3200 * np.exp(-t / 0.25)
    hiss = g.sweep(g.noise(len(t), rng), fc, "bp", 0.9, nb=10) * g.env_ad(t, 0.01, 0.35) * 1.4
    pop = g.crack(t, rng, 0.0, 0.006, 1500) * 0.6
    pings = sum(g.metal_ring(t, rng, rng.uniform(3800, 5600), 3, 0.05, t0) * rng.uniform(0.08, 0.16) for t0 in (0.45, 0.7, 0.95))
    return g.reverb(hiss + pop + pings, rng, 0.5, 0.12, bright=6000)


def g_trooper_kolto(rng):
    """A pressurised injector: a seal click, then a soft wet hiss with a faint rising whistle."""
    t = g.tt(0.8)
    seal = g.click(t, rng, 0.01, 0.9)
    hiss = g.bp(g.noise(len(t), rng), 4200, 1.2) * g.env_ad(t, 0.04, 0.25, 0.03) * 0.9
    bubbles = g.bp(g.crackle(len(t), 120 * g.env_ad(t, 0.05, 0.3, 0.05), rng), 1600, 2.5) * 1.5
    whistle = np.sin(g.phase(1300 + 500 * np.clip(t / 0.5, 0, 1), len(t))) * g.bell(t, 0.25, 0.1, 0.2) * 0.08
    return g.reverb(seal + hiss + bubbles + whistle, rng, 0.4, 0.12, bright=6000)


def g_trooper_tend(rng):
    """Hands at work over a downed trooper: webbing and cloth, a strap pulled tight, a little kolto."""
    t = g.tt(0.9)
    cloth = g.rustle(t, rng, 0.0, 0.45, 70, fc=2300) * 0.7
    strap = g.sweep(g.noise(len(t), rng), 900 + 1500 * g.bell(t, 0.5, 0.05, 0.08), "bp", 3) * g.bell(t, 0.5, 0.04, 0.08) * 0.8
    buckle = g.click(t, rng, 0.58, 1.1) * 0.5 + g.gear_rattle(t, rng, 0.05, 0.4, 4) * 0.6
    puff = g.bp(g.noise(len(t), rng), 4000, 1.2) * g.env_ad(t, 0.02, 0.12, 0.65) * 0.3
    return g.reverb(cloth + strap + buckle + puff, rng, 0.35, 0.1)


def g_trooper_stand(rng):
    """A trooper pushes up off the ground: effort through the helmet, plates settling, a boot planted."""
    t = g.tt(1.0)
    effort = g.bp(g.grunt(t, rng, 0.05, 0.35, 150, 175, 600, 1150), 900, 0.8) * 1.4
    plates = g.gear_rattle(t, rng, 0.1, 0.6, 12)
    boot = g.thump(t, 110, 55, 0.02, 0.06, t0=0.62) * 0.6 + g.lp(g.noise(len(t), rng), 1200) * g.env_ad(t, 0.003, 0.05, 0.62) * 0.4
    dust = g.lp(g.noise(len(t), rng), 2500) * g.env_ad(t, 0.05, 0.25, 0.65) * 0.15
    return g.reverb(effort + plates + boot + dust, rng, 0.5, 0.12, bright=4500)


def g_trooper_rally(rng):
    """A sergeant's rally: a fist struck on the chest plate and a short barked call through the helmet."""
    t = g.tt(0.9)
    fist = g.thump(t, 160, 80, 0.015, 0.05) * 0.8 + g.metal_ring(t, rng, 820, 5, 0.12) * 0.25
    call = g.bp(g.grunt(t, rng, 0.12, 0.3, 175, 150, 640, 1250), 1100, 0.7) * 1.8
    radio = g.hp(g.bp(g.noise(len(t), rng), 2500, 1.5), 900) * g.env_ad(t, 0.005, 0.03, 0.1) * 0.25
    return g.reverb(fist + call + radio, rng, 0.6, 0.15, bright=4000)


def g_trooper_flare(rng):
    """A flare launched: a hollow pop, then a fizzing burn that climbs away."""
    t = g.tt(1.8)
    pop = g.thump(t, 220, 90, 0.01, 0.04) * 0.9 + g.crack(t, rng, 0.0, 0.008, 800) * 0.7
    u = np.clip((t - 0.05) / 1.5, 0, 1)
    fizz = g.sweep(g.noise(len(t), rng), 1500 + 3500 * u, "bp", 1.2, nb=10) * (1 - u) ** 1.5 * g.env_ad(t, 0.03, 10.0, 0.04)
    sputter = g.hp(g.crackle(len(t), 300 * (1 - u), rng), 2500) * 0.6
    whoosh = g.whoosh(t, rng, 500 + 1500 * g.bell(t, 0.25, 0.08, 0.4), 1.4, g.bell(t, 0.25, 0.08, 0.4)) * 0.6
    return g.reverb(pop + fizz * 0.9 + sputter + whoosh, rng, 1.0, 0.25, bright=5000)


def g_trooper_radio(rng):
    """The helmet radio keying up for a bark: a click, a squelch of band-limited static, a click off."""
    t = g.tt(0.5)
    key = g.click(t, rng, 0.005, 1.3) * 0.6 + g.click(t, rng, 0.4, 1.1) * 0.4
    static = g.bp(g.noise(len(t), rng), 2000, 1.0) * (g.env_ad(t, 0.004, 0.06, 0.01) * 0.8 + g.bell(t, 0.38, 0.02, 0.02) * 0.4)
    tone = np.sin(g.phase(np.full(len(t), 1450.0), len(t))) * g.bell(t, 0.03, 0.01, 0.025) * 0.15
    return g.hp(key + static + tone, 300)


def g_trooper_hurt(rng):
    """A grunt muffled by the helmet, with plate and webbing rattle."""
    t = g.tt(0.5)
    voice = g.bp(g.grunt(t, rng, 0.01, 0.18, 165, 135, 600, 1150), 950, 0.7) * 1.8
    plates = g.gear_rattle(t, rng, 0.0, 0.15, 8) * 0.8
    hit = g.thump(t, 140, 70, 0.015, 0.04) * 0.4
    return g.reverb(voice + plates + hit, rng, 0.4, 0.1, bright=4000)


def g_trooper_death(rng):
    """A failing breath through the helmet, the rifle and plates hitting the ground, a last settle."""
    t = g.tt(1.4)
    voice = g.bp(g.grunt(t, rng, 0.0, 0.5, 150, 95, 560, 1000), 900, 0.7) * 1.2
    rifle = g.metal_ring(t, rng, 1500, 5, 0.1, 0.62) * 0.4 + g.click(t, rng, 0.62, 0.8) * 0.4
    body = g.thump(t, 95, 40, 0.05, 0.12, t0=0.8) * 0.7 + g.lp(g.noise(len(t), rng), 900) * g.env_ad(t, 0.003, 0.1, 0.8) * 0.5
    plates = g.gear_rattle(t, rng, 0.78, 1.05, 14)
    return g.reverb(voice + rifle + body + plates, rng, 0.6, 0.12, bright=4000)


# ---------------------------------------------------------------- M3: frag grenade

def g_grenade_arm(rng):
    """The pin pulled and the spoon flipped: two sharp metal clicks, a ping and a short arming beep."""
    t = g.tt(0.5)
    pin = g.click(t, rng, 0.01, 1.4) * 0.7 + g.metal_ring(t, rng, 4200, 3, 0.04, 0.01) * 0.25
    spoon = g.click(t, rng, 0.12, 1.1) * 0.6 + g.metal_ring(t, rng, 2900, 4, 0.08, 0.12) * 0.3
    beep = np.sin(g.phase(np.full(len(t), 1900.0), len(t))) * g.env_ad(t, 0.003, 0.05, 0.2) * (t < 0.27) * 0.3
    return g.reverb(pin + spoon + beep, rng, 0.35, 0.1, bright=6000)


def g_grenade_fuse(rng):
    """A rising tick for the 2 s fuse: ticks that come faster and higher until the last moment."""
    L = 2.0
    t = g.tt(L)
    x = np.zeros(len(t))
    t0, gap = 0.0, 0.22
    while t0 < L - 0.05:
        u = t0 / L
        f = 1500 * 2 ** (1.3 * u)
        x += np.sin(g.phase(np.full(len(t), f), len(t))) * g.env_ad(t, 0.002, 0.018, t0) * (0.5 + 0.5 * u)
        x += g.click(t, rng, t0, 1.0 + u) * 0.15
        t0 += gap
        gap = max(0.045, gap * 0.86)
    hiss = g.hp(g.noise(len(t), rng), 5000) * 0.03 * np.clip(t / L, 0, 1)
    return x + hiss


def g_grenade_burst(rng):
    """The burst: a hard crack and boom, shrapnel singing off, debris and dust settling."""
    t = g.tt(1.6)
    crack = g.crack(t, rng, 0.0, 0.012, 600) * 1.6
    boom = g.sat(g.thump(t, 85, 32, 0.07, 0.35), 2.0) * 1.1
    blast = g.sweep(g.noise(len(t), rng), 2500 * np.exp(-t / 0.15) + 120, "lp", nb=10) * g.env_ad(t, 0.002, 0.3) * 1.4
    shrapnel = sum(g.metal_ring(t, rng, rng.uniform(2500, 6000), 3, rng.uniform(0.03, 0.08), rng.uniform(0.02, 0.2)) * rng.uniform(0.05, 0.12)
                   for _ in range(6))
    debris = g.bp(g.crackle(len(t), 260 * np.exp(-np.maximum(t - 0.1, 0) / 0.35), rng), 1800, 0.9) * 3.0
    dust = g.lp(g.noise(len(t), rng), 900) * g.env_ad(t, 0.06, 0.45, 0.05) * 0.4
    return g.reverb(crack + boom + blast + shrapnel + debris + dust, rng, 1.4, 0.25, bright=3500)


FILES.update({
    "trooper.republic.charge": {"trooper/republic_charge": g_trooper_republic_charge},
    "trooper.republic.fire": {"trooper/republic_fire": g_trooper_republic_fire},
    "trooper.imperial.charge": {"trooper/imperial_charge": g_trooper_imperial_charge},
    "trooper.imperial.fire": {"trooper/imperial_fire": g_trooper_imperial_fire},
    "trooper.vent": {"trooper/vent": g_trooper_vent},
    "trooper.kolto": {"trooper/kolto": g_trooper_kolto},
    "trooper.tend": {"trooper/tend": g_trooper_tend},
    "trooper.stand": {"trooper/stand": g_trooper_stand},
    "trooper.rally": {"trooper/rally": g_trooper_rally},
    "trooper.flare": {"trooper/flare": g_trooper_flare},
    "trooper.radio": {"trooper/radio": g_trooper_radio},
    "trooper.hurt": {"trooper/hurt": g_trooper_hurt},
    "trooper.death": {"trooper/death": g_trooper_death},
    "grenade.arm": {"grenade/arm": g_grenade_arm},
    "grenade.fuse": {"grenade/fuse": g_grenade_fuse},
    "grenade.burst": {"grenade/burst": g_grenade_burst},
})
