package io.github.abradee.simplyElo.Commands;

import io.github.abradee.simplyElo.Managers.EloManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class ReloadCommand implements CommandExecutor {

    private final EloManager eloManager;

    public ReloadCommand(EloManager eloManager) {
        this.eloManager = eloManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("simplyelo.admin.reload")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }

        // Reload the config and dynamically update ranks/settings in EloManager
        eloManager.loadConfig();

        sender.sendMessage(ChatColor.GREEN + "SimplyElo configuration and ranks have been reloaded!");
        return true;
    }
}