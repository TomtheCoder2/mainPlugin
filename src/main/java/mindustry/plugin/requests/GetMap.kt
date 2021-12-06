package mindustry.plugin.requests;

import arc.files.Fi;
import arc.util.serialization.Base64Coder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import static arc.util.Log.debug;

import static mindustry.plugin.Utils.*;

public class GetMap {
    /**
     * Generate an image for a map (.msav)
     *
     * @implNote this sends a http request to the MindServer from Traqun.
     */
    public List<String> getMap(Fi file) {
        try {
            var client = HttpClient.newBuilder().build();
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(maps_url + "/map"))
                    .POST(HttpRequest.BodyPublishers.ofFile(Paths.get(file.absolutePath())))
                    .build();
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (200 == response.statusCode()) {
                byte[] bytes = response.body();
                debug(Arrays.toString(bytes));
                convertToPNG(bytes);
                HttpHeaders data = response.headers();
                Map<String, List<String>> mappeddata = data.map();
                List<String> finalData = new ArrayList<>();
                String absolutePath = new File("./temp/output.png").getCanonicalPath();
                finalData.add(absolutePath);
                for (Map.Entry<String, List<String>> entry : mappeddata.entrySet()) {
                    if (entry.getKey().equals("name") || entry.getKey().equals("author") || entry.getKey().equals("desc") || entry.getKey().equals("size")) {
                        finalData.add(Base64Coder.decodeString(entry.getValue().get(0)));
                        System.out.println(entry.getKey() + ":" + Base64Coder.decodeString(entry.getValue().get(0)));

                    } else {
                        finalData.add(entry.getValue().get(0));
                        System.out.println(entry.getKey() + ":" + entry.getValue().get(0));
                    }

                }
                return finalData;
            }
        } catch (Exception e) {
            System.out.println(e);
        }

        return null;
    }

    /**
     * just converts a byte[] to a png
     *
     * @param data the byte stream of the picture
     */
    private static void convertToPNG(byte[] data) throws IOException {
        ByteArrayInputStream bis = new ByteArrayInputStream(data);
        BufferedImage bImage2 = ImageIO.read(bis);
        ImageIO.write(bImage2, "png", new File("./temp/output.png"));
        System.out.println("image created");
    }
}