package mindustry.plugin.minimods;

import arc.Core;
import arc.Events;
import arc.struct.Seq;
import arc.util.Log;
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
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

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

    @NotNull
    static String genRandomString(int len) {
        StringBuilder rep = new StringBuilder();
        ArrayList<Character> chars = new ArrayList<>(Arrays.asList('@', '#', '$', '%', '^', '&', '*', '!'));
        for (int i = 0; i < len; i++) {
            rep.append(chars.get((int) (Math.random() * chars.size())));
        }
        return rep.toString();
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
            int pre = (int) (Core.app.getJavaHeap() / 1024.0 / 1024.0);
            System.gc();
            int post = (int) (Core.app.getJavaHeap() / 1024.0 / 1024.0);

//            Log.info("Garbage collection: @ mb - @ mb= @ mb", pre, post, pre - post);

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
                try {
                    if (player == null) return message;

                    Seq<String> usedSlurs = new Seq<>();
                    for (String slur : slurs) {
                        if (message.toLowerCase().contains(slur)) {
                            usedSlurs.add(slur);
                            message = message.replace(slur, "$@#!");
                        }
                    }
                    // list of numbers that resemble a letter
                    HashMap<String, String> numberLetters = new HashMap<>();
                    numberLetters.put("1", "i");
                    numberLetters.put("2", "to");
                    numberLetters.put("3", "e");
                    numberLetters.put("4", "a");
                    numberLetters.put("5", "r");
                    numberLetters.put("6", "b");
                    numberLetters.put("7", "t");
                    numberLetters.put("8", "g");
                    numberLetters.put("9", "g");
                    numberLetters.put("0", "o");
                    numberLetters.put("!", "i");
                    numberLetters.put("@", "a");
                    numberLetters.put("#", "h");
                    numberLetters.put("$", "s");
                    numberLetters.put("&", "a");
                    numberLetters.put("*", "x");
                    numberLetters.put("(", "c");


                    // same thing bannedWords
                    for (String bw : bannedWords) {
                        var mLower = message.toLowerCase().replace(" ", "");
                        for (String number : numberLetters.keySet()) {
                            if (mLower.contains(number)) {
                                mLower = mLower.replace(number, numberLetters.get(number));
                            }
                        }
                        if (mLower.contains(bw.toLowerCase())) {
                            usedSlurs.add(bw);
                            // now we need to find the position of the banned word in the message
                            // but the problem is that the message contains eg "$hit" but the banned word is "shit"
                            var index = mLower.indexOf(bw.toLowerCase());
                            var length = bw.length();
                            var randomString = genRandomString(length);
                            StringBuilder finalMessage = new StringBuilder();
                            for (int i = 0; i < message.length(); i++) {
                                if (i >= index && i < index + length) {
                                    if (message.charAt(i) == ' ') length++;
                                    if (length > randomString.length()) {
                                        finalMessage.append(genRandomString(1));
                                    } else {
                                        finalMessage.append(randomString.charAt(i - index));
                                    }
                                } else {
                                    finalMessage.append(message.charAt(i));
                                }
                            }
                            message = finalMessage.toString();
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
                } catch (Exception e) {
                    e.printStackTrace();
                    return message;
                }
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
