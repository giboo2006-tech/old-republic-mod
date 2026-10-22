#!/usr/bin/env python3
"""The mentors' travel camps, story/jedi_camp and story/sith_camp (docs/MILESTONE_6.md 5.3).

Both stay 7 x 5 x 7: StoryStructures places them centred beside the player (summons and brings), with
layer 0 as the ground (buried 1). Kept exactly:
  * a minecraft:lantern at (5, 3, 5) (StoryGameTests.placeStructuresSwitch looks for it), standing on a
    post so it survives the shape update;
  * (3, 1, 3) and (3, 2, 3) explicit air: the mentor's stand;
  * (0, 1..2, 3) and (6, 1..2, 3) explicit air as before (where the Crossing's mentors used to stand).

  jedi_camp  Watchwoman Ilyra Saelen travels light: a navy lean-to over a bedroll, a fire pit with a
             kettle, a training stump with a staff, a kyber shard on a carved stone for meditation, a
             Republic supply crate and her water jar.
  sith_camp  Overseer Teyvar Sorn's field camp: a black-and-crimson field shelter on a deck plate, an
             Imperial crate with a field comm unit, a brazier, a weapon rack, a red deck light.

Usage: python3 tools/assets/structures/qs/camps.py [--preview <dir>]
"""
import sys

from common import (AIR, LANTERN, S, Noise, Template, barrel, bed, by_noise, campfire, carpet, check, column, fence,
                    log, plants_on, preview_dir, slab, stairs, trapdoor, wall, write)

SIZE = (7, 5, 7)
STAND = [(3, 1, 3), (3, 2, 3)]
OLD_SPOTS = [(0, 1, 3), (0, 2, 3), (6, 1, 3), (6, 2, 3)]
LANTERN_CELL = (5, 3, 5)
META = dict(buried=1, terrain='any', clearance=2, max_slope=2, support_depth=4, ramp_rings=0, clear_above=3,
            spawn_radius=32)


def ground(t, seed, worn, rim):
    """Layer 0: trampled earth in the middle, the rim mostly left to the world (a few cells unset)."""
    n = Noise(seed, 2.2)
    cells = []
    for x in range(7):
        for z in range(7):
            edge = x in (0, 6) or z in (0, 6)
            corner = x in (0, 6) and z in (0, 6)
            if corner and n.at(x, 0, z) > 0.45:
                continue  # the world's own ground shows at a corner
            state = by_noise(n, rim if edge else worn, x, 0, z)
            t.set(x, 0, z, state)
            cells.append((x, 0, z))
    return cells


def keep_free(t):
    for p in STAND + OLD_SPOTS:
        t.set(*p, AIR)


def jedi_camp():
    t = Template(*SIZE, seed=7101, name='story/jedi_camp')
    cells = ground(t, 7101,
                   worn=[(S('coarse_dirt'), 4), (S('dirt_path'), 3), (S('grass_block', snowy=False), 2),
                         (S('podzol', snowy=False), 1)],
                   rim=[(S('grass_block', snowy=False), 6), (S('coarse_dirt'), 2), (S('podzol', snowy=False), 1)])
    # A worn track through the stand, north to south.
    for z in range(7):
        if t.get(3, 0, z) is not None:
            t.set(3, 0, z, S('dirt_path'))

    # The lean-to, north-west: navy side flaps, a canvas roof falling to the back, a bedroll under it.
    for x in (0, 2):
        t.set(x, 1, 0, S('or:canvas_navy'))
        t.set(x, 1, 1, S('or:canvas_navy'))
        column(t, x, 1, 2, 2, fence('spruce_fence'))
    for x in range(3):
        t.set(x, 2, 0, stairs('or:canvas_stairs', 'south'))
        t.set(x, 3, 1, stairs('or:canvas_stairs', 'south'))
        t.set(x, 3, 2, slab('or:canvas_slab'))
        t.set(x, 2, 1, AIR)
    t.set(1, 1, 0, AIR)
    bed(t, 1, 1, 1, 'light_gray', 'north')                    # foot at z 1, head at z 0
    t.set(1, 0, 0, S('podzol', snowy=False))
    t.set(1, 0, 1, S('podzol', snowy=False))
    t.set(1, 1, 2, carpet('brown'))                             # a mat at the foot of the bedroll

    # Her supplies, north-east: a Republic crate, a barrel, her water jar, a folded table.
    t.set(5, 1, 0, S('or:cargo_crate_republic'))
    t.set(6, 1, 0, barrel('up'))
    t.set(6, 2, 0, S('or:canvas_slab', type='bottom', waterlogged=False))
    t.set(6, 1, 1, S('decorated_pot', facing='south', cracked=False, waterlogged=False))
    t.set(4, 1, 0, fence('spruce_fence'))
    t.set(4, 2, 0, trapdoor('spruce_trapdoor', 'south', 'top'))  # a folding table with a lantern on it
    t.set(5, 2, 0, S('lantern', hanging=False, waterlogged=False))

    # The fire pit, south of the stand: the kettle beside the coals, a log and a stone to sit on.
    t.set(3, 0, 5, S('cobblestone'))
    t.set(3, 1, 5, campfire(True, facing='east'))
    t.set(2, 0, 5, S('coarse_dirt'))
    t.set(4, 0, 5, S('coarse_dirt'))
    t.set(4, 1, 5, S('cauldron'))
    t.set(4, 1, 6, slab('or:ruin_brick_slab'))                  # a stone seat at the fire
    t.set(2, 1, 6, log('stripped_spruce_log', 'x'))             # a log seat

    # The lantern on its post (kept at 5, 3, 5).
    column(t, 5, 1, 2, 5, fence('spruce_fence'))
    t.set(*LANTERN_CELL, LANTERN)

    # The training stump with a staff leaning on it, west.
    t.set(1, 1, 5, log('oak_log'))
    t.set(1, 2, 5, log('stripped_oak_log'))
    t.set(0, 1, 5, S('lightning_rod', facing='up', powered=False, waterlogged=False))
    t.set(0, 2, 5, S('lightning_rod', facing='up', powered=False, waterlogged=False))
    t.set(1, 1, 4, S('or:debris'))                               # chips hacked off the stump

    # Her meditation focus, south-east: a kyber shard on a carved stone.
    t.set(6, 1, 5, S('or:ruin_stone_carved'))
    t.set(6, 2, 5, S('or:kyber_cluster', facing='up'))
    t.set(5, 1, 6, carpet('light_blue'))
    t.set(6, 1, 6, S('moss_carpet'))

    keep_free(t)
    plants_on(t, [c for c in cells if c[0] in (0, 6) or c[2] in (0, 6)], t.rng, 0.35)
    keep_free(t)
    check(t, SIZE, air=STAND + OLD_SPOTS, cells={LANTERN_CELL: 'lantern', (5, 2, 5): 'spruce_fence'})
    return t


def sith_camp():
    t = Template(*SIZE, seed=7102, name='story/sith_camp')
    ground(t, 7102,
           worn=[(S('gravel'), 3), (S('coarse_dirt'), 4), (S('packed_mud'), 1)],
           rim=[(S('coarse_dirt'), 4), (S('gravel'), 2), (S('grass_block', snowy=False), 2)])

    # A deck plate under the field shelter, north-west, with a red deck light at its edge.
    for x in range(3):
        for z in range(3):
            t.set(x, 0, z, S('or:durasteel_deck'))
    t.set(2, 0, 2, S('or:light_panel_red'))

    # The field shelter: dark posts, a black roof edged in crimson, a cot under it.
    for x in (0, 2):
        column(t, x, 1, 2, 2, fence('dark_oak_fence'))
        t.set(x, 1, 0, S('or:imperial_plating'))
        t.set(x, 2, 0, S('or:imperial_plating'))
    for x in range(3):
        t.set(x, 3, 0, S('or:imperial_plating_slab', type='bottom', waterlogged=False))
        t.set(x, 3, 1, S('or:imperial_plating_slab', type='bottom', waterlogged=False))
        t.set(x, 3, 2, S('or:imperial_plating_slab', type='bottom', waterlogged=False))
        t.set(x, 2, 2, S('or:canvas_crimson') if x != 1 else AIR)   # a crimson valance over the front
        t.set(x, 2, 1, AIR)
    t.set(0, 1, 1, S('black_wool'))                                 # black side curtains
    t.set(2, 2, 1, S('black_wool'))
    t.set(1, 1, 0, AIR)
    t.set(1, 2, 0, S('or:imperial_plating_slab', type='top', waterlogged=False))
    bed(t, 1, 1, 1, 'red', 'north')
    t.set(2, 1, 1, S('or:imperial_plating_slab', type='bottom', waterlogged=False))  # a stool

    # The Imperial supply crates and a field comm unit, north-east.
    t.set(6, 1, 0, S('or:cargo_crate_imperial'))
    t.set(6, 2, 0, S('or:cargo_crate_imperial'))
    t.set(6, 1, 1, S('or:cargo_crate_imperial'))
    t.set(5, 1, 0, S('or:console', facing='south'))
    t.set(5, 2, 0, S('or:holo_screen', facing='south'))
    t.set(4, 1, 0, barrel('up'))
    t.set(4, 2, 0, S('or:imperial_plating_slab', type='bottom', waterlogged=False))

    # The brazier, south of the stand: coals on a black stone bowl.
    t.set(3, 0, 5, S('polished_blackstone'))
    t.set(3, 1, 5, campfire(True, facing='west'))
    for (x, z, f) in ((2, 5, 'east'), (4, 5, 'west'), (3, 6, 'north')):
        t.set(x, 1, z, stairs('polished_blackstone_brick_stairs', f))
    t.set(3, 1, 4, S('or:scorch_mark'))

    # The lantern on a black iron post (kept at 5, 3, 5).
    t.set(5, 1, 5, wall('polished_blackstone_wall'))
    t.set(5, 2, 5, wall('polished_blackstone_wall'))
    t.set(5, 3, 5, LANTERN)
    t.set(5, 0, 5, S('polished_blackstone'))

    # The weapon rack, west: two posts and a beam, staves and a pike resting in it.
    column(t, 0, 1, 2, 5, fence('dark_oak_fence'))
    column(t, 0, 1, 2, 6, fence('dark_oak_fence'))
    t.set(0, 3, 5, log('stripped_dark_oak_log', 'z'))
    t.set(0, 3, 6, log('stripped_dark_oak_log', 'z'))
    t.set(1, 1, 5, S('lightning_rod', facing='up', powered=False, waterlogged=False))
    t.set(1, 1, 6, S('lightning_rod', facing='up', powered=False, waterlogged=False))
    t.set(1, 2, 6, S('lightning_rod', facing='up', powered=False, waterlogged=False))

    # A sparring mat of grit, south-east, and a black tomb shard he carries for his lessons.
    t.set(5, 1, 6, S('or:debris'))
    t.set(6, 1, 6, S('or:tomb_stone_carved'))
    t.set(6, 2, 6, S('or:tomb_brick_slab', type='bottom', waterlogged=False))
    t.set(6, 1, 4, S('or:debris'))

    keep_free(t)
    check(t, SIZE, air=STAND + OLD_SPOTS, cells={LANTERN_CELL: 'lantern'})
    return t


def main(argv):
    prev = preview_dir(argv)
    for build in (jedi_camp, sith_camp):
        t = build()
        write(t, t.name, prev, META, views=('se', 'nw'), plans=[1, 2])


if __name__ == '__main__':
    main(sys.argv[1:])
