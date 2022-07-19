package mindustry.plugin.minimods;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import arc.Events;
import arc.graphics.Color;
import arc.math.geom.Position;
import arc.struct.IntSet;
import arc.struct.Seq;
import arc.util.CommandHandler;
import arc.util.Log;
import arc.util.Structs;
import arc.util.Timer;
import mindustry.Vars;
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
import mindustry.plugin.MiniMod;
import mindustry.plugin.database.Database;
import mindustry.plugin.utils.GameMsg;
import mindustry.type.UnitType;

public class Pets implements MiniMod {
    class PetController implements UnitController {
        // TODO
        Player player;
        Unit unit;
        String name;

        public PetController(Player player, String name) {
            this.player = player;
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
        public void updateUnit() {
            if (unit == null) return;
            if (!Groups.player.contains(p -> p == player)) {
                Call.unitDespawn(unit);
            }
            unit.x(player.x);
            unit.y(player.y);
            unit.elevation(1f);
//            Call.label(name, 1f, unit.x, unit.y);
        }
    }

    class PetTeam extends Team {
        public PetTeam(int id, String name, Color color) {
            super(id, name, color);
        }
    }

    /** Creates a team if one does not already exist */
    private Team getTeam(Color color) {
        double minErr = Double.POSITIVE_INFINITY;
        Team bestTeam = null;
        for (Team team : Team.all) {
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
        handler.<Player>register("pet", "[name]", "Spawns a pet", (args, player) -> {
            var pets = PetDatabase.getPets(player.uuid());
            if (pets == null || pets.length == 9) {
                player.sendMessage(GameMsg.error("Pet", "You have no pets!"));
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

    public void spawnPet(PetDatabase.Pet pet, Player player) {
        Unit unit = pet.species.spawn(player.team(), player.x, player.y);
        if (unit instanceof MechUnit) {
            MechUnit mechUnit = (MechUnit)unit;
            mechUnit.controller = new PetController(player, "[#" + pet.color + "]" + pet.name);
        } else if (unit instanceof PayloadUnit) {
            var payloadUnit = (PayloadUnit)unit;
            payloadUnit.controller = new PetController(player, "[#" + pet.color + "]" + pet.name);
        }

        Call.spawnEffect(unit.x, unit.y, unit.rotation, unit.type);
        Events.fire(new EventType.UnitSpawnEvent(unit));

        Team team = getTeam(pet.color);
        Log.info("team: " +team.id);
        Timer.schedule(() -> {
            unit.team(team);
        }, 1f);
    }
}

class PetDatabase {
    public static class Pet {
        /** UUID of user who owns the pet */
        public String owner;
        public String name;
        public UnitType species;
        public Color color;

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

            Pet pet = new Pet();
            pet.name = name;
            pet.owner = owner;
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
            Log.info("pets: " +pets.size);
            return pets.toArray(Pet.class);
        } catch(SQLException e) {
            Log.err("pet error: " +e);
            return null;
        }
    }
}