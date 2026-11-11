# OSD, Warnungen, Hinweise, Flugschule und Fortschritte

Grundlage: `docs/design.md` §2.4, §2.5, §2.8, §4.3, E4 und E21. Dieses Dokument beschreibt, was das Modul `ui` anlegt
(Pakete `client`, `client.core`, `net`, `net.core`, Daten unter `data/mobdefense/advancement/`, Texte in
`common/src/lang/ui/`), wie die anderen Stränge es benutzen und wie es geprüft wird.

---

## 1. OSD-Elemente (A2)

`client.MobDefenseOsd.register()` meldet beim Start des Clients vier Elemente über
`PropwashClientApi.registerOsdElement` an, alle in der Gruppe „Add-ons“ (`OsdGroup.ADDON`), links ausgerichtet.
Name im OSD-Editor: `mobdefense.osd.<pfad>` (nur ASCII, weil der Schlüssel `osd` enthält).

| Element | Standard | Platz (Spalte/Zeile) | Beispiel | Inhalt |
|---|---|---|---|---|
| `mobdefense:dispenser` | an | 1 / 12 | `DSP 2 ARW 12` | Slot, der als Nächstes feuert (gewählter, sonst der nächste belegte), Kürzel, Anzahl; `*` in der Abklingzeit; `DSP --` leer (Ton „Vorsicht“) |
| `mobdefense:dispenser_slots` | aus | 1 / 11 | `[ARW]SPL WND ---` | alle vier Slots, gewählter in Klammern |
| `mobdefense:booster` | an | 1 / 13 | `RKT RDY` / `RKT 1.2` / `RKT ---` | bereit, Restbrenndauer in Sekunden (Ton „Akzent“), leer (Ton „Vorsicht“) |
| `mobdefense:spear` | aus | 1 / 14 | `SPR 87%` | Haltbarkeit des Speers; unter 20 % oder ohne Speer Ton „Vorsicht“ |

- Ein Element erscheint nur, wenn das passende Zubehör an der Drohne sitzt (Dispenser vorn, Booster oben, Speer unten).
- In der Editor-Vorschau zeigt jedes Element das Beispiel aus der Tabelle.
- Die Texte baut der portable Kern `client.core.OsdLines`, die Kürzel `client.core.AmmoCodes` (design.md §2.5:
  `ARW SPC TIP SNW EGG SPL LNG XPB WND FIR RKT TNT BKT BON`, sonst `ITM`; modded `*_arrow` → `ARW`, `*_bucket` → `BKT`).
  Beide übersetzt `checkPortable` mit `--release 21`.
- Die Elemente wirken im FPV und im Sichtflug; sie lesen nur, was Propwash ohnehin an die Clients schickt. Sie werten
  `OsdContext` gleich aus, ob FPV oder Sichtflug. Bis Propwash 0.6.0-dev.7 ruft Propwash die OSD-Elemente der Add-ons im
  Sichtflug ohne Brille aber gar nicht auf; dort fehlen sie bis zum Propwash-Fix (Befund cov-md-a-2, Teil in Propwash).

### 1.1 Woher die Werte kommen (Vertrag mit md-dispenser und md-booster)

`client.OsdReader` liest einmal je Tick und Drohne:

| Wert | Quelle |
|---|---|
| Zubehör an der Halterung | `PropwashApi.accessories().accessoryAt(drohne, slot)` |
| Inhalt der Slots | `PropwashApi.accessories().contents(drohne, slot)` |
| gewählter Slot, Abklingzeit | Drohnen-Daten `mobdefense:dispenser` (B6, `Sync.TRACKING`), Felder `selected` (0–3) und `cooldown_until` (Spielzeit) |
| Brennende Rakete | Drohnen-Daten `mobdefense:booster`, Feld `ends_at` (Spielzeit) |
| feuernder Slot | `dispenser.core.SlotCycle.firing(selected, belegt)` |

Die Schlüssel meldet nicht dieses Modul an, sondern der jeweilige Strang. Der OSD-Teil sucht sie mit
`PropwashApi.droneData().find(id)` und liest den Wert über den Codec des Schlüssels als NBT-Objekt. Damit reicht es,
wenn md-dispenser und md-booster die Felder wie in design.md §4.2 benennen; eigene Klassen müssen sie nicht teilen.
Fehlt ein Schlüssel, gilt Slot 1 als gewählt, keine Abklingzeit und kein Brennen.

---

## 2. Warnungen (A2) und Paket `mobdefense:notice`

Sieben Hinweise sind über `PropwashClientApi.registerOsdWarning` angemeldet (`mobdefense:warning/<pfad>`):

| `net.Notice` | Englisch | Deutsch | Ton |
|---|---|---|---|
| `NO_AMMO` | `NO AMMO` | `KEINE MUNITION` | Vorsicht |
| `BUCKET_LOCKED` | `BUCKET LOCKED` | `EIMER GESPERRT` | Vorsicht |
| `TNT_LOCKED` | `TNT LOCKED` | `TNT GESPERRT` | Vorsicht |
| `BARREL_BLOCKED` | `BARREL BLOCKED` | `LAUF BLOCKIERT` | Vorsicht |
| `NO_PERMISSION` | `NO PERMISSION` | `KEIN RECHT` | Vorsicht |
| `THREAT` | `THREAT` | `BEDROHUNG` | Warnung |
| `HIT` | `HIT` | `TREFFER` | Akzent |

Was eine Warnung auslöst, weiß nur der Server (Ergebnis von `dispense`, Rechte, Lauf, Mob-Ziel). Deshalb schickt der
Server dem Piloten ein kleines Paket, der Client zeigt die Warnung für die angegebene Dauer, nur für diese Drohne:

```java
Notices.dispense(pilot, drone, result);              // EMPTY → NO_AMMO, BLOCKED → BARREL_BLOCKED, DENIED → NO_PERMISSION
Notices.send(pilot, drone, Notice.BUCKET_LOCKED);    // 2 s (Notice.DEFAULT_TICKS = 40)
Notices.send(pilot, drone, Notice.TNT_LOCKED);
Notices.threat(pilot, drone, 10);                    // Dauer in Ticks, solange ein Mob zielt wiederholen
Notices.send(pilot, drone, Notice.HIT, 30);          // Speer-Treffer, 1,5 s (SpearFeedback.NOTICE_TICKS)
```

- Paket S2C `mobdefense:notice` (`net.NoticePayload`): Entity-Id der Drohne, Art, Dauer in Ticks (1–200).
- Höchstens **4 Pakete je Sekunde und Pilot** (`net.core.RateGate`, gleitendes Fenster von 20 Server-Ticks); was
  darüber liegt, verwirft `Notices` und meldet `false`. Die Warnung steht ohnehin 2 s, nichts geht verloren.
- Einer der vier Plätze bleibt für `THREAT` frei (`Notices.THREAT_RESERVE`, `RateGate#allow(key, tick, vorbehalt)`):
  `NO_AMMO`, die übrigen Hinweise des Dispensers und `HIT` bekommen höchstens 3 je Sekunde, `THREAT` darf alle 4
  nehmen. `THREAT` kommt aus `mob.core.ThreatPulse` höchstens einmal je Sekunde und Drohne und findet so immer einen
  Platz, auch wenn der Pilot am leeren oder blockierten Dispenser Feuer hält (Befund AUTO3-1: vorher belegte
  `NO_AMMO` alle 4 Ticks im Drohnen-Tick jeden frei werdenden Platz, bevor `mob.Threats` am Tick-Ende sendet, und
  `BEDROHUNG` kam nie).
- Die Sperre zählt in Server-Ticks (`getTickCount()`), und die beginnen mit jedem integrierten Server wieder bei 0.
  Deshalb leert `Notices` die Sperre bei jedem Serverstart (`MobDefense.onServerStarted`, angemeldet in
  `Notices.install`), und `RateGate` beginnt für einen Schlüssel neu, sobald ein Tick vor seinem jüngsten Eintrag
  liegt; `prune` räumt solche Schlüssel ebenfalls ab. Befund AUTO58-1 (aus G-RC3P4-1): vorher blieben nach einem
  Weltwechsel im Einzelspieler (etwa „Speichern und Verlassen“, dann wieder öffnen) die großen Ticks der alten Welt vorn
  in der Liste, `drop` prüfte nur den Kopf, und nach 2–3 Hinweisen verwarf `Notices` jede Warnung (KEINE MUNITION,
  LAUF BLOCKIERT, EIMER und TNT GESPERRT, KEIN RECHT, BEDROHUNG, TREFFER), bis der neue Tick den alten um 20 überstieg.
  Selbsttest-Lauf `dispenser/,Lauf blockiert,Zwei Piloten` (S13 startet die Welt neu) auf Fabric: vorher FAIL „Lauf
  blockiert und leer“ mit „Warnung fehlt statt „BARREL BLOCKED““, danach 10 von 10 PASS.
- Nur an den Piloten (`ServerPlayer` des Aufrufers). Fabric schickt nur, wenn der Client den Kanal kennt
  (`ServerPlayNetworking.canSend`), NeoForge meldet den Kanal als optional an und prüft `hasChannel`.
- Anmeldung in den Loader-Einstiegen: Fabric `PayloadTypeRegistry.clientboundPlay()` und
  `ClientPlayNetworking.registerGlobalReceiver`, NeoForge `RegisterPayloadHandlersEvent` (ohne Handler, Server bleibt
  frei von Client-Klassen) und `RegisterClientPayloadHandlersEvent` im Client-Einstieg.

Wer ruft auf: md-dispenser nach jedem Schuss mit Fehlschlag (`Notices.dispense`, dazu `BUCKET_LOCKED` bei gesperrtem
Eimer und `TNT_LOCKED` bei gesperrtem TNT), md-mobs-1 für `THREAT`, solange ein Mob auf die Drohne zielt, md-spear für
`HIT` nach jedem wirksamen Speer-Stich (`docs/spear.md` §4 Punkt 12, Befund OWNM-4). `HIT` steht hinten in `net.Notice`,
damit die Nummern der übrigen Hinweise im Paket gleich bleiben.

Abweichung von design.md §4.3: Dort ist ein eigenes Paket nur für `THREAT` vorgesehen. `BLOCKED` und `DENIED` kennt
aber nur der Server (Mündung aus `MountPose`, Rechte des Piloten), deshalb trägt dasselbe Paket alle sieben Hinweise.

---

## 3. Hinweise

Die Tooltips der Teile (Shift) bekommen Bedienzeilen `mobdefense.help.<teil>.use.<n>` (Propwash liest sie selbst):

- Dispenser-Halterung: Feuern mit **Zubehör-Aktion 1** (Taster oder Schalter der Funke, Tastatur Ziffernblock 9),
  nächster Slot mit Zubehör-Aktion 2, **Rechtsklick beendet den Flug** wie bei jeder Drohne (E4), Beladen in der Wartung.
- Speer-Halterung: ein Speer, Spitze nach vorn, trifft von selbst ab 4,6 m/s, Beladen in der Wartung.
- Raketen-Booster: Zünden mit Zubehör-Aktion 3, genau eine Rakete, Nachladen in der Wartung.

Den Hinweis beim ersten Flug mit Name und Belegung jeder Aktion zeigt Propwash selbst aus `actionNameKey` (G2).

---

## 4. Flugschule

`client.MobDefenseGuide.register()` meldet drei Szenen an und verknüpft sie mit den Teilen
(`linkFlightSchoolItems`); die Halten-Taste (M) über einem Teil öffnet die Szene.

| Szene | Schritte | Teil |
|---|---|---|
| `mobdefense:dispenser` „Mob Defense: Dispenser“ | Beladen, Feuern (Zubehör-Aktion 1, Rechtsklick beendet den Flug), Slots und Regeln | Dispenser-Halterung |
| `mobdefense:spear` „Mob Defense: Speer“ | Einhängen, Lanzenflug | Speer-Halterung |
| `mobdefense:booster` „Mob Defense: Raketen-Booster“ | Beladen, Zünden (Zubehör-Aktion 3) | Raketen-Booster |

Jede Szene nennt Zubehör-Aktion 1 als Auslöser des Dispensers und dass der Rechtsklick den Flug beendet. Texte unter
`mobdefense.guide.scene.<name>.…` mit den Grenzen der Flugschule (Titel 38, Beschreibung 64, Schritttext 150,
Hinweis 90 Zeichen). Fehlt beim Start ein Teil, protokolliert die Anmeldung einen Fehler und überspringt die Szene.

---

## 5. Fortschritte

`data/mobdefense/advancement/`: `root` (eigener Reiter „Mob Defense“, erledigt mit dem ersten Mob-Defense-Teil im
Inventar) und vier Fortschritte unter `root` mit Kriterium `minecraft:impossible` (`done`), vergeben im Code:

| Id | Deutsch | Englisch | Auslöser (Strang) |
|---|---|---|---|
| `mobdefense:air_support` | Luftunterstützung | Air Support | Heiltrank aus dem Dispenser trifft einen Mitspieler (md-dispenser) |
| `mobdefense:lance_flight` | Lanzenflug | Drone Joust | Mob mit der Speer-Halterung besiegt (md-spear) |
| `mobdefense:afterburner` | Nachbrenner | Afterburner | Raketen-Booster gezündet (md-booster) |
| `mobdefense:recoil` | Rückstoß | Recoil | Windkugel aus dem Dispenser (md-dispenser) |

Vergeben am Server:

```java
AdvancementHolder holder = player.level().getServer().getAdvancements().get(MobDefense.id("afterburner"));
if (holder != null) {
    player.getAdvancements().award(holder, "done");
}
```

---

## 6. Tests

| Test | Prüft |
|---|---|
| `client.core.OsdLinesTest`, `client.core.AmmoCodesTest` | Texte aller Elemente, Kürzel aus design.md §2.5, ASCII, Längen |
| `client.OsdSpecTest` | vier Elemente wie in der Tabelle (Dispenser an 1/12, Booster an 1/13, Slots und Speer aus), Gruppe „Add-ons“, Raster |
| `client.OsdStateTest` | feuernder Slot, Abklingzeit, Booster bereit/brennend/leer, Speer-Haltbarkeit, Elemente nur mit Zubehör |
| `client.UiTextsTest` | OSD-Namen und Warnungen EN/DE wie im Design, Szenen-Texte vollständig und in den Grenzen, Szenen und Dispenser-Hinweis nennen Zubehör-Aktion 1 und „Rechtsklick beendet den Flug“, Texte der Fortschritte |
| `client.AdvancementFilesTest` | vier Fortschritte mit `minecraft:impossible` unter `root`, `root` mit den drei Teilen |
| `net.NoticeTest`, `net.core.RateGateTest` | Zuordnung `DispenseResult` → Warnung, höchstens 4 je Sekunde und Pilot in jedem Fenster, mit Vorbehalt höchstens 3 und der letzte Platz für `THREAT` |
| `net.NoticesWorldChangeTest`, `net.core.RateGateTest` (Weltwechsel) | Befund AUTO58-1: nach einem Tick der alten Welt (5000 bzw. 812) kommen die Ticks der neuen Welt (100, 200, 300, 4000 bzw. `THREAT` 158/178/198, `NO_AMMO` 288, `BARREL_BLOCKED` 313) durch, das Limit gilt danach wieder; `prune` entfernt Schlüssel der alten Uhr; der Serverstart leert die Sperre auch, wenn der neue Tick schon über dem alten liegt. Vorher 4 von 4 rot |
| `net.ThreatUnderFireTest` | Befund AUTO3-1 über `Notices.admit`: `NO_AMMO` im Drohnen-Tick bei jeder Schussfolge von 4 bis 40 Ticks, Bedrohung ab jedem Tick 0–39: `THREAT` sofort und lückenlos sichtbar, `NO_AMMO` bleibt sichtbar, nie mehr als 4 Pakete in 20 Ticks; zehnmal `THREAT` im selben Tick → 4 |
| `LangTest` | gleiche Schlüssel DE/EN, Umlaute, OSD-Texte nur ASCII |

Selbsttest-Modul `dev/ui/` (Filter `-PmobdefenseSelftestOnly=ui/`):

- **S11 OSD und Editor:** Anmeldung, Drohne mit allen drei Teilen und Inhalt (Ersatz-Verhalten mit 4/1/1 Slots, falls
  die Stränge der Teile noch fehlen), Texte der Elemente mit einem im Schritt gebauten `OsdContext` für FPV und für
  Sichtflug (`DSP 1 ARW 12`, `[ARW]SPL WND ---`, `RKT RDY`, `SPR …%`), Warnung `BUCKET LOCKED`/`EIMER GESPERRT` vom Server, nur für die eigene
  Drohne, zehnmal `THREAT` im selben Tick → 4 gesendet und 4 am Client, `THREAT` verschwindet nach der Dauer; OSD-Editor
  bei GUI 854×480 und 320×240, die Vorschau zeichnet Dispenser und Booster; Fotos `ui_osd_editor_854x480`,
  `ui_osd_editor_320x240` (je EN und `_de`). Ob Propwash die Elemente im Sichtflug selbst aufruft, prüft S11 nicht;
  das prüft der Schritt „Anzeigen im Sichtflug“ im Modul `covmda_ui` (`docs/testing.md`, Abdeckung cov-md-a).
- **S11b Flugschule:** drei Szenen angemeldet, jedes Teil öffnet seine Szene (`flightSchoolScenes`), Fotos
  `ui_flight_school_dispenser|spear|booster` EN/DE.
- **S11c Fortschritte:** fünf Fortschritte geladen, `air_support` lässt sich vergeben und zurücknehmen.
- Server-Prüfung für D1: Fortschritte geladen, Versand der Hinweise im Loader angemeldet.

---

## 7. Build und Selbsttest-Läufer

- `:common:mergeLang` meldet seine Ausgabe (`assets/mobdefense/lang/`) an. Vorher hielt Gradle die Datei für
  unverändert, und `:fabric:processResources` bzw. `:neoforge:processResources` meldeten im selben Lauf „UP-TO-DATE“ mit
  alten Texten. `:common:test` hat `common/src/lang` als Eingabe, damit `LangTest` nach einer reinen Textänderung läuft.
- `Shots.dir()` löst den Run-Ordner erst beim ersten Foto auf (Fallback: Spielordner des Loaders); die Startzeile des
  Läufers nennt nur die Einstellung. Vorher stürzte NeoForge mit `-PmobdefenseScreenshots=true` beim Mod-Aufbau ab, weil
  `Minecraft.getInstance()` dort noch fehlt.
