package eu.rawora.playLegendTask.commands;

import eu.rawora.playLegendTask.PlayLegendTask;
import eu.rawora.playLegendTask.managers.ConfigManager;
import eu.rawora.playLegendTask.managers.PlayerDataManager;
import eu.rawora.playLegendTask.model.Group;
import eu.rawora.playLegendTask.model.PlayerGroupInfo;
import eu.rawora.playLegendTask.util.TimeUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class GroupInfoCommand implements CommandExecutor {

    private final PlayLegendTask plugin;
    private final PlayerDataManager playerDataManager;
    private final ConfigManager configManager;

    public GroupInfoCommand(PlayLegendTask plugin) {
        this.plugin = plugin;
        this.playerDataManager = plugin.getPlayerDataManager();
        this.configManager = plugin.getConfigManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(configManager.getPrefixedMessage("error.console-cannot-use"));
            return true; // Befehl für Konsole nicht sinnvoll in dieser Form
        }

        Player player = (Player) sender;

        // TODO Optional: Permission Check (falls gewünscht)
        /*
        if (!player.hasPermission("playlegendtask.user.groupinfo")) {
            player.sendMessage(configManager.getPrefixedMessage("error.no-permission"));
            return true;
        }
        */

        PlayerGroupInfo info = playerDataManager.getPlayerGroupInfo(player.getUniqueId());
        Group group = playerDataManager.getPlayerGroup(player.getUniqueId()); // Holt auch Default-Gruppe als Fallback

        // Prüfen, ob Daten vorhanden sind (sollte immer der Fall sein, wenn Spieler online ist)
        if (info == null || group == null) {
            // sollte durch den PlayerDataManager (Fallback auf Default) verhindert werden
            plugin.getLogger().severe("Error: Could not retrieve group info for online player " + player.getName());
            player.sendMessage(configManager.getFormattedPrefixedMessage("error.generic", "%details%", "Could not retrieve your group info. Please contact an admin."));
            return true;
        }

        player.sendMessage(configManager.getMessage("groupinfo.header"));
        player.sendMessage(configManager.getFormattedMessage("groupinfo.group", "%group%", group.getName()));
        player.sendMessage(configManager.getFormattedMessage("groupinfo.prefix", "%prefix%", group.getPrefix())); // getPrefix() liefert farbigen String

        // Prüfe, ob Gruppe permanent oder temporär ist
        if (info.isPermanent()) {
            player.sendMessage(configManager.getMessage("groupinfo.expiry-permanent"));
        } else {
            // Berechne verbleibende Zeit
            long remainingMillis = info.getExpiryTime() - System.currentTimeMillis();
            if (remainingMillis <= 0) {
                // Sollte durch Expiry-Check im PlayerDataManager behandelt werden, aber als Fallback
                player.sendMessage(configManager.getFormattedMessage("groupinfo.expiry-temporary", "%time%", "Expired"));
            } else {
                String formattedTime = TimeUtil.formatDuration(remainingMillis);
                player.sendMessage(configManager.getFormattedMessage("groupinfo.expiry-temporary", "%time%", formattedTime));
            }
        }
        player.sendMessage(configManager.getMessage("groupinfo.footer"));

        return true; // Befehl erfolgreich behandelt
    }
}