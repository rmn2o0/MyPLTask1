package eu.rawora.playLegendTask.commands;

import eu.rawora.playLegendTask.PlayLegendTask;
import eu.rawora.playLegendTask.managers.ConfigManager;
import eu.rawora.playLegendTask.managers.GroupManager;
import eu.rawora.playLegendTask.model.Group;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public class GroupCommand implements CommandExecutor, TabCompleter {

    private final PlayLegendTask plugin;
    private final GroupManager groupManager;
    private final ConfigManager configManager;

    // Liste der Sub-Befehle für Tab-Completion
    private static final List<String> SUB_COMMANDS = List.of("create", "delete", "list", "setprefix");

    public GroupCommand(PlayLegendTask plugin) {
        this.plugin = plugin;
        this.groupManager = plugin.getGroupManager();
        this.configManager = plugin.getConfigManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        // 1. Permission Check
        if (!sender.hasPermission("playlegendtask.admin.group")) {
            sender.sendMessage(configManager.getPrefixedMessage("error.no-permission"));
            return true; // Befehl Handling fertig
        }

        // 2. Arg Check
        if (args.length == 0) {
            sendUsage(sender, label);
            return true; // Befehl Handling fertig
        }

        // 3. Arg Handling
        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "create":
                handleCreate(sender, args);
                break;
            case "delete":
                handleDelete(sender, args);
                break;
            case "list":
                handleList(sender);
                break;
            case "setprefix":
                handleSetPrefix(sender, args);
                break;
            default:
                sendUsage(sender, label);
                break;
        }

        return true; // Befehl Handling fertig
    }

    /** Zeigt die korrekte Nutzung des Befehls an. */
    private void sendUsage(CommandSender sender, String label) {
         sender.sendMessage(configManager.getFormattedPrefixedMessage("group.usage"));
         sender.sendMessage(ChatColor.YELLOW + "Available actions: " + String.join(", ", SUB_COMMANDS));
    }

    /** Behandelt den /group create <name> <prefix...> Befehl */
    private void handleCreate(CommandSender sender, String[] args) {
        // wir erwarten: /group create <name> <prefix mit &>
        if (args.length < 3) {
             sender.sendMessage(ChatColor.RED + "Usage: /group create <name> <prefix>");
            return;
        }
        String groupName = args[1];
        String prefixInput = String.join(" ", Arrays.copyOfRange(args, 2, args.length));

        groupManager.createGroup(groupName, prefixInput).whenCompleteAsync((success, throwable) -> {
            // Dieser Code wird ausgeführt, wenn die DB-Operation + Cache-Update fertig sind (im Hauptthread)
            if (throwable != null) {
                 // Allgemeiner Fehler (DB etc.)
                 sender.sendMessage(ChatColor.RED + "An error occurred while creating the group. Check console.");
                 plugin.getLogger().severe("Error creating group '" + groupName + "': " + throwable.getMessage());
                 throwable.printStackTrace(); // Stacktrace für Debugging
                return;
            }
            if (success) {
                 // Erfolg -> Nachricht aus messages.yml senden
                 sender.sendMessage(configManager.getFormattedPrefixedMessage("group.created",
                        "%group%", groupName,
                        // Zeige den Prefix farbig an in der Bestätigung
                        "%prefix%", ChatColor.translateAlternateColorCodes('&', prefixInput)));
            } else {
                 // Gruppe existiert bereits -> Nachricht aus messages.yml
                 sender.sendMessage(configManager.getFormattedPrefixedMessage("group.already-exists", "%group%", groupName));
            }
        }, runnable -> Bukkit.getScheduler().runTask(plugin, runnable)); // Stelle sicher, dass Feedback im Hauptthread gesendet wird
    }

    /** Behandelt den /group delete <name> Befehl */
    private void handleDelete(CommandSender sender, String[] args) {
        // wir erwarten: /group delete <name>
        if (args.length != 2) {
             sender.sendMessage(ChatColor.RED + "Usage: /group delete <name>");
            return;
        }
        String groupName = args[1];

        // Verhindere Löschen der Default-Gruppe
        if (groupName.equalsIgnoreCase(configManager.getDefaultGroupName())) {
             sender.sendMessage(configManager.getFormattedPrefixedMessage("group.cannot-delete-default", "%group%", groupName));
             return;
        }

        groupManager.deleteGroup(groupName).whenCompleteAsync((success, throwable) -> {
            // Callback im Hauptthread
            if (throwable != null) {
                 sender.sendMessage(ChatColor.RED + "An error occurred while deleting the group. Check console.");
                 plugin.getLogger().severe("Error deleting group '" + groupName + "': " + throwable.getMessage());
                 throwable.printStackTrace();
                 return;
             }
            if (success) {
                sender.sendMessage(configManager.getFormattedPrefixedMessage("group.deleted", "%group%", groupName));
            } else {
                // Gruppe nicht gefunden (oder Default - wurde oben abgefangen)
                sender.sendMessage(configManager.getFormattedPrefixedMessage("error.group-not-found", "%group%", groupName));
            }
        }, runnable -> Bukkit.getScheduler().runTask(plugin, runnable));
    }

    /** Behandelt den /group list Befehl */
    private void handleList(CommandSender sender) {
        // Hole alle Gruppen aus dem Cache des GroupManagers
        Collection<Group> groups = groupManager.getAllGroups();

        // Sende Kopfzeile aus messages.yml
        sender.sendMessage(configManager.getMessage("group.list-header"));

        if (groups.isEmpty()) {
            // Keine Gruppen gefunden (sollte mind. Default enthalten)
            sender.sendMessage(configManager.getMessage("group.list-empty"));
        } else {
            // Sortiere Gruppen alphabetisch nach Namen (case-insensitive) und gib sie aus
            groups.stream()
                .sorted(Comparator.comparing(Group::getName, String.CASE_INSENSITIVE_ORDER))
                .forEach(group -> sender.sendMessage(
                    configManager.getFormattedMessage("group.list-entry",
                            "%group%", group.getName(),
                            // Hole farbigen Prefix aus Group-Objekt
                            "%prefix%", group.getPrefix()) // getPrefix() gibt bereits farbigen String zurück
            ));
        }
    }

    /** Behandelt den /group setprefix <name> <prefix...> Befehl */
    private void handleSetPrefix(CommandSender sender, String[] args) {
        // Wir erwarten: /group setprefix <name> <neuer prefix mit &>
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /group setprefix <name> <new prefix>");
            return;
        }
        String groupName = args[1];
        String prefixInput = String.join(" ", Arrays.copyOfRange(args, 2, args.length));

        groupManager.setGroupPrefix(groupName, prefixInput).whenCompleteAsync((success, throwable) -> {
            // Callback im Hauptthread
            if (throwable != null) {
                 sender.sendMessage(ChatColor.RED + "An error occurred while setting the prefix. Check console.");
                 plugin.getLogger().severe("Error setting prefix for group '" + groupName + "': " + throwable.getMessage());
                 throwable.printStackTrace();
                 return;
             }
            if (success) {
                // Sende Bestätigung mit farbigem Prefix
                sender.sendMessage(configManager.getFormattedPrefixedMessage("group.setprefix",
                        "%group%", groupName, // TODO: Hole korrekten Namen (falls case geändert wurde) aus GroupManager?... noch checken
                        "%prefix%", ChatColor.translateAlternateColorCodes('&', prefixInput)));
            } else {
                // Gruppe nicht gefunden
                sender.sendMessage(configManager.getFormattedPrefixedMessage("error.group-not-found", "%group%", groupName));
            }
        }, runnable -> Bukkit.getScheduler().runTask(plugin, runnable));
    }

    /** Implementierung für Tab-Completion */
    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("playlegendtask.admin.group")) {
            return Collections.emptyList(); // Keine Vorschläge ohne Permission
        }

        if (args.length == 1) {
            // Filtere SUB_COMMANDS basierend auf dem, was der Spieler schon getippt hat
            return SUB_COMMANDS.stream()
                    .filter(sub -> sub.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            // Für 'delete' und 'setprefix' brauchen wir Gruppennamen
            if (subCommand.equals("delete") || subCommand.equals("setprefix")) {
                // Hole alle Gruppennamen aus dem Cache
                return groupManager.getAllGroups().stream()
                        .map(Group::getName)
                         // Filtere basierend auf der Eingabe (ignoriere Groß/Klein)
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
            // Für 'create' könnten wir hier nichts sinnvolles vorschlagen (Außer wir arbeiten irgendwann mal mit LLMs aber das wäre eine Ticken zu viel lol :)
            // Für 'list' gibt es keine weiteren Argumente
        }

        if (args.length >= 3 && args[0].equalsIgnoreCase("setprefix")) {
            // Hier könnte man ggf Farb-Codes (&0-&9, &a-&f, &k-&o, &r) vorschlagen ?
            // wenn das letzte Argument mit '&' beginnt.
            if (args[args.length - 1].matches(".*&$")) { // Prüft, ob das letzte Zeichen ein '&' ist
                 return Arrays.asList("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "a", "b", "c", "d", "e", "f", "k", "l", "m", "n", "o", "r").stream()
                     .map(code -> "&" + code) // Füge '&' hinzu
                     .collect(Collectors.toList());
            }
             // Oder man schlägt einfach nichts mehr vor... und spart sich 10 Minuten seiner Zeit am Wochenende solche Spielerein einzubauen
        }

        return Collections.emptyList();
    }
}