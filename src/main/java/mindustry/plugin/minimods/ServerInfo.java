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
            Call.infoMessage(player.con, Utils.Message.info());
        });

        handler.<Player>register("rules", "Server rules. Please read carefully.", (args, player) -> {
            Call.infoMessage(player.con, Utils.Message.rules());
        });

        handler.<Player>register("discord", "Place a message block below a player with links for our discord server and our trello page.", (args, player) -> {
            float x = player.getX();
            float y = player.getY();
            Tile tile = Vars.world.tileWorld(x, y);
            if ((tile.block() == null || tile.block() == Blocks.air)) {
                tile.setNet(Blocks.message, player.team(), 0);
                tile.build.configure("our discord ->https://discord.gg/qtjqCUbbdR\nour trello page ->https://trello.com/b/9aGq0kB9/plugin");
                player.sendMessage("[green]Successfully placed a message block.");
            } else {
                player.sendMessage("[scarlet]Cant place a message block here, because there is already a block here!");
            }
        });
    }
}
