# AAA Particles (Effekseer) effects

Every spell and status effect fires an **AAA Particles** emitter through
`AAALevel.addParticle(...)` (see `util/Vfx.java`). AAA loads effects from:

```
src/main/resources/assets/obsidian_arsenal/effeks/<id>.efkefc
```

`.efkefc` is Effekseer's compiled binary format. You make these files in the free **Effekseer editor**
(https://effekseer.github.io, version 1.7x/1.8x, matching AAA 2.x), not by hand. Put any textures an effect uses next to it
(`effeks/<id>/...` or `effeks/Texture/...`, the same layout AAA's own `debug` effect uses).

Until a file exists, AAA skips that emitter. It only writes a debug log line and nothing breaks. The custom sprite
particles (red hellfire, black Amaterasu, purple lightning flames, obsidian chunks, dust) always render underneath,
so every ability still has visuals.

## Conventions

* **+Z is forward** for directional effects (`rotationFromForward`). **+Y is up.** 1 Effekseer unit = 1 block.
* "Bound" effects follow the entity (`bindOnEntity`), so author them centred on the origin, around the body.
* Looping effects are re-emitted by the code at the interval shown. Make them last at least that long, then fade.

## Effect list

| id | used by | placement | length / notes |
|---|---|---|---|
| `obsidian_orb_trail` | Obsidian Spike Explosion projectile | bound to projectile, velocity = forward | loop ~4 s; purple wisps + obsidian grit trailing along -Z |
| `obsidian_spike_eruption` | Spike Explosion impact, each spike | world, at ground, scale 1 to 2 | ~1 s burst; ground crack, purple shockwave ring, debris upward |
| `obsidian_storm_vortex` | Obsidian Storm | bound to caster | ~4 s; purple vortex radius ~3.2 at waist height |
| `obsidian_tentacle_emerge` | Obsidian Tentacles (each) | world, facing outward | ~1 s; ground rupture, purple dust column |
| `purple_lightning_burst` | Purple Lightning Cloak cast | bound to caster | ~1 s; downward purple bolt + flash |
| `purple_lightning_aura` | Purple Lightning effect | bound, re-emitted every 2 s | 2 s; crackling purple arcs around the body |
| `obsidian_shatter` | Obsidian Shatter (each shard) | world, facing along the line | ~1 s; shard burst + purple glints |
| `hell_column` | Columns of Hell (each column) | bound to the column | ~4.5 s; red swirling flame pillar, ~4.5 blocks tall |
| `hellfire_stream` | Flaming Judgement | bound to caster head, re-emitted every 0.5 s | 0.6 s; red flame cone, 10 blocks long along +Z, widening |
| `gyroscope_rings` | Gyroscope | bound to caster (+1.1 Y), re-emitted every 2 s | 2 s; three tilted rotating fire rings, radius ~2.1 to 2.6 |
| `gyroscope_deflect` | Gyroscope blocks a hit or projectile | world / bound | ~0.4 s; red-orange spark flash |
| `amaterasu_ignite` | Amaterasu cast | bound to target | ~1 s; black flame eruption |
| `amaterasu_flames` | Amaterasu effect | bound to target, re-emitted every 1.5 s | 1.5 s; black flames with crimson edges engulfing the body |
| `indra_charge` | Indra's Arrow while channeling | bound to arrow | ~2 s; embers spiralling inward, growing glow |
| `indra_trail` | Indra's Arrow in flight | bound to arrow, velocity = forward | loop ~8 s; red/white flame trail |
| `plasma_beam_muzzle` | Plasma Beam start | bound to caster's hand | ~0.5 s; red muzzle flare |
| `black_hole_form` | Black Hole cast | world | ~1 s; purple implosion as the hole opens |
| `indra_explosion` | Indra's Arrow impact | world, scale 5 (radius 25) | ~3 s; author at radius 5 so scale 5 = 25 blocks; fire dome + shockwave |

Effect ids live in `Vfx.Effek` if you want to rename or add any.
