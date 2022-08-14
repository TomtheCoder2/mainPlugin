package mindustry.plugin.minimods;

import arc.Events;
import arc.struct.Seq;
import arc.util.Timer;
import mindustry.game.EventType;
import mindustry.plugin.MiniMod;
import mindustry.plugin.discord.Channels;
import mindustry.plugin.discord.DiscordPalette;
import mindustry.plugin.utils.Utils;
import org.javacord.api.entity.message.embed.EmbedBuilder;

import static mindustry.plugin.database.Database.hash;

/**
 * Logs player actions, such as player join &amp; leave and suspicious activity
 */
public class Logs implements MiniMod {
    Seq<JoinPlayerInfo> leftPlayers = new Seq<>();
    Seq<JoinPlayerInfo> joinPlayers = new Seq<>();

    public void registerEvents() {
        Events.on(EventType.PlayerLeave.class, event -> {
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
            if (joinPlayers.size == 0 && leftPlayers.size == 0) return;

            EmbedBuilder eb = new EmbedBuilder()
                    .setTitle("Player Join & Leave Log")
                    .setColor(DiscordPalette.INFO);

            if (joinPlayers.size != 0) {
                StringBuilder sb = new StringBuilder();
                for (JoinPlayerInfo player : joinPlayers) {
                    sb.append("`" + player.uuid + "` | `" + Utils.calculatePhash(player.uuid) + "` | `" + player.id + "` | `" + player.ip + "`: " + Utils.escapeEverything(player.name) + "\n");
                }
                eb.addField("Joined", sb.toString());
            }
            if (leftPlayers.size != 0) {
                StringBuilder sb = new StringBuilder();
                for (JoinPlayerInfo player : leftPlayers) {
                    sb.append("`" + player.uuid + "` | `" + Utils.calculatePhash(player.uuid) + "` | `" + player.id + "` | `" + player.ip + "`: " + Utils.escapeEverything(player.name) + "\n");
                }
                eb.addField("Left", sb.toString());
            }

            joinPlayers.clear();
            leftPlayers.clear();

            Channels.LOG.sendMessage(eb);
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
        Events.on(EventType.PlayerChatEvent.class, event -> {
            if (event.player == null) return;

            Seq<String> usedSlurs = new Seq<>();
            for (String slur : slurs) {
                if (event.message.toLowerCase().contains(slur)) {
                    usedSlurs.add(slur);
                }
            }
            if (usedSlurs.size == 0) return;

            Channels.LOG.sendMessage(new EmbedBuilder()
                    .setColor(DiscordPalette.WARN)
                    .setTitle("Slur usage")
                    .addInlineField("Player", Utils.escapeEverything(event.player.name) + "\n`" + event.player.uuid() + "`")
                    .addInlineField("Slurs", usedSlurs.toString(", "))
                    .addField("Message", event.message)
            );
        });
    }

    private static class JoinPlayerInfo {
        String name;
        String uuid;
        int id;
        String ip;
    }
}
