package mindustry.plugin.minimods;

import arc.util.CommandHandler;
import arc.util.Timer;
import mindustry.gen.Call;
import mindustry.gen.Player;
import mindustry.plugin.MiniMod;
import mindustry.plugin.database.Database;
import mindustry.plugin.discord.Roles;
import mindustry.plugin.discord.discordcommands.DiscordRegistrar;

import java.util.Objects;

import static mindustry.Vars.mods;

public class JS implements MiniMod {
    private boolean enableJS;
    private Timer.Task enableJSTask;

    @Override
    public void registerDiscordCommands(DiscordRegistrar handler) {
        handler.register("enablejs", "<true/false> [minutes]", d -> {
            d.roles = new long[]{Roles.ADMIN, Roles.MOD};
            d.category = "Moderation";
            d.help = "Enable/Disable JS command for everyone";
        }, ctx -> {
            String nickname = ctx.author().getDisplayName(ctx.server());
            switch (ctx.args.get("true/false")) {
                case "true":
                case "t":
                    long minutes = 10;
                    if (ctx.args.containsKey("minutes")) {
                        minutes = ctx.args.getLong("minutes", 10);
                    }
                    enableJS(minutes * 60, nickname);
                    ctx.success("Enabled JS", "Enabled /js command for " + minutes + " minutes.");
                    break;
                case "false":
                case "f":
                    disableJS(nickname);
                    ctx.success("Disabled JS", "Disabled /js command.");
                    break;
                default:
                    ctx.error("Error", "First argument must be true or false");
            }
        });

        handler.register("js", "<code>", 
            data -> {
                data.roles = new long[] { Roles.ADMIN, Roles.MOD, Roles.APPRENTICE };
                data.category = "Moderation";
                data.help = "Run JS code";
            },
            ctx -> {
                String res = mods.getScripts().runConsole(ctx.args.get("code"));
                ctx.success("Ran code", "Output:\n```\n" + res + "\n```");
            }
        );
    }

    private void enableJS(long sec, String name) {
        enableJS = true;
        if (enableJSTask != null) {
            enableJSTask.cancel();
        }
        enableJSTask = Timer.schedule(() -> {
            if (!enableJS) {
                return;
            }
            enableJS = false;
            Call.sendMessage("[cyan]/js[accent] command disabled for everyone!");
        }, sec);
        Call.sendMessage("[accent]Marshal " + name + "[accent] enabled the js command for everyone for " + (sec / 60) + " minutes! Do [cyan]/js <script...>[accent] to use it.");
    }

    private void disableJS(String name) {
        enableJS = false;
        Call.sendMessage("[accent]Marshal " + name + "[accent] disabled the [cyan]/js[accent] command for everyone!");
    }

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
                        enableJS(arg.length > 1 ? Integer.parseInt(arg[1]) * 60 : 10 * 60, player.name);
                    }
                    case "false", "f" -> {
                        disableJS(player.name);
                    }
                    default -> {
                        player.sendMessage("[scarlet]Second argument has to be true or false.");
                        return;
                    }
                }
            } else {
                player.sendMessage("[scarlet]This command is restricted to admins!");
            }
        });

        handler.<Player>register("js", "<script...>", "Run arbitrary Javascript.", (arg, player) -> {
            if (player.admin || enableJS) {
                player.sendMessage(mods.getScripts().runConsole(arg[0]));
            } else {
                player.sendMessage("[scarlet]This command is restricted to admins!");
            }
        });
    }
}
