package mindustry.plugin.discord;

import arc.struct.StringMap;
import mindustry.net.Administration;
import mindustry.plugin.database.Database;
import mindustry.plugin.discord.discordcommands.Context;
import mindustry.plugin.utils.LogAction;
import mindustry.plugin.utils.Rank;
import mindustry.plugin.utils.Utils;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.MessageAttachment;
import org.javacord.api.entity.message.embed.EmbedBuilder;

import java.awt.*;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public class DiscordLog {
    /**
     * Log an error.
     *
     * @param fields List of fields. May be null.
     */
    public static void error(String title, String description, StringMap fields) {
        EmbedBuilder eb = new EmbedBuilder()
                .setTitle(title)
                .setDescription(description)
                .setColor(DiscordPalette.ERROR)
                .setTimestamp(Instant.now());

        if (fields != null)
            for (var entry : fields) {
                eb.addField(entry.key, entry.value);
            }
        Channels.ERROR_LOG.sendMessage(eb);
    }

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
                    Administration.PlayerInfo info = Utils.getPlayerInfo(uuid);
                    desc.append(String.format("`%s` : `%s `:%s\n", uuid, info.lastIP, Utils.escapeEverything(info.lastName)));
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
        EmbedBuilder eb = new EmbedBuilder();
        final String reasonNotNull = Objects.equals(reason, "") || reason == null ? "Not provided" : reason;
        switch (action) {
            case ban, unban, blacklist, kick -> {
                eb.setTitle(action.getName() + " " + Utils.escapeEverything(info.lastName))
                        .addField("UUID", info.id, true)
                        .addField("IP", info.lastIP, true)
                        .addField(action + " by", "<@" + ctx.author().getIdAsString() + ">", true)
                        .addField("Reason", reasonNotNull, true);
            }
            case ipBan, ipUnban -> {
                eb.setTitle(action.getName() + " " + ip)
                        .addField("IP", ip, true)
                        .addField(action + " by", "<@" + ctx.author().getIdAsString() + ">", true)
                        .addField("Reason", reasonNotNull, true);
            }
            case uploadMap, updateMap -> {
                eb.setTitle(action.getName() + " " + Utils.escapeEverything(mapFile.getFileName()))
                        .addField("Uploaded by ", "<@" + ctx.author().getIdAsString() + ">", true);
            }
            case setRank -> {
                Database.Player pd = Database.getPlayerData(info.id);
                eb.setTitle(Utils.escapeEverything(info.lastName) + "'s rank was set to " + Rank.all[pd.rank].name + "!")
                        .setColor(new Color(0x00ff00))
                        .addField("UUID", info.id)
                        .addField("By", "<@" + ctx.author().getIdAsString() + ">");
            }
        }
        eb.setTimestampToNow();
        Channels.LOG.sendMessage(eb);
    }
}
