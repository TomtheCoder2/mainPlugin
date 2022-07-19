package mindustry.plugin.minimods;

import arc.util.Structs;
import mindustry.Vars;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Player;
import mindustry.gen.Unit;
import mindustry.plugin.MiniMod;
import mindustry.plugin.discord.DiscordLog;
import mindustry.plugin.discord.Roles;
import mindustry.plugin.discord.discordcommands.DiscordRegistrar;
import mindustry.plugin.utils.Utils;
import mindustry.type.Item;
import mindustry.type.UnitType;

public class Cheats implements MiniMod {
    @Override
    public void registerDiscordCommands(DiscordRegistrar handler) {
        handler.register("fillcore", "[team]",
                data -> {
                    data.aliases = new String[]{"fillitems"};
                    data.category = "Cheats";
                    data.roles = new long[]{Roles.MOD, Roles.ADMIN,Roles.APPRENTICE};
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

        handler.register("convert", "<player> <unit>", 
                data -> {
                    data.help = "Convert a player into a unit";
                    data.roles = new long[] { Roles.MOD, Roles.ADMIN, Roles.APPRENTICE };
                    data.category = "Cheats";
                },
                ctx -> {
                    UnitType unit = Vars.content.unit(ctx.args.get("unit"));
                    if (unit == null) {
                        ctx.error("Invalid unit type", ctx.args.get("unit") + " is not a valid unit type");
                    }
                    Player p = Utils.findPlayer(ctx.args.get("player"));
                    if (p == null) {
                        ctx.error("Player not found", ctx.args.get("player") + " is not online");
                    }
                    
                    Unit oldunit = p.unit();
                    p.unit(unit.spawn(p.x, p.y));
                    Call.unitDespawn(oldunit);
                    ctx.success("Success", "Changed " + Utils.escapeEverything(p.name()) + "'s unit to " + p.unit().type.name);
                    DiscordLog.cheat("Changed unit", ctx.author(), "Target: " + Utils.escapeEverything(p.name()) + "\nUnit: " +p.unit().type.name);
                }
        );
    }
}
