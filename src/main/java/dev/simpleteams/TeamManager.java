package dev.simpleteams;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public final class TeamManager {

    public record Invite(Team team, UUID inviter, long expiresAt) {}

    public record AllyRequest(Team from, Team to, long expiresAt) {}

    private static final long INVITE_LIFETIME_MS = 60_000L;
    private static final long ALLY_REQUEST_LIFETIME_MS = 10 * 60_000L;

    private final JavaPlugin plugin;
    private final File file;
    private final Map<String, Team> teams = new LinkedHashMap<>();
    private final Map<UUID, Team> byPlayer = new HashMap<>();
    private final Map<UUID, Invite> invites = new HashMap<>();
    private final List<AllyRequest> allyRequests = new ArrayList<>();

    public TeamManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "teams.yml");
    }

    // ---------- queries ----------

    public boolean sameTeam(UUID a, UUID b) {
        Team team = byPlayer.get(a);
        return team != null && team == byPlayer.get(b);
    }

    /** True if both players share a team that has friendly fire turned OFF. */
    public boolean isProtected(UUID a, UUID b) {
        Team ta = byPlayer.get(a);
        Team tb = byPlayer.get(b);
        if (ta == null || tb == null) return false;
        if (ta == tb) return !ta.friendlyFire();
        return areAllied(ta, tb); // allies are always protected
    }

    public boolean areAllied(Team a, Team b) {
        return a != b && a.allies().contains(b.key());
    }

    public List<Team> alliesOf(Team team) {
        List<Team> result = new ArrayList<>();
        for (String key : team.allies()) {
            Team ally = teams.get(key);
            if (ally != null) result.add(ally);
        }
        return result;
    }

    public List<Team> incomingAllyRequests(Team to) {
        purgeAllyRequests();
        return allyRequests.stream().filter(r -> r.to() == to).map(AllyRequest::from).toList();
    }

    public List<Team> outgoingAllyRequests(Team from) {
        purgeAllyRequests();
        return allyRequests.stream().filter(r -> r.from() == from).map(AllyRequest::to).toList();
    }

    public Team getTeam(UUID player) {
        return byPlayer.get(player);
    }

    public Team getTeam(String name) {
        return teams.get(name.toLowerCase(Locale.ROOT));
    }

    public Collection<Team> allTeams() {
        return Collections.unmodifiableCollection(teams.values());
    }

    // ---------- changes ----------

    public Team create(String name, UUID leader) {
        Team team = new Team(name, leader);
        teams.put(name.toLowerCase(Locale.ROOT), team);
        byPlayer.put(leader, team);
        save();
        return team;
    }

    public void disband(Team team) {
        for (UUID member : team.members()) {
            byPlayer.remove(member);
        }
        teams.remove(team.key());
        invites.values().removeIf(invite -> invite.team() == team);
        allyRequests.removeIf(r -> r.from() == team || r.to() == team);
        for (Team other : teams.values()) {
            other.allies().remove(team.key());
        }
        save();
    }

    // ---------- alliances ----------

    private void purgeAllyRequests() {
        long now = System.currentTimeMillis();
        allyRequests.removeIf(r -> r.expiresAt() < now);
    }

    /** Returns false if an identical request is already pending. */
    public boolean addAllyRequest(Team from, Team to) {
        purgeAllyRequests();
        for (AllyRequest r : allyRequests) {
            if (r.from() == from && r.to() == to) return false;
        }
        allyRequests.add(new AllyRequest(from, to, System.currentTimeMillis() + ALLY_REQUEST_LIFETIME_MS));
        return true;
    }

    /** Removes the pending request from -> to. Returns true if there was one. */
    public boolean takeAllyRequest(Team from, Team to) {
        purgeAllyRequests();
        return allyRequests.removeIf(r -> r.from() == from && r.to() == to);
    }

    public void addAlliance(Team a, Team b) {
        a.allies().add(b.key());
        b.allies().add(a.key());
        allyRequests.removeIf(r -> (r.from() == a && r.to() == b) || (r.from() == b && r.to() == a));
        save();
    }

    public void removeAlliance(Team a, Team b) {
        a.allies().remove(b.key());
        b.allies().remove(a.key());
        save();
    }

    public void addMember(Team team, UUID player) {
        team.members().add(player);
        byPlayer.put(player, team);
        invites.remove(player);
        save();
    }

    public void removeMember(Team team, UUID player) {
        team.members().remove(player);
        byPlayer.remove(player);
        if (team.members().isEmpty()) {
            disband(team);
            return;
        }
        if (team.isLeader(player)) {
            team.setLeader(team.members().iterator().next());
        }
        save();
    }

    public void invite(Team team, UUID inviter, UUID target) {
        invites.put(target, new Invite(team, inviter, System.currentTimeMillis() + INVITE_LIFETIME_MS));
    }

    /** Removes and returns the pending invite, or null if none / expired / team gone. */
    public Invite takeInvite(UUID target) {
        Invite invite = invites.remove(target);
        if (invite == null) return null;
        if (invite.expiresAt() < System.currentTimeMillis()) return null;
        if (teams.get(invite.team().name().toLowerCase(Locale.ROOT)) != invite.team()) return null;
        return invite;
    }

    // ---------- persistence ----------

    public void load() {
        if (!file.exists()) return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("teams");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            try {
                String name = section.getString(key + ".name", key);
                UUID leader = UUID.fromString(section.getString(key + ".leader", ""));
                Team team = new Team(name, leader);
                team.setFriendlyFire(section.getBoolean(key + ".friendly-fire", false));
                team.setStats(section.getInt(key + ".kills", 0), section.getInt(key + ".deaths", 0));
                for (String ally : section.getStringList(key + ".allies")) {
                    team.allies().add(ally.toLowerCase(Locale.ROOT));
                }
                for (String raw : section.getStringList(key + ".members")) {
                    team.members().add(UUID.fromString(raw));
                }
                teams.put(name.toLowerCase(Locale.ROOT), team);
                for (UUID member : team.members()) {
                    byPlayer.put(member, team);
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Skipping corrupt team entry '" + key + "' in teams.yml");
            }
        }

        // drop alliances with teams that no longer exist or that aren't mutual
        for (Team team : teams.values()) {
            team.allies().removeIf(allyKey -> {
                Team other = teams.get(allyKey);
                return other == null || other == team || !other.allies().contains(team.key());
            });
        }
    }

    public void save() {
        YamlConfiguration config = new YamlConfiguration();
        for (Team team : teams.values()) {
            String path = "teams." + team.name().toLowerCase(Locale.ROOT);
            config.set(path + ".name", team.name());
            config.set(path + ".leader", team.leader().toString());
            config.set(path + ".friendly-fire", team.friendlyFire());
            config.set(path + ".kills", team.kills());
            config.set(path + ".deaths", team.deaths());
            config.set(path + ".allies", new ArrayList<>(team.allies()));
            List<String> members = team.members().stream().map(UUID::toString).toList();
            config.set(path + ".members", members);
        }
        try {
            plugin.getDataFolder().mkdirs();
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save teams.yml", e);
        }
    }
}
