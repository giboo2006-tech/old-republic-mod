#!/usr/bin/env python3
"""Generates the W5 boss feature's textures and JSON (PLACEHOLDER art, original, deterministic).

Everything is drawn here with PIL from seeded noise; nothing is copied from any game or film.

Textures (src/main/resources/assets/oldrepublic/textures/):
  entity/ruin_sentinel/{watcher,tomb}.png            64x64 Ruin Sentinel skins (RuinSentinelModel UV layout)
  entity/ruin_sentinel/{watcher,tomb}_lumen.png      64x64 lumen layers (drawn additive and full bright)
  entity/orren_vasch/{base,unkept,echo,echo_unkept}.png  64x64 Orren Vasch skins (W5 plan 4.10 UV map)
  gui/sprites/portrait/orren_vasch.png               32x32 dialogue portrait
  block/resonance_pylon_{stone,bronze,top}.png       16x16 pylon base
  block/resonance_pylon_crystal_{dormant,singing,silenced,regrowing}.png  16x16 pylon crystal states
  block/reliquary_heart_{side,top,front,crystal}.png 16x16 the heart
  block/hoard_plinth_{side,top,front}.png            16x16 the hoard plinth
  block/reliquary_seal.png, block/reliquary_seal_vault.png  16x16 door seal and vault seal
  item/ruin_sentinel_spawn_egg.png                   16x16
  particle/{ward_haze,ward_current,lash_seam,lumen_spark}_{0,1}.png    8x8
  particle/{surge_dust,echo_smoke}_{0..3}.png        16x16

JSON (src/main/resources/assets/oldrepublic/):
  blockstates/{resonance_pylon,reliquary_heart,hoard_plinth,reliquary_seal}.json
  models/block/resonance_pylon_{dormant,singing,silenced,regrowing,empty}.json, reliquary_heart.json,
  hoard_plinth.json, reliquary_seal.json, reliquary_seal_vault.json, reliquary_seal_open.json
  items/{resonance_pylon,reliquary_heart,hoard_plinth,reliquary_seal,ruin_sentinel_spawn_egg}.json
  models/item/ruin_sentinel_spawn_egg.json
  particles/{ward_haze,ward_current,surge_dust,lash_seam,lumen_spark,echo_smoke}.json

Run from anywhere: python3 tools/assets/gen_textures_boss.py
"""
import json
import math
import os
import random

from PIL import Image

ASSETS = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "src", "main", "resources", "assets", "oldrepublic"))
NS = "oldrepublic"


# ---------------------------------------------------------------- output helpers

def write_png(rel, img):
	path = os.path.join(ASSETS, "textures", rel)
	os.makedirs(os.path.dirname(path), exist_ok=True)
	img.save(path)
	print("wrote", os.path.relpath(path))


def write_json(rel, data):
	for el in data.get("elements", []):
		for corner in (el["from"], el["to"]):
			if any(c < -16 or c > 32 for c in corner):
				raise SystemExit(f"{rel}: element corner {corner} outside -16..32")
	path = os.path.join(ASSETS, rel)
	os.makedirs(os.path.dirname(path), exist_ok=True)
	with open(path, "w", encoding="utf-8") as f:
		json.dump(data, f, indent="\t")
		f.write("\n")
	print("wrote", os.path.relpath(path))


def clamp(v):
	return max(0, min(255, int(round(v))))


def noisy(rgb, rnd, amount):
	return tuple(clamp(c + rnd.uniform(-amount, amount)) for c in rgb)


def shade(rgb, factor):
	return tuple(clamp(c * factor) for c in rgb)


def mix(a, b, t):
	return tuple(clamp(a[i] * (1 - t) + b[i] * t) for i in range(3))


def new(w, h):
	return Image.new("RGBA", (w, h), (0, 0, 0, 0))


# ---------------------------------------------------------------- box UV helper

def face_rects(u, v, w, h, d):
	"""Texture rectangles (x, y, width, height) of a model box, in the vanilla cube layout.

	Seen from the front, texture x grows with model x on the front face, so the entity's right
	side (model -x) is on the left of the face, as on a player skin.
	"""
	return {
		"top": (u + d, v, w, d),
		"bottom": (u + d + w, v, w, d),
		"east": (u, v + d, d, h),
		"front": (u + d, v + d, w, h),
		"west": (u + d + w, v + d, d, h),
		"back": (u + d + w + d, v + d, w, h),
	}


def paint_box(px, u, v, w, h, d, painter):
	"""Calls painter(face, fx, fy, fw, fh) for every texel; it returns RGB, RGBA or None (left as is)."""
	for face, (x0, y0, fw, fh) in face_rects(u, v, w, h, d).items():
		for fy in range(fh):
			for fx in range(fw):
				c = painter(face, fx, fy, fw, fh)
				if c is not None:
					px[x0 + fx, y0 + fy] = c if len(c) == 4 else c + (255,)


def rect(px, x0, y0, w, h, colour):
	for y in range(y0, y0 + h):
		for x in range(x0, x0 + w):
			px[x, y] = colour if len(colour) == 4 else colour + (255,)


# ================================================================ Ruin Sentinel

SENTINEL_PALETTES = {
	# Weathered pale stone, robe-like carved plates, a pale blue lumen.
	"watcher": {
		"stone": (178, 172, 158), "stone_dark": (132, 126, 114), "stone_light": (206, 200, 186),
		"lichen": (122, 128, 98), "bronze": (150, 108, 58), "bronze_dark": (98, 68, 38),
		"patina": (98, 142, 122), "slit": (24, 26, 30), "glass": (44, 58, 70), "lumen": (168, 220, 255),
	},
	# Dark basalt, angular, bronze spikes along the shoulders, a red lumen.
	"tomb": {
		"stone": (66, 63, 68), "stone_dark": (42, 40, 45), "stone_light": (96, 92, 97),
		"lichen": (78, 52, 46), "bronze": (138, 96, 52), "bronze_dark": (88, 58, 32),
		"patina": (120, 54, 38), "slit": (14, 10, 12), "glass": (64, 30, 30), "lumen": (255, 72, 54),
	},
}


class Stone:
	"""Per-skin stone painter: noise, a few cracks, weathering specks."""

	def __init__(self, pal, seed):
		self.pal = pal
		self.rnd = random.Random(seed)

	def stone(self, fx, fy, weather=0.08):
		p, r = self.pal, self.rnd
		c = p["stone"]
		roll = r.random()
		if roll < weather:
			c = mix(c, p["lichen"], 0.45)
		elif roll < weather + 0.12:
			c = p["stone_dark"]
		elif roll < weather + 0.2:
			c = p["stone_light"]
		return noisy(c, r, 7)

	def bronze(self, fx, fy, patina=0.15):
		p, r = self.pal, self.rnd
		c = p["bronze"] if (fx + fy) % 5 else p["bronze_dark"]
		if r.random() < patina:
			c = mix(c, p["patina"], 0.6)
		return noisy(c, r, 6)


def sentinel_skin(name):
	pal = SENTINEL_PALETTES[name]
	tomb = name == "tomb"
	img = new(64, 64)
	px = img.load()
	s = Stone(pal, 101 if tomb else 100)

	# Head 0,0 (8x8x8): a closed face plate. Watcher: one vertical slit. Tomb: two angled eye slits.
	def head(face, fx, fy, fw, fh):
		if face == "front":
			if fy == 0 or fx in (0, fw - 1):
				return noisy(pal["stone_dark"], s.rnd, 5)
			if not tomb and fx in (3, 4) and 2 <= fy <= 6:
				return pal["slit"]
			if tomb and fy == 3 and fx in (1, 2, 5, 6):
				return pal["slit"]
			if tomb and fy == 4 and fx in (2, 5):
				return pal["slit"]
			if tomb and fx in (3, 4) and fy >= 6:
				return noisy(pal["stone_dark"], s.rnd, 4)
			if fy == 1:
				return noisy(pal["stone_light"], s.rnd, 5)
			return s.stone(fx, fy, 0.03)
		if face == "top":
			return s.stone(fx, fy, 0.15)
		if fy == fh - 1:
			return noisy(pal["bronze_dark"], s.rnd, 5)
		return s.stone(fx, fy)

	paint_box(px, 0, 0, 8, 8, 8, head)

	# Crest 32,0 (2x3x10): a carved ridge along the helm (bronze-edged on the Tomb sentinel).
	paint_box(px, 32, 0, 2, 3, 10, lambda face, fx, fy, fw, fh:
			s.bronze(fx, fy) if tomb and fy == 0 else s.stone(fx, fy, 0.12))

	# Body 16,16 (8x12x4): carved chest; the Watcher's front falls in robe-like carved folds.
	def body(face, fx, fy, fw, fh):
		if face == "front":
			if fy in (0, 7) or fy == fh - 1:
				return noisy(pal["stone_dark"], s.rnd, 5)
			if 1 <= fy <= 6 and 2 <= fx <= 5:
				# The recess behind the lumen.
				return noisy(shade(pal["stone_dark"], 0.8), s.rnd, 4)
			if fy >= 8 and not tomb and fx in (1, 3, 4, 6):
				return noisy(pal["stone_dark"], s.rnd, 5)
			if fy >= 8 and tomb and (fx + fy) % 4 == 0:
				return noisy(pal["stone_light"], s.rnd, 5)
			return s.stone(fx, fy)
		if face == "back" and fy in (3, 8):
			return noisy(pal["stone_dark"], s.rnd, 5)
		return s.stone(fx, fy)

	paint_box(px, 16, 16, 8, 12, 4, body)

	# Legs 0,16 and arms 40,16 (4x12x4): stone, bronze bands at the knee and elbow.
	def limb(face, fx, fy, fw, fh):
		if fy in (5, 6):
			return s.bronze(fx, fy)
		if face in ("top", "bottom"):
			return noisy(pal["stone_dark"], s.rnd, 5)
		return s.stone(fx, fy)

	paint_box(px, 0, 16, 4, 12, 4, limb)
	paint_box(px, 40, 16, 4, 12, 4, limb)

	# Chest plates 0,32 (4x6x1): riveted bronze (the inner edge is column 3 of the front).
	def plate(face, fx, fy, fw, fh):
		if face == "front" and (fx, fy) in ((0, 0), (0, fh - 1), (2, 0), (2, fh - 1)):
			return noisy(pal["stone_light"], s.rnd, 4)
		if face == "front" and fx == fw - 1:
			return noisy(pal["bronze_dark"], s.rnd, 4)
		return s.bronze(fx, fy, 0.25)

	paint_box(px, 0, 32, 4, 6, 1, plate)

	# Spikes 12,32 (2x3x2, shared by the 2x2x2 spike): bronze, darker tips.
	paint_box(px, 12, 32, 2, 3, 2, lambda face, fx, fy, fw, fh:
			noisy(pal["bronze_dark"], s.rnd, 5) if face == "top" or fy == 0 else s.bronze(fx, fy, 0.1))

	# Pauldrons 24,32 (6x3x6): stone shoulder slabs with a bronze rim.
	paint_box(px, 24, 32, 6, 3, 6, lambda face, fx, fy, fw, fh:
			s.bronze(fx, fy) if face not in ("top", "bottom") and fy == fh - 1 else s.stone(fx, fy, 0.12))

	# Lumen 48,32 (4x4x1): the core's glass, dim (the lumen layer makes it glow).
	paint_box(px, 48, 32, 4, 4, 1, lambda face, fx, fy, fw, fh:
			noisy(pal["glass"], s.rnd, 6) if face == "front" else noisy(pal["bronze_dark"], s.rnd, 5))

	# Fauld 0,41 (10x3x6): hanging plates, alternating stone and bronze segments.
	paint_box(px, 0, 41, 10, 3, 6, lambda face, fx, fy, fw, fh:
			s.bronze(fx, fy) if (fx // 2) % 2 == 1 and face in ("front", "back", "east", "west") else s.stone(fx, fy))

	# Gauntlets 32,41 (5x6x5): bronze with stone knuckles.
	paint_box(px, 32, 41, 5, 6, 5, lambda face, fx, fy, fw, fh:
			s.stone(fx, fy, 0.05) if fy >= fh - 2 and face != "top" else s.bronze(fx, fy))

	# Greaves 0,50 (5x6x5): stone with a bronze top trim.
	paint_box(px, 0, 50, 5, 6, 5, lambda face, fx, fy, fw, fh:
			s.bronze(fx, fy) if fy == 0 and face != "bottom" else s.stone(fx, fy))

	# Tabard 20,50 (6x9x1, the Watcher's): carved stone cloth, folds and a worn hem.
	def tabard(face, fx, fy, fw, fh):
		if face == "front":
			if fy == fh - 1 and fx % 2 == 0:
				return noisy(pal["stone_dark"], s.rnd, 4)
			if fx in (1, 4):
				return noisy(pal["stone_dark"], s.rnd, 5)
			if fy == 1:
				return s.bronze(fx, fy)
		return s.stone(fx, fy, 0.1)

	paint_box(px, 20, 50, 6, 9, 1, tabard)

	# Back slab 36,52 (6x10x2): carved channels.
	paint_box(px, 36, 52, 6, 10, 2, lambda face, fx, fy, fw, fh:
			noisy(pal["stone_dark"], s.rnd, 5) if face == "back" and fx in (1, 4) else s.stone(fx, fy))
	return img


def sentinel_lumen(name):
	"""Only the glowing parts: the lumen, the face slit or eyes, chest seams and the plates' inner edges."""
	pal = SENTINEL_PALETTES[name]
	tomb = name == "tomb"
	lumen = pal["lumen"]
	core = mix(lumen, (255, 255, 255), 0.55)
	dim = shade(lumen, 0.55)
	faint = shade(lumen, 0.32)
	img = new(64, 64)
	px = img.load()

	# The lumen box: a white-hot core on the front, coloured around it.
	def lumen_box(face, fx, fy, fw, fh):
		if face == "front":
			centre = 1 <= fx <= 2 and 1 <= fy <= 2
			return (core if centre else lumen) + (255,)
		return dim + (255,)

	paint_box(px, 48, 32, 4, 4, 1, lumen_box)

	# Face: the head's front face is at (8, 8), 8x8.
	if tomb:
		for x, y in ((1, 3), (2, 3), (5, 3), (6, 3), (2, 4), (5, 4)):
			px[8 + x, 8 + y] = lumen + (255,)
	else:
		for y in range(2, 7):
			for x in (3, 4):
				px[8 + x, 8 + y] = (core if 3 <= y <= 5 else lumen) + (255,)

	# Seams in the chest around the recess (body front at (20, 20), 8x12).
	seams = [(1, 2), (1, 3), (6, 4), (6, 5), (3, 7), (4, 7), (4, 8), (2, 9)] if not tomb else \
		[(1, 1), (6, 2), (6, 3), (2, 7), (5, 7), (5, 8), (6, 9), (1, 9)]
	for x, y in seams:
		px[20 + x, 20 + y] = faint + (255,)

	# The plates' inner edge: a thin line of light where they meet (plate front at (1, 33), 4x6).
	for y in range(1, 5):
		px[1 + 3, 33 + y] = dim + (255,)
	return img


def spawn_egg():
	"""A pale stone egg with bronze flecks and a blue lumen spot."""
	rnd = random.Random(140)
	img = new(16, 16)
	px = img.load()
	base = SENTINEL_PALETTES["watcher"]["stone"]
	cx, cy = 7.5, 8.6
	for y in range(16):
		for x in range(16):
			dx = (x - cx) / 5.2
			dy = (y - cy) / (6.6 if y < cy else 5.6)
			r = dx * dx + dy * dy
			if r > 1.0:
				continue
			c = shade(base, 1.0 + (x - cx) * -0.04 + (y - cy) * -0.05)
			if r > 0.78:
				c = shade(c, 0.7)
			px[x, y] = noisy(c, rnd, 6) + (255,)
	for x, y in ((5, 5), (10, 7), (5, 12), (10, 12), (8, 4)):
		px[x, y] = SENTINEL_PALETTES["watcher"]["bronze"] + (255,)
	for x, y in ((7, 9), (8, 9), (7, 10), (8, 10)):
		px[x, y] = SENTINEL_PALETTES["watcher"]["lumen"] + (255,)
	px[7, 9] = (236, 248, 255, 255)
	return img


# ================================================================ Orren Vasch (W5 plan 4.10)

VASCH = {
	"ochre": (172, 138, 82), "ochre_dark": (128, 100, 58), "ochre_light": (196, 166, 110),
	"ink": (62, 64, 72), "ink_dark": (40, 42, 48), "ink_stain": (30, 34, 56),
	"skin": (186, 146, 118), "skin_shade": (156, 118, 94), "skin_dark": (128, 94, 74),
	"hair": (154, 150, 144), "hair_dark": (104, 100, 96), "eyes": (120, 140, 150),
	"leather": (92, 64, 42), "leather_dark": (60, 42, 28), "brass": (178, 146, 86),
	"holocron": (44, 32, 34), "seam": (196, 42, 40), "seam_hot": (238, 104, 86),
	"boot": (52, 40, 32),
}


def fabric(c, rnd, amount=6, weave=True, fx=0, fy=0):
	base = shade(c, 0.94) if weave and (fx + fy) % 2 else c
	return noisy(base, rnd, amount)


def vasch_skin(unkept):
	p = VASCH
	rnd = random.Random(201 if unkept else 200)
	img = new(64, 64)
	px = img.load()
	ochre = shade(p["ochre"], 0.9) if unkept else p["ochre"]

	# Head 0,0 (8x8x8): greying hair tied back, a lined face, a short greying beard.
	def head(face, fx, fy, fw, fh):
		if face == "front":
			if fy <= 1:
				return noisy(p["hair"] if (fx + fy) % 3 else p["hair_dark"], rnd, 6)
			if fy == 2 and fx in (1, 2, 5, 6):
				return noisy(p["hair_dark"], rnd, 5)
			if fy == 3:
				if fx in (1, 6):
					return (232, 228, 222)
				if fx in (2, 5):
					return p["eyes"]
			if fy == 4 and fx in (1, 2, 5, 6):
				return noisy(p["skin_shade"], rnd, 3)
			if fy == 4 and fx in (3, 4):
				return noisy(p["skin_dark"], rnd, 3)
			if fy >= 5:
				if fy == 6 and fx in (3, 4):
					return noisy(shade(p["skin_dark"], 0.8), rnd, 3)
				return noisy(p["hair"] if (fx + fy) % 2 else mix(p["hair"], p["hair_dark"], 0.5), rnd, 6)
			return noisy(p["skin"], rnd, 4)
		if face in ("east", "west"):
			if fy <= 2 or fx >= 5:
				return noisy(p["hair"] if (fx + fy) % 3 else p["hair_dark"], rnd, 6)
			if fy >= 5 and fx <= 2:
				return noisy(p["hair"], rnd, 6)
			return noisy(p["skin_shade"], rnd, 4)
		if face == "bottom":
			return noisy(p["skin_dark"], rnd, 4)
		return noisy(p["hair"] if (fx * 3 + fy) % 4 else p["hair_dark"], rnd, 6)

	paint_box(px, 0, 0, 8, 8, 8, head)

	# Hat 32,0 (8x8x8, the outer layer): base: the hair tie and tail, and the beard's edge.
	# Unkept: the ink-grey hood, up, framing the face.
	def hat(face, fx, fy, fw, fh):
		if unkept:
			if face == "front":
				if fy == 0 or fx in (0, fw - 1):
					return fabric(p["ink_dark"], rnd, 5)
				return None
			if face == "bottom":
				return None
			edge = face in ("east", "west") and fy == fh - 1 and fx % 2 == 0
			return fabric(p["ink"] if not edge else p["ink_dark"], rnd, 6, fx=fx, fy=fy)
		if face == "back" and fx in (3, 4):
			if fy == 2:
				return noisy(p["leather_dark"], rnd, 4)
			if 3 <= fy <= 6:
				return noisy(p["hair"] if fy % 2 else p["hair_dark"], rnd, 6)
		if face == "front" and fy == fh - 1 and fx in (2, 3, 4, 5):
			return noisy(mix(p["hair"], p["hair_dark"], 0.4), rnd, 6)
		return None

	paint_box(px, 32, 0, 8, 8, 8, hat)

	# Body 16,16 (8x12x4): an ink-grey undertunic with an ochre sash.
	def body(face, fx, fy, fw, fh):
		if face in ("front", "back", "east", "west") and fy in (7, 8):
			return fabric(p["ochre_dark"], rnd, 5)
		return fabric(p["ink"], rnd, 5, fx=fx, fy=fy)

	paint_box(px, 16, 16, 8, 12, 4, body)

	# Arms 40,16 (4x12x4): ink sleeves, fingerless leather gloves, bare fingertips.
	def arm(face, fx, fy, fw, fh):
		if face == "bottom":
			return noisy(p["skin_shade"], rnd, 4)
		if fy == fh - 1:
			return noisy(p["skin"], rnd, 4)
		if fy >= fh - 4:
			return noisy(p["leather"] if fx % 3 else p["leather_dark"], rnd, 5)
		return fabric(p["ink"], rnd, 5, fx=fx, fy=fy)

	paint_box(px, 40, 16, 4, 12, 4, arm)

	# Legs 0,16 (4x12x4): ink trousers and worn boots.
	def leg(face, fx, fy, fw, fh):
		if fy >= fh - 4 or face == "bottom":
			return noisy(p["boot"] if fy != fh - 4 else p["leather_dark"], rnd, 5)
		return fabric(p["ink_dark"], rnd, 5, fx=fx, fy=fy)

	paint_box(px, 0, 16, 4, 12, 4, leg)

	# Robe 16,32 (8x12x4): the layered Archivist robe in faded ochre, an ink-grey panel down the front.
	def robe(face, fx, fy, fw, fh):
		if face == "front" and fx in (3, 4):
			return fabric(p["ink"], rnd, 5, fx=fx, fy=fy)
		if face == "front" and fx in (2, 5):
			return fabric(p["ochre_light"], rnd, 5)
		if face in ("front", "back") and fy == fh - 1:
			return fabric(p["ochre_dark"], rnd, 5)
		if face == "front" and fy >= fh - 3 and rnd.random() < 0.12:
			# Ink spattered low on the front, where a writing case hangs.
			return noisy(p["ink_stain"], rnd, 4)
		c = fabric(ochre, rnd, 6, fx=fx, fy=fy)
		if unkept and rnd.random() < 0.15:
			c = shade(c, 0.85)
		return c

	paint_box(px, 16, 32, 8, 12, 4, robe)

	# Sleeves 40,32 (4x12x4): wide ochre sleeves with ink-stained cuffs; open at the wrist (gloves show).
	def sleeve(face, fx, fy, fw, fh):
		if face == "bottom" or fy >= fh - 2:
			return None
		if fy >= fh - 4:
			return noisy(p["ink_stain"] if (fx + fy) % 3 else p["ochre_dark"], rnd, 5)
		if face == "top":
			return fabric(p["ochre_dark"], rnd, 5)
		return fabric(ochre, rnd, 6, fx=fx, fy=fy)

	paint_box(px, 40, 32, 4, 12, 4, sleeve)

	# Skirt 0,48 (8x8x4): the robe below the waist, an ink-grey hem (frayed when unkept).
	def skirt(face, fx, fy, fw, fh):
		if face in ("top", "bottom"):
			return None if face == "bottom" else fabric(p["ochre_dark"], rnd, 5)
		if fy == fh - 1:
			if unkept and (fx * 7 + fy) % 3 == 0:
				return None
			return fabric(p["ink_dark"], rnd, 5)
		if fy == fh - 2:
			return fabric(p["ink"], rnd, 5)
		if face == "front" and fx in (3, 4):
			return fabric(p["ink"], rnd, 5, fx=fx, fy=fy)
		return fabric(ochre, rnd, 6, fx=fx, fy=fy)

	paint_box(px, 0, 48, 8, 8, 4, skirt)

	# Cloak 0,32 (8x16x0): inside at u 0-8, outside at u 8-16. Ink-grey with an ochre border; the
	# Unkept cloak's lower edge frays into gaps.
	for side, x0 in (("inside", 0), ("outside", 8)):
		for fy in range(16):
			for fx in range(8):
				if unkept and fy >= 13 and (fx * 5 + fy * 3) % 4 == 0:
					continue
				if unkept and fy == 15 and fx % 2 == 1:
					continue
				if fx in (0, 7) or fy == 0:
					c = fabric(p["ochre_dark"] if side == "outside" else p["ochre"], rnd, 5)
				else:
					c = fabric(p["ink"] if side == "outside" else p["ink_dark"], rnd, 6, fx=fx, fy=fy)
				if fy >= 14 and not unkept:
					c = shade(c, 0.85)
				px[x0 + fx, 32 + fy] = c + (255,)

	# Bandolier 24,48 (9x1x5): a leather strap of empty brass holocron cradles.
	def bandolier(face, fx, fy, fw, fh):
		if face in ("top", "front", "back") and fx % 3 == 1:
			if face == "top" and 1 <= fy <= 3:
				return p["ink_dark"] if fy == 2 else p["brass"]
			return noisy(p["brass"], rnd, 5)
		return noisy(p["leather"] if fx % 2 else p["leather_dark"], rnd, 5)

	paint_box(px, 24, 48, 9, 1, 5, bandolier)

	# Satchel 24,54 (4x4x2): leather with a flap and a brass buckle.
	def satchel(face, fx, fy, fw, fh):
		if face == "front" and fy == 1 and fx in (1, 2):
			return p["brass"]
		if face == "front" and fy <= 1:
			return noisy(p["leather_dark"], rnd, 4)
		return noisy(p["leather"], rnd, 5)

	paint_box(px, 24, 54, 4, 4, 2, satchel)

	# Holocron 36,54 (2x2x2): the stolen Sith holocron, dark with crimson seams (hot when Unkept).
	seam = p["seam_hot"] if unkept else p["seam"]
	paint_box(px, 36, 54, 2, 2, 2, lambda face, fx, fy, fw, fh:
			seam if (fx + fy) % 2 == 0 else noisy(p["holocron"], rnd, 4))
	return img


def echo_of(skin, seed):
	"""The rematch echo: desaturated toward a cool smoke grey, mottled, with thinning cloak and hem edges."""
	rnd = random.Random(seed)
	smoke = (150, 156, 166)
	img = skin.copy()
	px = img.load()
	for y in range(64):
		for x in range(64):
			r, g, b, a = px[x, y]
			if a == 0:
				continue
			luma = 0.3 * r + 0.59 * g + 0.11 * b
			c = mix((luma, luma, luma), smoke, 0.35)
			if rnd.random() < 0.08:
				c = mix(c, (210, 214, 220), 0.25)
			alpha = a
			# Cloak lower half and skirt hem: smoke at the edges (translucent where the renderer allows).
			if (x < 16 and 32 + 11 <= y < 48) or (y in (58, 59) and x < 24):
				alpha = clamp(a * (0.55 + 0.4 * rnd.random()))
			px[x, y] = c + (alpha,)
	return img


def vasch_portrait():
	"""Head and shoulders, 32x32: greying hair tied back, a short beard, tired eyes, amber rim light."""
	p = VASCH
	rnd = random.Random(230)
	img = Image.new("RGBA", (32, 32), (0, 0, 0, 255))
	px = img.load()
	top, bottom = (30, 26, 24), (58, 44, 30)
	for y in range(32):
		row = mix(top, bottom, y / 31)
		for x in range(32):
			px[x, y] = noisy(row, rnd, 3) + (255,)
	background = img.copy().load()

	# Shoulders: ochre robe over an ink tunic, a bandolier strap across.
	for y in range(23, 32):
		half = 9 + (y - 23)
		for x in range(16 - half, 16 + half):
			if 0 <= x < 32:
				px[x, y] = fabric(p["ochre"], rnd, 6, fx=x, fy=y) + (255,)
	for y in range(24, 32):
		for x in range(14, 18):
			px[x, y] = fabric(p["ink"], rnd, 4) + (255,)
	for i in range(10):
		x, y = 9 + i, 23 + i
		if 0 <= x < 32 and 0 <= y < 32:
			px[x, y] = p["leather"] + (255,)
			if i % 3 == 1:
				px[x, y] = p["brass"] + (255,)

	# Neck and face (a little long, lined).
	for y in range(19, 24):
		for x in range(14, 18):
			px[x, y] = noisy(p["skin_shade"], rnd, 3) + (255,)
	for y in range(6, 21):
		for x in range(10, 22):
			dx = (x - 15.5) / 6.0
			dy = (y - 13.0) / 8.0
			if dx * dx + dy * dy <= 1.0:
				px[x, y] = noisy(p["skin"], rnd, 4) + (255,)

	# Hair: grey, swept back to a tie (the tail shows past the right ear).
	for y in range(4, 10):
		for x in range(9, 23):
			dx = (x - 15.5) / 7.0
			dy = (y - 9.5) / 6.0
			if dx * dx + dy * dy <= 1.0 and y < 9 - (0 if x in (9, 10, 21, 22) else 1):
				px[x, y] = noisy(p["hair"] if (x + y) % 3 else p["hair_dark"], rnd, 6) + (255,)
	for y in range(9, 15):
		for x in (9, 22):
			px[x, y] = noisy(p["hair"], rnd, 6) + (255,)
	for y in range(10, 18):
		px[23, y] = noisy(p["hair_dark"], rnd, 5) + (255,)
	px[23, 10] = p["leather_dark"] + (255,)

	# Short greying beard along the jaw and chin.
	for y in range(16, 21):
		for x in range(11, 21):
			dx = (x - 15.5) / 5.0
			dy = (y - 17.0) / 4.0
			if dx * dx + dy * dy <= 1.0 and not (y == 16 and 13 <= x <= 18):
				px[x, y] = noisy(p["hair"] if (x + y) % 2 else mix(p["hair"], p["hair_dark"], 0.5), rnd, 6) + (255,)

	# Brows, tired eyes (lids low), the mouth line, the lines of the face.
	for x in (12, 13, 18, 19):
		px[x, 11] = p["hair_dark"] + (255,)
	for x in (12, 19):
		px[x, 12] = (224, 218, 210, 255)
	px[13, 12] = p["eyes"] + (255,)
	px[18, 12] = p["eyes"] + (255,)
	for x in (12, 13, 18, 19):
		px[x, 13] = shade(p["skin"], 0.82) + (255,)
	for x in range(14, 18):
		px[x, 17] = shade(p["skin_dark"], 0.8) + (255,)
	px[11, 15] = shade(p["skin"], 0.85) + (255,)
	px[20, 15] = shade(p["skin"], 0.85) + (255,)

	# Rim light from his amber blade, on the left edge of the figure (face, hair and shoulder).
	rim = (255, 176, 42)
	for y in range(4, 32):
		for x in range(1, 16):
			if px[x, y] != background[x, y]:
				px[x, y] = mix(px[x, y][:3], rim, 0.45) + (255,)
				break
	return img


# ================================================================ blocks

PYLON_STONE = (168, 160, 144)
PYLON_STONE_DARK = (122, 116, 104)
BRONZE = (150, 110, 60)
BRONZE_DARK = (100, 70, 40)
CRYSTAL = {
	"dormant": ((150, 168, 182), (196, 210, 220), (110, 124, 138)),
	"singing": ((196, 232, 255), (246, 252, 255), (150, 200, 236)),
	"silenced": ((70, 74, 82), (110, 114, 122), (40, 42, 48)),
	"regrowing": ((168, 200, 222), (220, 236, 248), (126, 160, 186)),
}


def stone_tex(seed, base=PYLON_STONE, dark=PYLON_STONE_DARK, courses=True):
	rnd = random.Random(seed)
	img = new(16, 16)
	px = img.load()
	for y in range(16):
		for x in range(16):
			c = base
			if courses and (y % 8 == 7 or (x + (8 if y // 8 % 2 else 0)) % 16 == 0):
				c = dark
			elif rnd.random() < 0.12:
				c = mix(base, dark, 0.5)
			px[x, y] = noisy(c, rnd, 6) + (255,)
	return img


def bronze_tex(seed):
	rnd = random.Random(seed)
	img = new(16, 16)
	px = img.load()
	for y in range(16):
		for x in range(16):
			c = BRONZE if y % 4 else BRONZE_DARK
			if rnd.random() < 0.1:
				c = mix(c, (96, 140, 118), 0.5)
			px[x, y] = noisy(c, rnd, 6) + (255,)
	for x in (2, 7, 12):
		px[x, 2] = (196, 160, 100, 255)
	return img


def pylon_top():
	img = stone_tex(302, courses=False)
	px = img.load()
	rnd = random.Random(303)
	for y in range(16):
		for x in range(16):
			r = math.hypot(x - 7.5, y - 7.5)
			if 3.5 <= r < 5.0:
				px[x, y] = noisy(BRONZE, rnd, 6) + (255,)
			elif r < 3.5:
				px[x, y] = noisy(shade(PYLON_STONE_DARK, 0.7), rnd, 4) + (255,)
	return img


def crystal_tex(state):
	"""Crystal faces: vertical facets, a bright edge, growth lines; silenced has dark cracks."""
	body, light, dark = CRYSTAL[state]
	rnd = random.Random(310 + list(CRYSTAL).index(state))
	img = new(16, 16)
	px = img.load()
	for y in range(16):
		for x in range(16):
			c = light if x % 8 in (1, 2) else (dark if x % 8 == 6 else body)
			if state == "regrowing" and y % 5 == 0 and x % 8 != 6:
				# Faint growth bands.
				c = mix(body, light, 0.45)
			if state == "singing" and x % 8 == 1:
				c = (255, 255, 255)
			px[x, y] = noisy(c, rnd, 4) + (255,)
	if state == "silenced":
		x = 5
		for y in range(16):
			x = max(0, min(15, x + rnd.choice((-1, 0, 1))))
			px[x, y] = (24, 24, 28, 255)
			if y % 4 == 0 and x + 1 < 16:
				px[x + 1, y] = (24, 24, 28, 255)
	return img


def heart_tex(face):
	rnd = random.Random({"side": 320, "top": 321, "front": 322, "crystal": 323}[face])
	if face == "crystal":
		img = new(16, 16)
		px = img.load()
		for y in range(16):
			for x in range(16):
				c = (232, 176, 84) if x % 6 in (1, 2) else (196, 128, 52)
				px[x, y] = noisy(c, rnd, 5) + (255,)
		return img
	img = stone_tex(324 if face != "top" else 325, base=(150, 142, 128), dark=(104, 98, 88), courses=face == "side")
	px = img.load()
	if face == "front":
		# A carved niche with a bronze frame.
		for y in range(3, 13):
			for x in range(4, 12):
				edge = x in (4, 11) or y in (3, 12)
				px[x, y] = noisy(BRONZE if edge else (40, 34, 30), rnd, 5) + (255,)
	elif face == "top":
		for y in range(16):
			for x in range(16):
				if math.hypot(x - 7.5, y - 7.5) < 4.5 and math.hypot(x - 7.5, y - 7.5) >= 3.2:
					px[x, y] = noisy(BRONZE, rnd, 5) + (255,)
	else:
		for x in range(16):
			px[x, 1] = noisy(BRONZE_DARK, rnd, 5) + (255,)
	return img


def plinth_tex(face):
	rnd = random.Random({"side": 330, "top": 331, "front": 332}[face])
	img = stone_tex(333 + len(face), base=(120, 112, 100), dark=(84, 78, 70), courses=face != "top")
	px = img.load()
	if face == "top":
		for cx in (3, 8, 13):
			for y in range(6, 10):
				for x in range(cx - 1, cx + 2):
					if 0 <= x < 16:
						px[x, y] = noisy(BRONZE if (x, y) != (cx, 7) else BRONZE_DARK, rnd, 5) + (255,)
	elif face == "front":
		for x in range(2, 14):
			px[x, 3] = noisy(BRONZE, rnd, 5) + (255,)
			px[x, 12] = noisy(BRONZE_DARK, rnd, 5) + (255,)
	return img


def seal_tex(vault):
	"""A carved slab with bronze bars (door seal), or a heavier one with a lock plate (vault seal)."""
	rnd = random.Random(341 if vault else 340)
	base = (104, 98, 92) if vault else (140, 132, 120)
	img = stone_tex(342 if vault else 343, base=base, dark=shade(base, 0.7), courses=False)
	px = img.load()
	for y in range(16):
		for x in range(16):
			if x in (0, 15) or y in (0, 15):
				px[x, y] = noisy(BRONZE_DARK, rnd, 5) + (255,)
			elif not vault and x in (5, 10):
				px[x, y] = noisy(BRONZE, rnd, 5) + (255,)
			elif vault and y in (4, 11):
				px[x, y] = noisy(BRONZE, rnd, 5) + (255,)
	if vault:
		for y in range(6, 10):
			for x in range(6, 10):
				px[x, y] = noisy(BRONZE if (x, y) not in ((7, 7), (8, 8)) else (36, 30, 26), rnd, 5) + (255,)
	else:
		for y in range(1, 15):
			if y % 3 == 0:
				px[7, y] = (64, 58, 54, 255)
				px[8, y + 1 if y < 14 else y] = (64, 58, 54, 255)
	return img


def el(frm, to, faces, rotation=None, emission=0, shade_faces=True):
	e = {"from": frm, "to": to, "faces": faces}
	if rotation:
		e["rotation"] = rotation
	if emission:
		e["light_emission"] = emission
	if not shade_faces:
		e["shade"] = False
	return e


def all_faces(tex, frm, to, cull=None):
	sx, sy, sz = (to[i] - frm[i] for i in range(3))
	faces = {
		"north": {"uv": [0, 0, sx, sy], "texture": tex},
		"south": {"uv": [0, 0, sx, sy], "texture": tex},
		"east": {"uv": [0, 0, sz, sy], "texture": tex},
		"west": {"uv": [0, 0, sz, sy], "texture": tex},
		"up": {"uv": [0, 0, sx, sz], "texture": tex},
		"down": {"uv": [0, 0, sx, sz], "texture": tex},
	}
	for side in cull or ():
		faces[side]["cullface"] = side
	return faces


def pylon_models():
	t = {"stone": f"{NS}:block/resonance_pylon_stone", "bronze": f"{NS}:block/resonance_pylon_bronze",
			"top": f"{NS}:block/resonance_pylon_top", "particle": f"{NS}:block/resonance_pylon_stone"}
	base = [
		el([0, 0, 0], [16, 9, 16], dict(all_faces("#stone", [0, 0, 0], [16, 9, 16], ("down",)))),
		el([1, 9, 1], [15, 11, 15], all_faces("#bronze", [1, 9, 1], [15, 11, 15])),
		el([2, 11, 2], [14, 14, 14], dict(all_faces("#stone", [2, 11, 2], [14, 14, 14]),
				up={"uv": [2, 2, 14, 14], "texture": "#top"})),
		el([4, 14, 4], [12, 15, 12], all_faces("#bronze", [4, 14, 4], [12, 15, 12])),
	]

	def crystal(scale, tex, emission):
		"""Three faceted prisms leaning out of the cradle: a tall centre and two shorter side crystals."""
		h_main, h_side = 15 + 9 * scale, 15 + 5 * scale
		faces = lambda frm, to: {s: {"uv": [0, 0, 4, 16], "texture": tex} for s in ("north", "south", "east", "west")} | \
				{"up": {"uv": [0, 0, 4, 4], "texture": tex}}
		return [
			el([6.5, 15, 6.5], [9.5, h_main, 9.5], faces(None, None),
					{"angle": 22.5, "axis": "y", "origin": [8, 15, 8]}, emission, False),
			el([4, 14.5, 7], [6, h_side, 9], faces(None, None),
					{"angle": 22.5, "axis": "z", "origin": [5, 15, 8]}, emission, False),
			el([10, 14.5, 6.5], [12, h_side - 1, 8.5], faces(None, None),
					{"angle": -22.5, "axis": "x", "origin": [11, 15, 7.5]}, emission, False),
		]

	states = {
		"dormant": crystal(1.0, "#crystal", 3),
		"singing": crystal(1.0, "#crystal", 12),
		"silenced": crystal(1.0, "#crystal", 0),
		"regrowing": crystal(0.45, "#crystal", 6),
		"empty": [
			# Broken stumps in the cradle.
			el([6.5, 15, 6.5], [8.5, 16, 8.5], all_faces("#stone", [6.5, 15, 6.5], [8.5, 16, 8.5])),
			el([9, 15, 8.5], [10.5, 15.5, 10], all_faces("#stone", [9, 15, 8.5], [10.5, 15.5, 10])),
		],
	}
	for state, crystal_els in states.items():
		textures = dict(t)
		if state != "empty":
			textures["crystal"] = f"{NS}:block/resonance_pylon_crystal_{state}"
		write_json(f"models/block/resonance_pylon_{state}.json", {
			"parent": "minecraft:block/block", "textures": textures, "elements": base + crystal_els})
	write_json("blockstates/resonance_pylon.json", {"variants": {
		f"crystal={s}": {"model": f"{NS}:block/resonance_pylon_{s}"} for s in states}})
	write_json("items/resonance_pylon.json", {"model": {"type": "minecraft:model", "model": f"{NS}:block/resonance_pylon_dormant"}})


def facing_variants(model):
	return {"variants": {
		"facing=north": {"model": model},
		"facing=east": {"model": model, "y": 90},
		"facing=south": {"model": model, "y": 180},
		"facing=west": {"model": model, "y": 270},
	}}


def heart_model():
	t = {k: f"{NS}:block/reliquary_heart_{k}" for k in ("side", "top", "front", "crystal")}
	t["particle"] = t["side"]
	body_faces = all_faces("#side", [1, 0, 1], [15, 12, 15], ("down",))
	body_faces["north"] = {"uv": [1, 4, 15, 16], "texture": "#front"}
	body_faces["up"] = {"uv": [1, 1, 15, 15], "texture": "#top"}
	elements = [
		el([0, 0, 0], [16, 2, 16], all_faces("#side", [0, 0, 0], [16, 2, 16], ("down",))),
		el([1, 2, 1], [15, 12, 15], body_faces),
		el([3, 12, 3], [13, 13, 13], all_faces("#side", [3, 12, 3], [13, 13, 13])),
		# The heart crystal in its cradle on top: dim amber, faintly lit.
		el([6.5, 13, 6.5], [9.5, 17, 9.5], {s: {"uv": [0, 0, 3, 4], "texture": "#crystal"} for s in ("north", "south", "east", "west", "up")},
				{"angle": 22.5, "axis": "y", "origin": [8, 13, 8]}, 6, False),
	]
	write_json("models/block/reliquary_heart.json", {"parent": "minecraft:block/block", "textures": t, "elements": elements})
	write_json("blockstates/reliquary_heart.json", facing_variants(f"{NS}:block/reliquary_heart"))
	write_json("items/reliquary_heart.json", {"model": {"type": "minecraft:model", "model": f"{NS}:block/reliquary_heart"}})


def plinth_model():
	t = {k: f"{NS}:block/hoard_plinth_{k}" for k in ("side", "top", "front")}
	t["particle"] = t["side"]
	body = all_faces("#side", [2, 1, 2], [14, 12, 14])
	body["north"] = {"uv": [2, 2, 14, 13], "texture": "#front"}
	elements = [
		el([1, 0, 1], [15, 1, 15], all_faces("#side", [1, 0, 1], [15, 1, 15], ("down",))),
		el([2, 1, 2], [14, 12, 14], body),
		el([0, 12, 0], [16, 14, 16], dict(all_faces("#side", [0, 12, 0], [16, 14, 16]),
				up={"uv": [0, 0, 16, 16], "texture": "#top"})),
	]
	write_json("models/block/hoard_plinth.json", {"parent": "minecraft:block/block", "textures": t, "elements": elements})
	write_json("blockstates/hoard_plinth.json", facing_variants(f"{NS}:block/hoard_plinth"))
	write_json("items/hoard_plinth.json", {"model": {"type": "minecraft:model", "model": f"{NS}:block/hoard_plinth"}})


def seal_models():
	write_json("models/block/reliquary_seal.json", {"parent": "minecraft:block/cube_all", "textures": {"all": f"{NS}:block/reliquary_seal"}})
	write_json("models/block/reliquary_seal_vault.json", {"parent": "minecraft:block/cube_all", "textures": {"all": f"{NS}:block/reliquary_seal_vault"}})
	# Open: nothing drawn, only the particle texture (the block has empty shapes).
	write_json("models/block/reliquary_seal_open.json", {"textures": {"particle": f"{NS}:block/reliquary_seal"}})
	write_json("blockstates/reliquary_seal.json", {"variants": {
		"open=false,vault=false": {"model": f"{NS}:block/reliquary_seal"},
		"open=false,vault=true": {"model": f"{NS}:block/reliquary_seal_vault"},
		"open=true,vault=false": {"model": f"{NS}:block/reliquary_seal_open"},
		"open=true,vault=true": {"model": f"{NS}:block/reliquary_seal_open"},
	}})
	write_json("items/reliquary_seal.json", {"model": {"type": "minecraft:model", "model": f"{NS}:block/reliquary_seal"}})


# ================================================================ particles (white or natural colour)

def soft_dot(size, radius, falloff, seed, centre=None, alpha=255):
	rnd = random.Random(seed)
	img = new(size, size)
	px = img.load()
	cx, cy = centre or ((size - 1) / 2, (size - 1) / 2)
	for y in range(size):
		for x in range(size):
			r = math.hypot(x - cx, y - cy) / radius
			a = max(0.0, 1.0 - r) ** falloff
			a *= 0.85 + 0.3 * rnd.random()
			if a > 0.03:
				px[x, y] = (255, 255, 255, clamp(min(1.0, a) * alpha))
	return img


def ward_haze(frame):
	"""Near-white soft specks (the client tints them pale crystal)."""
	img = new(8, 8)
	px = img.load()
	specks = [(3, 3, 200), (4, 4, 150), (2, 5, 90), (5, 2, 80)] if frame == 0 else [(4, 3, 190), (3, 4, 120), (5, 5, 70)]
	for x, y, a in specks:
		px[x, y] = (250, 252, 255, a)
	return img


def ward_current(frame):
	"""A white mote with a short tail (tinted pale crystal by the client)."""
	img = new(8, 8)
	px = img.load()
	head = (5, 3) if frame == 0 else (5, 4)
	for i, a in enumerate((255, 170, 100, 50)):
		x = head[0] - i
		y = head[1] + (i // 2 if frame == 1 else 0)
		if 0 <= x < 8:
			px[x, y] = (255, 255, 255, a)
	px[head[0], head[1] - 1] = (255, 255, 255, 90)
	return img


def surge_dust(frame):
	"""Sandy grey-brown puffs, natural colour; the frames grow and thin with age."""
	rnd = random.Random(400 + frame)
	img = new(16, 16)
	px = img.load()
	radius = 4.0 + frame * 1.3
	blobs = [(7.5 + rnd.uniform(-1.5, 1.5), 7.5 + rnd.uniform(-1.5, 1.5), radius * rnd.uniform(0.6, 1.0)) for _ in range(4)]
	for y in range(16):
		for x in range(16):
			a = 0.0
			for bx, by, br in blobs:
				a = max(a, 1.0 - math.hypot(x - bx, y - by) / br)
			a = a ** 1.2 * (1.0 - frame * 0.18) * (0.75 + 0.35 * rnd.random())
			if a > 0.05:
				c = mix((168, 150, 120), (128, 118, 104), rnd.random())
				px[x, y] = c + (clamp(min(1.0, a) * 230),)
	return img


def lash_seam(frame):
	"""A streak of grit and dust, natural colour."""
	rnd = random.Random(410 + frame)
	img = new(8, 8)
	px = img.load()
	for x in range(8):
		y = 4 + (1 if (x + frame) % 3 == 0 else 0)
		px[x, y] = noisy((120, 108, 90), rnd, 12) + (clamp(200 - abs(x - 3.5) * 30),)
		if rnd.random() < 0.5:
			px[x, y - 1] = noisy((168, 152, 126), rnd, 10) + (110,)
	for _ in range(3):
		px[rnd.randrange(8), rnd.randrange(2, 7)] = (86, 76, 64, 220)
	return img


def lumen_spark(frame):
	"""A white core with a soft edge (tinted pale blue or red by the client)."""
	img = new(8, 8)
	px = img.load()
	if frame == 0:
		for x, y, a in ((3, 3, 255), (4, 3, 255), (3, 4, 255), (4, 4, 255), (2, 3, 110), (5, 4, 110), (3, 2, 110), (4, 5, 110)):
			px[x, y] = (255, 255, 255, a)
	else:
		for x, y, a in ((3, 3, 255), (4, 4, 200), (4, 3, 140), (3, 4, 140)):
			px[x, y] = (255, 255, 255, a)
	return img


def echo_smoke(frame):
	"""Grey smoke (tinted by the client), loosening with age."""
	rnd = random.Random(420 + frame)
	img = new(16, 16)
	px = img.load()
	radius = 5.0 + frame
	blobs = [(7.5 + rnd.uniform(-2, 2), 7.5 + rnd.uniform(-2, 2), radius * rnd.uniform(0.5, 0.9)) for _ in range(5)]
	for y in range(16):
		for x in range(16):
			a = sum(max(0.0, 1.0 - math.hypot(x - bx, y - by) / br) for bx, by, br in blobs) * 0.6
			a = min(1.0, a) * (1.0 - frame * 0.2) * (0.7 + 0.4 * rnd.random())
			if a > 0.05:
				v = clamp(170 + 50 * rnd.random())
				px[x, y] = (v, v, v, clamp(a * 200))
	return img


PARTICLES = {
	"ward_haze": (2, ward_haze),
	"ward_current": (2, ward_current),
	"surge_dust": (4, surge_dust),
	"lash_seam": (2, lash_seam),
	"lumen_spark": (2, lumen_spark),
	"echo_smoke": (4, echo_smoke),
}


# ================================================================ main

def main():
	for name in SENTINEL_PALETTES:
		write_png(f"entity/ruin_sentinel/{name}.png", sentinel_skin(name))
		write_png(f"entity/ruin_sentinel/{name}_lumen.png", sentinel_lumen(name))
	write_png("item/ruin_sentinel_spawn_egg.png", spawn_egg())
	write_json("models/item/ruin_sentinel_spawn_egg.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/ruin_sentinel_spawn_egg"}})
	write_json("items/ruin_sentinel_spawn_egg.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/ruin_sentinel_spawn_egg"}})

	base = vasch_skin(False)
	unkept = vasch_skin(True)
	write_png("entity/orren_vasch/base.png", base)
	write_png("entity/orren_vasch/unkept.png", unkept)
	write_png("entity/orren_vasch/echo.png", echo_of(base, 210))
	write_png("entity/orren_vasch/echo_unkept.png", echo_of(unkept, 211))
	write_png("gui/sprites/portrait/orren_vasch.png", vasch_portrait())

	write_png("block/resonance_pylon_stone.png", stone_tex(300))
	write_png("block/resonance_pylon_bronze.png", bronze_tex(301))
	write_png("block/resonance_pylon_top.png", pylon_top())
	for state in CRYSTAL:
		write_png(f"block/resonance_pylon_crystal_{state}.png", crystal_tex(state))
	for face in ("side", "top", "front", "crystal"):
		write_png(f"block/reliquary_heart_{face}.png", heart_tex(face))
	for face in ("side", "top", "front"):
		write_png(f"block/hoard_plinth_{face}.png", plinth_tex(face))
	write_png("block/reliquary_seal.png", seal_tex(False))
	write_png("block/reliquary_seal_vault.png", seal_tex(True))
	pylon_models()
	heart_model()
	plinth_model()
	seal_models()

	for pid, (frames, make) in PARTICLES.items():
		for i in range(frames):
			write_png(f"particle/{pid}_{i}.png", make(i))
		write_json(f"particles/{pid}.json", {"textures": [f"{NS}:{pid}_{i}" for i in range(frames)]})


if __name__ == "__main__":
	main()
