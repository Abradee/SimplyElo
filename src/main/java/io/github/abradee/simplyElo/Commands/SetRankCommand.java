package io.github.abradee.simplyElo.Commands;

import io.github.abradee.simplyElo.Managers.EloManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class SetRankCommand implements CommandExecutor, TabCompleter {

    private final EloManager eloManager;

    public SetRankCommand(EloManager eloManager) {
        this.eloManager = eloManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("simplyelo.admin.setrank")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }

        if (args.length != 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /setrank <player> <rank>");
            return true;
        }

        String targetName = args[0];
        String rankInput = args[1].toUpperCase();

        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);

        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sender.sendMessage(ChatColor.RED + "Player not found.");
            return true;
        }

        EloManager.RankTier targetTier;
        try {
            targetTier = EloManager.RankTier.valueOf(rankInput);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(ChatColor.RED + "Invalid rank. Valid ranks are: " +
                    Arrays.stream(EloManager.RankTier.values())
                            .map(Enum::name)
                            .collect(Collectors.joining(", ")));
            return true;
        }

        int baselineElo = targetTier.getMinElo();
        eloManager.setElo(target.getUniqueId(), baselineElo);

        String coloredRankName = ChatColor.translateAlternateColorCodes('&', targetTier.getFormattedName());
        sender.sendMessage(ChatColor.GREEN + "Successfully set " + target.getName() + "'s rank to " + coloredRankName + ChatColor.GREEN + " (" + baselineElo + " Elo).");

        if (target.isOnline()) {
            Player onlineTarget = target.getPlayer();
            if (onlineTarget != null) {
                onlineTarget.sendMessage(ChatColor.GREEN + "Your rank has been set to " + coloredRankName + ChatColor.GREEN + " by an administrator.");
            }
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String partialName = args[0].toLowerCase();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(partialName)) {
                    completions.add(player.getName());
                }
            }
        } else if (args.length == 2) {
            String partialRank = args[1].toUpperCase();
            for (EloManager.RankTier tier : EloManager.RankTier.values()) {
                if (tier.name().startsWith(partialRank)) {
                    completions.add(tier.name());
                }
            }
        }

        return completions;
    }
}