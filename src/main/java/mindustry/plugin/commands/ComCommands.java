package mindustry.plugin.commands;

import arc.Core;
import arc.files.Fi;
import arc.util.Timer;
import mindustry.Vars;
import mindustry.content.Items;
import mindustry.core.GameState;
import mindustry.game.Team;
import mindustry.game.Teams.TeamData;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.io.SaveIO;
import mindustry.maps.Map;
import mindustry.mod.Mods;
import mindustry.plugin.data.PersistentPlayerData;
import mindustry.plugin.data.PlayerData;
import mindustry.plugin.database.MapData;
import mindustry.plugin.discordcommands.Command;
import mindustry.plugin.discordcommands.Context;
import mindustry.plugin.discordcommands.DiscordCommands;
import mindustry.plugin.ioMain;
import mindustry.plugin.requests.GetMap;
import mindustry.world.modules.ItemModule;
import org.javacord.api.entity.message.MessageBuilder;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.permission.Role;

import java.awt.*;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import java.util.Objects;

import static arc.util.Log.debug;
import static arc.util.Log.info;
import static mindustry.Vars.saveExtension;
import static mindustry.Vars.state;
import static mindustry.plugin.database.Utils.*;
import static mindustry.plugin.ioMain.*;
import static mindustry.plugin.utils.Utils.*;
import static mindustry.plugin.utils.ranks.Utils.rankNames;
import static mindustry.plugin.utils.ranks.Utils.rankRoles;

public class ComCommands {
//    public static ContentHandler contentHandler = new ContentHandler();

    GetMap map = new GetMap();

    public void registerCommands(DiscordCommands handler) {
        handler.registerCommand(new Command("chat") {
            {
                help = "Sends a message to in-game chat.";
                usage = "<message>";
                minArguments = 1;
                hidden = true;
            }

            public void run(Context ctx) {
                if (ctx.event.isPrivateMessage()) return;

                EmbedBuilder eb = new EmbedBuilder();
                ctx.message = escapeCharacters(ctx.message);
                if (ctx.message.length() < chatMessageMaxSize) {
                    Call.sendMessage("[sky]" + ctx.author.getName() + " @discord >[] " + ctx.message);
                    eb.setTitle("Command executed");
                    eb.setDescription("Your message was sent successfully..");
                    ctx.sendMessage(eb);
                } else {
                    ctx.reply("Message too big.");
                }
            }
        });

        handler.registerCommand(new Command("version") {
            {
                help = "Get Versions of the mods";
                minArguments = 0;
            }

            public void run(Context ctx) {
                EmbedBuilder eb = new EmbedBuilder()
                        .setTitle("Mods")
                        .setColor(new Color(0x00ff00));
                if (!Vars.mods.list().isEmpty()) {
                    info("Mods:");
                    for (Mods.LoadedMod mod : Vars.mods.list()) {
                        info("  @ &fi@", mod.meta.displayName(), mod.meta.version);
                        eb.addField(mod.meta.displayName(), mod.meta.version, true);
                    }
                } else {
                    info("No mods found.");
                    eb.setDescription("No mods found.");
                }
                ctx.sendMessage(eb);
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
                    for (Map map : Vars.maps.customMaps()) {
                        if (escapeEverything(map.name()).trim().toLowerCase(Locale.ROOT).startsWith(ctx.message.trim().toLowerCase(Locale.ROOT))) {
                            found = map;
                            break;
                        }
                    }
                }
                if (found == null) {
                    ctx.reply("Map not found!");
                    return;
                }

                EmbedBuilder embed = new EmbedBuilder()
                        .setTitle(escapeCharacters(found.name()))
                        .setAuthor(escapeCharacters(found.author()));
                MapData mapData = getMapData(escapeCharacters(found.name()));
                if (mapData != null) {
                    embed.setDescription(
                            escapeCharacters(found.description()) + "\n\n" +
                                    "**Positive Rating: **" + mapData.positiveRating + "\n" +
                                    "**Negative Rating: **" + mapData.negativeRating + "\n" +
                                    "**Highscore Time: **" + mapData.highscoreTime + "\n" +
                                    "**Highscore Wave: **" + mapData.highscoreWaves + "\n" +
                                    "**Shortest Game: **" + mapData.shortestGame + "\n" +
                                    "**Play Time: **" + mapData.playtime + "\n"
                    );
                } else {
                    embed.setDescription(escapeCharacters(found.description()));
                }
                embed.setFooter(found.width + "x" + found.height);
                try {
                    attachMapPng(found, embed, ctx);
//                    ctx.channel.sendFile(mapFile.file()).embed(eb.build()).queue();
                } catch (Exception e) {
                    ctx.sendEmbed(false, ":eyes: **internal server error**");
                    e.printStackTrace();
                }


//                Timer.Task task = Timer.schedule(() -> {
//                    ctx.channel.sendMessage(embed);
//                }, 10);
//                CompletableFuture.runAsync(() -> {
//                    try {
//                        debug(absolute);
//
//                        embed.setImage("attachment://output.png");
//                        MessageBuilder mb = new MessageBuilder();
//                        mb.addEmbed(embed);
//                        mb.addFile(new File(absolute));
//                        mb.addAttachment(mapFile.file());
//                        mb.send(ctx.channel);
//                        task.cancel();
//                    } catch (Exception e) {
//                        String err = Strings.neatError(e, true);
//                        int max = 900;
////                    errDelete(msg, "Error parsing map.", err.length() < max ? err : err.substring(0, max));
//                    }
//                });
            }
        });

        handler.registerCommand(new Command("mod") {
            {
                help = "Get info about a specific mod.";
                usage = "<mod>";
            }

            @Override
            public void run(Context ctx) {
                EmbedBuilder eb = new EmbedBuilder();
                if (ctx.args.length > 1) {
                    Mods.LoadedMod mod = getMod(ctx.message);
                    if (mod != null) {
                        eb.setTitle(mod.meta.displayName())
                                .addField("Internal Name: ", mod.name)
                                .addField("Version: ", mod.meta.version)
                                .addField("Author: ", mod.meta.author)
                                .addField("Description: ", mod.meta.description);
                    } else {
                        eb.setTitle("Error")
                                .setColor(new Color(0xff0000))
                                .setDescription("No mod with name " + ctx.args[1] + " found.");
                    }
                } else {
                    eb.setTitle("Mods")
                            .setColor(new Color(0x00ff00));
                    if (!Vars.mods.list().isEmpty()) {
                        for (Mods.LoadedMod mod : Vars.mods.list()) {
                            eb.addField(mod.meta.displayName(), mod.meta.version, true);
                        }
                    } else {
                        eb.setDescription("No mods found.");
                    }
                }
                ctx.sendMessage(eb);
            }
        });

        handler.registerCommand(new Command("players") {
            {
                help = "Check who is online and their ids.";
                aliases.add("p");
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

        handler.registerCommand(new Command("translate") {
            {
                help = "Translate text and send it in the in game chat";
                usage = "<language> <text...>";
            }

            @Override
            public void run(Context ctx) {
                ctx.channel.sendMessage(new EmbedBuilder()
                        .setTitle("Wrong channel!")
                        .setDescription("Please use the <#" + live_chat_channel_id + "> channel!")
                        .setColor(new Color(0xff0000)));
            }
        });

        handler.registerCommand(new Command("t") {
            {
                help = "Translate text and send it in the in game chat";
                usage = "<language> <text...>";
                hidden = true;
            }

            @Override
            public void run(Context ctx) {
                ctx.channel.sendMessage(new EmbedBuilder()
                        .setTitle("Wrong channel!")
                        .setDescription("Please use the <#" + live_chat_channel_id + "> channel!")
                        .setColor(new Color(0xff0000)));
            }
        });

        handler.registerCommand(new Command("status") {
            {
                help = "Get basic server information.";
                aliases.add("s");
            }

            public void run(Context ctx) {
                if (!state.is(GameState.State.playing)) {
                    EmbedBuilder eb = new EmbedBuilder()
                            .setTitle("Not hosting, please ping an admin!")
                            .setColor(Pals.error);
                    ctx.sendMessage(eb);
                    return;
                }
                try {
                    EmbedBuilder embed = new EmbedBuilder()
                            .setTitle(ioMain.serverName)
                            .addField("Players", String.valueOf(Groups.player.size()), true)
                            .addField("Map", Vars.state.map.name(), true)
                            .addField("Wave", String.valueOf(state.wave), true)
                            .addField("TPS", String.valueOf(Core.graphics.getFramesPerSecond()), true)
                            .addField("Next wave in", Math.round(state.wavetime / 60) + " seconds.", true);
                    // preview 
//                    Fi mapFile = Core.settings.getDataDirectory().child("../temp/map_" + Vars.state.map.name().replaceAll("[^a-zA-Z0-9\\.\\-]", "_") + saveExtension);
                    Fi tempDir = new Fi("temp/");
                    Fi mapFile = tempDir.child("map_" + Vars.state.map.name().replaceAll("[^a-zA-Z0-9\\.\\-]", "_") + "." + saveExtension);
                    Core.app.post(() -> {
                        try {
                            SaveIO.write(mapFile);
                            info("Saved to @", mapFile);
                            debug(mapFile.absolutePath());

                            attachMapPng(mapFile, embed, ctx);
//                            mapFile.delete();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });

                } catch (Exception e) {
                    debug(e.getMessage());
                    e.printStackTrace();
                    ctx.reply("An error has occurred.");
                }
            }
        });
        handler.registerCommand(new Command("resinfo") {
            {
                help = "Check the amount of resources in the core.";
                usage = "[team]";
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

                ctx.sendMessage(eb);
            }
        });

        handler.registerCommand(new Command("help") {
            {
                help = "Display all available commands and their usage.";
                usage = "[command]";
                aliases.add("h");
            }

            public void run(Context ctx) {
                if (ctx.args.length == 1) {
                    StringBuilder publicCommands = new StringBuilder();
                    StringBuilder management = new StringBuilder();
                    StringBuilder moderation = new StringBuilder();
                    StringBuilder mapReviewer = new StringBuilder();

                    ArrayList<Command> commandList = new ArrayList<>(handler.getAllCommands());
                    Collections.shuffle(commandList);

                    for (Command command : commandList) {
                        if (command.hidden) continue;
                        if (!command.hasPermission(ctx)) continue;
                        if (!Objects.equals(command.category, "public")) {
                            if (ctx.channel.getId() != Long.parseLong(staff_bot_channel_id)
                                    && ctx.channel.getId() != Long.parseLong(admin_bot_channel_id)) {
                                if (ctx.channel.getId() == Long.parseLong(apprentice_bot_channel_id) && !command.apprenticeCommand) {
                                    continue;
                                } else if (ctx.channel.getId() != Long.parseLong(apprentice_bot_channel_id)) {
                                    continue;
                                }
                            }
                        }
                        switch (command.category) {
                            case Categories.moderation -> {
                                moderation.append("**").append(command.name).append("** ");
//                                if (!command.usage.equals("")) {
//                                    moderation.append(command.usage);
//                                }
                                moderation.append("\n");
                            }
                            case Categories.management -> {
                                management.append("**").append(command.name).append("** ");
//                                if (!command.usage.equals("")) {
//                                    management.append(command.usage);
//                                }
                                management.append("\n");
                            }
                            case Categories.mapReviewer -> {
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
                    if (Objects.equals(ctx.args[1], "aliases")) {
                        embed.setTitle("All aliases");
                        StringBuilder aliases = new StringBuilder();
                        for (String alias : handler.aliasRegistry.keySet()) {
                            aliases.append(alias).append(" -> ").append(handler.aliasRegistry.get(alias).name).append("\n");
                        }
                        embed.setDescription(aliases.toString());
                        ctx.channel.sendMessage(embed);
                        return;
                    }
                    Command command = handler.registry.getOrDefault(ctx.args[1].toLowerCase(), handler.aliasRegistry.get(ctx.args[1].toLowerCase()));
                    if (command == null) {
                        embed.setColor(new Color(0xff0000))
                                .setTitle("Error")
                                .setDescription("Couldn't find this command!");
                        ctx.channel.sendMessage(embed);
                        return;
                    }
                    embed.setTitle(command.name)
                            .setDescription(command.help);
                    if (!command.usage.equals("")) {
                        embed.addField("Usage:", ioMain.prefix + command.name + " " + command.usage);
                    }
                    embed.addField("Category:", command.category);
                    StringBuilder aliases = new StringBuilder();
                    if (command.aliases.size() > 0) {
                        for (String alias : command.aliases) {
                            aliases.append(alias).append(", ");
                        }
                        embed.addField("Aliases:", aliases.toString());
                    }
                    ctx.channel.sendMessage(embed);
                }
            }
        });


        handler.registerCommand(new Command("redeem") {
            {
                help = "Sync in-game and Discord rank. [NOTE: Abusing this power and giving it to other players will result in a ban.]";
                usage = "<name|id>";
                minArguments = 1;
            }

            public void run(Context ctx) {
                EmbedBuilder eb = new EmbedBuilder();
                String target = "";
                if (ctx.args.length > 1) {
                    target = ctx.args[1];
                }
                debug(target);
                if (target.length() > 0) {
                    int rank = 0;
                    for (Role role : ctx.author.asUser().get().getRoles(ctx.event.getServer().get())) {
                        if (rankRoles.containsKey(role.getIdAsString())) {
                            if (rankRoles.get(role.getIdAsString()) > rank) {
                                rank = rankRoles.get(role.getIdAsString());
                            }
                        }
                    }
                    Player player = findPlayer(target);
                    if (player != null) {
                        PlayerData pd = getData(player.uuid());
                        if (pd != null) {
                            if (pd.rank < rank) {
                                pd.rank = rank;
                            } else {
                                // verify the action
                                PersistentPlayerData tdata = (playerDataGroup.getOrDefault(player.uuid(), null));
                                EmbedBuilder eb2 = new EmbedBuilder()
                                        .setTitle("Please head over to Mindustry to complete the process!");
                                ctx.channel.sendMessage(eb2);
                                tdata.task = Timer.schedule(() -> {
                                    eb.setTitle("Command terminated");
                                    eb.setDescription("Player did not accept the action!");
                                    eb.setColor(new Color(0xff0000));
                                    ctx.sendMessage(eb);
                                    tdata.redeemKey = -1;
                                }, 120);
                                tdata.redeem = ctx.author.getIdAsString();
                                int key = (int) (Math.random() * 100);
                                key += Math.random() * 1000;
                                debug(key);
                                tdata.redeemKey = key;
                                player.sendMessage("Please enter this code: [cyan]/redeem " + key + " [white]to complete the redeem process.");
                                return;
                            }
                            setData(player.uuid(), pd);
                        }
                        eb.setTitle("Command executed successfully");
                        eb.setDescription("Promoted " + escapeEverything(player.name) + " to " + escapeEverything(rankNames.get(rank).name) + ".");
                        player.name = rankNames.get(rank).tag + getPlayerInfo(player.uuid()).lastName;
                        //                            player.con.kick("Your rank was modified, please rejoin.", 0);
                    } else {
                        eb.setTitle("Command terminated");
                        eb.setDescription("Player not online or not found.");
                    }
                } else {
                    eb.setTitle("Command terminated");
                    eb.setDescription("Invalid arguments provided or no roles to redeem.");
                }
                ctx.sendMessage(eb);
            }

        });

//        TextChannel warningsChannel = null;
//        if (ioMain.data.has("warnings_chat_channel_id")) {
//            warningsChannel = getTextChannel(ioMain.data.getString("warnings_chat_channel_id"));
//        }
    }
}