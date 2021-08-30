package mindustry.plugin;

import arc.Core;
import arc.Events;
//import arc.struct.Seq;
import arc.struct.Seq;
import arc.util.Strings;
import mindustry.content.Blocks;
//import mindustry.entities.type.Player;
//import mindustry.entities.type.TileEntity;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.maps.Map;
import mindustry.maps.Maps;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.storage.CoreBlock;
import org.javacord.api.entity.user.User;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Transaction;
import redis.clients.jedis.exceptions.JedisException;

import java.awt.*;
import java.lang.reflect.Field;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.TimeZone;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static mindustry.Vars.*;
import static mindustry.plugin.ioMain.*;

public class Utils {
    public static int chatMessageMaxSize = 256;
    static String welcomeMessage = "";
    static String statMessage = "";
    static String ruleMessage = "";
    static String noPermissionMessage = "You don't have the required rank for this command. Learn more about ranks [pink]/info[]";

    // wheter ip verification is in place (detect vpns, disallow their build rights)
    static Boolean verification = false;

    static String promotionMessage =
            "[sky]%player%, you have been promoted to [sky]<active>[]!\n" +
                    "[#4287f5]You reached a playtime of - %playtime% minutes! That's 10+ hours!\n" +
                    "[#f54263]You played a total of %games% games!\n" +
                    "[#9342f5]You built a total of %buildings% buildings!\n" +
                    "[sky]Thank you for participating and enjoy your time on [orange]<[white]io[orange]>[sky]!\n"+
                    "[scarlet]Please rejoin for the change to take effect.";

    static String verificationMessage = "[scarlet]Your IP was flagged as a VPN.\n" +
            "\n" +
            "[sky]Please join our discord:\n" +
            "http://discord.mindustry.io\n" +
            "[#7a7a7a]verify your account in #verifications";

    static HashMap<Integer, Rank> rankNames = new HashMap<>();
    static HashMap<String, Integer> rankRoles = new HashMap<>();
    static Seq<String> bannedNames = new Seq<>();
    static Seq<String> onScreenMessages = new Seq<>();
    static String eventIp = "mindustry.io";
    static int eventPort = 1001;

    public static class Rank{
        public String tag = "";
        public String name = "";

        Rank(String t, String n){
            this.tag = t;
            this.name = n;
        }
    }

    public static void init(){
        rankNames.put(0, new Rank("[#7d7d7d]<none>[]", "none"));
        rankNames.put(1, new Rank("[accent]<[white][accent]>[]", "active"));
        rankNames.put(2, new Rank("[accent]<[white][accent]>[]", "veteran"));
        rankNames.put(3, new Rank("[accent]<[white][accent]>[]", "contributor"));
        rankNames.put(4, new Rank("[accent]<[white][accent]>[]", "moderator"));
        rankNames.put(5, new Rank("[accent]<[white][accent]>[]", "admin"));

        rankRoles.put("627985513600516109", 1);
        rankRoles.put("636968410441318430", 2);
        rankRoles.put("674778262857187347", 3);
        rankRoles.put("624959361789329410", 4);

        bannedNames.add("IGGGAMES");
        bannedNames.add("CODEX");
        bannedNames.add("VALVE");
        bannedNames.add("tuttop");
        bannedNames.add("Volas Y0uKn0w1sR34Lp");
        bannedNames.add("IgruhaOrg");
        bannedNames.add("андрей");

        activeRequirements.bannedBlocks.add(Blocks.conveyor);
        activeRequirements.bannedBlocks.add(Blocks.titaniumConveyor);
        activeRequirements.bannedBlocks.add(Blocks.junction);
        activeRequirements.bannedBlocks.add(Blocks.router);

        statMessage = Core.settings.getString("statMessage");
        welcomeMessage = Core.settings.getString("welcomeMessage");
        ruleMessage = Core.settings.getString("ruleMessage");
    }

    public static class Pals {
        public static Color warning = (Color.getHSBColor(5, 85, 95));
        public static Color info = (Color.getHSBColor(45, 85, 95));
        public static Color error = (Color.getHSBColor(3, 78, 91));
        public static Color success = (Color.getHSBColor(108, 80, 100));
    }

    public static class activeRequirements {
        public static Seq<Block> bannedBlocks = new Seq<>();
        public static int playtime = 75 * 10;
        public static int buildingsBuilt = 1500 * 10;
        public static int gamesPlayed = 10;
    }

    public static String escapeCharacters(String string){
        return escapeColorCodes(string.replaceAll("`", "").replaceAll("@", ""));
    }

    public static String escapeColorCodes(String string){
        return Strings.stripColors(string);
    }

    public static Map getMapBySelector(String query) {
        Map found = null;
        try {
            // try by number
            found = maps.customMaps().get(Integer.parseInt(query));
        } catch (Exception e) {
            // try by name
            for (Map m : maps.customMaps()) {
                if (m.name().replaceAll(" ", "").toLowerCase().contains(query.toLowerCase().replaceAll(" ", ""))) {
                    found = m;
                    break;
                }
            }
        }
        return found;
    }

    public static Player findPlayer(String identifier){
        Player found = null;
        for (Player player : Groups.player) {
            if(player == null) return null;
            if(player.uuid() == null) return null;
            if(player.con == null) return null;
            if(player.con.address == null) return null;

            if (player.con.address.equals(identifier.replaceAll(" ", "")) || String.valueOf(player.id).equals(identifier.replaceAll(" ", "")) || player.uuid().equals(identifier.replaceAll(" ", "")) || escapeColorCodes(player.name.toLowerCase().replaceAll(" ", "")).replaceAll("<.*?>", "").startsWith(identifier.toLowerCase().replaceAll(" ", ""))) {
                found = player;
            }
        }
        return found;
    }

//    public static String formatMessage(Player player, String message){
//        try {
//            message = message.replaceAll("%player%", escapeCharacters(player.name));
////            message = message.replaceAll("%map%", world.getMap().name());
//            message = message.replaceAll("%wave%", String.valueOf(state.wave));
////            PlayerData pd = getData(player.uuid);
//            if (pd != null) {
//                message = message.replaceAll("%playtime%", String.valueOf(pd.playTime));
//                message = message.replaceAll("%games%", String.valueOf(pd.gamesPlayed));
//                message = message.replaceAll("%buildings%", String.valueOf(pd.buildingsBuilt));
//                message = message.replaceAll("%rank%", rankNames.get(pd.rank).tag + " " + escapeColorCodes(rankNames.get(pd.rank).name));
//                if(pd.discordLink.length() > 0){
//                    User discordUser = api.getUserById(pd.discordLink).get(2, TimeUnit.SECONDS);
//                    if(discordUser != null) {
//                        message = message.replaceAll("%discord%", discordUser.getDiscriminatedName());
//                    }
//                } else{
//                    message = message.replaceAll("%discord%", "unlinked");
//                }
//            }
//        }catch(Exception ignore){};
//        return message;
//    }

//    public static PlayerData getData(String uuid) {
//        try(Jedis jedis = ioMain.pool.getResource()) {
//            String json = jedis.get(uuid);
//            if(json == null) return null;
//
//            try {
//                return gson.fromJson(json, PlayerData.class);
//            } catch(Exception e){
//                e.printStackTrace();
//                return null;
//            }
//        }
//    }
//
//    public static void setData(String uuid, PlayerData pd) {
//        try(Jedis jedis = ioMain.pool.getResource()) {
//            try {
//                String json = gson.toJson(pd);
//                jedis.set(uuid, json);
//            } catch(Exception e){
//                e.printStackTrace();
//            }
//        }
//    }

    public static void changeMap(Map found){
        Class<Maps> mapsClass = Maps.class;
        Field mapsField;
        try {
            mapsField = mapsClass.getDeclaredField("maps");
        } catch (NoSuchFieldException ex) {
            throw new RuntimeException("Could not find field 'maps' of class 'mindustry.maps.Maps'");
        }
        mapsField.setAccessible(true);
        Field mapsListField = mapsField;

        Seq<Map> mapsList;
        try {
            mapsList = (Seq<Map>)mapsListField.get(maps);
        } catch (IllegalAccessException ex) {
            throw new RuntimeException("unreachable");
        }

        Seq<Map> tempMapsList = mapsList.removeAll(map -> !map.custom || map != found);

        try {
            mapsListField.set(maps, tempMapsList);
        } catch (IllegalAccessException ex) {
            throw new RuntimeException("unreachable");
        }

        Events.fire(new EventType.GameOverEvent(Team.crux));

        try {
            mapsListField.set(maps, mapsList);
        } catch (IllegalAccessException ex) {
            throw new RuntimeException("unreachable");
        }
        maps.reload();
    }

//    public static CoreBlock.CoreEntity getCore(Team team){
//        Tile[][] tiles = world.getTiles();
//        for (int x = 0; x < tiles.length; ++x) {
//            for(int y = 0; y < tiles[0].length; ++y) {
//                if (tiles[x][y] != null && tiles[x][y].entity != null) {
//                    TileEntity ent = tiles[x][y].ent();
//                    if (ent instanceof CoreBlock.CoreEntity) {
//                        if(ent.getTeam() == team){
//                            return (CoreBlock.CoreEntity) ent;
//                        }
//                    }
//                }
//            }
//        }
//        return null;
//    }

    public static String epochToString(long epoch){
        Date date = new Date(epoch * 1000L);
        DateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        format.setTimeZone(TimeZone.getTimeZone("Etc/UTC"));
        return format.format(date) + " UTC";
    }

    public static String getKeyByValue(HashMap<String, Integer> map, Integer value) {
        for (java.util.Map.Entry<String, Integer> entry : map.entrySet()) {
            if (value.equals(entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
    }
}