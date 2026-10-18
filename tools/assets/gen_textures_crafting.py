#!/usr/bin/env python3
"""Generates the crafting feature's textures and model JSON (Milestone 4, W5). PLACEHOLDER art:
original, deterministic, drawn here from shapes and noise; nothing is copied or traced.

Textures (src/main/resources/assets/oldrepublic/textures/):
  block/engineering_bench_{top,side,front,metal,arc}.png   16x16 Engineering Bench faces
  item/alloy_plate.png, masterwork_fragment.png, schematic.png   16x16 materials
  item/medpac.png, kolto_injector.png, combat_stim.png, focus_stim.png   16x16 consumables
  item/fusion_cutter.png, scanner.png, repair_kit.png   16x16 tools
  item/frag_grenade.png, stun_grenade.png   16x16 grenades

JSON (src/main/resources/assets/oldrepublic/):
  blockstates/engineering_bench.json, models/block/engineering_bench.json, items/engineering_bench.json
  items/<id>.json and models/item/<id>.json for every item above (flat generated icons)

The saberstaff, training saber, staff grip and lenses are the saber generator's
(tools/assets/gen_textures_saber.py).

Run from anywhere: python3 tools/assets/gen_textures_crafting.py
"""
import json
import os
import random

from PIL import Image, ImageDraw

ASSETS = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "src", "main", "resources", "assets", "oldrepublic"))
NS = "oldrepublic"

# Worn workshop palette, matching the saber workbench.
STEEL = (150, 156, 162)
STEEL_DARK = (86, 92, 99)
STEEL_LIGHT = (196, 202, 208)
IRON = (62, 66, 72)
OUTLINE = (28, 30, 34)
BRASS = (170, 140, 86)
BRASS_LIGHT = (222, 196, 128)
OLIVE = (98, 104, 66)
CLOTH = (176, 160, 124)
TEAL = (52, 150, 150)
KOLTO = (86, 196, 214)
RED_STIM = (214, 76, 52)
VIOLET_STIM = (150, 98, 214)
HOT = (255, 168, 64)
BLUEPRINT = (44, 84, 140)
GLOW_GREEN = (120, 230, 150)


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


def shade(rgb, factor):
	return tuple(clamp(c * factor) for c in rgb)


def mix(a, b, t):
	return tuple(clamp(x + (y - x) * t) for x, y in zip(a, b))


def noisy(rgb, rnd, amount):
	return tuple(clamp(c + rnd.uniform(-amount, amount)) for c in rgb)


def new(w=16, h=16):
	return Image.new("RGBA", (w, h), (0, 0, 0, 0))


def put(img, x, y, rgb, a=255):
	if 0 <= x < img.width and 0 <= y < img.height:
		img.putpixel((x, y), (*rgb, a))


def opaque(img, x, y):
	return 0 <= x < img.width and 0 <= y < img.height and img.getpixel((x, y))[3] > 0


def finish_icon(img, seed, wear=4):
	"""Gives a flat icon depth: a dark outline around the shape, a lit top-left edge, a shaded
	bottom-right edge and a little wear noise."""
	rnd = random.Random(seed)
	src = img.copy()
	for y in range(16):
		for x in range(16):
			r, g, b, a = src.getpixel((x, y))
			if not a:
				continue
			c = noisy((r, g, b), rnd, wear)
			if not opaque(src, x - 1, y) or not opaque(src, x, y - 1):
				c = mix(c, (255, 255, 255), 0.22)
			elif not opaque(src, x + 1, y) or not opaque(src, x, y + 1):
				c = shade(c, 0.72)
			img.putpixel((x, y), (*c, a))
	out = img.copy()
	for y in range(16):
		for x in range(16):
			if src.getpixel((x, y))[3]:
				continue
			if any(opaque(src, x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
				put(out, x, y, OUTLINE, 255)
	return out


# ---------------------------------------------------------------- item icons

def alloy_plate():
	"""A stack of two rolled alloy plates, blue-grey, with rivets."""
	img = new()
	d = ImageDraw.Draw(img)
	d.polygon([(2, 9), (9, 6), (14, 9), (7, 12)], fill=(*shade(STEEL, 0.85), 255))
	d.polygon([(2, 9), (7, 12), (7, 13), (2, 10)], fill=(*STEEL_DARK, 255))
	d.polygon([(7, 12), (14, 9), (14, 10), (7, 13)], fill=(*shade(STEEL_DARK, 0.85), 255))
	d.polygon([(2, 6), (9, 3), (14, 6), (7, 9)], fill=(*mix(STEEL, (120, 150, 190), 0.35), 255))
	d.polygon([(2, 6), (7, 9), (7, 10), (2, 7)], fill=(*STEEL_DARK, 255))
	d.polygon([(7, 9), (14, 6), (14, 7), (7, 10)], fill=(*shade(STEEL_DARK, 0.85), 255))
	for x, y in ((5, 6), (9, 4), (11, 6), (7, 7)):
		put(img, x, y, STEEL_LIGHT)
	return finish_icon(img, 1)


def masterwork_fragment():
	"""A shard of gilded alloy with a violet core glow."""
	img = new()
	d = ImageDraw.Draw(img)
	d.polygon([(8, 1), (12, 5), (11, 12), (7, 15), (4, 10), (5, 4)], fill=(*BRASS, 255))
	d.polygon([(8, 1), (5, 4), (4, 10), (7, 15)], fill=(*BRASS_LIGHT, 255))
	d.polygon([(8, 5), (10, 7), (9, 11), (7, 11), (6, 8)], fill=(176, 120, 236, 255))
	d.line([(8, 6), (8, 10)], fill=(236, 214, 255, 255))
	put(img, 6, 3, (255, 248, 220))
	put(img, 7, 2, (255, 248, 220))
	return finish_icon(img, 2, 3)


def schematic():
	"""A rolled blueprint sheet: blue paper, white lines, a brass clip."""
	img = new()
	d = ImageDraw.Draw(img)
	d.rectangle([3, 2, 12, 13], fill=(*BLUEPRINT, 255))
	d.rectangle([3, 12, 12, 14], fill=(*shade(BLUEPRINT, 0.7), 255))
	d.ellipse([10, 11, 13, 14], fill=(*shade(BLUEPRINT, 0.85), 255))
	for y in (5, 7, 9):
		d.line([(5, y), (10, y)], fill=(196, 220, 245, 255))
	d.rectangle([5, 4, 7, 10], outline=(196, 220, 245, 255))
	d.rectangle([6, 1, 9, 2], fill=(*BRASS, 255))
	return finish_icon(img, 3, 2)


def medpac():
	"""A field medpac: a tan pouch with a teal panel and a white plus."""
	img = new()
	d = ImageDraw.Draw(img)
	d.rounded_rectangle([2, 4, 13, 13], 2, fill=(*CLOTH, 255))
	d.rectangle([2, 4, 13, 6], fill=(*shade(CLOTH, 0.85), 255))
	d.rectangle([5, 7, 10, 12], fill=(*TEAL, 255))
	d.rectangle([7, 8, 8, 11], fill=(240, 246, 244, 255))
	d.rectangle([6, 9, 9, 10], fill=(240, 246, 244, 255))
	d.rectangle([6, 2, 9, 3], fill=(*STEEL_DARK, 255))
	return finish_icon(img, 4)


def injector(liquid, seed):
	"""A pneumatic injector on the diagonal: steel body, a liquid window, a plunger and a needle."""
	img = new()
	for i in range(9):
		x, y = 3 + i, 12 - i
		body = STEEL if i < 3 or i > 6 else liquid
		for dx, dy in ((0, 0), (1, 0), (0, -1), (1, -1)):
			put(img, x + dx, y + dy, body)
	for i in range(2):
		put(img, 1 + i, 14 - i, STEEL_DARK)
		put(img, 2 + i, 14 - i, STEEL_DARK)
	put(img, 13, 2, STEEL_LIGHT)
	put(img, 14, 1, STEEL_LIGHT)
	put(img, 2, 11, IRON)
	put(img, 4, 13, IRON)
	put(img, 8, 6, mix(liquid, (255, 255, 255), 0.5))
	return finish_icon(img, seed, 3)


def fusion_cutter():
	"""A handheld cutter: a grip, a boxy body and a glowing orange tip."""
	img = new()
	d = ImageDraw.Draw(img)
	d.rectangle([3, 8, 6, 14], fill=(*IRON, 255))
	d.rectangle([2, 5, 11, 9], fill=(*STEEL, 255))
	d.rectangle([2, 5, 11, 6], fill=(*STEEL_LIGHT, 255))
	d.rectangle([11, 6, 13, 8], fill=(*STEEL_DARK, 255))
	d.rectangle([5, 7, 8, 8], fill=(*BRASS, 255))
	img = finish_icon(img, 5)
	put(img, 14, 7, HOT)
	put(img, 15, 7, (255, 230, 170))
	put(img, 14, 6, (255, 200, 120), 160)
	put(img, 14, 8, (255, 200, 120), 160)
	return img


def scanner():
	"""A handheld scanner: a dark body with a green trace on its screen and an antenna."""
	img = new()
	d = ImageDraw.Draw(img)
	d.rounded_rectangle([3, 3, 12, 14], 2, fill=(*STEEL_DARK, 255))
	d.rectangle([4, 4, 11, 9], fill=(20, 40, 30, 255))
	for x in range(5, 11):
		y = 7 - (1 if x in (6, 9) else 0) + (1 if x == 8 else 0)
		d.point((x, y), fill=(*GLOW_GREEN, 255))
	d.rectangle([5, 11, 6, 12], fill=(*BRASS, 255))
	d.rectangle([9, 11, 10, 12], fill=(*STEEL, 255))
	d.line([(11, 3), (13, 1)], fill=(*STEEL, 255))
	img = finish_icon(img, 6, 3)
	put(img, 13, 1, GLOW_GREEN)
	return img


def repair_kit():
	"""A small tool case with a brass latch and a wrench strapped to its lid."""
	img = new()
	d = ImageDraw.Draw(img)
	d.rectangle([2, 6, 13, 13], fill=(*OLIVE, 255))
	d.rectangle([2, 6, 13, 7], fill=(*shade(OLIVE, 1.2), 255))
	d.rectangle([6, 3, 9, 5], outline=(*STEEL_DARK, 255))
	d.rectangle([7, 8, 8, 9], fill=(*BRASS, 255))
	d.line([(4, 12), (11, 10)], fill=(*STEEL_LIGHT, 255))
	d.point((11, 9), fill=(*STEEL_LIGHT, 255))
	d.point((12, 10), fill=(*STEEL_LIGHT, 255))
	return finish_icon(img, 7)


def grenade(band, seed, stun):
	"""A round grenade with a coloured band, a cap and a pin ring."""
	img = new()
	d = ImageDraw.Draw(img)
	d.ellipse([3, 4, 13, 14], fill=(*(STEEL_DARK if stun else OLIVE), 255))
	d.rectangle([3, 8, 13, 10], fill=(*band, 255))
	d.rectangle([6, 2, 10, 4], fill=(*STEEL, 255))
	d.ellipse([10, 0, 13, 3], outline=(*STEEL_LIGHT, 255))
	img = finish_icon(img, seed)
	if stun:
		for x, y in ((5, 9), (8, 8), (11, 9)):
			put(img, x, y, (220, 240, 255))
	return img


ITEMS = {
	"alloy_plate": alloy_plate,
	"masterwork_fragment": masterwork_fragment,
	"schematic": schematic,
	"medpac": medpac,
	"kolto_injector": lambda: injector(KOLTO, 8),
	"combat_stim": lambda: injector(RED_STIM, 9),
	"focus_stim": lambda: injector(VIOLET_STIM, 10),
	"fusion_cutter": fusion_cutter,
	"scanner": scanner,
	"repair_kit": repair_kit,
	"frag_grenade": lambda: grenade((200, 120, 40), 11, False),
	"stun_grenade": lambda: grenade((80, 150, 230), 12, True),
}


# ---------------------------------------------------------------- the Engineering Bench

def bench_texture(face):
	rnd = random.Random({"top": 21, "side": 22, "front": 23, "metal": 24, "arc": 25}[face])
	img = new()
	for y in range(16):
		for x in range(16):
			if face == "top":
				# A scarred work plate with a grid of bolt holes and a scorched patch.
				c = noisy(shade(STEEL, 0.92), rnd, 7)
				if x in (0, 15) or y in (0, 15):
					c = shade(STEEL_DARK, 0.9)
				elif (x % 5 == 2) and (y % 5 == 2):
					c = IRON
				dx, dy = x - 10, y - 6
				if dx * dx + dy * dy < 7:
					c = mix(c, (52, 44, 40), 0.55)
			elif face == "side":
				c = noisy(STEEL_DARK, rnd, 5)
				if y in (0, 15) or x in (0, 15):
					c = shade(IRON, 0.9)
				elif y == 7:
					c = shade(STEEL_DARK, 0.7)
			elif face == "front":
				# A tool rack: two drawers with brass pulls and a warning stripe.
				c = noisy(STEEL_DARK, rnd, 5)
				if x in (0, 15) or y in (0, 15):
					c = shade(IRON, 0.9)
				elif y in (5, 10):
					c = shade(STEEL_DARK, 0.65)
				elif y in (2, 3) and 1 <= x <= 14:
					c = (200, 150, 50) if (x // 2) % 2 == 0 else (40, 40, 40)
				elif y in (7, 8, 12, 13) and 6 <= x <= 9:
					c = BRASS if y in (7, 12) else shade(BRASS, 0.7)
			elif face == "metal":
				c = noisy(IRON, rnd, 5)
				if (x + y) % 6 == 0:
					c = shade(IRON, 1.2)
			else:  # arc: the welder's emitter, white-blue glow
				dx, dy = x - 7.5, y - 7.5
				r = (dx * dx + dy * dy) ** 0.5
				c = mix((255, 255, 255), (110, 170, 255), min(1.0, r / 7.5))
			put(img, x, y, c)
	return img


def write_bench():
	t = {k: f"{NS}:block/engineering_bench_{k}" for k in ("top", "side", "front", "metal", "arc")}
	t["particle"] = t["metal"]

	def cube(frm, to, faces, emissive=False):
		el = {"from": frm, "to": to, "faces": {}}
		for side, (tex, uv) in faces.items():
			el["faces"][side] = {"uv": uv, "texture": f"#{tex}"}
		if emissive:
			el["light_emission"] = 12
		return el

	def all_faces(tex, frm, to):
		sx, sy, sz = (min(16, to[i] - frm[i]) for i in range(3))
		return {
			"north": (tex, [0, 0, sx, sy]),
			"south": (tex, [0, 0, sx, sy]),
			"east": (tex, [0, 0, sz, sy]),
			"west": (tex, [0, 0, sz, sy]),
			"up": (tex, [0, 0, sx, sz]),
			"down": (tex, [0, 0, sx, sz]),
		}

	elements = [
		# The heavy work plate, full width, at the top of the block.
		cube([0, 12, 0], [16, 16, 16], {
			"up": ("top", [0, 0, 16, 16]),
			"down": ("metal", [0, 0, 16, 16]),
			"north": ("front", [0, 0, 16, 4]),
			"south": ("side", [0, 0, 16, 4]),
			"east": ("side", [0, 0, 16, 4]),
			"west": ("side", [0, 0, 16, 4]),
		}),
		# The drawer cabinet under it, front left.
		cube([1, 0, 1], [10, 12, 14], {
			"north": ("front", [1, 4, 10, 16]),
			"south": ("side", [1, 4, 10, 16]),
			"east": ("side", [1, 4, 14, 16]),
			"west": ("side", [2, 4, 15, 16]),
			"down": ("metal", [1, 1, 10, 14]),
		}),
	]
	# Two legs on the open right side.
	for z in (1, 13):
		frm, to = [13, 0, z], [15, 12, z + 2]
		elements.append(cube(frm, to, all_faces("metal", frm, to)))
	# A brace between the legs.
	frm, to = [13, 3, 3], [15, 4, 13]
	elements.append(cube(frm, to, all_faces("metal", frm, to)))
	# The welding arm: a post at the back left, an arm over the plate and the glowing emitter tip.
	for frm, to in (([1, 16, 12], [3, 21, 14]), ([2, 20, 7], [3, 21, 13]), ([2, 19, 6], [4, 21, 8])):
		elements.append(cube(frm, to, all_faces("metal", frm, to)))
	tip_from, tip_to = [2.5, 18.5, 6.5], [3.5, 19, 7.5]
	elements.append(cube(tip_from, tip_to, {"down": ("arc", [6, 6, 10, 10]), "north": ("arc", [6, 6, 10, 8]),
			"south": ("arc", [6, 6, 10, 8]), "east": ("arc", [6, 6, 10, 8]), "west": ("arc", [6, 6, 10, 8])}, emissive=True))
	# A vice and a part on the plate.
	for frm, to in (([8, 16, 9], [13, 17, 12]), ([8, 17, 9], [9, 18.5, 12]), ([12, 17, 9], [13, 18.5, 12])):
		elements.append(cube(frm, to, all_faces("metal", frm, to)))

	write_json("models/block/engineering_bench.json", {"parent": "minecraft:block/block", "textures": t, "elements": elements})
	write_json("blockstates/engineering_bench.json", {"variants": {
		"facing=north": {"model": f"{NS}:block/engineering_bench"},
		"facing=east": {"model": f"{NS}:block/engineering_bench", "y": 90},
		"facing=south": {"model": f"{NS}:block/engineering_bench", "y": 180},
		"facing=west": {"model": f"{NS}:block/engineering_bench", "y": 270},
	}})
	write_json("items/engineering_bench.json", {"model": {"type": "minecraft:model", "model": f"{NS}:block/engineering_bench"}})


def main():
	for name, make in ITEMS.items():
		write_png(f"item/{name}.png", make())
		write_json(f"models/item/{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{name}"}})
		write_json(f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/{name}"}})
	for face in ("top", "side", "front", "metal", "arc"):
		write_png(f"block/engineering_bench_{face}.png", bench_texture(face))
	write_bench()


if __name__ == "__main__":
	main()
