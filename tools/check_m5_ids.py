#!/usr/bin/env python3
"""Milestone 5 identifier check (docs/MILESTONE_5.md section 18, and the sounds of 15.3 and 15.4). Run on CI.

Section 18 marks every Milestone 5 id as new or "existing, reused". This script fails when:

  * an id marked new already existed before Milestone 5, in the frozen pre-M5 tree below: a sound in sounds.json, a
    particle in assets/oldrepublic/particles/, an item in items/, a block in blockstates/, a data file under data/
    (dimension, dimension type, noise settings, biome, structure, structure set, feature, template, schematic, vendor
    stock, mission, loot table, damage type, tag), a skill tree node, a payload type, an effect event with a client
    handler, or a lang key (the names of items, blocks, entities, status effects, keys, biomes, dimensions, death
    messages and powers, and the 18.5 keys listed below); or when a new sound id is declared by the M1 catalogue in
    gen_sounds.py or by events_m2.json, events_m3.json or events_m4.json;
  * an id marked existing and reused is missing from the current tree, or is declared again: a reused sound listed in
    events_m5.json or without its sounds.json entry, subtitle or ModSounds constant; a reused particle, entity or
    payload not registered exactly once; a reused effect event without exactly one client handler; a reused lang key
    not defined by exactly its one lang part; a reused tag file gone;
  * a plain item of registry/M5Items (17.1) is not registered exactly once, there;
  * tools/assets/sounds/events_m5.json does not list exactly the new sound ids, each under its owner's recipe module;
  * a new sound id has no sounds.json entry, no subtitle in src/main/lang-parts/sounds.json or no ModSounds constant
    (gen_sounds.py --m5 --java writes all three);
  * a new particle (15.2) is registered more than once, and likewise a new item, block, entity, payload, client
    effect handler or skill tree node (registrations are counted where the id is a string literal: an id built at
    run time, such as a HiltParts item from part.itemId(), is not counted).

New ids that do not exist yet are fine: workers add them during the milestone (particles too: their owners register
them). --report prints how many of each kind exist so far.

Phase 0 decisions (these are the truth here):
  * the baseline is the last commit before Milestone 5 Phase 0, so what Phase 0 itself added (the six M5Items, the
    placeholder sounds of events_m5.json, quality.oldrepublic.legendary, the new HiltParts values) counts as new;
  * the sound features are the recipe modules of 15.4: flight (W1), space (W2), world_m5 (W3), story_m5 (W4),
    turning (W5), force_m5 (W6), saber_m5 (W7), enemy_m5 (W8);
  * verse_stone and holdfast_gate are registered with ModRegistry.blockOnly (18.1), so they have no item model; the
    other four W3 blocks have block items;
  * the 15.2 particles are registered by their owners, so they are new or absent, never required; the reused ones
    must stay registered exactly once;
  * owners add variants to the existing effect events lesson_complete (11-15), bench_craft (3), and send hunter_tell,
    hunter_attack, npc_windup, grenade_tell and grenade_fuse by id (15.3): a second ClientEffects.register for one of
    them would silently replace its handler, so each keeps exactly one;
  * ship_action, ability_input and ability_state gain buttons and slots (18.2), not a second payload type;
  * #c:bosses (data/c/tags/entity_type/bosses.json), #oldrepublic:push_resistant and the is_planet biome tag are
    existing tags that M5 adds to; the new tag files of 18.1 and 17.1 are created empty by the lead and filled by their
    owners, so they are new or absent;
  * the "feature asteroid" of 18.1 is a configured and a placed feature of that id; a template is the .nbt file under
    data/oldrepublic/structure/ (its site anchors follow it); "the W3 block tables" are the loot tables of the six W3
    blocks; "entities/<W8 entities>" are the loot tables of all nine W8 entities; the unnamed "gameplay/<W4 rewards>"
    tables and the hub_host and vendor variants (data inside the reused entities) are not ids here;
  * subtitles and skill node names are not frozen twice: before M5 every sound had exactly its
    subtitles.oldrepublic.<id> key and every node exactly its skill.oldrepublic.node.<id>.{name,desc} keys, so the
    sound and node baselines cover them; the 18.5 prefixes that are not tied to an id (turning.oldrepublic.*,
    space.oldrepublic.*, hud.oldrepublic.{combat,flight}.*, quest.oldrepublic.c5.*, the c5_, turn_ and rank4_
    dialogues, mission.oldrepublic.{c5,ghessar,tyrvann}_*, boss.oldrepublic.{ulvaine,cantor,mentor}.*, ...) had no
    key before M5 (checked in Phase 0) and are left to the lang parts' owners;
  * rank.oldrepublic.{jedi,sith}.4 ("Master", "Darth") are reused and stay in identity.json only; every pre-M5
    quest.oldrepublic.c2.banner.* key stays in quest.json only, and the M5 banners under that prefix are new.

The baseline is the tree at commit 04589ef (the last commit before Milestone 5 Phase 0), frozen in this file so the
check needs no git history. Only stdlib; run from anywhere: python3 tools/check_m5_ids.py [--report]
"""
import argparse
import ast
import json
import re
import sys
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/oldrepublic"
DATA = ROOT / "src/main/resources/data"
LANG_DIR = ROOT / "src/main/lang-parts"
SOUNDS_JSON = ASSETS / "sounds.json"
SOUND_LISTS = ROOT / "tools/assets/sounds"
EVENTS_M5 = SOUND_LISTS / "events_m5.json"
GEN_SOUNDS = ROOT / "tools/assets/gen_sounds.py"
MOD_SOUNDS = ROOT / "src/main/java/com/oldrepublic/registry/ModSounds.java"
M5_ITEMS_JAVA = ROOT / "src/main/java/com/oldrepublic/registry/M5Items.java"
JAVA_DIRS = [ROOT / "src/main/java", ROOT / "src/client/java"]


def ids(text):
    return text.split()


# ----------------------------------------------------------------------------
# Section 18, 15.2 and 15.4 (frozen with the spec and the Phase 0 decisions above; change only together with
# docs/MILESTONE_5.md)
# ----------------------------------------------------------------------------
NEW_BLOCKS = ids("salt_crust sunstone_ore stormglass_cluster peat verse_stone holdfast_gate")
BLOCK_ONLY = ids("verse_stone holdfast_gate")
M5_ITEMS = ids("sunstone stormglass_shard hymnstone_shard clan_iron concussion_missile sealed_cargo")
NEW_ITEMS = M5_ITEMS + ids("""
    hymn_interceptor_spawn_egg kajidic_raider_spawn_egg hask_fighter_spawn_egg
    emitter_cantor grip_sunforged pommel_hask lens_stormglass
    mod_clan_iron_plating mod_hymnweave_lining mod_storm_cell mod_sunstone_edge spindle_capacitor hymn_tuned_drive
    jedi_master_robe sith_darth_robe wayfarer_mantle cantors_mask
    watch_holocron ulvaines_writ
    cartel_enforcer_spawn_egg hask_warrior_spawn_egg hymnsworn_spawn_egg sand_scour_spawn_egg stormback_spawn_egg
""")
LEGENDARY = ids("""
    emitter_cantor pommel_hask grip_sunforged lens_stormglass mod_clan_iron_plating mod_hymnweave_lining mod_storm_cell
    mod_sunstone_edge spindle_capacitor hymn_tuned_drive
""")
W8_ENTITIES = ids("""
    cartel_enforcer hask_warrior hymnsworn sand_scour stormback darth_ulvaine first_cantor cantor_choir hymn_resonator
""")
NEW_ENTITIES = ids("""
    interceptor
    hymn_interceptor kajidic_raider hask_fighter blockade_gunship gunship_generator ship_bolt ship_missile nav_buoy
    vessba
""") + W8_ENTITIES
REUSED_ENTITIES = ids("hub_host vendor")
NEW_PARTICLES = ids("""
    atmosphere_streak hull_debris sand_gust verse_mote stasis_rime drain_mote valor_dust sand_burst spectral_wisp
""")
REUSED_PARTICLES = ids("""
    engine_exhaust spark_shower grenade_smoke air_ripple lightning_spark gather_dust crystal_glint flare_ember tomb_dust
    shadow_wisp light_mote vent_grit
""")
NEW_SOUNDS = {
    "flight": ids("""
        ship.flight.engine ship.flight.boost ship.flight.thruster ship.flight.atmosphere ship.flight.impact
        ship.flight.land_warn ship.flight.helm ship.flight.edge ship.interceptor.engine ship.refit ship.upgrade_fit
        ship.flight.alarm ship.flight.rescue
    """),
    "space": ids("""
        space.cannon.fire space.cannon.overheat space.turret.fire space.fighter.fire space.missile.lock_tone
        space.missile.locked space.missile.launch space.missile.warning space.bolt.hit_shield space.bolt.hit_hull
        space.shield.down space.shield.recharge space.fighter.engine space.hymn_interceptor.engine space.fighter.explode
        space.gunship.hum space.gunship.generator_down space.gunship.explode space.gunship.launch space.comm space.scan
        space.buoy ambient.space music.space
    """),
    "world_m5": ids("""
        verse_stone.hum verse_stone.pulse verse_stone.channel verse_stone.silence sandstorm.wind stormglass.form
        holdfast.gate hymnal.door music.ghessar music.tyrvann ambient.ghessar.dunes ambient.ghessar.saltflat
        ambient.ghessar.canyons ambient.tyrvann.stormwood ambient.tyrvann.mire ambient.tyrvann.basalt ambient.saltgate
        ambient.holdfast ambient.hymnal
    """),
    "story_m5": ids("story.chapter_five story.verse_vision"),
    "turning": ids("story.turning.offer story.turning.rite story.rank_four"),
    "force_m5": ids("""
        force.stasis.cast force.stasis.loop force.stasis.break force.valor.cast force.valor.end force.drain.loop
        force.drain.end force.fear.cast force.rage.cast force.rage.loop force.rage.end force.mind_trick.cast
        force.mind_trick.fail force.focus
    """),
    "saber_m5": ids("""
        saber.guardian.ignite saber.guardian.hum saber.guardian.retract saber.duelist.ignite saber.duelist.hum
        saber.duelist.retract saber.reliquary.ignite saber.reliquary.hum saber.reliquary.retract saber.cantor.ignite
        saber.cantor.hum saber.cantor.retract saber.offhand_swing bench.legendary
    """),
    "enemy_m5": ids("""
        cartel.bark cartel.hurt cartel.death cartel.shove
        hask.bark hask.hurt hask.death hask.slam_tell hask.slam hask.shield_block hask.bash_tell hask.bash
        hymnsworn.chant hymnsworn.hurt hymnsworn.death hymnsworn.call_tell hymnsworn.call_strike
        scour.hiss scour.burst_tell scour.burst scour.burrow scour.hurt scour.death scour.matriarch.sweep
        scour.matriarch.call
        stormback.idle stormback.hurt stormback.death stormback.charge_tell stormback.impact stormback.roar
        stormback.charged
        boss.ulvaine.hurt boss.ulvaine.phase boss.ulvaine.step_tell boss.ulvaine.step boss.ulvaine.lightning
        boss.ulvaine.rod_charge boss.ulvaine.rod_strike boss.ulvaine.pulse boss.ulvaine.crescendo
        boss.ulvaine.resonator boss.ulvaine.blast boss.ulvaine.yield boss.ulvaine.echo_fade
        boss.cantor.hurt boss.cantor.phase boss.cantor.beam_tell boss.cantor.beam boss.cantor.slam_tell
        boss.cantor.slam boss.cantor.chain boss.cantor.roots boss.cantor.fog boss.cantor.mark boss.cantor.strike
        boss.cantor.ring_tell boss.cantor.ring boss.cantor.silence boss.cantor.unravel boss.cantor.choir
        boss.cantor.engine
        music.boss.ulvaine music.boss.cantor story.hologram
    """),
}
REUSED_SOUNDS = ids("""
    workbench.tune blaster.impact story.lesson_complete story.duel_begin story.duel_end saber.form_change
    force.lightning.release
""")
NEW_EFFECTS = ids("stasis fear pacified valor rage drained")
NEW_POWERS = ids("force_stasis force_valor force_drain force_fear force_rage force_mind_trick")
NEW_SKILL_NODES = ids("""
    force_stasis stasis_field stasis_lock force_valor valor_shared valor_steadfast force_drain drain_life drain_essence
    force_fear fear_terror fear_dread force_rage rage_unbound rage_cold force_mind_trick mind_trick_confuse
    mind_trick_mass wayfarers_stride form_makashi form_djem_so form_jar_kai
""")
NEW_KEYS = ids("helm focus")
NEW_PAYLOADS = ids("""
    flight_boost flight_status flight_motion ship_fire ship_combat_status space_encounter planet_weather
    rank_four_checklist offhand_strike boss_music
""")
REUSED_PAYLOADS = ids("ship_action ability_input ability_state")
NEW_EFFECT_EVENTS = ids("""
    ship_flight atmosphere ship_refit ship_disabled
    ship_weapon ship_hit ship_shield missile_lock ship_destroyed gunship space_comm space_scan nav_buoy
    sandstorm verse_stone stormglass site_door
    verse_vision
    turning rank_four
    force_stasis force_valor force_drain force_fear force_rage force_mind_trick
    offhand_strike crystal_tune
    hologram scour hymnsworn stormback hask_warrior ulvaine first_cantor
""")
REUSED_EFFECT_EVENTS = ids("lesson_complete bench_craft hunter_tell hunter_attack npc_windup grenade_tell grenade_fuse")
NEW_DATA = {
    "oldrepublic/dimension": ids("""
        ghessar tyrvann orbit_home orbit_selvaren orbit_varkhast orbit_ghessar orbit_tyrvann
    """),
    "oldrepublic/dimension_type": ids("ghessar tyrvann orbit"),
    "oldrepublic/worldgen/noise_settings": ids("ghessar tyrvann orbit_void"),
    "oldrepublic/worldgen/biome": ids("""
        ghessar_dunes ghessar_saltflat ghessar_canyons tyrvann_stormwood tyrvann_mire tyrvann_basalt orbit_field
    """),
    "oldrepublic/worldgen/structure": ids("crashed_freighter kajidic_post hymn_shrine mire_grave"),
    "oldrepublic/worldgen/structure_set": ids("crashed_freighters kajidic_posts hymn_shrines mire_graves"),
    "oldrepublic/worldgen/configured_feature": ids("asteroid"),
    "oldrepublic/worldgen/placed_feature": ids("asteroid"),
    "oldrepublic/structure": ids("""
        hub/saltgate hub/holdfast site/verse_stone site/hymnal_arch site/hymnal site/debtor_camp site/detention_post
        planet/crashed_freighter planet/kajidic_post planet/hymn_shrine planet/mire_grave station/spindle
    """),
    "oldrepublic/schematic": ids("concussion_missile") + [f"{c}_legendary" for c in LEGENDARY],
    # No saltgate_shipwright: spec 7.1 gives him no stock lines; his services are his dialogue.
    "oldrepublic/vendor": ids("cartel_fence hask_armourer"),
    "oldrepublic/mission": ids("""
        c5_debt c5_cargo c5_matriarch c5_honour c5_counting_house
        ghessar_collect ghessar_recover ghessar_cull ghessar_escort ghessar_run ghessar_bounty
        tyrvann_hunt tyrvann_spar tyrvann_cleanse tyrvann_recover tyrvann_patrol tyrvann_bounty
    """),
    "oldrepublic/loot_table": ids("""
        gameplay/space_bounty chests/spindle_vault
        chests/crashed_freighter chests/kajidic_post chests/hymn_shrine chests/mire_grave
        gameplay/long_hymn
    """) + [f"blocks/{b}" for b in NEW_BLOCKS] + [f"entities/{e}" for e in W8_ENTITIES],
    "oldrepublic/damage_type": ids("ship_weapon force_drain storm_call hymn"),
    "oldrepublic/tags/entity_type": ids("ship_targets fearless strong_willed mind_trickable force_resistant"),
    "oldrepublic/tags/worldgen/biome": ids("is_ghessar is_tyrvann"),
    "oldrepublic/tags/worldgen/structure": ids("crashed_freighters kajidic_posts hymn_shrines mire_graves"),
}
REUSED_DATA = {
    "c/tags/entity_type": ids("bosses"),
    "oldrepublic/tags/entity_type": ids("push_resistant"),
    "oldrepublic/tags/worldgen/biome": ids("is_planet"),
}
DATA_EXT = {"oldrepublic/structure": ".nbt"}
BANNERS = ids("chapter5 turning rank_four legendary long_hymn")
NEW_LANG = ids("""
    destination.oldrepublic.saltgate destination.oldrepublic.holdfast
    faction.oldrepublic.vessba_kajidic faction.oldrepublic.clan_hask
    title.oldrepublic.wayfarer quality.oldrepublic.legendary journal.oldrepublic.chapter_header.5
""") + [f"quest.oldrepublic.c2.banner.{b}{v}" for b in BANNERS
       for v in ("", ".jedi", ".sith", ".independent", ".none")] \
    + [f"quest.oldrepublic.c2.banner.planet.{p}{v}" for p in ("ghessar", "tyrvann") for v in ("", ".sub")]
REUSED_LANG = {"rank.oldrepublic.jedi.4": "identity.json", "rank.oldrepublic.sith.4": "identity.json"}
REUSED_LANG_PREFIXES = {"quest.oldrepublic.c2.banner.": "quest.json"}

# ----------------------------------------------------------------------------
# Frozen pre-M5 baseline (commit 04589ef): sound ids, particle, item and blockstate files, data files, skill tree
# nodes, payload types, effect events with a client handler, and the lang keys of the families below.
# ----------------------------------------------------------------------------
# sounds.json ids (356); their subtitles.oldrepublic.<id> keys were exactly these
BASELINE_SOUNDS = set(ids("""
    acolyte.death acolyte.hurt acolyte.shove acolyte.shove_windup acolyte.yield ambient.cave ambient.greenwatch
    ambient.jedi_ruin ambient.outpost ambient.proving ambient.reliquary ambient.ruin_chime
    ambient.selvaren.highwood ambient.selvaren.meadow ambient.selvaren.tarn ambient.sith_tomb
    ambient.tomb_whisper ambient.varkhast.ashfall ambient.varkhast.redrock ambient.varkhast.tomb_valley
    ambient.wreck ambient.wreck_creak armour.equip_robe armour.equip_trooper armour.set_bonus ash_hound.death
    ash_hound.growl ash_hound.howl ash_hound.hurt ash_hound.idle ash_hound.lunge awakening.burst awakening.drone
    awakening.whispers beacon.recall beacon.refuse beacon.set bench.done bench.masterwork bench.mod_fit
    bench.mod_remove bench.salvage bench.weld blaster.bolt_flyby blaster.charge blaster.fire blaster.impact
    blaster.overheat blaster.pistol_fire blaster.repeater.fire blaster.repeater.spin_down
    blaster.repeater.spin_up blaster.rifle.charged blaster.vent blaster.vent_perfect boss.collector.binders
    boss.collector.echo_fade boss.collector.echo_form boss.collector.execute boss.collector.grip
    boss.collector.grip_break boss.collector.grip_hold boss.collector.hurl_lift boss.collector.hurl_return
    boss.collector.hurl_rip boss.collector.hurl_throw boss.collector.hurt boss.collector.lash_tell
    boss.collector.lash_wave boss.collector.leash boss.collector.lunge boss.collector.palm_tell
    boss.collector.spent boss.collector.surge_clash boss.collector.surge_gather boss.collector.surge_release
    boss.collector.transition boss.collector.unsung boss.collector.walk_out boss.collector.ward_hum
    boss.collector.yield boss.kaast.axe_draw boss.kaast.charge boss.kaast.combo boss.kaast.helmet_crack
    boss.kaast.hop boss.kaast.hurt boss.kaast.mine_arm boss.kaast.mine_beep boss.kaast.mine_burst
    boss.kaast.net_throw boss.kaast.phase boss.kaast.rifle boss.kaast.slam boss.kaast.slam_tell
    boss.kaast.walk_away boss.kaast.yield captain.death captain.heavy_windup captain.hurt captain.yield
    credits.pickup crystal.calling crystal.chime crystal.discharge crystal.form crystal.resonance
    crystal.unstable_charge dialogue.open dialogue.select droid.alert droid.death droid.fire droid.hurt
    droid.shield droid.spin_up droid.step electrostaff.charge electrostaff.clash electrostaff.shock
    force.barrier.break force.barrier.impact force.barrier.loop force.barrier.pulse force.barrier.start
    force.choke.crush force.choke.loop force.choke.release force.choke.start force.fail force.heal.end
    force.heal.interrupt force.heal.loop force.heal.start force.heal.ward force.jump.gather force.jump.glide
    force.jump.land force.jump.launch force.jump.slam force.lift.hurl force.lift.loop force.lift.slam
    force.lift.start force.lightning.charge force.lightning.impact force.lightning.loop force.lightning.release
    force.pull.cast force.pull.catch force.pull.disarm force.pull.drag force.push.charge force.push.impact
    force.push.release force.push.slam force.sense.cast force.sense.danger force.sense.reveal force.speed.burst
    force.speed.end force.speed.loop force.speed.start grenade.arm grenade.burst grenade.fuse grenade.stun
    grenade.throw heartstone.cleansed heartstone.wave hoard.open hollow.answer hollow.chime hollowhorn.charge
    hollowhorn.death hollowhorn.hurt hollowhorn.idle hollowhorn.impact hollowhorn.snort holocron.archive_keep
    holocron.archive_return holocron.break holocron.echo holocron.gatekeeper_fade holocron.gatekeeper_form
    holocron.hum holocron.listen holocron.open holocron.rise holocron.seal holocron.unmake hub.apprentice_spar
    identity.alignment_dark identity.alignment_light identity.rank_up jetpack.burst jetpack.empty journal.entry
    journal.open kolto.inject mandalorian.bark mandalorian.death mandalorian.explode mandalorian.flamer
    mandalorian.flamer_tell mandalorian.hurt mandalorian.jet mandalorian.jet_tell mandalorian.net
    mandalorian.net_tell mandalorian.rocket mandalorian.rocket_lock meditation.break meditation.complete
    meditation.loop medpac.use mission.accept mission.complete mission.update music.collector.archivist
    music.collector.singing_wall music.collector.unkept music.kaast.hunt music.kaast.last_stand music.kaast.trap
    music.selvaren music.varkhast planet.arrive poacher.death poacher.hurt puzzle.anchor_release
    puzzle.anchor_strain puzzle.counterweight_rise puzzle.counterweight_sink puzzle.counterweight_warn
    puzzle.pedestal_ready puzzle.rubble_crumble puzzle.seal_open puzzle.solved puzzle.step_extend
    puzzle.step_retract puzzle.urn_echo puzzle.urn_wrong puzzle.veil_crumble puzzle.veil_reveal pylon.crack
    pylon.regrow pylon.shatter pylon.silence pylon.sing rift.bleed rift.whisper saber.clash saber.deflect
    saber.finisher saber.form_change saber.guard_break saber.heavy_strike saber.heavy_windup saber.hit saber.hum
    saber.hum.singing saber.ignite saber.lightning_block saber.lunge saber.npc_windup saber.parry saber.retract
    saber.staff_ignite saber.staff_whirl saber.swing saber.throw saber.throw_catch saber.throw_loop scanner.ping
    scanner.pulse seeker.chirp seeker.fly seeker.scan sentinel.core_exposed sentinel.death sentinel.hurt
    sentinel.lance_charge sentinel.lance_fire sentinel.slam sentinel.slam_tell sentinel.wake settler.death
    settler.freed settler.hurt ship.climb ship.damaged ship.descent ship.fit_part ship.hyperspace ship.idle
    ship.jump_in ship.jump_out ship.land ship.power_up ship.ramp ship.spool ship.weld site.discovered
    skill.learn skill.respec skill.slot stim.crash stim.inject story.blade_touch story.cairn_touch
    story.calling_fall story.calling_holocron story.calling_reliquary story.chapter_four story.collector_fallen
    story.duel_begin story.duel_end story.lesson_complete story.ones_that_sing story.rank_three
    story.tablet_break story.two_voices terminal.use trial.begin trial.complete trial.echo_form trial.echo_lunge
    trooper.death trooper.flare trooper.hurt trooper.imperial.charge trooper.imperial.fire trooper.kolto
    trooper.radio trooper.rally trooper.republic.charge trooper.republic.fire trooper.stand trooper.tend
    trooper.vent vendor.buy vendor.refuse vent.hiss vent.puff vibro.cleave vibro.flurry vibroblade.hit
    vibroblade.swing waystone.cleanse waystone.corrupt waystone.hum workbench.assemble workbench.lens_fit
    workbench.slot workbench.tune
"""))

# assets/oldrepublic/particles/<id>.json
BASELINE_PARTICLES = set(ids("""
    air_ripple ash_flake ash_mote aurelite_glint blade_glint bolt_impact bolt_scorch conduit_spark contact_flash
    crystal_glint crystal_mote crystal_spark duskglass_shard echo_smoke engine_exhaust fail_mote falling_ember
    flame_jet flare_ember force_mote gather_dust grenade_smoke held_dust holocron_mote jump_trail kolto_mist
    lash_seam leaf_fall lift_shimmer light_mote lightning_segment lightning_spark lumen_spark lunge_streak
    miasma muzzle_glow projection_band pull_streak push_impact push_streak resonance_shimmer rope_fibre
    saber_spark sense_mote sense_stream shadow_wisp spark_shower steam_vent surge_dust tomb_dust veil_dust
    vent_grit vent_steam voice_dust voice_warm ward_current ward_glint ward_haze
"""))

# assets/oldrepublic/items/<id>.json
BASELINE_ITEMS = set(ids("""
    alloy_plate archive_record ash_hound_fang ash_hound_spawn_egg ashroot aurelite_ore aurelite_shard
    blaster_pistol blaster_rifle bounty_terminal budding_duskglass camp_gate cargo_bay_extension
    collapsed_rubble combat_stim contract_terminal cooked_hollowhorn counterweight_stone credit_bundle
    credit_chit crystal_poacher_spawn_egg crystal_veined_stone deepslate_aurelite_ore droid_actuator
    durasteel_plate duskglass_cluster duskglass_shard echo_urn electrostaff emitter_duelist emitter_guardian
    emitter_reliquary engineering_bench fence_book focus_stim frag_grenade fuel_tank_extension funerary_urn
    fusion_cutter grave_silk grip_ridged grip_staff grip_wrapped heartstone heavy_repeater hoard_plinth
    hollowhorn_hide hollowhorn_horn hollowhorn_spawn_egg holocron_pedestal holocron_shard hyperdrive_motivator
    hyperfuel_cell imperial_commendation imperial_panel jedi_boots jedi_holocron jedi_hood jedi_knight_robe
    jedi_robe jedi_trousers jet_pack kaast_chest kaast_helmet kaast_longshot kaast_pattern kolto_injector
    kyber_cluster kyber_crystal landing_beacon lens_honed lens_resonant lens_steady lightsaber lightsaber_staff
    lightsaber_workbench lumen_moss lumen_thread mandalorian_boots mandalorian_chest mandalorian_helmet
    mandalorian_hunter_spawn_egg mandalorian_legs marker_beacon masterwork_fragment meditation_stone medpac
    mod_attunement mod_conduit mod_cooling_cell mod_focused_barrel mod_heavy_barrel mod_keen_edge
    mod_overcharged_cell mod_plating mod_shock_edge mod_stabiliser mod_weave nav_computer pad_terminal
    passenger_seat poacher_captain_spawn_egg poachers_ledger pommel_archivist pommel_balanced pommel_weighted
    power_core pull_anchor puzzle_seal raw_hollowhorn reinforced_plating reliquary_heart reliquary_seal
    repair_kit republic_commendation republic_panel republic_trooper_boots republic_trooper_chestplate
    republic_trooper_helmet republic_trooper_leggings republic_trooper_spawn_egg resonance_pylon resonant_seal
    ruin_sentinel_spawn_egg ruin_stone ruin_stone_carved ruin_stone_cracked ruin_stone_mossy scanner schematic
    sealed_contract sealed_plating security_droid_spawn_egg silverleaf singing_crystal sith_acolyte_spawn_egg
    sith_boots sith_holocron sith_hood sith_lord_robe sith_robe sith_trooper_boots sith_trooper_chestplate
    sith_trooper_helmet sith_trooper_leggings sith_trooper_spawn_egg sith_trousers sith_war_boots sith_war_chest
    sith_war_helmet sith_war_legs spacer_boots spacer_cap spacer_jacket spacer_trousers step_stone stun_grenade
    tomb_relic tomb_stone tomb_stone_carved tomb_stone_cracked tomb_vent training_saber training_saber_red
    unstable_crystal_cluster urn_keeper veiled_stone vibroblade vibrosword wanderers_journal watch_battle_boots
    watch_battle_chest watch_battle_helmet watch_battle_legs wayfarer_holocron waystone waystone_corrupted
    wreck_conduit wreck_plating wreck_plating_scorched wreck_salvage
"""))

# assets/oldrepublic/blockstates/<id>.json
BASELINE_BLOCKS = set(ids("""
    ashroot aurelite_ore bounty_terminal budding_duskglass camp_gate collapsed_rubble contract_terminal
    counterweight_stone crystal_veined_stone deepslate_aurelite_ore duskglass_cluster echo_cairn echo_urn
    engineering_bench funerary_urn heartstone hoard_plinth holocron_pedestal hub_spawner imperial_panel
    kyber_cluster landing_beacon lightsaber_workbench lumen_moss marker_beacon meditation_stone pad_terminal
    pull_anchor puzzle_seal reliquary_heart reliquary_seal republic_panel resonance_pylon resonant_seal
    ruin_stone ruin_stone_carved ruin_stone_cracked ruin_stone_mossy sealed_plating silverleaf site_spawner
    step_stone tomb_stone tomb_stone_carved tomb_stone_cracked tomb_vent unstable_crystal_cluster urn_keeper
    veiled_stone waystone waystone_corrupted wreck_conduit wreck_plating wreck_plating_scorched wreck_salvage
"""))

# node ids of data/oldrepublic/skill_tree/*.json; their skill.oldrepublic.node.<id>.{name,desc} keys were exactly these
BASELINE_SKILL_NODES = set(ids("""
    archivists_hunger archivists_road archivists_ward barrier_return barrier_shelter calm_focus centered_mind
    choke_crush choke_dread crackling_grasp cruel_focus deep_reserves deflection_control dread_presence
    force_barrier force_choke force_heal force_jump force_lift force_lightning force_pull force_push force_sense
    force_speed form_ataru form_soresu fury guardians_patience guardians_resolve hardy heal_serene_ward
    heal_shared_light hungering_dark jump_crushing_descent jump_soaring_leap knights_vigil lens_mastery
    lift_hurl lift_learned_jedi lift_learned_sith lift_suspend lightning_chain lightning_consuming
    lords_dominion mending_breath pathfinders_poise pull_disarm pull_reel push_deflecting_pulse
    push_focused_blast push_repulse_wave quickened_flow quiet_step relic_lore riposte saber_lunge saber_throw
    scavenger sense_danger sense_seeker speed_burst speed_long_road speed_wayfarer staff_whirl steady_guard
    throw_rending_arc throw_ricochet tomb_hunger trailwise unbound watchers_insight
"""))

# payload types: new Type<>(OldRepublicMod.id("<id>"))
BASELINE_PAYLOADS = set(ids("""
    ability_info ability_input ability_state assign_slot bench_action dialogue_choice dialogue_close
    dialogue_end dialogue_node effect_event flight_phase jet_burst learn_node mission_action mission_board
    open_skills respec_confirm saber_action schematic_sync sense_reveal set_form ship_action ship_status
    site_presence skill_trees toggle_saber tracker_hint vendor_buy vendor_stock weapon_action
"""))

# effect events with a client handler (ClientEffects.register)
BASELINE_EFFECT_EVENTS = set(ids("""
    acolyte_shove acolyte_shove_clash alignment_shift anchor archive_choice armour_set awakening_burst
    awakening_dream beast_tell bench_craft blaster_fire blaster_heat bolt_impact boss_echo boss_grip boss_hurl
    boss_lash boss_leash boss_lunge boss_palm boss_phase boss_spent boss_surge boss_ward boss_yield cairn_touch
    calling captain_heavy cave_ambience collector_resolve consumable counterweight crystal_answer
    crystal_discharge crystal_form crystal_unstable_charge droid_tell electrostaff energy_fail flare_call
    force_barrier force_barrier_impact force_choke force_choke_target force_heal force_heal_ally force_heal_ward
    force_jump_gather force_jump_glide force_jump_land force_jump_launch force_jump_slam force_lift
    force_lift_target force_lightning force_lightning_hit force_lightning_target force_pull force_pull_disarm
    force_pull_hit force_push force_push_charge force_push_impact force_push_learned force_push_slam force_sense
    force_speed grenade_burst grenade_fuse grenade_player grenade_return grenade_tell heartstone hoard_open
    holocron_attune holocron_choice holocron_echo holocron_gatekeeper hunter_attack hunter_tell hyperspace
    jet_pack journal_entry kaast kolto_heal landing_beacon lens_fit lesson_complete level_up meditation
    mentor_depart mission_update mod_fit node_learned npc_deflect npc_guard_break npc_windup pedestal_ready
    player_blaster_fire puzzle_solved pylon_state rank_ceremony rank_up repeater_spin reputation_change
    resonance_up respec rubble_crumble saber_clash saber_combo saber_deflect saber_form saber_guard_break
    saber_heavy saber_hit saber_lunge saber_parry saber_staff saber_throw saber_throw_catch salvage
    scanner_pulse seal_open seal_refuse seeker_scan sentinel_core sentinel_death sentinel_lance sentinel_slam
    sentinel_wake settler_freed ship_damage ship_engine ship_jump ship_repair site_discovered spar step_stone
    terminal_use tomb_vent trial_begin trial_complete trial_echo_dissolve trial_echo_form trial_echo_lunge
    trial_echo_push trooper_bark trooper_charge trooper_rally trooper_salute trooper_tend trooper_vent urn_echo
    urn_wrong veil_open veil_reveal vibro_hit waystone workbench_assemble yield
"""))

# data/<namespace>/<folder>/<id>.json (.nbt for templates), for the folders section 18 adds to
BASELINE_DATA = {
    "c/tags/entity_type": set(ids("bosses")),
    "oldrepublic/damage_type": set(ids("""
        archive_debris archive_grip blaster camp_mine collector_surge crystal_discharge duskglass_cut
        force_choke force_impact force_lightning frag_grenade jet_slam mandalorian_rocket repelling_palm
        resonant_lash thrown_saber tomb_miasma urn_dust wrist_flamer
    """)),
    "oldrepublic/dimension": set(ids("selvaren varkhast")),
    "oldrepublic/dimension_type": set(ids("selvaren varkhast")),
    "oldrepublic/loot_table": set(ids("""
        blocks/ashroot blocks/aurelite_ore blocks/budding_duskglass blocks/contract_terminal
        blocks/crystal_veined_stone blocks/deepslate_aurelite_ore blocks/duskglass_cluster
        blocks/engineering_bench blocks/funerary_urn blocks/imperial_panel blocks/kyber_cluster
        blocks/landing_beacon blocks/lightsaber_workbench blocks/lumen_moss blocks/marker_beacon
        blocks/meditation_stone blocks/republic_panel blocks/ruin_stone blocks/ruin_stone_carved
        blocks/ruin_stone_cracked blocks/ruin_stone_mossy blocks/silverleaf blocks/tomb_stone
        blocks/tomb_stone_carved blocks/tomb_stone_cracked blocks/wreck_conduit blocks/wreck_plating
        blocks/wreck_plating_scorched blocks/wreck_salvage chests/crystal_cave_hidden chests/force_cache
        chests/greater_tomb chests/jedi_ruin chests/kaast_stash chests/sith_tomb chests/wayfarer_cache_m4
        chests/wreck_field entities/ash_hound entities/camp_mine entities/crystal_poacher
        entities/hollowhorn entities/hunter_net entities/hunter_rocket entities/kaast_droid
        entities/mandalorian_hunter entities/poacher_captain entities/republic_trooper
        entities/ruin_sentinel entities/security_droid entities/seeker_droid entities/sith_acolyte
        entities/sith_trooper entities/torvin_kaast gameplay/captive_payment gameplay/echo_cache
        gameplay/frontier_contract gameplay/planet_contract gameplay/r1_supply gameplay/settler_thanks
        gameplay/wayfarer_cache
    """)),
    "oldrepublic/mission": set(ids("""
        c4_both_doors c4_greenwatch c4_heartstone c4_off_the_books c4_proving c4_sepulchre c4_tessalys_road
        clear_camp i1_names i2_border_incident patrol_intercept r1_silent_patrol r2_credits_from_the_core
        secure_site selvaren_cleanse selvaren_cull selvaren_escort selvaren_recover selvaren_spar
        selvaren_survey survey tribute varkhast_cleanse varkhast_cull varkhast_escort varkhast_recover
        varkhast_spar varkhast_survey watch_duty
    """)),
    "oldrepublic/schematic": set(ids("""
        alloy_plate alloy_plate_duskglass blaster_pistol blaster_rifle cargo_bay_extension combat_stim
        credit_bundle electrostaff focus_stim frag_grenade fuel_tank_extension fusion_cutter
        fusion_cutter_masterwork grip_staff heavy_repeater heavy_repeater_masterwork hyperfuel_cell jet_pack
        kolto_injector landing_beacon lens_honed lens_honed_masterwork lens_resonant lens_steady
        mandalorian_boots mandalorian_chest mandalorian_chest_masterwork mandalorian_helmet mandalorian_legs
        medpac mod_attunement mod_conduit mod_cooling_cell mod_focused_barrel mod_heavy_barrel mod_keen_edge
        mod_overcharged_cell mod_plating mod_shock_edge mod_stabiliser mod_weave nav_computer
        nav_computer_holocron passenger_seat power_core power_core_aurelite power_core_singing
        reinforced_plating repair_kit scanner sith_war_boots sith_war_chest sith_war_helmet sith_war_legs
        spacer_boots spacer_cap spacer_jacket spacer_trousers stun_grenade vibroblade vibrosword
        watch_battle_boots watch_battle_chest watch_battle_helmet watch_battle_legs
    """)),
    "oldrepublic/structure": set(ids("""
        boss/hunting_camp boss/reliquary_hall boss/reliquary_surface crystal_cave/camp crystal_cave/descent
        crystal_cave/heart expedition/duskglass_rift_rift expedition/duskglass_rift_surface
        expedition/singing_hollow_grotto expedition/singing_hollow_surface greater_tomb/descent
        greater_tomb/gate greater_tomb/sealed_crypt greater_tomb/urn_vault hub/greenwatch hub/proving
        hub/sepulchre jedi_ruin/anchor_cache jedi_ruin/courtyard jedi_ruin/listening_hall
        jedi_ruin/pilgrim_niche jedi_ruin/stair mission/ambush_rocks mission/fence_camp
        mission/patrol_ambush mission/raider_camp_small mission/skirmish_line mission/smuggler_cache
        mission/survey_post outpost/imperial_post outpost/republic_post planet/heartstone
        planet/wayfarer_cache planet/waystone planet/waystone_corrupted ship/beacon_pad ship/cradle
        site/echo_shrine_jedi site/echo_shrine_sith site/echo_shrine_wayfarer sith_tomb/burial_chamber
        sith_tomb/dig_camp sith_tomb/gate sith_tomb/path sith_tomb/pilgrim_niche sith_tomb/processional
        sith_tomb/rubble_room story/couriers_fall story/jedi_camp story/raider_camp story/sith_camp
        wreck_field/hull wreck_field/path wreck_field/salvage_camp wreck_field/scar
    """)),
    "oldrepublic/tags/entity_type": set(ids("choke_immune droids push_resistant")),
    "oldrepublic/tags/worldgen/biome": set(ids("""
        has_structure/crystal_cave has_structure/greater_tomb has_structure/imperial_outpost
        has_structure/jedi_ruin has_structure/republic_outpost has_structure/sith_tomb
        has_structure/waystone has_structure/wreck_field is_planet is_selvaren is_varkhast
    """)),
    "oldrepublic/tags/worldgen/structure": set(ids("""
        crystal_caves greater_tombs imperial_outposts jedi_ruins republic_outposts sith_tombs waystones
        wreck_fields
    """)),
    "oldrepublic/vendor": set(ids("""
        imperial_quartermaster requisition_imperial requisition_republic spacer trainer_armourcraft
        trainer_armstech trainer_artifice trainer_medtech watch_quartermaster
    """)),
    "oldrepublic/worldgen/biome": set(ids("""
        selvaren_highwood selvaren_meadow selvaren_tarn varkhast_ashfall varkhast_redrock
        varkhast_tomb_valley
    """)),
    "oldrepublic/worldgen/configured_feature": set(ids("""
        ashroot_patch aurelite_cliff basalt_spire carved_wall dead_tree duskglass_wall highwood_floor hoodoo
        meadow_stones obelisk ore_aurelite silver_tree silver_tree_young silverleaf_patch tarn_shore
    """)),
    "oldrepublic/worldgen/noise_settings": set(ids("selvaren varkhast")),
    "oldrepublic/worldgen/placed_feature": set(ids("""
        ashroot_patch aurelite_cliff basalt_spire carved_wall dead_tree duskglass_wall highwood_floor
        highwood_saplings highwood_spruce highwood_trees hoodoo meadow_stones meadow_trees obelisk
        ore_aurelite silverleaf_patch tarn_shore
    """)),
    "oldrepublic/worldgen/structure": set(ids("""
        crystal_cave greater_tomb imperial_outpost jedi_ruin republic_outpost sith_tomb waystone wreck_field
    """)),
    "oldrepublic/worldgen/structure_set": set(ids("crystal_caves greater_tombs outposts ruins waystones")),
}

# lang keys of these families (prefix + name), over every lang part
BASELINE_LANG = {family + name for family, names in {
    "item.oldrepublic.": """
        alloy_plate archive_record ash_hound_fang ash_hound_spawn_egg aurelite_shard blaster_pistol
        blaster_rifle cargo_bay_extension combat_stim cooked_hollowhorn credit_bundle credit_chit
        crystal_poacher_spawn_egg droid_actuator durasteel_plate duskglass_shard electrostaff
        emitter_duelist emitter_duelist.desc emitter_guardian emitter_guardian.desc emitter_reliquary
        emitter_reliquary.desc fence_book focus_stim frag_grenade fuel_tank_extension fusion_cutter
        grave_silk grip_ridged grip_ridged.desc grip_staff grip_staff.desc grip_wrapped grip_wrapped.desc
        heavy_repeater hollowhorn_hide hollowhorn_horn hollowhorn_spawn_egg holocron_shard
        hyperdrive_motivator hyperfuel_cell imperial_commendation jedi_boots jedi_holocron jedi_hood
        jedi_knight_robe jedi_robe jedi_trousers jet_pack kaast_chest kaast_helmet kaast_longshot
        kaast_pattern kaast_pattern.desc kolto_injector kyber_crystal kyber_crystal.bound kyber_crystal.hue
        kyber_crystal.owner kyber_crystal.unattuned lens.fit lens.honed.effect lens.resonant.effect
        lens.steady.effect lens_honed lens_resonant lens_steady lightsaber lightsaber.color
        lightsaber.ignited lightsaber.lens lightsaber.owner lightsaber.parts lightsaber.retracted
        lightsaber.thrown lightsaber_staff lightsaber_staff.both lightsaber_staff.parts
        lightsaber_staff.single lumen_thread mandalorian_boots mandalorian_chest mandalorian_helmet
        mandalorian_hunter_spawn_egg mandalorian_legs masterwork_fragment medpac mod_attunement mod_conduit
        mod_cooling_cell mod_focused_barrel mod_heavy_barrel mod_keen_edge mod_overcharged_cell mod_plating
        mod_shock_edge mod_stabiliser mod_weave nav_computer passenger_seat poacher_captain_spawn_egg
        poachers_ledger poachers_ledger.page1 poachers_ledger.page2 poachers_ledger.page3
        poachers_ledger.page4 pommel_archivist pommel_archivist.desc pommel_balanced pommel_balanced.desc
        pommel_weighted pommel_weighted.desc power_core raw_hollowhorn reinforced_plating repair_kit
        republic_commendation republic_trooper_boots republic_trooper_chestplate republic_trooper_helmet
        republic_trooper_leggings republic_trooper_spawn_egg ruin_sentinel_spawn_egg scanner schematic
        schematic.discipline schematic.of sealed_contract sealed_contract.desc security_droid_spawn_egg
        singing_crystal sith_acolyte_spawn_egg sith_boots sith_holocron sith_hood sith_lord_robe sith_robe
        sith_trooper_boots sith_trooper_chestplate sith_trooper_helmet sith_trooper_leggings
        sith_trooper_spawn_egg sith_trousers sith_war_boots sith_war_chest sith_war_helmet sith_war_legs
        spacer_boots spacer_cap spacer_jacket spacer_trousers stun_grenade tomb_relic tomb_relic.desc
        training_saber training_saber.desc training_saber.safe training_saber_red vibroblade vibrosword
        wanderers_journal watch_battle_boots watch_battle_chest watch_battle_helmet watch_battle_legs
        wayfarer_holocron
    """,
    "block.oldrepublic.": """
        ashroot aurelite_ore banner.republic_chevron.black banner.republic_chevron.blue
        banner.republic_chevron.brown banner.republic_chevron.cyan banner.republic_chevron.gray
        banner.republic_chevron.green banner.republic_chevron.light_blue banner.republic_chevron.light_gray
        banner.republic_chevron.lime banner.republic_chevron.magenta banner.republic_chevron.orange
        banner.republic_chevron.pink banner.republic_chevron.purple banner.republic_chevron.red
        banner.republic_chevron.white banner.republic_chevron.yellow bounty_terminal budding_duskglass
        camp_gate collapsed_rubble contract_terminal counterweight_stone crystal_veined_stone
        deepslate_aurelite_ore duskglass_cluster echo_cairn echo_urn engineering_bench funerary_urn
        heartstone hoard_plinth holocron_pedestal hub_spawner imperial_panel kyber_cluster landing_beacon
        lightsaber_workbench lumen_moss marker_beacon meditation_stone pad_terminal pull_anchor puzzle_seal
        reliquary_heart reliquary_seal republic_panel resonance_pylon resonant_seal ruin_stone
        ruin_stone_carved ruin_stone_cracked ruin_stone_mossy sealed_plating silverleaf site_spawner
        step_stone tomb_stone tomb_stone_carved tomb_stone_cracked tomb_vent unstable_crystal_cluster
        urn_keeper veiled_stone waystone waystone_corrupted wreck_conduit wreck_plating
        wreck_plating_scorched wreck_salvage
    """,
    "entity.oldrepublic.": """
        acolyte_hub apprentice archive_debris ash_hound blaster_bolt camp_mine courier_shuttle
        crystal_poacher frag_grenade frontier_settler hollowhorn hub_host hunter_net hunter_rocket
        jedi_mentor kaast_droid mandalorian_hunter orren_vasch poacher_captain proving_guard pylon_focus
        republic_trooper ruin_sentinel security_droid seeker_droid shadow_echo sith_acolyte sith_mentor
        sith_trooper stun_grenade thrown_saber torvin_kaast vendor
    """,
    "effect.oldrepublic.": "",  # none before M5
    "ability.oldrepublic.": """
        force_barrier force_choke force_choke.no_breath force_choke.no_target force_choke.refused
        force_choke.shift_reason force_heal.not_still force_jump.not_grounded force_pull.no_target
        force_speed force_speed.cannot_run
    """,
    "key.oldrepublic.": """
        force_screen force_sense force_slot_2 force_slot_3 force_slot_4 journal saber_form toggle_saber
        use_force_power weapon_action
    """,
    "biome.oldrepublic.": """
        selvaren_highwood selvaren_meadow selvaren_tarn varkhast_ashfall varkhast_redrock
        varkhast_tomb_valley
    """,
    "dimension.oldrepublic.": "home selvaren varkhast",
    "death.attack.oldrepublic.": """
        archive_debris archive_debris.item archive_debris.player archive_grip archive_grip.item
        archive_grip.player blaster blaster.item blaster.player camp_mine camp_mine.item camp_mine.player
        collector_surge collector_surge.item collector_surge.player crystal_discharge
        crystal_discharge.player duskglass_cut duskglass_cut.player force_choke force_choke.item
        force_choke.player force_impact force_impact.player force_lightning force_lightning.player
        frag_grenade frag_grenade.item frag_grenade.player jet_slam jet_slam.item jet_slam.player
        mandalorian_rocket mandalorian_rocket.item mandalorian_rocket.player repelling_palm
        repelling_palm.item repelling_palm.player resonant_lash resonant_lash.item resonant_lash.player
        thrown_saber thrown_saber.item thrown_saber.player tomb_miasma tomb_miasma.player urn_dust
        urn_dust.player wrist_flamer wrist_flamer.item wrist_flamer.player
    """,
    "destination.oldrepublic.": """
        greenwatch greenwatch.desc greenwatch.pad home home.desc home.pad hunting_camp hunting_camp.desc
        hunting_camp.pad proving proving.desc proving.pad unknown
    """,
    "faction.oldrepublic.": """
        frontier jedi_order republic sith_empire tier.distrusted tier.honoured tier.hostile tier.neutral
        tier.trusted toast toast.tier
    """,
    "title.oldrepublic.": "keeper_of_echoes keeper_of_echoes.desc pathfinder pathfinder.desc",
    "quality.oldrepublic.": "fine masterwork standard",
    "rank.oldrepublic.": """
        banner.title independent.0 jedi.0 jedi.1 jedi.2 jedi.3 jedi.4 none.0 sith.0 sith.1 sith.2 sith.3
        sith.4
    """,
    "journal.oldrepublic.chapter_header.": "1 2 3 4",
    "quest.oldrepublic.c2.banner.": """
        boss boss.independent boss.jedi boss.none boss.sith chapter chapter.independent chapter.jedi
        chapter.none chapter.sith chapter3 chapter3.independent chapter3.jedi chapter3.none chapter3.sith
        chapter4 chapter4.independent chapter4.jedi chapter4.none chapter4.sith lesson_one
        lesson_one.independent lesson_one.jedi lesson_one.none lesson_one.sith lesson_two
        lesson_two.independent lesson_two.jedi lesson_two.none lesson_two.sith masterwork
        masterwork.independent masterwork.jedi masterwork.none masterwork.sith mission mission.independent
        mission.jedi mission.none mission.sith planet.home planet.home.sub planet.selvaren
        planet.selvaren.sub planet.varkhast planet.varkhast.sub rank3 rank3.independent rank3.jedi
        rank3.none rank3.sith reckoning reckoning.independent reckoning.jedi reckoning.none reckoning.sith
    """,
}.items() for name in names.split()}

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


def java_texts():
    """{path: text} of the main and client sources."""
    return {f: f.read_text(encoding="utf-8") for d in JAVA_DIRS for f in sorted(d.rglob("*.java"))}


ITEM_CALL = re.compile(r'\bitem\(\s*"([a-z0-9_]+)"')
BLOCK_CALL = re.compile(r'\bblock(?:Only)?\(\s*"([a-z0-9_]+)"')
PARTICLE_HELPER = re.compile(r'\bregister\(\s*"([a-z0-9_./]+)"')
PARTICLE_DIRECT = re.compile(r'Registry\.register\(\s*BuiltInRegistries\.PARTICLE_TYPE\s*,\s*'
                             r'(?:OldRepublicMod\.)?id\(\s*"([a-z0-9_./]+)"')
ENTITY_KEY = re.compile(r'(?:ResourceKey\.create|Registry\.register)\(\s*(?:BuiltIn)?Registries\.ENTITY_TYPE\s*,\s*'
                        r'(?:OldRepublicMod\.)?id\(\s*"([a-z0-9_]+)"')
ENTITY_HELPER = re.compile(r'\b(?:register|key)\(\s*"([a-z0-9_]+)"')
PAYLOAD_TYPE = re.compile(r'\bType<[^>]*>\(\s*OldRepublicMod\.id\(\s*"([a-z0-9_./]+)"\s*\)\s*\)')
ID_CONSTANT = re.compile(r'\bIdentifier\s+([A-Z][A-Z0-9_]*)\s*=\s*OldRepublicMod\.id\(\s*"([a-z0-9_./]+)"\s*\)')
EFFECT_HANDLER = re.compile(r'\bClientEffects\.register\(\s*(?:OldRepublicMod\.id\(\s*"([a-z0-9_./]+)"\s*\)'
                            r'|(?:([A-Z]\w*)\.)?([A-Z][A-Z0-9_]*)\b)')


class Sources:
    """Registration counts in the main and client sources, by id. Only literal ids are counted."""

    def __init__(self):
        texts = java_texts()
        self.items, self.blocks, self.particles = Counter(), Counter(), Counter()
        self.entities, self.payloads, self.handlers = Counter(), Counter(), Counter()
        self.item_files = {}
        constants = {}
        for f, text in texts.items():
            for m in ITEM_CALL.finditer(text):
                self.items[m.group(1)] += 1
                self.item_files.setdefault(m.group(1), []).append(f.name)
            for m in BLOCK_CALL.finditer(text):
                self.blocks[m.group(1)] += 1
            for m in PARTICLE_DIRECT.finditer(text):
                self.particles[m.group(1)] += 1
            for m in ENTITY_KEY.finditer(text):
                self.entities[m.group(1)] += 1
            for m in PAYLOAD_TYPE.finditer(text):
                self.payloads[m.group(1)] += 1
            for m in ID_CONSTANT.finditer(text):
                constants.setdefault(m.group(1), {})[f.stem] = m.group(2)
            for line in text.splitlines():
                # register("<name>") on a particle-type line: the pattern every *Particles class uses (so an item or
                # block of the same name is not mistaken for a particle)
                if "ParticleType" in line:
                    for m in PARTICLE_HELPER.finditer(line):
                        self.particles[m.group(1)] += 1
                # register("<name>", ...) or key("<name>") on an entity-type line (HubEntities, HunterEntities)
                if "EntityType" in line:
                    for m in ENTITY_HELPER.finditer(line):
                        self.entities[m.group(1)] += 1
        # ClientEffects.register(<Class>.<CONSTANT>, ...): the constant resolves to its OldRepublicMod.id("...")
        for f, text in texts.items():
            for m in EFFECT_HANDLER.finditer(text):
                literal, owner, name = m.groups()
                if literal:
                    self.handlers[literal] += 1
                    continue
                by_class = constants.get(name, {})
                if owner in by_class:
                    self.handlers[by_class[owner]] += 1
                elif owner is None and f.stem in by_class:
                    self.handlers[by_class[f.stem]] += 1
                elif owner is None and len(set(by_class.values())) == 1:
                    self.handlers[next(iter(by_class.values()))] += 1


def skill_nodes():
    """Counter of node ids over every skill tree."""
    out = Counter()
    for f in sorted((DATA / "oldrepublic/skill_tree").glob("*.json")):
        for node in (load_json(f) or {}).get("nodes", []):
            out[node.get("id")] += 1
    return out


def data_file(folder, name):
    return DATA / folder / f"{name}{DATA_EXT.get(folder, '.json')}"


def lang_hits(key, keys):
    """The keys of keys that are key itself or below it (key + '.')."""
    return sorted(k for k in keys if k == key or k.startswith(key + "."))


def new_lang_keys():
    """{lang key: what it names} for the names of the new ids and the 18.5 keys."""
    out = {}
    for kind, values in (("item", NEW_ITEMS), ("block", NEW_BLOCKS), ("entity", NEW_ENTITIES),
                         ("effect", NEW_EFFECTS), ("ability", NEW_POWERS), ("key", NEW_KEYS),
                         ("biome", NEW_DATA["oldrepublic/worldgen/biome"]),
                         ("dimension", NEW_DATA["oldrepublic/dimension"])):
        for v in values:
            out[f"{kind}.oldrepublic.{v}"] = f"new {kind} {v}"
    for d in NEW_DATA["oldrepublic/damage_type"]:
        out[f"death.attack.oldrepublic.{d}"] = f"new damage type {d}"
    for k in NEW_LANG:
        out[k] = "a new 18.5 key"
    return out


def check():
    problems = []
    new_sounds = [s for group in NEW_SOUNDS.values() for s in group]
    lang = lang_parts()
    src = Sources()
    nodes = skill_nodes()

    # --- section 18 itself must be consistent
    for label, values in (("sound", new_sounds + REUSED_SOUNDS),
                          ("item or block", NEW_ITEMS + NEW_BLOCKS),
                          ("entity", NEW_ENTITIES + REUSED_ENTITIES),
                          ("particle", NEW_PARTICLES + REUSED_PARTICLES),
                          ("skill node", NEW_SKILL_NODES), ("payload", NEW_PAYLOADS + REUSED_PAYLOADS),
                          ("effect event", NEW_EFFECT_EVENTS + REUSED_EFFECT_EVENTS), ("lang key", NEW_LANG)):
        dupes = sorted({v for v in values if values.count(v) > 1})
        if dupes:
            problems.append(f"{label} ids listed twice (or as both new and reused) in this script: {dupes}")
    for folder in sorted(set(NEW_DATA) | set(REUSED_DATA)):
        values = NEW_DATA.get(folder, []) + REUSED_DATA.get(folder, [])
        dupes = sorted({v for v in values if values.count(v) > 1})
        if dupes:
            problems.append(f"data ids listed twice (or as both new and reused) under {folder}: {dupes}")

    # --- new ids must not have existed before Milestone 5
    def existed(values, baseline, what):
        for v in values:
            if v in baseline:
                problems.append(f"new {what} {v} already existed before M5")

    existed(new_sounds, BASELINE_SOUNDS, "sound")
    existed(NEW_PARTICLES, BASELINE_PARTICLES, "particle")
    existed(NEW_ITEMS + NEW_BLOCKS, BASELINE_ITEMS, "item (assets/oldrepublic/items/)")
    existed(NEW_BLOCKS, BASELINE_BLOCKS, "block (assets/oldrepublic/blockstates/)")
    # before M5 the entity names were exactly the registered entity types
    existed(NEW_ENTITIES, {k.split(".", 2)[2] for k in BASELINE_LANG if k.startswith("entity.oldrepublic.")}, "entity")
    existed(NEW_SKILL_NODES, BASELINE_SKILL_NODES, "skill node")
    existed(NEW_PAYLOADS, BASELINE_PAYLOADS, "payload")
    existed(NEW_EFFECT_EVENTS, BASELINE_EFFECT_EVENTS, "effect event")
    for folder, values in NEW_DATA.items():
        existed([f"{folder}/{v}" for v in values], {f"{folder}/{v}" for v in BASELINE_DATA.get(folder, set())},
                "data file")
    for key, what in sorted(new_lang_keys().items()):
        hits = lang_hits(key, BASELINE_LANG)
        if hits:
            problems.append(f"lang key {key} ({what}) already existed before M5: {hits}")
    older = {"the M1 catalogue": m1_sound_ids()}
    older.update({f"events_m{m}.json": set(listed_ids(SOUND_LISTS / f"events_m{m}.json")) for m in (2, 3, 4)})
    for s in new_sounds:
        for where, declared in older.items():
            if s in declared:
                problems.append(f"new sound {s} is declared by {where}")

    # --- reused ids must exist, and be declared only once
    sounds = load_json(SOUNDS_JSON) or {}
    m5_ids = listed_ids(EVENTS_M5)
    java = MOD_SOUNDS.read_text(encoding="utf-8") if MOD_SOUNDS.exists() else ""
    for s in REUSED_SOUNDS:
        if s not in BASELINE_SOUNDS:
            problems.append(f"reused sound {s} is not in the pre-M5 baseline")
        if s not in sounds:
            problems.append(f"reused sound {s} is missing from sounds.json")
        if not any(s in declared for declared in older.values()):
            problems.append(f"reused sound {s} is no longer declared by the M1 catalogue or events_m2.json to "
                            f"events_m4.json")
        if s in m5_ids:
            problems.append(f"reused sound {s} must not be listed in events_m5.json")
        if f"subtitles.oldrepublic.{s}" not in lang:
            problems.append(f"reused sound {s} lost its subtitle")
        if java.count(f'register("{s}")') != 1:
            problems.append(f"reused sound {s} must keep exactly one ModSounds constant")
    for p in REUSED_PARTICLES:
        if p not in BASELINE_PARTICLES:
            problems.append(f"reused particle {p} is not in the pre-M5 baseline")
        if not (ASSETS / "particles" / f"{p}.json").exists():
            problems.append(f"reused particle {p} is missing from assets/oldrepublic/particles/")
        if src.particles[p] != 1:
            problems.append(f"reused particle {p} must be registered exactly once (found {src.particles[p]})")
    for e in REUSED_ENTITIES:
        key = f"entity.oldrepublic.{e}"
        if key not in BASELINE_LANG:
            problems.append(f"reused entity {e} is not in the pre-M5 baseline")
        if len(lang.get(key, [])) != 1:
            problems.append(f"reused entity {e}: {key} must be defined in exactly one lang part "
                            f"(found {lang.get(key, [])})")
        if src.entities[e] != 1:
            problems.append(f"reused entity {e} must be registered exactly once (found {src.entities[e]})")
    for p in REUSED_PAYLOADS:
        if p not in BASELINE_PAYLOADS:
            problems.append(f"reused payload {p} is not in the pre-M5 baseline")
        if src.payloads[p] != 1:
            problems.append(f"reused payload {p} must be declared exactly once (found {src.payloads[p]})")
    for e in REUSED_EFFECT_EVENTS:
        if e not in BASELINE_EFFECT_EVENTS:
            problems.append(f"reused effect event {e} is not in the pre-M5 baseline")
        if src.handlers[e] != 1:
            problems.append(f"reused effect event {e} must keep exactly one client handler "
                            f"(ClientEffects.register found {src.handlers[e]} times)")
    for folder, values in REUSED_DATA.items():
        for v in values:
            if v not in BASELINE_DATA.get(folder, set()):
                problems.append(f"reused data file {folder}/{v} is not in the pre-M5 baseline")
            if not data_file(folder, v).exists():
                problems.append(f"reused data file {data_file(folder, v).relative_to(ROOT)} is missing")
    for key, part in REUSED_LANG.items():
        if key not in BASELINE_LANG:
            problems.append(f"reused lang key {key} is not in the pre-M5 baseline")
        if lang.get(key) != [part]:
            problems.append(f"reused lang key {key} must be defined in {part} only (found {lang.get(key, [])})")
    for prefix, part in REUSED_LANG_PREFIXES.items():
        old = sorted(k for k in BASELINE_LANG if k.startswith(prefix))
        if not old:
            problems.append(f"reused lang prefix {prefix} has no key in the pre-M5 baseline")
        for key in old:
            if lang.get(key) != [part]:
                problems.append(f"reused lang key {key} must stay in {part} only (found {lang.get(key, [])})")

    # --- the plain items of registry/M5Items (17.1)
    for i in M5_ITEMS:
        if src.items[i] != 1 or src.item_files.get(i) != [M5_ITEMS_JAVA.name]:
            problems.append(f'{i} must be registered exactly once, in registry/M5Items '
                            f'(item("{i}", ...) found in {src.item_files.get(i, [])})')

    # --- new ids: registered at most once
    for label, values, counts in (("particle", NEW_PARTICLES, src.particles),
                                  ("item", NEW_ITEMS, src.items), ("block", NEW_BLOCKS, src.blocks),
                                  ("entity", NEW_ENTITIES, src.entities), ("payload", NEW_PAYLOADS, src.payloads),
                                  ("effect event client handler", NEW_EFFECT_EVENTS, src.handlers),
                                  ("skill node", NEW_SKILL_NODES, nodes)):
        for v in values:
            if counts[v] > 1:
                problems.append(f"new {label} {v} is registered {counts[v]} times")

    # --- events_m5.json lists exactly the new sound ids, under their owners' modules
    m5_events = load_json(EVENTS_M5)
    if m5_events is None:
        problems.append(f"{EVENTS_M5.relative_to(ROOT)} is missing")
    else:
        if len(m5_ids) != len(set(m5_ids)):
            problems.append("events_m5.json lists an id twice")
        expected = {s: feature for feature, group in NEW_SOUNDS.items() for s in group}
        for s in sorted(set(expected) - set(m5_ids)):
            problems.append(f"new sound {s} is missing from events_m5.json")
        for s in sorted(set(m5_ids) - set(expected)):
            problems.append(f"events_m5.json lists {s}, which section 18 does not mark new")
        for e in m5_events["events"]:
            if e["id"] in expected and e.get("feature") != expected[e["id"]]:
                problems.append(f"events_m5.json: {e['id']} belongs to feature {expected[e['id']]}, "
                                f"not {e.get('feature')}")

    # --- every new sound is generated: sounds.json entry, subtitle, ModSounds constant
    subtitles = load_json(LANG_DIR / "sounds.json") or {}
    for s in new_sounds:
        if s not in sounds:
            problems.append(f"new sound {s} has no sounds.json entry (run gen_sounds.py --m5 --java)")
        if f"subtitles.oldrepublic.{s}" not in subtitles:
            problems.append(f"new sound {s} has no subtitle in lang-parts/sounds.json")
        if f'register("{s}")' not in java:
            problems.append(f"new sound {s} has no ModSounds constant")
    return problems


def report():
    lang = lang_parts()
    sounds = load_json(SOUNDS_JSON) or {}
    src = Sources()
    nodes = skill_nodes()
    block_items = [b for b in NEW_BLOCKS if b not in BLOCK_ONLY]
    rows = [(f"sounds {feature} (sounds.json)", group, lambda s: s in sounds) for feature, group in NEW_SOUNDS.items()]
    rows += [
        ("particles (particles/<id>.json)", NEW_PARTICLES, lambda p: (ASSETS / "particles" / f"{p}.json").exists()),
        ("items (items/<id>.json)", NEW_ITEMS + block_items, lambda i: (ASSETS / "items" / f"{i}.json").exists()),
        ("blocks (blockstates/<id>.json)", NEW_BLOCKS, lambda b: (ASSETS / "blockstates" / f"{b}.json").exists()),
        ("entities (entity.oldrepublic.<id>)", NEW_ENTITIES, lambda e: f"entity.oldrepublic.{e}" in lang),
        ("status effects (effect.oldrepublic.<id>)", NEW_EFFECTS, lambda e: f"effect.oldrepublic.{e}" in lang),
        ("skill nodes (skill_tree/*.json)", NEW_SKILL_NODES, lambda n: nodes[n] > 0),
        ("keys (key.oldrepublic.<id>)", NEW_KEYS, lambda k: f"key.oldrepublic.{k}" in lang),
        ("payloads (Type<>(id(...)))", NEW_PAYLOADS, lambda p: src.payloads[p] > 0),
        ("effect events (ClientEffects.register)", NEW_EFFECT_EVENTS, lambda e: src.handlers[e] > 0),
    ]
    rows += [(f"data {folder.split('/', 1)[1]}/", values, lambda v, folder=folder: data_file(folder, v).exists())
             for folder, values in NEW_DATA.items()]
    rows.append(("lang keys of 18.5", NEW_LANG, lambda k: k in lang))
    for label, values, present in rows:
        missing = [v for v in values if not present(v)]
        print(f"  {label:44s} {len(values) - len(missing):3d}/{len(values)}")
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
        print(f"check_m5_ids: {p}")
    if problems:
        sys.exit(f"check_m5_ids: {len(problems)} problem(s)")
    print("check_m5_ids: ok")


if __name__ == "__main__":
    main()
