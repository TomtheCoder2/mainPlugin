package mindustry.plugin.minimods;

import arc.Events;
import arc.util.CommandHandler;
import arc.util.Strings;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.plugin.MiniMod;
import mindustry.plugin.discord.Channels;
import mindustry.plugin.discord.Roles;
import mindustry.plugin.discord.discordcommands.DiscordRegistrar;
import mindustry.plugin.utils.Utils;

import java.lang.reflect.Field;

public class Communication implements MiniMod {
    @Override
    public void registerEvents() {
        Events.on(EventType.PlayerChatEvent.class, event -> {
            if (event.message.charAt(0) != '/') {
                Player player = event.player;
                assert player != null;
                StringBuilder sb = new StringBuilder(event.message);
                for (int i = event.message.length() - 1; i >= 0; i--) {
                    if (sb.charAt(i) >= 0xF80 && sb.charAt(i) <= 0x107F) {
                        sb.deleteCharAt(i);
                    }
                }
                Channels.CHAT.sendMessage("**" + Utils.escapeEverything(event.player.name) + "**: " + sb);
            }
        });

        Channels.CHAT.addMessageCreateListener(event -> {
            if (event.getMessageAuthor().isBotUser()) {
                return;
            }
            Call.sendMessage("[sky]" + (event.getMessageAuthor().getDiscriminatedName()) + ":[white] " + event.getMessageContent());
        });

    }

    @Override
    public void registerDiscordCommands(DiscordRegistrar handler) {
        handler.register("alert", "<player|all|team> <message...>",
                data -> {
                    data.roles = new long[]{Roles.APPRENTICE, Roles.MOD, Roles.ADMIN};
                    data.help = "Alert player(s) using on-screen message";
                    data.aliases = new String[]{"a"};
                    data.category = "Communication";
                },
                ctx -> {
                    String target = ctx.args.get("player|all|team");
                    String message = ctx.args.get("message");
                    if (target.equals("all")) {
                        Call.infoMessage(message);
                        ctx.success("Sent Message", "Sent message to all players");
                    } else {
                        try {
                            Field f = Team.class.getDeclaredField(target);
                            Team team = (Team) f.get(null);

                            for (Player player : Groups.player) {
                                if (player.team().equals(team)) {
                                    Call.infoMessage(player.con, message);
                                }
                            }

                            ctx.success("Sent Message", "Sent message to all members of team " + team.name);
                        } catch (NoSuchFieldException | IllegalAccessException e) {
                            Player p = Utils.findPlayer(target);
                            if (p == null) {
                                ctx.error("No such player", "'" + target + "' is not a player or a team");
                            }
                            Call.infoMessage(p.con, message);
                            ctx.success("Sent Message", "Sent message to player " + Utils.escapeEverything(p.name()));
                        }
                    }
                }
        );
    }

    @Override
    public void registerCommands(CommandHandler handler) {
        handler.<Player>register("w", "<player> <text...>", "Whisper text to another player.", (args, player) -> {
            //find player by name
//            Player other = Groups.player.find(p -> p.name.equalsIgnoreCase(args[0]));

            Player other = Utils.findPlayer(args[0]);

            // give error message with scarlet-colored text if player isn't found
            if (other == null) {
                player.sendMessage("[scarlet]No player by that name found!");
                return;
            }

            // send the other player a message, using [lightgray] for gray text color and [] to reset color
            other.sendMessage("[orange][[[gray]whisper from [#ffd37f]" + Strings.stripColors(player.name) + "[orange]]: [gray]" + args[1]);
            player.sendMessage("[orange][[[gray]whisper to [#ffd37f]" + Strings.stripColors(other.name) + "[orange]]: [gray]" + args[1]);
        });

        handler.removeCommand("t");
        handler.<Player>register("t", "<message...>", "Send a message only to your teammates.", (args, player) -> {
            String message = args[0];
            String raw = "[#" + player.team().color.toString() + "]<T> " + Utils.formatMessage(player, message);
            Groups.player.each(p -> p.team() == player.team(), o -> o.sendMessage(raw, player, message));
        });

    }
}
