#!/usr/bin/env python3
"""Generates the identity feature's textures (PLACEHOLDER art, original, deterministic).

Writes into src/main/resources/assets/oldrepublic/textures/:
  entity/frontier_settler_<v>.png        64x64 settler skin, variants 0..2
  entity/frontier_settler_<v>_bound.png  the same skin with rope at the wrists and ankles (captive)
  particle/rope_fibre_0..2.png           8x8 frayed rope strands (pale; tinted tan in code)
  gui/sprites/identity/faction_<id>.png  9x9 faction icons for the Standing tab

The skin follows the UV layout of com.oldrepublic.client.identity.SettlerModel: the vanilla
humanoid parts (head 0,0; hat 32,0 left empty; body 16,16; arms 40,16, the left arm mirrors the
right; legs 0,16, mirrored likewise) plus the work tunic 16,32 (8x14x4) and the rolled hood
0,52 (9x3x5).

Run from the repository root: python3 tools/assets/gen_textures_identity.py
"""
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


def paint_box(img, u, v, w, h, d, colour):
	"""Fills every face of a box; colour(face, x, y, fw, fh) returns an RGBA tuple or None."""
	px = img.load()

	for face, (fx, fy, fw, fh) in face_rects(u, v, w, h, d).items():
		for y in range(fh):
			for x in range(fw):
				c = colour(face, x, y, fw, fh)

				if c is not None:
					px[fx + x, fy + y] = c


# ---------------------------------------------------------------- settler palettes
PALETTES = [
	# skin, hair, tunic, trousers, boots, beard
	dict(skin=(214, 168, 128), hair=(92, 60, 36), tunic=(176, 138, 76), trousers=(98, 74, 52), boots=(60, 44, 32), beard=False),
	dict(skin=(120, 82, 58), hair=(30, 24, 22), tunic=(104, 120, 82), trousers=(88, 88, 84), boots=(48, 40, 34), beard=False),
	dict(skin=(190, 146, 108), hair=(170, 160, 132), tunic=(150, 76, 52), trousers=(64, 58, 54), boots=(52, 40, 30), beard=True),
]
ROPE = (196, 164, 112)
ROPE_DARK = (138, 108, 66)


def settler(variant, bound):
	p = PALETTES[variant]
	rnd = random.Random(7100 + variant)
	img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))

	def a(rgb):
		return rgb + (255,)

	def head(face, x, y, fw, fh):
		skin = noisy(p["skin"], rnd, 5)
		hair = noisy(p["hair"], rnd, 10)

		if face == "top":
			return a(hair)

		if face == "bottom":
			return a(shade(p["skin"], 0.8))

		if face == "back":
			return a(hair if y < 6 else shade(skin, 0.9))

		if face in ("east", "west"):
			return a(hair if y < 3 or (y < 5 and (x < 2 if face == "west" else x > 5)) else skin)

		# front: hairline, brows, eyes, nose, mouth and an optional beard.
		if y < 2:
			return a(hair)

		if y == 3 and x in (1, 2, 5, 6):
			return a(shade(p["hair"], 0.8))

		if y == 4 and x in (1, 6):
			return a((236, 236, 230))

		if y == 4 and x in (2, 5):
			return a((52, 64, 80))

		if y == 5 and x in (3, 4):
			return a(shade(p["skin"], 0.85))

		if p["beard"] and y >= 6:
			return a(noisy(p["hair"], rnd, 8))

		if y == 6 and x in (3, 4):
			return a(shade(p["skin"], 0.7))

		return a(skin)

	def body(face, x, y, fw, fh):
		c = noisy(p["tunic"], rnd, 7)

		if y >= fh - 2 and face in ("front", "back", "east", "west"):
			return a(shade(p["trousers"], 0.9))

		return a(shade(c, 0.85 if face in ("east", "west", "bottom") else 1.0))

	def arm(face, x, y, fw, fh):
		if face == "top":
			return a(shade(p["tunic"], 0.9))

		if face == "bottom":
			return a(p["skin"])

		if y < 7:
			return a(noisy(shade(p["tunic"], 0.92), rnd, 6))

		if y == 7:
			return a(shade(p["tunic"], 0.7))

		if bound and y in (9, 10):
			return a(ROPE_DARK if (x + y) % 2 else ROPE)

		return a(noisy(p["skin"], rnd, 4))

	def leg(face, x, y, fw, fh):
		if face == "top":
			return a(p["trousers"])

		if face == "bottom" or y >= 9:
			return a(noisy(p["boots"], rnd, 5))

		if bound and y in (7, 8):
			return a(ROPE_DARK if (x + y) % 2 else ROPE)

		return a(noisy(p["trousers"], rnd, 6))

	def tunic(face, x, y, fw, fh):
		c = noisy(shade(p["tunic"], 1.05), rnd, 8)

		if face in ("top", "bottom"):
			return None

		if y == 9:
			# A plain cord belt.
			return a(shade(p["trousers"], 0.7))

		if y >= fh - 1:
			return a(shade(c, 0.75))

		if face == "front" and x == fw // 2 and y < 5:
			return a(shade(c, 0.7))

		# Worn patches.
		if rnd.random() < 0.05:
			return a(shade(c, 0.82))

		return a(shade(c, 0.9 if face in ("east", "west") else 1.0))

	def hood(face, x, y, fw, fh):
		c = noisy(shade(p["tunic"], 0.8), rnd, 6)
		return a(shade(c, 0.85 if face in ("bottom", "east", "west") else 1.0))

	paint_box(img, 0, 0, 8, 8, 8, head)
	paint_box(img, 16, 16, 8, 12, 4, body)
	paint_box(img, 40, 16, 4, 12, 4, arm)
	paint_box(img, 0, 16, 4, 12, 4, leg)
	paint_box(img, 16, 32, 8, 14, 4, tunic)
	paint_box(img, 0, 52, 9, 3, 5, hood)

	if bound:
		# A rope tied across the chest, under the tunic overlay's open neck.
		px = img.load()
		fx, fy, fw, fh = face_rects(16, 32, 8, 14, 4)["front"]

		for x in range(fw):
			y = fy + 3 + x // 3
			px[fx + x, y] = a(ROPE if x % 2 else ROPE_DARK)

	return img


# ---------------------------------------------------------------- particles
def rope_fibre(index):
	rnd = random.Random(7200 + index)
	img = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
	px = img.load()
	x, y = 1 + index, 6

	for step in range(5 + index % 2):
		px[x, y] = (240, 232, 214, 255 if step < 4 else 170)

		if rnd.random() < 0.6:
			x = min(7, x + 1)

		y = max(0, y - 1)

	# A frayed split at the top end.
	px[min(7, x + 1), max(0, y)] = (220, 210, 190, 150)
	return img


# ---------------------------------------------------------------- faction icons
def icon(shape, rgb, dark):
	img = Image.new("RGBA", (9, 9), (0, 0, 0, 0))
	px = img.load()

	for y, row in enumerate(shape):
		for x, ch in enumerate(row):
			if ch == "#":
				px[x, y] = rgb + (255,)
			elif ch == "+":
				px[x, y] = dark + (255,)

	return img


JEDI = [
	"....#....",
	"...###...",
	".#.###.#.",
	"##.#+#.##",
	"##.#+#.##",
	".##.#.##.",
	"..##.##..",
	"...###...",
	"....#....",
]
SITH = [
	"..#####..",
	".#+++++#.",
	"#++#+#++#",
	"#+++#+++#",
	"#++###++#",
	"#+++#+++#",
	"#++#+#++#",
	".#+++++#.",
	"..#####..",
]
FRONTIER = [
	"....#....",
	"...#+#...",
	"..#+++#..",
	".#+++++#.",
	"#########",
	".#++#++#.",
	".#++#++#.",
	".#+##++#.",
	".#######.",
]


def main():
	for variant in range(len(PALETTES)):
		out("entity/frontier_settler_%d.png" % variant, settler(variant, False))
		out("entity/frontier_settler_%d_bound.png" % variant, settler(variant, True))

	for i in range(3):
		out("particle/rope_fibre_%d.png" % i, rope_fibre(i))

	out("gui/sprites/identity/faction_jedi_order.png", icon(JEDI, (243, 227, 166), (156, 138, 82)))
	out("gui/sprites/identity/faction_sith_empire.png", icon(SITH, (208, 70, 58), (60, 14, 12)))
	out("gui/sprites/identity/faction_frontier.png", icon(FRONTIER, (201, 166, 107), (110, 82, 50)))


if __name__ == "__main__":
	main()
