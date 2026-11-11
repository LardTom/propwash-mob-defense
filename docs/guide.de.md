# Propwash: Mob Defense – Anleitung

English: [`guide.md`](guide.md)

Mob Defense ist ein offizielles Add-on für Propwash FPV. Drei Zubehörteile machen aus einer FPV-Drohne ein Werkzeug gegen
Mobs: die **Dispenser-Halterung** vorn, die **Speer-Halterung** unten und der **Raketen-Booster** oben. Feindliche Mobs
bemerken fliegende Drohnen und wehren sich, und Welt-Regeln legen fest, was auf einem Server erlaubt ist. Alles läuft mit
Vanilla-Items: Pfeile, Tränke, Windkugeln, TNT, Speere und Feuerwerksraketen.

Inhalt: 1 Erste Schritte · 2 Bedienung · 3 Dispenser-Halterung · 4 Speer-Halterung · 5 Raketen-Booster · 6 OSD und
Warnungen · 7 Mobs reagieren auf Drohnen · 8 Welt-Regeln · 9 Auf Servern · 10 Flugschule und Fortschritte · 11 FAQ ·
12 Kompatibilität

---

## 1. Erste Schritte

1. **Installieren:** Propwash FPV ab 0.6.0 und Mob Defense in denselben Ordner `mods`, auf dem Server und auf jedem
   Client (Abschnitt 12).
2. **Teile besorgen.** Im Kreativmodus liegen sie im Reiter „Propwash: Mob Defense“. Im Überlebensmodus werden sie
   hergestellt (Rezepte im Rezeptbuch, freigeschaltet, sobald man eine der Zutaten besitzt):

   | Teil | Halterung | Rezept |
   |---|---|---|
   | Dispenser-Halterung | vorn | oben Platine, in der Mitte Eisenklumpen – Werfer – Eisenklumpen, unten Kupferspule |
   | Speer-Halterung | unten | oben Eisenklumpen, Lücke, Eisenklumpen, darunter drei Carbonplatten |
   | Raketen-Booster | oben | oben Kupferklumpen – Feuerzeug – Kupferklumpen, darunter mittig eine Carbonplatte |

   Platine, Kupferspule und Carbonplatte sind Zwischenteile von Propwash. Der Werfer ist der Vanilla-Block, den die
   englische Fassung „Dispenser“ nennt.
3. **Anbauen.** Das Zubehör kommt an der Drohnen-Werkbank von Propwash auf seine Halterung, wie jedes andere Teil. Jedes
   Teil hat Gewicht und Luftwiderstand, die Werkbank zeigt das neue Abfluggewicht. Der kleinste Rahmen mit Halterung vorn
   ist der 3″-Cinewhoop; der 65-mm-Whoop hat nur unten eine Halterung.
4. **Beladen.** Drohne abstellen, schleichen und rechtsklicken, in der Wartung **Zubehör** drücken. Munition in den
   Dispenser, einen Speer in die Speer-Halterung, eine Feuerwerksrakete in den Booster.
5. **Fliegen** wie gewohnt und mit **Zubehör-Aktion 1** feuern (Abschnitt 2). Der Rechtsklick beendet wie bei jeder Drohne
   den Flug – er feuert nicht.

---

## 2. Bedienung

| Funktion | Standard | Hinweis |
|---|---|---|
| Dispenser feuern | **Zubehör-Aktion 1**: Ziffernblock 9 oder ein Taster bzw. Schalter der Funke | am besten auf einem Taster der Funke; ein Schalter gibt Dauerfeuer |
| Nächster Slot des Dispensers | Zubehör-Aktion 2: Ziffernblock 0 | wählt den nächsten belegten Slot |
| Raketen-Booster zünden | Zubehör-Aktion 3: Ziffernblock Enter | nur im Flug |
| FPV-Flug beenden | „FPV-Flug beenden“ (Y) oder Rechtsklick | wie bei jeder Propwash-Drohne |

- **Zubehör-Aktion 1 ist der Auslöser.** An der Funke legst du sie im Konfigurator von Propwash (K) im Tab „Modi“ auf einen
  Taster oder Schalter, wie jede andere Schalterfunktion; der Tab zeigt daneben die Bedeutung („Zubehör-Aktion 1 –
  Dispenser feuern“). Auf der Tastatur ist es Ziffernblock 9, im Tab „Tasten“ frei belegbar, auch auf eine Seitentaste der
  Maus.
- Beim ersten Flug mit Dispenser zeigt Propwash einmal, welche Aktion was tut und wie sie gerade belegt ist. Ist
  Zubehör-Aktion 1 nirgends belegt, verweist der Hinweis auf den Tab „Modi“.
- Zubehör-Aktion 4 bleibt frei.
- Der Speer braucht keinen Knopf: Er trifft von selbst (Abschnitt 4).

---

## 3. Dispenser-Halterung

Der Dispenser zeigt immer genau nach vorn. Es gibt keinen Drehturm: Gezielt wird mit der ganzen Drohne. Die Halterung
sitzt vorn unter dem Rahmen, nur ihre Steinfront ragt über die Nase.

**Vier Slots.** Jeder Slot nimmt bis zu 16 Stück oder die Stapelgröße des Items; TNT höchstens 4, Wurf- und Verweiltränke
sowie Eimer je 1. Drohnen-Items passen nicht hinein.

**Feuern.** Ein kurzer Druck feuert genau einmal. Hältst du Zubehör-Aktion 1, folgt der zweite Schuss nach dem *Anlauf bis
zum Dauerfeuer* (500 ms), danach feuert der Dispenser im *Abstand im Dauerfeuer* (200 ms) weiter. Es feuert der gewählte
Slot; ist er leer, der nächste belegte. Zubehör-Aktion 2 wählt den nächsten belegten Slot.

**Deine Schussfolge.** Im Konfigurator (K), Tab „Mob Defense“, stellst du beide Zeiten für dich ein, je 200 bis 2000 ms in
Schritten von 50 ms. Sie werden auf deinem Rechner gespeichert (`config/mobdefense-client.json`) und gelten auf jedem
Server mit Mob Defense. Die Welt-Regel „Abklingzeit des Dispensers“ kann mehr verlangen; der Tab zeigt die Untergrenze der
aktuellen Welt. Zwei Schüsse liegen nie näher als 200 ms (4 Ticks, wie beim Vanilla-Werfer) beieinander, ob du hältst,
schnell drückst oder ein anderes Add-on die Aktion auslöst.

**Was er feuert.** Alles, was ein Vanilla-Werfer kann, aus der Mündung vor der Drohne:

- Geschosse: alle Pfeile (auch Spektral- und getränkte Pfeile), Schneebälle, Eier, Wurf- und Verweiltränke,
  Erfahrungsfläschchen, Windkugeln, Feuerkugeln und Feuerwerksraketen. Sie fliegen wie aus einem Vanilla-Werfer, dazu
  kommt das Tempo der Drohne; Besitzer ist der Pilot.
- TNT wird an der Mündung gezündet und fällt mit dem Tempo der Drohne. TNT ist standardmäßig erlaubt; die Welt-Regel
  „TNT aus dem Dispenser“ schaltet es ab (`TNT GESPERRT`).
- Alles andere wirkt wie beim Vanilla-Werfer auf den Block vor der Mündung: Knochenmehl, Schere, Feuerzeug, Rüstung
  anlegen, Kürbisse und Schädel setzen, Shulkerkisten setzen, Leuchtstein in den Seelenanker, Honigwaben, Pinsel, Boote und
  Loren; ein leerer Eimer schöpft. Items ohne solches Verhalten fliegen als Item nach vorn.
- Wasser-, Lava- und Pulverschnee-Eimer sowie Eimer mit Fischen oder Tieren verweigert er, solange die Welt-Regel sie nicht
  erlaubt.

**Rückstoß.** Jeder Schuss stößt die Drohne mit dem Impuls des Geschosses zurück: ein Pfeil kaum, ein Wurftrank und eine
**Windkugel** spürbar – eine Windkugel stößt einen Cinewhoop und eine 5″ um einige Meter pro Sekunde zurück, beide bleiben
fliegbar. Jedes Geschoss, auch die Windkugel, stößt gerade durch den Schwerpunkt, die Drohne bleibt waagerecht, auch beim
letzten Trank. Der Windstoß einer Windkugel aus dem Dispenser stößt Mobs, Spieler und Drohnen halb so stark weg wie eine
Vanilla-Windkugel.

**Gewicht.** Die Beladung zählt zum Abfluggewicht. Eine voll beladene Drohne fliegt spürbar träger:

| Item | g | Item | g |
|---|---|---|---|
| Pfeil (alle Arten) | 20 | Windkugel | 50 |
| Schneeball, Ei | 60 | Feuerkugel | 80 |
| Wurf-, Verweiltrank | 350 | Feuerwerksrakete | 60 |
| Erfahrungsfläschchen | 300 | TNT | 600 |
| Knochenmehl | 15 | Eimer leer / voll | 250 / 1250 |

Vier Tränke oder vier TNT machen jede Drohne deutlich langsamer. Am besten trägt sie eine 7″ mit LiPo, die
Long-Range-7″ mit Li-Ion-Akku schlechter als eine 5″. Mit acht TNT (4,8 kg) hebt eine 5″ nicht mehr ab.

**Töne:** die Töne des Vanilla-Werfers, auch das Klicken, wenn ein Schuss verweigert wird.

---

## 4. Speer-Halterung

- Nimmt genau **einen Speer**, Spitze nach vorn: Holz, Stein, Kupfer, Eisen, Gold, Diamant oder Netherit. Dreizack,
  Schwerter und Stöcke passen nicht; Speere aus anderen Mods gehen, wenn sie echte Speere sind.
- Alle sehen den Speer unter der Drohne; die Spitze liegt etwa 10 cm vor der Mitte der Halterung – an einer 5″ etwa
  auf Höhe der vorderen Propeller, an einer 7″ ragen die Propeller weiter nach vorn.
- **Er trifft von selbst.** Flieg mit der Nase voran durch oder in einen Mob – stößt der Rahmen an, bevor die Spitze
  ankommt, zählt das trotzdem als Speer-Treffer. Ab 4,6 m/s entlang der Nase (gegenüber dem Ziel) macht der Speer
  Schaden wie ein Vanilla-Speer bei gleichem Tempo, mal der Welt-Regel „Speerschaden“ (Standard 100 %), höchstens 15.
  Beispiel Eisenspeer: 5 m/s → 5, 10 m/s → 10, ab 15 m/s → 15. Rückstoß und Abwerfen von Reitern folgen den
  Vanilla-Schwellen des Speers.
- Jedes Ziel wird höchstens einmal je halbe Sekunde getroffen. Verzauberungen auf Schaden wie Schärfe und Verbrennung
  wirken, Ausfallschritt nicht. Jeder Treffer kostet einen Punkt Haltbarkeit; ein zerbrochener Speer verschwindet mit dem
  Vanilla-Ton.
- Bei einem Treffer hörst du den Treffer-Ton des Speers an der Kamera der Drohne, siehst Krit-Partikel am Ziel, und das OSD
  zeigt kurz **TREFFER**.

---

## 5. Raketen-Booster

- Hält genau **eine Feuerwerksrakete**. Nachladen in der Wartung nach der Landung; eine Abklingzeit gibt es nicht.
- **Zubehör-Aktion 3** zündet im Flug: Schub entlang der Nase wie mit Elytren – für jede Drohnengröße dieselben 2 g,
  höchstens 60 N. Er brennt 1,25, 1,75 oder 2,25 s bei Flugdauer 1, 2 oder 3. Eine 5″ wird fast 25 m/s schneller.
- Funken und Rauch am Heck, der Vanilla-Startton. Danach ist der Slot leer.
- Eine Rakete **mit Sternen** explodiert am Ende wie an den Elytren: Schaden `5 + 2 × Sterne` an der Drohne und an
  Lebewesen in der Nähe. Schon ein Stern zerbricht die eigene Drohne – zum Boosten Raketen ohne Sterne nehmen.
- Wechselt der Pilot oder nimmt jemand die Rakete heraus, während sie brennt, endet der Booster sofort (kein Schub, keine
  Explosion).

---

## 6. OSD und Warnungen

Die Elemente stehen im OSD-Editor von Propwash in der Gruppe „Add-ons“ und lassen sich dort verschieben. Ein Element
erscheint nur, wenn das passende Zubehör an der Drohne sitzt. OSD-Texte sind reines ASCII und in jeder Sprache gleich:

| Element | Standard | Beispiel | Zeigt |
|---|---|---|---|
| Dispenser | an | `DSP 2 ARW 12` | Slot, der als Nächstes feuert, Munition, Anzahl; `*` in der Abklingzeit; `DSP --` leer |
| Dispenser-Slots | aus | `[ARW]SPL WND ---` | alle vier Slots, der gewählte in Klammern |
| Raketen-Booster | an | `RKT RDY` / `RKT 1.2` / `RKT ---` | bereit, restliche Brenndauer, leer |
| Speer-Haltbarkeit | aus | `SPR 87%` | Haltbarkeit des Speers |

Kürzel der Munition: `ARW` Pfeil, `SPC` Spektralpfeil, `TIP` getränkter Pfeil, `SNW` Schneeball, `EGG` Ei, `SPL` Wurftrank,
`LNG` Verweiltrank, `XPB` Erfahrungsfläschchen, `WND` Windkugel, `FIR` Feuerkugel, `RKT` Rakete, `TNT`, `BKT` Eimer, `BON`
Knochenmehl, `ITM` alles andere.

Warnungen (2 s, nur für den Piloten):

| Deutsch | Englisch | Bedeutung |
|---|---|---|
| `KEINE MUNITION` | `NO AMMO` | alle Slots leer |
| `EIMER GESPERRT` | `BUCKET LOCKED` | Eimer mit Flüssigkeit sind in dieser Welt aus |
| `TNT GESPERRT` | `TNT LOCKED` | die Welt-Regel „TNT aus dem Dispenser“ ist in dieser Welt aus (oder Vanilla `tnt_explodes` ist aus) |
| `LAUF BLOCKIERT` | `BARREL BLOCKED` | ein Block sitzt direkt vor der Mündung |
| `KEIN RECHT` | `NO PERMISSION` | du darfst hier nicht bauen, oder Griefing durch Drohnen ist aus |
| `BEDROHUNG` | `THREAT` | ein Mob hat es auf deine Drohne abgesehen |
| `TREFFER` | `HIT` | der Speer hat getroffen |

---

## 7. Mobs reagieren auf Drohnen

Jeder feindliche Mob hat eine eigene Reaktion auf Drohnen. Die Grundregeln:

- Mobs reagieren nur auf Drohnen, die **scharf** sind (in der Luft oder gelandet), heimfliegen oder autonom fliegen. Eine
  Drohne, die mit der Fernbedienung verbunden, aber nicht scharf ist, bleibt wie eine **abgestellte Drohne** unbehelligt;
  Entschärfen beendet jeden Angriff auf sie.
- **Spieler zuerst:** Ein Mob, der schon einen Spieler als Ziel hat, wechselt nur zu einer Drohne, die weniger als halb so
  weit weg ist. Ohne Ziel nimmt er die nächste Drohne in Reichweite (24 Blöcke), die er sehen kann.
- Je Drohne reagieren höchstens 12 Mobs gleichzeitig.
- Schaden durch Mobs skaliert die Welt-Regel „Schaden durch Mobs an Drohnen“. Explosionen und Windkugeln stoßen fliegende
  Drohnen.
- Solange ein Mob es auf deine Drohne abgesehen hat, zeigt das OSD **BEDROHUNG**.

| Art | Mobs | Reaktion |
|---|---|---|
| Verfolger | Zombie, Wüstenzombie, Ertrunkener, Zombiedorfbewohner, Zombienautilus | werden von der Drohne angelockt, laufen (bzw. schwimmen) unter sie und schlagen zu, sobald sie höchstens 2,5 Blöcke über ihrem Kopf ist oder gelandet ist |
| Schläger | Zombifizierter Piglin (nur wütend), Diener, Piglin-Barbar, Verwüster, Hoglin, Zoglin, Wither-Skelett | wie Verfolger; der Verwüster brüllt die Drohne weg, Hoglin und Zoglin schleudern sie nach oben |
| Schützen | Skelett, Eiswanderer, Sumpfskelett, Vertrockneter, Illusionist, Plünderer, Piglin mit Armbrust | bleiben stehen, halten vor und schießen ihre eigenen Pfeile, 4–24 Blöcke weit mit Sichtlinie |
| Feuerkugeln | Lohe, Ghast | Lohe: drei kleine Feuerkugeln bis 16 Blöcke; Ghast: große Feuerkugel bis 64 Blöcke, die Explosion stößt die Drohne |
| Wind | Böe | Windkugeln bis 16 Blöcke stoßen die Drohne weg |
| Geschoss | Shulker | öffnet sich und schießt Shulker-Geschosse, die der Drohne folgen, bis 16 Blöcke; ein Treffer lässt die Drohne 10 Sekunden lang schweben wie die Schwebekraft (sie steigt auch ohne Gas, eigener Schub kommt dazu) |
| Tränke | Hexe | wirft Wurftränke des Schadens bis 10 Blöcke |
| Dreizack | Ertrunkener mit Dreizack | wirft den Dreizack bis 16 Blöcke |
| Beschwörer | Magier | Fangzähne unter Drohnen, die höchstens 2 Blöcke hoch fliegen; höher beschwört er Plagegeister |
| Flieger | Phantom, Plagegeist | jagen die Drohne in der Luft; Phantome kreisen über Drohnen mehr als 10 Blöcke über dem Boden oder nachts im Freien und stoßen herab |
| Creeper | Creeper | läuft unter eine Drohne, die höchstens 4 Blöcke hoch fliegt, und zündet |
| Springer | Schleim, Magmawürfel, Schwefelwürfel, Spinne, Höhlenspinne | springen an Drohnen, die höchstens 3 Blöcke hoch fliegen; Spinnen klettern dafür Wände hoch |
| Laser | Wächter, Großwächter | Laser auf Drohnen bis 16 Blöcke mit Sichtlinie, wie bei Spielern: Schaden nach der Vanilla-Ladezeit, der Wächter erst ab mehr als 3 Blöcken Abstand |
| Blick | Enderman, Knarz | Enderman: wird wütend, wenn die FPV-Kamera ihm ins Gesicht sieht, teleportiert sich zur tiefen Drohne oder greift den Piloten an. Knarz: läuft zur Drohne und erstarrt, solange die Kamera ihn ansieht |
| Boss | Wither | Wither-Schädel bis 32 Blöcke |
| Boden | Silberfischchen, Endermite und jeder andere feindliche Mob, auch aus anderen Mods | greifen gelandete oder höchstens 1 Block hoch schwebende Drohnen an |

Enderdrache, Riese, Zombiedromedar, Zombiepferd und Wärter beachten Drohnen nicht (die Flügel des Drachen stoßen Drohnen
ohnehin). Blockschaden durch Creeper- und Ghast-Explosionen folgt wie immer Vanilla `mob_griefing`. Datenpakete und andere
Mods ordnen ihre Mobs über die Entity-Tags `mobdefense:reacts/<art>` zu oder nehmen sie mit `mobdefense:ignores_drones`
aus (Tag-Namen englisch: `chase`, `brute`, `archer`, `fireball`, `wind`, `bullet`, `potion`, `caster`, `flyer`,
`creeper`, `jumper`, `beam`, `stare`, `boss`, `ground`).

Abschalten lässt sich alles mit der Welt-Regel „Mobs reagieren auf Drohnen“; die Voreinstellung „Training“ tut das,
„Realistisch“ und „Arcade“ schalten sie wieder ein.

---

## 8. Welt-Regeln

Im Konfigurator von Propwash (K), Tab „Welt-Regeln“, Abschnitt „Mob Defense“. Im Einzelspieler änderst du sie selbst, auf
einem Server die Operatoren. Jede Regel ist auch eine Gamerule (`/gamerule mobdefense:<id> <wert>`).

| Regel | Id | Standard | Bereich | Wirkung |
|---|---|---|---|---|
| Mobs reagieren auf Drohnen | `mobs_react_to_drones` | an | – | Feindliche Mobs bemerken fliegende Drohnen und wehren sich: Zombies kommen näher, Skelette schießen, Phantome jagen. Aus: Mobs beachten Drohnen nicht. |
| Reichweite der Mobs | `mob_reaction_range` | 24 | 8–64 | Entfernung in Blöcken, in der Mobs eine Drohne bemerken. |
| Schaden durch Mobs an Drohnen | `mob_damage_to_drones_percent` | 100 | 0–400 | Schaden, den Mobs Drohnen zufügen, in Prozent des normalen Werts (0 = keiner, 400 = vierfach). |
| Payloads treffen Spieler | `payloads_hit_players` | aus | – | Schädliche Payloads (Geschosse, schädliche Tränke, Speer, TNT-Schaden) treffen Spieler. Heil- und andere positive Tränke wirken immer, Vanilla-PvP gilt zusätzlich. |
| TNT aus dem Dispenser | `allow_tnt` | an | – | Die Dispenser-Halterung darf gezündetes TNT abwerfen. Aus: Der Dispenser verweigert TNT. |
| Griefing durch Drohnen | `drone_griefing` | an | – | Payloads dürfen Blöcke verändern: TNT-Sprengschaden, Feuer aus Feuerkugel und Feuerzeug, gesetzte Blöcke und Flüssigkeiten. Vanilla tnt_explodes gilt zusätzlich. |
| Eimer mit Flüssigkeit aus dem Dispenser | `allow_fluid_buckets` | aus | – | Wasser-, Lava- und Pulverschnee-Eimer sowie Eimer mit Fischen oder Tieren dürfen ihren Inhalt setzen. Aus: Der Dispenser verweigert sie, leere Eimer schöpfen weiter. |
| Echte Munition | `real_ammo` | an | – | Jeder Schuss verbraucht ein Item. Aus: Der Slot muss belegt sein, das Item bleibt. |
| Abklingzeit des Dispensers (Ticks) | `dispenser_cooldown_ticks` | 4 | 0–100 | Ticks zwischen zwei Schüssen der Dispenser-Halterung (20 Ticks = 1 s, Vanilla-Dispenser: 4). |
| Speerschaden | `spear_damage_percent` | 100 | 0–100 | Anteil am Vanilla-Speerschaden, den die Speer-Halterung zufügt, in Prozent. |

- Die Texte sind die aus dem Spiel (aus dem Web-Export des Add-ons). Unter 4 Ticks bleibt die Abklingzeit bei 4 Ticks.
- Welten behalten die gespeicherten Werte. Welten aus einer frühen Entwicklungsfassung haben beim Speerschaden womöglich noch
  50 %: `/gamerule mobdefense:spear_damage_percent 100`.

---

## 9. Auf Servern

- Mob Defense muss **auf dem Server und auf jedem Client** liegen. Wer es nicht hat, bekommt von Propwash statt eines Fehlers
  die Übersicht „Add-ons passen nicht zum Server“ mit Name und Version von Mob Defense.
- Der Server entscheidet jeden Schuss. Aktionen kommen von Propwash geprüft an (Pilot, Ratenbegrenzung), zusätzlich zur
  Abklingzeit.
- **Rechte:** Blöcke setzen, zünden, schöpfen und sprengen geht nur, wo der Pilot bauen darf. Im Spawnschutz oder im
  Abenteuermodus verweigert der Dispenser mit `KEIN RECHT`.
- **PvP:** Besitzer jeder Payload ist der Pilot. Ist Vanilla `pvp` aus, trifft nichts Spieler. Ist „Payloads treffen
  Spieler“ aus (Standard), lassen Pfeile, schädliche Tränke, Speer und TNT-Schaden Spieler in Ruhe, Heiltränke heilen aber
  weiter – einen Mitspieler aus der Luft zu heilen, ist ausdrücklich gewollt.
- **Last:** Mobs suchen nur nach Drohnen, solange eine Drohne fliegt, zeitlich gestaffelt und mit einem festen Budget je
  Tick.
- Zuschauer einer Drohne sehen Speer und Rakete; das OSD erscheint nur im eigenen Videobild des Piloten.
- Welten bleiben ohne Add-on heil: Propwash hält die Teile als Platzhalter und die Daten der Drohne fest, mit Mob Defense
  ist alles wieder da.

---

## 10. Flugschule und Fortschritte

- **Flugschule:** M über einem der drei Teile halten (oder die Flugschule im Konfigurator öffnen) für die Kapitel
  „Mob Defense: Dispenser“, „Mob Defense: Speer“ und „Mob Defense: Raketen-Booster“.
- **Fortschritte** in einem eigenen Reiter „Mob Defense“: „Luftunterstützung“ (Mitspieler mit einem Heiltrank aus dem
  Dispenser treffen), „Lanzenflug“ (Mob mit der Speer-Halterung besiegen), „Nachbrenner“ (Raketen-Booster zünden) und
  „Rückstoß“ (Windkugel aus dem Dispenser schießen).

---

## 11. FAQ

**Warum feuert der Rechtsklick nicht?** In Propwash beendet der Rechtsklick immer den Flug, bei jeder Drohne. So bleibt der
Ausstieg überall gleich, und die Hände bleiben an der Funke. Gefeuert wird mit Zubehör-Aktion 1 – am besten auf einem
Taster der Funke.

**Geht das auf Servern?** Ja. Auf dem Server und auf jedem Client installieren. Die Welt-Regeln stellt der Betreiber ein,
siehe Abschnitt 9.

**Kann ich andere Spieler treffen?** Nur, wenn der Server es erlaubt: Die Welt-Regel „Payloads treffen Spieler“ und Vanilla
`pvp` müssen beide an sein. Heil- und andere positive Tränke wirken immer.

**Warum sind Eimer gesperrt?** Damit niemand Wasser- oder Lavasäulen in die Luft setzt. Leere Eimer schöpfen weiter.
Betreiber können Eimer mit Flüssigkeit über die Welt-Regel „Eimer mit Flüssigkeit aus dem Dispenser“ erlauben.

**Meine Drohne hebt nicht mehr ab.** Die Beladung ist zu schwer. Munition zählt mit ihrem echten Gewicht; TNT (je 600 g)
und Tränke (je 350 g) summieren sich schnell. Weniger mitnehmen oder eine größere Drohne bauen.

**Meine Drohne ist am Ende des Boosts zerbrochen.** Die Rakete hatte Sterne. Zum Boosten Raketen ohne Sterne nehmen.

**Mobs beachten meine Drohne nicht.** Prüf die Welt-Regel „Mobs reagieren auf Drohnen“ (die Voreinstellung „Training“
schaltet sie aus), die Reichweite und ob die Drohne scharf ist: Abgestellte und nicht scharfe Drohnen bleiben unbehelligt.

**Gibt es eine Reaktion von Sculk oder Wärter?** Nicht in 1.0. Wärter beachten Drohnen vorerst nicht.

---

## 12. Kompatibilität

- Minecraft 26.3 · Fabric (Loader ab 0.19.5 mit Fabric API) oder NeoForge ab 26.3.0.10-beta · Java 25.
- Braucht **Propwash FPV ab 0.6.0** (Add-on-API 1.8).
- Server und Client brauchen beide das Add-on.
- **Just More Parts:** passt; jeder JMP-Rahmen mit Halterung vorn trägt den Dispenser.
- **Kamera-Drohnen:** passt; beide nutzen die Halterung vorn, eine Drohne trägt also entweder ein Gimbal oder die
  Dispenser-Halterung.
- **Andere Mods:** Mob Defense ändert nichts in Propwash und greift nur leicht in die KI der Vanilla-Mobs ein. Ändert eine
  andere Mod einen Mob so, dass seine Reaktion nicht mehr greift, reagiert dieser Mob einfach nicht auf Drohnen, und das
  Log sagt es einmal.
- **Sharecodes:** Die drei Teile haben eigene Kurznummern in Propwashs Sharecodes. Munition gehört nicht in einen
  Sharecode. Ohne das Add-on bleibt die Halterung leer.
- Ressourcenpakete, die den Vanilla-Werfer ändern, ändern auch die Vorderseite der Dispenser-Halterung.
