package dev.simpleteams;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class Team {

    private final String name;
    private UUID leader;
    private boolean friendlyFire = false;
    private final Set<String> allies = new LinkedHashSet<>();
    private int kills;
    private int deaths;
    private final Set<UUID> members = new LinkedHashSet<>();

    public Team(String name, UUID leader) {
        this.name = name;
        this.leader = leader;
        this.members.add(leader);
    }

    public String name() {
        return name;
    }

    /** Lowercase lookup key. */
    public String key() {
        return name.toLowerCase(Locale.ROOT);
    }

    /** Keys (see {@link #key()}) of allied teams. */
    public Set<String> allies() {
        return allies;
    }

    public UUID leader() {
        return leader;
    }

    public void setLeader(UUID leader) {
        this.leader = leader;
    }

    public boolean isLeader(UUID id) {
        return leader.equals(id);
    }

    public int kills() {
        return kills;
    }

    public int deaths() {
        return deaths;
    }

    public void setStats(int kills, int deaths) {
        this.kills = kills;
        this.deaths = deaths;
    }

    public void addKill() {
        kills++;
    }

    public void addDeath() {
        deaths++;
    }

    public boolean friendlyFire() {
        return friendlyFire;
    }

    public void setFriendlyFire(boolean friendlyFire) {
        this.friendlyFire = friendlyFire;
    }

    public Set<UUID> members() {
        return members;
    }
}
