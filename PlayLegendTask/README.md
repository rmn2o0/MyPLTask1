# PLTask Plugin

## Beschreibung

Minecraft-Plugin, das ein Gruppensystem mit Berechtigungen und zeitlich begrenzten Mitgliedschaften bietet. Es speichert alle notwendigen Informationen in einer Datenbank.
Das Plugin ermöglicht die Verwaltung von Gruppen, das Hinzufügen von Spielern zu Gruppen, und das Zuweisen von Zeitbeschränkungen für die Gruppenmitgliedschaft.


## Implementierte Features / Umgesetzte Anforderungen

Dieser Abschnitt gibt einen Überblick darüber, wie die Anforderungen aus der Playlegend Bewerber-Aufgabe (Dezember 2024) in diesem Plugin umgesetzt wurden.

### Mindestanforderungen (Minimum Requirements)

* **Gruppenverwaltung Ingame:**
    * **Status:** ✅ Erledigt
    * **Komponenten:** `/group` Befehl (`GroupCommand.java`), `GroupManager.java`, Datenbank-Layer (`DatabaseManager`, `SQLiteManager`, `MySQLManager`)

* **Gruppeneigenschaften (Name, Prefix):**
    * **Status:** ✅ Erledigt
    * **Komponenten:** `model/Group.java`, `GroupManager.java`, Datenbank-Layer

* **Spielerzuweisung (Permanent):**
    * **Status:** ✅ Erledigt
    * **Komponenten:** `/setgroup` Befehl (`SetGroupCommand.java`), `PlayerDataManager.java`, Datenbank-Layer (speichert `null` expiry)

* **Spielerzuweisung (Temporär mit Zeitangabe):**
    * **Status:** ✅ Erledigt
    * **Komponenten:** `/setgroup` Befehl (`SetGroupCommand.java`), `util/TimeUtil.java` (Zeit-Parser), `PlayerDataManager.java` (Ablauf-Logik), Datenbank-Layer (speichert Timestamp)

* **Prefix-Anzeige (Chat & Join/Tablist):**
    * **Status:** ✅ Erledigt
    * **Komponenten:** `PlayerChatListener.java`, `PlayerDataManager.java` (`updatePlayerVisuals`), `GroupManager.java`

* **Sofortige Aktualisierung (ohne Kick):**
    * **Status:** ✅ Erledigt
    * **Komponenten:** Befehle -> Manager -> `PlayerDataManager.updatePlayerVisuals` aktualisiert Cache und Spieler-Anzeige sofort.

* **Anpassbare Nachrichten (Config File):**
    * **Status:** ✅ Erledigt
    * **Komponenten:** `messages.yml`, `ConfigManager.java`

* **Befehl für Spielerinfo (/groupinfo):**
    * **Status:** ✅ Erledigt
    * **Komponenten:** `GroupInfoCommand.java`, `PlayerDataManager.java`, `TimeUtil.java`, `ConfigManager.java`

* **Info-Schilder (Name & Rang):**
    * **Status:** ✅ Erledigt
    * **Komponenten:** `SignListener.java`, `SignManager.java`, Datenbank-Layer, `config.yml`

* **Relationale Datenbank-Speicherung:**
    * **Status:** ✅ Erledigt
    * **Komponenten:** `db/` Package (Interface + SQLite/MySQL Implementierungen), `config.yml` (Konfiguration). Speichert Gruppen, Spielerzuweisungen, Schilder.

### Bonus-Aufgaben (Bonus Tasks)

* **Gruppen-Permissions & `hasPermission`-Check:**
    * **Status:** ⚙️ Mechanismus implementiert
    * **Komponenten:** `PermissionManager.java` (via Bukkit `PermissionAttachment`), `PlayerDataManager.java`
    * **Hinweis:** Laden/Speichern der Permissions *pro Gruppe* muss noch implementiert werden (z.B. via Datenbank oder extra Config). Der technische Unterbau ist vorhanden.

* **`*`-Permission:**
    * **Status:** ✅ Unterstützt (durch Bukkit)
    * **Komponenten:** `PermissionManager.java` (via Bukkit API)
    * **Hinweis:** Funktioniert, wenn Gruppen-Permissions (inkl. `*`) korrekt geladen und via `PermissionManager` gesetzt werden.

* **Multi-Sprachunterstützung:**
    * **Status:** ⚙️ Teilweise Erledigt (Framework)
    * **Komponenten:** `messages.yml`, `ConfigManager.java`
    * **Hinweis:** Momentan nur *eine* Sprache via `messages.yml` anpassbar. Echte Mehrsprachigkeit benötigt noch Erweiterungen (Spracherkennung, Laden verschiedener Dateien).

* **Sortierte Tablist mit Gruppe:**
    * **Status:** ⚙️ Teilweise Erledigt (Prefix angezeigt)
    * **Komponenten:** `PlayerDataManager.java`, `config.yml` (Format)
    * **Hinweis:** Der Gruppen-Prefix wird dem Spielernamen vorangestellt. Eine echte Sortierung nach Gruppenrang/-gewicht ist nicht implementiert und erfordert noch weitere Logik.

* **Scoreboard mit Gruppe:**
    * **Status:** ✅ Erledigt
    * **Komponenten:** `PlayerDataManager.java` (`updateScoreboard`), `config.yml` (Titel, Zeilen)

---

**Hinweis:** Dieses Plugin wurde als meine Musterlösung für die Bewerberaufgabe entwickelt und implementiert alle Kernanforderungen sowie die Grundlagen für die Bonusaufgaben. 
Es unterstützt SQLite und MySQL und nutzt asynchrone Datenbankoperationen.