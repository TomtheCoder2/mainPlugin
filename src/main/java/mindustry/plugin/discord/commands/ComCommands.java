//package mindustry.plugin.discord.commands;
//
//import arc.Core;
//import arc.files.Fi;
//import arc.struct.LongSeq;
//import arc.util.Timer;
//import mindustry.Vars;
//import mindustry.content.Items;
//import mindustry.core.GameState;
//import mindustry.game.Team;
//import mindustry.game.Teams.TeamData;
//import mindustry.gen.Call;
//import mindustry.gen.Groups;
//import mindustry.gen.Player;
//import mindustry.io.SaveIO;
//import mindustry.maps.Map;
//import mindustry.mod.Mods;
//import mindustry.plugin.data.PersistentPlayerData;
//import mindustry.plugin.database.Database;
//import mindustry.plugin.discord.discordcommands.Command;
//import mindustry.plugin.discord.discordcommands.Context;
//import mindustry.plugin.discord.discordcommands.DiscordCommands;
//import mindustry.plugin.ioMain;
//import mindustry.plugin.requests.GetMap;
//import mindustry.plugin.utils.Rank;
//import mindustry.world.modules.ItemModule;
//import org.javacord.api.entity.message.MessageBuilder;
//import org.javacord.api.entity.message.embed.EmbedBuilder;
//import org.javacord.api.entity.permission.Role;
//
//import java.awt.*;
//import java.lang.reflect.Field;
//import java.util.ArrayList;
//import java.util.Arrays;
//import java.util.Collections;
//import java.util.Locale;
//import java.util.Objects;
//
//import static arc.util.Log.debug;
//import static arc.util.Log.info;
//import static mindustry.Vars.saveExtension;
//import static mindustry.Vars.state;
//import static mindustry.plugin.ioMain.*;
//import static mindustry.plugin.utils.Utils.*;
//
//public class ComCommands {
////    public static ContentHandler contentHandler = new ContentHandler();
//
//    GetMap map = new GetMap();
//
//    public void registerCommands(DiscordCommands handler) {
//        handler.registerCommand(new Command("chat") {
//            {
//                help = "Sends a message to in-game chat.";
//                usage = "<message>";
//                minArguments = 1;
//                hidden = true;
//            }
//
//            public void run(Context ctx) {
//                if (ctx.event.isPrivateMessage()) return;
//
//                EmbedBuilder eb = new EmbedBuilder();
//                ctx.message = escapeCharacters(ctx.message);
//                if (ctx.message.length() < chatMessageMaxSize) {
//                    Call.sendMessage("[sky]" + ctx.author.getName() + " @discord >[] " + ctx.message);
//                    eb.setTitle("Command executed");
//                    eb.setDescription("Your message was sent successfully..");
//                    ctx.sendMessage(eb);
//                } else {
//                    ctx.reply("Message too big.");
//                }
//            }
//        });
//
//        handler.registerCommand(new Command("version") {
//            {
//                help = "Get Versions of the mods";
//                minArguments = 0;
//            }
//
//            public void run(Context ctx) {
//                EmbedBuilder eb = new EmbedBuilder()
//                        .setTitle("Mods")
//                        .setColor(new Color(0x00ff00));
//                if (!Vars.mods.list().isEmpty()) {
//                    info("Mods:");
//                    for (Mods.LoadedMod mod : Vars.mods.list()) {
//                        info("  @ &fi@", mod.meta.displayName(), mod.meta.version);
//                        eb.addField(mod.meta.displayName(), mod.meta.version, true);
//                    }
//                } else {
//                    info("No mods found.");
//                    eb.setDescription("No mods found.");
//                }
//                ctx.sendMessage(eb);
//            }
//        });
//
//
//        handler.registerCommand(new Command("mod") {
//            {
//                help = "Get info about a specific mod.";
//                usage = "<mod>";
//            }
//
//            @Override
//            public void run(Context ctx) {
//                EmbedBuilder eb = new EmbedBuilder();
//                if (ctx.args.length > 1) {
//                    Mods.LoadedMod mod = getMod(ctx.message);
//                    if (mod != null) {
//                        eb.setTitle(mod.meta.displayName())
//                                .addField("Internal Name: ", mod.name)
//                                .addField("Version: ", mod.meta.version)
//                                .addField("Author: ", mod.meta.author)
//                                .addField("Description: ", mod.meta.description);
//                    } else {
//                        eb.setTitle("Error")
//                                .setColor(new Color(0xff0000))
//                                .setDescription("No mod with name " + ctx.args[1] + " found.");
//                    }
//                } else {
//                    eb.setTitle("Mods")
//                            .setColor(new Color(0x00ff00));
//                    if (!Vars.mods.list().isEmpty()) {
//                        for (Mods.LoadedMod mod : Vars.mods.list()) {
//                            eb.addField(mod.meta.displayName(), mod.meta.version, true);
//                        }
//                    } else {
//                        eb.setDescription("No mods found.");
//                    }
//                }
//                ctx.sendMessage(eb);
//            }
//        });
///*
//        handler.registerCommand(new Command("players") {
//            {
//                help = "Check who is online and their ids.";
//                aliases.add("p");
//            }
//
//            public void run(Context ctx) {
//                StringBuilder msg = new StringBuilder("**Players online: " + Groups.player.size() + "**\n```\n");
//                for (Player player : Groups.player) {
//                    msg.append("· ").append(escapeEverything(player)).append(" : ").append(player.id).append("\n");
//                }
//                msg.append("```");
////                ctx.channel.sendMessage(msg.toString());
//                StringBuilder lijst = new StringBuilder();
////                StringBuilder admins = new StringBuilder();
//                EmbedBuilder eb = new EmbedBuilder()
//                        .setTitle("Players: " + Groups.player.size());
////                lijst.append("Players: " + Groups.player.size() + "\n");
////                admins.append(" ");
////                if (Groups.player.count(p -> p.admin) > 0) {
////                    admins.append("online admins: ");// + Vars.playerGroup.all().count(p->p.isAdmin)+"\n");
////                }
//                if (Groups.player.size() == 0) {
//                    lijst.append("No players are currently in the server.");// + Vars.playerGroup.all().count(p->p.isAdmin)+"\n");
//                }
//                for (Player player : Groups.player) {
//                    lijst.append("`* ");
//                    if (player.admin()) {
//                        lijst.append(String.format("%-9s :` ", "admin"));
//                    } else {
//                        lijst.append(String.format("%-9s :` ", player.id));
//                    }
//                    lijst.append(escapeEverything(player.name)).append("\n");
//                }*/
///*
//                new MessageBuilder()
//                        .setEmbed(new EmbedBuilder()
//                                .setTitle("Players online: " + Groups.player.size())
////                                    .setDescription( "Info about the Server: ")
//                                .setDescription(lijst.toString())
////                                .addField("Admins: ", admins+" ")
////                                .addField("Players:", lijst.toString())
//                                .setColor(Color.ORANGE))
//                        .send(ctx.channel);
//            }
//        });*/
///*
//        handler.registerCommand(new Command("translate") {
//            {
//                help = "Translate text and send it in the in game chat";
//                usage = "<language> <text...>";
//            }
//
//            @Override
//            public void run(Context ctx) {
//                ctx.channel.sendMessage(new EmbedBuilder()
//                        .setTitle("Wrong channel!")
//                        .setDescription("Please use the <#" + live_chat_channel_id + "> channel!")
//                        .setColor(new Color(0xff0000)));
//            }
//        });
//
//        handler.registerCommand(new Command("t") {
//            {
//                help = "Translate text and send it in the in game chat";
//                usage = "<language> <text...>";
//                hidden = true;
//            }
//
//            @Override
//            public void run(Context ctx) {
//                ctx.channel.sendMessage(new EmbedBuilder()
//                        .setTitle("Wrong channel!")
//                        .setDescription("Please use the <#" + live_chat_channel_id + "> channel!")
//                        .setColor(new Color(0xff0000)));
//            }
//        });*/
///*
//        handler.registerCommand(new Command("status") {
//            {
//                help = "Get basic server information.";
//                aliases.add("s");
//            }
//
//            public void run(Context ctx) {
//                if (!state.is(GameState.State.playing)) {
//                    EmbedBuilder eb = new EmbedBuilder()
//                            .setTitle("Not hosting, please ping an admin!")
//                            .setColor(Pals.error);
//                    ctx.sendMessage(eb);
//                    return;
//                }
//                try {
//                    EmbedBuilder embed = new EmbedBuilder()
//                            .setTitle(ioMain.serverName)
//                            .addField("Players", String.valueOf(Groups.player.size()), true)
//                            .addField("Map", Vars.state.map.name(), true)
//                            .addField("Wave", String.valueOf(state.wave), true)
//                            .addField("TPS", String.valueOf(Core.graphics.getFramesPerSecond()), true)
//                            .addField("Next wave in", Math.round(state.wavetime / 60) + " seconds.", true);
//                    // preview
////                    Fi mapFile = Core.settings.getDataDirectory().child("../temp/map_" + Vars.state.map.name().replaceAll("[^a-zA-Z0-9\\.\\-]", "_") + saveExtension);
//                    Fi tempDir = new Fi("temp/");
//                    Fi mapFile = tempDir.child("map_" + escapeEverything(Vars.state.map.name()).replaceAll("[^a-zA-Z0-9\\.\\-]", "_") + "." + saveExtension);
//                    Core.app.post(() -> {
//                        try {
//                            SaveIO.write(mapFile);
//                            info("Saved to @", mapFile);
//                            debug(mapFile.absolutePath());
//
//                            attachMapPng(mapFile, embed, ctx);
////                            mapFile.delete();
//                        } catch (Exception e) {
//                            e.printStackTrace();
//                        }
//                    });
//
//                } catch (Exception e) {
//                    debug(e.getMessage());
//                    e.printStackTrace();
//                    ctx.reply("An error has occurred.");
//                }
//            }
//        });
//
//        handler.registerCommand(new Command("resinfo") {
//            {
//                help = "Check the amount of resources in the core.";
//                usage = "[team]";
//            }
//
//            public void run(Context ctx) {
//                if (!state.rules.waves) {
//                    ctx.reply("Only available in survival mode!");
//                    return;
//                }
//                // the normal player team is "sharded"
//                TeamData data = state.teams.get(Team.sharded);
//                if (ctx.args.length == 2) {
//                    try {
//                        Field field = Team.class.getDeclaredField(ctx.args[1]);
//                        data = state.teams.get((Team) field.get(null));
//                    } catch (NoSuchFieldException | IllegalAccessException ignored) {
//                    }
//                }
//                //-- Items are shared between cores
////                CoreEntity core = data.cores.first();
//                ItemModule core = Groups.player.first().core().items;
////                ItemModule items = core.items;
//                EmbedBuilder eb = new EmbedBuilder()
//                        .setTitle("Resources in the core:");
////                items.forEach((item, amount) -> eb.addInlineField(item.name, String.valueOf(amount)));
//
//                eb.addInlineField("copper: ", core.get(Items.copper) + "\n");
//                eb.addInlineField("lead: ", core.get(Items.lead) + "\n");
//                eb.addInlineField("graphite: ", core.get(Items.graphite) + "\n");
//                eb.addInlineField("metaglass: ", core.get(Items.metaglass) + "\n");
//                eb.addInlineField("titanium: ", core.get(Items.titanium) + "\n");
//                eb.addInlineField("thorium: ", core.get(Items.thorium) + "\n");
//                eb.addInlineField("silicon: ", core.get(Items.silicon) + "\n");
//                eb.addInlineField("plastanium: ", core.get(Items.plastanium) + "\n");
//                eb.addInlineField("phase fabric: ", core.get(Items.phaseFabric) + "\n");
//                eb.addInlineField("surge alloy: ", core.get(Items.surgeAlloy) + "\n");
//
//                ctx.sendMessage(eb);
//            }
//        });*/
//
////        TextChannel warningsChannel = null;
////        if (ioMain.data.has("warnings_chat_channel_id")) {
////            warningsChannel = getTextChannel(ioMain.data.getString("warnings_chat_channel_id"));
////        }
//    }
//}