package mindustry.plugin.minimods;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.javacord.api.entity.message.embed.EmbedBuilder;

import arc.Events;
import arc.struct.Seq;
import arc.util.CommandHandler;
import arc.util.Reflect;
import arc.util.Strings;
import arc.util.Timer;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.plugin.MiniMod;
import mindustry.plugin.discord.Channels;
import mindustry.plugin.discord.DiscordPalette;
import mindustry.plugin.discord.Roles;
import mindustry.plugin.discord.discordcommands.DiscordRegistrar;
import mindustry.plugin.utils.Utils;
import mindustry.ui.Menus;

public class Communication implements MiniMod {
    public static class ScreenMessage {
        String title;
        String message;
        Button[] buttons;

        public static class Button {
            String text;
            String action;
        }

        public EmbedBuilder embed() {
            return new EmbedBuilder()
                        .setColor(DiscordPalette.INFO)
                        .setTitle("Screen Message")
                        .addField("Title", this.title)
                        .addField("Message", this.message)
                        .addField("Buttons", "[" + Arrays.stream(this.buttons).map(b -> b.action + ":" + b.text).collect(Collectors.joining("] [")) + "]");
        }
    }
    private ScreenMessage message;

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
        
        Events.on(EventType.PlayerJoin.class, event -> {
            Timer.schedule(() -> {
                if (!event.player.con.isConnected()) return;
                if (this.message != null)
                    showScreenMessage(this.message, event.player);
            }, 30);
        });
    }

    private static void showScreenMessage(ScreenMessage msg, Player target) {
        int id = Menus.registerMenu((player, selection) -> {
            String action = msg.buttons[selection].action;
            if (action == null) {
                return;
            }

            if (action.equals("event")) {
                mindustry.plugin.minimods.Events.join(player);
            }
        });

        String[][] buttons = new String[][] {
            Arrays.stream(msg.buttons).map(b -> b.text).toArray(String[]::new)
            new String[] {},
        };

        if (target != null) {
            Call.menu(id, msg.title, msg.message, buttons);
        } else {
            Call.menu(target.con, id, msg.title, msg.message, buttons);
        }
    }

    @Override
    public void registerDiscordCommands(DiscordRegistrar handler) {
        handler.register("screenmessage", "[title] [stuff...]", 
            data -> {
                data.usage = "<title> <buttons...> <message...> OR [clear]";
                data.help = "Send an on-screen message. Button syntax is [action:Some text] or [Some text]. Possible actions are 'event'.";
            },
            ctx -> {
                String title = ctx.args.get("title");
                if (title == null) {
                    if (this.message == null) {
                        ctx.sendEmbed(DiscordPalette.INFO, "No Screen Message", "There is no active screen message.");
                        return;
                    }

                    ctx.sendEmbed(message.embed().setTitle("Active Screen Message"));
                    return;
                }
                if (title.equals("clear")) {
                    if (this.message == null) {
                        ctx.error("No Screen Message", "There is no active screen message to clear.");
                        return;
                    }

                    ctx.sendEmbed(message.embed().setTitle("Removed Message").setColor(DiscordPalette.SUCCESS));
                    return;
                }

                String stuff = ctx.args.get("stuff");
                
                Seq<ScreenMessage.Button> buttons = new Seq<>();
                int bracketDepth = 0;
                String buttonData = null;
                int messageStart = 0;
                for (int i = 0; i < stuff.length(); i++) {
                    char c = stuff.charAt(0);
                    if (c == '[') {
                        if (bracketDepth == 0) {
                            buttonData = "";
                        }
                        bracketDepth += 1;
                    }

                    if (c == ']') {
                        bracketDepth -= 1;
                        if (bracketDepth < 0) {
                            ctx.error("Error", "Unmatched ]");
                            return;
                        }
                        if (bracketDepth == 0) {
                            messageStart = i + 1;

                            var button = new ScreenMessage.Button();
                            String[] parts = buttonData.split(":");
                            if (parts.length == 1) {
                                button.action = null;
                                button.text = buttonData;
                            } else {
                                button.action = parts[0];
                                button.text = Arrays.stream(parts).skip(1).collect(Collectors.joining(":"));
                            }
                            buttons.add(button);
                        }
                    }

                    if (buttonData != null) {
                        buttonData += c;
                    }
                }
                if (bracketDepth != 0) {
                    ctx.error("Error", "Unmatched [");
                    return;
                }

                String message = stuff.substring(messageStart);
                
                ScreenMessage msg = new ScreenMessage();
                msg.title = title;
                msg.message = message;
                msg.buttons = buttons.toArray(ScreenMessage.Button.class);
                this.message = msg;

                showScreenMessage(msg, null);

                ctx.sendEmbed(msg.embed());
            }
        );

        handler.register("alert", "<player|all|team> <message...>", 
            data -> { 
                data.roles = new long[] { Roles.APPRENTICE, Roles.MOD, Roles.ADMIN };
                data.help = "Alert player(s) using on-screen message";
                data.aliases = new String[] { "a" };
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
                        Team team = (Team)f.get(null);

                        for (Player player : Groups.player) {
                            if (player.team().equals(team)) {
                                Call.infoMessage(player.con, message);
                            }
                        }

                        ctx.success("Sent Message", "Sent message to all members of team " + team.name);
                    } catch(NoSuchFieldException | IllegalAccessException e) {
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
