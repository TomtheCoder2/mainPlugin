package mindustry.plugin.requests;

import arc.struct.ObjectMap;
import arc.struct.StringMap;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;


/**
 * This code snippet is from the MindustryBR-plugin
 * source: https://github.com/MindustryBR/MindustryBR-plugin/tree/96a2ab33038c248e3b69c89e45071f5f9601cb67
 *
 *                               MIT License
 *
 *                        Copyright (c) 2021 King-BR
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.*/

public class Translate {
    public static String API = "https://translate-api.ml";

    private Translate() {
        super();
    }

    public static String translate(String text, String lang) throws IOException, InterruptedException {
        StringMap tmp = new StringMap();
        tmp.put("text", text);
        tmp.put("lang", lang);

        return Request.get("/translate", tmp);
    }

    public static String detect(String text) throws IOException, InterruptedException {
        StringMap tmp = new StringMap();
        tmp.put("text", text);

        return Request.get("/detect", tmp);
    }

    public static class Request {
        // one instance, reuse
        private static final HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .build();

        private Request() {
            super();
        }

        public static String get(String route, StringMap query) throws IOException, InterruptedException {
            StringBuilder urlStr = new StringBuilder()
                    .append(Translate.API)
                    .append(route == null ? "/" : route);

            if (query.size > 0) {
                urlStr.append("?");
                ObjectMap.Keys<String> keys = query.keys();
                for (String key : keys) {
                    urlStr.append(key)
                            .append("=")
                            .append(URLEncoder.encode(query.get(key), StandardCharsets.UTF_8))
                            .append("&");
                }
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .GET()
                    .uri(URI.create(urlStr.toString()))
                    .setHeader("User-Agent", "King-BR/MindustryBR-plugin")
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.body();
        }
    }
}