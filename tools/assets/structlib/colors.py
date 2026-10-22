"""Approximate block colours for the preview renderer.

Mod blocks take the average colour of their own textures. Vanilla textures are
not in the repository, so vanilla blocks use the hand-made table below, then a
few name rules (dye colours, wood types, stone families). Unknown blocks get a
stable grey-ish colour from their name so they still show up.
"""
import hashlib
import os

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..'))
MOD_TEXTURES = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'oldrepublic', 'textures', 'block')
BUILD_TEXTURES = os.path.join(MOD_TEXTURES, 'build')

DYES = {
    'white': (233, 236, 236), 'orange': (240, 118, 19), 'magenta': (189, 68, 179),
    'light_blue': (58, 175, 217), 'yellow': (248, 197, 39), 'lime': (112, 185, 25),
    'pink': (237, 141, 172), 'gray': (62, 68, 71), 'light_gray': (142, 142, 134),
    'cyan': (21, 137, 145), 'purple': (121, 42, 172), 'blue': (53, 57, 157),
    'brown': (114, 71, 40), 'green': (84, 109, 27), 'red': (160, 39, 34), 'black': (20, 21, 25),
}
TERRACOTTA = {
    'white': (209, 178, 161), 'orange': (161, 83, 37), 'magenta': (149, 88, 108),
    'light_blue': (113, 108, 137), 'yellow': (186, 133, 35), 'lime': (103, 117, 52),
    'pink': (161, 78, 78), 'gray': (57, 42, 35), 'light_gray': (135, 106, 97),
    'cyan': (86, 91, 91), 'purple': (118, 70, 86), 'blue': (74, 59, 91),
    'brown': (77, 51, 35), 'green': (76, 83, 42), 'red': (143, 61, 46), 'black': (37, 22, 16),
}
WOOD_PLANKS = {
    'oak': (162, 130, 78), 'spruce': (114, 84, 48), 'birch': (192, 175, 121),
    'jungle': (160, 115, 80), 'acacia': (168, 90, 50), 'dark_oak': (66, 43, 20),
    'mangrove': (117, 54, 48), 'cherry': (226, 178, 172), 'bamboo': (193, 173, 80),
    'crimson': (101, 48, 70), 'warped': (43, 104, 99), 'pale_oak': (226, 218, 210),
}
WOOD_LOGS = {
    'oak': (109, 85, 50), 'spruce': (58, 37, 16), 'birch': (216, 215, 210),
    'jungle': (85, 67, 25), 'acacia': (103, 96, 86), 'dark_oak': (60, 46, 26),
    'mangrove': (84, 66, 41), 'cherry': (54, 33, 44), 'bamboo': (127, 144, 58),
    'crimson': (92, 25, 29), 'warped': (58, 58, 77), 'pale_oak': (88, 81, 77),
}
LEAVES = {
    'oak': (60, 120, 30), 'spruce': (50, 90, 50), 'birch': (100, 140, 60),
    'jungle': (50, 130, 20), 'acacia': (70, 120, 25), 'dark_oak': (40, 100, 20),
    'mangrove': (70, 120, 30), 'cherry': (230, 170, 200), 'azalea': (90, 120, 40),
    'flowering_azalea': (120, 120, 80), 'pale_oak': (160, 165, 150),
}

TABLE = {
    'stone': (125, 125, 125), 'cobblestone': (122, 122, 122), 'mossy_cobblestone': (110, 118, 94),
    'stone_bricks': (122, 121, 122), 'mossy_stone_bricks': (115, 121, 105), 'cracked_stone_bricks': (118, 117, 118),
    'chiseled_stone_bricks': (119, 118, 119), 'smooth_stone': (159, 159, 159), 'granite': (149, 103, 85),
    'polished_granite': (154, 106, 89), 'diorite': (188, 188, 188), 'polished_diorite': (192, 193, 194),
    'andesite': (136, 136, 136), 'polished_andesite': (132, 134, 133), 'deepslate': (80, 80, 82),
    'cobbled_deepslate': (77, 77, 80), 'polished_deepslate': (72, 72, 73), 'deepslate_bricks': (70, 70, 71),
    'cracked_deepslate_bricks': (64, 64, 65), 'deepslate_tiles': (54, 54, 55), 'cracked_deepslate_tiles': (52, 52, 52),
    'chiseled_deepslate': (54, 54, 54), 'tuff': (108, 109, 102), 'polished_tuff': (98, 104, 99),
    'tuff_bricks': (98, 102, 95), 'chiseled_tuff': (89, 93, 87), 'chiseled_tuff_bricks': (99, 102, 95),
    'calcite': (223, 224, 220), 'dripstone_block': (134, 107, 92), 'pointed_dripstone': (129, 102, 89),
    'blackstone': (42, 36, 41), 'polished_blackstone': (53, 48, 56), 'polished_blackstone_bricks': (48, 42, 49),
    'cracked_polished_blackstone_bricks': (44, 37, 43), 'chiseled_polished_blackstone': (53, 48, 56),
    'gilded_blackstone': (56, 43, 38), 'basalt': (80, 81, 86), 'polished_basalt': (88, 88, 91),
    'smooth_basalt': (72, 72, 78), 'obsidian': (15, 10, 24), 'crying_obsidian': (32, 10, 60),
    'sandstone': (216, 203, 155), 'cut_sandstone': (217, 206, 159), 'chiseled_sandstone': (216, 202, 154),
    'smooth_sandstone': (223, 214, 170), 'red_sandstone': (186, 99, 29), 'cut_red_sandstone': (189, 101, 31),
    'chiseled_red_sandstone': (183, 96, 27), 'smooth_red_sandstone': (181, 98, 31), 'bricks': (150, 97, 83),
    'mud_bricks': (137, 104, 79), 'packed_mud': (142, 106, 79), 'mud': (60, 57, 60), 'terracotta': (152, 94, 68),
    'quartz_block': (236, 230, 223), 'smooth_quartz': (236, 230, 223), 'quartz_bricks': (234, 229, 221),
    'quartz_pillar': (235, 230, 224), 'chiseled_quartz_block': (231, 226, 218), 'prismarine': (99, 156, 151),
    'prismarine_bricks': (99, 171, 158), 'dark_prismarine': (51, 91, 75), 'end_stone': (219, 222, 158),
    'end_stone_bricks': (218, 224, 162), 'purpur_block': (169, 125, 169), 'purpur_pillar': (171, 129, 171),
    'nether_bricks': (44, 21, 26), 'red_nether_bricks': (69, 7, 9), 'cracked_nether_bricks': (40, 20, 23),
    'netherrack': (97, 38, 38), 'nether_wart_block': (114, 2, 2), 'warped_wart_block': (22, 119, 121),
    'soul_sand': (81, 62, 50), 'soul_soil': (75, 57, 46), 'magma_block': (142, 63, 31), 'glowstone': (171, 131, 84),
    'shroomlight': (240, 146, 70), 'sea_lantern': (172, 199, 190), 'redstone_lamp': (95, 54, 30),
    'ochre_froglight': (250, 245, 206), 'verdant_froglight': (229, 244, 228), 'pearlescent_froglight': (245, 240, 239),
    'iron_block': (220, 220, 220), 'gold_block': (246, 208, 61), 'diamond_block': (98, 237, 228),
    'emerald_block': (42, 203, 87), 'lapis_block': (30, 67, 140), 'redstone_block': (175, 24, 5),
    'netherite_block': (66, 61, 63), 'coal_block': (16, 15, 15), 'copper_block': (192, 107, 79),
    'exposed_copper': (161, 125, 103), 'weathered_copper': (108, 153, 110), 'oxidized_copper': (82, 162, 132),
    'cut_copper': (191, 106, 80), 'raw_iron_block': (166, 135, 107), 'amethyst_block': (133, 97, 191),
    'budding_amethyst': (132, 96, 186), 'grass_block': (95, 159, 53), 'dirt': (134, 96, 67),
    'coarse_dirt': (119, 85, 59), 'rooted_dirt': (144, 103, 76), 'podzol': (91, 63, 24), 'mycelium': (111, 99, 105),
    'dirt_path': (148, 121, 65), 'farmland': (110, 75, 46), 'sand': (219, 207, 163), 'red_sand': (190, 102, 33),
    'suspicious_sand': (215, 202, 157), 'gravel': (131, 127, 126), 'suspicious_gravel': (128, 123, 122),
    'clay': (160, 166, 179), 'snow_block': (249, 254, 254), 'snow': (249, 254, 254), 'powder_snow': (248, 253, 253),
    'ice': (145, 183, 253), 'packed_ice': (141, 180, 250), 'blue_ice': (116, 167, 253), 'water': (52, 94, 200),
    'lava': (207, 92, 20), 'moss_block': (89, 109, 45), 'moss_carpet': (89, 109, 45), 'pale_moss_block': (106, 115, 104),
    'pale_moss_carpet': (106, 115, 104), 'hay_block': (166, 139, 12), 'bone_block': (229, 225, 207),
    'dried_kelp_block': (50, 58, 38), 'sponge': (195, 192, 74), 'wet_sponge': (171, 181, 70),
    'mushroom_stem': (203, 196, 185), 'brown_mushroom_block': (149, 111, 81), 'red_mushroom_block': (200, 46, 45),
    'bookshelf': (117, 94, 59), 'chiseled_bookshelf': (130, 104, 66), 'crafting_table': (119, 73, 42),
    'furnace': (110, 110, 110), 'blast_furnace': (108, 108, 108), 'smoker': (107, 87, 66), 'barrel': (134, 100, 58),
    'chest': (163, 116, 46), 'trapped_chest': (163, 116, 46), 'ender_chest': (40, 60, 60), 'lectern': (173, 137, 83),
    'cartography_table': (103, 87, 67), 'fletching_table': (197, 180, 133), 'smithing_table': (57, 58, 70),
    'loom': (142, 119, 91), 'stonecutter': (123, 118, 111), 'grindstone': (142, 142, 142), 'anvil': (68, 68, 68),
    'chipped_anvil': (68, 68, 68), 'damaged_anvil': (68, 68, 68), 'cauldron': (74, 73, 74), 'composter': (117, 75, 41),
    'beehive': (182, 146, 94), 'bee_nest': (202, 160, 74), 'jukebox': (94, 64, 47), 'note_block': (88, 58, 40),
    'target': (229, 176, 168), 'tnt': (182, 67, 58), 'scaffolding': (174, 134, 80), 'ladder': (160, 125, 75),
    'iron_bars': (136, 139, 135), 'chain': (60, 65, 80), 'iron_chain': (60, 65, 80), 'lightning_rod': (198, 108, 82),
    'lantern': (106, 91, 83), 'soul_lantern': (71, 99, 114), 'torch': (255, 200, 90), 'wall_torch': (255, 200, 90),
    'soul_torch': (110, 220, 230), 'soul_wall_torch': (110, 220, 230), 'redstone_torch': (200, 40, 20),
    'redstone_wall_torch': (200, 40, 20), 'campfire': (110, 85, 55), 'soul_campfire': (80, 110, 120),
    'end_rod': (230, 225, 215), 'candle': (233, 223, 190), 'cobweb': (228, 233, 234), 'vine': (60, 110, 30),
    'glow_lichen': (112, 131, 121), 'sculk': (12, 30, 36), 'sculk_vein': (8, 48, 60), 'sculk_sensor': (7, 70, 84),
    'sculk_catalyst': (15, 32, 38), 'sculk_shrieker': (198, 205, 169), 'reinforced_deepslate': (80, 83, 79),
    'lodestone': (147, 149, 152), 'respawn_anchor': (70, 30, 120), 'beacon': (117, 220, 215), 'conduit': (160, 139, 113),
    'spawner': (36, 46, 62), 'trial_spawner': (49, 62, 76), 'vault': (54, 69, 79), 'heavy_core': (82, 86, 94),
    'decorated_pot': (140, 80, 60), 'flower_pot': (124, 68, 53), 'skeleton_skull': (200, 200, 200),
    'skeleton_wall_skull': (200, 200, 200), 'wither_skeleton_skull': (40, 40, 40), 'player_head': (100, 70, 50),
    'tripwire_hook': (140, 120, 90), 'lever': (110, 95, 70), 'stone_button': (125, 125, 125), 'glass': (200, 225, 235),
    'tinted_glass': (44, 38, 46), 'glass_pane': (200, 225, 235), 'short_grass': (90, 150, 50), 'grass': (90, 150, 50),
    'tall_grass': (90, 150, 50), 'fern': (80, 130, 50), 'large_fern': (80, 130, 50), 'dead_bush': (120, 90, 50),
    'bush': (80, 130, 45), 'firefly_bush': (80, 120, 45), 'short_dry_grass': (190, 170, 100), 'tall_dry_grass': (190, 170, 100),
    'leaf_litter': (150, 110, 60), 'sweet_berry_bush': (60, 100, 40), 'cactus': (85, 127, 43),
    'sugar_cane': (148, 192, 101), 'bamboo': (90, 140, 40), 'lily_pad': (40, 120, 40), 'big_dripleaf': (110, 140, 50),
    'small_dripleaf': (110, 140, 50), 'hanging_roots': (160, 110, 90), 'spore_blossom': (210, 110, 160),
    'azalea': (100, 125, 50), 'flowering_azalea': (120, 120, 80), 'brown_mushroom': (150, 110, 80),
    'red_mushroom': (210, 50, 50), 'crimson_fungus': (140, 30, 30), 'warped_fungus': (30, 130, 120),
    'dandelion': (240, 220, 30), 'poppy': (200, 30, 20), 'blue_orchid': (50, 160, 220), 'allium': (180, 110, 220),
    'azure_bluet': (220, 225, 230), 'red_tulip': (200, 50, 30), 'orange_tulip': (230, 120, 30),
    'white_tulip': (230, 230, 230), 'pink_tulip': (230, 160, 200), 'oxeye_daisy': (230, 230, 200),
    'cornflower': (70, 100, 220), 'lily_of_the_valley': (230, 230, 230), 'wither_rose': (30, 30, 30),
    'sunflower': (240, 200, 30), 'lilac': (200, 150, 200), 'rose_bush': (180, 40, 40), 'peony': (230, 170, 220),
    'pink_petals': (240, 170, 200), 'wildflowers': (230, 210, 120), 'torchflower': (220, 120, 50),
    'pitcher_plant': (110, 140, 200), 'seagrass': (40, 110, 40), 'kelp': (60, 120, 40), 'sea_pickle': (100, 120, 40),
    'iron_door': (200, 200, 200), 'iron_trapdoor': (200, 200, 200), 'heavy_weighted_pressure_plate': (220, 220, 220),
    'light_weighted_pressure_plate': (246, 208, 61), 'rail': (130, 110, 90), 'powered_rail': (150, 120, 60),
    'detector_rail': (130, 100, 90), 'activator_rail': (130, 90, 80), 'hopper': (70, 70, 70), 'dispenser': (110, 110, 110),
    'dropper': (110, 110, 110), 'observer': (90, 90, 90), 'piston': (150, 125, 90), 'sticky_piston': (130, 140, 90),
    'daylight_detector': (120, 100, 70), 'crafter': (110, 100, 90), 'brewing_stand': (120, 100, 80),
    'enchanting_table': (60, 30, 40), 'bell': (230, 190, 70), 'cake': (230, 220, 210), 'copper_bulb': (200, 120, 80),
    'copper_grate': (190, 110, 80), 'oxidized_copper_grate': (80, 160, 130), 'weathered_copper_grate': (110, 150, 110),
    'exposed_copper_grate': (160, 125, 100), 'cobbled_deepslate_wall': (77, 77, 80), 'structure_block': (90, 70, 90),
    'jigsaw': (90, 70, 90), 'barrier': (220, 40, 40), 'light': (255, 255, 200), 'creaking_heart': (90, 80, 70),
    'resin_block': (217, 99, 25), 'resin_bricks': (206, 90, 30), 'chiseled_resin_bricks': (200, 90, 30),
    'resin_clump': (217, 99, 25), 'dried_ghast': (180, 170, 170), 'copper_lantern': (180, 110, 80),
    'copper_torch': (160, 230, 140), 'copper_wall_torch': (160, 230, 140), 'copper_chain': (180, 100, 70),
    'copper_bars': (190, 110, 80), 'copper_chest': (190, 110, 80), 'shelf': (150, 115, 70),
}

ALPHA = {
    'glass': 110, 'glass_pane': 110, 'water': 150, 'ice': 180, 'tinted_glass': 200,
    'cobweb': 120, 'barrier': 0, 'light': 0, 'structure_void': 0,
}

STONE_FAMILIES = [
    ('cobbled_deepslate', 'cobbled_deepslate'), ('polished_deepslate', 'polished_deepslate'),
    ('deepslate_brick', 'deepslate_bricks'), ('deepslate_tile', 'deepslate_tiles'),
    ('polished_blackstone_brick', 'polished_blackstone_bricks'), ('polished_blackstone', 'polished_blackstone'),
    ('blackstone', 'blackstone'), ('mossy_stone_brick', 'mossy_stone_bricks'), ('stone_brick', 'stone_bricks'),
    ('mossy_cobblestone', 'mossy_cobblestone'), ('cobblestone', 'cobblestone'), ('smooth_stone', 'smooth_stone'),
    ('polished_granite', 'polished_granite'), ('granite', 'granite'), ('polished_diorite', 'polished_diorite'),
    ('diorite', 'diorite'), ('polished_andesite', 'polished_andesite'), ('andesite', 'andesite'),
    ('tuff_brick', 'tuff_bricks'), ('polished_tuff', 'polished_tuff'), ('tuff', 'tuff'),
    ('smooth_red_sandstone', 'smooth_red_sandstone'), ('cut_red_sandstone', 'cut_red_sandstone'),
    ('red_sandstone', 'red_sandstone'), ('smooth_sandstone', 'smooth_sandstone'), ('cut_sandstone', 'cut_sandstone'),
    ('sandstone', 'sandstone'), ('mud_brick', 'mud_bricks'), ('red_nether_brick', 'red_nether_bricks'),
    ('nether_brick', 'nether_bricks'), ('end_stone_brick', 'end_stone_bricks'), ('smooth_quartz', 'smooth_quartz'),
    ('quartz', 'quartz_block'), ('purpur', 'purpur_block'), ('dark_prismarine', 'dark_prismarine'),
    ('prismarine_brick', 'prismarine_bricks'),
    ('prismarine', 'prismarine'), ('brick', 'bricks'), ('stone', 'stone'), ('resin_brick', 'resin_bricks'),
    ('cut_copper', 'cut_copper'), ('oxidized_cut_copper', 'oxidized_copper'), ('weathered_cut_copper', 'weathered_copper'),
    ('exposed_cut_copper', 'exposed_copper'),
]

_cache = {}


def _avg_png(path):
    try:
        from PIL import Image
    except ImportError:
        return None
    try:
        im = Image.open(path).convert('RGBA')
    except OSError:
        return None
    w, h = im.size
    if h > w:  # animated strip: first frame only
        im = im.crop((0, 0, w, w))
    px = [p for p in im.getdata() if p[3] > 20]
    if not px:
        return None
    n = len(px)
    return (sum(p[0] for p in px) // n, sum(p[1] for p in px) // n, sum(p[2] for p in px) // n)


def _mod_color(name, face):
    bases = [name]
    for suffix in ('_stairs', '_slab', '_wall', '_pillar', '_fence_gate', '_fence', '_door', '_trapdoor',
                   '_pane', '_button', '_pressure_plate'):
        if name.endswith(suffix):
            b = name[: -len(suffix)]
            bases += [b, b + 's']
            break
    for base in bases:
        candidates = []
        if face == 'top':
            candidates += [base + '_top', base + '_top_0', base + '_end']
        candidates += [base, base + '_side', base + '_front', base + '_0', base + '_side_0', base + '_body', base + '_post']
        for folder in (BUILD_TEXTURES, MOD_TEXTURES):
            for c in candidates:
                p = os.path.join(folder, c + '.png')
                if os.path.exists(p):
                    col = _avg_png(p)
                    if col:
                        return col
    # any texture starting with the base name
    base = bases[-2] if len(bases) > 1 else bases[0]
    for folder in (BUILD_TEXTURES, MOD_TEXTURES):
        try:
            for fn in sorted(os.listdir(folder)):
                if fn.startswith(base) and fn.endswith('.png'):
                    col = _avg_png(os.path.join(folder, fn))
                    if col:
                        return col
        except OSError:
            pass
    return None


def _hash_color(name):
    h = hashlib.md5(name.encode()).digest()
    return (90 + h[0] % 80, 90 + h[1] % 80, 90 + h[2] % 80)


def _vanilla_color(name, face):
    if name in TABLE:
        c = TABLE[name]
        if name == 'grass_block' and face != 'top':
            return (134, 96, 67)
        return c
    for dye, col in sorted(DYES.items(), key=lambda kv: -len(kv[0])):
        if name.startswith(dye + '_'):
            rest = name[len(dye) + 1:]
            if rest.startswith('terracotta') or rest.startswith('glazed_terracotta'):
                return TERRACOTTA[dye]
            if rest.startswith('concrete_powder'):
                return tuple(min(255, int(v * 1.1 + 15)) for v in col)
            if 'stained_glass' in rest:
                return col
            return col
    for wood, col in sorted(WOOD_PLANKS.items(), key=lambda kv: -len(kv[0])):
        if name.startswith(wood + '_') or name.startswith('stripped_' + wood + '_'):
            rest = name.split(wood + '_', 1)[1]
            if rest.startswith('log') or rest.startswith('wood') or rest.startswith('stem') or rest.startswith('hyphae'):
                if name.startswith('stripped_'):
                    return col
                if face == 'top' and rest.startswith('log'):
                    return col
                return WOOD_LOGS[wood]
            if rest.startswith('leaves'):
                return LEAVES.get(wood, (60, 120, 30))
            if rest.startswith('sapling') or rest.startswith('propagule'):
                return LEAVES.get(wood, (60, 120, 30))
            return col
    if name.endswith('_leaves'):
        return LEAVES.get(name[: -len('_leaves')], (60, 120, 30))
    for prefix, fam in STONE_FAMILIES:
        if name.startswith(prefix) or name.startswith('waxed_' + prefix):
            return TABLE.get(fam, (125, 125, 125))
    if name.endswith('_carpet'):
        return _vanilla_color(name[: -len('_carpet')] + '_wool', face)
    if 'copper' in name:
        if 'oxidized' in name:
            return TABLE['oxidized_copper']
        if 'weathered' in name:
            return TABLE['weathered_copper']
        if 'exposed' in name:
            return TABLE['exposed_copper']
        return TABLE['copper_block']
    if name.endswith('_ore'):
        return (120, 120, 120) if not name.startswith('deepslate') else (80, 80, 82)
    if name.endswith('_candle'):
        return TABLE['candle']
    if 'coral' in name:
        return (200, 90, 120)
    if name.endswith('_head') or name.endswith('_skull'):
        return (190, 190, 190)
    if name.endswith('_bed'):
        return (160, 39, 34)
    if name.endswith('_banner'):
        return (200, 200, 200)
    if name.endswith('_sign') or name.endswith('_hanging_sign'):
        return (160, 130, 80)
    if name.startswith('potted_'):
        return TABLE['flower_pot']
    if name.startswith('infested_'):
        return _vanilla_color(name[len('infested_'):], face)
    return _hash_color(name)


def color(block_id, face='side'):
    """Return (r, g, b, a) for a namespaced block id and a face ('top' or 'side')."""
    key = (block_id, face)
    if key in _cache:
        return _cache[key]
    ns, _, name = block_id.partition(':')
    if not name:
        ns, name = 'minecraft', ns
    rgb = None
    if ns == 'oldrepublic':
        rgb = _mod_color(name, face)
    if rgb is None:
        rgb = _vanilla_color(name, face)
    a = ALPHA.get(name, 255)
    if name.endswith('stained_glass') or name.endswith('stained_glass_pane'):
        a = 140
    result = (rgb[0], rgb[1], rgb[2], a)
    _cache[key] = result
    return result
