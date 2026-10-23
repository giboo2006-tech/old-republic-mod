#!/usr/bin/env python3
"""The shared hilt swatches (docs/MILESTONE_7.md 10.2): textures/item/hilt/common.png, which every hilt
model names "#common", and the paints that name its regions. The lead owns this file; design modules
ask for a paint by name and workers ask for new swatches in their reports.

  from common import paint
  steel = paint("steel")                      # tinted machined metal: the part's material colours it
  d.add(octo(0.0, 3.7, 1.1, steel, top=True))
  d.add(side_box(2.0, 3.0, 1.1, 0.6, 0.3, paint("gunmetal"), k=0))

Layout (64x64, 4 texels per model unit; T = tinted greyscale):
  y  0-15  steel T, steel_dark T, steel_worn T, steel_satin T              16x16 each
  y 16-31  rubber, ttrack (ridged rubber), gunmetal, brass                 16x16 each
  y 32-47  leather (32x16, a wrap that continues around an octagon of radius about 1.15 in "wrap" mode)
           mouth, vent T, glass_amber, glass_violet                       8x8 each (y 32)
           knurl T, ember, glass_blue, glass_green                        8x8 each (y 40)
  y 48-63  flat colours, 4x4 each, shown as one texel ("solid"): caps, edges, rims, tiny faces; the
           buttons are bevelled and show whole ("fit") on faces about one unit wide

Every big swatch names its flat counterpart, so an octagon's caps take the matching colour. The top rows
of a swatch are its most typical ones: a short face in "scale" mode shows only the top of the window.

Run python3 tools/assets/hilts/common.py to write the texture (--out PATH writes it elsewhere).
"""
import argparse
import os
import random
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

from lib import ASSETS, Sheet, grey, _clamp  # noqa: E402

TEXTURE_PATH = os.path.join(ASSETS, "textures", "item", "hilt", "common.png")

# name: (x, y, w, h, tinted, flat, mode)
BIG = {
	"steel": (0, 0, 16, 16, True, "steel_flat", "scale"),
	"steel_dark": (16, 0, 16, 16, True, "steel_dark_flat", "scale"),
	"steel_worn": (32, 0, 16, 16, True, "steel_worn_flat", "scale"),
	"steel_satin": (48, 0, 16, 16, True, "steel_hi_flat", "scale"),
	"rubber": (0, 16, 16, 16, False, "rubber_flat", "scale"),
	"ttrack": (16, 16, 16, 16, False, "rubber_flat", "scale"),
	"gunmetal": (32, 16, 16, 16, False, "gunmetal_flat", "scale"),
	"brass": (48, 16, 16, 16, False, "brass_flat", "scale"),
	"leather": (0, 32, 32, 16, False, "leather_flat", "wrap"),
	"mouth": (32, 32, 8, 8, False, "mouth_flat", "fit"),
	"vent": (40, 32, 8, 8, True, "steel_shadow_flat", "fit"),
	"glass_amber": (48, 32, 8, 8, False, "amber_flat", "fit"),
	"glass_violet": (56, 32, 8, 8, False, "violet_flat", "fit"),
	"knurl": (32, 40, 8, 8, True, "steel_lo_flat", "scale"),
	"ember": (40, 40, 8, 8, False, "ember_flat", "fit"),
	"glass_blue": (48, 40, 8, 8, False, "crystal_blue_flat", "fit"),
	"glass_green": (56, 40, 8, 8, False, "crystal_green_flat", "fit"),
}

# Flat colours in reading order from (0, 48), 4x4 each. A greyscale value is tinted, an RGB triple is not.
FLATS = [
	# tinted metal, light to dark
	("steel_flat", 204), ("steel_hi_flat", 236), ("steel_lo_flat", 156), ("steel_shadow_flat", 104),
	("steel_dark_flat", 128), ("steel_deep_flat", 58), ("steel_worn_flat", 178), ("white_metal_flat", 250),
	# untinted
	("black_flat", (14, 14, 16)), ("rubber_flat", (30, 30, 33)), ("gunmetal_flat", (74, 78, 87)),
	("gunmetal_hi_flat", (112, 118, 130)), ("brass_flat", (190, 150, 76)), ("brass_hi_flat", (232, 198, 120)),
	("brass_lo_flat", (128, 94, 42)), ("mouth_flat", (9, 9, 11)),
	("leather_flat", (80, 53, 34)), ("cord_flat", (122, 118, 106)), ("bone_flat", (222, 214, 192)),
	("copper_flat", (178, 102, 64)), ("lacquer_flat", (30, 24, 28)), ("lacquer_hi_flat", (62, 52, 58)),
	("pale_flat", (214, 208, 226)), ("paint_red_flat", (132, 34, 30)),
	# glowing or coloured accents (give their elements light_emission when they should glow)
	("ember_flat", (236, 84, 44)), ("sunstone_flat", (255, 172, 72)), ("amber_flat", (250, 186, 84)),
	("violet_flat", (184, 146, 255)), ("crystal_blue_flat", (118, 196, 255)), ("crystal_green_flat", (124, 236, 150)),
	("indicator_red_flat", (255, 64, 52)), ("indicator_green_flat", (88, 240, 120)),
]

# Bevelled buttons, 4x4 each, after the flats: (name, base, highlight, shadow)
BUTTONS = [
	("button_red", (196, 34, 30), (255, 118, 100), (104, 14, 12)),
	("button_green", (40, 160, 72), (126, 236, 150), (16, 82, 34)),
	("button_blue", (40, 92, 204), (122, 170, 255), (18, 40, 110)),
	("button_amber", (214, 146, 38), (255, 214, 120), (120, 74, 14)),
	("button_black", (34, 34, 38), (92, 92, 100), (8, 8, 10)),
	("button_steel", 196, 240, 112),
]


def build():
	"""The common sheet, painted."""
	sh = Sheet(texture="#common", seed=7)
	for name, (x, y, w, h, tint, flat, mode) in BIG.items():
		sh.region(name, x, y, w, h, tint=tint, flat=flat, mode=mode)
	i = 0
	for name, _c in FLATS:
		sh.region(name, (i % 16) * 4, 48 + (i // 16) * 4, 4, 4, tint=isinstance(_c, (int, float)), mode="solid")
		i += 1
	for name, base, _hi, _lo in BUTTONS:
		x, y = (i % 16) * 4, 48 + (i // 16) * 4
		sh.region(name, x, y, 4, 4, tint=isinstance(base, (int, float)), solid_px=(x + 1, y + 1), mode="fit")
		i += 1
	if i > 64:
		raise SystemExit("the flat colours overflow the sheet")

	_steel(sh, "steel", 204, lathe=3.0, grooves=(6, 13), seed=11)
	_steel(sh, "steel_dark", 128, lathe=3.0, grooves=(9,), seed=12)
	_worn(sh, "steel_worn", 182, seed=13)
	_satin(sh, "steel_satin", 222, seed=14)
	_rubber(sh, "rubber", seed=15)
	_ttrack(sh, "ttrack", seed=16)
	_gunmetal(sh, "gunmetal", seed=17)
	_brass(sh, "brass", seed=18)
	_leather(sh, "leather", seed=19)
	_mouth(sh, "mouth")
	_vent(sh, "vent")
	_glass(sh, "glass_amber", (240, 160, 56))
	_glass(sh, "glass_violet", (160, 112, 236))
	_knurl(sh, "knurl")
	_ember(sh, "ember")
	_glass(sh, "glass_blue", (80, 164, 240))
	_glass(sh, "glass_green", (82, 212, 124))
	for name, c in FLATS:
		sh.fill(name, grey(c) if isinstance(c, (int, float)) else c)
	for name, base, hi, lo in BUTTONS:
		_button(sh, name, base, hi, lo)
	probs = sh.problems()
	if probs:
		raise SystemExit("\n".join(probs))
	return sh


# ------------------------------------------------------------------ swatch painters

def _rows(rng, n, amount, smooth=0.55):
	"""A gently varying value per row (lathe marks): a smoothed random walk around 0."""
	out, v = [], 0.0
	for _ in range(n):
		v = v * smooth + rng.uniform(-amount, amount)
		out.append(v)
	return out


def _steel(sh, name, base, lathe, grooves, seed):
	reg = sh.regions[name][0]
	rng = random.Random(seed)
	rows = _rows(rng, reg.h, lathe)
	for x, y, lx, ly in sh._pixels(reg):
		v = base + rows[ly] + rng.uniform(-1.6, 1.6)
		if ly in grooves:
			v -= 16
		elif ly - 1 in grooves:
			v += 7
		sh.px(x, y, grey(v))


def _worn(sh, name, base, seed):
	reg = sh.regions[name][0]
	rng = random.Random(seed)
	rows = _rows(rng, reg.h, 4.0)
	blot = {(rng.randrange(reg.w), rng.randrange(reg.h)) for _ in range(4)}
	for x, y, lx, ly in sh._pixels(reg):
		v = base + rows[ly] + rng.uniform(-4, 4)
		# soft darker patches (old oxidation) around a few centres
		for bx, by in blot:
			d = abs(lx - bx) + abs(ly - by)
			if d <= 2:
				v -= 14 - d * 4
		sh.px(x, y, grey(v))
	for _ in range(14):   # pits: a dark texel with a lighter rim below
		px, py = reg.x + rng.randrange(reg.w), reg.y + rng.randrange(reg.h - 1)
		sh.px(px, py, grey(sh.get(px, py)[0] - 58))
		sh.px(px, py + 1, grey(sh.get(px, py + 1)[0] + 14))
	for _ in range(4):    # scratches: short light diagonals
		px, py = reg.x + rng.randrange(reg.w - 3), reg.y + rng.randrange(reg.h - 3)
		for k in range(rng.randint(2, 4)):
			sh.px(px + k, py + k, grey(min(250, sh.get(px + k, py + k)[0] + 26)))


def _satin(sh, name, base, seed):
	reg = sh.regions[name][0]
	rng = random.Random(seed)
	cols = _rows(rng, reg.w, 3.0, smooth=0.3)
	for x, y, lx, ly in sh._pixels(reg):
		sh.px(x, y, grey(base + cols[lx] + rng.uniform(-1.2, 1.2)))


def _rubber(sh, name, seed):
	reg = sh.regions[name][0]
	rng = random.Random(seed)
	cols = _rows(rng, reg.w, 1.6, smooth=0.3)
	for x, y, lx, ly in sh._pixels(reg):
		v = 30 + cols[lx] + rng.uniform(-1.5, 1.5)
		sh.px(x, y, (v, v, v + 3))


def _ttrack(sh, name, seed):
	"""Ridged rubber: a ridge every 4 texels (one per model unit): lit top edge, face, shadowed foot, gap."""
	reg = sh.regions[name][0]
	rng = random.Random(seed)
	profile = (66, 46, 37, 11)
	for x, y, lx, ly in sh._pixels(reg):
		v = profile[ly % 4] + rng.uniform(-1.5, 1.5)
		sh.px(x, y, (v, v, v + 3))


def _gunmetal(sh, name, seed):
	reg = sh.regions[name][0]
	rng = random.Random(seed)
	cols = _rows(rng, reg.w, 3.0, smooth=0.3)
	for x, y, lx, ly in sh._pixels(reg):
		n = cols[lx] + rng.uniform(-1.5, 1.5)
		sh.px(x, y, (72 + n, 76 + n, 85 + n))


def _brass(sh, name, seed):
	reg = sh.regions[name][0]
	rng = random.Random(seed)
	rows = _rows(rng, reg.h, 4.0)
	for x, y, lx, ly in sh._pixels(reg):
		n = rows[ly] + rng.uniform(-2, 2)
		sh.px(x, y, (190 + n, 150 + n * 0.9, 76 + n * 0.6))


def _leather(sh, name, seed):
	"""A spiral wrap: diagonal strips 4 texels apart, each with a lit upper edge and a dark seam. 32 texels
	wide, so 8 columns of 4 continue around an octagon side by side ("wrap" mode)."""
	reg = sh.regions[name][0]
	rng = random.Random(seed)
	for x, y, lx, ly in sh._pixels(reg):
		t = (lx + ly) % 4
		if t == 0:
			c = (44, 28, 18)
		elif t == 1:
			c = (110, 76, 50)
		else:
			c = (82, 54, 34)
		n = rng.uniform(-3, 3)
		sh.px(x, y, [v + n for v in c])


def _mouth(sh, name):
	"""The emitter's dark mouth seen from above: a dull rim, a darker throat, a near-black disc."""
	reg = sh.regions[name][0]
	for x, y, lx, ly in sh._pixels(reg):
		d = max(abs(lx - 3.5), abs(ly - 3.5))
		if d > 3:
			c = (52, 54, 60)
		elif d > 2:
			c = (26, 27, 31)
		elif d > 1:
			c = (14, 14, 17)
		else:
			c = (6, 6, 8)
		sh.px(x, y, c)


def _vent(sh, name):
	"""Two vertical vent slots in metal: a dark slot, a shadowed top lip, a lit bottom lip."""
	reg = sh.regions[name][0]
	sh.fill(reg, grey(200))
	for col in (2, 5):
		for row in range(1, 7):
			sh.px(reg.x + col, reg.y + row, grey(20))
		sh.px(reg.x + col, reg.y + 0, grey(150))
		sh.px(reg.x + col, reg.y + 7, grey(236))


def _glass(sh, name, rgb):
	"""Coloured glass: light at the top, deep at the bottom, a bright glint, a dark rim at the sides."""
	reg = sh.regions[name][0]
	for x, y, lx, ly in sh._pixels(reg):
		t = ly / (reg.h - 1)
		k = 1.25 - 0.55 * t
		c = [_clamp(v * k) for v in rgb]
		if lx in (0, reg.w - 1):
			c = [_clamp(v * 0.7) for v in c]
		sh.px(x, y, c)
	sh.px(reg.x + 2, reg.y + 1, [_clamp(v + 110) for v in rgb])
	sh.px(reg.x + 3, reg.y + 1, [_clamp(v + 70) for v in rgb])
	sh.px(reg.x + 2, reg.y + 2, [_clamp(v + 70) for v in rgb])


def _knurl(sh, name):
	reg = sh.regions[name][0]
	for x, y, lx, ly in sh._pixels(reg):
		a = (lx + ly) % 4 == 0
		b = (lx - ly) % 4 == 0
		v = 118 if (a and b) else 146 if (a or b) else 204
		sh.px(x, y, grey(v))


def _ember(sh, name):
	"""A glowing ember-red inlay: a hot core fading to deep red at the edges."""
	reg = sh.regions[name][0]
	for x, y, lx, ly in sh._pixels(reg):
		d = max(abs(lx - 3.5), abs(ly - 3.5)) / 3.5
		c = (255 - 40 * d, 128 - 74 * d, 64 - 36 * d)
		sh.px(x, y, c)


def _button(sh, name, base, hi, lo):
	reg = sh.regions[name][0]
	conv = lambda c: grey(c) if isinstance(c, (int, float)) else c
	base, hi, lo = conv(base), conv(hi), conv(lo)
	# h h h b / h b b l / h b b l / b l l l
	pattern = ("hhhb", "hbbl", "hbbl", "blll")
	for ly, row in enumerate(pattern):
		for lx, ch in enumerate(row):
			sh.px(reg.x + lx, reg.y + ly, {"h": hi, "b": base, "l": lo}[ch])


# ------------------------------------------------------------------ the paints

_SHEET = None


def sheet():
	"""The painted common sheet (built once)."""
	global _SHEET
	if _SHEET is None:
		_SHEET = build()
	return _SHEET


def paint(name, mode=None):
	"""The paint of a common swatch: paint("steel"), paint("brass_flat"), paint("leather", "wrap")."""
	return sheet().paint(name, mode)


def names():
	return sorted(sheet().regions)


if __name__ == "__main__":
	ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
	ap.add_argument("--out", help="write the texture here instead of the assets folder")
	ap.add_argument("--list", action="store_true", help="list the swatch names")
	args = ap.parse_args()
	if args.list:
		for n in names():
			reg, tint, flat, _s, mode = sheet().regions[n]
			print(f"{n:22} {reg.x:2},{reg.y:2} {reg.w:2}x{reg.h:<2} {'tinted' if tint else '      '} {mode:5} {flat or ''}")
	else:
		path = args.out or TEXTURE_PATH
		sheet().save(path)
		print(f"wrote {os.path.relpath(path)}")
