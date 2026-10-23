"""Worldgen for OR's meteor craters (docs/MILESTONE_7.md 4.1): the start pool of oldrepublic:wilds/starfall_crater
with its three variants, the structure, the structure set and the has_structure biome tag. Run after the templates
are built (build_all.py runs both).

    python3 tools/assets/structures/or/worldgen_or.py

Placement: oldrepublic:clear_of_origin (the plan's choice: the mod's random spread that never starts within 256
blocks of (0, 0), so no crater opens under a new world's spawn and starfall is a short journey away), spacing 30,
separation 10, salt 960700, with the exclusion zone every overworld wilds set keeps (4 chunks from the ruins).
Biomes: the overworld's land, as the other wilds: no oceans, rivers, beaches, swamps or caves (so not the deep
dark), and not the steepest peaks and slopes, where a 40-block bowl would only cut cliffs.
"""
import sys

import common as C
from common import W

NAME = 'starfall_crater'
TEMPLATES = ('fresh', 'old', 'deep')
WEIGHTS = {'fresh': 1, 'old': 1, 'deep': 1}
BURIED = 14
SPACING, SEPARATION, SALT = 30, 10, 960700
MIN_DISTANCE = 256
BIOMES = ['minecraft:plains', 'minecraft:sunflower_plains', 'minecraft:snowy_plains', 'minecraft:ice_spikes',
          'minecraft:desert', '#minecraft:is_savanna', '#minecraft:is_forest', '#minecraft:is_taiga', '#minecraft:is_jungle',
          '#minecraft:is_badlands', '#minecraft:is_hill', 'minecraft:meadow', 'minecraft:cherry_grove']


def write(fits):
    """fits: {variant: (template height, clear inset)} from starfall_crater.build_all()."""
    starts = []
    for name in TEMPLATES:
        height, inset = fits[name]
        starts.append(('wilds/%s/%s' % (NAME, name), C.adapted(height, yard_stand=BURIED, support_depth=8, ramp_rings=2,
                                                                clear_inset=inset), WEIGHTS[name]))
    return C.wilds_family(NAME, starts, BIOMES, SPACING, SEPARATION, SALT, BURIED, placement='clear_of_origin',
                          min_distance=MIN_DISTANCE)


def main():
    import starfall_crater
    fits = {}
    for name, build in starfall_crater.VARIANTS:
        t, inset = build()
        fits[name] = (t.sy, inset)
    print('worldgen', write(fits))


if __name__ == '__main__':
    sys.exit(main())
