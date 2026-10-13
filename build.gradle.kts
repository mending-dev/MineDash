allprojects {
    group = "dev.mending.minedash"
    version = "1.0.0-SNAPSHOT"

    repositories {
        mavenCentral()
        maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
    }
}

subprojects {
    apply(plugin = "java-library")

    // The bungeecord-chat snapshot required by spigot-api 1.8.8 no longer exists
    configurations.all {
        exclude(group = "net.md-5", module = "bungeecord-chat")
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        // Compile to Java 8 bytecode so the plugin runs on Java 8 up to 25
        options.release.set(8)
    }
}