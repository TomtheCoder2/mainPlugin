package mindustry.plugin.minimods;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.javacord.api.entity.message.embed.EmbedBuilder;

import arc.Events;
import arc.graphics.Color;
import arc.math.geom.Position;
import arc.struct.IntSet;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import arc.util.CommandHandler;
import arc.util.Log;
import arc.util.Structs;
import arc.util.Timer;
import mindustry.Vars;
import mindustry.content.UnitTypes;
import mindustry.entities.units.UnitController;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.game.Teams;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.MechUnit;
import mindustry.gen.PayloadUnit;
import mindustry.gen.Player;
import mindustry.gen.Unit;
import mindustry.net.Administration;
import mindustry.plugin.MiniMod;
import mindustry.plugin.database.Database;
import mindustry.plugin.discord.discordcommands.DiscordRegistrar;
import mindustry.plugin.utils.GameMsg;
import mindustry.plugin.utils.Rank;
import mindustry.plugin.utils.Utils;
import mindustry.type.UnitType;

public class Pets implements MiniMod {
    ObjectSet<String> havePets = new ObjectSet<>();

    class PetController implements UnitController {
        final String uuid;
        final Player player;
        final String name;
        /** 1/s */
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

        long prevTime = System.currentTimeMillis();

        @Override
        public void removed(Unit ignore) {
            havePets.remove(uuid);
        }

        boolean hasLabel = false;

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

            // movement
            double theta = player.unit().rotation * (Math.PI/180);
            float vx = 10f*((player.x - (float)(40*Math.cos(theta))) - unit.x);
            float vy = 10f*((player.y - (float)(40*Math.sin(theta))) - unit.y);
            if (vx*vx+vy*vy > maxVel*maxVel) {
                double mul = Math.sqrt(maxVel*maxVel/((double)vx*vx+(double)vy*vy));
                vx *= mul;
                vy *= mul;
            }
//            Log.info("vel: " + vx + " " + vy);

            unit.x += vx*(dt)/1000f;
            unit.y += vy*(dt)/1000f;
            unit.rotation = unit.angleTo(player);

            // boost
            unit.elevation(1f);

            // labels
            if (!hasLabel && Math.abs(vx) < 0.1 && Math.abs(vy) < 0.1) {
//                Log.info("name: " +name);
                Call.label(name, 1f, unit.x, unit.y+5);
                hasLabel = true;
                Timer.schedule(() -> {
                    hasLabel = false;
                }, 1f);
            }
        }
    }

    static class PetTeam extends Team {
        public PetTeam(int id, String name, Color color) {
            super(id, name, color);
        }
    }

    /** Creates a team if one does not already exist */
    private Team getTeam(Color color) {
        double minErr = Double.POSITIVE_INFINITY;
        Team bestTeam = null;
        for (Team team : Team.all) {
            if (team.id <= 5) continue; // don't want player to control pets

            float[] hsv1 = team.color.toHsv(new float[3]);
            float[] hsv2 = color.toHsv(new float[3]);
            double err =
                1.0*(hsv1[0] - hsv2[0]) * (hsv1[0] - hsv2[0]) +
                100.0*(hsv1[1] - hsv2[1]) * (hsv1[1] - hsv2[1]) +
                100.0*(hsv1[2] - hsv2[2]) * (hsv1[2] - hsv2[2]);
            if (err < minErr) {
                minErr = err;
                bestTeam = team;
            }
        }

        return bestTeam;
    }

    @Override
    public void registerCommands(CommandHandler handler) {
        handler.<Player>register("pet", "[name...]", "Spawns a pet", (args, player) -> {
            if (havePets.contains(player.uuid())) {
                player.sendMessage(GameMsg.error("Pet", "You already have a pet spawned!"));
            }

            var pets = PetDatabase.getPets(player.uuid());
            if (pets == null || pets.length == 9) {
                player.sendMessage(GameMsg.error("Pet", "You didn't create any pets. Join our Discord to make a pet."));
            }

            var pet = pets[(int)(Math.random() * pets.length)];
            if (args.length != 0) {
                pet = Structs.find(pets, p -> p.name.equalsIgnoreCase(args[0]));
                if (pet == null) {
                    player.sendMessage(GameMsg.error("Pet", "Pet '" + args[0] + "' doesn't exist"));
                }
            }

            spawnPet(pet, player);
        });
    }

    private void spawnPet(PetDatabase.Pet pet, Player player) {
        Unit unit = pet.species.spawn(player.team(), player.x, player.y);

        // initialize controller
        UnitController controller = new PetController(player, pet.name);
        if (unit instanceof MechUnit) {
            MechUnit mechUnit = (MechUnit)unit;
            mechUnit.controller = controller;
        } else if (unit instanceof PayloadUnit) {
            var payloadUnit = (PayloadUnit)unit;
            payloadUnit.controller = controller;
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
                    ctx.error("Too Many Pets", "You currently have " + pets.length + " pets, but a " + Rank.all[pd.rank].name+ " can only have " + maxPets(pd.rank) + " pets.");
                    return;
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

                int tier = 0;
                if (pet.species == UnitTypes.quad || pet.species == UnitTypes.scepter || pet.species == UnitTypes.vela || pet.species == UnitTypes.arkyid) {
                    tier = 4;
                } else if (pet.species == UnitTypes.fortress || pet.species == UnitTypes.quasar || pet.species == UnitTypes.spiroct || pet.species == UnitTypes.zenith || pet.species == UnitTypes.mega) {
                    tier = 3;
                } else if (pet.species == UnitTypes.mace || pet.species == UnitTypes.pulsar || pet.species == UnitTypes.atrax || pet.species == UnitTypes.horizon || pet.species == UnitTypes.poly) {
                    tier = 2;
                } else if (pet.species == UnitTypes.dagger || pet.species == UnitTypes.nova || pet.species == UnitTypes.crawler || pet.species == UnitTypes.flare || pet.species == UnitTypes.mono) {
                    tier = 1;
                } else {
                    ctx.error("Unsupport Species", "Species must be T1-4, not be a naval unit, and not be the antumbra");
                    return;
                }

                if (tier > maxTier(pd.rank)) {
                    ctx.error("Insufficient Rank", pet.species.name + " is tier " + tier + ", but a " + Rank.all[pd.rank].name+ " can only have tier " + maxTier(pd.rank) + " pets.");
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

                ctx.sendEmbed(new EmbedBuilder()
                    .setColor(new java.awt.Color(pet.color.r, pet.color.g, pet.color.b))
                    .setTitle("Pet: " +pet.name)
                    .addInlineField("Owner", ownerName)
                    .addInlineField("Species", pet.species.localizedName)
                    .addInlineField("Color", "#" + pet.color.toString())
                );
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

    private int maxPets(int rank) {
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

    private int maxTier(int rank) {
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
}

class PetDatabase {
    public static class Pet {
        /** UUID of user who owns the pet */
        public String owner;
        public String name;
        public UnitType species;
        public Color color;

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
            return pet;
        }
    }

    public static Pet[] getPets(String owner) {
        String sql = "SELECT * FROM pets WHERE owner = ?";
        try {
            PreparedStatement ps = Database.conn.prepareStatement(sql);
            ps.setString(1, owner);
            
            ResultSet rs = ps.executeQuery();
            Seq<Pet> pets = new Seq<>();
            while (rs.next()) {
                Log.info("new pet");
                Pet pet = Pet.fromSQL(rs);
                pets.add(pet);
            }
            return pets.toArray(Pet.class);
        } catch(SQLException e) {
            Log.err("pet error: " +e);
            return null;
        }
    }

    /** Inserts a new pet */
    public static void addPet(Pet pet) {
        String sql = "INSERT INTO pets VALUES (?, ?, ?, ?)";
        try {
            PreparedStatement pstmt = Database.conn.prepareStatement(sql);
            pstmt.setString(1, pet.owner);
            pstmt.setString(2, pet.name);
            pstmt.setString(3, pet.species.name);
            pstmt.setString(4, pet.color.toString());
            
            pstmt.executeUpdate();
        } catch (SQLException e) {
            Log.err(e);
        }
    }

    /** Updates a given pet, using name and owner as identifiers */
    public static void updatePet(Pet pet) {
        String sql = "UPDATE pets SET color = ?, species = ? WHERE owner = ? AND name = ?";
        try {
            PreparedStatement pstmt = Database.conn.prepareStatement(sql);
            pstmt.setString(1, pet.color.toString());
            pstmt.setString(2, pet.species.name);
            pstmt.setString(3, pet.owner);
            pstmt.setString(4, pet.name);
            
            pstmt.executeUpdate();
        } catch (SQLException e) {
            Log.err(e);
        }        
    }

    /** Removes a pet */
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
}