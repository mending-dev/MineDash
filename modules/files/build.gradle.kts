// Adds the tasks npmInstall and buildFrontend for modules/files/frontend
apply(from = rootProject.file("gradle/frontend.gradle.kts"))

dependencies {
    // Modules only depend on the API, never on core
    compileOnly(project(":api"))
    compileOnly("org.spigotmc:spigot-api:1.8.8-R0.1-SNAPSHOT")
}

tasks.processResources {
    val version = project.version
    inputs.property("version", version)
    filesMatching("module.json") {
        expand("version" to version)
    }

    // Bundle the module frontend into the jar under /web
    from(tasks.named("buildFrontend")) {
        into("web")
    }
}