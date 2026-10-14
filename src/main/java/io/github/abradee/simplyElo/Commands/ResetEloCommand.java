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
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ResetEloCommand implements CommandExecutor, TabCompleter {

    private static final int DEFAULT_ELO = 100;
    private final JavaPlugin plugin;
    private final EloManager eloManager;

    public ResetEloCommand(JavaPlugin plugin, EloManager eloManager) {
        this.plugin = plugin;
        this.eloManager = eloManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("simplyelo.admin.reset")) {
            sender.sendMessage(Component.text("You do not have permission to run this command.", NamedTextColor.RED));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(Component.text("Usage: /resetelo <player>", NamedTextColor.RED));
            return true;
        }

        String targetName = args[0];
        Player onlineTarget = Bukkit.getPlayerExact(targetName);

        if (onlineTarget != null) {
            eloManager.setElo(onlineTarget.getUniqueId(), DEFAULT_ELO);

            sender.sendMessage(Component.text("Successfully reset ", NamedTextColor.GREEN)
                    .append(Component.text(onlineTarget.getName(), NamedTextColor.GOLD))
                    .append(Component.text("'s Elo to " + DEFAULT_ELO + ".", NamedTextColor.GREEN)));

            onlineTarget.sendMessage(Component.text("Your Elo has been reset to " + DEFAULT_ELO + " by an administrator.", NamedTextColor.YELLOW));
            return true;
        }

        sender.sendMessage(Component.text("Looking up " + targetName + " in database...", NamedTextColor.DARK_GRAY));

        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            @SuppressWarnings("deprecation")
            OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(targetName);

            if (!offlineTarget.hasPlayedBefore() && !offlineTarget.isOnline()) {
                sender.sendMessage(Component.text("Player '" + targetName + "' has never joined this server.", NamedTextColor.RED));
                return;
            }

            eloManager.saveEloSync(offlineTarget.getUniqueId(), DEFAULT_ELO);

            sender.sendMessage(Component.text("Successfully reset offline player ", NamedTextColor.GREEN)
                    .append(Component.text(offlineTarget.getName() != null ? offlineTarget.getName() : targetName, NamedTextColor.GOLD))
                    .append(Component.text("'s Elo to " + DEFAULT_ELO + ".", NamedTextColor.GREEN)));
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