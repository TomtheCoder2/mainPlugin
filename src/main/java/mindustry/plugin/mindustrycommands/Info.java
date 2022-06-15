package mindustry.plugin.mindustrycommands;

import arc.util.CommandHandler;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.plugin.MiniMod;
import mindustry.plugin.data.PlayerData;

import static mindustry.plugin.database.Utils.getData;
import static mindustry.plugin.utils.Utils.*;
import static mindustry.plugin.utils.ranks.Utils.inGameListRanks;
import static mindustry.plugin.utils.ranks.Utils.listRequirements;

public class Info implements MiniMod {
    public void registerCommands(CommandHandler handler) {
        handler.<Player>register("info", "Display info about our server.", (args, player) -> {
            Call.infoMessage(player.con, infoMessage);
        });

        handler.<Player>register("rules", "Server rules. Please read carefully.", (args, player) -> {
            Call.infoMessage(player.con, ruleMessage);
        });

        handler.<Player>register("event", "Join an ongoing event (if there is one)", (args, player) -> {
            if (eventIp.length() > 0) {
                Call.connect(player.con, eventIp, eventPort);
            } else {
                player.sendMessage("[scarlet]There is no ongoing event at this time.");
            }
        });
        handler.<Player>register("players", "Display all players and their ids", (args, player) -> {
            StringBuilder builder = new StringBuilder();
            builder.append("[orange]List of players: \n");
            for (Player p : Groups.player) {
                if (p.admin) {
                    builder.append("[accent]");
                } else {
                    builder.append("[lightgray]");
                }
                builder.append(p.name).append("[accent] : ").append(p.id).append("\n");
            }
            player.sendMessage(builder.toString());
        });
        handler.<Player>register("ranks", "Show for all ranks.", (args, player) -> { // self info
            Call.infoMessage(player.con, formatMessage(player, inGameListRanks()));
        });
        handler.<Player>register("req", "Show the requirements for all ranks", (args, player) -> { // self info
            Call.infoMessage(player.con, formatMessage(player, listRequirements()));
        });
        handler.<Player>register("stats", "[player]", "Display stats of the specified player.", (args, player) -> {
            if (args.length > 0) {
                Player p = findPlayer(args[0]);
                if (p != null) {
                    PlayerData pd = getData(p.uuid());
                    if (pd != null) {
                        Call.infoMessage(player.con, formatMessage(p, statMessage));
                    }
                } else {
                    player.sendMessage("[scarlet]Error: Player not found or offline");
                }
            } else {
                Call.infoMessage(player.con, formatMessage(player, statMessage));
            }
        });
    }
}
