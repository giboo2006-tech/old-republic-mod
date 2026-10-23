#!/usr/bin/env python3
"""Generates the turning feature's item assets (W5, MILESTONE_5.md 8.3, 18): original and deterministic.

The Watch holocron (`watch_holocron`) is the keepsake Keeper Ansa lends a Jedi in "A Gift for the Empire". It is the
Watch's own work, unlike the Order's pale cube or the Empire's black pyramid: a squat hexagonal lantern of grey-green
river stone, a darker capstone with a pale jade leaf-vein seam, and a moss-glass core (Selvaren's teal, #8FC6C9) that
shows through narrow slits in its faces. The core hums: an eight-frame animation brightens and dims the slits and the
capstone's gem over two seconds (interpolated, so it never flickers or flashes).

Writes (src/main/resources/assets/oldrepublic/):
  textures/item/watch_holocron.png          16x128: eight 16x16 frames of the inventory icon, stacked
  textures/item/watch_holocron.png.mcmeta   the animation (frametime 5, interpolated)
  models/item/watch_holocron.json           item/generated with that texture
  items/watch_holocron.json                 the item model definition

Run from anywhere: python3 tools/assets/gen_textures_turning.py
"""
import json
import math
import os
import random

from PIL import Image

ASSETS = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "src", "main", "resources", "assets", "oldrepublic"))
NS = "oldrepublic"
FRAMES = 8
FRAME_TIME = 5

# Grey-green river stone, lit from the upper left; the core is Selvaren's moss-glass teal.
STONE_TOP = (190, 204, 192)
STONE_LEFT = (158, 174, 164)
STONE_FRONT = (138, 154, 145)
STONE_RIGHT = (110, 124, 117)
CAP = (96, 112, 104)
EDGE = (40, 50, 46)
SEAM = (196, 226, 206)
CORE_DIM = (70, 122, 126)
CORE = (143, 198, 201)
CORE_HI = (222, 246, 240)


# ---------------------------------------------------------------- helpers

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


def mix(a, b, t):
	return tuple(clamp(a[i] * (1 - t) + b[i] * t) for i in range(3))


def noisy(rgb, rnd, amount):
	d = rnd.randint(-amount, amount)
	return tuple(clamp(c + d) for c in rgb)


# ---------------------------------------------------------------- the icon

# The lantern, pixel by pixel. E edge, T capstone (lit), C capstone (shade), S leaf-vein seam, G the gem, D the rim and
# the course line, L left face (lit), F front face, R right face (shade); K, k and r the core through the front, left
# and right slits (animated); '.' transparent.
ICON = (
	"................",
	".......EE.......",
	"......ETSE......",
	".....ETTSCE.....",
	"....ETTTGCCE....",
	"...ETTTTSCCCE...",
	"..EDDDDDDDDDDE..",
	"..ELLFFFFFFRRE..",
	"..ELkFKFFKFRrE..",
	"..ELkFKFFKFRrE..",
	"..ELkFKFFKFRrE..",
	"..ELLFKFFKFRRE..",
	"..EDDDDDDDDDDE..",
	"..ELLFFFFFFRRE..",
	"...EEEEEEEEEE...",
	"................",
)
STONE = {"T": STONE_TOP, "C": CAP, "D": (122, 136, 128), "L": STONE_LEFT, "F": STONE_FRONT, "R": STONE_RIGHT}
# How bright the core is through each kind of slit: the front is open to the eye, the sides are turned away.
SLIT = {"K": 1.0, "k": 0.7, "r": 0.5}


def body():
	"""The stone lantern with its core dark: faces, capstone, rim and seam (the slits and gem are drawn per frame)."""
	rnd = random.Random(500)
	img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
	px = img.load()

	for y, row in enumerate(ICON):
		for x, c in enumerate(row):
			if c in STONE:
				px[x, y] = noisy(STONE[c], rnd, 5) + (255,)
			elif c == "E":
				px[x, y] = EDGE + (255,)
			elif c == "S":
				px[x, y] = SEAM + (255,)

	# The front face's lit edge, where it turns toward the left face.
	for y in range(7, 14):
		if ICON[y][5] == "F":
			px[5, y] = mix(px[5, y][:3], STONE_LEFT, 0.5) + (255,)

	return img


def frame(base, phase):
	"""One frame: the core glows through the slits and the capstone's gem at {@code phase} (0..1) of its hum."""
	img = base.copy()
	px = img.load()
	glow = 0.5 + 0.5 * math.sin(phase * math.tau)

	for y, row in enumerate(ICON):
		for x, c in enumerate(row):
			if c in SLIT:
				# Brightest in the middle of the slit (rows 9 and 10), dimmer at its ends.
				middle = 1.0 if 9 <= y <= 10 else 0.8
				t = SLIT[c] * middle * (0.55 + 0.45 * glow)
				colour = mix(CORE_DIM, CORE, min(1.0, t * 1.2))

				if c == "K" and 9 <= y <= 10 and glow > 0.6:
					colour = mix(CORE, CORE_HI, (glow - 0.6) / 0.4 * 0.8)

				px[x, y] = colour + (255,)
			elif c == "G":
				px[x, y] = mix(CORE, CORE_HI, glow) + (255,)

	return img


def main():
	base = body()
	sheet = Image.new("RGBA", (16, 16 * FRAMES), (0, 0, 0, 0))

	for i in range(FRAMES):
		sheet.paste(frame(base, i / FRAMES), (0, 16 * i))

	write_png("item/watch_holocron.png", sheet)
	path = os.path.join(ASSETS, "textures", "item", "watch_holocron.png.mcmeta")

	with open(path, "w", encoding="utf-8") as f:
		json.dump({"animation": {"frametime": FRAME_TIME, "interpolate": True}}, f, indent="\t")
		f.write("\n")

	print("wrote", os.path.relpath(path))
	write_json("models/item/watch_holocron.json", {
		"parent": "minecraft:item/generated",
		"textures": {"layer0": NS + ":item/watch_holocron"},
	})
	write_json("items/watch_holocron.json", {
		"model": {"type": "minecraft:model", "model": NS + ":item/watch_holocron"},
	})


if __name__ == "__main__":
	main()
