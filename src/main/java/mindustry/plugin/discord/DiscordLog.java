package mindustry.plugin.discord;

import arc.struct.StringMap;
import arc.util.Log;
import mindustry.gen.Groups;
import mindustry.net.Administration;
import mindustry.plugin.utils.Utils;
import org.javacord.api.entity.message.MessageAttachment;
import org.javacord.api.entity.message.MessageBuilder;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.user.User;

import java.io.File;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

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
     * Log a cheat
     */
    public static void cheat(String action, User mod, String information) {
        EmbedBuilder eb = new EmbedBuilder()
                .setColor(java.awt.Color.PINK)
                .setTitle(action)
                .addInlineField("Moderator", "<@" + mod.getId() + ">")
                .addInlineField("Players online", StreamSupport.stream(Groups.player.spliterator(), false)
                        .map(p -> Utils.escapeEverything(p.name()) + " `" + p.uuid() + "`")
                        .collect(Collectors.joining("\n")));
        if (information != null) {
            eb.addField("Additional Information", information);
        }
        Channels.LOG.sendMessage(eb);
//        Call.sendMessage(GameMsg.custom("Cheat", "yellow", mod.getDiscriminatedName() + " has used the " + action + " cheat!"));
    }

    public static void moderation(String action, String mod, Administration.PlayerInfo info, String reason, String additionalInfo) {
        EmbedBuilder eb = new EmbedBuilder()
                .setColor(DiscordPalette.WARN)
                .setTitle(action)
                .addField("Moderator", mod)
                .addField("Reason", reason == null || reason.equals("") ? "None" : reason);
        if (info != null) {
            eb.addField("Target UUID", info.id)
                    .addField("Target name", Utils.escapeEverything(info.lastName))
                    .addField("Target IP", info.lastIP);
        }
        if (additionalInfo != null) {
            eb.setDescription(additionalInfo);
        }
        eb.setTimestampToNow();

        Channels.LOG.sendMessage(eb);
    }

    public static void moderationLogColonel(String action, String mod, Administration.PlayerInfo info, String reason, String additionalInfo, List<MessageAttachment> images) {
        EmbedBuilder eb = new EmbedBuilder()
                .setColor(DiscordPalette.WARN)
                .setTitle(action)
                .addField("Moderator", mod)
                .addField("Reason", reason == null || reason.equals("") ? "None" : reason);
        if (info != null) {
            eb.addField("Target hashed UUID", Utils.calculatePhash(info.id))
                    .addField("Target name", Utils.escapeEverything(info.lastName));
        }
        if (additionalInfo != null) {
            eb.setDescription(additionalInfo);
        }
        eb.setTimestampToNow();
        MessageBuilder mb = new MessageBuilder();
        try {
            eb.setImage(images.get(0).downloadAsInputStream());
            for (MessageAttachment i : images) {
                if (images.get(0) == i) continue;
                mb.addAttachment(i.downloadAsInputStream(), "image_" + i.getFileName());
            }
        } catch (Exception e) {
            Log.info("Could not load images.");
            e.printStackTrace();
        }
        mb.addEmbed(eb);
        mb.send(Channels.COLONEL_LOG);
    }

    public static void moderation(String action, User mod, Administration.PlayerInfo info, String reason, String additionalInfo) {
        moderation(action, "<@" + mod.getId() + ">", info, reason, additionalInfo);
    }
}
