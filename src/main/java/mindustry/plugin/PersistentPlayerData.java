package mindustry.plugin;
//import arc.struct.Array;
//import mindustry.entities.type.BaseUnit;
//import mindustry.entities.type.Player;
import mindustry.entities.bullet.BulletType;

import java.io.Serializable;

public class PersistentPlayerData implements Serializable {
    public String origName;
//    public Array<BaseUnit> draugPets = new Array<>();
    public int bbIncrementor = 0;
    public boolean spawnedLichPet;
    public boolean spawnedPowerGen;

    // 50 configures per 1000 ms
    public Ratelimit configureRatelimit = new Ratelimit(50, 1000);
    // 10 rotates per 1000 ms
    public Ratelimit rotateRatelimit = new Ratelimit(10, 1000);

    public PersistentPlayerData() {}
}