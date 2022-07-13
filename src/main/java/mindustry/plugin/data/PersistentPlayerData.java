package mindustry.plugin.data;
//import arc.struct.Array;
//import mindustry.entities.type.BaseUnit;
//import mindustry.entities.type.Player;

import mindustry.plugin.utils.Ratelimit;

import java.io.Serializable;

public class PersistentPlayerData implements Serializable {
    public String origName;
    //    public Array<BaseUnit> draugPets = new Array<>();
    //    public boolean spawnedLichPet;
//    public boolean spawnedPowerGen;

    public boolean snowBall = false;


    // 50 configures per 1000 ms
    public Ratelimit configureRatelimit = new Ratelimit(50, 1000);
    // 10 rotates per 1000 ms
    public Ratelimit rotateRatelimit = new Ratelimit(10, 1000);

    public PersistentPlayerData() {
    }
}