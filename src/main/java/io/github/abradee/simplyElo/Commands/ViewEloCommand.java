package io.github.abradee.simplyElo.Commands;

import io.github.abradee.simplyElo.Managers.EloManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ViewEloCommand implements CommandExecutor, TabCompleter {

    private final EloManager eloManager;

    public ViewEloCommand(EloManager eloManager) {
        this.eloManager = eloManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Console must specify a player: /viewelo <player>", NamedTextColor.RED));
                return true;
            }

            int elo = eloManager.getElo(player.getUniqueId());
            player.sendMessage(Component.text("Your Elo rating: ", NamedTextColor.GRAY)
                    .append(Component.text(elo, NamedTextColor.GOLD)));
            return true;
        }

        String targetName = args[0];
        Player onlineTarget = Bukkit.getPlayerExact(targetName);

        if (onlineTarget != null) {
            int elo = eloManager.getElo(onlineTarget.getUniqueId());
            sender.sendMessage(Component.text(onlineTarget.getName() + "'s Elo: ", NamedTextColor.GRAY)
                    .append(Component.text(elo, NamedTextColor.GOLD)));
            return true;
        }

        sender.sendMessage(Component.text("Looking up " + targetName + " in database...", NamedTextColor.DARK_GRAY));

        Bukkit.getAsyncScheduler().runNow(eloManager.getPlugin(), task -> {
            @SuppressWarnings("deprecation")
            OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(targetName);

            if (!offlineTarget.hasPlayedBefore() && !offlineTarget.isOnline()) {
                sender.sendMessage(Component.text("Player '" + targetName + "' has never joined this server.", NamedTextColor.RED));
                return;
            }

            eloManager.loadPlayer(offlineTarget.getUniqueId()).thenAccept(elo -> {
                sender.sendMessage(Component.text(offlineTarget.getName() + "'s Elo: ", NamedTextColor.GRAY)
                        .append(Component.text(elo, NamedTextColor.GOLD)));

                if (!offlineTarget.isOnline()) {
                    eloManager.unloadPlayer(offlineTarget.getUniqueId());
                }
            });
        });

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}