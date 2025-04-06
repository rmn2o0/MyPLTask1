package eu.rawora.playLegendTask;

import eu.rawora.playLegendTask.commands.GroupCommand;
import eu.rawora.playLegendTask.commands.GroupInfoCommand;
import eu.rawora.playLegendTask.commands.SetGroupCommand;
import eu.rawora.playLegendTask.db.DatabaseManager;
import eu.rawora.playLegendTask.db.MySQLManager;
import eu.rawora.playLegendTask.db.SQLiteManager;
import eu.rawora.playLegendTask.listeners.PlayerChatListener;
import eu.rawora.playLegendTask.listeners.PlayerJoinQuitListener;
import eu.rawora.playLegendTask.listeners.SignListener;
import eu.rawora.playLegendTask.managers.*;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Objects;
import java.util.logging.Level;

public final class PlayLegendTask extends JavaPlugin {

    // Statische Instanz für einfachen Zugriff von überall
    private static PlayLegendTask instance;

    // Instanzen der Manager-Klassen
    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private GroupManager groupManager;
    private PlayerDataManager playerDataManager;
    private PermissionManager permissionManager;
    private SignManager signManager; // Manager für die Info-Schilder

    // Task für die regelmäßige Aktualisierung der Schilder
    private BukkitTask signUpdateTask;

    @Override
    public void onEnable() {
        instance = this; // Statische Instanz setzen
        getLogger().info("Enabling PlayLegendTask Plugin...");

        // 1. ConfigManager initialisieren und Konfigurationen laden
        // (liest config.yml und messages.yml ein)
        configManager = new ConfigManager(this);
        configManager.loadConfigs();
        getLogger().info("Configuration files loaded.");

        // 2. DatabaseManager initialisieren und Datenbankverbindung aufbauen
        // (entscheidet basierend auf config.yml, ob SQLite oder MySQL)
        if (!initializeDatabase()) {
            // Wenn DB-Verbindung fehlschlägt, Plugin deaktivieren
            getLogger().severe("Database initialization failed! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 3. GroupManager initialisieren (verwaltet Gruppendefinitionen)
        // Lädt Gruppen aus der Datenbank in einen Cache
        groupManager = new GroupManager(this);
        groupManager.loadGroupsFromDatabase();

        // 4. PermissionManager initialisieren (verwaltet Bukkit Permissions)
        permissionManager = new PermissionManager(this);

        // 5. PlayerDataManager initialisieren (verwaltet Online-Spielerdaten)
        playerDataManager = new PlayerDataManager(this);

        // 6. SignManager initialisieren (verwaltet Info-Schilder)
        signManager = new SignManager(this);
        signManager.loadSigns();

        // 7. Befehle registrieren
        registerCommands();

        // 8. Event Listener registrieren
        registerListeners();

        // 9. Spieler handlenn, die beim Plugin-Start bereits online sind (wichtig für /reload)
        playerDataManager.initializeOnlinePlayers();

        // 10. Task starten, der die Info-Schilder regelmäßig aktualisiert (falls aktiviert)
        startSignUpdateTask();

        getLogger().info("PlayLegendTask Plugin enabled successfully!");
    }

    @Override
    public void onDisable() {
        getLogger().info("Disabling PlayLegendTask Plugin...");

        // 1. Geplante Tasks stoppen
        if (signUpdateTask != null && !signUpdateTask.isCancelled()) {
            signUpdateTask.cancel();
            getLogger().info("Sign update task cancelled.");
        }

        // 2. Spielerdaten aufräumen (Permission Attachments entfernen etc.)
        if (playerDataManager != null) {
            playerDataManager.cleanupAllPlayers();
        }

        // 3. Datenbankverbindung schließen
        if (databaseManager != null) {
            databaseManager.disconnect();
            getLogger().info("Database connection closed.");
        }

        getLogger().info("PlayLegendTask Plugin disabled.");
        instance = null;
    }

    /**
     * Initialisiert den passenden DatabaseManager (SQLite oder MySQL)
     * basierend auf der Konfiguration und baut die Verbindung auf.
     *
     * @return true bei Erfolg, false bei Fehlern.
     */
    private boolean initializeDatabase() {
        String dbType = configManager.getDBType().toLowerCase();
        getLogger().info("Initializing database connection (Type: " + dbType + ")");
        try {
            if (dbType.equals("mysql")) {
                databaseManager = new MySQLManager(this);
            } else { // Standard: SQLite
                if (!dbType.equals("sqlite")) {
                    getLogger().warning("Database type '" + configManager.getDBType() + "' not recognized. Defaulting to SQLite.");
                }
                databaseManager = new SQLiteManager(this);
            }
            databaseManager.connect();          // Verbindung aufbauen
            databaseManager.initializeDatabase(); // Tabellen erstellen/prüfen
            getLogger().info("Database connection successful and initialization started.");
            return true;
        } catch (Exception e) { // Fängt  Fehler ab
            getLogger().log(Level.SEVERE, "Database connection/initialization failed!", e);
            return false;
        }
    }

    /**
     * Registriert die Befehle
     */
    private void registerCommands() {
        // Gruppe Command + TabCompleter
        GroupCommand groupExecutor = new GroupCommand(this);
        Objects.requireNonNull(getCommand("group"), "Command 'group' is not defined in plugin.yml!").setExecutor(groupExecutor);
        Objects.requireNonNull(getCommand("group"), "Command 'group' is not defined in plugin.yml!").setTabCompleter(groupExecutor);

        // SetGroup Command + TabCompleter
        SetGroupCommand setGroupExecutor = new SetGroupCommand(this);
        Objects.requireNonNull(getCommand("setgroup"), "Command 'setgroup' is not defined in plugin.yml!").setExecutor(setGroupExecutor);
        Objects.requireNonNull(getCommand("setgroup"), "Command 'setgroup' is not defined in plugin.yml!").setTabCompleter(setGroupExecutor);

        // GroupInfo Command (kein TabCompleter benötigt)
        Objects.requireNonNull(getCommand("groupinfo"), "Command 'groupinfo' is not defined in plugin.yml!").setExecutor(new GroupInfoCommand(this));

        getLogger().info("Commands registered.");
    }

    /**
     * Registriert die Events
     */
    private void registerListeners() {
        PluginManager pm = Bukkit.getPluginManager();

        // Kern-Listener
        pm.registerEvents(new PlayerJoinQuitListener(this), this);
        pm.registerEvents(new PlayerChatListener(this), this);

        // Sign-Listener nur registrieren, wenn Schilder in der Config aktiviert sind
        if (configManager.isSignsEnabled()) {
            pm.registerEvents(new SignListener(this), this);
            getLogger().info("Sign Listener registered (Signs are enabled).");
        } else {
            getLogger().info("Sign Listener not registered (Signs are disabled in config).");
        }

        getLogger().info("Core Event Listeners registered.");
    }

    /**
     * Startet den Task, der Info-Schilder regelmäßig aktualisiert.
     */
    private void startSignUpdateTask() {
        // Nur starten, wenn Schilder aktiviert sind und der Manager existiert
        if (!configManager.isSignsEnabled() || signManager == null) return;

        long interval = configManager.getSignUpdateInterval() * 20; // Intervall aus Config holen, Mal 20 für die Umrechnung zu Tics
        if (interval <= 0) {
            getLogger().warning("Sign update interval is zero or negative in config.yml, sign updates disabled.");
            return;
        }

        // Starte einen asynchronen, wiederholenden Task.
        // Die eigentliche Block-Aktualisierung im SignManager wird dann wieder auf dem Hauptthread ausgeführt.
        signUpdateTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            signManager.updateAllSigns(); // Diese Methode kümmert sich ums Thread-Management intern
        }, 100L, interval); // Wiederholung alle 'interval' Ticks

        getLogger().info("Sign update task scheduled to run every " + interval + " ticks.");
    }


    /**
     * Gibt die statische Instanz des Plugins zurück.
     *
     * @return PlayLegendTask Instanz
     */
    public static PlayLegendTask getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public GroupManager getGroupManager() {
        return groupManager;
    }

    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    public PermissionManager getPermissionManager() {
        return permissionManager;
    }

    public SignManager getSignManager() {
        return signManager;
    }
}