package io.github.abradee.simplyElo.Listeners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import io.github.abradee.simplyElo.Managers.EloManager;

public class KillListener implements Listener {

    private final EloManager eloManager;

    public KillListener(EloManager eloManager) {
        this.eloManager = eloManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        if (killer == null || killer.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }

        int oldKillerElo = eloManager.getElo(killer.getUniqueId());
        int oldVictimElo = eloManager.getElo(victim.getUniqueId());

        eloManager.processMatch(killer.getUniqueId(), victim.getUniqueId());

        int newKillerElo = eloManager.getElo(killer.getUniqueId());
        int newVictimElo = eloManager.getElo(victim.getUniqueId());

        int killerDelta = newKillerElo - oldKillerElo;
        int victimDelta = oldVictimElo - newVictimElo;

        killer.sendMessage(Component.text("+ " + killerDelta + " Elo (Now: " + newKillerElo + ")", NamedTextColor.GREEN));
        victim.sendMessage(Component.text("- " + victimDelta + " Elo (Now: " + newVictimElo + ")", NamedTextColor.RED));
    }
}
