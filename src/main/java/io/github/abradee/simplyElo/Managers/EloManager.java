package io.github.abradee.simplyElo.Managers;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class EloManager {

    private int defaultElo = 1000;
    private int kFactor = 32;

    private final JavaPlugin plugin;
    private final String connectionUrl;
    private final Map<UUID, Integer> eloCache = new ConcurrentHashMap<>();

    public record LeaderboardEntry(UUID uuid, String name, int elo) {}
    private final List<LeaderboardEntry> topLeaderboardCache = new CopyOnWriteArrayList<>();

    private final List<RankTier> rankTiers = new ArrayList<>();

    public static class RankTier {
        private final String id;
        private final String displayName;
        private final String color;
        private final String icon;
        private final int minElo;

        public RankTier(String id, String displayName, String color, String icon, int minElo) {
            this.id = id;
            this.displayName = displayName;
            this.color = color;
            this.icon = icon;
            this.minElo = minElo;
        }

        public String getId() { return id; }
        public String getDisplayName() { return displayName; }
        public String getColor() { return color; }
        public String getIcon() { return icon; }
        public int getMinElo() { return minElo; }
        public String getFormattedName() { return color + displayName; }
        public String getFormattedIcon() { return color + icon; }
    }

    public EloManager(JavaPlugin plugin) {
        this.plugin = plugin;

        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        loadConfig();

        File dbFile = new File(plugin.getDataFolder(), "elo_data.db");
        this.connectionUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();

        initDatabase();

        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::refreshTopCache, 20L, 1200L);
    }

    public List<RankTier> getRankTiers() {
        return this.rankTiers;
    }

    public RankTier getRankTierById(String id) {
        for (RankTier tier : rankTiers) {
            if (tier.getId().equalsIgnoreCase(id)) {
                return tier;
            }
        }
        return null;
    }

    public void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        this.defaultElo = config.getInt("settings.default-elo", 1000);
        this.kFactor = config.getInt("settings.k-factor", 32);

        rankTiers.clear();
        ConfigurationSection ranksSection = config.getConfigurationSection("ranks");
        if (ranksSection != null) {
            for (String key : ranksSection.getKeys(false)) {
                String displayName = ranksSection.getString(key + ".display-name", key);
                String color = ranksSection.getString(key + ".color", "&7");
                String icon = ranksSection.getString(key + ".icon", "");
                int minElo = ranksSection.getInt(key + ".min-elo", 0);

                rankTiers.add(new RankTier(key, displayName, color, icon, minElo));
            }
        } else {
            rankTiers.add(new RankTier("UNRANKED", "Unranked", "&7", "✧", 0));
        }

        rankTiers.sort((a, b) -> Integer.compare(b.getMinElo(), a.getMinElo()));
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(connectionUrl);
    }

    private void initDatabase() {
        String createTableSql = """
            CREATE TABLE IF NOT EXISTS player_elo (
                uuid TEXT PRIMARY KEY,
                elo INTEGER NOT NULL
            );
        """;
        String createIndexSql = "CREATE INDEX IF NOT EXISTS idx_player_elo_score ON player_elo(elo DESC);";

        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(createTableSql);
            stmt.execute(createIndexSql);
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to initialize SQLite database: " + e.getMessage());
        }
    }

    public void refreshTopCache() {
        String sql = "SELECT uuid, elo FROM player_elo ORDER BY elo DESC LIMIT 50;";
        List<LeaderboardEntry> updatedList = new ArrayList<>();

        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                int elo = rs.getInt("elo");

                OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
                String name = op.getName() != null ? op.getName() : "Unknown";

                updatedList.add(new LeaderboardEntry(uuid, name, elo));
            }

            topLeaderboardCache.clear();
            topLeaderboardCache.addAll(updatedList);
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to update top leaderboard cache: " + e.getMessage());
        }
    }

    public LeaderboardEntry getPlayerAtRank(int placement) {
        int index = placement - 1;
        if (index >= 0 && index < topLeaderboardCache.size()) {
            return topLeaderboardCache.get(index);
        }
        return null;
    }

    public int getElo(UUID uuid) {
        return eloCache.getOrDefault(uuid, defaultElo);
    }

    public RankTier getRankTier(UUID uuid) {
        int elo = getElo(uuid);
        for (RankTier tier : rankTiers) {
            if (elo >= tier.getMinElo()) {
                return tier;
            }
        }
        return rankTiers.get(rankTiers.size() - 1);
    }

    public CompletableFuture<Integer> getLeaderboardPosition(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            int currentElo = getElo(uuid);
            String sql = "SELECT COUNT(*) + 1 AS position FROM player_elo WHERE elo > ?;";

            try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, currentElo);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    return rs.getInt("position");
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Could not calculate leaderboard position for " + uuid + ": " + e.getMessage());
            }

            return -1;
        });
    }

    public void setElo(UUID uuid, int elo) {
        int clamped = Math.max(0, elo);
        eloCache.put(uuid, clamped);
        saveEloAsync(uuid, clamped);
    }

    public void processMatch(UUID winnerUuid, UUID loserUuid) {
        int winnerElo = getElo(winnerUuid);
        int loserElo = getElo(loserUuid);

        double expectedWinner = 1.0 / (1.0 + Math.pow(10.0, (loserElo - winnerElo) / 400.0));
        double expectedLoser = 1.0 / (1.0 + Math.pow(10.0, (winnerElo - loserElo) / 400.0));

        int newWinnerElo = Math.max(0, (int) Math.round(winnerElo + kFactor * (1.0 - expectedWinner)));
        int newLoserElo = Math.max(0, (int) Math.round(loserElo + kFactor * (0.0 - expectedLoser)));

        setElo(winnerUuid, newWinnerElo);
        setElo(loserUuid, newLoserElo);
    }

    public CompletableFuture<Integer> loadPlayer(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT elo FROM player_elo WHERE uuid = ?;";

            try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    int elo = rs.getInt("elo");
                    eloCache.put(uuid, elo);
                    return elo;
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Could not load Elo for " + uuid + ": " + e.getMessage());
            }

            eloCache.put(uuid, defaultElo);
            return defaultElo;
        });
    }

    public void unloadPlayer(UUID uuid) {
        eloCache.remove(uuid);
    }

    public void saveEloAsync(UUID uuid, int elo) {
        CompletableFuture.runAsync(() -> saveEloSync(uuid, elo));
    }

    public void saveEloSync(UUID uuid, int elo) {
        String sql = """
            INSERT INTO player_elo (uuid, elo) VALUES (?, ?)
            ON CONFLICT(uuid) DO UPDATE SET elo = excluded.elo;
        """;

        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setInt(2, elo);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to save Elo for " + uuid + ": " + e.getMessage());
        }
    }

    public void saveAllSync() {
        String sql = """
            INSERT INTO player_elo (uuid, elo) VALUES (?, ?)
            ON CONFLICT(uuid) DO UPDATE SET elo = excluded.elo;
        """;

        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            conn.setAutoCommit(false);

            for (Map.Entry<UUID, Integer> entry : eloCache.entrySet()) {
                stmt.setString(1, entry.getKey().toString());
                stmt.setInt(2, entry.getValue());
                stmt.addBatch();
            }

            stmt.executeBatch();
            conn.commit();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to batch save Elo data: " + e.getMessage());
        }
    }

    public JavaPlugin getPlugin() {
        return this.plugin;
    }
}