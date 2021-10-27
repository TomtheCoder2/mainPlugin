package mindustry.plugin;

import arc.Core;
import arc.files.Fi;
import arc.util.Strings;
import mindustry.Vars;
import mindustry.content.Items;
import mindustry.game.Team;
import mindustry.game.Teams.TeamData;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.maps.Map;
import mindustry.plugin.discordcommands.Command;
import mindustry.plugin.discordcommands.Context;
import mindustry.plugin.discordcommands.DiscordCommands;
import mindustry.plugin.discordcommands.RoleRestrictedCommand;
import mindustry.plugin.requests.GetMap;
import mindustry.world.modules.ItemModule;
import net.dv8tion.jda.api.entities.Role;
import org.javacord.api.entity.message.MessageBuilder;
import org.javacord.api.entity.message.embed.EmbedBuilder;

import java.awt.*;
import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

import static mindustry.Vars.state;
import static mindustry.plugin.Utils.*;
import static mindustry.gen.StateSnapshotCallPacket.*;

public class ComCommands {
//    public static ContentHandler contentHandler = new ContentHandler();

    GetMap map = new GetMap();

    public void registerCommands(DiscordCommands handler) {
        handler.registerCommand(new Command("chat") {
            {
                help = "Sends a message to in-game chat.";
                usage = "<message>";
                minArguments = 1;
            }

            public void run(Context ctx) {
                if (ctx.event.isPrivateMessage()) return;

                EmbedBuilder eb = new EmbedBuilder();
                ctx.message = escapeCharacters(ctx.message);
                if (ctx.message.length() < chatMessageMaxSize) {
                    Call.sendMessage("[sky]" + ctx.author.getName() + " @discord >[] " + ctx.message);
                    eb.setTitle("Command executed");
                    eb.setDescription("Your message was sent successfully..");
                    ctx.channel.sendMessage(eb);
                } else {
                    ctx.reply("Message too big.");
                }
            }
        });
        handler.registerCommand(new Command("map") {
            {
                help = "Preview and download a server map in a .msav file format.";
                usage = "<mapname/mapid>";
                minArguments = 1;
            }

            public void run(Context ctx) {
                if (ctx.args.length < 2) {
                    ctx.reply("Not enough arguments, use `%map <mapname/mapid>`".replace("%", ioMain.prefix));
                    return;
                }

                Map found = getMapBySelector(ctx.message.trim());
                if (found == null) {
                    ctx.reply("Map not found!");
                    return;
                }

                Fi mapfile = found.file;
                try {
                    String absolute = map.getMap(mapfile).get(0);
                    System.out.println(absolute);

                    EmbedBuilder embed = new EmbedBuilder()
                            .setTitle(escapeCharacters(found.name()))
                            .setDescription(escapeCharacters(found.description()))
                            .setAuthor(escapeCharacters(found.author()))
                            .setImage("attachment://output.png");
                    MessageBuilder mb = new MessageBuilder();
                    mb.addEmbed(embed);
                    mb.addFile(new File(absolute));
                    mb.addAttachment(mapfile.file());
                    mb.send(ctx.channel);
                } catch (Exception e) {
                    String err = Strings.neatError(e, true);
                    int max = 900;
//                    errDelete(msg, "Error parsing map.", err.length() < max ? err : err.substring(0, max));
                }
            }
        });
        handler.registerCommand(new Command("players") {
            {
                help = "Check who is online and their ids.";
            }

            public void run(Context ctx) {
                StringBuilder msg = new StringBuilder("**Players online: " + Groups.player.size() + "**\n```\n");
                for (Player player : Groups.player) {
                    msg.append("· ").append(escapeEverything(player)).append(" : ").append(player.id).append("\n");
                }
                msg.append("```");
//                ctx.channel.sendMessage(msg.toString());
                StringBuilder lijst = new StringBuilder();
//                StringBuilder admins = new StringBuilder();
                EmbedBuilder eb = new EmbedBuilder()
                        .setTitle("Players: " + Groups.player.size());
//                lijst.append("Players: " + Groups.player.size() + "\n");
//                admins.append(" ");
//                if (Groups.player.count(p -> p.admin) > 0) {
//                    admins.append("online admins: ");// + Vars.playerGroup.all().count(p->p.isAdmin)+"\n");
//                }
                if (Groups.player.size() == 0) {
                    lijst.append("No players are currently in the server.");// + Vars.playerGroup.all().count(p->p.isAdmin)+"\n");
                }
                for (Player player : Groups.player) {
                    lijst.append("`* ");
                    if (player.admin()) {
                        lijst.append(String.format("%-9s :` ", "admin"));
                    } else {
                        lijst.append(String.format("%-9s :` ", player.id));
                    }
                    lijst.append(escapeEverything(player.name)).append("\n");
                }

                new MessageBuilder()
                        .setEmbed(new EmbedBuilder()
                                .setTitle("Players online: " + Groups.player.size())
//                                    .setDescription( "Info about the Server: ")
                                .setDescription(lijst.toString())
//                                .addField("Admins: ", admins+" ")
//                                .addField("Players:", lijst.toString())
                                .setColor(Color.ORANGE))
                        .send(ctx.channel);
            }
        });
        handler.registerCommand(new Command("status") {
            {
                help = "Get basic server information.";
            }

            public void run(Context ctx) {
                try {
                    EmbedBuilder eb = new EmbedBuilder()
                            .setTitle(ioMain.serverName)
                            .addField("Players", String.valueOf(Groups.player.size()), true)
                            .addField("Map", Vars.state.map.name(), true)
                            .addField("Wave", String.valueOf(state.wave), true)
                            .addField("TPS", String.valueOf(Core.graphics.getFramesPerSecond()), true)
                            .addField("Next wave in", Math.round(state.wavetime / 60) + " seconds.", true);

                    ctx.channel.sendMessage(eb);
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                    e.printStackTrace();
                    ctx.reply("An error has occurred.");
                }
            }
        });
        handler.registerCommand(new Command("resinfo") {
            {
                help = "Check the amount of resources in the core.";
                usage =  "[team]";
            }

            public void run(Context ctx) {
                if (!state.rules.waves) {
                    ctx.reply("Only available in survival mode!");
                    return;
                }
                // the normal player team is "sharded"
                TeamData data = state.teams.get(Team.sharded);
                if (ctx.args.length == 2) {
                    try {
                        Field field = Team.class.getDeclaredField(ctx.args[1]);
                        data = state.teams.get((Team) field.get(null));
                    } catch (NoSuchFieldException | IllegalAccessException ignored) {
                    }
                }
                //-- Items are shared between cores
//                CoreEntity core = data.cores.first();
                ItemModule core = Groups.player.first().core().items;
//                ItemModule items = core.items;
                EmbedBuilder eb = new EmbedBuilder()
                        .setTitle("Resources in the core:");
//                items.forEach((item, amount) -> eb.addInlineField(item.name, String.valueOf(amount)));

                eb.addInlineField("copper: ", core.get(Items.copper) + "\n");
                eb.addInlineField("lead: ", core.get(Items.lead) + "\n");
                eb.addInlineField("graphite: ", core.get(Items.graphite) + "\n");
                eb.addInlineField("metaglass: ", core.get(Items.metaglass) + "\n");
                eb.addInlineField("titanium: ", core.get(Items.titanium) + "\n");
                eb.addInlineField("thorium: ", core.get(Items.thorium) + "\n");
                eb.addInlineField("silicon: ", core.get(Items.silicon) + "\n");
                eb.addInlineField("plastanium: ", core.get(Items.plastanium) + "\n");
                eb.addInlineField("phase fabric: ", core.get(Items.phaseFabric) + "\n");
                eb.addInlineField("surge alloy: ", core.get(Items.surgeAlloy) + "\n");

                ctx.channel.sendMessage(eb);
            }
        });

        handler.registerCommand(new Command("help") {
            {
                help = "Display all available commands and their usage.";
                usage = "[command]";
            }

            public void run(Context ctx) {
                if (ctx.args.length == 1) {
                    StringBuilder publicCommands = new StringBuilder();
                    StringBuilder restrictedCommands = new StringBuilder();
                    StringBuilder management = new StringBuilder();
                    StringBuilder moderation = new StringBuilder();
                    StringBuilder mapReviewer = new StringBuilder();


                    for (Command command : handler.getAllCommands()) {
                        if (command.hidden) continue;
                        if (!command.hasPermission(ctx)) continue;
                        switch (command.category) {
                            case "moderation" -> {
                                moderation.append("**").append(command.name).append("** ");
//                                if (!command.usage.equals("")) {
//                                    moderation.append(command.usage);
//                                }
                                moderation.append("\n");
                            }
                            case "management" -> {
                                management.append("**").append(command.name).append("** ");
//                                if (!command.usage.equals("")) {
//                                    management.append(command.usage);
//                                }
                                management.append("\n");
                            }
                            case "mapReviewer" -> {
                                mapReviewer.append("**").append(command.name).append("** ");
//                                if (!command.usage.equals("")) {
//                                    mapReviewer.append(command.usage);
//                                }
                                mapReviewer.append("\n");
                            }
                            default -> {
                                publicCommands.append("**").append(command.name).append("** ");
//                                if (!command.usage.equals("")) {
//                                    publicCommands.append(command.usage);
//                                }
                                publicCommands.append("\n");
                            }
                        }
                    }
                    EmbedBuilder embed = new EmbedBuilder()
                            .setTitle("Commands:");
                    embed.addField("**__Public:__**", publicCommands.toString(), true);
                    if (moderation.length() != 0) {
                        embed.addField("**__Moderation:__**", moderation.toString(), true);
                    }
                    if (management.length() != 0) {
                        embed.addField("**__Management:__**", management.toString(), true);
                    }
                    if (mapReviewer.length() != 0) {
                        embed.addField("**__Map reviewer:__**", mapReviewer.toString(), true);
                    }
                    ctx.channel.sendMessage(embed);
                } else {
                    EmbedBuilder embed = new EmbedBuilder();
                    for (Command command : handler.getAllCommands()) {
                        if (command.name.equals(ctx.args[1])) {
                            embed.setTitle(command.name)
                                    .setDescription(command.help);
                            if (!command.usage.equals("")) {
                                embed.addField("Usage:", ioMain.prefix + command.name + " " + command.usage);
                            }
                            embed.addField("Category:", command.category);
                        }
                    }
                    ctx.channel.sendMessage(embed);
                }
            }
        });

//        handler.registerCommand(new Command("redeem") {
//            {
//                help = "<name|id> Promote your in-game rank. [NOTE: Abusing this power and giving it to other players will result in a ban.]";
//            }
//
//            public void run(Context ctx) {
//                CompletableFuture.runAsync(() -> {
//                    EmbedBuilder eb = new EmbedBuilder();
//                    String target = "";
//                    if (ctx.args.length > 1) {
//                        target = ctx.args[1];
//                    }
//                    List<Role> authorRoles = ctx.author.asUser().get().getRoles(ctx.event.getServer().get()); // javacord gay
//                    List<String> roles = new ArrayList<>();
//                    for (Role r : authorRoles) {
//                        if (r != null) {
//                            roles.add(r.getIdAsString());
//                        }
//                    }
//                    if (target.length() > 0) {
//                        int rank = 0;
//                        for (String role : roles) {
//                            if (rankRoles.containsKey(role)) {
//                                if (rankRoles.get(role) > rank) {
//                                    rank = rankRoles.get(role);
//                                }
//                            }
//                        }
//                        Player player = findPlayer(target);
//                        if (player != null && rank > 0) {
//                            PlayerData pd = getData(player.uuid());
//                            if (pd != null) {
//                                pd.rank = rank;
//                                setData(player.uuid(), pd);
//                            }
//                            eb.setTitle("Command executed successfully");
//                            eb.setDescription("Promoted " + escapeCharacters(player.name) + " to " + escapeColorCodes(rankNames.get(rank).name) + ".");
//                            ctx.channel.sendMessage(eb);
//                            player.con.kick("Your rank was modified, please rejoin.", 0);
//                        } else {
//                            eb.setTitle("Command terminated");
//                            eb.setDescription("Player not online or not found.");
//                            ctx.channel.sendMessage(eb);
//                        }
//
//                    } else {
//                        eb.setTitle("Command terminated");
//                        eb.setDescription("Invalid arguments provided or no roles to redeem.");
//                        ctx.channel.sendMessage(eb);
//                    }
//                });
//            }
//
//        });

//        TextChannel warningsChannel = null;
//        if (ioMain.data.has("warnings_chat_channel_id")) {
//            warningsChannel = getTextChannel(ioMain.data.getString("warnings_chat_channel_id"));
//        }
    }
}