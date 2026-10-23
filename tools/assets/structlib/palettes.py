"""Material palettes: named roles with weighted block choices.

    from structlib.palettes import Palette
    jedi = Palette(
        wall=[('or:ruin_stone', 6), ('or:ruin_stone_cracked', 2), ('or:ruin_stone_mossy', 2)],
        trim='or:ruin_stone_carved',
        floor=[('smooth_stone', 3), ('polished_andesite', 1)],
    )
    t.fill(0, 1, 0, 10, 5, 0, jedi.picker('wall', t.rng))      # mixed per cell, repeatable
    t.fill(0, 0, 0, 10, 0, 10, jedi.patches('floor', seed=4))    # mixed in noise patches
    jedi['trim']                                                  # the role's main block

A role's value is one state or a list of (state, weight). Each family's builder module keeps its own
palettes next to its code; the shared ones below cover the materials several families use.
"""
from . import blocks as B
from .nature import Noise


class Palette:
    def __init__(self, **roles):
        self.roles = {}
        for k, v in roles.items():
            self.roles[k] = _norm(v)

    def __getitem__(self, role):
        return self.roles[role][0][0]

    def __contains__(self, role):
        return role in self.roles

    def options(self, role):
        return list(self.roles[role])

    def pick(self, role, rng):
        opts = self.roles[role]
        if len(opts) == 1:
            return opts[0][0]
        total = sum(w for _, w in opts)
        r = rng.random() * total
        for s, w in opts:
            r -= w
            if r <= 0:
                return s
        return opts[-1][0]

    def picker(self, role, rng):
        """A callable f(x, y, z) -> state for fill(), choosing per cell."""
        return lambda x, y, z: self.pick(role, rng)

    def patches(self, role, seed=0, scale=4.0):
        """A callable that chooses by coherent noise, so variants clump into patches."""
        opts = self.roles[role]
        total = sum(w for _, w in opts)
        noise = Noise(seed, scale)
        jitter = Noise(seed + 977, 1.3)

        def f(x, y, z):
            v = 0.75 * noise.at(x, y, z) + 0.25 * jitter.at(x, y, z)
            r = v * total
            for s, w in opts:
                r -= w
                if r <= 0:
                    return s
            return opts[-1][0]
        return f

    def with_roles(self, **roles):
        """A copy with some roles replaced or added."""
        p = Palette()
        p.roles = dict(self.roles)
        for k, v in roles.items():
            p.roles[k] = _norm(v)
        return p


def _canon(s):
    return B.build(*B.parse(s))


def _norm(v):
    if isinstance(v, str):
        return [(_canon(v), 1)]
    out = []
    for item in v:
        if isinstance(item, str):
            out.append((_canon(item), 1))
        else:
            s, w = item
            out.append((_canon(s), w))
    return out


# ---------------------------------------------------------------- shared palettes (the build kit, MILESTONE_6 4)
# Roles every palette has: wall, floor, trim, stairs, slab. Most also have: pillar, wall_top (a wall block),
# roof, light, crate, cloth, rubble. Copy one with with_roles() to tune it for a family.

TECH = Palette(
    wall=[('or:durasteel_plating', 7), ('or:durasteel_vent', 1), ('or:rusted_plating', 1)],
    floor=[('or:durasteel_deck', 6), ('or:durasteel_grate', 1)],
    trim='or:durasteel_beam[axis=x]',
    pillar='or:durasteel_beam[axis=y]',
    pipes='or:durasteel_pipes[axis=y]',
    stairs='or:durasteel_plating_stairs',
    slab='or:durasteel_plating_slab',
    floor_slab='or:durasteel_deck_slab',
    grate='or:durasteel_grate',
    roof=[('or:durasteel_plating', 4), ('or:rusted_plating', 1)],
    light='or:light_panel',
    crate=[('or:cargo_crate', 3), ('minecraft:barrel[facing=up,open=false]', 1)],
    machine='or:console',
    screen='or:holo_screen',
    rubble=[('or:debris', 3), ('minecraft:gravel', 1)],
)

REPUBLIC = Palette(
    wall=[('or:republic_plating', 7), ('or:durasteel_plating', 1), ('or:republic_panel', 1)],
    floor=[('or:pale_tiles', 6), ('or:durasteel_deck', 1)],
    trim='or:durasteel_beam[axis=x]',
    pillar='or:durasteel_beam[axis=y]',
    stairs='or:republic_plating_stairs',
    slab='or:republic_plating_slab',
    floor_stairs='or:pale_tile_stairs',
    floor_slab='or:pale_tile_slab',
    roof=[('or:republic_plating', 4), ('or:durasteel_plating', 1)],
    light='or:light_panel',
    crate=[('or:cargo_crate_republic', 3), ('or:cargo_crate', 1)],
    cloth='or:canvas_navy',
    glass='minecraft:light_blue_stained_glass_pane',
    rubble=[('or:debris', 3), ('or:scorch_mark', 1)],
)

IMPERIAL = Palette(
    wall=[('or:imperial_plating', 7), ('or:durasteel_plating', 1), ('or:imperial_panel', 1)],
    floor=[('or:durasteel_deck', 5), ('or:imperial_plating', 1)],
    trim='or:durasteel_beam[axis=x]',
    pillar='or:durasteel_beam[axis=y]',
    stairs='or:imperial_plating_stairs',
    slab='or:imperial_plating_slab',
    floor_slab='or:durasteel_deck_slab',
    roof=[('or:imperial_plating', 4), ('or:durasteel_plating', 1)],
    light='or:light_panel_red',
    crate=[('or:cargo_crate_imperial', 3), ('or:cargo_crate', 1)],
    cloth='or:canvas_crimson',
    glass='minecraft:tinted_glass',
    rubble=[('or:debris', 3), ('or:scorch_mark', 1)],
)

SCRAP = Palette(
    wall=[('or:rusted_plating', 5), ('or:durasteel_plating', 2), ('or:wreck_plating', 2), ('minecraft:weathered_cut_copper', 1)],
    floor=[('or:durasteel_deck', 2), ('minecraft:coarse_dirt', 2), ('minecraft:gravel', 1), ('or:debris', 1)],
    trim='or:durasteel_beam[axis=x]',
    pillar='or:durasteel_beam[axis=y]',
    stairs='or:durasteel_plating_stairs',
    slab='or:durasteel_plating_slab',
    roof=[('or:rusted_plating', 3), ('or:canvas', 2), ('or:wreck_plating_scorched', 1)],
    light='or:light_panel_amber',
    crate=[('or:cargo_crate', 3), ('minecraft:barrel[facing=up,open=false]', 2)],
    cloth=[('or:canvas', 3), ('minecraft:brown_wool', 1)],
    rubble=[('or:debris', 4), ('minecraft:gravel', 1)],
)

JEDI_STONE = Palette(
    wall=[('or:ruin_bricks', 6), ('or:ruin_stone', 2), ('or:ruin_stone_cracked', 1), ('or:ruin_stone_mossy', 1)],
    floor=[('or:ruin_stone', 4), ('or:ruin_bricks', 2), ('or:ruin_stone_cracked', 1)],
    trim='or:ruin_stone_carved',
    pillar='or:ruin_pillar[axis=y]',
    stairs='or:ruin_brick_stairs',
    slab='or:ruin_brick_slab',
    wall_top='or:ruin_brick_wall',
    roof=[('or:ruin_brick_slab', 3), ('or:ruin_stone_mossy', 1)],
    light='minecraft:soul_lantern',
    rubble=[('or:collapsed_rubble', 1), ('minecraft:cobblestone', 2), ('or:ruin_brick_slab', 2)],
)

SITH_STONE = Palette(
    wall=[('or:tomb_bricks', 6), ('or:tomb_stone', 2), ('or:tomb_stone_cracked', 1), ('minecraft:polished_blackstone_bricks', 1)],
    floor=[('or:tomb_stone', 4), ('or:tomb_bricks', 2), ('minecraft:polished_blackstone', 1)],
    trim='or:tomb_stone_carved',
    pillar='or:tomb_pillar[axis=y]',
    stairs='or:tomb_brick_stairs',
    slab='or:tomb_brick_slab',
    wall_top='or:tomb_brick_wall',
    roof=[('or:tomb_brick_slab', 3), ('or:tomb_stone_cracked', 1)],
    light='or:light_panel_red',
    glow='minecraft:magma_block',
    rubble=[('or:collapsed_rubble', 1), ('minecraft:blackstone', 2), ('or:tomb_brick_slab', 2)],
)

WATCH_STONE = Palette(
    wall=[('or:watch_stone', 7), ('minecraft:mossy_stone_bricks', 1), ('minecraft:cracked_stone_bricks', 1)],
    floor=[('or:watch_stone', 4), ('minecraft:mossy_cobblestone', 1), ('minecraft:polished_andesite', 1)],
    trim='minecraft:polished_andesite',
    pillar='minecraft:stripped_dark_oak_log[axis=y]',
    stairs='or:watch_stone_stairs',
    slab='or:watch_stone_slab',
    wall_top='or:watch_stone_wall',
    roof=[('minecraft:dark_oak_planks', 3), ('minecraft:spruce_planks', 1)],
    roof_stairs='minecraft:dark_oak_stairs',
    light='minecraft:lantern',
    cloth='minecraft:green_wool',
    rubble=[('minecraft:cobblestone', 2), ('minecraft:mossy_cobblestone', 1)],
)

ASH_STONE = Palette(
    wall=[('or:ash_bricks', 6), ('minecraft:blackstone', 1), ('minecraft:basalt[axis=y]', 1)],
    floor=[('or:ash_bricks', 2), ('minecraft:blackstone', 2), ('minecraft:gravel', 1)],
    trim='minecraft:polished_basalt[axis=x]',
    pillar='minecraft:polished_basalt[axis=y]',
    stairs='or:ash_brick_stairs',
    slab='or:ash_brick_slab',
    wall_top='or:ash_brick_wall',
    roof=[('or:rusted_plating', 2), ('or:ash_brick_slab', 2)],
    light='minecraft:lantern',
    metal='or:rusted_plating',
    rubble=[('minecraft:blackstone', 2), ('minecraft:gravel', 2), ('or:debris', 1)],
)

ADOBE = Palette(
    wall=[('or:adobe', 6), ('or:adobe_bricks', 2), ('minecraft:smooth_sandstone', 1)],
    floor=[('or:adobe_bricks', 2), ('minecraft:smooth_sandstone', 2), ('minecraft:packed_mud', 1)],
    trim='or:adobe_bricks',
    pillar='or:adobe_bricks',
    stairs='or:adobe_stairs',
    slab='or:adobe_slab',
    wall_top='or:adobe_wall',
    roof=[('or:adobe_slab', 3), ('or:canvas', 1)],
    light='minecraft:lantern',
    cloth=[('or:canvas', 3), ('or:canvas_crimson', 1)],
    salt='or:salt_bricks',
    rubble=[('minecraft:sand', 2), ('or:adobe_slab', 1)],
)

STORMSTONE = Palette(
    wall=[('or:stormstone_bricks', 6), ('or:stormstone', 2), ('minecraft:deepslate_bricks', 1)],
    floor=[('or:stormstone', 2), ('or:stormstone_bricks', 2), ('minecraft:tuff', 1)],
    trim='minecraft:polished_deepslate',
    pillar='minecraft:polished_basalt[axis=y]',
    stairs='or:stormstone_brick_stairs',
    slab='or:stormstone_brick_slab',
    wall_top='or:stormstone_brick_wall',
    roof=[('minecraft:oxidized_cut_copper', 2), ('or:stormstone_brick_slab', 2)],
    light='minecraft:soul_lantern',
    rubble=[('or:stormstone', 2), ('minecraft:tuff', 1)],
)

SHARED = {'TECH': TECH, 'REPUBLIC': REPUBLIC, 'IMPERIAL': IMPERIAL, 'SCRAP': SCRAP, 'JEDI_STONE': JEDI_STONE,
          'SITH_STONE': SITH_STONE, 'WATCH_STONE': WATCH_STONE, 'ASH_STONE': ASH_STONE, 'ADOBE': ADOBE,
          'STORMSTONE': STORMSTONE}
