package eu.rawora.playLegendTask.managers;

import eu.rawora.playLegendTask.PlayLegendTask;
import eu.rawora.playLegendTask.model.Group;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionAttachmentInfo;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class PermissionManager {
    //Bonus Beispiel
    private final PlayLegendTask plugin;
    // Speichert die PermissionAttachments für jeden online Spieler
    // Key: Spieler-UUID, Value: Bukkit PermissionAttachment
    private final Map<UUID, PermissionAttachment> attachments = new HashMap<>();

    public PermissionManager(PlayLegendTask plugin) {
        this.plugin = plugin;
        //TODO:  Hier könnte man z.B. Gruppen-Permissions aus einer Datei laden,
        // oder der GroupManager könnte sie aus der DB laden und bereitstellen.
    }

    /**
     * Aktualisiert die Permissions eines Spielers basierend auf seiner Gruppe.
     * Entfernt alte Permissions und fügt die der neuen Gruppe hinzu.
     * Muss im Bukkit-Hauptthread ausgeführt werden.
     *
     * @param player Der Spieler.
     * @param group  Die Gruppe, deren Permissions angewendet werden sollen.
     */
    public void updatePlayerPermissions(Player player, Group group) {
        if (player == null || group == null) {
            plugin.getLogger().warning("Cannot update permissions: Player or Group is null.");
            return;
        }

        UUID uuid = player.getUniqueId();

        removeAttachment(player);

        PermissionAttachment attachment = player.addAttachment(plugin);
        attachments.put(uuid, attachment);
        plugin.getLogger().info("Created new permission attachment for " + player.getName());

        // 3. Berechtigungen der Gruppe setzen
        // TODO: logik implementieren, um permissions für die Gruppe zu setzen / holen
        // Beispiel-Szenarien:
        // a) Permissions sind Teil des Group-Objekts
        // b) Permissions werden aus einer separaten Konfigurationsdatei geladen
        // c) Permissions werden direkt aus der Datenbank für die Gruppe abgefragt?

        // --- Platzhalter / Beispiel-Logik ---

        // Beispiel a): Wenn Group.java eine getPermissions() Methode hätte:
        /*
        Set<String> permissions = group.getPermissions();
        if (permissions != null && !permissions.isEmpty()) {
            plugin.getLogger().info("Applying " + permissions.size() + " permissions for group '" + group.getName() + "' to " + player.getName() + "...");
            boolean hasWildcard = permissions.contains("*"); // Prüfe auf '*'

            for (String permission : permissions) {
                if (permission.equals("*")) continue; // Wildcard wird von Bukkit gehandhabt

                boolean value = true;
                // Negative Permissions (z.B. "-bukkit.command.plugins")
                if (permission.startsWith("-")) {
                    permission = permission.substring(1);
                    value = false;
                }
                attachment.setPermission(permission.toLowerCase(), value);
                // plugin.getLogger().fine("Set permission: " + permission + " = " + value); // Logging
            }
        } else {
             plugin.getLogger().info("No specific permissions found for group '" + group.getName() + "'. Applying defaults/none.");
        }


         usw...........
        */

        // Oder Beispiel Einfaches Hardcoding basierend auf Gruppennamen
        // Nur zur Demonstration hier
        if (group.getName().equalsIgnoreCase("Admin")) {
            attachment.setPermission("playlegendtask.admin.*", true);
            attachment.setPermission("bukkit.command.gamemode", true);
            plugin.getLogger().info("Applied placeholder admin permissions to " + player.getName());
        } else if (group.getName().equalsIgnoreCase("Moderator")) {
            attachment.setPermission("playlegendtask.admin.setgroup", true); // Beispiel: Mod darf /setgroup
            attachment.setPermission("bukkit.command.kick", true);
            plugin.getLogger().info("Applied placeholder moderator permissions to " + player.getName());
        } else {
            // Default oder andere Gruppen bekommen vielleicht nur User-Rechte
            // attachment.setPermission("playlegendtask.user.groupinfo", true); // Falls /groupinfo eine braucht
            plugin.getLogger().info("Applied placeholder default/user permissions to " + player.getName() + " (Group: " + group.getName() + ")");
        }
        // TODO dies könnte man wie beispiel b) auch einfach per Config umsetzen


        player.recalculatePermissions();
        plugin.getLogger().info("Recalculated permissions for " + player.getName());

        // TODO: Optional: Effektive Permissions loggen zum Debuggen um Fehler zu vermeiden
        // logEffectivePermissions(player);
    }

    /**
     * Entfernt das PermissionAttachment eines Spielers (z.B. bei Quit oder Gruppenwechsel).
     * Muss im Bukkit-Hauptthread ausgeführt werden.
     *
     * @param player Der Spieler.
     */
    public void removeAttachment(Player player) {
        UUID uuid = player.getUniqueId();
        if (attachments.containsKey(uuid)) {
            try {
                player.removeAttachment(attachments.get(uuid));
                plugin.getLogger().info("Removed permission attachment from " + player.getName());
            } catch (IllegalArgumentException e) {
                // Kann passieren, wenn Attachment aus irgendeinem Grund schon entfernt wurde (z.B. durch /reload?)
                // Ist normalerweise kein kritischer Fehler.
                plugin.getLogger().warning("Could not remove permission attachment for " + player.getName() + " (already removed?): " + e.getMessage());
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Unexpected error removing permission attachment for " + player.getName(), e);
            } finally {
                attachments.remove(uuid);
            }
        }
    }

    /**
     * Hilfsmethode zum Loggen der aktuell aktiven Permissions eines Spielers (nützlich für Debugging).
     *
     * @param player Der Spieler.
     */
    private void logEffectivePermissions(Player player) {
        plugin.getLogger().info("--- Effective Permissions for " + player.getName() + " ---");
        for (PermissionAttachmentInfo pai : player.getEffectivePermissions()) {
            plugin.getLogger().info(String.format("- %s = %b (Source: %s)",
                    pai.getPermission(),
                    pai.getValue(),
                    pai.getAttachment() != null ? "Plugin Attachment" : "Default/Other"));
        }
        plugin.getLogger().info("--- End Effective Permissions ---");
    }
}