#!/usr/bin/env python3
"""Generates the holocron feature's textures and item JSON (original, deterministic).

Spec section 4: the Jedi holocron is a pale stone cube with gold seams, the Sith holocron a black
glass stepped pyramid with crimson seams, the Wayfarer holocron a worn brass sphere with a ring.
Each has a lit look (seams glowing) and a dark silhouette (unfound on the Holocrons shelf, and the
look of a sealed holocron as it leaves the pedestal).

Textures (src/main/resources/assets/oldrepublic/textures/):
  item/<kind>_holocron.png         16x16 inventory icon body
  item/<kind>_holocron_seams.png   16x16 inventory icon seams only (transparent elsewhere)
  item/<kind>_holocron_dark.png    16x16 dark silhouette icon (seams dim, no glow)
  item/<kind>_holocron_model.png   16x16 material sheet of the 3D model: body (top left), seams
                                   (top right), dark body (bottom left), dark seams (bottom right)
  gui/sprites/portrait/gatekeeper_<id>.png   32x32 projected head and shoulders of the 12 world
                                   holocrons' gatekeepers, banded like the projection, tinted by kind
  gui/sprites/portrait/gatekeeper_figure.png 64x64 pale banded humanoid in the player skin layout
                                   (overlay layers transparent): the projected figure's skin,
                                   multiplied by the kind's tint in code
  particle/holocron_mote_0.png     8x8 soft round speck, white (tinted in code)
  particle/projection_band_0.png   16x16 thin soft horizontal line, white (tinted in code)

JSON (src/main/resources/assets/oldrepublic/):
  items/<kind>_holocron.json       select on custom model data string "dark" (dark silhouette),
                                   then on display context: the flat icon in GUIs, the 3D model
                                   (with emissive seams) in hands, on the ground and in frames
  models/item/<kind>_holocron.json, <kind>_holocron_dark.json             flat icons
  models/item/<kind>_holocron_held.json, <kind>_holocron_held_dark.json   3D models
  particles/holocron_mote.json, particles/projection_band.json

The 3D models are 6 units across, centred on the model origin, so the FIXED display (scale 1)
shows a 0.375-block holocron: what RisingHolocron draws over the pedestal. The seam elements are
slightly outset, unshaded and carry light_emission, like the lightsaber blade.

Run from anywhere: python3 tools/assets/gen_textures_holocron.py
"""
import json
import math
import os
import random

from PIL import Image, ImageDraw

ASSETS = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "src", "main", "resources", "assets", "oldrepublic"))
NS = "oldrepublic"
KINDS = ("jedi", "sith", "wayfarer")
MODEL_MIN, MODEL_MAX = -16.0, 32.0


# ---------------------------------------------------------------- output helpers

def write_png(rel, img):
	path = os.path.join(ASSETS, "textures", rel)
	os.makedirs(os.path.dirname(path), exist_ok=True)
	img.save(path)
	print("wrote", os.path.relpath(path))


def write_json(rel, data):
	for el in data.get("elements", []):
		for corner in (el["from"], el["to"]):
			if any(c < MODEL_MIN or c > MODEL_MAX for c in corner):
				raise SystemExit(f"{rel}: element corner {corner} outside {MODEL_MIN}..{MODEL_MAX}")
	path = os.path.join(ASSETS, rel)
	os.makedirs(os.path.dirname(path), exist_ok=True)
	with open(path, "w", encoding="utf-8") as f:
		json.dump(data, f, indent="\t")
		f.write("\n")
	print("wrote", os.path.relpath(path))


def clamp(v):
	return max(0, min(255, int(round(v))))


def noisy(rgb, rnd, amount):
	d = rnd.randint(-amount, amount)
	return tuple(clamp(c + d) for c in rgb)


def shade(rgb, factor):
	return tuple(clamp(c * factor) for c in rgb)


def mix(a, b, t):
	return tuple(clamp(a[i] * (1 - t) + b[i] * t) for i in range(3))


# ---------------------------------------------------------------- palettes

PALETTES = {
	"jedi": {
		# Pale, slightly warm stone; gold seams.
		"top": (226, 218, 196), "left": (200, 191, 168), "right": (166, 158, 138), "edge": (96, 88, 74),
		"seam": (246, 206, 112), "seam_hi": (255, 238, 176),
		"dark_top": (82, 78, 72), "dark_left": (66, 62, 57), "dark_right": (50, 47, 43), "dark_edge": (30, 28, 25),
		"dark_seam": (104, 92, 70),
	},
	"sith": {
		# Black glass with a cold sheen; crimson seams.
		"top": (62, 54, 64), "left": (42, 36, 44), "right": (24, 20, 27), "edge": (8, 6, 10),
		"gloss": (112, 98, 116),
		"seam": (214, 58, 46), "seam_hi": (255, 132, 104),
		"dark_top": (34, 31, 36), "dark_left": (25, 23, 27), "dark_right": (16, 15, 18), "dark_edge": (6, 5, 7),
		"dark_seam": (62, 34, 34),
	},
	"wayfarer": {
		# Worn brass with green-brown patina; pale gold seams.
		"light": (226, 194, 128), "base": (176, 140, 82), "shadow": (108, 82, 46), "edge": (58, 42, 24),
		"patina": (112, 122, 92), "ring": (146, 112, 62), "ring_dark": (96, 72, 40),
		"seam": (255, 222, 140), "seam_hi": (255, 244, 204),
		"dark_light": (92, 82, 64), "dark_base": (70, 62, 48), "dark_shadow": (48, 42, 32), "dark_edge": (24, 20, 15),
		"dark_ring": (58, 50, 38), "dark_seam": (96, 86, 64),
	},
}


# ---------------------------------------------------------------- flat icons

def cube_icon(dark):
	"""An isometric stone cube; seams on the three front edges and across each face."""
	p = PALETTES["jedi"]
	pre = "dark_" if dark else ""
	rnd = random.Random(101 if dark else 100)
	body = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
	seams = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
	d = ImageDraw.Draw(body)
	top = [(8, 1), (14, 4), (8, 7), (2, 4)]
	left = [(2, 4), (8, 7), (8, 14), (2, 11)]
	right = [(8, 7), (14, 4), (14, 11), (8, 14)]
	d.polygon(left, fill=p[pre + "left"] + (255,))
	d.polygon(right, fill=p[pre + "right"] + (255,))
	d.polygon(top, fill=p[pre + "top"] + (255,))
	px = body.load()
	for y in range(16):
		for x in range(16):
			if px[x, y][3]:
				px[x, y] = noisy(px[x, y][:3], rnd, 5) + (255,)
	outline(body, p[pre + "edge"])

	sd = ImageDraw.Draw(seams)
	colour = (p["dark_seam"] if dark else p["seam"]) + (255,)
	# The three edges that meet at the front corner, where the stone plates join.
	sd.line([(8, 8), (8, 13)], fill=colour)
	sd.line([(3, 5), (7, 7)], fill=colour)
	sd.line([(9, 7), (13, 5)], fill=colour)
	# A small inset plate on each side face.
	sd.line([(4, 8), (6, 9)], fill=colour)
	sd.line([(10, 9), (12, 8)], fill=colour)
	if not dark:
		hi = p["seam_hi"] + (255,)
		sd.point([(8, 7), (8, 8)], fill=hi)
	return body, seams


def pyramid_icon(dark):
	"""A black glass stepped pyramid seen corner on; crimson seams along the steps and the front edge."""
	p = PALETTES["sith"]
	pre = "dark_" if dark else ""
	rnd = random.Random(201 if dark else 200)
	body = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
	seams = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
	d = ImageDraw.Draw(body)
	apex, base_l, base_f, base_r = (8, 1), (1, 12), (8, 15), (15, 12)
	d.polygon([apex, base_l, base_f], fill=p[pre + "left"] + (255,))
	d.polygon([apex, base_f, base_r], fill=p[pre + "right"] + (255,))
	px = body.load()
	for y in range(16):
		for x in range(16):
			if px[x, y][3]:
				px[x, y] = noisy(px[x, y][:3], rnd, 3) + (255,)
	if not dark:
		# A cold gloss streak down the lit face.
		for (x, y) in ((6, 5), (5, 7), (4, 9), (6, 6), (5, 8)):
			px[x, y] = p["gloss"] + (255,)
	# The steps: a darker notch line where each tier sits on the next.
	sd = ImageDraw.Draw(seams)
	colour = (p["dark_seam"] if dark else p["seam"]) + (255,)
	for t in (0.4, 0.7):
		lp = lerp_pt(apex, base_l, t)
		fp = lerp_pt(apex, base_f, t)
		rp = lerp_pt(apex, base_r, t)
		sd.line([lp, fp, rp], fill=colour)
	sd.line([apex, (8, 14)], fill=colour)
	outline(body, p[pre + "edge"])
	if not dark:
		sd.point([apex, lerp_pt(apex, base_f, 0.4)], fill=p["seam_hi"] + (255,))
	return body, seams


def sphere_icon(dark):
	"""A worn brass sphere with a tilted ring; pale gold seams on its meridian and equator."""
	p = PALETTES["wayfarer"]
	pre = "dark_" if dark else ""
	rnd = random.Random(301 if dark else 300)
	body = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
	seams = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
	px = body.load()
	cx, cy, r = 7.5, 8.0, 5.3
	ring_back, ring_front = ring_pixels(cx, cy)
	ring_colour = p["dark_ring"] if dark else p["ring"]
	for (x, y) in ring_back:
		px[x, y] = shade(ring_colour, 0.75) + (255,)
	for y in range(16):
		for x in range(16):
			nx = (x + 0.5 - cx) / r
			ny = (y + 0.5 - cy) / r
			dd = nx * nx + ny * ny
			if dd > 1.0:
				continue
			nz = math.sqrt(1 - dd)
			lam = max(0.0, -0.55 * nx - 0.5 * ny + 0.67 * nz)
			if lam > 0.72:
				c = p[pre + "light"]
			elif lam > 0.38:
				c = p[pre + "base"]
			else:
				c = p[pre + "shadow"]
			if not dark and rnd.random() < 0.08 and lam < 0.6:
				c = mix(c, p["patina"], 0.6)
			px[x, y] = noisy(c, rnd, 4) + (255,)
	outline(body, p[pre + "edge"])
	for (x, y) in ring_front:
		px[x, y] = noisy(ring_colour, rnd, 4) + (255,)
	sp = seams.load()
	colour = (p["dark_seam"] if dark else p["seam"]) + (255,)
	# Studs of light set into the ring's front.
	for (x, y) in sorted(ring_front):
		if (x * 7 + y) % 4 == 0:
			sp[x, y] = colour
	# Seams: the equator (a shallow arc) and a meridian, on the sphere only.
	for x in range(3, 13):
		y = int(round(cy + 1.4 * math.sqrt(max(0.0, 1 - ((x + 0.5 - cx) / r) ** 2)) - 0.5))
		if px[x, y][3] and (x, y) not in ring_front:
			sp[x, y] = colour
	for y in range(4, 13):
		x = int(round(cx + 1.6 * math.sqrt(max(0.0, 1 - ((y + 0.5 - cy) / r) ** 2)) - 0.5))
		if px[x, y][3] and (x, y) not in ring_front:
			sp[x, y] = colour
	if not dark:
		sp[int(cx + 1), int(cy + 1)] = p["seam_hi"] + (255,)
	return body, seams


def ring_pixels(cx, cy):
	"""Pixels of a tilted ellipse around the sphere, split into the half behind it and the half in front."""
	back, front = set(), set()
	tilt = math.radians(-18)
	for i in range(240):
		a = 2 * math.pi * i / 240
		ex, ey = 7.6 * math.cos(a), 2.3 * math.sin(a)
		x = cx + ex * math.cos(tilt) - ey * math.sin(tilt)
		y = cy + ex * math.sin(tilt) + ey * math.cos(tilt)
		pt = (int(math.floor(x + 0.5)), int(math.floor(y + 0.5)))
		if 0 <= pt[0] < 16 and 0 <= pt[1] < 16:
			(front if math.sin(a) > 0 else back).add(pt)
	return back - front, front


def lerp_pt(a, b, t):
	return (int(round(a[0] + (b[0] - a[0]) * t)), int(round(a[1] + (b[1] - a[1]) * t)))


def outline(img, colour):
	"""Darkens the outermost opaque pixels so the shape reads at 16 px."""
	px = img.load()
	w, h = img.size
	edge = []
	for y in range(h):
		for x in range(w):
			if not px[x, y][3]:
				continue
			for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
				nx, ny = x + dx, y + dy
				if not (0 <= nx < w and 0 <= ny < h) or not px[nx, ny][3]:
					edge.append((x, y))
					break
	for (x, y) in edge:
		px[x, y] = mix(px[x, y][:3], colour, 0.55) + (255,)


def merged(body, seams):
	out = body.copy()
	out.alpha_composite(seams)
	return out


ICONS = {"jedi": cube_icon, "sith": pyramid_icon, "wayfarer": sphere_icon}


# ---------------------------------------------------------------- 3D model sheet and models

def model_sheet(kind):
	"""Body, seam, dark body and dark seam quadrants (8x8 each) of the 3D model's texture."""
	p = PALETTES[kind]
	rnd = random.Random({"jedi": 400, "sith": 410, "wayfarer": 420}[kind])
	img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
	px = img.load()
	if kind == "jedi":
		body, dark_body = p["top"], p["dark_top"]
		trim, dark_trim = p["right"], p["dark_right"]
	elif kind == "sith":
		body, dark_body = p["left"], p["dark_left"]
		trim, dark_trim = p["top"], p["dark_top"]
	else:
		body, dark_body = p["base"], p["dark_base"]
		trim, dark_trim = p["ring"], p["dark_ring"]
	for y in range(8):
		for x in range(8):
			base = trim if y >= 6 else body
			dbase = dark_trim if y >= 6 else dark_body
			if kind == "wayfarer" and y < 6 and rnd.random() < 0.1:
				base = mix(base, p["patina"], 0.5)
			if kind == "sith" and y < 6 and (x + y) % 5 == 0:
				base = mix(base, p["gloss"], 0.35)
			px[x, y] = noisy(base, rnd, 5) + (255,)
			px[x, y + 8] = noisy(dbase, rnd, 3) + (255,)
	if kind == "jedi":
		# A 6x6 face pattern: a plate seam inset one pixel, joined to the face's corners.
		for (x, y) in seam_face_pattern():
			px[8 + x, y] = p["seam"] + (255,)
			px[8 + x, 8 + y] = p["dark_seam"] + (255,)
	else:
		for y in range(8):
			for x in range(8):
				px[8 + x, y] = (p["seam_hi"] if (x + y) % 4 == 0 else p["seam"]) + (255,)
				px[8 + x, 8 + y] = p["dark_seam"] + (255,)
	return img


def seam_face_pattern():
	pts = set()
	for i in range(1, 5):
		pts.update({(i, 1), (i, 4), (1, i), (4, i)})
	pts.update({(0, 0), (5, 0), (0, 5), (5, 5)})
	return pts


BODY = [0, 0, 8, 6]
TRIM = [0, 6, 8, 8]
SEAM = [8, 0, 14, 6]
SEAM_SOLID = [8, 0, 16, 8]


def dark_uv(region):
	return [region[0], region[1] + 8, region[2], region[3] + 8]


def sized(region, w, h):
	u0, v0, u1, v1 = region
	return [u0, v0, min(u1, u0 + max(1, round(w))), min(v1, v0 + max(1, round(h)))]


def element(frm, to, region, dark, emissive=False, fit=True, rotation=None):
	sx, sy, sz = (to[i] - frm[i] for i in range(3))
	reg = dark_uv(region) if dark else region

	def uv(w, h):
		return sized(reg, w, h) if fit else list(reg)

	el = {
		"from": [round(c, 3) for c in frm],
		"to": [round(c, 3) for c in to],
		"faces": {
			"north": {"uv": uv(sx, sy), "texture": "#holocron"},
			"south": {"uv": uv(sx, sy), "texture": "#holocron"},
			"east": {"uv": uv(sz, sy), "texture": "#holocron"},
			"west": {"uv": uv(sz, sy), "texture": "#holocron"},
			"up": {"uv": uv(sx, sz), "texture": "#holocron"},
			"down": {"uv": uv(sx, sz), "texture": "#holocron"},
		},
	}
	if rotation is not None:
		el["rotation"] = rotation
	if emissive:
		el["shade"] = False
		if not dark:
			el["light_emission"] = 12
	return el


def centred(lo, hi, y0, y1):
	return [lo, y0, lo], [hi, y1, hi]


def cube_elements(dark):
	f, t = centred(5, 11, 5, 11)
	# The seam shell sits 0.05 outside the stone; only the seam pixels of its texture are opaque.
	shell_f, shell_t = centred(4.95, 11.05, 4.95, 11.05)
	return [element(f, t, BODY, dark), element(shell_f, shell_t, SEAM, dark, emissive=True, fit=False)]


def pyramid_elements(dark):
	tiers = [(4.5, 11.5, 5.0, 6.6), (5.4, 10.6, 6.6, 8.2), (6.3, 9.7, 8.2, 9.8), (7.2, 8.8, 9.8, 11.0)]
	els = []
	for lo, hi, y0, y1 in tiers:
		f, t = centred(lo, hi, y0, y1)
		els.append(element(f, t, BODY, dark))
	# A thin glowing band where each tier rests on the one below, and a capstone seam.
	for lo, hi, y0, y1 in tiers[1:]:
		f, t = centred(lo - 0.12, hi + 0.12, y0 - 0.2, y0 + 0.2)
		els.append(element(f, t, SEAM_SOLID, dark, emissive=True))
	f, t = centred(7.6, 8.4, 11.0, 11.4)
	els.append(element(f, t, SEAM_SOLID, dark, emissive=True))
	return els


def sphere_elements(dark):
	# A sphere of radius 3 around (8, 8, 8) from five stacked slabs.
	slabs = [(6.6, 9.4, 5.0, 5.7), (5.6, 10.4, 5.7, 6.7), (5.0, 11.0, 6.7, 9.3), (5.6, 10.4, 9.3, 10.3), (6.6, 9.4, 10.3, 11.0)]
	els = []
	for lo, hi, y0, y1 in slabs:
		f, t = centred(lo, hi, y0, y1)
		els.append(element(f, t, BODY, dark))
	# The equator seam, and a meridian seam on two opposite sides.
	f, t = centred(4.92, 11.08, 7.85, 8.15)
	els.append(element(f, t, SEAM_SOLID, dark, emissive=True))
	els.append(element([7.85, 5.6, 4.92], [8.15, 10.4, 11.08], SEAM_SOLID, dark, emissive=True))
	# The ring: four bars of a square frame round the sphere, tilted together.
	tilt = {"angle": 22.5, "axis": "x", "origin": [8, 8, 8]}
	lo, hi, inner_lo, inner_hi = 3.6, 12.4, 4.3, 11.7
	y0, y1 = 7.7, 8.3
	els.append(element([lo, y0, lo], [hi, y1, inner_lo], TRIM, dark, rotation=tilt))
	els.append(element([lo, y0, inner_hi], [hi, y1, hi], TRIM, dark, rotation=tilt))
	els.append(element([lo, y0, inner_lo], [inner_lo, y1, inner_hi], TRIM, dark, rotation=tilt))
	els.append(element([inner_hi, y0, inner_lo], [hi, y1, inner_hi], TRIM, dark, rotation=tilt))
	return els


ELEMENTS = {"jedi": cube_elements, "sith": pyramid_elements, "wayfarer": sphere_elements}

DISPLAY = {
	"thirdperson_righthand": {"rotation": [10, -30, 0], "translation": [0, 3, 1], "scale": [0.6, 0.6, 0.6]},
	"thirdperson_lefthand": {"rotation": [10, 30, 0], "translation": [0, 3, 1], "scale": [0.6, 0.6, 0.6]},
	"firstperson_righthand": {"rotation": [0, -30, 0], "translation": [1.5, 4, 0], "scale": [0.6, 0.6, 0.6]},
	"firstperson_lefthand": {"rotation": [0, 30, 0], "translation": [1.5, 4, 0], "scale": [0.6, 0.6, 0.6]},
	"ground": {"translation": [0, 3, 0], "scale": [0.5, 0.5, 0.5]},
	"head": {"translation": [0, 13, 0], "scale": [0.8, 0.8, 0.8]},
	"gui": {"rotation": [30, 225, 0], "scale": [1.4, 1.4, 1.4]},
	"fixed": {"scale": [1, 1, 1]},
}


def held_model(kind, dark):
	sheet = f"{NS}:item/{kind}_holocron_model"
	return {
		"gui_light": "side",
		"textures": {"holocron": sheet, "particle": sheet},
		"elements": ELEMENTS[kind](dark),
		"display": DISPLAY,
	}


def item_definition(kind):
	def by_context(suffix):
		return {
			"type": "minecraft:select",
			"property": "minecraft:display_context",
			"cases": [{"when": ["gui"], "model": {"type": "minecraft:model", "model": f"{NS}:item/{kind}_holocron{suffix}"}}],
			"fallback": {"type": "minecraft:model", "model": f"{NS}:item/{kind}_holocron_held{suffix}"},
		}

	return {"model": {
		"type": "minecraft:select",
		"property": "minecraft:custom_model_data",
		"index": 0,
		"cases": [{"when": "dark", "model": by_context("_dark")}],
		"fallback": by_context(""),
	}}


# ---------------------------------------------------------------- gatekeeper portraits

TINTS = {
	# (shadow, mid, highlight) of the projection's light, by kind.
	"jedi": ((28, 24, 16), (224, 198, 136), (255, 248, 226)),
	"sith": ((24, 10, 10), (198, 82, 62), (255, 196, 178)),
	"wayfarer": ((26, 18, 10), (184, 132, 70), (240, 206, 146)),
}

# Each gatekeeper: kind, face width, hair, headgear, clothes and marks (spec 4.2 gatekeepers).
GATEKEEPERS = [
	("wayfarers_compass", "wayfarer", {"face": 5.0, "hair": "short", "head": "goggles", "neck": "scarf"}),
	("wayfarer_pell", "wayfarer", {"face": 5.4, "hair": "none", "hood": True, "beard": "full", "neck": "robe"}),
	("wayfarer_osk", "wayfarer", {"face": 5.6, "hair": "short", "head": "cap", "neck": "collar", "mark": "eyepiece", "beard": "stubble"}),
	("wayfarer_maelis", "wayfarer", {"face": 4.9, "hair": "tied", "head": "band", "neck": "scarf"}),
	("jedi_morrane", "jedi", {"face": 5.3, "hair": "none", "hood": True, "beard": "long", "neck": "robe", "mark": "lined"}),
	("jedi_sarrow", "jedi", {"face": 4.8, "hair": "bun", "neck": "robe"}),
	("jedi_ketterin", "jedi", {"face": 5.5, "hair": "short", "neck": "pauldrons", "mark": "scar"}),
	("jedi_vanterre", "jedi", {"face": 4.9, "hair": "long", "head": "circlet", "neck": "collar"}),
	("sith_zarvos", "sith", {"face": 5.6, "hair": "none", "head": "crest", "neck": "pauldrons", "beard": "stubble"}),
	("sith_ysolde", "sith", {"face": 4.9, "hair": "long", "neck": "collar", "mark": "tattoo"}),
	("sith_thrane", "sith", {"face": 5.4, "hair": "bald", "head": "visor", "neck": "collar"}),
	("sith_torr", "sith", {"face": 5.0, "hair": "none", "hood": True, "mask": True, "neck": "robe"}),
]


class Grid:
	"""A 32x32 field of light (0 dark to 1 bright) painted shape by shape, later mapped to the tint."""

	def __init__(self, size=32):
		self.size = size
		self.v = [[None] * size for _ in range(size)]

	def set(self, x, y, value):
		if 0 <= x < self.size and 0 <= y < self.size:
			self.v[y][x] = max(0.0, min(1.0, value))

	def get(self, x, y):
		return self.v[y][x] if 0 <= x < self.size and 0 <= y < self.size else None

	def ellipse(self, cx, cy, rx, ry, value, lit=True, only=None):
		for y in range(self.size):
			for x in range(self.size):
				nx = (x + 0.5 - cx) / rx
				ny = (y + 0.5 - cy) / ry
				dd = nx * nx + ny * ny
				if dd > 1.0 or (only is not None and not only(x, y)):
					continue
				if lit:
					nz = math.sqrt(1 - dd)
					lam = max(0.0, -0.5 * nx - 0.45 * ny + 0.74 * nz)
					self.set(x, y, value * (0.5 + 0.55 * lam))
				else:
					self.set(x, y, value)

	def rect(self, x0, y0, x1, y1, value):
		for y in range(y0, y1):
			for x in range(x0, x1):
				self.set(x, y, value)


def portrait(kind, traits, seed):
	rnd = random.Random(seed)
	g = Grid()
	cx, cy, rx, ry = 16.0, 12.6, traits["face"] + 0.7, 7.4
	in_face = lambda x, y: ((x + 0.5 - cx) / rx) ** 2 + ((y + 0.5 - cy) / ry) ** 2 <= 1.0

	# Hood behind the head and over the shoulders.
	if traits.get("hood"):
		g.ellipse(cx, 13.5, rx + 3.4, 10.5, 0.42)

	# Shoulders and clothes.
	for y in range(22, 32):
		half = min(15.5, 8.0 + math.sqrt(y - 21) * 3.2)
		for x in range(32):
			if abs(x + 0.5 - cx) <= half:
				side = (x + 0.5 - cx) / half
				g.set(x, y, 0.5 - 0.16 * side - 0.01 * (y - 22))
	neck = traits.get("neck")
	if neck == "pauldrons":
		g.ellipse(8.5, 24.0, 5.0, 3.4, 0.7)
		g.ellipse(23.5, 24.0, 5.0, 3.4, 0.62)
	elif neck == "collar":
		for y in range(18, 25):
			for x in range(10, 22):
				if abs(x + 0.5 - cx) > 2.2 + (y - 18) * 0.5:
					g.set(x, y, 0.6 - 0.02 * (y - 18))
	elif neck == "scarf":
		for y in range(19, 24):
			for x in range(11, 21):
				g.set(x, y, 0.56 if (x + y) % 3 else 0.44)
	elif neck == "robe":
		for y in range(21, 32):
			for x in range(32):
				if abs(abs(x + 0.5 - cx) - (y - 19) * 0.55) < 0.8 and abs(x + 0.5 - cx) < 12:
					g.set(x, y, 0.62)

	# Neck, then the face.
	if neck not in ("collar", "scarf"):
		g.rect(14, 18, 18, 22, 0.55)
	g.ellipse(cx, cy, rx, ry, 0.84)

	hair = traits.get("hair")
	top = lambda limit: (lambda x, y: y < limit)
	if hair in ("short", "long", "tied", "bun"):
		g.ellipse(cx, 9.6, rx + 0.6, 4.6, 0.4, only=top(9))
	if hair == "long":
		for y in range(8, 22):
			for x in (int(cx - rx - 0.5), int(cx - rx + 0.5), int(cx + rx - 0.5), int(cx + rx + 0.5)):
				g.set(x, y, 0.38 - 0.006 * (y - 8))
	if hair == "tied":
		for y in range(9, 19):
			g.set(int(cx + rx + 0.5), y, 0.36)
	if hair == "bun":
		g.ellipse(cx, 4.4, 2.3, 2.0, 0.44)
	if hair == "bald":
		for (x, y) in ((13, 8), (14, 7), (15, 7)):
			g.set(x, y, 0.98)
	if traits.get("hood"):
		# The hood's rim shades the brow.
		g.ellipse(cx, 9.0, rx + 1.4, 3.6, 0.3, only=lambda x, y: y < 9)

	beard = traits.get("beard")
	if beard in ("full", "long"):
		bottom = 22 if beard == "long" else 20
		for y in range(15, bottom):
			for x in range(10, 23):
				if in_face(x, y) or (y >= 19 and abs(x + 0.5 - cx) < 3.5 - (y - 19) * 0.6):
					g.set(x, y, (0.86 if beard == "long" else 0.5) - 0.05 * ((x + y) % 2))
	elif beard == "stubble":
		for y in range(15, 20):
			for x in range(10, 23):
				if in_face(x, y) and (x + y) % 2 == 0:
					g.set(x, y, g.get(x, y) * 0.78)

	if traits.get("mask"):
		for y in range(14, 20):
			for x in range(10, 23):
				if in_face(x, y):
					g.set(x, y, 0.3 if y % 2 else 0.42)

	head = traits.get("head")
	if head == "goggles":
		g.ellipse(13.3, 7.6, 1.7, 1.4, 0.95, lit=False)
		g.ellipse(18.7, 7.6, 1.7, 1.4, 0.95, lit=False)
		g.set(13, 7, 0.25)
		g.set(18, 7, 0.25)
		g.rect(10, 7, 12, 8, 0.6)
		g.rect(20, 7, 22, 8, 0.6)
	elif head == "cap":
		g.ellipse(cx, 8.2, rx + 0.8, 4.0, 0.46, only=top(8))
		g.rect(9, 8, 23, 9, 0.28)
	elif head == "band":
		g.rect(int(cx - rx), 8, int(cx + rx) + 1, 9, 0.66)
	elif head == "circlet":
		g.rect(int(cx - rx) + 1, 8, int(cx + rx), 9, 0.9)
		g.set(16, 8, 1.0)
		g.set(15, 8, 1.0)
	elif head == "crest":
		g.ellipse(cx, 10.0, rx + 0.9, 6.0, 0.5, only=top(10))
		for y in range(10, 17):
			g.set(int(cx - rx - 0.4), y, 0.48)
			g.set(int(cx + rx + 0.4), y, 0.44)
		for y in range(0, 6):
			g.set(15, y, 0.7)
			g.set(16, y, 0.62)
	elif head == "visor":
		g.rect(10, 10, 22, 12, 0.24)
		g.rect(10, 10, 22, 11, 0.36)

	# Eyes, brows and mouth (the visor and mask cover theirs).
	if head != "visor":
		for x in (13, 14, 18, 19):
			g.set(x, 12, 0.1)
		g.set(14, 12, 0.72)
		g.set(18, 12, 0.72)
		for x in (12, 13, 14, 18, 19, 20):
			if g.get(x, 11) is not None and g.get(x, 11) > 0.45:
				g.set(x, 11, g.get(x, 11) * 0.62)
	if not traits.get("mask") and beard not in ("full", "long"):
		for x in (15, 16, 17):
			g.set(x, 16, 0.36)

	mark = traits.get("mark")
	if mark == "scar":
		for i in range(5):
			g.set(19 + i // 2, 9 + i, 0.98)
	elif mark == "tattoo":
		for y in range(13, 17):
			g.set(13, y, 0.22)
			g.set(19, y, 0.22)
		g.set(16, 9, 0.22)
		g.set(16, 10, 0.22)
	elif mark == "eyepiece":
		for (x, y) in ((17, 11), (18, 11), (19, 11), (20, 11), (17, 13), (18, 13), (19, 13), (20, 13), (17, 12), (20, 12)):
			g.set(x, y, 0.95)
	elif mark == "lined":
		for (x, y) in ((12, 14), (20, 14), (13, 15), (19, 15), (15, 9), (16, 9), (17, 9)):
			g.set(x, y, g.get(x, y) * 0.7 if g.get(x, y) else 0.4)

	return colourise(g, kind, rnd)


def colourise(g, kind, rnd):
	"""Maps the light field to the kind's tint, adds the dark backdrop, banding, a rim light and one
	slipped row, the look of a projection rather than a painting."""
	low, mid, high = TINTS[kind]
	img = Image.new("RGBA", (32, 32), (0, 0, 0, 255))
	px = img.load()
	glitch = 6 + rnd.randint(0, 3) * 5
	for y in range(32):
		for x in range(32):
			v = g.get(x, y)
			if v is None:
				# Backdrop: near black with a faint wash of the projection's light toward the centre.
				d = math.hypot(x + 0.5 - 16, y + 0.5 - 14) / 22
				wash = max(0.0, 0.16 - 0.14 * d)
				px[x, y] = noisy(mix((8, 9, 12), mid, wash), rnd, 2) + (255,)
				continue
			edge = any(g.get(x + dx, y + dy) is None for dx, dy in ((1, 0), (-1, 0), (0, -1))) and x > 16
			if edge:
				v = min(1.0, v + 0.25)
			if y % 3 == 2:
				v *= 0.8
			c = mix(low, mid, v / 0.6) if v < 0.6 else mix(mid, high, (v - 0.6) / 0.4)
			px[x, y] = noisy(c, rnd, 3) + (255,)
	# One row slipped a pixel sideways.
	row = [px[x, glitch] for x in range(32)]
	for x in range(32):
		px[x, glitch] = row[x - 1] if x > 0 else row[0]
	return img


# ---------------------------------------------------------------- projected figure skin

# Base layer boxes of the 64x64 player skin: (u, v, width, height, depth).
SKIN_PARTS = {
	"head": (0, 0, 8, 8, 8),
	"body": (16, 16, 8, 12, 4),
	"right_arm": (40, 16, 4, 12, 4),
	"left_arm": (32, 48, 4, 12, 4),
	"right_leg": (0, 16, 4, 12, 4),
	"left_leg": (16, 48, 4, 12, 4),
}
# Rows (from the top of the figure) where each part's front face starts: for the fade toward the feet.
PART_TOP = {"head": 0, "body": 8, "right_arm": 8, "left_arm": 8, "right_leg": 20, "left_leg": 20}


def figure_skin():
	"""Pale, horizontally banded, more transparent toward the feet; overlay layers left transparent."""
	rnd = random.Random(500)
	img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
	px = img.load()
	for name, (u, v, w, h, d) in SKIN_PARTS.items():
		for y in range(v, v + d + h):
			for x in range(u, u + 2 * (w + d)):
				in_caps = y < v + d
				if in_caps and not (u + d <= x < u + d + 2 * w):
					continue
				row = PART_TOP[name] + max(0, y - (v + d))
				fade = 1.0 - max(0.0, (row - 18) / 14.0) * 0.6
				band = 0.82 if row % 3 == 2 else 1.0
				lum = 236 * band
				alpha = 225 * fade * band
				px[x, y] = (clamp(lum + rnd.randint(-4, 4)), clamp(lum + rnd.randint(-4, 4)), clamp(lum + 6), clamp(alpha))
	# A face: brow shadow and eyes on the head's front (u 8..16, v 8..16).
	for x in range(9, 15):
		px[x, 10] = (200, 200, 206, 200)
	for x in (10, 13):
		px[x, 11] = (150, 150, 158, 215)
	for x in (11, 12):
		px[x, 14] = (196, 196, 204, 205)
	# A robe's edge down the body's front (u 20..28, v 20..32).
	for y in range(20, 32):
		px[23, y] = (210, 210, 216, px[23, y][3])
	return img


# ---------------------------------------------------------------- particles

def mote_texture():
	img = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
	px = img.load()
	for y in range(8):
		for x in range(8):
			d = math.hypot(x - 3.5, y - 3.5) / 3.6
			a = max(0.0, 1.0 - d) ** 1.6
			if a > 0.02:
				px[x, y] = (255, 255, 255, clamp(a * 255))
	return img


def band_texture():
	img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
	px = img.load()
	for y in range(16):
		fy = max(0.0, 1.0 - abs(y - 7.5) / 1.6)
		for x in range(16):
			fx = min(1.0, (min(x, 15 - x) + 0.5) / 3.0)
			a = fx * fy ** 1.4
			if a > 0.02:
				px[x, y] = (255, 255, 255, clamp(a * 220))
	return img


# ---------------------------------------------------------------- main

def main():
	for kind in KINDS:
		body, seams = ICONS[kind](False)
		dark_body, dark_seams = ICONS[kind](True)
		write_png(f"item/{kind}_holocron.png", body)
		write_png(f"item/{kind}_holocron_seams.png", seams)
		write_png(f"item/{kind}_holocron_dark.png", merged(dark_body, dark_seams))
		write_png(f"item/{kind}_holocron_model.png", model_sheet(kind))
		write_json(f"models/item/{kind}_holocron.json", {"parent": "minecraft:item/generated", "textures": {
			"layer0": f"{NS}:item/{kind}_holocron", "layer1": f"{NS}:item/{kind}_holocron_seams"}})
		write_json(f"models/item/{kind}_holocron_dark.json", {"parent": "minecraft:item/generated", "textures": {
			"layer0": f"{NS}:item/{kind}_holocron_dark"}})
		write_json(f"models/item/{kind}_holocron_held.json", held_model(kind, False))
		write_json(f"models/item/{kind}_holocron_held_dark.json", held_model(kind, True))
		write_json(f"items/{kind}_holocron.json", item_definition(kind))

	for i, (hid, kind, traits) in enumerate(GATEKEEPERS):
		write_png(f"gui/sprites/portrait/gatekeeper_{hid}.png", portrait(kind, traits, 600 + i))
	write_png("gui/sprites/portrait/gatekeeper_figure.png", figure_skin())

	write_png("particle/holocron_mote_0.png", mote_texture())
	write_png("particle/projection_band_0.png", band_texture())
	write_json("particles/holocron_mote.json", {"textures": [f"{NS}:holocron_mote_0"]})
	write_json("particles/projection_band.json", {"textures": [f"{NS}:projection_band_0"]})


if __name__ == "__main__":
	main()
