package mindustry.plugin.minimods;

import arc.Events;
import arc.graphics.Color;
import arc.struct.ObjectMap;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import arc.util.CommandHandler;
import arc.util.Log;
import arc.util.Structs;
import arc.util.Timer;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.content.Items;
import mindustry.content.UnitTypes;
import mindustry.entities.Units;
import mindustry.entities.abilities.Ability;
import mindustry.entities.units.UnitController;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.plugin.MiniMod;
import mindustry.plugin.database.Database;
import mindustry.plugin.discord.discordcommands.DiscordRegistrar;
import mindustry.plugin.utils.GameMsg;
import mindustry.plugin.utils.Rank;
import mindustry.plugin.utils.Utils;
import mindustry.type.Item;
import mindustry.type.ItemStack;
import mindustry.type.UnitType;
import mindustry.world.Block;
import mindustry.world.Tile;

import org.javacord.api.entity.message.embed.EmbedBuilder;

import java.io.IOException;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
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
        for (Team team : Team.all) {
            if (team.id <= 5 && team != Team.derelict) continue; // don't want player to control pets

            float[] hsv1 = team.color.toHsv(new float[3]);
            float[] hsv2 = color.toHsv(new float[3]);
            double err =
                    1.0 * (hsv1[0] - hsv2[0]) * (hsv1[0] - hsv2[0]) +
                            100.0 * (hsv1[1] - hsv2[1]) * (hsv1[1] - hsv2[1]) +
                            100.0 * (hsv1[2] - hsv2[2]) * (hsv1[2] - hsv2[2]);
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
            }

            var pet = Structs.find(pets, p -> p.name.equalsIgnoreCase(args[0]));
            if (pet == null) {
                player.sendMessage(GameMsg.error("Pet", "Pet '" + args[0] + "' doesn't exist"));
                return;
            }

            var alreadySpawned = spawnedPets.get(player.uuid(), new Seq<>());
            if (alreadySpawned.contains(pet.name)) {
                player.sendMessage(GameMsg.error("Pet", "Pet '" + args[0] + "' is already spawned!"));
                return;
            }
            alreadySpawned.add(pet.name);
            spawnedPets.put(player.uuid(), alreadySpawned);

            spawnPet(pet, player);
        });
    }

    private void spawnPet(PetDatabase.Pet pet, Player player) {
        Unit unit = pet.species.spawn(player.team(), player.x, player.y);

        // initialize controller
        UnitController controller = new PetController(player, pet.name);
        if (unit instanceof MechUnit) {
            MechUnit mechUnit = (MechUnit) unit;
            mechUnit.controller(controller);
        } else if (unit instanceof PayloadUnit) {
            var payloadUnit = (PayloadUnit) unit;
            payloadUnit.controller(controller);
        }
        controller.unit(unit);

        // spawn unit
        Call.spawnEffect(unit.x, unit.y, unit.rotation, unit.type);
        Events.fire(new EventType.UnitSpawnEvent(unit));

        // set team to correct team
        Team team = getTeam(pet.color);
        Timer.schedule(() -> {
            unit.team(team);
        }, 1f);
    }

    @Override
    public void registerDiscordCommands(DiscordRegistrar handler) {
        handler.register("addpet", "<species> <color:rrggbbaa> <name...>",
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
                    if (pets.length > maxPets(pd.rank)) {
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
                    pet.color = Color.valueOf(ctx.args.get("color:rrggbbaa"));
                    if (pet.color == null) {
                        pet.color = Color.black;
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
                        foodEaten += ":black_circle: " + pet.eatenCoal + "\n";
                    }
                    if (Structs.contains(foods, Items.copper)) {
                        foodEaten += ":brown_square: " + pet.eatenCopper + "\n";
                    }
                    if (Structs.contains(foods, Items.lead)) {
                        foodEaten += ":purple_square: " + pet.eatenLead + "\n";
                    }
                    if (Structs.contains(foods, Items.titanium)) {
                        foodEaten += ":blue_square: " + pet.eatenTitanium + "\n";
                    }
                    if (Structs.contains(foods, Items.thorium)) {
                        foodEaten += ":red_heart: " + pet.eatenThorium + "\n";
                    }
                    if (Structs.contains(foods, Items.beryllium)) {
                        foodEaten += ":green_square: " + pet.eatenBeryllium + "\n";
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

        handler.register("updatepet", "<color:rrggbbaa> <name...>",
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

                    pet.color = Color.valueOf(ctx.args.get("color:rrggbbaa"));
                    if (pet.color == null) {
                        pet.color = Color.black;
                    }

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
        } else if (type.flying) {
            return  new Item[] { Items.copper, Items.lead, Items.titanium, Items.thorium };
        } else {
            return  new Item[] { Items.copper, Items.lead, Items.titanium };
        }
    }

    static class PetTeam extends Team {
        public PetTeam(int id, String name, Color color) {
            super(id, name, color);
        }
    }

    class PetController implements UnitController {
        final String uuid;
        final Player player;
        final String name;
        /**
         * 1/s
         */
        int maxVel = 250;
        Unit unit;

        public PetController(Player player, String name) {
            this.player = player;
            this.uuid = player.uuid();
            this.name = name;
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
        }

        long prevTime = System.currentTimeMillis();
        boolean hasLabel = false;
        boolean isEating = false;
        // mining subset of eating
        Tile mining = null;

        @Override
        public void updateUnit() {
            if (unit == null) return;
            if (!Groups.player.contains(p -> p == player)) {
                Log.warn("pet owner disconnected :(");
                Call.unitDespawn(unit);
            }

            long dt = System.currentTimeMillis() - prevTime;
            prevTime += dt;

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
            float vx = 10f * ((player.x - (float) (40 * Math.cos(theta))) - unit.x);
            float vy = 10f * ((player.y - (float) (40 * Math.sin(theta))) - unit.y);
            if (vx * vx + vy * vy > maxVel * maxVel) {
                double mul = Math.sqrt(maxVel * maxVel / ((double) vx * vx + (double) vy * vy));
                vx *= mul;
                vy *= mul;
            }

            unit.x += vx * (dt) / 1000f;
            unit.y += vy * (dt) / 1000f;

            if (!isEating) {
                unit.rotation = unit.angleTo(player);
            }

            // boost
            unit.elevation(1f);

            // labels
            if (!hasLabel && Math.abs(vx) < 1 && Math.abs(vy) < 1) {
                Call.label(name, 1f, unit.x, unit.y + 5);
                hasLabel = true;
                Timer.schedule(() -> {
                    hasLabel = false;
                }, 1f);
            }

            handleFood(Math.abs(vx) < 1 && Math.abs(vy) < 1, dt);
        }

        private int itemsEaten = 0;
        private long lastAction = 0;
        protected void handleFood(boolean isStill, long dt) {
            // food
            if (!isEating && !unit.hasItem()) {
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

                Log.info("possible ores: " + tiles.size);
                if (tiles.size != 0) {
                    this.mining = tiles.random();
                    Log.info("mining: " + this.mining.drop());
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