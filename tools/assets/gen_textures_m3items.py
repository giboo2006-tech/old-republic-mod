#!/usr/bin/env python3
"""Generates the Milestone 3 shared items' textures and item JSON (PLACEHOLDER art, original, deterministic).

These are the items registered up front in com.oldrepublic.registry.M3Items (spec section 15.1).
Each owner replaces its item's art later: the materials belong to the world feature, the
commendations to the mission feature, the shard to the holocron feature and the singing crystal
to the saber feature.

Textures (src/main/resources/assets/oldrepublic/textures/):
  item/durasteel_plate.png        16x16 riveted, weathered grey plate
  item/lumen_thread.png           16x16 loose skein of faintly glowing pale thread
  item/grave_silk.png             16x16 folded charcoal silk with a crimson sheen and a frayed edge
  item/republic_commendation.png  16x16 blue-white enamel chevron pin on a navy field
  item/imperial_commendation.png  16x16 crimson and black service bar with steel end clasps
  item/holocron_shard.png         16x16 sliver of pale crystal bound in brass
  item/singing_crystal.png        16x16 amber kyber crystal

JSON (src/main/resources/assets/oldrepublic/):
  items/<id>.json, models/item/<id>.json  (flat generated item models)

Run from anywhere: python3 tools/assets/gen_textures_m3items.py
"""
import json
import os
import random

from PIL import Image, ImageDraw

ASSETS = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "src", "main", "resources", "assets", "oldrepublic"))
NS = "oldrepublic"

STEEL = (128, 134, 140)
STEEL_DARK = (72, 78, 84)
STEEL_LIGHT = (184, 190, 196)
BRASS = (170, 140, 86)
BRASS_DARK = (112, 88, 50)
BRASS_LIGHT = (214, 186, 124)
NAVY = (40, 70, 120)
ENAMEL = (190, 214, 240)
CRIMSON = (150, 36, 40)
BLACK = (26, 23, 26)


# ---------------------------------------------------------------- output helpers

def write_png(rel, img):
	path = os.path.join(ASSETS, "textures", rel)
	os.makedirs(os.path.dirname(path), exist_ok=True)
	img.save(path)
	print("wrote", os.path.relpath(path))


def write_json(rel, data):
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


def new():
	return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


def put(img, x, y, rgb, a=255):
	if 0 <= x < img.width and 0 <= y < img.height:
		img.putpixel((x, y), (*rgb, a))


def opaque(img, x, y):
	return 0 <= x < img.width and 0 <= y < img.height and img.getpixel((x, y))[3] == 255


def halo(img, rgb, alpha):
	"""A one-pixel soft glow around every opaque pixel, on empty pixels only."""
	glow = [(x, y) for y in range(16) for x in range(16) if img.getpixel((x, y))[3] == 0
			and any(opaque(img, x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))]

	for x, y in glow:
		put(img, x, y, rgb, alpha)


def outline(img, rgb):
	"""A dark one-pixel outline around the opaque shape, like vanilla item icons."""
	edge = [(x, y) for y in range(16) for x in range(16) if img.getpixel((x, y))[3] == 0
			and any(opaque(img, x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))]

	for x, y in edge:
		put(img, x, y, rgb)


# ---------------------------------------------------------------- textures

def durasteel_plate():
	img = new()
	rnd = random.Random(301)

	# Plate face with chamfered corners, and its thickness along the bottom and right.
	for y in range(4, 12):
		for x in range(2, 14):
			if (x, y) in ((2, 4), (13, 4), (2, 11), (13, 11)):
				continue
			put(img, x, y, noisy(STEEL, rnd, 7))

	for x in range(3, 14):
		put(img, x, 12, noisy(STEEL_DARK, rnd, 4))
	for y in range(5, 12):
		put(img, 14, y, noisy(STEEL_DARK, rnd, 4))

	# Bevel light along the top and left.
	for x in range(3, 13):
		put(img, x, 4, noisy(STEEL_LIGHT, rnd, 5))
	for y in range(5, 11):
		put(img, 2, y, noisy(shade(STEEL_LIGHT, 0.92), rnd, 5))

	# Rivets in the corners.
	for x, y in ((4, 6), (11, 6), (4, 9), (11, 9)):
		put(img, x, y, STEEL_DARK)
		put(img, x - 1, y - 1, STEEL_LIGHT)

	# A scored scratch and a patch of rust.
	for x, y in ((6, 8), (7, 7), (8, 7), (9, 6)):
		put(img, x, y, shade(STEEL_LIGHT, 1.05))
	for x, y in ((9, 10), (10, 10), (10, 9)):
		put(img, x, y, noisy((122, 86, 60), rnd, 6))

	outline(img, (34, 36, 40))
	return img


def lumen_thread():
	img = new()
	rnd = random.Random(302)
	thread = (206, 244, 232)
	dim = (150, 206, 194)
	draw = ImageDraw.Draw(img)

	# A loose skein: two wound loops crossing, and a free end curling away.
	draw.ellipse((3, 3, 12, 10), outline=(*thread, 255))
	draw.ellipse((5, 5, 13, 12), outline=(*dim, 255))
	for x, y in ((4, 11), (3, 12), (2, 13), (2, 14), (3, 15)):
		put(img, x, y, dim)

	# Faint flecks of light along the strands.
	for x, y in ((7, 3), (12, 6), (5, 9), (10, 12)):
		put(img, x, y, (240, 255, 250))
	for _ in range(3):
		put(img, rnd.randint(4, 11), rnd.randint(4, 10), noisy(thread, rnd, 10), 140)

	halo(img, (120, 220, 200), 70)
	return img


def grave_silk():
	img = new()
	rnd = random.Random(303)
	silk = (50, 42, 54)
	sheen = (92, 78, 98)

	# The folded cloth: a square with its upper right corner turned down.
	for y in range(3, 13):
		for x in range(2, 14):
			if x - 2 > (y - 3) + 7:
				continue
			put(img, x, y, noisy(silk, rnd, 5))

	for i in range(5):
		for j in range(i + 1):
			put(img, 9 + i, 3 + j, noisy(sheen, rnd, 6))

	# A crimson sheen along the fold, and a frayed lower edge.
	for x, y in ((9, 3), (10, 4), (11, 5), (12, 6), (13, 7)):
		put(img, x, y, CRIMSON)
	for x, y in ((4, 6), (5, 7), (6, 8), (7, 9)):
		put(img, x, y, shade(CRIMSON, 0.75))
	for x in range(2, 14):
		if x % 2 == 0:
			put(img, x, 13, noisy(silk, rnd, 5))

	outline(img, (16, 12, 18))
	return img


def republic_commendation():
	img = new()
	draw = ImageDraw.Draw(img)

	# Round pin: steel rim, navy enamel field.
	draw.ellipse((1, 1, 14, 14), fill=(*STEEL, 255))
	draw.ellipse((2, 2, 13, 13), fill=(*NAVY, 255))

	# An upward chevron and a small star above it, in blue-white enamel.
	for i in range(4):
		put(img, 7 - i, 8 + i, ENAMEL)
		put(img, 8 + i, 8 + i, ENAMEL)
		put(img, 7 - i, 9 + i, shade(ENAMEL, 0.82))
		put(img, 8 + i, 9 + i, shade(ENAMEL, 0.82))
	for x, y in ((7, 4), (8, 4), (6, 5), (7, 5), (8, 5), (9, 5), (7, 6), (8, 6)):
		put(img, x, y, (232, 240, 252))

	# A glint on the rim.
	put(img, 4, 3, STEEL_LIGHT)
	put(img, 3, 4, STEEL_LIGHT)
	outline(img, (24, 30, 44))
	return img


def imperial_commendation():
	img = new()

	# Service bar: black border, a crimson centre stripe between thin black stripes.
	for y in range(5, 11):
		for x in range(3, 13):
			put(img, x, y, BLACK)
	for y in range(6, 10):
		for x in range(4, 12):
			put(img, x, y, CRIMSON if y in (7, 8) else shade(CRIMSON, 0.55))
	for x in range(4, 12):
		put(img, x, 6, shade(CRIMSON, 1.2))

	# Steel clasps at both ends.
	for y in range(5, 11):
		put(img, 2, y, STEEL)
		put(img, 13, y, STEEL_DARK)
	put(img, 2, 5, STEEL_LIGHT)

	outline(img, (14, 10, 12))
	return img


def holocron_shard():
	img = new()
	crystal = (214, 228, 242)
	core = (246, 250, 255)

	# A sliver of pale crystal, running from lower left to upper right.
	for i in range(9):
		x, y = 4 + i, 12 - i
		put(img, x, y, crystal)
		put(img, x + 1, y, shade(crystal, 0.84))
		if 2 <= i <= 6:
			put(img, x, y - 1, core)

	# Brass bands binding it, and a brass cap at its foot.
	for x, y in ((6, 10), (7, 10), (6, 11), (9, 7), (10, 7), (9, 8)):
		put(img, x, y, BRASS)
	put(img, 7, 9, BRASS_LIGHT)
	put(img, 10, 6, BRASS_LIGHT)
	for x, y in ((2, 13), (3, 13), (3, 12), (4, 13), (2, 14), (3, 14)):
		put(img, x, y, BRASS_DARK)
	put(img, 3, 12, BRASS_LIGHT)

	outline(img, (40, 34, 30))
	halo(img, (200, 220, 255), 40)
	return img


def singing_crystal():
	img = new()
	draw = ImageDraw.Draw(img)

	# The kyber crystal's silhouette, in warm amber with a bright face and a hot core.
	draw.polygon([(8, 1), (12, 6), (11, 13), (8, 15), (5, 13), (4, 6)], fill=(232, 158, 58, 255))
	draw.polygon([(8, 1), (4, 6), (5, 13), (8, 15)], fill=(250, 198, 110, 255))
	draw.line([(8, 1), (8, 15)], fill=(196, 120, 40, 255))
	draw.line([(4, 6), (12, 6)], fill=(206, 134, 48, 255))
	for x, y in ((7, 8), (7, 9), (6, 9)):
		put(img, x, y, (255, 236, 190))
	for x, y, a in ((6, 4, 255), (6, 5, 200), (10, 9, 150)):
		put(img, x, y, (255, 255, 255), a)

	halo(img, (255, 190, 90), 60)
	return img


TEXTURES = {
	"durasteel_plate": durasteel_plate,
	"lumen_thread": lumen_thread,
	"grave_silk": grave_silk,
	"republic_commendation": republic_commendation,
	"imperial_commendation": imperial_commendation,
	"holocron_shard": holocron_shard,
	"singing_crystal": singing_crystal,
}


def main():
	for item, make in TEXTURES.items():
		write_png(f"item/{item}.png", make())
		write_json(f"items/{item}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/{item}"}})
		write_json(f"models/item/{item}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{item}"}})


if __name__ == "__main__":
	main()
