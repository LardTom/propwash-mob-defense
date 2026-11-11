# Changelog

What changed in **Propwash: Mob Defense**, for players and server owners. The player guide is in
[`docs/guide.md`](docs/guide.md) (German: [`docs/guide.de.md`](docs/guide.de.md)), developer notes per day in
[`changelogs/`](changelogs/) (German).

## [1.0.0] - First release: dispenser, spear and rocket booster, and mobs that fight back

### Highlights
- **Your drone has a dispenser now:** a dispenser mount under the front of the frame with four slots fires arrows, potions, wind charges, TNT and everything else a vanilla dispenser can, on accessory action 1, best on a button of your radio. Wind charges kick back for real.
- **Spear and rocket booster:** a spear under the drone hits mobs in passing with vanilla spear damage, and one firework rocket on top gives an elytra-style boost.
- **Mobs fight back:** hostile mobs react to armed drones. Zombies come closer, skeletons shoot, phantoms hunt, guardians lock on with their laser, and endermen get angry when your camera looks at them.

### New
- Three accessories in their own creative tab, with recipes from vanilla items and Propwash's intermediate parts: **Dispenser Mount** (front, 150 g), **Spear Mount** (bottom, 40 g) and **Rocket Booster** (top, 28 g). Every part has real weight and drag, and the load counts too: four potions or four TNT make any drone noticeably slower.
- Dispenser mount: sits under the front of the frame on every drone (only its stone front sticks out past the nose) and always fires straight ahead from its muzzle. Four slots, a short press fires once, holding keeps firing, and accessory action 2 picks the next slot. Set your own fire rate (delay before continuous fire and time between shots, 200 to 2000 ms) in the new configurator tab *Mob Defense*.
- The front of the dispenser mount uses the vanilla dispenser texture, so resource packs change it too.
- Spear mount: takes one spear from wooden to netherite, hits by itself from 4.6 m/s, with the spear's hit sound at your camera, crit particles and a short *HIT* in the OSD. The spear and other mounted items also show in your own FPV view.
- Rocket booster: accessory action 3 ignites one firework rocket, 2 g along the nose for 1.25 to 2.25 s; rockets with stars explode at the end.
- Wind charges push the drone straight back at its centre of mass.
- Mob reactions for every hostile vanilla mob: zombies are lured and strike low drones, skeletons and pillagers lead their shots, blazes and ghasts fire, a breeze blows the drone away, phantoms circle and swoop, creepers wait under low drones, the wither fires skulls and an evoker sends fangs and vexes. Guardians and elder guardians lock their laser onto armed drones like onto players, and shulker bullets make a drone float up. Mobs from other mods that have no reaction of their own attack drones close to the ground.
- Mobs only react to drones that are armed or on autopilot (return home, autonomous flight). A drone that is only linked to the radio, on the ground or after a crash, shows no THREAT, disarming ends every attack, and a mob that is after a player stays on the player.
- OSD elements in the editor group *Add-ons* (dispenser, all four slots, rocket booster, spear durability) and warnings: NO AMMO, BUCKET LOCKED, TNT LOCKED, BARREL BLOCKED, NO PERMISSION, THREAT and HIT.
- Flight School chapters for all three accessories and five advancements: *Mob Defense*, *Air Support*, *Drone Joust*, *Afterburner* and *Recoil*.
- English and German.

### For server owners
- Ten world rules in their own section *Mob Defense* of the tab *World rules*: mobs react to drones (on), reaction range (24 blocks), mob damage to drones (100 %), payloads hit players (off), TNT from the dispenser (on), drone griefing (on), fluid buckets from the dispenser (off), real ammo (on), dispenser cooldown (4 ticks) and spear damage (100 %).
- The server decides every shot. Blocks are only placed, lit or blown up where the pilot may build; spawn protection and adventure mode are respected. Healing potions always heal, harmful payloads only hit players when the world rule and vanilla PvP allow it.
- Mobs only look for drones while one is flying, with a fixed budget per tick.
- A player without the add-on gets Propwash's join message with the add-on's name and version instead of an error.

### Compatibility
- Minecraft 26.3 · Fabric (Loader 0.19.5+, Fabric API) or NeoForge 26.3 (26.3.0.10-beta+) · Java 25 · install it on the server and on every client that plays there.
- Needs Propwash FPV 0.6.0 or newer (add-on API 1.8). With an older Propwash the game stops at start with a clear message.
- Works with Just More Parts and Camera Drones (both use the front mount, so a drone carries either a gimbal or the dispenser mount). The three parts have their own short numbers in Propwash's share codes; ammunition is not part of a share code.
- Worlds stay intact if the add-on is removed: Propwash keeps the parts as placeholders and the drone's data.
