package mindustry.plugin.utils;

import java.awt.image.BufferedImage;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.InflaterInputStream;

import arc.Core;
import arc.files.Fi;
import arc.graphics.Color;
import arc.graphics.Pixmap;
import arc.graphics.g2d.Draw;
import arc.scene.ui.Image;
import arc.util.Log;
import arc.util.io.CounterInputStream;
import mindustry.Vars;
import mindustry.io.MapIO;
import mindustry.io.SaveIO;
import mindustry.io.SaveVersion;
import mindustry.maps.Map;
import mindustry.world.Block;
import mindustry.world.CachedTile;
import mindustry.world.ColorMapper;
import mindustry.world.Tile;
import mindustry.world.Tiles;
import mindustry.world.WorldContext;
import mindustry.world.blocks.environment.Floor;

/** Renders maps and schematics to images. Requires certain data files, such as {@code block_colors.png} */
public class ContentServer {
    static {
        Pixmap pixmap = new Pixmap(Core.settings.getDataDirectory().child("pheonix/block_colors.png"));
        for(int i = 0; i < pixmap.width; i++){
            if(Vars.content.blocks().size > i){
                int color = pixmap.get(i, 0);

                if(color == 0 || color == 255) continue;

                Block block = Vars.content.block(i);
                block.mapColor.rgba8888(color);
                //partial alpha colors indicate a square sprite
                block.squareSprite = block.mapColor.a > 0.5f;
                block.mapColor.a = 1f;
                block.hasColor = true;
            }
        }
        pixmap.dispose();
        ColorMapper.load();
    }

    private static int rgb(Color color) {
        return (0xff << 24) + (color.rgba8888() >> 8);
    }

    public static BufferedImage renderTiles(Tiles tiles) {
        var image = new BufferedImage(tiles.width, tiles.height, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < tiles.width; x++) {
            for (int y = 0; y < tiles.height; y++) {
                Tile tile = tiles.get(x, y);
                var color = new Color(MapIO.colorFor(tile.block(), tile.floor(), tile.overlay(), tile.team()));
                if (color.r == 0 && color.g == 0 && color.b == 0) {
                    Log.warn("invalid tile | block " + tile.block().name + " | floor " + tile.floor().name + " | overlay " + tile.overlay().name);
                }
                image.setRGB(x, y, rgb(color));
            }
        }
        return image;
    }

    /** Renders a map to an image
     * @return the rendered image, or null if it failed
     */
    public static BufferedImage renderMap(Map map) {
        try {
            return pixmapToImage(MapIO.generatePreview(map));
        } catch(IOException e) {
            Log.err(e);
            return null;
        }
    }

    /** Renders a map given in raw bytes. May fail.
     */
    public static BufferedImage renderRaw(byte[] data) {
        Fi fi = Fi.tempFile("render-raw");
        fi.writeBytes(data);
        try {
            Map m = MapIO.createMap(fi, true);
            return renderMap(m);
        } catch (Exception e) {
            Log.err(e);
            return null;
        }
    }

    /** Renders the ongoing game */
    public static BufferedImage renderGame() {
        return renderTiles(Vars.world.tiles);
    }

    private static BufferedImage pixmapToImage(Pixmap pixmap) {
        var image = new BufferedImage(pixmap.width, pixmap.height, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < pixmap.width; x++) {
            for (int y = 0; y < pixmap.height; y++) {
                image.setRGB(x, y, rgb(new Color(pixmap.get(x, y))));
            }
        }
        return image;
    }
}
