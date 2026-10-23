#!/usr/bin/env python3
"""Generates space's textures and item JSON (Milestone 5, W2). PLACEHOLDER art: original and
deterministic, drawn here from shapes and noise; nothing is copied or traced, and no ship design
from any game or film is imitated.

Textures (src/main/resources/assets/oldrepublic/textures/):
  environment/space/planet_{home,selvaren,varkhast,ghessar,tyrvann}.png
                                512x256 equirectangular colour maps (longitude across, latitude down),
                                seamless across the date line; OrbitSky samples them per vertex
  environment/space/nebula.png  512x64 band (along x, wrapping; across y), colour with alpha
  entity/space/hymn_interceptor.png (+ _glow)  128x64  black lacquer and violet, blade wings
  entity/space/kajidic_raider.png (+ _glow)    128x64  gunmetal, ochre bands, patches and soot
  entity/space/hask_fighter.png (+ _glow)      128x64  clan iron, ember and bone
  entity/space/blockade_gunship.png            256x256 slab-sided dark hull, violet trim
  entity/space/blockade_gunship_glow.png       engines, running lights, bridge windows
  entity/space/blockade_gunship_core.png       the shield generator's core
  entity/space/ship_missile.png                32x32
  entity/space/nav_buoy.png (+ _lamp)          64x32 (the lamp is white: the renderer tints it)
  particle/hull_debris_{0..3}.png              8x8 plating fragments (light grey: the particle tints them)
  item/{concussion_missile,sealed_cargo,hymn_interceptor_spawn_egg,kajidic_raider_spawn_egg,
       hask_fighter_spawn_egg}.png             16x16 icons

JSON (src/main/resources/assets/oldrepublic/):
  items/<id>.json and models/item/<id>.json for the five items above
  particles/hull_debris.json

The entity textures follow their models' box UV layouts (StarfighterModel, GunshipModel,
ShipMissileRenderer.MissileModel, NavBuoyRenderer.BuoyModel): a box at texOffs (u, v) of size
(w, h, d) takes the top at (u+d, v) w x d (the nose at its top edge, the ship's right on the right),
the bottom at (u+d+w, v), the left side at (u, v+d) d x h (the nose on the left), the rear at
(u+d, v+d) w x h, the right side at (u+d+w, v+d) d x h (the nose on the right) and the front at
(u+2d+w, v+d) w x h. Keep the box tables below in step with the models' texOffs; check_layout
refuses overlaps. Each _glow texture has the same layout, transparent except what glows.

Run from anywhere: python3 tools/assets/gen_textures_space.py
"""
import json
import math
import os
import random

import numpy as np
from PIL import Image

ASSETS = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "src", "main", "resources", "assets", "oldrepublic"))
NS = "oldrepublic"


# ---------------------------------------------------------------- output and colour helpers

def write_png(rel, img):
	path = os.path.join(ASSETS, "textures", rel)
	os.makedirs(os.path.dirname(path), exist_ok=True)
	img.save(path)
	print("wrote", os.path.relpath(path))


def write_json(rel, data):
	path = os.path.join(ASSETS, rel)
	os.makedirs(os.path.dirname(path), exist_ok=True)
	with open(path, "w", encoding="utf-8", newline="\n") as f:
		json.dump(data, f, indent="\t")
		f.write("\n")
	print("wrote", os.path.relpath(path))


def clamp(v):
	return max(0, min(255, int(round(v))))


def shade(rgb, factor):
	return tuple(clamp(c * factor) for c in rgb[:3])


def mix(a, b, t):
	return tuple(clamp(x + (y - x) * t) for x, y in zip(a[:3], b[:3]))


def noisy(rgb, rnd, amount):
	return tuple(clamp(c + rnd.uniform(-amount, amount)) for c in rgb[:3])


def hash01(*values):
	h = 2166136261
	for v in values:
		h = ((h ^ (v & 0xFFFFFFFF)) * 16777619) & 0xFFFFFFFF
	h ^= h >> 13
	h = (h * 0x5BD1E995) & 0xFFFFFFFF
	h ^= h >> 15
	return (h & 0xFFFF) / 65535.0


# ---------------------------------------------------------------- noise (numpy, seamless on the sphere)

def _lattice(ix, iy, iz, seed):
	n = (ix * 374761393 + iy * 668265263 + iz * 1440662683 + seed * 2654435761) & 0xFFFFFFFF
	n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
	n = n ^ (n >> 16)
	return (n & 0xFFFFFF) / float(0xFFFFFF)


def value_noise3(x, y, z, seed):
	"""Smooth value noise in 0..1 at the points (x, y, z)."""
	x0 = np.floor(x)
	y0 = np.floor(y)
	z0 = np.floor(z)
	fx = x - x0
	fy = y - y0
	fz = z - z0
	ix = x0.astype(np.int64)
	iy = y0.astype(np.int64)
	iz = z0.astype(np.int64)
	sx = fx * fx * (3 - 2 * fx)
	sy = fy * fy * (3 - 2 * fy)
	sz = fz * fz * (3 - 2 * fz)

	def corner(dx, dy, dz):
		return _lattice(ix + dx, iy + dy, iz + dz, seed)

	x00 = corner(0, 0, 0) + (corner(1, 0, 0) - corner(0, 0, 0)) * sx
	x10 = corner(0, 1, 0) + (corner(1, 1, 0) - corner(0, 1, 0)) * sx
	x01 = corner(0, 0, 1) + (corner(1, 0, 1) - corner(0, 0, 1)) * sx
	x11 = corner(0, 1, 1) + (corner(1, 1, 1) - corner(0, 1, 1)) * sx
	y0v = x00 + (x10 - x00) * sy
	y1v = x01 + (x11 - x01) * sy
	return y0v + (y1v - y0v) * sz


def fbm3(x, y, z, seed, octaves=5, frequency=2.0, gain=0.5, lacunarity=2.03):
	"""Fractal value noise, normalised to about 0..1."""
	total = np.zeros_like(x)
	amplitude = 1.0
	norm = 0.0
	f = frequency
	for i in range(octaves):
		total += amplitude * value_noise3(x * f + 17.3 * i, y * f - 9.1 * i, z * f + 5.7 * i, seed + i * 101)
		norm += amplitude
		amplitude *= gain
		f *= lacunarity
	return total / norm


def ridged3(x, y, z, seed, octaves=4, frequency=3.0, sharpness=6):
	"""Ridged noise: 1 along thin crests (canyons, lava cracks, mountain chains), 0 away from them."""
	n = fbm3(x, y, z, seed, octaves, frequency)
	return np.clip(1 - np.abs(n - 0.5) * 2, 0, 1) ** sharpness


def sphere_grid(w, h):
	"""Unit-sphere points for an equirectangular map: longitude across, latitude down (north at the top)."""
	u = (np.arange(w) + 0.5) / w
	v = (np.arange(h) + 0.5) / h
	lon, lat = np.meshgrid(u * 2 * math.pi, (0.5 - v) * math.pi)
	return np.cos(lat) * np.cos(lon), np.sin(lat), np.cos(lat) * np.sin(lon), lat


def ramp(values, stops):
	"""Maps values (0..1) through colour stops [(t, (r, g, b)), ...] to an (h, w, 3) float array."""
	ts = [s[0] for s in stops]
	out = np.zeros(values.shape + (3,))
	for c in range(3):
		out[..., c] = np.interp(values, ts, [s[1][c] for s in stops])
	return out


def lerp_rgb(a, b, t):
	"""Blends two (h, w, 3) colour arrays (or a colour tuple) by t (h, w)."""
	a = np.asarray(a, dtype=float)
	b = np.asarray(b, dtype=float)
	return a + (b - a) * t[..., None]


def smoothstep(e0, e1, x):
	t = np.clip((x - e0) / (e1 - e0), 0, 1)
	return t * t * (3 - 2 * t)


def to_image(rgb, alpha=None):
	arr = np.clip(np.round(rgb), 0, 255).astype(np.uint8)
	if alpha is None:
		return Image.fromarray(arr, "RGB").convert("RGBA")
	a = np.clip(np.round(alpha), 0, 255).astype(np.uint8)
	return Image.fromarray(np.dstack([arr, a]), "RGBA")


# ---------------------------------------------------------------- the planets seen from orbit (5.1)

MAP_W, MAP_H = 512, 256


def clouds(x, y, z, seed, cover, lat, banding=0.0):
	"""Cloud opacity 0..1: domain-warped noise for swirls, thinned by cover (0 none, 1 overcast)."""
	wx = fbm3(x, y, z, seed + 7, 3, 2.2) - 0.5
	wy = fbm3(x, y, z, seed + 8, 3, 2.2) - 0.5
	wz = fbm3(x, y, z, seed + 9, 3, 2.2) - 0.5
	n = fbm3(x + wx * 1.6, y + wy * 1.6, z + wz * 1.6, seed, 5, 3.0)
	if banding:
		n = n + banding * np.sin(lat * 9 + n * 4) * 0.08
	threshold = 0.62 - cover * 0.3
	return smoothstep(threshold, threshold + 0.16, n)


def planet_home():
	"""The home world: blue oceans, continents of forest, steppe and desert, ice caps, white weather."""
	x, y, z, lat = sphere_grid(MAP_W, MAP_H)
	alat = np.abs(lat) / (math.pi / 2)
	elevation = fbm3(x, y, z, 1101, 6, 1.6)
	land = smoothstep(0.52, 0.56, elevation)
	moisture = fbm3(x, y, z, 1102, 4, 2.4)
	ocean = ramp(np.clip((elevation - 0.25) / 0.31, 0, 1), [(0, (14, 36, 82)), (0.7, (24, 62, 124)), (1, (46, 104, 160))])
	desert_band = np.exp(-((alat - 0.3) / 0.12) ** 2)
	green = ramp(moisture, [(0, (124, 118, 72)), (0.45, (72, 112, 58)), (1, (38, 84, 46))])
	dry = np.array((176, 150, 104))
	ground = lerp_rgb(green, dry, np.clip(desert_band * (1.2 - moisture * 1.4), 0, 1))
	mountains = smoothstep(0.66, 0.78, elevation)
	ground = lerp_rgb(ground, (112, 104, 96), mountains * 0.7)
	tundra = smoothstep(0.62, 0.75, alat)
	ground = lerp_rgb(ground, (132, 128, 116), tundra)
	surface = lerp_rgb(ocean, ground, land)
	ice = smoothstep(0.84, 0.9, alat + (elevation - 0.5) * 0.2)
	surface = lerp_rgb(surface, (232, 238, 244), ice)
	c = clouds(x, y, z, 1103, 0.45, lat, banding=1.0)
	surface = lerp_rgb(surface, (244, 246, 250), c * 0.85)
	return to_image(surface)


def planet_selvaren():
	"""Selvaren: a forest world under mist, with silver lakes and pale cloud banks."""
	x, y, z, lat = sphere_grid(MAP_W, MAP_H)
	elevation = fbm3(x, y, z, 1201, 6, 1.9)
	tone = fbm3(x, y, z, 1202, 4, 3.0)
	forest = ramp(tone, [(0, (30, 70, 40)), (0.5, (52, 104, 58)), (1, (90, 132, 74))])
	water = smoothstep(0.42, 0.38, elevation)
	surface = lerp_rgb(forest, (150, 168, 178), water)
	highland = smoothstep(0.68, 0.8, elevation)
	surface = lerp_rgb(surface, (120, 132, 118), highland * 0.6)
	mist = clouds(x, y, z, 1203, 0.3, lat)
	surface = lerp_rgb(surface, (208, 226, 218), mist * 0.6)
	return to_image(surface)


def planet_varkhast():
	"""Varkhast: rust plains and black basalt, glowing fissures, ash in the air."""
	x, y, z, lat = sphere_grid(MAP_W, MAP_H)
	base = fbm3(x, y, z, 1301, 6, 2.0)
	surface = ramp(base, [(0, (54, 50, 50)), (0.4, (98, 70, 54)), (0.6, (142, 76, 46)), (1, (176, 108, 70))])
	cracks = ridged3(x, y, z, 1302, 4, 3.4, sharpness=18)
	surface = lerp_rgb(surface, (226, 104, 40), np.clip(cracks * 1.1, 0, 1) * smoothstep(0.55, 0.35, base))
	ash = clouds(x, y, z, 1303, 0.35, lat)
	surface = lerp_rgb(surface, (122, 110, 100), ash * 0.7)
	return to_image(surface)


def planet_ghessar():
	"""Ghessar: a dune sea, white salt flats, dark canyon chains, a little dust in the air."""
	x, y, z, lat = sphere_grid(MAP_W, MAP_H)
	base = fbm3(x, y, z, 1401, 6, 1.8)
	dunes = 0.5 + 0.5 * np.sin((x * 31 + y * 7 + fbm3(x, y, z, 1402, 3, 4.0) * 9))
	sand = ramp(base * 0.8 + dunes * 0.2, [(0, (172, 120, 62)), (0.5, (206, 158, 86)), (1, (230, 194, 128))])
	salt = smoothstep(0.62, 0.66, fbm3(x, y, z, 1403, 4, 2.6))
	surface = lerp_rgb(sand, (236, 230, 214), salt)
	canyons = ridged3(x, y, z, 1404, 5, 2.8, sharpness=16)
	surface = lerp_rgb(surface, (118, 74, 44), np.clip(canyons * 0.9, 0, 1))
	dust = clouds(x, y, z, 1405, 0.15, lat)
	surface = lerp_rgb(surface, (222, 190, 140), dust * 0.6)
	return to_image(surface)


def planet_tyrvann():
	"""Tyrvann: dark jungle, black mire and basalt under a storm that never clears."""
	x, y, z, lat = sphere_grid(MAP_W, MAP_H)
	base = fbm3(x, y, z, 1501, 6, 2.0)
	surface = ramp(base, [(0, (20, 40, 44)), (0.42, (22, 30, 26)), (0.5, (28, 66, 40)), (0.75, (40, 84, 50)), (1, (82, 88, 88))])
	ridges = ridged3(x, y, z, 1502, 4, 3.0)
	surface = lerp_rgb(surface, (76, 80, 84), np.clip(ridges, 0, 1) * 0.8)
	storm = clouds(x, y, z, 1503, 0.85, lat, banding=1.5)
	deep = fbm3(x, y, z, 1504, 3, 5.0)
	cloud_rgb = ramp(deep, [(0, (88, 96, 100)), (1, (168, 176, 178))])
	surface = lerp_rgb(surface, cloud_rgb, storm * 0.92)
	return to_image(surface)


def nebula():
	"""A faint band of violet and blue gas with darker lanes; alpha fades to nothing at both edges."""
	w, h = 512, 64
	u = (np.arange(w) + 0.5) / w
	v = (np.arange(h) + 0.5) / h
	U, V = np.meshgrid(u, v)
	ang = U * 2 * math.pi
	# A ring in noise space, so the band wraps without a seam.
	x = np.cos(ang) * 3.0
	z = np.sin(ang) * 3.0
	y = (V - 0.5) * 1.6
	gas = fbm3(x, y, z, 1601, 6, 1.5)
	wisps = fbm3(x * 2.1, y * 2.6, z * 2.1, 1602, 4, 2.0)
	across = np.exp(-((V - 0.5) / 0.24) ** 2)
	lanes = smoothstep(0.52, 0.75, fbm3(x * 1.4, y * 3.0, z * 1.4, 1603, 4, 2.0))
	alpha = np.clip(across * (0.35 + gas * 0.9) * (1 - lanes * 0.55), 0, 1) * 255
	colour = ramp(wisps, [(0, (64, 80, 160)), (0.45, (110, 82, 176)), (0.75, (168, 104, 176)), (1, (214, 170, 210))])
	return to_image(colour, alpha)


# ---------------------------------------------------------------- box layouts

class Box:
	"""One model cube: texOffs (u, v) and size (w, h, d)."""

	def __init__(self, u, v, w, h, d):
		self.u, self.v, self.w, self.h, self.d = u, v, w, h, d

	def faces(self):
		"""Face rects. top/bottom: x across (left to right), y from the nose back; left: x from the nose back;
		right: x from the tail forward; rear: x left to right; front: x right to left; sides y from the top down."""
		u, v, w, h, d = self.u, self.v, self.w, self.h, self.d
		return {
			"top": (u + d, v, w, d),
			"bottom": (u + d + w, v, w, d),
			"left": (u, v + d, d, h),
			"rear": (u + d, v + d, w, h),
			"right": (u + d + w, v + d, d, h),
			"front": (u + 2 * d + w, v + d, w, h),
		}

	def footprint(self):
		return self.u, self.v, self.u + 2 * (self.w + self.d), self.v + self.d + self.h


def check_layout(name, boxes, tex_w, tex_h):
	"""Refuses overlapping or out-of-range boxes, so a texOffs typo shows here, not in game."""
	taken = {}
	for key, box in boxes.items():
		x0, y0, x1, y1 = box.footprint()
		if x1 > tex_w or y1 > tex_h:
			raise SystemExit(f"{name}: {key} runs off the {tex_w}x{tex_h} texture")
		for other, (a0, b0, a1, b1) in taken.items():
			if x0 < a1 and a0 < x1 and y0 < b1 and b0 < y1:
				raise SystemExit(f"{name}: {key} overlaps {other}")
		taken[key] = (x0, y0, x1, y1)


class Painter:
	"""Draws into one face rect in face-local coordinates (0, 0 is the rect's top-left)."""

	def __init__(self, img, rect):
		self.img = img
		self.x, self.y, self.w, self.h = rect

	def px(self, x, y, rgb, a=255):
		if 0 <= x < self.w and 0 <= y < self.h:
			self.img.putpixel((self.x + x, self.y + y), (*rgb[:3], a))

	def get(self, x, y):
		return self.img.getpixel((self.x + x, self.y + y))

	def fill(self, rgb, a=255):
		for y in range(self.h):
			for x in range(self.w):
				self.px(x, y, rgb, a)

	def rect(self, x0, y0, x1, y1, rgb, a=255):
		for y in range(max(0, y0), min(self.h, y1 + 1)):
			for x in range(max(0, x0), min(self.w, x1 + 1)):
				self.px(x, y, rgb, a)

	def tint(self, x, y, rgb, t):
		if 0 <= x < self.w and 0 <= y < self.h:
			r, g, b, a = self.get(x, y)
			self.px(x, y, mix((r, g, b), rgb, t), a)


def faces(img, box, *names):
	rects = box.faces()
	return [Painter(img, rects[n]) for n in (names or rects.keys())]


def face(img, box, name):
	return Painter(img, box.faces()[name])


def canvas(w, h):
	return Image.new("RGBA", (w, h), (0, 0, 0, 0))


# ---------------------------------------------------------------- materials

def plates(p, base, seed, sx=6, sy=0, grain=4, seam=None, depth=0.45, vary=0.06):
	"""Hull panels: grain noise, a tone per panel, darker seams every sx columns and sy rows."""
	rnd = random.Random(seed)
	seam = seam or shade(base, 0.62)
	for y in range(p.h):
		for x in range(p.w):
			panel = hash01(seed, x // max(1, sx or 99), y // max(1, sy or 99))
			c = shade(base, 1 - vary + panel * vary * 2)
			p.px(x, y, noisy(c, rnd, grain))
	if sx:
		for x in range(sx, p.w, sx):
			for y in range(p.h):
				p.tint(x, y, seam, depth)
	if sy:
		for y in range(sy, p.h, sy):
			for x in range(p.w):
				p.tint(x, y, seam, depth)


def lacquer(p, base, seed, highlight=None, sx=5):
	"""A glossy dark finish: smooth, with faint panel lines and a soft sheen toward the top."""
	rnd = random.Random(seed)
	hi = highlight or shade(base, 1.6)
	for y in range(p.h):
		for x in range(p.w):
			sheen = max(0.0, 1 - y / max(1, p.h)) * 0.25
			c = mix(noisy(base, rnd, 2), hi, sheen)
			p.px(x, y, c)
	if sx:
		for x in range(sx, p.w, sx):
			for y in range(p.h):
				p.tint(x, y, shade(base, 0.55), 0.5)


def grime(p, seed, rgb=(40, 36, 32), amount=0.35, streaks=True):
	"""Soot and streaks: scattered spots, some running down."""
	rnd = random.Random(seed)
	for _ in range(max(1, p.w * p.h // 30)):
		x, y = rnd.randrange(p.w), rnd.randrange(p.h)
		p.tint(x, y, rgb, amount * rnd.uniform(0.4, 1.0))
	if streaks and p.h > 3:
		for _ in range(max(1, p.w // 5)):
			x = rnd.randrange(p.w)
			y0 = rnd.randrange(max(1, p.h // 2))
			for y in range(y0, min(p.h, y0 + rnd.randrange(2, p.h))):
				p.tint(x, y, rgb, amount * 0.6)


def rivets(p, rgb, step=3, inset=1, seed=0):
	for x in range(inset, p.w - inset, step):
		for y in (inset, p.h - 1 - inset):
			if hash01(seed, x, y) > 0.25:
				p.tint(x, y, rgb, 0.6)


def hazard(p, a, b, x0, y0, x1, y1, period=4):
	"""Diagonal stripes in a rect."""
	for y in range(y0, y1 + 1):
		for x in range(x0, x1 + 1):
			p.px(x, y, a if ((x + y) // (period // 2)) % 2 == 0 else b)


def glow_square(p, cx, cy, radius, inner, outer, falloff=1.0):
	"""A soft square glow centred on (cx, cy) for an emissive texture: bright inner fading to outer, then nothing."""
	for y in range(p.h):
		for x in range(p.w):
			d = max(abs(x + 0.5 - cx), abs(y + 0.5 - cy)) / max(0.5, radius)
			if d <= 1:
				t = min(1.0, d ** falloff)
				c = mix(inner, outer, t)
				p.px(x, y, c, clamp(255 * (1 - 0.35 * t)))


# ---------------------------------------------------------------- the Hymn interceptor

# Keep in step with StarfighterModel#createHymnLayer.
HYMN_BOXES = {
	"body": Box(0, 0, 4, 4, 20),
	"nose": Box(48, 0, 2, 2, 5),
	"canopy": Box(48, 8, 3, 2, 6),
	"engine": Box(66, 0, 6, 6, 4),
	"fin": Box(66, 10, 1, 4, 6),
	"wing": Box(0, 24, 10, 1, 6),
	"blade": Box(32, 24, 1, 4, 8),
	"gun": Box(80, 10, 1, 1, 12),
}
LACQUER = (30, 28, 38)
LACQUER_HI = (66, 60, 82)
VIOLET = (138, 82, 214)
VIOLET_DARK = (74, 42, 122)
VIOLET_GLASS = (52, 30, 84)
VIOLET_GLASS_HI = (150, 120, 206)
GUNMETAL = (72, 72, 82)
GUNMETAL_DARK = (44, 44, 52)


def hymn_interceptor():
	img = canvas(128, 64)
	b = HYMN_BOXES
	for i, p in enumerate(faces(img, b["body"])):
		lacquer(p, LACQUER, 2100 + i, LACQUER_HI)
	top = face(img, b["body"], "top")
	for y in range(2, top.h - 1):
		top.px(0, y, VIOLET_DARK)
		top.px(top.w - 1, y, VIOLET_DARK)
	for y in range(1, 4):
		top.px(1 + (y % 2), y, VIOLET)
	for name in ("left", "right"):
		p = face(img, b["body"], name)
		for x in range(p.w):
			p.px(x, 1, VIOLET_DARK)
		# Three violet dashes forward of the wing root.
		for k in range(3):
			x = 2 + k * 2 if name == "left" else p.w - 3 - k * 2
			p.px(x, 2, VIOLET)
	face(img, b["body"], "bottom").fill(shade(LACQUER, 0.8))
	grime(face(img, b["body"], "bottom"), 2110, (14, 12, 18), 0.3, streaks=False)

	for i, p in enumerate(faces(img, b["nose"])):
		lacquer(p, LACQUER, 2120 + i, LACQUER_HI, sx=0)
	front = face(img, b["nose"], "front")
	front.fill(VIOLET_DARK)
	front.px(0, 0, VIOLET)

	for i, p in enumerate(faces(img, b["canopy"])):
		p.fill(VIOLET_GLASS)
	ctop = face(img, b["canopy"], "top")
	for y in range(ctop.h):
		ctop.px(min(ctop.w - 1, y // 2), y, VIOLET_GLASS_HI)
	for name in ("left", "right", "front", "rear"):
		p = face(img, b["canopy"], name)
		for x in range(p.w):
			p.px(x, p.h - 1, LACQUER)
		p.px(p.w // 2, 0, VIOLET_GLASS_HI)

	for i, p in enumerate(faces(img, b["engine"], "top", "bottom", "left", "right")):
		for y in range(p.h):
			for x in range(p.w):
				ring = (y if i < 2 else x) % 2 == 0
				p.px(x, y, GUNMETAL if ring else GUNMETAL_DARK)
	rear = face(img, b["engine"], "rear")
	rear.fill(GUNMETAL)
	rear.rect(1, 1, 4, 4, (18, 16, 24))
	rear.rect(2, 2, 3, 3, VIOLET_DARK)
	face(img, b["engine"], "front").fill(GUNMETAL_DARK)

	for i, p in enumerate(faces(img, b["fin"])):
		lacquer(p, LACQUER, 2140 + i, LACQUER_HI, sx=0)
	face(img, b["fin"], "top").fill(VIOLET_DARK)
	for name in ("left", "right"):
		p = face(img, b["fin"], name)
		for x in range(p.w):
			p.px(x, 0, VIOLET_DARK)

	wing_top = face(img, b["wing"], "top")
	lacquer(wing_top, LACQUER, 2150, LACQUER_HI, sx=3)
	for x in range(wing_top.w):
		wing_top.px(x, 0, VIOLET_DARK)
	for y in range(wing_top.h):
		wing_top.px(wing_top.w - 1, y, VIOLET_DARK)
	wing_bottom = face(img, b["wing"], "bottom")
	lacquer(wing_bottom, shade(LACQUER, 0.8), 2151, LACQUER, sx=3)
	for x in range(wing_bottom.w):
		wing_bottom.px(x, 0, VIOLET_DARK)
	for name in ("left", "right", "rear"):
		face(img, b["wing"], name).fill(LACQUER)
	face(img, b["wing"], "front").fill(VIOLET_DARK)

	for i, p in enumerate(faces(img, b["blade"])):
		lacquer(p, LACQUER, 2160 + i, LACQUER_HI, sx=0)
	inner = face(img, b["blade"], "left")
	outer = face(img, b["blade"], "right")
	for x in range(inner.w):
		inner.px(x, 0, VIOLET_DARK)
		outer.px(x, 0, VIOLET)
		outer.px(x, outer.h - 1, VIOLET_DARK)
	for y in range(inner.h):
		inner.px(0, y, VIOLET_DARK)
		outer.px(outer.w - 1, y, VIOLET)
	face(img, b["blade"], "top").fill(VIOLET)
	face(img, b["blade"], "front").fill(VIOLET)

	for p in faces(img, b["gun"]):
		p.fill(GUNMETAL)
	face(img, b["gun"], "top").rect(0, 0, 0, 2, GUNMETAL_DARK)
	face(img, b["gun"], "front").fill((14, 12, 18))
	return img


def hymn_interceptor_glow():
	img = canvas(128, 64)
	b = HYMN_BOXES
	glow_square(face(img, b["engine"], "rear"), 3, 3, 3.0, (244, 232, 255), (150, 84, 240), 1.4)
	front = face(img, b["nose"], "front")
	front.px(0, 0, (200, 160, 255), 200)
	# The blades' singing edges: a faint violet line, brightest at the leading tip.
	top = face(img, b["blade"], "top")
	for y in range(top.h):
		top.px(0, y, (150, 96, 240), clamp(200 - y * 18))
	face(img, b["blade"], "front").fill((190, 140, 255), 220)
	return img


# ---------------------------------------------------------------- the kajidic raider

# Keep in step with StarfighterModel#createRaiderLayer.
RAIDER_BOXES = {
	"body": Box(0, 0, 10, 7, 18),
	"nose": Box(56, 0, 7, 5, 4),
	"canopy": Box(56, 9, 5, 2, 6),
	"wing": Box(0, 25, 6, 2, 10),
	"pod": Box(32, 25, 3, 3, 12),
	"engine": Box(78, 0, 4, 4, 5),
	"rack": Box(62, 25, 6, 2, 8),
	"patch": Box(78, 9, 1, 4, 7),
}
RAIDER_METAL = (98, 96, 90)
RAIDER_DARK = (62, 60, 56)
OCHRE = (184, 118, 54)
RUST = (126, 70, 40)
PRIMER = (136, 66, 50)
PATCH_LIGHT = (150, 148, 140)
SOOT = (34, 32, 30)
AMBER_GLASS = (88, 62, 30)
AMBER_GLASS_HI = (204, 160, 92)


def patches(p, seed, count):
	"""Welded patch plates of other metals, with rivets round their edges."""
	rnd = random.Random(seed)
	for _ in range(count):
		w = rnd.randint(2, max(2, min(5, p.w - 1)))
		h = rnd.randint(2, max(2, min(4, p.h - 1)))
		x0 = rnd.randrange(0, max(1, p.w - w))
		y0 = rnd.randrange(0, max(1, p.h - h))
		colour = rnd.choice((PATCH_LIGHT, PRIMER, shade(RAIDER_METAL, 1.15)))
		for y in range(y0, y0 + h):
			for x in range(x0, x0 + w):
				edge = x in (x0, x0 + w - 1) or y in (y0, y0 + h - 1)
				c = noisy(colour, rnd, 5)
				p.px(x, y, shade(c, 0.78) if edge and (x + y) % 2 == 0 else c)


def kajidic_raider():
	img = canvas(128, 64)
	b = RAIDER_BOXES
	for i, p in enumerate(faces(img, b["body"])):
		plates(p, RAIDER_METAL, 2200 + i, sx=6, sy=6 if p.h > 8 else 0, grain=6)
	for name in ("left", "right"):
		p = face(img, b["body"], name)
		for x in range(p.w):
			p.px(x, 2, OCHRE)
			p.px(x, 3, shade(OCHRE, 0.85))
		patches(p, 2210 if name == "left" else 2211, 2)
		grime(p, 2212 if name == "left" else 2213, RUST, 0.4)
		# Scorch toward the engines.
		for y in range(p.h):
			for k in range(4):
				x = p.w - 1 - k if name == "left" else k
				p.tint(x, y, SOOT, 0.45 - k * 0.1)
	top = face(img, b["body"], "top")
	for y in range(1, 5):
		for x in range(top.w):
			if abs(x - (top.w - 1) / 2) <= y - 0.5 and abs(x - (top.w - 1) / 2) >= y - 2.5:
				top.px(x, y, OCHRE)
	patches(top, 2214, 3)
	grime(top, 2215, RUST, 0.3)
	bottom = face(img, b["body"], "bottom")
	plates(bottom, RAIDER_DARK, 2216, sx=5, sy=6)
	grime(bottom, 2217, SOOT, 0.4)

	for i, p in enumerate(faces(img, b["nose"])):
		plates(p, RAIDER_METAL, 2220 + i, sx=0, grain=6)
	nose_top = face(img, b["nose"], "top")
	for x in range(nose_top.w):
		nose_top.px(x, nose_top.h - 1, OCHRE)
	front = face(img, b["nose"], "front")
	front.rect(1, 1, 5, 3, (26, 24, 22))
	for x in range(1, 6, 2):
		front.rect(x, 1, x, 3, (52, 50, 46))
	for x in range(front.w):
		front.px(x, 0, OCHRE)
		front.px(x, front.h - 1, OCHRE)

	for p in faces(img, b["canopy"]):
		p.fill(AMBER_GLASS)
	ctop = face(img, b["canopy"], "top")
	for y in range(ctop.h):
		ctop.px(1 + (y // 3), y, AMBER_GLASS_HI)
	for name in ("left", "right", "front", "rear"):
		p = face(img, b["canopy"], name)
		for x in range(p.w):
			p.px(x, p.h - 1, RAIDER_DARK)
		if p.w > 3:
			p.px(p.w // 2, 0, RAIDER_DARK)

	for i, p in enumerate(faces(img, b["wing"])):
		plates(p, RAIDER_METAL, 2230 + i, sx=0, sy=0, grain=6)
	wtop = face(img, b["wing"], "top")
	hazard(wtop, OCHRE, (40, 38, 34), wtop.w - 2, 0, wtop.w - 1, wtop.h - 1, 4)
	patches(wtop, 2236, 1)
	grime(wtop, 2237, RUST, 0.3)
	face(img, b["wing"], "right").fill(OCHRE)

	for i, p in enumerate(faces(img, b["pod"])):
		plates(p, RAIDER_DARK, 2240 + i, sx=4, grain=5)
	muzzle = face(img, b["pod"], "front")
	muzzle.fill(shade(RAIDER_METAL, 1.1))
	muzzle.px(1, 1, (12, 12, 12))
	for name in ("left", "right"):
		p = face(img, b["pod"], name)
		x = 1 if name == "left" else p.w - 2
		p.rect(x, 0, x, p.h - 1, OCHRE)

	for i, p in enumerate(faces(img, b["engine"])):
		plates(p, shade(RAIDER_DARK, 0.85), 2250 + i, sx=2, grain=5)
		grime(p, 2255 + i, SOOT, 0.5, streaks=False)
	nozzle = face(img, b["engine"], "rear")
	nozzle.fill(RAIDER_DARK)
	nozzle.rect(1, 1, 2, 2, (20, 16, 14))

	for i, p in enumerate(faces(img, b["rack"])):
		plates(p, RAIDER_DARK, 2260 + i, sx=0, grain=4)
	under = face(img, b["rack"], "bottom")
	for x in (1, 4):
		under.rect(x, 0, x, under.h - 2, (180, 178, 170))
		under.px(x, 0, (196, 48, 40))

	for i, p in enumerate(faces(img, b["patch"])):
		p.fill(PATCH_LIGHT)
	outer = face(img, b["patch"], "right")
	rivets(outer, RAIDER_DARK, 2, 0, 2270)
	grime(outer, 2271, RUST, 0.35)
	return img


def kajidic_raider_glow():
	img = canvas(128, 64)
	glow_square(face(img, RAIDER_BOXES["engine"], "rear"), 2, 2, 2.0, (255, 226, 170), (255, 128, 44), 1.2)
	return img


# ---------------------------------------------------------------- the Hask fighter

# Keep in step with StarfighterModel#createHaskLayer.
HASK_BOXES = {
	"body": Box(0, 0, 5, 5, 20),
	"nose": Box(50, 0, 3, 3, 4),
	"canopy": Box(50, 7, 4, 2, 6),
	"engine": Box(70, 11, 4, 4, 3),
	"fin": Box(106, 0, 1, 5, 6),
	"wing": Box(0, 25, 10, 1, 6),
	"tip": Box(32, 25, 1, 4, 5),
	"gun": Box(84, 0, 1, 1, 10),
}
CLAN_IRON = (76, 82, 92)
IRON_DARK = (46, 50, 58)
EMBER = (194, 72, 40)
EMBER_DARK = (122, 42, 28)
BONE = (214, 204, 180)
CLAN_GLASS = (34, 58, 66)
CLAN_GLASS_HI = (120, 172, 180)


def hask_fighter():
	img = canvas(128, 64)
	b = HASK_BOXES
	for i, p in enumerate(faces(img, b["body"])):
		plates(p, CLAN_IRON, 2300 + i, sx=5, grain=4)
	top = face(img, b["body"], "top")
	for y in range(top.h):
		top.px(2, y, EMBER)
	for k in range(2):
		y = 2 + k * 3
		top.px(1, y, BONE)
		top.px(3, y, BONE)
		top.px(0, y + 1, BONE)
		top.px(4, y + 1, BONE)
	for name in ("left", "right"):
		p = face(img, b["body"], name)
		for x in range(p.w):
			p.px(x, 1, BONE)
		for k in range(6):
			x = k if name == "left" else p.w - 1 - k
			p.px(x, 2, EMBER)
			p.px(x, 3, EMBER_DARK)
		grime(p, 2310 if name == "left" else 2311, (30, 30, 34), 0.25)
	plates(face(img, b["body"], "bottom"), IRON_DARK, 2312, sx=5)

	for p in faces(img, b["nose"]):
		plates(p, EMBER, 2320, sx=0, grain=5)
	front = face(img, b["nose"], "front")
	front.fill(BONE)
	front.px(1, 1, IRON_DARK)

	for p in faces(img, b["canopy"]):
		p.fill(CLAN_GLASS)
	ctop = face(img, b["canopy"], "top")
	for y in range(ctop.h):
		ctop.px(1, y, CLAN_GLASS_HI if y % 3 else CLAN_GLASS)
	for name in ("left", "right", "front", "rear"):
		p = face(img, b["canopy"], name)
		for x in range(p.w):
			p.px(x, p.h - 1, IRON_DARK)

	for i, p in enumerate(faces(img, b["engine"])):
		for y in range(p.h):
			for x in range(p.w):
				p.px(x, y, IRON_DARK if (x + y) % 3 else CLAN_IRON)
	nozzle = face(img, b["engine"], "rear")
	nozzle.fill(IRON_DARK)
	nozzle.rect(1, 1, 2, 2, (18, 16, 16))

	for i, p in enumerate(faces(img, b["fin"])):
		plates(p, EMBER, 2330 + i, sx=0, grain=5)
	face(img, b["fin"], "top").fill(BONE)
	for name in ("left", "right"):
		p = face(img, b["fin"], name)
		for x in range(p.w):
			p.px(x, 0, BONE)

	wtop = face(img, b["wing"], "top")
	plates(wtop, CLAN_IRON, 2340, sx=3, grain=4)
	for x in range(wtop.w):
		wtop.px(x, 0, EMBER)
		wtop.px(x, 1, EMBER_DARK)
	for y in range(wtop.h):
		wtop.px(wtop.w - 1, y, BONE)
	plates(face(img, b["wing"], "bottom"), IRON_DARK, 2341, sx=3)
	for name in ("left", "right", "rear"):
		face(img, b["wing"], name).fill(CLAN_IRON)
	face(img, b["wing"], "front").fill(EMBER)

	for p in faces(img, b["tip"]):
		plates(p, EMBER, 2350, sx=0, grain=5)
	for name in ("left", "right"):
		p = face(img, b["tip"], name)
		for x in range(p.w):
			p.px(x, p.h - 1, BONE)

	for p in faces(img, b["gun"]):
		p.fill(IRON_DARK)
	face(img, b["gun"], "front").fill((14, 14, 16))
	return img


def hask_fighter_glow():
	img = canvas(128, 64)
	glow_square(face(img, HASK_BOXES["engine"], "rear"), 2, 2, 2.0, (255, 236, 184), (255, 170, 70), 1.2)
	return img


# ---------------------------------------------------------------- the blockade gunship

# Keep in step with GunshipModel#createBodyLayer.
GUNSHIP_BOXES = {
	"hull_fore": Box(0, 0, 32, 22, 40),
	"prow": Box(144, 0, 24, 16, 8),
	"keel_fore": Box(0, 62, 16, 2, 36),
	"spine_fore": Box(104, 62, 6, 4, 36),
	"turret": Box(208, 0, 4, 3, 4),
	"barrel": Box(208, 8, 2, 2, 7),
	"door": Box(144, 24, 6, 1, 16),
	"hull_aft": Box(0, 102, 32, 22, 40),
	"engine_block": Box(144, 126, 28, 18, 8),
	"keel_aft": Box(0, 164, 16, 2, 36),
	"spine_aft": Box(104, 164, 6, 4, 36),
	"bridge": Box(188, 62, 12, 5, 14),
	"gen_base": Box(188, 81, 10, 2, 10),
	"gen_column": Box(228, 81, 4, 4, 4),
	"gen_core": Box(188, 93, 6, 2, 6),
	"gen_cap": Box(212, 93, 7, 2, 7),
	"gen_emitter": Box(240, 93, 1, 1, 1),
}
G_HULL = (68, 66, 76)
G_HULL_DARK = (44, 43, 50)
G_HULL_LIGHT = (98, 96, 108)
G_VIOLET = (118, 68, 190)
G_VIOLET_DARK = (68, 38, 108)
G_WINDOW = (46, 30, 76)
G_GRILLE = (24, 24, 30)
CHAR = (30, 28, 30)
# Running lights along the hull sides: every LIGHT_STEP columns from LIGHT_FIRST, on row LIGHT_ROW.
LIGHT_ROW, LIGHT_FIRST, LIGHT_STEP = 17, 4, 10


def gunship_side(p, seed):
	"""A slab side: big panels, a violet band high up, vent grilles, housings for the running lights."""
	plates(p, G_HULL, seed, sx=8, sy=7, grain=4, depth=0.5)
	for x in range(p.w):
		p.px(x, 3, G_VIOLET_DARK)
		p.px(x, 4, G_VIOLET)
	rnd = random.Random(seed + 1)
	for x0 in range(2, p.w - 4, 10):
		if rnd.random() < 0.7:
			for y in range(9, 13):
				for x in range(x0, x0 + 4):
					p.px(x, y, G_GRILLE if (y % 2 == 0) else G_HULL_DARK)
	for x in range(LIGHT_FIRST, p.w, LIGHT_STEP):
		p.px(x, LIGHT_ROW, G_HULL_LIGHT)
	grime(p, seed + 2, (26, 24, 30), 0.25)


def charred(p, seed):
	"""The break: dark torn decks and beams, a few glowing red edges."""
	rnd = random.Random(seed)
	for y in range(p.h):
		for x in range(p.w):
			c = noisy(CHAR, rnd, 6)
			if x % 6 == 0 or y % 7 == 0:
				c = shade(G_HULL_DARK, 0.9)
			if rnd.random() < 0.03:
				c = (120, 44, 26)
			p.px(x, y, c)


def blockade_gunship():
	img = canvas(256, 256)
	b = GUNSHIP_BOXES
	for half, seed in (("hull_fore", 2400), ("hull_aft", 2450)):
		box = b[half]
		gunship_side(face(img, box, "left"), seed)
		gunship_side(face(img, box, "right"), seed + 10)
		top = face(img, box, "top")
		plates(top, G_HULL, seed + 20, sx=8, sy=8, grain=4)
		top.rect(13, 0, 18, top.h - 1, G_HULL_DARK)
		rnd = random.Random(seed + 21)
		for _ in range(3):
			x0, y0 = rnd.randrange(2, 8), rnd.randrange(2, top.h - 6)
			for k in range(4):
				top.tint(x0 + k, y0, G_GRILLE, 0.5)
				top.tint(x0 + k, y0 + 3, G_GRILLE, 0.5)
				top.tint(x0, y0 + k, G_GRILLE, 0.5)
				top.tint(x0 + 3, y0 + k, G_GRILLE, 0.5)
		bottom = face(img, box, "bottom")
		plates(bottom, G_HULL_DARK, seed + 30, sx=8, sy=8, grain=3)
		grime(bottom, seed + 31, (20, 18, 22), 0.3, streaks=False)
	# The launch bay in the fore belly (under the doors): model x -6..6, z 4..20.
	bay = face(img, b["hull_fore"], "bottom")
	bay.rect(10, 19, 21, 35, (20, 20, 24))
	for y in range(19, 36):
		bay.px(9, y, G_VIOLET_DARK)
		bay.px(22, y, G_VIOLET_DARK)
	charred(face(img, b["hull_fore"], "rear"), 2460)
	charred(face(img, b["hull_aft"], "front"), 2461)
	plates(face(img, b["hull_fore"], "front"), G_HULL, 2462, sx=8, sy=7)
	plates(face(img, b["hull_aft"], "rear"), G_HULL_DARK, 2463, sx=8, sy=7)

	for i, p in enumerate(faces(img, b["prow"])):
		plates(p, G_HULL, 2470 + i, sx=6, sy=0, grain=4)
	prow = face(img, b["prow"], "front")
	plates(prow, G_HULL_LIGHT, 2477, sx=6, sy=8, grain=4)
	prow.rect(3, 6, 20, 7, G_GRILLE)
	for x in range(2, 22):
		prow.px(x, 5, G_VIOLET_DARK)
		prow.px(x, 8, G_VIOLET_DARK)
	ptop = face(img, b["prow"], "top")
	for y in range(ptop.h):
		for dx in (y, ptop.w - 1 - y):
			if 0 <= dx < ptop.w:
				ptop.px(dx, y, G_VIOLET)

	for keel, seed in (("keel_fore", 2480), ("keel_aft", 2485)):
		for i, p in enumerate(faces(img, b[keel])):
			plates(p, G_HULL_DARK, seed + i, sx=4, grain=3)
	for spine, seed in (("spine_fore", 2490), ("spine_aft", 2495)):
		for i, p in enumerate(faces(img, b[spine])):
			plates(p, G_HULL_LIGHT, seed + i, sx=6, grain=4)
		stop = face(img, b[spine], "top")
		for y in range(stop.h):
			stop.px(2, y, G_VIOLET)
			stop.px(3, y, G_VIOLET_DARK)

	for i, p in enumerate(faces(img, b["turret"])):
		plates(p, G_HULL_DARK, 2500 + i, sx=0, grain=3)
	ttop = face(img, b["turret"], "top")
	for k in range(4):
		ttop.px(k, 0, G_VIOLET)
		ttop.px(k, 3, G_VIOLET)
		ttop.px(0, k, G_VIOLET)
		ttop.px(3, k, G_VIOLET)
	for p in faces(img, b["barrel"]):
		p.fill(G_HULL_DARK)
	face(img, b["barrel"], "top").rect(0, 0, 1, 0, G_HULL_LIGHT)
	face(img, b["barrel"], "front").fill((10, 10, 12))

	for i, p in enumerate(faces(img, b["door"])):
		plates(p, G_HULL_DARK, 2510 + i, sx=0, sy=4, grain=3)
	under = face(img, b["door"], "bottom")
	hazard(under, G_VIOLET, G_GRILLE, 0, 0, 1, under.h - 1, 4)

	for i, p in enumerate(faces(img, b["engine_block"])):
		plates(p, G_HULL_DARK, 2520 + i, sx=7, grain=3)
	nozzles = face(img, b["engine_block"], "rear")
	nozzles.fill(G_HULL_DARK)
	for nx in (1, 10, 19):
		for ny in (1, 10):
			nozzles.rect(nx, ny, nx + 7, ny + 6, G_HULL_LIGHT)
			nozzles.rect(nx + 1, ny + 1, nx + 6, ny + 5, (16, 16, 22))

	for i, p in enumerate(faces(img, b["bridge"])):
		plates(p, G_HULL, 2530 + i, sx=5, grain=4)
	for name in ("front", "left", "right", "rear"):
		p = face(img, b["bridge"], name)
		for x in range(1, p.w - 1):
			p.px(x, 1, G_WINDOW if x % 2 else G_HULL_DARK)
			p.px(x, 2, G_WINDOW)
	face(img, b["bridge"], "top").px(6, 7, G_HULL_LIGHT)

	for i, p in enumerate(faces(img, b["gen_base"])):
		plates(p, G_HULL_DARK, 2540 + i, sx=0, grain=3)
	gtop = face(img, b["gen_base"], "top")
	for a in range(12):
		ang = a / 12 * math.tau
		gtop.px(int(round(4.5 + math.cos(ang) * 3.6)), int(round(4.5 + math.sin(ang) * 3.6)), G_HULL_LIGHT)
	for name in ("left", "right", "front", "rear"):
		p = face(img, b["gen_base"], name)
		for x in range(p.w):
			p.px(x, 0, G_VIOLET_DARK)
	for p in faces(img, b["gen_column"]):
		for y in range(p.h):
			for x in range(p.w):
				p.px(x, y, G_HULL_LIGHT if x % 2 == 0 else G_HULL_DARK)
	for p in faces(img, b["gen_core"]):
		p.fill((58, 32, 92))
	for i, p in enumerate(faces(img, b["gen_cap"])):
		plates(p, G_HULL_LIGHT, 2550 + i, sx=0, grain=3)
	ctop = face(img, b["gen_cap"], "top")
	for r in (1, 3):
		for k in range(r, 7 - r):
			ctop.px(k, r, G_HULL_DARK)
			ctop.px(k, 6 - r, G_HULL_DARK)
			ctop.px(r, k, G_HULL_DARK)
			ctop.px(6 - r, k, G_HULL_DARK)
	for p in faces(img, b["gen_emitter"]):
		p.fill((200, 198, 214))
	return img


def blockade_gunship_glow():
	"""The engines, the running lights, the prow's sensor slit and the bridge windows."""
	img = canvas(256, 256)
	b = GUNSHIP_BOXES
	nozzles = face(img, b["engine_block"], "rear")
	for nx in (1, 10, 19):
		for ny in (1, 10):
			for y in range(ny + 1, ny + 6):
				for x in range(nx + 1, nx + 7):
					d = max(abs(x - (nx + 3.5)) / 3.0, abs(y - (ny + 3.0)) / 2.5)
					nozzles.px(x, y, mix((226, 214, 255), (108, 80, 226), min(1.0, d)), clamp(255 * (1 - 0.3 * min(1.0, d))))
	for half in ("hull_fore", "hull_aft"):
		for name in ("left", "right"):
			p = face(img, b[half], name)
			for x in range(LIGHT_FIRST, p.w, LIGHT_STEP):
				p.px(x, LIGHT_ROW, (255, 84, 120))
	prow = face(img, b["prow"], "front")
	for x in range(3, 21):
		prow.px(x, 6, (150, 100, 230), 150)
		prow.px(x, 7, (150, 100, 230), 110)
	for name in ("front", "left", "right", "rear"):
		p = face(img, b["bridge"], name)
		for x in range(1, p.w - 1):
			if hash01(2560, x, len(name)) > 0.45:
				p.px(x, 2, (190, 170, 240), 170)
	return img


def blockade_gunship_core():
	"""The shield generator's core and emitter (pulsed by the renderer)."""
	img = canvas(256, 256)
	b = GUNSHIP_BOXES
	for name, p in zip(("top", "bottom", "left", "rear", "right", "front"), faces(img, b["gen_core"])):
		for y in range(p.h):
			for x in range(p.w):
				edge = name in ("top", "bottom") and (x in (0, p.w - 1) or y in (0, p.h - 1))
				p.px(x, y, (150, 110, 240) if edge else (214, 190, 255))
		if name not in ("top", "bottom"):
			for x in range(p.w):
				p.px(x, 0, (246, 238, 255))
	for p in faces(img, b["gen_emitter"]):
		p.fill((250, 244, 255))
	return img


# ---------------------------------------------------------------- the concussion missile

# Keep in step with ShipMissileRenderer.MissileModel#createBodyLayer.
MISSILE_BOXES = {
	"body": Box(0, 0, 2, 2, 8),
	"nose": Box(20, 0, 1, 1, 2),
	"fin_h": Box(0, 10, 4, 1, 2),
	"fin_v": Box(12, 10, 1, 4, 2),
}
M_BODY = (92, 94, 100)
M_BAND = (214, 180, 60)


def ship_missile():
	img = canvas(32, 32)
	b = MISSILE_BOXES
	rnd = random.Random(2600)
	for p in faces(img, b["body"]):
		for y in range(p.h):
			for x in range(p.w):
				p.px(x, y, noisy(M_BODY, rnd, 4))
	for name in ("top", "bottom"):
		p = face(img, b["body"], name)
		for x in range(p.w):
			p.px(x, 1, M_BAND)
			p.px(x, 2, M_BAND)
	left = face(img, b["body"], "left")
	right = face(img, b["body"], "right")
	for y in range(left.h):
		for x in (1, 2):
			left.px(x, y, M_BAND)
			right.px(right.w - 1 - x, y, M_BAND)
	face(img, b["body"], "rear").fill((26, 26, 30))
	for p in faces(img, b["nose"]):
		p.fill((222, 222, 216))
	face(img, b["nose"], "front").fill((176, 44, 40))
	for name in ("fin_h", "fin_v"):
		for p in faces(img, b[name]):
			p.fill((58, 60, 66))
	return img


# ---------------------------------------------------------------- the nav buoy

# Keep in step with NavBuoyRenderer.BuoyModel#createBodyLayer.
BUOY_BOXES = {
	"base": Box(0, 0, 8, 4, 8),
	"mast": Box(32, 0, 2, 12, 2),
	"lamp": Box(40, 0, 4, 4, 4),
	"vane": Box(0, 12, 6, 4, 1),
	"collar": Box(0, 20, 6, 1, 6),
}
B_METAL = (64, 66, 70)
B_LIGHT = (126, 130, 134)
B_YELLOW = (222, 178, 52)
B_ORANGE = (232, 122, 44)


def nav_buoy():
	img = canvas(64, 32)
	b = BUOY_BOXES
	for i, p in enumerate(faces(img, b["base"])):
		plates(p, B_METAL, 2700 + i, sx=0, grain=4)
	btop = face(img, b["base"], "top")
	for a in range(10):
		ang = a / 10 * math.tau
		btop.px(int(round(3.5 + math.cos(ang) * 3)), int(round(3.5 + math.sin(ang) * 3)), B_LIGHT)
	for name in ("left", "right", "front", "rear"):
		hazard(face(img, b["base"], name), B_YELLOW, (30, 30, 32), 0, 1, 7, 2, 4)
	for p in faces(img, b["mast"]):
		for y in range(p.h):
			for x in range(p.w):
				p.px(x, y, B_METAL if y % 3 == 0 else B_LIGHT)
	for p in faces(img, b["lamp"]):
		p.fill((206, 210, 204))
		for k in range(p.w):
			p.px(k, 0, B_METAL)
			p.px(k, p.h - 1, B_METAL)
	for i, p in enumerate(faces(img, b["vane"])):
		plates(p, B_LIGHT, 2710 + i, sx=0, grain=4)
	for name in ("front", "rear"):
		p = face(img, b["vane"], name)
		tip = p.w - 1 if name == "rear" else 0
		for y in range(p.h):
			p.px(tip, y, B_ORANGE)
	for p in faces(img, b["collar"]):
		p.fill(B_METAL)
	return img


def nav_buoy_lamp():
	"""The lamp's glass in white: the renderer tints it amber, or green once reached."""
	img = canvas(64, 32)
	for p in faces(img, BUOY_BOXES["lamp"]):
		for y in range(1, p.h - 1):
			for x in range(p.w):
				p.px(x, y, (255, 255, 255))
	return img


# ---------------------------------------------------------------- hull debris (particle)

def hull_debris(index):
	"""A jagged plate fragment, light grey with a darker edge and a seam: the particle tints it."""
	rnd = random.Random(2800 + index)
	img = canvas(8, 8)
	cx, cy = 3.5 + rnd.uniform(-0.5, 0.5), 3.5 + rnd.uniform(-0.5, 0.5)
	corners = []
	for k in range(5 + index % 2):
		ang = k / (5 + index % 2) * math.tau + rnd.uniform(-0.4, 0.4)
		corners.append((cx + math.cos(ang) * rnd.uniform(1.8, 3.6), cy + math.sin(ang) * rnd.uniform(1.8, 3.6)))

	def inside(x, y):
		hit = False
		n = len(corners)
		for i in range(n):
			x1, y1 = corners[i]
			x2, y2 = corners[(i + 1) % n]
			if (y1 > y) != (y2 > y) and x < (x2 - x1) * (y - y1) / (y2 - y1) + x1:
				hit = not hit
		return hit

	px = img.load()
	for y in range(8):
		for x in range(8):
			if inside(x + 0.5, y + 0.5):
				edge = not all(inside(x + 0.5 + dx, y + 0.5 + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
				c = noisy((214, 214, 216), rnd, 10)
				if edge:
					c = shade(c, 0.72)
				if (x + index) % 4 == 0:
					c = shade(c, 0.85)
				px[x, y] = c + (255,)
	return img


# ---------------------------------------------------------------- items

def concussion_missile_icon():
	"""A missile lying corner to corner: white nose, yellow band, grey body, dark fins."""
	img = canvas(16, 16)
	px = img.load()
	rnd = random.Random(2900)
	for t in range(3, 13):
		x, y = t, 15 - t
		for dx, dy in ((0, 0), (1, 0), (0, -1)):
			c = M_BODY
			if t >= 11:
				c = (226, 226, 220)
			elif t in (9, 10):
				c = M_BAND
			c = noisy(c, rnd, 4)
			if (dx, dy) == (1, 0):
				c = shade(c, 0.75)
			px[x + dx, y + dy] = c + (255,)
	px[13, 2] = (176, 44, 40, 255)
	px[14, 1] = (176, 44, 40, 255)
	for x, y in ((2, 11), (3, 12), (4, 14), (5, 13), (1, 12), (3, 14)):
		px[x, y] = (58, 60, 66, 255)
	px[2, 14] = (255, 170, 80, 255)
	px[1, 15] = (255, 120, 40, 200)
	return img


def sealed_cargo_icon():
	"""A sealed crate: blue-grey plating, a sealing band and a lit amber lock."""
	img = canvas(16, 16)
	px = img.load()
	rnd = random.Random(2910)
	for y in range(3, 14):
		for x in range(2, 14):
			c = noisy((92, 104, 118), rnd, 5)
			if x in (2, 13) or y in (3, 13):
				c = shade(c, 0.65)
			elif y == 4:
				c = shade(c, 1.2)
			if x in (7, 8):
				c = (70, 74, 80)
			px[x, y] = c + (255,)
	for y in (7, 8, 9):
		px[7, y] = (226, 160, 60, 255)
		px[8, y] = (255, 196, 96, 255) if y == 8 else (226, 160, 60, 255)
	for x in (4, 11):
		px[x, 6] = (120, 132, 146, 255)
		px[x, 11] = (120, 132, 146, 255)
	return img


def spawn_egg(base, spots, seed):
	rnd = random.Random(seed)
	img = canvas(16, 16)
	px = img.load()
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
			px[x, y] = noisy(c, rnd, 5) + (255,)
	for x, y in ((5, 5), (10, 7), (5, 11), (10, 12), (8, 4), (7, 9)):
		px[x, y] = noisy(spots, rnd, 4) + (255,)
	return img


EGGS = {
	"hymn_interceptor_spawn_egg": (LACQUER_HI, VIOLET),
	"kajidic_raider_spawn_egg": (RAIDER_METAL, OCHRE),
	"hask_fighter_spawn_egg": (CLAN_IRON, EMBER),
}
ITEMS = ("concussion_missile", "sealed_cargo") + tuple(sorted(EGGS))


def item_json(name):
	write_json(f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/{name}"}})
	write_json(f"models/item/{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{name}"}})


# ---------------------------------------------------------------- main

def main():
	check_layout("hymn_interceptor", HYMN_BOXES, 128, 64)
	check_layout("kajidic_raider", RAIDER_BOXES, 128, 64)
	check_layout("hask_fighter", HASK_BOXES, 128, 64)
	check_layout("blockade_gunship", GUNSHIP_BOXES, 256, 256)
	check_layout("ship_missile", MISSILE_BOXES, 32, 32)
	check_layout("nav_buoy", BUOY_BOXES, 64, 32)

	for name, fn in (("planet_home", planet_home), ("planet_selvaren", planet_selvaren), ("planet_varkhast", planet_varkhast),
			("planet_ghessar", planet_ghessar), ("planet_tyrvann", planet_tyrvann), ("nebula", nebula)):
		write_png(f"environment/space/{name}.png", fn())

	write_png("entity/space/hymn_interceptor.png", hymn_interceptor())
	write_png("entity/space/hymn_interceptor_glow.png", hymn_interceptor_glow())
	write_png("entity/space/kajidic_raider.png", kajidic_raider())
	write_png("entity/space/kajidic_raider_glow.png", kajidic_raider_glow())
	write_png("entity/space/hask_fighter.png", hask_fighter())
	write_png("entity/space/hask_fighter_glow.png", hask_fighter_glow())
	write_png("entity/space/blockade_gunship.png", blockade_gunship())
	write_png("entity/space/blockade_gunship_glow.png", blockade_gunship_glow())
	write_png("entity/space/blockade_gunship_core.png", blockade_gunship_core())
	write_png("entity/space/ship_missile.png", ship_missile())
	write_png("entity/space/nav_buoy.png", nav_buoy())
	write_png("entity/space/nav_buoy_lamp.png", nav_buoy_lamp())

	for i in range(4):
		write_png(f"particle/hull_debris_{i}.png", hull_debris(i))
	write_json("particles/hull_debris.json", {"textures": [f"{NS}:hull_debris_{i}" for i in range(4)]})

	write_png("item/concussion_missile.png", concussion_missile_icon())
	write_png("item/sealed_cargo.png", sealed_cargo_icon())
	for i, (name, (base, spots)) in enumerate(sorted(EGGS.items())):
		write_png(f"item/{name}.png", spawn_egg(base, spots, 2950 + i))
	for name in ITEMS:
		item_json(name)


if __name__ == "__main__":
	main()
