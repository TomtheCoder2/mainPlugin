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
import mindustry.plugin.discord.Channels;
import mindustry.plugin.discord.DiscordPalette;
import mindustry.plugin.discord.discordcommands.Context;
import org.javacord.api.entity.message.embed.EmbedBuilder;

import java.util.ArrayList;
import java.util.List;

import static mindustry.Vars.netServer;
import static mindustry.plugin.database.Database.getUUIDs;
import static mindustry.plugin.utils.Utils.*;

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
        var pd = Database.getPlayerDataByPhash(identifier);
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


    public static Administration.PlayerInfo findPlayerDiscord(String target, Context ctx) {
        // first search in online players
        Player found = null;
        for (Player player : Groups.player) {
            if (player == null) return null; // how does that even happen wtf
            if (player.uuid() == null) return null;
            if (player.con == null) return null;
            if (player.con.address == null) return null;

            if (player.con.address.equals(target.replaceAll(" ", "")) ||
                    String.valueOf(player.id).equals(target.replaceAll(" ", "")) ||
                    player.uuid().equals(target.replaceAll(" ", "")) ||
                    Utils.escapeEverything(player).toLowerCase().replaceAll(" ", "").startsWith(target.toLowerCase().replaceAll(" ", ""))) {
                found = player;
            }
        }
//        if (found != null) {
//            return netServer.admins.getInfo(found.uuid());
//        }
        var uuids = getUUIDs(ctx.args.get("player"));
        if (uuids != null && (uuids.size > 1 || (uuids.size == 1 && found != null))) {
            EmbedBuilder eb = new EmbedBuilder();
            eb.setTitle("Multiple Players Found");
            eb.setColor(DiscordPalette.INFO);
            StringBuilder sb = new StringBuilder();
            List<String> uuids_list = new ArrayList<>();
            for (var uuid : uuids) {
                if (uuid == null) continue;
                if (uuid.equals("")) continue;
                var info = Vars.netServer.admins.getInfo(uuid);
                if (info == null) continue;
                if (uuids_list.contains(uuid)) continue;
                // TODO: there could be a problem if the server doesnt know any names. but thats very unlikely
                if (info.lastName == null || info.lastName.equals("<unknown>")) continue;
                sb.append(String.format("%s (`%s`)" + (ctx.channel() == Channels.APPRENTICE_BOT ? "" : " - %s") + "\n", escapeEverything(info.lastName), calculatePhash(uuid), (ctx.channel() == Channels.APPRENTICE_BOT ? "" : info.lastIP)));
                uuids_list.add(uuid);
            }
//                        System.out.println("before splitting: " + sb.toString().length());
//                        System.out.println("after splitting: " + split(sb.toString(), 3000)[0].length());
            eb.setDescription("Multiple players were found with the given name. Please specify the UUID or IP of the player you want to lookup.\n\n" + split(sb.toString(), 3000)[0]);
            if (found != null) {
                eb.addField("Online Players", String.format("%s (`%s`)" + (ctx.channel() == Channels.APPRENTICE_BOT ? "" : " - %s") + "\n", escapeEverything(found.name), calculatePhash(found.uuid()), (ctx.channel() == Channels.APPRENTICE_BOT ? "" : found.con.address)));
            }
            ctx.channel().sendMessage(eb);
            return null;
        }
        var info = Query.findPlayerInfo(ctx.args.get("player"));
        if (info == null && uuids != null && uuids.size == 1) {
            info = Vars.netServer.admins.getInfo(uuids.first());
        }
        if (info == null) {
            ctx.error("No such player", ctx.args.get("player") + " is not in the database");
            return null;
        }
        if (info.names.size == 0) {
            ctx.error("Unknown Player", "Could not find player " + ctx.args.get("player"));
            return null;
        }
        return info;
    }

    /**
     * Get player info by uuid, name, phash, or IP
     */
    public static Administration.PlayerInfo findPlayerInfo(String target) {
        var pd = Database.getPlayerDataByPhash(target);
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
        return info;
    }
}
