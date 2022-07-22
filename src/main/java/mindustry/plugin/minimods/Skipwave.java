package mindustry.plugin.minimods;

import arc.struct.ObjectIntMap;
import arc.util.CommandHandler;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.plugin.MiniMod;
import mindustry.plugin.utils.GameMsg;
import arc.Events;

/** Provides the /skipwave command */
public class Skipwave implements MiniMod {
    private ObjectIntMap<String> votes = new ObjectIntMap<>();

    @Override
    public void registerEvents() {
        Events.on(EventType.PlayerLeave.class, event -> {
            votes.remove(event.player.uuid());
        });

        Events.on(EventType.WaveEvent.class, event -> {
            votes.clear();
        });
    }

    private static int requiredVotes() {
        return Groups.player.size() <= 3 ? 2 : 3;
    }

    @Override
    public void registerCommands(CommandHandler handler) {
        handler.<Player>register("skipwave", "[y/n]", "Skip wave of enemies", (args, player) -> {
            int vote = 1;
            if (args.length > 0) {
                switch (args[0]) {
                    case "y", "yes": vote = 1; break;
                    case "n", "no": vote = -1; break;
                    default:
                        player.sendMessage(GameMsg.error("Skipwave", "Second argument must be 'yes' or 'no'"));
                        return;
                }
            }

            votes.put(player.uuid(), vote);

            int totalVotes = votes.values().toArray().sum();
            Call.sendMessage(GameMsg.info("Skipwave", "[orange]" + player.name() + "[lightgray] has voted to " + 
                (vote == 1 ? "skip" : "not skip") + " the wave " + "(" + totalVotes + "/" + requiredVotes() + ")"));
            if (totalVotes >= requiredVotes()) {
                Call.sendMessage(GameMsg.success("Skipwave", "Vote passed. Skipping wave."));
                Vars.logic.runWave();
                votes.clear();
            }
        });
    }
}
