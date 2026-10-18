package dev.simpleteams;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class SimpleTeams extends JavaPlugin {

    private TeamManager teamManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        teamManager = new TeamManager(this);
        teamManager.load();

        TeamCommand command = new TeamCommand(teamManager);
        PluginCommand team = getCommand("team");
        if (team != null) {
            team.setExecutor(command);
            team.setTabCompleter(command);
        }

        getServer().getPluginManager().registerEvents(new FriendlyFireListener(teamManager), this);
        getServer().getPluginManager().registerEvents(new StatsListener(teamManager), this);
        if (getConfig().getBoolean("redirect-vanilla-team", true)) {
            getServer().getPluginManager().registerEvents(new VanillaTeamRedirect(), this);
        }
        getLogger().info("Loaded " + teamManager.allTeams().size() + " team(s).");
    }

    @Override
    public void onDisable() {
        if (teamManager != null) {
            teamManager.save();
        }
    }
}
