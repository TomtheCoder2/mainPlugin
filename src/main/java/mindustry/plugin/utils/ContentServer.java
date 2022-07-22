package mindustry.plugin.utils;

import java.awt.image.BufferedImage;

import arc.graphics.Pixmap;
import arc.graphics.g2d.Draw;
import arc.scene.ui.Image;
import mindustry.Vars;
import mindustry.maps.Map;

public class ContentServer {
    public static BufferedImage render(Map map) {
        var texture = map.texture;
        if (texture == null) {
            return null;
        }
        Pixmap pixmap = new Pixmap(texture.width, texture.height);
        texture.draw(pixmap);
        return pixmapToImage(pixmap);
    }

    public static BufferedImage renderGame() {
        if (Vars.renderer.minimap.getTexture() != null) {
            var texture = Vars.renderer.minimap.getTexture(); 
            Pixmap pixmap = new Pixmap(texture.width, texture.height);
            texture.draw(pixmap);
            return pixmapToImage(pixmap);
        } else {
            return null;
        }
    }

    protected static BufferedImage pixmapToImage(Pixmap pixmap) {
        var image = new BufferedImage(pixmap.width, pixmap.height, BufferedImage.TYPE_INT_ARGB);
        
        var pixels = pixmap.pixels;
        for (int x = 0; x < pixmap.width; x++) {
            for (int y = 0; y < pixmap.height; y++) {
                int idx = y * pixmap.width + x;
                byte r = pixels.get(idx*4),
                    g = pixels.get(idx*4+1),
                    b = pixels.get(idx*4+2);
                image.setRGB(x, y, (0xff << 24) + (r << 16) + (g << 8) + (b));
            }
        }

        return image;
    }
}
