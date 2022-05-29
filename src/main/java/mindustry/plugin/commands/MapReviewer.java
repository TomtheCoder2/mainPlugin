package mindustry.plugin.commands;

import arc.Core;
import arc.files.Fi;
import arc.struct.Seq;
import arc.struct.StringMap;
import mindustry.io.SaveIO;
import mindustry.maps.Map;
import mindustry.plugin.discordcommands.Command;
import mindustry.plugin.discordcommands.Context;
import mindustry.plugin.discordcommands.DiscordCommands;
import mindustry.plugin.discordcommands.RoleRestrictedCommand;
import mindustry.plugin.ioMain;
import mindustry.plugin.requests.GetMap;
import mindustry.plugin.utils.Utils;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.MessageAttachment;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.json.JSONObject;

import java.awt.*;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.zip.InflaterInputStream;

import static arc.util.Log.debug;
import static mindustry.Vars.maps;
import static mindustry.plugin.utils.CustomLog.logAction;
import static mindustry.plugin.utils.LogAction.updateMap;
import static mindustry.plugin.utils.LogAction.uploadMap;
import static mindustry.plugin.utils.Utils.Categories.mapReviewer;
import static mindustry.plugin.utils.Utils.*;

public class MapReviewer {
    private final JSONObject data;
    public GetMap map = new GetMap();

    public MapReviewer(JSONObject data) {
        this.data = data;
    }

    public void registerCommands(DiscordCommands handler) {
        if (data.has("mapSubmissions_roleid")) {
            String reviewerRole = data.getString("mapSubmissions_roleid");
            handler.registerCommand(new RoleRestrictedCommand("uploadmap") {
                {
                    help = "Upload a new map (Include a .msav file with command message)";
                    role = reviewerRole;
                    usage = "<.msav attachment>";
                    category = mapReviewer;
                    aliases.add("ul");
                }

                public void run(Context ctx) {
                    EmbedBuilder eb = new EmbedBuilder();
                    Seq<MessageAttachment> ml = new Seq<>();
                    for (MessageAttachment ma : ctx.event.getMessageAttachments()) {
                        String[] splitMessage = ma.getFileName().split("\\.");
                        if (splitMessage[splitMessage.length - 1].trim().equals("msav")) {
                            ml.add(ma);
                        }
                    }
                    if (ml.size != 1) {
                        eb.setTitle("Map upload terminated.");
                        eb.setColor(Utils.Pals.error);
                        eb.setDescription("You need to add one valid .msav file!");
                        ctx.sendMessage(eb);
                        return;
                    }
                    for (MessageAttachment ma : ml) {
                        boolean updated = false;
                        if (Core.settings.getDataDirectory().child("maps").child(ma.getFileName()).exists()) {
                            // update the map
//                            Core.settings.getDataDirectory().child("maps").child(ma.getFileName()).delete();
                            updated = true;
//                        eb.setTitle("Map upload terminated.");
//                        eb.setColor(Pals.error);
//                        eb.setDescription("There is already a map with this name on the server!");
//                        ctx.sendMessage(eb);
//                        return;
                        }
                        // more custom filename checks possible

                        CompletableFuture<byte[]> cf = ma.downloadAsByteArray();
                        Fi fh = Core.settings.getDataDirectory().child("maps").child(ma.getFileName());

                        try {
                            byte[] data = cf.get();
                            if (!SaveIO.isSaveValid(new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(data))))) {
                                eb.setTitle("Map upload terminated.");
                                eb.setColor(Utils.Pals.error);
                                eb.setDescription("Map file `" + fh.name() + "` corrupted or invalid.");
                                ctx.sendMessage(eb);
                                continue;
                            }
                            if (updated)
                                Core.settings.getDataDirectory().child("maps").child(ma.getFileName()).delete();
                            fh.writeBytes(cf.get(), false);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        maps.reload();
                        eb.setTitle("Map upload completed.");
                        if (updated)
                            eb.setDescription("Successfully updated " + ma.getFileName());
                        else
                            eb.setDescription(ma.getFileName() + " was added successfully into the playlist!");
                        ctx.sendMessage(eb);
                        logAction((updated ? updateMap : uploadMap), null, ctx, null, ma);
                    }
                }
            });

            handler.registerCommand(new RoleRestrictedCommand("removemap") {
                {
                    help = "Remove a map from the playlist (use mapname/mapid retrieved from the %maps command)".replace("%", ioMain.prefix);
                    role = reviewerRole;
                    usage = "<mapname/mapid>";
                    category = mapReviewer;
                    minArguments = 1;
                    aliases.add("rm");
                }

                @Override
                public void run(Context ctx) {
                    EmbedBuilder eb = new EmbedBuilder();
                    if (ctx.args.length < 2) {
                        eb.setTitle("Command terminated.");
                        eb.setColor(Utils.Pals.error);
                        eb.setDescription("Not enough arguments, use `%removemap <mapname/mapid>`".replace("%", ioMain.prefix));
                        ctx.sendMessage(eb);
                        return;
                    }
                    Map found = getMapBySelector(ctx.message.trim());
                    if (found == null) {
                        eb.setTitle("Command terminated.");
                        eb.setColor(Utils.Pals.error);
                        eb.setDescription("Map not found");
                        ctx.sendMessage(eb);
                        return;
                    }

                    maps.removeMap(found);
                    maps.reload();

                    eb.setTitle("Command executed.");
                    eb.setDescription(found.name() + " was successfully removed from the playlist.");
                    ctx.sendMessage(eb);
                }
            });

            if (data.has("mapSubmissions_id")) {
                TextChannel tc = getTextChannel(ioMain.data.getString("mapSubmissions_id"));
                handler.registerCommand(new Command("submitmap") {
                    {
                        help = " Submit a new map to be added into the server playlist in a .msav file format.";
                        usage = "<.msav attachment>";
                    }

                    public void run(Context ctx) {
                        EmbedBuilder eb = new EmbedBuilder();
                        Seq<MessageAttachment> ml = new Seq<>();
                        for (MessageAttachment ma : ctx.event.getMessageAttachments()) {
                            if (ma.getFileName().split("\\.", 2)[1].trim().equals("msav")) {
                                ml.add(ma);
                            }
                        }
                        if (ml.size != 1) {
                            eb.setTitle("Map upload terminated.");
                            eb.setColor(Utils.Pals.error);
                            eb.setDescription("You need to add one valid .msav file!");
                            ctx.sendMessage(eb);
                            return;
                        } else if (Core.settings.getDataDirectory().child("maps").child(ml.get(0).getFileName()).exists()) {
                            eb.setTitle("Map upload terminated.");
                            eb.setColor(Utils.Pals.error);
                            eb.setDescription("There is already a map with this name on the server!");
                            ctx.sendMessage(eb);
                            return;
                        }
                        CompletableFuture<byte[]> cf = ml.get(0).downloadAsByteArray();

                        try {
                            byte[] data = cf.get();
                            if (!SaveIO.isSaveValid(new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(data))))) {
                                eb.setTitle("Map upload terminated.");
                                eb.setColor(Utils.Pals.error);
                                eb.setDescription("Map file corrupted or invalid.");
                                ctx.sendMessage(eb);
                                return;
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        eb.setTitle("Map upload completed.");
                        eb.setDescription(ml.get(0).getFileName() + " was successfully queued for review by moderators!");

                        try {
                            InputStream data = ml.get(0).downloadAsInputStream();
                            StringMap meta = getMeta(data);
                            Fi mapFile = new Fi("./temp/map_submit_" + meta.get("name").replaceAll("[^a-zA-Z0-9\\.\\-]", "_") + ".msav");
                            mapFile.writeBytes(cf.get(), false);

                            EmbedBuilder embed = new EmbedBuilder()
                                    .setTitle(escapeEverything(meta.get("name")))
                                    .setDescription(meta.get("description"))
                                    .setAuthor(ctx.author.getName(), ctx.author.getAvatar().getUrl().toString(), ctx.author.getAvatar().getUrl().toString())
                                    .setColor(new Color(0xF8D452))
                                    .setFooter("Size: " + meta.getInt("width") + " X " + meta.getInt("height"));
                            debug("mapFile size: @", mapFile.length());
                            attachMapPng(mapFile, embed, tc);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        assert tc != null;
                        tc.sendMessage("<@&" + reviewerRole + ">");
                        ctx.sendMessage(eb);
                    }
                });
            }
        }
    }
}
