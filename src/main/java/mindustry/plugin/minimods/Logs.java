package mindustry.plugin.minimods;

import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.springframework.beans.factory.Aware;

import arc.Events;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import arc.util.Timer;
import mindustry.game.EventType;
import mindustry.plugin.MiniMod;
import mindustry.plugin.discord.Channels;
import mindustry.plugin.discord.DiscordPalette;
import mindustry.plugin.utils.Utils;

/** Logs player actions, such as player join & leave and suspicious activity */
public class Logs implements MiniMod {
    private static class JoinPlayerInfo {
        String name;
        String uuid;
        int id;
        String ip;
    }

    Seq<JoinPlayerInfo> leftPlayers = new Seq<>();
    Seq<JoinPlayerInfo> joinPlayers = new Seq<>();

    public void registerEvents() {
        Events.on(EventType.PlayerLeave.class, event -> {
            JoinPlayerInfo data = new JoinPlayerInfo();
            data.name = event.player.name;
            data.uuid = event.player.uuid();
            data.id = event.player.id;
            data.ip =event.player.ip();

            leftPlayers.add(data);
        });

        Events.on(EventType.PlayerJoin.class, event -> {
            JoinPlayerInfo data = new JoinPlayerInfo();
            data.name = event.player.name;
            data.uuid = event.player.uuid();
            data.id = event.player.id;
            data.ip =event.player.ip();

            joinPlayers.add(data);
        });

        Timer.schedule(() -> {
            EmbedBuilder eb = new EmbedBuilder()
                .setTitle("Player Join & Leave Log")
                .setColor(DiscordPalette.SUCCESS); 

            if (joinPlayers.size != 0) {
                StringBuilder sb = new StringBuilder();
                for (JoinPlayerInfo player : joinPlayers) {
                    sb.append("`" + player.uuid + "` | `" + player.id + "` | `" + player.ip + ": " + Utils.escapeColorCodes(player.name));
                }
                eb.addField("Joined", sb.toString());
            }
            if (leftPlayers.size != 0) {
                StringBuilder sb = new StringBuilder();
                for (JoinPlayerInfo player : leftPlayers) {
                    sb.append("`" + player.uuid + "` | `" + player.id + "` | `" + player.ip + ": " + Utils.escapeColorCodes(player.name));
                }
                eb.addField("Left", sb.toString());
            }

            Channels.LOG.sendMessage(eb);
        }, 30, 30);
    }
}
