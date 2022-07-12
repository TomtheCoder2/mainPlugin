package mindustry.plugin.minimods;

import arc.Events;
import arc.struct.ObjectMap;
import arc.struct.ObjectSet;
import arc.util.CommandHandler;
import mindustry.game.EventType;
import mindustry.gen.Player;
import mindustry.plugin.MiniMod;
import mindustry.plugin.utils.GameMsg;
import mindustry.world.Block;
import mindustry.world.Tile;

public class Inspector implements MiniMod {
    /** Players with inspector enabled */
    private ObjectSet<String> players;

    private static class Pos {
        public int x;
        public int y;

        @Override
        public int hashCode() {
            return this.x * 31 + this.y;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Pos)) return false;
            Pos p = (Pos)o;
            return this.x == p.x && this.y == p.y;
        }

        public Pos(Tile tile) {
            x = tile.x;
            y = tile.y;
        }
    }

    private ObjectMap<Pos, TileInfo> tileInfos = new ObjectMap<>();

    private static class TileInfo {
        public String placedBy;
        public String destroyedBy;
        public String configuredBy; 

        public String placedByName;
        public String destroyedByName;
        public String configuredByName;

        /** Only set for blocks that are Blocks.air */
        public Block previousBlock;
    }

    @Override
    public void registerCommands(CommandHandler handler) {
        handler.<Player>register("inspector", "", "Toggle inspector", (args, player) -> {
            boolean had = players.contains(player.uuid());
            if (had) {
                players.remove(player.uuid());
            } else {
                players.add(player.uuid());
            }

            player.sendMessage(GameMsg.info("Inspector", (had ? "Disabled": "Enabled") + " the inspector"));
        });
    }

    @Override
    public void registerEvents() {
        Events.on(EventType.BuildSelectEvent.class, event -> {
            if (event.tile == null) return;
        
            String playerName = event.builder.isPlayer() ? event.builder.getPlayer().name : ("unit " + event.builder.type.name);
            String playerUUID = event.builder.isPlayer() ? event.builder.getPlayer().uuid() : null;

            TileInfo info = tileInfos.get(new Pos(event.tile), new TileInfo());
            if (!event.breaking) { // construction
                info.placedBy = playerUUID;
                info.placedByName = playerName;
                info.previousBlock = event.tile.block();
            } else {
                info.destroyedBy = playerUUID;
                info.destroyedByName = playerName;
            }
            tileInfos.put(new Pos(event.tile), info);
        });

        Events.on(EventType.ConfigEvent.class, event -> {
            if (event.tile == null || event.player == null) return;

            TileInfo info = tileInfos.get(new Pos(event.tile.tile), new TileInfo());
            info.configuredBy = event.player.uuid();
            info.configuredByName = event.player.name;
            tileInfos.put(new Pos(event.tile.tile), info);
        });

        Events.on(EventType.WorldLoadEvent.class, event -> {
            tileInfos.clear();
        });

        Events.on(EventType.TapEvent.class, event -> {
            if (event.tile == null) return;
            if (!players.contains(event.player.uuid())) return;
            
            String s = "[white]" + event.tile.block().name + "[pink] ([white]" + event.tile.x + "[pink], [white]" + event.tile.y + "[pink])";
            TileInfo info = tileInfos.get(new Pos(event.tile));
            if (info != null) {
                if (info.configuredByName != null) {
                    s += "\n - Last configured by: [white]" + info.configuredByName + "[pink]";
                }
                if (info.placedByName != null) {
                    s += "\n - Last placed by: [white]" + info.placedByName + "[pink]";
                }
                if (info.destroyedByName != null) {
                    s += "\n - Last destroyed " + (info.previousBlock == null ? "" : " from [accent]" + info.previousBlock.name) + "[pink] by: [white]" + info.destroyedByName + "[pink]";
                }
            }

            event.player.sendMessage(GameMsg.custom("Inspector", "pink", s));
        });
    }
}
