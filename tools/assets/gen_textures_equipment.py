#!/usr/bin/env python3
"""Generates the era armour art (spec 9.4; PLACEHOLDER art, original, deterministic).

Writes into src/main/resources/assets/oldrepublic/:
  textures/item/<18 armour ids>.png                          16x16 inventory icons
  textures/entity/equipment/humanoid/<asset>.png             64x32 plain armour layers (6 assets)
  textures/entity/equipment/humanoid_leggings/<asset>.png    64x32 leggings layers (4 assets)
  textures/entity/robe/<asset>.png                           64x64 robe model textures (4 assets)
  equipment/<asset>.json                                     equipment assets (layers humanoid, humanoid_leggings)
  items/<id>.json, models/item/<id>.json                     item definitions and models of the 18 pieces

Assets: republic_trooper, sith_trooper, jedi_robes, sith_robes, jedi_knight (the Knight's robe,
chest only), sith_lord (the Lord's robe, chest only).

The plain layers use the vanilla armour layout (64x32): head 0,0 (hat 32,0 left empty), body
16,16, arms 40,16, legs 0,16 (boots in the humanoid layer, trousers in humanoid_leggings).

Robe model layout (64x64), mirrored in com.oldrepublic.client.equipment.RobeModel:
  hood 0,0 (8x8x8)      brim 0,57 (9x2x2)     flaps 56,0 (1x7x2)     cowl 0,32 (9x2x5)
  torso 32,0 (8x12x4)   sleeves 0,16 (4x12x4) cuffs 24,39 (5x4x5)    back panel 48,16 (7x9x1)
  belt 28,32 (8x2x4)    mantle 30,50 (10x3x6) sash 56,26 (2x15x1)
  waist 0,39 (8x3x4)    legs 16,16 (4x12x4)   skirt front 44,39 (4x10x1) skirt back 0,46 (4x10x1)
  boots 32,16 (4x5x4)   wraps 10,48 (5x2x5)

gen_textures_trooper.py imports PALETTE, composite() and the plate painters from here, so the
troopers wear exactly the armour the player can craft (spec 7.1).

Run from the repository root: python3 tools/assets/gen_textures_equipment.py
"""
import json
import os
import random

from PIL import Image

ASSETS = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "src", "main", "resources", "assets", "oldrepublic")
ROOT = os.path.join(ASSETS, "textures")

# ---------------------------------------------------------------- palette
# Muted, worn Old Republic colours. Each set's roles are read by the painters below.
PALETTE = {
	"republic": {
		"plate": (214, 210, 198), "plate_light": (234, 231, 222), "plate_dark": (168, 164, 154),
		"suit": (38, 46, 72), "suit_dark": (26, 32, 52), "stripe": (176, 146, 82),
		"visor": (44, 66, 92), "visor_light": (110, 150, 180), "trim": (92, 98, 110),
		"belt": (62, 58, 54), "buckle": (176, 146, 82), "glove": (48, 50, 58),
	},
	"imperial": {
		"plate": (66, 66, 72), "plate_light": (96, 96, 104), "plate_dark": (44, 44, 50),
		"suit": (24, 22, 26), "suit_dark": (14, 13, 16), "stripe": (148, 30, 40),
		"visor": (12, 11, 14), "visor_light": (70, 40, 44), "trim": (148, 30, 40),
		"belt": (30, 28, 32), "buckle": (160, 164, 170), "glove": (20, 19, 22),
	},
	"jedi": {
		"outer": (112, 80, 50), "outer_dark": (80, 56, 34), "outer_light": (138, 102, 66),
		"tunic": (214, 200, 168), "tunic_dark": (182, 166, 134), "sash": (196, 178, 140),
		"trousers": (170, 150, 116), "trousers_dark": (140, 122, 92),
		"leather": (86, 58, 36), "leather_dark": (60, 40, 26), "buckle": (196, 170, 100),
		"thread": (226, 210, 150), "lining": (150, 112, 74),
	},
	"sith": {
		"outer": (34, 30, 36), "outer_dark": (20, 18, 22), "outer_light": (56, 50, 58),
		"tunic": (46, 40, 48), "tunic_dark": (30, 26, 32), "sash": (118, 24, 32),
		"trousers": (50, 44, 52), "trousers_dark": (34, 30, 36),
		"leather": (28, 24, 26), "leather_dark": (16, 14, 16), "buckle": (150, 152, 160),
		"thread": (110, 98, 120), "lining": (104, 22, 30),
	},
	# The rank 3 robes: the set's colours plus a mantle and a sash.
	"jedi_knight": {"mantle": (96, 66, 40), "mantle_dark": (70, 48, 30), "mantle_trim": (226, 210, 150),
			"sash": (230, 218, 182), "sash_thread": (200, 166, 88)},
	"sith_lord": {"mantle": (112, 22, 30), "mantle_dark": (76, 14, 20), "mantle_trim": (160, 162, 170),
			"sash": (26, 22, 28), "sash_thread": (150, 152, 160)},
}

ROBE_BASE = {"jedi_robes": "jedi", "sith_robes": "sith", "jedi_knight": "jedi", "sith_lord": "sith"}

# ---------------------------------------------------------------- helpers


def out(rel, img):
	path = os.path.join(ROOT, rel)
	os.makedirs(os.path.dirname(path), exist_ok=True)
	img.save(path)
	print("wrote", os.path.normpath(path))


def write_json(rel, data):
	path = os.path.join(ASSETS, rel)
	os.makedirs(os.path.dirname(path), exist_ok=True)
	with open(path, "w", encoding="utf-8") as f:
		json.dump(data, f, indent=2)
		f.write("\n")
	print("wrote", os.path.normpath(path))


def clamp(v):
	return max(0, min(255, int(round(v))))


def noisy(rgb, rnd, amount):
	return tuple(clamp(c + rnd.uniform(-amount, amount)) for c in rgb)


def shade(rgb, factor):
	return tuple(clamp(c * factor) for c in rgb)


def mix(a, b, t):
	return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(3))


def face_rects(u, v, w, h, d):
	"""Texture rectangles (x, y, width, height) of a model box, in the vanilla cube layout."""
	return {
		"top": (u + d, v, w, d),
		"bottom": (u + d + w, v, w, d),
		"east": (u, v + d, d, h),
		"front": (u + d, v + d, w, h),
		"west": (u + d + w, v + d, d, h),
		"back": (u + d + w + d, v + d, w, h),
	}


def paint_box(px, u, v, w, h, d, painter):
	"""Calls painter(face, fx, fy, fw, fh) for every texel of a box; it returns RGB(A) or None (transparent)."""
	for face, (x0, y0, fw, fh) in face_rects(u, v, w, h, d).items():
		for fy in range(fh):
			for fx in range(fw):
				c = painter(face, fx, fy, fw, fh)
				if c is not None:
					px[x0 + fx, y0 + fy] = c if len(c) == 4 else c + (255,)


def bevel(c, fx, fy, fw, fh, amount=0.08):
	"""Lit top-left edge, shadowed bottom-right edge: reads as a plate with thickness."""
	if fy == 0 or fx == 0:
		return shade(c, 1 + amount)
	if fy == fh - 1 or fx == fw - 1:
		return shade(c, 1 - amount)
	return c


def composite(size, *layers):
	"""Alpha-composites the layers in order onto a transparent canvas of {size} (each pasted at 0,0)."""
	canvas = Image.new("RGBA", size, (0, 0, 0, 0))
	for layer in layers:
		if layer is None:
			continue
		sheet = Image.new("RGBA", size, (0, 0, 0, 0))
		sheet.paste(layer, (0, 0))
		canvas = Image.alpha_composite(canvas, sheet)
	return canvas


def is_side(face):
	return face in ("east", "west")


def front_edge(face, fx, fw):
	"""Distance in texels from a side face's front edge (east faces end at the front, west faces start there)."""
	return fw - 1 - fx if face == "east" else fx


# ---------------------------------------------------------------- trooper plates (shared with the trooper skins)


def trooper_plate(c, rnd, fx, fy, fw, fh, amount=5):
	return noisy(bevel(c, fx, fy, fw, fh), rnd, amount)


def helmet_painter(army, rnd):
	"""Head box 8x8x8: the Republic rounded helmet with one wide visor band, or the Imperial angular helmet."""
	p = PALETTE[army]
	republic = army == "republic"

	def paint(face, fx, fy, fw, fh):
		if face == "bottom":
			return noisy(p["suit_dark"], rnd, 4)
		if face == "top":
			c = p["plate_light"] if republic and 2 <= fx <= 5 and 2 <= fy <= 5 else p["plate"]
			if not republic and fx in (3, 4):
				c = p["plate_light"]
			return trooper_plate(c, rnd, fx, fy, fw, fh)
		if face == "front":
			if republic:
				if fy in (3, 4):
					c = p["visor_light"] if (fy == 3 and fx in (1, 2)) else p["visor"]
					return noisy(c, rnd, 3)
				if fy == 2:
					return noisy(p["plate_dark"], rnd, 4)
				if fy == 6 and 2 <= fx <= 5:
					return noisy(p["trim"] if fx % 2 == 0 else p["plate_dark"], rnd, 3)
				if fy == 7:
					return noisy(p["plate_dark"], rnd, 4)
				return trooper_plate(p["plate"], rnd, fx, fy, fw, fh)
			# Imperial: brow ridge, two narrow eye slits, a nose ridge, angled cheeks, crimson piping.
			if fy == 2:
				return noisy(p["plate_light"], rnd, 4)
			if fy == 3 and fx in (1, 2, 5, 6):
				return noisy(p["visor"], rnd, 2) if fx in (2, 5) else noisy(p["visor_light"], rnd, 3)
			if fx in (3, 4) and 3 <= fy <= 6:
				return noisy(p["plate_light"], rnd, 4)
			if (fx, fy) in ((0, 4), (1, 5), (2, 6), (7, 4), (6, 5), (5, 6)):
				return noisy(p["plate_dark"], rnd, 3)
			if fy == 7:
				return noisy(p["trim"], rnd, 4)
			return trooper_plate(p["plate"], rnd, fx, fy, fw, fh)
		if is_side(face):
			edge = front_edge(face, fx, fw)
			if republic:
				if fy in (3, 4) and edge <= 1:
					return noisy(p["visor"], rnd, 3)
				if 3 <= fy <= 6 and 2 <= fx <= 5:
					ring = fy in (3, 6) or fx in (2, 5)
					return noisy(p["plate_dark"] if ring else p["plate_light"], rnd, 4)
				return trooper_plate(p["plate"], rnd, fx, fy, fw, fh)
			if fy == 6:
				return noisy(p["trim"], rnd, 4)
			if fy == 7:
				return noisy(p["plate_dark"], rnd, 4)
			if fy == 2 and edge <= 2:
				return noisy(p["plate_light"], rnd, 4)
			return trooper_plate(p["plate"], rnd, fx, fy, fw, fh)
		# back
		if republic:
			if fy == 5 and 1 <= fx <= 6:
				return noisy(p["plate_dark"], rnd, 4)
			if fy == 7:
				return noisy(p["trim"], rnd, 4)
			return trooper_plate(p["plate"], rnd, fx, fy, fw, fh)
		if fx in (3, 4):
			return noisy(p["plate_light"], rnd, 4)
		if fy == 6:
			return noisy(p["trim"], rnd, 4)
		return trooper_plate(p["plate"], rnd, fx, fy, fw, fh)

	return paint


def chest_painter(army, rnd, suit_alpha=True):
	"""Body box 8x12x4: chest plates over the undersuit, segmented abdomen, belt. With
	suit_alpha=False the undersuit texels are left transparent (for the trooper's cuirass part)."""
	p = PALETTE[army]
	republic = army == "republic"

	def suit():
		return noisy(p["suit"], rnd, 4) if suit_alpha else None

	def paint(face, fx, fy, fw, fh):
		if face == "top":
			return trooper_plate(p["plate"], rnd, fx, fy, fw, fh)
		if face == "bottom":
			return suit()
		if fy == 10:
			if face == "front" and fx in (3, 4):
				return noisy(p["buckle"], rnd, 5)
			return noisy(p["belt"], rnd, 4)
		if face == "front":
			if fy == 0 and 2 <= fx <= 5:
				return suit()
			if fy <= 5:
				c = p["plate"]
				if fx in (3, 4) and fy >= 2:
					c = p["plate_dark"] if fx == 4 else p["plate"]
				if not republic and fx in (0, 7) and 1 <= fy <= 5:
					return noisy(p["trim"], rnd, 4)
				return trooper_plate(c, rnd, fx, fy, fw, 6)
			if fy in (6, 8) and 1 <= fx <= 6:
				return trooper_plate(p["plate"] if republic else p["plate_light"], rnd, fx - 1, 0, 6, 1)
			if fy == 11:
				return suit()
			return suit()
		if face == "back":
			if fy <= 6:
				if 2 <= fx <= 5 and 1 <= fy <= 5:
					ring = fx in (2, 5) or fy in (1, 5)
					return noisy(p["plate_dark"] if ring else p["trim"], rnd, 4)
				return trooper_plate(p["plate"], rnd, fx, fy, fw, 7)
			return suit()
		# sides
		if 1 <= fy <= 5:
			edge = front_edge(face, fx, fw)
			if edge <= 1:
				return trooper_plate(p["plate"], rnd, fx, fy, fw, fh)
			if not republic and edge == 2:
				return noisy(p["trim"], rnd, 4)
		return suit()

	return paint


def arm_painter(army, rnd, band=None):
	"""Arm box 4x12x4: pauldron with the shoulder stripe (or {band}), undersuit, vambrace, glove."""
	p = PALETTE[army]
	stripe = band or p["stripe"]

	def paint(face, fx, fy, fw, fh):
		if face == "top":
			return trooper_plate(p["plate"], rnd, fx, fy, fw, fh)
		if face == "bottom":
			return noisy(p["glove"], rnd, 4)
		if fy <= 3:
			if fy == 2:
				return noisy(stripe, rnd, 5)
			return trooper_plate(p["plate"], rnd, fx, fy, fw, 4)
		if fy <= 6:
			return noisy(p["suit"], rnd, 4)
		if fy <= 10:
			if fy == 7:
				return noisy(p["plate_light"], rnd, 4)
			return trooper_plate(p["plate"], rnd, fx, fy - 7, fw, 4)
		return noisy(p["glove"], rnd, 4)

	return paint


def boot_painter(army, rnd):
	"""Leg box 4x12x4 of the humanoid layer: only the boots (rows 7-11)."""
	p = PALETTE[army]

	def paint(face, fx, fy, fw, fh):
		if face == "top":
			return None
		if face == "bottom":
			return noisy(p["suit_dark"], rnd, 3)
		if fy < 7:
			return None
		if fy <= 9:
			if face == "front" and fy == 7:
				return noisy(p["plate_light"], rnd, 4)
			return trooper_plate(p["plate"], rnd, fx, fy - 7, fw, 3)
		return noisy(p["glove"] if fy == 11 else p["plate_dark"], rnd, 4)

	return paint


def legging_painter(army, rnd):
	"""Leg box 4x12x4 of the leggings layer: thigh plates, knee pad, undersuit shin."""
	p = PALETTE[army]

	def paint(face, fx, fy, fw, fh):
		if face == "top":
			return None
		if face == "bottom":
			return noisy(p["suit_dark"], rnd, 3)
		if fy <= 4:
			if face in ("front", "east", "west") and not (is_side(face) and front_edge(face, fx, fw) > 2):
				return trooper_plate(p["plate"], rnd, fx, fy, fw, 5)
			return noisy(p["suit"], rnd, 4)
		if fy in (5, 6):
			if face == "front":
				return noisy(p["plate_light"] if fy == 5 else p["plate_dark"], rnd, 4)
			return noisy(p["suit"], rnd, 4)
		return noisy(p["suit"], rnd, 4)

	return paint


def waist_painter(army, rnd):
	"""Body box of the leggings layer: only the hips (rows 9-11), with a plate at the front."""
	p = PALETTE[army]

	def paint(face, fx, fy, fw, fh):
		if face in ("top",) or fy < 9:
			return None
		if face == "bottom":
			return noisy(p["suit_dark"], rnd, 3)
		if face == "front" and fy >= 10 and fx in (3, 4):
			return noisy(p["plate"], rnd, 4)
		return noisy(p["suit"], rnd, 4)

	return paint


def trooper_layers(army):
	"""The plain armour layers of a trooper set: (humanoid 64x32, humanoid_leggings 64x32)."""
	rnd = random.Random(1001 if army == "republic" else 1002)
	humanoid = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
	px = humanoid.load()
	paint_box(px, 0, 0, 8, 8, 8, helmet_painter(army, rnd))
	paint_box(px, 16, 16, 8, 12, 4, chest_painter(army, rnd))
	paint_box(px, 40, 16, 4, 12, 4, arm_painter(army, rnd))
	paint_box(px, 0, 16, 4, 12, 4, boot_painter(army, rnd))

	leggings = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
	px = leggings.load()
	paint_box(px, 16, 16, 8, 12, 4, waist_painter(army, rnd))
	paint_box(px, 0, 16, 4, 12, 4, legging_painter(army, rnd))
	return humanoid, leggings


# ---------------------------------------------------------------- robes


def cloth(c, rnd, fx, fy, amount=5, folds=3):
	"""Woven cloth: noise plus soft vertical fold lines."""
	f = 0.92 if fx % folds == 0 else 1.0
	return noisy(shade(c, f), rnd, amount)


def hood_painter(kind, rnd):
	"""Head box 8x8x8: a deep hood, open at the face (transparent), shadowed inside the opening."""
	p = PALETTE[kind]

	def paint(face, fx, fy, fw, fh):
		if face == "bottom":
			return None
		if face == "front":
			if 1 <= fx <= 6 and fy >= 2:
				return None
			if fy <= 1:
				return cloth(p["outer_dark"] if fy == 1 else p["outer"], rnd, fx, fy)
			return cloth(p["lining"] if fx in (1, 6) else p["outer_dark"], rnd, fx, fy)
		if face == "top":
			return cloth(p["outer_light"] if fx in (3, 4) else p["outer"], rnd, fx, fy)
		if is_side(face):
			edge = front_edge(face, fx, fw)
			c = p["outer_dark"] if edge == 0 else p["outer"]
			return cloth(shade(c, 1.0 - 0.03 * fy), rnd, fx, fy)
		# back: the hood narrows into a fold down the middle.
		if fx in (3, 4) and fy >= 3:
			return cloth(p["outer_dark"], rnd, fx, fy)
		return cloth(p["outer"], rnd, fx, fy)

	return paint


def robe_body_painter(kind, rnd, rank=None):
	"""Body box 8x12x4: the tunic with crossed lapels under the open outer robe, sash and belt."""
	p = PALETTE[kind]
	r = PALETTE[rank] if rank else None

	def paint(face, fx, fy, fw, fh):
		if face == "top":
			return cloth(r["mantle"] if r else p["outer"], rnd, fx, fy)
		if face == "bottom":
			return cloth(p["tunic_dark"], rnd, fx, fy)
		if face == "front":
			if fx in (0, 7):
				return cloth(p["outer"], rnd, fx, fy)
			if r and fy <= 1:
				return cloth(r["mantle"] if fy == 0 else r["mantle_dark"], rnd, fx, fy)
			if fy in (6, 7):
				return cloth(p["sash"], rnd, fx, fy, 3, 4)
			if fy == 8:
				return noisy(p["buckle"] if fx in (3, 4) else p["leather"], rnd, 4)
			if fy <= 5:
				# Lapels: the left over the right, meeting low on the chest.
				left = fx - 1 == fy // 2 + 0
				right = 6 - fx == fy // 2
				if left or right:
					return cloth(p["tunic_dark"], rnd, fx, fy)
				if 2 + fy // 2 >= fx >= 1 + fy // 2 or fx > 6 - fy // 2:
					return cloth(p["tunic"], rnd, fx, fy)
				return cloth(shade(p["tunic"], 0.95), rnd, fx, fy)
			return cloth(p["tunic"] if fy < 11 else p["tunic_dark"], rnd, fx, fy)
		if face == "back":
			if r and fy <= 2:
				return cloth(r["mantle_dark"] if fy == 2 else r["mantle"], rnd, fx, fy)
			if fy == 8:
				return noisy(p["leather"], rnd, 4)
			return cloth(p["outer"] if fy < 11 else p["outer_dark"], rnd, fx, fy)
		if fy == 8:
			return noisy(p["leather"], rnd, 4)
		if r and fy <= 1:
			return cloth(r["mantle"], rnd, fx, fy)
		return cloth(p["outer"], rnd, fx, fy)

	return paint


def robe_arm_painter(kind, rnd, rank=None):
	"""Arm box 4x12x4: a wide outer sleeve, a folded cuff, the tunic sleeve and a glove."""
	p = PALETTE[kind]
	r = PALETTE[rank] if rank else None

	def paint(face, fx, fy, fw, fh):
		if face == "top":
			return cloth(r["mantle"] if r else p["outer"], rnd, fx, fy)
		if face == "bottom":
			return noisy(p["leather_dark"], rnd, 4)
		if r and fy <= 3:
			if fy == 3:
				return cloth(r["mantle_trim"], rnd, fx, fy, 4)
			return cloth(r["mantle"], rnd, fx, fy)
		if fy <= 8:
			return cloth(p["outer"] if fy < 8 else p["outer_light"], rnd, fx, fy)
		if fy == 9:
			return cloth(p["outer_dark"], rnd, fx, fy)
		if fy == 10:
			return cloth(p["lining"], rnd, fx, fy)
		return noisy(p["leather"], rnd, 4)

	return paint


def robe_boot_painter(kind, rnd):
	"""Leg box 4x12x4 of the humanoid layer: wrapped leather boots (rows 7-11)."""
	p = PALETTE[kind]

	def paint(face, fx, fy, fw, fh):
		if face == "top" or fy < 7 and face != "bottom":
			return None
		if face == "bottom":
			return noisy(p["leather_dark"], rnd, 3)
		if fy <= 9:
			band = (fy + fx) % 3 == 0
			return noisy(p["outer_dark"] if band else p["leather"], rnd, 4)
		return noisy(p["leather_dark"] if fy == 11 else p["leather"], rnd, 4)

	return paint


def trouser_painter(kind, rnd):
	"""Leg box 4x12x4 of the leggings layer: loose trousers with fold lines, tucked into the boots."""
	p = PALETTE[kind]

	def paint(face, fx, fy, fw, fh):
		if face == "top":
			return None
		if face == "bottom":
			return noisy(p["trousers_dark"], rnd, 3)
		c = p["trousers_dark"] if fy >= 10 or (fx + fy // 4) % 4 == 0 else p["trousers"]
		return cloth(c, rnd, fx, fy, 4)

	return paint


def trouser_waist_painter(kind, rnd):
	p = PALETTE[kind]

	def paint(face, fx, fy, fw, fh):
		if face == "top" or fy < 9:
			return None
		if face == "bottom":
			return noisy(p["trousers_dark"], rnd, 3)
		return cloth(p["trousers"] if fy > 9 else p["sash"], rnd, fx, fy, 4)

	return paint


def robe_layers(asset):
	"""The plain armour layers of a robe asset: (humanoid, humanoid_leggings or None for the rank 3 robes)."""
	kind = ROBE_BASE[asset]
	rank = asset if asset in ("jedi_knight", "sith_lord") else None
	rnd = random.Random({"jedi_robes": 2001, "sith_robes": 2002, "jedi_knight": 2003, "sith_lord": 2004}[asset])
	humanoid = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
	px = humanoid.load()

	if rank is None:
		paint_box(px, 0, 0, 8, 8, 8, hood_painter(kind, rnd))
		paint_box(px, 0, 16, 4, 12, 4, robe_boot_painter(kind, rnd))

	paint_box(px, 16, 16, 8, 12, 4, robe_body_painter(kind, rnd, rank))
	paint_box(px, 40, 16, 4, 12, 4, robe_arm_painter(kind, rnd, rank))

	if rank is not None:
		sash(px, 16 + 4, 16 + 4, 8, 12, PALETTE[rank], rnd)
		return humanoid, None

	leggings = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
	px = leggings.load()
	paint_box(px, 16, 16, 8, 12, 4, trouser_waist_painter(kind, rnd))
	paint_box(px, 0, 16, 4, 12, 4, trouser_painter(kind, rnd))
	return humanoid, leggings


def sash(px, x0, y0, w, h, r, rnd):
	"""A diagonal sash across a front face (rank 3 robes), from the left shoulder to the right hip."""
	for i in range(h - 3):
		x = x0 + w - 1 - int(i * (w - 1) / (h - 4))
		if 0 <= x - x0 < w:
			px[x, y0 + i] = noisy(r["sash_thread"] if i % 3 == 0 else r["sash"], rnd, 4) + (255,)
			if x - 1 >= x0:
				px[x - 1, y0 + i] = noisy(r["sash"], rnd, 4) + (255,)


# ---------------------------------------------------------------- robe model textures (64x64)
ROBE_UV = {
	"hood": (0, 0, 8, 8, 8), "brim": (0, 57, 9, 2, 2), "flap": (56, 0, 1, 7, 2), "cowl": (0, 32, 9, 2, 5),
	"torso": (32, 0, 8, 12, 4), "sleeve": (0, 16, 4, 12, 4), "cuff": (24, 39, 5, 4, 5), "back_panel": (48, 16, 7, 9, 1),
	"belt": (28, 32, 8, 2, 4), "mantle": (30, 50, 10, 3, 6), "sash": (56, 26, 2, 15, 1),
	"waist": (0, 39, 8, 3, 4), "leg": (16, 16, 4, 12, 4), "skirt_front": (44, 39, 4, 10, 1), "skirt_back": (0, 46, 4, 10, 1),
	"boot": (32, 16, 4, 5, 4), "wrap": (10, 48, 5, 2, 5),
}


def robe_model_texture(asset):
	kind = ROBE_BASE[asset]
	rank = asset if asset in ("jedi_knight", "sith_lord") else None
	p = PALETTE[kind]
	r = PALETTE[rank] if rank else None
	rnd = random.Random({"jedi_robes": 3001, "sith_robes": 3002, "jedi_knight": 3003, "sith_lord": 3004}[asset])
	img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
	px = img.load()

	def box(name, painter):
		u, v, w, h, d = ROBE_UV[name]
		paint_box(px, u, v, w, h, d, painter)

	if rank is None:
		box("hood", hood_painter(kind, rnd))

		def brim(face, fx, fy, fw, fh):
			if face == "bottom":
				return cloth(p["lining"], rnd, fx, fy)
			return cloth(p["outer_dark"] if face == "front" else p["outer"], rnd, fx, fy)

		box("brim", brim)

		def flap(face, fx, fy, fw, fh):
			if face in ("west",):
				return cloth(p["lining"], rnd, fx, fy)
			return cloth(p["outer"] if fy < fh - 1 else p["outer_dark"], rnd, fx, fy)

		box("flap", flap)

		def cowl(face, fx, fy, fw, fh):
			if face == "bottom":
				return None
			if face == "top":
				return cloth(p["outer_light"] if (fx + fy) % 4 == 0 else p["outer"], rnd, fx, fy)
			return cloth(p["outer_dark"] if fy == fh - 1 else p["outer"], rnd, fx, fy)

		box("cowl", cowl)

	box("torso", robe_body_painter(kind, rnd, rank))
	box("sleeve", robe_arm_painter(kind, rnd, rank))

	def cuff(face, fx, fy, fw, fh):
		if face == "top":
			return None
		if face == "bottom":
			return noisy(p["outer_dark"] if fx in (0, fw - 1) or fy in (0, fh - 1) else p["leather_dark"], rnd, 3)
		return cloth(p["outer_dark"] if fy == fh - 1 else p["outer"], rnd, fx, fy)

	box("cuff", cuff)

	def back_panel(face, fx, fy, fw, fh):
		if face in ("top", "bottom"):
			return cloth(p["outer_dark"], rnd, fx, fy)
		if fy == fh - 1:
			return cloth(p["lining"], rnd, fx, fy)
		return cloth(p["outer"] if face != "front" else p["outer_dark"], rnd, fx, fy, 5, 2)

	box("back_panel", back_panel)

	def belt(face, fx, fy, fw, fh):
		if face == "front" and fx in (3, 4):
			return noisy(p["buckle"], rnd, 5)
		if face == "front" and fx == 6 and fy == 1:
			return noisy(p["leather_dark"], rnd, 3)
		return noisy(p["leather"], rnd, 4)

	box("belt", belt)

	if r is not None:
		def mantle(face, fx, fy, fw, fh):
			if face == "bottom":
				return cloth(r["mantle_dark"], rnd, fx, fy)
			if face == "top":
				return cloth(r["mantle"] if (fx + fy) % 5 else r["mantle_dark"], rnd, fx, fy)
			if fy == fh - 1:
				return noisy(r["mantle_trim"], rnd, 4)
			return cloth(r["mantle"], rnd, fx, fy)

		box("mantle", mantle)

		def sash_part(face, fx, fy, fw, fh):
			if face in ("top", "bottom"):
				return noisy(r["sash"], rnd, 3)
			if face == "front" and fy % 3 == 1:
				return noisy(r["sash_thread"], rnd, 4)
			return noisy(r["sash"], rnd, 4)

		box("sash", sash_part)
		return img

	def waist(face, fx, fy, fw, fh):
		if face == "top":
			return None
		return cloth(p["sash"] if fy == 0 and face != "bottom" else p["trousers"], rnd, fx, fy, 4)

	box("waist", waist)
	box("leg", trouser_painter(kind, rnd))

	def skirt(dark):
		def paint(face, fx, fy, fw, fh):
			if face in ("top", "bottom"):
				return cloth(p["outer_dark"], rnd, fx, fy)
			if fy == fh - 1:
				return cloth(p["lining"], rnd, fx, fy)
			c = p["tunic"] if kind == "jedi" and not dark else (p["outer_dark"] if dark else p["outer"])
			if face != "front" and face != "back":
				c = p["outer_dark"]
			return cloth(c, rnd, fx, fy, 4, 2)

		return paint

	box("skirt_front", skirt(False))
	box("skirt_back", skirt(True))

	def boot(face, fx, fy, fw, fh):
		if face == "top":
			return None
		if face == "bottom":
			return noisy(p["leather_dark"], rnd, 3)
		return noisy(p["leather_dark"] if fy == fh - 1 else p["leather"], rnd, 4)

	box("boot", boot)

	def wrap(face, fx, fy, fw, fh):
		if face in ("top", "bottom"):
			return None
		band = (fx + fy) % 2 == 0
		return cloth(p["outer_dark"] if band else p["outer"], rnd, fx, fy, 4)

	box("wrap", wrap)
	return img


# ---------------------------------------------------------------- item icons (16x16)
# Masks: '.' empty, 'o' outline, 'p' main, 'l' light, 'd' dark, 'a' accent, 'v' visor or opening, 'b' belt, 'g' glint.
ICONS = {
	"helmet_republic": [
		"................",
		"................",
		".....oooooo.....",
		"....olllllpo....",
		"...ollpppppdo...",
		"...olpppppppo...",
		"...ovvvvvvvvo...",
		"...ovgvvvvvvo...",
		"...oppppppppo...",
		"...opddppddpo...",
		"...opppppppdo...",
		"....oddddddo....",
		".....oooooo.....",
		"................",
		"................",
		"................",
	],
	"helmet_imperial": [
		"................",
		"................",
		"......oooo......",
		"....oollllpoo...",
		"...olppllpppdo..",
		"...olllllllldo..",
		"...ovvplplvvdo..",
		"...oppplppppdo..",
		"...odpplppdpdo..",
		"....odplppdpo...",
		"....oaaaaaaao...",
		".....oddddoo....",
		"......oooo......",
		"................",
		"................",
		"................",
	],
	"chest": [
		"................",
		"..ooo......ooo..",
		".olaao....oaapo.",
		".olppooooooppdo.",
		".olpppllllpppdo.",
		"..opppplpppppo..",
		"..olpppldpppdo..",
		"..oopppldppdoo..",
		"...olppldpppo...",
		"...olddddddpo...",
		"...oppppppppo...",
		"...olddddddpo...",
		"...obbbaabbbo...",
		"...oddddddddo...",
		"....oooooooo....",
		"................",
	],
	"legs": [
		"................",
		"...oooooooooo...",
		"...obbbaabbbo...",
		"...olpppppppo...",
		"...olpppdpppo...",
		"...olppoolppo...",
		"...olpdooldpo...",
		"...ollpo.olpo...",
		"...ollpo.olpo...",
		"...odddo.oddo...",
		"...olpdo.olpo...",
		"...olpdo.olpo...",
		"...olpdo.olpo...",
		"...oddoo.oddo...",
		"...oooo..oooo...",
		"................",
	],
	"boots": [
		"................",
		"................",
		"................",
		"................",
		"...oooo..oooo...",
		"...olpo..olpo...",
		"...olpo..olpo...",
		"...olao..olao...",
		"...olpo..olpo...",
		"...olpo..olpo...",
		"..oolpo.oolpo...",
		".olppdo.olppdo..",
		".olppdo.olppdo..",
		".odddddoodddddo.",
		".ooooooo.oooooo.",
		"................",
	],
	"hood": [
		"................",
		"......oooo......",
		".....ollppo.....",
		"....olpppppo....",
		"...olpppppppo...",
		"...olpvvvvppo...",
		"..olpvvvvvvppo..",
		"..olpvvvvvvpdo..",
		"..olpvvvvvvpdo..",
		"..olppvvvvppdo..",
		".olpppppppppddo.",
		".olppppppppppdo.",
		".oddddddddddddo.",
		"..oooooooooooo..",
		"................",
		"................",
	],
	"robe": [
		"................",
		"...ooo....ooo...",
		"..olppoooopppo..",
		".olppplllllppdo.",
		".olpppldaldppdo.",
		"olppppladldpppdo",
		"olpp.oladlo.ppdo",
		"opdo.olaado.opdo",
		"oooo.obbbbo.oooo",
		".....olaalo.....",
		".....olaado.....",
		"....olpaadpo....",
		"....olppppdo....",
		"....olppppdo....",
		"....oddddddo....",
		".....oooooo.....",
	],
	"trousers": [
		"................",
		"...oooooooooo...",
		"...oaaaaaaaao...",
		"...olpppppppo...",
		"...olpppppdpo...",
		"...olppoolppo...",
		"...olpdo.olpo...",
		"...olpdo.oldo...",
		"...olpdo.oldo...",
		"...olpdo.olpo...",
		"...olpdo.olpo...",
		"...olpdo.oldo...",
		"...odddo.oddo...",
		"...obbbo.obbo...",
		"...oooo..oooo...",
		"................",
	],
	"robe_boots": [
		"................",
		"................",
		"................",
		"................",
		"...oooo..oooo...",
		"...odpo..odpo...",
		"...oapo..oapo...",
		"...odpo..odpo...",
		"...oapo..oapo...",
		"...odpo..odpo...",
		"..oolpo.oolpo...",
		".olppdo.olppdo..",
		".olppdo.olppdo..",
		".odddddoodddddo.",
		".ooooooo.oooooo.",
		"................",
	],
}

# Colour roles per icon set: main, light, dark, accent, visor/opening, belt, glint, outline.
ICON_COLOURS = {
	"republic": {"p": (210, 206, 194), "l": (236, 233, 224), "d": (160, 156, 146), "a": (176, 146, 82),
			"v": (44, 66, 92), "b": (62, 58, 54), "g": (150, 190, 220), "o": (40, 44, 54)},
	"imperial": {"p": (70, 70, 76), "l": (104, 104, 112), "d": (46, 46, 52), "a": (150, 32, 42),
			"v": (14, 12, 16), "b": (30, 28, 32), "g": (110, 60, 64), "o": (14, 13, 16)},
	"jedi": {"p": (118, 84, 52), "l": (146, 110, 72), "d": (84, 58, 36), "a": (214, 200, 168),
			"v": (40, 30, 22), "b": (86, 58, 36), "g": (226, 210, 150), "o": (40, 28, 18)},
	"sith": {"p": (40, 36, 42), "l": (64, 58, 66), "d": (24, 22, 26), "a": (130, 26, 34),
			"v": (10, 8, 10), "b": (28, 24, 26), "g": (150, 152, 160), "o": (8, 7, 9)},
	"jedi_knight": {"p": (118, 84, 52), "l": (146, 110, 72), "d": (84, 58, 36), "a": (232, 220, 184),
			"v": (40, 30, 22), "b": (200, 166, 88), "g": (226, 210, 150), "o": (40, 28, 18)},
	"sith_lord": {"p": (40, 36, 42), "l": (64, 58, 66), "d": (24, 22, 26), "a": (150, 152, 160),
			"v": (10, 8, 10), "b": (120, 22, 30), "g": (150, 152, 160), "o": (8, 7, 9)},
	"jedi_trousers": {"p": (176, 156, 120), "l": (200, 182, 146), "d": (140, 122, 92), "a": (196, 178, 140),
			"v": (40, 30, 22), "b": (86, 58, 36), "g": (226, 210, 150), "o": (50, 38, 26)},
	"sith_trousers": {"p": (54, 48, 56), "l": (76, 70, 80), "d": (34, 30, 36), "a": (118, 24, 32),
			"v": (10, 8, 10), "b": (28, 24, 26), "g": (150, 152, 160), "o": (8, 7, 9)},
}

ITEMS = {
	"republic_trooper_helmet": ("helmet_republic", "republic"),
	"republic_trooper_chestplate": ("chest", "republic"),
	"republic_trooper_leggings": ("legs", "republic"),
	"republic_trooper_boots": ("boots", "republic"),
	"sith_trooper_helmet": ("helmet_imperial", "imperial"),
	"sith_trooper_chestplate": ("chest", "imperial"),
	"sith_trooper_leggings": ("legs", "imperial"),
	"sith_trooper_boots": ("boots", "imperial"),
	"jedi_hood": ("hood", "jedi"),
	"jedi_robe": ("robe", "jedi"),
	"jedi_knight_robe": ("robe", "jedi_knight"),
	"jedi_trousers": ("trousers", "jedi_trousers"),
	"jedi_boots": ("robe_boots", "jedi"),
	"sith_hood": ("hood", "sith"),
	"sith_robe": ("robe", "sith"),
	"sith_lord_robe": ("robe", "sith_lord"),
	"sith_trousers": ("trousers", "sith_trousers"),
	"sith_boots": ("robe_boots", "sith"),
}


def icon(item_id):
	mask_name, colour_set = ITEMS[item_id]
	mask = ICONS[mask_name]
	colours = ICON_COLOURS[colour_set]
	rnd = random.Random(sum(ord(ch) for ch in item_id))
	img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
	px = img.load()
	for y, row in enumerate(mask):
		assert len(row) == 16, (mask_name, y)
		for x, ch in enumerate(row):
			if ch == ".":
				continue
			c = colours[ch]
			if ch not in ("o", "g", "v"):
				c = noisy(c, rnd, 5)
			px[x, y] = c + (255,)
	# The rank 3 robes carry a mantle across the shoulders.
	if item_id in ("jedi_knight_robe", "sith_lord_robe"):
		mantle = PALETTE[colour_set]["mantle"]
		trim = PALETTE[colour_set]["mantle_trim"]
		for x in range(2, 14):
			for y in (2, 3):
				if px[x, y][3] and mask[y][x] != "o":
					px[x, y] = noisy(mantle, rnd, 4) + (255,)
			if px[x, 4][3] and mask[4][x] != "o":
				px[x, 4] = trim + (255,)
	out(f"item/{item_id}.png", img)


# ---------------------------------------------------------------- JSON


def item_json(name):
	write_json(f"models/item/{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"oldrepublic:item/{name}"}})
	write_json(f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"oldrepublic:item/{name}"}})


def equipment_json(asset, leggings):
	layers = {"humanoid": [{"texture": f"oldrepublic:{asset}"}]}
	if leggings:
		layers["humanoid_leggings"] = [{"texture": f"oldrepublic:{asset}"}]
	write_json(f"equipment/{asset}.json", {"layers": layers})


def main():
	for army, asset in (("republic", "republic_trooper"), ("imperial", "sith_trooper")):
		humanoid, leggings = trooper_layers(army)
		out(f"entity/equipment/humanoid/{asset}.png", humanoid)
		out(f"entity/equipment/humanoid_leggings/{asset}.png", leggings)
		equipment_json(asset, True)

	for asset in ("jedi_robes", "sith_robes", "jedi_knight", "sith_lord"):
		humanoid, leggings = robe_layers(asset)
		out(f"entity/equipment/humanoid/{asset}.png", humanoid)
		if leggings is not None:
			out(f"entity/equipment/humanoid_leggings/{asset}.png", leggings)
		equipment_json(asset, leggings is not None)
		out(f"entity/robe/{asset}.png", robe_model_texture(asset))

	for item_id in ITEMS:
		icon(item_id)
		item_json(item_id)


if __name__ == "__main__":
	main()
