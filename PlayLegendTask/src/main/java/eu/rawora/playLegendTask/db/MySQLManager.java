package eu.rawora.playLegendTask.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import eu.rawora.playLegendTask.PlayLegendTask;
import eu.rawora.playLegendTask.model.Group;
import eu.rawora.playLegendTask.model.PlayerGroupInfo;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class MySQLManager implements DatabaseManager {

    private final PlayLegendTask plugin;
    private HikariDataSource dataSource; // Connection Pool

    public MySQLManager(PlayLegendTask plugin) {
        this.plugin = plugin;
    }

    /**
     * Baut den Connection Pool zu MySQL auf, basierend auf der config.yml.
     * @throws SQLException Wenn die Pool-Initialisierung fehlschlägt.
     */
    @Override
    public void connect() throws Exception {
         HikariConfig config = new HikariConfig();

         // Lese DB-Details aus ConfigManager
         String host = plugin.getConfigManager().getDBHost();
         int port = plugin.getConfigManager().getDBPort();
         String dbName = plugin.getConfigManager().getDBName();
         String user = plugin.getConfigManager().getDBUser();
         String pass = plugin.getConfigManager().getDBPassword();
         // Lese optionale Parameter aus config.yml (mit Defaults)
         boolean useSSL = plugin.getConfigManager().getConfig().getBoolean("database.mysql.useSSL", false);
         boolean autoReconnect = plugin.getConfigManager().getConfig().getBoolean("database.mysql.autoReconnect", true);
         String serverTimezone = plugin.getConfigManager().getConfig().getString("database.mysql.serverTimezone", "UTC"); // Wichtig für Timestamps

         // Erstelle JDBC URL
         config.setJdbcUrl(String.format("jdbc:mysql://%s:%d/%s?useSSL=%b&autoReconnect=%b&serverTimezone=%s",
                 host, port, dbName, useSSL, autoReconnect, serverTimezone));

         // Setze Zugangsdaten
         config.setUsername(user);
         config.setPassword(pass);

         //  "best practice " optimierte HikariCP-Einstellungen
         config.addDataSourceProperty("cachePrepStmts", "true");
         config.addDataSourceProperty("prepStmtCacheSize", "250");
         config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
         config.addDataSourceProperty("useServerPrepStmts", "true");
         config.addDataSourceProperty("useLocalSessionState", "true");
         config.addDataSourceProperty("rewriteBatchedStatements", "true");
         config.addDataSourceProperty("cacheResultSetMetadata", "true");
         config.addDataSourceProperty("cacheServerConfiguration", "true");
         config.addDataSourceProperty("elideSetAutoCommits", "true");
         config.addDataSourceProperty("maintainTimeStats", "false");

         // Pool-Einstellungen
         config.setMaximumPoolSize(10);
         config.setMinimumIdle(5);
         config.setConnectionTimeout(30000);
         config.setIdleTimeout(600000);
         config.setMaxLifetime(1800000);

         try {
            dataSource = new HikariDataSource(config);
         } catch (Exception e) { // Fängt Hikari-Initialisierungsfehler ab
             throw new SQLException("MySQL connection pool initialization failed! Check driver and config.", e);
         }
    }

    @Override
    public void disconnect() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close(); // Schließt den Pool und alle Verbindungen
        }
    }

    /**
     * Holt eine Verbindung aus dem Pool.
     * @return Eine aktive SQL-Verbindung.
     * @throws SQLException Wenn keine Verbindung erhalten werden kann.
     */
     private Connection getConnection() throws SQLException {
         if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("Database source is not available or closed.");
        }
        return dataSource.getConnection();
    }

    /**
     * Erstellt die benötigten Tabellen synchron, falls sie nicht existieren (MySQL Syntax).
     */
    @Override
    public void initializeDatabase() {
        // Führe Tabellenerstellung SYNCHRON aus.
        String groupsTable = "CREATE TABLE IF NOT EXISTS `groups` ("
                + "`name` VARCHAR(36) PRIMARY KEY NOT NULL,"
                + "`prefix` VARCHAR(255)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;";

        String playerGroupsTable = "CREATE TABLE IF NOT EXISTS `player_groups` ("
                + "`uuid` VARCHAR(36) PRIMARY KEY NOT NULL,"
                + "`group_name` VARCHAR(36),"
                + "`expiry_time` BIGINT,"
                + "FOREIGN KEY (`group_name`) REFERENCES `groups`(`name`) ON DELETE SET NULL,"
                + "INDEX `idx_group_name` (`group_name`)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;";

        String signsTable = "CREATE TABLE IF NOT EXISTS `signs` ("
                + "`world` VARCHAR(255) NOT NULL,"
                + "`x` INT NOT NULL,"
                + "`y` INT NOT NULL,"
                + "`z` INT NOT NULL,"
                + "`target_uuid` VARCHAR(36) NOT NULL,"
                + "PRIMARY KEY (`world`(191), `x`, `y`, `z`)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;";

        try (Connection conn = getConnection(); Statement statement = conn.createStatement()) {
            statement.execute(groupsTable);
            statement.execute(playerGroupsTable);
            statement.execute(signsTable);
            plugin.getLogger().info("MySQL tables checked/created successfully.");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "CRITICAL: Could not create MySQL tables! Plugin might not work correctly.", e);
            Bukkit.getPluginManager().disablePlugin(plugin); // dekativieren, da sonst ohne Datenbank
        }
    }

    // --- Implementierung der Interface-Methoden (asynchron mit MySQL Syntax) ---

    @Override
    public CompletableFuture<Void> saveGroupAsync(Group group) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO `groups` (`name`, `prefix`) VALUES (?, ?) ON DUPLICATE KEY UPDATE `prefix` = VALUES(`prefix`)";
            try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, group.getName());
                pstmt.setString(2, group.getRawPrefix());
                pstmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not save group: " + group.getName(), e);
                throw new RuntimeException(e);
            }
        }, runnable -> Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    @Override
    public CompletableFuture<Void> deleteGroupAsync(String groupName) {
         return CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM `groups` WHERE `name` = ?";
            try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
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
            String sql = "SELECT `prefix` FROM `groups` WHERE `name` = ?";
            try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, groupName);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    return new Group(groupName, rs.getString("prefix"));
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not get group: " + groupName, e);
                 throw new RuntimeException(e);
            }
            return null; // Nicht gefunden
        }, runnable -> Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    @Override
    public CompletableFuture<List<Group>> getAllGroupsAsync() {
        return CompletableFuture.supplyAsync(() -> {
            List<Group> groups = new ArrayList<>();
            String sql = "SELECT `name`, `prefix` FROM `groups`";
             try (Connection conn = getConnection(); Statement stmt = conn.createStatement();
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
            String sql = "UPDATE `groups` SET `prefix` = ? WHERE `name` = ?";
             try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, prefix); // raw Prefix speichern
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
            String sql = "INSERT INTO `player_groups` (`uuid`, `group_name`, `expiry_time`) VALUES (?, ?, ?) " +
                         "ON DUPLICATE KEY UPDATE `group_name` = VALUES(`group_name`), `expiry_time` = VALUES(`expiry_time`)";
            try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, playerUUID.toString());
                pstmt.setString(2, groupName); // Gruppenname
                if (expiryTime == null) {
                    pstmt.setNull(3, Types.BIGINT); // BIGINT für MySQL Timestamps
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
            String sql = "SELECT `group_name`, `expiry_time` FROM `player_groups` WHERE `uuid` = ?";
            try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, playerUUID.toString());
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    String groupName = rs.getString("group_name");
                    if (groupName == null) {
                        plugin.getLogger().warning("Player " + playerUUID + " references a NULL group (likely deleted).");
                        return null;
                    }
                    long expiryTimestamp = rs.getLong("expiry_time");
                    Long expiry = rs.wasNull() ? null : expiryTimestamp;
                    return new PlayerGroupInfo(playerUUID, groupName, expiry);
                }
            } catch (SQLException e) {
                 plugin.getLogger().log(Level.SEVERE, "Could not get player group info for " + playerUUID, e);
                 throw new RuntimeException(e);
            }
            return null; // Nicht gefunden
        }, runnable -> Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    @Override
     public CompletableFuture<Void> removePlayerFromGroupAsync(UUID playerUUID) {
        return CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM `player_groups` WHERE `uuid` = ?";
            try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
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
             String sql = "INSERT INTO `signs` (`world`, `x`, `y`, `z`, `target_uuid`) VALUES (?, ?, ?, ?, ?) " +
                          "ON DUPLICATE KEY UPDATE `target_uuid` = VALUES(`target_uuid`)";
             try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
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
             String sql = "DELETE FROM `signs` WHERE `world` = ? AND `x` = ? AND `y` = ? AND `z` = ?";
              try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
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
             String sql = "SELECT `world`, `x`, `y`, `z`, `target_uuid` FROM `signs`";
              try (Connection conn = getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
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
             } catch (SQLException | IllegalArgumentException e) { // Fängt auch UUID-Parsing-Fehler ab
                 plugin.getLogger().log(Level.SEVERE, "Could not load sign locations", e);
                 throw new RuntimeException(e);
             }
             return signLocations;
         }, runnable -> Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable));
    }
}