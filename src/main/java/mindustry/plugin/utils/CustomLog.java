package mindustry.plugin.utils;

import mindustry.net.Administration;
import mindustry.plugin.data.PlayerData;
import mindustry.plugin.discordcommands.Context;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.MessageAttachment;
import org.javacord.api.entity.message.embed.EmbedBuilder;

import java.awt.*;
import java.util.List;
import java.util.Objects;

import static arc.util.Log.err;
import static mindustry.plugin.database.Utils.getData;
import static mindustry.plugin.ioMain.log_channel_id;
import static mindustry.plugin.utils.Utils.*;
import static mindustry.plugin.utils.ranks.Utils.rankNames;

public class CustomLog {
    /**
     * log a list of connections in the discord log channel
     *
     * @param connection whether they joined or left
     */
    public static void logConnections(TextChannel log_channel, List<String> leftPlayers, String connection) {
        if (leftPlayers.size() > 0) {
            EmbedBuilder eb = new EmbedBuilder();
            eb.setTitle("Player " + connection + " log");
            StringBuilder desc = new StringBuilder();
            for (String uuid : leftPlayers) {
//                if (player == null) continue;
                try {
                    Administration.PlayerInfo info = getPlayerInfo(uuid);
                    desc.append(String.format("`%s` : `%s `:%s\n", uuid, info.lastIP, escapeEverything(info.lastName)));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            if (Objects.equals(connection, "leave")) {
                eb.setColor(new Color(0xff0000));
            } else {
                eb.setColor(new Color(0x00ff00));
            }
            eb.setDescription(desc.toString());
            assert log_channel != null;
            log_channel.sendMessage(eb);
        }
        leftPlayers.clear();
    }

    public static void logAction(LogAction action, Administration.PlayerInfo info, Context ctx, String reason) {
        logAction(action, info, ctx, reason, null, null);
    }

    public static void logAction(LogAction action, Administration.PlayerInfo info, Context ctx, String reason, MessageAttachment mapFile) {
        logAction(action, info, ctx, reason, mapFile, null);
    }

    public static void logAction(LogAction action, Context ctx, String reason, String ip) {
        logAction(action, null, ctx, reason, null, ip);
    }

    public static void logAction(LogAction action, Administration.PlayerInfo info, Context ctx, String reason, MessageAttachment mapFile, String ip) {
        TextChannel log_channel = getTextChannel(log_channel_id);
        EmbedBuilder eb = new EmbedBuilder();
        final String reasonNotNull = Objects.equals(reason, "") || reason == null ? "Not provided" : reason;
        switch (action) {
            case ban, unban, blacklist, kick -> {
                eb.setTitle(action.getName() + " " + escapeEverything(info.lastName))
                        .addField("UUID", info.id, true)
                        .addField("IP", info.lastIP, true)
                        .addField(action + " by", "<@" + ctx.author.getIdAsString() + ">", true)
                        .addField("Reason", reasonNotNull, true);
            }
            case ipBan, ipUnban -> {
                eb.setTitle(action.getName() + " " + ip)
                        .addField("IP", ip, true)
                        .addField(action + " by", "<@" + ctx.author.getIdAsString() + ">", true)
                        .addField("Reason", reasonNotNull, true);
            }
            case uploadMap -> {
                eb.setTitle(action.getName() + " " + escapeEverything(mapFile.getFileName()))
                        .addField("Uploaded by ", "<@" + ctx.author.getIdAsString() + ">", true);
            }
            case setRank -> {
                PlayerData pd = getData(info.id);
                eb.setTitle(escapeEverything(info.lastName) + "'s rank was set to " + rankNames.get(pd.rank).name + "!")
                        .setColor(new Color(0x00ff00))
                        .addField("UUID", info.id)
                        .addField("By", "<@" + ctx.author.getIdAsString() + ">");
            }
        }
        eb.setTimestampToNow();
        if (log_channel == null) {
            err("No log channel found for logging " + action + " message!");
            return;
        }
        log_channel.sendMessage(eb);
    }
}
