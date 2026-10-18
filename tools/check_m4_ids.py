#!/usr/bin/env python3
"""Milestone 4 identifier check (docs/MILESTONE_4.md section 16 and 13.2). Run on CI.

Section 16 marks every Milestone 4 id as new or "existing, reused". This script fails when:

  * an id marked new already existed before Milestone 4 (in sounds.json, assets/oldrepublic/particles/,
    assets/oldrepublic/items/ or a lang part of the frozen pre-M4 tree below), or when a new sound id is
    declared by the M1 catalogue in gen_sounds.py, events_m2.json or events_m3.json;
  * an id marked reused is missing from the current tree, or is declared again (a reused sound in events_m3.json or
    events_m4.json, a reused item registered twice or named by two lang parts);
  * a spec spelling that Phase 0 renamed or folded into an existing id shows up (blaster.pistol.fire,
    blaster.rifle.fire, staff_grip);
  * tools/assets/sounds/events_m4.json does not list exactly the new sound ids, each under its owner's recipe module;
  * a new sound id has no sounds.json entry, no subtitle in src/main/lang-parts/sounds.json or no ModSounds constant
    (gen_sounds.py --m4 --java writes all three);
  * a new particle (13.2) is registered more than once.

New ids that do not exist yet are fine: workers add them during the milestone (particles too: their owners register
them). --report prints how many of each kind exist so far.

Phase 0 decisions that differ from the spec text (these are the truth here):
  * blaster_pistol, blaster_rifle and vibroblade are existing, reused items; frag_grenade is a NEW item (before M4 it
    was only an entity type, which stays as it is);
  * the saberstaff grip item is grip_staff (the HiltParts naming), not staff_grip;
  * the player pistol and rifle fire with the existing blaster.pistol_fire and blaster.fire, so the spec's
    blaster.pistol.fire and blaster.rifle.fire are not ids;
  * the 13.2 particles are registered by their owners, so they are new or absent, never required.

The baseline is the tree at commit 447ce97 (the last commit before Milestone 4 Phase 0), frozen in this file so
the check needs no git history. Only stdlib; run from anywhere: python3 tools/check_m4_ids.py [--report]
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
EVENTS_M4 = ROOT / "tools/assets/sounds/events_m4.json"
GEN_SOUNDS = ROOT / "tools/assets/gen_sounds.py"
MOD_SOUNDS = ROOT / "src/main/java/com/oldrepublic/registry/ModSounds.java"
JAVA_DIRS = [ROOT / "src/main/java", ROOT / "src/client/java"]


def ids(text):
    return text.split()


# ----------------------------------------------------------------------------
# Section 16 and 13.2 (frozen with the spec and the Phase 0 decisions above; change only together with
# docs/MILESTONE_4.md)
# ----------------------------------------------------------------------------
NEW_BLOCKS = ids("""
    silverleaf aurelite_ore deepslate_aurelite_ore ashroot duskglass_cluster budding_duskglass waystone
    waystone_corrupted heartstone sealed_plating hub_spawner camp_gate
    landing_beacon engineering_bench marker_beacon
""")
NEW_ITEMS = ids("""
    aurelite_shard duskglass_shard
    hollowhorn_hide hollowhorn_horn ash_hound_fang droid_actuator raw_hollowhorn cooked_hollowhorn
    power_core hyperdrive_motivator nav_computer hyperfuel_cell passenger_seat cargo_bay_extension reinforced_plating
    fuel_tank_extension
    heavy_repeater vibrosword electrostaff kaast_longshot
    watch_battle_helmet watch_battle_chest watch_battle_legs watch_battle_boots
    sith_war_helmet sith_war_chest sith_war_legs sith_war_boots
    mandalorian_helmet mandalorian_chest mandalorian_legs mandalorian_boots
    spacer_cap spacer_jacket spacer_trousers spacer_boots kaast_helmet kaast_chest
    mod_plating mod_weave mod_attunement mod_conduit mod_stabiliser jet_pack mod_focused_barrel mod_heavy_barrel
    mod_cooling_cell mod_overcharged_cell mod_keen_edge mod_shock_edge
    alloy_plate masterwork_fragment schematic medpac kolto_injector combat_stim focus_stim fusion_cutter scanner
    stun_grenade repair_kit frag_grenade
    lightsaber_staff training_saber training_saber_red grip_staff lens_honed lens_steady lens_resonant
    credit_chit credit_bundle kaast_pattern
    wayfarer_relic tomb_relic sealed_contract
    mandalorian_hunter_spawn_egg security_droid_spawn_egg hollowhorn_spawn_egg ash_hound_spawn_egg
""")
REUSED_ITEMS = ids("blaster_pistol blaster_rifle vibroblade")
RENAMED_ITEMS = {"staff_grip": "grip_staff"}
NEW_ENTITIES = ids("""
    courier_shuttle mandalorian_hunter security_droid hollowhorn ash_hound seeker_droid kaast_droid torvin_kaast
    hunter_rocket hunter_net camp_mine hub_host apprentice acolyte_hub proving_guard vendor
""")
REUSED_ENTITIES = ids("frag_grenade")
NEW_PARTICLES = ids("""
    steam_vent ash_flake flame_jet engine_exhaust spark_shower held_dust aurelite_glint duskglass_shard
""")
NEW_SOUNDS = {
    "world_m4": ids("""
        waystone.corrupt waystone.cleanse waystone.hum heartstone.wave heartstone.cleansed hollow.chime hollow.answer
        rift.whisper rift.bleed ambient.selvaren.highwood ambient.selvaren.meadow ambient.selvaren.tarn
        ambient.varkhast.redrock ambient.varkhast.ashfall ambient.varkhast.tomb_valley ambient.greenwatch
        ambient.proving music.selvaren music.varkhast
    """),
    "story_m4": ids("""
        story.chapter_four planet.arrive seeker.scan seeker.chirp seeker.fly vendor.buy vendor.refuse credits.pickup
        hub.apprentice_spar
    """),
    "ship": ids("""
        ship.idle ship.spool ship.climb ship.descent ship.land ship.ramp ship.jump_out ship.jump_in ship.hyperspace
        ship.fit_part ship.weld ship.power_up ship.damaged beacon.set beacon.refuse beacon.recall
    """),
    "gear": ids("""
        blaster.repeater.fire blaster.rifle.charged blaster.overheat blaster.vent blaster.vent_perfect
        blaster.repeater.spin_up blaster.repeater.spin_down vibro.flurry vibro.cleave electrostaff.charge
        electrostaff.shock electrostaff.clash jetpack.burst jetpack.empty
    """),
    "crafting": ids("""
        bench.mod_fit bench.mod_remove bench.weld bench.done bench.masterwork bench.salvage medpac.use kolto.inject
        stim.inject stim.crash scanner.pulse scanner.ping grenade.throw grenade.stun
    """),
    "saber": ids("saber.staff_ignite saber.staff_whirl workbench.lens_fit workbench.tune"),
    "force_m4": ids("""
        force.speed.start force.speed.loop force.speed.end force.speed.burst force.barrier.start force.barrier.loop
        force.barrier.impact force.barrier.pulse force.barrier.break force.choke.start force.choke.loop
        force.choke.release force.choke.crush
    """),
    "enemy_m4": ids("""
        mandalorian.jet_tell mandalorian.flamer_tell mandalorian.rocket_lock mandalorian.net_tell mandalorian.jet
        mandalorian.flamer mandalorian.rocket mandalorian.explode mandalorian.net mandalorian.hurt mandalorian.death
        mandalorian.bark droid.spin_up droid.fire droid.shield droid.death droid.hurt droid.step droid.alert
        hollowhorn.snort hollowhorn.charge hollowhorn.impact hollowhorn.idle hollowhorn.hurt hollowhorn.death
        ash_hound.growl ash_hound.howl ash_hound.lunge ash_hound.idle ash_hound.hurt ash_hound.death
        boss.kaast.hurt boss.kaast.hop boss.kaast.rifle boss.kaast.phase boss.kaast.mine_arm boss.kaast.mine_beep
        boss.kaast.mine_burst boss.kaast.net_throw boss.kaast.axe_draw boss.kaast.combo boss.kaast.charge
        boss.kaast.slam_tell boss.kaast.slam boss.kaast.helmet_crack boss.kaast.yield boss.kaast.walk_away
        music.kaast.hunt music.kaast.trap music.kaast.last_stand
    """),
}
REUSED_SOUNDS = ids("blaster.pistol_fire blaster.fire")
RENAMED_SOUNDS = {"blaster.pistol.fire": "blaster.pistol_fire", "blaster.rifle.fire": "blaster.fire"}

# ----------------------------------------------------------------------------
# Frozen pre-M4 baseline (commit 447ce97): sound ids, particle files, item definitions, and the lang keys
# with the item / block / entity / subtitles prefixes.
# ----------------------------------------------------------------------------
BASELINE_SOUNDS = set("""
    acolyte.death acolyte.hurt acolyte.shove acolyte.shove_windup acolyte.yield ambient.cave ambient.jedi_ruin
    ambient.outpost ambient.reliquary ambient.ruin_chime ambient.sith_tomb ambient.tomb_whisper ambient.wreck
    ambient.wreck_creak armour.equip_robe armour.equip_trooper armour.set_bonus awakening.burst awakening.drone
    awakening.whispers blaster.bolt_flyby blaster.charge blaster.fire blaster.impact blaster.pistol_fire
    boss.collector.binders boss.collector.echo_fade boss.collector.echo_form boss.collector.execute
    boss.collector.grip boss.collector.grip_break boss.collector.grip_hold boss.collector.hurl_lift
    boss.collector.hurl_return boss.collector.hurl_rip boss.collector.hurl_throw boss.collector.hurt
    boss.collector.lash_tell boss.collector.lash_wave boss.collector.leash boss.collector.lunge
    boss.collector.palm_tell boss.collector.spent boss.collector.surge_clash boss.collector.surge_gather
    boss.collector.surge_release boss.collector.transition boss.collector.unsung boss.collector.walk_out
    boss.collector.ward_hum boss.collector.yield captain.death captain.heavy_windup captain.hurt captain.yield
    crystal.calling crystal.chime crystal.discharge crystal.form crystal.resonance crystal.unstable_charge
    dialogue.open dialogue.select force.fail force.heal.end force.heal.interrupt force.heal.loop force.heal.start
    force.heal.ward force.jump.gather force.jump.glide force.jump.land force.jump.launch force.jump.slam
    force.lift.hurl force.lift.loop force.lift.slam force.lift.start force.lightning.charge force.lightning.impact
    force.lightning.loop force.lightning.release force.pull.cast force.pull.catch force.pull.disarm force.pull.drag
    force.push.charge force.push.impact force.push.release force.push.slam force.sense.cast force.sense.danger
    force.sense.reveal grenade.arm grenade.burst grenade.fuse hoard.open holocron.archive_keep
    holocron.archive_return holocron.break holocron.echo holocron.gatekeeper_fade holocron.gatekeeper_form
    holocron.hum holocron.listen holocron.open holocron.rise holocron.seal holocron.unmake identity.alignment_dark
    identity.alignment_light identity.rank_up journal.entry journal.open meditation.break meditation.complete
    meditation.loop mission.accept mission.complete mission.update music.collector.archivist
    music.collector.singing_wall music.collector.unkept poacher.death poacher.hurt puzzle.anchor_release
    puzzle.anchor_strain puzzle.counterweight_rise puzzle.counterweight_sink puzzle.counterweight_warn
    puzzle.pedestal_ready puzzle.rubble_crumble puzzle.seal_open puzzle.solved puzzle.step_extend
    puzzle.step_retract puzzle.urn_echo puzzle.urn_wrong puzzle.veil_crumble puzzle.veil_reveal pylon.crack
    pylon.regrow pylon.shatter pylon.silence pylon.sing saber.clash saber.deflect saber.finisher saber.form_change
    saber.guard_break saber.heavy_strike saber.heavy_windup saber.hit saber.hum saber.hum.singing saber.ignite
    saber.lightning_block saber.lunge saber.npc_windup saber.parry saber.retract saber.swing saber.throw
    saber.throw_catch saber.throw_loop sentinel.core_exposed sentinel.death sentinel.hurt sentinel.lance_charge
    sentinel.lance_fire sentinel.slam sentinel.slam_tell sentinel.wake settler.death settler.freed settler.hurt
    site.discovered skill.learn skill.respec skill.slot story.blade_touch story.cairn_touch story.calling_fall
    story.calling_holocron story.calling_reliquary story.collector_fallen story.duel_begin story.duel_end
    story.lesson_complete story.ones_that_sing story.rank_three story.tablet_break story.two_voices terminal.use
    trial.begin trial.complete trial.echo_form trial.echo_lunge trooper.death trooper.flare trooper.hurt
    trooper.imperial.charge trooper.imperial.fire trooper.kolto trooper.radio trooper.rally trooper.republic.charge
    trooper.republic.fire trooper.stand trooper.tend trooper.vent vent.hiss vent.puff vibroblade.hit
    vibroblade.swing workbench.assemble workbench.slot
""".split())

BASELINE_PARTICLES = set("""
    air_ripple ash_mote blade_glint bolt_impact bolt_scorch conduit_spark contact_flash crystal_glint crystal_mote
    crystal_spark echo_smoke fail_mote falling_ember flare_ember force_mote gather_dust grenade_smoke holocron_mote
    jump_trail kolto_mist lash_seam leaf_fall lift_shimmer light_mote lightning_segment lightning_spark lumen_spark
    lunge_streak miasma muzzle_glow projection_band pull_streak push_impact push_streak resonance_shimmer rope_fibre
    saber_spark sense_mote sense_stream shadow_wisp surge_dust tomb_dust veil_dust vent_grit vent_steam voice_dust
    voice_warm ward_current ward_glint ward_haze
""".split())

BASELINE_ITEMS = set("""
    archive_record blaster_pistol blaster_rifle bounty_terminal collapsed_rubble counterweight_stone
    crystal_poacher_spawn_egg crystal_veined_stone durasteel_plate echo_urn emitter_duelist emitter_guardian
    emitter_reliquary fence_book funerary_urn grave_silk grip_ridged grip_wrapped hoard_plinth holocron_pedestal
    holocron_shard imperial_commendation imperial_panel jedi_boots jedi_holocron jedi_hood jedi_knight_robe
    jedi_robe jedi_trousers kyber_cluster kyber_crystal lightsaber lightsaber_workbench lumen_moss lumen_thread
    meditation_stone poacher_captain_spawn_egg poachers_ledger pommel_archivist pommel_balanced pommel_weighted
    pull_anchor puzzle_seal reliquary_heart reliquary_seal republic_commendation republic_panel
    republic_trooper_boots republic_trooper_chestplate republic_trooper_helmet republic_trooper_leggings
    republic_trooper_spawn_egg resonance_pylon resonant_seal ruin_sentinel_spawn_egg ruin_stone ruin_stone_carved
    ruin_stone_cracked ruin_stone_mossy singing_crystal sith_acolyte_spawn_egg sith_boots sith_holocron sith_hood
    sith_lord_robe sith_robe sith_trooper_boots sith_trooper_chestplate sith_trooper_helmet sith_trooper_leggings
    sith_trooper_spawn_egg sith_trousers step_stone tomb_stone tomb_stone_carved tomb_stone_cracked tomb_vent
    unstable_crystal_cluster urn_keeper veiled_stone vibroblade wanderers_journal wayfarer_holocron wreck_conduit
    wreck_plating wreck_plating_scorched wreck_salvage
""".split())

BASELINE_LANG = set("""
    block.oldrepublic.banner.republic_chevron.black block.oldrepublic.banner.republic_chevron.blue
    block.oldrepublic.banner.republic_chevron.brown block.oldrepublic.banner.republic_chevron.cyan
    block.oldrepublic.banner.republic_chevron.gray block.oldrepublic.banner.republic_chevron.green
    block.oldrepublic.banner.republic_chevron.light_blue block.oldrepublic.banner.republic_chevron.light_gray
    block.oldrepublic.banner.republic_chevron.lime block.oldrepublic.banner.republic_chevron.magenta
    block.oldrepublic.banner.republic_chevron.orange block.oldrepublic.banner.republic_chevron.pink
    block.oldrepublic.banner.republic_chevron.purple block.oldrepublic.banner.republic_chevron.red
    block.oldrepublic.banner.republic_chevron.white block.oldrepublic.banner.republic_chevron.yellow
    block.oldrepublic.bounty_terminal block.oldrepublic.collapsed_rubble block.oldrepublic.counterweight_stone
    block.oldrepublic.crystal_veined_stone block.oldrepublic.echo_cairn block.oldrepublic.echo_urn
    block.oldrepublic.funerary_urn block.oldrepublic.hoard_plinth block.oldrepublic.holocron_pedestal
    block.oldrepublic.imperial_panel block.oldrepublic.kyber_cluster block.oldrepublic.lightsaber_workbench
    block.oldrepublic.lumen_moss block.oldrepublic.meditation_stone block.oldrepublic.pull_anchor
    block.oldrepublic.puzzle_seal block.oldrepublic.reliquary_heart block.oldrepublic.reliquary_seal
    block.oldrepublic.republic_panel block.oldrepublic.resonance_pylon block.oldrepublic.resonant_seal
    block.oldrepublic.ruin_stone block.oldrepublic.ruin_stone_carved block.oldrepublic.ruin_stone_cracked
    block.oldrepublic.ruin_stone_mossy block.oldrepublic.site_spawner block.oldrepublic.step_stone
    block.oldrepublic.tomb_stone block.oldrepublic.tomb_stone_carved block.oldrepublic.tomb_stone_cracked
    block.oldrepublic.tomb_vent block.oldrepublic.unstable_crystal_cluster block.oldrepublic.urn_keeper
    block.oldrepublic.veiled_stone block.oldrepublic.wreck_conduit block.oldrepublic.wreck_plating
    block.oldrepublic.wreck_plating_scorched block.oldrepublic.wreck_salvage entity.oldrepublic.archive_debris
    entity.oldrepublic.blaster_bolt entity.oldrepublic.crystal_poacher entity.oldrepublic.frag_grenade
    entity.oldrepublic.frontier_settler entity.oldrepublic.jedi_mentor entity.oldrepublic.orren_vasch
    entity.oldrepublic.poacher_captain entity.oldrepublic.pylon_focus entity.oldrepublic.republic_trooper
    entity.oldrepublic.ruin_sentinel entity.oldrepublic.shadow_echo entity.oldrepublic.sith_acolyte
    entity.oldrepublic.sith_mentor entity.oldrepublic.sith_trooper entity.oldrepublic.thrown_saber
    item.oldrepublic.archive_record item.oldrepublic.blaster_pistol item.oldrepublic.blaster_rifle
    item.oldrepublic.crystal_poacher_spawn_egg item.oldrepublic.durasteel_plate item.oldrepublic.emitter_duelist
    item.oldrepublic.emitter_duelist.desc item.oldrepublic.emitter_guardian item.oldrepublic.emitter_guardian.desc
    item.oldrepublic.emitter_reliquary item.oldrepublic.emitter_reliquary.desc item.oldrepublic.fence_book
    item.oldrepublic.grave_silk item.oldrepublic.grip_ridged item.oldrepublic.grip_ridged.desc
    item.oldrepublic.grip_wrapped item.oldrepublic.grip_wrapped.desc item.oldrepublic.holocron_shard
    item.oldrepublic.imperial_commendation item.oldrepublic.jedi_boots item.oldrepublic.jedi_holocron
    item.oldrepublic.jedi_hood item.oldrepublic.jedi_knight_robe item.oldrepublic.jedi_robe
    item.oldrepublic.jedi_trousers item.oldrepublic.kyber_crystal item.oldrepublic.kyber_crystal.bound
    item.oldrepublic.kyber_crystal.hue item.oldrepublic.kyber_crystal.owner item.oldrepublic.kyber_crystal.unattuned
    item.oldrepublic.lightsaber item.oldrepublic.lightsaber.color item.oldrepublic.lightsaber.ignited
    item.oldrepublic.lightsaber.owner item.oldrepublic.lightsaber.parts item.oldrepublic.lightsaber.retracted
    item.oldrepublic.lightsaber.thrown item.oldrepublic.lumen_thread item.oldrepublic.poacher_captain_spawn_egg
    item.oldrepublic.poachers_ledger item.oldrepublic.poachers_ledger.page1 item.oldrepublic.poachers_ledger.page2
    item.oldrepublic.poachers_ledger.page3 item.oldrepublic.poachers_ledger.page4 item.oldrepublic.pommel_archivist
    item.oldrepublic.pommel_archivist.desc item.oldrepublic.pommel_balanced item.oldrepublic.pommel_balanced.desc
    item.oldrepublic.pommel_weighted item.oldrepublic.pommel_weighted.desc item.oldrepublic.republic_commendation
    item.oldrepublic.republic_trooper_boots item.oldrepublic.republic_trooper_chestplate
    item.oldrepublic.republic_trooper_helmet item.oldrepublic.republic_trooper_leggings
    item.oldrepublic.republic_trooper_spawn_egg item.oldrepublic.ruin_sentinel_spawn_egg
    item.oldrepublic.singing_crystal item.oldrepublic.sith_acolyte_spawn_egg item.oldrepublic.sith_boots
    item.oldrepublic.sith_holocron item.oldrepublic.sith_hood item.oldrepublic.sith_lord_robe
    item.oldrepublic.sith_robe item.oldrepublic.sith_trooper_boots item.oldrepublic.sith_trooper_chestplate
    item.oldrepublic.sith_trooper_helmet item.oldrepublic.sith_trooper_leggings
    item.oldrepublic.sith_trooper_spawn_egg item.oldrepublic.sith_trousers item.oldrepublic.vibroblade
    item.oldrepublic.wanderers_journal item.oldrepublic.wayfarer_holocron subtitles.oldrepublic.acolyte.death
    subtitles.oldrepublic.acolyte.hurt subtitles.oldrepublic.acolyte.shove
    subtitles.oldrepublic.acolyte.shove_windup subtitles.oldrepublic.acolyte.yield
    subtitles.oldrepublic.ambient.cave subtitles.oldrepublic.ambient.jedi_ruin subtitles.oldrepublic.ambient.outpost
    subtitles.oldrepublic.ambient.reliquary subtitles.oldrepublic.ambient.ruin_chime
    subtitles.oldrepublic.ambient.sith_tomb subtitles.oldrepublic.ambient.tomb_whisper
    subtitles.oldrepublic.ambient.wreck subtitles.oldrepublic.ambient.wreck_creak
    subtitles.oldrepublic.armour.equip_robe subtitles.oldrepublic.armour.equip_trooper
    subtitles.oldrepublic.armour.set_bonus subtitles.oldrepublic.awakening.burst
    subtitles.oldrepublic.awakening.drone subtitles.oldrepublic.awakening.whispers
    subtitles.oldrepublic.blaster.bolt_flyby subtitles.oldrepublic.blaster.charge subtitles.oldrepublic.blaster.fire
    subtitles.oldrepublic.blaster.impact subtitles.oldrepublic.blaster.pistol_fire
    subtitles.oldrepublic.boss.collector.binders subtitles.oldrepublic.boss.collector.echo_fade
    subtitles.oldrepublic.boss.collector.echo_form subtitles.oldrepublic.boss.collector.execute
    subtitles.oldrepublic.boss.collector.grip subtitles.oldrepublic.boss.collector.grip_break
    subtitles.oldrepublic.boss.collector.grip_hold subtitles.oldrepublic.boss.collector.hurl_lift
    subtitles.oldrepublic.boss.collector.hurl_return subtitles.oldrepublic.boss.collector.hurl_rip
    subtitles.oldrepublic.boss.collector.hurl_throw subtitles.oldrepublic.boss.collector.hurt
    subtitles.oldrepublic.boss.collector.lash_tell subtitles.oldrepublic.boss.collector.lash_wave
    subtitles.oldrepublic.boss.collector.leash subtitles.oldrepublic.boss.collector.lunge
    subtitles.oldrepublic.boss.collector.palm_tell subtitles.oldrepublic.boss.collector.spent
    subtitles.oldrepublic.boss.collector.surge_clash subtitles.oldrepublic.boss.collector.surge_gather
    subtitles.oldrepublic.boss.collector.surge_release subtitles.oldrepublic.boss.collector.transition
    subtitles.oldrepublic.boss.collector.unsung subtitles.oldrepublic.boss.collector.walk_out
    subtitles.oldrepublic.boss.collector.ward_hum subtitles.oldrepublic.boss.collector.yield
    subtitles.oldrepublic.captain.death subtitles.oldrepublic.captain.heavy_windup
    subtitles.oldrepublic.captain.hurt subtitles.oldrepublic.captain.yield subtitles.oldrepublic.crystal.calling
    subtitles.oldrepublic.crystal.chime subtitles.oldrepublic.crystal.discharge subtitles.oldrepublic.crystal.form
    subtitles.oldrepublic.crystal.resonance subtitles.oldrepublic.crystal.unstable_charge
    subtitles.oldrepublic.dialogue.open subtitles.oldrepublic.dialogue.select subtitles.oldrepublic.force.fail
    subtitles.oldrepublic.force.heal.end subtitles.oldrepublic.force.heal.interrupt
    subtitles.oldrepublic.force.heal.loop subtitles.oldrepublic.force.heal.start
    subtitles.oldrepublic.force.heal.ward subtitles.oldrepublic.force.jump.gather
    subtitles.oldrepublic.force.jump.glide subtitles.oldrepublic.force.jump.land
    subtitles.oldrepublic.force.jump.launch subtitles.oldrepublic.force.jump.slam
    subtitles.oldrepublic.force.lift.hurl subtitles.oldrepublic.force.lift.loop
    subtitles.oldrepublic.force.lift.slam subtitles.oldrepublic.force.lift.start
    subtitles.oldrepublic.force.lightning.charge subtitles.oldrepublic.force.lightning.impact
    subtitles.oldrepublic.force.lightning.loop subtitles.oldrepublic.force.lightning.release
    subtitles.oldrepublic.force.pull.cast subtitles.oldrepublic.force.pull.catch
    subtitles.oldrepublic.force.pull.disarm subtitles.oldrepublic.force.pull.drag
    subtitles.oldrepublic.force.push.charge subtitles.oldrepublic.force.push.impact
    subtitles.oldrepublic.force.push.release subtitles.oldrepublic.force.push.slam
    subtitles.oldrepublic.force.sense.cast subtitles.oldrepublic.force.sense.danger
    subtitles.oldrepublic.force.sense.reveal subtitles.oldrepublic.grenade.arm subtitles.oldrepublic.grenade.burst
    subtitles.oldrepublic.grenade.fuse subtitles.oldrepublic.hoard.open subtitles.oldrepublic.holocron.archive_keep
    subtitles.oldrepublic.holocron.archive_return subtitles.oldrepublic.holocron.break
    subtitles.oldrepublic.holocron.echo subtitles.oldrepublic.holocron.gatekeeper_fade
    subtitles.oldrepublic.holocron.gatekeeper_form subtitles.oldrepublic.holocron.hum
    subtitles.oldrepublic.holocron.listen subtitles.oldrepublic.holocron.open subtitles.oldrepublic.holocron.rise
    subtitles.oldrepublic.holocron.seal subtitles.oldrepublic.holocron.unmake
    subtitles.oldrepublic.identity.alignment_dark subtitles.oldrepublic.identity.alignment_light
    subtitles.oldrepublic.identity.rank_up subtitles.oldrepublic.journal.entry subtitles.oldrepublic.journal.open
    subtitles.oldrepublic.meditation.break subtitles.oldrepublic.meditation.complete
    subtitles.oldrepublic.meditation.loop subtitles.oldrepublic.mission.accept
    subtitles.oldrepublic.mission.complete subtitles.oldrepublic.mission.update
    subtitles.oldrepublic.music.collector.archivist subtitles.oldrepublic.music.collector.singing_wall
    subtitles.oldrepublic.music.collector.unkept subtitles.oldrepublic.poacher.death
    subtitles.oldrepublic.poacher.hurt subtitles.oldrepublic.puzzle.anchor_release
    subtitles.oldrepublic.puzzle.anchor_strain subtitles.oldrepublic.puzzle.counterweight_rise
    subtitles.oldrepublic.puzzle.counterweight_sink subtitles.oldrepublic.puzzle.counterweight_warn
    subtitles.oldrepublic.puzzle.pedestal_ready subtitles.oldrepublic.puzzle.rubble_crumble
    subtitles.oldrepublic.puzzle.seal_open subtitles.oldrepublic.puzzle.solved
    subtitles.oldrepublic.puzzle.step_extend subtitles.oldrepublic.puzzle.step_retract
    subtitles.oldrepublic.puzzle.urn_echo subtitles.oldrepublic.puzzle.urn_wrong
    subtitles.oldrepublic.puzzle.veil_crumble subtitles.oldrepublic.puzzle.veil_reveal
    subtitles.oldrepublic.pylon.crack subtitles.oldrepublic.pylon.regrow subtitles.oldrepublic.pylon.shatter
    subtitles.oldrepublic.pylon.silence subtitles.oldrepublic.pylon.sing subtitles.oldrepublic.saber.clash
    subtitles.oldrepublic.saber.deflect subtitles.oldrepublic.saber.finisher subtitles.oldrepublic.saber.form_change
    subtitles.oldrepublic.saber.guard_break subtitles.oldrepublic.saber.heavy_strike
    subtitles.oldrepublic.saber.heavy_windup subtitles.oldrepublic.saber.hit subtitles.oldrepublic.saber.hum
    subtitles.oldrepublic.saber.hum.singing subtitles.oldrepublic.saber.ignite
    subtitles.oldrepublic.saber.lightning_block subtitles.oldrepublic.saber.lunge
    subtitles.oldrepublic.saber.npc_windup subtitles.oldrepublic.saber.parry subtitles.oldrepublic.saber.retract
    subtitles.oldrepublic.saber.swing subtitles.oldrepublic.saber.throw subtitles.oldrepublic.saber.throw_catch
    subtitles.oldrepublic.saber.throw_loop subtitles.oldrepublic.sentinel.core_exposed
    subtitles.oldrepublic.sentinel.death subtitles.oldrepublic.sentinel.hurt
    subtitles.oldrepublic.sentinel.lance_charge subtitles.oldrepublic.sentinel.lance_fire
    subtitles.oldrepublic.sentinel.slam subtitles.oldrepublic.sentinel.slam_tell subtitles.oldrepublic.sentinel.wake
    subtitles.oldrepublic.settler.death subtitles.oldrepublic.settler.freed subtitles.oldrepublic.settler.hurt
    subtitles.oldrepublic.site.discovered subtitles.oldrepublic.skill.learn subtitles.oldrepublic.skill.respec
    subtitles.oldrepublic.skill.slot subtitles.oldrepublic.story.blade_touch subtitles.oldrepublic.story.cairn_touch
    subtitles.oldrepublic.story.calling_fall subtitles.oldrepublic.story.calling_holocron
    subtitles.oldrepublic.story.calling_reliquary subtitles.oldrepublic.story.collector_fallen
    subtitles.oldrepublic.story.duel_begin subtitles.oldrepublic.story.duel_end
    subtitles.oldrepublic.story.lesson_complete subtitles.oldrepublic.story.ones_that_sing
    subtitles.oldrepublic.story.rank_three subtitles.oldrepublic.story.tablet_break
    subtitles.oldrepublic.story.two_voices subtitles.oldrepublic.terminal.use subtitles.oldrepublic.trial.begin
    subtitles.oldrepublic.trial.complete subtitles.oldrepublic.trial.echo_form
    subtitles.oldrepublic.trial.echo_lunge subtitles.oldrepublic.trooper.death subtitles.oldrepublic.trooper.flare
    subtitles.oldrepublic.trooper.hurt subtitles.oldrepublic.trooper.imperial.charge
    subtitles.oldrepublic.trooper.imperial.fire subtitles.oldrepublic.trooper.kolto
    subtitles.oldrepublic.trooper.radio subtitles.oldrepublic.trooper.rally
    subtitles.oldrepublic.trooper.republic.charge subtitles.oldrepublic.trooper.republic.fire
    subtitles.oldrepublic.trooper.stand subtitles.oldrepublic.trooper.tend subtitles.oldrepublic.trooper.vent
    subtitles.oldrepublic.vent.hiss subtitles.oldrepublic.vent.puff subtitles.oldrepublic.vibroblade.hit
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


def listed_ids(path):
    data = load_json(path)
    return [e["id"] for e in data["events"]] if data else []


def java_sources():
    for d in JAVA_DIRS:
        yield from d.rglob("*.java")


def call_counts(names, pattern, line_filter=None):
    """How often pattern (a regex whose group 1 is the id) matches each name across the main and client sources,
    counting only lines that contain line_filter when it is given."""
    counts = dict.fromkeys(names, 0)
    regex = re.compile(pattern)
    for f in java_sources():
        for line in f.read_text(encoding="utf-8").splitlines():
            if line_filter and line_filter not in line:
                continue
            for m in regex.finditer(line):
                if m.group(1) in counts:
                    counts[m.group(1)] += 1
    return counts


def particle_registrations(names):
    """register("<name>") on a particle-type line: the pattern every *Particles class uses. Only lines that name a
    ParticleType count, so an item or block of the same name (duskglass_shard) is not mistaken for a particle."""
    return call_counts(names, r'\bregister\(\s*"([a-z0-9_./]+)"', "ParticleType")


def item_registrations(names):
    """ModRegistry.item("<name>", ...) calls."""
    return call_counts(names, r'\bitem\(\s*"([a-z0-9_]+)"')


def lang_keys(kind, names):
    return {f"{kind}.oldrepublic.{n}": n for n in names}


def check():
    problems = []
    new_sounds = [s for group in NEW_SOUNDS.values() for s in group]
    lang = lang_parts()

    # --- section 16 itself must be consistent
    for label, values in (("new sound", new_sounds + REUSED_SOUNDS),
                          ("new item", NEW_ITEMS + NEW_BLOCKS + REUSED_ITEMS),
                          ("new entity", NEW_ENTITIES + REUSED_ENTITIES), ("new particle", NEW_PARTICLES)):
        dupes = sorted({v for v in values if values.count(v) > 1})
        if dupes:
            problems.append(f"{label} ids listed twice (or as both new and reused) in this script: {dupes}")

    # --- new ids must not have existed before Milestone 4
    for s in new_sounds:
        if s in BASELINE_SOUNDS:
            problems.append(f"new sound {s} already existed in sounds.json before M4")
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
            problems.append(f"lang key {k} of a new id already existed in a lang part before M4")
    m1 = m1_sound_ids()
    older = {"the M1 catalogue": m1, "events_m2.json": set(listed_ids(EVENTS_M2)),
             "events_m3.json": set(listed_ids(EVENTS_M3))}
    for s in new_sounds:
        for where, declared in older.items():
            if s in declared:
                problems.append(f"new sound {s} is declared by {where}")

    # --- reused ids must exist, and be declared only once
    sounds = load_json(SOUNDS_JSON) or {}
    m4_ids = listed_ids(EVENTS_M4)
    for s in REUSED_SOUNDS:
        if s not in BASELINE_SOUNDS:
            problems.append(f"reused sound {s} is not in the pre-M4 baseline")
        if s not in sounds:
            problems.append(f"reused sound {s} is missing from sounds.json")
        if not any(s in older[w] for w in ("the M1 catalogue", "events_m2.json")):
            problems.append(f"reused sound {s} is no longer declared by the M1 catalogue or events_m2.json")
        for name, listed in (("events_m3.json", older["events_m3.json"]), ("events_m4.json", m4_ids)):
            if s in listed:
                problems.append(f"reused sound {s} must not be listed in {name}")
        if f"subtitles.oldrepublic.{s}" not in lang:
            problems.append(f"reused sound {s} lost its subtitle")
    for i in REUSED_ITEMS:
        if i not in BASELINE_ITEMS:
            problems.append(f"reused item {i} is not in the pre-M4 baseline")
        if not (ASSETS / "items" / f"{i}.json").exists():
            problems.append(f"reused item {i} is missing from assets/oldrepublic/items/")
        key = f"item.oldrepublic.{i}"
        if len(lang.get(key, [])) != 1:
            problems.append(f"reused item {i}: {key} must be defined in exactly one lang part "
                            f"(found {lang.get(key, [])})")
    reg = item_registrations(REUSED_ITEMS + list(RENAMED_ITEMS))
    for i in REUSED_ITEMS:
        if reg[i] != 1:
            problems.append(f'reused item {i} must be registered exactly once '
                            f'(item("{i}", ...) appears {reg[i]} times)')
    for e in REUSED_ENTITIES:
        key = f"entity.oldrepublic.{e}"
        if key not in BASELINE_LANG:
            problems.append(f"reused entity {e} is not in the pre-M4 baseline")
        if len(lang.get(key, [])) != 1:
            problems.append(f"reused entity {e}: {key} must be defined in exactly one lang part "
                            f"(found {lang.get(key, [])})")

    # --- spec spellings that Phase 0 renamed or folded into an existing id must not appear
    java = MOD_SOUNDS.read_text(encoding="utf-8") if MOD_SOUNDS.exists() else ""
    for spelling, actual in RENAMED_SOUNDS.items():
        if spelling in sounds or spelling in m4_ids or f'register("{spelling}")' in java:
            problems.append(f"sound {spelling} must not exist: the spec's id is the existing {actual}")
    for spelling, actual in RENAMED_ITEMS.items():
        if ((ASSETS / "items" / f"{spelling}.json").exists() or f"item.oldrepublic.{spelling}" in lang
                or reg[spelling]):
            problems.append(f"item {spelling} must not exist: the item is {actual}")

    # --- particles: new or absent, registered at most once
    preg = particle_registrations(NEW_PARTICLES)
    for p in NEW_PARTICLES:
        if preg[p] > 1:
            problems.append(f'new particle {p} is registered {preg[p]} times')

    # --- events_m4.json lists exactly the new sound ids, under their owners' modules
    m4_events = load_json(EVENTS_M4)
    if m4_events is None:
        problems.append(f"{EVENTS_M4.relative_to(ROOT)} is missing")
    else:
        if len(m4_ids) != len(set(m4_ids)):
            problems.append("events_m4.json lists an id twice")
        expected = {s: feature for feature, group in NEW_SOUNDS.items() for s in group}
        for s in sorted(set(expected) - set(m4_ids)):
            problems.append(f"new sound {s} is missing from events_m4.json")
        for s in sorted(set(m4_ids) - set(expected)):
            problems.append(f"events_m4.json lists {s}, which section 16 does not mark new")
        for e in m4_events["events"]:
            if e["id"] in expected and e.get("feature") != expected[e["id"]]:
                problems.append(f"events_m4.json: {e['id']} belongs to feature {expected[e['id']]}, "
                                f"not {e.get('feature')}")

    # --- every new sound is generated: sounds.json entry, subtitle, ModSounds constant
    subtitles = load_json(LANG_DIR / "sounds.json") or {}
    for s in new_sounds:
        if s not in sounds:
            problems.append(f"new sound {s} has no sounds.json entry (run gen_sounds.py --m4 --java)")
        if f"subtitles.oldrepublic.{s}" not in subtitles:
            problems.append(f"new sound {s} has no subtitle in lang-parts/sounds.json")
        if f'register("{s}")' not in java:
            problems.append(f"new sound {s} has no ModSounds constant")
    return problems


def report():
    lang = lang_parts()
    sounds = load_json(SOUNDS_JSON) or {}
    rows = [(f"sounds {feature} (sounds.json)", group, lambda s: s in sounds) for feature, group in NEW_SOUNDS.items()]
    rows += [
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
        print(f"check_m4_ids: {p}")
    if problems:
        sys.exit(f"check_m4_ids: {len(problems)} problem(s)")
    print("check_m4_ids: ok")


if __name__ == "__main__":
    main()
