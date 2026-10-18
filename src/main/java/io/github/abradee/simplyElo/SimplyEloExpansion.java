package io.github.abradee.simplyElo;

import io.github.abradee.simplyElo.Managers.EloManager;
import io.github.abradee.simplyElo.Managers.EloManager.LeaderboardEntry;
import io.github.abradee.simplyElo.Managers.EloManager.RankTier;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

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
        String lower = params.toLowerCase();

        // %simplyelo_leaderboard_<placement>% -> Player name (e.g. Notch)
        // %simplyelo_leaderboard_<placement>_name% -> Player name
        // %simplyelo_leaderboard_<placement>_elo% -> Player elo value
        if (lower.startsWith("leaderboard_")) {
            String[] parts = lower.split("_");
            if (parts.length >= 2) {
                try {
                    int placement = Integer.parseInt(parts[1]);
                    LeaderboardEntry entry = eloManager.getPlayerAtRank(placement);

                    if (entry == null) {
                        return "---";
                    }

                    if (parts.length == 3 && parts[2].equals("elo")) {
                        return String.valueOf(entry.elo());
                    }

                    return entry.name();
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }

        // The placeholders below require a target player
        if (player == null) {
            return "";
        }

        UUID uuid = player.getUniqueId();

        // %simplyelo_elo%
        if (lower.equals("elo")) {
            return String.valueOf(eloManager.getElo(uuid));
        }

        // %simplyelo_rank% -> Colored rank name (e.g. Gold)
        if (lower.equals("rank") || lower.equals("tier")) {
            RankTier tier = eloManager.getRankTier(uuid);
            return ChatColor.translateAlternateColorCodes('&', tier.getFormattedName());
        }

        // %simplyelo_rank_emoji% or %simplyelo_rank_icon% -> Colored icon/emoji (e.g. ★)
        if (lower.equals("rank_emoji") || lower.equals("rank_icon")) {
            RankTier tier = eloManager.getRankTier(uuid);
            return ChatColor.translateAlternateColorCodes('&', tier.getFormattedIcon());
        }

        // %simplyelo_rank_tag% -> Combined tag (e.g. [★ Gold])
        if (lower.equals("rank_tag")) {
            RankTier tier = eloManager.getRankTier(uuid);
            String formatted = tier.getColor() + "[" + tier.getIcon() + " " + tier.getDisplayName() + "]";
            return ChatColor.translateAlternateColorCodes('&', formatted);
        }

        // %simplyelo_position% or %simplyelo_top% -> Current standing (#1, #2, etc.)
        if (lower.equals("position") || lower.equals("top")) {
            try {
                int position = eloManager.getLeaderboardPosition(uuid).get();
                return position > 0 ? String.valueOf(position) : "Unranked";
            } catch (Exception e) {
                return "N/A";
            }
        }

        return null;
    }
}