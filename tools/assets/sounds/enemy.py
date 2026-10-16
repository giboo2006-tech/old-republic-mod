"""PLACEHOLDER sound recipes for the enemy feature (Sith Acolyte, Poacher Captain).

Owned by the saber worker. Synthesised from scratch with the helpers in
gen_sounds.py; see events_m2.json for the event list.
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
