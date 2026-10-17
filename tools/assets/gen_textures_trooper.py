#!/usr/bin/env python3
"""Generates the eight trooper skins (spec 7.1; PLACEHOLDER art, original, deterministic).

Writes src/main/resources/assets/oldrepublic/textures/entity/trooper/<army>_<role>.png (64x64):
republic_{rifleman,medic,sergeant,officer} and imperial_{rifleman,grenadier,sergeant,officer}.

The armoured skins are composited from the very layers the player's trooper armour uses
(gen_textures_equipment.trooper_layers): a plain undersuit, then the leggings layer, then the
humanoid layer, so a trooper and a player in the same set look alike. The helmet is painted on
both the head and the hat layer (the hat is the helmet shell of TrooperModel).

The lower half follows the extra parts of com.oldrepublic.client.enemy.TrooperModel:
cuirass 0,32 (8x12x4); pauldrons 24,32 (5x3x5); belt 0,48 (8x2x4); injector 30,50 (1x3x2);
bandolier 52,41 (1x13x5); Republic visor 44,32 (9x2x1), ears 52,35 (1x3x3), dome 24,40 (7x2x7);
Imperial brow 24,40 (9x1x2), nose 46,40 (1x4x1), cheeks 24,43 (1x3x4), crest 0,54 (2x1x8).
Officers wear no armour: cap 0,32 (9x3x9), peak 36,32 (8x1x3), tunic 0,44 (8x12x4), sleeves 40,36 (4x12x4).

Role marks: the medic's teal shoulder band and kolto injector, the sergeant's gold chevrons on the
helmet sides, the grenadier's bandolier.

Run from the repository root: python3 tools/assets/gen_textures_trooper.py
"""
import random

from PIL import Image

from gen_textures_equipment import PALETTE, chest_painter, composite, helmet_painter, noisy, out, paint_box, shade, trooper_layers, trooper_plate

TEAL = (64, 176, 164)
TEAL_DARK = (40, 120, 112)
CHEVRON = (196, 162, 86)
POUCH = (88, 92, 64)
POUCH_DARK = (60, 62, 44)
STRAP = (52, 44, 36)


def undersuit(army, rnd):
	"""The plain suit under the armour: every vanilla part of the 64x64 skin's upper half."""
	p = PALETTE[army]
	img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
	px = img.load()

	def suit(face, fx, fy, fw, fh):
		return noisy(p["suit"] if face != "bottom" else p["suit_dark"], rnd, 4)

	paint_box(px, 0, 0, 8, 8, 8, suit)
	paint_box(px, 16, 16, 8, 12, 4, suit)
	paint_box(px, 40, 16, 4, 12, 4, suit)
	paint_box(px, 0, 16, 4, 12, 4, suit)
	return img


def armour_parts(army, role, rnd):
	"""The TrooperModel extra parts (lower half) for an armoured role."""
	p = PALETTE[army]
	republic = army == "republic"
	img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
	px = img.load()
	stripe = TEAL if role == "medic" else p["stripe"]

	paint_box(px, 0, 32, 8, 12, 4, chest_painter(army, rnd, suit_alpha=False))

	def pauldron(face, fx, fy, fw, fh):
		if face == "bottom":
			return noisy(p["plate_dark"], rnd, 3)
		if face == "top":
			return trooper_plate(p["plate"], rnd, fx, fy, fw, fh)
		if fy == fh - 1:
			return noisy(stripe, rnd, 4)
		return trooper_plate(p["plate"], rnd, fx, fy, fw, fh)

	paint_box(px, 24, 32, 5, 3, 5, pauldron)

	def belt(face, fx, fy, fw, fh):
		if face == "front" and fx in (1, 6):
			return noisy(POUCH, rnd, 4)
		if face == "front" and fx in (3, 4):
			return noisy(p["buckle"], rnd, 5)
		if face in ("east", "west") and fy == 0:
			return noisy(POUCH_DARK, rnd, 3)
		return noisy(p["belt"], rnd, 4)

	paint_box(px, 0, 48, 8, 2, 4, belt)

	if role == "medic":
		def injector(face, fx, fy, fw, fh):
			if face == "top":
				return noisy((180, 186, 190), rnd, 3)
			if fy == 1 and face in ("east", "west", "front", "back"):
				return noisy(TEAL, rnd, 3)
			return noisy((120, 126, 132), rnd, 4)

		paint_box(px, 30, 50, 1, 3, 2, injector)

	if role == "grenadier":
		def bandolier(face, fx, fy, fw, fh):
			if face in ("east", "west") and fy % 3 == 1:
				return noisy(POUCH, rnd, 4)
			if face in ("east", "west") and fy % 3 == 2:
				return noisy(POUCH_DARK, rnd, 3)
			return noisy(STRAP, rnd, 4)

		paint_box(px, 52, 41, 1, 13, 5, bandolier)

	if republic:
		def visor(face, fx, fy, fw, fh):
			if face == "front":
				return noisy(p["visor_light"] if fy == 0 and fx in (1, 2, 3) else p["visor"], rnd, 3)
			return noisy(p["plate_dark"], rnd, 3)

		paint_box(px, 44, 32, 9, 2, 1, visor)

		def ear(face, fx, fy, fw, fh):
			if face in ("east", "west") and fx == 1 and fy == 1:
				return noisy(p["trim"], rnd, 3)
			return trooper_plate(p["plate_light"], rnd, fx, fy, fw, fh)

		paint_box(px, 52, 35, 1, 3, 3, ear)

		def dome(face, fx, fy, fw, fh):
			if face == "top":
				return trooper_plate(p["plate_light"] if 2 <= fx <= 4 and 2 <= fy <= 4 else p["plate"], rnd, fx, fy, fw, fh)
			return trooper_plate(p["plate"], rnd, fx, fy, fw, fh)

		paint_box(px, 24, 40, 7, 2, 7, dome)
	else:
		def ridge(face, fx, fy, fw, fh):
			return trooper_plate(p["plate_light"], rnd, fx, fy, fw, fh, 3)

		paint_box(px, 24, 40, 9, 1, 2, ridge)
		paint_box(px, 46, 40, 1, 4, 1, ridge)

		def cheek(face, fx, fy, fw, fh):
			if fy == fh - 1:
				return noisy(p["trim"], rnd, 4)
			return trooper_plate(p["plate"], rnd, fx, fy, fw, fh)

		paint_box(px, 24, 43, 1, 3, 4, cheek)

		def crest(face, fx, fy, fw, fh):
			if face == "top" and fx in (0, 1) and fy % 2 == 0:
				return noisy(p["trim"], rnd, 4)
			return trooper_plate(p["plate_light"], rnd, fx, fy, fw, fh, 3)

		paint_box(px, 0, 54, 2, 1, 8, crest)

	return img


def shoulder_band(img, colour, rnd):
	"""Repaints the arm's shoulder stripe (row 2 of the arm box at 40,16) in {colour}."""
	px = img.load()
	for x in range(40, 56):
		px[x, 16 + 4 + 2] = noisy(colour, rnd, 4) + (255,)


def chevrons(img, rnd):
	"""Gold chevrons on both helmet sides, on the head and the hat layer."""
	px = img.load()
	shape = ((1, 1), (2, 1), (5, 1), (6, 1), (2, 2), (3, 2), (4, 2), (5, 2))
	for u in (0, 32):
		for side_x in (u, u + 16):
			for (fx, fy) in shape:
				px[side_x + fx, 8 + fy] = noisy(CHEVRON, rnd, 5) + (255,)


def armoured_skin(army, role):
	rnd = random.Random(sum(ord(c) for c in army + role) * 31)
	humanoid, leggings = trooper_layers(army)
	skin = composite((64, 64), undersuit(army, rnd), leggings, humanoid)

	# The hat layer is the helmet shell: the same helmet painting as the head.
	helmet = Image.new("RGBA", (32, 16), (0, 0, 0, 0))
	paint_box(helmet.load(), 0, 0, 8, 8, 8, helmet_painter(army, random.Random(7 if army == "republic" else 8)))
	skin.paste(helmet, (32, 0))

	if role == "medic":
		shoulder_band(skin, TEAL, rnd)
	if role == "sergeant":
		chevrons(skin, rnd)

	skin = composite((64, 64), skin, armour_parts(army, role, rnd))
	out(f"entity/trooper/{army}_{role}.png", skin)


# ---------------------------------------------------------------- officers
OFFICERS = {
	"republic": {
		"uniform": (38, 50, 86), "uniform_dark": (26, 34, 60), "trim": (196, 162, 86), "trousers": (30, 36, 56),
		"skin": (192, 146, 116), "skin_dark": (160, 116, 90), "hair": (88, 60, 40), "hair_side": (88, 60, 40),
		"badge": ((196, 162, 86), (120, 160, 210)), "boot": (24, 22, 24), "shirt": (210, 206, 196),
	},
	"imperial": {
		"uniform": (96, 102, 98), "uniform_dark": (70, 74, 72), "trim": (30, 28, 32), "trousers": (60, 64, 62),
		"skin": (214, 182, 162), "skin_dark": (180, 148, 130), "hair": (40, 34, 32), "hair_side": (120, 118, 116),
		"badge": ((150, 30, 40), (160, 164, 170)), "boot": (16, 15, 18), "shirt": (60, 64, 62),
	},
}


def officer_skin(army):
	o = OFFICERS[army]
	rnd = random.Random(501 if army == "republic" else 502)
	img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
	px = img.load()

	def head(face, fx, fy, fw, fh):
		if face == "top":
			return noisy(o["hair"], rnd, 6)
		if face == "bottom":
			return noisy(o["skin_dark"], rnd, 4)
		if face == "front":
			if fy <= 1:
				return noisy(o["hair"], rnd, 6)
			if fy == 3 and fx in (1, 2, 5, 6):
				return (232, 228, 220) if fx in (1, 6) else (52, 56, 64)
			if fy == 2 and fx in (1, 2, 5, 6):
				return noisy(shade(o["hair"], 1.1), rnd, 4)
			if fy == 5 and fx in (3, 4):
				return noisy(o["skin_dark"], rnd, 3)
			if fy == 6 and 2 <= fx <= 5:
				return noisy(shade(o["skin_dark"], 0.85), rnd, 3)
			return noisy(o["skin"], rnd, 5)
		if face == "back":
			return noisy(o["hair"] if fy < 6 else o["skin_dark"], rnd, 6)
		if fy < 4:
			return noisy(o["hair_side"] if fy >= 2 else o["hair"], rnd, 6)
		return noisy(o["skin"], rnd, 5)

	paint_box(px, 0, 0, 8, 8, 8, head)

	def body(face, fx, fy, fw, fh):
		if fy >= 10 and face != "top":
			return noisy(o["trousers"], rnd, 4)
		return noisy(o["shirt"] if face == "front" and fx in (3, 4) and fy < 3 else o["uniform"], rnd, 4)

	paint_box(px, 16, 16, 8, 12, 4, body)

	def arm(face, fx, fy, fw, fh):
		if face == "bottom" or fy == 11:
			return noisy(o["skin"], rnd, 4)
		return noisy(o["uniform"], rnd, 4)

	paint_box(px, 40, 16, 4, 12, 4, arm)

	def leg(face, fx, fy, fw, fh):
		if face == "bottom" or fy >= 9:
			return noisy(o["boot"], rnd, 3)
		if fy == 8:
			return noisy(shade(o["boot"], 1.6), rnd, 3)
		return noisy(o["trousers"], rnd, 4)

	paint_box(px, 0, 16, 4, 12, 4, leg)

	def cap(face, fx, fy, fw, fh):
		if face == "top":
			return noisy(o["uniform"], rnd, 4)
		if face == "bottom":
			return noisy(o["uniform_dark"], rnd, 3)
		if fy == fh - 1:
			return noisy(o["trim"], rnd, 4)
		if face == "front" and fy == 0 and fx in (4,):
			return noisy(o["badge"][0], rnd, 3)
		return noisy(o["uniform"], rnd, 4)

	paint_box(px, 0, 32, 9, 3, 9, cap)

	def peak(face, fx, fy, fw, fh):
		return noisy((22, 20, 24), rnd, 3)

	paint_box(px, 36, 32, 8, 1, 3, peak)

	def tunic(face, fx, fy, fw, fh):
		if face == "top":
			return noisy(o["uniform"], rnd, 4)
		if face == "bottom":
			return None
		if fy == 7:
			return noisy(o["trim"] if not (face == "front" and fx in (3, 4)) else (170, 172, 178), rnd, 4)
		if face == "front":
			if fy == 0 and fx in (3, 4):
				return noisy(o["shirt"], rnd, 3)
			if fy == 2 and fx in (1, 2):
				return noisy(o["badge"][0 if fx == 1 else 1], rnd, 3)
			if fx == 4 and 1 <= fy <= 6:
				return noisy(o["uniform_dark"], rnd, 3)
		if fy >= 8:
			return noisy(o["uniform_dark"] if fy == 11 else o["uniform"], rnd, 4)
		return noisy(o["uniform"], rnd, 4)

	paint_box(px, 0, 44, 8, 12, 4, tunic)

	def sleeve(face, fx, fy, fw, fh):
		if face == "top":
			return noisy(o["uniform"], rnd, 4)
		if face == "bottom":
			return None
		if fy == 10:
			return noisy(o["trim"], rnd, 4)
		if fy == 11:
			return None
		return noisy(o["uniform"], rnd, 4)

	paint_box(px, 40, 36, 4, 12, 4, sleeve)
	out(f"entity/trooper/{army}_officer.png", img)


def main():
	for role in ("rifleman", "medic", "sergeant"):
		armoured_skin("republic", role)
	for role in ("rifleman", "grenadier", "sergeant"):
		armoured_skin("imperial", role)
	officer_skin("republic")
	officer_skin("imperial")


if __name__ == "__main__":
	main()
