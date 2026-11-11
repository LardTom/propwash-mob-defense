# Propwash: Mob Defense

**Deine Drohne hat jetzt einen Dispenser.** Mob Defense bringt drei Zubehörteile für Drohnen aus Propwash FPV: eine
Dispenser-Halterung vorn, die Pfeile, Tränke, Windkugeln und TNT feuert, eine Speer-Halterung unten, die Mobs im
Vorbeiflug trifft, und einen Raketen-Booster oben für einen Schub wie mit Elytren. Und die Mobs wehren sich: Zombies
kommen näher, Skelette schießen, Phantome jagen.

> **Braucht [Propwash FPV](https://modrinth.com/mod/propwash-fpv) ab 0.6.0** auf Client und Server. Das ist ein
> offizielles Add-on: Es bringt das Zubehör und die Reaktionen der Mobs; geflogen wird mit Propwash.

---

## Öffentlicher Server

Spiel Propwash mit anderen auf unserem öffentlichen Server **`mc.propwashfpv.com`**. Seit Propwash 0.6.0 trittst du mit
**Fabric oder NeoForge** bei. Trag die Adresse in deine Serverliste ein; fehlt dir eine Mod, nennt sie der
Beitrittsbildschirm.

## Drei Zubehörteile

| | Halterung | Was es tut |
|---|---|---|
| **Dispenser-Halterung** | vorn | vier Slots, feuert immer gerade nach vorn: Pfeile, Wurf- und Verweiltränke, Windkugeln, Feuerkugeln, TNT und alles andere, was ein Vanilla-Werfer kann |
| **Speer-Halterung** | unten | ein Speer von Holz bis Netherit, trifft von selbst ab 4,6 m/s mit Vanilla-Speerschaden |
| **Raketen-Booster** | oben | eine Feuerwerksrakete, 2 g entlang der Nase für 1,25 bis 2,25 s, wie mit Elytren |

- **Echtes Gewicht:** Jedes Teil und jede Munition zählt. Vier Tränke oder vier TNT machen eine 5″ spürbar träger; eine
  7″ trägt sie am besten.
- **Echter Rückstoß:** Eine Windkugel stößt einen Cinewhoop und eine 5″ spürbar zurück, beide bleiben fliegbar.
- **Rezepte im Überlebensmodus** aus Vanilla-Items und den Zwischenteilen von Propwash, angebaut an Propwashs Werkbank,
  beladen in der Wartung.
- **Eigene Modelle im Stil von Propwash:** Die Vorderseite des Dispensers nutzt die Vanilla-Textur des Werfers,
  Ressourcenpakete ändern sie also mit.

## Feuern

Gefeuert wird mit **Zubehör-Aktion 1**, am besten auf einem Taster der Funke, auf der Tastatur Ziffernblock 9. Ein kurzer
Druck feuert einmal, gehalten feuert der Dispenser weiter; die eigene Schussfolge stellst du im Konfigurator im Tab
„Mob Defense“ ein. Zubehör-Aktion 2 wählt den nächsten Slot, Zubehör-Aktion 3 zündet den Booster. Der Rechtsklick
beendet wie bei jeder Drohne den Flug.

Das OSD zeigt gewählten Slot, Munition und Anzahl (`DSP 2 ARW 12`), den Booster (`RKT RDY`) und Warnungen wie
`KEINE MUNITION`, `TNT GESPERRT` oder `BEDROHUNG`. Kapitel in der Flugschule erklären alle drei Teile im Spiel.

## Die Mobs wehren sich

Jeder feindliche Vanilla-Mob reagiert auf seine Art auf fliegende Drohnen:

- **Zombies** werden angelockt und schlagen nach tiefen Drohnen, **Skelette** und **Plünderer** halten vor,
- **Lohen** und **Ghasts** schießen, eine **Böe** pustet die Drohne weg, **Shulker-Geschosse** folgen ihr,
- **Phantome** kreisen und stoßen herab, **Creeper** warten unter tiefen Drohnen,
- **Endermen** werden wütend, wenn die FPV-Kamera ihnen ins Gesicht sieht, und ein **Knarz** erstarrt unter ihrem Blick.

Wächter und Große Wächter erfassen scharfe Drohnen mit dem Laser wie Spieler, und Shulker-Geschosse lassen eine Drohne
schweben. Mobs reagieren nur auf scharfe Drohnen oder Drohnen im Autopilot, und ein Mob, der hinter einem Spieler her
ist, bleibt beim Spieler. Datenpakete
und andere Mods tragen ihre Mobs über Entity-Tags ein.

## Für Server gemacht

- **Welt-Regeln** in einem eigenen Abschnitt: Mob-Reaktionen an oder aus, Reichweite, Schaden durch Mobs an Drohnen,
  Payloads gegen Spieler (Standard aus), TNT (standardmäßig erlaubt), Griefing durch Drohnen, Eimer mit Flüssigkeit
  (Standard aus), echte Munition, Abklingzeit des Dispensers und Speerschaden.
- Der Server entscheidet jeden Schuss; Spawnschutz, Abenteuermodus und Vanilla-PvP gelten. Heiltränke heilen immer,
  Luftunterstützung für Mitspieler.
- Wer das Add-on nicht hat, bekommt eine klare Meldung mit Name und Version statt eines Fehlers.

## Kompatibilität

- Minecraft 26.3 · Fabric (Loader ab 0.19.5, Fabric API) oder NeoForge 26.3 · Java 25.
- Propwash FPV ab 0.6.0, auf dem Server und auf jedem Client.
- Passt zu **Just More Parts** und **Kamera-Drohnen** (eine Drohne trägt vorn entweder ein Gimbal oder den Dispenser). Die
  Teile reisen in Propwashs Sharecodes mit, die Munition nicht.
- Englisch und Deutsch.

---

Anleitung, Welt-Regeln und FAQ: [propwashfpv.com](https://propwashfpv.com).

Von lardtom. Kein offizielles Minecraft-Produkt. Nicht von Mojang oder Microsoft genehmigt oder mit ihnen verbunden.

Propwash und seine Add-ons sind kostenlos. Wenn sie dir gefallen, kannst du das Projekt auf
[Ko-fi](https://ko-fi.com/propwashfpv) unterstützen. Danke!
