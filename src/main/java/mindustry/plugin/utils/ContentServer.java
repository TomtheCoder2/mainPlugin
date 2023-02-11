package mindustry.plugin.utils;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.Pixmap;
import arc.graphics.g2d.Draw;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.io.CounterInputStream;
import arc.util.io.Reads;
import arc.util.serialization.Base64Coder;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.entities.units.BuildPlan;
import mindustry.game.Schematic;
import mindustry.game.Team;
import mindustry.io.MapIO;
import mindustry.io.SaveIO;
import mindustry.maps.Map;
import mindustry.world.*;
import mindustry.world.blocks.environment.Floor;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.InflaterInputStream;

import static mindustry.game.Schematics.read;

/**
 * Renders maps and schematics to images. Requires certain data files, such as {@code block_colors.png}
 */
public class ContentServer {
    static {
        Pixmap pixmap = new Pixmap(Core.settings.getDataDirectory().child("pheonix/block_colors.png"));
        for (int i = 0; i < pixmap.width; i++) {
            if (Vars.content.blocks().size > i) {
                int color = pixmap.get(i, 0);

                if (color == 0 || color == 255) continue;

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
                image.setRGB(x, tiles.height - y - 1, rgb(color));
            }
        }
        return image;
    }

    /**
     * Renders the ongoing game
     */
    public static BufferedImage renderGame() {
        return renderTiles(Vars.world.tiles);
    }

    /**
     * Renders a map to an image
     *
     * @return the rendered image, or null if it failed
     */
    public static BufferedImage renderMap(Map map) {
        return renderData(map.file.read(Vars.bufferSize));
    }

    /**
     * Renders a map given in raw bytes. May fail.
     */
    public static BufferedImage renderRaw(byte[] data) {
        return renderData(new ByteArrayInputStream(data));
    }

    /**
     * Renders an image given an input stream of a map file
     */
    protected static BufferedImage renderData(InputStream s) {
        try (InputStream is = new InflaterInputStream(s); CounterInputStream counter = new CounterInputStream(is); DataInputStream stream = new DataInputStream(counter)) {
            SaveIO.readHeader(stream);
            int version = stream.readInt();
            var ver = SaveIO.getSaveWriter(version);
            ver.region("meta", stream, counter, ver::readStringMap);
            ver.region("content", stream, counter, ver::readContentHeader);

            final BufferedImage[] image = new BufferedImage[1];
            ver.region("preview_map", stream, counter, in -> {
                int width = in.readUnsignedShort();
                int height = in.readUnsignedShort();

                Log.info(width + "," + height);
                image[0] = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                for (int i = 0; i < width * height; i++) {
                    int x = i % width;
                    int y = i / width;

//                    Log.info(x + "," + y);
                    short floorID = in.readShort();
                    short oreID = in.readShort();
                    int consecutives = in.readUnsignedByte();

                    Block floor = Vars.content.block(floorID);
                    if (floor == null) floor = Blocks.stone;
                    Block overlay = Vars.content.block(oreID);

                    var color = (floor != null && floor instanceof Floor && floor.asFloor().wallOre && overlay != null) || (overlay != null && overlay.useColor) ? overlay.mapColor : floor.mapColor;
                    image[0].setRGB(x, height - y - 1, rgb(color));

                    for (int j = i + 1; j < i + 1 + consecutives; j++) {
                        int newx = j % width, newy = j / width;
                        image[0].setRGB(newx, height - newy - 1, rgb(color));
                    }
                    i += consecutives;
                }

                for (int i = 0; i < width * height; i++) {
                    int x = i % width;
                    int y = i / width;

                    short blockID = in.readShort();
                    Block block_ = Vars.content.block(blockID);
                    if (block_ == null) block_ = Blocks.air;
                    final Block block = block_;

                    byte packedCheck = in.readByte();
                    boolean hadEntity = (packedCheck & 1) != 0;
                    boolean hadData = (packedCheck & 2) != 0;

                    boolean isCenter = true;
                    if (hadEntity) isCenter = in.readBoolean();

                    final Team[] team = new Team[1];
                    int consecutives = 0;
                    if (hadEntity) {
                        if (isCenter) {
                            if (block.hasBuilding()) {
                                try {
                                    ver.readChunk(in, true, in2 -> {
                                        byte revision = in.readByte();
                                        Tile tile = new CachedTile();
                                        tile.setBlock(block);
                                        if (tile.build != null) tile.build.readAll(Reads.get(in2), revision);
                                        team[0] = tile.team();
                                    });
                                } catch (Throwable e) {
                                    throw new IOException("Failed to read tile entity of block: " + block, e);
                                }
                            } else {
                                ver.skipChunk(in, true);
                            }
                        }
                    } else if (hadData) {
                        stream.readByte();
                    } else {
                        consecutives = stream.readUnsignedByte();
                    }

                    for (int j = i; j < i + 1 + consecutives; j++) {
                        int newx = j % width, newy = j / width;
                        if (block.synthetic() && team[0] != null) {
                            image[0].setRGB(newx, height - newy - 1, rgb(team[0].color));
                        } else if (block.solid) {
                            // not much importance, but this ignores wall ores
                            image[0].setRGB(newx, height - newy - 1, rgb(block.mapColor));
                        }

                    }

                    i += consecutives;
                }
            });

            return image[0];
        } catch (Exception e) {
            Log.err(e);
            System.gc();
            return null;
        }
    }
}
