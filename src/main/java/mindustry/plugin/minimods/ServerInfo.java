package mindustry.plugin.minimods;

import arc.util.CommandHandler;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.gen.Call;
import mindustry.gen.Player;
import mindustry.plugin.MiniMod;
import mindustry.plugin.utils.Utils;
import mindustry.world.Tile;

/**
 * MiniMod providing commands relating to server information.
 */
public class ServerInfo implements MiniMod {
    @Override
    public void registerCommands(CommandHandler handler) {
        handler.<Player>register("info", "Display info about our server.", (args, player) -> {
            Call.infoMessage(player.con, Utils.infoMessage);
        });

        handler.<Player>register("rules", "Server rules. Please read carefully.", (args, player) -> {
            Call.infoMessage(player.con, Utils.ruleMessage);
        });

        handler.<Player>register("event", "Join an ongoing event (if there is one)", (args, player) -> {
            if (Utils.eventIp.length() > 0) {
                Call.connect(player.con, Utils.eventIp, Utils.eventPort);
            } else {
                player.sendMessage("[scarlet]There is no ongoing event at this time.");
            }
        });

        handler.<Player>register("discord", "Place a message block below a player with links for our discord server.", (args, player) -> {
            float x = player.getX();
            float y = player.getY();
            Tile tile = Vars.world.tileWorld(x, y);
            if ((tile.block() == null || tile.block() == Blocks.air)) {
                tile.setNet(Blocks.message, player.team(), 0);
                tile.build.configure("https://discord.phoenix-network.dev\nhttps://discord.gg/qtjqCUbbdR");
                player.sendMessage("[green]Successfully placed a message block.");
            } else {
                player.sendMessage("[scarlet]Cant place a message block here, because there is already a block here!");
            }
        });
    }
}
