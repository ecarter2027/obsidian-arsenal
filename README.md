# Obsidian Arsenal (Forge 1.20.1)

## Downloads

Every build is in [Releases](../../releases) and in the [`releases/`](releases) folder. Forge 1.20.1; also needs GeckoLib, PlayerAnimator, AAA Particles and Iron's Spells 'n Spellbooks.

Obsidian armor, a two-mode Obsidian Gauntlet with a grappling hook, and three new Iron's Spells schools (**Obsidian**, **Hellfire** and **Plasma**) with twelve spells.

Full feature list: [DESCRIPTION.md](DESCRIPTION.md). Version history: [CHANGELOG.md](CHANGELOG.md).

## Epic Fight add-on (optional)

[`obsidian_ef_compat/`](obsidian_ef_compat) is **Obsidian EF Compat**, a separate jar that gives the gauntlet a full Epic Fight
moveset:
- claw-and-kick combos with purple slash VFX
- right-click guard
- the Obsidian Impaler and Twin Meteor skills
- the Obsidian Stretch dodge
- battle-mode visuals for the armor

Its jar is attached to each release next to the main mod. See [COMPAT_DESCRIPTION.md](COMPAT_DESCRIPTION.md).

Build it with `gradle build` in that folder, after putting these jars in `obsidian_ef_compat/libs`:
- Epic Fight 20.14.x
- GeckoLib
- an Obsidian Arsenal jar

`tools/gen_claw_anims.py` regenerates its combos, guard and idle animations and `ClawMoves.java`.

## Required mods (all four are hard dependencies)

| Mod | Used for |
|---|---|
| **GeckoLib** 4.7+ | All 3D models + animations: gauntlet (stretching attacks), spikes, shards, tentacles, storm spikes, hell columns, gyroscope rings, orb, Indra's arrow |
| **PlayerAnimator** 1.0.2-rc1+ | Gauntlet 7-hit combo body animations (third and first person) and every spell's cast animation (played through Iron's cast system) |
| **Iron's Spells 'n Spellbooks** 3.4+ (plus its deps Curios + Caelus) | Obsidian and Hellfire `SchoolType`s, school spell power and resist attributes, 10 spells, scrolls, mana, cooldowns, Scroll Forge focuses, spell damage types |
| **AAA Particles** 2.x | Effekseer VFX for every spell, effect and gauntlet attack (see `EFFEKS.md`) |

They are `mandatory=true` in `mods.toml`, compiled against in `build.gradle`, and checked again at startup.

## Build

1. In `gradle.properties`, set `irons_file_id` to the CurseForge file id of the Iron's Spells 1.20.1 build you use.
2. Use a Forge 1.20.1 MDK Gradle wrapper (copy `gradlew`, `gradlew.bat`, `gradle/` from the MDK), then run `./gradlew build`.
3. Optional: author the `.efkefc` effects listed in `EFFEKS.md` (until then, the built-in sprite particles render).
4. `tools/gen_assets.py` regenerates all models, animations, textures, lang and data files (`pip install pillow numpy`).

## Content

**Obsidian Armor**: 4/7/9/4 defense, 4 toughness, 0.15 knockback resistance, 50x durability multiplier
(netherite is 3/6/8/3, 3, 0.1, 37x). Each piece also gives +125 max mana, +10% Obsidian and Hellfire spell power,
and +5% Hellfire resistance. Crafted from obsidian in the vanilla armor shapes.

**Obsidian Gauntlet** (`CNC / OOO / ONO`: C = crying obsidian, N = netherite ingot, O = obsidian). Sneak + right-click switches mode.
* *Mining*: pickaxe, axe, shovel and hoe in one. 3500 durability, speed 13, mining level 4 (netherite is 2031 and 9). Right-click strips logs, makes paths and tills soil.
* *Combat*: left-click chains 7 attacks: Jab (punch), Rending Hook (claw), **Piercing Lance** (gauntlet stretches to 7.5 blocks), **Hammer Slam** (smash), Claw Rake (claw), **Seismic Reach** (stretches to 9 blocks, pulls enemies in), **Meteor Smash** (leap and crater).
* *Grappling hook*: the grapple key (default **H**) fires an obsidian claw on a chain (80 blocks); press again to let go. Hooked blocks reel you in, hooked mobs are yanked to you.

**Obsidian school**: Obsidian Spike Explosion, Obsidian Storm, Obsidian Tentacles, Purple Lightning Cloak, Obsidian Shatter,
Indra's Arrow (charged long cast) (Scroll Forge focus: crying obsidian).
**Hellfire school**: Columns of Hell, Flaming Judgement (continuous), Gyroscope, Amaterasu (targeted)
(Scroll Forge focus: fire charge).
**Plasma school**: Plasma Beam (continuous; rounded, tapered 3D beam from the hand that instantly breaks every block along
your line of sight), Black Hole (charged projectile; Interstellar-style lensing shader with a lensed purple accretion disk,
works with Iris/Oculus shader packs; pulls mobs within 20 blocks, rips up terrain, collapses with a shockwave)
(Scroll Forge focus: redstone block).

Scrolls for every spell and level appear in Iron's Scrolls tab, and the max-level scroll of each is also in the Obsidian Arsenal tab.

Warning: Indra's Arrow deletes a 25 block radius sphere, Plasma Beam tunnels through blocks and Black Hole tears up terrain (all only when `mobGriefing` is on).
