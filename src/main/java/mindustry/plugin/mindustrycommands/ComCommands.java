package mindustry.plugin.mindustrycommands;

import arc.util.CommandHandler;
import arc.util.Strings;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.plugin.data.PersistentPlayerData;
import mindustry.plugin.data.PlayerData;
import mindustry.plugin.requests.Translate;
import mindustry.plugin.utils.Utils;
import org.javacord.api.entity.channel.TextChannel;
import org.json.JSONObject;

import static arc.util.Log.debug;
import static mindustry.Vars.netServer;
import static mindustry.plugin.database.Utils.getData;
import static mindustry.plugin.ioMain.*;
import static mindustry.plugin.utils.Utils.*;
import static mindustry.plugin.utils.ranks.Utils.rankNames;

public class ComCommands {
    public void registerCommands(CommandHandler handler) {
        handler.<Player>register("w", "<player> <text...>", "Whisper text to another player.", (args, player) -> {
            //find player by name
//            Player other = Groups.player.find(p -> p.name.equalsIgnoreCase(args[0]));

            Player other = Utils.findPlayer(args[0]);

            // give error message with scarlet-colored text if player isn't found
            if (other == null) {
                player.sendMessage("[scarlet]No player by that name found!");
                return;
            }

            // send the other player a message, using [lightgray] for gray text color and [] to reset color
            other.sendMessage("[orange][[[gray]whisper from [#ffd37f]" + Strings.stripColors(player.name) + "[orange]]: [gray]" + args[1]);
            player.sendMessage("[orange][[[gray]whisper to [#ffd37f]" + Strings.stripColors(other.name) + "[orange]]: [gray]" + args[1]);
        });
        handler.<Player>register("translate", "<language> <text...>", "Translate your message", (arg, player) -> {
            try {
                JSONObject res = new JSONObject(Translate.translate(escapeEverything(arg[1]), arg[0]));
                if (res.has("translated") && res.getJSONObject("translated").has("text")) {
                    String translated = res.getJSONObject("translated").getString("text");
                    debug(translated);
                    Call.sendMessage("<translated>[orange][[[accent]" + player.name + "[orange]][white]: " + translated);
                    TextChannel tc = getTextChannel(live_chat_channel_id);
                    assert tc != null;
                    tc.sendMessage("<translated>**" + escapeEverything(player.name) + "**: " + translated);
                } else {
                    debug(res);
                    player.sendMessage("[scarlet]There was an error: " + (res.has("error") ? res.getString("error") : "No more information, ask Nautilus on discord!"));
                }
            } catch (Exception e) {
                player.sendMessage("[scarlet]There was an error: " + e.getMessage());
            }
        });
        handler.removeCommand("t");
        handler.<Player>register("t", "<message...>", "Send a message only to your teammates.", (args, player) -> {
            String message = args[0];
            if (!checkChatRatelimit(message, player)) return;
            String raw = "[#" + player.team().color.toString() + "]<T> " + chatFormatter.format(player, message);
            Groups.player.each(p -> p.team() == player.team(), o -> o.sendMessage(raw, player, message));
        });
        handler.<Player>register("rainbow", "Give your username a rainbow animation", (args, player) -> {
            PlayerData pd = getData(player.uuid());
            if (pd == null) {
                player.sendMessage("There was an error!");
                return;
            }
            if (pd.rank >= 0) {
                PersistentPlayerData tdata = (playerDataGroup.getOrDefault(player.uuid(), null));
                if (tdata == null) return; // shouldn't happen, ever
                if (tdata.doRainbow) {
                    player.sendMessage("[sky]Rainbow effect toggled off.");
                    tdata.doRainbow = false;
                    player.name = rankNames.get(pd.rank).tag + netServer.admins.getInfo(player.uuid()).names.get(0);
                } else {
                    player.sendMessage("[sky]Rainbow effect toggled on.");
                    tdata.doRainbow = true;
                }
            } else {
                player.sendMessage(noPermissionMessage);
            }
        });
    }
}
