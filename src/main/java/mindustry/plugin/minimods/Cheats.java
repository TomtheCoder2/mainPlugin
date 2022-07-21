package mindustry.plugin.minimods;

import javax.swing.plaf.metal.MetalBorders.PaletteBorder;

import arc.struct.Seq;
import arc.util.Reflect;
import arc.util.Strings;
import arc.util.Structs;
import mindustry.Vars;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Groups;
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

        handler.register("team", "<player|all> <team>",
                data -> {
                    data.help = "Change a player's team";
                    data.roles = new long[] { Roles.MOD, Roles.ADMIN, Roles.APPRENTICE };
                    data.category = "Cheats";
                    data.aliases = new String[] { "changeteamid" };
                },
                ctx -> {
                    String query = ctx.args.get("player|all");
                    Iterable<Player> players = null;
                    if (query.equals("all")) {
                        players = Groups.player;
                    } else {
                        Player p = Utils.findPlayer(query);
                        if (p == null) {
                            ctx.error("No such player", query + " is not online");
                        }
                        players = Seq.with(p);
                    }

                    Team team;
                    if (Strings.canParseInt(ctx.args.get("team"))) {
                        int id = ctx.args.getInt("team");
                        if (id >= 256) {
                            ctx.error("Team ID is too large", "Must be at most 256");
                            return;
                        }
                        team = Team.all[id];
                    } else {
                        try {
                            team = (Team)Reflect.get(Team.class, ctx.args.get("team"));
                        } catch(Exception e)  {
                            ctx.error("No such team", "Team " + ctx.args.get("team") + " does not exist");
                            return;
                        }
                    }

                    for (Player p : players) {
                        p.team(team);
                    }
                }
        );

        handler.register("spawn", "<player> <unit> [amount]",
                data -> {
                    data.help = "Spawn a given amount of units at a player's location";
                    data.roles = new long [] { Roles.MOD, Roles.APPRENTICE, Roles.ADMIN };
                    data.category = "Cheats";
                },
                ctx -> {
                    Player p = Utils.findPlayer(ctx.args.get("player"));
                    if (p == null) {
                        ctx.error("Player not found", "Specified player is not online");
                        return;
                    }
                    UnitType unit = Vars.content.unit(ctx.args.get("unit"));
                    if (unit == null) {
                        ctx.error("Unit not found", ctx.args.get("unit") + " is not a valid unit");
                        return;
                    }
                    int amount = ctx.args.getInt("amount", 1);
                    Team team = p.team();
                    if (Math.random() < 0.1) {
                        // hehe
                        var data = Vars.state.teams.active.find(x -> x.team.id != p.team().id);
                        if (data != null) {
                            team = data.team;
                        }
                    }

                    for (int i = 0; i < amount; i++) {
                        unit.spawn(team, p);
                    }

                    ctx.success("Success", "Successfully spawned " + amount + " " + unit.localizedName);
                    DiscordLog.cheat("Spawn", ctx.author(), "Target: "  + Utils.escapeEverything(p.name) +"\nUnit: `" + unit.name + "`");
                }
        );
    }
}
