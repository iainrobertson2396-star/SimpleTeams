package dev.simpleteams;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class TeamCommand implements CommandExecutor, TabCompleter {

    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final Component PREFIX = Component.text("[Team] ", NamedTextColor.GOLD);
    private static final List<String> SUBCOMMANDS = List.of(
            "create", "invite", "accept", "deny", "leave", "kick", "transfer", "disband", "ally", "unally", "allies", "ff", "stats", "info", "list");

    private final TeamManager teams;

    public TeamCommand(TeamManager teams) {
        this.teams = teams;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use team commands.");
            return true;
        }
        if (args.length == 0) {
            help(player);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create" -> create(player, args);
            case "invite" -> invite(player, args);
            case "accept" -> accept(player);
            case "deny" -> deny(player);
            case "leave" -> leave(player);
            case "kick" -> kick(player, args);
            case "transfer" -> transfer(player, args);
            case "disband" -> disband(player);
            case "ff" -> friendlyFire(player, args);
            case "stats" -> stats(player, args);
            case "ally" -> ally(player, args);
            case "unally" -> unally(player, args);
            case "allies" -> allies(player);
            case "info" -> info(player, args);
            case "list" -> list(player);
            default -> help(player);
        }
        return true;
    }

    // ---------- subcommands ----------

    private void create(Player player, String[] args) {
        if (args.length < 2) {
            send(player, NamedTextColor.RED, "Usage: /team create <name>");
            return;
        }
        if (teams.getTeam(player.getUniqueId()) != null) {
            send(player, NamedTextColor.RED, "You're already in a team. Leave it first with /team leave.");
            return;
        }
        String name = args[1];
        if (!VALID_NAME.matcher(name).matches()) {
            send(player, NamedTextColor.RED, "Team names must be 3-16 characters: letters, numbers, or underscores.");
            return;
        }
        if (teams.getTeam(name) != null) {
            send(player, NamedTextColor.RED, "A team with that name already exists.");
            return;
        }
        Team team = teams.create(name, player.getUniqueId());
        send(player, NamedTextColor.GREEN, "Created team " + team.name() + ". Use /team invite <player> to add members.");
    }

    private void invite(Player player, String[] args) {
        Team team = requireLeader(player);
        if (team == null) return;
        if (args.length < 2) {
            send(player, NamedTextColor.RED, "Usage: /team invite <player>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            send(player, NamedTextColor.RED, "That player isn't online.");
            return;
        }
        if (target.equals(player)) {
            send(player, NamedTextColor.RED, "You can't invite yourself.");
            return;
        }
        if (teams.getTeam(target.getUniqueId()) != null) {
            send(player, NamedTextColor.RED, target.getName() + " is already in a team.");
            return;
        }

        teams.invite(team, player.getUniqueId(), target.getUniqueId());
        send(player, NamedTextColor.GREEN, "Invited " + target.getName() + " to " + team.name() + ". The invite expires in 60 seconds.");
        target.sendMessage(PREFIX
                .append(Component.text(player.getName() + " invited you to join team " + team.name() + ". ", NamedTextColor.YELLOW))
                .append(Component.text("[Accept]", NamedTextColor.GREEN).clickEvent(ClickEvent.runCommand("/team accept")))
                .append(Component.space())
                .append(Component.text("[Deny]", NamedTextColor.RED).clickEvent(ClickEvent.runCommand("/team deny"))));
    }

    private void accept(Player player) {
        if (teams.getTeam(player.getUniqueId()) != null) {
            send(player, NamedTextColor.RED, "You're already in a team.");
            return;
        }
        TeamManager.Invite invite = teams.takeInvite(player.getUniqueId());
        if (invite == null) {
            send(player, NamedTextColor.RED, "You have no pending invite (it may have expired).");
            return;
        }
        Team team = invite.team();
        teams.addMember(team, player.getUniqueId());
        broadcast(team, NamedTextColor.GREEN, player.getName() + " joined the team!");
    }

    private void deny(Player player) {
        TeamManager.Invite invite = teams.takeInvite(player.getUniqueId());
        if (invite == null) {
            send(player, NamedTextColor.RED, "You have no pending invite.");
            return;
        }
        send(player, NamedTextColor.YELLOW, "Invite declined.");
        Player inviter = Bukkit.getPlayer(invite.inviter());
        if (inviter != null) {
            send(inviter, NamedTextColor.YELLOW, player.getName() + " declined your invite.");
        }
    }

    private void leave(Player player) {
        Team team = teams.getTeam(player.getUniqueId());
        if (team == null) {
            send(player, NamedTextColor.RED, "You're not in a team.");
            return;
        }
        boolean wasLeader = team.isLeader(player.getUniqueId());
        boolean wasLast = team.members().size() == 1;
        teams.removeMember(team, player.getUniqueId());
        send(player, NamedTextColor.YELLOW, "You left " + team.name() + (wasLast ? " (the team was disbanded)." : "."));
        if (!wasLast) {
            broadcast(team, NamedTextColor.YELLOW, player.getName() + " left the team.");
            if (wasLeader) {
                broadcast(team, NamedTextColor.YELLOW, nameOf(team.leader()) + " is now the team leader.");
            }
        }
    }

    private void kick(Player player, String[] args) {
        Team team = requireLeader(player);
        if (team == null) return;
        if (args.length < 2) {
            send(player, NamedTextColor.RED, "Usage: /team kick <player>");
            return;
        }
        UUID target = findMember(team, args[1]);
        if (target == null) {
            send(player, NamedTextColor.RED, "That player isn't on your team.");
            return;
        }
        if (target.equals(player.getUniqueId())) {
            send(player, NamedTextColor.RED, "You can't kick yourself. Use /team leave or /team disband.");
            return;
        }
        teams.removeMember(team, target);
        broadcast(team, NamedTextColor.YELLOW, nameOf(target) + " was removed from the team.");
        Player online = Bukkit.getPlayer(target);
        if (online != null) {
            send(online, NamedTextColor.RED, "You were removed from team " + team.name() + ".");
        }
    }

    private void transfer(Player player, String[] args) {
        Team team = requireLeader(player);
        if (team == null) return;
        if (args.length < 2) {
            send(player, NamedTextColor.RED, "Usage: /team transfer <player>");
            return;
        }
        UUID target = findMember(team, args[1]);
        if (target == null) {
            send(player, NamedTextColor.RED, "That player isn't on your team.");
            return;
        }
        if (target.equals(player.getUniqueId())) {
            send(player, NamedTextColor.RED, "You're already the leader.");
            return;
        }
        team.setLeader(target);
        teams.save();
        broadcast(team, NamedTextColor.GREEN, nameOf(target) + " is now the team leader.");
    }

    private void disband(Player player) {
        Team team = requireLeader(player);
        if (team == null) return;
        broadcast(team, NamedTextColor.RED, "Team " + team.name() + " was disbanded by " + player.getName() + ".");
        teams.disband(team);
    }

    private void friendlyFire(Player player, String[] args) {
        Team team = teams.getTeam(player.getUniqueId());
        if (team == null) {
            send(player, NamedTextColor.RED, "You're not in a team.");
            return;
        }
        if (args.length < 2) {
            send(player, NamedTextColor.YELLOW, "Friendly fire is currently "
                    + (team.friendlyFire() ? "ON" : "OFF") + " for " + team.name()
                    + ". The leader can change it with /team ff <on|off>.");
            return;
        }
        if (!team.isLeader(player.getUniqueId())) {
            send(player, NamedTextColor.RED, "Only the team leader can change friendly fire.");
            return;
        }
        boolean enable;
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "on", "true", "enable" -> enable = true;
            case "off", "false", "disable" -> enable = false;
            default -> {
                send(player, NamedTextColor.RED, "Usage: /team ff <on|off>");
                return;
            }
        }
        if (team.friendlyFire() == enable) {
            send(player, NamedTextColor.YELLOW, "Friendly fire is already " + (enable ? "ON" : "OFF") + ".");
            return;
        }
        team.setFriendlyFire(enable);
        teams.save();
        broadcast(team, enable ? NamedTextColor.RED : NamedTextColor.GREEN,
                "Friendly fire is now " + (enable ? "ON - teammates can hurt each other!" : "OFF - teammates are protected."));
    }

    private void ally(Player player, String[] args) {
        Team team = requireLeader(player);
        if (team == null) return;
        if (args.length < 2) {
            send(player, NamedTextColor.RED, "Usage: /team ally <team>");
            return;
        }
        Team other = teams.getTeam(args[1]);
        if (other == null) {
            send(player, NamedTextColor.RED, "No team named '" + args[1] + "'.");
            return;
        }
        if (other == team) {
            send(player, NamedTextColor.RED, "You can't ally with your own team.");
            return;
        }
        if (teams.areAllied(team, other)) {
            send(player, NamedTextColor.RED, "You're already allied with " + other.name() + ".");
            return;
        }

        // They already asked us: accepting creates the alliance.
        if (teams.takeAllyRequest(other, team)) {
            teams.addAlliance(team, other);
            broadcast(team, NamedTextColor.GREEN, "Your team is now allied with " + other.name() + "!");
            broadcast(other, NamedTextColor.GREEN, "Your team is now allied with " + team.name() + "!");
            return;
        }

        if (!teams.addAllyRequest(team, other)) {
            send(player, NamedTextColor.YELLOW, "You already sent an ally request to " + other.name() + ".");
            return;
        }
        broadcast(team, NamedTextColor.GREEN, "Sent an ally request to " + other.name() + ". It expires in 10 minutes.");

        Player otherLeader = Bukkit.getPlayer(other.leader());
        if (otherLeader != null) {
            otherLeader.sendMessage(PREFIX
                    .append(Component.text("Team " + team.name() + " wants to ally with your team. ", NamedTextColor.YELLOW))
                    .append(Component.text("[Accept]", NamedTextColor.GREEN)
                            .clickEvent(ClickEvent.runCommand("/team ally " + team.name())))
                    .append(Component.space())
                    .append(Component.text("[Decline]", NamedTextColor.RED)
                            .clickEvent(ClickEvent.runCommand("/team unally " + team.name()))));
        }
    }

    private void unally(Player player, String[] args) {
        Team team = requireLeader(player);
        if (team == null) return;
        if (args.length < 2) {
            send(player, NamedTextColor.RED, "Usage: /team unally <team>");
            return;
        }
        Team other = teams.getTeam(args[1]);
        if (other == null) {
            send(player, NamedTextColor.RED, "No team named '" + args[1] + "'.");
            return;
        }

        if (teams.areAllied(team, other)) {
            teams.removeAlliance(team, other);
            broadcast(team, NamedTextColor.YELLOW, "Your alliance with " + other.name() + " has ended.");
            broadcast(other, NamedTextColor.YELLOW, team.name() + " ended its alliance with your team.");
        } else if (teams.takeAllyRequest(other, team)) {
            broadcast(team, NamedTextColor.YELLOW, "Declined the ally request from " + other.name() + ".");
            broadcast(other, NamedTextColor.YELLOW, team.name() + " declined your ally request.");
        } else if (teams.takeAllyRequest(team, other)) {
            broadcast(team, NamedTextColor.YELLOW, "Cancelled your ally request to " + other.name() + ".");
        } else {
            send(player, NamedTextColor.RED, "You have no alliance or pending request with " + other.name() + ".");
        }
    }

    private void allies(Player player) {
        Team team = teams.getTeam(player.getUniqueId());
        if (team == null) {
            send(player, NamedTextColor.RED, "You're not in a team.");
            return;
        }
        List<Team> allied = teams.alliesOf(team);
        List<Team> incoming = teams.incomingAllyRequests(team);
        List<Team> outgoing = teams.outgoingAllyRequests(team);

        send(player, NamedTextColor.AQUA, "Allies: " + (allied.isEmpty() ? "none"
                : allied.stream().map(Team::name).collect(Collectors.joining(", "))));
        if (!incoming.isEmpty()) {
            send(player, NamedTextColor.YELLOW, "Incoming requests (leader: /team ally <team> to accept): "
                    + incoming.stream().map(Team::name).collect(Collectors.joining(", ")));
        }
        if (!outgoing.isEmpty()) {
            send(player, NamedTextColor.YELLOW, "Outgoing requests: "
                    + outgoing.stream().map(Team::name).collect(Collectors.joining(", ")));
        }
    }

    private void stats(Player player, String[] args) {
        Team team;
        if (args.length >= 2) {
            team = teams.getTeam(args[1]);
            if (team == null) {
                send(player, NamedTextColor.RED, "No team named '" + args[1] + "'.");
                return;
            }
        } else {
            team = teams.getTeam(player.getUniqueId());
            if (team == null) {
                send(player, NamedTextColor.RED, "You're not in a team. Use /team stats <name> to look one up.");
                return;
            }
        }
        double ratio = team.deaths() == 0 ? team.kills() : (double) team.kills() / team.deaths();
        send(player, NamedTextColor.AQUA, "Stats for team " + team.name() + ":");
        send(player, NamedTextColor.GREEN, "Kills: " + team.kills());
        send(player, NamedTextColor.RED, "Deaths: " + team.deaths());
        send(player, NamedTextColor.YELLOW, "K/D: " + String.format(Locale.ROOT, "%.2f", ratio));
    }

    private void info(Player player, String[] args) {
        Team team;
        if (args.length >= 2) {
            team = teams.getTeam(args[1]);
            if (team == null) {
                send(player, NamedTextColor.RED, "No team named '" + args[1] + "'.");
                return;
            }
        } else {
            team = teams.getTeam(player.getUniqueId());
            if (team == null) {
                send(player, NamedTextColor.RED, "You're not in a team. Use /team info <name> to look one up.");
                return;
            }
        }
        String members = team.members().stream()
                .map(id -> (team.isLeader(id) ? "★ " : "") + nameOf(id)
                        + (Bukkit.getPlayer(id) != null ? "" : " (offline)"))
                .collect(Collectors.joining(", "));
        send(player, NamedTextColor.AQUA, "Team " + team.name() + " (" + team.members().size() + " members) - friendly fire "
                + (team.friendlyFire() ? "ON" : "OFF"));
        send(player, NamedTextColor.GRAY, members);
        List<Team> allyTeams = teams.alliesOf(team);
        if (!allyTeams.isEmpty()) {
            send(player, NamedTextColor.GREEN, "Allies: "
                    + allyTeams.stream().map(Team::name).collect(Collectors.joining(", ")));
        }
    }

    private void list(Player player) {
        if (teams.allTeams().isEmpty()) {
            send(player, NamedTextColor.YELLOW, "There are no teams yet. Create one with /team create <name>.");
            return;
        }
        String all = teams.allTeams().stream()
                .map(t -> t.name() + " (" + t.members().size() + ")")
                .collect(Collectors.joining(", "));
        send(player, NamedTextColor.AQUA, "Teams: " + all);
    }

    private void help(Player player) {
        send(player, NamedTextColor.AQUA, "Team commands:");
        String[] lines = {
                "/team create <name> - create a team",
                "/team invite <player> - invite a player (leader)",
                "/team accept | deny - answer an invite",
                "/team leave - leave your team",
                "/team kick <player> - remove a member (leader)",
                "/team transfer <player> - hand over leadership (leader)",
                "/team disband - delete your team (leader)",
                "/team ff [on|off] - view or set friendly fire (leader sets)",
                "/team stats [team] - show a team's kills and deaths",
                "/team ally <team> - request or accept an alliance (leader)",
                "/team unally <team> - end an alliance or decline/cancel a request (leader)",
                "/team allies - show allies and pending requests",
                "/team info [team] - show team members",
                "/team list - list all teams"
        };
        for (String line : lines) {
            player.sendMessage(Component.text(line, NamedTextColor.GRAY));
        }
    }

    // ---------- helpers ----------

    private Team requireLeader(Player player) {
        Team team = teams.getTeam(player.getUniqueId());
        if (team == null) {
            send(player, NamedTextColor.RED, "You're not in a team.");
            return null;
        }
        if (!team.isLeader(player.getUniqueId())) {
            send(player, NamedTextColor.RED, "Only the team leader can do that.");
            return null;
        }
        return team;
    }

    private UUID findMember(Team team, String name) {
        for (UUID id : team.members()) {
            if (nameOf(id).equalsIgnoreCase(name)) return id;
        }
        return null;
    }

    private String nameOf(UUID id) {
        String name = Bukkit.getOfflinePlayer(id).getName();
        return name != null ? name : id.toString().substring(0, 8);
    }

    private void send(Player player, NamedTextColor color, String text) {
        player.sendMessage(PREFIX.append(Component.text(text, color)));
    }

    private void broadcast(Team team, NamedTextColor color, String text) {
        for (UUID id : team.members()) {
            Player online = Bukkit.getPlayer(id);
            if (online != null) send(online, color, text);
        }
    }

    // ---------- tab completion ----------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) return List.of();
        List<String> options = new ArrayList<>();

        if (args.length == 1) {
            options.addAll(SUBCOMMANDS);
        } else if (args.length == 2) {
            Team own = teams.getTeam(player.getUniqueId());
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "invite" -> Bukkit.getOnlinePlayers().stream()
                        .filter(p -> teams.getTeam(p.getUniqueId()) == null)
                        .forEach(p -> options.add(p.getName()));
                case "kick", "transfer" -> {
                    if (own != null) own.members().forEach(id -> options.add(nameOf(id)));
                }
                case "ff" -> options.addAll(List.of("on", "off"));
                case "ally" -> teams.allTeams().stream()
                        .filter(t -> own == null || (t != own && !teams.areAllied(own, t)))
                        .forEach(t -> options.add(t.name()));
                case "unally" -> {
                    if (own != null) teams.alliesOf(own).forEach(t -> options.add(t.name()));
                }
                case "info", "stats" -> teams.allTeams().forEach(t -> options.add(t.name()));
                default -> { }
            }
        }

        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
