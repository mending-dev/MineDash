rootProject.name = "MineDash"

include("api", "core")

// Auto-register every folder inside /modules as a Gradle subproject
file("modules").listFiles()
    ?.filter { it.isDirectory && File(it, "build.gradle.kts").exists() }
    ?.forEach { include(":modules:${it.name}") }