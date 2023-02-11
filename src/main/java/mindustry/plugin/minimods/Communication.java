package mindustry.plugin.minimods;

import arc.Core;
import arc.Events;
import arc.struct.Seq;
import arc.util.Align;
import arc.util.CommandHandler;
import arc.util.Strings;
import arc.util.Timer;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.game.Gamemode;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.plugin.MiniMod;
import mindustry.plugin.discord.Channels;
import mindustry.plugin.discord.DiscordPalette;
import mindustry.plugin.discord.DiscordVars;
import mindustry.plugin.discord.Roles;
import mindustry.plugin.discord.discordcommands.DiscordRegistrar;
import mindustry.plugin.utils.GameMsg;
import mindustry.plugin.utils.Query;
import mindustry.plugin.utils.Utils;
import mindustry.ui.Menus;
import org.javacord.api.entity.message.embed.EmbedBuilder;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;

import static mindustry.plugin.database.Database.bannedWords;
import static mindustry.plugin.minimods.Logs.genRandomString;
import static mindustry.plugin.utils.Utils.escapeFoosCharacters;
import static mindustry.plugin.utils.Utils.getArrayListFromString;

public class Communication implements MiniMod {
    public static ArrayList<String> autoScreenMessages = new ArrayList<>();
    private final Seq<String> screenMessages = new Seq<>();
    private Announcement announcement = null;

    private static void showAnnouncement(Announcement msg, Player target) {
        int id = Menus.registerMenu((player, selection) -> {
            String action = msg.buttons[selection].action;
            if (action == null) {
                return;
            }

            if (action.equals("event")) {
                if (!mindustry.plugin.minimods.Events.join(player)) {
                    player.sendMessage(GameMsg.error("Events", "There is no ongoing event at this time."));
                }
            }
        });

        String[][] buttons = new String[][]{
                Arrays.stream(msg.buttons).map(b -> b.text).toArray(String[]::new)
        };

        if (target == null) {
            Call.menu(id, msg.title, msg.message, buttons);
        } else {
            Call.menu(target.con, id, msg.title, msg.message, buttons);
        }
    }

    @Override
    public void registerEvents() {
        Events.on(EventType.PlayerChatEvent.class, event -> {
            // we don't want to leak infos when we are in pvp game mode
            if (event.message.startsWith("/t") && !Vars.state.rules.pvp) {
                event.message.replace("/t", "<T> ");
            }
            if (event.message.charAt(0) != '/') {
                Player player = event.player;
                assert player != null;
                String message = escapeFoosCharacters(event.message);
                for (String bw : bannedWords) {
                    if (message.toLowerCase().contains(bw)) {
                        // create random string of length bw.length consisting of ['@', '#', '$', '%', '^', '&', '*', '!']
                        message = message.replace(bw, genRandomString(bw.length()));
                    }
                }
                Channels.CHAT.sendMessage("**" + Utils.escapeEverything(event.player.name) + "**: " + message);
            }
        });

        Timer.schedule(() -> {
            int ypos = 300;
            for (String message : screenMessages) {
                Call.infoPopup(message, 10f, Align.topRight, ypos, 0, 0, 0);
                ypos += message.split("\n").length * 20;
                ypos += 20;
            }

            for (String message : autoScreenMessages) {
                Call.infoPopup(message, 10f, Align.topRight, ypos, 0, 0, 0);
                ypos += message.split("\n").length * 20;
                ypos += 20;
            }
        }, 10f, 10f);

        Channels.CHAT.addMessageCreateListener(event -> {
            if (!event.getMessageAuthor().isRegularUser()) {
                return;
            }

            var server = event.getServer().get();
            var author = event.getMessageAuthor().asUser().get();
            String name = "";
            if (author.getNickname(server).isPresent()) {
                name = author.getNickname(server) + " (" + author.getDiscriminatedName() + ")";
            } else {
                name = author.getDiscriminatedName();
            }

            Call.sendMessage("[sky]" + name + ":[white] " + event.getMessageContent());
        });

        Events.on(EventType.PlayerJoin.class, event -> {
            Timer.schedule(() -> {
                if (!event.player.con.isConnected()) return;
                if (this.announcement != null)
                    showAnnouncement(this.announcement, event.player);
            }, 15);
        });
    }

    @Override
    public void registerDiscordCommands(DiscordRegistrar handler) {
        handler.register("announcement", "[title] [stuff...]",
                data -> {
                    data.usage = "<title...> <buttons...> <message...> OR [clear]";
                    data.help = "Set the announcement for the server. Button syntax is [action:Some text] or [Some text]. Possible actions are 'event'.";
                    data.roles = new long[]{Roles.APPRENTICE, Roles.MOD, Roles.ADMIN};
                    data.category = "Communication";
                },
                ctx -> {
                    String title = ctx.args.get("title");
                    if (title == null) {
                        if (this.announcement == null) {
                            ctx.sendEmbed(DiscordPalette.INFO, "No Announcement", "There is no active announcement.");
                            return;
                        }

                        ctx.sendEmbed(announcement.embed().setTitle("Active Announcement"));
                        return;
                    }
                    if (title.equals("clear")) {
                        if (this.announcement == null) {
                            ctx.error("No Announcement", "There is no active announcement to clear.");
                            return;
                        }

                        ctx.sendEmbed(announcement.embed().setTitle("Removed Announcement").setColor(DiscordPalette.SUCCESS));
                        return;
                    }

                    String stuff = ctx.args.get("stuff");
                    if (this.announcement != null) {
                        ctx.error("Announcement Already Exists", "Use `" + DiscordVars.prefix + "announcement clear` to delete the announcement");
                        return;
                    }
                    if (stuff == null) {
                        ctx.error("Message Body Required", "Cannot have announcement without message body");
                        return;
                    }
                    if (!stuff.contains("[")) {
                        ctx.error("Buttons Required", "Cannot have announcement without buttons");
                    }

                    // title = the title
                    // stuff = buttons + message
                    stuff = title + " " + stuff;
                    int firstBracket = stuff.indexOf("[");
                    title = stuff.substring(0, firstBracket);
                    stuff = stuff.substring(firstBracket);

                    Seq<Announcement.Button> buttons = new Seq<>();
                    int bracketDepth = 0;
                    int buttonStart = -1;
                    int messageStart = 0;
                    for (int i = 0; i < stuff.length(); i++) {
                        char c = stuff.charAt(i);
                        if (c == '[') {
                            if (bracketDepth == 0) {
                                buttonStart = i;
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

                                String buttonData = stuff.substring(buttonStart + 1, i);
                                buttonStart = -1;

                                var button = new Announcement.Button();
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
                    }
                    if (bracketDepth != 0) {
                        ctx.error("Error", "Unmatched [\nBracket depth: " + bracketDepth);
                        return;
                    }

                    String message = stuff.substring(messageStart);

                    Announcement msg = new Announcement();
                    msg.title = title;
                    msg.message = message;
                    msg.buttons = buttons.toArray(Announcement.Button.class);
                    this.announcement = msg;

                    showAnnouncement(msg, null);

                    ctx.sendEmbed(msg.embed());
                }
        );

        handler.register("screenmessage", "<add|remove|list> [message...]",
                data -> {
                    data.roles = new long[]{Roles.APPRENTICE, Roles.MOD, Roles.ADMIN};
                    data.category = "Communication";
                    data.help = "Show a persistent screen message on the side";
                },
                ctx -> {
                    switch (ctx.args.get("add|remove|list")) {
                        case "list":
                            EmbedBuilder eb = new EmbedBuilder()
                                    .setTitle("Active Screen Messages")
                                    .setColor(DiscordPalette.INFO);
                            if (screenMessages.size == 0) {
                                eb.setDescription("None");
                            }

                            int i = 0;
                            for (String message : screenMessages) {
                                eb.addField(i + "", "```\n" + message + "\n```");
                                i++;
                            }
                            ctx.sendEmbed(eb);
                            break;
                        case "remove":
                            String idstr = ctx.args.get("message");
                            if (idstr == null || !Strings.canParseInt(idstr)) {
                                ctx.error("Invalid ID", "ID must be a number");
                                return;
                            }
                            int id = Strings.parseInt(idstr);
                            if (id >= screenMessages.size || id < 0) {
                                ctx.error("Invalid ID", "ID is out of range");
                                return;
                            }

                            String message = screenMessages.get(id);
                            screenMessages.remove(id);
                            ctx.success("Successfully removed message " + id, "```\n" + message + "\n```");
                            break;
                        case "add":
                            message = ctx.args.get("message");
                            if (message == null) {
                                ctx.error("Must provide message", ":(");
                                return;
                            }
                            message = message.replace("\\n ", "\n").replace("\\n", "\n");

                            screenMessages.add(message);
                            ctx.success("Successfully added message", ":)");
                            break;
                    }
                }
        );

        handler.register("autoscreenmessage", "<add|list|remove> [message...]",
                data -> {
                    data.category = "Communication";
                    data.aliases = new String[]{"asm"};
                    data.roles = new long[]{Roles.ADMIN, Roles.MOD};
                    data.help = "Add, remove or list screen messages that are set automatically after the server restarts";
                },
                ctx -> {
                    autoScreenMessages = getArrayListFromString(Core.settings.getString("autoscreenmessages", "[]"));
                    switch (ctx.args.get("add|list|remove")) {
                        case "list":
                            EmbedBuilder eb = new EmbedBuilder()
                                    .setTitle("Active Screen Messages")
                                    .setColor(DiscordPalette.INFO);
                            if (autoScreenMessages.size() == 0) {
                                eb.setDescription("None");
                            }

                            int i = 0;
                            for (String message : autoScreenMessages) {
                                eb.addField(i + "", "```\n" + message + "\n```");
                                i++;
                            }
                            ctx.sendEmbed(eb);
                            break;
                        case "remove":
                            String idstr = ctx.args.get("message");
                            if (idstr == null || !Strings.canParseInt(idstr)) {
                                ctx.error("Invalid ID", "ID must be a number");
                                return;
                            }
                            int id = Strings.parseInt(idstr);
                            if (id >= autoScreenMessages.size() || id < 0) {
                                ctx.error("Invalid ID", "ID is out of range");
                                return;
                            }

                            String message = autoScreenMessages.get(id);
                            autoScreenMessages.remove(id);
                            ctx.success("Successfully removed message " + id, "```\n" + message + "\n```");
                            break;
                        case "add":
                            message = ctx.args.get("message");
                            if (message == null) {
                                ctx.error("Must provide message", ":(");
                                return;
                            }
                            message = message.replace("\\n ", "\n").replace("\\n", "\n");

                            autoScreenMessages.add(message);
                            ctx.success("Successfully added message", ":)");
                            break;
                    }
                    Core.settings.put("autoscreenmessages", autoScreenMessages.toString());
                    Core.settings.autosave();
                }
        );

        handler.register("alert", "<player|all|team> <message...>",
                data -> {
                    data.roles = new long[]{Roles.APPRENTICE, Roles.MOD, Roles.ADMIN};
                    data.help = "Alert player(s) using a one-time on-screen message";
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
                            Player p = Query.findPlayerEntity(target);
                            if (p == null) {
                                ctx.error("No such player", "'" + target + "' is not a player or a team");
                                return;
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

            Player other = Query.findPlayerEntity(args[0]);

            // give error message with scarlet-colored text if player isn't found
            if (other == null) {
                player.sendMessage("[scarlet]No player by that name found!");
                return;
            }

            // send the other player a message, using [lightgray] for gray text color and [] to reset color
            other.sendMessage("[orange][[[gray]whisper from [#ffd37f]" + Strings.stripColors(player.name) + "[orange]]: [gray]" + args[1]);
            player.sendMessage("[orange][[[gray]whisper to [#ffd37f]" + Strings.stripColors(other.name) + "[orange]]: [gray]" + args[1]);
            Logs.logWhisper(player, other, args[1]);
        });

        handler.removeCommand("t");
        handler.<Player>register("t", "<message...>", "Send a message only to your teammates.", (args, player) -> {
            String message = args[0];
            String raw = "[#" + player.team().color.toString() + "]<T> " + Vars.netServer.chatFormatter.format(player, message);
            Groups.player.each(p -> p.team() == player.team(), o -> o.sendMessage(raw, player, message));

            if (Vars.state.rules.mode() != Gamemode.pvp) {
                Channels.CHAT.sendMessage("<T> **" + Utils.escapeEverything(player.name) + "**: " + Strings.stripGlyphs(message));
            }
        });
    }

    public static class Announcement {
        String title;
        String message;
        Button[] buttons;

        public EmbedBuilder embed() {
            return new EmbedBuilder()
                    .setColor(DiscordPalette.INFO)
                    .setTitle("Screen Message")
                    .addField("Title", this.title)
                    .addField("Message", this.message)
                    .addField("Buttons", "[" + Arrays.stream(this.buttons).map(b -> b.action + ":" + b.text).collect(Collectors.joining("] [")) + "]");
        }

        public static class Button {
            String text;
            String action;
        }
    }
}
