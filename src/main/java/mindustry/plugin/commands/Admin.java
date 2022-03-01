package mindustry.plugin.commands;

import arc.Core;
import arc.files.Fi;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Timer;
import mindustry.core.GameState;
import mindustry.game.Gamemode;
import mindustry.gen.Call;
import mindustry.maps.Map;
import mindustry.maps.MapException;
import mindustry.mod.Mods;
import mindustry.net.Administration;
import mindustry.plugin.discordcommands.Context;
import mindustry.plugin.discordcommands.DiscordCommands;
import mindustry.plugin.discordcommands.RoleRestrictedCommand;
import mindustry.plugin.requests.GetMap;
import mindustry.plugin.utils.Utils;
import org.javacord.api.entity.message.MessageAttachment;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.json.JSONObject;

import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;

import static arc.util.Log.*;
import static mindustry.Vars.*;
import static mindustry.plugin.ioMain.*;
import static mindustry.plugin.utils.Utils.Categories.*;
import static mindustry.plugin.utils.Utils.*;

public class Admin {
    private final JSONObject data;
    public GetMap map = new GetMap();

    public Admin(JSONObject data) {
        this.data = data;
    }

    public void registerCommands(DiscordCommands handler) {
        if (data.has("administrator_roleid")) {
            String adminRole = data.getString("administrator_roleid");
            // TODO: make an update command to update the EI mod

            handler.registerCommand(new RoleRestrictedCommand("config") {
                {
                    help = "Configure server settings.";
                    usage = "[name] [value...]";
                    role = adminRole;
                }

                @Override
                public void run(Context ctx) {
                    EmbedBuilder eb = new EmbedBuilder();
                    if (ctx.args.length == 1) {
//                        info("All config values:");
                        eb.setTitle("All config values:");
                        for (Administration.Config c : Administration.Config.all) {
//                            info("&lk| @: @", c.name(), "&lc&fi" + c.get());
//                            info("&lk| | &lw" + c.description);
//                            info("&lk|");
                            eb.addField(escapeEverything(c.name()) + ": " + c.get(), c.description, true);
                        }
                        ctx.sendMessage(eb);
                        return;
                    }

                    try {
                        Administration.Config c = Administration.Config.valueOf(ctx.args[1]);
                        if (ctx.args.length == 2) {
//                            info("'@' is currently @.", c.name(), c.get());
                            eb.setTitle("'" + c.name() + "' is currently " + c.get() + ".");
                            ctx.sendMessage(eb);
                        } else {
                            if (c.isBool()) {
                                c.set(ctx.args[2].equals("on") || ctx.args[2].equals("true"));
                            } else if (c.isNum()) {
                                try {
                                    c.set(Integer.parseInt(ctx.args[2]));
                                } catch (NumberFormatException e) {
//                                    err("Not a valid number: @", ctx.args[2]);
                                    eb.setTitle("Not a valid Number: " + ctx.args[2]).setColor(new Color(0xff0000));
                                    ctx.sendMessage(eb);
                                    return;
                                }
                            } else if (c.isString()) {
                                c.set(ctx.args[2].replace("\\n", "\n"));
                            }

//                            info("@ set to @.", c.name(), c.get());
                            eb.setTitle(c.name() + " set to " + c.get() + ".");
                            ctx.sendMessage(eb);
                            Core.settings.forceSave();
                        }
                    } catch (IllegalArgumentException e) {
//                        err("Unknown config: '@'. Run the command with no arguments to get a list of valid configs.", ctx.args[1]);
                        eb.setTitle("Error")
                                .setDescription("Unknown config: '" + ctx.args[1] + "'. Run the command with no arguments to get a list of valid configs.")
                                .setColor(new Color(0xff0000));
                        ctx.sendMessage(eb);
                    }
                }
            });

            handler.registerCommand(new RoleRestrictedCommand("uploadmod") {
                {
                    help = "Upload a new mod (Include a .zip file with command message)";
                    role = adminRole;
                    usage = "<.zip attachment>";
                    category = management;
                    aliases.add("umod");
                }

                public void run(Context ctx) {
                    EmbedBuilder eb = new EmbedBuilder();
                    Seq<MessageAttachment> ml = new Seq<>();
                    for (MessageAttachment ma : ctx.event.getMessageAttachments()) {
                        if (ma.getFileName().split("\\.")[ma.getFileName().split("\\.").length - 1].trim().equals("zip")) {
                            ml.add(ma);
                        }
                    }
                    if (ml.size != 1) {
                        eb.setTitle("Mod upload terminated.");
                        eb.setColor(Utils.Pals.error);
                        eb.setDescription("You need to add one valid .zip file!");
                        ctx.sendMessage(eb);
                        return;
                    } else if (Core.settings.getDataDirectory().child("mods").child(ml.get(0).getFileName()).exists()) {
                        eb.setTitle("Mod upload terminated.");
                        eb.setColor(Utils.Pals.error);
                        eb.setDescription("There is already a mod with this name on the server!");
                        ctx.sendMessage(eb);
                        return;
                    }
                    // more custom filename checks possible

                    CompletableFuture<byte[]> cf = ml.get(0).downloadAsByteArray();
                    Fi fh = Core.settings.getDataDirectory().child("mods").child(ml.get(0).getFileName());

                    try {
                        byte[] data = cf.get();
//                        if (!SaveIO.isSaveValid(new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(data))))) {
//                            eb.setTitle("Mod upload terminated.");
//                            eb.setColor(Pals.error);
//                            eb.setDescription("Mod file corrupted or invalid.");
//                            ctx.sendMessage(eb);
//                            return;
//                        }
                        fh.writeBytes(cf.get(), false);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    maps.reload();
                    eb.setTitle("Mod upload completed.");
                    eb.setDescription(ml.get(0).getFileName() + " was added successfully into the mod folder!\n" +
                            "Restart the server (`<restart <server>`) to activate the mod!");
                    ctx.sendMessage(eb);
//                    Utils.LogAction("uploadmap", "Uploaded a new map", ctx.author, null);
                }
            });

            handler.registerCommand(new RoleRestrictedCommand("removemod") {
                {
                    help = "Remove a mod from the folder";
                    role = adminRole;
                    usage = "<mapname/mapid>";
                    category = mapReviewer;
                    minArguments = 1;
                    aliases.add("rmod");
                }

                @Override
                public void run(Context ctx) {
                    EmbedBuilder eb = new EmbedBuilder();
                    debug(ctx.message);
                    Mods.LoadedMod mod = getMod(ctx.message);
                    if (mod == null) {
                        eb.setTitle("Command terminated.");
                        eb.setColor(Utils.Pals.error);
                        eb.setDescription("Mod not found");
                        ctx.sendMessage(eb);
                        return;
                    }
                    debug(mod.file.file().getAbsoluteFile().getAbsolutePath());
                    Path path = Paths.get(mod.file.file().getAbsoluteFile().getAbsolutePath());
                    mod.dispose();

                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        e.printStackTrace();
                        eb.setTitle("There was an error performing this command.")
                                .setDescription(e.getMessage())
                                .setColor(new Color(0xff0000));
                        return;
                    }
                    if (mod.file.file().getAbsoluteFile().delete()) {
                        eb.setTitle("Command executed.");
                        eb.setDescription(mod.name + " was successfully removed from the folder.\nRestart the server to disable the mod (`<restart <server>`).");
                    } else {
                        eb.setTitle("There was an error performing this command.")
                                .setColor(new Color(0xff0000));
                    }
                    ctx.sendMessage(eb);
                }
            });


            handler.registerCommand(new RoleRestrictedCommand("enableJs") {
                {
                    help = "Enable/Disable js command for everyone.";
                    role = adminRole;
                    category = moderation;
                    usage = "<true|false>";
                }

                @Override
                public void run(Context ctx) {
                    EmbedBuilder eb = new EmbedBuilder();
                    switch (ctx.args[1]) {
                        case "true", "t" -> {
                            enableJs = true;
                            if (enableJsTask != null) {
                                enableJsTask.cancel();
                            }
                            enableJsTask = Timer.schedule(() -> {
                                enableJs = false;
                                Call.sendMessage("[accent]js command disabled for everyone!");
                            }, 10 * 60);
                            Call.sendMessage("[accent]Marshal " + ctx.author.getName() + "[accent] enabled the js command for everyone! Do [cyan]/js <script...>[accent] to use it.");
                        }
                        case "false", "f" -> {
                            enableJs = false;
                            Call.sendMessage("[accent]js command disabled for everyone!");
                        }
                        default -> {
                            eb.setTitle("Error")
                                    .setColor(new Color(0xff0000))
                                    .setDescription("[scarlet]Second argument has to be true or false.");
                            ctx.sendMessage(eb);
                            return;
                        }
                    }
                    eb.setTitle((enableJs ? "Enabled" : "Disabled") + " js")
                            .setDescription((enableJs ? "Enabled" : "Disabled") + " js for everyone" + (enableJs ? " for 10 minutes." : "."))
                            .setColor(new Color((enableJs ? 0x00ff00 : 0xff0000)));
                    ctx.sendMessage(eb);
                }
            });

            handler.registerCommand(new RoleRestrictedCommand("start") {
                {
                    help = "Restart the server. Will default to survival and a random map if not specified.";
                    role = adminRole;
                    category = management;
                    usage = "[mapname] [mode]";
                }

                public void run(Context ctx) {
                    net.closeServer();
                    state.set(GameState.State.menu);

                    // start the server again
                    EmbedBuilder eb = new EmbedBuilder();
                    Gamemode preset = Gamemode.survival;

                    if (ctx.args.length > 2) {
                        try {
                            preset = Gamemode.valueOf(ctx.args[2]);
                        } catch (IllegalArgumentException e) {
                            err("No gamemode '@' found.", ctx.args[2]);
                            eb.setTitle("Command terminated.");
                            eb.setColor(Utils.Pals.error);
                            eb.setDescription("No gamemode " + ctx.args[2] + " found.");
                            ctx.sendMessage(eb);
                            return;
                        }
                    }

                    Map result;
                    if (ctx.args.length > 1) {
                        result = getMapBySelector(ctx.args[1]);
                        if (result == null) {
                            eb.setTitle("Command terminated.");
                            eb.setColor(Pals.error);
                            eb.setDescription("Map \"" + escapeCharacters(ctx.args[1]) + "\" not found!");
                            ctx.sendMessage(eb);
                            return;
                        }
                    } else {
                        result = maps.getShuffleMode().next(preset, state.map);
                        info("Randomized next map to be @.", result.name());
                    }

                    info("Loading map...");

                    logic.reset();
//                    lastMode = preset;
//                    Core.settings.put("lastServerMode", lastMode.name());
                    try {
                        world.loadMap(result, result.applyRules(preset));
                        state.rules = result.applyRules(preset);
                        logic.play();

                        info("Map loaded.");
                        eb.setTitle("Map loaded!");
                        eb.setColor(Pals.success);
                        eb.setDescription("Hosting map: " + result.name());
                        ctx.sendMessage(eb);

                        netServer.openServer();
                    } catch (MapException e) {
                        Log.err(e.map.name() + ": " + e.getMessage());
                        eb.setTitle("Command terminated.");
                        eb.setColor(Pals.error);
                        eb.setDescription("e.map.name() + \": \" + e.getMessage()");
                        ctx.sendMessage(eb);
                        return;
                    }
                }
            });
        }
        if (data.has("exit_roleid")) {
            handler.registerCommand(new RoleRestrictedCommand("exit") {
                {
                    help = "Close the server.";
                    role = data.getString("exit_roleid");
                    category = management;
                }

                public void run(Context ctx) {
                    getTextChannel(log_channel_id).sendMessage(new EmbedBuilder()
                            .setTitle(ctx.author.getDisplayName() + " closed the server!")
                            .setColor(new Color(0xff0000))).join();

                    ctx.channel.sendMessage(new EmbedBuilder()
                            .setTitle("Closed the server!")
                            .setColor(new Color(0xff0000))).join();
                    net.dispose();
                    Core.app.exit();
                    System.exit(1);
                }
            });
        }
    }
}
