package io.github.abradee.simplyElo.Listeners;

import io.github.abradee.simplyElo.Managers.EloManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class ConnectionListener implements Listener {

    private final EloManager eloManager;

    public ConnectionListener(EloManager eloManager) {
        this.eloManager = eloManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        eloManager.loadPlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        eloManager.unloadPlayer(event.getPlayer().getUniqueId());
    }
}