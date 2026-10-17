"""PLACEHOLDER sound recipes for the boss feature (W5): the Ruin Sentinel, Orren Vasch the Collector,
the resonance pylons, the Reliquary bed, the hoard and the three streamed fight-music loops.

Owned by W5. Synthesised from scratch with the helpers in gen_sounds.py (oscillators, filtered
noise, inharmonic partials, convolution reverb): nothing is sampled. See events_m3.json for the
event list and docs/MILESTONE_3.md sections 7.7, 8 and 13.3 (and docs/assets/boss.md) for the
intended final sound of each event. File names keep the Phase 0 placeholder names
(<group>/<rest of the id with dots as underscores>), so sounds.json does not change.

The music is procedural placeholder music, labelled as such: three seamless loops built from a
small sequencer (pads, plucked strings, crystal bells, frame drums, low stabs) whose lengths are
whole bars, so they loop on the beat.
"""
import gen_sounds as g

np = g.np


# ================================================================ building blocks

def midi(n):
	return 440.0 * 2 ** ((n - 69) / 12)


def grind(t, rng, env, fc=700, rate=900):
	"""Stone grinding on stone: stuttering band noise and fine grit."""
	n = len(t)
	grit = g.bp(g.crackle(n, rate, rng), fc * 2.2, 0.8) * 1.6
	stutter = 0.55 + 0.45 * np.abs(np.sin(2 * np.pi * 11 * t + rng.uniform(0, 3)))
	rub = g.bp(g.noise(n, rng), fc, 1.1) * stutter
	return (grit + rub) * env


def debris(t, rng, t0, tau, rate=200, fc=1700):
	"""Falling chips and pebbles after t0, thinning out with time constant tau."""
	curve = rate * np.exp(-np.maximum(t - t0, 0) / tau) * (t >= t0)
	return g.bp(g.crackle(len(t), curve, rng), fc, 0.9) * 2.6


def dust(t, rng, t0, attack, decay, fc=900):
	"""A soft low hiss of settling dust."""
	return g.lp(g.noise(len(t), rng), fc) * g.env_ad(t, attack, decay, t0) * 0.6


def crystal(t, rng, f0, decay, t0=0.0, amp=1.0):
	"""A struck crystal: chime partials with a glassy top."""
	n = len(t)
	ratios = g.CHIME_RATIOS
	decays = [decay, decay * 0.6, decay * 0.4, decay * 0.25, decay * 0.15]
	amps = [1.0, 0.5, 0.35, 0.2, 0.12]
	return g.bell_partials(n, f0, ratios, decays, amps, rng, t0=t0, attack=0.002) * amp


def inhale(t, rng, t_end, fc_lo=200, fc_hi=2600, q=None):
	"""Air drawn in: a rising low-passed rush that cuts off at t_end."""
	u = np.clip(t / max(t_end, 1e-3), 0, 1)
	fc = fc_lo + (fc_hi - fc_lo) * u ** 2
	rush = g.sweep(g.noise(len(t), rng), fc, "lp", nb=12)
	return rush * u ** 2 * np.where(t > t_end, np.exp(-(t - t_end) / 0.05), 1.0)


def force_hum(t, f, env, rng):
	"""The low bent-air hum of a Force effort: detuned sines with a breathy band."""
	n = len(t)
	f = np.broadcast_to(np.asarray(f, float), (n,))
	tone = np.sin(g.phase(f, n)) + 0.6 * np.sin(g.phase(f * 1.502, n)) + 0.3 * np.sin(g.phase(f * 2.01, n))
	air = g.bp(g.noise(n, rng), 420, 1.5) * 0.5
	return (tone * 0.5 + air) * env


def footstep(t, rng, t0, weight=1.0):
	"""One boot on stone."""
	heel = g.thump(t, 140, 70, 0.015, 0.05, t0=t0) * 0.7
	scuff = g.bp(g.noise(len(t), rng), 1800, 1.2) * g.env_ad(t, 0.004, 0.05, t0) * 0.5
	return (heel + scuff) * weight


# ================================================================ Ruin Sentinel

def g_sentinel_wake(rng):
	"""Stone grinds loose, the lumen ignites with a low glassy hum swelling up."""
	t = g.tt(1.6)
	env = g.env_ad(t, 0.25, 0.6)
	stone = grind(t, rng, env, 650) * 0.9
	f = 70 + 40 * np.clip(t / 0.9, 0, 1)
	ignite = force_hum(t, f, np.clip((t - 0.2) / 0.7, 0, 1) * np.exp(-np.maximum(t - 1.1, 0) / 0.25), rng) * 0.9
	glass = crystal(t, rng, 1240, 0.5, t0=0.55, amp=0.25)
	return g.reverb(stone + ignite + glass + debris(t, rng, 0.1, 0.5, 90, 1500) * 0.4, rng, 1.0, 0.25, bright=3500)


def g_sentinel_slam_tell(rng):
	"""Arms rising: a heavy grind and a creak of bronze, rising in pitch."""
	t = g.tt(1.1)
	env = np.clip(t / 0.15, 0, 1) * np.exp(-np.maximum(t - 0.7, 0) / 0.15)
	stone = grind(t, rng, env, 500 + 400 * np.clip(t / 0.8, 0, 1).mean()) * 0.8
	f = 160 * (1 + 0.6 * np.clip(t / 0.8, 0, 1))
	creak = g.bp(g.saw(g.phase(f, len(t)), 260, nh=20), 900, 3) * env * 0.4
	return g.reverb(stone + creak + dust(t, rng, 0.0, 0.2, 0.5) * 0.5, rng, 0.8, 0.2, bright=3000)


def g_sentinel_slam(rng):
	"""The arms driven into the floor: a deep stone impact, a crunch and a spray of debris."""
	t = g.tt(1.6)
	boom = g.thump(t, 85, 30, 0.07, 0.4) * 1.1
	crunch = g.bp(g.noise(len(t), rng), 750, 0.8) * g.env_ad(t, 0.002, 0.07) * 1.8
	crack = g.crack(t, rng, 0.0, 0.015, 600) * 1.2
	ring = g.metal_ring(t, rng, 420, 5, 0.3) * 0.25
	return g.reverb(boom + crunch + crack + ring + debris(t, rng, 0.03, 0.35, 260) + dust(t, rng, 0.05, 0.1, 0.6),
			rng, 1.2, 0.25, bright=3000)


def g_sentinel_core_exposed(rng):
	"""The chest plates grind apart and the core's hum rises, bright and exposed."""
	t = g.tt(1.4)
	plates = grind(t, rng, g.env_ad(t, 0.02, 0.18), 1100, 600) * 0.7
	clank = g.metal_ring(t, rng, 610, 6, 0.25, t0=0.18) * 0.4 + g.thump(t, 160, 90, 0.02, 0.06, t0=0.18) * 0.4
	f = 110 + 60 * np.clip((t - 0.15) / 0.4, 0, 1)
	hum = force_hum(t, f, np.clip((t - 0.15) / 0.2, 0, 1) * np.exp(-np.maximum(t - 0.5, 0) / 0.5), rng) * 0.8
	glass = crystal(t, rng, 990, 0.7, t0=0.2, amp=0.3)
	return g.reverb(plates + clank + hum + glass, rng, 1.0, 0.22, bright=4500)


def g_sentinel_lance_charge(rng):
	"""Motes drawn into the chest: an intake of air under a rising glassy whine (the 24-tick tell)."""
	t = g.tt(1.35)
	u = np.clip(t / 1.2, 0, 1)
	f = 300 * 4 ** u
	whine = (np.sin(g.phase(f, len(t))) + 0.4 * np.sin(g.phase(f * 2.003, len(t)))) * u ** 1.5
	air = inhale(t, rng, 1.2, 300, 3800) * 0.8
	tick = g.bp(g.crackle(len(t), 40 + 400 * u ** 2, rng), 4200, 1.2) * 0.6
	x = whine * 0.45 + air + tick
	x *= np.where(t > 1.2, np.exp(-(t - 1.2) / 0.03), 1.0)
	return g.reverb(x, rng, 0.6, 0.15, bright=6000)


def g_sentinel_lance_fire(rng):
	"""A heavy amber-white bolt: a deep blaster discharge with a crystal crack and a stone body."""
	bolt = g.make_blaster_fire(2100, 110, 0.05, 520)(rng)
	t = g.tt(len(bolt) / g.SR)
	body = g.thump(t, 95, 40, 0.05, 0.18) * 0.8
	glass = crystal(t, rng, 1480, 0.25, amp=0.35) + g.crack(t, rng, 0.0, 0.008, 2500) * 0.6
	return bolt + body + glass


def g_sentinel_hurt(rng):
	"""A crack in the stone, a few chips and a dull bronze knock."""
	t = g.tt(0.7)
	crack = g.crack(t, rng, 0.0, 0.012, 800) * 1.3
	knock = g.thump(t, 220, 120, 0.02, 0.06) * 0.6 + g.metal_ring(t, rng, 780, 4, 0.12) * 0.25
	chips = debris(t, rng, 0.02, 0.12, 160, 2200) * 0.6
	return g.reverb(crack + knock + chips, rng, 0.6, 0.18, bright=4000)


def g_sentinel_death(rng):
	"""The lumen's hum gutters and falls while the body cracks and crumbles into rubble and dust."""
	t = g.tt(2.6)
	f = 120 * np.exp(-t / 0.6) + 35
	stutter = 0.5 + 0.5 * np.sign(np.sin(2 * np.pi * (9 * t + 6 * t ** 2)))
	gutter = force_hum(t, f, g.lp(stutter, 120) * g.env_ad(t, 0.01, 0.5), rng) * 0.8
	cracks = sum(g.crack(t, rng, t0, 0.012, 700) * a for t0, a in ((0.0, 1.2), (0.25, 0.8), (0.55, 1.0), (0.9, 0.6)))
	collapse = g.thump(t, 75, 30, 0.08, 0.35, t0=0.6) * 0.9 + g.thump(t, 90, 40, 0.06, 0.25, t0=1.05) * 0.6
	rubble = debris(t, rng, 0.2, 0.9, 320, 1500) + debris(t, rng, 0.6, 0.6, 200, 900) * 0.6
	ring = g.metal_ring(t, rng, 350, 5, 0.5, t0=0.62) * 0.3
	return g.reverb(gutter + cracks + collapse + rubble + ring + dust(t, rng, 0.5, 0.4, 1.2, 700),
			rng, 1.4, 0.28, bright=3000)


# ================================================================ Orren Vasch

def g_collector_hurt(rng):
	"""A tired man's sharp exhale and grunt, robes snapping."""
	t = g.tt(0.6)
	voice = g.grunt(t, rng, 0.0, 0.2, 150, 105, 560, 1150) * 1.3
	cloth = g.rustle(t, rng, 0.0, 0.15, 90, fc=2400) * 0.4
	return g.reverb(voice + cloth, rng, 0.6, 0.2, bright=4000)


def g_hurl_lift(rng):
	"""A chunk tears from the pile: cracking, grinding, grit pouring off it, a low Force hum swelling."""
	t = g.tt(1.4)
	tear = g.crack(t, rng, 0.0, 0.02, 500) * 1.3 + grind(t, rng, g.env_ad(t, 0.01, 0.3), 600) * 0.8
	pour = debris(t, rng, 0.05, 0.6, 220, 2000) * 0.8
	hum = force_hum(t, 65 + 25 * np.clip(t / 0.8, 0, 1), np.clip(t / 0.3, 0, 1) * np.exp(-np.maximum(t - 0.9, 0) / 0.2), rng) * 0.7
	return g.reverb(tear + pour + hum, rng, 1.1, 0.25, bright=3500)


def g_hurl_throw(rng):
	"""A heavy stone flung: a deep pressure push and a rough whoosh."""
	t = g.tt(0.9)
	b = g.bell(t, 0.18, 0.06, 0.25)
	push = g.thump(t, 70, 40, 0.05, 0.12) * 0.8
	rush = g.whoosh(t, rng, 250 + 1000 * b, 1.1, b) * 1.4
	grit = debris(t, rng, 0.05, 0.25, 120, 2400) * 0.4
	return g.reverb(push + rush + grit, rng, 0.8, 0.2)


def g_hurl_rip(rng):
	"""The chunk ripped from his hold crashes down between them."""
	t = g.tt(1.4)
	fall = g.whoosh(t, rng, 600 * np.exp(-t / 0.2) + 200, 1.3, g.env_ad(t, 0.05, 0.1)) * 0.6
	hit = g.thump(t, 90, 32, 0.06, 0.32, t0=0.18) * 1.1 + g.crack(t, rng, 0.18, 0.02, 500) * 1.3
	return g.reverb(fall + hit + debris(t, rng, 0.2, 0.4, 280) + dust(t, rng, 0.2, 0.1, 0.6), rng, 1.2, 0.25, bright=3000)


def g_hurl_return(rng):
	"""Pushed back at him: a bent-air streak, a pressure snap and the stone tumbling away."""
	t = g.tt(0.9)
	snap = g.thump(t, 120, 50, 0.02, 0.08) * 0.8 + g.bp(g.noise(len(t), rng), 600, 1.0) * g.env_ad(t, 0.002, 0.05)
	b = g.bell(t, 0.25, 0.05, 0.3)
	streak = g.whoosh(t, rng, 2400 * np.exp(-t / 0.3) + 300, 1.6, b) * 1.3
	return g.reverb(snap + streak + debris(t, rng, 0.05, 0.3, 100) * 0.4, rng, 0.8, 0.22)


def g_palm_tell(rng):
	"""Air and dust drawn toward his palm, a short in-breath that stops dead."""
	t = g.tt(0.9)
	air = inhale(t, rng, 0.7, 180, 2200) * 1.2
	grit = g.bp(g.crackle(len(t), 200 * np.clip(t / 0.7, 0, 1), rng), 2600, 1.0) * (t < 0.72) * 0.5
	hum = force_hum(t, 80 + 30 * np.clip(t / 0.7, 0, 1), np.clip(t / 0.7, 0, 1) ** 2 * (t < 0.72), rng) * 0.5
	return g.reverb(air + grit + hum, rng, 0.7, 0.18)


def g_lash_tell(rng):
	"""A seam of floor dust lifts in a line: a dry rising hiss of grit."""
	t = g.tt(1.1)
	u = np.clip(t / 0.9, 0, 1)
	hiss = g.sweep(g.noise(len(t), rng), 400 + 2400 * u, "bp", 1.3, nb=10) * u * np.where(t > 0.9, np.exp(-(t - 0.9) / 0.05), 1)
	grit = g.bp(g.crackle(len(t), 100 + 600 * u, rng), 3000, 1.0) * u * 0.6
	return g.reverb(hiss * 0.8 + grit, rng, 0.8, 0.2, bright=5000)


def g_lash_wave(rng):
	"""A travelling ridge of dust and grit: a rolling rumble that passes and fades."""
	t = g.tt(1.6)
	roll = g.lp(g.noise(len(t), rng), 140) * g.bell(t, 0.45, 0.15, 0.5) * 2.0
	stutter = 0.5 + 0.5 * np.abs(np.sin(2 * np.pi * 7 * t))
	grit = debris(t, rng, 0.0, 0.8, 350, 1300) * stutter
	whoosh = g.whoosh(t, rng, 300 + 700 * g.bell(t, 0.45, 0.2, 0.4), 1.2, g.bell(t, 0.45, 0.2, 0.4)) * 0.6
	return g.reverb(roll + grit + whoosh, rng, 1.0, 0.22, bright=3000)


def g_surge_gather(rng):
	"""The air bends toward him: a long inward rush with the stolen holocron's dissonant undertone."""
	t = g.tt(1.9)
	rush = inhale(t, rng, 1.5, 120, 3200) * 1.4
	f = 55 * (1 + 0.3 * np.clip(t / 1.5, 0, 1))
	under = (np.sin(g.phase(f, len(t))) + 0.5 * np.sin(g.phase(f * 1.06, len(t)))) * np.clip(t / 1.5, 0, 1) ** 2 * (t < 1.52)
	grit = debris(t, rng, 0.2, 1.0, 120, 2400) * np.clip(t / 1.5, 0, 1) * 0.5
	return g.reverb(rush + under * 0.6 + grit, rng, 1.2, 0.25, bright=3500)


def g_surge_release(rng):
	"""The Surge breaks outward: a deep pressure blast, a roaring wall of air and dust, tumbling stone."""
	t = g.tt(2.6)
	blast = g.thump(t, 70, 24, 0.1, 0.6) * 1.2 + g.crack(t, rng, 0.0, 0.03, 300) * 1.4
	b = g.env_ad(t, 0.02, 0.6)
	roar = g.sweep(g.noise(len(t), rng), 2200 * np.exp(-t / 0.6) + 180, "lp", nb=12) * b * 1.6
	stones = debris(t, rng, 0.1, 0.9, 320, 1400) + debris(t, rng, 0.3, 0.7, 150, 800) * 0.7
	return g.reverb(blast + roar + stones + dust(t, rng, 0.2, 0.3, 1.2), rng, 1.8, 0.3, bright=3000)


def g_surge_clash(rng):
	"""The wall breaks against a blade or a Push: a blade-on-pressure clash and air tearing past."""
	clash = g.make_clash(820, g.SABER_F * 1.2)(rng)
	t = g.tt(len(clash) / g.SR)
	tear = g.whoosh(t, rng, 3000 * np.exp(-t / 0.2) + 400, 1.4, g.env_ad(t, 0.005, 0.3)) * 1.2
	thud = g.thump(t, 80, 40, 0.05, 0.2) * 0.7
	return clash + tear + thud


def g_spent(rng):
	"""He sags: a ragged out-breath, the holocron's light guttering down, dust settling."""
	t = g.tt(1.8)
	breath = g.bp(g.noise(len(t), rng), 900, 1.5) * g.env_ad(t, 0.05, 0.35) * 0.8
	voice = g.grunt(t, rng, 0.02, 0.4, 120, 85, 500, 1000) * 0.6
	f = 210 * np.exp(-t / 0.5) + 60
	stutter = 0.5 + 0.5 * np.sign(np.sin(2 * np.pi * (6 * t + 5 * t ** 2)))
	gutter = np.sin(g.phase(f, len(t))) * g.lp(stutter, 90) * g.env_ad(t, 0.01, 0.6) * 0.5
	return g.reverb(breath + voice + gutter + dust(t, rng, 0.3, 0.3, 0.8, 600), rng, 1.2, 0.25)


def g_grip(rng):
	"""The Grip closes: a tight low choke of pressure and a creak, pebbles lifting."""
	t = g.tt(1.1)
	env = np.clip(t / 0.15, 0, 1) * np.exp(-np.maximum(t - 0.5, 0) / 0.3)
	choke = force_hum(t, 48 + 10 * np.sin(2 * np.pi * 3 * t), env, rng) * 1.0
	creak = g.bp(g.saw(g.phase(110 + 40 * t, len(t)), 160, nh=20), 700, 4) * env * 0.4
	pebbles = g.bp(g.crackle(len(t), 160 * env, rng), 2800, 1.0) * 0.6
	return g.reverb(choke + creak + pebbles, rng, 0.9, 0.22)


def g_grip_hold(rng):
	"""Held: a straining, wavering pressure and grit trickling."""
	t = g.tt(1.4)
	env = g.env_ad(t, 0.1, 0.8)
	strain = force_hum(t, 52 * (1 + 0.04 * np.sin(2 * np.pi * 5 * t)), env, rng) * 0.9
	trickle = debris(t, rng, 0.0, 1.0, 120, 2600) * 0.5
	return g.reverb(strain + trickle, rng, 0.9, 0.22)


def g_grip_break(rng):
	"""The Grip breaks: a burst of grit and the pressure letting go."""
	t = g.tt(0.9)
	burst = g.bp(g.noise(len(t), rng), 1400, 0.8) * g.env_ad(t, 0.002, 0.06) * 1.4
	release = g.whoosh(t, rng, 2000 * np.exp(-t / 0.15) + 300, 1.2, g.env_ad(t, 0.005, 0.2)) * 1.0
	thud = g.thump(t, 140, 60, 0.03, 0.1) * 0.6
	return g.reverb(burst + release + thud + debris(t, rng, 0.0, 0.25, 260, 2400) * 0.6, rng, 0.8, 0.2)


def g_lunge(rng):
	"""The Unkept lunge: a kick of dust, then the amber blade's streaking swing."""
	swing = g.make_swing(dict(dur=0.75, tp=0.32, f0=84, up=0.24, down=0.14, w1=0.12, w2=0.14, wh=1900))(rng)
	t = g.tt(len(swing) / g.SR)
	kick = g.bp(g.noise(len(t), rng), 600, 1.0) * g.env_ad(t, 0.003, 0.06) * 0.8 + g.thump(t, 110, 50, 0.03, 0.08) * 0.6
	return swing + kick


def g_ward_hum(rng):
	"""Seamless ward hum: a shimmering crystal resonance with slow beating and air (4 s loop)."""
	L = 4.0
	t = g.tt(L)
	n = len(t)
	x = np.zeros(n)
	for f, a in ((220.0, 1.0), (221.5, 0.7), (330.4, 0.5), (440.9, 0.35), (661.0, 0.18)):
		x += a * np.sin(g.phase(np.full(n, f), n, loop=True) + rng.uniform(0, 6.28))
	am = 1 + 0.18 * np.sin(2 * np.pi * 6 * t / L) + 0.08 * np.sin(2 * np.pi * 14 * t / L + 0.7)
	air = g.bp(g.noise(n, rng), 3200, 2.5, loop=True) * 0.25 * (1 + 0.4 * np.sin(2 * np.pi * 2 * t / L))
	return g.reverb(x * am * 0.4 + air, rng, 1.6, 0.3, loop=True, bright=5000)


def g_unsung(rng):
	"""The ward collapses: the currents snap back with a crystal crack, a falling tone and a rumble of ceiling dust."""
	t = g.tt(2.4)
	snap = g.crack(t, rng, 0.0, 0.01, 2000) * 1.0 + crystal(t, rng, 1660, 0.35, amp=0.6)
	f = 440 * np.exp(-t / 0.4) + 70
	fall = (np.sin(g.phase(f, len(t))) + 0.4 * np.sin(g.phase(f * 1.5, len(t)))) * g.env_ad(t, 0.01, 0.7) * 0.5
	rumble = g.lp(g.noise(len(t), rng), 120) * g.env_ad(t, 0.15, 0.8, 0.1) * 1.5
	return g.reverb(snap + fall + rumble + debris(t, rng, 0.3, 0.8, 140, 1800) * 0.6, rng, 1.6, 0.3)


def g_transition(rng):
	"""The Reliquary shakes: a deep rumble, falling grit and the crystals' rising choir."""
	t = g.tt(3.2)
	rumble = g.lp(g.noise(len(t), rng), 90) * g.env_ad(t, 0.3, 1.2) * 2.2 + g.thump(t, 60, 25, 0.15, 0.8) * 0.8
	choir = np.zeros(len(t))
	for f in (220.0, 261.6, 329.6, 392.0):
		choir += np.sin(g.phase(np.full(len(t), f * (1 + 0.003 * rng.standard_normal())), len(t)))
	choir = g.bp(choir, 600, 0.7) * np.clip(t / 1.5, 0, 1) * np.exp(-np.maximum(t - 2.0, 0) / 0.5) * 0.35
	shards = sum(crystal(t, rng, f, 0.5, t0=t0, amp=0.25) for f, t0 in ((1320, 0.8), (1760, 1.2), (1480, 1.6), (1980, 1.9)))
	return g.reverb(rumble + choir + shards + debris(t, rng, 0.2, 1.4, 160, 1500), rng, 2.0, 0.32, bright=3500)


def g_yield(rng):
	"""He yields: the amber blade retracts, the crystals dim in a falling chime, a low drone settles."""
	retract = g.g_saber_retract(rng)
	t = g.tt(4.0)
	x = np.zeros(len(t))
	x[:len(retract)] += retract * 0.9
	for k, f in enumerate((1318.5, 1174.7, 987.8, 880.0)):
		x += crystal(t, rng, f, 0.9, t0=0.35 + 0.3 * k, amp=0.18)
	drone = force_hum(t, 55.0, np.clip((t - 0.6) / 1.0, 0, 1) * np.exp(-np.maximum(t - 2.8, 0) / 0.5), rng) * 0.6
	return g.reverb(x + drone + dust(t, rng, 0.5, 0.5, 1.5, 600), rng, 1.8, 0.3)


def g_leash(rng):
	"""A puff of smoke: a soft whoomp and a hiss where he was."""
	t = g.tt(1.0)
	whoomp = g.thump(t, 90, 45, 0.04, 0.12) * 0.6 + g.lp(g.noise(len(t), rng), 500) * g.env_ad(t, 0.01, 0.08) * 0.8
	hiss = g.bp(g.noise(len(t), rng), 2500, 0.9) * g.env_ad(t, 0.02, 0.35) * 0.5
	return g.reverb(whoomp + hiss, rng, 1.0, 0.25)


def g_echo_form(rng):
	"""The echo gathers from smoke over 60 ticks: a reversed swirl and a cold, rising choir."""
	t = g.tt(3.2)
	u = np.clip(t / 3.0, 0, 1)
	swirl = g.sweep(g.noise(len(t), rng), 300 + 1800 * u ** 2, "bp", 1.0, nb=12) * u ** 1.5 * 0.9
	choir = np.zeros(len(t))
	for f in (196.0, 233.1, 293.7):
		choir += np.sin(g.phase(f * (1 + 0.01 * np.sin(2 * np.pi * 4.5 * t)), len(t)))
	choir = g.bp(choir, 700, 0.8) * u ** 2 * 0.4
	voices = g.whisper_voice(3.2, rng, 0.9, 0.3, 2.8) * u * 0.5
	x = (swirl + choir + voices) * np.where(t > 3.0, np.exp(-(t - 3.0) / 0.08), 1.0)
	return g.reverb(x, rng, 2.0, 0.35, bright=4000)


def g_echo_fade(rng):
	"""The echo breaks into slow smoke that drains into the floor: a falling hiss and a choir fading."""
	t = g.tt(3.0)
	hiss = g.sweep(g.noise(len(t), rng), 2400 * np.exp(-t / 1.0) + 200, "bp", 1.0, nb=12) * g.env_ad(t, 0.05, 1.0) * 0.9
	choir = np.zeros(len(t))
	for f in (196.0, 233.1, 293.7):
		choir += np.sin(g.phase(f * np.exp(-t / 6.0), len(t)))
	choir = g.bp(choir, 700, 0.8) * g.env_ad(t, 0.02, 0.9) * 0.35
	return g.reverb(hiss + choir, rng, 2.0, 0.35, bright=4000)


def g_binders(rng):
	"""Republic or Imperial binders lock: two ratchet clicks and a solid clank."""
	t = g.tt(0.8)
	clicks = g.click(t, rng, 0.02, 1.0) * 0.6 + g.click(t, rng, 0.09, 1.1) * 0.6
	clank = g.metal_ring(t, rng, 1350, 6, 0.18, t0=0.2) * 0.8 + g.thump(t, 300, 160, 0.01, 0.04, t0=0.2) * 0.5
	return g.reverb(clicks + clank, rng, 0.6, 0.15)


def g_execute(rng):
	"""A single stroke: the swing, the blade's sear, a body settling to the stone, the amber hum guttering out."""
	swing = g.make_swing(dict(dur=0.6, tp=0.22, f0=82, up=0.2, down=0.16, w1=0.08, w2=0.14, wh=1500))(rng)
	t = g.tt(2.6)
	x = np.zeros(len(t))
	x[:len(swing)] += swing
	sear = g.bp(g.noise(len(t), rng), 3600, 0.9) * g.env_ad(t, 0.004, 0.12, 0.22) * 0.6
	fall = g.thump(t, 90, 45, 0.05, 0.2, t0=0.9) * 0.6 + g.bp(g.noise(len(t), rng), 300, 1.0) * g.env_ad(t, 0.01, 0.1, 0.9) * 0.4
	f = 30 + (g.SABER_F - 30) * np.exp(-np.maximum(t - 1.3, 0) / 0.5)
	gutter = g.saber_tone(f, rng, bright=1600) * np.clip((t - 1.2) / 0.1, 0, 1) * np.exp(-np.maximum(t - 1.3, 0) / 0.5) * 0.4
	return g.reverb(x + sear + fall + gutter + dust(t, rng, 1.0, 0.3, 0.8, 600), rng, 1.4, 0.28)


def g_walk_out(rng):
	"""Footsteps on stone receding toward the descent."""
	t = g.tt(4.0)
	x = np.zeros(len(t))
	for k in range(7):
		t0 = 0.1 + k * 0.52 + rng.uniform(-0.03, 0.03)
		x += footstep(t, rng, t0, 0.9 * 0.78 ** k)
	cloth = g.rustle(t, rng, 0.0, 3.6, 25, fc=2200) * 0.15 * np.exp(-t / 1.5)
	return g.reverb(g.lp(x, 3000) + cloth, rng, 1.6, 0.35, bright=3000)


# ================================================================ pylons

def g_pylon_sing(rng):
	"""Seamless singing crystal: a sustained tone with a slow vibrato and a breathy formant (3 s loop)."""
	L = 3.0
	t = g.tt(L)
	n = len(t)
	vib = 1 + 0.004 * np.sin(2 * np.pi * 15 * t / L)
	x = np.zeros(n)
	for f, a in ((523.3, 1.0), (784.9, 0.45), (1046.5, 0.3), (1569.0, 0.12)):
		x += a * np.sin(g.phase(f * vib, n, loop=True) + rng.uniform(0, 6.28))
	am = 1 + 0.12 * np.sin(2 * np.pi * 4 * t / L)
	breath = g.bp(g.noise(n, rng), 1050, 4, loop=True) * 0.18 * am
	return g.reverb(x * am * 0.4 + breath, rng, 1.4, 0.3, loop=True, bright=6000)


def g_pylon_crack(rng):
	"""A crystal chip struck off: a glassy tick and a short ring."""
	t = g.tt(0.6)
	return g.reverb(g.crack(t, rng, 0.0, 0.004, 3000) * 0.9 + crystal(t, rng, 2093, 0.18, amp=0.5)
			+ debris(t, rng, 0.0, 0.08, 200, 4500) * 0.3, rng, 0.6, 0.18, bright=7000)


def g_pylon_silence(rng):
	"""The crystal cracks dark: a split, its song falling away, glints tinkling down."""
	t = g.tt(1.8)
	split = g.crack(t, rng, 0.0, 0.015, 1500) * 1.2
	f = 523.3 * np.exp(-t / 0.5) + 130
	fall = (np.sin(g.phase(f, len(t))) + 0.4 * np.sin(g.phase(f * 1.5, len(t)))) * g.env_ad(t, 0.005, 0.5) * 0.4
	glints = sum(crystal(t, rng, rng.uniform(2400, 4200), 0.12, t0=t0, amp=0.18) for t0 in (0.15, 0.3, 0.42, 0.6, 0.85))
	return g.reverb(split + fall + glints, rng, 1.2, 0.25, bright=6000)


def g_pylon_regrow(rng):
	"""The crystal grows back over the 60-tick tell: a crystalline crackle and a swelling tone."""
	t = g.tt(3.2)
	u = np.clip(t / 3.0, 0, 1)
	crackle = g.bp(g.crackle(len(t), 80 + 500 * u, rng), 3600, 1.4) * 0.6
	f = 523.3 * (0.75 + 0.25 * u)
	tone = (np.sin(g.phase(f, len(t))) + 0.4 * np.sin(g.phase(f * 1.5, len(t)))) * u ** 2 * 0.4
	x = (crackle + tone) * np.where(t > 3.0, np.exp(-(t - 3.0) / 0.06), 1.0)
	return g.reverb(x, rng, 1.2, 0.25, bright=6000)


def g_pylon_shatter(rng):
	"""The pylon shatters into him: a bright crash of glass and shards ringing out."""
	t = g.tt(2.0)
	crash = g.crack(t, rng, 0.0, 0.03, 1800) * 1.4 + g.bp(g.noise(len(t), rng), 5000, 0.7) * g.env_ad(t, 0.002, 0.15) * 1.0
	shards = sum(crystal(t, rng, rng.uniform(1500, 3800), 0.4, t0=rng.uniform(0.0, 0.3), amp=0.3) for _ in range(8))
	whoosh = g.whoosh(t, rng, 3000 * np.exp(-t / 0.3) + 500, 1.2, g.env_ad(t, 0.01, 0.3)) * 0.6
	return g.reverb(crash + shards + whoosh, rng, 1.4, 0.28, bright=7000)


# ================================================================ the Reliquary and the hoard

def g_ambient_reliquary(rng):
	"""Seamless Reliquary bed (16 s): a low crystal drone with slow beating, cold air, distant drips and settling stone."""
	L = 16.0
	t = g.tt(L)
	n = len(t)
	drone = np.zeros(n)
	for f, a in ((110.0, 1.0), (110.7, 0.8), (164.8, 0.45), (220.2, 0.25), (330.0, 0.1)):
		drone += a * np.sin(g.phase(np.full(n, f), n, loop=True) + rng.uniform(0, 6.28))
	drone *= 1 + 0.2 * np.sin(2 * np.pi * 2 * t / L) + 0.1 * np.sin(2 * np.pi * 5 * t / L + 1.1)
	fc = 260 + 120 * np.sin(2 * np.pi * t / L) + 60 * np.sin(2 * np.pi * 3 * t / L + 0.5)
	air = g.sweep(g.noise(n, rng), fc, "lp", nb=10, loop=True) * 0.8
	events = np.zeros(n)
	for k in range(9):
		t0 = (k + rng.uniform(0.1, 0.9)) * L / 9
		drip = crystal(g.tt(1.2), rng, rng.uniform(1300, 2400), 0.25, amp=0.25)
		g.place_circular(events, drip, t0)
	for t0 in (L * 0.3, L * 0.78):
		tt_ = g.tt(1.5)
		settle = g.thump(tt_, 60, 30, 0.1, 0.4) * 0.3 + debris(tt_, rng, 0.05, 0.4, 60, 1200) * 0.2
		g.place_circular(events, settle, t0)
	return g.reverb(drone * 0.18 + air + events, rng, 2.4, 0.35, loop=True, bright=3500)


def g_hoard_open(rng):
	"""The vault seal crumbles and motes rise from three holocrons: a stone collapse, then a resolving shimmer."""
	t = g.tt(3.0)
	crumble = g.crack(t, rng, 0.0, 0.02, 600) + g.thump(t, 80, 35, 0.08, 0.3, t0=0.05) * 0.9
	rubble = debris(t, rng, 0.05, 0.6, 260, 1500) + dust(t, rng, 0.1, 0.2, 0.9)
	shimmer = sum(crystal(t, rng, f, 1.2, t0=t0, amp=0.25) for f, t0 in ((880.0, 0.7), (1108.7, 0.95), (1318.5, 1.2)))
	return g.reverb(crumble + rubble + shimmer, rng, 1.8, 0.3, bright=5000)


# ================================================================ music (procedural placeholders)

def inst_pad(freqs, dur, attack=0.6, release=1.0, bright=900):
	t = g.tt(dur + release)
	n = len(t)
	x = np.zeros(n)
	for f in freqs:
		for det in (0.996, 1.004):
			x += g.saw(g.phase(np.full(n, f * det), n), f, nh=10)
	env = np.clip(t / attack, 0, 1) * np.where(t > dur, np.exp(-(t - dur) / (release / 3)), 1.0)
	return g.lp(x, bright) * env / (2 * len(freqs))


def inst_pluck(f, dur, rng, bright=0.6):
	t = g.tt(dur)
	return g.bell_partials(len(t), f, [1, 2, 3, 4, 5], [dur * 0.45, dur * 0.25, dur * 0.15, dur * 0.1, dur * 0.06],
			[1, 0.55 * bright, 0.3 * bright, 0.15 * bright, 0.08 * bright], rng, attack=0.003, detune=0.001)


def inst_bell(f, dur, rng):
	return crystal(g.tt(dur), rng, f, dur * 0.5)


def inst_bass(f, dur):
	t = g.tt(dur)
	n = len(t)
	x = np.sin(g.phase(np.full(n, f), n)) + 0.4 * g.lp(g.saw(g.phase(np.full(n, f), n), f, nh=12), 300)
	return x * g.env_ad(t, 0.01, dur * 0.6)


def inst_drum(rng, f_hi=95, f_lo=45, decay=0.3, skin=0.4):
	t = g.tt(decay * 3)
	return g.thump(t, f_hi, f_lo, 0.04, decay) + g.bp(g.noise(len(t), rng), 320, 1.0) * g.env_ad(t, 0.001, 0.04) * skin


def inst_stab(f, dur, rng):
	t = g.tt(dur)
	n = len(t)
	x = g.saw(g.phase(np.full(n, f), n), f, nh=20) + g.saw(g.phase(np.full(n, f * 1.006), n), f, nh=20)
	x = g.sweep(x, 250 + 1700 * np.exp(-t / 0.1), "lp", nb=8)
	return g.sat(x * g.env_ad(t, 0.008, dur * 0.45), 2.0)


def arrange(L, events):
	buf = np.zeros(g.ns(L))
	for t0, x in events:
		g.place_circular(buf, x, t0 % L)
	return buf


def g_music_archivist(rng):
	"""Phase 1, "The Archivist" (80 BPM, 8 bars of A minor): measured and melancholy. Pads, a plucked
	arpeggio, a slow archival chime motif and a soft frame drum."""
	beat = 60 / 80
	bar = beat * 4
	L = bar * 8
	chords = [(45, (57, 60, 64)), (43, (57, 60, 64)), (41, (53, 57, 60)), (40, (52, 56, 59)),
			(45, (57, 60, 64)), (38, (50, 53, 57)), (41, (53, 57, 60)), (40, (52, 56, 59))]
	pad, bass, pluck, bells, drums = [], [], [], [], []
	arp = [0, 1, 2, 1, 0, 2, 1, 2]
	for b, (root, chord) in enumerate(chords):
		t0 = b * bar
		pad.append((t0, inst_pad([midi(n) for n in chord], bar, 0.8, 1.2, 800)))
		bass.append((t0, inst_bass(midi(root), bar * 0.9)))
		bass.append((t0 + beat * 3.5, inst_bass(midi(root + 7), beat * 0.5) * 0.6))
		for i, k in enumerate(arp):
			vel = 0.55 if i % 2 == 0 else 0.35
			pluck.append((t0 + i * beat / 2, inst_pluck(midi(chord[k] + 12), 1.4, rng) * vel))
		drums.append((t0, inst_drum(rng, 90, 42, 0.35) * 0.9))
		drums.append((t0 + 2 * beat, inst_drum(rng, 80, 40, 0.25) * 0.45))
		if b in (1, 5):
			for (n, at) in ((76, 0.0), (74, 1.5), (72, 2.0), (69, 3.0)):
				bells.append((t0 + at * beat, inst_bell(midi(n), 2.4, rng) * 0.5))
	mix = arrange(L, pad) * 0.8 + arrange(L, bass) * 0.55 + arrange(L, pluck) * 0.45 + arrange(L, bells) * 0.4 + arrange(L, drums) * 0.6
	return g.reverb(mix, rng, 2.2, 0.28, loop=True, bright=4000)


def g_music_singing_wall(rng):
	"""Phase 2, "The Singing Wall" (96 BPM, 8 bars of D minor): the pylons' crystal ostinato over pulsing
	pads, a driving bass and drums on every beat."""
	beat = 60 / 96
	bar = beat * 4
	L = bar * 8
	chords = [(38, (50, 53, 57)), (34, (46, 50, 53)), (36, (48, 52, 55)), (33, (45, 49, 52))] * 2
	ostinato = [81, 86, 88, 86, 89, 88, 86, 88]
	pad, bass, bells, drums = [], [], [], []
	for b, (root, chord) in enumerate(chords):
		t0 = b * bar
		pad.append((t0, inst_pad([midi(n) for n in chord], bar, 0.3, 0.8, 1100)))
		for i in range(8):
			bass.append((t0 + i * beat / 2, inst_bass(midi(root), beat * 0.45) * (0.8 if i % 2 == 0 else 0.5)))
			bells.append((t0 + i * beat / 2, inst_bell(midi(ostinato[i] - (2 if b % 4 == 3 else 0)), 0.9, rng) * (0.5 if i % 2 == 0 else 0.35)))
		for k in range(4):
			drums.append((t0 + k * beat, inst_drum(rng, 100, 45, 0.25) * (0.9 if k in (0, 2) else 0.5)))
		drums.append((t0 + 3.5 * beat, inst_drum(rng, 140, 70, 0.12, 0.8) * 0.35))
	n = g.ns(L)
	t = np.arange(n) / g.SR
	# Sixteenth-note pulse on the pad (a whole number of pulses per loop).
	pulse = 0.65 + 0.35 * (0.5 + 0.5 * np.cos(2 * np.pi * (L / (beat / 4)) * t / L))
	mix = arrange(L, pad) * pulse * 0.8 + arrange(L, bass) * 0.5 + arrange(L, bells) * 0.35 + arrange(L, drums) * 0.65
	return g.reverb(mix, rng, 2.0, 0.28, loop=True, bright=4500)


def g_music_unkept(rng):
	"""Phase 3, "Unkept" (112 BPM, 8 bars of C harmonic minor): dark and driving. Low saw stabs off the
	beat, a taiko pattern, dissonant crystal clusters and a saturated drone."""
	beat = 60 / 112
	bar = beat * 4
	L = bar * 8
	chords = [(36, (48, 51, 55)), (32, (44, 48, 51)), (41, (53, 56, 60)), (43, (55, 59, 62))] * 2
	stabs, drums, bells = [], [], []
	for b, (root, chord) in enumerate(chords):
		t0 = b * bar
		for at in (0.0, 1.5, 3.0):
			stabs.append((t0 + at * beat, (inst_stab(midi(root), beat * 0.9, rng) + inst_stab(midi(root + 7), beat * 0.9, rng) * 0.6) * 0.5))
		for at, v, f in ((0.0, 1.0, 85), (0.5, 0.4, 110), (2.0, 0.9, 80), (2.75, 0.5, 120), (3.5, 0.7, 95)):
			drums.append((t0 + at * beat, inst_drum(rng, f, 38, 0.3, 0.6) * v))
		if b in (3, 7):
			for k, n_ in enumerate((83, 84, 86, 87)):
				bells.append((t0 + (2 + k * 0.25) * beat, inst_bell(midi(n_), 1.6, rng) * 0.4))
	n = g.ns(L)
	t = np.arange(n) / g.SR
	drone = g.sat(np.sin(g.phase(np.full(n, midi(24)), n, loop=True)) * 1.5, 2.0) * (0.8 + 0.2 * np.sin(2 * np.pi * 8 * t / L))
	grit = g.lp(g.noise(n, rng), 200, loop=True) * 0.3
	mix = arrange(L, stabs) * 0.7 + arrange(L, drums) * 0.75 + arrange(L, bells) * 0.35 + drone * 0.18 + grit
	return g.reverb(mix, rng, 1.8, 0.25, loop=True, bright=3800)


FILES = {
	"sentinel.wake": {"sentinel/wake": g_sentinel_wake},
	"sentinel.slam_tell": {"sentinel/slam_tell": g_sentinel_slam_tell},
	"sentinel.slam": {"sentinel/slam": g_sentinel_slam},
	"sentinel.core_exposed": {"sentinel/core_exposed": g_sentinel_core_exposed},
	"sentinel.lance_charge": {"sentinel/lance_charge": g_sentinel_lance_charge},
	"sentinel.lance_fire": {"sentinel/lance_fire": g_sentinel_lance_fire},
	"sentinel.hurt": {"sentinel/hurt": g_sentinel_hurt},
	"sentinel.death": {"sentinel/death": g_sentinel_death},
	"boss.collector.hurt": {"boss/collector_hurt": g_collector_hurt},
	"boss.collector.hurl_lift": {"boss/collector_hurl_lift": g_hurl_lift},
	"boss.collector.hurl_throw": {"boss/collector_hurl_throw": g_hurl_throw},
	"boss.collector.hurl_rip": {"boss/collector_hurl_rip": g_hurl_rip},
	"boss.collector.hurl_return": {"boss/collector_hurl_return": g_hurl_return},
	"boss.collector.palm_tell": {"boss/collector_palm_tell": g_palm_tell},
	"boss.collector.lash_tell": {"boss/collector_lash_tell": g_lash_tell},
	"boss.collector.lash_wave": {"boss/collector_lash_wave": g_lash_wave},
	"boss.collector.surge_gather": {"boss/collector_surge_gather": g_surge_gather},
	"boss.collector.surge_release": {"boss/collector_surge_release": g_surge_release},
	"boss.collector.surge_clash": {"boss/collector_surge_clash": g_surge_clash},
	"boss.collector.spent": {"boss/collector_spent": g_spent},
	"boss.collector.grip": {"boss/collector_grip": g_grip},
	"boss.collector.grip_hold": {"boss/collector_grip_hold": g_grip_hold},
	"boss.collector.grip_break": {"boss/collector_grip_break": g_grip_break},
	"boss.collector.lunge": {"boss/collector_lunge": g_lunge},
	"boss.collector.ward_hum": {"boss/collector_ward_hum": g_ward_hum},
	"boss.collector.unsung": {"boss/collector_unsung": g_unsung},
	"boss.collector.transition": {"boss/collector_transition": g_transition},
	"boss.collector.yield": {"boss/collector_yield": g_yield},
	"boss.collector.leash": {"boss/collector_leash": g_leash},
	"boss.collector.echo_form": {"boss/collector_echo_form": g_echo_form},
	"boss.collector.echo_fade": {"boss/collector_echo_fade": g_echo_fade},
	"boss.collector.binders": {"boss/collector_binders": g_binders},
	"boss.collector.execute": {"boss/collector_execute": g_execute},
	"boss.collector.walk_out": {"boss/collector_walk_out": g_walk_out},
	"pylon.sing": {"pylon/sing": g_pylon_sing},
	"pylon.crack": {"pylon/crack": g_pylon_crack},
	"pylon.silence": {"pylon/silence": g_pylon_silence},
	"pylon.regrow": {"pylon/regrow": g_pylon_regrow},
	"pylon.shatter": {"pylon/shatter": g_pylon_shatter},
	"music.collector.archivist": {"music/collector_archivist": g_music_archivist},
	"music.collector.singing_wall": {"music/collector_singing_wall": g_music_singing_wall},
	"music.collector.unkept": {"music/collector_unkept": g_music_unkept},
	"ambient.reliquary": {"ambient/reliquary": g_ambient_reliquary},
	"hoard.open": {"hoard/open": g_hoard_open},
}
