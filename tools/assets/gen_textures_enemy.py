#!/usr/bin/env python3
"""Generates the Crystal Poacher and blaster textures (PLACEHOLDER art, original, deterministic).

Writes into src/main/resources/assets/oldrepublic/textures/:
  entity/crystal_poacher.png        64x64 humanoid skin plus the extra parts of PoacherModel
  item/blaster_rifle.png            16x16 inventory sprite (side profile)
  item/blaster_rifle_model.png      16x16 swatch texture for the 3D in-hand model
  item/crystal_poacher_spawn_egg.png 16x16 spawn egg
  particle/muzzle_glow_0..1.png     8x8 soft hot cores (tinted in code)
  particle/bolt_impact_0..1.png     8x8 tiny sparks (tinted in code)
  particle/bolt_scorch_0..2.png     8x8 soft smoke puffs (tinted in code)

The skin follows the UV layout of com.oldrepublic.client.enemy.PoacherModel: the vanilla humanoid
parts (head 0,0; hat 32,0 left empty; body 16,16; arms 40,16; legs 0,16) plus jacket 16,32,
sleeve 40,32, shoulder plate 0,48, goggle strap 20,48, goggle lenses 56,48, belt 0,58 and
cutter 24,58.

Run from the repository root: python3 tools/assets/gen_textures_enemy.py
"""
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


if __name__ == "__main__":
	poacher_skin()
	rifle_sprite()
	rifle_model_texture()
	spawn_egg()
	particles()
