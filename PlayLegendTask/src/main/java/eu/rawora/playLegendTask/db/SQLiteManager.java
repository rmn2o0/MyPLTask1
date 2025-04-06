package eu.rawora.playLegendTask.db;

import eu.rawora.playLegendTask.PlayLegendTask;
import eu.rawora.playLegendTask.model.Group;
import eu.rawora.playLegendTask.model.PlayerGroupInfo;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class SQLiteManager implements DatabaseManager {

    private final PlayLegendTask plugin;
    private Connection connection;
    private final String dbPath;

    public SQLiteManager(PlayLegendTask plugin) {
        this.plugin = plugin;
        this.dbPath = plugin.getDataFolder().getAbsolutePath() + File.separator + plugin.getConfigManager().getSQLiteFilename();
    }

    @Override
    public void connect() throws Exception {
        File dbFile = new File(dbPath);
        // Erstellt Ordner falls nötig
        if (!dbFile.getParentFile().exists()) {
            dbFile.getParentFile().mkdirs();
        }
        // Erstellt Datei , falls nötig
        if (!dbFile.exists()) {
            try {
                dbFile.createNewFile();
            } catch (IOException e) {
                throw new SQLException("Could not create SQLite database file!", e);
            }
        }

        try {
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        } catch (SQLException e) { // TODO: ggf. noch ClassNotFoundException catchen
            throw new SQLException("SQLite connection failed!", e);
        }
    }

    @Override
    public void disconnect() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error disconnecting SQLite database", e);
        }
    }

    /**
     * Erstellt die benötigten Tabellen asynchron, falls sie nicht existieren.
     *
     // TODO: Wechsel auf Synchron
     */
    @Override
    public void initializeDatabase() {
        // Führt die Tabellenerstellung  SYNCHRON aus, um Race Conditions zu vermeiden..
        // Da dies aber meist nur beim allerersten Start etwas tut, ist der Performance-Impact gering.
        String groupsTable = "CREATE TABLE IF NOT EXISTS groups ("
                + "name TEXT PRIMARY KEY NOT NULL,"
                + "prefix TEXT"
                // + ", permissions TEXT" // TODO: für Permissions ggf. so einpflegen
                + ");";

        String playerGroupsTable = "CREATE TABLE IF NOT EXISTS player_groups ("
                + "uuid TEXT PRIMARY KEY NOT NULL,"
                + "group_name TEXT,"
                + "expiry_time INTEGER,"
                + "FOREIGN KEY (group_name) REFERENCES groups(name) ON DELETE SET NULL"
                + ");";

        String signsTable = "CREATE TABLE IF NOT EXISTS signs ("
                + "world TEXT NOT NULL,"
                + "x INTEGER NOT NULL,"
                + "y INTEGER NOT NULL,"
                + "z INTEGER NOT NULL,"
                + "target_uuid TEXT NOT NULL,"
                + "PRIMARY KEY (world, x, y, z)"
                + ");";

        try (Statement statement = connection.createStatement()) {
            statement.execute(groupsTable);
            statement.execute(playerGroupsTable);
            statement.execute(signsTable);
            plugin.getLogger().info("SQLite tables checked/created successfully.");
        } catch (SQLException e) {
            // Logge den Fehler kritisch, da die DB-Struktur essentiell ist
            plugin.getLogger().log(Level.SEVERE, "CRITICAL: Could not create SQLite tables! Plugin might not work correctly.", e);
            Bukkit.getPluginManager().disablePlugin(plugin); // Deaktivieren, da sonst keine Datenbank da
        }
    }

    // --- Implementierung der Interface-Methoden (asynchron) ---

    @Override
    public CompletableFuture<Void> saveGroupAsync(Group group) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT OR REPLACE INTO groups (name, prefix) VALUES (?, ?)";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, group.getName()); // Benutze den Originalnamen
                pstmt.setString(2, group.getRawPrefix()); // Speichere raw Prefix
                pstmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not save group: " + group.getName(), e);
                throw new RuntimeException(e); // Wichtig für CompletableFuture Fehlerbehandlung
            }
        }, runnable -> Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    @Override
    public CompletableFuture<Void> deleteGroupAsync(String groupName) {
         return CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM groups WHERE name = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, groupName);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not delete group: " + groupName, e);
                 throw new RuntimeException(e);
            }
        }, runnable -> Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    @Override
    public CompletableFuture<Group> getGroupAsync(String groupName) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT prefix FROM groups WHERE name = ? COLLATE NOCASE";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, groupName);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    return new Group(groupName, rs.getString("prefix"));
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not get group: " + groupName, e);
                 throw new RuntimeException(e);
            }
            return null; // Gruppe nicht gefunden
        }, runnable -> Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable));
    }


    @Override
    public CompletableFuture<List<Group>> getAllGroupsAsync() {
        return CompletableFuture.supplyAsync(() -> {
            List<Group> groups = new ArrayList<>();
            String sql = "SELECT name, prefix FROM groups";
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    groups.add(new Group(rs.getString("name"), rs.getString("prefix")));
                }
            } catch (SQLException e) {
                 plugin.getLogger().log(Level.SEVERE, "Could not get all groups", e);
                 throw new RuntimeException(e);
            }
            return groups;
        }, runnable -> Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    @Override
    public CompletableFuture<Void> updateGroupPrefixAsync(String groupName, String prefix) {
         return CompletableFuture.runAsync(() -> {
            String sql = "UPDATE groups SET prefix = ? WHERE name = ? COLLATE NOCASE";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, prefix); // Speichere raw Prefix
                pstmt.setString(2, groupName);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not update prefix for group: " + groupName, e);
                 throw new RuntimeException(e);
            }
        }, runnable -> Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    @Override
    public CompletableFuture<Void> setPlayerGroupAsync(UUID playerUUID, String groupName, Long expiryTime) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT OR REPLACE INTO player_groups (uuid, group_name, expiry_time) VALUES (?, ?, ?)";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, playerUUID.toString());
                pstmt.setString(2, groupName); // Gruppenname (kann NULL sein, wenn Gruppe gelöscht wurde) TODO: hier ggf. noch zwischen Handling
                if (expiryTime == null) {
                    pstmt.setNull(3, Types.INTEGER); // Verwende INTEGER für SQLite Timestamps
                } else {
                    pstmt.setLong(3, expiryTime);
                }
                pstmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not set player group for " + playerUUID, e);
                throw new RuntimeException(e);
            }
        }, runnable -> Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable));
    }


    @Override
    public CompletableFuture<PlayerGroupInfo> getPlayerGroupInfoAsync(UUID playerUUID) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT group_name, expiry_time FROM player_groups WHERE uuid = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, playerUUID.toString());
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    String groupName = rs.getString("group_name");
                    // Wenn group_name NULL ist (weil Gruppe gelöscht), gib null zurück oder Default? Hier: null , aber TODO: hier ggf. noch weiter handlen
                    if (groupName == null) {
                         plugin.getLogger().warning("Player " + playerUUID + " references a NULL group (likely deleted).");
                         return null; // TODO: ggf. einfach Default zuweisen, oder ein anderes "eigenes" Objekt returnen
                    }
                    long expiryTimestamp = rs.getLong("expiry_time");
                    Long expiry = rs.wasNull() ? null : expiryTimestamp;
                    return new PlayerGroupInfo(playerUUID, groupName, expiry);
                }
            } catch (SQLException e) {
                 plugin.getLogger().log(Level.SEVERE, "Could not get player group info for " + playerUUID, e);
                 throw new RuntimeException(e);
            }
            return null; // Spieler nicht in der DB gefunden
        }, runnable -> Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    @Override
     public CompletableFuture<Void> removePlayerFromGroupAsync(UUID playerUUID) {
        return CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM player_groups WHERE uuid = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, playerUUID.toString());
                pstmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not remove player group data for " + playerUUID, e);
                throw new RuntimeException(e);
            }
        }, runnable -> Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    @Override
    public CompletableFuture<Void> saveSignLocationAsync(Location location, UUID targetPlayerUUID) {
         return CompletableFuture.runAsync(() -> {
             String sql = "INSERT OR REPLACE INTO signs (world, x, y, z, target_uuid) VALUES (?, ?, ?, ?, ?)";
             try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                 pstmt.setString(1, location.getWorld().getName());
                 pstmt.setInt(2, location.getBlockX());
                 pstmt.setInt(3, location.getBlockY());
                 pstmt.setInt(4, location.getBlockZ());
                 pstmt.setString(5, targetPlayerUUID.toString());
                 pstmt.executeUpdate();
             } catch (SQLException e) {
                 plugin.getLogger().log(Level.SEVERE, "Could not save sign location: " + location, e);
                 throw new RuntimeException(e);
             }
         }, runnable -> Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    @Override
    public CompletableFuture<Void> deleteSignLocationAsync(Location location) {
         return CompletableFuture.runAsync(() -> {
             String sql = "DELETE FROM signs WHERE world = ? AND x = ? AND y = ? AND z = ?";
             try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                 pstmt.setString(1, location.getWorld().getName());
                 pstmt.setInt(2, location.getBlockX());
                 pstmt.setInt(3, location.getBlockY());
                 pstmt.setInt(4, location.getBlockZ());
                 pstmt.executeUpdate();
             } catch (SQLException e) {
                 plugin.getLogger().log(Level.SEVERE, "Could not delete sign location: " + location, e);
                 throw new RuntimeException(e);
             }
         }, runnable -> Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    @Override
    public CompletableFuture<Map<Location, UUID>> getAllSignLocationsAsync() {
         return CompletableFuture.supplyAsync(() -> {
             Map<Location, UUID> signLocations = new HashMap<>();
             String sql = "SELECT world, x, y, z, target_uuid FROM signs";
             try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
                 while (rs.next()) {
                     World world = Bukkit.getWorld(rs.getString("world"));
                     if (world != null) {
                         Location loc = new Location(world, rs.getInt("x"), rs.getInt("y"), rs.getInt("z"));
                         UUID targetUUID = UUID.fromString(rs.getString("target_uuid"));
                         signLocations.put(loc, targetUUID);
                     } else {
                         plugin.getLogger().warning("Could not load sign location: World '" + rs.getString("world") + "' not found.");
                     }
                 }
             } catch (SQLException | IllegalArgumentException e) {
                 plugin.getLogger().log(Level.SEVERE, "Could not load sign locations", e);
                 throw new RuntimeException(e);
             }
             return signLocations;
         }, runnable -> Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable));
    }
}