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
            new Rank(null, "Civilian", new Color(0xffffffff), "New combatants", "None"),     // 0
            new Rank("\uE865", "Soldier", new Color(0xb29d35ff)),  // 1
            new Rank("\uE861", "Corporal", new Color(0xb28835ff)), // 2
            new Rank("\uE804", "Brigadier", new Color(0xb27435ff)),// 3
            new Rank("\uE826", "Sargeant", new Color(0xb25f35ff)), // 4
            new Rank("\uE806", "Major", new Color(0xb23b23ff)),    // 5
            new Rank("\uE810", "Lieutenant", new Color(0x991e1eff), "", "Be decorated by General/Marshal"),     // 6
            new Rank("\uE811", "Captain", new Color(0x7f192aff), "Map creator / coder", "Create maps or code for the server"), // 7
            new Rank("\uE808", "Map Reviewer", new Color(0x7f192aff), "", "Apply on Discord"),  // 8
            new Rank("\uE864", "Colonel", new Color(0x99e500ff), "Junior Moderator", "Apply on Discord"),  // 9
            new Rank("\uE817", "General", new Color(0x00e500ff), "Moderator", "Be decorated from Colonel"),          // 10
            new Rank("\uE814", "Marshal", new Color(0x0098e5ff), "Admin", "Be admin"),                // 11
    };

    public final static int APPRENTICE = 9;
    public final static int MOD = 10;

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
