package mindustry.plugin.minimods;

import arc.util.CommandHandler;
import mindustry.gen.Call;
import mindustry.gen.Player;
import mindustry.plugin.MiniMod;
import mindustry.plugin.discord.DiscordPalette;
import mindustry.plugin.discord.Roles;
import mindustry.plugin.discord.discordcommands.DiscordRegistrar;
import mindustry.plugin.utils.GameMsg;

/**
 * Events functionality
 */
public class Events implements MiniMod {
    private static Events instance;
    private String eventIP = null;
    private int eventPort = 0;

    public Events() {
        instance = this;
    }

    /**
     * Joins an event, if there is one.
     *
     * @param player the player that will join
     * @return true if the join was successful, false if not
     */
    public static boolean join(Player player) {
        if (instance.eventIP != null) {
            Call.connect(player.con, instance.eventIP, instance.eventPort);
            return true;
        }

        return false;
    }

    @Override
    public void registerDiscordCommands(DiscordRegistrar handler) {
        handler.register("event", "[ipaddr]",
                data -> {
                    data.help = "Update event IP & port";
                    data.category = "Management";
                    data.roles = new long[]{Roles.ADMIN, Roles.MOD};
                },
                ctx -> {
                    if (!ctx.args.containsKey("ipaddr")) {
                        ctx.sendEmbed(DiscordPalette.INFO, "Event", eventIP == null ? "No event on going" : "IP: " + eventIP + ":" + eventPort);
                        return;
                    }

                    String ipaddr = ctx.args.get("ipaddr");
                    if (ipaddr.equalsIgnoreCase("none") || ipaddr.equalsIgnoreCase("null")) {
                        String prevIP = eventIP == null ? "none" : eventIP + ":" + eventPort;
                        eventIP = null;
                        ctx.success("Disabled Event", "Disabled event. Previous IP: " + prevIP);
                        return;
                    }

                    String[] parts = ipaddr.split(":");

                    if (parts.length != 2) {
                        ctx.error("Invalid Address", "Format must be <ip>:<port>");
                        return;
                    }

                    try {
                        eventPort = Integer.parseInt(parts[1]);
                        eventIP = parts[0];
                    } catch (NumberFormatException e) {
                        ctx.error("Invalid Address", parts[1] + " is not a valid port");
                        return;
                    }

                    ctx.success("Set Event IP", "Successfully updated event IP to `" + eventIP + ":" + eventPort + "`");
                }
        );
    }

    @Override
    public void registerCommands(CommandHandler handler) {
        handler.<Player>register("event", "Join an ongoing event (if there is one)", (args, player) -> {
            if (eventIP != null) {
                Call.connect(player.con, eventIP, eventPort);
            } else {
                player.sendMessage(GameMsg.error("Events", "There is no ongoing event at this time."));
            }
        });
    }
}
