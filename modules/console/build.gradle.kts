dependencies {
    // Modules only depend on the API, never on core
    compileOnly(project(":api"))
    compileOnly("org.spigotmc:spigot-api:1.8.8-R0.1-SNAPSHOT")
}