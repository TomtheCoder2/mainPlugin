package mindustry.plugin.utils;

import arc.struct.ObjectSet;
import arc.util.Timer;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.maps.Map;

import java.util.Formatter;

import static mindustry.Vars.netServer;

public class MapVoteSession {
    Map target;
    ObjectSet<String> voted = new ObjectSet<>();
    MapVoteSession[] map;
    Timer.Task task;
    int votes;

    //voting round duration in seconds
    float voteDuration = 3f * 60;

    public MapVoteSession(MapVoteSession[] map, Map target) {
        this.target = target;
        this.map = map;
        this.task = Timer.schedule(() -> {
            if (!checkPass()) {
                StringBuilder sbuf = new StringBuilder();
                Formatter fmt = new Formatter(sbuf);
                fmt.format("[lightgray]Vote failed. Not enough votes to switch map to[accent] %b[lightgray].",
                        target.name());
                Call.sendMessage(sbuf.toString());
                map[0] = null;
                task.cancel();
            }
        }, voteDuration);
    }

    public int votesRequired() {
        return (int) (Groups.player.size() / 1.5f);
    }

    public void vote(Player player, int d) {
        votes += d;
        voted.addAll(player.uuid(), netServer.admins.getInfo(player.uuid()).lastIP);
        StringBuilder sbuf = new StringBuilder();
        Formatter fmt = new Formatter(sbuf);
        fmt.format("[orange]%s[lightgray] has voted to change the map to[orange] %s[].[accent] (%d/%d)\n[lightgray]Type[orange] /rtv to agree.",
                player.name, target.name(), votes, votesRequired());
        Call.sendMessage(sbuf.toString());
        checkPass();
    }

    boolean checkPass() {
        if (votes >= votesRequired()) {
            StringBuilder sbuf = new StringBuilder();
            Formatter fmt = new Formatter(sbuf);
            fmt.format("[orange]Vote passed.[scarlet] changing map to %s.", target.name());
            Call.sendMessage(sbuf.toString());
//            Call.sendMessage(Strings.format("[orange]Vote passed.[scarlet] changing map to %s.", target.name()));
            Utils.changeMap(target);
            map[0] = null;
            task.cancel();
            return true;
        }
        return false;
    }
}