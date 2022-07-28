package mindustry.plugin.minimods;

import arc.struct.ObjectIntMap;
import arc.struct.ObjectSet;
import arc.util.CommandHandler;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.plugin.MiniMod;
import mindustry.plugin.utils.Cooldowns;
import mindustry.plugin.utils.GameMsg;
import arc.Events;

/** Provides the /skipwave command */
public class Skipwave implements MiniMod {
    private ObjectSet<String> votes = new ObjectSet<>();

    @Override
    public void registerEvents() {
        Events.on(EventType.PlayerLeave.class, event -> {
            votes.remove(event.player.uuid());
        });

        Events.on(EventType.WaveEvent.class, event -> {
            votes.clear();
        });
        Events.on(EventType.WorldLoadEvent.class, event -> {
            votes.clear();
        });
    }

    private static int requiredVotes() {
        return Groups.player.size() / 2 + 1;
    }

    @Override
    public void registerCommands(CommandHandler handler) {
        Cooldowns.instance.set("skipwave", 5);
        handler.<Player>register("skipwave", "[y/n]", "Skip wave of enemies", (args, player) -> {
            if (!Cooldowns.instance.canRun("skipwave", player.uuid())) {
                player.sendMessage(GameMsg.ratelimit("Skip", "skipwave"));
            }
            boolean vote = true;
            if (args.length > 0) {
                switch (args[0]) {
                    case "y", "yes": vote = true; break;
                    case "n", "no": vote = false; break;
                    default:
                        player.sendMessage(GameMsg.error("Skipwave", "Second argument must be 'yes' or 'no'"));
                        return;
                }
            }

            if (vote) {
                votes.add(player.uuid());
            } else {
                votes.remove(player.uuid());
            }

            int totalVotes = votes.size;
            Call.sendMessage(GameMsg.info("Skip", "[white]" + player.name() + "[" + GameMsg.INFO + "] has voted to " + 
                (vote ? "skip" : "not skip") + " the wave " + "(" + totalVotes + "/" + requiredVotes() + "). Type [" + GameMsg.CMD + "]/skipwave[] to agree to skip the wave."));
            if (totalVotes >= requiredVotes()) {
                Call.sendMessage(GameMsg.success("Skip", "Vote passed. Skipping wave."));
                Vars.logic.runWave();
                votes.clear();
            }
        });
    }
}
