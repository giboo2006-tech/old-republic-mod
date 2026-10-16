#!/usr/bin/env python3
"""Generates the enemy textures and item JSON (PLACEHOLDER art, original, deterministic).

Writes into src/main/resources/assets/oldrepublic/textures/:
  entity/crystal_poacher.png        64x64 humanoid skin plus the extra parts of PoacherModel
  item/blaster_rifle.png            16x16 inventory sprite (side profile)
  item/blaster_rifle_model.png      16x16 swatch texture for the 3D in-hand model
  item/crystal_poacher_spawn_egg.png 16x16 spawn egg
  particle/muzzle_glow_0..1.png     8x8 soft hot cores (tinted in code)
  particle/bolt_impact_0..1.png     8x8 tiny sparks (tinted in code)
  particle/bolt_scorch_0..2.png     8x8 soft smoke puffs (tinted in code)
  entity/sith_acolyte_{acolyte,brenn,deserter}.png  64x64 skins in the AcolyteModel layout
  entity/poacher_captain.png        64x64 skin in the CaptainModel layout
  item/vibroblade.png, item/blaster_pistol.png  16x16 handheld sprites
  item/sith_acolyte_spawn_egg.png, item/poacher_captain_spawn_egg.png  16x16 spawn eggs
and models/item + items JSON for the vibroblade, the blaster pistol and the two spawn eggs.

AcolyteModel layout: vanilla humanoid (the hat layer 32,0 is the hood; empty on the hoodless
skins) plus robe 16,32, sleeves 40,32 (the left one mirrored), skirt 0,48 and sash 24,48.
CaptainModel layout: vanilla humanoid (the hat layer is the hat's crown) plus coat 16,32,
sleeves 40,32, coat tails 0,48 and hat brim 24,48.

The skin follows the UV layout of com.oldrepublic.client.enemy.PoacherModel: the vanilla humanoid
parts (head 0,0; hat 32,0 left empty; body 16,16; arms 40,16; legs 0,16) plus jacket 16,32,
sleeve 40,32, shoulder plate 0,48, goggle strap 20,48, goggle lenses 56,48, belt 0,58 and
cutter 24,58.

Run from the repository root: python3 tools/assets/gen_textures_enemy.py
"""
import json
import math
import os
import random

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "src", "main", "resources", "assets", "oldrepublic", "textures")


def out(rel, img):
	path = os.path.join(ROOT, rel)
	os.makedirs(os.path.dirname(path), exist_ok=True)
	img.save(path)
	print("wrote", os.path.normpath(path))


def clamp(v):
	return max(0, min(255, int(round(v))))


def noisy(rgb, rnd, amount):
	return tuple(clamp(c + rnd.uniform(-amount, amount)) for c in rgb)


def shade(rgb, factor):
	return tuple(clamp(c * factor) for c in rgb)


# ---------------------------------------------------------------- box UV helper
FACES = ("top", "bottom", "east", "front", "west", "back")


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
	"""Calls painter(face, fx, fy, fw, fh) for every texel; it returns RGBA or None (transparent)."""
	for face, (x0, y0, fw, fh) in face_rects(u, v, w, h, d).items():
		for fy in range(fh):
			for fx in range(fw):
				c = painter(face, fx, fy, fw, fh)
				if c is not None:
					px[x0 + fx, y0 + fy] = c if len(c) == 4 else c + (255,)


# ---------------------------------------------------------------- poacher skin
SKIN = (176, 130, 100)
SKIN_DARK = (140, 98, 74)
HAIR = (46, 38, 32)
STUBBLE = (120, 88, 70)
SHIRT = (88, 96, 82)
JACKET = (118, 86, 56)
JACKET_DARK = (84, 60, 40)
TROUSERS = (70, 66, 58)
BOOT = (42, 34, 28)
STEEL = (150, 152, 150)
STEEL_DARK = (92, 94, 96)
STRAP = (40, 34, 30)
LENS = (230, 150, 40)
BELT = (70, 48, 32)
POUCH = (96, 84, 60)
CUTTER_TIP = (255, 120, 40)


def poacher_skin():
	rnd = random.Random(11)
	img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
	px = img.load()

	def head(face, x, y, w, h):
		if face == "top":
			return noisy(HAIR, rnd, 8)
		if face == "bottom":
			return noisy(SKIN_DARK, rnd, 6)
		if y < 2:
			return noisy(HAIR, rnd, 8)
		if face == "front":
			# Eyes, a scar across one brow, stubble on the jaw.
			if y == 4 and x in (1, 2, 5, 6):
				return (236, 230, 220) if x in (1, 6) else (52, 70, 60)
			if y == 3 and x in (1, 2, 5, 6):
				return shade(HAIR, 1.1)
			if (x, y) in ((5, 2), (6, 3)):
				return (196, 120, 106)
			if y == 6 and x in (3, 4):
				return (128, 80, 70)
			if y >= 5:
				return noisy(STUBBLE, rnd, 10)
			return noisy(SKIN, rnd, 7)
		if face == "back":
			return noisy(HAIR, rnd, 8) if y < 6 else noisy(SKIN_DARK, rnd, 6)
		# Sides: ears and short hair above them.
		if y < 4 and (x > 4 if face == "east" else x < 3):
			return noisy(HAIR, rnd, 8)
		if y >= 6:
			return noisy(STUBBLE, rnd, 10)
		return noisy(SKIN, rnd, 7)

	paint_box(px, 0, 0, 8, 8, 8, head)

	def body(face, x, y, w, h):
		if face in ("top", "bottom"):
			return noisy(SHIRT, rnd, 6)
		if y >= 10:
			return noisy(TROUSERS, rnd, 6)
		return noisy(SHIRT, rnd, 8)

	paint_box(px, 16, 16, 8, 12, 4, body)

	def arm(face, x, y, w, h):
		if face == "top":
			return noisy(SHIRT, rnd, 6)
		if face == "bottom" or y >= 10:
			# Fingerless glove over the hand.
			return noisy(STRAP, rnd, 6) if (x + y) % 3 else noisy(SKIN_DARK, rnd, 6)
		if y == 9:
			return noisy(SKIN, rnd, 6)
		return noisy(SHIRT, rnd, 8)

	paint_box(px, 40, 16, 4, 12, 4, arm)

	def leg(face, x, y, w, h):
		if face == "top":
			return noisy(TROUSERS, rnd, 6)
		if face == "bottom" or y >= 9:
			c = noisy(BOOT, rnd, 6)
			return shade(c, 1.3) if y == 9 else c
		if face in ("east", "west") and 3 <= y <= 6:
			# Cargo pocket.
			return noisy(shade(TROUSERS, 1.15), rnd, 5) if y > 3 else shade(TROUSERS, 0.7)
		if y in (4, 5) and (x * 7 + y) % 5 == 0:
			return shade(TROUSERS, 0.8)
		return noisy(TROUSERS, rnd, 7)

	paint_box(px, 0, 16, 4, 12, 4, leg)

	def jacket(face, x, y, w, h):
		if face == "bottom":
			return None
		if face == "top":
			return noisy(JACKET_DARK, rnd, 6)
		if y >= 10:
			# Hem stops above the belt line; the belt part covers it.
			return None
		if face == "front":
			# Open vest: the shirt shows down the middle, collar flaps at the top.
			if 3 <= x <= 4 and y >= 1:
				return None
			if y == 0 or (y == 1 and x in (2, 5)):
				return noisy(JACKET_DARK, rnd, 5)
			if x in (2, 5):
				return shade(JACKET, 0.82)
			if (x, y) in ((1, 6), (6, 6), (1, 7), (6, 7)):
				return noisy(POUCH, rnd, 6)
		if face == "back" and y in (2, 3) and 1 <= x <= 6:
			# Patched panel.
			return noisy(shade(JACKET, 1.18), rnd, 5)
		if rnd.random() < 0.06:
			return shade(JACKET, 0.72)
		return noisy(JACKET, rnd, 9)

	paint_box(px, 16, 32, 8, 12, 4, jacket)

	def sleeve(face, x, y, w, h):
		if face == "bottom" or y >= 8:
			return None
		if face == "top":
			return noisy(JACKET_DARK, rnd, 6)
		if y == 7:
			return noisy(JACKET_DARK, rnd, 5)
		if y in (2, 5):
			return shade(JACKET, 0.86)
		return noisy(JACKET, rnd, 9)

	paint_box(px, 40, 32, 4, 12, 4, sleeve)

	def plate(face, x, y, w, h):
		c = noisy(STEEL, rnd, 10)
		if face == "bottom":
			return STEEL_DARK
		if face == "top":
			if rnd.random() < 0.15:
				return shade(c, 0.75)
			return c if (x + y) % 4 else shade(c, 1.12)
		if y == h - 1:
			return STEEL_DARK
		if (x, y) in ((1, 1), (w - 2, 1)):
			return (70, 70, 72)
		return c

	paint_box(px, 0, 48, 5, 4, 5, plate)

	def strap(face, x, y, w, h):
		if face in ("top", "bottom"):
			return None
		return noisy(STRAP, rnd, 6) if y == 0 else noisy(shade(STRAP, 1.25), rnd, 5)

	paint_box(px, 20, 48, 8, 2, 8, strap)

	def lens(face, x, y, w, h):
		if face == "front":
			if x == 0 and y == 0:
				return (255, 228, 160)
			return LENS if y == 0 else shade(LENS, 0.7)
		return STEEL_DARK

	paint_box(px, 56, 48, 3, 2, 1, lens)

	def belt(face, x, y, w, h):
		if face in ("top", "bottom"):
			return noisy(BELT, rnd, 5)
		if face == "front" and x in (3, 4):
			return STEEL if y == 0 else STEEL_DARK
		if face in ("east", "west", "back") and (x + (0 if face == "back" else 1)) % 3 == 0:
			return noisy(POUCH, rnd, 6)
		return noisy(BELT, rnd, 7)

	paint_box(px, 0, 58, 8, 2, 4, belt)

	def cutter(face, x, y, w, h):
		if face == "bottom":
			return CUTTER_TIP
		if y == h - 1:
			return CUTTER_TIP
		if y == 0:
			return STEEL_DARK
		return noisy(STEEL, rnd, 8)

	paint_box(px, 24, 58, 1, 4, 2, cutter)
	out("entity/crystal_poacher.png", img)


# ---------------------------------------------------------------- rifle
GUNMETAL = (58, 62, 66)
GUNMETAL_LIGHT = (104, 108, 112)
BARREL = (34, 36, 40)
GRIP = (92, 66, 44)
CELL = (220, 64, 40)


def rifle_sprite():
	"""Inventory sprite: side profile, muzzle to the right, slight upward tilt by layout."""
	rnd = random.Random(21)
	img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
	px = img.load()

	def put(x, y, c):
		px[x, y] = c + (255,)

	# Stock (left), receiver, barrel (right).
	for x in range(1, 5):
		for y in range(7, 10 + (1 if x < 3 else 0)):
			put(x, y, noisy(GRIP, rnd, 8))
	put(1, 7, shade(GRIP, 0.7))
	for x in range(4, 11):
		for y in range(6, 9):
			put(x, y, noisy(GUNMETAL, rnd, 6))
	for x in range(4, 11):
		put(x, 6, GUNMETAL_LIGHT)
	for x in range(10, 16):
		put(x, 7, noisy(BARREL, rnd, 4))
	put(12, 6, GUNMETAL_LIGHT)
	put(15, 6, GUNMETAL_LIGHT)
	put(15, 8, GUNMETAL_LIGHT)
	# Scope.
	for x in range(6, 10):
		put(x, 5, noisy(BARREL, rnd, 4))
	put(9, 5, (150, 190, 200))
	# Grip, trigger and power cell.
	for y in range(9, 12):
		put(6, y, noisy(GRIP, rnd, 6))
		put(7, y, noisy(shade(GRIP, 0.8), rnd, 6))
	put(8, 9, BARREL)
	put(8, 10, BARREL)
	put(9, 9, CELL)
	put(10, 9, shade(CELL, 0.75))
	out("item/blaster_rifle.png", img)


def rifle_model_texture():
	"""Swatches for blaster_rifle_in_hand.json; each 4x4 block (in model UV units) is one material.

	Row 0: gunmetal, light metal, grip, power cell. Row 1: barrel (with vent marks), scope glass.
	"""
	rnd = random.Random(31)
	img = Image.new("RGBA", (16, 16), (0, 0, 0, 255))
	px = img.load()
	swatches = {
		(0, 0): GUNMETAL,
		(1, 0): GUNMETAL_LIGHT,
		(2, 0): GRIP,
		(3, 0): CELL,
		(0, 1): BARREL,
		(1, 1): (120, 170, 186),
		(2, 1): shade(GRIP, 0.7),
		(3, 1): STEEL_DARK,
	}
	for (sx, sy), base in swatches.items():
		for y in range(4):
			for x in range(4):
				c = noisy(base, rnd, 7)
				if (sx, sy) == (0, 1) and x == 1:
					c = shade(base, 0.6)
				if (sx, sy) == (3, 0) and (x, y) == (1, 1):
					c = (255, 190, 150)
				px[sx * 4 + x, sy * 4 + y] = c + (255,)
	for y in range(8, 16):
		for x in range(16):
			px[x, y] = noisy(GUNMETAL, rnd, 5) + (255,)
	out("item/blaster_rifle_model.png", img)


def spawn_egg():
	"""Egg in jacket brown with amber goggle-lens spots."""
	rnd = random.Random(41)
	img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
	px = img.load()
	cx, cy = 7.5, 8.6
	for y in range(16):
		for x in range(16):
			dx = (x - cx) / 5.2
			dy = (y - cy) / (6.6 if y < cy else 5.6)
			r = dx * dx + dy * dy
			if r > 1.0:
				continue
			edge = r > 0.78
			light = (x - cx) * -0.04 + (y - cy) * -0.05
			c = shade(JACKET, 1.0 + light)
			if edge:
				c = shade(c, 0.7)
			px[x, y] = noisy(c, rnd, 6) + (255,)
	for (x, y) in ((5, 6), (6, 6), (9, 7), (10, 7), (6, 11), (9, 12), (10, 12)):
		px[x, y] = (LENS if y < 9 else STEEL) + (255,)
	px[5, 5] = (255, 228, 160, 255)
	out("item/crystal_poacher_spawn_egg.png", img)


# ---------------------------------------------------------------- particles
def radial(size, falloff, core, seed=0, wobble=0.0):
	"""Near-white sprite with alpha falling off from the centre; tinted in code."""
	rnd = random.Random(seed)
	img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
	px = img.load()
	c = (size - 1) / 2
	for y in range(size):
		for x in range(size):
			d = math.hypot(x - c, y - c) / (size / 2)
			d += rnd.uniform(-wobble, wobble)
			a = max(0.0, 1.0 - d) ** falloff
			if d < core:
				a = 1.0
			if a <= 0.02:
				continue
			v = clamp(255 - 40 * min(1.0, d))
			px[x, y] = (v, v, v, clamp(a * 255))
	return img


def spark(seed):
	rnd = random.Random(seed)
	img = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
	px = img.load()
	for (x, y) in ((3, 3), (4, 3), (3, 4), (4, 4)):
		px[x, y] = (255, 255, 255, 255)
	for (x, y) in ((2, 3), (5, 4), (3, 2), (4, 5)):
		px[x, y] = (255, 255, 255, 150 + rnd.randint(0, 60))
	return img


def particles():
	out("particle/muzzle_glow_0.png", radial(8, 1.6, 0.18, seed=1))
	out("particle/muzzle_glow_1.png", radial(8, 2.2, 0.28, seed=2))
	out("particle/bolt_impact_0.png", spark(3))
	out("particle/bolt_impact_1.png", radial(8, 3.0, 0.2, seed=4))
	out("particle/bolt_scorch_0.png", radial(8, 1.2, 0.0, seed=5, wobble=0.15))
	out("particle/bolt_scorch_1.png", radial(8, 1.0, 0.0, seed=6, wobble=0.22))
	out("particle/bolt_scorch_2.png", radial(8, 1.4, 0.0, seed=7, wobble=0.3))


# ---------------------------------------------------------------- Sith Acolyte (three skins)
ACOLYTE_PALETTES = {
	# Hooded cultist: near-black robe, dark red trim and sash, pale skin, yellowed eyes.
	"acolyte": {"robe": (36, 30, 34), "trim": (118, 24, 30), "skin": (192, 154, 134), "hair": (30, 24, 24),
			"eyes": (214, 170, 60), "hood": True, "tatters": 0.0, "scar": False},
	# Brenn, the rival: no hood, charcoal robe with a crimson sash, tanned skin, red-brown hair, a
	# scar through one eyebrow.
	"brenn": {"robe": (52, 50, 54), "trim": (150, 28, 34), "skin": (164, 116, 88), "hair": (104, 50, 32),
			"eyes": (70, 60, 52), "hood": False, "tatters": 0.0, "scar": True},
	# Deserter: faded, patched grey-brown robe with the trim torn off, unkempt hair.
	"deserter": {"robe": (88, 78, 70), "trim": (110, 62, 56), "skin": (176, 132, 104), "hair": (64, 54, 44),
			"eyes": (60, 74, 66), "hood": False, "tatters": 0.25, "scar": False},
}


def acolyte_skin(name):
	"""64x64 skin in the AcolyteModel layout (see that class and the module docstring)."""
	pal = ACOLYTE_PALETTES[name]
	rnd = random.Random(51 + len(name))
	img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
	px = img.load()
	robe, trim, skin, hair = pal["robe"], pal["trim"], pal["skin"], pal["hair"]

	def head(face, x, y, w, h):
		if face == "top":
			return noisy(hair, rnd, 8)
		if face == "bottom":
			return noisy(shade(skin, 0.8), rnd, 5)
		if face == "front":
			if y < 2:
				return noisy(hair, rnd, 8)
			if y == 3 and x in (1, 2, 5, 6):
				return shade(hair, 1.1)
			if y == 4 and x in (1, 2, 5, 6):
				return (232, 226, 216) if x in (1, 6) else pal["eyes"]
			if pal["scar"] and (x, y) in ((5, 2), (5, 3), (6, 5)):
				return (196, 118, 104)
			if y == 6 and x in (3, 4):
				return shade(skin, 0.68)
			return noisy(skin, rnd, 6)
		if face == "back":
			return noisy(hair, rnd, 8) if y < 7 else noisy(shade(skin, 0.85), rnd, 5)
		if y < 3 or (y < 5 and (x > 4 if face == "east" else x < 3)):
			return noisy(hair, rnd, 8)
		return noisy(skin, rnd, 6)

	paint_box(px, 0, 0, 8, 8, 8, head)

	def hood(face, x, y, w, h):
		if not pal["hood"] or face == "bottom":
			return None
		if face == "front" and 1 <= x <= 6 and y >= 2:
			# The face opening, shadowed at its upper edge.
			return (12, 10, 12, 150) if y == 2 else None
		c = noisy(robe, rnd, 5)
		if face == "front" and (x in (0, 7) or y == 1):
			return shade(trim, 0.85)
		if face == "back" and y >= 6:
			return shade(c, 0.85)
		return c

	paint_box(px, 32, 0, 8, 8, 8, hood)

	def plain(face, x, y, w, h):
		if face == "top":
			return noisy(shade(robe, 0.9), rnd, 5)
		return noisy(robe, rnd, 6)

	paint_box(px, 16, 16, 8, 12, 4, plain)

	def arm(face, x, y, w, h):
		if face == "bottom" or y >= 10:
			# Dark gloves.
			return noisy((34, 28, 26), rnd, 4)
		return noisy(robe, rnd, 6)

	paint_box(px, 40, 16, 4, 12, 4, arm)

	def leg(face, x, y, w, h):
		if face == "bottom" or y >= 9:
			return noisy((30, 26, 24), rnd, 4)
		return noisy(shade(robe, 0.8), rnd, 5)

	paint_box(px, 0, 16, 4, 12, 4, leg)

	def tattered(c, y, h):
		if pal["tatters"] > 0 and y >= h - 2 and rnd.random() < pal["tatters"] * 2:
			return None
		if pal["tatters"] > 0 and rnd.random() < pal["tatters"] * 0.3:
			return noisy(shade(c, 1.25), rnd, 6)
		return c

	def outer_robe(face, x, y, w, h):
		if face == "bottom":
			return None
		c = noisy(robe, rnd, 7)
		if face == "top":
			return shade(c, 0.9)
		if face == "front":
			# A crossed-over front: the trim runs diagonally from the left shoulder.
			if x == min(7, y // 2 + 1) and pal["tatters"] < 0.2:
				return noisy(trim, rnd, 5)
			if y == 0:
				return noisy(shade(trim, 0.8), rnd, 5)
		if face == "back" and x in (3, 4) and y > 1:
			return shade(c, 0.85)
		return tattered(c, y, h)

	paint_box(px, 16, 32, 8, 12, 4, outer_robe)

	def sleeve(face, x, y, w, h):
		if face == "bottom" or y >= 10:
			return None
		c = noisy(robe, rnd, 7)
		if face == "top":
			return shade(c, 0.9)
		if y == 9 and pal["tatters"] < 0.2:
			return noisy(trim, rnd, 5)
		return tattered(c, y, 10)

	paint_box(px, 40, 32, 4, 12, 4, sleeve)

	def skirt(face, x, y, w, h):
		if face in ("top", "bottom"):
			return None
		c = noisy(shade(robe, 0.92), rnd, 7)
		if face in ("front", "back") and x in (3, 4):
			# The split at the front and back, so the legs can move.
			return None if y >= 2 else shade(c, 0.8)
		if y == h - 1 and pal["tatters"] < 0.2:
			return noisy(trim, rnd, 5)
		return tattered(c, y, h)

	paint_box(px, 0, 48, 8, 6, 4, skirt)

	def sash(face, x, y, w, h):
		if face in ("top", "bottom"):
			return noisy(shade(trim, 0.8), rnd, 5)
		if face == "front" and x in (3, 4):
			return (150, 146, 140) if y == 0 else (96, 92, 90)
		return noisy(trim, rnd, 6)

	paint_box(px, 24, 48, 8, 2, 4, sash)
	out(f"entity/sith_acolyte_{name}.png", img)


# ---------------------------------------------------------------- Poacher Captain
CAPTAIN_COAT = (96, 46, 40)
CAPTAIN_COAT_DARK = (64, 30, 28)
CAPTAIN_SKIN = (170, 122, 96)
CAPTAIN_HAIR = (34, 28, 26)
BRASS = (190, 150, 70)
HAT = (58, 44, 34)


def captain_skin():
	"""64x64 skin in the CaptainModel layout (coat 16,32; sleeves 40,32; tails 0,48; brim 24,48)."""
	rnd = random.Random(61)
	img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
	px = img.load()

	def head(face, x, y, w, h):
		if face == "top":
			return noisy(CAPTAIN_HAIR, rnd, 6)
		if face == "bottom":
			return noisy(shade(CAPTAIN_SKIN, 0.8), rnd, 5)
		if face == "front":
			if y < 2:
				return noisy(CAPTAIN_HAIR, rnd, 6)
			if y == 3 and x in (1, 2, 5, 6):
				return shade(CAPTAIN_HAIR, 1.2)
			if y == 4 and x in (1, 2, 5, 6):
				return (236, 230, 222) if x in (1, 6) else (60, 86, 70)
			if (x, y) in ((1, 5), (1, 6), (2, 6)):
				# An old burn scar along the jaw.
				return (150, 96, 84)
			if y == 6 and x in (3, 4):
				return (140, 72, 64)
			return noisy(CAPTAIN_SKIN, rnd, 6)
		if face == "back":
			# A braid down the back.
			if x in (3, 4):
				return noisy(shade(CAPTAIN_HAIR, 1.3 if y % 2 else 1.0), rnd, 4)
			return noisy(CAPTAIN_HAIR, rnd, 6) if y < 6 else noisy(shade(CAPTAIN_SKIN, 0.85), rnd, 5)
		if y < 4:
			return noisy(CAPTAIN_HAIR, rnd, 6)
		return noisy(CAPTAIN_SKIN, rnd, 6)

	paint_box(px, 0, 0, 8, 8, 8, head)

	def crown(face, x, y, w, h):
		if face == "bottom":
			return None
		if face == "top":
			return noisy(HAT, rnd, 5)
		if y >= 3:
			return None
		return noisy(BRASS, rnd, 6) if y == 2 else noisy(HAT, rnd, 5)

	paint_box(px, 32, 0, 8, 8, 8, crown)

	def body(face, x, y, w, h):
		if y >= 10:
			return noisy(TROUSERS, rnd, 6)
		return noisy((150, 140, 122), rnd, 6)

	paint_box(px, 16, 16, 8, 12, 4, body)

	def arm(face, x, y, w, h):
		if face == "bottom" or y >= 10:
			return noisy(STRAP, rnd, 5)
		return noisy((150, 140, 122), rnd, 6)

	paint_box(px, 40, 16, 4, 12, 4, arm)

	def leg(face, x, y, w, h):
		if face == "bottom" or y >= 7:
			c = noisy(BOOT, rnd, 5)
			return shade(c, 1.35) if y == 7 else c
		return noisy(TROUSERS, rnd, 6)

	paint_box(px, 0, 16, 4, 12, 4, leg)

	def coat(face, x, y, w, h):
		if face == "bottom":
			return None
		c = noisy(CAPTAIN_COAT, rnd, 8)
		if face == "top":
			return noisy(CAPTAIN_COAT_DARK, rnd, 5)
		if face == "front":
			if 3 <= x <= 4 and y >= 2:
				return None
			if x in (2, 5) and y % 3 == 1:
				return BRASS
			if y < 2:
				return noisy(CAPTAIN_COAT_DARK, rnd, 5)
		if y == 9:
			# The belt line.
			return noisy(BELT, rnd, 5) if not (face == "front" and x in (2, 5)) else BRASS
		return c

	paint_box(px, 16, 32, 8, 12, 4, coat)

	def sleeve(face, x, y, w, h):
		if face == "bottom" or y >= 10:
			return None
		if y >= 8:
			return noisy(CAPTAIN_COAT_DARK, rnd, 5)
		return noisy(CAPTAIN_COAT, rnd, 8)

	paint_box(px, 40, 32, 4, 12, 4, sleeve)

	def tails(face, x, y, w, h):
		if face in ("top", "bottom"):
			return None
		if face == "front" and 2 <= x <= 5:
			return None
		if face in ("east", "west") and y >= 3 and x == (0 if face == "east" else w - 1):
			return None
		return noisy(shade(CAPTAIN_COAT, 0.92), rnd, 7) if y < h - 1 else noisy(CAPTAIN_COAT_DARK, rnd, 4)

	paint_box(px, 0, 48, 8, 5, 4, tails)

	def brim(face, x, y, w, h):
		c = noisy(HAT, rnd, 5)
		if face == "bottom":
			return shade(c, 0.7)
		if face == "top" and (x in (0, w - 1) or y in (0, h - 1)):
			return shade(c, 1.15)
		return c

	paint_box(px, 24, 48, 10, 1, 10, brim)
	out("entity/poacher_captain.png", img)


# ---------------------------------------------------------------- vibroblade and blaster pistol
def vibroblade_sprite():
	"""Handheld sprite: a heavy knife-sword, grip lower left, point upper right."""
	rnd = random.Random(71)
	img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
	px = img.load()
	for i in range(4, 15):
		x, y = i, 15 - i
		px[x, y] = noisy(STEEL, rnd, 8) + (255,)
		if y + 1 < 16:
			px[x, y + 1] = noisy(STEEL_DARK, rnd, 6) + (255,)
		if i % 3 == 0 and x - 1 >= 0:
			# The vibration emitters along the spine glow faintly.
			px[x - 1, y] = (150, 200, 220, 255)
	px[14, 1] = (230, 234, 236, 255)
	# Cross-guard and grip.
	for (x, y) in ((3, 10), (4, 11), (5, 12), (2, 11), (6, 13)):
		px[x, y] = noisy(GUNMETAL, rnd, 5) + (255,)
	for i in range(3):
		px[1 + i, 14 - i] = noisy(GRIP, rnd, 6) + (255,)
	px[0, 15] = GUNMETAL_LIGHT + (255,)
	out("item/vibroblade.png", img)


def pistol_sprite():
	"""Handheld sprite: compact blaster pistol, muzzle upper right."""
	rnd = random.Random(81)
	img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
	px = img.load()
	for x in range(4, 13):
		for y in range(5, 8):
			px[x, y] = noisy(GUNMETAL, rnd, 6) + (255,)
		px[x, 5] = GUNMETAL_LIGHT + (255,)
	for x in range(12, 15):
		px[x, 6] = noisy(BARREL, rnd, 4) + (255,)
	px[14, 5] = GUNMETAL_LIGHT + (255,)
	for y in range(8, 13):
		px[5, y] = noisy(GRIP, rnd, 6) + (255,)
		px[6, y] = noisy(shade(GRIP, 0.8), rnd, 6) + (255,)
	px[7, 8] = BARREL + (255,)
	px[8, 8] = BARREL + (255,)
	px[10, 7] = CELL + (255,)
	px[11, 7] = shade(CELL, 0.75) + (255,)
	out("item/blaster_pistol.png", img)


def egg(name, base, spots, seed):
	"""A spawn egg in {base} with {spots}."""
	rnd = random.Random(seed)
	img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
	px = img.load()
	cx, cy = 7.5, 8.6
	for y in range(16):
		for x in range(16):
			dx = (x - cx) / 5.2
			dy = (y - cy) / (6.6 if y < cy else 5.6)
			r = dx * dx + dy * dy
			if r > 1.0:
				continue
			light = (x - cx) * -0.04 + (y - cy) * -0.05
			c = shade(base, 1.0 + light)
			if r > 0.78:
				c = shade(c, 0.7)
			px[x, y] = noisy(c, rnd, 6) + (255,)
	for (x, y) in ((5, 6), (6, 6), (9, 7), (10, 7), (6, 11), (9, 12), (10, 12)):
		px[x, y] = spots + (255,)
	out(f"item/{name}_spawn_egg.png", img)


# ---------------------------------------------------------------- item JSON
ASSETS = os.path.join(ROOT, "..")


def write_json(rel, data):
	path = os.path.join(ASSETS, rel)
	os.makedirs(os.path.dirname(path), exist_ok=True)
	with open(path, "w", encoding="utf-8") as f:
		json.dump(data, f, indent=2)
		f.write("\n")
	print("wrote", os.path.normpath(path))


def item_json(name, parent):
	write_json(f"models/item/{name}.json", {"parent": parent, "textures": {"layer0": f"oldrepublic:item/{name}"}})
	write_json(f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"oldrepublic:item/{name}"}})


def m2_assets():
	for name in ACOLYTE_PALETTES:
		acolyte_skin(name)
	captain_skin()
	vibroblade_sprite()
	pistol_sprite()
	egg("sith_acolyte", (40, 32, 36), (150, 28, 34), 91)
	egg("poacher_captain", CAPTAIN_COAT, BRASS, 92)
	item_json("vibroblade", "minecraft:item/handheld")
	item_json("blaster_pistol", "minecraft:item/handheld")
	item_json("sith_acolyte_spawn_egg", "minecraft:item/generated")
	item_json("poacher_captain_spawn_egg", "minecraft:item/generated")


if __name__ == "__main__":
	poacher_skin()
	rifle_sprite()
	rifle_model_texture()
	spawn_egg()
	particles()
	m2_assets()
