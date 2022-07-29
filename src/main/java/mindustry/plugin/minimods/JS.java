package mindustry.plugin.minimods;

import arc.util.CommandHandler;
import arc.util.Strings;
import arc.util.Timer;
import mindustry.gen.Call;
import mindustry.gen.Player;
import mindustry.plugin.MiniMod;
import mindustry.plugin.database.Database;
import mindustry.plugin.discord.Roles;
import mindustry.plugin.discord.discordcommands.DiscordRegistrar;
import mindustry.plugin.utils.GameMsg;
import mindustry.plugin.utils.Rank;
import mindustry.plugin.utils.Utils;

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
            Call.sendMessage(GameMsg.info("JS", "[" + GameMsg.CMD + "]/js[] command now disabled!"));
        }, sec);
        Call.sendMessage(GameMsg.info("JS", 
            "[white]" + name + "[" + GameMsg.INFO + "] enabled the js command for everyone for "
                + (sec / 60) + " minutes! Do [" + GameMsg.CMD + "]/js <script...>[] to use it."));
    }

    private void disableJS(String name) {
        enableJS = false;
        Call.sendMessage(GameMsg.info("JS", "[white]" + name + "[" + GameMsg.INFO + "] disabled the [" + GameMsg.CMD + "]/js[" + GameMsg.INFO + "] command for everyone!"));
    }

    @Override
    public void registerCommands(CommandHandler handler) {
        Utils.registerRankCommand(handler, "enablejs", "<true/false> [time]", Rank.MOD, "Enable/Disable js command for everyone. (Time in minutes)", (arg, player) -> {
            if (arg.length > 1) {
                if (!Strings.canParseInt(arg[1]))
                    player.sendMessage(GameMsg.error("JS", "Second argument must be an integer"));
            }
            switch (arg[0]) {
                case "true", "t" -> {
                    enableJS(arg.length > 1 ? Integer.parseInt(arg[1]) * 60 : 10 * 60, player.name);
                }
                case "false", "f" -> {
                    disableJS(player.name);
                }
                default -> {
                    player.sendMessage(GameMsg.error("JS", "First argument must be 'true' or 'false'"));
                    return;
                }
            }
        });

        handler.<Player>register("js", "<script...>", "Run arbitrary Javascript.", (arg, player) -> {
            if (player.admin || enableJS) {
                player.sendMessage(mods.getScripts().runConsole(arg[0]));
            } else {
                player.sendMessage(GameMsg.noPerms("JS"));
            }
        });
    }
}
