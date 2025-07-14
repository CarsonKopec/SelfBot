plugins {
    id("java")
}

group = "com.github.imagineforgee"
version = "0.0.1"


repositories {
    mavenCentral()
    maven { url = uri("https://dl.cloudsmith.io/public/clawsoftstudios/purffectlib/maven/") }
    maven { url = uri("https://m2.dv8tion.net/releases") }
    maven { url = uri("https://jitpack.io") }
    maven { url = uri("https://maven.lavalink.dev/releases") }
}


tasks.jar {
    manifest {
        attributes["Main-Class"] = "com.github.imagineforgee.bot.Main"
    }
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
