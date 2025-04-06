package eu.rawora.playLegendTask.commands;

import eu.rawora.playLegendTask.PlayLegendTask;
import eu.rawora.playLegendTask.managers.ConfigManager;
import eu.rawora.playLegendTask.managers.GroupManager;
import eu.rawora.playLegendTask.managers.PlayerDataManager;
import eu.rawora.playLegendTask.model.Group;
import eu.rawora.playLegendTask.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public class SetGroupCommand implements CommandExecutor, TabCompleter {

    private final PlayLegendTask plugin;
    private final PlayerDataManager playerDataManager;
    private final GroupManager groupManager;
    private final ConfigManager configManager;

    public SetGroupCommand(PlayLegendTask plugin) {
        this.plugin = plugin;
        this.playerDataManager = plugin.getPlayerDataManager();
        this.groupManager = plugin.getGroupManager();
        this.configManager = plugin.getConfigManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("playlegendtask.admin.setgroup")) {
            sender.sendMessage(configManager.getPrefixedMessage("error.no-permission"));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(configManager.getFormattedPrefixedMessage("error.invalid-usage",
                    "%usage%", "/" + label + " <player> <group> [duration]"));
            return true;
        }

        String playerName = args[0];
        String groupName = args[1];
        String durationString = null;
        if (args.length >= 3) {
            durationString = String.join("", Arrays.copyOfRange(args, 2, args.length));
        }

        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(playerName);
        UUID targetUUID;
        String targetPlayerName = targetPlayer.getName();

        try {
            targetUUID = targetPlayer.getUniqueId();
            // Prüfe, ob Spielername überhaupt existiert / UUID gefunden wurde
            if (targetUUID == null || targetPlayerName == null /*|| !targetPlayer.hasPlayedBefore()*/) { // hasPlayedBefore optional prüfen TODO: checken was performanter ist
                sender.sendMessage(configManager.getFormattedPrefixedMessage("error.player-not-found", "%player%", playerName));
                return true;
            }
        } catch (Exception e) {
            sender.sendMessage(configManager.getFormattedPrefixedMessage("error.player-not-found", "%player%", playerName));
            return true;
        }

        Group targetGroup = groupManager.getGroup(groupName);
        if (targetGroup == null) {
            sender.sendMessage(configManager.getFormattedPrefixedMessage("error.group-not-found", "%group%", groupName));
            return true;
        }

        Long durationMillis = null;
        if (durationString != null && !durationString.trim().isEmpty() && !durationString.equalsIgnoreCase("permanent")) { // "permanent" explizit behandeln
            durationMillis = TimeUtil.parseDuration(durationString);
            if (durationMillis == null) {
                sender.sendMessage(configManager.getPrefixedMessage("error.invalid-duration"));
                return true;
            }
        }

        final Long finalDurationMillis = durationMillis; // Für Lambda


        playerDataManager.setPlayerGroup(targetUUID, targetGroup.getName(), finalDurationMillis) // finalDurationMillis von oben
                .whenCompleteAsync((success, throwable) -> { // success ist das Boolean-Ergebnis, throwable die Exception
                    // Callback wird im Hauptthread ausgeführt (wegen Executor am Ende)

                    if (throwable != null) {
                        // Ein echter Fehler ist während der asynchronen Ausführung aufgetreten (DB-Problem etc.)
                        sender.sendMessage(configManager.getFormattedPrefixedMessage("error.generic", "%details%", "Could not set group. Check console."));
                        plugin.getLogger().severe("Error setting group for " + targetPlayerName + ": " + throwable.getMessage());
                        // throwable.printStackTrace(); // TODO optional für mehr Debug-Infos in Konsole
                        return;
                    }

                    // Kein direkter Fehler aufgetreten, prüfe das Ergebnis (success)
                    // wir nutzen hier mal Boolean.TRUE.equals() für einen blöd gesagt "sicheren" Vergleich,
                    // da 'success' theoretisch auch null sein könnte (sollte aber nicht mehr).
                    if (Boolean.TRUE.equals(success)) {
                        // Alles lief gut
                        if (finalDurationMillis == null) { // Permanent
                            sender.sendMessage(configManager.getFormattedPrefixedMessage("setgroup.success-permanent",
                                    "%player%", targetPlayerName, // Korrekten Namen verwenden
                                    "%group%", targetGroup.getName()));
                        } else { // Temporär
                            String formattedTime = TimeUtil.formatDuration(finalDurationMillis);
                            sender.sendMessage(configManager.getFormattedPrefixedMessage("setgroup.success-temporary",
                                    "%player%", targetPlayerName, // Korrekten Namen verwenden
                                    "%group%", targetGroup.getName(),
                                    "%time%", formattedTime));
                        }
                    } else {
                        // Die Operation ist ohne Exception fehlgeschlagen (success war false oder null)
                        // Dies sollte nicht  passieren,  außer die Gruppe wurde z.B. genau zwischen Validierung und Ausführung gelöscht...
                        // TODO: hier ggf. noch weiteren Ausnahme-Case handlen
                        sender.sendMessage(configManager.getFormattedPrefixedMessage("error.generic", "%details%", "Setting group failed unexpectedly. Group might not exist?"));
                    }

                }, runnable -> Bukkit.getScheduler().runTask(plugin, runnable));

        return true;
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("playlegendtask.admin.setgroup")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            String currentInput = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(currentInput))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            String currentInput = args[1].toLowerCase();
            return groupManager.getAllGroups().stream()
                    .map(Group::getName)
                    .filter(name -> name.toLowerCase().startsWith(currentInput))
                    .collect(Collectors.toList());
        }

        if (args.length == 3) {
            List<String> suggestions = new ArrayList<>(List.of("1h", "1d", "7d", "30m", "permanent"));
            String currentInput = args[2].toLowerCase();
            return suggestions.stream()
                    .filter(s -> s.toLowerCase().startsWith(currentInput))
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}