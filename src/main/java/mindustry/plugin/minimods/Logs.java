package mindustry.plugin.minimods;

import arc.Events;
import arc.struct.Seq;
import arc.util.Strings;
import arc.util.Timer;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.gen.Call;
import mindustry.gen.Player;
import mindustry.plugin.MiniMod;
import mindustry.plugin.database.Database;
import mindustry.plugin.discord.Channels;
import mindustry.plugin.discord.DiscordPalette;
import mindustry.plugin.utils.Rank;
import mindustry.plugin.utils.Utils;
import org.javacord.api.entity.message.embed.EmbedBuilder;

import java.util.ArrayList;
import java.util.Arrays;

import static mindustry.plugin.database.Database.bannedWords;
import static mindustry.plugin.database.Database.updateBannedWordsClient;
import static mindustry.plugin.discord.Channels.LIVE_LOG;
import static mindustry.plugin.utils.Utils.*;

/**
 * Logs player actions, such as player join &amp; leave and suspicious activity
 */
public class Logs implements MiniMod {
    public static long live_log_message = 0;
    static StringBuilder whisperLog = new StringBuilder();
    Seq<JoinPlayerInfo> leftPlayers = new Seq<>();
    Seq<JoinPlayerInfo> joinPlayers = new Seq<>();

    public static void logWhisper(Player sender, Player target, String message) {
        var formattedMessage = Strings.format("@: `@` -> @: `@`: `@`\n", escapeEverything(sender.name), calculatePhash(sender.uuid()), escapeEverything(target.name), calculatePhash(target.uuid()), message);
        // check if the whisperLog message is longer 4096  chars
        if (whisperLog.toString().length() + formattedMessage.length() > 4096) {
            // send message in logs
            Channels.LOG.sendMessage(new EmbedBuilder()
                    .setTitle("Whisper Log")
                    .setColor(DiscordPalette.INFO)
                    .setDescription(whisperLog.toString())
            );
            // clear the whisperLog
            whisperLog = new StringBuilder();
        }
        // add the message to the whisperLog
        whisperLog.append(formattedMessage);
    }

    public void registerEvents() {
        Events.on(EventType.PlayerLeave.class, event -> {
            Database.Player pd = Database.getPlayerData(event.player.uuid());
            if (pd != null) {
                Rank rank = Rank.all[pd.rank];
                Call.sendMessage("[#" + rank.color.toString().substring(0, 6) + "]" + rank.name + "[] " + event.player.name + "[accent] left the battlefield!");
            }
            JoinPlayerInfo data = new JoinPlayerInfo();
            data.name = event.player.name;
            data.uuid = event.player.uuid();
            data.id = event.player.id;
            data.ip = event.player.ip();

            leftPlayers.add(data);
        });

        Events.on(EventType.PlayerJoin.class, event -> {
            JoinPlayerInfo data = new JoinPlayerInfo();
            data.name = event.player.name;
            data.uuid = event.player.uuid();
            data.id = event.player.id;
            data.ip = event.player.ip();

            joinPlayers.add(data);
        });

        Timer.schedule(() -> {
            System.gc();
            if (joinPlayers.size == 0 && leftPlayers.size == 0) return;

            EmbedBuilder eb = new EmbedBuilder()
                    .setTitle("Player Join & Leave Log")
                    .setColor(DiscordPalette.INFO);
            EmbedBuilder colonel_eb = new EmbedBuilder()
                    .setTitle("Player Join & Leave Log")
                    .setColor(DiscordPalette.INFO);

            if (joinPlayers.size != 0) {
                StringBuilder sb = new StringBuilder();
                for (JoinPlayerInfo player : joinPlayers) {
                    sb.append("`" + player.uuid + "` | `" + Utils.calculatePhash(player.uuid) + "` | `" + player.id + "` | `" + player.ip + "`: " + Utils.escapeEverything(player.name) + "\n");
                }
                eb.addField("Joined", sb.toString());
                sb = new StringBuilder();
                for (JoinPlayerInfo player : joinPlayers) {
                    sb.append("`" + Utils.calculatePhash(player.uuid) + "` | `" + player.id + "`: " + Utils.escapeEverything(player.name) + "\n");
                }
                colonel_eb.addField("Joined", sb.toString());
            }
            if (leftPlayers.size != 0) {
                StringBuilder sb = new StringBuilder();
                for (JoinPlayerInfo player : leftPlayers) {
                    sb.append("`" + player.uuid + "` | `" + calculatePhash(player.uuid) + "` | `" + player.id + "` | `" + player.ip + "`: " + Utils.escapeEverything(player.name) + "\n");
                }
                eb.addField("Left", sb.toString());
                sb = new StringBuilder();
                for (JoinPlayerInfo player : leftPlayers) {
                    sb.append("`" + calculatePhash(player.uuid) + "` | `" + player.id + "`: " + Utils.escapeEverything(player.name) + "\n");
                }
                colonel_eb.addField("Left", sb.toString());
            }
            if (leftPlayers.size != 0 || joinPlayers.size != 0) {
                joinPlayers.clear();
                leftPlayers.clear();

                colonel_eb.setTimestampToNow();
                eb.setTimestampToNow();
                Channels.LOG.sendMessage(eb);
                Channels.COLONEL_LOG.sendMessage(colonel_eb);
                try {
                    LIVE_LOG.getMessageById(live_log_message).thenAccept(message -> {
                        message.edit(colonel_eb);
                    });
                } catch (Exception e) {
                    // someone deleted the message!!
                    try {
                        Channels.LIVE_LOG.sendMessage(colonel_eb).thenAccept(message -> {
                            live_log_message = message.getId();
                        });
                    } catch (Exception ignored) {
                    }
                }
            }
            updateBannedWordsClient();
            System.gc();
        }, 30, 30);

        String[] slurs = new String[]{
                "chink",
                "cracker",
                "nigger",
                "kike",
                "faggot", "fag",
                "dyke",
                "tranny", "trannie"
        };

        Events.on(EventType.ServerLoadEvent.class, event -> {
            Vars.netServer.admins.addChatFilter((player, message) -> {
                if (player == null) return message;

                Seq<String> usedSlurs = new Seq<>();
                for (String slur : slurs) {
                    if (message.toLowerCase().contains(slur)) {
                        usedSlurs.add(slur);
                        message = message.replace(slur, "$@#!");
                    }
                }
                // same thing bannedWords
                for (String bw : bannedWords) {
                    if (message.toLowerCase().contains(bw)) {
                        usedSlurs.add(bw);
                        // create random string of length bw.length consisting of ['@', '#', '$', '%', '^', '&', '*', '!']
                        StringBuilder rep = new StringBuilder();
                        ArrayList<Character> chars = new ArrayList<>(Arrays.asList('@', '#', '$', '%', '^', '&', '*', '!'));
                        for (int i = 0; i < bw.length(); i++) {
                            rep.append(chars.get((int) (Math.random() * chars.size())));
                        }
                        message = message.replace(bw, rep);
                    }
                }
                if (usedSlurs.size == 0) return message;

                Channels.LOG.sendMessage(new EmbedBuilder()
                        .setColor(DiscordPalette.WARN)
                        .setTitle("Slur usage")
                        .addInlineField("Player", Utils.escapeEverything(player.name) + "\n`" + player.uuid() + "`")
                        .addInlineField("Slurs", usedSlurs.toString(", "))
                        .addField("Message", escapeFoosCharacters(message))
                );
                return message;
            });
        });
    }

    private static class JoinPlayerInfo {
        String name;
        String uuid;
        int id;
        String ip;
    }
}
