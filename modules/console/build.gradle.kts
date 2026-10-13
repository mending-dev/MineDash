dependencies {
    // Modules only depend on the API, never on core
    compileOnly(project(":api"))
    compileOnly("org.spigotmc:spigot-api:1.8.8-R0.1-SNAPSHOT")
    // Provided by the server at runtime. Oldest version Minecraft uses, newer ones stay compatible
    compileOnly("org.apache.logging.log4j:log4j-core:2.0-beta9")
}

tasks.processResources {
    val version = project.version
    inputs.property("version", version)
    filesMatching("module.json") {
        expand("version" to version)
    }
}