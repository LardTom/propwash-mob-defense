# Propwash: Mob Defense – Player guide

Deutsch: [`guide.de.md`](guide.de.md)

Mob Defense is an official add-on for Propwash FPV. Three accessories turn an FPV drone into a tool against mobs: a
**dispenser mount** in front, a **spear mount** below and a **rocket booster** on top. Hostile mobs notice flying drones
and fight back, and world rules decide what is allowed on a server. Everything works with vanilla items: arrows,
potions, wind charges, TNT, spears and firework rockets.

Contents: 1 Getting started · 2 Controls · 3 Dispenser mount · 4 Spear mount · 5 Rocket booster · 6 OSD and warnings ·
7 Mobs react to drones · 8 World rules · 9 On servers · 10 Flight school and advancements · 11 FAQ · 12 Compatibility

---

## 1. Getting started

1. **Install** Propwash FPV 0.6.0 or newer and Mob Defense in the same `mods` folder, on the server and on every client
   (section 12).
2. **Get the parts.** In creative mode they are in the tab *Propwash: Mob Defense*. In survival they are crafted
   (recipes in the recipe book, unlocked as soon as you have one of the ingredients):

   | Part | Mount | Recipe |
   |---|---|---|
   | Dispenser Mount | front | circuit board on top, iron nugget – dispenser – iron nugget in the middle, copper coil below |
   | Spear Mount | bottom | iron nugget, gap, iron nugget on top, three carbon plates below |
   | Rocket Booster | top | copper nugget – flint and steel – copper nugget on top, carbon plate below in the middle |

   Circuit board, copper coil and carbon plate are Propwash's intermediate parts.
3. **Build them on.** Put the accessory on its mount at Propwash's drone workbench, like any other part. Each part has
   weight and drag, the workbench shows the new all-up weight. The smallest frame with a front mount is the 3″
   cinewhoop; the 65 mm whoop only has a bottom mount.
4. **Load them.** Place the drone, sneak and right-click it, then press **Accessories** in the service menu. Put
   ammunition into the dispenser, a spear into the spear mount and one firework rocket into the booster.
5. **Fly** as usual and fire with **accessory action 1** (section 2). The right-click ends the flight, as with every
   drone – it does not fire.

---

## 2. Controls

| Function | Default | Notes |
|---|---|---|
| Fire the dispenser | **Accessory action 1**: Keypad 9, or a button or switch on your radio | best on a momentary button of the radio; a switch gives continuous fire |
| Next dispenser slot | Accessory action 2: Keypad 0 | picks the next filled slot |
| Ignite the rocket booster | Accessory action 3: Keypad Enter | only while the drone flies |
| End the FPV flight | *Stop FPV flight* (Y) or right-click | as with every Propwash drone |

- **Accessory action 1 is the trigger.** On a radio, assign it in Propwash's configurator (K), tab *Modes*, like any
  other switch function; the tab shows the meaning next to it ("Accessory action 1 – Fire dispenser"). On the keyboard
  it is Keypad 9 and can be rebound in the tab *Keys*, also to a side button of the mouse.
- On the first flight with the dispenser, Propwash shows once which action does what and how it is bound right now.
  If accessory action 1 is bound nowhere, the hint points to the tab *Modes*.
- Accessory action 4 is not used.
- The spear needs no button: it hits by itself (section 4).

---

## 3. Dispenser mount

The dispenser always points straight ahead. There is no turret: you aim with the whole drone. The mount sits under the
front of the frame; only its stone front sticks out past the nose.

**Four slots.** Each slot takes up to 16 items or the stack size of the item; TNT at most 4, splash and lingering potions
and buckets 1 each. Drone items do not fit.

**Firing.** A short press fires exactly once. Hold accessory action 1 and the second shot follows after the *delay
before continuous fire* (500 ms), then the dispenser keeps firing at the *time between shots* (200 ms). The selected
slot fires; if it is empty, the next filled one. Accessory action 2 picks the next filled slot.

**Your fire rate.** In the configurator (K), tab *Mob Defense*, you set both times for yourself, each from 200 to
2000 ms in steps of 50 ms. They are saved on your computer (`config/mobdefense-client.json`) and apply on every server
with Mob Defense. The world rule *Dispenser cooldown* can demand more; the tab shows the lower limit of the current
world. Two shots are never closer than 200 ms (4 ticks, like a vanilla dispenser), whether you hold the button, press
it quickly or another add-on triggers the action.

**What it fires.** Everything a vanilla dispenser can do, from the muzzle in front of the drone:

- Projectiles: all arrows (also spectral and tipped arrows), snowballs, eggs, splash and lingering potions, bottles
  o' enchanting, wind charges, fire charges and firework rockets. They fly like from a vanilla dispenser, plus the speed
  of the drone; the pilot is the owner.
- TNT is primed at the muzzle and falls with the speed of the drone. TNT is allowed by default; the world rule *TNT from
  the dispenser* turns it off (`TNT LOCKED`).
- Everything else works on the block in front of the muzzle like a vanilla dispenser: bone meal, shears, flint and
  steel, putting on armor, placing pumpkins and skulls, placing shulker boxes, charging a respawn anchor with
  glowstone, honeycomb, brushes, boats and minecarts; an empty bucket scoops. Items without such a behavior fly
  forward as items.
- Water, lava and powder snow buckets and buckets with fish or animals are refused unless the world rule allows them.

**Recoil.** Every shot pushes the drone back by the momentum of the projectile: an arrow barely, a splash potion and a
**wind charge** noticeably – a wind charge pushes a cinewhoop and a 5″ back by a few meters per second, and both stay
flyable. Every projectile, the wind charge included, pushes straight through the center of mass, so the drone stays level,
even with the last potion. The gust of a wind charge from the dispenser knocks mobs, players and drones back half as hard as
a vanilla wind charge.

**Weight.** The load counts towards the all-up weight. A fully loaded drone flies noticeably more sluggish:

| Item | g | Item | g |
|---|---|---|---|
| Arrow (all kinds) | 20 | Wind charge | 50 |
| Snowball, egg | 60 | Fire charge | 80 |
| Splash, lingering potion | 350 | Firework rocket | 60 |
| Bottle o' enchanting | 300 | TNT | 600 |
| Bone meal | 15 | Bucket empty / full | 250 / 1250 |

Four potions or four TNT make any drone much slower. A 7″ with a LiPo carries them best, the long-range 7″ with a Li-ion
pack worse than a 5″. With eight TNT (4.8 kg) a 5″ no longer takes off.

**Sounds:** the vanilla dispenser sounds, including the click when a shot is refused.

---

## 4. Spear mount

- Takes exactly **one spear**, tip forward: wooden, stone, copper, iron, golden, diamond or netherite. Tridents, swords
  and sticks do not fit; spears from other mods work if they are real spears.
- Everyone sees the spear under the drone; its tip sits about 10 cm in front of the middle of the mount – on a 5″ about
  level with the front props, on a 7″ the props reach further forward.
- **It hits by itself.** Fly through or into a mob nose first – if the frame bumps into it before the tip arrives, that
  still counts as a spear hit. From 4.6 m/s along the nose (relative to the target) the spear deals damage
  like a vanilla spear at the same speed, times the world rule *Spear damage* (100 % by default), at most 15. Example
  iron spear: 5 m/s → 5, 10 m/s → 10, 15 m/s and faster → 15. Knockback and dismounting riders follow the vanilla
  thresholds of the spear.
- Each target can be hit once per half second. Damage enchantments such as Sharpness and Fire Aspect work, Lunge does
  not. Every hit costs one point of durability; a broken spear disappears with the vanilla sound.
- On a hit you hear the spear's hit sound at the drone's camera, see crit particles on the target and the OSD shows
  **HIT** for a moment.

---

## 5. Rocket booster

- Holds exactly **one firework rocket**. Reload it in the service menu after landing; there is no cooldown.
- **Accessory action 3** ignites it while the drone flies: a push along the nose like with elytra – the same 2 g for
  every drone size, at most 60 N. It burns 1.25, 1.75 or 2.25 s for flight duration 1, 2 or 3. A 5″ gets almost 25 m/s
  faster.
- Sparks and smoke at the tail, the vanilla launch sound. Afterwards the slot is empty.
- A rocket **with stars** explodes at the end like at an elytra: damage `5 + 2 × stars` to the drone and to living things
  around it. Even one star breaks your own drone – use rockets without stars for boosting.
- If the pilot changes or someone takes the rocket out while it burns, the booster stops at once (no push, no
  explosion).

---

## 6. OSD and warnings

The elements are in the group *Add-ons* of Propwash's OSD editor and can be moved there. An element only shows when the
matching accessory is on the drone. OSD texts are plain ASCII and the same in every language:

| Element | Default | Example | Shows |
|---|---|---|---|
| Dispenser | on | `DSP 2 ARW 12` | slot that fires next, ammo, count; `*` during the cooldown; `DSP --` empty |
| Dispenser slots | off | `[ARW]SPL WND ---` | all four slots, the selected one in brackets |
| Rocket booster | on | `RKT RDY` / `RKT 1.2` / `RKT ---` | ready, burn time left, empty |
| Spear durability | off | `SPR 87%` | durability of the spear |

Ammo codes: `ARW` arrow, `SPC` spectral arrow, `TIP` tipped arrow, `SNW` snowball, `EGG` egg, `SPL` splash potion, `LNG`
lingering potion, `XPB` bottle o' enchanting, `WND` wind charge, `FIR` fire charge, `RKT` rocket, `TNT`, `BKT` bucket,
`BON` bone meal, `ITM` anything else.

Warnings (2 s, only for the pilot):

| English | German | Meaning |
|---|---|---|
| `NO AMMO` | `KEINE MUNITION` | all slots empty |
| `BUCKET LOCKED` | `EIMER GESPERRT` | fluid buckets are off on this world |
| `TNT LOCKED` | `TNT GESPERRT` | the world rule *TNT from the dispenser* is off on this world (or vanilla `tnt_explodes` is off) |
| `BARREL BLOCKED` | `LAUF BLOCKIERT` | a block sits right in front of the muzzle |
| `NO PERMISSION` | `KEIN RECHT` | you may not build here, or drone griefing is off |
| `THREAT` | `BEDROHUNG` | a mob is going after your drone |
| `HIT` | `TREFFER` | the spear hit |

---

## 7. Mobs react to drones

Every hostile mob has its own reaction to drones. The basic rules:

- Mobs only react to drones that are **armed** (in the air or landed) or that return home or fly on their own. A drone that
  is linked to your remote but not armed is left alone, just like a **parked drone**; disarming ends every attack on it.
- **Players come first:** a mob that already has a player as its target only switches to a drone that is less than half
  as far away. Without a target it takes the nearest drone within the reaction range (24 blocks) that it can see.
- Up to 12 mobs per drone react at the same time.
- Damage from mobs is scaled by the world rule *Mob damage to drones*. Explosions and wind charges push flying drones.
- While a mob goes after your drone, the OSD shows **THREAT**.

| Kind | Mobs | Reaction |
|---|---|---|
| Chase | Zombie, Husk, Drowned, Zombie Villager, Zombie Nautilus | lured by the drone, run (or swim) under it and strike when it is at most 2.5 blocks above their head or has landed |
| Brute | Zombified Piglin (only when angry), Vindicator, Piglin Brute, Ravager, Hoglin, Zoglin, Wither Skeleton | like Chase; the Ravager roars the drone away, Hoglin and Zoglin toss it upwards |
| Archer | Skeleton, Stray, Bogged, Parched, Illusioner, Pillager, Piglin with crossbow | stop, lead the target and shoot their own arrows, 4–24 blocks away with line of sight |
| Fireball | Blaze, Ghast | Blaze: three small fire charges up to 16 blocks; Ghast: a big fireball up to 64 blocks whose explosion pushes the drone |
| Wind | Breeze | wind charges up to 16 blocks push the drone away |
| Bullet | Shulker | opens up and fires shulker bullets that follow the drone, up to 16 blocks; a hit makes the drone float up for 10 seconds like Levitation (it rises even without throttle, your own thrust adds on top) |
| Potion | Witch | throws splash potions of harming up to 10 blocks |
| Trident | Drowned with trident | throws the trident up to 16 blocks |
| Caster | Evoker | fangs under drones flying at most 2 blocks high; higher up it summons vexes |
| Flyer | Phantom, Vex | hunt the drone in the air; phantoms circle and swoop on drones more than 10 blocks up, or at night in the open |
| Creeper | Creeper | walks under a drone flying at most 4 blocks high and ignites |
| Jumper | Slime, Magma Cube, Sulfur Cube, Spider, Cave Spider | jump at drones flying at most 3 blocks high; spiders climb walls to reach them |
| Beam | Guardian, Elder Guardian | laser on drones up to 16 blocks with line of sight, like on players: damage after the vanilla charge time, the Guardian only from more than 3 blocks away |
| Stare | Enderman, Creaking | Enderman: angry when the FPV camera looks it in the face, teleports to a low drone or goes for the pilot. Creaking: walks to the drone and freezes while the camera looks at it |
| Boss | Wither | wither skulls up to 32 blocks |
| Ground | Silverfish, Endermite and any other hostile mob, also from other mods | attack drones that have landed or hover at most 1 block high |

Ender Dragon, Giant, Camel Husk, Zombie Horse and the Warden ignore drones (the dragon's wings already push drones). Block
damage from creeper and ghast explosions follows vanilla `mob_griefing`. Data packs and other mods can assign their mobs
with the entity tags `mobdefense:reacts/<kind>` or exclude them with `mobdefense:ignores_drones`.

Turn it all off with the world rule *Mobs react to drones*; the world-rule preset *Training* does that, *Realistic* and
*Arcade* turn it back on.

---

## 8. World rules

In Propwash's configurator (K), tab *World rules*, section *Mob Defense*. In single player you can change them, on a
server operators can. Each rule is also a gamerule (`/gamerule mobdefense:<id> <value>`).

| Rule | Id | Default | Range | Effect |
|---|---|---|---|---|
| Mobs react to drones | `mobs_react_to_drones` | on | – | Hostile mobs notice flying drones and fight back: zombies come closer, skeletons shoot, phantoms hunt. Off: mobs ignore drones. |
| Mob reaction range | `mob_reaction_range` | 24 | 8–64 | Distance in blocks within which mobs notice a drone. |
| Mob damage to drones | `mob_damage_to_drones_percent` | 100 | 0–400 | Damage mobs deal to drones, in percent of the normal value (0 = none, 400 = four times). |
| Payloads hit players | `payloads_hit_players` | off | – | Harmful payloads (projectiles, harmful potions, spear, TNT damage) hit players. Healing and other positive potions always work; vanilla PvP still applies. |
| TNT from the dispenser | `allow_tnt` | on | – | The dispenser mount may drop primed TNT. Off: the dispenser refuses TNT. |
| Drone griefing | `drone_griefing` | on | – | Payloads may change blocks: TNT damage, fire from fire charges and flint and steel, placed blocks and fluids. Vanilla tnt_explodes still applies. |
| Fluid buckets from the dispenser | `allow_fluid_buckets` | off | – | Water, lava and powder snow buckets and buckets with fish or animals may place their contents. Off: the dispenser refuses them, empty buckets still scoop. |
| Real ammo | `real_ammo` | on | – | Every shot uses up one item. Off: the slot must be filled, but the item stays. |
| Dispenser cooldown (ticks) | `dispenser_cooldown_ticks` | 4 | 0–100 | Ticks between two shots of the dispenser mount (20 ticks = 1 s, vanilla dispenser: 4). |
| Spear damage | `spear_damage_percent` | 100 | 0–100 | Share of the vanilla spear damage that the spear mount deals, in percent. |

- The texts are the ones from the game (from the add-on's web export). Below 4 ticks the cooldown stays at 4 ticks.
- Worlds keep the stored values. Worlds that were created with an early development build may still have spear damage
  at 50 %: `/gamerule mobdefense:spear_damage_percent 100`.

---

## 9. On servers

- Mob Defense must be installed **on the server and on every client**. A player without it gets Propwash's message
  *Add-ons don't match this server* with the name and version of Mob Defense instead of an error.
- The server decides every shot. Actions arrive checked by Propwash (pilot, rate limit), on top of the cooldown.
- **Permissions:** placing, igniting, scooping and blowing up blocks only works where the pilot may build. In spawn
  protection or adventure mode the dispenser refuses with `NO PERMISSION`.
- **PvP:** the pilot owns every payload. With vanilla `pvp` off, nothing hits players. With *Payloads hit players* off
  (default), arrows, harmful potions, the spear and TNT damage leave players alone, but healing potions still heal –
  healing a teammate from the air is meant to work.
- **Load:** mobs only look for drones while a drone is flying, staggered over time, with a fixed budget per tick.
- Spectators of a drone see its spear and rocket; the OSD only shows on the pilot's own video feed.
- Worlds stay intact without the add-on: Propwash keeps the parts as placeholders and the data of the drone, and
  everything is back when Mob Defense is installed again.

---

## 10. Flight school and advancements

- **Flight school:** hold M over one of the three parts (or open the flight school in the configurator) for the
  chapters *Mob Defense: Dispenser*, *Mob Defense: Spear* and *Mob Defense: Rocket booster*.
- **Advancements** in their own tab *Mob Defense*: *Air Support* (hit another player with a healing potion from the
  dispenser), *Drone Joust* (defeat a mob with the spear mount), *Afterburner* (ignite the rocket booster) and
  *Recoil* (fire a wind charge from the dispenser).

---

## 11. FAQ

**Why doesn't right-click fire?** In Propwash the right-click always ends the flight, with every drone. That keeps the
exit the same everywhere and your hands on the radio. Fire with accessory action 1 – best on a button of your radio.

**Does it work on servers?** Yes. Install it on the server and on every client. The server owner sets the world rules;
see section 9.

**Can I hit other players?** Only if the server allows it: the world rule *Payloads hit players* and vanilla `pvp` must
both be on. Healing and other positive potions always work.

**Why are buckets locked?** So nobody builds water or lava columns in the air. Empty buckets still scoop. Server owners
can allow fluid buckets with the world rule *Fluid buckets from the dispenser*.

**My drone doesn't take off any more.** Your load is too heavy. Ammunition counts with its real weight; TNT (600 g each)
and potions (350 g each) add up quickly. Take fewer, or use a bigger drone.

**My drone broke when the booster ran out.** The rocket had stars. Use rockets without stars for boosting.

**Mobs ignore my drone.** Check the world rule *Mobs react to drones* (the preset *Training* turns it off), the reaction
range and whether the drone is armed: parked drones and drones that are not armed are left alone.

**Is there a sculk or warden reaction?** Not in 1.0. Wardens ignore drones for now.

---

## 12. Compatibility

- Minecraft 26.3 · Fabric (Loader 0.19.5 or newer with Fabric API) or NeoForge 26.3.0.10-beta or newer · Java 25.
- Needs **Propwash FPV 0.6.0 or newer** (add-on API 1.8).
- Server and client both need the add-on.
- **Just More Parts:** works; every JMP frame with a front mount carries the dispenser.
- **Camera Drones:** works; both use the front mount, so a drone carries either a gimbal or the dispenser mount.
- **Other mods:** Mob Defense changes nothing inside Propwash and only touches vanilla mob AI lightly. If another mod
  changes a mob so that its reaction no longer works, that mob simply doesn't react to drones and the log says so once.
- **Share codes:** the three parts have their own short numbers in Propwash's share codes. Ammunition is not part of a
  share code. Without the add-on the mount stays empty.
- Resource packs that change the vanilla dispenser also change the front of the dispenser mount.
