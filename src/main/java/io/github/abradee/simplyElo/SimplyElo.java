package io.github.abradee.simplyElo;

import de.clickism.modrinthupdatechecker.ModrinthUpdateChecker;
import io.github.abradee.simplyElo.Commands.ResetEloCommand;
import io.github.abradee.simplyElo.Commands.SetRankCommand;
import io.github.abradee.simplyElo.Commands.ViewEloCommand;
import io.github.abradee.simplyElo.Managers.EloManager;
import io.github.abradee.simplyElo.Listeners.ConnectionListener;
import io.github.abradee.simplyElo.Listeners.KillListener;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class SimplyElo extends JavaPlugin {

    private EloManager eloManager;
    private boolean placeholderApiPresent;

    @Override
    public void onEnable() {
        getLogger().info("The plugin is initializing...");

        this.eloManager = new EloManager(this);

        PluginManager pm = Bukkit.getPluginManager();
        pm.registerEvents(new KillListener(eloManager), this);
        pm.registerEvents(new ConnectionListener(eloManager), this);

        ViewEloCommand viewEloCommand = new ViewEloCommand(eloManager);
        Objects.requireNonNull(getCommand("viewelo")).setExecutor(viewEloCommand);
        Objects.requireNonNull(getCommand("viewelo")).setTabCompleter(viewEloCommand);

        ResetEloCommand resetEloCommand = new ResetEloCommand(this, eloManager);
        Objects.requireNonNull(getCommand("resetelo")).setExecutor(resetEloCommand);
        Objects.requireNonNull(getCommand("resetelo")).setTabCompleter(resetEloCommand);

        SetRankCommand setRankCmd = new SetRankCommand(eloManager);
        getCommand("setrank").setExecutor(setRankCmd);
        getCommand("setrank").setTabCompleter(setRankCmd);

        if (pm.isPluginEnabled("PlaceholderAPI")) {
            this.placeholderApiPresent = true;
            new SimplyEloExpansion(this, eloManager).register();
            getLogger().info("PlaceholderAPI found! Registered %simplyelo_elo% successfully.");
        } else {
            this.placeholderApiPresent = false;
            getLogger().warning("PlaceholderAPI not found. Placeholders will not be available.");
        }

        getLogger().info("The plugin has started.");
        getLogger().info("Feel free to donate through https://patreon.com/abradee");

        new ModrinthUpdateChecker("simplyelo", "paper", null)
                .checkVersion(latestVersion -> {
                    String currentVersion = getDescription().getVersion();

                    if (currentVersion.contains("-BUILD") || currentVersion.contains("-SNAPSHOT")) {
                        getLogger().warning("Running a development build (" + currentVersion + "). Skipping update check.");
                        getLogger().warning("Development builds are NOT for anything outside of testing.");
                        return;
                    }

                    if (currentVersion.equals(latestVersion)) {
                        getLogger().info("You are running the latest version!");
                    } else {
                        getLogger().warning("A new update is available: v" + latestVersion);
                        getLogger().warning("Your current version: v" + currentVersion);
                        getLogger().warning("Download it here: https://modrinth.com/plugin/simplyelo");
                    }
                });
    }

    public EloManager getEloManager() {
        return eloManager;
    }

    public boolean isPlaceholderApiPresent() {
        return placeholderApiPresent;
    }

    @Override
    public void onDisable() {
        if (eloManager != null) {
            eloManager.saveAllSync();
        }

        getLogger().info("The plugin has stopped.");
        getLogger().info("Thanks for using SimplyElo!");
    }
}