package mindustry.plugin.utils.ranks;

/**
 * Requirements for ranks
 */
public class Requirement {
    public int buildingsBuilt;
    public int gamesPlayed;
    public int playtime;

    public Requirement(int inputPlaytime, int inputBuildingsBuilt, int inputGamesPlayed) {
        this.playtime = inputPlaytime;
        this.buildingsBuilt = inputBuildingsBuilt;
        this.gamesPlayed = inputGamesPlayed;
    }
}
