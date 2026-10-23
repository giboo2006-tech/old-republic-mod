#!/usr/bin/env python3
"""Milestone 3 identifier check (docs/MILESTONE_3.md 15.1 "Id check" and section 16). Run on CI.

Section 16 marks every Milestone 3 id as new or "existing, reused". This script fails when:

  * an id marked new already existed before Milestone 3 (in sounds.json, assets/oldrepublic/particles/,
    assets/oldrepublic/items/ or a lang part of the frozen pre-M3 tree below), or when a new sound id is
    declared by the M1 catalogue in gen_sounds.py or by events_m2.json;
  * an id marked reused is missing from the current tree, or is declared again (a reused sound in
    events_m3.json, a reused particle registered twice);
  * tools/assets/sounds/events_m3.json does not list exactly the new sound ids of section 16, each under its
    owner's recipe module;
  * a new sound id has no sounds.json entry, no subtitle in src/main/lang-parts/sounds.json or no ModSounds constant
    (gen_sounds.py --m3 --java writes all three).

New ids that do not exist yet are fine: workers add them during the milestone. --report prints how many of each
kind exist so far.

The baseline is the tree at commit fa82dc82 (the last commit before Milestone 3 Phase 0), frozen in this file so
the check needs no git history. Only stdlib; run from anywhere: python3 tools/check_m3_ids.py [--report]
"""
import argparse
import ast
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/oldrepublic"
LANG_DIR = ROOT / "src/main/lang-parts"
SOUNDS_JSON = ASSETS / "sounds.json"
EVENTS_M2 = ROOT / "tools/assets/sounds/events_m2.json"
EVENTS_M3 = ROOT / "tools/assets/sounds/events_m3.json"
GEN_SOUNDS = ROOT / "tools/assets/gen_sounds.py"
MOD_SOUNDS = ROOT / "src/main/java/com/oldrepublic/registry/ModSounds.java"
JAVA_DIRS = [ROOT / "src/main/java", ROOT / "src/client/java"]


def ids(text):
    return text.split()


# ----------------------------------------------------------------------------
# Section 16 (frozen with the spec; change only together with docs/MILESTONE_3.md)
# ----------------------------------------------------------------------------
NEW_BLOCKS = ids("""
    veiled_stone counterweight_stone step_stone echo_urn urn_keeper pull_anchor collapsed_rubble puzzle_seal
    holocron_pedestal echo_cairn tomb_vent bounty_terminal site_spawner lumen_moss funerary_urn wreck_salvage
    ruin_stone ruin_stone_carved ruin_stone_cracked ruin_stone_mossy tomb_stone tomb_stone_carved tomb_stone_cracked
    wreck_plating wreck_plating_scorched wreck_conduit republic_panel imperial_panel
    resonance_pylon reliquary_heart hoard_plinth reliquary_seal
""")
NEW_ITEMS = ids("""
    lumen_thread grave_silk durasteel_plate republic_commendation imperial_commendation archive_record fence_book
    jedi_holocron sith_holocron wayfarer_holocron holocron_shard
    republic_trooper_helmet republic_trooper_chestplate republic_trooper_leggings republic_trooper_boots
    sith_trooper_helmet sith_trooper_chestplate sith_trooper_leggings sith_trooper_boots
    jedi_hood jedi_robe jedi_trousers jedi_boots jedi_knight_robe sith_hood sith_robe sith_trousers sith_boots
    sith_lord_robe
    republic_trooper_spawn_egg sith_trooper_spawn_egg ruin_sentinel_spawn_egg
    pommel_archivist emitter_reliquary singing_crystal
""")
REUSED_ITEMS = ids("blaster_rifle blaster_pistol")
NEW_ENTITIES = ids("republic_trooper sith_trooper frag_grenade ruin_sentinel orren_vasch pylon_focus archive_debris")
NEW_PARTICLES = ids("""
    light_mote leaf_fall tomb_dust veil_dust vent_grit miasma conduit_spark falling_ember
    sense_mote sense_stream lift_shimmer holocron_mote projection_band
    kolto_mist vent_steam grenade_smoke flare_ember
    ward_haze ward_current surge_dust lash_seam lumen_spark echo_smoke
""")
REUSED_PARTICLES = ids("crystal_glint force_mote")
NEW_SOUNDS = {
    "world": ids("""
        ambient.jedi_ruin ambient.ruin_chime ambient.sith_tomb ambient.tomb_whisper ambient.wreck ambient.wreck_creak
        ambient.outpost puzzle.veil_reveal puzzle.veil_crumble puzzle.counterweight_sink puzzle.counterweight_warn
        puzzle.counterweight_rise puzzle.step_extend puzzle.step_retract puzzle.urn_echo puzzle.urn_wrong
        puzzle.anchor_strain puzzle.anchor_release puzzle.rubble_crumble puzzle.seal_open puzzle.solved
        puzzle.pedestal_ready vent.hiss vent.puff terminal.use
    """),
    "story": ids("""
        site.discovered story.ones_that_sing story.calling_holocron story.calling_reliquary story.calling_fall
        story.collector_fallen story.rank_three story.cairn_touch story.blade_touch story.tablet_break
        mission.accept mission.update mission.complete
    """),
    "force": ids("""
        force.sense.cast force.sense.reveal force.sense.danger force.lift.start force.lift.loop force.lift.slam
        force.lift.hurl
    """),
    "holocron": ids("""
        holocron.rise holocron.open holocron.gatekeeper_form holocron.gatekeeper_fade holocron.hum holocron.seal
        holocron.break holocron.listen holocron.echo holocron.archive_return holocron.archive_keep holocron.unmake
    """),
    "enemy": ids("""
        trooper.republic.charge trooper.republic.fire trooper.imperial.charge trooper.imperial.fire trooper.vent
        trooper.kolto trooper.tend trooper.stand trooper.rally trooper.flare trooper.radio trooper.hurt trooper.death
        grenade.arm grenade.fuse grenade.burst
    """),
    "equipment": ids("armour.equip_trooper armour.equip_robe armour.set_bonus"),
    "boss": ids("""
        sentinel.wake sentinel.slam_tell sentinel.slam sentinel.core_exposed sentinel.lance_charge sentinel.lance_fire
        sentinel.hurt sentinel.death boss.collector.hurt boss.collector.hurl_lift boss.collector.hurl_throw
        boss.collector.hurl_rip boss.collector.hurl_return boss.collector.palm_tell boss.collector.lash_tell
        boss.collector.lash_wave boss.collector.surge_gather boss.collector.surge_release boss.collector.surge_clash
        boss.collector.spent boss.collector.grip boss.collector.grip_hold boss.collector.grip_break
        boss.collector.lunge boss.collector.ward_hum boss.collector.unsung boss.collector.transition
        boss.collector.yield boss.collector.leash boss.collector.echo_form boss.collector.echo_fade
        boss.collector.binders boss.collector.execute boss.collector.walk_out pylon.sing pylon.crack pylon.silence
        pylon.regrow pylon.shatter music.collector.archivist music.collector.singing_wall music.collector.unkept
        ambient.reliquary hoard.open
    """),
    "saber": ids("saber.hum.singing"),
}
REUSED_SOUNDS = ids("story.lesson_complete")

# ----------------------------------------------------------------------------
# Frozen pre-M3 baseline (commit fa82dc82): sound ids, particle files, item definitions, and the lang keys
# with the item / block / entity / subtitles prefixes.
# ----------------------------------------------------------------------------
BASELINE_SOUNDS = set("""
    acolyte.death acolyte.hurt acolyte.shove acolyte.shove_windup acolyte.yield ambient.cave awakening.burst
    awakening.drone awakening.whispers blaster.bolt_flyby blaster.charge blaster.fire blaster.impact
    blaster.pistol_fire captain.death captain.heavy_windup captain.hurt captain.yield crystal.calling crystal.chime
    crystal.discharge crystal.form crystal.resonance crystal.unstable_charge dialogue.open dialogue.select force.fail
    force.heal.end force.heal.interrupt force.heal.loop force.heal.start force.heal.ward force.jump.gather
    force.jump.glide force.jump.land force.jump.launch force.jump.slam force.lightning.charge force.lightning.impact
    force.lightning.loop force.lightning.release force.pull.cast force.pull.catch force.pull.disarm force.pull.drag
    force.push.charge force.push.impact force.push.release force.push.slam identity.alignment_dark
    identity.alignment_light identity.rank_up journal.entry journal.open meditation.break meditation.complete
    meditation.loop poacher.death poacher.hurt saber.clash saber.deflect saber.finisher saber.form_change
    saber.guard_break saber.heavy_strike saber.heavy_windup saber.hit saber.hum saber.ignite saber.lightning_block
    saber.lunge saber.npc_windup saber.parry saber.retract saber.swing saber.throw saber.throw_catch saber.throw_loop
    settler.death settler.freed settler.hurt skill.learn skill.respec skill.slot story.duel_begin story.duel_end
    story.lesson_complete story.two_voices trial.begin trial.complete trial.echo_form trial.echo_lunge vibroblade.hit
    vibroblade.swing workbench.assemble workbench.slot
""".split())
BASELINE_PARTICLES = set("""
    air_ripple ash_mote blade_glint bolt_impact bolt_scorch contact_flash crystal_glint crystal_mote crystal_spark
    fail_mote force_mote gather_dust jump_trail lightning_segment lightning_spark lunge_streak muzzle_glow pull_streak
    push_impact push_streak resonance_shimmer rope_fibre saber_spark shadow_wisp voice_dust voice_warm ward_glint
""".split())
BASELINE_ITEMS = set("""
    blaster_pistol blaster_rifle crystal_poacher_spawn_egg crystal_veined_stone emitter_duelist emitter_guardian
    grip_ridged grip_wrapped kyber_cluster kyber_crystal lightsaber lightsaber_workbench meditation_stone
    poacher_captain_spawn_egg poachers_ledger pommel_balanced pommel_weighted resonant_seal sith_acolyte_spawn_egg
    unstable_crystal_cluster vibroblade wanderers_journal
""".split())
BASELINE_LANG = set("""
    block.oldrepublic.crystal_veined_stone block.oldrepublic.kyber_cluster block.oldrepublic.lightsaber_workbench
    block.oldrepublic.meditation_stone block.oldrepublic.resonant_seal block.oldrepublic.unstable_crystal_cluster
    entity.oldrepublic.blaster_bolt entity.oldrepublic.crystal_poacher entity.oldrepublic.frontier_settler
    entity.oldrepublic.jedi_mentor entity.oldrepublic.poacher_captain entity.oldrepublic.shadow_echo
    entity.oldrepublic.sith_acolyte entity.oldrepublic.sith_mentor entity.oldrepublic.thrown_saber
    item.oldrepublic.blaster_pistol item.oldrepublic.blaster_rifle item.oldrepublic.crystal_poacher_spawn_egg
    item.oldrepublic.emitter_duelist item.oldrepublic.emitter_duelist.desc item.oldrepublic.emitter_guardian
    item.oldrepublic.emitter_guardian.desc item.oldrepublic.grip_ridged item.oldrepublic.grip_ridged.desc
    item.oldrepublic.grip_wrapped item.oldrepublic.grip_wrapped.desc item.oldrepublic.kyber_crystal
    item.oldrepublic.kyber_crystal.bound item.oldrepublic.kyber_crystal.hue item.oldrepublic.kyber_crystal.owner
    item.oldrepublic.kyber_crystal.unattuned item.oldrepublic.lightsaber item.oldrepublic.lightsaber.color
    item.oldrepublic.lightsaber.ignited item.oldrepublic.lightsaber.owner item.oldrepublic.lightsaber.parts
    item.oldrepublic.lightsaber.retracted item.oldrepublic.lightsaber.thrown item.oldrepublic.poacher_captain_spawn_egg
    item.oldrepublic.poachers_ledger item.oldrepublic.poachers_ledger.page1 item.oldrepublic.poachers_ledger.page2
    item.oldrepublic.poachers_ledger.page3 item.oldrepublic.poachers_ledger.page4 item.oldrepublic.pommel_balanced
    item.oldrepublic.pommel_balanced.desc item.oldrepublic.pommel_weighted item.oldrepublic.pommel_weighted.desc
    item.oldrepublic.sith_acolyte_spawn_egg item.oldrepublic.vibroblade item.oldrepublic.wanderers_journal
    subtitles.oldrepublic.acolyte.death subtitles.oldrepublic.acolyte.hurt subtitles.oldrepublic.acolyte.shove
    subtitles.oldrepublic.acolyte.shove_windup subtitles.oldrepublic.acolyte.yield subtitles.oldrepublic.ambient.cave
    subtitles.oldrepublic.awakening.burst subtitles.oldrepublic.awakening.drone
    subtitles.oldrepublic.awakening.whispers subtitles.oldrepublic.blaster.bolt_flyby
    subtitles.oldrepublic.blaster.charge subtitles.oldrepublic.blaster.fire subtitles.oldrepublic.blaster.impact
    subtitles.oldrepublic.blaster.pistol_fire subtitles.oldrepublic.captain.death
    subtitles.oldrepublic.captain.heavy_windup subtitles.oldrepublic.captain.hurt subtitles.oldrepublic.captain.yield
    subtitles.oldrepublic.crystal.calling subtitles.oldrepublic.crystal.chime subtitles.oldrepublic.crystal.discharge
    subtitles.oldrepublic.crystal.form subtitles.oldrepublic.crystal.resonance
    subtitles.oldrepublic.crystal.unstable_charge subtitles.oldrepublic.dialogue.open
    subtitles.oldrepublic.dialogue.select subtitles.oldrepublic.force.fail subtitles.oldrepublic.force.heal.end
    subtitles.oldrepublic.force.heal.interrupt subtitles.oldrepublic.force.heal.loop
    subtitles.oldrepublic.force.heal.start subtitles.oldrepublic.force.heal.ward
    subtitles.oldrepublic.force.jump.gather subtitles.oldrepublic.force.jump.glide
    subtitles.oldrepublic.force.jump.land subtitles.oldrepublic.force.jump.launch subtitles.oldrepublic.force.jump.slam
    subtitles.oldrepublic.force.lightning.charge subtitles.oldrepublic.force.lightning.impact
    subtitles.oldrepublic.force.lightning.loop subtitles.oldrepublic.force.lightning.release
    subtitles.oldrepublic.force.pull.cast subtitles.oldrepublic.force.pull.catch
    subtitles.oldrepublic.force.pull.disarm subtitles.oldrepublic.force.pull.drag
    subtitles.oldrepublic.force.push.charge subtitles.oldrepublic.force.push.impact
    subtitles.oldrepublic.force.push.release subtitles.oldrepublic.force.push.slam
    subtitles.oldrepublic.identity.alignment_dark subtitles.oldrepublic.identity.alignment_light
    subtitles.oldrepublic.identity.rank_up subtitles.oldrepublic.journal.entry subtitles.oldrepublic.journal.open
    subtitles.oldrepublic.meditation.break subtitles.oldrepublic.meditation.complete
    subtitles.oldrepublic.meditation.loop subtitles.oldrepublic.poacher.death subtitles.oldrepublic.poacher.hurt
    subtitles.oldrepublic.saber.clash subtitles.oldrepublic.saber.deflect subtitles.oldrepublic.saber.finisher
    subtitles.oldrepublic.saber.form_change subtitles.oldrepublic.saber.guard_break
    subtitles.oldrepublic.saber.heavy_strike subtitles.oldrepublic.saber.heavy_windup subtitles.oldrepublic.saber.hit
    subtitles.oldrepublic.saber.hum subtitles.oldrepublic.saber.ignite subtitles.oldrepublic.saber.lightning_block
    subtitles.oldrepublic.saber.lunge subtitles.oldrepublic.saber.npc_windup subtitles.oldrepublic.saber.parry
    subtitles.oldrepublic.saber.retract subtitles.oldrepublic.saber.swing subtitles.oldrepublic.saber.throw
    subtitles.oldrepublic.saber.throw_catch subtitles.oldrepublic.saber.throw_loop subtitles.oldrepublic.settler.death
    subtitles.oldrepublic.settler.freed subtitles.oldrepublic.settler.hurt subtitles.oldrepublic.skill.learn
    subtitles.oldrepublic.skill.respec subtitles.oldrepublic.skill.slot subtitles.oldrepublic.story.duel_begin
    subtitles.oldrepublic.story.duel_end subtitles.oldrepublic.story.lesson_complete
    subtitles.oldrepublic.story.two_voices subtitles.oldrepublic.trial.begin subtitles.oldrepublic.trial.complete
    subtitles.oldrepublic.trial.echo_form subtitles.oldrepublic.trial.echo_lunge subtitles.oldrepublic.vibroblade.hit
    subtitles.oldrepublic.vibroblade.swing subtitles.oldrepublic.workbench.assemble
    subtitles.oldrepublic.workbench.slot
""".split())


# ----------------------------------------------------------------------------
# Current tree
# ----------------------------------------------------------------------------

def load_json(path):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError:
        return None


def lang_parts():
    """{key: [part file names]} over every lang part."""
    owners = {}
    for f in sorted(LANG_DIR.glob("*.json")):
        for k in load_json(f):
            owners.setdefault(k, []).append(f.name)
    return owners


def m1_sound_ids():
    """The ids of the M1 catalogue: the string first arguments of E(...) calls in gen_sounds.py."""
    tree = ast.parse(GEN_SOUNDS.read_text(encoding="utf-8"))
    out = set()
    for node in ast.walk(tree):
        if isinstance(node, ast.Call) and isinstance(node.func, ast.Name) and node.func.id == "E" and node.args:
            first = node.args[0]
            if isinstance(first, ast.Constant) and isinstance(first.value, str):
                out.add(first.value)
    return out


def java_sources():
    for d in JAVA_DIRS:
        yield from d.rglob("*.java")


def register_counts(names):
    """How often register("<name>") appears across the main and client Java sources."""
    counts = dict.fromkeys(names, 0)
    pattern = re.compile(r'register\(\s*"([a-z0-9_./]+)"')
    for f in java_sources():
        for m in pattern.finditer(f.read_text(encoding="utf-8")):
            if m.group(1) in counts:
                counts[m.group(1)] += 1
    return counts


def lang_keys(kind, names):
    return {f"{kind}.oldrepublic.{n}": n for n in names}


def check():
    problems = []
    new_sounds = [s for group in NEW_SOUNDS.values() for s in group]
    lang = lang_parts()

    # --- section 16 itself must be consistent
    for label, values in (("new sound", new_sounds), ("new item", NEW_ITEMS + NEW_BLOCKS),
                          ("new particle", NEW_PARTICLES)):
        dupes = sorted({v for v in values if values.count(v) > 1})
        if dupes:
            problems.append(f"{label} ids listed twice in this script: {dupes}")

    # --- new ids must not have existed before Milestone 3
    for s in new_sounds:
        if s in BASELINE_SOUNDS:
            problems.append(f"new sound {s} already existed in sounds.json before M3")
    for p in NEW_PARTICLES:
        if p in BASELINE_PARTICLES:
            problems.append(f"new particle {p} already existed in assets/oldrepublic/particles/")
    for i in NEW_ITEMS + NEW_BLOCKS:
        if i in BASELINE_ITEMS:
            problems.append(f"new id {i} already existed in assets/oldrepublic/items/")
    new_keys = {**lang_keys("item", NEW_ITEMS), **lang_keys("block", NEW_BLOCKS), **lang_keys("entity", NEW_ENTITIES),
                **lang_keys("subtitles", new_sounds)}
    for k in sorted(new_keys):
        if k in BASELINE_LANG:
            problems.append(f"lang key {k} of a new id already existed in a lang part before M3")
    m1, m2 = m1_sound_ids(), {e["id"] for e in load_json(EVENTS_M2)["events"]}
    for s in new_sounds:
        if s in m1 or s in m2:
            problems.append(f"new sound {s} is declared by the {'M1 catalogue' if s in m1 else 'events_m2.json'}")

    # --- reused ids must exist, and be declared only once
    sounds = load_json(SOUNDS_JSON) or {}
    m3_events = load_json(EVENTS_M3)
    m3_ids = [e["id"] for e in m3_events["events"]] if m3_events else []
    for s in REUSED_SOUNDS:
        if s not in BASELINE_SOUNDS:
            problems.append(f"reused sound {s} is not in the pre-M3 baseline")
        if s not in sounds:
            problems.append(f"reused sound {s} is missing from sounds.json")
        if s not in m1 and s not in m2:
            problems.append(f"reused sound {s} is no longer declared by the M1 catalogue or events_m2.json")
        if s in m3_ids:
            problems.append(f"reused sound {s} must not be listed in events_m3.json")
        if f"subtitles.oldrepublic.{s}" not in lang:
            problems.append(f"reused sound {s} lost its subtitle")
    reg = register_counts(REUSED_PARTICLES + NEW_PARTICLES)
    for p in REUSED_PARTICLES:
        if p not in BASELINE_PARTICLES:
            problems.append(f"reused particle {p} is not in the pre-M3 baseline")
        if not (ASSETS / "particles" / f"{p}.json").exists():
            problems.append(f"reused particle {p} is missing from assets/oldrepublic/particles/")
        if reg[p] != 1:
            problems.append(f'reused particle {p} must be registered exactly once '
                            f'(register("{p}") appears {reg[p]} times)')
    for p in NEW_PARTICLES:
        if reg[p] > 1:
            problems.append(f'new particle {p} is registered {reg[p]} times')
    for i in REUSED_ITEMS:
        if i not in BASELINE_ITEMS:
            problems.append(f"reused item {i} is not in the pre-M3 baseline")
        if not (ASSETS / "items" / f"{i}.json").exists():
            problems.append(f"reused item {i} is missing from assets/oldrepublic/items/")
        key = f"item.oldrepublic.{i}"
        if len(lang.get(key, [])) != 1:
            problems.append(f"reused item {i}: {key} must be defined in exactly one lang part "
                            f"(found {lang.get(key, [])})")

    # --- events_m3.json lists exactly the new sound ids, under their owners' modules
    if m3_events is None:
        problems.append(f"{EVENTS_M3.relative_to(ROOT)} is missing")
    else:
        if len(m3_ids) != len(set(m3_ids)):
            problems.append("events_m3.json lists an id twice")
        expected = {s: feature for feature, group in NEW_SOUNDS.items() for s in group}
        for s in sorted(set(expected) - set(m3_ids)):
            problems.append(f"new sound {s} is missing from events_m3.json")
        for s in sorted(set(m3_ids) - set(expected)):
            problems.append(f"events_m3.json lists {s}, which section 16 does not mark new")
        for e in m3_events["events"]:
            if e["id"] in expected and e.get("feature") != expected[e["id"]]:
                problems.append(f"events_m3.json: {e['id']} belongs to feature {expected[e['id']]}, "
                                f"not {e.get('feature')}")

    # --- every new sound is generated: sounds.json entry, subtitle, ModSounds constant
    java = MOD_SOUNDS.read_text(encoding="utf-8") if MOD_SOUNDS.exists() else ""
    subtitles = load_json(LANG_DIR / "sounds.json") or {}
    for s in new_sounds:
        if s not in sounds:
            problems.append(f"new sound {s} has no sounds.json entry (run gen_sounds.py --m3 --java)")
        if f"subtitles.oldrepublic.{s}" not in subtitles:
            problems.append(f"new sound {s} has no subtitle in lang-parts/sounds.json")
        if f'register("{s}")' not in java:
            problems.append(f"new sound {s} has no ModSounds constant")
    return problems


def report():
    lang = lang_parts()
    new_sounds = [s for group in NEW_SOUNDS.values() for s in group]
    sounds = load_json(SOUNDS_JSON) or {}
    rows = [
        ("sounds (sounds.json)", new_sounds, lambda s: s in sounds),
        ("particles (particles/<id>.json)", NEW_PARTICLES, lambda p: (ASSETS / "particles" / f"{p}.json").exists()),
        ("items (items/<id>.json)", NEW_ITEMS, lambda i: (ASSETS / "items" / f"{i}.json").exists()),
        ("blocks (block.oldrepublic.<id>)", NEW_BLOCKS, lambda b: f"block.oldrepublic.{b}" in lang),
        ("entities (entity.oldrepublic.<id>)", NEW_ENTITIES, lambda e: f"entity.oldrepublic.{e}" in lang),
    ]
    for label, values, present in rows:
        missing = [v for v in values if not present(v)]
        print(f"  {label:36s} {len(values) - len(missing):3d}/{len(values)}")
        if missing:
            print("      not yet: " + " ".join(missing))


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--report", action="store_true", help="also print which new ids exist so far")
    args = ap.parse_args()
    problems = check()
    if args.report:
        report()
    for p in problems:
        print(f"check_m3_ids: {p}")
    if problems:
        sys.exit(f"check_m3_ids: {len(problems)} problem(s)")
    print("check_m3_ids: ok")


if __name__ == "__main__":
    main()
