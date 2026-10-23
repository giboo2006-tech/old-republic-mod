"""PLACEHOLDER sound recipes for the equipment feature (era armour, Milestone 3).

Owned by W4. Synthesised from scratch with the helpers in gen_sounds.py; see
events_m3.json for the event list and docs/MILESTONE_3.md sections 9 and 13.3.
The file names are the Phase 0 placeholder names (armour/<id>), so sounds.json
does not change.

- armour.equip_trooper: the trooper sets' equip sound (their armour material).
- armour.equip_robe: the robes' equip sound.
- armour.set_bonus: a set was completed (the armour_set effect, played to the
  wearer with a pitch per set by the client).
"""
import gen_sounds as g

np = g.np


def g_equip_trooper(rng):
    """Plates seated and clasped: a dull plate knock, two latch clicks, a strap cinched and a short rattle."""
    t = g.tt(0.7)
    knock = g.thump(t, 190, 95, 0.012, 0.04) * 0.6 + g.metal_ring(t, rng, 1100, 5, 0.08) * 0.2
    latches = g.click(t, rng, 0.08, 1.0) * 0.6 + g.click(t, rng, 0.17, 1.15) * 0.5
    strap = g.sweep(g.noise(len(t), rng), 800 + 1800 * g.bell(t, 0.3, 0.04, 0.07), "bp", 3) * g.bell(t, 0.3, 0.03, 0.07) * 0.7
    rattle = g.gear_rattle(t, rng, 0.02, 0.35, 6) * 0.5
    return g.reverb(knock + latches + strap + rattle, rng, 0.35, 0.1, bright=5000)


def g_equip_robe(rng):
    """Heavy cloth drawn on: a soft swish, folds settling, and the belt buckle closed."""
    t = g.tt(0.9)
    swish = g.whoosh(t, rng, 700 + 1600 * g.bell(t, 0.15, 0.06, 0.12), 0.9, g.bell(t, 0.15, 0.06, 0.15)) * 0.9
    folds = g.rustle(t, rng, 0.05, 0.6, 55, fc=1700) * 0.6
    buckle = g.click(t, rng, 0.62, 0.8) * 0.35 + g.metal_ring(t, rng, 2300, 3, 0.05, 0.62) * 0.08
    return g.reverb(swish + folds + buckle, rng, 0.35, 0.1, bright=4000)


def g_set_bonus(rng):
    """The set settles: a last soft clasp and a warm two-note rise that rings out under a breath of air."""
    t = g.tt(1.6)
    clasp = g.click(t, rng, 0.01, 0.9) * 0.35 + g.thump(t, 150, 90, 0.015, 0.05) * 0.3
    first = g.bell_partials(len(t), 392.0, [1, 2.0, 3.01, 4.2], [0.9, 0.5, 0.3, 0.15], [1, 0.3, 0.12, 0.05], rng, t0=0.06, attack=0.012)
    second = g.bell_partials(len(t), 587.33, [1, 2.0, 3.01, 4.2], [1.1, 0.6, 0.3, 0.15], [1, 0.25, 0.1, 0.04], rng, t0=0.22, attack=0.015)
    air = g.sweep(g.noise(len(t), rng), 900 + 1800 * g.bell(t, 0.3, 0.12, 0.4), "bp", 1.0, nb=8) * g.bell(t, 0.3, 0.12, 0.4) * 0.15
    return g.reverb(clasp + (first * 0.4 + second * 0.45) + air, rng, 1.3, 0.3, bright=5000)


FILES = {
    "armour.equip_trooper": {"armour/equip_trooper": g_equip_trooper},
    "armour.equip_robe": {"armour/equip_robe": g_equip_robe},
    "armour.set_bonus": {"armour/set_bonus": g_set_bonus},
}
