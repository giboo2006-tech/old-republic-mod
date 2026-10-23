#!/usr/bin/env python3
"""The grip designs (docs/MILESTONE_7.md 6.3): DESIGNS maps each grip id of HiltParts to its Design.
Grips live in the band y -0.1 to 3.7 with a body radius of 0.85 to 1.15 and a collar of radius 1.05 to
1.15 at both ends; the staff grip ("staff", slot staff_grip) spans y 4.6 to 11.4 in the staff model.

The Ridged grip is the lead's reference design: read it before writing another. Preview with
  python3 tools/assets/hilts/preview.py part grip ridged --out <scratch dir>
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from common import paint as cp  # noqa: E402
from lib import Design, around, box, lathe, octo, registry, side_box  # noqa: E402

DESIGNS, design = registry()


@design
def ridged():
	"""The classic: a steel tube with four rubber T-tracks of short ridges over its lower two thirds, a
	clamp band at the top carrying the activation box, a clamp lever behind and a clamp screw."""
	d = Design("grip", "ridged", "Ridged")
	steel, dark, satin, rubber = cp("steel"), cp("steel_dark"), cp("steel_satin"), cp("rubber")

	d.add(
		lathe([
			(-0.1, 0.12, 1.1, steel),      # the join collar
			(0.12, 2.7, 0.95, steel),      # the tube
			(2.7, 2.86, 1.04, dark),       # a turned band under the clamp
			(2.86, 3.52, 1.12, satin),     # the clamp
			(3.52, 3.7, 1.08, steel),      # the join collar
		]),
	)
	# a T-track: a rubber backing strip and six short ridges, on the four diagonal sides
	track = [side_box(0.28, 2.5, 0.95, 0.5, 0.06, rubber, name="track")]
	y = 0.3
	for i in range(6):
		track += side_box(y, y + 0.22, 0.95, 0.62, 0.2, rubber, name="ridge", seed=i)
		y += 0.36
	d.add(around(track, 4, start=45))
	# the activation box on the thumb side, the clamp lever behind, the clamp screw on the -z side
	d.add(
		side_box(2.94, 3.44, 1.12, 0.6, 0.28, cp("gunmetal"), name="box"),
		side_box(3.06, 3.32, 1.4, 0.26, 0.1, cp("button_black"), name="stud"),
		side_box(2.9, 3.48, 1.12, 0.46, 0.08, cp("steel_satin"), k=4, name="lever"),
		side_box(3.08, 3.3, 1.12, 0.22, 0.12, cp("brass"), k=2, name="screw"),
	)
	return d


@design
def staff():
	"""A plain stand-in for the saberstaff's long grip, so the staff previews work from the start: steel tubes
	to each end, a black ridged middle and an activation box for each blade, point-symmetric about the staff's
	centre (8, 8, 8). HG designs the real one."""
	d = Design("staff_grip", "staff", "Staff")
	steel, dark = cp("steel"), cp("steel_dark")

	d.add(
		lathe([
			(4.6, 4.8, 1.1, steel),        # the join collar
			(4.8, 6.4, 0.95, steel),       # the lower tube
			(6.4, 6.6, 1.05, dark),        # a turned band
			(6.6, 9.4, 1.0, cp("ttrack")), # the ridged middle
			(9.4, 9.6, 1.05, dark),        # a turned band
			(9.6, 11.2, 0.95, steel),      # the upper tube
			(11.2, 11.4, 1.1, steel),      # the join collar
		]),
		# an activation box under each blade: the upper one on the thumb side, the lower one turned with it
		side_box(10.0, 10.6, 0.95, 0.6, 0.28, cp("gunmetal"), name="box"),
		side_box(5.4, 6.0, 0.95, 0.6, 0.28, cp("gunmetal"), k=4, name="box"),
	)
	return d


if __name__ == "__main__":
	for id_, d in DESIGNS.items():
		probs = d.problems()
		print(f"{d.item_id}: {len(d.elements)} elements" + ("" if not probs else "\n  " + "\n  ".join(probs)))
