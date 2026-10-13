package io.github.abradee.simplyElo;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import io.github.abradee.simplyElo.Managers.EloManager;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SimplyEloExpansion extends PlaceholderExpansion {

    private final SimplyElo plugin;
    private final EloManager eloManager;

    public SimplyEloExpansion(SimplyElo plugin, EloManager eloManager) {
        this.plugin = plugin;
        this.eloManager = eloManager;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "simplyelo";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return "";
        }

        if (params.equalsIgnoreCase("elo")) {
            return String.valueOf(eloManager.getElo(player.getUniqueId()));
        }

        return params;
    }
}