# Tests – Propwash: Mob Defense

Grundlage: `docs/design.md` §6 (Tests) und §11 (Stränge). Dieses Dokument beschreibt die Werkzeuge: Selbsttest-Läufer,
Anmeldung der Schritte je Modul, Server-Rauchtest (D1), Beitrittstest mit und ohne Add-on (D2), `tools/fastcheck.sh`
und den Web-Export. Minecraft-Läufe auf dem Entwicklungsrechner immer über das Slot-Skript außerhalb des Repositorys
starten (`<slot-skript> ./gradlew …`); die Ports der Server leitet `prepareServerSmoke` aus `PROPWASH_MC_SLOT` ab
(`25631 + Slot`, beide Loader gleich).

---

## 1. Schnell ohne Spiel

| Befehl | Prüft |
|---|---|
| `./gradlew :common:test` | JUnit (`common/src/test`, sieht auch das Quell-Set `devtest`) |
| `./gradlew :common:checkPortable` | portable Kerne `*/core` mit `--release 21` ohne Klassenpfad (läuft auch in `check`) |
| `./gradlew build` | alles, dazu die Quell-Sets `devtest` und `jointest` beider Loader |
| `tools/fastcheck.sh test` | JUnit ohne Gradle (Sekunden statt Minuten) |
| `tools/fastcheck.sh all` | `common`, `fabric`, `neoforge` (übersetzt `main`, `devtest`, `jointest`, prüft JSON) und `checkPortable` |
| `tools/fastcheck.sh checkPortable` | nur die portablen Kerne, wie die Gradle-Aufgabe |

`tools/fastcheck.sh` braucht einmal je Arbeitsbaum `./gradlew writeClasspath` (schreibt `.classpath/*.txt` und den
JUnit-Konsolenstarter `.classpath/junit-console.jar`, beides ignoriert). Fehlt `.classpath/` im Arbeitsbaum, nimmt das
Skript die des Hauptbaums. JUnit-Optionen gehen durch: `tools/fastcheck.sh test --select-class de.lardtom.mobdefense.dev.SelfTestSelectionTest`.
Ein anderes JDK: `MD_JDK=<pfad>`; die Zahl der Ausgabezeilen: `MD_TAIL=<n>`.

---

## 2. Selbsttest (`devtest`, Fabric und NeoForge)

```sh
./gradlew :fabric:runClientSelftest                                         # alle Schritte
./gradlew :fabric:runClientSelftest -PmobdefenseSelftestOnly='dispenser/'   # nur ein Modul
./gradlew :fabric:runClientSelftest -PmobdefenseSelftestOnly='Probe,!Eimer' -PmobdefenseScreenshots=true
./gradlew :neoforge:runClientSelftest -PmobdefenseSelftestOnly='core/'
```

- **Welt.** Der Läufer legt vom Titelbildschirm aus eine neue flache Kreativwelt „Mob Defense Selftest“ an (Seed 1,
  ohne Strukturen, Tageszeit, Wetter und Mob-Spawns aus) und löscht vorher alte Welten dieses Namens.
- **Schritte** heißen `mobdefense: <was>`, der Präfix kommt automatisch dazu. Jeder Schritt gehört einem Modul (Ordner
  `dev/<modul>/`). Am Ende läuft immer der Basis-Schritt `mobdefense: Aufräumen – …`, sobald mindestens ein Schritt
  ausgewählt ist.
- **Filter** `-PmobdefenseSelftestOnly=<liste>` (oder Umgebungsvariable `MOBDEFENSE_SELFTEST_ONLY`): Einträge getrennt
  durch `,` oder `|`. `<präfix>` wählt Schritte, deren Name mit dem Präfix beginnt, mit oder ohne `mobdefense: `, ohne
  Groß-/Kleinschreibung; `<modul>/` wählt alle Schritte eines Moduls; `!<präfix>` bzw. `!<modul>/` nimmt Schritte heraus.
  Passt kein Schritt, endet der Lauf mit `FAIL kein Schritt passt zur Auswahl`.
- **Ergebnis.** `fabric/runs/selftest-client/mobdefense-selftest-result.txt` (bzw. `neoforge/…`) beginnt mit `PASS` oder
  `FAIL`; Gradle bricht bei `FAIL` oder fehlender Datei ab. Die Schrittliste mit Zeit und Meldung je Schritt steht in
  `mobdefense-selftest-steps.txt` daneben, das Log in `logs/latest.log` (Zeilen `[Mob-Defense-Selbsttest]`).
- **Bildschirmfotos** nur mit `-PmobdefenseScreenshots=true`: jedes Foto auf Englisch und auf Deutsch (`<name>.png`,
  `<name>_de.png`) im Run-Ordner unter `screenshots/selftest/`. `-PmobdefenseScreenshots=<ordner>` schreibt stattdessen
  dorthin (relativ zum Repository, z. B. `docs/screenshots/selftest`). Für Fotos Fabric nehmen; NeoForge kann es auch.
- **Aufräumen.** Nach jedem Schritt laufen erst sein eigenes Aufräumen (falls angegeben), dann `Arena.reset`: Bildschirm
  zu, Tasten los, Sprache und Fenstergröße zurück, Overlays weg; am Server alle über `Arena` gesetzten Blöcke zurück,
  über `Arena` gespawnte Entities, Drohnen und Items im Umkreis von 128 Blöcken weg, jede geänderte Gamerule (auch die
  von Add-ons) auf den Stand beim Betreten, Inventar und Rüstung leer, Effekte weg, Kreativmodus, volle Gesundheit,
  zurück zum Startpunkt. Der Basis-Schritt prüft am Ende genau das (und dass jede Gamerule `mobdefense:*` auf ihrem
  Standard steht, kein Flug läuft und keine Taste gedrückt ist).
- **Zeitgrenzen.** Jeder Schritt hat seine eigene Grenze in Ticks; alle zusammen dürfen die Summe der Grenzen plus 60 s
  nicht überschreiten. Ein Wächter-Thread beendet ein hängendes Spiel (10 min bis zur Welt, danach doppelte Zeitgrenze
  plus 5 min) und schreibt `FAIL Wächter …`.
- **Rauchtest** `./gradlew :fabric:runClientSmoke` (bzw. `:neoforge:`): Welt anlegen, betreten, verlassen, keine Schritte.

### 2.1 Eigene Schritte eines Moduls

Jedes Modul legt seine Klassen in `common/src/devtest/java/de/lardtom/mobdefense/dev/<modul>/` an, darunter eine Klasse,
die `dev.DevModule` umsetzt, und hängt **eine Zeile** an `dev.DevModules#all` an (voller Klassenname, kein Import):

```java
modules.add(new de.lardtom.mobdefense.dev.dispenser.DispenserModule());
```

```java
public final class DispenserModule implements DevModule {
    public String name() { return "dispenser"; }                                  // = Ordnername, [a-z0-9_]

    public void clientSteps(SelfTest.Steps steps) {                              // nur im Selbsttest-Client
        DispenserSteps.register(steps);
    }

    public void serverChecks(ServerChecks checks) {                              // nur im Server-Rauchtest (D1)
        checks.add("Abklingzeit am Server", 40, server -> "…");                  // Text = Zusammenfassung, Ausnahme = FAIL
    }
}
```

- `steps.add(was, maxTicks, body)` bzw. `steps.add(was, maxTicks, body, aufräumen)`; `body` liefert je Tick
  `StepResult.pending()`, `pass()`, `pass(text)` oder `fail(grund)`. Namen müssen über alle Module eindeutig sein.
- `dev.client.Script` reiht Stufen aneinander: `.server(label, (server, state) -> …)` läuft im Server-Thread,
  `.check`, `.run`, `.await(label, ticks, bedingung)`, `.sleep(ticks)`, `.append(…)`.
- `dev.client.Arena`: `player`, `level`, `place`/`fill` (Blöcke, werden zurückgesetzt), `spawn`/`track` (Entities,
  werden entfernt), `rule(server, id, wert)` (Gamerule setzen und prüfen), `stack(id)`, `command`, `ground(dx, dz)`.
- `dev.client.Shots.photos(name[, vorher])` macht EN und DE, `Shots.language(…)`/`restoreLanguage(…)` wechseln die
  Sprache, `Shots.window(ctx, ticks, breite, höhe, gui)` stellt eine Fenstergröße ein (wird nach dem Schritt
  zurückgestellt).
- Klassen mit Client-Code nur aus `clientSteps` erreichen: Der Server-Rauchtest lädt jedes `DevModule` und prüft, dass
  keine Client-Klasse geladen wird (§3).
- Vorlage: Modul `probe` (`dev/probe/`) mit einem Schritt und einer Server-Prüfung.

---

## 3. Server-Rauchtest (D1)

```sh
./gradlew :fabric:runServerSmoke
./gradlew :neoforge:runServerSmoke
```

Dedizierter Server mit Propwash und Mob Defense in einer flachen Welt, 100 Ticks. Geprüft:

| Prüfung | Erwartung |
|---|---|
| Add-on | Mob Defense gestartet, Version von Mob Defense und Propwash bekannt, Propwash-API erfüllt `REQUIRED_API` |
| Teile | Teileregister eingefroren, jedes Teil `mobdefense:*` hat ein Item |
| Welt-Regeln | jede Gamerule `mobdefense:*` steht in `RuleDefaults` mit gleichem Typ, Standard und Bereich und hat in der neuen Welt ihren Standard; sobald eine angemeldet ist, müssen alle da sein (außer `sculk_hears_drones`) |
| PW3-Tabelle | `data/mobdefense/propwash_share/table.json` gelesen: `format` 1, `namespace` `mobdefense`, Ids ohne Namensraum, jedes Teil hat einen Eintrag. Ohne Teile darf die Tabelle fehlen |
| Module | `serverChecks` aller Module zu ihrem Tick |
| kein ERROR | Gradle prüft `logs/latest.log` auf `ERROR`, `FATAL` und Mixin-Fehler |
| keine Client-Klasse | die JVM schreibt jede geladene Klasse nach `runs/smoke-server/mobdefense-classload.log` (`-Xlog:class+load`); keine darf aus `net.minecraft.client`, `com.mojang.blaze3d` oder einem `client`-Paket von Mob Defense stammen. Fehlt `de.lardtom.mobdefense.MobDefense` im Protokoll, gilt es als unvollständig |

Ergebnis: `runs/smoke-server/mobdefense-selftest-result.txt`, eine Zeile `PASS dedizierter Server, … | <prüfung>: <zusammenfassung> | …`.

---

## 4. Beitritt mit und ohne Add-on (D2)

Zwei Prozesse: Server zuerst, Client erst, wenn der Server lauscht (Port aus „Starting Minecraft server on *:<port>“).

```sh
# Server (hält bis jointest.stop im Run-Ordner liegt, höchstens <sekunden>)
MOBDEFENSE_JOINTEST_HOLD=1800 <slot-skript> ./gradlew :fabric:runServerSmoke

# Client mit Mob Defense
MOBDEFENSE_JOINTEST=127.0.0.1:<port> MOBDEFENSE_JOINTEST_LABEL=mit-addon MOBDEFENSE_JOINTEST_EXPECT='joined' \
  <slot-skript> ./gradlew :fabric:runClientSmoke

# Client ohne Mob Defense (nur Propwash und die Hilfs-Mod mobdefense_jointest)
MOBDEFENSE_JOINTEST=127.0.0.1:<port> MOBDEFENSE_JOINTEST_LABEL=ohne-addon MOBDEFENSE_JOINTEST_EXPECT='<erwartung>' \
  <slot-skript> ./gradlew :fabric:runClientNoAddon

touch fabric/runs/smoke-server/jointest.stop
```

- Der Client verbindet sich vom Titelbildschirm, wartet, bis er in der Welt ist oder ein Bildschirm 1,5 s stehen
  bleibt, schreibt Klasse, Titel, alle Texte und `detailsText()` nach `runs/<run>/jointest/<fall>.txt`, ein
  Bildschirmfoto `<fall>.png` daneben und die Zeile `mobdefense_client=present|absent`. Bei einem Beitritt steht am
  Anfang `joined`.
- `MOBDEFENSE_JOINTEST_EXPECT=<a>|<b>|!<c>`: verlangte Textstellen ohne Groß-/Kleinschreibung, `!` = darf nicht vorkommen.
  Zusätzlich muss `runClientSmoke` Mob Defense enthalten und `runClientNoAddon` nicht; sonst `FAIL`.
- Erwartung ohne Add-on: Solange Mob Defense keine Teile anmeldet, lässt Propwash den Spieler herein (`joined`). Mit
  Teilen zeigt Propwash die Übersicht, dass Mob Defense fehlt: `MOBDEFENSE_JOINTEST_EXPECT='Mob Defense|!joined'`.
- Der Server schreibt jeden Beitritt als `[Beitrittstest] <name> beigetreten` ins Log; sein Ergebnis (D1 plus Zahl der
  Beitritte) steht wie in §3 in `runs/smoke-server/mobdefense-selftest-result.txt`.
- NeoForge genauso mit `:neoforge:runServerSmoke`, `:neoforge:runClientSmoke` und `:neoforge:runClientNoAddon`.
- Die Hilfs-Mod `mobdefense_jointest` (Quell-Set `jointest` in `fabric/` und `neoforge/`, Code in `dev/join/`) hängt
  bewusst nicht von Propwash ab und wird nur in `runClientNoAddon` geladen.
  Auf NeoForge laden alle anderen Runs deshalb nur `mobdefense` (`loadedMods`); ohne diese Einschränkung lädt
  ModDevGradle jede Mod aus `neoForge.mods` in jeden Run, und `dev.join` läge in zwei Mods (FML bricht ab).

---

## 5. Web-Export

```sh
./gradlew webExport          # = :fabric:runWebExport, schreibt nach build/web-export/
```

Format und Prüfungen: `docs/web-export.md`. Ergebnis in `fabric/runs/web-export/mobdefense-selftest-result.txt`.

---

## Abdeckung cov-md-b

Prüfplan 0.6.0, Teil 6 Nr. 23 und Teil 7 (Dispenser-Verhalten, Speer, Booster, Welt-Regeln, Server, Sharecode). Alle Klassen
beginnen mit `CovMdB` und liegen im Paket der geprüften Funktion; angemeldet sind fünf Module in `dev.DevModules`:

```sh
./gradlew :fabric:runClientSelftest -PmobdefenseSelftestOnly='covmdb_dispenser/,covmdb_spear/,covmdb_booster/,covmdb_mob/,covmdb_core/' -PmobdefenseScreenshots=true
./gradlew :neoforge:runClientSelftest -PmobdefenseSelftestOnly='covmdb_dispenser/,covmdb_spear/,covmdb_booster/,covmdb_mob/,covmdb_core/'
```

| Modul | Schritte |
|---|---|
| `covmdb_dispenser` | Windkugel fängt sich (autonomer Schwebeflug, Knüppel in der Mitte, Lage unter 15° in 2 s, Stoß mindestens die Hälfte von Impuls/Masse), Windkugel an der Wand (1,5 Blöcke, Versatz größer als ins Freie, Windstoß über `DRONE_KNOCKBACK`, Platzpunkt und Fangen), Block-Verhalten wie Vanilla (je Fall eine Drohne auf einem Sockel, Probe-Schuss für den Mündungsblock, Würfe im Abstand von 3 Ticks wegen der Ratenbegrenzung), Eimer gesperrt (jeder Eimer aus dem Register), leerer Eimer schöpft, TNT mit `tnt_explodes false`, Pilot als Verursacher (Testspieler, Pfeil und Speer), zwei Piloten simuliert (die Pilotensitzung eines Testspielers ohne Client endet sofort, deshalb fliegt Drohne B autonom mit dem Spieler des Clients als Besitzer, Aktionen an B löst der Testspieler aus; Drohne A wird am Boden scharf geschaltet, weil Mobs nur auf scharfe Drohnen oder Autopilot reagieren; erst wenn beide Drohnen fliegen und B ruhig schwebt, je fünf Zombies vor jeder Drohne) |
| `covmdb_spear` | Treffer-Sperre (Zombie mit 5 m/s, Riese mit 4,8 m/s; die Haltbarkeit zählt jeden Stich), Haltbarkeit und OSD `SPR …%` mit Goldspeer, Bruch mit Ton, Verzauberungen, Rückstoß und Hühnerjockey, Vorbeiflüge bis zum Tod (Bereich 2 bis 3 mit Speerschaden 100 %), Fotos Werkbank und Third Person |
| `covmdb_booster` | Flug beenden beim Brennen (Taste `key.propwash.drone_exit`), alle drei Teile an der 7″, Vergleich mit der Vanilla-Elytra-Rakete (Modell `CovMdBElytraReference` aus den Formeln von `FireworkRocketEntity` und `LivingEntity#updateFallFlyingMovement`, Blick waagerecht, Lebensdauer über alle Zufallswerte gemittelt), Bildfolge der Funken `cov_booster_sparks_01` bis `_08` |
| `covmdb_mob` | `mob_reaction_range` 8 und 64, `mob_damage_to_drones_percent` 0, 100 und 400; Mob-Interaktionen (Befund OT4M-1, `dev.mob.client.CovMdBMobInteractionSteps`): Wächter und Großer Wächter im Becken neben der Drohne über Gras (Becken ab 3 m, damit auch ein zum Grund gesunkener Wächter sie sieht) treffen nach 80 bzw. 60 Ticks mit 1 bzw. 3 Magieschaden, Shulker-Geschoss hebt die autonom schwebende Drohne trotz Höhenhalten 10 s lang an und sie sinkt danach zurück, abgestellte Drohne bekommt nach einem Treffer (direkt über `DamageSources#mobProjectile`, ein fliegendes Geschoss trifft die flache Drohne am Boden nicht immer) keine Schwebekraft und bleibt liegen, mit Pilot `THREAT` am Client, Laser nach der Ladezeit und Auftrieb im Flugmodell des Clients; einzeln mit `-PmobdefenseSelftestOnly='Wächter-Laser,Shulker-Geschoss,Mit Pilot: Wächter'` |
| `covmdb_core` | Abschnittsname im Tab „Welt-Regeln“ EN/DE (Foto), Sharecode über Werkbank und Sharecode-Fenster (Zwischenablage wird danach zurückgesetzt), Import mit `/propwash code give`, Fotos der Modelle neben Propwash-Teilen |

JUnit: `dispenser.CovMdBFluidBucketTagTest`, `spear.core.CovMdBContactCooldownTest`, `spear.CovMdBSpearCooldownTest`,
`booster.CovMdBElytraReferenceTest`, `mob.core.CovMdBMobInteractionTest`.

Gesperrt bis zum Fix (fertig in der Klasse, ohne Anmeldung bzw. mit `@Disabled`): Windkugel an der Wand mit 2,5 Blöcken
(`CovMdBWindSteps#wallStep(WALL_DISTANCE, …)`, der Windstoß platzt 2,6 m von der Drohne und erreicht sie nicht), die
Speer-Verbrennung (`CovMdBSpearSteps#fireAspect`) und `CovMdBFluidBucketTagTest#everyRegisteredBucketIsInTheTag`.

Zwei echte Clients am dedizierten Server (Prüfplan 7.19 „Zwei Piloten gleichzeitig“): Server und beide Clients mit
`MOBDEFENSE_JOINTEST_COV_TWO_PILOTS=1` starten. Sobald zwei Spieler da sind, stellt der Server sie in den Kreativmodus (sonst
greifen die Zombies ihre Körper an), stellt je eine Drohne mit Dispenser und Speer-Halterung für jeden, startet beide Flüge und
beschwört zehn Zombies. Jeder Client schaltet seine Drohne am Boden scharf (Mobs reagieren nur auf scharfe Drohnen). Der Server
wartet darauf höchstens 600 Ticks und danach höchstens weitere 600 Ticks, bis sich die Zombies auf beide Drohnen verteilen und
ihnen im Mittel um mindestens 1 m näher gekommen sind (Mob-Reaktion). Erst dann feuert er je Drohne mit der Aktion ihres Piloten,
schreibt `mobdefense-cov-two-pilots.txt` in den Run-Ordner und beendet beide Flüge 40 Ticks später. Geht ein Pilot vor dem
Ergebnis, ist das Ergebnis `FAIL`; jede Abmeldung danach steht als eigene Zeile `getrennt nach dem Ergebnis: …` darunter.

Mit dieser Variablen bleibt der Beitrittstest-Client nach dem Beitritt in der Welt (`JoinProbe.stayUntil`, höchstens 240 s),
bis der Server seinen Flug beendet; erst dann trennt er sich. Er schreibt nach `jointest/cov-two-pilots-<fall>.txt`, ob er die
eigene Drohne fliegt, das OSD am Anfang und am Ende (ein Schuss weniger), nach wie vielen Ticks sie scharf war und wann der
Server den Flug beendet hat. Verliert er die Verbindung vorher, endet sein Beitrittstest mit `FAIL`. Gemischt mit einem
NeoForge-Client am Fabric-Server:

```sh
MOBDEFENSE_JOINTEST_HOLD=1800 MOBDEFENSE_JOINTEST_COV_TWO_PILOTS=1 <slot-skript> ./gradlew :fabric:runServerSmoke
# je in einem eigenen Prozess, sobald der Server lauscht
MOBDEFENSE_JOINTEST=127.0.0.1:<port> MOBDEFENSE_JOINTEST_LABEL=two-fabric MOBDEFENSE_JOINTEST_EXPECT=joined \
  MOBDEFENSE_JOINTEST_COV_TWO_PILOTS=1 <slot-skript> ./gradlew :fabric:runClientSmoke
MOBDEFENSE_JOINTEST=127.0.0.1:<port> MOBDEFENSE_JOINTEST_LABEL=two-neoforge MOBDEFENSE_JOINTEST_EXPECT=joined \
  MOBDEFENSE_JOINTEST_COV_TWO_PILOTS=1 <slot-skript> ./gradlew :neoforge:runClientSmoke
touch fabric/runs/smoke-server/jointest.stop
```

Stand 22.10.2026: Fabric 24 von 24 Schritten PASS (26 Bildschirmfotos), NeoForge 24 von 24 PASS, JUnit 175 gefunden, 174 grün,
1 gesperrt; Server-Rauchtest und Beitrittstest mit Add-on auf Fabric und NeoForge PASS.

---

## Abdeckung cov-md-c

Prüfplan 0.6.0, Teil 7.15 und 7.16 (Mob-Reaktionen) und Nr. 152 (Kennzahlen für die Fairness der Mobs). Teil 7.22 (Sculk und
Warden) bleibt offen, solange der optionale Strang `md-sculk` nicht in `main` ist. Alle Klassen beginnen mit `CovMdC` und
liegen im Paket der geprüften Funktion; angemeldet ist das Modul `covmdc_mob` in `dev.DevModules`:

```sh
./gradlew :fabric:runClientSelftest -PmobdefenseSelftestOnly='covmdc_mob/' -PmobdefenseScreenshots=true
./gradlew :neoforge:runClientSelftest -PmobdefenseSelftestOnly='covmdc_mob/'
```

| Gruppe | Schritte |
|---|---|
| `Mob-Reaktionen:` (`dev.mob.client.CovMdCRuleSteps`) | Nur fliegende Drohnen (abgestellte Drohne, 5 Zombies, 1200 Ticks im Zeitraffer `/tick sprint`: keine Zuordnung, kein Ziel-Goal, kein Treffer), Gelandet während des Flugs (Pilotensitzung über `Pilot.fly()` ohne Start, am Boden scharf geschaltet: Zombie trifft binnen 200 Ticks, Silberfischchen und Endermite beißen die gelandete Drohne), Sichtlinie (Steinwand: 100 Ticks kein Pfeil, Wand weg: Pfeil), Höchstens etwa ein Dutzend (30 Zombies ohne Eigenbewegung in 3 bis 17,5 m: genau die 12 nächsten zugeordnet, die 18 übrigen ohne Ziel-Goal, höchstens 64 Auswertungen je Tick), Heimflug (5″ mit Pilot 30 m vom Startpunkt, drei Skelette, Taste `key.propwash.rescue`: im Heimflug neue Pfeile auf die Drohne), Spieler bleibt erstes Ziel (Testspieler aus `Guests` im Überleben 20 m hinter dem Zombie: Drohne in 15 m keine Zuordnung, in 7 m Wechsel, danach läuft der Zombie zur Drohne), Datenpaket (siehe unten) |
| `Mob je Art:` (`dev.mob.client.CovMdCKindSteps`) | Ertrunkener ohne Dreizack und Zombienautilus im Becken, Ertrunkener mit Dreizack (20 m nichts, 14 m gerichteter Wurf und Treffer), Diener und Witherskelett, danach Piglin-Barbar allein, Zombifizierter Piglin (ruhig, nach dem Schlag des Testspielers wütend und an der Drohne), Verwüster (Brüllen: waagerechter Stoß aus mehr als 2,5 m), Hoglin und Zoglin je allein (Stoß nach oben), Plünderer, Illusionist und Piglin mit Armbrust (eigene Pfeile auf die Drohne gerichtet), Hexe (12 m nichts, 8 m Treffer mit Schaden 6 × (1 − d/4) bei d ≤ 2), Magier (14 m nichts, Fangzähne unter der Drohne auf 1,5 m, höher keine neuen Fangzähne, Plagegeister jagen die Drohne), Creeper mit `mob_griefing false` (Blockzahl gleich, Explosionsstoß mit Skala 1), Schwefelwürfel (Stoß ohne Schaden), Schleim und Magmawürfel (Treffer auf 2,5 m, keine Zuordnung auf 5,5 m), Spinnen (Treffer auf 2 m, frei in 9 m keine Zuordnung, an der Wand klettern beide hoch und treffen), Wächter (erster Schaden frühestens 80 Ticks nach der Zuordnung), Knarz (Abstand sinkt ohne Kamerablick, steht im Blick 60 Ticks, läuft danach wieder), Wither mit `mob_reaction_range 48` (36 m nichts, 28 m gerichteter Schädel), Endermite (Biss auf 0,7 m, keine Zuordnung auf 3 m) |
| `Fairness:` (`dev.mob.client.CovMdCFairnessSteps`) | Reaktionsradius (Zombie in 20 m reagiert, Zombie in 28 m und Skelett in 27 m nicht), drei Skelette 15 m vor der schwebenden 5″ am Tag (Treffer pro Minute im Zeitraffer, dann fünf Versuche mit neuer Drohne und vollem Mob-Schaden: Zeit vom Einschalten der Reaktion bis zum Ausfall), Lohe in 10 m (Trefferquote der kleinen Feuerkugeln in einer Minute) |

- `dev.mob.client.CovMdCLab` zeichnet `DRONE_DAMAGED` (mit Spielzeit und Id des Geschosses) und `DRONE_KNOCKBACK` (mit dem
  nächsten Mob der Szene) selbst auf und protokolliert Geschosse und Fangzähne je Server-Tick über `MobDefense.onServerTick`;
  so stimmen Zählungen auch im Zeitraffer. Der Schwebeflug läuft über `autonomy().start` mit absoluter Höhe; Mobs ohne
  Eigenbewegung bekommen Tempo 0 (`freeze`), Spinnen im Wand-Schritt Resistenz V gegen Stürze und Drohnen-Stöße.
- Datenpaket (Nr. 92): `dev.mob.CovMdCPack` kopiert `common/src/devtest/resources/cov/cov-md-c/ignores_zombie/` (Tag
  `mobdefense:ignores_drones` mit `minecraft:zombie`) in den Ordner `datapacks/` der Welt, `/reload` schaltet es ein; am Ende
  `datapack disable "file/covmdc_ignores_zombie"` und Löschen. Dieselbe Prüfung am dedizierten Server (`dev.mob.CovMdCPackCheck`,
  geparkte Drohne über `DroneReactions.setActiveFilter` als aktiv) nur mit Schalter und Haltezeit:

```sh
MOBDEFENSE_COV_MDC_PACK=1 MOBDEFENSE_JOINTEST_HOLD=600 <slot-skript> ./gradlew :fabric:runServerSmoke
MOBDEFENSE_COV_MDC_PACK=1 MOBDEFENSE_JOINTEST_HOLD=600 <slot-skript> ./gradlew :neoforge:runServerSmoke
```

  Ergebnis `runs/smoke-server/mobdefense-covmdc-pack.txt`; die Prüfung legt danach `jointest.stop` ab, Fehler stehen als
  `ERROR` im Log.
- JUnit: `mob.core.CovMdCSwitchRuleTest` (Wechselregel mit den Prüfplan-Zahlen 20, 15 und unter 8 m, Grenze genau bei der
  Hälfte), `mob.core.CovMdCBudgetSelectionTest` (die echte Auswahl `mob.core.Selection` und `Selection.Pending` mit `Budget` und
  `ReactionLimits`: die 12 nächsten, abgelehnte Mobs kosten eine Auswertung, Fortsetzung im nächsten Tick, gestaffelt nie mehr
  als 64 Auswertungen je Tick).
- Kennzahlen für Nr. 152 nennt `docs/design.md` nicht; die Schritte prüfen einen Vorschlag: Treffer pro Minute 10 bis 40,
  kürzeste Überlebenszeit mindestens 2 s, Lohen-Quote 50 bis 100 % bei mindestens 12 Feuerkugeln je Minute.

Stand 23.10.2026 mit Propwash 0.6.0-dev.7: Fabric und NeoForge je 26 von 26 Schritten PASS (je zwei volle Läufe des Moduls,
10 Bildschirmfotos auf Fabric); Server-Prüfung Datenpaket auf Fabric und NeoForge PASS, Server-Rauchtest ohne Schalter beider
Loader PASS; JUnit 186 gefunden, 185 grün, 1 gesperrt (aus cov-md-b). Gemessen: drei Skelette 20 bis 31 Treffer je Minute bei
57 bis 59 Pfeilen, Überlebenszeit 2,3 bis 8,7 s (in etwa einem Drittel der Versuche fällt die 5″ schon der ersten Salve nach
etwa 2,4 s, zwei Pfeiltreffer erreichen die Bruchgrenze von Propwash), Lohe 20 von 20 Feuerkugeln.

---

## Abdeckung cov-md-a

Prüfplan 0.6.0, Teil 6 Nr. 9 und 16 und Teil 7 (Teile, Werkbank, Beladen, Bedienung, Geschosse, OSD, Flugschule und
Fortschritte). Alle Klassen beginnen mit `CovMdA`; angemeldet sind drei Module in `dev.DevModules`:

```sh
./gradlew :fabric:runClientSelftest -PmobdefenseSelftestOnly='covmda_core/,covmda_ui/' -PmobdefenseScreenshots=true
./gradlew :fabric:runClientSelftest -PmobdefenseSelftestOnly='covmda_dispenser/' -PmobdefenseScreenshots=true
./gradlew :neoforge:runClientSelftest -PmobdefenseSelftestOnly='covmda_core/,covmda_ui/,covmda_dispenser/'
```

| Modul | Schritte |
|---|---|
| `covmda_core` | Kreativ-Reiter (eigener Reiter mit genau den drei Teilen, im Propwash-Reiter keines davon, dort aber Greifer und LED-Streifen; Fotos beider Reiter), Rezeptbuch im Überlebensmodus (ohne Zutat leer, Eisennugget, Kupfernugget, Gruppe wie Greifer und LED-Streifen), Werkbank belegte Plätze (Tausch über das Menü, alte Teile im Inventar), Whoop ohne Halterung vorn mit Regel an und aus (Tooltip am Platz), Lackierung (Servo und Gehäuse getrennt, nach dem Bauen erhalten), Just More Parts (nur mit JMP-Jar im Run-Ordner `mods/`, sonst Hinweis), Zubehör-Inventar (Slots 4/1/1, Beladung … g, Shift-Klick) |
| `covmda_dispenser` | Pfeilarten, Schneeball/Ei/Erfahrungsfläschchen, Feuerkugel, Rakete als Geschoss, Item ohne Verhalten, Schadenstrank, TNT-Zündzeit, Kamera-Uptilt 30°, Lauf blockiert und leer (Steinblock erst nach dem Flugstart in den Block der Mündung, die Propwash bei einem leeren Schuss meldet, Schuss im selben Tick; Warnung, Ton `dispenser.fail`), Taster und Schalter der Funke (simuliert über `triggerAction`), Tastatur (Ziffernblock 9), leichter werdende Drohne, Dauerfeuer auf neun Zombies mit Probeschuss, Drohnentempo, 7″ gegen 5″ mit Windkugel, eigene Pfeile und Schneebälle mit 25 m/s, eigene Windkugeln (eine eingeholte eigene Windkugel fliegt seit Propwash 0.6.0-dev.8 ohne Zusammenstoß durch die Drohne, je 10 mit 25 m/s vorwärts und im Steigflug; was trotzdem ankäme, wehrt `OwnShotGuard` ab) |
| `covmda_ui` | Tab „Modi“ (Zeile anklicken, Name in der Detailzeile, EN und DE), OSD-Standard nach dem ersten Flug, Slot-Übersicht (Messfühler-Element `mobdefense:covmda_probe`), Flugschule über die Taste M je Teil, Fortschritte Luftunterstützung, Rückstoß und Nachbrenner, Anzeigen im Sichtflug (erst mit Propwash nach 0.6.0-dev.7), Warnung BEDROHUNG (Skelett mit Bogen neben der schwebenden 5″ mit Pilot, höchstens 4 Pakete je Sekunde, ohne Skelett weg), Warnung BEDROHUNG bei Dauerfeuer ins Leere (Ziffernblock 9 am leeren Dispenser gehalten, dann das Skelett: BEDROHUNG und KEINE MUNITION zugleich, Befund AUTO3-1), Fortschritt Lanzenflug (Speer-Stich mit Pilot besiegt einen Zombie) |

JUnit: `data.CovMdARecipeBookTest`, `data.CovMdAJustMorePartsTest` und `data.CovMdARecipeConflictTest` (Abzüge der 22 Rahmen
und 146 Werkbank-Rezepte von Just More Parts 1.2.0 in `common/src/test/resources/cov/cov-md-a/`; mit `MOBDEFENSE_JMP_JAR=<jar>`
zusätzlich Vergleich mit dem Jar),
`data.CovMdALangTest`, `dispenser.CovMdAPayloadTest`, `dispenser.core.CovMdAFireGateTest`, `spear.CovMdASpearPayloadTest`,
`client.CovMdAOsdDesignTest`.

- Bildschirmtexte liest `dev.ui.client.CovMdAScreenText`: Der Bildschirm wird in einen eigenen `GuiRenderState`
  extrahiert, die Texte kommen per Reflection aus `GuiTextRenderState` (Vanilla, nicht Propwash).
- Die Funke lässt sich über die API nicht simulieren; Taster und Schalter laufen über
  `PropwashApi.accessories().triggerAction` (Puls 2 Ticks bzw. 40 Ticks gehalten).
- Anzeigen im Sichtflug (`CovMdAUiSteps.registerLineOfSight`, Befund cov-md-a-2) ist in `covmda_ui` angemeldet, meldet
  den Schritt aber erst mit einer Propwash-Version nach 0.6.0-dev.7 an (`CovMdAUiSteps.LINE_OF_SIGHT_AFTER`). Die
  Ursache liegt in Propwash: Bis 0.6.0-dev.7 ruft Propwash die OSD-Elemente der Add-ons im Sichtflug nicht auf. Ohne
  diese Grenze endet der Schritt gegen 0.6.0-dev.7 auf Fabric und NeoForge mit FAIL „Propwash ruft die OSD-Elemente der
  Add-ons nicht wie erwartet auf … letzter Aufruf nie“. Mit 0.6.0-dev.7 steht stattdessen im Log „Schritt „Anzeigen im
  Sichtflug“ nicht angemeldet …“; mit dem Umstieg auf eine neuere Propwash-Version läuft er von selbst.
- Gesperrt bis zum Fix: keiner mehr. `CovMdAFlightSteps.registerOwnWindCharges` ist seit dem Fix zu cov-md-a-4
  angemeldet, `CovMdAUiSteps.registerThreat` (BEDROHUNG) seit dem Fix zu cov-md-a-5,
  `CovMdAUiSteps.registerThreatUnderFire` (BEDROHUNG bei Dauerfeuer ins Leere) seit dem Fix zu AUTO3-1 und
  `CovMdAUiSteps.registerLanceFlight` (Fortschritt Lanzenflug) seit dem Fix zu cov-md-a-3.

Stand 23.10.2026 nach dem Rebase auf main (mit cov-md-c): Fabric und NeoForge je 29 von 29 Schritten PASS (Fabric 26
Bildschirmfotos), JUnit 227 gefunden, 225 grün, 2 gesperrt (cov-md-b-1 und cov-md-a-1).

Seit dem Fix zu cov-md-a-1 läuft `CovMdAPayloadTest#fourPotionsOrFourTntSlowEveryDroneAndASevenInchWithLipoCarriesThemBest`
mit (vorher `fourPotionsOrFourTntOnlyMakeSenseOnASevenInch`, `@Disabled`). Die Gewichte aus design §2.1.4 bleiben. Wie viel
eine Drohne trägt, bestimmt die Physik von Propwash. Der Test vergleicht die 5″, die Long-Range-7″ mit Li-Ion und eine 7″
mit LiPo (`b6s_1300`) mit Halterung: Schub/Gewicht leer 8,28, 4,93 und 9,44, mit vier Tränken (1400 g) 2,69, 2,05 und
3,40, mit vier TNT (2400 g) 1,82, 1,44 und 2,33. Jede Drohne fällt mit beiden Lasten auf höchstens die Hälfte, die 7″
mit LiPo trägt am besten (ab 1,5), die Long-Range-7″ schlechter als die 5″.

## Owner-Playtest 23.10. (Modul `ownm_dispenser`, Befunde OWNM-1 bis OWNM-3)

```sh
./gradlew :fabric:runClientSelftest -PmobdefenseSelftestOnly='ownm_dispenser/' -PmobdefenseScreenshots=true
./gradlew :neoforge:runClientSelftest -PmobdefenseSelftestOnly='ownm_dispenser/'
```

| Schritt | Prüft |
|---|---|
| Einzelschuss | Ziffernblock 9 für 5 Ticks und der Taster der Funke (`triggerAction`, am Server genau 8 Ticks) feuern je genau einen Schuss; die Notiz nennt, wie lange der Server die Aktion gehalten sah |
| Anlauf beim Halten | 40 Ticks gehalten: 8 bis 10 Schuss, zwischen erstem und zweitem genau 10 Ticks, danach genau 4 |
| Tab „Mob Defense“ | Tab `mobdefense:settings` im K-Menü bei GUI 854×480 und 320×240 (Regler ganz sichtbar, Fotos EN und DE), Anlauf 750 ms und Abstand 500 ms einstellen, Strg+S schreibt `config/mobdefense-client.json`, Dauerfeuer danach mit Abständen 15 und 10 Ticks; mit der Welt-Regel `dispenser_cooldown_ticks` 20 liegen alle Abstände bei 20 Ticks; zum Schluss zurück auf 500/200 ms |
| Wurftrank im Schwebeflug | 5″ mit vier Wurftränken (Wasser) im Höhenhalten, vier Würfe nacheinander bis zum letzten Trank (4, 3, 2 und 1 geladen; vor jedem Wurf Position halten und zur Ruhe kommen): je Wurf Rückstoß = 0,35 kg × Tempo des Tranks gegen die Drohne (±5 %), größte Drehrate höchstens 670 °/s (voller Knüppel der Standard-Rates), Neigung höchstens +30°, der Trank fliegt mindestens 3 m und beschädigt die eigene Drohne nicht (Befund AUTO21-1); zum Vergleich eine Windkugel an einer frischen 5″ (Hebel, E24) |
| Vorderseite | das geladene Modell zeigt auf der Vorderseite `minecraft:block/dispenser_front`, Foto von vorn (EN und DE) |
| Auslöser im Takt wie der Flugplaner (OWNC5B-1) | 5″ ohne Pilot auf einer Säule, 64 Pfeile, Zubehör-Aktion 1 alle 2 Ticks über `triggerAction` (drücken und sofort loslassen, 24 Auslöser in 48 Ticks): Welt-Regel `dispenser_cooldown_ticks` 1 mit Standard-Abstand 12 Pfeile im Abstand 4, mit Abstand 6 Ticks des auslösenden Spielers 8 Pfeile im Abstand 6, mit Welt-Regel 8 6 Pfeile im Abstand 8; danach Welt-Regel und Schussfolge zurück. Vor dem Fix auf beiden Loadern 24 Pfeile im Abstand 2 |

JUnit: `dispenser.core.FireCadenceTest` (Anlauf, Abstand, feste Untergrenze 4 Ticks, Welt-Regel als Untergrenze, Druck in
der Abklingzeit, nach jedem Versuch der größte Wert aus Welt-Regel, 4 Ticks und Abstand, Auslöser im Takt von 1 bis 20 Ticks
nie schneller als der Abstand, Anlauf unter dem Abstand gilt als Abstand), `dispenser.DispenserRecoilTest` (Wurftränke bei 4 bis 1 geladenen
und geneigtem Lauf: Stoß am Schwerpunkt, voll entgegen dem Lauf; seit RC3P2-1 auch die Windkugel am Schwerpunkt, der Hebel
und `dispenser.core.RecoilLeverTest` sind entfallen; ohne Rückstoß kein Stoß),
`dispenser.DispenserFrontTextureTest` (Verweis statt Kopie, ganze Textur, nichts davor,
alle Texturen der Halterung im Block-Atlas).

Gemessen (Fabric, vor und nach dem Fix): Einzelschuss 3 statt 1 Schuss, Anlauf 4 statt 10 Ticks, kein Tab; Wurftrank
vorher +131,5° Neigung und 1302 °/s, nachher +16 bis +20° und 350 bis 440 °/s bei unverändert 4,7 bis 4,9 m/s Rückstoß-Tempo;
der Trank fliegt in beiden Fällen 13 bis 17 m weit und trifft die Drohne nicht.

Befund AUTO21-1 (28.10., Fabric, Schritt mit vier Würfen): vor dem Fix Wurf 1 bis 4 +17,8° / +32,8° / +73,7° / +168,5° bei
342 / 559 / 1147 / 3688 °/s (FAIL ab Wurf 2), nach dem Fix (Stoß am Schwerpunkt) alle vier höchstens +0,1° und 2 °/s, zurück
4,6 / 5,3 / 6,7 / 9,1 m/s, jeder Trank fliegt 14 bis 17 m ohne Treffer an der Drohne.


## Dispenser unter dem Rahmen (Befund FB2M-1, 09.11.2026)

```sh
MOBDEFENSE_JMP_JAR=<justmoreparts-fabric-…jar> MOBDEFENSE_CAMDRONES_JAR=<camdrones-fabric-…jar> tools/fastcheck.sh test --select-class de.lardtom.mobdefense.dispenser.DispenserUnderFrameTest
cp <justmoreparts-fabric-…jar> <camdrones-fabric-…jar> fabric/runs/selftest-client/mods/      # NeoForge: die NeoForge-Jars nach neoforge/runs/selftest-client/mods/
./gradlew :fabric:runClientSelftest '-PmobdefenseSelftestOnly=Unter dem Rahmen' -PmobdefenseScreenshots=true
./gradlew :neoforge:runClientSelftest '-PmobdefenseSelftestOnly=Unter dem Rahmen'
```

- JUnit `dispenser.DispenserUnderFrameTest`: Felder `recess_mm`/`drop_mm` und die Lage an jedem Rahmen mit Halterung vorn
  aus den Modellen; ohne Umgebungsvariablen nur die Propwash-Rahmen, die Zeile `[dispenser] … nicht gesetzt` nennt das.
- Selbsttest „Unter dem Rahmen“ im Modul `dispenser/`: je Rahmen Mündung und Mitte aus der `MountPose`, Pfeil an der
  Mündung, Lage am Rahmenmodell und ein Seitenbild `dispenser_under_<namensraum>_<rahmen>` aus der Zuschauer-Sicht (Fotos
  nicht im Repository; Stand 09.11. mit Just More Parts 1.2.0-rc.3 und Camera Drones 1.0.0-rc.3: 25 Rahmen und
  `freestyle_5` mit Speer-Halterung, 26 Fotos). Ohne die Add-on-Jars misst der Schritt nur die 4 Propwash-Rahmen und sagt das
  im Ergebnis.
- Werte und Grenzen: `docs/dispenser.md` §10.

## Bildschirmfotos im Repository (`docs/screenshots/selftest/`)

Herkunft: voller Selbsttest auf Fabric mit `-PmobdefenseScreenshots=true` gegen API-Stand B (Propwash 0.6.0-dev.7,
Mob Defense `main` auf Stand `feb7f54`, 22.10.2026), Fenster 1708 × 960 (GUI-Fotos 960 × 720), je Foto Englisch
(`<name>.png`) und Deutsch (`<name>_de.png`). Übernommen ist nur, was nach Sichtung noch dem Stand von `main` mit
Propwash 0.6.0-dev.10 entspricht:

| Foto | Schritt | Zeigt |
|---|---|---|
| `probe_world` | Probe | flache Testwelt, Beschreibung des Add-ons EN/DE |
| `dispenser_fpv_osd`, `dispenser_fpv` | S2 | FPV mit OSD `DSP 1 ARW 16` bzw. `DSP 4 TNT 3` beim Feuern |
| `dispenser_bucket_locked_de` | S3 | Warnung `EIMER GESPERRT` (nur Deutsch, siehe unten) |
| `dispenser_wind` | S4 | Windkugel aus dem Cinewhoop, OSD `DSP 1 WND 1`; der Cinewhoop bleibt waagerecht (neu aufgenommen am 04.11.2026 zu Befund RC3P2-1 gegen Propwash mit RC3P-2, vorher Überschlag „DOUBLE FLIP“) |
| `dispenser_cloud` | S5 | Verweilwolke um den Zombie |
| `dispenser_tnt_locked` | S7 | Warnung `TNT LOCKED` / `TNT GESPERRT` |
| `spear_drone` | S8 | Speer unter der Drohne, Spitze nach vorn |
| `booster_burn` | S9 | Funken und Rauch am Heck beim Brennen |
| `mob_<art>` | S10 | je Art eine Szene: `chase`, `brute`, `archer`, `fireball`, `wind`, `bullet`, `potion`, `trident`, `caster`, `flyer`, `creeper`, `jumper`, `beam`, `stare` (Enderman), `stare_creaking` (Knarz), `boss`, `ground` |
| `ui_osd_editor_320x240`, `ui_osd_editor_320x240_list` | S11 | OSD-Editor bei GUI 320×240, Gruppe „Add-ons“ mit den vier Elementen |
| `ui_flight_school_spear`, `ui_flight_school_booster` | S11b | Flugschul-Kapitel Speer und Raketen-Booster |
| `jointest_ohne_addon` | D2 | Client ohne Mob Defense: Propwashs Übersicht „Add-ons don't match this server“ mit Mob Defense (Fabric, NeoForge gleich) |

Nicht übernommen, weil inzwischen überholt oder unvollständig (beim nächsten vollen Lauf mit Fotos neu aufnehmen):

| Foto | Grund |
|---|---|
| `core_workbench`, `dispenser_service`, `ui_flight_school_dispenser` | Dispenser-Halterung noch mit eigener Vorderseite; seit Befund OWNM-3 zeigt sie die Vanilla-Textur |
| `core_world_rules`, `core_world_rules_end` | Überschrift noch „Propwash: Mob Defense“ und oben abgeschnitten, Speerschaden noch 50 %; heute „Mob Defense“ und 100 % |
| `ui_osd_editor_854x480`, `ui_osd_editor_854x480_list` | Seitenleiste des Konfigurators ohne den Tab „Mob Defense“ (seit Befund OWNM-1) |
| `dispenser_bucket_locked` (Englisch) | die Warnung `BUCKET LOCKED` ist im Bild nicht zu sehen |
| Krater-Fotos zu S7, Zuschauer-Foto D4 | S7 kam nur aus einem Diagnose-Lauf mit geänderter TNT-Zahl; auf dem D4-Foto sind Speer und Rakete nicht zu erkennen |

Neu aufnehmen: `./gradlew :fabric:runClientSelftest -PmobdefenseScreenshots=docs/screenshots/selftest` (über das
Slot-Skript); danach nur Fotos mit sichtbarer Änderung committen, Render-Rauschen verwerfen.
