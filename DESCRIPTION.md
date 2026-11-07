# Obsidian Arsenal

**Black glass, purple fire, and hellfire.** Obsidian Arsenal is an addon for Iron's Spells 'n Spellbooks. It adds three new schools of magic (**Obsidian**, **Hellfire** and **Plasma**) with twelve spells, a full set of living obsidian armor, and the **Obsidian Gauntlet**, a claw that works as both a tool and a weapon.

Forge 1.20.1.

---

## Obsidian Armor

Sharp, slanted plates of obsidian skin-armor in black, grey and purple. The purple seams glow in the dark, and jagged obsidian spikes jut from the shoulders and limbs.

| | Obsidian | Netherite |
|---|---|---|
| Defense (helmet / chest / legs / boots) | 4 / 7 / 9 / 4 | 3 / 6 / 8 / 3 |
| Toughness | 4 | 3 |
| Knockback resistance | 0.15 | 0.1 |
| Durability multiplier | 50x | 37x |

Each piece also gives **+125 max mana**, **+10% Obsidian and Hellfire spell power** and **+5% Hellfire resistance**. Craft it from obsidian in the vanilla armor shapes.

## Obsidian Gauntlet

A smooth obsidian claw that grows out of your arm, with an opposing thumb and fingers curled like a gripping hand. Its claws flex while you move and snap open when you strike.

**Grappling hook:** press the grapple key (**H** by default) to fire an obsidian claw on a chain up to 80 blocks. Hook a block to reel yourself to it with no fall damage, or hook a mob to yank it to you (bosses pull you to them instead). Press the key again to let go.

Recipe: `CNC / OOO / ONO` (C = crying obsidian, N = netherite ingot, O = obsidian).

**Sneak + right-click** switches between two modes:
- **Mining mode:**
  - A pickaxe, axe, shovel and hoe in one: 3500 durability, mining speed 13, mining level 4.
  - Right-click strips logs, makes paths and tills soil.
- **Combat mode:** a 7-hit combo:
  1. Jab
  2. Rending Hook
  3. **Piercing Lance** (the gauntlet stretches out 7.5 blocks)
  4. **Hammer Slam**
  5. Claw Rake
  6. **Seismic Reach** (stretches 9 blocks and pulls enemies in)
  7. **Meteor Smash** (a leap and a crater)

## Spells

### Obsidian school
*Scroll Forge focus: crying obsidian*
- **Obsidian Spike Explosion:** hurl an obsidian orb. Where it lands, jagged spikes burst from the ground and impale nearby enemies.
- **Obsidian Storm:** a ring of whirling obsidian spikes shreds everything around you.
- **Obsidian Tentacles:** obsidian tentacles claw out of the ground and lash at enemies for several seconds.
- **Purple Lightning Cloak:** wrap yourself in purple lightning for double movement speed and a violet afterimage trail.
- **Obsidian Shatter:** shards of obsidian erupt in a line about six blocks long.
- **Indra's Arrow:** charge an arrow of hellfire and loose it. It wipes out a sphere 25 blocks in radius where it lands.

### Hellfire school
*Scroll Forge focus: fire charge*
- **Columns of Hell:** four swirling pillars of hellfire orbit you and burn everything they pass.
- **Flaming Judgement:** channel a continuous torrent of red hellfire up to ten blocks ahead.
- **Gyroscope:** rings of hellfire spin around you, blocking all damage and every arrow while they last.
- **Amaterasu:** set the creature you're looking at on fire with black flames that burn until it dies.

### Plasma school
*Scroll Forge focus: redstone block*
- **Plasma Beam:** a continuous, tapered beam of red plasma from your hand that burns through enemies and instantly breaks every block in your line of sight.
- **Black Hole:** launch a glowing purple singularity. It bends light around itself with a lensed accretion disk, drags in mobs within 20 blocks, rips up terrain and collapses with a shockwave. The lensing works with Iris/Oculus shader packs.

Scrolls for every spell at every level are in Iron's scroll tab, and the max-level scroll of each spell is also in the Obsidian Arsenal tab.

> **Warning:** Indra's Arrow, Plasma Beam and Black Hole destroy terrain when `mobGriefing` is on.

---

## Epic Fight compatibility (optional add-on: Obsidian EF Compat)

Install **Obsidian EF Compat** next to Epic Fight to give the gauntlet a full Epic Fight moveset:

- **Wild claw-and-kick combos:**
  - Wide clawing swipes at full reach, mixed with spins, leaps, X-slashes and kicks (front snap, roundhouse, axe kick, rising knee, spinning hook kick).
  - 10 hits with one gauntlet, and a separate 10-hit combo when you dual-wield.
- **Purple slash VFX:** every claw and kick leaves a purple trail and a purple slash crescent along its swing.
- **Sprint attacks:** an elbow strike with one gauntlet, or a running shred with two.
- **Right-click guard:** cross both claws in an X to block, with parries and guard breaks.
- **Skills:** Obsidian Impaler with one gauntlet, Twin Meteor with two.
- **Obsidian Stretch (dodge skill):**
  - Your body stretches from where you stand to where you dash, aimed by your view and movement keys.
  - No cooldown, and it works in the air.
- **Battle-mode visuals:** a predator's guard stance with the claws out, and the armor keeps its purple glow and spikes in battle mode.

---

## Requirements

- **Forge** 1.20.1 (47+)
- **Iron's Spells 'n Spellbooks** 3.4+, with its dependencies **Curios** and **Caelus**
- **GeckoLib** 4.7+
- **PlayerAnimator** 1.0.2-rc1+
- **AAA Particles** 2.x
- *Optional:* **Epic Fight** 20.14+ with **Obsidian EF Compat**
