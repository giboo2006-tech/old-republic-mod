#!/usr/bin/env python3
"""Generates Chapter Two's textures (PLACEHOLDER art, original, deterministic).

Writes into src/main/resources/assets/oldrepublic/textures/:
  entity/jedi_mentor.png                 64x64 Master Ilyra Saelen: sand and brown robes, dark under-tunic,
                                         lowered hood, grey-streaked hair
  entity/sith_mentor.png                 64x64 Overseer Teyvar Sorn: Imperial greys, black half-cape,
                                         a scar over one brow, gloves
  gui/sprites/portrait/<role>_mentor.png 32x32 dialogue / Standing portraits (head and shoulders)
  item/poachers_ledger.png               16x16 battered account book with a clasp
  particle/voice_warm_0..2.png           8x8 soft glints (tinted pale gold in code)
  particle/voice_dust_0..2.png           8x8 irregular dust specks (tinted grey-blue in code)
  particle/ash_mote_0..2.png             8x8 ash flakes (tinted dark grey in code)

The skins follow com.oldrepublic.client.mentor.MentorModel: the vanilla humanoid parts (head 0,0;
hat 32,0 left empty; body 16,16; arms 40,16, the left arm mirrors the right; legs 0,16, mirrored
likewise), the robe 16,32 (8x14x4), the skirt 0,50 (9x9x5), the hood 36,50 (9x3x5) and the cape
40,32 (8x16x1).

Run from the repository root: python3 tools/assets/gen_textures_mentor.py
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


def fabric(base, rnd, amount=7):
	"""A plain woven colour with a little noise and darker vertical folds."""
	def colour(face, x, y, fw, fh):
		fold = 0.9 if (x % 3 == 2 and face in ("front", "back")) else 1.0
		return noisy(shade(base, fold), rnd, amount) + (255,)
	return colour


# ---------------------------------------------------------------- skins

JEDI = {
	"skin": (196, 150, 118),
	"hair": (92, 78, 66),
	"grey": (168, 164, 158),
	"eyes": (70, 96, 84),
	"robe": (186, 160, 118),
	"under": (66, 52, 42),
	"belt": (98, 70, 44),
	"boots": (58, 44, 34),
	"hood": (150, 120, 84),
}

SITH = {
	"skin": (206, 170, 146),
	"hair": (34, 32, 34),
	"grey": (34, 32, 34),
	"eyes": (120, 72, 46),
	"robe": (92, 96, 102),
	"under": (52, 54, 60),
	"belt": (30, 30, 34),
	"boots": (24, 24, 28),
	"cape": (18, 18, 22),
	"glove": (28, 28, 32),
}


def head(img, pal, rnd, sith):
	skin, hair = pal["skin"], pal["hair"]

	def colour(face, x, y, fw, fh):
		if face == "top":
			return noisy(hair, rnd, 8) + (255,)
		if face == "bottom":
			return shade(skin, 0.8) + (255,)
		if face == "front":
			if y < 2:
				streak = not sith and x in (2, 5)
				return noisy(pal["grey"] if streak else hair, rnd, 6) + (255,)
			if y == 3 and x in (1, 2, 5, 6):
				# Brows; the Overseer's right brow is split by a pale scar.
				if sith and x == 2:
					return (224, 196, 182, 255)
				return shade(hair, 0.9) + (255,)
			if y == 4 and x in (2, 5):
				return pal["eyes"] + (255,)
			if y == 4 and x in (1, 6):
				return (236, 232, 226, 255)
			if sith and x == 2 and y in (2, 5):
				return (224, 196, 182, 255)
			if y == 6 and 3 <= x <= 4:
				return shade(skin, 0.75) + (255,)
			return noisy(skin, rnd, 4) + (255,)
		# Sides and back: hair, worn long and tied for the Jedi, cropped for the Sith.
		limit = fh if not sith else 3
		if y < limit or (face == "back" and y < fh):
			streak = not sith and x % 4 == 1 and y > 1
			return noisy(pal["grey"] if streak else hair, rnd, 7) + (255,)
		return noisy(skin, rnd, 4) + (255,)

	paint_box(img, 0, 0, 8, 8, 8, colour)


def body(img, pal, rnd):
	robe, under, belt = pal["robe"], pal["under"], pal["belt"]

	def colour(face, x, y, fw, fh):
		if face in ("top", "bottom"):
			return noisy(under, rnd, 5) + (255,)
		if y in (7, 8):
			return noisy(belt, rnd, 6) + (255,)
		if face == "front" and 3 <= x <= 4 and y < 7:
			return noisy(under, rnd, 5) + (255,)
		return noisy(robe, rnd, 7) + (255,)

	paint_box(img, 16, 16, 8, 12, 4, colour)


def arm(img, pal, rnd, sith):
	robe, skin = pal["robe"], pal["skin"]

	def colour(face, x, y, fw, fh):
		if face == "bottom" or y >= fh - 2:
			return noisy(pal["glove"] if sith else skin, rnd, 5) + (255,)
		if face == "top":
			return noisy(robe, rnd, 6) + (255,)
		return noisy(shade(robe, 0.95), rnd, 7) + (255,)

	paint_box(img, 40, 16, 4, 12, 4, colour)


def leg(img, pal, rnd):
	def colour(face, x, y, fw, fh):
		if y >= fh - 4 or face == "bottom":
			return noisy(pal["boots"], rnd, 5) + (255,)
		return noisy(pal["under"], rnd, 6) + (255,)

	paint_box(img, 0, 16, 4, 12, 4, colour)


def robe_layers(img, pal, rnd, sith):
	paint_box(img, 16, 32, 8, 14, 4, robe_over(pal, rnd, sith))
	skirt_base = pal["robe"] if not sith else pal["under"]
	paint_box(img, 0, 50, 9, 9, 5, fabric(shade(skirt_base, 0.92), rnd))

	if sith:
		paint_box(img, 40, 32, 8, 16, 1, fabric(pal["cape"], rnd, 4))
	else:
		paint_box(img, 36, 50, 9, 3, 5, fabric(pal["hood"], rnd, 6))


def robe_over(pal, rnd, sith):
	"""The outer robe: open at the front over the under-tunic, a sash at the waist."""
	robe, under, belt = pal["robe"], pal["under"], pal["belt"]

	def colour(face, x, y, fw, fh):
		if face in ("top", "bottom"):
			return None
		if y in (7, 8):
			return noisy(belt, rnd, 6) + (255,)
		if face == "front" and 3 <= x <= 4:
			# Open front: the under-tunic shows (transparent here, the body layer is drawn).
			return None
		if sith and face == "front" and y == 2 and x in (1, 6):
			return (150, 150, 156, 255)
		fold = 0.88 if x % 3 == 0 else 1.0
		return noisy(shade(robe, fold), rnd, 6) + (255,)

	return colour


def skin_texture(pal, seed, sith):
	rnd = random.Random(seed)
	img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
	head(img, pal, rnd, sith)
	body(img, pal, rnd)
	arm(img, pal, rnd, sith)
	leg(img, pal, rnd)
	robe_layers(img, pal, rnd, sith)
	return img


# ---------------------------------------------------------------- portraits

def portrait(pal, seed, sith):
	"""Head and shoulders on a dim background, 32x32."""
	rnd = random.Random(seed)
	img = Image.new("RGBA", (32, 32), (0, 0, 0, 255))
	px = img.load()
	bg_top = (36, 30, 26) if not sith else (22, 22, 28)
	bg_bottom = (64, 54, 40) if not sith else (40, 26, 30)

	for y in range(32):
		t = y / 31
		row = tuple(clamp(bg_top[i] * (1 - t) + bg_bottom[i] * t) for i in range(3))
		for x in range(32):
			px[x, y] = noisy(row, rnd, 3) + (255,)

	# Shoulders and robe.
	shoulder = pal["robe"] if not sith else pal["cape"]
	for y in range(23, 32):
		half = 9 + (y - 23)
		for x in range(16 - half, 16 + half):
			if 0 <= x < 32:
				px[x, y] = noisy(shoulder, rnd, 6) + (255,)
	for y in range(24, 32):
		for x in range(14, 18):
			px[x, y] = noisy(pal["under"], rnd, 4) + (255,)
	if not sith:
		for y in range(21, 25):
			for x in range(8, 24):
				if abs(x - 15.5) > 3:
					px[x, y] = noisy(pal["hood"], rnd, 6) + (255,)

	# Neck and face.
	for y in range(19, 24):
		for x in range(14, 18):
			px[x, y] = noisy(shade(pal["skin"], 0.85), rnd, 3) + (255,)
	for y in range(6, 20):
		for x in range(10, 22):
			dx = (x - 15.5) / 6.0
			dy = (y - 12.5) / 7.5
			if dx * dx + dy * dy <= 1.0:
				px[x, y] = noisy(pal["skin"], rnd, 4) + (255,)

	# Hair.
	for y in range(4, 10):
		for x in range(9, 23):
			dx = (x - 15.5) / 7.0
			dy = (y - 9.5) / 6.0
			if dx * dx + dy * dy <= 1.0 and y < 9 - (0 if x in (9, 10, 21, 22) else 1):
				streak = not sith and (x + y) % 4 == 0
				px[x, y] = noisy(pal["grey"] if streak else pal["hair"], rnd, 6) + (255,)
	if not sith:
		for y in range(9, 19):
			for x in (9, 10, 21, 22):
				px[x, y] = noisy(pal["hair"] if (x + y) % 3 else pal["grey"], rnd, 6) + (255,)

	# Brows, eyes, mouth.
	for x in (12, 13, 18, 19):
		px[x, 11] = shade(pal["hair"], 0.9) + (255,)
	px[13, 12] = pal["eyes"] + (255,)
	px[18, 12] = pal["eyes"] + (255,)
	px[12, 12] = (230, 226, 220, 255)
	px[19, 12] = (230, 226, 220, 255)
	for x in range(14, 18):
		px[x, 16] = shade(pal["skin"], 0.72) + (255,)
	if sith:
		for y in (10, 11, 12, 13):
			px[12, y] = (226, 200, 186, 255)

	# A thin rim light in the mentor's accent.
	rim = (232, 217, 168) if not sith else (176, 38, 42)
	for y in range(6, 20):
		for x in range(21, 23):
			if px[x, y][:3] != px[x + 1 if x < 31 else x, y][:3]:
				px[x, y] = tuple(clamp(px[x, y][i] * 0.6 + rim[i] * 0.4) for i in range(3)) + (255,)
	return img


# ---------------------------------------------------------------- ledger and particles

def ledger():
	rnd = random.Random(31)
	img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
	px = img.load()
	cover = (92, 58, 36)
	for y in range(2, 15):
		for x in range(3, 14):
			edge = x in (3, 13) or y in (2, 14)
			px[x, y] = noisy(shade(cover, 0.7 if edge else 1.0), rnd, 8) + (255,)
	for y in range(3, 14):
		px[13, y] = (214, 202, 176, 255) if y % 2 else (196, 184, 160, 255)
	for y in range(7, 10):
		for x in range(11, 14):
			px[x, y] = (150, 140, 120, 255)
	px[12, 8] = (90, 86, 80, 255)
	for x in range(5, 10):
		px[x, 5] = (140, 98, 60, 255)
	# A scorch on the corner: the camp burned around it.
	for (x, y) in ((4, 13), (5, 13), (4, 12)):
		px[x, y] = (40, 30, 24, 255)
	return img


def glint(seed):
	rnd = random.Random(seed)
	img = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
	px = img.load()
	r = 2.2 + rnd.random() * 0.8
	for y in range(8):
		for x in range(8):
			d = math.hypot(x - 3.5, y - 3.5)
			a = max(0.0, 1.0 - d / r)
			if a > 0:
				px[x, y] = (255, 255, 255, clamp(255 * a * a))
	return img


def speck(seed, flake):
	rnd = random.Random(seed)
	img = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
	px = img.load()
	cells = 6 if flake else 4
	x, y = 3, 3
	for _ in range(cells):
		px[x, y] = (255, 255, 255, clamp(170 + rnd.random() * 80))
		x = max(1, min(6, x + rnd.choice((-1, 0, 1))))
		y = max(1, min(6, y + rnd.choice((-1, 0, 1, 1 if flake else 0))))
	return img


def main():
	out("entity/jedi_mentor.png", skin_texture(JEDI, 11, False))
	out("entity/sith_mentor.png", skin_texture(SITH, 12, True))
	out("gui/sprites/portrait/jedi_mentor.png", portrait(JEDI, 21, False))
	out("gui/sprites/portrait/sith_mentor.png", portrait(SITH, 22, True))
	out("item/poachers_ledger.png", ledger())

	for i in range(3):
		out("particle/voice_warm_%d.png" % i, glint(40 + i))
		out("particle/voice_dust_%d.png" % i, speck(50 + i, False))
		out("particle/ash_mote_%d.png" % i, speck(60 + i, True))


if __name__ == "__main__":
	main()
