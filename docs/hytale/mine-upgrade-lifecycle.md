# Mine-Upgrade-Lifecycle

## Stabile Building-ID und Worker-Runtime

Minen behalten bei einem Upgrade ihre fachliche Building-ID. Dadurch bleiben persistente Arbeitsplatz-Zuweisungen der Bewohner stabil, obwohl die aktive Building-Instanz durch die nächste Phase ersetzt wird.

Für Worker-Runtime ist die Building-ID allein deshalb keine ausreichende Identität. `MinerWorkSystem` behandelt zusätzlich einen Wechsel der Gebäudephase als Runtime-Wechsel. Wenn dieselbe Mine von Phase 1 auf 2 oder von 2 auf 3 wechselt, werden der lokale Eingangs-, Connector-, Segment- und Navigationszustand des Miners zurückgesetzt. Die Arbeitsplatz-ID bleibt unverändert; der Miner läuft anschließend selbstständig über die Marker der neuen Phase wieder in die Mine und setzt seine Arbeit fort.

Das erzeugt keinen zusätzlichen häufigen Tick. Die Prüfung erfolgt innerhalb der bereits verzögerten Miner-Session.

## Portal vor dem `mine_tunnel_connector`

Der authored `mine_tunnel_connector` bleibt bei allen drei Minenphasen an seiner bestehenden Position. Der sichtbare Holzrahmen gehört nicht in die erste Abbau-Reihe direkt am Connector.

Als Authoring-Regel gilt deshalb:

- Der Holzrahmen liegt einen Block weiter im bereits gebauten Minenraum.
- Die direkt zum Tunnel zeigende Reihe bleibt Stein, damit der erste Abbau des Miners keine tragenden Holzblöcke zerstört.
- Zugehörige Laternen werden zusammen mit dem Holzrahmen verschoben.
- `Mine_01` besitzt an diesem Portal bewusst keine Laternen; `Mine_02` und `Mine_03` schon.

So bleibt der fertige Portalrahmen optisch erhalten, während der Miner hinter ihm in den natürlichen Fels weitergräbt.
