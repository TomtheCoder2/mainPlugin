package mindustry.plugin.minimods;

import arc.Events;
import arc.graphics.Color;
import arc.struct.ObjectMap;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import arc.util.CommandHandler;
import arc.util.Log;
import arc.util.Reflect;
import arc.util.Structs;
import arc.util.Timer;
import mindustry.Vars;
import mindustry.ai.types.MinerAI;
import mindustry.content.Blocks;
import mindustry.content.Items;
import mindustry.content.UnitTypes;
import mindustry.entities.Units;
import mindustry.entities.abilities.Ability;
import mindustry.entities.units.UnitController;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.game.Teams;
import mindustry.gen.*;
import mindustry.plugin.MiniMod;
import mindustry.plugin.database.Database;
import mindustry.plugin.discord.DiscordVars;
import mindustry.plugin.discord.discordcommands.DiscordRegistrar;
import mindustry.plugin.utils.GameMsg;
import mindustry.plugin.utils.Rank;
import mindustry.plugin.utils.Utils;
import mindustry.type.Item;
import mindustry.type.ItemStack;
import mindustry.type.UnitType;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.defense.BuildTurret;
import mindustry.world.blocks.defense.turrets.BaseTurret;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import mindustry.world.blocks.defense.turrets.PointDefenseTurret;
import mindustry.world.blocks.defense.turrets.Turret;
import mindustry.world.blocks.storage.CoreBlock;

import org.javacord.api.entity.message.embed.EmbedBuilder;

import java.io.IOException;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.lang.reflect.Field;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class Pets implements MiniMod {
    ObjectMap<String, Seq<String>> spawnedPets = new ObjectMap<>();

    /**
     * Creates a team if one does not already exist
     */
    private Team getTeam(Color color) {
        double minErr = Double.POSITIVE_INFINITY;
        Team bestTeam = null;

        // hsv2 = hsv of the desired color
        float[] hsv2 = color.toHsv(new float[3]);
        if (hsv2[2] <= 0.3 && hsv2[1] <= 0.15) {
            return Team.derelict;
        }

        for (Team team : Team.all) {
            if (team.id <= 5 && team != Team.derelict) continue; // don't want player to control pets

            float[] hsv1 = team.color.toHsv(new float[3]);
            double err =
                    1.0 * (hsv1[0] - hsv2[0]) * (hsv1[0] - hsv2[0]) +
                            200.0 * 200.0 * (hsv1[1] - hsv2[1]) * (hsv1[1] - hsv2[1]) +
                            200.0 * 200.0 * (hsv1[2] - hsv2[2]) * (hsv1[2] - hsv2[2]);

            if (err < minErr) {
                minErr = err;
                bestTeam = team;
            }
        }

        return bestTeam;
    }


    @Override
    public void registerEvents() {
        for (UnitType unit : Vars.content.units()) {
            if (unit.itemCapacity <= 0) {
                unit.itemCapacity = 10;
            }
        }
    }

    @Override
    public void registerCommands(CommandHandler handler) {
        handler.<Player>register("pet", "[name...]", "Spawns a pet", (args, player) -> {
            var pets = PetDatabase.getPets(player.uuid());
            if (pets == null || pets.length == 0) {
                player.sendMessage(GameMsg.error("Pet", "You didn't create any pets. Join our Discord to make a pet."));
                return;
            }

            if (args.length == 0) {
                player.sendMessage(GameMsg.custom("Pet", "sky", Seq.with(pets).toString(", ", p -> p.name)));
                return;
            }

            var pet = Structs.find(pets, p -> p.name.equalsIgnoreCase(args[0]));
            if (pet == null) {
                player.sendMessage(GameMsg.error("Pet", "Pet '" + args[0] + "' doesn't exist"));
                return;
            }

            var alreadySpawned = spawnedPets.get(player.uuid(), new Seq<>());
            if (alreadySpawned.contains(pet.name)) {
                player.sendMessage(GameMsg.error("Pet", "Pet '" + args[0] + "' is already spawned"));
                return;
            }

            if (!spawnPet(pet, player)) {
                player.sendMessage(GameMsg.error("Pet", "Pet [#" + pet.color.toString().substring(0, 6) + "]" + pet.name + "[scarlet] can't be spawned here"));
                return;
            }

            alreadySpawned.add(pet.name);
            spawnedPets.put(player.uuid(), alreadySpawned);
            player.sendMessage(GameMsg.success("Pet", "Pet [#" + pet.color.toString().substring(0, 6) + "]" + pet.name + "[green] successfully spawned!"));
        });
    }

    private boolean spawnPet(PetDatabase.Pet pet, Player player) {
        // correct team can't be set instantly, otherwise pet won't spawn
        Unit unit = pet.species.spawn(player.team(), player.x, player.y);
        if (unit == null) {
            return false;
        }

        // initialize controller
        Team team = getTeam(pet.color);
        UnitController controller = new PetController(player, pet.name, pet.color, team);
        unit.controller(controller);
        controller.unit(unit);

//        Call.spawnEffect(unit.x, unit.y, unit.rotation, unit.type);
//        Events.fire(new EventType.UnitSpawnEvent(unit));
        return true;
    }

    @Override
    public void registerDiscordCommands(DiscordRegistrar handler) {
        handler.register("addpet", "<species> <color:rrggbb> <name...>",
                data -> {
                    data.help = "Create a new pet";
                    data.category = "Pets";
                },
                ctx -> {
                    Database.Player pd = Database.getDiscordData(ctx.author().getId());
                    if (pd == null) {
                        ctx.error("Not in database", "You have not linked your discord account. Use /redeem to link.");
                        return;
                    }

                    var pets = PetDatabase.getPets(pd.uuid);
                    if (pets.length >= maxPets(pd.rank)) {
                        ctx.error("Too Many Pets", "You currently have " + pets.length + " pets, but a " + Rank.all[pd.rank].name + " can only have " + maxPets(pd.rank) + " pets.");
                        return;
                    }
                    for (var pet : pets) {
                        if (pet.name.equalsIgnoreCase(ctx.args.get("name"))) {
                            ctx.error("Pet already exists", "You already have a pet named '" + pet.name + "'");
                            return;
                        }
                    }

                    var pet = new PetDatabase.Pet(pd.uuid, ctx.args.get("name"));
                    pet.color = Utils.parseColor(ctx.args.get("color:rrggbb"));
                    if (pet.color == null) {
                        ctx.error("Not a valid color", "Make sure you are using `rrggbb` format");
                        return;
                    }
                    pet.species = Vars.content.units().find(u -> u.name.equalsIgnoreCase(ctx.args.get("species")));
                    if (pet.species == null) {
                        ctx.error("Invalid Species", "'" + ctx.args.get("species") + "' is not a valid unit");
                        return;
                    }

                    int tier = tierOf(pet.species);
                    if (tier < 0) {
                        ctx.error("Unsupport Species", "Species must be T1-4, not be a naval unit, and not be the antumbra");
                        return;
                    }

                    if (tier > maxTier(pd.rank)) {
                        ctx.error("Insufficient Rank", pet.species.name + " is tier " + tier + ", but a " + Rank.all[pd.rank].name + " can only have tier " + maxTier(pd.rank) + " pets.");
                        return;
                    }

                    PetDatabase.addPet(pet);
                    ctx.success("Created pet", "Successfully created " + pet.name);
                }
        );

        handler.register("pet", "<name...>",
                data -> {
                    data.help = "Show pet information";
                    data.category = "Pets";
                },
                ctx -> {
                    Database.Player pd = Database.getDiscordData(ctx.author().getId());
                    if (pd == null) {
                        ctx.error("Not in database", "You have not linked your discord account. Use /redeem to link.");
                        return;
                    }

                    String name = ctx.args.get("name");
                    var pets = PetDatabase.getPets(pd.uuid);
                    var pet = Structs.find(pets, p -> p.name.equalsIgnoreCase(name));
                    if (pet == null) {
                        ctx.error("No such pet", "You don't have a pet named '" + name + "'");
                        return;
                    }

                    String ownerName = "unknown";
                    var info = Vars.netServer.admins.getInfo(pd.uuid);
                    if (info != null) {
                        ownerName = Utils.escapeEverything(info.lastName);
                    }

                    String foodEaten = "";
                    Item[] foods = possibleFoods(pet.species);
                    if (Structs.contains(foods, Items.coal)) {
                        foodEaten += DiscordVars.emoji("coal").getMentionTag() + " " + pet.eatenCoal + "\n";
                    }
                    if (Structs.contains(foods, Items.copper)) {
                        foodEaten += DiscordVars.emoji("copper").getMentionTag() + " " + pet.eatenCopper + "\n";
                    }
                    if (Structs.contains(foods, Items.lead)) {
                        foodEaten += DiscordVars.emoji("lead").getMentionTag() + " " + pet.eatenLead + "\n";
                    }
                    if (Structs.contains(foods, Items.titanium)) {
                        foodEaten += DiscordVars.emoji("titanium").getMentionTag() + " " + pet.eatenTitanium + "\n";
                    }
                    if (Structs.contains(foods, Items.thorium)) {
                        foodEaten += DiscordVars.emoji("thorium").getMentionTag() + " " + pet.eatenThorium + "\n";
                    }
                    if (Structs.contains(foods, Items.beryllium)) {
                        foodEaten += DiscordVars.emoji("beryllium").getMentionTag() + " " + pet.eatenBeryllium + "\n";
                    }

                    ctx.sendEmbed(new EmbedBuilder()
                            .setColor(new java.awt.Color(pet.color.r, pet.color.g, pet.color.b))
                            .setTitle("Pet: " + pet.name)
                            .addInlineField("Owner", ownerName)
                            .addInlineField("Species", pet.species.localizedName)
                            .addInlineField("Color", "#" + pet.color.toString())
                            .addInlineField("Food Eaten", foodEaten.trim())
                    );
                }
        );

        handler.register("updatepet", "<color:rrggbb> <name...>",
                data -> {
                    data.help = "Update an existing pet";
                    data.category = "Pets";
                },
                ctx -> {
                    Database.Player pd = Database.getDiscordData(ctx.author().getId());
                    if (pd == null) {
                        ctx.error("Not in database", "You have not linked your discord account. Use /redeem to link.");
                        return;
                    }                    

                    String name = ctx.args.get("name");
                    var pets = PetDatabase.getPets(pd.uuid);
                    var pet = Structs.find(pets, p -> p.name.equalsIgnoreCase(name));
                    if (pet == null) {
                        ctx.error("No such pet", "You don't have a pet named '" + name + "'");
                        return;
                    }

                    pet.color = Utils.parseColor(ctx.args.get("color:rrggbb"));
                    if (pet.color == null) {
                        ctx.error("Not a valid color", "Make sure you are using `rrggbb` format");
                        return;
                    }

                    PetDatabase.updatePet(pet);
                    ctx.success("Successfully updated " + pet.name, "Changed their color to " + pet.color.toString());
                }
        );

        handler.register("removepet", "<name...>",
                data -> {
                    data.help = "Remove a pet";
                    data.category = "Pets";
                },
                ctx -> {
                    Database.Player pd = Database.getDiscordData(ctx.author().getId());
                    if (pd == null) {
                        ctx.error("Not in database", "You have not linked your discord account. Use /redeem to link.");
                        return;
                    }

                    String name = ctx.args.get("name");
                    var pets = PetDatabase.getPets(pd.uuid);
                    // find the pet, in case the player uses wrong casing
                    var pet = Structs.find(pets, p -> p.name.equalsIgnoreCase(name));
                    if (pet == null) {
                        ctx.error("No such pet", "You don't have a pet named '" + name + "'");
                        return;
                    }

                    PetDatabase.removePet(pd.uuid, pet.name);
                    ctx.success("Success", "Successfully deleted " + pet.name);
                }
        );
    }

    protected static int maxPets(int rank) {
        if (rank <= 1) {
            return 0;
        } else if (rank == 2) {
            return 1;
        } else if (rank == 3) {
            return 2;
        } else {
            return 3;
        }
    }

    protected static int tierOf(UnitType type) {
        if (type == UnitTypes.quad || type == UnitTypes.scepter || type == UnitTypes.vela || type == UnitTypes.arkyid) {
            return 4;
        } else if (type == UnitTypes.fortress || type == UnitTypes.quasar || type == UnitTypes.spiroct || type == UnitTypes.zenith || type == UnitTypes.mega) {
            return 3;
        } else if (type == UnitTypes.mace || type == UnitTypes.pulsar || type == UnitTypes.atrax || type == UnitTypes.horizon || type == UnitTypes.poly) {
            return 2;
        } else if (type == UnitTypes.dagger || type == UnitTypes.nova || type == UnitTypes.crawler || type == UnitTypes.flare || type == UnitTypes.mono) {
            return 1;
        }
        return -1;
    }

    protected static int maxTier(int rank) {
        if (rank <= 1) {
            return 0;
        } else if (rank <= 3) {
            return 1;
        } else if (rank == 4) {
            return 2;
        } else if (rank == 5) {
            return 3;
        } else {
            return 4;
        }
    }

    protected static Item[] possibleFoods (UnitType type) {
        if (type == UnitTypes.crawler) {
            return new Item[] { Items.coal };
        } else if (type == UnitTypes.quasar || type == UnitTypes.pulsar) {
            return new Item[] { Items.beryllium, Items.titanium, Items.thorium };
        } else if (type.flying && type != UnitTypes.quad) {
            return  new Item[] { Items.copper, Items.lead, Items.titanium, Items.thorium };
        } else {
            return  new Item[] { Items.copper, Items.lead, Items.titanium };
        }
    }

    protected static int rank(PetDatabase.Pet pet) {
        var items = possibleFoods(pet.species);
        long min = Long.MAX_VALUE;
        for (var item : items) {
            long value = switch(item.name) {
                case "coal" -> pet.eatenCoal;
                case "copper" -> pet.eatenCopper;
                case "lead" -> pet.eatenLead;
                case "titanium" -> pet.eatenTitanium;
                case "thorium" -> pet.eatenThorium;
                case "beryllium" -> pet.eatenBeryllium;
                default -> 0;
            };
            if (value < min) {
                min = value;
            }
        }

        if (min < 100) {
            return 0;
        } else if (min < 250) {
            return 1;
        } else if (min < 500) {
            return 2;
        } else if (min < 1000) {
            return 3;
        } else if (min < 5000) {
            return 4;
        } else {
            return 5;
        }
    }

    class PetController implements UnitController {
        final String uuid;
        final Player player;
        final String name;
        final Team unitTeam;
        final Color color;
        /**
         * 1/s
         */
        int maxVel = 250;
        Unit unit;

        public PetController(Player player, String name, Color color, Team unitTeam) {
            this.player = player;
            this.uuid = player.uuid();
            this.name = name;
            this.color = color;
            this.unitTeam = unitTeam;
        }

        @Override
        public void unit(Unit unit) {
            this.unit = unit;
        }

        @Override
        public Unit unit() {
            return unit;
        }

        @Override
        public void removed(Unit ignore) {
            var pets = spawnedPets.get(uuid);
            pets.remove(name);

            if (player.con != null && player.con.isConnected()) {
                player.sendMessage(GameMsg.custom("Pet", "yellow", "Your pet [#" + color.toString().substring(0,6) + "]" + name + "[yellow] died! " +
                    "Make sure you are spawning any ground pets on empty tiles."
                ));
            }
        }

        private Unit closePet() {
            for (Unit unit : Groups.unit) {
                if (isPet(unit)
                    && unit.dst(this.unit) <= 15 * Vars.tilesize
                    && unit.type == this.unit.type
                    && unit != this.unit
                    && petOwner(unit) != uuid) {
                    return unit;
                }
            }
            return null;
        }

        /** Returns whether the pet is near a turret or friendly unit */
        private boolean isNearDanger() {
            for (var data : Vars.state.teams.active) {
                if (data.turretTree == null) {
                    continue;
                }
                final boolean[] shouldHide = new boolean[1];
                data.turretTree.intersect(unit.x - 500f, unit.y - 500f, 1000f, 1000f, turret -> {
                    if (!(turret.block() instanceof BaseTurret)) {
                        Log.warn("a turret that isn't a turret: " + turret.getClass().getCanonicalName());
                    }
                    BaseTurret bt = (BaseTurret)turret.block();
                    boolean targetAir = true;
                    if (bt instanceof Turret) {
                        targetAir = ((Turret)bt).targetAir;
                    }
                    if (bt instanceof PointDefenseTurret || bt instanceof BuildTurret) {
                        targetAir = false;
                    }

                    if (targetAir && unit.dst(turret) <= bt.range + Vars.tilesize) {
                        shouldHide[0] = true;
                    } 
                });
                if (shouldHide[0]) {
                    return true;
                }
            }

            for (Unit enemyUnit : Groups.unit) {
                if (enemyUnit.team() == player.team() && !enemyUnit.isPlayer() &&
                    !(enemyUnit.controller() instanceof PetController) && 
                    !(enemyUnit.controller() instanceof MinerAI) && // exclude mono
                    enemyUnit.type.targetAir &&
                    enemyUnit.type != UnitTypes.mega && enemyUnit.type != UnitTypes.poly) { // exclude mega & poly
                        if (unit.dst(enemyUnit) <= enemyUnit.range() + Vars.tilesize) {
                            return true;
                        }
                    }
            }

            return false;
        }

        private boolean isPet(Unit unit) {
            return unit.controller() instanceof PetController;
        }

        private String petOwner(Unit unit) {
            PetController controller = (PetController)unit.controller();
            return controller.uuid;
        }

        private CoreBlock.CoreBuild closeCore() {
            var cores = Vars.state.teams.cores(player.team());
            if (cores == null || cores.size == 0) return null;
            CoreBlock.CoreBuild closestCore = null;
            float closestDst = 8 * Vars.tilesize;
            for (var core : cores) {
                if (unit.dst(core.x, core.y) <= closestDst) {
                    closestDst = unit.dst(core.x, core.y);
                    closestCore = core;
                }
            }
            return closestCore;
        }

        long prevTime = System.currentTimeMillis();
        boolean hasLabel = false;
        boolean isEating = false;
        // mining subset of eating
        Tile mining = null;

        // for friends
        float friendRotDir = 0;

        @Override
        public void updateUnit() {
            if (unit == null) return;
            if (!Groups.player.contains(p -> p == player)) {
                Log.warn("pet owner disconnected :(");
                Call.unitDespawn(unit);
            }

            long dt = System.currentTimeMillis() - prevTime;
            prevTime += dt;

            // set team
            if (isNearDanger()) {
                // stealth mode
                unit.team = Team.derelict;
            } else {
                unit.team = unitTeam;
            }

            // keep pet alive
            unit.health(unit.maxHealth);
            unit.shield(0);
            unit.shieldAlpha = 0;
            unit.armor(1000f);

            // determine angle behind which to set
            double theta = player.unit().rotation;
            var allPets = spawnedPets.get(uuid);
            if (allPets.size == 2) {
                int idx = allPets.indexOf(name);
                theta = theta - 25 + 50 * idx;
            } else if (allPets.size == 3) {
                int idx = allPets.indexOf(name);
                theta = theta - 45 + 45 * idx;
            }
            theta *= (Math.PI / 180);

            // movement
            float targetx = (player.x - (float) (40 * Math.cos(theta)));
            float targety = (player.y - (float) (40 * Math.sin(theta)));
            if (closeCore() != null) {
                var core = closeCore();
                double thetaCore = core.angleTo(targetx, targety);
                targetx = core.x + 8 * (float)Math.cos(thetaCore);
                targety = core.y + 8 * (float)Math.sin(thetaCore);
            }

            float vx = 10f * (targetx - unit.x);
            float vy = 10f * (targety - unit.y);
            if (vx * vx + vy * vy > maxVel * maxVel) {
                double mul = Math.sqrt(maxVel * maxVel / ((double) vx * vx + (double) vy * vy));
                vx *= mul;
                vy *= mul;
            }

            unit.x += vx * (dt) / 1000f;
            unit.y += vy * (dt) / 1000f;

            // rotation
            if (!isEating) {
                Unit closePet = closePet();
                if (closePet != null) {
                    float targetAngle = unit.angleTo(closePet);
                    if (Math.abs(unit.rotation - targetAngle) >= 30) {
                        unit.rotation += (targetAngle - unit.rotation) * 0.25f;
                    } else {
                        if (unit.rotation - targetAngle >= 25) {
                            friendRotDir = -1;
                        } else if (unit.rotation - targetAngle <= -25) {
                            friendRotDir = 1;
                        }

                        if (friendRotDir > 0) {
                            unit.rotation += 2*360 * dt / 1000;
                        } else {
                            unit.rotation -= 2*360 * dt / 1000;
                        }
                    }
                } else {
                    unit.rotation = unit.angleTo(player);
                }
            }

            // boost
            unit.elevation(1f);

            // labels
            boolean isStill = Math.abs(vx) < 2 && Math.abs(vy) < 2;
            if (!hasLabel && isStill) {
                Call.label("[#" + color.toString().substring(0, 6) + "]" + name, 1f, unit.x, unit.y + unit.hitSize()/2 + Vars.tilesize);
                hasLabel = true;
                Timer.schedule(() -> {
                    hasLabel = false;
                }, 1f);
            }

            handleFood(isStill, dt);
        }

        private int itemsEaten = 0;
        private long lastAction = 0;
        protected void handleFood(boolean isStill, long dt) {
            // food
            if (!isEating && !unit.hasItem() && isStill) {
                int startX = unit.tileX() - 5;
                int startY = unit.tileY() - 5;
                Seq<Tile> tiles = new Seq<>();
                for (int x = startX; x < startX + 10; x++) {
                    for (int y = startY; y < startY + 10; y++) {                        
                        Tile tile = Vars.world.tiles.get(x, y);
                        if (tile == null) continue;
                        if (tile.drop() != null && Structs.contains(possibleFoods(unit.type), tile.drop()) && tile.block() == Blocks.air) {
                            tiles.add(tile);
                        }
                    }
                }

//                Log.info("possible ores: " + tiles.size);
                if (tiles.size != 0) {
                    this.mining = tiles.random();
//                    Log.info("mining: " + this.mining.drop());
                    isEating = true;
                }
            }

            if (isEating) {
                // if `mining` is non-null, mine that tile
                // additionally increase stack 4/s (1/250ms)
                if (mining != null) {
                    unit.mineTile(mining);
                    if (System.currentTimeMillis() - lastAction > 250) {
                        if (unit.stack == null || unit.stack.item != mining.drop()) {
                            unit.stack = new ItemStack(mining.drop(), 1);
                        }
                        if (unit.stack.amount < unit.itemCapacity())
                            unit.stack.amount += 1;
                        lastAction = System.currentTimeMillis();
                    }
                }

                // if mine is too far away, skip to eating
                if (mining != null && (mining.x - unit.tileX())*(mining.x - unit.tileX()) + (mining.y - unit.tileY())*(mining.y - unit.tileY()) >= 10*10) {
                    mining = null;
                    if (unit.stack != null) {
                        itemsEaten = unit.stack.amount;
                    } else {
                        itemsEaten = 0;
                    }
                }
                if (unit.stack != null && unit.stack.amount >= unit.itemCapacity()) {
                    mining = null;
                    itemsEaten = unit.stack.amount;
                }

                // eat
                if (unit.stack != null && mining == null) {
                    // eat one item every 500ms
                    if (System.currentTimeMillis() - lastAction > 500) {
                        unit.stack.amount -= 1;
                        unit.heal();
                        lastAction = System.currentTimeMillis();
                    }

                    if (unit.stack.amount == 0) {
                        var item = unit.stack.item;
                        unit.clearItem();                       
                        isEating = false;

                        // update database
                        int amount = itemsEaten;
                        var pet = Structs.find(PetDatabase.getPets(uuid), p -> p.name.equals(this.name));
                        if (pet == null) { // pet was deleted
                            Call.unitDespawn(unit);
                            return;
                        }
                        if (item == Items.coal) {
                            pet.eatenCoal += amount;
                        } else if (item == Items.copper) {
                            pet.eatenCopper += amount;
                        } else if (item == Items.lead) {
                            pet.eatenLead += amount;
                        } else if (item == Items.titanium) { 
                            pet.eatenTitanium += amount;
                        } else if (item == Items.thorium) {
                            pet.eatenThorium += amount;
                        } else if (item == Items.beryllium) {
                            pet.eatenBeryllium += amount;
                        }
                        PetDatabase.updatePet(pet);
                    }
                }


                // rotation
                if (mining != null)
                    unit.rotation = unit.angleTo(mining);
                else
                    // spin 360 degrees/sec
                    unit.rotation += dt * 360f / 1000f;

            }
        }
    }
}

class PetDatabase {
    public static Pet[] getPets(String owner) {
        String sql = "SELECT * FROM pets WHERE owner = ?";
        try {
            PreparedStatement ps = Database.conn.prepareStatement(sql);
            ps.setString(1, owner);

            ResultSet rs = ps.executeQuery();
            Seq<Pet> pets = new Seq<>();
            while (rs.next()) {
                Pet pet = Pet.fromSQL(rs);
                pets.add(pet);
            }
            return pets.toArray(Pet.class);
        } catch (SQLException e) {
            Log.err("pet error: " + e);
            return null;
        }
    }

    /**
     * Inserts a new pet
     */
    public static void addPet(Pet pet) {
        String sql = "INSERT INTO pets VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try {
            PreparedStatement pstmt = Database.conn.prepareStatement(sql);
            pstmt.setString(1, pet.owner);
            pstmt.setString(2, pet.name);
            pstmt.setString(3, pet.species.name);
            pstmt.setString(4, pet.color.toString());
            pstmt.setLong(5, pet.eatenCoal);
            pstmt.setLong(6, pet.eatenCopper);
            pstmt.setLong(7, pet.eatenLead);
            pstmt.setLong(8, pet.eatenTitanium);
            pstmt.setLong(9, pet.eatenThorium);
            pstmt.setLong(10, pet.eatenBeryllium);

            pstmt.executeUpdate();
        } catch (SQLException e) {
            Log.err(e);
        }
    }

    /**
     * Updates a given pet, using name and owner as identifiers
     */
    public static void updatePet(Pet pet) {
        String sql = "UPDATE pets SET color = ?, species = ?, eatenCoal = ?, eatenCopper = ?, eatenLead = ?, eatenTitanium = ?, eatenThorium = ?, eatenBeryllium = ? WHERE owner = ? AND name = ?";
        try {
            PreparedStatement pstmt = Database.conn.prepareStatement(sql);
            pstmt.setString(1, pet.color.toString());
            pstmt.setString(2, pet.species.name);
            pstmt.setLong(3, pet.eatenCoal);
            pstmt.setLong(4, pet.eatenCopper);
            pstmt.setLong(5, pet.eatenLead);
            pstmt.setLong(6, pet.eatenTitanium);
            pstmt.setLong(7, pet.eatenThorium);
            pstmt.setLong(8, pet.eatenBeryllium);
            pstmt.setString(9, pet.owner);
            pstmt.setString(10, pet.name);

            pstmt.executeUpdate();
        } catch (SQLException e) {
            Log.err(e);
        }
    }

    /**
     * Removes a pet
     */
    public static void removePet(String owner, String name) {
        String sql = "DELETE FROM pets WHERE owner = ? AND name = ?";
        try {
            PreparedStatement pstmt = Database.conn.prepareStatement(sql);
            pstmt.setString(1, owner);
            pstmt.setString(2, name);

            pstmt.executeUpdate();
        } catch (SQLException e) {
            Log.err(e);
        }
    }

    public static class Pet {
        /**
         * UUID of user who owns the pet
         */
        public String owner;
        public String name;
        public UnitType species;
        public Color color;
        
        public long eatenCoal;
        public long eatenCopper;
        public long eatenLead;
        public long eatenTitanium;
        public long eatenThorium;
        public long eatenBeryllium;

        public Pet(String owner, String name) {
            this.owner = owner;
            this.name = name;
        }

        public static Pet fromSQL(ResultSet rs) throws SQLException {
            String name = rs.getString("name");
            String owner = rs.getString("owner");
            String colorStr = rs.getString("color");
            String speciesStr = rs.getString("species");
            UnitType species = Vars.content.units().find(u -> u.name.equals(speciesStr));
            if (species == null) {
                return null;
            }
            Color color = Color.valueOf(colorStr);

            Pet pet = new Pet(owner, name);
            pet.color = color;
            pet.species = species;

            pet.eatenCopper = rs.getLong("eatenCopper");
            pet.eatenLead = rs.getLong("eatenLead");
            pet.eatenTitanium = rs.getLong("eatenTitanium");
            pet.eatenThorium = rs.getLong("eatenThorium");
            pet.eatenCoal = rs.getLong("eatenCoal");
            pet.eatenBeryllium = rs.getLong("eatenBeryllium");

            return pet;
        }
    }
}