package dev.simpleteams;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

/**
 * Tracks team kills and deaths, counting only deaths caused by another player.
 * - Deaths: a team member being killed by a player (mobs, lava, falling, etc. are ignored).
 * - Kills: a team member killing a player. Killing your own teammate
 *   (possible when friendly fire is on) counts as a death for the team but not as a kill.
 */
public final class StatsListener implements Listener {

    private final TeamManager teams;

    public StatsListener(TeamManager teams) {
        this.teams = teams;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim)) return;

        Team victimTeam = teams.getTeam(victim.getUniqueId());
        Team killerTeam = teams.getTeam(killer.getUniqueId());
        boolean changed = false;

        if (victimTeam != null) {
            victimTeam.addDeath();
            changed = true;
        }
        if (killerTeam != null && killerTeam != victimTeam) {
            killerTeam.addKill();
            changed = true;
        }

        if (changed) {
            teams.save();
        }
    }
}
