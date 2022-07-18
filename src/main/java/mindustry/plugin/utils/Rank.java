package mindustry.plugin.utils;

import arc.graphics.Color;
import arc.struct.ObjectMap;

/**
 * Represents a rank.
 */
public class Rank {
    /**
     * List of ranks
     */
    public static final Rank[] all = new Rank[]{
            new Rank("[#7d7d7d][]", "Civilian", new Color(0x7ab7cbff), "New combatants", "None"),     // 0
            new Rank("[accent]|[white]\uE865[accent]|[]", "Soldier", new Color(0x7ac3cbff)),  // 1
            new Rank("[accent]|[white]\uE861[accent]|[]", "Corporal", new Color(0x7acbc6ff)), // 2
            new Rank("[accent]|[white]\uE804[accent]|[]", "Brigadier", new Color(0x7acbb8ff)),// 3
            new Rank("[accent]|[white]\uE826[accent]|[]", "Sargeant", new Color(0x7acba1ff)), // 4
            new Rank("[accent]|[white]\uE806[accent]|[]", "Major", new Color(0x7acb8fff)),    // 5
            new Rank("[accent]|[white]\uE810[accent]|[]", "Lieutenant", new Color(0x80cb7aff), "", "Be decorated by General/Marshal"),     // 6
            new Rank("[accent]|[white]\uE811[accent]|[]", "Captain", new Color(0x97cb7aff), "Map creator / coder", "Create maps or code for the server"), // 7
            new Rank("[accent]|[white]\uE808[accent]|[]", "Map Reviewer", new Color(0xb5cb7aff), "", "Apply on Discord"),  // 8
            new Rank("[accent]|[white]\uE864[accent]|[]", "Colonel", new Color(0xcbc47aff), "Junior Moderator", "Apply on Discord"),  // 9
            new Rank("[accent]|[white]\uE817[accent]|[]", "General", new Color(0xcbb07aff), "Moderator", "Be decorated from Colonel"),          // 10
            new Rank("[accent]|[white]\uE814[accent]|[]", "Marshal", new Color(0xcb9c7aff), "Admin", "Be admin"),                // 11
    };

    /**
     * A mapping from discord role IDs to rank indexes
     */
    public static final ObjectMap<Long, Integer> roles = ObjectMap.of(
            897568732749115403L, 1,
            881618465117581352L, 2,
            906958402100535296L, 3,
            897565215670042645L, 4,
            900369018110738442L, 5,
            900369102978310206L, 6
    );
    /**
     * List of requirements
     */
    public static final ObjectMap<Integer, Req> reqs = ObjectMap.of(
            1, new Req(300, 6000, 5), // soldier
            2, new Req(600, 12000, 10), // corporal
            3, new Req(1200, 24000, 20), // brigadier
            4, new Req(2400, 48000, 40), // sargeant
            5, new Req(4800, 96000, 80) // major
    );
    /**
     * Name tag (displayed before player name)
     */
    public String tag = "";
    /**
     * Name of rank
     */
    public String name = "";
    /**
     * Description of rank (e.g. "Moderator")
     */
    public String description = null;
    /**
     * Description of requirements (e.g. "Apply on discord")
     */
    public String requirements = null;
    public Color color;

    public Rank(String tag, String name, Color color, String desc, String req) {
        this.tag = tag;
        this.name = name;
        this.color = color;
        this.description = desc;
        this.requirements = req;
    }

    public Rank(String tag, String name, Color color) {
        this.tag = tag;
        this.name = name;
        this.color = color;
    }

    /**
     * Represents the statistical requirements of a given role.
     */
    public static class Req {
        public int buildingsBuilt;
        public int gamesPlayed;
        public int playTime;

        public Req(int playTime, int buildingsBuilt, int gamesPlayed) {
            this.playTime = playTime;
            this.buildingsBuilt = buildingsBuilt;
            this.gamesPlayed = gamesPlayed;
        }
    }
}
