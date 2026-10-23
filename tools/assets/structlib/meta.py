"""Site meta: how the runtime placer sits a template in the ground (docs/STRUCTURES.md 7).

    from structlib import meta
    meta.write('story/raider_camp', buried=2, terrain='high', clearance=12)
    meta.write('site/hymnal', buried=9, links={'site/hymnal_arch': (11, 28, -9)})

Writes data/oldrepublic/site_meta/<template path>.json (read by com.oldrepublic.world.site.SiteMeta).
Every field is optional; DEFAULTS are what the game uses for a field that is left out.
"""
import json
import os

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..'))
META = os.path.join(ROOT, 'src', 'main', 'resources', 'data', 'oldrepublic', 'site_meta')

DEFAULTS = {'buried': 1, 'max_slope': 4, 'clearance': 8, 'support_depth': 8, 'ramp_rings': 2, 'clear_above': 6,
            'terrain': 'any', 'spawn_radius': 64}
TERRAINS = ('any', 'flat', 'high', 'low')
INT_FIELDS = ('buried', 'max_slope', 'clearance', 'support_depth', 'ramp_rings', 'clear_above', 'spawn_radius')
FIELDS = INT_FIELDS + ('terrain', 'biomes', 'links')


def _id(v):
    return v if ':' in v else 'oldrepublic:' + v


def path_of(template_path):
    return os.path.join(META, template_path + '.json')


def problems(data):
    """What is wrong with a meta dict (as read from its file); empty when it is fine."""
    out = []
    if not isinstance(data, dict):
        return ['not a JSON object']
    for k, v in data.items():
        if k not in FIELDS:
            out.append('unknown field %r (known: %s)' % (k, ', '.join(FIELDS)))
        elif k in INT_FIELDS:
            if not isinstance(v, int) or isinstance(v, bool) or v < 0 or v > 256:
                out.append('%s must be a whole number from 0 to 256, not %r' % (k, v))
        elif k == 'terrain' and v not in TERRAINS:
            out.append('terrain must be one of %s, not %r' % (', '.join(TERRAINS), v))
        elif k == 'biomes' and not (isinstance(v, str) and v.startswith('#') and ':' in v):
            out.append('biomes must be a biome tag like "#oldrepublic:has_structure/x", not %r' % (v,))
        elif k == 'links':
            if not isinstance(v, dict):
                out.append('links must be an object {template id: [x, y, z]}')
            else:
                for t, off in v.items():
                    if ':' not in t or not (isinstance(off, list) and len(off) == 3 and all(isinstance(i, int) for i in off)):
                        out.append('link %r: needs a namespaced template id and [x, y, z] whole numbers' % (t,))
    return out


def write(template_path, **fields):
    """Writes the meta file of a template (fields as in DEFAULTS, plus biomes and links). links maps a
    template path to its origin offset from this template's origin, unrotated: {'site/hymnal_arch': (11, 28, -9)}."""
    data = {}
    for k, v in fields.items():
        if k == 'biomes' and v is not None:
            v = v if v.startswith('#') else '#' + _id(v)
        elif k == 'links':
            v = {_id(t): [int(i) for i in off] for t, off in sorted(v.items())}
        elif k == 'terrain':
            v = v.lower()
        if v is not None:
            data[k] = v
    bad = problems(data)
    if bad:
        raise ValueError('site meta for %s: %s' % (template_path, '; '.join(bad)))
    path = path_of(template_path)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    text = json.dumps(data, indent=2, sort_keys=True) + '\n'
    if not (os.path.exists(path) and open(path).read() == text):
        with open(path, 'w') as f:
            f.write(text)
    return path


def read(template_path):
    """The meta of a template with the defaults filled in, or None when it has no file."""
    path = path_of(template_path)
    if not os.path.exists(path):
        return None
    with open(path) as f:
        data = json.load(f)
    out = dict(DEFAULTS)
    out.update(data)
    return out
