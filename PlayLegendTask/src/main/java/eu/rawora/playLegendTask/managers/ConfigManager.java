package eu.rawora.playLegendTask.managers;

import eu.rawora.playLegendTask.PlayLegendTask;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class ConfigManager {

    private final PlayLegendTask plugin;
    private FileConfiguration config;
    private File configFile;
    private FileConfiguration messages;
    private File messagesFile;

    public ConfigManager(PlayLegendTask plugin) {
        this.plugin = plugin;
    }

    /**
     * Lädt beide Konfigurationsdateien (config.yml, messages.yml).
     * Kopiert Standarddateien aus dem JAR, falls sie nicht existieren.
     * Lädt Standardwerte aus dem JAR, um sicherzustellen, dass alle Schlüssel vorhanden sind.
     */
    public void loadConfigs() {
        plugin.getDataFolder().mkdirs(); // Erstellt den Plugin-Datenordner, falls nicht vorhanden

        // --- config.yml ---
        configFile = new File(plugin.getDataFolder(), "config.yml");
        // Kopiere Default aus JAR, falls Datei nicht existiert
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false); //nicht ersetzen, falls schon da
            plugin.getLogger().info("Default config.yml copied to plugin folder.");
        }
        config = YamlConfiguration.loadConfiguration(configFile);
        // Lade die Default-Werte aus dem JAR (wichtig für neue/fehlende Keys nach Updates)
        InputStream defaultConfigStream = plugin.getResource("config.yml");
        if (defaultConfigStream != null) {
            // Setze die Defaults  ,  ohne die User-Config zu überschreiben
            config.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(defaultConfigStream)));
            config.options().copyDefaults(true);
            saveConfig();
        } else {
            plugin.getLogger().warning("Could not find default config.yml in JAR!");
        }


        // --- messages.yml ---
        messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
            plugin.getLogger().info("Default messages.yml copied to plugin folder.");
        }
        // Lade die Datei
        messages = YamlConfiguration.loadConfiguration(messagesFile);
        // Lade die Default-Werte aus dem JAR
        InputStream defaultMessagesStream = plugin.getResource("messages.yml");
        if (defaultMessagesStream != null) {
            messages.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(defaultMessagesStream)));
            messages.options().copyDefaults(true);
           saveMessages();
        } else {
            plugin.getLogger().warning("Could not find default messages.yml in JAR!");
        }

        plugin.getLogger().info("Configuration files loaded.");
    }

    /**
     * Speichert die aktuelle In-Memory-Konfiguration in die config.yml Datei.
     * Nützlich, falls Einstellungen zur Laufzeit geändert werden können sollen.
     */
    public void saveConfig() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save config.yml", e);
        }
    }

    /**
     * Speichert die aktuelle In-Memory-Nachrichtenkonfiguration in die messages.yml Datei.
     * Normalerweise nicht notwendig, es sei denn Nachrichten können Ingame geändert werden. Bisher nicht eingebaut.
     */
    public void saveMessages() {
        try {
            messages.save(messagesFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save messages.yml", e);
        }
    }

    // --- Zugriffs-Methoden für Nachrichten ---

    /**
     * Holt eine Nachricht aus messages.yml und wendet Farb-Codes an.
     *
     * @param path Pfad zur Nachricht (z.B. "error.no-permission")
     * @return Farbige Nachricht oder Fehlermeldung, falls Pfad ungültig.
     */
    public String getMessage(String path) {
        // Holt den String oder einen Standard-Fehlertext, falls der Pfad nicht existiert
        String rawMessage = messages.getString(path, "&cMessage not found: " + path);
        return ChatColor.translateAlternateColorCodes('&', rawMessage);
    }

    /**
     * Holt eine Nachricht und stellt den Plugin-Prefix voran.
     *
     * @param path Pfad zur Nachricht in messages.yml
     * @return Farbige, vorangestellte Nachricht.
     */
    public String getPrefixedMessage(String path) {
        return getMessage("prefix") + getMessage(path); // TODO: Check ob prefix überhaupt  in messages.yml definiert ist
    }

    /**
     * Holt eine Nachricht, ersetzt Platzhalter und wendet Farb-Codes an.
     *
     * @param path         Pfad zur Nachricht
     * @param replacements Platzhalter und ihre Werte (z.B. "%player%", "Steve", "%group%", "Admin")
     * @return Formatierte und farbige Nachricht.
     */
    public String getFormattedMessage(String path, String... replacements) {
        String message = getMessage(path);
        if (replacements.length % 2 != 0) {
            plugin.getLogger().warning("Invalid number of replacements provided for message path: " + path);
            return message;
        }
        for (int i = 0; i < replacements.length; i += 2) {
            if (replacements[i] == null || replacements[i + 1] == null) {
                plugin.getLogger().warning("Null replacement found for message path: " + path + " at index " + i);
                continue;
            }
            message = message.replace(replacements[i], replacements[i + 1]);
        }
        return message;
    }

    /**
     * Kombiniert getPrefixedMessage und getFormattedMessage.
     *
     * @param path         Pfad zur Nachricht
     * @param replacements Platzhalter und ihre Werte
     * @return Formatierte, farbige Nachricht mit Plugin-Prefix.
     */
    public String getFormattedPrefixedMessage(String path, String... replacements) {
        return getMessage("prefix") + getFormattedMessage(path, replacements);
    }

    // --- Zugriffs-Methoden für Einstellungen aus config.yml ---

    // Datenbank
    public String getDBType() {
        return config.getString("database.type", "sqlite").toLowerCase();
    }

    public String getSQLiteFilename() {
        return config.getString("database.sqlite.filename", "playerdata.db");
    }

    public String getDBHost() {
        return config.getString("database.mysql.host", "localhost");
    }

    public int getDBPort() {
        return config.getInt("database.mysql.port", 3306);
    }

    public String getDBName() {
        return config.getString("database.mysql.database", "playlegend_groups");
    }

    public String getDBUser() {
        return config.getString("database.mysql.username", "user");
    }

    public String getDBPassword() {
        return config.getString("database.mysql.password", "password");
    }
    // TODO: Man könnte hier auch getBoolean für useSSL etc. hinzufügen

    // Gruppen
    public String getDefaultGroupName() {
        return config.getString("default-group", "Default");
    }

    // Schilder (Signs)
    public boolean isSignsEnabled() {
        return config.getBoolean("signs.enabled", true);
    }

    public String getSignCreationIdentifier() {
        return config.getString("signs.creation-identifier", "[GroupInfo]");
    }

    public long getSignUpdateInterval() {
        return config.getLong("signs.update-interval", 100L);
    }

    // Holt die Formatierungszeile für Schilder (Index 1-4)
    public String getSignLine(int line) {
        if (line < 1 || line > 4) return ""; // Gültige Zeilen sind 1-4
        return config.getString("signs.line" + line, ""); // Holt z.B. "signs.line1"
    }

    // Tablist
    public boolean isTablistEnabled() {
        return config.getBoolean("tablist.enabled", true);
    }

    public String getTablistFormat() {
        return config.getString("tablist.format", "%group_prefix% &r%player%");
    }

    // Scoreboard
    public boolean isScoreboardEnabled() {
        return config.getBoolean("scoreboard.enabled", true);
    }

    // Gibt den Titel bereits farbig zurück
    public String getScoreboardTitle() {
        return ChatColor.translateAlternateColorCodes('&', config.getString("scoreboard.title", "&ePlayer Info"));
    }

    // Gibt die Zeilen bereits farbig zurück
    public List<String> getScoreboardLines() {
        return config.getStringList("scoreboard.lines").stream()
                .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                .collect(Collectors.toList());
    }

    // Direkter Zugriff auf die FileConfiguration Objekte, falls benötigt
    public FileConfiguration getConfig() {
        return config;
    }

    public FileConfiguration getMessages() {
        return messages;
    }
}