package mindustry.plugin.minimods;

import arc.Core;
import arc.files.Fi;
import arc.struct.StringMap;
import arc.util.CommandHandler;
import arc.util.Strings;
import arc.util.io.CounterInputStream;
import mindustry.Vars;
import mindustry.gen.Player;
import mindustry.io.SaveIO;
import mindustry.io.SaveVersion;
import mindustry.maps.Map;
import mindustry.plugin.MiniMod;
import mindustry.plugin.database.Database;
import mindustry.plugin.discord.Channels;
import mindustry.plugin.discord.DiscordLog;
import mindustry.plugin.discord.DiscordPalette;
import mindustry.plugin.discord.Roles;
import mindustry.plugin.discord.discordcommands.DiscordRegistrar;
import mindustry.plugin.utils.ContentServer;
import mindustry.plugin.utils.GameMsg;
import mindustry.plugin.utils.Query;
import mindustry.plugin.utils.Utils;
import org.javacord.api.entity.message.MessageAttachment;
import org.javacord.api.entity.message.MessageBuilder;
import org.javacord.api.entity.message.embed.EmbedBuilder;

import java.io.ByteArrayInputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.InflaterInputStream;

import static mindustry.plugin.utils.Utils.escapeEverything;

/**
 * Implements commands relating to map management
 */
public class Maps implements MiniMod {
    @Override
    public void registerDiscordCommands(DiscordRegistrar handler) {
        handler.register("addmap", "",
                data -> {
                    data.usage = "<.msav attachment>";
                    data.help = "Upload a map (attach an msav file)";
                    data.roles = new long[]{Roles.MAP_SUBMISSIONS};
                    data.aliases = new String[]{"uploadmap", "updatemap"};
                    data.category = "Maps";
                },
                ctx -> {
                    if (ctx.event.getMessageAttachments().size() != 1) {
                        ctx.error("Wrong Number of Attachments", "Please provide one and only one attachment");
                        return;
                    }

                    MessageAttachment attachment = ctx.event.getMessageAttachments().get(0);
                    byte[] data = attachment.downloadAsByteArray().join();
                    if (!SaveIO.isSaveValid(new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(data))))) {
                        ctx.error("Invalid Save File", "`" + attachment.getFileName() + "` is corrupted or invalid.");
                        return;
                    }

                    Fi file = Core.settings.getDataDirectory().child("maps").child(attachment.getFileName());
                    boolean didExist = file.exists();
                    if (didExist) file.delete();
                    file.writeBytes(data);

                    Vars.maps.reload();

                    ctx.success((didExist ? "Updated" : "Uploaded") + " New Map", "Filename: `" + attachment.getFileName() + "`");
                }
        );

        handler.register("submitmap", "",
                data -> {
                    data.usage = "<.msav attachment>";
                    data.help = "Submit a map to be added to the playlist";
                    data.category = "Maps";
                },
                ctx -> {
                    if (ctx.event.getMessageAttachments().size() != 1) {
                        ctx.error("Wrong Number of Attachments", "Please provide one and only one attachment");
                        return;
                    }

                    MessageAttachment attachment = ctx.event.getMessageAttachments().get(0);
                    byte[] data = attachment.downloadAsByteArray().join();
                    if (!SaveIO.isSaveValid(new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(data))))) {
                        ctx.error("Invalid Save File", "`" + attachment.getFileName() + "` is corrupted or invalid.");
                        return;
                    }

                    StringMap meta = null;
                    try {
                        CounterInputStream counter = new CounterInputStream(new InflaterInputStream(new ByteArrayInputStream(data)));
                        DataInput stream = new DataInputStream(counter);
                        SaveIO.readHeader(stream);

                        int version = stream.readInt();
                        SaveVersion ver = SaveIO.getSaveWriter(version);
                        StringMap[] metaOut = {null};
                        ver.region("meta", stream, counter, in -> metaOut[0] = ver.readStringMap(in));
                        meta = metaOut[0];
                    } catch (IOException e) {
                        e.printStackTrace();
                        DiscordLog.error("Error Submitting Map", "```\n" + e.getMessage() + "\n```", null);
                        ctx.error("Internal Error", "Try uploading again.");
                        return;
                    }

                    EmbedBuilder eb = new EmbedBuilder()
                        .setTitle(escapeEverything(meta.get("name")))
                        .setDescription(meta.get("description"))
                        .setAuthor(ctx.author().getDisplayName(ctx.server()), ctx.author().getAvatar().getUrl().toString(), ctx.author().getAvatar().getUrl().toString())
                        .setColor(DiscordPalette.WARN)
                        .setImage(ContentServer.renderRaw(data))
                        .setFooter("Size: " + meta.getInt("width") + "x" + meta.getInt("height"));
                    new MessageBuilder()
                        .addEmbed(eb)
                        .addAttachment(data, "Map " + Strings.stripColors(meta.get("name")) + ".msav")
                        .send(Channels.MAP_SUBMISSIONS)
                        .join();

                    ctx.success("Submitted map", "Map " + Strings.stripColors(meta.get("name")) + " queued for review by map reviewers in <#" + Channels.MAP_SUBMISSIONS.getId() + ">!");
                }
        );

        handler.register("removemap", "<map...>",
                data -> {
                    data.help = "Remove a map from the playlist";
                    data.roles = new long[]{Roles.MAP_SUBMISSIONS};
                    data.category = "Maps";
                    data.aliases = new String[]{"rm"};
                },
                ctx -> {
                    Map found = Query.findMap(ctx.args.get("map"));
                    if (found == null) {
                        ctx.error("Map not found", "Map '" + ctx.args.get("map") + "' does not exist");
                    }

                    String name = found.name();
                    Vars.maps.removeMap(found);
                    Vars.maps.reload();

                    ctx.success("Removed Map", "Map '" + escapeEverything(name) + "' was removed from the playlist");
                }
        );

        handler.register("maps", "",
                data -> {
                    data.help = "List maps in the playlist";
                    data.category = "Maps";
                },
                ctx -> {
                    EmbedBuilder eb = new EmbedBuilder();
                    eb.setTitle("Maps");
                    eb.setColor(DiscordPalette.INFO);
                    for (Map map : Vars.maps.customMaps()) {
                        eb.addInlineField(Strings.stripColors(map.name()), map.rules().mode().name() + "\n" + map.width + "x" + map.height);
                    }
                    ctx.sendEmbed(eb);

                    eb = new EmbedBuilder().setTitle("Ranking").setColor(DiscordPalette.INFO);
                    StringBuilder sb = new StringBuilder();
                    sb.append(String.format("%-2s | %-4s | %-4s | %-5s\n", "#", "+", "-", "%"));
                    var maps = Database.rankMapRatings(100, 0);
                    int c = 1;
                    for (Database.Map m : maps) {
                        if (Query.findMap(m.name) == null) continue;
                        sb.append(String.format("%-2d | %-4d | %-4d | %-5.1f%% %s\n", c, m.positiveRating, m.negativeRating, m.positiveRating / (double)(m.positiveRating + m.negativeRating) * 100, Strings.stripColors(m.name)));
                        c++;
                    }
                    eb.setDescription("```\n" + sb + "\n```");
                    ctx.sendEmbed(eb);
                }
        );

        handler.register("map", "<map...>",
                data -> {
                    data.help = "View information about a given map";
                    data.category = "Maps";
                },
                ctx -> {
                    Map found = Query.findMap(ctx.args.get("map"));
                    if (found == null) {
                        ctx.error("No such map", "Map '" + ctx.args.get("map") + " does not exist in the playlist");
                        return;
                    }

                    EmbedBuilder eb = new EmbedBuilder()
                            .setColor(DiscordPalette.INFO)
                            .setTitle(found.name())
                            .setDescription(found.description())
                            .setAuthor(found.author())
                            .addField("Size", found.width + "x" + found.height);

                    Database.Map md = Database.getMapData(found.name());
                    if (md != null) {
                        eb.addInlineField("Positive Votes", md.positiveRating + "")
                                .addInlineField("Negative Votes", md.negativeRating + "")
                                .addField("Highscore Waves", md.highscoreWaves + "")
                                .addInlineField("Longest Game", md.highscoreTime + " min")
                                .addInlineField("Shortest Game", md.shortestGame + " min")
                                .addInlineField("Total Play Time", md.playTime + " min");
                    }

                    eb.setImage(ContentServer.renderMap(found));

                    ctx.reply(
                            new MessageBuilder()
                                    .addEmbed(eb)
                                    .addAttachment(found.file.file())
                    );
                }
        );
    }

    @Override
    public void registerCommands(CommandHandler handler) {
        handler.<Player>register("maps", "List all maps", (args, player) -> {
            StringBuilder msg = new StringBuilder();
            msg.append(GameMsg.custom("Maps", "white", "List of maps:\n"));
            for (Map map : Vars.maps.customMaps()) {
                msg.append("[white] - [sky]" + map.name() + " [accent]" + map.width + "[white]x[accent]" + map.height + " [lightgray]" + map.rules().mode().name());
            }

            player.sendMessage(msg.toString());
        });
    }
}
