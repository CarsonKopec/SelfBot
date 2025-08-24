plugins {
    id("java")
}

group = "com.github.imagineforgee.selfbotlib"
version = "0.0.1"
var opusVersions = "1.1.1"

repositories {
    mavenCentral()
    maven { url = uri("https://dl.cloudsmith.io/public/clawsoftstudios/purffectlib/maven/") }
    maven { url = uri("https://m2.dv8tion.net/releases") }
    maven { url = uri("https://jitpack.io") }
    maven { url = uri("https://maven.lavalink.dev/releases") }
}

dependencies {
    implementation("org.java-websocket:Java-WebSocket:1.5.3")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.slf4j:slf4j-simple:2.0.7")
    implementation("io.projectreactor:reactor-core:3.8.0-M4")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.clawsoftstudios.purrfectlib:javautils:0.1.1")
    implementation("com.goterl:lazysodium-java:5.2.0")
    implementation("net.java.dev.jna:jna:5.17.0")
    implementation("dev.arbjerg:lavaplayer:2.2.4")
    implementation("dev.lavalink.youtube:common:1.13.3")

    implementation("club.minnced:opus-java-api:${opusVersions}")
    implementation("club.minnced:opus-java-natives:${opusVersions}")
    implementation("club.minnced:opus-java:${opusVersions}")
}

tasks.test {
    useJUnitPlatform()
}