#!/usr/bin/env python3
"""The emitter designs (docs/MILESTONE_7.md 6.3): DESIGNS maps each emitter id of HiltParts to its
Design. Emitters live in the band y 3.7 to 6.0 (tall shrouds to 7.6), meet the grip with a collar of
radius 1.05 to 1.15 at y 3.7, and say where their blade leaves them (Blade: mouth, tilt, quillons).

The Guardian is the lead's reference design: read it before writing another. Preview with
  python3 tools/assets/hilts/preview.py part emitter guardian --out <scratch dir>
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from common import paint as cp  # noqa: E402
from lib import Blade, Design, box, lathe, octo, registry, ring, side_box  # noqa: E402

DESIGNS, design = registry()


@design
def guardian():
	"""A straight, heavy shroud with a flat crown and vent slots on every side; a narrow neck carrying the
	activation plate and its red button on the thumb side."""
	d = Design("emitter", "guardian", "Guardian")
	sh = d.sheet
	steel, dark, satin = cp("steel"), cp("steel_dark"), cp("steel_satin")

	# the sheet: three vent slots per side (a dark slot, a shadowed top lip, a lit bottom lip), and a
	# turned neck with two fine grooves
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
	vents, neck = sh.paint("vents"), sh.paint("neck")

	d.add(
		lathe([
			(3.7, 3.88, 1.1, steel),       # the join collar
			(3.88, 4.62, 0.9, neck),       # the narrow neck
			(4.62, 4.8, 1.22, satin),      # the flare
			(4.8, 4.95, 1.35, steel),      # the shroud's lower lip
			(4.95, 5.95, 1.31, vents),     # the vented band
		], top=False),
		# the crown: a flat ring with a bevelled lip around the recessed mouth and its emitter disc
		ring(5.95, 6.2, 1.38, 0.36, steel, inner=dark, name="crown"),
		ring(6.2, 6.32, 1.3, 0.28, satin, inner=dark, name="lip"),
		box(7.0, 6.02, 7.0, 9.0, 6.06, 9.0, cp("mouth"), faces=["up"], name="mouth"),
		# the activation plate and its button on the thumb side
		side_box(3.98, 4.52, 0.9, 0.66, 0.3, cp("gunmetal"), name="plate"),
		side_box(4.12, 4.38, 1.2, 0.32, 0.14, cp("button_red"), name="button"),
	)
	d.blade = Blade(mouth=(8.0, 6.06))
	return d


if __name__ == "__main__":
	for id_, d in DESIGNS.items():
		probs = d.problems()
		print(f"{d.item_id}: {len(d.elements)} elements" + ("" if not probs else "\n  " + "\n  ".join(probs)))
