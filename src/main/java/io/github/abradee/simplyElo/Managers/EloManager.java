package io.github.abradee.simplyElo.Managers;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
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

    private static final int DEFAULT_ELO = 1000;
    private static final int K_FACTOR = 32;

    private final JavaPlugin plugin;
    private final String connectionUrl;
    private final Map<UUID, Integer> eloCache = new ConcurrentHashMap<>();

    public record LeaderboardEntry(UUID uuid, String name, int elo) {}
    private final List<LeaderboardEntry> topLeaderboardCache = new CopyOnWriteArrayList<>();

    public enum RankTier {
        UNRANKED("Unranked", "&7", "✧", 0),
        BRONZE("Bronze", "&c", "✦", 1000),
        SILVER("Silver", "&f", "✦", 1200),
        GOLD("Gold", "&e", "★", 1400),
        PLATINUM("Platinum", "&b", "★", 1600),
        DIAMOND("Diamond", "&9", "✪", 1800),
        MASTER("Master", "&d", "❂", 2000);

        private final String displayName;
        private final String color;
        private final String icon;
        private final int minElo;

        RankTier(String displayName, String color, String icon, int minElo) {
            this.displayName = displayName;
            this.color = color;
            this.icon = icon;
            this.minElo = minElo;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getColor() {
            return color;
        }

        public String getIcon() {
            return icon;
        }

        public int getMinElo() {
            return minElo;
        }

        public String getFormattedName() {
            return color + displayName;
        }

        public String getFormattedIcon() {
            return color + icon;
        }

        public static RankTier fromElo(int elo) {
            RankTier current = UNRANKED;
            for (RankTier tier : values()) {
                if (elo >= tier.minElo) {
                    current = tier;
                }
            }
            return current;
        }
    }

    public EloManager(JavaPlugin plugin) {
        this.plugin = plugin;

        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        File dbFile = new File(plugin.getDataFolder(), "elo_data.db");
        this.connectionUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();

        initDatabase();

        // Refresh top 50 cache asynchronously every 60 seconds (1200 ticks)
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::refreshTopCache, 20L, 1200L);
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
        return eloCache.getOrDefault(uuid, DEFAULT_ELO);
    }

    public RankTier getRankTier(UUID uuid) {
        return RankTier.fromElo(getElo(uuid));
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

        int newWinnerElo = Math.max(0, (int) Math.round(winnerElo + K_FACTOR * (1.0 - expectedWinner)));
        int newLoserElo = Math.max(0, (int) Math.round(loserElo + K_FACTOR * (0.0 - expectedLoser)));

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

            eloCache.put(uuid, DEFAULT_ELO);
            return DEFAULT_ELO;
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