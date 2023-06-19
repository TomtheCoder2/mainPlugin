package mindustry.plugin.utils;

import arc.Core;
import arc.files.Fi;
import arc.graphics.Color;
import arc.graphics.Texture;
import arc.graphics.g2d.*;
import arc.graphics.g2d.TextureAtlas.AtlasRegion;
import arc.graphics.g2d.TextureAtlas.TextureAtlasData;
import arc.math.Mathf;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Log;
import mindustry.Vars;
import mindustry.core.GameState;
import mindustry.core.Version;
import mindustry.ctype.Content;
import mindustry.ctype.ContentType;
import mindustry.entities.units.BuildPlan;
import mindustry.game.Schematic;
import mindustry.game.Schematics;
import mindustry.plugin.PhoenixMain;
import mindustry.world.Block;
import mindustry.world.blocks.environment.OreBlock;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URL;
import java.util.Objects;

import static arc.util.Log.debug;
import static mindustry.Vars.schematicBaseStart;

public class ContentHandler {
    public static final String schemHeader = schematicBaseStart;

    Color co = new Color();
    Graphics2D currentGraphics;
    BufferedImage currentImage;
    ObjectMap<String, Fi> imageFiles = new ObjectMap<>();
    public boolean usingInternal = false;
    ObjectMap<String, URL> imageUrls = new ObjectMap<>();
    ObjectMap<String, BufferedImage> regions = new ObjectMap<>();

    public ContentHandler() {
        //clear cache
        new Fi("cache").deleteDirectory();

        Version.enabled = false;
        for (ContentType type : ContentType.all) {
            for (Content content : Vars.content.getBy(type)) {
                try {
                    content.init();
                } catch (Throwable ignored) {
                }
            }
        }
        // Is this required?
        Vars.state = new GameState();

        String assets = PluginConfig.assetsDir;
        if (PluginConfig.assetsDir == null) {
            assets = "./assets";
        }
        debug("Loading assets from " + assets);
        Fi assets_raw = new Fi(assets).parent().child("assets-raw/sprites_out");
        if (!assets_raw.exists()) {
            Log.warn("Raw asset folder @ not found", assets_raw.absolutePath());
            usingInternal = true;
        }
        Fi atlas = new Fi(assets + "/sprites/sprites.aatls");
        if (!atlas.exists()) {
            Log.warn("Aatls file not found at: @", atlas.absolutePath());
            usingInternal = true;
        }
//        usingInternal = true;
        TextureAtlasData data;
        if (usingInternal) {
            Log.info("Using internal sprites");
            Class<?> cls = PhoenixMain.class;
            atlas = new Fi("") {
                @Override
                public InputStream read() {
                    return Objects.requireNonNull(cls.getResourceAsStream("/atlas/sprites.aatls"));
                }
            };
            try (InputStream stream = cls.getResourceAsStream("/aa-sprites/names.txt")) {
                InputStreamReader isr = new InputStreamReader(stream);
                BufferedReader reader = new BufferedReader(isr);
                String name;
                while ((name = reader.readLine()) != null) {
                    imageUrls.put(name, Objects.requireNonNull(cls.getResource("/aa-sprites/"+name+".png")));
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            // Aatls needed for sprite sizing, sprites not needed as sprites_raw is used?
            assets_raw.walk(f -> {
                if (f.extEquals("png")) {
                    imageFiles.put(f.nameWithoutExtension(), f);
                }
            });
        }
        data = new TextureAtlasData(atlas, new Fi(""), false);
        Core.atlas = new TextureAtlas();


        data.getPages().each(page -> {
            page.texture = Texture.createEmpty(null);
            page.texture.width = page.width;
            page.texture.height = page.height;
        });

        data.getRegions().each(reg -> Core.atlas.addRegion(reg.name, new AtlasRegion(reg.page.texture, reg.left, reg.top, reg.width, reg.height) {{
            name = reg.name;
            texture = reg.page.texture;
        }}));

        Lines.useLegacyLine = true;
        Core.atlas.setErrorRegion("error");
        Draw.scl = 1f / 4f;
        Core.batch = new SpriteBatch(0) {
            @Override
            protected void draw(TextureRegion region, float x, float y, float originX, float originY, float width, float height, float rotation) {
                x += 4;
                y += 4;

                x *= 4;
                y *= 4;
                width *= 4;
                height *= 4;

                y = currentImage.getHeight() - (y + height / 2f) - height / 2f;

                AffineTransform at = new AffineTransform();
                at.translate(x, y);
                at.rotate(-rotation * Mathf.degRad, originX * 4, originY * 4);

                currentGraphics.setTransform(at);
                BufferedImage image = getImage(((AtlasRegion) region).name);
                if (!color.equals(Color.white)) {
                    image = tint(image, color);
                }

                currentGraphics.drawImage(image, 0, 0, (int) width, (int) height, null);
            }

            @Override
            protected void draw(Texture texture, float[] spriteVertices, int offset, int count) {
                //do nothing
            }
        };

        for (ContentType type : ContentType.values()) {
            for (Content content : Vars.content.getBy(type)) {
                try {
                    content.load();
                    content.loadIcon();
                } catch (Throwable ignored) {
                }
            }
        }

        try {
            BufferedImage image = ImageIO.read(new File(assets + "/sprites/block_colors.png"));
            int width = image.getWidth(), blocksSize = Vars.content.blocks().size;
            Log.info("Block colors: @, Blocks: @", width, blocksSize);
            if (width < blocksSize) Log.err("Image width less than content size! @/@");
            for (Block block : Vars.content.blocks()) {
                if (block.id > width-1) break;
                block.mapColor.argb8888(image.getRGB(block.id, 0));
                if (block instanceof OreBlock) {
                    block.mapColor.set(block.itemDrop.color);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

//        Vars.world = new World() {
//            public Tile tile(int x, int y) {
//                return new Tile(x, y);
//            }
//        };
    }

    //for testing only
    public static void main(String[] args) throws Exception {
        new ContentHandler().previewSchematic(Schematics.readBase64("bXNjaAF4nDWQXW6DQAyEB3b5MX/JW0/BQ6repuoDJa6EBEsFJFJu01v0WL1C7XWLhD6NGc8sizPOKXwYFsbTyzIF7i/P+zgcB2/9lT84jIx8Ht553pG9/nx9v3kUfwaU4xru/Fg31NPBS7+vt038p8/At2U4prG/btM8A7jIiwzxISBBihypghTOlFMlx4EXayIDr3MICkRFqmJMIog72f+w06HancIZvCGD04ocsak0Z4VEURsaQyufpM1rZiGW1Ik97pW6F0+v62RFZEVkRaRFihhNFk0WTRZNds5KMyGIP1bZndQ6VETVmGpMtaZa6+/sEjpVv/XMJCs="));
    }

    private BufferedImage getImage(String name) {
        return regions.get(name, () -> {
            try {
                if (usingInternal) {
                    return ImageIO.read(imageUrls.get(name, imageUrls.get("error")));
                } else {
                    return ImageIO.read(imageFiles.get(name, imageFiles.get("error")).file());
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private BufferedImage tint(BufferedImage image, Color color) {
        BufferedImage copy = new BufferedImage(image.getWidth(), image.getHeight(), image.getType());
        Color tmp = new Color();
        for (int x = 0; x < copy.getWidth(); x++) {
            for (int y = 0; y < copy.getHeight(); y++) {
                int argb = image.getRGB(x, y);
                tmp.argb8888(argb);
                tmp.mul(color);
                copy.setRGB(x, y, tmp.argb8888());
            }
        }
        return copy;
    }

    public Schematic parseSchematic(String text) throws Exception {
        return Schematics.readBase64(text);
    }

    public BufferedImage previewSchematic(Schematic schem) throws Exception {
        var maxSize = 1024;
        if (schem.width > maxSize || schem.height > maxSize)
            throw new IOException("Schematic cannot be larger than " + maxSize + "x" + maxSize + ".");
        BufferedImage image = new BufferedImage(schem.width * 32, schem.height * 32, BufferedImage.TYPE_INT_ARGB);

        Draw.reset();
        Seq<BuildPlan> requests = schem.tiles.map(t -> new BuildPlan(t.x, t.y, t.rotation, t.block, t.config));
        currentGraphics = image.createGraphics();
        currentImage = image;
        requests.each(req -> {
            req.animScale = 1f;
            req.worldContext = false;
            req.block.drawPlanRegion(req, requests);
            Draw.reset();
        });

        requests.each(req -> req.block.drawPlanConfigTop(req, requests));

        return image;
    }
}