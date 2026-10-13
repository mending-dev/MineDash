plugins {
    id("com.gradleup.shadow") version "9.2.2"
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:1.8.8-R0.1-SNAPSHOT")
    implementation(project(":api"))

    // Last Javalin line that supports Java 8 (Jetty 9.4)
    implementation("io.javalin:javalin:4.6.8")
    // Routes Jetty/Javalin logging to java.util.logging, which Bukkit prints
    implementation("org.slf4j:slf4j-jdk14:1.7.36")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.mindrot:jbcrypt:0.4")
}

tasks.processResources {
    val version = project.version
    inputs.property("version", version)
    filesMatching("plugin.yml") {
        expand("version" to version)
    }
}

tasks.shadowJar {
    archiveBaseName.set("MineDash")
    archiveClassifier.set("")

    // Required so Jetty's service files survive relocation
    mergeServiceFiles()
    exclude("module-info.class", "META-INF/versions/*/module-info.class", "META-INF/maven/**")

    // Relocate everything except our own api package (modules need it unrelocated)
    val libs = "dev.mending.minedash.libs"
    relocate("io.javalin", "${libs}.javalin")
    relocate("org.eclipse.jetty", "${libs}.jetty")
    relocate("javax.servlet", "${libs}.servlet")
    relocate("org.slf4j", "${libs}.slf4j")
    relocate("kotlin", "${libs}.kotlin")
    relocate("org.jetbrains", "${libs}.jetbrains")
    relocate("com.google.gson", "${libs}.gson")
    relocate("org.mindrot", "${libs}.mindrot")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}