package io.github.abradee.simplyElo.Managers;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class EloManager {

    private static final int DEFAULT_ELO = 100;
    private static final int K_FACTOR = 32;

    private final JavaPlugin plugin;
    private final String connectionUrl;
    private final Map<UUID, Integer> eloCache = new ConcurrentHashMap<>();

    public EloManager(JavaPlugin plugin) {
        this.plugin = plugin;

        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        File dbFile = new File(plugin.getDataFolder(), "elo_data.db");
        this.connectionUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();

        initDatabase();
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(connectionUrl);
    }

    private void initDatabase() {
        String sql = """
            CREATE TABLE IF NOT EXISTS player_elo (
                uuid TEXT PRIMARY KEY,
                elo INTEGER NOT NULL
            );
        """;

        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to initialize SQLite database: " + e.getMessage());
        }
    }

    public int getElo(UUID uuid) {
        return eloCache.getOrDefault(uuid, DEFAULT_ELO);
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

            // Defaults to 1000 if not found in DB
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