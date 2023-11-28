import arc.files.Fi
import arc.files.ZipFi
import arc.graphics.Color
import arc.graphics.Pixmap
import arc.math.Mathf
import arc.struct.Seq
import arc.util.serialization.Jval
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers
import java.time.LocalTime
import java.util.*
import java.util.concurrent.Semaphore
import kotlin.io.path.Path
import kotlin.io.path.forEachDirectoryEntry
import kotlin.io.path.nameWithoutExtension


val mindustryVer by extra("v146")
val arcVer by extra("v146")


buildscript {
    repositories {
        maven("https://www.jitpack.io")
    }
    dependencies {
        /** Referencing [arcVer] seems to break the entire script.
        Remember to update this whenever arcVer is updated too
         */
        classpath("com.github.anuken.Arc:arc-core:v146")
    }
}

plugins {
    kotlin("jvm") version "1.8.0"
    java
}

repositories {
    mavenCentral()
    maven("https://www.jitpack.io")
    maven("https://oss.sonatype.org/content/repositories/releases/")
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
    maven {
        name = "m2-dv8tion"
        url = uri("https://m2.dv8tion.net/releases")
    }
    maven("https://maven.xpdustry.com/anuken")
}

configurations.all{
    resolutionStrategy.eachDependency {
        if(this.requested.group == "com.github.Anuken.Arc"){
            this.useVersion("v146")
        }
    }
}

dependencies {
    // Mindustry
    compileOnly("com.github.anuken.mindustry:server:$mindustryVer")
    compileOnly("com.github.anuken.mindustry:core:$mindustryVer")
    compileOnly("com.github.Anuken.Arc:arc-core:$arcVer")

    implementation("com.electronwill.night-config:toml:3.6.6")
    implementation("org.javacord:javacord:3.5.0")
    implementation("jfree:jfreechart:1.0.13")
    implementation("org.postgresql:postgresql:42.5.4")
    compileOnly("com.github.7003mars:rollback:b1.4.3")
}

tasks.jar {
    exclude("META-INF/*.RSA", "META-INF/*.SF", "META-INF/*.DSA")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    // As from https://stackoverflow.com/a/52818011
    from(configurations.runtimeClasspath.get()
        .filter { !it.name.endsWith("pom") }.map {if (it.isDirectory) it else zipTree(it)})
}

tasks.processResources {
    duplicatesStrategy = DuplicatesStrategy.FAIL
    // Always update the mod version
    // If doing other jank in the future, remove this
    outputs.upToDateWhen { false }

    if (!hasProperty("exclude-sprites")) {
        dependsOn(":genSprites")
        dependsOn(":extractAtlas")
        with(copySpec {
            from("$buildDir/tmp/")
            include("aa-sprites/")
            include("atlas/sprites.aatls", "atlas/block_colors.png")
        })
    }
    val buildVer: String = project.findProperty("buildVersion") as String? ?: "build-${LocalTime.now()}"
    with(copySpec {
        from("plugin.hjson")
        filter {
            if (it.contains("version")) "version: \"$buildVer\"" else it
        }
    })
}

tasks.register("downloadSprites") {
    // Allow for incremental builds with these
//    enabled = false
    inputs.property("asset-ver", mindustryVer)
    outputs.dir(layout.buildDirectory.dir("tmp/assets"))
    // Base url for the GitHub api
    val api = "https://api.github.com/repos/anuken/mindustry"
    doLast {
        val ghToken: String = System.getenv("gh_token")
        val client: HttpClient = HttpClient.newHttpClient()
        val builder: HttpRequest.Builder = with(HttpRequest.newBuilder()) {
            header("accept", "application/vnd.github+json")
            header("Authorization", ghToken)
        }

        val downloadUrls: Seq<String> = Seq()
        fun getAll(url: String, output: Seq<String>) {
            logger.info("Searching $url")
            val req: HttpRequest = builder.uri(URI.create(url)).build()
            client.sendAsync(req, BodyHandlers.ofString()).thenApply { Jval.read(it.body())}.thenAccept {
                val paths: Jval.JsonArray = it.asArray()
                for (path in paths) {
                    when (path.getString("type")) {
                        "file" -> output.add(path.getString("download_url"))
                        "dir" -> getAll(path.getString("url"), output)
                    }
                }
            }.join()
        }
        getAll("$api/contents/core/assets-raw/sprites/blocks?ref=$mindustryVer", downloadUrls)
        downloadUrls.add("https://raw.githubusercontent.com/Anuken/Mindustry/$mindustryVer/core/assets-raw/sprites/effects/error.png")
        downloadUrls.filter { it.endsWith("png") }
        logger.lifecycle("Total size ${downloadUrls.size}")

        val downloadBuilder: HttpRequest.Builder = with(HttpRequest.newBuilder()) {
            header("Accept", "image/png")
        }
        val semaphore = Semaphore(30)
        for (url: String in downloadUrls) {
            semaphore.acquire()
            val fileName:String = url.split('/').last()
            val req: HttpRequest = downloadBuilder.uri(URI.create(url)).build()
            client.sendAsync(req, BodyHandlers.ofFile(Path("$buildDir/tmp/assets/$fileName"))).thenAccept {
                logger.info("Saved $fileName")
                semaphore.release()
            }
        }
    }
}

tasks.register("genSprites") {
    dependsOn(":downloadSprites")
//    enabled = false
    val outputDir = "$buildDir/tmp/aa-sprites"
    outputs.dir(outputDir)
    doLast {
        // Make an empty file
        val names: Fi = Fi("$outputDir/names.txt").also { it.delete() }
        names.writeString(mindustryVer+"\n");
        Path("$buildDir/tmp/assets").forEachDirectoryEntry("**.png") {
            names.writeString(it.nameWithoutExtension+"\n", true)
            antiAliasing(it.toFile(), File("$outputDir/${it.fileName}"))
        }
    }
}

tasks.register("extractAtlas") {
    inputs.property("release-ver", mindustryVer)
    val taskDir = "$buildDir/tmp/atlas"
    outputs.file("$taskDir/sprites.aatls")
    doLast {
        val releaseUrl = "https://github.com/Anuken/Mindustry/releases/download/$mindustryVer/Mindustry.jar"
        val client: HttpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()
        val req: HttpRequest = with(HttpRequest.newBuilder()) {
            uri(URI.create(releaseUrl))
            header("Accept", "application/octet-stream")
        }.build()
        println("Downloading game jar from $releaseUrl")
        val resp = client.send(req, BodyHandlers.ofFile(Path("$taskDir/game.jar")))
        val gameJar: ZipFi = ZipFi(Fi(resp.body().toFile()))
        gameJar.child("sprites").child("sprites.aatls").copyTo(Fi("$taskDir/sprites.aatls"))
        gameJar.child("sprites").child("block_colors.png").copyTo(Fi("$taskDir/block_colors.png"))
    }
}


fun Pixmap.getRGB(x: Int, y: Int): Int {
    return this.getRaw(Mathf.clamp(x, 0, this.width-1), Mathf.clamp(y, 0, this.height-1))
}

/**
 * Code comes from https://github.com/PlumyGames/mgpp/blob/master/main/src/task/AntiAlias.kt
 */
fun antiAliasing(from: File, to: File) {
    val image = Pixmap(Fi(from))
    val out = image.copy()
    val color = Color()
    val sum = Color()
    val suma = Color()
    val p = IntArray(9)
    for (x in 0 until image.width) {
        for (y in 0 until image.height) {
            val A: Int = image.getRGB(x - 1, y + 1)
            val B: Int = image.getRGB(x, y + 1)
            val C: Int = image.getRGB(x + 1, y + 1)
            val D: Int = image.getRGB(x - 1, y)
            val E: Int = image.getRGB(x, y)
            val F: Int = image.getRGB(x + 1, y)
            val G: Int = image.getRGB(x - 1, y - 1)
            val H: Int = image.getRGB(x, y - 1)
            val I: Int = image.getRGB(x + 1, y - 1)
            Arrays.fill(p, E)
            if (D == B && D != H && B != F) p[0] = D
            if ((D == B && D != H && B != F && E != C) || (B == F && B != D && F != H && E != A)) p[1] = B
            if (B == F && B != D && F != H) p[2] = F
            if ((H == D && H != F && D != B && E != A) || (D == B && D != H && B != F && E != G)) p[3] = D
            if ((B == F && B != D && F != H && E != I) || (F == H && F != B && H != D && E != C)) p[5] = F
            if (H == D && H != F && D != B) p[6] = D
            if ((F == H && F != B && H != D && E != G) || (H == D && H != F && D != B && E != I)) p[7] = H
            if (F == H && F != B && H != D) p[8] = F
            suma.set(0)

            for (c in p) {
                color.rgba8888(c)
                color.premultiplyAlpha()
                suma.r(suma.r + color.r)
                suma.g(suma.g + color.g)
                suma.b(suma.b + color.b)
                suma.a(suma.a + color.a)
            }
            var fm = if (suma.a <= 0.001f) 0f else (1f / suma.a)
            suma.mul(fm, fm, fm, fm)
            var total = 0f
            sum.set(0)

            for (c in p) {
                color.rgba8888(c)
                val a = color.a
                color.lerp(suma, (1f - a))
                sum.r(sum.r + color.r)
                sum.g(sum.g + color.g)
                sum.b(sum.b + color.b)
                sum.a(sum.a + a)
                total += 1f
            }
            fm = 1f / total
            sum.mul(fm, fm, fm, fm)
            out.setRaw(x, y, sum.rgba8888())
            sum.set(0)
        }
    }
    image.dispose()
    out.dispose()

    Fi(to).writePng(out)
}
