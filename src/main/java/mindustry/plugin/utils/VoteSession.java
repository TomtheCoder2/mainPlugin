package mindustry.plugin.utils;

import arc.struct.ObjectSet;
import arc.util.Strings;
import arc.util.Timer;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.net.Administration;
import mindustry.plugin.database.PlayerData;
import org.javacord.api.entity.message.embed.EmbedBuilder;

import java.awt.*;
import java.time.Instant;

import static mindustry.Vars.netServer;
import static mindustry.plugin.database.Utils.getData;
import static mindustry.plugin.database.Utils.setData;
import static mindustry.plugin.ioMain.*;

public class VoteSession {
    public Player target;
    public ObjectSet<String> voted = new ObjectSet<>();
    VoteSession[] map;
    Timer.Task task;
    int votes;
    Player startedVk = null;

    //duration of a kick in seconds
    int kickDuration = 60 * 60;
    //voting round duration in seconds
    float voteDuration = 0.5f * 60;

    public VoteSession(VoteSession[] map, Player target) {
//            kickDuration = 60; // testing only
        this.target = target;
        this.map = map;
        this.task = Timer.schedule(() -> {
            if (!checkPass()) {
                Call.sendMessage(Strings.format("[lightgray]Vote failed. Not enough votes to kick[orange] @[lightgray].", target.name));
                logVk(false);
                // unfreeze the player
                PersistentPlayerData tdata = (playerDataGroup.getOrDefault(target.uuid(), null));
                assert tdata != null;
                tdata.frozen = false;
                map[0] = null;
                task.cancel();
            }
        }, voteDuration);
    }

    public void vote(Player player, int d) {
        if (votes == 0 && this.startedVk == null) {
            this.startedVk = player;
        }
        votes += d;
        voted.addAll(player.uuid(), netServer.admins.getInfo(player.uuid()).lastIP);

        Call.sendMessage(Strings.format("[lightgray]@[lightgray] has voted on kicking[orange] @[lightgray].[accent] (@/@)\n[lightgray]Type[orange] /vote <y/n>[] to agree.",
                player.name, target.name, votes, votesRequired()));

        checkPass();
    }

    public void vote(int d) {
        votes += d;

        Call.sendMessage(Strings.format("[lightgray][scarlet]server[lightgray] has voted on kicking[orange] @[lightgray].[accent] (@/@)\n[lightgray]Type[orange] /vote <y/n>[] to agree.",
                target.name, votes, votesRequired()));

        checkPass();
    }

    public boolean checkPass() {
        if (votes >= votesRequired()) {
            Call.sendMessage(Strings.format("[orange]Vote passed.[scarlet] @[orange] will be banned from the server for @ minutes.", target.name, (kickDuration / 60)));
//                Groups.player.each(p -> p.uuid().equals(target.uuid()), p -> p.kick(Packets.KickReason.vote, kickDuration * 1000));
            logVk(true);

            // ban in database
            banInDatabase(target, startedVk.name);
            map[0] = null;
            task.cancel();
            return true;
        }
        return false;
    }

    private void logVk(boolean success) {
        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("Votekick " + (success ? "succeeded" : "failed") + "!")
                .addField("Target:", Utils.escapeEverything(target.name) + "\n" + target.uuid())
                .setColor(success ? new Color(0xff0000) : new Color(0x00ff00))
                .addField("Started by:", Utils.escapeEverything(startedVk.name) + "\n" + startedVk.uuid());
        Utils.getTextChannel(log_channel_id).sendMessage(eb);
    }

    private void banInDatabase(Player target, String name) {
        target.con.kick("");
        long now = Instant.now().getEpochSecond();
        long until = now + kickDuration;
        String banId = target.uuid().substring(0, 4);
        PlayerData pd = getData(target.uuid());
        if (pd != null) {
            pd.bannedUntil = until;
            pd.banReason = "Votekicked by " + name + "[white]\n" + "[accent]Until: " + Utils.epochToString(until) + "\n[accent]Ban ID:[] " + banId;
            setData(target.uuid(), pd);
        }
        Administration.PlayerInfo info = netServer.admins.getInfo(target.uuid());
        info.lastKicked = 0;
    }

    public int votesRequired() {
        return 2 + (Groups.player.size() > 4 ? 1 : 0);
    }
}