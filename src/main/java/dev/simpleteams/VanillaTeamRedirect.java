package dev.simpleteams;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.event.server.ServerCommandEvent;

import java.util.Locale;

/**
 * Makes sure Minecraft's built-in scoreboard "/team" command can't be used.
 * Both "/team ..." and "/minecraft:team ..." are rewritten to this plugin's command,
 * and the vanilla "minecraft:team" entry is hidden from tab completion.
 *
 * Note: this only covers commands typed by players and the console. Command blocks
 * and datapack functions run vanilla commands directly and are not affected.
 */
public final class VanillaTeamRedirect implements Listener {

    private static final String TARGET = "simpleteams:team";

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();
        String line = message.startsWith("/") ? message.substring(1) : message;
        String redirected = redirect(line);
        if (redirected != null) {
            event.setMessage("/" + redirected);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onConsoleCommand(ServerCommandEvent event) {
        String redirected = redirect(event.getCommand());
        if (redirected != null) {
            event.setCommand(redirected);
        }
    }

    @EventHandler
    public void onCommandSend(PlayerCommandSendEvent event) {
        event.getCommands().remove("minecraft:team");
    }

    /** Returns the rewritten command line, or null if it isn't a vanilla team command. */
    private String redirect(String commandLine) {
        String trimmed = commandLine.stripLeading();
        int space = trimmed.indexOf(' ');
        String label = (space == -1 ? trimmed : trimmed.substring(0, space)).toLowerCase(Locale.ROOT);
        if (!label.equals("team") && !label.equals("minecraft:team")) {
            return null;
        }
        String rest = space == -1 ? "" : trimmed.substring(space);
        return TARGET + rest;
    }
}
