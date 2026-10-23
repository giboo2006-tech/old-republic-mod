#!/usr/bin/env python3
"""Generates the ships and travel textures and model JSON (Milestone 4, W3; Milestone 5, W1). PLACEHOLDER
art: original, deterministic, drawn here from shapes and noise; nothing is copied or traced, and no
ship design from any game or film is imitated (a generic blunt-nosed courier and a generic wedge-hulled
interceptor).

Textures (src/main/resources/assets/oldrepublic/textures/):
  entity/ship/courier_shuttle_{watch,imperial,vasch}.png   512x256 hull paints (Watch green, Imperial
                                                           grey, Vasch rust-red), faded and worn
  entity/ship/courier_shuttle_lights.png                   emissive running lights (wing tips, fin
                                                           beacon, nose lens, canopy glow, ramp edge)
  entity/ship/courier_shuttle_lights_damaged.png           the same with the fin beacon dead (DISABLED)
  entity/ship/courier_shuttle_engines.png                  emissive nozzle glow
  entity/ship/courier_shuttle_scorch.png                   translucent soot and blast marks (hull < 70)
  gui/destination_map.png                                  256x128 star chart backdrop (the points,
                                                           lanes and labels are drawn by the screen)
  particle/engine_exhaust_{0..3}.png, spark_shower_{0,1}.png   8x8 particle frames
  particle/atmosphere_streak_{0..2}.png                    8x8 streaks of hot air (Milestone 5, 4.7)
  entity/ship/interceptor_{watch,imperial,vasch,lights,lights_damaged,engines,scorch}.png
                                                           256x128, the interceptor's paints and layers
                                                           (Milestone 5, 4.9), in InterceptorModel's layout
  item/{power_core,hyperdrive_motivator,nav_computer,nav_computer_loaded,hyperfuel_cell,
       passenger_seat,cargo_bay_extension,reinforced_plating,fuel_tank_extension}.png   16x16 icons
  block/landing_beacon_{base,post,post_front,lamp}.png (+ lamp .mcmeta, a slow interpolated pulse, no strobe),
  block/pad_terminal_{base,body,screen}.png                16x16 block faces

JSON (src/main/resources/assets/oldrepublic/):
  items/<id>.json and models/item/<id>.json for the eight ship items (nav_computer switches to its
  lit screen when it carries oldrepublic:nav_data)
  blockstates/{landing_beacon,pad_terminal}.json, models/block/{landing_beacon,pad_terminal}.json,
  items/{landing_beacon,pad_terminal}.json
  particles/{engine_exhaust,spark_shower,atmosphere_streak}.json

The shuttle texture follows CourierShuttleModel's box UV layout: a box at texOffs (u, v) of size
(w, h, d) takes the top at (u+d, v) w x d, the bottom at (u+d+w, v), the port side at (u, v+d)
d x h, the rear at (u+d, v+d) w x h, the starboard side at (u+d+w, v+d) and the front at
(u+2d+w, v+d). Keep PARTS below in step with the model's texOffs, and INTERCEPTOR_PARTS with
InterceptorModel's. Both ships carry the Milestone 5 weapon parts (ShipWeaponParts: cannons, the gunner
turret, the missile rack and the shield emitter) in one 132 x 12 block of their texture (weapon_boxes).

Run from anywhere: python3 tools/assets/gen_textures_ship.py
"""
import json
import math
import os
import random

from PIL import Image, ImageDraw

ASSETS = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "src", "main", "resources", "assets", "oldrepublic"))
NS = "oldrepublic"
TEX_W, TEX_H = 512, 256

# Hull palette: off-white durasteel, greys, glass, canvas and timber.
HULL = (212, 208, 196)
HULL_SHADE = (176, 172, 162)
SEAM = (132, 130, 124)
BELLY = (118, 120, 122)
METAL = (98, 102, 108)
METAL_DARK = (58, 62, 68)
DARK = (38, 41, 46)
GLASS = (26, 38, 54)
GLASS_HI = (96, 132, 160)
CANVAS = (166, 148, 110)
CANVAS_DARK = (128, 112, 82)
CANVAS_PATCH = (148, 138, 104)
ROPE = (92, 76, 52)
WOOD = (122, 90, 58)
WOOD_DARK = (86, 62, 40)
HAZARD = (206, 168, 52)
OCHRE = (186, 128, 62)
OUTLINE = (28, 30, 34)
STEEL = (150, 156, 162)
STEEL_DARK = (86, 92, 99)
STEEL_LIGHT = (196, 202, 208)
BRASS = (170, 140, 86)
BRASS_LIGHT = (222, 196, 128)
CORE_BLUE = (120, 210, 255)
CORE_VIOLET = (170, 130, 255)
SCREEN_GREEN = (110, 230, 160)
SCREEN_DARK = (24, 34, 30)

# Faded paints (spec 4.1).
PAINTS = {
	"watch": (82, 120, 90),
	"imperial": (96, 102, 112),
	"vasch": (146, 72, 50),
}


# ---------------------------------------------------------------- output helpers

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
	return tuple(clamp(c * factor) for c in rgb)


def mix(a, b, t):
	return tuple(clamp(x + (y - x) * t) for x, y in zip(a, b))


def noisy(rgb, rnd, amount):
	return tuple(clamp(c + rnd.uniform(-amount, amount)) for c in rgb)


def hash01(*values):
	h = 2166136261
	for v in values:
		h = ((h ^ (v & 0xFFFFFFFF)) * 16777619) & 0xFFFFFFFF
	h ^= h >> 13
	h = (h * 0x5BD1E995) & 0xFFFFFFFF
	h ^= h >> 15
	return (h & 0xFFFF) / 65535.0


# ---------------------------------------------------------------- the shuttle's box layout

class Box:
	"""One CourierShuttleModel cube: texOffs (u, v) and size (w, h, d)."""

	def __init__(self, name, u, v, w, h, d):
		self.name, self.u, self.v, self.w, self.h, self.d = name, u, v, w, h, d

	def faces(self):
		u, v, w, h, d = self.u, self.v, self.w, self.h, self.d
		return {
			"top": (u + d, v, w, d),
			"bottom": (u + d + w, v, w, d),
			"port": (u, v + d, d, h),
			"rear": (u + d, v + d, w, h),
			"starboard": (u + d + w, v + d, d, h),
			"front": (u + 2 * d + w, v + d, w, h),
		}


# Keep in step with CourierShuttleModel#createBodyLayer.
PARTS = {
	"hull": Box("hull", 0, 0, 28, 18, 64),
	"nose": Box("nose", 186, 48, 22, 14, 12),
	"nose_tip": Box("nose_tip", 330, 48, 14, 8, 4),
	"canopy": Box("canopy", 256, 48, 18, 5, 18),
	"spine": Box("spine", 186, 0, 12, 3, 44),
	"fin": Box("fin", 448, 28, 3, 6, 14),
	"wing": Box("wing", 386, 0, 22, 3, 24),
	"nacelle": Box("nacelle", 300, 0, 10, 10, 32),
	"nozzle": Box("nozzle", 368, 48, 8, 8, 2),
	"ramp": Box("ramp", 386, 28, 12, 1, 18),
	"strut_nose_leg": Box("strut_nose_leg", 404, 84, 2, 6, 2),
	"strut_nose_foot": Box("strut_nose_foot", 414, 84, 5, 1, 5),
	"strut_leg": Box("strut_leg", 436, 84, 2, 5, 2),
	"strut_foot": Box("strut_foot", 446, 84, 6, 1, 6),
	"tarp": Box("tarp", 0, 84, 34, 26, 68),
	"jack": Box("jack", 390, 84, 3, 10, 3),
	"cargo": Box("cargo", 274, 84, 16, 3, 20),
	"tank": Box("tank", 348, 84, 4, 3, 16),
	"plating": Box("plating", 206, 84, 23, 12, 10),
}


def weapon_boxes(u, v):
	"""The Milestone 5 weapon parts' boxes from the texture block at (u, v): keep in step with ShipWeaponParts#define."""
	return {
		"cannon_pod": Box("cannon_pod", u, v, 3, 3, 9),
		"cannon_barrel": Box("cannon_barrel", u + 24, v, 1, 1, 3),
		"turret_base": Box("turret_base", u + 32, v, 6, 1, 6),
		"turret_head": Box("turret_head", u + 56, v, 5, 2, 5),
		"turret_gun": Box("turret_gun", u + 76, v, 1, 1, 7),
		"rack": Box("rack", u + 92, v, 6, 2, 6),
		"emitter": Box("emitter", u + 116, v, 4, 1, 4),
	}


PARTS.update(weapon_boxes(210, 112))

# The interceptor (Milestone 5, 4.9). Keep in step with InterceptorModel#createBodyLayer.
I_TEX_W, I_TEX_H = 256, 128
INTERCEPTOR_PARTS = {
	"hull": Box("hull", 0, 0, 14, 9, 34),
	"nose": Box("nose", 96, 0, 10, 7, 10),
	"nose_tip": Box("nose_tip", 136, 0, 6, 4, 5),
	"canopy": Box("canopy", 96, 18, 8, 6, 12),
	"wing": Box("wing", 0, 44, 14, 2, 16),
	"nacelle": Box("nacelle", 60, 44, 5, 5, 14),
	"nozzle": Box("nozzle", 98, 44, 4, 4, 2),
	"fin": Box("fin", 110, 44, 2, 6, 8),
	"strut_leg": Box("strut_leg", 158, 0, 2, 4, 2),
	"strut_foot": Box("strut_foot", 166, 0, 4, 1, 4),
}
INTERCEPTOR_PARTS.update(weapon_boxes(0, 64))


def check_layout(parts=None, tex_w=TEX_W, tex_h=TEX_H):
	"""Refuses overlapping or out-of-range boxes, so a texOffs typo shows here, not in game."""
	taken = {}
	for box in (parts or PARTS).values():
		x0, y0 = box.u, box.v
		x1, y1 = box.u + 2 * (box.w + box.d), box.v + box.d + box.h
		if x1 > tex_w or y1 > tex_h:
			raise SystemExit(f"{box.name} runs off the {tex_w}x{tex_h} texture")
		for name, (a0, b0, a1, b1) in taken.items():
			if x0 < a1 and a0 < x1 and y0 < b1 and b0 < y1:
				raise SystemExit(f"{box.name} overlaps {name}")
		taken[box.name] = (x0, y0, x1, y1)


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


def face(img, box, name):
	return Painter(img, box.faces()[name])


def all_faces(img, box):
	return [(n, Painter(img, r)) for n, r in box.faces().items()]


# ---------------------------------------------------------------- hull materials

def plate_surface(p, base, seed, seam_step=8, seam_rows=True, grain=5):
	"""Durasteel panels: noise, vertical seams every seam_step, a horizontal seam, sparse rivets."""
	rnd = random.Random(seed)
	for y in range(p.h):
		for x in range(p.w):
			c = noisy(base, rnd, grain)
			if hash01(seed, x // 3, y // 5) > 0.82:
				c = shade(c, 0.95)
			p.px(x, y, c)
	if seam_step:
		for x in range(seam_step, p.w, seam_step):
			for y in range(p.h):
				p.tint(x, y, SEAM, 0.55)
	if seam_rows and p.h > 8:
		for x in range(p.w):
			p.tint(x, p.h // 2, SEAM, 0.35)
	for x in range(2, p.w - 1, 4):
		for y in (1, p.h - 2):
			if hash01(seed, x, y, 7) > 0.45:
				p.tint(x, y, SEAM, 0.5)


def weather(p, seed, amount=0.25):
	"""Grime: streaks running down and scuffs."""
	rnd = random.Random(seed)
	for _ in range(max(1, p.w // 6)):
		x = rnd.randrange(p.w)
		start = rnd.randrange(max(1, p.h // 2))
		length = rnd.randrange(2, max(3, p.h))
		for y in range(start, min(p.h, start + length)):
			p.tint(x, y, (90, 84, 74), amount * (1 - (y - start) / max(1, length)))
	for _ in range(max(1, p.w * p.h // 120)):
		x, y = rnd.randrange(p.w), rnd.randrange(p.h)
		p.tint(x, y, (70, 66, 60), amount * 1.5)


def paint_band(p, y0, y1, paint, seed, fade=0.18):
	"""A faded paint band with chips showing the hull through it."""
	for y in range(max(0, y0), min(p.h, y1 + 1)):
		for x in range(p.w):
			if hash01(seed, x, y) < fade:
				continue
			r, g, b, a = p.get(x, y)
			light = (r + g + b) / (3 * 200)
			p.px(x, y, mix(shade(paint, light), (r, g, b), 0.12))


def paint_all(p, paint, seed, fade=0.12):
	paint_band(p, 0, p.h - 1, paint, seed, fade)


def side_hull(p, paint, seed):
	"""The fuselage side: off-white panels, a paint band, a dark belly edge, grime."""
	plate_surface(p, HULL, seed)
	paint_band(p, 7, 10, paint, seed + 1)
	for x in range(p.w):
		p.tint(x, 6, SEAM, 0.4)
		p.tint(x, 11, SEAM, 0.4)
	for y in range(p.h - 3, p.h):
		for x in range(p.w):
			p.tint(x, y, BELLY, 0.6)
	# Access hatches and a vent row.
	for hx in (p.w // 4, (3 * p.w) // 4):
		p.rect(hx - 3, 2, hx + 3, 5, shade(HULL, 0.9))
		for x in range(hx - 3, hx + 4):
			p.tint(x, 2, SEAM, 0.6)
			p.tint(x, 5, SEAM, 0.6)
	for x in range(p.w // 2 - 6, p.w // 2 + 6, 2):
		p.px(x, 13, METAL_DARK)
	weather(p, seed + 2)


def top_hull(p, paint, seed):
	plate_surface(p, HULL, seed, seam_step=7)
	# Two paint stripes running nose to tail, and a dorsal hatch.
	for x in (5, 6, p.w - 7, p.w - 6):
		for y in range(p.h):
			if hash01(seed, x, y) > 0.15:
				p.px(x, y, shade(paint, 0.95))
	cx, cy = p.w // 2, p.h // 2
	p.rect(cx - 4, cy - 5, cx + 4, cy + 5, shade(HULL, 0.88))
	for x in range(cx - 4, cx + 5):
		p.tint(x, cy - 5, SEAM, 0.7)
		p.tint(x, cy + 5, SEAM, 0.7)
	for y in range(cy - 5, cy + 6):
		p.tint(cx - 4, y, SEAM, 0.7)
		p.tint(cx + 4, y, SEAM, 0.7)
	weather(p, seed + 3, 0.18)


def belly(p, seed):
	plate_surface(p, BELLY, seed, seam_step=6, grain=6)
	# Gear bays and a ramp seam.
	for bx, by in ((p.w // 2, p.h // 8), (p.w // 4, p.h // 2), ((3 * p.w) // 4, p.h // 2)):
		p.rect(bx - 2, by - 2, bx + 2, by + 2, METAL_DARK)
	weather(p, seed + 4, 0.3)


def rear_hull(p, seed):
	"""The tail: a dark ramp door frame over the belly and a pair of vents."""
	plate_surface(p, HULL_SHADE, seed, seam_step=0)
	cx = p.w // 2
	p.rect(cx - 6, p.h - 8, cx + 5, p.h - 1, METAL_DARK)
	p.rect(cx - 5, p.h - 7, cx + 4, p.h - 1, DARK)
	for vx in (4, p.w - 7):
		for y in range(3, 8, 2):
			p.rect(vx, y, vx + 2, y, DARK)
	weather(p, seed + 5, 0.3)


# ---------------------------------------------------------------- per-part painters

def paint_hull(img, paint):
	box = PARTS["hull"]
	side_hull(face(img, box, "port"), paint, 11)
	side_hull(face(img, box, "starboard"), paint, 11)
	top_hull(face(img, box, "top"), paint, 12)
	belly(face(img, box, "bottom"), 13)
	rear_hull(face(img, box, "rear"), 14)
	plate_surface(face(img, box, "front"), HULL, 15, seam_step=0)


def paint_nose(img, paint):
	box = PARTS["nose"]
	for name, p in all_faces(img, box):
		plate_surface(p, HULL, 21, seam_step=6)
	# A paint chevron on each side, sweeping up toward the nose.
	for name in ("port", "starboard"):
		p = face(img, box, name)
		for x in range(p.w):
			lo = 5 + x // 3
			for y in range(lo, min(p.h, lo + 3)):
				if hash01(22, x, y) > 0.15:
					p.px(x, y, paint)
		weather(p, 23, 0.2)
	top = face(img, box, "top")
	for y in range(top.h):
		t = y / max(1, top.h - 1)
		for x in range(top.w):
			if hash01(24, x, y) > 0.2 + 0.6 * t:
				top.tint(x, y, paint, 0.8)
	front = face(img, box, "front")
	front.rect(front.w // 2 - 5, 4, front.w // 2 + 4, 9, METAL_DARK)
	for x in range(front.w // 2 - 5, front.w // 2 + 5, 2):
		front.px(x, 6, DARK)
	belly(face(img, box, "bottom"), 25)


def paint_nose_tip(img):
	box = PARTS["nose_tip"]
	for name, p in all_faces(img, box):
		plate_surface(p, METAL_DARK, 31, seam_step=0, grain=4)
	front = face(img, box, "front")
	cx, cy = front.w // 2, front.h // 2
	front.rect(cx - 2, cy - 2, cx + 1, cy + 1, DARK)
	front.rect(cx - 1, cy - 1, cx, cy, (40, 70, 86))


def paint_canopy(img):
	box = PARTS["canopy"]
	rnd = random.Random(41)
	for name, p in all_faces(img, box):
		if name in ("bottom", "rear"):
			plate_surface(p, HULL_SHADE, 42, seam_step=0)
			continue
		for y in range(p.h):
			for x in range(p.w):
				p.px(x, y, noisy(mix(GLASS, GLASS_HI, 0.12 * (1 - y / max(1, p.h))), rnd, 3))
		# Frame: the edges and struts every six pixels.
		for x in range(p.w):
			p.px(x, 0, HULL_SHADE)
			p.px(x, p.h - 1, HULL_SHADE)
		for x in range(0, p.w, 6):
			for y in range(p.h):
				p.px(x, y, HULL_SHADE)
		# A diagonal glint.
		for i in range(min(p.w, p.h)):
			p.tint(p.w - 3 - i, i, (210, 230, 245), 0.25)


def paint_spine(img, paint):
	box = PARTS["spine"]
	for name, p in all_faces(img, box):
		plate_surface(p, HULL, 51, seam_step=8)
	top = face(img, box, "top")
	for y in range(4, top.h - 4, 3):
		top.rect(3, y, top.w - 4, y, METAL_DARK)
	for name in ("port", "starboard"):
		p = face(img, box, name)
		for x in range(p.w):
			if hash01(52, x) > 0.2:
				p.px(x, 1, shade(paint, 0.9))


def paint_fin(img, paint):
	box = PARTS["fin"]
	for name, p in all_faces(img, box):
		plate_surface(p, HULL, 61, seam_step=0)
		paint_all(p, paint, 62)
		weather(p, 63, 0.2)


def paint_wing(img, paint):
	box = PARTS["wing"]
	for name, p in all_faces(img, box):
		plate_surface(p, HULL if name != "bottom" else BELLY, 71, seam_step=5)
	top = face(img, box, "top")
	# Paint at the outer tip (the high-u end; the left wing's mirror flips it outward too).
	for x in range(top.w - 5, top.w):
		for y in range(top.h):
			if hash01(72, x, y) > 0.15:
				top.px(x, y, paint)
	for y in range(0, top.h, 6):
		for x in range(top.w):
			top.tint(x, y, SEAM, 0.35)
	weather(top, 73, 0.2)
	tip = face(img, box, "starboard")
	paint_all(tip, paint, 74)


def paint_nacelle(img):
	box = PARTS["nacelle"]
	for name, p in all_faces(img, box):
		plate_surface(p, METAL, 81, seam_step=4, seam_rows=False, grain=6)
	# Heat tint toward the rear: the side faces run nose (high u) to tail (low u) or back; tint both ends lightly
	# and the rear quarter strongly so either reading looks scorched aft.
	for name in ("port", "starboard", "top", "bottom"):
		p = face(img, box, name)
		horizontal = name in ("port", "starboard")
		length = p.w if horizontal else p.h
		for i in range(length):
			t = max(0.0, 1 - i / (length * 0.3))
			for j in range(p.h if horizontal else p.w):
				x, y = (i, j) if horizontal else (j, i)
				p.tint(x, y, (92, 80, 110), 0.45 * t)
		# Ribs.
		for i in range(3, length, 5):
			for j in range(p.h if horizontal else p.w):
				x, y = (i, j) if horizontal else (j, i)
				p.tint(x, y, METAL_DARK, 0.5)
	front = face(img, box, "front")
	front.fill(METAL_DARK)
	cx, cy = front.w // 2, front.h // 2
	for y in range(front.h):
		for x in range(front.w):
			r = math.hypot(x + 0.5 - front.w / 2, y + 0.5 - front.h / 2)
			if r < 4.2:
				front.px(x, y, DARK if r > 1.2 else METAL)
			if 1.5 < r < 4.0 and (math.atan2(y - cy, x - cx) * 3) % 1.0 < 0.35:
				front.px(x, y, METAL_DARK)
	face(img, box, "rear").fill(shade(METAL_DARK, 0.8))


def paint_nozzle(img):
	box = PARTS["nozzle"]
	for name, p in all_faces(img, box):
		plate_surface(p, METAL_DARK, 91, seam_step=0, grain=4)
	rear = face(img, box, "rear")
	for y in range(rear.h):
		for x in range(rear.w):
			r = math.hypot(x + 0.5 - rear.w / 2, y + 0.5 - rear.h / 2)
			if r < 3.0:
				rear.px(x, y, (30, 30, 36))
			elif r < 3.9:
				rear.px(x, y, (84, 74, 90))


def paint_ramp(img):
	box = PARTS["ramp"]
	for name, p in all_faces(img, box):
		plate_surface(p, BELLY, 101, seam_step=0)
	top = face(img, box, "top")
	top.fill(METAL_DARK)
	for y in range(1, top.h, 3):
		top.rect(1, y, top.w - 2, y, (78, 82, 88))
	for x in (0, top.w - 1):
		for y in range(top.h):
			top.px(x, y, HAZARD if (y // 2) % 2 == 0 else DARK)


def paint_struts(img):
	for key in ("strut_nose_leg", "strut_leg"):
		for name, p in all_faces(img, PARTS[key]):
			plate_surface(p, METAL, 111, seam_step=0, grain=5)
			for y in range(0, p.h, 2):
				p.tint(0, y, STEEL_LIGHT, 0.4)
	for key in ("strut_nose_foot", "strut_foot"):
		for name, p in all_faces(img, PARTS[key]):
			plate_surface(p, DARK, 112, seam_step=0, grain=4)


def paint_tarp(img):
	"""Patched canvas with folds, stitched patches and tie-down ropes."""
	box = PARTS["tarp"]
	for name, p in all_faces(img, box):
		rnd = random.Random(121 + len(name))
		for y in range(p.h):
			for x in range(p.w):
				c = noisy(CANVAS, rnd, 7)
				fold = math.sin((y + hash01(122, x // 6) * 4) * 0.7)
				if fold > 0.7:
					c = mix(c, CANVAS_DARK, 0.35)
				p.px(x, y, c)
		for i in range(max(1, p.w * p.h // 500)):
			x0 = int(hash01(123, i, len(name)) * max(1, p.w - 10))
			y0 = int(hash01(124, i, len(name)) * max(1, p.h - 8))
			pw = 5 + int(hash01(125, i) * 6)
			ph = 4 + int(hash01(126, i) * 5)
			p.rect(x0, y0, x0 + pw, y0 + ph, noisy(CANVAS_PATCH, rnd, 6))
			for x in range(x0, x0 + pw + 1, 2):
				p.px(x, y0, CANVAS_DARK)
				p.px(x, y0 + ph, CANVAS_DARK)
			for y in range(y0, y0 + ph + 1, 2):
				p.px(x0, y, CANVAS_DARK)
				p.px(x0 + pw, y, CANVAS_DARK)
		if name in ("port", "starboard", "top"):
			for x in range(6, p.w, 16):
				for y in range(p.h):
					p.px(x, y, ROPE)


def paint_jacks(img):
	box = PARTS["jack"]
	for name, p in all_faces(img, box):
		rnd = random.Random(131)
		for y in range(p.h):
			for x in range(p.w):
				c = noisy(WOOD, rnd, 8)
				if (y + x * 3) % 5 == 0:
					c = shade(c, 0.82)
				p.px(x, y, c)
		if name in ("top", "bottom"):
			p.fill(WOOD_DARK)


def paint_upgrades(img, paint):
	cargo = PARTS["cargo"]
	for name, p in all_faces(img, cargo):
		plate_surface(p, METAL, 141, seam_step=5, grain=5)
	for name in ("front", "rear"):
		p = face(img, cargo, name)
		for x in range(p.w):
			for y in range(p.h):
				p.px(x, y, HAZARD if ((x + y) // 2) % 2 == 0 else DARK)
	tank = PARTS["tank"]
	for name, p in all_faces(img, tank):
		plate_surface(p, OCHRE, 151, seam_step=0, grain=6)
		horizontal = name in ("port", "starboard")
		length = p.w if horizontal else p.h
		for i in range(2, length, 4):
			for j in range(p.h if horizontal else p.w):
				x, y = (i, j) if horizontal else (j, i)
				p.px(x, y, METAL_DARK)
	plating = PARTS["plating"]
	for name, p in all_faces(img, plating):
		plate_surface(p, (124, 128, 134), 161, seam_step=6, grain=5)
		for x in range(1, p.w, 3):
			for y in (1, p.h - 2):
				p.px(x, y, STEEL_DARK)
		if name in ("port", "starboard", "top"):
			for x in range(p.w):
				if hash01(162, x) > 0.25:
					p.px(x, 0, paint)


def shuttle(paint_name):
	paint = PAINTS[paint_name]
	img = Image.new("RGBA", (TEX_W, TEX_H), (0, 0, 0, 0))
	paint_hull(img, paint)
	paint_nose(img, paint)
	paint_nose_tip(img)
	paint_canopy(img)
	paint_spine(img, paint)
	paint_fin(img, paint)
	paint_wing(img, paint)
	paint_nacelle(img)
	paint_nozzle(img)
	paint_ramp(img)
	paint_struts(img)
	paint_tarp(img)
	paint_jacks(img)
	paint_upgrades(img, paint)
	paint_weapons(img, PARTS, paint)
	return img


# ---------------------------------------------------------------- the Milestone 5 weapon parts (both ships)

def paint_weapons(img, parts, paint):
	"""Cannon pods and barrels, the gunner turret's ring, head and guns, the missile rack and the shield emitter."""
	pod = parts["cannon_pod"]
	for name, p in all_faces(img, pod):
		plate_surface(p, METAL, 301, seam_step=3, seam_rows=False, grain=5)
		if name in ("port", "starboard", "top"):
			# A paint stripe along the pod and heat staining toward the muzzle end.
			horizontal = name != "top"
			length = p.w if horizontal else p.h
			for i in range(length):
				for j in range(p.h if horizontal else p.w):
					x, y = (i, j) if horizontal else (j, i)
					if j == 1 and hash01(302, i) > 0.2:
						p.px(x, y, paint)
	face(img, pod, "front").fill(METAL_DARK)
	barrel = parts["cannon_barrel"]
	for name, p in all_faces(img, barrel):
		plate_surface(p, DARK, 303, seam_step=0, grain=3)
	face(img, barrel, "front").fill((18, 18, 22))

	base = parts["turret_base"]
	for name, p in all_faces(img, base):
		plate_surface(p, METAL_DARK, 311, seam_step=2, seam_rows=False, grain=4)
	head = parts["turret_head"]
	for name, p in all_faces(img, head):
		plate_surface(p, HULL_SHADE, 312, seam_step=0, grain=5)
		paint_band(p, 0, 0, paint, 313)
	# The gunner's sight slit across the front.
	front = face(img, head, "front")
	front.rect(1, 1, front.w - 2, 1, GLASS)
	gun = parts["turret_gun"]
	for name, p in all_faces(img, gun):
		plate_surface(p, DARK, 314, seam_step=0, grain=3)
		for i in range(0, max(p.w, p.h), 3):
			p.tint(i if p.w > p.h else 0, 0 if p.w > p.h else i, STEEL_LIGHT, 0.3)

	rack = parts["rack"]
	for name, p in all_faces(img, rack):
		plate_surface(p, METAL, 321, seam_step=3, seam_rows=False, grain=5)
	# Two tube mouths on the front, hazard edges on the sides.
	front = face(img, rack, "front")
	front.fill(METAL_DARK)
	for cx in (1, 4):
		front.rect(cx, 0, cx, 1, (24, 24, 28))
	for name in ("port", "starboard"):
		p = face(img, rack, name)
		for x in range(p.w):
			p.px(x, p.h - 1, HAZARD if (x // 2) % 2 == 0 else DARK)

	emitter = parts["emitter"]
	for name, p in all_faces(img, emitter):
		plate_surface(p, BRASS, 331, seam_step=0, grain=6)
	top = face(img, emitter, "top")
	for y in range(top.h):
		for x in range(top.w):
			r = math.hypot(x + 0.5 - top.w / 2, y + 0.5 - top.h / 2)
			if r < 1.2:
				top.px(x, y, (60, 110, 140))
			elif r < 1.9:
				top.px(x, y, STEEL_DARK)


def weapon_lights(img, parts):
	"""The emitter's ring of blue light and an amber sensor on the turret head."""
	top = face(img, parts["emitter"], "top")
	for y in range(top.h):
		for x in range(top.w):
			r = math.hypot(x + 0.5 - top.w / 2, y + 0.5 - top.h / 2)
			if r < 1.2:
				top.px(x, y, (150, 225, 255))
	front = face(img, parts["turret_head"], "front")
	front.px(front.w // 2, 1, (255, 186, 92))


# ---------------------------------------------------------------- emissive and overlay layers

def glow_dot(p, cx, cy, rgb, radius=1.6):
	for y in range(p.h):
		for x in range(p.w):
			r = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
			if r <= radius:
				a = 255 if r <= radius * 0.5 else int(255 * (1 - (r - radius * 0.5) / (radius * 0.5)))
				p.px(x, y, rgb, max(0, a))


def lights(damaged):
	img = Image.new("RGBA", (TEX_W, TEX_H), (0, 0, 0, 0))
	amber = (255, 186, 92)
	white = (240, 248, 255)
	cyan = (130, 230, 255)
	wing = PARTS["wing"]
	# Wing tips (both wings share the outer side face) and the leading edge's tip.
	tip = face(img, wing, "starboard")
	glow_dot(tip, tip.w / 2, tip.h / 2, amber, 1.8)
	front = face(img, wing, "front")
	glow_dot(front, front.w - 1.5, front.h / 2, amber, 1.4)
	# The fin beacon (dead on a damaged ship).
	if not damaged:
		fin_top = face(img, PARTS["fin"], "top")
		fin_top.rect(0, 1, fin_top.w - 1, 3, white)
		for name in ("port", "starboard"):
			p = face(img, PARTS["fin"], name)
			glow_dot(p, 2, 0.8, white, 1.5)
	# The nose sensor lens.
	tip_front = face(img, PARTS["nose_tip"], "front")
	tip_front.rect(tip_front.w // 2 - 1, tip_front.h // 2 - 1, tip_front.w // 2, tip_front.h // 2, cyan)
	# A faint cockpit glow behind the windscreen.
	for name in ("front", "port", "starboard"):
		p = face(img, PARTS["canopy"], name)
		for y in range(1, p.h - 1):
			for x in range(p.w):
				if x % 6 != 0:
					p.px(x, y, (60, 110, 140), 70)
	# Ramp edge lights.
	ramp = face(img, PARTS["ramp"], "top")
	for y in range(1, ramp.h, 4):
		ramp.px(0, y, amber)
		ramp.px(ramp.w - 1, y, amber)
	weapon_lights(img, PARTS)
	return img


def engines():
	img = Image.new("RGBA", (TEX_W, TEX_H), (0, 0, 0, 0))
	rear = face(img, PARTS["nozzle"], "rear")
	for y in range(rear.h):
		for x in range(rear.w):
			r = math.hypot(x + 0.5 - rear.w / 2, y + 0.5 - rear.h / 2)
			if r < 1.6:
				rear.px(x, y, (235, 248, 255))
			elif r < 3.0:
				rear.px(x, y, (130, 200, 255))
			elif r < 3.9:
				rear.px(x, y, (70, 120, 220), 200)
	for name in ("port", "starboard", "top", "bottom"):
		p = face(img, PARTS["nozzle"], name)
		p.fill((60, 100, 200), 90)
	return img


def scorch():
	img = Image.new("RGBA", (TEX_W, TEX_H), (0, 0, 0, 0))
	targets = [("hull", "port"), ("hull", "starboard"), ("hull", "top"), ("nose", "port"), ("nose", "starboard"),
			   ("nose", "top"), ("wing", "top"), ("nacelle", "port"), ("nacelle", "starboard"), ("nacelle", "top"),
			   ("spine", "top"), ("fin", "port"), ("fin", "starboard")]
	for i, (part, name) in enumerate(targets):
		p = face(img, PARTS[part], name)
		rnd = random.Random(171 + i)
		for _ in range(max(1, p.w * p.h // 90)):
			cx, cy = rnd.uniform(0, p.w), rnd.uniform(0, p.h)
			radius = rnd.uniform(1.5, 4.5)
			for y in range(int(cy - radius) - 1, int(cy + radius) + 2):
				for x in range(int(cx - radius * 1.6) - 1, int(cx + radius * 1.6) + 2):
					d = math.hypot((x + 0.5 - cx) / 1.6, y + 0.5 - cy)
					if d < radius and 0 <= x < p.w and 0 <= y < p.h:
						a = int(200 * (1 - d / radius) * (0.6 + 0.4 * hash01(172, x, y)))
						old = p.get(x, y)[3]
						p.px(x, y, (34, 28, 24), max(old, a))
		# A few blast streaks.
		for _ in range(max(1, p.w // 20)):
			x = rnd.randrange(p.w)
			for y in range(p.h):
				if hash01(173, x, y) > 0.4:
					p.px(x, y, (44, 36, 30), max(p.get(x, y)[3], 90))
	return img


# ---------------------------------------------------------------- the interceptor (Milestone 5, 4.9)

def i_side(p, paint, seed):
	"""The interceptor's hull side: off-white panels, a paint flash along the middle, a dark belly edge, an intake grille."""
	plate_surface(p, HULL, seed, seam_step=6)
	paint_band(p, 3, 4, paint, seed + 1)
	for x in range(p.w):
		p.tint(x, 2, SEAM, 0.4)
		p.tint(x, 5, SEAM, 0.4)
		p.tint(x, p.h - 1, BELLY, 0.7)
	# The intake grille ahead of the engine pods (the faces run nose to tail one way or the other: both ends get one).
	for gx in (4, p.w - 9):
		for x in range(gx, gx + 5):
			p.px(x, 6, METAL_DARK if x % 2 == 0 else DARK)
	weather(p, seed + 2, 0.2)


def i_top(p, paint, seed):
	plate_surface(p, HULL, seed, seam_step=5)
	# A broad paint stripe down the spine and a panel line either side.
	cx = p.w // 2
	for y in range(p.h):
		for x in range(cx - 2, cx + 2):
			if hash01(seed, x, y) > 0.15:
				p.px(x, y, paint)
		p.tint(cx - 4, y, SEAM, 0.5)
		p.tint(cx + 3, y, SEAM, 0.5)
	weather(p, seed + 3, 0.15)


def paint_interceptor_body(img, paint):
	parts = INTERCEPTOR_PARTS
	hull = parts["hull"]
	i_side(face(img, hull, "port"), paint, 401)
	i_side(face(img, hull, "starboard"), paint, 401)
	i_top(face(img, hull, "top"), paint, 402)
	belly(face(img, hull, "bottom"), 403)
	rear = face(img, hull, "rear")
	plate_surface(rear, HULL_SHADE, 404, seam_step=0)
	rear.rect(rear.w // 2 - 2, 2, rear.w // 2 + 1, rear.h - 2, METAL_DARK)
	plate_surface(face(img, hull, "front"), HULL, 405, seam_step=0)

	nose = parts["nose"]
	for name, p in all_faces(img, nose):
		plate_surface(p, HULL, 411, seam_step=5)
	for name in ("port", "starboard"):
		p = face(img, nose, name)
		# A paint chevron sweeping forward along each cheek.
		for x in range(p.w):
			lo = 2 + x // 4
			for y in range(lo, min(p.h, lo + 2)):
				if hash01(412, x, y) > 0.15:
					p.px(x, y, paint)
		weather(p, 413, 0.15)
	top = face(img, nose, "top")
	for y in range(top.h):
		for x in range(top.w):
			if hash01(414, x, y) > 0.35 + 0.5 * (y / max(1, top.h - 1)):
				top.tint(x, y, paint, 0.8)
	belly(face(img, nose, "bottom"), 415)

	tip = parts["nose_tip"]
	for name, p in all_faces(img, tip):
		plate_surface(p, METAL_DARK, 421, seam_step=0, grain=4)
	front = face(img, tip, "front")
	front.rect(front.w // 2 - 1, front.h // 2 - 1, front.w // 2, front.h // 2, (40, 70, 86))

	canopy = parts["canopy"]
	rnd = random.Random(431)
	for name, p in all_faces(img, canopy):
		if name == "bottom":
			plate_surface(p, HULL_SHADE, 432, seam_step=0)
			continue
		for y in range(p.h):
			for x in range(p.w):
				p.px(x, y, noisy(mix(GLASS, GLASS_HI, 0.15 * (1 - y / max(1, p.h))), rnd, 3))
		# A single frame hoop and the rim.
		for x in range(p.w):
			p.px(x, p.h - 1, HULL_SHADE)
		if name in ("port", "starboard", "top"):
			hoop = p.w // 2 if name != "top" else None
			if hoop is not None:
				for y in range(p.h):
					p.px(hoop, y, HULL_SHADE)
			else:
				for x in range(p.w):
					p.px(x, p.h // 2, HULL_SHADE)
		for i in range(min(p.w, p.h)):
			p.tint(p.w - 2 - i, i, (210, 230, 245), 0.3)

	wing = parts["wing"]
	for name, p in all_faces(img, wing):
		plate_surface(p, HULL if name != "bottom" else BELLY, 441, seam_step=4)
	top = face(img, wing, "top")
	# The path's paint on the outer third and a dark leading edge.
	for x in range(top.w - 5, top.w):
		for y in range(top.h):
			if hash01(442, x, y) > 0.12:
				top.px(x, y, paint)
	for x in range(top.w):
		top.tint(x, 0, METAL_DARK, 0.5)
	weather(top, 443, 0.15)
	paint_all(face(img, wing, "starboard"), paint, 444)

	nacelle = parts["nacelle"]
	for name, p in all_faces(img, nacelle):
		plate_surface(p, METAL, 451, seam_step=3, seam_rows=False, grain=6)
	for name in ("port", "starboard", "top", "bottom"):
		p = face(img, nacelle, name)
		horizontal = name in ("port", "starboard")
		length = p.w if horizontal else p.h
		for i in range(length):
			t = max(0.0, 1 - i / (length * 0.35))
			for j in range(p.h if horizontal else p.w):
				x, y = (i, j) if horizontal else (j, i)
				p.tint(x, y, (92, 80, 110), 0.45 * t)
	front = face(img, nacelle, "front")
	front.fill(METAL_DARK)
	front.rect(1, 1, front.w - 2, front.h - 2, DARK)
	face(img, nacelle, "rear").fill(shade(METAL_DARK, 0.8))

	nozzle = parts["nozzle"]
	for name, p in all_faces(img, nozzle):
		plate_surface(p, METAL_DARK, 461, seam_step=0, grain=4)
	rear = face(img, nozzle, "rear")
	for y in range(rear.h):
		for x in range(rear.w):
			r = math.hypot(x + 0.5 - rear.w / 2, y + 0.5 - rear.h / 2)
			rear.px(x, y, (30, 30, 36) if r < 1.5 else (84, 74, 90))

	fin = parts["fin"]
	for name, p in all_faces(img, fin):
		plate_surface(p, HULL, 471, seam_step=0)
		paint_all(p, paint, 472)
		weather(p, 473, 0.2)

	for name, p in all_faces(img, parts["strut_leg"]):
		plate_surface(p, METAL, 481, seam_step=0, grain=5)
	for name, p in all_faces(img, parts["strut_foot"]):
		plate_surface(p, DARK, 482, seam_step=0, grain=4)


def interceptor(paint_name):
	paint = PAINTS[paint_name]
	img = Image.new("RGBA", (I_TEX_W, I_TEX_H), (0, 0, 0, 0))
	paint_interceptor_body(img, paint)
	paint_weapons(img, INTERCEPTOR_PARTS, paint)
	return img


def interceptor_lights(damaged):
	img = Image.new("RGBA", (I_TEX_W, I_TEX_H), (0, 0, 0, 0))
	parts = INTERCEPTOR_PARTS
	amber = (255, 186, 92)
	white = (240, 248, 255)
	cyan = (130, 230, 255)
	# Wing tips.
	tip = face(img, parts["wing"], "starboard")
	glow_dot(tip, tip.w / 2, tip.h / 2, amber, 1.2)
	# The fin's tail beacon (dead on a damaged ship).
	if not damaged:
		fin_top = face(img, parts["fin"], "top")
		fin_top.rect(0, 0, fin_top.w - 1, 2, white)
	# The nose sensor and a faint cockpit glow.
	front = face(img, parts["nose_tip"], "front")
	front.rect(front.w // 2 - 1, front.h // 2 - 1, front.w // 2, front.h // 2, cyan)
	for name in ("front", "port", "starboard"):
		p = face(img, parts["canopy"], name)
		for y in range(0, p.h - 1):
			for x in range(p.w):
				p.px(x, y, (60, 110, 140), 60)
	weapon_lights(img, parts)
	return img


def interceptor_engines():
	img = Image.new("RGBA", (I_TEX_W, I_TEX_H), (0, 0, 0, 0))
	rear = face(img, INTERCEPTOR_PARTS["nozzle"], "rear")
	for y in range(rear.h):
		for x in range(rear.w):
			r = math.hypot(x + 0.5 - rear.w / 2, y + 0.5 - rear.h / 2)
			if r < 1.1:
				rear.px(x, y, (240, 230, 255))
			elif r < 2.0:
				rear.px(x, y, (170, 150, 255))
			else:
				rear.px(x, y, (110, 90, 220), 190)
	for name in ("port", "starboard", "top", "bottom"):
		face(img, INTERCEPTOR_PARTS["nozzle"], name).fill((90, 80, 200), 90)
	return img


def interceptor_scorch():
	img = Image.new("RGBA", (I_TEX_W, I_TEX_H), (0, 0, 0, 0))
	targets = [("hull", "port"), ("hull", "starboard"), ("hull", "top"), ("nose", "port"), ("nose", "starboard"), ("nose", "top"),
			   ("wing", "top"), ("nacelle", "port"), ("nacelle", "starboard"), ("nacelle", "top"), ("fin", "port"), ("fin", "starboard")]
	for i, (part, name) in enumerate(targets):
		p = face(img, INTERCEPTOR_PARTS[part], name)
		rnd = random.Random(491 + i)
		for _ in range(max(1, p.w * p.h // 70)):
			cx, cy = rnd.uniform(0, p.w), rnd.uniform(0, p.h)
			radius = rnd.uniform(1.0, 3.0)
			for y in range(int(cy - radius) - 1, int(cy + radius) + 2):
				for x in range(int(cx - radius * 1.6) - 1, int(cx + radius * 1.6) + 2):
					d = math.hypot((x + 0.5 - cx) / 1.6, y + 0.5 - cy)
					if d < radius and 0 <= x < p.w and 0 <= y < p.h:
						a = int(200 * (1 - d / radius) * (0.6 + 0.4 * hash01(492, x, y)))
						p.px(x, y, (34, 28, 24), max(p.get(x, y)[3], a))
	return img


def atmosphere_streak_frame(i):
	"""A thin vertical streak of hot air, brightest at its middle (the particle tints it); three lengths."""
	img = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
	half = 2 + i
	for y in range(4 - half, 4 + half):
		t = 1 - abs(y + 0.5 - 4) / half
		img.putpixel((3, y), (255, 255, 255, clamp(255 * t)))
		img.putpixel((4, y), (255, 255, 255, clamp(150 * t)))
	return img


# ---------------------------------------------------------------- the destination map backdrop

def destination_map():
	"""A dark star field with soft nebula clouds; the chart's points and lanes are drawn by the screen."""
	w, h = 256, 128
	img = Image.new("RGBA", (w, h), (0, 0, 0, 255))
	rnd = random.Random(181)
	for y in range(h):
		for x in range(w):
			n = 0.0
			for octave, scale in ((1.0, 40.0), (0.5, 18.0), (0.25, 8.0)):
				n += octave * (0.5 + 0.5 * math.sin(x / scale * 1.3 + math.cos(y / scale * 1.7) * 2.1 + octave * 3)
							   * math.cos(y / scale * 0.9 + math.sin(x / scale * 0.7) * 1.6))
			n /= 1.75
			cloud = max(0.0, n - 0.45) * 1.6
			base = (6, 10, 22)
			c = mix(base, (34, 52, 92), cloud)
			if x > w * 0.6:
				c = mix(c, (60, 30, 40), cloud * 0.5 * (x - w * 0.6) / (w * 0.4))
			img.putpixel((x, y), (*c, 255))
	for _ in range(420):
		x, y = rnd.randrange(w), rnd.randrange(h)
		b = rnd.uniform(0.25, 1.0)
		img.putpixel((x, y), (*mix(img.getpixel((x, y))[:3], (220, 230, 255), b), 255))
	for _ in range(24):
		x, y = rnd.randrange(1, w - 1), rnd.randrange(1, h - 1)
		for dx, dy in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
			t = 0.9 if (dx, dy) == (0, 0) else 0.35
			img.putpixel((x + dx, y + dy), (*mix(img.getpixel((x + dx, y + dy))[:3], (240, 244, 255), t), 255))
	# Faint grid, as on a chart.
	for x in range(0, w, 32):
		for y in range(h):
			img.putpixel((x, y), (*mix(img.getpixel((x, y))[:3], (60, 110, 150), 0.12), 255))
	for y in range(0, h, 32):
		for x in range(w):
			img.putpixel((x, y), (*mix(img.getpixel((x, y))[:3], (60, 110, 150), 0.12), 255))
	return img


# ---------------------------------------------------------------- particles

def exhaust_frame(i):
	"""One of four soft puffs, each a little thinner (the particle picks one at random and tints it)."""
	img = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
	radius = 3.6 - i * 0.35
	for y in range(8):
		for x in range(8):
			d = math.hypot(x + 0.5 - 4, y + 0.5 - 4)
			if d < radius:
				a = (1 - d / radius) * (1 - i * 0.18)
				if hash01(191, i, x, y) < 0.15 * i:
					a *= 0.4
				img.putpixel((x, y), (255, 255, 255, clamp(255 * a)))
	return img


def spark_frame(i):
	"""A hot spark with a short tail; frame 1 is a dimmer variant (the particle picks one at random)."""
	img = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
	core = (255, 240, 200) if i == 0 else (255, 196, 120)
	for x, y, a in ((3, 3, 255), (4, 3, 255), (3, 4, 255), (4, 4, 255), (2, 3, 140), (5, 4, 140), (3, 2, 120), (4, 5, 120)):
		img.putpixel((x, y), (*core, a if i == 0 else int(a * 0.7)))
	return img


# ---------------------------------------------------------------- item icons

def new(w=16, h=16):
	return Image.new("RGBA", (w, h), (0, 0, 0, 0))


def opaque(img, x, y):
	return 0 <= x < img.width and 0 <= y < img.height and img.getpixel((x, y))[3] > 0


def put(img, x, y, rgb, a=255):
	if 0 <= x < img.width and 0 <= y < img.height:
		img.putpixel((x, y), (*rgb, a))


def finish_icon(img, seed, wear=4):
	"""A flat icon's depth: a dark outline, a lit top-left edge, a shaded bottom-right edge, wear."""
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


def power_core():
	"""A squat drum with brass caps and a blue core window."""
	img = new()
	d = ImageDraw.Draw(img)
	d.rectangle([3, 4, 12, 12], fill=(*STEEL, 255))
	d.rectangle([2, 3, 13, 4], fill=(*BRASS, 255))
	d.rectangle([2, 12, 13, 13], fill=(*BRASS, 255))
	d.rectangle([6, 6, 9, 10], fill=(*CORE_BLUE, 255))
	d.rectangle([7, 7, 8, 9], fill=(230, 250, 255, 255))
	d.rectangle([4, 6, 4, 10], fill=(*STEEL_DARK, 255))
	d.rectangle([11, 6, 11, 10], fill=(*STEEL_DARK, 255))
	return finish_icon(img, 201)


def hyperdrive_motivator():
	"""A coil-wrapped cylinder lying diagonally with a violet glow at its heart."""
	img = new()
	for i in range(11):
		x, y = 2 + i, 12 - i
		for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1), (-1, 0), (0, -1)):
			put(img, x + dx, y + dy, STEEL_DARK if i % 3 else BRASS)
	for i in range(3, 8):
		put(img, 2 + i, 12 - i, CORE_VIOLET)
	put(img, 7, 7, (240, 225, 255))
	put(img, 2, 14, STEEL)
	put(img, 13, 1, STEEL)
	return finish_icon(img, 202)


def nav_computer(loaded):
	"""A handheld console; blank screen, or lit with a star plot once it carries coordinates."""
	img = new()
	d = ImageDraw.Draw(img)
	d.rounded_rectangle([2, 3, 13, 13], 1, fill=(*STEEL_DARK, 255))
	d.rectangle([4, 5, 11, 10], fill=(*(SCREEN_GREEN if loaded else SCREEN_DARK), 255))
	if loaded:
		for x, y in ((5, 9), (7, 7), (10, 6)):
			put(img, x, y, (230, 255, 240))
		for x, y in ((6, 8), (8, 7), (9, 6)):
			put(img, x, y, (60, 150, 100))
	else:
		put(img, 5, 6, (50, 70, 60))
	for x in (5, 7, 9):
		put(img, x, 12, BRASS_LIGHT)
	return finish_icon(img, 203 if loaded else 204)


def hyperfuel_cell():
	"""A capped glass cell of glowing amber fuel."""
	img = new()
	d = ImageDraw.Draw(img)
	d.rectangle([5, 3, 10, 13], fill=(*STEEL_DARK, 255))
	d.rectangle([6, 5, 9, 11], fill=(255, 176, 64, 255))
	d.rectangle([7, 6, 7, 10], fill=(255, 230, 160, 255))
	d.rectangle([4, 2, 11, 3], fill=(*STEEL, 255))
	d.rectangle([4, 13, 11, 14], fill=(*STEEL, 255))
	return finish_icon(img, 205)


def passenger_seat():
	"""A padded crash couch with a harness."""
	img = new()
	d = ImageDraw.Draw(img)
	d.rectangle([4, 2, 10, 9], fill=(120, 108, 92, 255))
	d.rectangle([3, 9, 12, 12], fill=(120, 108, 92, 255))
	d.rectangle([3, 13, 4, 14], fill=(*STEEL_DARK, 255))
	d.rectangle([11, 13, 12, 14], fill=(*STEEL_DARK, 255))
	d.line([5, 3, 9, 8], fill=(*HAZARD, 255))
	d.line([9, 3, 5, 8], fill=(*HAZARD, 255))
	return finish_icon(img, 206)


def cargo_bay_extension():
	"""A cargo pod with hazard-striped ends."""
	img = new()
	d = ImageDraw.Draw(img)
	d.rectangle([2, 5, 13, 12], fill=(*METAL, 255))
	for x in (2, 3, 12, 13):
		for y in range(5, 13):
			put(img, x, y, HAZARD if (x + y) % 2 == 0 else DARK)
	d.rectangle([5, 7, 10, 10], fill=(*STEEL_DARK, 255))
	return finish_icon(img, 207)


def reinforced_plating():
	"""A thick bolted armour plate."""
	img = new()
	d = ImageDraw.Draw(img)
	d.polygon([(3, 2), (12, 2), (14, 6), (14, 13), (2, 13), (2, 6)], fill=(124, 128, 134, 255))
	d.line([2, 7, 14, 7], fill=(*STEEL_DARK, 255))
	for x, y in ((4, 4), (11, 4), (4, 11), (11, 11)):
		put(img, x, y, STEEL_LIGHT)
	return finish_icon(img, 208)


def fuel_tank_extension():
	"""A pair of banded ochre tanks."""
	img = new()
	d = ImageDraw.Draw(img)
	d.rounded_rectangle([2, 3, 7, 13], 2, fill=(*OCHRE, 255))
	d.rounded_rectangle([8, 3, 13, 13], 2, fill=(*OCHRE, 255))
	for y in (5, 9):
		d.line([2, y, 13, y], fill=(*METAL_DARK, 255))
	put(img, 4, 2, STEEL)
	put(img, 11, 2, STEEL)
	return finish_icon(img, 209)


ITEMS = {
	"power_core": power_core,
	"hyperdrive_motivator": hyperdrive_motivator,
	"hyperfuel_cell": hyperfuel_cell,
	"passenger_seat": passenger_seat,
	"cargo_bay_extension": cargo_bay_extension,
	"reinforced_plating": reinforced_plating,
	"fuel_tank_extension": fuel_tank_extension,
}


# ---------------------------------------------------------------- blocks

def metal_face(seed, base=METAL, dark=METAL_DARK, bolts=True):
	img = Image.new("RGBA", (16, 16), (0, 0, 0, 255))
	rnd = random.Random(seed)
	for y in range(16):
		for x in range(16):
			c = noisy(base, rnd, 6)
			if x in (0, 15) or y in (0, 15):
				c = shade(c, 0.8)
			img.putpixel((x, y), (*c, 255))
	if bolts:
		for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
			img.putpixel((x, y), (*dark, 255))
	return img


def beacon_base():
	img = metal_face(211, METAL_DARK, DARK)
	for x in range(16):
		if (x // 2) % 2 == 0:
			img.putpixel((x, 0), (*HAZARD, 255))
			img.putpixel((x, 15), (*HAZARD, 255))
	return img


def beacon_post():
	"""The post's sides; the front carries an arrow pointing toward the pad."""
	img = metal_face(212, STEEL, STEEL_DARK)
	for y in range(3, 13, 3):
		for x in range(3, 13):
			img.putpixel((x, y), (*shade(STEEL, 0.85), 255))
	return img


def beacon_post_front():
	img = beacon_post()
	for i, row in enumerate(range(4, 9)):
		for x in range(8 - i - 1, 8 + i + 1):
			img.putpixel((x, row), (*HAZARD, 255))
	for y in range(9, 13):
		for x in (7, 8):
			img.putpixel((x, y), (*HAZARD, 255))
	return img


def beacon_lamp_frames():
	"""Four frames of a slow, interpolated pulse (no hard on/off strobe)."""
	frames = []
	for level in (1.0, 0.7, 0.45, 0.7):
		img = Image.new("RGBA", (16, 16), (0, 0, 0, 255))
		for y in range(16):
			for x in range(16):
				r = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
				glow = max(0.0, 1 - r / 9) * level
				c = mix((70, 40, 20), (255, 196, 96), glow)
				if x in (0, 15) or y in (0, 15):
					c = METAL_DARK
				img.putpixel((x, y), (*c, 255))
		frames.append(img)
	sheet = Image.new("RGBA", (16, 16 * len(frames)), (0, 0, 0, 0))
	for i, f in enumerate(frames):
		sheet.paste(f, (0, 16 * i))
	return sheet


def terminal_screen():
	img = Image.new("RGBA", (16, 16), (0, 0, 0, 255))
	for y in range(16):
		for x in range(16):
			c = (20, 36, 52)
			if 1 <= x <= 14 and 1 <= y <= 14:
				c = mix((30, 70, 100), (90, 180, 230), 0.3 + 0.2 * math.sin(y * 0.9))
				if y % 3 == 0 and 3 <= x <= 12 and hash01(221, x, y) > 0.3:
					c = (170, 230, 255)
			else:
				c = METAL_DARK
			img.putpixel((x, y), (*c, 255))
	return img


def element(frm, to, textures, rotation=None):
	faces = {}
	for direction, (tex, uv) in textures.items():
		faces[direction] = {"uv": uv, "texture": tex}
	e = {"from": frm, "to": to, "faces": faces}
	if rotation:
		e["rotation"] = rotation
	return e


def box_faces(frm, to, side, top, bottom=None, front=None):
	"""Faces of an axis-aligned element with UVs cut from the element's own extent."""
	x0, y0, z0 = frm
	x1, y1, z1 = to
	bottom = bottom or top
	front = front or side
	return {
		"north": (front, [16 - x1, 16 - y1, 16 - x0, 16 - y0]),
		"south": (side, [x0, 16 - y1, x1, 16 - y0]),
		"east": (side, [16 - z1, 16 - y1, 16 - z0, 16 - y0]),
		"west": (side, [z0, 16 - y1, z1, 16 - y0]),
		"up": (top, [x0, z0, x1, z1]),
		"down": (bottom, [x0, 16 - z1, x1, 16 - z0]),
	}


def facing_variants(model):
	return {"variants": {
		"facing=north": {"model": model},
		"facing=east": {"model": model, "y": 90},
		"facing=south": {"model": model, "y": 180},
		"facing=west": {"model": model, "y": 270},
	}}


def write_beacon():
	write_png("block/landing_beacon_base.png", beacon_base())
	write_png("block/landing_beacon_post.png", beacon_post())
	write_png("block/landing_beacon_post_front.png", beacon_post_front())
	write_png("block/landing_beacon_lamp.png", beacon_lamp_frames())
	write_json("textures/block/landing_beacon_lamp.png.mcmeta", {"animation": {"frametime": 15, "interpolate": True}})
	base, post, front, lamp = (f"#{n}" for n in ("base", "post", "front", "lamp"))
	model = {
		"parent": "minecraft:block/block",
		"textures": {
			"base": f"{NS}:block/landing_beacon_base",
			"post": f"{NS}:block/landing_beacon_post",
			"front": f"{NS}:block/landing_beacon_post_front",
			"lamp": f"{NS}:block/landing_beacon_lamp",
			"particle": f"{NS}:block/landing_beacon_post",
		},
		"elements": [
			element([3, 0, 3], [13, 2, 13], box_faces([3, 0, 3], [13, 2, 13], base, base)),
			element([5, 2, 5], [11, 10, 11], box_faces([5, 2, 5], [11, 10, 11], post, post, post, front)),
			element([5.5, 10, 5.5], [10.5, 13, 10.5], box_faces([5.5, 10, 5.5], [10.5, 13, 10.5], lamp, lamp)),
		],
	}
	write_json("models/block/landing_beacon.json", model)
	write_json("blockstates/landing_beacon.json", facing_variants(f"{NS}:block/landing_beacon"))
	write_json("items/landing_beacon.json", {"model": {"type": "minecraft:model", "model": f"{NS}:block/landing_beacon"}})


def write_terminal():
	write_png("block/pad_terminal_base.png", metal_face(231, METAL_DARK, DARK))
	write_png("block/pad_terminal_body.png", metal_face(232, STEEL, STEEL_DARK))
	write_png("block/pad_terminal_screen.png", terminal_screen())
	base, body, screen = "#base", "#body", "#screen"
	model = {
		"parent": "minecraft:block/block",
		"textures": {
			"base": f"{NS}:block/pad_terminal_base",
			"body": f"{NS}:block/pad_terminal_body",
			"screen": f"{NS}:block/pad_terminal_screen",
			"particle": f"{NS}:block/pad_terminal_body",
		},
		"elements": [
			element([2, 0, 2], [14, 3, 14], box_faces([2, 0, 2], [14, 3, 14], base, base)),
			element([4, 3, 5], [12, 11, 11], box_faces([4, 3, 5], [12, 11, 11], body, body)),
			# The console top, tilted toward the user standing on the north side.
			element([2, 11, 3], [14, 14, 13], box_faces([2, 11, 3], [14, 14, 13], body, screen, body),
					{"origin": [8, 12.5, 8], "axis": "x", "angle": -22.5}),
		],
	}
	write_json("models/block/pad_terminal.json", model)
	write_json("blockstates/pad_terminal.json", facing_variants(f"{NS}:block/pad_terminal"))
	write_json("items/pad_terminal.json", {"model": {"type": "minecraft:model", "model": f"{NS}:block/pad_terminal"}})


# ---------------------------------------------------------------- main

def flat_item(name, texture=None):
	texture = texture or name
	write_json(f"models/item/{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{texture}"}})


def main():
	check_layout()
	check_layout(INTERCEPTOR_PARTS, I_TEX_W, I_TEX_H)
	for paint in PAINTS:
		write_png(f"entity/ship/courier_shuttle_{paint}.png", shuttle(paint))
	write_png("entity/ship/courier_shuttle_lights.png", lights(False))
	write_png("entity/ship/courier_shuttle_lights_damaged.png", lights(True))
	write_png("entity/ship/courier_shuttle_engines.png", engines())
	write_png("entity/ship/courier_shuttle_scorch.png", scorch())
	write_png("gui/destination_map.png", destination_map())

	for paint in PAINTS:
		write_png(f"entity/ship/interceptor_{paint}.png", interceptor(paint))
	write_png("entity/ship/interceptor_lights.png", interceptor_lights(False))
	write_png("entity/ship/interceptor_lights_damaged.png", interceptor_lights(True))
	write_png("entity/ship/interceptor_engines.png", interceptor_engines())
	write_png("entity/ship/interceptor_scorch.png", interceptor_scorch())
	for i in range(3):
		write_png(f"particle/atmosphere_streak_{i}.png", atmosphere_streak_frame(i))
	write_json("particles/atmosphere_streak.json", {"textures": [f"{NS}:atmosphere_streak_{i}" for i in range(3)]})

	for i in range(4):
		write_png(f"particle/engine_exhaust_{i}.png", exhaust_frame(i))
	for i in range(2):
		write_png(f"particle/spark_shower_{i}.png", spark_frame(i))
	write_json("particles/engine_exhaust.json", {"textures": [f"{NS}:engine_exhaust_{i}" for i in range(4)]})
	write_json("particles/spark_shower.json", {"textures": [f"{NS}:spark_shower_{i}" for i in range(2)]})

	for name, make in ITEMS.items():
		write_png(f"item/{name}.png", make())
		flat_item(name)
		write_json(f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/{name}"}})
	write_png("item/nav_computer.png", nav_computer(False))
	write_png("item/nav_computer_loaded.png", nav_computer(True))
	flat_item("nav_computer")
	flat_item("nav_computer_loaded")
	write_json("items/nav_computer.json", {"model": {
		"type": "minecraft:condition",
		"property": "minecraft:has_component",
		"component": f"{NS}:nav_data",
		"on_true": {"type": "minecraft:model", "model": f"{NS}:item/nav_computer_loaded"},
		"on_false": {"type": "minecraft:model", "model": f"{NS}:item/nav_computer"},
	}})

	write_beacon()
	write_terminal()


if __name__ == "__main__":
	main()
