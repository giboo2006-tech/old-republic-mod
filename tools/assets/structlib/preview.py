"""Render structure templates to PNG previews so a design can be checked by eye.

Usage:
    python3 tools/assets/structlib/preview.py <template.nbt> [more.nbt ...] -o out.png
        [--views se,nw] [--plans auto|1,5,9] [--cut Y] [--ground Y] [--width 1600]

Each template becomes one sheet: isometric views from two opposite corners,
optional cutaway views and top-down floor plans. Mod blocks take their real
shape from their block models and their colour from their textures; vanilla
blocks use approximate shapes and colours (see colors.py). Data blocks that are
invisible in game (site and hub spawners, structure void) show as small magenta
markers so their placement can be checked.

Render from Python with render_template(template_dict, path) or sheet(...).
"""
import argparse
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import colors  # noqa: E402
import nbtio  # noqa: E402

try:
    from PIL import Image, ImageDraw, ImageFont
except ImportError:  # pragma: no cover
    print('preview.py needs Pillow (python3 -m pip install pillow)', file=sys.stderr)
    raise

ASSETS = os.path.join(colors.ROOT, 'src', 'main', 'resources', 'assets', 'oldrepublic')
SKIP = {'minecraft:air', 'minecraft:cave_air', 'minecraft:void_air', 'minecraft:structure_void',
        'minecraft:barrier', 'minecraft:light'}
MARKERS = {'oldrepublic:site_spawner', 'oldrepublic:hub_spawner'}
FULL = [(0, 0, 0, 1, 1, 1)]

PLANTS = ('short_grass', 'grass', 'tall_grass', 'fern', 'large_fern', 'dead_bush', 'bush', 'firefly_bush',
          'short_dry_grass', 'tall_dry_grass', 'sweet_berry_bush', 'dandelion', 'poppy', 'blue_orchid', 'allium',
          'azure_bluet', 'red_tulip', 'orange_tulip', 'white_tulip', 'pink_tulip', 'oxeye_daisy', 'cornflower',
          'lily_of_the_valley', 'wither_rose', 'sunflower', 'lilac', 'rose_bush', 'peony', 'torchflower',
          'pitcher_plant', 'seagrass', 'tall_seagrass', 'kelp', 'kelp_plant', 'brown_mushroom', 'red_mushroom',
          'crimson_fungus', 'warped_fungus', 'crimson_roots', 'warped_roots', 'nether_sprouts', 'hanging_roots',
          'spore_blossom', 'sugar_cane', 'azalea', 'flowering_azalea', 'big_dripleaf', 'big_dripleaf_stem',
          'small_dripleaf', 'cave_vines', 'cave_vines_plant', 'weeping_vines', 'twisting_vines',
          'weeping_vines_plant', 'twisting_vines_plant', 'pointed_dripstone', 'amethyst_cluster',
          'large_amethyst_bud', 'medium_amethyst_bud', 'small_amethyst_bud', 'cobweb', 'wheat', 'carrots',
          'potatoes', 'beetroots', 'nether_wart', 'cactus_flower', 'open_eyeblossom', 'closed_eyeblossom')
FLAT = ('rail', 'powered_rail', 'detector_rail', 'activator_rail', 'lily_pad', 'leaf_litter', 'pink_petals',
        'wildflowers', 'redstone_wire', 'tripwire', 'moss_carpet', 'pale_moss_carpet', 'glow_lichen', 'sculk_vein',
        'resin_clump')


# ---------------------------------------------------------------- shapes

def _rot_box_y(box, quarter):
    """Rotate a unit-cell box clockwise (seen from above) by quarter * 90 degrees around the cell centre."""
    x0, y0, z0, x1, y1, z1 = box
    for _ in range(quarter % 4):
        x0, z0, x1, z1 = 1 - z1, x0, 1 - z0, x1
    return (min(x0, x1), y0, min(z0, z1), max(x0, x1), y1, max(z0, z1))


def _facing_quarter(facing):
    # boxes below are written for facing north; turn them to the given facing
    return {'north': 0, 'east': 1, 'south': 2, 'west': 3}.get(facing, 0)


def _rot_x(box, quarter):
    """Rotate around the x axis (for models with an x rotation), quarter * 90 degrees."""
    x0, y0, z0, x1, y1, z1 = box
    for _ in range(quarter % 4):
        y0, z0, y1, z1 = 1 - z1, y0, 1 - z0, y1
    return (x0, min(y0, y1), min(z0, z1), x1, max(y0, y1), max(z0, z1))


_model_cache = {}


def _load_json(path):
    try:
        with open(path) as f:
            return json.load(f)
    except (OSError, ValueError):
        return None


def _model_boxes(model_ref, depth=0):
    """Boxes of a mod block model, following parents. None when the shape is unknown."""
    if depth > 6 or not model_ref:
        return None
    if model_ref in _model_cache:
        return _model_cache[model_ref]
    ns, _, path = model_ref.partition(':')
    if not path:
        ns, path = 'minecraft', ns
    result = None
    if ns == 'minecraft':
        p = path.split('/')[-1]
        if p in ('cube_all', 'cube', 'cube_column', 'cube_bottom_top', 'orientable', 'cube_column_horizontal',
                 'leaves', 'cube_directional', 'cube_mirrored_all', 'orientable_with_bottom', 'block'):
            result = FULL
        elif p in ('cross', 'tinted_cross', 'flower_pot_cross'):
            result = [(0.2, 0, 0.2, 0.8, 0.85, 0.8)]
        elif p == 'slab':
            result = [(0, 0, 0, 1, 0.5, 1)]
        elif p == 'slab_top':
            result = [(0, 0.5, 0, 1, 1, 1)]
        elif p == 'carpet':
            result = [(0, 0, 0, 1, 0.0625, 1)]
        elif p.startswith('template_torch'):
            result = [(0.4375, 0, 0.4375, 0.5625, 0.625, 0.5625)]
    else:
        m = _load_json(os.path.join(colors.ROOT, 'src', 'main', 'resources', 'assets', ns, 'models', path + '.json'))
        if m is not None:
            if 'elements' in m:
                result = []
                for e in m['elements']:
                    f, t = e.get('from', [0, 0, 0]), e.get('to', [16, 16, 16])
                    result.append((min(f[0], t[0]) / 16, min(f[1], t[1]) / 16, min(f[2], t[2]) / 16,
                                   max(f[0], t[0]) / 16, max(f[1], t[1]) / 16, max(f[2], t[2]) / 16))
            elif 'parent' in m:
                result = _model_boxes(m['parent'], depth + 1)
    _model_cache[model_ref] = result
    return result


def _matches(when, props):
    if 'OR' in when:
        return any(_matches(w, props) for w in when['OR'])
    if 'AND' in when:
        return all(_matches(w, props) for w in when['AND'])
    for k, v in when.items():
        if str(props.get(k, '')) not in str(v).split('|'):
            return False
    return True


def _mod_shape(name, props):
    bs = _load_json(os.path.join(ASSETS, 'blockstates', name + '.json'))
    if bs is None:
        return None
    parts = []
    if 'variants' in bs:
        chosen = None
        for key, var in bs['variants'].items():
            if key == '':
                chosen = var
                break
            conds = dict(kv.split('=') for kv in key.split(','))
            if all(str(props.get(k, '')) == v for k, v in conds.items()):
                chosen = var
                break
        if chosen is None:
            chosen = next(iter(bs['variants'].values()))
        parts.append(chosen[0] if isinstance(chosen, list) else chosen)
    elif 'multipart' in bs:
        for part in bs['multipart']:
            if 'when' not in part or _matches(part['when'], props):
                a = part['apply']
                parts.append(a[0] if isinstance(a, list) else a)
    boxes = []
    for part in parts:
        b = _model_boxes(part.get('model'))
        if b is None:
            return None
        qx = int(part.get('x', 0)) // 90
        qy = int(part.get('y', 0)) // 90
        for box in b:
            box = _rot_x(box, qx) if qx else box
            boxes.append(_rot_box_y(box, qy) if qy else box)
    return boxes or None


def shape(block_id, props):
    """Return a list of boxes (x0, y0, z0, x1, y1, z1) inside the unit cell."""
    ns, _, name = block_id.partition(':')
    if ns == 'oldrepublic':
        s = _mod_shape(name, props)
        if s is not None:
            return s
    facing = props.get('facing', 'north')
    q = _facing_quarter(facing)
    if name.endswith('_slab'):
        t = props.get('type', 'bottom')
        return FULL if t == 'double' else [(0, 0.5, 0, 1, 1, 1)] if t == 'top' else [(0, 0, 0, 1, 0.5, 1)]
    if name.endswith('_stairs'):
        top = props.get('half', 'bottom') == 'top'
        base = (0, 0.5, 0, 1, 1, 1) if top else (0, 0, 0, 1, 0.5, 1)
        step = (0, 0, 0, 1, 0.5, 0.5) if top else (0, 0.5, 0, 1, 1, 0.5)
        return [base, _rot_box_y(step, q)]
    if name.endswith('_wall') and not any(name.endswith(s) for s in ('_wall_torch', '_wall_sign', '_wall_banner',
                                                                      '_wall_head', '_wall_skull', '_wall_fan',
                                                                      '_wall_hanging_sign')):
        boxes = [(0.25, 0, 0.25, 0.75, 1, 0.75)]
        for d, qq in (('north', 0), ('east', 1), ('south', 2), ('west', 3)):
            v = props.get(d, 'none')
            if v not in ('none', 'false'):
                h = 1 if v == 'tall' else 0.875
                boxes.append(_rot_box_y((0.3125, 0, 0, 0.6875, h, 0.5), qq))
        return boxes
    if name.endswith('_fence'):
        boxes = [(0.375, 0, 0.375, 0.625, 1, 0.625)]
        for d, qq in (('north', 0), ('east', 1), ('south', 2), ('west', 3)):
            if props.get(d) == 'true':
                boxes.append(_rot_box_y((0.4375, 0.375, 0, 0.5625, 0.9375, 0.5), qq))
        return boxes
    if name.endswith('_fence_gate'):
        return [_rot_box_y((0, 0.3125, 0.4375, 1, 1, 0.5625), q)]
    if name.endswith('_pane') or name in ('iron_bars', 'copper_bars') or name.endswith('_bars'):
        boxes = [(0.4375, 0, 0.4375, 0.5625, 1, 0.5625)]
        for d, qq in (('north', 0), ('east', 1), ('south', 2), ('west', 3)):
            if props.get(d) == 'true':
                boxes.append(_rot_box_y((0.4375, 0, 0, 0.5625, 1, 0.5), qq))
        return boxes
    if name in ('chain', 'iron_chain', 'copper_chain', 'end_rod', 'lightning_rod') or name.endswith('_chain'):
        axis = props.get('axis') or {'up': 'y', 'down': 'y', 'north': 'z', 'south': 'z'}.get(facing, 'x')
        if axis == 'x':
            return [(0, 0.4, 0.4, 1, 0.6, 0.6)]
        if axis == 'z':
            return [(0.4, 0.4, 0, 0.6, 0.6, 1)]
        return [(0.4, 0, 0.4, 0.6, 1, 0.6)]
    if name.endswith('_carpet') or name in FLAT:
        return [(0, 0, 0, 1, 0.0625, 1)]
    if name.endswith('_pressure_plate'):
        return [(0.0625, 0, 0.0625, 0.9375, 0.0625, 0.9375)]
    if name == 'snow':
        return [(0, 0, 0, 1, int(props.get('layers', 1)) / 8, 1)]
    if name.endswith('_trapdoor'):
        if props.get('open') == 'true':
            return [_rot_box_y((0, 0, 0.8125, 1, 1, 1), q)]
        return [(0, 0.8125, 0, 1, 1, 1)] if props.get('half') == 'top' else [(0, 0, 0, 1, 0.1875, 1)]
    if name.endswith('_door'):
        return [_rot_box_y((0, 0, 0.8125, 1, 1, 1), q)]
    if name == 'ladder' or name.endswith('_wall_sign') or name in ('vine',):
        return [_rot_box_y((0, 0, 0.875, 1, 1, 1), q)]
    if 'torch' in name:
        return [(0.4375, 0, 0.4375, 0.5625, 0.625, 0.5625)]
    if name.endswith('lantern') and 'sea' not in name:
        if props.get('hanging') == 'true':
            return [(0.3125, 0.0625, 0.3125, 0.6875, 0.625, 0.6875), (0.45, 0.625, 0.45, 0.55, 1, 0.55)]
        return [(0.3125, 0, 0.3125, 0.6875, 0.5625, 0.6875)]
    if name.endswith('campfire'):
        return [(0, 0, 0, 1, 0.4375, 1)]
    if name.endswith('candle') or name.endswith('_button') or name in ('lever', 'tripwire_hook', 'sea_pickle'):
        return [(0.375, 0, 0.375, 0.625, 0.4, 0.625)]
    if name.endswith('_sign') and 'hanging' not in name:
        return [(0.25, 0, 0.4375, 0.75, 1, 0.5625)]
    if name.endswith('_hanging_sign'):
        return [(0.0625, 0, 0.4375, 0.9375, 0.625, 0.5625)]
    if name.endswith('_skull') or name.endswith('_head'):
        return [(0.25, 0, 0.25, 0.75, 0.5, 0.75)]
    if name == 'flower_pot' or name.startswith('potted_'):
        return [(0.3125, 0, 0.3125, 0.6875, 0.375, 0.6875), (0.35, 0.375, 0.35, 0.65, 0.8, 0.65)]
    if name.endswith('_bed'):
        return [(0, 0, 0, 1, 0.5625, 1)]
    if name in ('chest', 'trapped_chest', 'ender_chest', 'copper_chest') or name.endswith('_chest'):
        return [(0.0625, 0, 0.0625, 0.9375, 0.875, 0.9375)]
    if name.endswith('_wall_banner'):
        return [_rot_box_y((0.0625, -0.8, 0.875, 0.9375, 0.9, 1), q)]
    if name.endswith('_banner'):
        return [(0.45, 0, 0.45, 0.55, 1.9, 0.55), (0.0625, 0.8, 0.45, 0.9375, 1.9, 0.55)]
    if name in ('anvil', 'chipped_anvil', 'damaged_anvil'):
        return [_rot_box_y((0.125, 0, 0.1875, 0.875, 1, 0.8125), q)]
    if name in ('enchanting_table',):
        return [(0, 0, 0, 1, 0.75, 1)]
    if name in ('decorated_pot',):
        return [(0.0625, 0, 0.0625, 0.9375, 1, 0.9375)]
    if name in ('bell', 'brewing_stand', 'conduit', 'heavy_core'):
        return [(0.25, 0, 0.25, 0.75, 0.75, 0.75)]
    if name in PLANTS or name.endswith('_sapling') or name.endswith('_propagule') or name.endswith('_bud') \
            or name.endswith('_cluster') or name.endswith('_coral') or name.endswith('_coral_fan') \
            or name.endswith('_roots') or name.endswith('_fungus') or name.endswith('_mushroom'):
        return [(0.2, 0, 0.2, 0.8, 0.85, 0.8)]
    if name.endswith('_cluster') or name.endswith('_bud'):
        return [(0.25, 0, 0.25, 0.75, 0.6, 0.75)]
    return FULL


def is_full(boxes):
    return len(boxes) == 1 and boxes[0] == (0, 0, 0, 1, 1, 1)


# ---------------------------------------------------------------- grid

class Grid:
    """A dense view of a template: cells[(x, y, z)] = (block_id, props)."""

    def __init__(self, size, cells, entities=()):
        self.size = tuple(size)
        self.cells = cells
        self.entities = list(entities)

    @classmethod
    def from_template(cls, t):
        pal = t.get('palette') or (t.get('palettes') or [[]])[0]
        cells = {}
        for b in t.get('blocks', []):
            st = pal[b['state']]
            name = st.get('Name', 'minecraft:air')
            props = {k: str(v) for k, v in (st.get('Properties') or {}).items()}
            if name == 'minecraft:jigsaw':
                fs = (b.get('nbt') or {}).get('final_state', 'minecraft:air')
                name, props = _parse_state(fs)
            if name in SKIP:
                continue
            cells[tuple(b['pos'])] = (name, props)
        ents = [e.get('pos') for e in t.get('entities', [])]
        return cls(t['size'], cells, ents)

    def rotated(self, quarter):
        """Rotate clockwise seen from above so the camera at +x,+z sees a different corner."""
        sx, sy, sz = self.size
        cells, size = self.cells, (sx, sy, sz)
        for _ in range(quarter % 4):
            sx, sy, sz = size
            new = {}
            for (x, y, z), v in cells.items():
                new[(sz - 1 - z, y, x)] = v
            cells, size = new, (sz, sy, sx)
        g = Grid(size, cells)
        g.quarter = quarter % 4
        return g


def _parse_state(s):
    name, props = s, {}
    if '[' in s:
        name, rest = s.split('[', 1)
        rest = rest.rstrip(']')
        for kv in rest.split(','):
            if '=' in kv:
                k, v = kv.split('=', 1)
                props[k.strip()] = v.strip()
    if ':' not in name:
        name = 'minecraft:' + name
    return name, props


# ---------------------------------------------------------------- iso render

def _shade(c, f):
    return (max(0, min(255, int(c[0] * f))), max(0, min(255, int(c[1] * f))),
            max(0, min(255, int(c[2] * f))), c[3])


def render_iso(grid, quarter=0, tile=None, max_width=1600, cut=None, ground=None, label=None):
    g = grid.rotated(quarter) if quarter else grid
    q = quarter % 4
    sx, sy, sz = g.size
    if tile is None:
        tile = max(3.0, min(18.0, max_width / ((sx + sz) * 0.866 + 2)))
    ss = 2  # supersampling
    t = tile * ss
    ax, ay = 0.866 * t, 0.5 * t
    vy = 1.0 * t

    def proj(x, y, z):
        return ((x - z) * ax, (x + z) * ay - y * vy)

    xs = [proj(x, y, z)[0] for x in (0, sx) for y in (0, sy) for z in (0, sz)]
    ys = [proj(x, y, z)[1] for x in (0, sx) for y in (0, sy + 2) for z in (0, sz)]
    minx, maxx, miny, maxy = min(xs), max(xs), min(ys), max(ys)
    pad = 12 * ss
    top_pad = (28 if label else 8) * ss
    w = int(maxx - minx + 2 * pad)
    h = int(maxy - miny + pad + top_pad)
    img = Image.new('RGBA', (w, h), (214, 222, 230, 255))
    d = ImageDraw.Draw(img, 'RGBA')
    ox, oy = pad - minx, top_pad - miny

    def P(x, y, z):
        px, py = proj(x, y, z)
        return (px + ox, py + oy)

    if ground is not None:
        poly = [P(0, ground, 0), P(sx, ground, 0), P(sx, ground, sz), P(0, ground, sz)]
        d.polygon(poly, fill=(150, 160, 150, 90), outline=(120, 130, 120, 160))

    cells = g.cells
    full_cache = {}

    def full_at(p):
        v = cells.get(p)
        if v is None:
            return False
        key = (v[0], tuple(sorted(v[1].items())))
        r = full_cache.get(key)
        if r is None:
            boxes = shape(v[0], _unrotate_props(v[1], q))
            r = is_full(boxes) and colors.color(v[0])[3] == 255 and v[0] not in MARKERS
            full_cache[key] = r
        return r

    items = []
    for (x, y, z), (name, props) in cells.items():
        if cut is not None and y > cut:
            continue
        p0 = _unrotate_props(props, q)
        boxes = shape(name, p0)
        if is_full(boxes) and name not in MARKERS:
            if full_at((x + 1, y, z)) and full_at((x, y, z + 1)) and (full_at((x, y + 1, z)) and (cut is None or y < cut)):
                continue
        if name in MARKERS:
            boxes = [(0.3, 0.3, 0.3, 0.7, 0.7, 0.7)]
        for b in boxes:
            bx = _rot_box_y(b, q) if q else b
            items.append((x + y + z + (bx[0] + bx[1] + bx[2]) / 3.0, x, y, z, bx, name))
    items.sort(key=lambda it: it[0])
    for _, x, y, z, b, name in items:
        x0, y0, z0, x1, y1, z1 = x + b[0], y + b[1], z + b[2], x + b[3], y + b[4], z + b[5]
        if name in MARKERS:
            ctop = cside = (230, 40, 200, 255)
        else:
            ctop = colors.color(name, 'top')
            cside = colors.color(name, 'side')
        if ctop[3] == 0:
            continue
        # height tint: a little darker low down so levels read apart
        k = 0.82 + 0.18 * (y / max(1, sy - 1))
        faces = (
            ([P(x0, y1, z0), P(x1, y1, z0), P(x1, y1, z1), P(x0, y1, z1)], _shade(ctop, 1.0 * k)),
            ([P(x1, y0, z0), P(x1, y1, z0), P(x1, y1, z1), P(x1, y0, z1)], _shade(cside, 0.80 * k)),
            ([P(x0, y0, z1), P(x1, y0, z1), P(x1, y1, z1), P(x0, y1, z1)], _shade(cside, 0.64 * k)),
        )
        for poly, col in faces:
            edge = _shade(col, 0.82)
            d.polygon(poly, fill=col, outline=edge if tile >= 6 else None)
    for e in grid.entities if not quarter else []:
        if e:
            px, py = P(e[0], e[1], e[2])
            r = max(2, t * 0.25)
            d.ellipse((px - r, py - r, px + r, py + r), fill=(255, 220, 0, 255), outline=(0, 0, 0, 255))
    img = img.resize((w // ss, h // ss), Image.LANCZOS)
    if label:
        dd = ImageDraw.Draw(img)
        dd.text((8, 6), label, fill=(20, 24, 30, 255), font=_font(14))
    return img


def _unrotate_props(props, q):
    """Shapes are computed from the template's own facing; rotation is applied to the boxes after."""
    return props


_fonts = {}


def _font(size):
    if size not in _fonts:
        try:
            _fonts[size] = ImageFont.truetype('DejaVuSans.ttf', size)
        except OSError:
            try:
                _fonts[size] = ImageFont.load_default(size)
            except TypeError:
                _fonts[size] = ImageFont.load_default()
    return _fonts[size]


# ---------------------------------------------------------------- plans

def render_plan(grid, y_level, scale=None, max_size=700, label=None):
    """Top-down slice: what stands at y_level (dark), else the floor below it (lighter, fading with depth)."""
    sx, sy, sz = grid.size
    if scale is None:
        scale = max(3, min(14, int(max_size / max(sx, sz))))
    top_pad = 22 if label else 4
    img = Image.new('RGBA', (sx * scale + 8, sz * scale + top_pad + 4), (235, 238, 242, 255))
    d = ImageDraw.Draw(img)
    for x in range(sx):
        for z in range(sz):
            col = None
            v = grid.cells.get((x, y_level, z))
            if v is not None:
                c = (230, 40, 200, 255) if v[0] in MARKERS else colors.color(v[0], 'top')
                boxes = shape(v[0], v[1])
                f = 0.55 if is_full(boxes) else 0.85
                col = _shade(c, f)
            else:
                for dy in range(1, 6):
                    v = grid.cells.get((x, y_level - dy, z))
                    if v is not None:
                        c = (230, 40, 200, 255) if v[0] in MARKERS else colors.color(v[0], 'top')
                        col = _shade(c, 1.15 - 0.08 * dy)
                        break
            if col is not None:
                x0, z0 = 4 + x * scale, top_pad + z * scale
                d.rectangle((x0, z0, x0 + scale - 1, z0 + scale - 1), fill=col)
    if label:
        d.text((6, 3), label, fill=(20, 24, 30, 255), font=_font(13))
    return img


def _enclosed(grid):
    """True when the top layer is mostly solid, as for a piece buried in a rock shell."""
    sx, sy, sz = grid.size
    top = {}
    for (x, y, z) in grid.cells:
        if top.get((x, z), -1) < y:
            top[(x, z)] = y
    high = sum(1 for v in top.values() if v >= sy - 2)
    return high > 0.6 * sx * sz


def auto_plan_levels(grid):
    """Pick floor levels: y values where many cells are walkable floor with air above."""
    sx, sy, sz = grid.size
    counts = []
    for y in range(sy - 2):
        n = 0
        for (x, yy, z), v in grid.cells.items():
            if yy == y and (x, y + 1, z) not in grid.cells and (x, y + 2, z) not in grid.cells:
                n += 1
        counts.append(n)
    levels = []
    for y, n in enumerate(counts):
        if n >= max(12, 0.03 * sx * sz):
            if not levels or y - levels[-1] >= 3:
                levels.append(y)
    return [y + 1 for y in levels][:6]


# ---------------------------------------------------------------- sheets

def _stack(images, horizontal=True, gap=8, bg=(244, 246, 248, 255)):
    images = [i for i in images if i is not None]
    if not images:
        return Image.new('RGBA', (10, 10), bg)
    if horizontal:
        w = sum(i.width for i in images) + gap * (len(images) + 1)
        h = max(i.height for i in images) + 2 * gap
        out = Image.new('RGBA', (w, h), bg)
        x = gap
        for i in images:
            out.paste(i, (x, gap))
            x += i.width + gap
    else:
        w = max(i.width for i in images) + 2 * gap
        h = sum(i.height for i in images) + gap * (len(images) + 1)
        out = Image.new('RGBA', (w, h), bg)
        y = gap
        for i in images:
            out.paste(i, (gap, y))
            y += i.height + gap
    return out


def _wrap(images, max_width):
    rows, row, width = [], [], 0
    for i in images:
        if row and width + i.width > max_width:
            rows.append(_stack(row))
            row, width = [], 0
        row.append(i)
        width += i.width + 8
    if row:
        rows.append(_stack(row))
    return _stack(rows, horizontal=False)


def sheet(template, title='', views=('se', 'nw'), plans='auto', cut=None, ground=None, width=1600):
    grid = Grid.from_template(template)
    quarters = {'se': 0, 'sw': 1, 'nw': 2, 'ne': 3}
    each = width if len(views) <= 1 else width // 2
    panels = []
    sx, sy, sz = grid.size
    for v in views:
        lab = '%s  %dx%dx%d  view %s' % (title, sx, sy, sz, v.upper()) if title else 'view ' + v.upper()
        panels.append(render_iso(grid, quarters.get(v, 0), max_width=each - 20, ground=ground, label=lab))
    levels = auto_plan_levels(grid) if plans == 'auto' else (plans or [])
    if cut is None and _enclosed(grid):
        # a buried piece shows only its shell from outside: cut through each floor instead
        cut = [min(sy - 1, y + 2) for y in (levels or [sy // 3, 2 * sy // 3])][:3]
    if cut is not None:
        for c in (cut if isinstance(cut, (list, tuple)) else [cut]):
            panels.append(render_iso(grid, 0, max_width=each - 20, cut=c, ground=ground, label='cutaway at y=%d' % c))
    plan_imgs = [render_plan(grid, y, max_size=min(560, width // 3), label='plan y=%d' % y) for y in levels]
    return _wrap(panels + plan_imgs, width)


def render_file(path, out, **kw):
    t = nbtio.load(path)
    title = kw.pop('title', None) or os.path.splitext(os.path.basename(path))[0]
    img = sheet(t, title=title, **kw)
    img.convert('RGB').save(out)
    return out


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('templates', nargs='+')
    ap.add_argument('-o', '--out', required=True, help='output PNG (with several templates: a directory)')
    ap.add_argument('--views', default='se,nw')
    ap.add_argument('--plans', default='auto', help="'auto', 'none' or a comma list of y levels")
    ap.add_argument('--cut', default=None, help='comma list of cutaway heights')
    ap.add_argument('--ground', type=int, default=None, help='draw a ground plane at this template y')
    ap.add_argument('--width', type=int, default=1600)
    a = ap.parse_args(argv)
    plans = 'auto' if a.plans == 'auto' else [] if a.plans == 'none' else [int(v) for v in a.plans.split(',')]
    cut = [int(v) for v in a.cut.split(',')] if a.cut else None
    views = [v for v in a.views.split(',') if v]
    if len(a.templates) == 1 and a.out.endswith('.png'):
        render_file(a.templates[0], a.out, views=views, plans=plans, cut=cut, ground=a.ground, width=a.width)
        print(a.out)
        return
    os.makedirs(a.out, exist_ok=True)
    for p in a.templates:
        o = os.path.join(a.out, os.path.splitext(os.path.basename(p))[0] + '.png')
        render_file(p, o, views=views, plans=plans, cut=cut, ground=a.ground, width=a.width)
        print(o)


if __name__ == '__main__':
    main()
