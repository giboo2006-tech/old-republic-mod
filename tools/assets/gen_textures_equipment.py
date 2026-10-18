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

Milestone 4 (W4) additions (written by m4(); `--m4` writes only these):
  textures/entity/equipment/humanoid/<asset>.png            watch_battle, sith_war, mandalorian, spacer, kaast
  textures/entity/equipment/humanoid_leggings/<asset>.png   the same but kaast (helmet and chest only)
  textures/entity/robe/{watch_battle,sith_war}.png          64x64 cloth over the chest plate (PLATE_CLOTH_UV,
                                                            mirrored in RobeModel.createPlateCloth: back panel
                                                            0,0 9x20x1; front panel 24,0 6x10x1; hood fold 0,24 8x3x2)
  textures/item/<18 M4 armour ids, 4 weapons, 11 mods, jet_pack>.png   16x16 icons
  textures/item/{heavy_repeater,kaast_longshot}_model.png   16x16 swatches for the 3D in-hand models
  textures/particle/{steam_vent,flame_jet}_0..3.png         8x8 white frames (tinted in code)
  equipment/<asset>.json, items/<id>.json, models/item/<id>[_in_hand].json, particles/{steam_vent,flame_jet}.json

Run from the repository root: python3 tools/assets/gen_textures_equipment.py [--m4]
"""
import json
import os
import random
import sys

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


# ---------------------------------------------------------------- Milestone 4 (W4): the four sets, Kaast's pieces
# Original shapes and colours (PLACEHOLDER art). None copies a film or game costume: the Mandalorian
# helmet is a plain T-visor slab with a side range-finder stalk, the Sith war helm a half-helm with two
# bronze horn stubs, the Watch helm an open-faced cap with a high collar.
M4_PALETTE = {
	"watch": {
		"plate": (78, 104, 82), "plate_light": (106, 134, 106), "plate_dark": (54, 74, 58),
		"bone": (214, 204, 178), "bone_dark": (172, 162, 136), "trim": (190, 160, 96),
		"suit": (66, 56, 44), "suit_dark": (44, 38, 30), "belt": (74, 54, 36), "buckle": (190, 160, 96),
		"glove": (58, 46, 36), "cloth": (196, 186, 158), "cloth_dark": (160, 150, 124),
	},
	"sithwar": {
		"plate": (38, 33, 38), "plate_light": (62, 56, 62), "plate_dark": (22, 19, 23),
		"bronze": (156, 108, 58), "bronze_dark": (108, 72, 38), "red": (136, 26, 30), "red_dark": (92, 16, 20),
		"suit": (30, 24, 28), "suit_dark": (16, 13, 16), "belt": (38, 30, 28), "buckle": (156, 108, 58),
		"glove": (22, 18, 20),
	},
	"mando": {
		"plate": (94, 106, 112), "plate_light": (128, 140, 144), "plate_dark": (64, 72, 78),
		"paint": (152, 86, 52), "visor": (16, 18, 20), "visor_light": (62, 72, 78), "device": (176, 62, 50),
		"suit": (54, 50, 44), "suit_dark": (36, 33, 30), "belt": (62, 50, 38), "buckle": (170, 150, 90),
		"glove": (40, 36, 32), "kama": (90, 76, 56), "kama_dark": (66, 54, 40),
	},
	"kaast": {
		"plate": (72, 68, 62), "plate_light": (102, 96, 88), "plate_dark": (46, 42, 38),
		"paint": (198, 154, 66), "visor": (34, 10, 12), "visor_light": (124, 34, 36), "device": (198, 154, 66),
		"suit": (42, 36, 32), "suit_dark": (28, 24, 22), "belt": (52, 40, 30), "buckle": (198, 154, 66),
		"glove": (32, 28, 26), "kama": (96, 34, 30), "kama_dark": (68, 24, 22),
	},
	"spacer": {
		"jacket": (122, 86, 54), "jacket_light": (150, 110, 72), "jacket_dark": (88, 60, 38),
		"shirt": (178, 170, 150), "trousers": (86, 90, 74), "trousers_dark": (62, 66, 54),
		"belt": (60, 44, 30), "pouch": (112, 94, 64), "buckle": (184, 174, 144),
		"lens": (124, 172, 184), "rim": (72, 66, 58), "cap": (98, 72, 48), "cap_dark": (72, 52, 34),
		"boot": (72, 52, 36), "boot_dark": (48, 34, 24), "glove": (64, 48, 34),
	},
}
M4_SETS = {"watch_battle": "watch", "sith_war": "sithwar", "mandalorian": "mando", "spacer": "spacer", "kaast": "kaast"}


def worn(c, rnd, fx, fy, fw, fh, amount=6):
	"""A plate texel with a bevel and a little wear."""
	return noisy(bevel(c, fx, fy, fw, fh), rnd, amount)


def m4_head(kind, rnd):
	p = M4_PALETTE[kind]

	def watch(face, fx, fy, fw, fh):
		if face == "bottom":
			return None
		if face == "top":
			return worn(p["bone"] if fx in (3, 4) else p["plate"], rnd, fx, fy, fw, fh)
		if face == "front":
			if fy == 0:
				return noisy(p["plate_light"], rnd, 4)
			if fy == 1:
				return noisy(p["bone"] if 2 <= fx <= 5 else p["plate"], rnd, 4)
			if fx in (0, 7) and fy <= 5:
				return worn(p["plate"], rnd, fx, fy, fw, fh)
			if fy >= 6 and fx in (0, 1, 6, 7):
				return noisy(p["bone"] if fy == 6 else p["bone_dark"], rnd, 4)
			return None
		if is_side(face):
			if fy >= 6:
				return noisy(p["bone"] if fy == 6 else p["bone_dark"], rnd, 4)
			if front_edge(face, fx, fw) == 0:
				return noisy(p["trim"], rnd, 4)
			return worn(p["plate"], rnd, fx, fy, fw, fh)
		if fy >= 5:
			return noisy(p["bone"] if fy == 5 else p["bone_dark"], rnd, 4)
		return worn(p["plate_light"] if fx in (3, 4) else p["plate"], rnd, fx, fy, fw, fh)

	def sithwar(face, fx, fy, fw, fh):
		if face == "bottom":
			return None
		if face == "top":
			if fx in (0, 1, 6, 7) and fy <= 2:
				return noisy(p["bronze"] if fy < 2 else p["bronze_dark"], rnd, 5)
			return worn(p["red"] if fx in (3, 4) else p["plate"], rnd, fx, fy, fw, fh)
		if face == "front":
			if fy <= 2:
				return worn(p["plate"], rnd, fx, fy, fw, 3)
			if fy == 3:
				return noisy(p["bronze"], rnd, 4)
			if fx in (0, 7) and fy <= 5:
				return worn(p["plate_dark"], rnd, fx, fy, fw, fh)
			return None
		if is_side(face):
			edge = front_edge(face, fx, fw)
			if fy <= 1 and 1 <= edge <= 3:
				return noisy(p["bronze"], rnd, 5)
			if fy <= 5:
				return worn(p["plate"], rnd, fx, fy, fw, 6) if fy != 5 else noisy(p["bronze_dark"], rnd, 4)
			return None
		if fy <= 6:
			return worn(p["red_dark"] if fx in (3, 4) else p["plate"], rnd, fx, fy, fw, 7)
		return None

	def mando(face, fx, fy, fw, fh):
		if face == "bottom":
			return noisy(p["suit_dark"], rnd, 3)
		if face == "top":
			return worn(p["paint"] if fx in (3, 4) else p["plate"], rnd, fx, fy, fw, fh)
		if face == "front":
			if fy == 3 and 1 <= fx <= 6 or fx in (3, 4) and 4 <= fy <= 6:
				return noisy(p["visor_light"] if (fx, fy) == (1, 3) else p["visor"], rnd, 2)
			if fy == 7:
				return noisy(p["plate_dark"], rnd, 4)
			if fx in (0, 1) and 4 <= fy <= 6 or fx in (6, 7) and 4 <= fy <= 6:
				return noisy(p["paint"] if (fx + fy) % 3 else p["plate_light"], rnd, 6)
			return worn(p["plate"], rnd, fx, fy, fw, fh)
		if is_side(face):
			edge = front_edge(face, fx, fw)
			# The range-finder stalk on the left side only.
			if face == "east" and edge in (2, 3) and fy <= 3:
				return noisy(p["device"] if fy == 0 else p["plate_dark"], rnd, 3)
			if fy == 7:
				return noisy(p["plate_dark"], rnd, 4)
			if 3 <= fy <= 5 and 3 <= fx <= 5:
				return noisy(p["plate_light"], rnd, 4)
			return worn(p["plate"], rnd, fx, fy, fw, fh)
		if fy in (5, 6) and 2 <= fx <= 5:
			return noisy(p["paint"], rnd, 6)
		return worn(p["plate"], rnd, fx, fy, fw, fh)

	def spacer(face, fx, fy, fw, fh):
		if face == "bottom":
			return None
		if face == "top":
			return noisy(p["cap"] if (fx + fy) % 3 else p["cap_dark"], rnd, 4)
		if face == "front":
			if fy <= 1:
				return noisy(p["cap"] if fy == 0 else p["cap_dark"], rnd, 4)
			if fy == 2:
				if fx in (1, 2, 5, 6):
					return noisy(p["lens"], rnd, 3) if fx in (2, 5) else noisy(shade(p["lens"], 1.25), rnd, 3)
				return noisy(p["rim"], rnd, 3)
			return None
		if is_side(face):
			edge = front_edge(face, fx, fw)
			if fy <= 1:
				return noisy(p["cap"], rnd, 4)
			if fy == 2:
				return noisy(p["rim"], rnd, 3)
			if 3 <= fy <= 5 and 2 <= edge <= 5:
				return noisy(p["cap_dark"] if fy == 5 else p["cap"], rnd, 4)
			return None
		if fy <= 3:
			return noisy(p["rim"] if fy == 2 else p["cap"], rnd, 4)
		return None

	return {"watch": watch, "sithwar": sithwar, "mando": mando, "kaast": mando, "spacer": spacer}[kind]


def m4_body(kind, rnd):
	p = M4_PALETTE[kind]

	def watch(face, fx, fy, fw, fh):
		if face == "top":
			return worn(p["bone"], rnd, fx, fy, fw, fh)
		if face == "bottom":
			return noisy(p["suit_dark"], rnd, 3)
		if fy == 10:
			return noisy(p["buckle"] if face == "front" and fx in (3, 4) else p["belt"], rnd, 4)
		if face == "front":
			if fy == 0:
				return noisy(p["bone"], rnd, 4)
			if fy <= 6:
				if fx in (0, 7) or fy == 6:
					return noisy(p["bone_dark"], rnd, 4)
				return worn(p["plate_light"] if fx in (3, 4) and fy <= 2 else p["plate"], rnd, fx, fy, fw, 7)
			# The tabard's lower half (with robe models off this is all of it).
			if 2 <= fx <= 5:
				return cloth(p["cloth"] if fx in (3, 4) else p["cloth_dark"], rnd, fx, fy, 4)
			return noisy(p["suit"], rnd, 4)
		if face == "back":
			if fy <= 6:
				return worn(p["plate"], rnd, fx, fy, fw, 7)
			return cloth(p["cloth_dark"], rnd, fx, fy, 4)
		if fy <= 6 and front_edge(face, fx, fw) <= 1:
			return worn(p["plate"], rnd, fx, fy, fw, fh)
		return noisy(p["suit"], rnd, 4)

	def sithwar(face, fx, fy, fw, fh):
		if face == "top":
			return worn(p["bronze_dark"] if fx in (0, 7) else p["plate"], rnd, fx, fy, fw, fh)
		if face == "bottom":
			return noisy(p["suit_dark"], rnd, 3)
		if fy == 10:
			return noisy(p["buckle"] if face == "front" and fx in (3, 4) else p["belt"], rnd, 4)
		if face == "front":
			if fy == 0 or fx in (0, 7) and fy <= 8:
				return noisy(p["bronze"], rnd, 5)
			if 2 <= fy <= 5 and fx in (3, 4):
				return noisy(p["red"] if fy < 5 else p["red_dark"], rnd, 4)
			if fy <= 6:
				return worn(p["plate"], rnd, fx, fy, fw, 7)
			if fy in (7, 9):
				return noisy(p["plate_light"], rnd, 4)
			return noisy(p["plate_dark"] if fy == 8 else p["suit"], rnd, 4)
		if face == "back":
			if fy <= 8:
				return worn(p["plate"], rnd, fx, fy, fw, 9) if fx not in (0, 7) else noisy(p["bronze_dark"], rnd, 4)
			return noisy(p["suit"], rnd, 4)
		if fy <= 8 and front_edge(face, fx, fw) <= 1:
			return worn(p["plate"], rnd, fx, fy, fw, fh)
		return noisy(p["suit"], rnd, 4)

	def mando(face, fx, fy, fw, fh):
		if face == "top":
			return worn(p["plate"], rnd, fx, fy, fw, fh)
		if face == "bottom":
			return noisy(p["suit_dark"], rnd, 3)
		if fy == 10:
			return noisy(p["buckle"] if face == "front" and fx in (2, 5) else p["belt"], rnd, 4)
		if face == "front":
			if fy == 0 and 2 <= fx <= 5:
				return noisy(p["suit"], rnd, 4)
			if fy <= 5:
				# Two chest plates with a seam down the middle; worn paint on the left one.
				if fx in (3, 4):
					return noisy(p["plate_dark"] if fx == 4 else p["plate"], rnd, 4)
				if fx <= 2 and 1 <= fy <= 3:
					return noisy(p["paint"] if (fx * 3 + fy) % 5 else p["plate_light"], rnd, 7)
				return worn(p["plate"], rnd, fx, fy, fw, 6)
			if fy in (6, 8) and 1 <= fx <= 6:
				return worn(p["plate_light"], rnd, fx - 1, 0, 6, 1)
			return noisy(p["suit"], rnd, 4)
		if face == "back":
			if fy <= 6:
				if 2 <= fx <= 5 and 1 <= fy <= 5:
					return noisy(p["plate_dark"] if fx in (2, 5) or fy in (1, 5) else p["plate"], rnd, 4)
				return worn(p["plate"], rnd, fx, fy, fw, 7)
			return noisy(p["suit"], rnd, 4)
		if 1 <= fy <= 5 and front_edge(face, fx, fw) <= 1:
			return worn(p["plate"], rnd, fx, fy, fw, fh)
		return noisy(p["suit"], rnd, 4)

	def spacer(face, fx, fy, fw, fh):
		if face == "top":
			return noisy(p["jacket_light"], rnd, 4)
		if face == "bottom":
			return noisy(p["jacket_dark"], rnd, 3)
		if fy == 10:
			return noisy(p["buckle"] if face == "front" and fx in (3, 4) else p["belt"], rnd, 4)
		if face == "front":
			# Open collar over the shirt, then a quilted front with a zip line.
			if fy <= 2 and 3 - fy // 2 <= fx <= 4 + fy // 2 - (0 if fy else 1):
				return noisy(p["shirt"], rnd, 4)
			if fx == 4 and fy >= 3:
				return noisy(p["jacket_dark"], rnd, 3)
		quilt = fy % 3 == 1
		return noisy(p["jacket_dark"] if quilt else p["jacket"], rnd, 5)

	return {"watch": watch, "sithwar": sithwar, "mando": mando, "kaast": mando, "spacer": spacer}[kind]


def m4_arm(kind, rnd):
	p = M4_PALETTE[kind]

	def paint(face, fx, fy, fw, fh):
		if kind == "spacer":
			if face == "bottom" or fy == 11:
				return noisy(p["glove"], rnd, 4)
			if fy == 10:
				return noisy(p["jacket_dark"], rnd, 3)
			return noisy(p["jacket_dark"] if fy % 3 == 1 else p["jacket"], rnd, 5)
		if face == "bottom" or fy == 11:
			return noisy(p["glove"], rnd, 4)
		if fy <= 3:
			if kind == "watch":
				# Leaf-cut pauldron: bone, its lower edge cut into points.
				if face == "top":
					return worn(p["bone"], rnd, fx, fy, fw, fh)
				if fy == 3:
					return noisy(p["bone_dark"], rnd, 4) if fx % 2 == 0 else noisy(p["suit"], rnd, 4)
				return worn(p["bone"] if fy < 2 else p["plate"], rnd, fx, fy, fw, 4)
			if kind == "sithwar":
				if face == "top" and fx in (1, 2) and fy in (1, 2):
					return noisy(p["bronze"], rnd, 5)
				return noisy(p["bronze"], rnd, 4) if fy == 3 else worn(p["plate"], rnd, fx, fy, fw, 4)
			if face != "top" and fy == 1:
				return noisy(p["paint"], rnd, 7)
			return worn(p["plate"], rnd, fx, fy, fw, 4)
		if fy <= 6:
			return noisy(p["suit"], rnd, 4)
		if fy <= 10:
			if kind == "sithwar" and fy == 7:
				return noisy(p["bronze"], rnd, 4)
			if kind in ("mando", "kaast") and face == "front" and fy == 8 and fx in (1, 2):
				return noisy(p["device"], rnd, 4)
			return worn(p["plate"], rnd, fx, fy - 7, fw, 4)
		return noisy(p["glove"], rnd, 4)

	return paint


def m4_boot(kind, rnd):
	p = M4_PALETTE[kind]

	def paint(face, fx, fy, fw, fh):
		if face == "top" or fy < 7:
			return None
		if kind == "spacer":
			if face == "bottom" or fy == 11:
				return noisy(p["boot_dark"], rnd, 3)
			if face == "front" and fy == 8 and fx in (1, 2):
				return noisy(p["buckle"], rnd, 4)
			return noisy(p["boot"], rnd, 4)
		if face == "bottom" or fy == 11:
			return noisy(p["glove"], rnd, 3)
		if kind == "watch" and face == "front" and fy >= 9:
			return noisy(p["bone"], rnd, 4)
		if kind == "sithwar" and fy == 7:
			return noisy(p["bronze"], rnd, 4)
		if kind in ("mando", "kaast") and fy == 7 and face == "front":
			return noisy(p["paint"], rnd, 6)
		return worn(p["plate"], rnd, fx, fy - 7, fw, 4)

	return paint


def m4_waist(kind, rnd):
	p = M4_PALETTE[kind]

	def paint(face, fx, fy, fw, fh):
		if face == "top" or fy < 9:
			return None
		if face == "bottom":
			return noisy(p.get("suit_dark", p.get("trousers_dark")), rnd, 3)
		if kind == "spacer":
			# Belt pouches over the hips.
			if face == "front" and fy >= 10 and fx in (1, 2, 5, 6):
				return noisy(p["pouch"], rnd, 4)
			return noisy(p["trousers"], rnd, 4)
		if kind in ("mando", "kaast") and face in ("east", "west", "back"):
			return cloth(p["kama"], rnd, fx, fy, 4)
		if face == "front" and fy >= 10 and fx in (3, 4):
			return worn(p["plate"], rnd, fx, fy, fw, fh)
		return noisy(p["suit"], rnd, 4)

	return paint


def m4_legging(kind, rnd):
	p = M4_PALETTE[kind]

	def paint(face, fx, fy, fw, fh):
		if face == "top":
			return None
		if kind == "spacer":
			if face == "bottom":
				return noisy(p["trousers_dark"], rnd, 3)
			if is_side(face) and 3 <= fy <= 5 and 1 <= fx <= 2:
				return noisy(p["trousers_dark"] if fy == 3 else p["pouch"], rnd, 4)
			return noisy(p["trousers_dark"] if fx == 0 and face == "front" else p["trousers"], rnd, 4)
		if face == "bottom":
			return noisy(p["suit_dark"], rnd, 3)
		if kind in ("mando", "kaast") and fy <= 6 and (face in ("back", "west") or face == "east" and front_edge(face, fx, fw) >= 1):
			# The kama: cloth flaps over the back and sides of the thighs.
			return cloth(p["kama_dark"] if fy == 6 else p["kama"], rnd, fx, fy, 4)
		if fy <= 4:
			if face == "front" or is_side(face) and front_edge(face, fx, fw) <= 1:
				if kind == "sithwar" and fx in (0, 3) and face == "front":
					return noisy(p["bronze_dark"], rnd, 4)
				return worn(p["plate"], rnd, fx, fy, fw, 5)
			return noisy(p["suit"], rnd, 4)
		if fy in (5, 6) and face == "front":
			knee = p["bone"] if kind == "watch" else p["plate_light"]
			return noisy(knee if fy == 5 else p["plate_dark"], rnd, 4)
		return noisy(p["suit"], rnd, 4)

	return paint


def m4_layers(asset):
	"""The plain armour layers of an M4 set: (humanoid 64x32, humanoid_leggings 64x32 or None for Kaast)."""
	kind = M4_SETS[asset]
	rnd = random.Random({"watch_battle": 4101, "sith_war": 4102, "mandalorian": 4103, "spacer": 4104, "kaast": 4105}[asset])
	humanoid = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
	px = humanoid.load()
	paint_box(px, 0, 0, 8, 8, 8, m4_head(kind, rnd))
	paint_box(px, 16, 16, 8, 12, 4, m4_body(kind, rnd))
	paint_box(px, 40, 16, 4, 12, 4, m4_arm(kind, rnd))
	paint_box(px, 0, 16, 4, 12, 4, m4_boot(kind, rnd))

	if asset == "kaast":
		return humanoid, None

	leggings = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
	px = leggings.load()
	paint_box(px, 16, 16, 8, 12, 4, m4_waist(kind, rnd))
	paint_box(px, 0, 16, 4, 12, 4, m4_legging(kind, rnd))
	return humanoid, leggings


# The cloth over the clothed chest plates (64x64), mirrored in RobeModel.createPlateCloth.
PLATE_CLOTH_UV = {"back": (0, 0, 9, 20, 1), "front": (24, 0, 6, 10, 1), "hood": (0, 24, 8, 3, 2)}


def plate_cloth_texture(asset):
	"""Watch battle: a bone tabard with a green edge, a short back panel and the lowered hood.
	Sith war: a long torn red-black cape and a short front fall; no hood."""
	watch = asset == "watch_battle"
	p = M4_PALETTE["watch" if watch else "sithwar"]
	rnd = random.Random(4201 if watch else 4202)
	img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
	px = img.load()

	def box(name, painter):
		u, v, w, h, d = PLATE_CLOTH_UV[name]
		paint_box(px, u, v, w, h, d, painter)

	if watch:
		def back(face, fx, fy, fw, fh):
			if fy >= 13 or face in ("top", "bottom"):
				return None
			if fy == 12 and fx % 2 == 1:
				return None
			edge = face in ("front", "back") and fx in (0, fw - 1)
			return cloth(p["plate"] if edge else p["cloth"], rnd, fx, fy, 4)

		def front(face, fx, fy, fw, fh):
			if face in ("top", "bottom"):
				return None
			if fy == fh - 1 and fx % 2 == 1:
				return None
			if face == "front" and 2 <= fx <= 3 and 1 <= fy <= 3:
				# A leaf device on the tabard.
				return noisy(p["plate"], rnd, 4)
			edge = face in ("front", "back") and fx in (0, fw - 1)
			return cloth(p["plate"] if edge else p["cloth"], rnd, fx, fy, 4)

		def hood(face, fx, fy, fw, fh):
			if face == "bottom":
				return None
			return cloth(p["cloth_dark"] if fy == fh - 1 or face == "top" and fx % 3 == 0 else p["cloth"], rnd, fx, fy, 4)

		box("back", back)
		box("front", front)
		box("hood", hood)
	else:
		tatters = [random.Random(4300 + x).randint(0, 3) for x in range(9)]

		def back(face, fx, fy, fw, fh):
			if face == "bottom":
				return None
			if face in ("front", "back") and fy >= fh - tatters[min(fx, 8)]:
				return None
			if face == "top":
				return noisy(p["bronze_dark"], rnd, 4)
			if fy == 0:
				return noisy(p["bronze"], rnd, 4)
			# Red outside, black lining inside, darker toward the hem.
			c = p["red"] if face == "back" or is_side(face) else p["plate"]
			return cloth(shade(c, 1.0 - 0.012 * fy), rnd, fx, fy, 5)

		def front(face, fx, fy, fw, fh):
			if face in ("top", "bottom") or fy >= 6:
				return None
			if fy == 5 and fx % 2 == 0:
				return None
			return cloth(p["red_dark"] if fy == 0 else p["plate_light"], rnd, fx, fy, 4)

		box("back", back)
		box("front", front)
	return img


# ---------------------------------------------------------------- Milestone 4 icons (16x16)
M4_ICONS = {
	"helmet_open": [
		"................",
		"................",
		".....oooooo.....",
		"....oaalllpo....",
		"...olppppppdo...",
		"...olpppppppo...",
		"...olvvvvvvpo...",
		"...opv....vpo...",
		"...opv....vpo...",
		"...opvv..vvpo...",
		"...oaaa..aaao...",
		"...oaaaaaaaao...",
		"....oddddddo....",
		".....oooooo.....",
		"................",
		"................",
	],
	"helmet_horned": [
		"................",
		".ao..........oa.",
		".aao........oaa.",
		"..aaooooooooaa..",
		"...olllppppdo...",
		"...olppbbpppo...",
		"...olppbbpppo...",
		"...oaaaaaaaao...",
		"...opv....vpo...",
		"...odv....vdo...",
		"....o......o....",
		"................",
		"................",
		"................",
		"................",
		"................",
	],
	"helmet_tvisor": [
		"................",
		"................",
		".....oooooo.....",
		"....olllllpo..g.",
		"...olpaapppdo.d.",
		"...olpaappppodo.",
		"...ovvvvvvvvo...",
		"...ovgvvvvvvo...",
		"...oppvvvvppo...",
		"...oapvvvvpao...",
		"...oapvvvvpdo...",
		"....oddddddo....",
		".....oooooo.....",
		"................",
		"................",
		"................",
	],
	"cap_goggles": [
		"................",
		"................",
		"................",
		".....oooooo.....",
		"....olllpppo....",
		"...olpppppppo...",
		"...olpppppppo...",
		"..obbbbbbbbbbo..",
		"..obvvbbbbvvbo..",
		"..obvgbbbbvgbo..",
		"..obbbbbbbbbbo..",
		"...opo....opo...",
		"...opo....opo...",
		"...oo......oo...",
		"................",
		"................",
	],
	"jacket": [
		"................",
		"..ooo......ooo..",
		".olppoo..oopppo.",
		".olppplaalpppdo.",
		".olpppdaadpppdo.",
		"olppppddddppppdo",
		"olpp.oppdpo.ppdo",
		"opdo.oppdpo.opdo",
		"oooo.oppdpo.oooo",
		".....oppdpo.....",
		".....oppdpo.....",
		"....obbabbbo....",
		"....oaapaapo....",
		"....oppppppo....",
		".....oooooo.....",
		"................",
	],
}

M4_ICON_COLOURS = {
	"watch": {"p": (82, 108, 86), "l": (110, 138, 110), "d": (56, 76, 60), "a": (214, 204, 178),
			"v": (40, 32, 26), "b": (74, 54, 36), "g": (230, 222, 196), "o": (26, 32, 26)},
	"sithwar": {"p": (40, 35, 40), "l": (66, 60, 66), "d": (24, 20, 24), "a": (156, 108, 58),
			"v": (12, 10, 12), "b": (140, 28, 32), "g": (200, 150, 90), "o": (10, 8, 10)},
	"mando": {"p": (96, 108, 114), "l": (130, 142, 146), "d": (66, 74, 80), "a": (156, 88, 54),
			"v": (16, 18, 20), "b": (62, 50, 38), "g": (180, 66, 52), "o": (22, 24, 28)},
	"kaast": {"p": (74, 70, 64), "l": (104, 98, 90), "d": (48, 44, 40), "a": (200, 156, 66),
			"v": (36, 10, 12), "b": (96, 34, 30), "g": (210, 168, 76), "o": (18, 16, 14)},
	"spacer": {"p": (124, 88, 56), "l": (152, 112, 74), "d": (90, 62, 40), "a": (178, 170, 150),
			"v": (124, 172, 184), "b": (72, 66, 58), "g": (210, 236, 240), "o": (36, 26, 18)},
	"spacer_trousers": {"p": (88, 92, 76), "l": (110, 114, 96), "d": (64, 68, 56), "a": (112, 94, 64),
			"v": (40, 30, 22), "b": (60, 44, 30), "g": (184, 174, 144), "o": (30, 32, 26)},
	"spacer_boots": {"p": (74, 54, 38), "l": (100, 76, 54), "d": (50, 36, 26), "a": (184, 174, 144),
			"v": (40, 30, 22), "b": (60, 44, 30), "g": (184, 174, 144), "o": (26, 18, 12)},
}

M4_ITEMS = {
	"watch_battle_helmet": ("helmet_open", "watch"),
	"watch_battle_chest": ("chest", "watch"),
	"watch_battle_legs": ("legs", "watch"),
	"watch_battle_boots": ("boots", "watch"),
	"sith_war_helmet": ("helmet_horned", "sithwar"),
	"sith_war_chest": ("chest", "sithwar"),
	"sith_war_legs": ("legs", "sithwar"),
	"sith_war_boots": ("boots", "sithwar"),
	"mandalorian_helmet": ("helmet_tvisor", "mando"),
	"mandalorian_chest": ("chest", "mando"),
	"mandalorian_legs": ("legs", "mando"),
	"mandalorian_boots": ("boots", "mando"),
	"spacer_cap": ("cap_goggles", "spacer"),
	"spacer_jacket": ("jacket", "spacer"),
	"spacer_trousers": ("trousers", "spacer_trousers"),
	"spacer_boots": ("robe_boots", "spacer_boots"),
	"kaast_helmet": ("helmet_tvisor", "kaast"),
	"kaast_chest": ("chest", "kaast"),
}

# Weapons and mods: masks use the same letters with their own colour roles.
GEAR_ICONS = {
	"heavy_repeater": [
		"................",
		"................",
		"................",
		".....oooo.......",
		"....oaaaao......",
		"oo.oddddddoooooo",
		"olooplllllpbbbbo",
		"olpppppppppddddo",
		"olpppppppppbbbbo",
		"oddooppvpppoooo.",
		".oo.oppoocco....",
		"....oddo.cco....",
		"....oddo.oo.....",
		".....oo.........",
		"................",
		"................",
	],
	"kaast_longshot": [
		"................",
		"................",
		"................",
		"......oooooo....",
		"......oaggao....",
		"ooo...oooooo....",
		"oaaooooooooooooo",
		"olppppplllllbbbo",
		"opppppppppppoooo",
		"oddoopvppoo.....",
		".oo.oppoo.......",
		"....oddo........",
		".....oo.........",
		"................",
		"................",
		"................",
	],
	"vibrosword": [
		"............ooo.",
		"...........ollo.",
		"..........ollpo.",
		".........ollpo..",
		"........ollpo...",
		".......ollpo....",
		"......ollpo.....",
		".....ollpo......",
		"..oo.olpo.......",
		"..oaoopo........",
		"...oaao.........",
		"...obaao........",
		"..obbo.oo.......",
		".obbo...........",
		"oaao............",
		"ooo.............",
	],
	"electrostaff": [
		"............oooo",
		"...........ogvgo",
		"...........ovaao",
		"..........oaaoo.",
		".........opdo...",
		"........opdo....",
		".......opdo.....",
		"......obbo......",
		".....obbo.......",
		"....opdo........",
		"...opdo.........",
		"..opdo..........",
		".oaao...........",
		"oaavo...........",
		"ogvgo...........",
		"oooo............",
	],
	"mod_plating": [
		"................",
		"................",
		"...oooooooooo...",
		"..ollllllllpdo..",
		"..olppppppppdo..",
		"..olpappppappo..",
		"..olppppppppdo..",
		"..olppppppppdo..",
		"..olpappppapdo..",
		"..oppppppppddo..",
		"...odddddddddo..",
		"....oooooooooo..",
		"................",
		"................",
		"................",
		"................",
	],
	"mod_weave": [
		"................",
		"................",
		"..oooooooooooo..",
		"..olpdlpdlpdlo..",
		"..opdlpdlpdlpo..",
		"..odlpdlpdlpdo..",
		"..olpdlpdlpdlo..",
		"..opdlpdlpdlpo..",
		"..odlpdlpdlpdo..",
		"..olpdlpdlpdlo..",
		"..opdlpdlpdlpo..",
		"..oooooooooooo..",
		"................",
		"................",
		"................",
		"................",
	],
	"mod_attunement": [
		"................",
		"................",
		".....oooooo.....",
		"....oaaaaaao....",
		"...oaovvvvoao...",
		"..oaovgllvvoao..",
		"..oaovllvvvoao..",
		"..oaovvvvvvoao..",
		"..oaovvvvvvoao..",
		"...oaovvvvoao...",
		"....oaaaaaao....",
		".....oooooo.....",
		"................",
		"................",
		"................",
		"................",
	],
	"mod_conduit": [
		"................",
		"................",
		"...oo......oo...",
		"..oaao....oaao..",
		"..oaaooooooaao..",
		"...oolvlvlvoo...",
		"....ovlvlvlo....",
		"....olvlvlvo....",
		"....ovlvlvlo....",
		"...oolvlvlvoo...",
		"..oaaooooooaao..",
		"..oaao....oaao..",
		"...oo......oo...",
		"................",
		"................",
		"................",
	],
	"mod_stabiliser": [
		"................",
		"................",
		".....oooooo.....",
		"...ooaaaaaaoo...",
		"..oaao....oaao..",
		"..oao.oooo.oao..",
		".oao.oplpdo.oao.",
		".oao.olgpdo.oao.",
		".oao.oppddo.oao.",
		".oao.odddoo.oao.",
		"..oao.oooo.oao..",
		"..oaao....oaao..",
		"...ooaaaaaaoo...",
		".....oooooo.....",
		"................",
		"................",
	],
	"jet_pack": [
		"................",
		"..oooo....oooo..",
		".ollppo..ollppo.",
		".olppdooooppddo.",
		".olppdobbopppdo.",
		".olppdobbopppdo.",
		".olpadobbopapdo.",
		".olppdobbopppdo.",
		".olppdooooppddo.",
		".oddddo...odddo.",
		"..oaao....oaao..",
		"..ovvo....ovvo..",
		"...vv......vv...",
		"...g........g...",
		"................",
		"................",
	],
	"mod_focused_barrel": [
		"................",
		"................",
		"................",
		"................",
		"..oo........oo..",
		".oaaoooooooaaao.",
		".oaalllllllaavo.",
		".oaapppppppaago.",
		".oaaddddddaaavo.",
		".oaaoooooooaaao.",
		"..oo........oo..",
		"................",
		"................",
		"................",
		"................",
		"................",
	],
	"mod_heavy_barrel": [
		"................",
		"................",
		"................",
		"..oooooooooooo..",
		".ollllllllllldo.",
		".olppbppbppbpdo.",
		".olppbppbppbpvo.",
		".olppbppbppbpdo.",
		".odddddddddddo..",
		"..oooooooooooo..",
		"................",
		"................",
		"................",
		"................",
		"................",
		"................",
	],
	"mod_cooling_cell": [
		"................",
		"................",
		"......oooo......",
		".....oaaaao.....",
		"....oooooooo....",
		"...obvbvbvbvo...",
		"...ovlllllldo...",
		"...obvvvvvvbo...",
		"...ovlllllldo...",
		"...obvvvvvvbo...",
		"...ovlllllldo...",
		"...obvbvbvbvo...",
		"....oooooooo....",
		"................",
		"................",
		"................",
	],
	"mod_overcharged_cell": [
		"................",
		"................",
		"......oooo......",
		".....oaaaao.....",
		"....oooooooo....",
		"....oppplppo....",
		"....oppgldpo....",
		"....opglldpo....",
		"....oglllgpo....",
		"....oplggppo....",
		"....oplgpppo....",
		"....opgppddo....",
		"....oooooooo....",
		"................",
		"................",
		"................",
	],
	"mod_keen_edge": [
		"................",
		"................",
		"................",
		"................",
		"...........ooo..",
		".........oolgo..",
		".......oollgpo..",
		".....oollgppo...",
		"...oollgppdo....",
		"..olgppppdo.....",
		"..oopppddo......",
		"...oaaoo........",
		"...oaao.........",
		"....oo..........",
		"................",
		"................",
	],
	"mod_shock_edge": [
		"................",
		"................",
		"..........v.....",
		".........vg.....",
		"...........ooo..",
		"........voolgo..",
		".......oollgpo..",
		".....oollgppo...",
		"...oollgppdo.v..",
		"..olgppppdo.vg..",
		"..oopppddo...v..",
		"...oaaoo........",
		"...oaao.........",
		"....oo..........",
		"................",
		"................",
	],
}

STEEL = {"p": (150, 158, 166), "l": (206, 214, 222), "d": (96, 104, 112), "o": (24, 26, 30)}
GEAR_COLOURS = {
	"heavy_repeater": {**STEEL, "p": (76, 80, 84), "l": (118, 122, 126), "d": (46, 48, 52), "a": (176, 146, 82),
			"b": (34, 36, 40), "v": (98, 70, 46), "c": (210, 70, 46), "g": (230, 240, 255)},
	"kaast_longshot": {**STEEL, "p": (70, 66, 60), "l": (110, 104, 96), "d": (44, 40, 36), "a": (200, 156, 66),
			"b": (30, 30, 34), "v": (96, 34, 30), "g": (200, 120, 110), "c": (200, 156, 66)},
	"vibrosword": {**STEEL, "a": (176, 146, 82), "b": (70, 52, 36), "v": (150, 200, 230), "g": (255, 255, 255), "c": (0, 0, 0)},
	"electrostaff": {**STEEL, "p": (84, 88, 96), "l": (130, 136, 146), "d": (52, 56, 62), "a": (180, 186, 196),
			"b": (40, 36, 34), "v": (110, 190, 255), "g": (220, 244, 255), "c": (0, 0, 0)},
	"mod_plating": {**STEEL, "a": (100, 106, 112), "b": (0, 0, 0), "v": (0, 0, 0), "g": (0, 0, 0), "c": (0, 0, 0)},
	"mod_weave": {"p": (110, 100, 86), "l": (160, 150, 130), "d": (70, 62, 52), "o": (30, 26, 22), "a": (0, 0, 0),
			"b": (0, 0, 0), "v": (0, 0, 0), "g": (0, 0, 0), "c": (0, 0, 0)},
	"mod_attunement": {**STEEL, "a": (176, 146, 82), "v": (120, 170, 210), "l": (210, 236, 250), "g": (255, 255, 255),
			"b": (0, 0, 0), "c": (0, 0, 0)},
	"mod_conduit": {**STEEL, "a": (176, 146, 82), "l": (230, 196, 120), "v": (150, 110, 60), "b": (0, 0, 0), "g": (0, 0, 0),
			"c": (0, 0, 0)},
	"mod_stabiliser": {**STEEL, "a": (120, 128, 136), "g": (255, 255, 255), "b": (0, 0, 0), "v": (0, 0, 0), "c": (0, 0, 0)},
	"jet_pack": {**STEEL, "p": (96, 104, 110), "l": (132, 140, 146), "d": (62, 68, 74), "a": (176, 146, 82),
			"b": (40, 42, 46), "v": (255, 170, 80), "g": (255, 236, 170), "c": (0, 0, 0)},
	"mod_focused_barrel": {**STEEL, "a": (176, 146, 82), "v": (120, 190, 210), "g": (230, 250, 255), "b": (0, 0, 0), "c": (0, 0, 0)},
	"mod_heavy_barrel": {**STEEL, "p": (70, 74, 80), "l": (110, 116, 122), "d": (44, 46, 50), "b": (36, 38, 42),
			"v": (20, 20, 24), "a": (0, 0, 0), "g": (0, 0, 0), "c": (0, 0, 0)},
	"mod_cooling_cell": {**STEEL, "a": (176, 146, 82), "b": (70, 110, 150), "v": (110, 170, 220), "l": (190, 230, 255),
			"d": (50, 80, 110), "g": (0, 0, 0), "c": (0, 0, 0)},
	"mod_overcharged_cell": {**STEEL, "p": (150, 50, 36), "l": (240, 150, 90), "d": (100, 30, 22), "a": (176, 146, 82),
			"g": (255, 230, 160), "b": (0, 0, 0), "v": (0, 0, 0), "c": (0, 0, 0)},
	"mod_keen_edge": {**STEEL, "g": (255, 255, 255), "a": (70, 52, 36), "b": (0, 0, 0), "v": (0, 0, 0), "c": (0, 0, 0)},
	"mod_shock_edge": {**STEEL, "g": (220, 244, 255), "a": (70, 52, 36), "v": (110, 190, 255), "b": (0, 0, 0), "c": (0, 0, 0)},
}


def mask_icon(name, mask, colours, seed):
	rnd = random.Random(seed)
	img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
	px = img.load()
	for y, row in enumerate(mask):
		assert len(row) == 16, (name, y, row)
		for x, ch in enumerate(row):
			if ch in (".", " "):
				continue
			c = colours[ch]
			if ch not in ("o", "g", "v"):
				c = noisy(c, rnd, 5)
			px[x, y] = c + (255,)
	out(f"item/{name}.png", img)


def m4_icons():
	masks = dict(ICONS)
	masks.update(M4_ICONS)
	for item_id, (mask_name, colour_set) in M4_ITEMS.items():
		mask_icon(item_id, masks[mask_name], M4_ICON_COLOURS[colour_set], sum(ord(ch) for ch in item_id))
		item_json(item_id)
	for item_id, mask in GEAR_ICONS.items():
		mask_icon(item_id, mask, GEAR_COLOURS[item_id], sum(ord(ch) for ch in item_id) + 7)


# ---------------------------------------------------------------- Milestone 4 weapon models


def swatch_texture(name, swatches, seed):
	"""A 16x16 swatch sheet for a 3D in-hand model; each 4x4 block (model UV units) is one material."""
	rnd = random.Random(seed)
	img = Image.new("RGBA", (16, 16), (0, 0, 0, 255))
	px = img.load()
	for (sx, sy), base in swatches.items():
		for y in range(4):
			for x in range(4):
				c = noisy(base, rnd, 6)
				if base == swatches.get((0, 1)) and x == 1:
					c = shade(base, 0.65)
				px[sx * 4 + x, sy * 4 + y] = c + (255,)
	for y in range(8, 16):
		for x in range(16):
			if (x // 4, y // 4) not in swatches:
				px[x, y] = noisy(swatches[(0, 0)], rnd, 5) + (255,)
	out(f"item/{name}_model.png", img)


def cube(name, frm, to, uv, faces=None):
	"""A model element with every face on swatch {uv} (an (x, y) swatch index), or per-face overrides."""
	def rect(s):
		return [s[0] * 4, s[1] * 4, s[0] * 4 + 4, s[1] * 4 + 4]
	sides = {f: {"uv": rect((faces or {}).get(f, uv)), "texture": "#gun"} for f in ("north", "south", "east", "west", "up", "down")}
	return {"name": name, "from": frm, "to": to, "faces": sides}


# The same hold as the M1 rifle's in-hand model, so all long guns sit alike in the hands.
LONG_GUN_DISPLAY = {
	"thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.6, 0.6, 0.6]},
	"thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.6, 0.6, 0.6]},
	"firstperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 1, -2], "scale": [0.6, 0.6, 0.6]},
	"firstperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 1, -2], "scale": [0.6, 0.6, 0.6]},
	"head": {"rotation": [0, 0, 0], "translation": [0, 10, 0], "scale": [0.8, 0.8, 0.8]},
}


def long_gun(name, elements):
	write_json(f"models/item/{name}_in_hand.json", {
		"textures": {"gun": f"oldrepublic:item/{name}_model", "particle": f"oldrepublic:item/{name}"},
		"elements": elements, "gui_light": "front", "display": LONG_GUN_DISPLAY})
	write_json(f"models/item/{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"oldrepublic:item/{name}"}})
	write_json(f"items/{name}.json", {"model": {
		"type": "minecraft:select", "property": "minecraft:display_context",
		"cases": [{"when": ["gui", "ground", "fixed", "on_shelf"], "model": {"type": "minecraft:model", "model": f"oldrepublic:item/{name}"}}],
		"fallback": {"type": "minecraft:model", "model": f"oldrepublic:item/{name}_in_hand"}}})


def weapon_models():
	# Swatches: (0,0) body metal, (1,0) light metal, (2,0) grip, (3,0) accent/cell, (0,1) barrel, (1,1) glass, (2,1) dark grip, (3,1) muzzle.
	swatch_texture("heavy_repeater", {(0, 0): (76, 80, 84), (1, 0): (118, 122, 126), (2, 0): (98, 70, 46), (3, 0): (210, 70, 46),
			(0, 1): (34, 36, 40), (1, 1): (176, 146, 82), (2, 1): (68, 50, 34), (3, 1): (20, 20, 22)}, 4401)
	long_gun("heavy_repeater", [
		cube("receiver", [6.25, 8, -1], [9.75, 11.5, 12], (0, 0), {"up": (1, 0)}),
		cube("barrel_top", [7.25, 10, -12], [8.75, 11.5, -1], (0, 1), {"north": (3, 1)}),
		cube("barrel_left", [6.5, 8.25, -11], [8, 9.75, -1], (0, 1), {"north": (3, 1)}),
		cube("barrel_right", [8, 8.25, -11], [9.5, 9.75, -1], (0, 1), {"north": (3, 1)}),
		cube("barrel_collar", [6.25, 8, -8], [9.75, 11.75, -7], (1, 1)),
		cube("muzzle_collar", [6.25, 8, -12], [9.75, 11.75, -11], (1, 0), {"north": (3, 1)}),
		cube("carry_handle", [7.5, 11.5, 1], [8.5, 13, 8], (1, 0)),
		cube("drum_cell", [9.75, 7, 2], [11.5, 10.5, 7], (3, 0)),
		cube("foregrip", [7.25, 5.5, 0], [8.75, 8, 2.5], (2, 1)),
		cube("grip", [7.25, 4.5, 8], [8.75, 8, 10.5], (2, 0)),
		cube("stock", [6.75, 7.5, 12], [9.25, 10.5, 17], (2, 0), {"south": (2, 1)}),
	])
	swatch_texture("kaast_longshot", {(0, 0): (70, 66, 60), (1, 0): (110, 104, 96), (2, 0): (96, 34, 30), (3, 0): (200, 156, 66),
			(0, 1): (30, 30, 34), (1, 1): (200, 120, 110), (2, 1): (68, 24, 22), (3, 1): (16, 16, 18)}, 4402)
	long_gun("kaast_longshot", [
		cube("receiver", [6.75, 8.5, -2], [9.25, 11, 12], (0, 0), {"up": (1, 0)}),
		cube("barrel", [7.25, 9, -16], [8.75, 10.5, -2], (0, 1), {"north": (3, 1)}),
		cube("barrel_band", [7, 8.75, -9], [9, 10.75, -8], (3, 0)),
		cube("muzzle_brake", [6.75, 8.5, -16], [9.25, 11, -14], (1, 0), {"north": (3, 1)}),
		cube("scope", [7.25, 11, -1], [8.75, 12.75, 8], (0, 1), {"north": (1, 1), "south": (1, 1)}),
		cube("scope_mounts", [7.5, 11, 1], [8.5, 11.25, 6], (3, 0)),
		cube("grip", [7.25, 5, 7], [8.75, 8.5, 9.5], (2, 0)),
		cube("trigger_guard", [7.5, 7.5, 5.5], [8.5, 8.5, 7], (3, 1)),
		cube("stock", [7, 7.25, 12], [9, 10.75, 19], (2, 0), {"south": (2, 1), "up": (3, 0)}),
		cube("cheek_rest", [7, 10.75, 13], [9, 11.5, 17], (2, 1)),
	])
	write_json("models/item/vibrosword.json", {"parent": "minecraft:item/handheld", "textures": {"layer0": "oldrepublic:item/vibrosword"}})
	write_json("items/vibrosword.json", {"model": {"type": "minecraft:model", "model": "oldrepublic:item/vibrosword"}})
	write_json("models/item/electrostaff.json", {"parent": "minecraft:item/handheld", "textures": {"layer0": "oldrepublic:item/electrostaff"}})
	write_json("items/electrostaff.json", {"model": {"type": "minecraft:model", "model": "oldrepublic:item/electrostaff"}})
	for mod in [k for k in GEAR_ICONS if k.startswith("mod_") or k == "jet_pack"]:
		item_json(mod)


# ---------------------------------------------------------------- Milestone 4 particles (8x8, white; tinted in code)


def soft_puff(seed, radius, lumps, alpha):
	rnd = random.Random(seed)
	img = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
	px = img.load()
	centres = [(3.5 + rnd.uniform(-1, 1), 3.5 + rnd.uniform(-1, 1), radius * rnd.uniform(0.7, 1.0)) for _ in range(lumps)]
	for y in range(8):
		for x in range(8):
			a = 0.0
			for cx, cy, r in centres:
				d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5 / r
				a = max(a, max(0.0, 1.0 - d) ** 1.4)
			if a > 0.03:
				px[x, y] = (255, 255, 255, clamp(alpha * a))
	return img


def flame_frame(index):
	"""A flame tongue at frame 0 thinning into a ragged wisp by frame 3 (white; the code colours it)."""
	rnd = random.Random(4600 + index)
	img = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
	px = img.load()
	length = [3.4, 3.0, 2.6, 2.2][index]
	width = [1.9, 2.1, 2.3, 2.5][index]
	core = [1.0, 0.75, 0.45, 0.2][index]
	for y in range(8):
		for x in range(8):
			dx = (x - 3.5) / width
			dy = (y - 4.2) / length
			d = (dx * dx + dy * dy) ** 0.5
			if index >= 2:
				d += rnd.uniform(0, 0.35)
			a = max(0.0, 1.0 - d)
			if a <= 0.03:
				continue
			hot = clamp(200 + 55 * core * a)
			px[x, y] = (255, hot, clamp(hot * (0.7 + 0.3 * core)), clamp(255 * a ** (0.8 + 0.3 * index)))
	return img


def particles():
	for i, (radius, lumps, alpha) in enumerate([(2.2, 2, 210), (2.8, 3, 190), (3.3, 3, 160), (3.8, 4, 120)]):
		out(f"particle/steam_vent_{i}.png", soft_puff(4500 + i, radius, lumps, alpha))
	write_json("particles/steam_vent.json", {"textures": [f"oldrepublic:steam_vent_{i}" for i in range(4)]})
	for i in range(4):
		out(f"particle/flame_jet_{i}.png", flame_frame(i))
	write_json("particles/flame_jet.json", {"textures": [f"oldrepublic:flame_jet_{i}" for i in range(4)]})


def m4():
	for asset in M4_SETS:
		humanoid, leggings = m4_layers(asset)
		out(f"entity/equipment/humanoid/{asset}.png", humanoid)
		if leggings is not None:
			out(f"entity/equipment/humanoid_leggings/{asset}.png", leggings)
		equipment_json(asset, leggings is not None)
	for asset in ("watch_battle", "sith_war"):
		out(f"entity/robe/{asset}.png", plate_cloth_texture(asset))
	m4_icons()
	weapon_models()
	particles()


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
	if "--m4" in sys.argv[1:]:
		m4()
		return

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

	m4()


if __name__ == "__main__":
	main()
