package mindustry.plugin.utils.ranks;

import java.awt.*;
import java.util.HashMap;
import mindustry.plugin.utils.ranks.Rank;

public class Utils {
    public static HashMap<Integer, Rank> rankNames = new HashMap<>();
    public static HashMap<Integer, Requirement> rankRequirements = new HashMap<>();
    public static HashMap<String, Integer> rankRoles = new HashMap<>();

    public static void init() {
        //        "\uE816";
        // set all ranks
        rankNames.put(0, new Rank("[#7d7d7d][]", "Civilian", "The new combatants at the front", new Color(0x7ab7cb)));
        // first try:
//        rankNames.put(1, new Rank("[accent]<[white]\uE802[accent]>[]", "private"));
//        rankNames.put(2, new Rank("[accent]<[white]\uE813[accent]>[]", "general"));
//        rankNames.put(3, new Rank("[accent]<[white]\uE824[accent]>[]", "sargent"));
//        rankNames.put(4, new Rank("[accent]<[white]\uE815[accent]>[]", "corporal"));
//        rankNames.put(5, new Rank("[accent]<[white]\uE819[accent]>[]", "pro"));
//        rankNames.put(6, new Rank("[accent]<[white]\uE809[accent]>[]", "contributor"));
//        rankNames.put(7, new Rank("[accent]<[white]\uE817[accent]>[]", "moderator"));
//        rankNames.put(8, new Rank("[accent]<[white][accent]>[]", "admin"));
        // second try:
//        rankNames.put(1, new Rank("[accent]<[white]\uE800[accent]>[] ", "newbie"));
//        rankNames.put(2, new Rank("[accent]<[white]\uE826[accent]>[] ", "active"));
//        rankNames.put(3, new Rank("[accent]<[white]\uE813[accent]>[] ", "veteran"));
//        rankNames.put(4, new Rank("[accent]<[white]\uE809[accent]>[] ", "map_creator:"));
//        rankNames.put(5, new Rank("[accent]<[white]\uE88E[accent]>[] ", "moderator_jr:"));
//        rankNames.put(6, new Rank("[accent]<[white]\uE82C[accent]>[] ", "moderator"));
        // third try:
        // ranks in normal form:
        /**
         * Private => Soldier
         * General => Corporal
         * Corporal => Sargeant
         * Sargeant => Major
         * Pro player => Lieutenant
         * Contributor => Captain
         * Mod Jr => Colonel
         * Mod => General
         * Admin => Marshal
         * */
        // icons:
        /**
         * -Soldier  (uE865)
         * -Corporal (uE861)
         * -Sargeant  (uE806)
         * -Major ()
         * -Lieutenant ()
         * -Captain (uE811)
         * -Colonel (uE864)
         * -General (uE817)
         * -Marshall (uE814)
         * */
        rankNames.put(1, new Rank("[accent]|[white]\uE865[accent]|[]", "Soldier", new Color(0x7ac3cb)));
        rankNames.put(2, new Rank("[accent]|[white]\uE861[accent]|[]", "Corporal", new Color(0x7acbc6)));
        rankNames.put(3, new Rank("[accent]|[white]\uE804[accent]|[]", "Brigadier", new Color(0x7acbb8)));
        rankNames.put(4, new Rank("[accent]|[white]\uE826[accent]|[]", "Sargeant", new Color(0x7acba1)));
        rankNames.put(5, new Rank("[accent]|[white]\uE806[accent]|[]", "Major", new Color(0x7acb8f)));
        rankNames.put(6, new Rank("[accent]|[white]\uE810[accent]|[]", "Lieutenant", "Be decorated by General/Marshal", new Color(0x80cb7a)));
        rankNames.put(7, new Rank("[accent]|[white]\uE811[accent]|[]", "Captain", "Create maps or code for the server", new Color(0x97cb7a)));
        rankNames.put(8, new Rank("[accent]|[white]\uE808[accent]|[]", "Map reviewer", "Apply at our discord server", new Color(0xb5cb7a)));
        rankNames.put(9, new Rank("[accent]|[white]\uE864[accent]|[]", "Colonel", "Apply at our discord server (Junior Mod)", new Color(0xcbc47a)));//app mod
        rankNames.put(10, new Rank("[accent]|[white]\uE817[accent]|[]", "General", "Be decorated from Colonel", new Color(0xcbb07a))); // mod
        rankNames.put(11, new Rank("[accent]|[white]\uE814[accent]|[]", "Marshal", "Be admin", new Color(0xcb9c7a))); // admin


        rankRequirements.put(1, new Requirement(300,  6000,  5));
        rankRequirements.put(2, new Requirement(600,  12000, 10));
        rankRequirements.put(3, new Requirement(1200, 24000, 20));
        rankRequirements.put(4, new Requirement(2400, 48000, 40));
        rankRequirements.put(5, new Requirement(4800, 96000, 80));


        rankRoles.put("897568732749115403", 1);
        rankRoles.put("881618465117581352", 2);
        rankRoles.put("906958402100535296", 3);
        rankRoles.put("897565215670042645", 4);
        rankRoles.put("900369018110738442", 5);
        rankRoles.put("900369102978310206", 6);
    }

    /**
     * Get a list of all ranks for the help page
     */
    public static String listRanks() {
        StringBuilder list = new StringBuilder();
        list.append("```java\n");
        for (var entry : rankNames.entrySet()) {
            list.append(entry.getKey()).append(": ").append(entry.getValue().name).append("\n");
        }
        list.append("```");
        return list.toString();
    }

    /**
     * list all ranks for the /ranks command
     */
    public static String inGameListRanks() {
        StringBuilder list = new StringBuilder("[accent]List of all ranks:\n");
        for (var entry : rankNames.entrySet()) {
            list.append(entry.getValue().tag).append(" [#").append(Integer.toHexString(rankNames.get(entry.getKey()).color.getRGB()).substring(2)).append("]").append(entry.getValue().name).append("\n");
        }
        list.append("\n[green]Type [sky]/req [green]to see the requirements for the ranks");
        return list.toString();
    }

    /**
     * show the requirements for the ranks
     */
    public static String listRequirements() {
        StringBuilder list = new StringBuilder("[accent]List of all requirements:\n");
        for (var entry : rankNames.entrySet()) {
            list.append("[#").append(Integer.toHexString(rankNames.get(entry.getKey()).color.getRGB()).substring(2)).append("]").append(entry.getValue().name).append(" ");
            if (entry.getValue().description != null) {
                list.append(" : [orange]").append(entry.getValue().description).append("\n");
            } else {
                list
                        .append(": [red]")
                        .append((rankRequirements.get(entry.getKey()).playtime >= 1000 ? rankRequirements.get(entry.getKey()).playtime / 1000 + "k" : rankRequirements.get(entry.getKey()).playtime))
                        .append(" mins[white]/ [orange]")
                        .append(rankRequirements.get(entry.getKey()).gamesPlayed)
                        .append(" games[white]/ [yellow]")
                        .append(rankRequirements.get(entry.getKey()).buildingsBuilt / 1000).append("k built\n");
            }
        }
        return list.toString();
    }
}
