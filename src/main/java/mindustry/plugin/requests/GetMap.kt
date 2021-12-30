package mindustry.plugin.requests

import arc.files.Fi
import arc.util.Http
import arc.util.Log
import arc.util.serialization.Base64Coder
import mindustry.plugin.utils.Utils
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.lang.Exception
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Paths
import java.util.*
import javax.imageio.ImageIO

class GetMap {
    /**
     * Generate an image for a map (.msav)
     *
     * @implNote this sends a http request to the MindServer from Traqun.
     */
    fun getMap(file: Fi): List<String>? {
        try {
            val client = HttpClient.newBuilder().build()
            val request = HttpRequest.newBuilder()
                .uri(URI.create(Utils.maps_url + "/map"))
                .POST(HttpRequest.BodyPublishers.ofFile(Paths.get(file.absolutePath())))
                .build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofByteArray())
            if (200 == response.statusCode()) {
                val bytes = response.body()
                Log.debug(Arrays.toString(bytes))
                convertToPNG(bytes)
                val data = response.headers()
                val mappeddata = data.map()
                val finalData: MutableList<String> = ArrayList()
                val absolutePath: String = File("./temp/output.png").getCanonicalPath()
                finalData.add(absolutePath)
                for ((key, value) in mappeddata) {
                    if (key == "name" || key == "author" || key == "desc" || key == "size") {
                        finalData.add(Base64Coder.decodeString(value[0]))
                        println(key + ":" + Base64Coder.decodeString(value[0]))
                    } else {
                        finalData.add(value[0])
                        println(key + ":" + value[0])
                    }
                }
                return finalData
            }
        } catch (e: Exception) {
            println(e)
        }
        return null
    }

    fun getMap2(file: Fi): BufferedImage? {
        val stream: ByteArrayInputStream = file.readByteStream()
        val request: Http.HttpRequest = Http.post(Utils.maps_url + "/map").content(stream, stream.available().toLong())
        var image: BufferedImage? = null;
        request.submit { httpResponse: Http.HttpResponse ->
            val bytes: ByteArray = httpResponse.result
            val i = ImageIO.read(ByteArrayInputStream(bytes))
            println(httpResponse)
            image = i
        }
        return image;
    }

    companion object {
        /**
         * just converts a byte[] to a png
         *
         * @param data the byte stream of the picture
         */
        @Throws(IOException::class)
        private fun convertToPNG(data: ByteArray) {
            val bis = ByteArrayInputStream(data)
            val bImage2: BufferedImage = ImageIO.read(bis)
            ImageIO.write(bImage2, "png", File("./temp/output.png"))
            println("image created")
        }

    }
}