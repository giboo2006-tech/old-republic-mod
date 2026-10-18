#!/usr/bin/env python3
"""Generates the Milestone 4 hub people, dialogue portraits, story items and hub blocks
(W2: Chapter Four, vendors, economy). PLACEHOLDER art, original, deterministic.

Writes into src/main/resources/assets/oldrepublic/:
  textures/entity/hub/<variant>.png       64x64 skins for the hub people (MentorModel layout, below):
                                          ansa, kaddor, zarvos (the echo), apprentice_0..2, acolyte_0..3,
                                          proving_guard, watch_quartermaster, imperial_quartermaster, spacer,
                                          trainer_armstech, trainer_armourcraft, trainer_artifice, trainer_medtech
  textures/gui/sprites/portrait/<id>.png  32x32 dialogue portraits: ansa, kaddor, kaast, mandalorian, smuggler,
                                          quartermaster_watch, quartermaster_imperial, spacer, trainer,
                                          apprentice, acolyte
  textures/item/<id>.png                  16x16: credit_chit, credit_bundle, kaast_pattern, tomb_relic, sealed_contract
  textures/block/contract_terminal_{front,side,top}.png, marker_beacon_{post,lamp,base}.png   16x16
  items/, models/item/, models/block/, blockstates/   the JSON for those items and the two blocks

The skins follow com.oldrepublic.client.mentor.MentorModel (the robed humanoid the hub people
share): the vanilla humanoid parts (head 0,0; hat 32,0, used for helmets, caps and goggles; body
16,16; arms 40,16, the left arm mirrors the right; legs 0,16, mirrored likewise), the robe 16,32
(8x14x4), the skirt 0,50 (9x9x5), the hood 36,50 (9x3x5) and the cape 40,32 (8x16x1). The
renderer shows the hood or the cape, never both (HubNpcRenderer.hooded); a layer left transparent
is not drawn (cutout), which is how coats, armour and aprons leave the legs bare.

The designs are generic and original: robed keepers and acolytes, a helmeted guard with a plain
visor band, quartermasters and trainers in work clothes. The hunters' helmets (Kaast and the
Mandalorian portrait) are rounded with a straight horizontal visor slit, deliberately not any film
or game design.

Run from the repository root: python3 tools/assets/gen_textures_hub.py
"""
import json
import math
import os
import random

from PIL import Image

ASSETS = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "src", "main", "resources", "assets", "oldrepublic")
TEXTURES = os.path.join(ASSETS, "textures")


def out(rel, img):
	path = os.path.join(TEXTURES, rel)
	os.makedirs(os.path.dirname(path), exist_ok=True)
	img.save(path)
	print("wrote", os.path.normpath(path))


def out_json(rel, data, tabs=True):
	path = os.path.join(ASSETS, rel)
	os.makedirs(os.path.dirname(path), exist_ok=True)

	with open(path, "w", encoding="utf-8") as f:
		f.write(json.dumps(data, indent="\t" if tabs else 2) + "\n")

	print("wrote", os.path.normpath(path))


def clamp(v):
	return max(0, min(255, int(round(v))))


def noisy(rgb, rnd, amount):
	return tuple(clamp(c + rnd.uniform(-amount, amount)) for c in rgb)


def shade(rgb, factor):
	return tuple(clamp(c * factor) for c in rgb)


def mix(a, b, t):
	return tuple(clamp(a[i] * (1 - t) + b[i] * t) for i in range(3))


def opaque(rgb):
	return tuple(rgb[:3]) + (255,)


# ---------------------------------------------------------------- box painting (vanilla cube layout)

def face_rects(u, v, w, h, d):
	"""Texture rectangles (x, y, width, height) of a model box."""
	return {
		"top": (u + d, v, w, d),
		"bottom": (u + d + w, v, w, d),
		"east": (u, v + d, d, h),
		"front": (u + d, v + d, w, h),
		"west": (u + d + w, v + d, d, h),
		"back": (u + d + w + d, v + d, w, h),
	}


def paint_box(img, u, v, w, h, d, colour):
	"""Fills every face of a box; colour(face, x, y, fw, fh) returns an RGBA tuple or None (left clear)."""
	px = img.load()

	for face, (fx, fy, fw, fh) in face_rects(u, v, w, h, d).items():
		for y in range(fh):
			for x in range(fw):
				c = colour(face, x, y, fw, fh)

				if c is not None:
					px[fx + x, fy + y] = c


def fabric(base, rnd, amount=7):
	"""A woven colour with a little noise and darker vertical folds."""
	def colour(face, x, y, fw, fh):
		fold = 0.9 if (x % 3 == 2 and face in ("front", "back")) else 1.0
		return opaque(noisy(shade(base, fold), rnd, amount))
	return colour


# ---------------------------------------------------------------- people

# Shared colours. Watch people wear forest greens, bark browns and undyed cream; the Empire's
# people wear greys and black with a red sash or band.
CREAM = (190, 180, 152)
BARK = (92, 70, 48)
LEATHER = (116, 82, 52)
BOOTS = (56, 42, 32)
IMP_GREY = (76, 80, 88)
IMP_DARK = (42, 44, 50)
IMP_BLACK = (24, 24, 28)
IMP_RED = (148, 34, 38)


def person(**kw):
	"""A person's look with defaults: see the module docstring for the layers each field paints."""
	p = {
		"skin": (196, 150, 118), "hair": (70, 54, 40), "hair_style": "short", "grey": None, "eyes": (70, 80, 70),
		"beard": None, "scar": False, "lines": False, "grey_mode": "streaks",
		"style": "robe", "garment": (120, 110, 90), "under": (70, 60, 50), "legs": None, "belt": BARK, "boots": BOOTS,
		"gloves": None, "trim": None, "hood": None, "cape": None, "skirt": None,
		"hat": None, "hat_colour": (60, 60, 64), "visor": (30, 30, 34), "lens": (120, 170, 180), "badge": None,
	}
	p.update(kw)

	if p["legs"] is None:
		p["legs"] = p["under"]

	return p


def head(img, P, rnd):
	skin, hair, style = P["skin"], P["hair"], P["hair_style"]
	grey = P["grey"]

	def hair_px(x, y, face="front"):
		# Grey as fine streaks through the hair, or only at the temples (the sides).
		if P["grey_mode"] == "temples":
			streak = grey is not None and face in ("east", "west") and y >= 1
		else:
			streak = grey is not None and x % 3 == 1 and y % 3 != 2
		return opaque(noisy(grey if streak else hair, rnd, 7))

	def colour(face, x, y, fw, fh):
		if face == "top":
			return hair_px(x, y, face) if style != "bald" else opaque(noisy(shade(skin, 0.95), rnd, 4))
		if face == "bottom":
			if P["beard"] is not None:
				return opaque(noisy(P["beard"], rnd, 6))
			return opaque(shade(skin, 0.8))
		if face == "front":
			if y < (1 if style in ("cropped", "bald") else 2) and style != "bald":
				return hair_px(x, y)
			if y == 3 and x in (1, 2, 5, 6):
				if P["scar"] and x == 5:
					return (226, 200, 186, 255)
				return opaque(shade(hair if style != "bald" else skin, 0.7))
			if y == 4 and x in (2, 5):
				return opaque(P["eyes"])
			if y == 4 and x in (1, 6):
				return (234, 230, 224, 255)
			if P["scar"] and x == 5 and y in (2, 5):
				return (226, 200, 186, 255)
			if P["lines"] and y == 5 and x in (1, 6):
				return opaque(shade(skin, 0.86))
			if P["beard"] is not None and (y >= 6 or (y == 5 and x in (0, 7))):
				if y == 6 and 3 <= x <= 4:
					return opaque(shade(P["beard"], 0.7))
				return opaque(noisy(P["beard"], rnd, 6))
			if y == 6 and 3 <= x <= 4:
				return opaque(shade(skin, 0.74))
			return opaque(noisy(skin, rnd, 4))
		# Sides and back.
		if style == "bald":
			return opaque(noisy(shade(skin, 0.95), rnd, 4))
		limit = {"cropped": 2, "short": 3, "tied": fh, "long": fh}.get(style, 3)
		if face == "back" and style in ("short", "cropped"):
			limit += 2
		if y < limit:
			if style == "tied" and face == "back" and 3 <= x <= 4 and y in (4, 5):
				return opaque(shade(hair, 0.65))
			return hair_px(x, y, face)
		if P["beard"] is not None and face in ("east", "west") and y >= 5:
			return opaque(noisy(P["beard"], rnd, 6))
		return opaque(noisy(skin, rnd, 4))

	paint_box(img, 0, 0, 8, 8, 8, colour)


def hat(img, P, rnd):
	"""The head's outer layer (32,0): a helmet, a cap, goggles or nothing."""
	kind = P["hat"]

	if kind is None:
		return

	c, visor, lens = P["hat_colour"], P["visor"], P["lens"]

	def colour(face, x, y, fw, fh):
		if kind == "helmet":
			if face == "bottom":
				return None
			if face == "front":
				if y < 2:
					return opaque(noisy(shade(c, 1.05), rnd, 5))
				if y in (3, 4):
					return opaque(noisy(visor, rnd, 4)) if 1 <= x <= 6 else opaque(noisy(c, rnd, 5))
				if y == 2:
					return opaque(shade(c, 0.8))
				# Cheek guards; the mouth and chin stay bare.
				return opaque(noisy(shade(c, 0.9), rnd, 5)) if x in (0, 7) else None
			if face == "top":
				return opaque(noisy(c, rnd, 5)) if not (3 <= x <= 4) else opaque(shade(c, 0.78))
			return opaque(noisy(shade(c, 0.95 if y % 4 else 0.82), rnd, 5))
		if kind == "cap":
			if face == "top":
				return opaque(noisy(c, rnd, 5))
			if face == "bottom":
				return None
			if y < 2:
				return opaque(noisy(c, rnd, 5)) if not (face == "front" and y == 1 and 2 <= x <= 5 and P["badge"]) else opaque(P["badge"])
			if face == "front" and y == 2:
				return opaque(shade(c, 0.7))
			return None
		if kind == "goggles":
			if face in ("top", "bottom"):
				return None
			if y == 2:
				if face == "front" and x in (1, 2, 5, 6):
					return opaque(noisy(lens, rnd, 6)) if x in (2, 5) else opaque(shade(lens, 0.75))
				return opaque(noisy(c, rnd, 5))
			if y == 3 and face == "front" and x in (1, 2, 5, 6):
				return opaque(shade(lens, 0.6))
			return None
		return None

	paint_box(img, 32, 0, 8, 8, 8, colour)


def body(img, P, rnd):
	under, belt = P["under"], P["belt"]

	def colour(face, x, y, fw, fh):
		if face in ("top", "bottom"):
			return opaque(noisy(under, rnd, 5))
		if y in (7, 8):
			if face == "front" and y == 7 and x in (3, 4):
				return (150, 140, 120, 255)
			return opaque(noisy(belt, rnd, 6))
		if P["badge"] is not None and face == "front" and y == 2 and x in (5, 6):
			return opaque(P["badge"])
		return opaque(noisy(under, rnd, 6))

	paint_box(img, 16, 16, 8, 12, 4, colour)


def arm(img, P, rnd):
	sleeve = P["garment"] if P["style"] in ("robe", "coat", "tunic") else P["under"]
	hand = P["gloves"] if P["gloves"] is not None else P["skin"]
	trim = P["trim"]

	def colour(face, x, y, fw, fh):
		if face == "bottom" or y >= fh - 2:
			return opaque(noisy(hand, rnd, 5))
		if trim is not None and y == fh - 3:
			return opaque(noisy(trim, rnd, 5))
		if P["style"] == "armour" and y < 4:
			return opaque(noisy(shade(P["garment"], 1.05 if y else 1.15), rnd, 5))
		return opaque(noisy(shade(sleeve, 0.95), rnd, 7))

	paint_box(img, 40, 16, 4, 12, 4, colour)


def leg(img, P, rnd):
	def colour(face, x, y, fw, fh):
		if y >= fh - 4 or face == "bottom":
			return opaque(noisy(P["boots"], rnd, 5))
		if P["style"] == "armour" and 4 <= y <= 6 and face == "front":
			return opaque(noisy(P["garment"], rnd, 5))
		return opaque(noisy(P["legs"], rnd, 6))

	paint_box(img, 0, 16, 4, 12, 4, colour)


def robe_layer(P, rnd):
	"""The outer layer over the body (16,32, 8x14x4) in the person's style."""
	style, g, belt, trim = P["style"], P["garment"], P["belt"], P["trim"]

	def colour(face, x, y, fw, fh):
		if face in ("top", "bottom"):
			return None
		if style == "robe":
			if y in (7, 8):
				return opaque(noisy(belt, rnd, 6))
			if face == "front" and 3 <= x <= 4:
				return None
			if trim is not None and face == "front" and x in (2, 5):
				return opaque(noisy(trim, rnd, 5))
			return opaque(noisy(shade(g, 0.88 if x % 3 == 0 else 1.0), rnd, 6))
		if style == "coat":
			if y > 11:
				return None
			if y in (7, 8):
				return opaque(noisy(belt, rnd, 6))
			if face == "front" and x == 4 and y < 7:
				return opaque(shade(g, 0.7)) if y % 2 else (170, 160, 130, 255)
			if trim is not None and (y == 0 or (face == "front" and x == 3 and y < 7)):
				return opaque(noisy(trim, rnd, 5))
			return opaque(noisy(shade(g, 0.92 if x % 4 == 0 else 1.0), rnd, 6))
		if style == "tunic":
			if y > 10:
				return None
			if y in (7, 8):
				return opaque(noisy(belt, rnd, 6))
			if trim is not None and y == 10:
				return opaque(noisy(trim, rnd, 5))
			if face == "front" and y < 3 and 3 <= x <= 4:
				return opaque(shade(g, 0.75))
			return opaque(noisy(g, rnd, 6))
		if style == "armour":
			if y > 9:
				return None
			if y in (7, 8):
				return opaque(noisy(belt, rnd, 5))
			if face == "front" and y < 6:
				plate = shade(g, 1.12 if 1 <= x <= 6 and y < 4 else 0.95)
				if y == 4 and trim is not None and 2 <= x <= 5:
					return opaque(trim)
				return opaque(noisy(plate, rnd, 4))
			return opaque(noisy(shade(g, 0.9 if y % 3 == 2 else 1.0), rnd, 5))
		if style == "apron":
			if face == "front":
				if y == 0 and x in (1, 6):
					return opaque(shade(g, 0.8))
				if 1 <= x <= 6 and 1 <= y <= 12:
					if y == 9 and 2 <= x <= 5:
						return opaque(shade(g, 0.72))
					return opaque(noisy(g, rnd, 6))
				return None
			if y in (7, 8):
				return opaque(noisy(shade(g, 0.8), rnd, 5))
			if face == "back" and y < 7 and x in (1, 6):
				return opaque(shade(g, 0.8))
			return None
		return None

	return colour


def outer_layers(img, P, rnd):
	paint_box(img, 16, 32, 8, 14, 4, robe_layer(P, rnd))

	if P["skirt"] is not None:
		paint_box(img, 0, 50, 9, 9, 5, fabric(P["skirt"], rnd))

	if P["hood"] is not None:
		paint_box(img, 36, 50, 9, 3, 5, fabric(P["hood"], rnd, 6))

	if P["cape"] is not None:
		paint_box(img, 40, 32, 8, 16, 1, fabric(P["cape"], rnd, 4))


def skin_texture(P, seed):
	rnd = random.Random(seed)
	img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
	head(img, P, rnd)
	hat(img, P, rnd)
	body(img, P, rnd)
	arm(img, P, rnd)
	leg(img, P, rnd)
	outer_layers(img, P, rnd)
	return img


WATCH_GREEN = (72, 96, 66)
WATCH_MOSS = (100, 118, 80)

PEOPLE = {
	# Keeper Ansa Veylin: last keeper of the Greenwatch, patient and tired. Grey hair tied back,
	# moss over-robe open over undyed cream, a deep green hood.
	"ansa": person(skin=(164, 118, 90), hair=(146, 142, 136), grey=(196, 192, 186), hair_style="tied", eyes=(70, 96, 72), lines=True,
				style="robe", garment=WATCH_MOSS, under=(176, 164, 134), legs=(92, 80, 62), belt=BARK, hood=(66, 88, 60), skirt=(88, 106, 72)),
	# Overseer Vael Kaddor: runs the Proving's trials. Cropped dark hair greying at the temples, a
	# scarred brow, a grey uniform coat with red trim, black gloves and a black half-cape.
	"kaddor": person(skin=(212, 180, 158), hair=(54, 52, 52), grey=(150, 148, 146), grey_mode="temples", hair_style="cropped", eyes=(110, 84, 62), scar=True,
				style="coat", garment=IMP_GREY, under=IMP_DARK, legs=(48, 50, 56), belt=IMP_BLACK, boots=IMP_BLACK, gloves=IMP_BLACK,
				trim=IMP_RED, cape=IMP_BLACK, skirt=(58, 60, 66), badge=(190, 170, 110)),
	# The Sepulchre's echo of the gatekeeper Zarvos: cold violet greys, eyes like embers.
	"zarvos": person(skin=(150, 144, 160), hair=(38, 34, 46), hair_style="long", eyes=(230, 186, 96),
				style="robe", garment=(62, 54, 76), under=(36, 32, 44), belt=(24, 22, 30), boots=(22, 20, 28), gloves=(30, 26, 36),
				trim=(120, 70, 140), cape=(28, 24, 36), skirt=(50, 44, 60)),
	# The Greenwatch's apprentices: young, hooded, in plain sage and sand tunics.
	"apprentice_0": person(skin=(230, 192, 162), hair=(182, 132, 72), hair_style="short", eyes=(80, 110, 130),
				style="tunic", garment=(150, 160, 118), under=CREAM, legs=(110, 96, 74), belt=LEATHER, hood=(122, 132, 96)),
	"apprentice_1": person(skin=(120, 84, 60), hair=(30, 26, 24), hair_style="tied", eyes=(60, 44, 32),
				style="tunic", garment=(172, 150, 108), under=(160, 150, 124), legs=(96, 84, 64), belt=BARK, hood=(140, 122, 86)),
	"apprentice_2": person(skin=(190, 140, 100), hair=(112, 62, 40), hair_style="short", eyes=(76, 96, 60),
				style="tunic", garment=(136, 150, 128), under=CREAM, legs=(100, 92, 76), belt=LEATHER, hood=(110, 124, 104)),
	# The Proving's acolytes: dark grey robes, a red sash, a black half-cape.
	"acolyte_0": person(skin=(226, 196, 176), hair=(22, 22, 24), hair_style="cropped", eyes=(90, 70, 60),
				style="robe", garment=(58, 60, 66), under=(36, 36, 42), belt=(118, 32, 36), boots=IMP_BLACK, cape=(24, 24, 28), skirt=(46, 48, 54)),
	"acolyte_1": person(skin=(96, 66, 50), hair=(20, 18, 18), hair_style="bald", eyes=(54, 40, 30),
				style="robe", garment=(62, 62, 70), under=(34, 34, 40), belt=(118, 32, 36), boots=IMP_BLACK, cape=(24, 24, 28), skirt=(46, 48, 54)),
	"acolyte_2": person(skin=(200, 160, 130), hair=(132, 42, 32), hair_style="short", eyes=(70, 90, 70),
				style="robe", garment=(54, 56, 62), under=(36, 36, 42), belt=(118, 32, 36), boots=IMP_BLACK, cape=(24, 24, 28), skirt=(44, 46, 52)),
	"acolyte_3": person(skin=(172, 122, 96), hair=(204, 194, 172), hair_style="short", eyes=(100, 110, 130),
				style="robe", garment=(60, 58, 64), under=(34, 34, 40), belt=(118, 32, 36), boots=IMP_BLACK, cape=(24, 24, 28), skirt=(44, 46, 52)),
	# An Imperial guard: a plain helmet with a dark visor band, armour plates over a dark suit.
	"proving_guard": person(skin=(200, 164, 136), hair=(40, 34, 30), hair_style="cropped", eyes=(70, 70, 70),
				style="armour", garment=(92, 96, 104), under=(40, 42, 48), legs=(40, 42, 48), belt=IMP_BLACK, boots=(20, 20, 24),
				gloves=(30, 30, 34), trim=IMP_RED, cape=(26, 26, 30), hat="helmet", hat_colour=(84, 88, 96), visor=(46, 18, 20)),
	# The Watch quartermaster: a leather apron over a green tunic, a full beard.
	"watch_quartermaster": person(skin=(200, 156, 120), hair=(120, 82, 52), hair_style="short", beard=(108, 72, 46), eyes=(70, 80, 60),
				style="apron", garment=LEATHER, under=WATCH_GREEN, legs=(86, 74, 58), belt=BARK, hood=(80, 96, 66)),
	# The Imperial quartermaster: a grey coat with a red armband stripe, a peaked cap.
	"imperial_quartermaster": person(skin=(182, 140, 110), hair=(92, 72, 52), hair_style="short", eyes=(80, 70, 60),
				style="coat", garment=(84, 88, 96), under=IMP_DARK, legs=(54, 56, 62), belt=IMP_BLACK, boots=IMP_BLACK,
				trim=IMP_RED, cape=(30, 30, 34), skirt=(64, 68, 74), hat="cap", hat_colour=(64, 66, 74), badge=(176, 150, 96)),
	# A spacer: a worn flight jacket with a pale collar, goggles on the brow, a scarf (or a duster on Varkhast).
	"spacer": person(skin=(186, 140, 104), hair=(64, 42, 28), hair_style="short", eyes=(80, 100, 90),
				style="coat", garment=(148, 96, 54), under=(70, 74, 64), legs=(90, 84, 70), belt=(60, 44, 30),
				trim=(186, 158, 116), hood=(140, 120, 90), cape=(96, 74, 52), hat="goggles", hat_colour=(52, 42, 32), lens=(120, 180, 190)),
	# The trainers: work clothes of their trade.
	"trainer_armstech": person(skin=(140, 96, 70), hair=(30, 28, 26), hair_style="cropped", beard=(40, 36, 32), eyes=(60, 50, 40),
				style="tunic", garment=(96, 92, 72), under=(72, 70, 58), legs=(80, 76, 62), belt=(72, 52, 32),
				hood=(80, 80, 70), cape=(42, 42, 46), hat="goggles", hat_colour=(40, 36, 32), lens=(180, 70, 46)),
	"trainer_armourcraft": person(skin=(220, 184, 156), hair=(160, 152, 142), hair_style="short", beard=(150, 144, 136), eyes=(80, 90, 100), lines=True,
				style="apron", garment=(72, 60, 52), under=(112, 104, 94), legs=(70, 64, 58), belt=(50, 40, 32), gloves=(70, 56, 42),
				hood=(96, 90, 80), cape=(36, 36, 40)),
	"trainer_artifice": person(skin=(110, 78, 60), hair=(22, 20, 20), hair_style="long", eyes=(60, 50, 44),
				style="robe", garment=(70, 84, 112), under=(40, 46, 60), legs=(46, 50, 62), belt=(36, 40, 54), trim=(140, 200, 222),
				hood=(60, 72, 98), cape=(40, 46, 62), skirt=(62, 74, 100)),
	"trainer_medtech": person(skin=(236, 200, 176), hair=(182, 122, 62), hair_style="tied", eyes=(70, 110, 100),
				style="tunic", garment=(202, 208, 202), under=(92, 122, 122), legs=(110, 120, 118), belt=(80, 100, 100), trim=(60, 150, 150),
				hood=(172, 182, 178), cape=(60, 80, 80)),
}


# ---------------------------------------------------------------- portraits (32x32)

BG = {
	"watch": ((30, 40, 32), (58, 72, 50)),
	"empire": ((22, 22, 28), (48, 26, 30)),
	"frontier": ((34, 32, 30), (68, 56, 44)),
	"hunter": ((24, 20, 18), (72, 44, 30)),
}


def ellipse_in(x, y, cx, cy, rx, ry):
	dx = (x - cx) / rx
	dy = (y - cy) / ry
	return dx * dx + dy * dy <= 1.0


def portrait(P, seed, bg, rim, hood_up=False, helmet=None):
	"""Head and shoulders on a dim gradient. helmet: None or {"base", "panel", "visor", "antenna"}."""
	rnd = random.Random(seed)
	img = Image.new("RGBA", (32, 32), (0, 0, 0, 255))
	back = img.load()
	top, bottom = BG[bg]

	for y in range(32):
		row = mix(top, bottom, y / 31)
		for x in range(32):
			back[x, y] = opaque(noisy(row, rnd, 3))

	# The figure is drawn on its own layer, so the rim light can find its outline.
	figure = blank(32)
	px = figure.load()
	garment = P["garment"] if P["style"] != "apron" else P["under"]

	# Shoulders, the garment's neckline and trim.
	for y in range(23, 32):
		half = 9 + (y - 23)
		for x in range(16 - half, 16 + half):
			if 0 <= x < 32:
				px[x, y] = opaque(noisy(garment, rnd, 6))
	for y in range(24, 32):
		for x in range(14, 18):
			px[x, y] = opaque(noisy(P["under"], rnd, 4))
	if P["trim"] is not None:
		for y in range(24, 32):
			for x in (13, 18):
				px[x, y] = opaque(P["trim"])
	if P["style"] == "apron":
		for y in range(26, 32):
			for x in range(11, 21):
				px[x, y] = opaque(noisy(P["garment"], rnd, 5))
		for y in range(23, 26):
			px[11, y] = px[20, y] = opaque(shade(P["garment"], 0.8))
	if P["style"] == "armour":
		for y in range(25, 32):
			for x in range(9, 23):
				px[x, y] = opaque(noisy(shade(P["garment"], 1.1 if y < 29 else 0.95), rnd, 4))
	if P["badge"] is not None:
		px[21, 27] = px[22, 27] = opaque(P["badge"])
	if P["hood"] is not None and not hood_up and helmet is None:
		for y in range(21, 25):
			for x in range(8, 24):
				if abs(x - 15.5) > 3:
					px[x, y] = opaque(noisy(P["hood"], rnd, 6))

	if hood_up and P["hood"] is not None:
		for y in range(2, 26):
			for x in range(5, 27):
				if ellipse_in(x, y, 15.5, 14, 10.5, 12.5):
					edge = not ellipse_in(x, y, 15.5, 14, 9.5, 11.5)
					px[x, y] = opaque(noisy(shade(P["hood"], 1.1 if edge else 0.95), rnd, 5))

	if helmet is not None:
		draw_helmet(px, rnd, helmet)
	else:
		draw_face(px, rnd, P, hood_up)

	# A thin rim light in the speaker's accent along the right edge of the head and shoulders.
	for y in range(2, 32):
		for x in range(31, 15, -1):
			if px[x, y][3] > 0:
				px[x, y] = opaque(mix(px[x, y], rim, 0.5))
				break

	img.alpha_composite(figure)
	return img


def draw_face(px, rnd, P, hood_up):
	skin, hair, style = P["skin"], P["hair"], P["hair_style"]

	for y in range(19, 24):
		for x in range(14, 18):
			px[x, y] = opaque(noisy(shade(skin, 0.85), rnd, 3))
	for y in range(6, 20):
		for x in range(10, 22):
			if ellipse_in(x, y, 15.5, 12.5, 6.0, 7.5):
				px[x, y] = opaque(noisy(skin, rnd, 4))

	grey = P["grey"]
	if style != "bald":
		for y in range(4, 10):
			for x in range(9, 23):
				line = 9 - (0 if x in (9, 10, 21, 22) else 1) - (1 if style == "cropped" else 0)
				if ellipse_in(x, y, 15.5, 9.5, 7.0, 6.0) and y < line and not (hood_up and y < 6):
					streak = grey is not None and (x in (9, 10, 21, 22) if P["grey_mode"] == "temples" else x % 3 == 1)
					px[x, y] = opaque(noisy(grey if streak else hair, rnd, 6))
		if style in ("long", "tied"):
			for y in range(9, 19 if style == "long" else 14):
				for x in (9, 10, 21, 22):
					px[x, y] = opaque(noisy(hair, rnd, 6))
	else:
		for x in range(11, 21):
			px[x, 6] = opaque(shade(skin, 1.06))

	for x in (12, 13, 18, 19):
		px[x, 11] = opaque(shade(hair if style != "bald" else skin, 0.7))
	px[13, 12] = opaque(P["eyes"])
	px[18, 12] = opaque(P["eyes"])
	px[12, 12] = (230, 226, 220, 255)
	px[19, 12] = (230, 226, 220, 255)
	px[15, 14] = px[16, 14] = opaque(shade(skin, 0.88))
	for x in range(14, 18):
		px[x, 16] = opaque(shade(skin, 0.7))
	if P["lines"]:
		px[11, 13] = px[20, 13] = opaque(shade(skin, 0.86))
		px[13, 17] = px[18, 17] = opaque(shade(skin, 0.88))
	if P["scar"]:
		for y in (10, 11, 12, 13):
			px[18 if y != 12 else 17, y] = (228, 204, 190, 255)
	if P["beard"] is not None:
		for y in range(14, 21):
			for x in range(10, 22):
				if ellipse_in(x, y, 15.5, 15.5, 6.2, 5.5) and (y >= 16 or x in (10, 11, 20, 21)) and not (y == 16 and 14 <= x <= 17):
					px[x, y] = opaque(noisy(P["beard"], rnd, 6))
	hat = P["hat"]
	if hat == "goggles":
		for x in range(9, 23):
			px[x, 8] = opaque(noisy(P["hat_colour"], rnd, 4))
		for cx in (13, 18):
			for dx in (-1, 0, 1):
				for dy in (0, 1):
					px[cx + dx, 7 + dy] = opaque(shade(P["lens"], 1.0 if dy == 0 and dx < 1 else 0.7))
	elif hat == "cap":
		for y in range(3, 9):
			for x in range(9, 23):
				if ellipse_in(x, y, 15.5, 8.5, 7.2, 5.5) and y <= 8:
					px[x, y] = opaque(noisy(P["hat_colour"], rnd, 4))
		for x in range(9, 23):
			px[x, 9] = opaque(shade(P["hat_colour"], 0.6))
		if P["badge"] is not None:
			px[15, 6] = px[16, 6] = opaque(P["badge"])
	elif hat == "helmet":
		for y in range(3, 12):
			for x in range(8, 24):
				if ellipse_in(x, y, 15.5, 10, 8.0, 7.5) and y < 12:
					px[x, y] = opaque(noisy(P["hat_colour"], rnd, 4))
		for x in range(10, 22):
			px[x, 11] = px[x, 12] = opaque(noisy(P["visor"], rnd, 3))
		for y in range(12, 18):
			px[9, y] = px[22, y] = opaque(shade(P["hat_colour"], 0.9))


def draw_helmet(px, rnd, H):
	"""A rounded hunter's helmet with a straight visor slit, worn paint and scratches."""
	base, panel, visor = H["base"], H["panel"], H["visor"]

	for y in range(19, 25):
		for x in range(12, 20):
			px[x, y] = opaque(noisy((40, 38, 36), rnd, 3))
	for y in range(3, 22):
		for x in range(8, 24):
			if ellipse_in(x, y, 15.5, 12.0, 7.6, 9.5):
				shine = 1.12 if ellipse_in(x, y, 13, 7, 3.0, 2.5) else 1.0
				c = panel if (y < 8 and 12 <= x <= 19) else base
				px[x, y] = opaque(noisy(shade(c, shine), rnd, 5))
	# The visor: one straight slit across the eyes, a faint lit edge.
	for x in range(10, 22):
		px[x, 11] = opaque(noisy(visor, rnd, 3))
		px[x, 12] = opaque(noisy(shade(visor, 0.8), rnd, 3))
	px[11, 11] = opaque(mix(visor, (230, 120, 70), 0.5))
	# Cheek seams and a chin vent of three short lines.
	for y in range(14, 20):
		px[11, y] = opaque(shade(base, 0.72))
		px[20, y] = opaque(shade(base, 0.72))
	for x in (14, 16, 18):
		px[x, 17] = px[x, 18] = opaque(shade(base, 0.6))
	# Scratches and chipped paint.
	for _ in range(7):
		x = rnd.randint(10, 21)
		y = rnd.randint(5, 19)
		if px[x, y][:3] != visor:
			px[x, y] = opaque(mix(px[x, y], (190, 186, 176), 0.55))
	if H.get("antenna"):
		for y in range(1, 9):
			px[23, y] = opaque(shade((60, 58, 56), 1.0 if y > 2 else 1.3))
		px[23, 1] = (210, 70, 54, 255)


PORTRAITS = {
	"ansa": (PEOPLE["ansa"], "watch", (196, 214, 160), {}),
	"kaddor": (PEOPLE["kaddor"], "empire", (176, 38, 42), {}),
	"quartermaster_watch": (PEOPLE["watch_quartermaster"], "watch", (214, 190, 140), {}),
	"quartermaster_imperial": (PEOPLE["imperial_quartermaster"], "empire", (176, 150, 100), {}),
	"spacer": (PEOPLE["spacer"], "frontier", (200, 170, 120), {}),
	"trainer": (person(skin=(176, 132, 100), hair=(70, 56, 44), hair_style="short", beard=(76, 60, 46), eyes=(70, 80, 70),
				style="apron", garment=(84, 66, 50), under=(112, 108, 96), hat="goggles", hat_colour=(46, 40, 34), lens=(110, 170, 200)),
				"frontier", (150, 200, 220), {}),
	"apprentice": (PEOPLE["apprentice_0"], "watch", (196, 214, 160), {}),
	"acolyte": (PEOPLE["acolyte_0"], "empire", (176, 38, 42), {}),
	# A smuggler of the frontier: stubble, a scar, a quilted vest over a pale shirt.
	"smuggler": (person(skin=(178, 132, 98), hair=(52, 38, 28), hair_style="short", beard=(70, 52, 38), eyes=(90, 76, 54), scar=True,
				style="coat", garment=(104, 84, 60), under=(170, 160, 136), trim=(70, 56, 40)), "frontier", (224, 176, 104), {}),
	# Kaast: rust and cream paint on a rounded helmet, an antenna stub, a dark ember visor.
	"kaast": (person(garment=(96, 70, 50), under=(50, 46, 42), style="armour"), "hunter", (220, 120, 70),
				{"helmet": {"base": (150, 86, 52), "panel": (196, 182, 150), "visor": (34, 22, 20), "antenna": True}}),
	# One of the hunters: grey-green paint, no antenna.
	"mandalorian": (person(garment=(84, 92, 80), under=(46, 48, 44), style="armour"), "hunter", (200, 170, 120),
				{"helmet": {"base": (92, 104, 90), "panel": (128, 132, 118), "visor": (26, 30, 30), "antenna": False}}),
}


# ---------------------------------------------------------------- items (16x16)

def blank(n=16):
	return Image.new("RGBA", (n, n), (0, 0, 0, 0))


def chit(px, rnd, ox, oy):
	"""One credit chit: a small brass-rimmed tablet with a teal data stripe, 9x6 at (ox, oy)."""
	rim, body_c, stripe = (196, 160, 72), (126, 100, 46), (72, 150, 156)

	for y in range(6):
		for x in range(9):
			if (x in (0, 8) and y in (0, 5)):
				continue
			edge = x in (0, 8) or y in (0, 5)
			c = rim if edge else body_c
			if not edge and y == 2 and 2 <= x <= 6:
				c = stripe
			if edge and (x == 0 or y == 0):
				c = shade(rim, 1.2)
			if edge and (x == 8 or y == 5):
				c = shade(rim, 0.65)
			px[ox + x, oy + y] = opaque(noisy(c, rnd, 4))
	px[ox + 2, oy + 3] = opaque((236, 214, 140))


def item_credit_chit():
	img = blank()
	px = img.load()
	chit(px, random.Random(11), 3, 5)
	return img


def item_credit_bundle():
	img = blank()
	px = img.load()
	rnd = random.Random(12)

	for ox, oy in ((5, 2), (3, 5), (1, 8)):
		chit(px, rnd, ox, oy)

	# A red strap around the stack.
	for y in range(2, 14):
		for x in (7, 8):
			if px[x, y][3] > 0 or (y > 2 and y < 13):
				px[x, y] = opaque(noisy((140, 44, 40) if x == 7 else (104, 32, 30), rnd, 4))
	return img


def item_kaast_pattern():
	"""A rolled flimsi pattern, half open, the armour's outline drawn in rust, tied with cord."""
	img = blank()
	px = img.load()
	rnd = random.Random(13)
	paper, ink = (212, 202, 172), (158, 80, 44)

	for y in range(3, 14):
		for x in range(2, 12):
			px[x, y] = opaque(noisy(shade(paper, 0.92 if x == 2 or y == 13 else 1.0), rnd, 5))
	# The roll on the right.
	for y in range(2, 15):
		for x in range(11, 15):
			t = (x - 11) / 3
			px[x, y] = opaque(noisy(shade(paper, 1.05 - 0.35 * abs(t - 0.35)), rnd, 4))
	for y in (6, 7, 9, 10):
		px[11, y] = px[12, y] = px[13, y] = px[14, y] = opaque(shade((96, 66, 42), 1.0 if y in (6, 9) else 0.8))
	# A helmet and a breastplate outline.
	for x in range(5, 9):
		px[x, 4] = opaque(ink)
	px[4, 5] = px[9, 5] = px[4, 6] = px[9, 6] = opaque(ink)
	for x in range(5, 9):
		px[x, 6] = opaque(shade(ink, 0.8))
	for x in range(4, 10):
		px[x, 8] = opaque(ink)
	for y in range(9, 12):
		px[4, y] = px[9, y] = opaque(ink)
	px[5, 12] = px[8, 12] = opaque(ink)
	px[6, 12] = px[7, 12] = opaque(ink)
	px[6, 10] = px[7, 10] = opaque(shade(ink, 0.8))
	return img


def item_tomb_relic():
	"""A carved shard of dark tomb stone with a glyph that still glows a dull red."""
	img = blank()
	px = img.load()
	rnd = random.Random(14)
	stone, edge = (62, 54, 60), (104, 94, 100)

	for y in range(1, 15):
		half = 1 + min(y, 9) * 0.45 if y < 11 else 5 - (y - 11) * 0.6
		for x in range(16):
			if abs(x - 7.5) <= half:
				border = abs(x - 7.5) > half - 1 or y == 14
				px[x, y] = opaque(noisy(edge if border and x < 8 else shade(stone, 0.8 if border else 1.0), rnd, 5))
	glyph = [(7, 4), (8, 4), (7, 5), (6, 6), (9, 6), (7, 7), (8, 7), (7, 8), (7, 9), (5, 9), (9, 9), (6, 10), (8, 10), (7, 11)]
	for x, y in glyph:
		px[x, y] = opaque((212, 64, 48) if (x + y) % 3 else (255, 132, 92))
	return img


def item_sealed_contract():
	"""A folded contract with a dark red seal and two ribbon tails."""
	img = blank()
	px = img.load()
	rnd = random.Random(15)
	paper = (206, 196, 172)

	for y in range(2, 14):
		for x in range(3, 13):
			c = shade(paper, 0.86) if y == 7 else paper
			px[x, y] = opaque(noisy(c, rnd, 4))
	for y in (4, 5, 9):
		for x in range(5, 11 if y != 5 else 9):
			px[x, y] = opaque((120, 112, 100))
	for x, y in ((8, 12), (8, 13), (11, 12), (11, 13), (8, 14), (12, 14)):
		px[x, y] = opaque((132, 36, 34))
	for y in range(8, 13):
		for x in range(7, 13):
			if ellipse_in(x, y, 9.5, 10.5, 2.4, 2.2):
				px[x, y] = opaque(noisy((150, 40, 36), rnd, 4))
	px[9, 9] = opaque((206, 96, 84))
	px[9, 10] = px[10, 10] = opaque((104, 26, 24))
	return img


ITEMS = {
	"credit_chit": item_credit_chit,
	"credit_bundle": item_credit_bundle,
	"kaast_pattern": item_kaast_pattern,
	"tomb_relic": item_tomb_relic,
	"sealed_contract": item_sealed_contract,
}


# ---------------------------------------------------------------- blocks (16x16)

def metal(rnd, base, n=16):
	img = Image.new("RGBA", (n, n))
	px = img.load()

	for y in range(n):
		for x in range(n):
			px[x, y] = opaque(noisy(base, rnd, 4))
	return img, px


def bevel(px, base, n=16):
	for i in range(n):
		px[i, 0] = opaque(shade(base, 1.18))
		px[0, i] = opaque(shade(base, 1.12))
		px[i, n - 1] = opaque(shade(base, 0.66))
		px[n - 1, i] = opaque(shade(base, 0.72))


def terminal_front():
	"""The contract board: a dark screen with an amber header bar and teal entries, three keys below."""
	rnd = random.Random(21)
	base = (74, 78, 84)
	img, px = metal(rnd, base)
	bevel(px, base)

	for y in range(2, 11):
		for x in range(2, 14):
			px[x, y] = opaque(noisy((18, 26, 30), rnd, 3))
	for x in range(3, 13):
		px[x, 3] = opaque((214, 160, 70))
	for y, length in ((5, 8), (7, 6), (9, 9)):
		px[3, y] = opaque((214, 160, 70))
		for x in range(5, 5 + length - 2):
			px[x, y] = opaque((84, 196, 184) if (x + y) % 5 else (52, 140, 132))
	for x in range(2, 14):
		px[x, 11] = opaque(shade(base, 0.7))
	for i, c in enumerate(((70, 170, 90), (210, 160, 60), (170, 60, 50))):
		px[4 + i * 3, 13] = opaque(c)
		px[5 + i * 3, 13] = opaque(shade(c, 0.7))
	return img


def terminal_side():
	rnd = random.Random(22)
	base = (88, 92, 98)
	img, px = metal(rnd, base)
	bevel(px, base)

	for y in range(4, 9):
		if y % 2 == 0:
			for x in range(3, 13):
				px[x, y] = opaque(shade(base, 0.55))
	for x in range(1, 15):
		px[x, 11] = opaque(shade(base, 0.75))
	for y in range(12, 15):
		for x in range(1, 15):
			px[x, y] = opaque(noisy(shade(base, 0.62), rnd, 3))
	for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
		px[x, y] = opaque(shade(base, 1.3))
	return img


def terminal_top():
	rnd = random.Random(23)
	base = (82, 86, 92)
	img, px = metal(rnd, base)
	bevel(px, base)

	for i in range(1, 15):
		px[i, 8] = opaque(shade(base, 0.7))
	for x, y in ((3, 3), (12, 3), (3, 12), (12, 12)):
		px[x, y] = opaque(shade(base, 1.3))
	px[7, 4] = px[8, 4] = opaque((70, 170, 160))
	return img


def beacon_post():
	"""Scrap plating: rust-streaked steel with a hazard band of worn yellow and black."""
	rnd = random.Random(31)
	base = (96, 92, 88)
	img, px = metal(rnd, base)

	for y in range(16):
		for x in range(16):
			if rnd.random() < 0.18:
				px[x, y] = opaque(noisy((128, 74, 44), rnd, 10))
	for y in range(4, 7):
		for x in range(16):
			px[x, y] = opaque(noisy((196, 160, 54) if (x + y) % 4 < 2 else (34, 32, 30), rnd, 6))
	for y in range(16):
		px[0, y] = opaque(shade(base, 0.7))
	return img


def beacon_lamp():
	"""The red lamp: a lens with a hot core and a glint."""
	rnd = random.Random(32)
	img, px = metal(rnd, (160, 36, 34))

	for y in range(16):
		for x in range(16):
			core = ellipse_in(x, y, 7.5, 7.5, 3.5, 3.5)
			px[x, y] = opaque(noisy((228, 70, 56) if core else (150, 34, 32), rnd, 6))
	px[6, 6] = px[7, 6] = opaque((255, 190, 170))
	return img


def beacon_base():
	rnd = random.Random(33)
	base = (58, 56, 54)
	img, px = metal(rnd, base)
	bevel(px, base)

	for x, y in ((5, 5), (10, 5), (5, 10), (10, 10)):
		px[x, y] = opaque((120, 116, 108))
	return img


BLOCK_TEXTURES = {
	"contract_terminal_front": terminal_front,
	"contract_terminal_side": terminal_side,
	"contract_terminal_top": terminal_top,
	"marker_beacon_post": beacon_post,
	"marker_beacon_lamp": beacon_lamp,
	"marker_beacon_base": beacon_base,
}


def cube_faces(uv_side, uv_top, texture):
	"""The six faces of an element, all from one texture: the sides at uv_side, the top and bottom at uv_top."""
	faces = {f: {"uv": uv_side, "texture": texture} for f in ("north", "south", "east", "west")}
	faces["up"] = {"uv": uv_top, "texture": texture}
	faces["down"] = {"uv": uv_top, "texture": texture}
	return faces


def block_json():
	ns = "oldrepublic:"
	out_json("blockstates/contract_terminal.json", {"variants": {
		f"facing={facing}": dict({"model": ns + "block/contract_terminal"}, **({"y": rot} if rot else {}))
		for facing, rot in (("north", 0), ("east", 90), ("south", 180), ("west", 270))
	}})
	faces = {f: {"texture": "#side", "cullface": f} for f in ("south", "east", "west")}
	faces["north"] = {"texture": "#front", "cullface": "north"}
	faces["up"] = {"texture": "#top", "cullface": "up"}
	faces["down"] = {"texture": "#top", "cullface": "down"}
	out_json("models/block/contract_terminal.json", {
		"parent": "minecraft:block/block",
		"textures": {
			"particle": ns + "block/contract_terminal_side",
			"front": ns + "block/contract_terminal_front",
			"side": ns + "block/contract_terminal_side",
			"top": ns + "block/contract_terminal_top",
		},
		"elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": faces}],
	})

	out_json("blockstates/marker_beacon.json", {"variants": {"": {"model": ns + "block/marker_beacon"}}})
	out_json("models/block/marker_beacon.json", {
		"parent": "minecraft:block/block",
		"textures": {
			"particle": ns + "block/marker_beacon_post",
			"post": ns + "block/marker_beacon_post",
			"lamp": ns + "block/marker_beacon_lamp",
			"base": ns + "block/marker_beacon_base",
		},
		"elements": [
			# The foot plate, the post, a battery box on its side, and the lamp on top.
			{"from": [4, 0, 4], "to": [12, 1, 12], "faces": cube_faces([4, 15, 12, 16], [4, 4, 12, 12], "#base")},
			{"from": [6, 1, 6], "to": [10, 11, 10], "faces": cube_faces([6, 1, 10, 11], [6, 6, 10, 10], "#post")},
			{"from": [10, 2, 7], "to": [11, 6, 9], "faces": cube_faces([7, 10, 9, 14], [7, 7, 9, 8], "#base")},
			{"from": [6.5, 11, 6.5], "to": [9.5, 14, 9.5], "faces": cube_faces([5, 5, 8, 8], [6, 6, 9, 9], "#lamp")},
		],
	})

	for name in ("contract_terminal", "marker_beacon"):
		out_json(f"models/item/{name}.json", {"parent": ns + "block/" + name})
		out_json(f"items/{name}.json", {"model": {"type": "minecraft:model", "model": ns + "item/" + name}})


def item_json(name):
	ns = "oldrepublic:"
	out_json(f"models/item/{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": ns + "item/" + name}}, tabs=False)
	out_json(f"items/{name}.json", {"model": {"type": "minecraft:model", "model": ns + "item/" + name}}, tabs=False)


def main():
	for i, (variant, P) in enumerate(PEOPLE.items()):
		out(f"entity/hub/{variant}.png", skin_texture(P, 4100 + i))

	for i, (name, (P, bg, rim, extra)) in enumerate(PORTRAITS.items()):
		out(f"gui/sprites/portrait/{name}.png", portrait(P, 4200 + i, bg, rim, **extra))

	for name, make in ITEMS.items():
		out(f"item/{name}.png", make())
		item_json(name)

	for name, make in BLOCK_TEXTURES.items():
		out(f"block/{name}.png", make())

	block_json()


if __name__ == "__main__":
	main()
