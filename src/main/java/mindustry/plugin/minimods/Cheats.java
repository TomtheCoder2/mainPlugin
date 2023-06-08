package mindustry.plugin.minimods;

import arc.struct.Seq;
import arc.util.CommandHandler;
import arc.util.Strings;
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
import mindustry.plugin.utils.GameMsg;
import mindustry.plugin.utils.Query;
import mindustry.plugin.utils.Rank;
import mindustry.plugin.utils.Utils;
import mindustry.type.Item;
import mindustry.type.UnitType;
import mindustry.world.Block;
import mindustry.world.Tile;

public class Cheats implements MiniMod {
    private boolean cheatsEnabled = false;


    private boolean fillcore(Team team) {
        if (team.core() == null) {
            return false;
        }

        for (Item item : Vars.content.items()) {
            team.core().items().set(item, team.core().storageCapacity);
        }
        return true;
    }

    @Override
    public void registerDiscordCommands(DiscordRegistrar handler) {
        handler.register("fillcore", "[team]",
                data -> {
                    data.aliases = new String[]{"fillitems"};
                    data.category = "Cheats";
                    data.roles = new long[]{Roles.MOD, Roles.ADMIN};
                },
                ctx -> {
                    String teamName = ctx.args.get("team");
                    Team team = Team.sharded;
                    if (teamName != null) {
                        team = Query.findTeam(teamName);
                        if (team == null) {
                            ctx.error("No such team", "Team '" + teamName + "' does not exist");
                            return;
                        }
                    }

                    if (!fillcore(team)) {
                        ctx.error("Core does not exist", "Team " + team.name + " does not have a core");
                        return;
                    }

                    ctx.success("Filled core", "Filled core of team " + team.name);
                    DiscordLog.cheat("Filled core", ctx.author(), "Team: " + team.name + "\nMap:\n" + Strings.stripColors(Vars.state.map.name()));
                }
        );

        handler.register("convert", "<player> <unit>",
                data -> {
                    data.help = "Convert a player into a unit";
                    data.roles = new long[]{Roles.MOD, Roles.ADMIN};
                    data.category = "Cheats";
                },
                ctx -> {
                    UnitType unit = Vars.content.unit(ctx.args.get("unit"));
                    if (unit == null) {
                        ctx.error("Invalid unit type", ctx.args.get("unit") + " is not a valid unit type");
                    }
                    Player p = Query.findPlayerEntity(ctx.args.get("player"));
                    if (p == null) {
                        ctx.error("Player not found", ctx.args.get("player") + " is not online");
                    }

                    Unit oldunit = p.unit();
                    p.unit(unit.spawn(p.x, p.y));
                    Call.unitDespawn(oldunit);
                    ctx.success("Success", "Changed " + Utils.escapeEverything(p.name()) + "'s unit to " + p.unit().type.name);
                    DiscordLog.cheat("Changed unit", ctx.author(), "Target: " + Utils.escapeEverything(p.name()) + "\nUnit: " + p.unit().type.name);
                }
        );

        handler.register("team", "<player|all> <team>",
                data -> {
                    data.help = "Change a player's team";
                    data.roles = new long[]{Roles.MOD, Roles.ADMIN};
                    data.category = "Cheats";
                    data.aliases = new String[]{"changeteamid"};
                },
                ctx -> {
                    String query = ctx.args.get("player|all");
                    Iterable<Player> players = null;
                    if (query.equals("all")) {
                        players = Groups.player;
                    } else {
                        Player p = Query.findPlayerEntity(query);
                        if (p == null) {
                            ctx.error("No such player", query + " is not online");
                            return;
                        }
                        players = Seq.with(p);
                    }

                    Team team = Query.findTeam(ctx.args.get("team"));
                    if (team == null) {
                        ctx.error("No such team", "Team " + ctx.args.get("team") + " does not exist");
                        return;
                    }

                    int n = 0;
                    for (Player p : players) {
                        p.team(team);
                        n++;
                    }

                    ctx.success("Changed " + n + " players' team", "Team: " + team.name);
                    DiscordLog.cheat("Changed team", ctx.author(), "Target: " + query + "\nTeam: " + team.name);
                }
        );

        handler.register("setblock", "<player> <block> [rotation]",
                data -> {
                    data.help = "Create a block at the player's current location for the player's team";
                    data.roles = new long[]{Roles.MOD, Roles.ADMIN};
                    data.category = "Cheats";
                },
                ctx -> {
                    Player p = Query.findPlayerEntity(ctx.args.get("player"));
                    if (p == null) {
                        ctx.error("Player not found", "Target player is not online");
                        return;
                    }
                    Block block = Vars.content.block(ctx.args.get("block"));
                    if (block == null) {
                        ctx.error("Block not found", ctx.args.get("block") + " is not a valid block");
                        return;
                    }
                    int rotation = ctx.args.getInt("rotation", 0);

                    Tile tile = Vars.world.tile(p.tileX(), p.tileY());
                    if (tile == null) {
                        ctx.error("Tile is null", "Player is out of bounds");
                        return;
                    }
                    tile.setNet(block, p.team(), rotation);
                    ctx.success("Set block successfully", "Set block at (" + tile.x + ", " + tile.y + ") to " + block.name);
                    DiscordLog.cheat("Set block", ctx.author(), "Target: " + Utils.escapeEverything(p.name()) + "\nBlock: " + block.name);
                }
        );

        handler.register("spawn", "<player> <unit> [amount]",
                data -> {
                    data.help = "Spawn a given amount of units at a player's location";
                    data.roles = new long[]{Roles.MOD, Roles.ADMIN};
                    data.category = "Cheats";
                },
                ctx -> {
                    Player p = Query.findPlayerEntity(ctx.args.get("player"));
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
                        // hehe <- who, why just why
                        var data = Vars.state.teams.active.find(x -> x.team.id != p.team().id);
                        if (data != null) {
                            team = data.team;
                        }
                    }

                    for (int i = 0; i < amount; i++) {
                        unit.spawn(team, p);
                    }

                    ctx.success("Success", "Successfully spawned " + amount + " " + unit.localizedName);
                    DiscordLog.cheat("Spawn", ctx.author(), "Target: " + Utils.escapeEverything(p.name) + "\nUnit: `" + unit.name + "`");
                }
        );

        handler.register("killunits", "<team> <unit|all>",
                data -> {
                    data.help = "Kill all units of a team";
                    data.category = "Cheats";
                    data.roles = new long[]{Roles.MOD, Roles.ADMIN};
                },
                ctx -> {
                    Team team = Query.findTeam(ctx.args.get("team"));
                    if (team == null) {
                        ctx.error("No such team", "Team " + ctx.args.get("team") + " is not a team");
                        return;
                    }

                    UnitType type = Vars.content.unit(ctx.args.get("unit|all"));
                    if (type == null && !ctx.args.get("unit").equals("all")) {
                        ctx.error("No such unit", "That is not a valid unit");
                    }

                    int amount = 0;
                    for (Unit unit : Groups.unit) {
                        if (unit.team == team && (type == null || unit.type == type)) {
                            unit.kill();
                            amount += 1;
                        }
                    }

                    ctx.success("Killed " + amount + " units", "From team " + team.name);
                    DiscordLog.cheat("Kill units", ctx.author(), "Team: " + team.name + "\nUnits killed: " + amount + "\nUnit type: " + (type == null ? "all" : type.name));
                }
        );
    }

    @Override
    public void registerCommands(CommandHandler handler) {
        Utils.registerRankCommand(handler, "enablecheats", "[true/false]", Rank.MOD, "Enable cheats for all player", (args, player) -> {
            boolean shouldEnable = false;
            if (args.length == 1) {
                switch (args[0]) {
                    case "t", "true" -> shouldEnable = true;
                    case "f", "false" -> shouldEnable = false;
                    default -> {
                        player.sendMessage(GameMsg.error("Cheat", "Second argument to [" + GameMsg.CMD + "]/enable-cheats[] must be 'true' or 'false'"));
                        return;
                    }
                }
            }

            if (shouldEnable && cheatsEnabled) {
                player.sendMessage(GameMsg.error("Cheat", "Cheats already enabled"));
                return;
            }
            if (!shouldEnable && !cheatsEnabled) {
                player.sendMessage(GameMsg.error("Cheat", "Cheats already disabled"));
                return;
            }

            cheatsEnabled = shouldEnable;

            Call.sendMessage(GameMsg.info("Cheat", "Cheats are now " + (cheatsEnabled ? "enabled" : "disabled") + " for everyone by [white]" + player.name + "[" + GameMsg.INFO + "]!"));
        });

        handler.<Player>register("team", "<team> [player]", "Switch a player's team", (args, player) -> {
            if (!player.admin && !cheatsEnabled) {
                player.sendMessage(GameMsg.error("Cheat", "You must be an admin to use this command!"));
            }

            // TODO
        });


        handler.<Player>register("label", "<duration> <text...>", "[admin only] Create an in-world label at the current position.", (args, player) -> {
            if (!player.admin) {
                player.sendMessage(GameMsg.noPerms("Cheat"));
                return;
            }

            if (args[0].length() == 0 || args[1].length() == 0) {
                player.sendMessage("[scarlet]Invalid arguments provided.");
                return;
            }

            float x = player.getX();
            float y = player.getY();

            Tile targetTile = Vars.world.tileWorld(x, y);
            Call.label(args[1], Float.parseFloat(args[0]), targetTile.worldx(), targetTile.worldy());
        });
    }
}
