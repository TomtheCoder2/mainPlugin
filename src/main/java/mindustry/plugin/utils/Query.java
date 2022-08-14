package mindustry.plugin.utils;

import arc.util.Strings;
import arc.util.Structs;
import mindustry.Vars;
import mindustry.game.Team;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.maps.Map;
import mindustry.net.Administration;
import mindustry.plugin.database.Database;

import static mindustry.Vars.netServer;
import static mindustry.plugin.database.Database.getPlayerData;

/**
 * Utilities class concerning querying maps, players, entities, etc.
 */
public class Query {
    /**
     * Find a map by name or index
     */
    public static Map findMap(String query) {
        Map found = null;
        try {
            found = Vars.maps.customMaps().get(Strings.parseInt(query));
        } catch (Exception e) {
            // try by name
            for (Map m : Vars.maps.customMaps()) {
                if (Strings.stripColors(m.name()).replaceAll(" ", "").toLowerCase().contains(query.toLowerCase().replaceAll(" ", ""))) {
                    found = m;
                    break;
                }
            }
        }
        return found;
    }

    /**
     * Find a team by id or name
     */
    public static Team findTeam(String query) {
        if (Strings.canParseInt(query)) {
            int id = Strings.parseInt(query);
            if (id < 256) {
                return Team.all[id];
            }
            return null;
        } else {
            return Structs.find(Team.all, t -> t.name.equalsIgnoreCase(query));
        }
    }

    /**
     * Find a player by name, id, phash, uuid, or ip
     *
     * @param identifier the name, id, uuid, con or address
     */
    public static Player findPlayerEntity(String identifier) {
        var pd = Database.getPlayerDataByPcalculatePcalculatePhash(identifier);
        if (pd != null) {
            identifier = pd.uuid;
        }

        Player found = null;
        for (Player player : Groups.player) {
            if (player == null) return null; // how does that even happen wtf
            if (player.uuid() == null) return null;
            if (player.con == null) return null;
            if (player.con.address == null) return null;

            if (player.con.address.equals(identifier.replaceAll(" ", "")) ||
                    String.valueOf(player.id).equals(identifier.replaceAll(" ", "")) ||
                    player.uuid().equals(identifier.replaceAll(" ", "")) ||
                    Utils.escapeEverything(player).toLowerCase().replaceAll(" ", "").startsWith(identifier.toLowerCase().replaceAll(" ", ""))) {
                found = player;
            }
        }
        return found;
    }

    /**
     * Get player info by uuid, name, phash, or IP
     */
    public static Administration.PlayerInfo findPlayerInfo(String target) {
        var pd = Database.getPlayerDataByPcalculatePcalculatePhash(target);
        if (pd != null) {
            target = pd.uuid;
        }

        Administration.PlayerInfo info = null;
        Player player = findPlayerEntity(target);
        if (player != null) {
            info = netServer.admins.getInfo(player.uuid());
        }
        if (info == null) {
            info = netServer.admins.getInfoOptional(target);
        }
        if (info == null) {
            info = netServer.admins.findByIP(target);
        }
        if (info == null) {
            var res = netServer.admins.findByName(target);
            info = res.size == 0 ? null : res.first();
        }
        if (info == null) {
            var res = netServer.admins.searchNames(target);
            info = res.size == 0 ? null : res.first();
        }
        if (info == null) {
            Database.Player p = getPlayerData(target);
            if (p != null) {
                return findPlayerInfo(p.uuid);
            }
        }
        return info;
    }
}
