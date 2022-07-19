package mindustry.plugin.minimods;

import arc.util.Structs;
import mindustry.Vars;
import mindustry.game.Team;
import mindustry.plugin.MiniMod;
import mindustry.plugin.discord.Channels;
import mindustry.plugin.discord.DiscordLog;
import mindustry.plugin.discord.Roles;
import mindustry.plugin.discord.discordcommands.DiscordRegistrar;
import mindustry.plugin.utils.Utils;
import mindustry.type.Item;

public class Cheats implements MiniMod {
    @Override
    public void registerDiscordCommands(DiscordRegistrar handler) {
        handler.register("fillcore", "[team]",
            data -> {
                data.aliases = new String[] { "fillitems" };
                data.category = "Cheats";
                data.roles = new long[] { Roles.MOD, Roles.ADMIN };
            },
            ctx -> {
                String teamName = ctx.args.get("team");
                Team team = Team.sharded;
                if (teamName != null) {
                    team = Structs.find(Team.all, t -> t.name.equalsIgnoreCase(teamName));
                    if (team == null) {
                        ctx.error("No such team", "Team '" + teamName + "' does not exist");
                        return;
                    }
                }

                if (team.core() == null) {
                    ctx.error("Core does not exist", "Team " + team.name + " does not have a core");
                    return;
                }

                for (Item item : Vars.content.items()) {
                    team.core().items().set(item, team.core().storageCapacity);
                }

                ctx.success("Filled core", "Filled core of team " + team.name);
                DiscordLog.cheat("Filled core", ctx.author(), "Team: " + team.name + "\nMap:\n" + Utils.escapeColorCodes(Vars.state.map.name()));
            }
        );
    }
}
