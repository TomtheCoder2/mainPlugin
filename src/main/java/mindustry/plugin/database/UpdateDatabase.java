package mindustry.plugin.database;

import java.sql.SQLException;

import static mindustry.plugin.database.Database.connect;
import static mindustry.plugin.database.Database.setHash;

//"database": {
//    "url": "jdbc:postgresql://172.104.253.198/mindustrydata",
//    "user": "postgres",
//    "password": "Nautilus2021#",
//    "table_player": "playerdata2"
//  },
public class UpdateDatabase {
    public static void main(String[] args) throws SQLException {
        connect("jdbc:postgresql://phoenix-network.dev/mindustrydata", "postgres", "Nautilus2021#", "playerdata");
        setHash();
    }
}
