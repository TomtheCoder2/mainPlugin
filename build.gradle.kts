import java.time.LocalTime
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
}

dependencies {
    val mindustryVer = "v144.3"
    val arcVer = mindustryVer
    // Mindustry
    compileOnly("com.github.Anuken.mindustryjitpack:server:$mindustryVer")
    compileOnly("com.github.Anuken.mindustryjitpack:core:$mindustryVer")
    compileOnly("com.github.Anuken.Arc:arc-core:$arcVer")

    implementation("com.electronwill.night-config:toml:3.6.6")
    implementation("org.javacord:javacord:3.5.0")
    implementation("net.dv8tion:JDA:4.3.0_277")
    implementation("jfree:jfreechart:1.0.13")
    implementation("org.postgresql:postgresql:42.5.4")
    compileOnly("com.github.7003Mars:rollback:b1.2")
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
    val buildVer: String = project.findProperty("buildVersion") as String? ?: "build-${LocalTime.now()}"
    with(copySpec {
        from("plugin.hjson")
        filter {
            if (it.contains("version")) "version: \"$buildVer\"" else it
        }
    })
}


