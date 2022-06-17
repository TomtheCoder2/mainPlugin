package mindustry.plugin.mindustrycommands;

import arc.util.CommandHandler;
import arc.util.Timer;
import mindustry.gen.Call;
import mindustry.gen.Player;
import mindustry.plugin.MiniMod;
import mindustry.plugin.database.Database;

import java.util.Objects;

import static mindustry.Vars.mods;
import static mindustry.plugin.ioMain.enableJs;
import static mindustry.plugin.ioMain.enableJsTask;

public class Admin implements MiniMod {
    @Override
    public void registerCommands(CommandHandler handler) {
        handler.<Player>register("enablejs", "<true/false> [time]", "Enable/Disable js command for everyone. (Time in minutes)", (arg, player) -> {
            Database.Player pd = Database.getPlayerData(player.uuid());
            if (arg.length > 1) {
                try {
                    Integer.parseInt(arg[1]);
                } catch (Exception e) {
                    player.sendMessage("[scarlet]Second argument has to be an Integer!");
                }
            }
            if (player.admin && Objects.requireNonNull(pd).rank >= 10) {
                switch (arg[0]) {
                    case "true", "t" -> {
                        enableJs = true;
                        if (enableJsTask != null) {
                            enableJsTask.cancel();
                        }
                        enableJsTask = Timer.schedule(() -> {
                            enableJs = false;
                            Call.sendMessage("[accent]js command disabled for everyone!");
                        }, arg.length > 1 ? Integer.parseInt(arg[1]) * 60 : 10 * 60);
                        Call.sendMessage("[accent]Marshal " + player.name + "[accent] enabled the js command for everyone" + (" for " + (enableJs ? (arg.length > 1 ? arg[1] : "10") + " minutes!" : "!")) + "Do [cyan]/js <script...>[accent] to use it.");
                    }
                    case "false", "f" -> {
                        enableJs = false;
                        Call.sendMessage("[accent]js command disabled for everyone!");
                    }
                    default -> {
                        player.sendMessage("[scarlet]Second argument has to be true or false.");
                        return;
                    }
                }
                player.sendMessage((enableJs ? "[green]Enabled[accent]" : "[scarlet]Disabled[accent]") + " js for everyone" + (" for " + (enableJs ? (arg.length > 1 ? arg[1] : "10") + " minutes!" : "!")));
            } else {
                player.sendMessage("[scarlet]This command is restricted to admins!");
            }
        });

        handler.<Player>register("js", "<script...>", "Run arbitrary Javascript.", (arg, player) -> {
            Database.Player pd = Database.getPlayerData(player.uuid());
            if ((player.admin && Objects.requireNonNull(pd).rank >= 9) || enableJs) {
                player.sendMessage(mods.getScripts().runConsole(arg[0]));
            } else {
                player.sendMessage("[scarlet]This command is restricted to admins!");
            }
        });
    }
}
