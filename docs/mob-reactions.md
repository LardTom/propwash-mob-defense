# Mob-Reaktionen – Propwash: Mob Defense

Grundlage: `docs/design.md` §2.6 (Mobs reagieren auf Drohnen), §2.9 (Last), §4.1 (Pakete), E12 (Tags und Arten), E13 (Spieler
zuerst, geparkte Drohnen tabu). Dieses Dokument beschreibt den ersten Teil (Strang `md-mobs-1`): den Reaktions-Takt, die
Tags und die Arten `chase`, `brute`, `archer`, `fireball`, `creeper` und `flyer`, und den zweiten Teil (Strang `md-mobs-2`):
die übrigen Arten `stare`, `beam`, `caster`, `boss`, `ground`, `wind`, `bullet`, `potion`, `jumper` und `trident`. Damit steht
jeder feindliche Vanilla-Mob in genau einem Tag `reacts/*` oder in `ignores_drones`.

---

## 1. Was Spieler sehen

| Art | Mobs | Reaktion |
|---|---|---|
| `chase` | Zombie, Wüstenzombie, Ertrunkener, Zombiedorfbewohner, Zombienautilus | wird von der Drohne angelockt, läuft (bzw. schwimmt) unter sie und schlägt zu, sobald ihre Unterkante höchstens 2,5 Blöcke über dem Kopf ist oder sie gelandet ist |
| `brute` | Zombifizierter Piglin (nur wütend), Diener, Piglin-Barbar, Verwüster, Hoglin, Zoglin, Wither-Skelett | wie `chase`; der Verwüster brüllt die Drohne aus 4 Blöcken weg, Hoglin und Zoglin schleudern sie bei jedem Treffer nach oben |
| `archer` | Skelett, Eiswanderer, Sumpfskelett, Vertrockneter, Illusionist, Plünderer, Piglin mit Armbrust | bleibt stehen, zielt mit Vorhalt und schießt seine eigenen Pfeile mit halber Vanilla-Streuung (die Drohne ist viel kleiner als ein Spieler), bei Sichtlinie und 4–24 Blöcken Abstand; ohne Bogen oder Armbrust wie `chase` |
| `fireball` | Lohe, Ghast | Lohe: drei kleine Feuerkugeln je Salve bis 16 Blöcke; Ghast: große Feuerkugel bis 64 Blöcke, die Explosion stößt die Drohne |
| `creeper` | Creeper | läuft unter eine Drohne, die höchstens 4 Blöcke über dem Boden fliegt, und zündet bei höchstens 3 Blöcken Abstand (wie Vanilla: weg ab 7 Blöcken) |
| `flyer` | Phantom, Plagegeist | Phantom: kreist über der Drohne und stößt herab, wenn sie mehr als 10 Blöcke über dem Boden fliegt oder es Nacht und sie im Freien ist; Plagegeist: greift im Flug an |
| `stare` | Enderman, Knarz | Enderman: wird wütend, wenn die FPV-Kamera ihm ins Gesicht sieht (gleicher Blickkegel wie bei Spielern), teleportiert sich zur tiefen Drohne (Unterkante höchstens 4 Blöcke über dem Boden) und schlägt zu; fliegt sie höher und hat sie einen Piloten, greift er den Piloten an; ohne Kamerablick bleibt er neutral. Knarz: läuft ohne Kamerablick zur Drohne und schlägt zu, unter dem Kamerablick erstarrt er wie unter dem Blick eines Spielers |
| `beam` | Wächter, Großwächter | wie bei Spielern auf Drohnen bis 16 Blöcke mit Sichtlinie, auch über Land und auch vom Wächter auf dem Trockenen: Laser als Partikellinie (Farbe wie Vanilla von Violett nach Gelb), Schaden nach der Vanilla-Ladezeit (Wächter 80, Großwächter 60 Ticks; 1 Magieschaden, auf Schwer +2, beim Großwächter +2, danach der Schlag des Wächters); der Wächter erst ab mehr als 3 Blöcken Abstand, der Großwächter hält auch näher fest |
| `caster` | Magier | bis 12 Blöcke: Fangzähne in einer Linie unter die Drohne, wenn sie höchstens 2 Blöcke über dem Boden fliegt (der Biss reicht 2 Blöcke hoch); höher beschwört er drei Plagegeister, die sie als `flyer` jagen |
| `boss` | Wither | schießt bis 32 Blöcke Wither-Schädel mit Vorhalt aus seinen drei Köpfen, die Explosion stößt die Drohne |
| `ground` | Silberfischchen, Endermite und jeder andere `Enemy` ohne Tag (auch aus Mods) | läuft zu gelandeten oder höchstens 1 Block über dem Boden schwebenden Drohnen und beißt bzw. schlägt zu |
| `wind` | Böe | lädt wie in Vanilla (Einatmen) und schießt bis 16 Blöcke Windkugeln mit Vorhalt, die Drohne wird weggestoßen |
| `bullet` | Shulker | öffnet sich und schießt bis 16 Blöcke Shulker-Geschosse, die der Drohne folgen; ein Treffer lässt die Drohne 10 s lang schweben (Auftrieb wie die Schwebekraft, siehe unten) |
| `potion` | Hexe | wirft bis 10 Blöcke Wurf-Schadenstränke mit Vorhalt; platzt der Trank im Umkreis von 2 Blöcken, nimmt die Drohne Schaden (6 × (1 − Abstand/4), wie Vanilla) |
| `jumper` | Schleim, Magmawürfel, Schwefelwürfel, Spinne, Höhlenspinne | springen an Drohnen, die höchstens 3 Blöcke über dem Boden fliegen; Spinnen klettern zu Drohnen an einer Wand; Schleim und Magmawürfel schaden beim Treffer (Winzlinge nicht), der friedliche Schwefelwürfel stößt die Drohne nur |
| `trident` | Ertrunkener mit Dreizack (Variante von `chase`) | holt aus und wirft bis 16 Blöcke den Dreizack mit Vorhalt; ohne Dreizack gilt `chase` |

Grundregeln für alle Arten:

- **Nur scharfe Drohnen oder Autopilot.** Reagiert wird auf scharf geschaltete Drohnen (auch gelandet, solange sie scharf sind)
  und auf Drohnen im Heimflug oder autonomen Flug (`DroneView#armed` oder `DroneView#autopilot`). Eine Drohne, die mit der
  Fernbedienung verbunden, aber nicht scharf ist (`DroneView#flying` meldet dann schon „fliegt“), bleibt ebenso unbehelligt wie
  eine abgestellte; nach einem Absturz oder Entschärfen enden laufende Angriffe und die Warnung `THREAT` (Befund FB1M-1).
- **Spieler zuerst.** Hat ein Mob ein Ziel, wechselt er nur zur Drohne, wenn sie weniger als halb so weit weg ist wie das Ziel.
  Brain-Mobs (Piglin, Piglin-Barbar, Hoglin, Zoglin, Zombienautilus) behalten ein vorhandenes Ziel immer. Ohne Ziel nimmt ein
  Mob die nächste Drohne in `mob_reaction_range` mit Sichtlinie; verliert er sie aus dem Blick, sucht er sie noch 5 s weiter.
- **Regel `mobs_react_to_drones` aus:** keine Reaktion, laufende Reaktionen enden im nächsten Tick (Voreinstellung Training).
- **Schaden** an der Drohne kommt über Propwashs `DRONE_DAMAGED` an und wird mit `mob_damage_to_drones_percent` skaliert,
  sobald der Verursacher ein Mob ist (Schläge, Pfeile, Feuerkugeln, Explosionen). Brennt die Drohne nach einer Feuerkugel,
  zählt das Feuer nicht als Mob-Schaden.
- **Stöße.** Explosionen (Creeper, Ghast, TNT) und Windkugeln stoßen fliegende Drohnen über `DRONE_KNOCKBACK`: Propwash hat
  dafür kein eigenes Verhalten (Standard-Skala 0), Mob Defense schaltet die Stöße mit Skala 1 ein (so stark wie bei einem Mob
  gleicher Masse, Propwash begrenzt auf 50 N·s). Schläge der Mobs (Rückstoß-Attribut), das Brüllen des Verwüsters und der Wurf
  von Hoglin und Zoglin laufen als eigener Stoß über dasselbe Ereignis (Ursache `OTHER`, nur während des Schlags).
- **Tags** `mobdefense:reacts/<art>` ordnen Mobs einer Art zu, `mobdefense:ignores_drones` nimmt Mobs aus (Enderdrache,
  Riese, Zombiedromedar, Zombiepferd und – bis der optionale Sculk-Strang die Art `warden` bringt – der Wärter). Datenpakete und
  andere Mods tragen ihre Mobs dort ein; ein Mob gehört in genau ein Tag. Jeder andere `Enemy` ohne Tag reagiert als `ground`
  (Auffang-Art), Mobs aus `ignores_drones` nie.
- **Varianten.** Eine Art kann für einzelne Mobs eines Tags eine andere Reaktion wählen: der Ertrunkene steht in `reacts/chase`,
  hält er einen Dreizack, reagiert er als `trident`. Die Wahl fällt bei jeder Auswertung neu (`ReactionKinds#refine`).
- **Schwebekraft.** Trifft ein Shulker-Geschoss eine scharfe Drohne oder eine im Autopilot (gleich, wer geschossen hat), bekommt
  sie 200 Ticks (10 s) lang Auftrieb über `DronePhysics#push`, je Tick neu berechnet:
  Masse × (9,81 m/s² + 4/s × (1 m/s − Steiggeschwindigkeit)), begrenzt auf 0 bis 3 g. Das ist die Vanilla-Schwebekraft: Die
  Schwerkraft ist aufgehoben, und die Steiggeschwindigkeit rückt je Tick um ein Fünftel näher an 0,05 Blöcke je Tick. Ohne Schub
  steigt die Drohne also mit 1 m/s, mit Schwebeschub mit gut 3,4 m/s; Höhenhalten kommt nicht dagegen an, nur Schub nach unten
  (Rückenflug). Ein weiterer Treffer setzt die Dauer wieder auf 10 s (keine Stufen). Der Auftrieb endet, sobald die Drohne
  entschärft, abgestürzt oder abgestellt ist; eine abgestellte Drohne bekommt keinen. Abgesagte Treffer (`DRONE_DAMAGED`
  abgebrochen) heben nicht, `mob_damage_to_drones_percent` 0 lässt den Auftrieb. Bei geflogenen Drohnen schickt Propwash den
  Schub an den Client des Piloten.
- `mobGriefing` gilt wie immer für Blockschäden durch Creeper- und Ghast-Explosionen.
- **Warnung `THREAT`/`BEDROHUNG`.** Solange mindestens ein Mob auf eine Drohne mit Pilot reagiert (gleich welche Art, die
  Reaktion läuft), bekommt der Pilot die OSD-Warnung (`Notices.threat`, docs/osd-ux.md §2): sofort beim ersten Mob, danach
  einmal je Sekunde aufgefrischt, sichtbar jeweils 2 s. Endet die letzte Reaktion, verschwindet sie nach höchstens 2 s.
  Drohnen ohne Pilot (Heimflug, autonomer Flug) lösen keine Warnung aus. Das gilt auch, während der Pilot am leeren oder
  blockierten Dispenser Feuer hält: Von den 4 Paketen je Sekunde bleibt einer für `THREAT` frei.

---

## 2. Technik

### 2.1 Reaktions-Takt (`mob.DroneReactions`)

- Läuft am Ende jedes Server-Ticks je Level über `DroneAccess#loaded(ServerLevel)` (keine Entity-Suche). Ohne scharfe Drohne
  oder Drohne im Autopilot oder mit `mobs_react_to_drones` aus: keine Suche, alle Zuordnungen des Levels enden. Wird eine
  Drohne entschärft, während andere noch scharf sind, gibt `DroneReactions#usable` ihre Zuordnungen nicht mehr heraus: Ziel-Goal
  und Brain-Takt enden im nächsten Tick, `THREAT` wird nicht mehr aufgefrischt.
- Jede Drohne sucht alle 10 Ticks, gestaffelt über ihre Entity-Id (`mob.core.ReactionLimits#due`), Mobs im Würfel mit
  `mob_reaction_range` um ihre Mitte, deren Typ in einem Tag `reacts/*` steht, sortiert nach Abstand.
- Höchstens 12 Mobs je Drohne werden zugeordnet, höchstens 64 Mob-Auswertungen je Tick und Level; eine Suche, die das Budget
  nicht schafft, läuft im nächsten Tick vor den neuen Suchen an derselben Stelle weiter, höchstens eine offene Suche je Drohne.
  Die Auswahl ist der portable Kern `mob.core.Selection` (nach Abstand sortiert, gleiche Abstände in der Reihenfolge der Suche,
  jede Auswertung kostet eine Einheit des Budgets, ein abgelehnter Mob belegt keinen Platz), die offenen Suchen hält
  `Selection.Pending`; `DroneReactions` liefert je Tick nur die Prüfung eines Mobs (fehlt die Drohne oder ist sie weder scharf
  noch im Autopilot, endet die Suche ohne Budget).
- Eine Zuordnung hält 25 Ticks und wird bei jeder Suche erneuert. Liegt ein Mob näher an einer anderen Drohne, wechselt er.
- Zahmen Tieren (z. B. gezähmtem Zombienautilus) und Mobs ohne KI (`NoAI`) wird nichts zugeordnet.

### 2.2 Wege in die KI

- **Goal-Mobs** bekommen bei ihrer ersten Zuordnung ein Ziel-Goal (`mob.DroneGoal`, Priorität 1, Flags `MOVE` und `LOOK`) über
  den Accessor `mixin.MobAccessor` (Feld `goalSelector`). Das Goal läuft nur mit gültiger Zuordnung; es sperrt Herumlaufen und
  Umsehen, gibt aber sofort an die Vanilla-Angriffe ab, sobald ein Spielerziel näher ist (Regel oben). Weil Drohnen keine
  `LivingEntity` sind, setzt Mob Defense kein `Mob#setTarget`; der Zustand je Mob liegt in einer schwachen Tabelle.
  Der Einbau beim ersten Kontakt statt beim Laden (Design §2.6) spart Goals für Mobs, die nie eine Drohne sehen, und braucht
  keine Loader-Ereignisse.
- **Brain-Mobs** (Gedächtnis `WALK_TARGET` registriert) steuert der Reaktions-Takt selbst: `WALK_TARGET` unter die Drohne,
  `LOOK_TARGET` auf die Drohne, Angriffe direkt. Beim Ende werden nur die eigenen Einträge gelöscht.
- **Phantome** steuert `mixin.PhantomAccessor` (Felder `moveTargetPoint` und `anchorPoint`): Kreisen 6 Blöcke über der Drohne
  mit 8 Blöcken Radius, nach 2–4 s Sturzflug, Treffer bei Berührung, danach wieder Kreisen.
- **Haken für die übrigen Arten.** `mixin.CreakingMixin` (kleine Injektion am Ende von `Creaking#checkCanMove`) lässt den Knarz
  erstarren, solange ihn die Kamera einer Drohne ansieht; den Rest erledigt Vanilla (Geräusch, Stillstand, synchronisierter
  Zustand). `mixin.ShulkerAccessor` (Invoker `setRawPeekAmount`) öffnet den Shulker zum Schießen,
  `mixin.CubeMobMoveControlAccessor` (Invoker `setDirection`, `setWantedMovement` der Vanilla-Würfelsteuerung) lässt Schleime zur
  Drohne hüpfen. Fehlt ein Haken, erstarrt der Knarz nicht, der Shulker schießt geschlossen und Würfel springen nur aus der Nähe.
- **Fehler.** Die Mixin-Konfiguration `mobdefense.mob.mixins.json` ist nicht zwingend. `mob.Access` prüft beim ersten Gebrauch
  einmal geschützt, ob die Accessoren angewandt sind; fehlt einer (andere Mod, geändertes Vanilla), bleiben die Reaktionen, die
  ihn brauchen, aus (eine Warnung im Log). Wirft eine Reaktion zur Laufzeit, schaltet `mob.Failures` sie für diesen Mob-Typ ab
  und schreibt genau eine Warnung ins Log; das Spiel läuft weiter.

### 2.3 Pakete und Erweiterung

| Klasse | Aufgabe |
|---|---|
| `mob.DroneReactions` | Takt, Budget, Zuordnungen, Anmeldung der Listener, Diagnose (`stats`, `assigned`, `assignments`) |
| `mob.Threats` | Warnung `THREAT` an den Piloten jeder Drohne mit laufender Reaktion, im Takt von `mob.core.ThreatPulse` |
| `mob.ReactionKinds` | Liste der Arten; weitere Arten mit `ReactionKinds.register(new …Reaction())` (Strang `md-mobs-2`) |
| `mob.ReactionTags` | Tags `reacts/<art>` und `ignores_drones`, Zuordnung Mob → Art |
| `mob.Reaction` | Schnittstelle einer Art: `accepts`, `maxRange`, `wants`, `start`, `tick`, `stop`, `priority` |
| `mob.reaction.*Reaction` | die Arten: Teil 1 `Chase`, `Brute`, `Archer`, `Fireball`, `Creeper`, `Flyer`; Teil 2 `Stare`, `Beam`, `Caster`, `Boss`, `Ground`, `Wind`, `Bullet`, `Potion`, `Jumper`, `Trident` |
| `mob.reaction.Support` | gemeinsame Hilfen der übrigen Arten (Sichtlinie, Wasser unter der Drohne, Wand neben der Drohne, Partikellinie, Schaden mit Stoß-Bereich) |
| `mob.reaction.FreezeHook` | Marker, den `mixin.CreakingMixin` dem Knarz gibt (Prüfung, ob der Haken angewandt ist) |
| `mob.DroneState` | Zustand einer Drohne je Tick (Mitte, Unterkante, Höhe über Boden, gelandet) |
| `mob.Moves`, `mob.Strikes`, `mob.Aiming`, `mob.Knockbacks` | Wege, Schläge, Vorhalt über `DroneAccess#aim`, Stöße über `DRONE_KNOCKBACK` |
| `mob.Levitations` | Schwebekraft nach Shulker-Treffern: merkt sich je Drohne das Ende (Listener `DRONE_DAMAGED`) und schiebt im Server-Takt über `DronePhysics#push`; `lifting` meldet für Tests, ob eine Drohne gerade schwebt |
| `mob.core.*` | portabel: `ReactionLimits` (Takt, Budget, Spieler zuerst), `KindWindows` (Fenster je Art, Teil 1), `StageTwoWindows` (Fenster, Trank-Schaden, Sprunghöhe, Zorndauer der übrigen Arten), `Gaze` (Blickkegel der Kamera), `Levitation` (Dauer und Auftrieb der Schwebekraft), `Budget`, `Selection` (die nächsten Mobs je Drohne mit Budget und Fortsetzung, `Selection.Pending` für offene Suchen), `ThreatPulse` (Takt der Warnung `THREAT`: sofort, Auffrischen alle 20 Ticks, Anzeige 40 Ticks, nach abgewiesenem Paket erneut nach 5 Ticks) |

Eine neue Art braucht: eine Klasse mit `Reaction`, eine Zeile `register(…)` in `ReactionKinds`, ein Tag
`data/mobdefense/tags/entity_type/reacts/<art>.json` und ihre Mobs in der Tabelle von `ReactionTagsTest`. Eine Variante braucht
statt des Tags `ReactionKinds.registerVariant(<grundart>, …)`; ihr `accepts` entscheidet je Mob. Die optionale Art `warden`
(Sculk-Strang) nimmt den Wärter aus `ignores_drones` heraus und in `reacts/warden` auf; `ReactionTagsTest` erwartet das, sobald
die Art angemeldet ist.

### 2.4 Kamerablick (G6)

- `DroneAccess#cameraPose` liefert Auge, Blickrichtung und vertikales Sichtfeld der FPV-Kamera aus Linse, Uptilt und Lage der
  Drohne, auch im Heimflug und im autonomen Flug. Ohne Kamera im Bau gibt es keinen Kamerablick.
- **Enderman:** Blick auf die Augenhöhe in einem Kegel mit `1 − 0,025 / Abstand` (wie `Enderman#isBeingStaredBy`) und freie
  Sichtlinie durch Kollisionsblöcke. Der Zorn hält 20–39 s (wie Vanilla) und gilt nur für diese Drohne. Hat die Drohne keinen
  Piloten oder ist der Pilot im Kreativ- oder Zuschauermodus, bleibt der Enderman bei der Drohne.
- **Knarz:** Blick auf drei Höhen (Augen, halbe Höhe, Mitte) in einem Kegel mit `1 − 0,5` (wie `Creaking#checkCanMove`), höchstens
  bis zur Hälfte des Sichtfelds der Kamera, Sichtlinie durch sichtbare Blöcke. Der Blick wird je Tick neu geprüft und gilt zwei Ticks.

### 2.5 Abweichungen vom Design

- Großwächter: Schaden nach 60 statt 80 Ticks, wie seine Vanilla-Ladezeit.
- Der Biss der Fangzähne reicht 2 Blöcke hoch (Vanilla: 0,8), damit „Drohne ≤ 2 Blöcke über Boden“ aus dem Design gilt.
- Den Strahl des Wächters und das Ausholen von Wächter und Magier zeichnet Vanilla nur für Lebewesen; Mob Defense zeigt den Strahl
  als Partikellinie, der Magier hat keine Zauberpose.
- Laser (Befund OT4M-1, Owner-Test rc.4.1): Das Design sieht den Laser nur auf Drohnen über oder im Wasser vor. Jetzt erfassen
  Wächter und Großwächter scharfe Drohnen wie Spieler: Sichtlinie bis 16 Blöcke, auch über Land, der Wächter wie in Vanilla
  erst ab mehr als 3 Blöcken Abstand. Abweichung von Vanilla: Der Großwächter fängt auch näher als 3 Blöcke an (Vanilla nur
  beim Weitermachen).
- Schwebekraft (Befund OT4M-1): im Design nicht vorgesehen; Shulker-Geschosse heben Drohnen wie oben beschrieben. Die
  Rechnung nimmt Propwashs Standard-Schwerkraft 9,81 m/s²; mit der Welt-Regel `gravity_scale` steigt die Drohne ohne Schub
  entsprechend schneller oder langsamer als 1 m/s (die Propwash-API meldet die Schwerkraft nicht).
- Die Arten `potion`, `jumper` und `trident` sind in design.md §11 unter „übrige Arten“ dem Strang `md-mobs-2` zugeordnet und
  hier mit umgesetzt.

---

## 3. Tests

| Test | Prüft |
|---|---|
| `ReactionTagsTest` (JUnit, startet Vanilla) | jeder feindliche Vanilla-Mob (Kategorie `MONSTER`) steht in genau einem Tag `reacts/*` oder in `ignores_drones` (Ausnahmeliste plus Mobs der optionalen Art `warden`, solange sie nicht angemeldet ist); die Tags folgen der Tabelle aus §2.6; jede Art des Designs außer `warden` ist mit vollständigem Tag umgesetzt; Reihenfolge der Arten; `trident` ist Variante von `chase` ohne eigenes Tag, `ground` die Auffang-Art |
| `ThreatPulseTest` (JUnit) | Takt der Warnung `THREAT`: erstes Paket sofort, bei Dauerbedrohung lückenlos sichtbar (4 Ticks Laufzeit eingerechnet) mit höchstens einem Paket je Sekunde, weg spätestens 2 s nach dem letzten Mob, abgewiesenes Paket nach 5 Ticks erneut, Platz im Limit von 4 Paketen je Sekunde für die Hinweise des Dispensers, ein Paket je Sekunde passt in den für `THREAT` freigehaltenen Platz |
| `net.ThreatUnderFireTest` (JUnit) | Befund AUTO3-1: Dauerfeuer ins Leere (`NO_AMMO` im Drohnen-Tick, Anlauf 10 Ticks, dann alle 4 bis 40 Ticks), Bedrohung beginnt mittendrin: `THREAT` sofort und lückenlos sichtbar, `NO_AMMO` ebenso, höchstens 4 Pakete je Sekunde |
| Selbsttest „Warnung BEDROHUNG“ (Modul `covmda_ui`) | Skelett mit Bogen 10 m neben der schwebenden 5″ mit Pilot: `THREAT` am Client, höchstens 4 Pakete je Sekunde, ohne Skelett wieder weg |
| Selbsttest „Warnung BEDROHUNG bei Dauerfeuer ins Leere“ (Modul `covmda_ui`) | Befund AUTO3-1: Ziffernblock 9 am leeren Dispenser gehalten, `NO AMMO` am Client, dann das Skelett: `THREAT` am Client, `NO AMMO` bleibt, höchstens 4 Pakete je Sekunde, beide 100 Ticks lang zugleich sichtbar |
| `UnarmedDroneTest` (JUnit) | Befund FB1M-1: Standardfilter von `DroneReactions` – verbunden, aber nicht scharf (am Boden und in der Luft) und abgestellt: keine Reaktion; scharf (am Boden und in der Luft) und Autopilot (ohne Pilot, Heimflug): Reaktion; der Filter für Selbsttests ersetzt den Standard weiter und fällt mit `null` auf ihn zurück |
| Selbsttest „S10 nicht scharf“ (Modul `mob/`) | Befund FB1M-1: 5″ mit Pilot (`Pilot.fly()`) nachts im Freien am Boden, Phantom 10 m daneben und 8 m höher, Zombie ohne Eigenbewegung 2,5 m daneben: nicht scharf 200 Ticks keine Zuordnung, kein Treffer, kein `THREAT` am Client; scharf geschaltet: Phantom und Zombie zugeordnet, `THREAT` am Client; entschärft: keine Zuordnung mehr nach höchstens 40 Ticks, `THREAT` weg nach höchstens 60 Ticks, danach 100 Ticks ohne Zuordnung, Treffer und `THREAT` |
| `ReactionCoreTest` (JUnit) | Staffelung (100 Drohnen gleichmäßig auf 10 Ticks), Budget 64, Spieler zuerst (genau halb so weit reicht nicht), Fenster je Art |
| `SelectionTest` (JUnit) | Auswahl mit den echten `Budget` und `ReactionLimits`: von 30 Mobs genau die 12 nächsten, abgelehnte kosten eine Auswertung, aber keinen Platz, weniger Mobs als Plätze, gleiche Abstände in der Reihenfolge der Suche, Fortsetzung an derselben Stelle ohne doppelte Auswertung, offene Suchen im nächsten Tick vor neuen, Ende ohne Budget, wenn die Drohne fehlt, höchstens eine offene Suche je Drohne, 100 gestaffelte Drohnen nie über 64 Auswertungen je Tick |
| `StageTwoCoreTest` (JUnit) | Blickkegel Enderman (enger mit dem Abstand) und Knarz (begrenzt auf das Sichtfeld), Fenster der übrigen Arten, Trank-Schaden nur im Umkreis 2, Sprung erreicht die Drohne, Zorn 20–39 s |
| `CovMdBMobInteractionTest` (JUnit, Befund OT4M-1) | Wächter erst ab mehr als 3 Blöcken, Großwächter auch näher; Schwebekraft 200 Ticks; ohne Schub Tick für Tick dieselbe Steiggeschwindigkeit wie ein Spieler mit Schwebekraft (Vanilla-Rechnung `LivingEntity#travel`), mit Schwebeschub kein Auftrieb über gut 3,4 m/s, Grenzen 0 bis 3 g, ungültige Werte ohne Auftrieb, unter der Grenze von `DronePhysics#push` |
| Selbsttest Mob-Interaktionen (Modul `covmdb_mob`, Befund OT4M-1) | Wächter und Großwächter im Becken 7 m neben der autonom schwebenden Drohne über Gras: erster Treffer frühestens 80 bzw. 60 Ticks nach der Zuordnung, Magieschaden 1 bzw. 3; Shulker-Geschoss an der autonom schwebenden Drohne: trotz Höhenhalten mindestens 2 m höher in 5 s, höchster Punkt 170 bis 245 Ticks nach dem Treffer, danach zurück auf die Haltehöhe; abgestellte Drohne bekommt nach einem Treffer keine Schwebekraft (`Levitations#lifting`) und bleibt liegen; mit Pilot (`Pilot.fly()`): `THREAT` am Client, Laser nach der Ladezeit, Auftrieb kommt im Flugmodell des Clients an (mindestens 2 m über der Haltehöhe der Flughilfe in 5 s) |
| Server-Rauchtest (D1) | Tags am Server geladen (Stichproben je Art, auch der übrigen), Accessoren auf `Mob` und `Phantom` angewandt, `CreakingMixin`, `ShulkerAccessor` und `CubeMobMoveControlAccessor` angewandt, Wärter ausgenommen, Variante und Auffang-Art angemeldet, Listener angemeldet, keine Reaktion abgeschaltet |
| Selbsttest S10 Teil 1 (`-PmobdefenseSelftestOnly=mob/`) | je Art eine autonom schwebende Drohne (Propwash B7, Halten von Position und Höhe) und Mobs mit Lederhelm und Feuerresistenz: `chase` (Zombie läuft darunter und trifft, Schaden 50 % mit Regel auf 50), `brute` (Diener über Goal, Hoglin über Brain, Stoß nach oben), `archer` (Treffer in höchstens 5 Pfeilen), `fireball` (drei kleine, eine große Feuerkugel, Explosionsstoß mit Skala 1), `creeper` (zündet unter der Drohne auf 2 m, Explosionsstoß), `flyer` (Phantom trifft die Drohne auf 12 m), Regeln (geparkte Drohne unbehelligt, Regel aus ohne Reaktion, wieder an mit Reaktion, Spieler bleibt Ziel); je Art ein Foto `mob_<art>` (EN und DE) |
| Selbsttest S10 Teil 2 (im Modul `mob/`, einzeln mit `-PmobdefenseSelftestOnly='S10 stare,S10 beam,S10 caster,S10 boss,S10 ground,S10 wind,S10 bullet,S10 potion,S10 jumper,S10 trident'`) | `stare`: Enderman im Kamerablick der tiefen Drohne wird wütend, ein zweiter hinter der Kamera nicht, 9 m zur Seite gestellt teleportiert er sich zur Drohne und trifft; mit Pilot (FPV-Flug, Halten in 6 m) greift er den Piloten an; Knarz läuft ohne Blick zur Drohne und steht im Blick 40 Ticks still; `beam`: Becken unter der Drohne, Wächter trifft erst nach der Ladezeit; `caster`: Fangzähne unter der Drohne auf 1,5 m beißen, auf 5 m jagen beschworene Plagegeister die Drohne; `boss`: Wither-Schädel treffen bzw. stoßen; `ground`: Silberfischchen beißt die Drohne auf 0,7 m, auf 3 m lässt es sie in Ruhe; `wind`: Windkugel stößt mit Skala 1; `bullet`: Shulker-Geschoss trifft; `potion`: Schadenstrank trifft; `jumper`: Spinne und Schleim treffen die Drohne auf 2 m; `trident`: Ertrunkener mit Dreizack hat die Art `trident` und trifft; je Art ein Foto `mob_<art>`, dazu `mob_stare_creaking` (EN und DE) |
| D6 am dedizierten Server Fabric und NeoForge | 10 Drohnen, 50 Mobs, MSPT mit Reaktion höchstens 2 ms über ohne |

### 3.1 D6 – Last

```sh
<slot-skript> ./gradlew :fabric:runServerD6
<slot-skript> ./gradlew :neoforge:runServerD6
```

- Beide Loader haben dieselben Tasks mit denselben Prüfungen. `prepareServerD6` legt `fabric/runs/d6-server` bzw.
  `neoforge/runs/d6-server` an: flache Welt, `pause-when-empty-seconds=0` (sonst hält der Server ohne
  Spieler nach 60 s an), kein Spawnschutz, Schwierigkeit normal.
- Der Server läuft als Server-Rauchtest mit `-Dmobdefense.d6=true`: Die Server-Prüfungen aus §3 laufen mit, das Modul `mob`
  startet nach 20 Ticks den Lauf `dev.mob.D6Run`. Er lädt die Chunks um 8,8 dauerhaft, stellt die Zeit auf Nacht (Zeit steht,
  keine Spawns, `mobGriefing` aus, Mob-Schaden 0 %, damit die Drohnen bleiben), legt 10 Drohnen im Kreis mit 12 Blöcken Radius
  ab und spawnt 50 Mobs (10 Zombies, 5 Wüstenzombies, 10 Skelette, 5 Eiswanderer, 5 Diener, 5 Plünderer, 5 Hoglins, 5 Phantome)
  20–32 Blöcke vom Mittelpunkt.
- Weil ohne Spieler keine Drohne fliegen kann (Pilot oder autonomer Flug brauchen einen Spieler), setzt D6 mit
  `DroneReactions.setActiveFilter(view -> true)` alle geladenen Drohnen als aktiv. Das gibt es nur im Entwicklertest.
- Ablauf: 200 Ticks Anlauf mit Reaktion, dann abwechselnd je 300 Ticks Reaktion aus und an, drei Paare. Gemessen wird die
  Tick-Zeit des Servers (`MinecraftServer#getTickTimesNanos`) ohne die ersten 60 Ticks jeder Phase; verglichen werden die
  Mediane. Dazu die eigene Zeit des Reaktions-Takts, die größte Zahl Auswertungen je Tick und die Zahl zugeordneter Mobs.
- Ergebnis `<loader>/runs/d6-server/mobdefense-d6-result.txt` (`PASS D6: …` oder `FAIL D6: …`); danach legt der Lauf
  `jointest.stop` ab, der Server-Rauchtest schreibt `mobdefense-selftest-result.txt` und beendet den Server. Gradle prüft
  beide Dateien und das Log auf `ERROR`.

### 3.2 Stand

Messung am 20.10.2026 mit Propwash 0.6.0-dev.4 auf dem Entwicklungsrechner (andere Minecraft-Läufe parallel): D6 Fabric
Median aus 0,73 ms, an 0,91 ms (Δ 0,18 ms, Grenze 2 ms), Reaktions-Takt 0,03 ms je Tick, höchstens 43 Auswertungen je Tick,
50 Mobs gleichzeitig zugeordnet, 1223 Treffer an Drohnen; Server-Prüfungen PASS ohne Client-Klasse. Selbsttest `mob/` Fabric
und NeoForge je 8 von 8 PASS, Fotos `mob_chase`, `mob_brute`, `mob_archer`, `mob_fireball`, `mob_creeper`, `mob_flyer`.

Teil 2 (Strang `md-mobs-2`, 20.10.2026, Propwash 0.6.0-dev.4, auf `main` mit md-booster): JUnit 165 von 165 grün
(`ReactionTagsTest` ohne offene Arten, `StageTwoCoreTest`), `checkPortable` und `tools/fastcheck.sh all` grün. Selbsttest `mob/`
(Teil 1 und 2) Fabric und NeoForge je 20 von 20 PASS; Fotos zusätzlich `mob_stare`, `mob_stare_creaking`, `mob_beam`,
`mob_caster`, `mob_boss`, `mob_ground`, `mob_wind`, `mob_bullet`, `mob_potion`, `mob_jumper`, `mob_trident` (EN und DE). Server-Rauchtest
Fabric und NeoForge PASS mit 26 Prüfungen, die drei Vanilla-Haken angewandt, keine Client-Klasse.

D6 auf NeoForge (22.10.2026, Propwash 0.6.0-dev.7): `:neoforge:runServerD6` mit denselben Tasks und Prüfungen wie auf Fabric.
Median aus 1,45 ms, an 1,66 ms (Δ 0,21 ms, Grenze 2 ms), Reaktions-Takt 0,04 ms je Tick, höchstens 46 Auswertungen je Tick,
46 Mobs gleichzeitig zugeordnet, 1098 Treffer an Drohnen; Server-Rauchtest im selben Lauf PASS mit 27 Prüfungen ohne
Client-Klasse. Die D7-Prüfung läuft im D6-Server mit und braucht dort den Spawnschutz, den sie seit der Korrektur im
Dispenser-Strang für sich selbst einschaltet; ohne diese Korrektur besteht nur die D6-Messung (Δ 0,25 ms).

Budget-Auswahl im portablen Kern (Befund `cov-md-c-1`, 23.10.2026, Propwash 0.6.0-dev.7): `mob.core.Selection` und
`Selection.Pending` statt der privaten Suche in `DroneReactions`, Verhalten gleich. JUnit 199 gefunden, 198 grün, 1 gesperrt
(aus cov-md-b), davon `SelectionTest` 13 von 13 und `CovMdCBudgetSelectionTest` 5 von 5 an der echten Auswahl; `checkPortable`
und `tools/fastcheck.sh all` grün. Selbsttest `mob/` mit den Schritten `Mob-Reaktionen:` aus cov-md-c Fabric und NeoForge je
27 von 27 PASS; „Höchstens etwa ein Dutzend“: 12 zugeordnet, davon unter den 12 nächsten 12, Ziel-Goal bei den 18 entfernten 0,
größte Zahl Auswertungen je Tick 12. D6 Fabric Median aus 1,01 ms, an 1,21 ms (Δ 0,20 ms), höchstens 50 Auswertungen je Tick;
NeoForge Δ −0,75 ms (Rechner unter Last), höchstens 47 Auswertungen je Tick; Server-Prüfungen im selben Lauf je 27 PASS.

Warnung BEDROHUNG (Befund `cov-md-a-5`, 23.10.2026, Propwash 0.6.0-dev.7): `Notices.threat` hatte keinen Aufrufer, jetzt sendet
`mob.Threats` am Ende jedes Reaktions-Takts. Selbsttest „Warnung BEDROHUNG“ vorher FAIL („BEDROHUNG kommt nicht (Pakete 0)“
nach 600 Ticks), nachher PASS auf Fabric und NeoForge mit 1 Paket je Sekunde. JUnit 234 gefunden, 232 grün, 2 gesperrt
(cov-md-a-1, cov-md-b-1), davon `ThreatPulseTest` 7 von 7; `checkPortable` und `tools/fastcheck.sh all` grün. Selbsttest `mob/`,
`covmdb_mob/`, `covmdc_mob/`, `ui/` und „Warnung BEDROHUNG“ Fabric und NeoForge je 51 von 51 PASS. D6 Fabric Median aus 0,78 ms,
an 1,02 ms (Δ 0,24 ms), NeoForge aus 1,03 ms, an 1,20 ms (Δ 0,17 ms); Server-Prüfungen im selben Lauf je 27 PASS.

BEDROHUNG bei Dauerfeuer ins Leere (Befund AUTO3-1, 27.10.2026, Propwash 0.6.0-dev.9): Hält der Pilot am leeren oder
blockierten Dispenser Feuer, sendet der Drohnen-Tick alle 4 Ticks `NO_AMMO` und belegte jeden frei werdenden Platz der
gemeinsamen Grenze von 4 Paketen je Sekunde, bevor `mob.Threats` am Tick-Ende sendet; `BEDROHUNG` kam nie. Jetzt bleibt einer
der 4 Plätze für `THREAT` frei (`Notices.THREAT_RESERVE`, docs/osd-ux.md §2). JUnit `net.ThreatUnderFireTest` vorher rot („0 von
80 Versuchen gesendet“), Selbsttest „Warnung BEDROHUNG bei Dauerfeuer ins Leere“ vorher FAIL auf Fabric („BEDROHUNG kommt nicht
(Pakete 129)“); nachher JUnit 258 gefunden, 257 grün, 1 gesperrt, `checkPortable` und `tools/fastcheck.sh all` grün, der Schritt
PASS auf Fabric und NeoForge (3 Pakete je Sekunde am Client, BEDROHUNG und KEINE MUNITION 100 Ticks zugleich sichtbar).
Selbsttest `mob/`, `covmdb_mob/`, `covmdc_mob/` Fabric und NeoForge je 47 von 47, `ui/` mit beiden BEDROHUNG-Schritten je 6 von 6,
Hinweise des Dispensers und des Speers (S3, S7, S8) Fabric 4 von 4; NeoForge S3 und S8 PASS, S7 erst im dritten Lauf PASS
(zweimal das bekannte „kommt nicht zur Ruhe“ nach dem Vorwärtsflug, die Warnung TNT_LOCKED kam jedes Mal im OSD an).

Nur scharfe Drohnen oder Autopilot (Befund FB1M-1, 09.11.2026, Propwash 0.6.0-rc.3): Propwash setzt `DroneView#flying`,
sobald die Fernbedienung verbunden ist, auch am Boden und ohne Scharfschalten. Der Standardfilter von `DroneReactions` nutzte
genau das; deshalb griffen nachts im Freien Phantome (Phantom-Fenster) und Zombies eine verbundene, nicht scharfe Drohne an,
auch nach einem Absturz, und das OSD zeigte `THREAT`. Jetzt gilt `armed` oder `autopilot`. JUnit `UnarmedDroneTest` vorher 2
von 6 rot, Selbsttest „S10 nicht scharf“ vorher FAIL auf Fabric („THREAT bei nicht scharfer Drohne; nicht scharfe Drohne mit
Pilot angegriffen: Phantom zugeordnet, Zombie zugeordnet, 2 Zuordnungen im Level“); nachher JUnit 293 gefunden, 292 grün,
1 gesperrt, `checkPortable` und `tools/fastcheck.sh all` grün. Selbsttest `mob/`, `covmdb_mob/`, `covmdc_mob/`, beide
BEDROHUNG-Schritte und „Zwei Piloten (simuliert)“ Fabric und NeoForge je 51 von 51 PASS: Angriff endet höchstens 2 Ticks nach
dem Entschärfen, `THREAT` nach 30 Ticks weg. „Gelandet während des Flugs“ und „Zwei Piloten (simuliert)“ schalten die Drohne
des Piloten jetzt am Boden scharf. Server-Rauchtest Fabric und NeoForge PASS mit 26 Prüfungen.
