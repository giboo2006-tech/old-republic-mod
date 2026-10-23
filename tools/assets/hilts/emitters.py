#!/usr/bin/env python3
"""The emitter designs (docs/MILESTONE_7.md 6.3): DESIGNS maps each emitter id of HiltParts to its
Design. Emitters live in the band y 3.7 to 6.0 (tall shrouds to 7.6), meet the grip with a collar of
radius 1.05 to 1.15 at y 3.7, and say where their blade leaves them (Blade: mouth, tilt, quillons).

The Guardian is the reference design: read it before writing another. Each design is one function: a
docstring saying what it looks like, its sheet, then its elements from the collar up. The helpers below
paint what most designs share (the mouth, the crown's bore, a turned band). Preview with
  python3 tools/assets/hilts/preview.py part emitter guardian --out <scratch dir>
  python3 tools/assets/hilts/preview.py staff guardian --out <scratch dir>
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from common import paint as cp  # noqa: E402
from lib import (  # noqa: E402
	Blade, Design, around, bend, bend_point, box, lathe, octo, registry, ring, rot_z, side_box, transform, turn)

DESIGNS, design = registry()


# ------------------------------------------------------------------ shared pieces

def mouth(y, half):
	"""The emitter disc's near-black mouth: a flat square facing up at height y, at the bottom of a crown's
	bore (half is at least the bore's inner radius, so its corners hide in the walls)."""
	return box(8 - half, y - 0.04, 8 - half, 8 + half, y, 8 + half, cp("mouth"), faces=["up"], name="mouth")


def bore(sh, x, y):
	"""Paint a crown's inner wall at (x, y) of the sheet, 4x4: dark metal darkening toward the mouth."""
	sh.region("bore", x, y, 4, 4, tint=True, mode="fit")
	for i, v in enumerate((96, 68, 46, 30)):
		sh.rect(x, y + i, x + 3, y + i, (v, v, v))
	return sh.paint("bore")


def flat(sh, name, x, y, rgb, tint=True):
	"""A 2x2 flat colour (the caps of a painted band)."""
	sh.region(name, x, y, 2, 2, tint=tint, mode="solid")
	sh.fill(name, rgb)
	return sh.paint(name)


def grooves(sh, name, x, y, w, h, base, rows, flat_xy, spread=3, seed=1, tint=True):
	"""A turned band: brushed metal (a grey level, or an RGB when untinted) with a dark groove and a lit lower lip
	at each of rows, and its flat colour (name_flat, 2x2 at flat_xy) for the caps. Returns the band's paint."""
	rgb = (base,) * 3 if isinstance(base, (int, float)) else tuple(base)
	flat(sh, name + "_flat", flat_xy[0], flat_xy[1], rgb, tint)
	sh.region(name, x, y, w, h, tint=tint, flat=name + "_flat")
	sh.brushed(name, base, spread=spread, vertical=False, seed=seed)
	for r in rows:
		sh.rect(x, y + r, x + w - 1, y + r, [c * 0.55 for c in rgb])
		sh.rect(x, y + r + 1, x + w - 1, y + r + 1, [min(255, c * 1.12) for c in rgb])
	return sh.paint(name)


# ------------------------------------------------------------------ the designs

@design
def guardian():
	"""A straight, heavy shroud with a flat crown and three vent slots on every side, a dark groove under the crown
	and a brass ring under the shroud; a narrow neck carrying the activation plate and its red button on the
	thumb side."""
	d = Design("emitter", "guardian", "Guardian")
	sh = d.sheet
	steel, satin = cp("steel"), cp("steel_satin")

	# the sheet: three vent slots per side (a dark slot, a shadowed top lip, a lit bottom lip), a turned neck with
	# two fine grooves, the bore
	sh.region("vents", 0, 0, 7, 6, tint=True, flat="lip", mode="fit")
	sh.region("lip", 8, 0, 2, 2, tint=True, mode="solid")
	sh.region("neck", 12, 0, 8, 8, tint=True, mode="scale")
	sh.fill("vents", (198, 198, 198))
	for x in (1, 3, 5):
		sh.px(x, 0, (150, 150, 150))
		for y in range(1, 5):
			sh.px(x, y, (16, 16, 16) if y > 1 else (34, 34, 34))
		sh.px(x, 5, (238, 238, 238))
	sh.fill("lip", (206, 206, 206))
	sh.brushed("neck", 132, spread=3, vertical=False, seed=3)
	for y in (2, 5):
		sh.rect(12, y, 19, y, (92, 92, 92))
		sh.rect(12, y + 1, 19, y + 1, (160, 160, 160))
	b = bore(sh, 24, 0)
	vents, neck = sh.paint("vents"), sh.paint("neck")

	d.add(
		lathe([
			(3.7, 3.86, 1.1, steel),       # the join collar
			(3.86, 4.5, 0.9, neck),        # the narrow neck
			(4.5, 4.6, 1.0, cp("brass")),  # a brass ring under the shroud
			(4.6, 4.76, 1.22, satin),      # the flare
			(4.76, 4.9, 1.35, steel),      # the shroud's lower lip
			(4.9, 5.96, 1.31, vents),      # the vented band
			(5.96, 6.06, 1.27, cp("black_flat")),   # a dark groove under the crown
		], top=False),
		# the crown: a flat ring with a bevelled lip around the recessed mouth
		ring(6.06, 6.3, 1.38, 0.36, steel, inner=b, name="crown"),
		ring(6.3, 6.42, 1.3, 0.28, satin, inner=b, name="lip"),
		mouth(6.1, 1.04),
		# the activation plate and its button on the thumb side
		side_box(3.96, 4.44, 0.9, 0.66, 0.3, cp("gunmetal"), name="plate"),
		side_box(4.08, 4.32, 1.2, 0.32, 0.14, cp("button_red"), name="button"),
	)
	d.blade = Blade(mouth=(8.0, 6.1))
	return d


@design
def duelist():
	"""Slim and tapered like a pen: a dark neck with a thin brass ring, a long satin barrel with a fine black band,
	stepping in toward a bevelled lip around the mouth; a small blue sensor stud on the thumb side and a slim clip
	along the back."""
	d = Design("emitter", "duelist", "Duelist")
	sh = d.sheet
	steel, satin, dark, gunmetal = cp("steel"), cp("steel_satin"), cp("steel_dark"), cp("gunmetal")
	b = bore(sh, 0, 0)

	d.add(
		lathe([
			(3.7, 3.86, 1.1, steel),       # the join collar
			(3.86, 4.16, 0.82, dark),      # the neck
			(4.16, 4.28, 0.95, cp("brass")),   # the thin brass ring
			(4.28, 4.4, 0.82, dark),       # the neck above it
			(4.4, 5.36, 0.96, satin),      # the barrel
			(5.36, 5.46, 0.92, cp("black_flat")),   # a fine black band
			(5.46, 5.6, 0.96, satin),
			(5.6, 5.94, 0.9, satin),       # the taper, in two steps
			(5.94, 6.24, 0.84, satin),
		]),
		ring(6.24, 6.46, 0.79, 0.18, steel, inner=b, name="lip"),   # the bevelled lip, inside the taper's top
		mouth(6.29, 0.63),
		# the blue sensor stud in its small dark plate, on the thumb side
		side_box(4.74, 5.2, 0.96, 0.38, 0.05, gunmetal, name="sensor plate"),
		side_box(4.86, 5.08, 1.01, 0.22, 0.08, cp("button_blue"), name="sensor"),
		# the clip along the back, standing off the barrel on its foot
		side_box(4.5, 5.9, 1.0, 0.2, 0.08, gunmetal, k=4, inset=0.0, skip=(), name="clip"),
		side_box(4.42, 4.6, 0.94, 0.2, 0.14, gunmetal, k=4, name="clip foot"),
	)
	d.blade = Blade(mouth=(8.0, 6.29))
	return d


@design
def reliquary():
	"""A squared archival shroud between square brass bands: riveted steel plates, dark corner posts, a square
	crown around the mouth, on the thumb side a brass-framed glass window over the amber emitter crystal and on
	the back a brass clasp."""
	d = Design("emitter", "reliquary", "Reliquary")
	sh = d.sheet
	steel, satin, dark, brass = cp("steel"), cp("steel_satin"), cp("steel_dark"), cp("brass")

	# the sheet: a riveted plate (a dark seam inside its edge, a rivet in each corner), the window (the amber
	# crystal behind glass), the bore
	flat(sh, "plate_flat", 10, 0, (198, 198, 198))
	sh.region("plate", 0, 0, 9, 6, tint=True, flat="plate_flat", mode="fit")
	sh.brushed("plate", 200, spread=3, vertical=True, seed=8)
	sh.outline("plate", (128, 128, 128))
	sh.rivets("plate", [(1, 1), (6, 1), (1, 3), (6, 3)], head=(240, 240, 240), shadow=(96, 96, 96))
	sh.region("window", 0, 8, 4, 4, mode="fit")
	for y, row in enumerate((
			((74, 44, 16), (236, 160, 60), (255, 206, 110), (74, 44, 16)),
			((64, 38, 14), (226, 146, 50), (246, 184, 84), (64, 38, 14)),
			((56, 32, 12), (206, 126, 40), (232, 160, 64), (56, 32, 12)),
			((46, 26, 10), (176, 102, 30), (200, 130, 46), (46, 26, 10)))):
		for x, c in enumerate(row):
			sh.px(x, 8 + y, c)
	b = bore(sh, 12, 0)
	plate = sh.paint("plate")

	h = 1.12      # the shroud's half width
	posts = [box(8 + sx * h - 0.14, 4.08, 8 + sz * h - 0.14, 8 + sx * h + 0.14, 5.6, 8 + sz * h + 0.14, dark, name="post")
			for sx in (-1, 1) for sz in (-1, 1)]
	d.add(
		lathe([
			(3.7, 3.86, 1.1, steel),       # the join collar
			(3.86, 3.98, 0.96, dark),      # a short neck
		]),
		box(8 - 1.22, 3.98, 8 - 1.22, 8 + 1.22, 4.08, 8 + 1.22, brass, name="band"),      # the lower brass band
		box(8 - h, 4.08, 8 - h, 8 + h, 5.6, 8 + h, plate, skip=("up", "down"), name="shroud"),
		posts,
		box(8 - 1.22, 5.6, 8 - 1.22, 8 + 1.22, 5.78, 8 + 1.22, brass, name="band"),        # the upper brass band
		# the square crown around the mouth
		box(7.0, 5.78, 7.0, 9.0, 6.04, 7.22, satin, paints={"south": b}, skip=("down",), name="crown"),
		box(7.0, 5.78, 8.78, 9.0, 6.04, 9.0, satin, paints={"north": b}, skip=("down",), name="crown"),
		box(7.0, 5.78, 7.22, 7.22, 6.04, 8.78, satin, paints={"east": b}, skip=("down", "north", "south"), name="crown"),
		box(8.78, 5.78, 7.22, 9.0, 6.04, 8.78, satin, paints={"west": b}, skip=("down", "north", "south"), name="crown"),
		mouth(5.84, 0.8),
		# the window on the thumb side: the glass, and its raised brass frame
		box(9.08, 4.42, 7.6, 9.15, 5.24, 8.4, sh.paint("window"), faces=["east"], light=4, name="window"),
		box(9.06, 5.24, 7.48, 9.22, 5.36, 8.52, brass, skip=("west",), name="frame"),
		box(9.06, 4.3, 7.48, 9.22, 4.42, 8.52, brass, skip=("west",), name="frame"),
		box(9.06, 4.42, 7.48, 9.22, 5.24, 7.6, brass, skip=("west", "up", "down"), name="frame"),
		box(9.06, 4.42, 8.4, 9.22, 5.24, 8.52, brass, skip=("west", "up", "down"), name="frame"),
		# the clasp on the back
		box(6.8, 4.66, 7.8, 6.88, 5.02, 8.2, brass, skip=("east",), name="clasp"),
	)
	d.blade = Blade(mouth=(8.0, 5.84))
	return d


@design
def cantor():
	"""The Cantor's legendary emitter: a slim satin throat banded with a violet glass inlay, flaring in steps into
	a wide crown, and four resonator fins on the diagonals leaning out of the flare like petals, each with a dark
	resonator slot and a dark lining."""
	d = Design("emitter", "cantor", "Cantor")
	sh = d.sheet
	steel, satin, dark = cp("steel"), cp("steel_satin"), cp("steel_dark")

	# the sheet: a fin's outer face (a lit top edge, a dark slot down its middle), the bore
	flat(sh, "fin_flat", 8, 0, (216, 216, 216))
	sh.region("fin", 0, 0, 4, 7, tint=True, flat="fin_flat", mode="fit")
	sh.brushed("fin", 216, spread=3, seed=7)
	sh.rect(0, 0, 3, 0, (240, 240, 240))
	for y in range(1, 6):
		sh.px(1, y, (30, 30, 30))
		sh.px(2, y, (52, 52, 52))
	sh.rect(1, 6, 2, 6, (244, 244, 244))
	b = bore(sh, 12, 0)

	inlay = octo(4.28, 4.44, 0.82, cp("glass_violet").solid(), top=True, bottom=True, name="inlay")
	for e in inlay:
		e.light = 5
	# a fin: a plate across the thumb side seated on the flare, leaned out about its foot, then turned onto a diagonal
	fin = bend([box(9.2, 5.21, 7.52, 9.38, 6.72, 8.48, satin, paints={"east": sh.paint("fin"), "west": dark}, name="fin")],
			5.21, 14.0, pivot_x=9.28)
	d.add(
		lathe([
			(3.7, 3.86, 1.1, steel),       # the join collar
			(3.86, 4.28, 0.78, satin),     # the slim throat, below the inlay
		], top=False),
		inlay,
		lathe([
			(4.44, 4.95, 0.78, satin),     # the throat above the inlay
			(4.95, 5.08, 0.94, steel),     # the flare, in steps
			(5.08, 5.21, 1.1, steel),
			(5.21, 5.34, 1.24, steel),
			(5.34, 5.62, 1.38, dark),      # the crown band
		], top=False, bottom=False),
		ring(5.62, 5.86, 1.44, 0.32, satin, inner=b, name="crown"),
		mouth(5.68, 1.14),
		around(fin, 4, start=45),
	)
	d.blade = Blade(mouth=(8.0, 5.68))
	return d


@design
def vanguard():
	"""Heavy and industrial: a thick bolted lower collar, then a long cylindrical shroud with rows of horizontal vent
	slots down both flanks and a window cut into the thumb side onto the copper-wound inner emitter tube; turned
	grooves under a plain crown."""
	d = Design("emitter", "vanguard", "Vanguard")
	sh = d.sheet
	steel, satin = cp("steel"), cp("steel_satin")

	# the sheet: the thick collar (a lit bevel and a turned groove), the upper shroud (two grooves under the
	# crown), the vents (three rows of slots), the coil, the bore
	collar = grooves(sh, "collar", 0, 0, 16, 3, 150, rows=(1,), flat_xy=(16, 0), seed=9)
	sh.rect(0, 0, 15, 0, (186, 186, 186))
	upper = grooves(sh, "upper", 0, 4, 16, 4, 204, rows=(0, 2), flat_xy=(16, 4), seed=19)
	sh.region("vents", 20, 0, 4, 6, tint=True, flat="collar_flat", mode="fit")
	sh.fill("vents", (204, 204, 204))
	for y in (1, 3, 5):
		sh.rect(20, y - 1, 23, y - 1, (176, 176, 176))
		sh.rect(20, y, 23, y, (24, 24, 24))
	sh.region("coil", 0, 8, 8, 8, mode="scale")
	for y in range(8):
		sh.rect(0, 8 + y, 7, 8 + y, (178, 102, 64) if y % 2 == 0 else (64, 36, 22))
	sh.rect(0, 8, 7, 8, (226, 150, 100))
	b = bore(sh, 28, 0)
	vents = sh.paint("vents")

	d.add(
		lathe([
			(3.7, 3.84, 1.12, steel),      # the join collar
			(3.84, 4.5, 1.45, collar),     # the thick lower collar
			(4.5, 4.6, 1.34, satin),       # a turned shoulder
			(4.6, 4.92, 1.28, steel),      # the shroud below the window; its top is the window's sill
		]),
		octo(4.92, 6.3, 1.28, steel, sides={2: vents, 6: vents}, skip=(0,), name="window band"),
		octo(6.3, 7.02, 1.28, upper, bottom=True, name="shroud"),
		# behind the window: the inner emitter tube in a niche (dark side walls and backing, so no line of sight
		# through the window leaves the hilt)
		octo(4.92, 6.3, 0.66, sh.paint("coil"), name="coil"),
		box(7.2, 4.92, 7.44, 9.28, 6.3, 7.47, cp("steel_dark"), faces=["south"], name="niche"),
		box(7.2, 4.92, 8.53, 9.28, 6.3, 8.56, cp("steel_dark"), faces=["north"], name="niche"),
		box(7.2, 4.92, 7.47, 7.26, 6.3, 8.53, cp("black_flat"), faces=["east"], name="backing"),
		ring(7.02, 7.28, 1.34, 0.34, satin, inner=b, name="crown"),
		mouth(7.08, 1.02),
		# bolts around the collar
		around(side_box(4.04, 4.34, 1.45, 0.3, 0.1, cp("gunmetal"), name="bolt"), 4),
	)
	d.blade = Blade(mouth=(8.0, 7.08))
	return d


def port(y, paint, vents, nozzle):
	"""A quillon port on the thumb side, its axis at height y along +x: an octagonal tube with vent slits, a
	flared nozzle, and the nozzle's dark mouth facing +x at x 10.98 (8 + 2.98)."""
	along = [
		octo(y + 1.2, y + 2.56, 0.3, paint, sides={2: vents, 4: vents, 6: vents}, name="port"),
		octo(y + 2.56, y + 2.96, 0.42, nozzle, top=True, bottom=True, name="nozzle"),
	]
	return transform(along, rot_z(-90.0), (8.0, y, 8.0)) + [
		box(10.96, y - 0.3, 7.7, 10.98, y + 0.3, 8.3, cp("mouth"), faces=["east"], name="port mouth")]


@design
def warden():
	"""A crossguard: a broad head over a squared guard block, and from a dark band under the crown two short quillon
	ports reaching out along +z and -z, each an octagonal tube with vent slits ending in a flared nozzle with a
	dark mouth, where the side blades leave (8.1). Built along x, then turned a quarter about the axis, so first
	person (which looks at the thumb side, +x) sees the crossguard spread across the hilt."""
	d = Design("emitter", "warden", "Warden")
	sh = d.sheet
	steel, satin, dark = cp("steel"), cp("steel_satin"), cp("steel_dark")

	# the sheet: the head (a turned groove), the port's vent slits, the bore
	head = grooves(sh, "head", 0, 0, 16, 6, 200, rows=(1,), flat_xy=(16, 0), seed=10)
	flat(sh, "port_flat", 16, 4, (128, 128, 128))
	sh.region("port", 0, 8, 6, 3, tint=True, flat="port_flat", mode="fit")
	sh.fill("port", (128, 128, 128))
	for x in (1, 4):
		sh.px(x, 9, (20, 20, 20))
		sh.px(x + 1, 9, (20, 20, 20))
	flat(sh, "guard_flat", 16, 12, (196, 196, 196))
	sh.region("guard", 0, 12, 14, 2, tint=True, flat="guard_flat", mode="fit")
	sh.brushed("guard", 196, spread=3, vertical=False, seed=11)
	sh.rect(0, 12, 13, 12, (232, 232, 232))
	sh.rect(0, 13, 13, 13, (150, 150, 150))
	sh.rivets("guard", [(1, 0), (11, 0)], head=(240, 240, 240), shadow=(110, 110, 110))
	b = bore(sh, 20, 0)

	quillon = port(5.26, dark, sh.paint("port"), satin)
	guard = sh.paint("guard")
	d.add(
		lathe([
			(3.7, 3.86, 1.1, steel),       # the join collar
			(3.86, 4.1, 0.95, dark),       # the neck
		]),
		# the squared guard block, wider along the ports' axis
		box(8 - 1.7, 4.1, 8 - 1.1, 8 + 1.7, 4.55, 8 + 1.1, steel,
				paints={f: guard for f in ("north", "south", "east", "west")}, name="guard"),
		lathe([
			(4.55, 4.7, 1.28, satin),      # the head's foot
			(4.7, 4.98, 1.42, head),       # the broad head, below the ports
			(4.98, 5.54, 1.36, dark),      # the dark band the ports leave
			(5.54, 5.86, 1.42, steel),     # the head above them
		], top=False),
		ring(5.86, 6.1, 1.48, 0.34, satin, inner=b, name="crown"),
		mouth(5.92, 1.16),
		quillon, turn(quillon, 180),
	)
	d.elements = turn(d.elements, 90)
	d.blade = Blade(mouth=(8.0, 5.92), quillons={"y": 5.26, "z": 2.95, "length": 3.5})
	return d


@design
def watchman():
	"""A Watch knight's emitter, old and much used: worn, pitted steel, a grooved brass band, a shroud with a turned
	groove and four ribs rising into a crown of four low, stepped teeth on the diagonals."""
	d = Design("emitter", "watchman", "Watchman")
	sh = d.sheet
	dark = cp("steel_dark")

	# the sheet: worn steel (fine streaks, sparse pits and scratches, a turned groove near the top), the grooved
	# brass band, the bore
	flat(sh, "worn_flat", 16, 8, (184, 184, 184))
	sh.region("worn", 0, 8, 16, 16, tint=True, flat="worn_flat")
	sh.brushed("worn", 186, spread=4, vertical=False, seed=21)
	sh.wear("worn", pits=14, scratches=4, amount=46, seed=22)
	sh.rect(0, 9, 15, 9, (110, 110, 110))
	sh.rect(0, 10, 15, 10, (206, 206, 206))
	worn = sh.paint("worn")
	band = grooves(sh, "band", 0, 0, 16, 6, (190, 150, 76), rows=(1, 3), flat_xy=(16, 0), seed=12, tint=False)
	b = bore(sh, 20, 0)

	# a rib up the shroud and the stepped tooth above it, on the thumb side (turned onto the diagonals below)
	tooth = [
		side_box(4.62, 5.7, 1.3, 0.22, 0.07, worn, name="rib"),
		side_box(5.95, 6.22, 1.02, 0.62, 0.36, worn, inset=0.0, skip=(), name="tooth"),
		side_box(6.22, 6.4, 1.06, 0.42, 0.28, worn, inset=0.0, skip=("down",), name="tooth tip"),
	]
	d.add(
		lathe([
			(3.7, 3.86, 1.1, worn),        # the join collar
			(3.86, 4.25, 1.0, dark),       # a dark neck
			(4.25, 4.62, 1.24, band),      # the grooved brass band
			(4.62, 5.7, 1.3, worn),        # the shroud
		], top=False),
		ring(5.7, 5.95, 1.36, 0.34, worn, inner=b, name="crown"),
		mouth(5.76, 1.04),
		around(tooth, 4, start=45),
	)
	d.blade = Blade(mouth=(8.0, 5.76))
	return d


PENITENT_PIVOT = 4.32
PENITENT_BEND = 22.5


@design
def penitent():
	"""Darth Ulvaine's curved emitter: a dark lacquered socket, a knee, then a neck bent 22.5 degrees toward the
	thumb side with an ember-red ring under a narrow head, and a talon rising from the head's outer side and
	hooking in over the mouth."""
	d = Design("emitter", "penitent", "Penitent")
	sh = d.sheet
	dark, satin = cp("steel_dark"), cp("steel_satin")

	# the sheet: dark lacquer (a lit top edge, faint streaks), the bore
	flat(sh, "lacquer_flat", 16, 0, (30, 24, 28), tint=False)
	sh.region("lacquer", 0, 0, 16, 8, flat="lacquer_flat")
	sh.brushed("lacquer", (34, 27, 32), spread=2, vertical=True, seed=14)
	sh.rect(0, 0, 15, 0, (66, 54, 62))
	lacquer = sh.paint("lacquer")
	b = bore(sh, 20, 0)

	ember = octo(5.2, 5.32, 0.88, cp("ember_flat"), top=True, bottom=True, name="ember")
	for e in ember:
		e.light = 5
	knee = lathe([(4.22, 4.6, 0.94, dark)], bottom=False, name="knee")   # capped: the neck leaves through its top
	neck = [
		lathe([(4.32, 5.2, 0.82, lacquer)], top=False, bottom=False, name="neck"),   # from the pivot, deep in the knee
		ember,
		lathe([
			(5.32, 5.56, 0.82, lacquer),   # the neck above the ember ring
			(5.56, 5.92, 0.92, dark),      # the narrow head
		], top=False, bottom=False, name="head"),
		ring(5.92, 6.14, 0.96, 0.22, satin, inner=b, name="lip"),
		mouth(5.98, 0.76),
		# the talon: a plate on the outer side, then two pieces leaning in more and more
		side_box(5.5, 6.3, 0.92, 0.62, 0.2, lacquer, k=4, name="talon"),
		bend([box(6.88, 6.26, 7.78, 7.12, 6.66, 8.22, dark, name="talon")], 6.26, 18.0, pivot_x=7.0),
		bend([box(6.92, 6.6, 7.84, 7.08, 6.98, 8.16, dark, name="talon tip")], 6.26, 40.0, pivot_x=7.0),
	]
	d.add(
		lathe([
			(3.7, 3.86, 1.1, dark),        # the join collar
			(3.86, 4.42, 1.0, lacquer),    # the socket
		]),
		bend(knee, PENITENT_PIVOT, PENITENT_BEND / 2),
		bend(neck, PENITENT_PIVOT, PENITENT_BEND),
	)
	mx, my, _ = bend_point((8.0, 5.98, 8.0), PENITENT_PIVOT, PENITENT_BEND)
	d.blade = Blade(mouth=(float(mx), float(my)), tilt=PENITENT_BEND)
	return d


@design
def choir():
	"""The Long Hymn choir's emitter: a tall bell of stacked rings, satin and dark by turns, flaring to the mouth,
	ribbed with eight thin pipes that follow the flare and rise past the crown at graded heights like an organ's,
	and a pale violet inlay at the bell's foot."""
	d = Design("emitter", "choir", "Choir")
	sh = d.sheet
	steel, satin, dark = cp("steel"), cp("steel_satin"), cp("steel_dark")

	# the sheet: a stacked ring (a shadowed foot), a pipe (a lit edge), the inlay, the bore
	flat(sh, "ring_flat", 16, 0, (206, 206, 206))
	sh.region("ring", 0, 0, 16, 2, tint=True, flat="ring_flat")
	sh.brushed("ring", 206, spread=2, vertical=False, seed=16)
	sh.rect(0, 1, 15, 1, (150, 150, 150))
	sh.region("pipe", 20, 0, 2, 8, tint=True, flat="ring_flat", mode="fit")
	sh.fill("pipe", (196, 196, 196))
	sh.rect(20, 0, 20, 7, (236, 236, 236))
	sh.region("inlay", 24, 0, 2, 2, mode="solid")
	sh.fill("inlay", (206, 188, 248))
	rings = sh.paint("ring")
	b = bore(sh, 28, 0)

	inlay = octo(4.28, 4.4, 0.96, sh.paint("inlay"), top=True, bottom=True, name="inlay")
	for e in inlay:
		e.light = 4
	# a pipe follows the flare: built upright on the thumb side, leaned out about its foot as steeply as the bell
	# flares (13.3 degrees, so it lies on the top corner of every ring with no gap behind it), then turned into
	# place; the flanks' pipes are the tallest
	tops = (6.7, 6.95, 7.2, 6.95, 6.7, 6.95, 7.2, 6.95)
	pipes = []
	for k in range(8):
		p = box(8.82, 4.5, 7.89, 9.04, tops[k], 8.11, sh.paint("pipe"), paints={"up": cp("black_flat")}, name="pipe")
		pipes += turn(bend([p], 4.5, 13.3, pivot_x=8.82), 45 * k)
	d.add(
		lathe([
			(3.7, 3.86, 1.1, steel),       # the join collar
			(3.86, 4.28, 0.86, dark),      # the bell's foot
		], top=False),
		inlay,
		lathe([
			(4.4, 4.78, 0.9, rings),       # the bell: stacked rings flaring upward
			(4.78, 5.16, 0.98, dark),
			(5.16, 5.54, 1.07, rings),
			(5.54, 5.92, 1.16, dark),
			(5.92, 6.3, 1.26, rings),
		], top=False, bottom=False),
		ring(6.3, 6.54, 1.36, 0.3, satin, inner=b, name="crown"),
		mouth(6.36, 1.08),
		pipes,
	)
	d.blade = Blade(mouth=(8.0, 6.36))
	return d


if __name__ == "__main__":
	for id_, d in DESIGNS.items():
		probs = d.problems()
		glow = sum(1 for e in d.elements if e.light)
		print(f"{d.item_id}: {len(d.elements)} elements, {glow} glowing" + ("" if not probs else "\n  " + "\n  ".join(probs)))
