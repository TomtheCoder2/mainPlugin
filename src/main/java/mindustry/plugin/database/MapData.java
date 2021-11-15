package mindustry.plugin.database;

public class MapData implements Cloneable {
    /**
     * name varchar,
     * positiveRating int,
     * negativeRating int,
     * highscore     bigint,
     * playtime      bigint
     */
    public String name;
    public int positiveRating = 0;
    public int negativeRating = 0;
    public long highscoreTime = 0;
    public long highscoreWaves = 0;
    public long shortestGame = 0;
    public long playtime = 0;

    public MapData(String name) {
        this.name = name;
    }

    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }
}